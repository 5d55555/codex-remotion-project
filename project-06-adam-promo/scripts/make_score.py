#!/usr/bin/env python3
"""Deterministic orchestral gramophone score. Standard library + NumPy only.
All cue positions are local frames; segment offsets come from timeline.json.
"""
import json
import math
from pathlib import Path
import wave
import numpy as np

SR = 48000
ROOT = Path(__file__).resolve().parents[1]
RNG = np.random.default_rng(1895)
TIMELINE = json.loads((ROOT / 'src/timeline.json').read_text())
FPS = TIMELINE['fps']
assert FPS == 30 and TIMELINE['overlap'] == 0
START, LENGTH = {}, {}
frames = 0
for segment in TIMELINE['segments']:
    START[segment['id']] = frames
    LENGTH[segment['id']] = segment['frames']
    frames += segment['frames']
N = round(frames * SR / FPS)
music = np.zeros((N, 2), dtype=np.float64)


def sec(s, f=0):
    return (START[s] + f) / FPS


def env(n, attack=.02, release=.12):
    e = np.ones(n)
    a, r = min(n, round(attack*SR)), min(n, round(release*SR))
    if a: e[:a] *= np.sin(np.linspace(0, np.pi/2, a))**2
    if r: e[-r:] *= np.sin(np.linspace(np.pi/2, 0, r))**2
    return e


def add(s, f, x, gain=1, pan=0):
    i = round(sec(s, f)*SR)
    x = np.asarray(x)
    count = min(len(x), N-i)
    if count <= 0: return
    music[i:i+count, 0] += x[:count]*gain*math.sqrt((1-pan)/2)
    music[i:i+count, 1] += x[:count]*gain*math.sqrt((1+pan)/2)


def hz(m): return 440*2**((m-69)/12)


def tone(m, duration, kind='brass'):
    t = np.arange(round(duration*SR))/SR
    freq = hz(m)
    p = 2*np.pi*freq*t + .007*np.sin(2*np.pi*5.1*t)
    if kind == 'bell':
        x = sum(a*np.sin(p*h)*np.exp(-t/d) for h,a,d in [(1,1,2.4),(2.01,.45,1.2),(2.76,.22,.6),(4.08,.12,.3)])
        return x*env(len(t), .004, .25)*.5
    if kind == 'organ':
        x = sum(np.sin(p*h)*a for h,a in [(1,1),(2,.55),(3,.26),(4,.16),(6,.07)])
        return x*env(len(t), .28, .8)*.36
    if kind == 'choir':
        x = sum(np.sin(p*h + .025*np.sin(2*np.pi*(4.3+h*.06)*t))*a for h,a in [(1,.7),(2,.4),(3,.55),(4,.2),(5,.25),(7,.13)])
        return x*env(len(t), .65, 1.0)*.35
    if kind == 'strings':
        x = sum(np.sin(p*h+.12*np.sin(2*np.pi*5*t))*1/h for h in range(1,9))
        return x*env(len(t), .5, .7)*.3
    if kind == 'bass':
        return (np.sin(p)+.35*np.sin(2*p)+.14*np.sin(3*p))*env(len(t), .035, .12)*.6
    x = sum(np.sin(p*h)*np.exp(-h/5)/h**.55 for h in range(1,13))
    return x*env(len(t), .09, min(.5,duration*.35))*.45


def chord(s,f,notes,d,g=.25,kind='brass'):
    for j,m in enumerate(notes): add(s,f,tone(m,d,kind),g, (j/(max(1,len(notes)-1))-.5)*.65)


def noise(d, low=400, high=7000):
    n=round(d*SR)
    x=RNG.standard_normal(n)
    spec=np.fft.rfft(x)
    freq=np.fft.rfftfreq(n,1/SR)
    spec *= (1-np.exp(-(freq/low)**4))*np.exp(-(freq/high)**6)
    x=np.fft.irfft(spec,n)
    return x/(np.std(x)+1e-12)


def drum(kind='snare',d=.4):
    t=np.arange(round(d*SR))/SR
    if kind=='timp':
        x=np.sin(2*np.pi*(64*t+2.5*(1-np.exp(-t*12))))*np.exp(-t*3.8)+.12*noise(d,80,1500)*np.exp(-t*24)
    elif kind=='cym': x=noise(d,1600,10500)*np.exp(-t*2.4)*.45
    elif kind=='boom':
        x=1.1*np.sin(2*np.pi*(39*t+5*(1-np.exp(-t*9))))*np.exp(-t*2)+noise(d,65,2500)*np.exp(-t*2.8)*.8
    else: x=noise(d,650,6500)*np.exp(-t*22)*.65+.25*np.sin(2*np.pi*175*t)*np.exp(-t*35)
    return x*env(len(t), .003, .1)


def hit(s,f,g=.55):
    add(s,f,drum('timp',1.7),g,-.15)
    add(s,f,drum('cym',2),g*.5,.25)


