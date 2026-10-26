#!/usr/bin/env python3
"""Deterministic, frame-cued orchestral/mythic synthesis. Python + NumPy only."""
import json
import wave
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SR = 48000
RNG = np.random.default_rng(731905)
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
mix = np.zeros((N, 2), dtype=np.float32)


def env(t, duration, attack=.01, release=.2):
    return np.minimum(1, t / max(attack, 1 / SR)) ** 2 * np.minimum(1, np.maximum(0, duration - t) / max(release, 1 / SR)) ** 2


def add(sid, frame, sound, gain=1, pan=0, echo=True):
    """All placements are computed from the current timeline, including tails."""
    pos = round((START[sid] + frame) * SR / FPS)
    signal = np.asarray(sound, dtype=np.float32) * gain
    def place(offset, level, p):
        begin = pos + round(offset * SR)
        lo, hi = max(0, begin), min(N, begin + len(signal))
        if hi <= lo:
            return
        weights = np.sqrt([(1-p)/2, (1+p)/2])
        mix[lo:hi] += signal[lo-begin:hi-begin, None] * (level * weights)
    place(0, 1, pan)
    if echo:
        for delay, level in ((.113, .12), (.271, .10), (.433, .075), (.719, .05), (1.071, .03)):
            place(delay, level, -pan if delay < .5 else pan * .5)


def synth(kind, freq=110, dur=3):
    t = np.arange(round(dur * SR), dtype=np.float64) / SR
    if kind == 'pluck':
        y = sum(np.sin(2*np.pi*freq*k*t + .05*k) * np.exp(-t*(1.7+.6*k)) / k**1.25 for k in range(1, 10))
        y *= env(t, dur, .003, .15)
    elif kind in ('bell', 'gong', 'glass', 'chain'):
        ratios = {'bell':[1,2.01,2.76,4.07,5.4], 'gong':[1,1.43,1.91,2.67,3.79,5.13], 'glass':[1,2.76,5.4,8.93], 'chain':[1,1.37,2.19,3.53,5.91,8.13]}[kind]
        y = sum(np.sin(2*np.pi*freq*r*t + .3*i)*np.exp(-t*(.5+i*.27))/(1+i)**.85 for i,r in enumerate(ratios))
        y *= env(t, dur, .006, min(1, dur*.3))
    elif kind in ('organ', 'choir', 'strings'):
        y = np.zeros_like(t)
        if kind == 'organ':
            for k, a in ((.5,.25),(1,1),(2,.4),(3,.19),(4,.12),(6,.05)):
                y += a*np.sin(2*np.pi*freq*k*t)
        else:
            for detune in (-.004, 0, .003):
                phase = 2*np.pi*freq*(1+detune)*t + .045*np.sin(2*np.pi*4.7*t)
                y += (np.sin(phase)+.18*np.sin(2*phase)+.06*np.sin(3*phase))/3
            if kind == 'strings':
                y *= .65+.35*np.sin(2*np.pi*6.1*t)**2
        y *= env(t, dur, min(1.3, dur*.25), min(2.5, dur*.4))
    elif kind in ('drum', 'impact', 'stone'):
        phase = 2*np.pi*(freq*t + freq*.9*.045*(1-np.exp(-t/.045)))
        y = np.sin(phase)*np.exp(-t/(.6 if kind=='drum' else 1.2))
        noise = RNG.normal(0, 1, len(t))
        noise = np.convolve(noise, np.ones(19)/19, mode='same')
        y += noise*np.exp(-t/.09)*(.4 if kind=='drum' else 1.5)
        if kind != 'drum':
            y += .3*np.sin(2*np.pi*freq*1.43*t)*np.exp(-t/1.6)
        y *= env(t, dur, .003, .3)
    elif kind in ('air', 'riser', 'tear', 'rumble'):
        noise = RNG.normal(0, 1, len(t))
        # Moving averages remove the brittle, white-noise digital edge.
        width = 131 if kind == 'rumble' else 13
        noise = np.convolve(noise, np.ones(width)/width, mode='same')
        phase = 2*np.pi*(freq*t + (freq*3 / (2*dur))*t*t)
        y = noise*.7 + .2*np.sin(phase)
        y *= env(t, dur, dur*.85 if kind in ('air','riser') else .015, .12)
        if kind in ('air', 'riser'):
            y *= (t/dur)**1.5
    else:
        raise ValueError(kind)
    return y.astype(np.float32)


def note(s, f, kind, hz=110, dur=3, gain=.3, pan=0):
    add(s, f, synth(kind, hz, dur), gain, pan)


def chord(s, f, notes, dur, gain=.2, kind='organ'):
    for i, hz in enumerate(notes):
        note(s, f, kind, hz, dur, gain/np.sqrt(len(notes)), (i/(max(1,len(notes)-1))-.5)*.7)


PENTA = [110, 123.4708, 138.5913, 164.8138, 184.9972]
WEST = [65.4064, 130.8128, 155.5635, 195.9977]

def east(s, f, step=20, gain=.24):
    for i, degree in enumerate((0,2,4,3,1,2,0)):
        note(s, f+i*step, 'pluck', PENTA[degree]*2, 3, gain, (-1)**i*.35)


def hit(s, f, gain=1):
    note(s,f,'impact',38,4.8,gain)
    note(s,f,'gong',58,6,gain*.38,-.15)

# Cold opening: distant metal, reverse breath, a single low detonation.
note('cold',0,'choir',32.7,8.6,.022)
note('cold',14,'bell',196,5,.10,-.3)
note('cold',150,'air',65,20/FPS,.42)
hit('cold',170,.75)
# Explicit cold-tail fade before frame 260, without modifying following cues.
a,b = round(START['cold']*SR/FPS), round((START['cold']+260)*SR/FPS)
mix[b-SR:b] *= np.linspace(1,0,SR,dtype=np.float32)[:,None]
mix[b:round((START['cold']+LENGTH['cold'])*SR/FPS)] = 0

note('title',20,'gong',73.4,5,.45)
east('title',20,18,.32)
chord('title',40,WEST,4.65,.45)
chord('title',65,[110,164.81,220],3.8,.23,'choir')

chord('origin',0,[65.4,130.8,185],15,.12,'choir')
for i in range(10):
    note('origin',70+i*2,'glass',220*2**(i/12),1.5,.065,i/12-.4)
for f in (120,250): note('origin',f,'bell',330,4,.09)
for f in range(340,LENGTH['origin'],30): note('origin',f,'drum',45,1.1,.13+.22*(f-340)/110)

east('tz',0,52,.32)
chord('tz',0,[55,110,164.81],14,.18,'choir')
for f,h in ((0,110),(190,82.4)): note('tz',f,'gong',h,8,.17)
for f,h in zip((60,110,160),(880,987.77,1108.73)): note('tz',f,'bell',h,2.5,.22,.3)
note('tz',200,'gong',49,7,.6)
for f,ns in ((0,WEST),(130,[61.735,123.47,146.83,185]),(260,[55,110,130.81,164.81])):
    chord('god',f,ns,5.3,.36)
chord('god',0,[261.63,311.13,392],14,.17,'choir')
for f in range(20,400,37): note('god',f,'strings',783.99,2,.075,(-1)**f*.5)
note('god',60,'glass',1046.5,3,.22)
chord('god',130,[739.99,783.99],2.6,.22,'glass')
east('r1',0,27,.3)
chord('r1',0,WEST,7,.4)
f=60
while f < 207:
    note('r1',f,'drum',43,.9,.22+.5*(f-60)/152)
    f += max(6,26-(f-60)*.13)
hit('r1',212,1.25)

# Round two: 120 BPM taiko; the fog interval uses darker, quieter drums.
for i,f in enumerate(range(0,LENGTH['r2'],15)):
    fog = 286 <= f <= 466
    note('r2',f,'drum',32 if fog else (48 if i%4 else 39),1.2,(.17 if fog else .34)*(1 if i%2==0 else .55),(-1)**i*.18)
for f in (100,112,196,440,700): hit('r2',f,.48 if f<150 else 1.05)
for f in (45,385,645): chord('r2',f,[98,123.47,146.83],.65,.52)
for f in (115,272,525):
    note('r2',f,'gong',98,2,.3)
    east('r2',f,3,.27)