def swoosh(s,f):
    d=.55; t=np.arange(round(d*SR))/SR
    x=noise(d,800,5500)*np.sin(np.pi*t/d)**2
    x+=.15*np.sin(2*np.pi*(250*t+1800*t*t))*np.sin(np.pi*t/d)**2
    add(s,f,x,.14,.1)


def paper(s,f): add(s,f,noise(.18,900,6500)*np.exp(-np.arange(round(.18*SR))/SR*45)*env(round(.18*SR),.002,.04),.24)

MAJOR=[[48,55,60,64],[45,52,57,60],[41,48,53,57],[43,50,55,59]]
THEME=[67,67,72,71,69,67,64,67]

def march(s,start,end,g=.4):
    beat=60/108
    k=0
    while start+k*beat*FPS<end:
        f=start+k*beat*FPS
        notes=MAJOR[(k//4)%4]
        fullness=1.25 if s=='steam' and f>=190 else 1
        add(s,f,tone(notes[0]-12,beat*.82,'bass'),g*.7*fullness,-.15)
        if k%2==1: add(s,f,drum(),g*.48,.2)
        if k%2==0: chord(s,f,notes,beat*1.65,g*.35*fullness)
        if k%2==0: add(s,f,tone(THEME[(k//2)%8],beat*1.5),g*.47*fullness,.1)
        k+=1

# Leader: mechanical shutter at 24 Hz; countdown ends at local frame 120.
t=np.arange(round(LENGTH['leader']/FPS*SR))/SR
projector=(.35*np.sin(2*np.pi*58*t)+.15*np.sin(2*np.pi*116*t))
projector+=.24*(np.maximum(0,np.cos(2*np.pi*24*t))**28)*noise(len(t)/SR,500,4500)
add('leader',0,projector*env(len(t),.08,.15),.035)
for f in [0,24,48,72,96]:
    t=np.arange(4800)/SR
    add('leader',f,np.sin(2*np.pi*1000*t)*env(len(t),.008,.025),.15)
add('open',14,tone(84,2.4,'bell'),.15)
chord('open',60,[48,55,60,64,67],6,.31)
march('steam',0,360)
t=np.arange(round(1.2*SR))/SR
whistle=sum(np.sin(2*np.pi*h*t+.1*np.sin(2*np.pi*4*t)) for h in [392,493.88,587.33])/3
add('steam',236,whistle*env(len(t),.16,.3),.28)
chord('smog',0,[45,52,57,60],7,.17,'strings')
chord('smog',210,[38,45,53,56],6,.20,'strings')
swoosh('smog',150); paper('smog',168)
chord('smog',334,[43,49,58,61],1.6,.29)
for f in range(10,126,5):
    if RNG.random()<.78:
        d=RNG.uniform(.04,.14)
        x=noise(d,1800,5800); add('quill',f,x*env(len(x),.01,.025),RNG.uniform(.012,.035),-.2)
for j,m in enumerate([76,79,83,78,76,71,74,75]): add('quill',140+j*12,tone(m,.85,'bell'),.17)
add('quill',250,tone(40,2.6,'bell'),.35)
chord('war',10,[33,40,46,57],5.5,.22,'strings')
t=np.arange(round(7.8*SR))/SR
freq=430+160*np.sin(2*np.pi*.34*t-np.pi/2)
p=2*np.pi*np.cumsum(freq)/SR
add('war',130,(np.sin(p)+.22*np.sin(3*p))*env(len(t),.6,1.5),.19)
add('war',130,(np.sin(2*np.pi*47*t)+.4*np.sin(2*np.pi*94*t))*(.8+.2*np.sin(2*np.pi*1.7*t))*env(len(t),.8,1),.16)
for f in [184,204,219,239,264,284]: add('war',f,drum('boom',3),.65,RNG.uniform(-.45,.45))
for f in [214,304]: swoosh('war',f)
for f in [232,322]: paper('war',f)
for f in range(300,396,30): add('war',f,drum('timp',1.3),.24)
chord('war',402,[38,45,51,58,61],1.95,.24,'strings')
# A military roll builds into the return of the brass.
for f in np.arange(0,150,3.5): add('godwar',f,drum('snare',.22),.09+.13*f/150,.2)
for f in range(0,150,25): add('godwar',f,drum('timp',1),.27)
hit('godwar',150,.9)
chord('godwar',170,[48,55,60,64,67,72],3.9,.43)
for j in range(5): chord('creed',j*78,MAJOR[j%4],3,.22,'organ')
for f in [26,164,214,264]: add('creed',f,tone(84 if f!=214 else 88,2.3,'bell'),.14)
chord('creed',350,[43,55,59,62,67],3.3,.26,'organ')
for f,notes in [(360,[43,55,59,62]),(392,[48,55,60,64])]:
    hit('creed',f,.55); chord('creed',f,notes,1.8,.36)
chord('chapel',0,[48,55,60,64],4.6,.16,'choir')
for j,m in enumerate([60,62,64,67]): add('chapel',60+j*18,tone(m,1.7,'choir'),.16)
chord('chapel',140,[55,60,64],1.7,.025,'choir')
chord('chapel',190,[55,60,64],1.1,.045,'choir')
add('chapel',212,tone(88,5,'bell'),.43)
for f,g,notes in [(232,.16,[48,55,60,64]),(266,.23,[43,55,59,62]),(300,.40,[36,48,55,60,64,67,72])]:
    d=(450-f)/FPS
    chord('chapel',f,notes,d,g,'organ'); chord('chapel',f,[m+12 for m in notes[1:5]],d,g*.75,'choir')
for f,notes in [(16,[43,50,55,59,62]),(46,[43,55,59,65]),(110,[36,48,55,60,64,67])]:
    hit('slogan',f,.7); chord('slogan',f,notes,3.9,.40)
chord('slogan',110,[48,55,60,64,72],4.2,.22,'organ')
# Credits stop two seconds before the end, leaving only surface noise.
for j,m in enumerate(THEME+[64,62,60]):
    f=j*23
    d=min(1.3,(LENGTH['credits']-60-f)/FPS)
    if d>0: add('credits',f,tone(m+12,d,'bell'),.17*(1-f/(LENGTH['credits']-30)))

# Modest room reflections, kept out of the last two seconds of the credits.
original=music.copy()
for delay,g in [(.071,.13),(.113,.10),(.193,.08),(.317,.06),(.479,.035),(.733,.025)]:
    k=round(delay*SR)
    music[k:]+=original[:-k,::-1]*g
del original
end_music=round(sec('credits',LENGTH['credits']-60)*SR)
music[end_music-2400:end_music]*=np.linspace(1,0,2400)[:,None]
music[end_music:]=0

# Gentle common time-base variation: 0.32 ms wow + 0.035 ms flutter.
t=np.arange(N)/SR
positions=np.arange(N)+SR*(.00032*np.sin(2*np.pi*.57*t)+.000035*np.sin(2*np.pi*6.3*t))
for c in range(2): music[:,c]=np.interp(positions,np.arange(N),music[:,c])
# Gramophone passband with smooth skirts (zero-phase FFT filtering).
def filter_audio(x, low=None, high=7000):
    n=len(x); freqs=np.fft.rfftfreq(n,1/SR)
    response=1/np.sqrt(1+(freqs/high)**12)
    if low is not None: response*=1/np.sqrt(1+(low/np.maximum(freqs,.001))**8)
    return np.fft.irfft(np.fft.rfft(x)*response,n)
for c in range(2): music[:,c]=filter_audio(music[:,c],150,7000)
# Quiet continuous shellac hiss and softly rounded isolated crackles.
hiss=filter_audio(RNG.standard_normal(N),450,6500)*.0015
surface=hiss
for i in RNG.integers(0,N-900,size=round(N/SR*9)):
    size=int(RNG.integers(30,220)); tt=np.arange(size)/SR
    tick=RNG.uniform(.003,.018)*RNG.choice([-1,1])*np.exp(-tt*1100)*np.sin(2*np.pi*RNG.uniform(1200,4500)*tt)*env(size,.00015,.0003)
    surface[i:i+size]+=tick
music+=surface[:,None]*np.array([1,.96])
# Calibrate crest factor after tanh and the mandatory pre-normalization 15 kHz
# low-pass. Binary search drive to reach RMS -17 dBFS at a -1 dBFS peak.
fade=np.ones(N); fade[:480]=np.linspace(0,1,480); fade[-SR:]=np.linspace(1,0,SR)
target_peak=10**(-1/20); target_ratio=10**(-17/20)/target_peak

def master(drive):
    y=np.tanh(music*drive)
    for c in range(2): y[:,c]=filter_audio(y[:,c],None,15000)
    y*=fade[:,None]
    return y

lo,hi=.05,100.0
for _ in range(13):
    mid=(lo+hi)/2
    y=master(mid)
    ratio=np.sqrt(np.mean(y*y))/np.max(np.abs(y))
    if ratio<target_ratio: lo=mid
    else: hi=mid
y=master((lo+hi)/2)
y*=target_peak/np.max(np.abs(y))
pcm=np.rint(y*32767).astype('<i2')
output=ROOT/'public/audio/score.wav'
output.parent.mkdir(parents=True,exist_ok=True)
with wave.open(str(output),'wb') as wav:
    wav.setnchannels(2); wav.setsampwidth(2); wav.setframerate(SR); wav.writeframes(pcm.tobytes())
actual=pcm.astype(np.float64)/32768
print(f'Output: {output.relative_to(ROOT)}')
print(f'Duration: {N/SR:.6f} s | {frames} frames | {N} samples | 48000 Hz / 16-bit / stereo')
print(f'Peak: {20*np.log10(np.max(np.abs(actual))):.3f} dBFS')
print(f'RMS: {20*np.log10(np.sqrt(np.mean(actual**2))):.3f} dBFS')
for s in START: print(f'  {s:8s}: {sec(s):.6f} s')
assert len(pcm)==N and np.isfinite(y).all()