chord('r2',410,[261.63,392,523.25],4,.42,'choir')
for f in range(540,630,4): note('r2',f,'pluck',PENTA[(f//4)%5]*3,.8,.14,(-1)**(f//4)*.5)
for f in range(655,745,9): note('r2',f,'strings',740*(1+.035*np.sin(f)),1.3,.13,.5*np.sin(f))

chord('r3',0,[36.7,55,82.4],23,.16,'choir')
note('r3',110,'choir',29.135,220/FPS,.35)
for f in range(0,LENGTH['r3'],30):
    if not 170 <= f < 300: note('r3',f,'drum',36,1.8,.32)
for f in range(280,350,7): note('r3',f,'drum',49,1,.35+(f-280)/220,(-1)**f*.2)
for f,g in ((360,1.65),(500,1.1),(625,1.35),(690,1.1)): hit('r3',f,g)
for f in (420,440,460): note('r3',f,'chain',430,2.4,.48)
chord('r3',575,[880,932.33,987.77,1046.5],3,.4,'choir')
for i in range(7): note('r3',625+i,'chain',320+i*177,1.8,.20,(-1)**i*.6)
note('r3',662,'rumble',27.5,2,.55)

note('r4',30,'riser',45,10,.85)
f=30
while f<330:
    note('r4',f,'drum',42,.8,.19+.6*(f-30)/300)
    f += max(4,25-(f-30)*.073)
chord('r4',120,[130.81,261.63,392,523.25],7,.38,'strings')
note('r4',262,'tear',380,1.7,.65)
hit('r4',330,2.05)

chord('fall',0,[55,110,138.59],13,.17,'choir')
for f,hz in zip((110,132,154,176,198,220,242),(739.989,659.255,554.365,493.883,440,369.994,329.628)):
    note('fall',f,'bell',hz,4,.25,.2)
    note('fall',f+36,'bell',hz*2,1.8,.065,-.3)
note('fall',300,'air',65,140/FPS,.24)
chord('fall',300,[110,164.81,220],140/FPS,.17,'choir')
note('fall',410,'stone',33,3,.65)
chord('fall',460,[55,110,138.59,164.81,195.998],6,.25)
for s in ('legacy','ledger'):
    chord(s,0,[55,110,164.81],LENGTH[s]/FPS+1,.075,'choir')
for f in (30,100,170,240,310): note('legacy',f,'glass',659.25,2,.065)
for f in (24,54,84): note('ledger',f,'glass',880,2,.065)
note('end',6,'bell',220,6,.22)
chord('end',60,[55,110,138.59,164.81,195.998],6,.20)

# DC removal, smooth final fade, gentle nonlinear mastering, then anti-overshoot LPF.
mix -= np.mean(mix,axis=0)
mix[-SR:] *= np.linspace(1,0,SR,dtype=np.float32)[:,None]**1.5
# 257-tap windowed-sinc low-pass: passband ends near 15 kHz.
k = np.arange(257)-128
kernel = 2*15000/SR*np.sinc(2*15000/SR*k)*np.hamming(257)
kernel /= kernel.sum()

def lowpass(audio):
    block=65536
    size=131072
    spectrum=np.fft.rfft(kernel,size)
    out=np.zeros((len(audio)+256,2),np.float32)
    for start in range(0,len(audio),block):
        part=audio[start:start+block]
        filtered=np.fft.irfft(np.fft.rfft(part,size,axis=0)*spectrum[:,None],size,axis=0)
        count=min(len(part)+256,len(out)-start)
        out[start:start+count] += filtered[:count].astype(np.float32)
    return out[128:128+len(audio)]

peak_target=10**(-1/20)
rms_target=10**(-17/20)
# Solve saturation drive for the requested crest factor, preserving quiet passages.
probe=mix[::8].astype(np.float64)
lo,hi=.001,150.0
for _ in range(26):
    drive=(lo+hi)/2
    trial=np.tanh(probe*drive)
    ratio=np.sqrt(np.mean(trial*trial))/np.max(np.abs(trial))
    if ratio < rms_target/peak_target: lo=drive
    else: hi=drive
master=lowpass(np.tanh(mix*((lo+hi)/2)))
master *= peak_target/np.max(np.abs(master))
# The linear FIR can spread the last sample: keep both file boundaries at zero.
master[:240] *= np.linspace(0,1,240,dtype=np.float32)[:,None]
master[-240:] *= np.linspace(1,0,240,dtype=np.float32)[:,None]
pcm=np.rint(np.clip(master,-1,1)*32767).astype('<i2')
output=ROOT/'public/audio/score.wav'
output.parent.mkdir(parents=True,exist_ok=True)
with wave.open(str(output),'wb') as wav:
    wav.setnchannels(2)
    wav.setsampwidth(2)
    wav.setframerate(SR)
    wav.writeframes(pcm.tobytes())
actual=pcm.astype(np.float64)/32768
peak=np.max(np.abs(actual))
rms=np.sqrt(np.mean(actual*actual))
with wave.open(str(output),'rb') as wav:
    assert wav.getnframes()==N and wav.getnchannels()==2 and wav.getsampwidth()==2 and wav.getframerate()==SR
assert np.isfinite(master).all()
print(f'Duration: {N/SR:.6f} s | {frames} frames at {FPS} fps | {N} samples')
print(f'Peak: {20*np.log10(peak):.3f} dBFS | RMS: {20*np.log10(rms):.3f} dBFS')
for sid,start in START.items(): print(f'{sid:8s}: {start/FPS:10.6f} s (frame {start})')
print(f'Wrote {output}')
