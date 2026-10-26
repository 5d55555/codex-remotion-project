#!/usr/bin/env python3
"""Deterministic procedural chiptune score. Only NumPy and Python stdlib."""
import json
import wave
from pathlib import Path
import numpy as np

SR = 48000
ROOT = Path(__file__).resolve().parents[1]
rng = np.random.default_rng(20261026)
timeline = json.loads((ROOT / 'src/timeline.json').read_text())
assert timeline['fps'] == 30 and timeline['overlap'] == 0
segments = timeline['segments']
total_frames = sum(s['frames'] for s in segments)
N = total_frames * SR // 30
mix = np.zeros((N, 2), dtype=np.float32)
starts = {}
frame = 0
for s in segments:
    starts[s['id']] = frame / 30
    frame += s['frames']


def hz(note):
    return 440 * 2 ** ((note - 69) / 12)


def put(at, x, gain=1, pan=0):
    i = round(at * SR)
    lo, hi = max(0, i), min(N, i + len(x))
    if hi <= lo:
        return
    a = np.asarray(x[max(0, -i):max(0, -i) + hi-lo], dtype=np.float32)
    mix[lo:hi, 0] += a * (gain * np.sqrt((1-pan)/2))
    mix[lo:hi, 1] += a * (gain * np.sqrt((1+pan)/2))


def tone(at, note, dur, gain=.15, kind='square', pan=0, slide=0, vibrato=0):
    t = np.arange(round(dur*SR), dtype=np.float32) / SR
    f = hz(note) * 2 ** ((slide*t/max(dur, .001) + vibrato*np.sin(2*np.pi*6*t))/12)
    phase = np.cumsum(f, dtype=np.float64) / SR
    if kind == 'square':
        # Finite harmonics retain chip character without ultrasonic aliasing.
        y = sum(np.sin(2*np.pi*k*phase)/k for k in (1, 3, 5, 7)) * .75
    elif kind == 'triangle':
        y = 2/np.pi * np.arcsin(np.sin(2*np.pi*phase))
    elif kind == 'brass':
        y = sum(np.sin(2*np.pi*k*phase)/k for k in range(1, 7))*.6
    elif kind == 'bell':
        y = (np.sin(2*np.pi*phase) + .4*np.sin(2*np.pi*2.76*phase)*np.exp(-t*4)) * np.exp(-t*2)
    else:
        y = np.sin(2*np.pi*phase)
    attack = .012 if dur > .5 else .004
    env = np.minimum(1, t/attack) * np.minimum(1, (dur-t)/min(.09, dur*.25))
    if kind in ('square', 'brass'):
        env *= .65 + .35*np.exp(-t*14)
    put(at, y*env, gain, pan)


def noise(at, dur, gain, rising=False, pan=0):
    t = np.arange(round(dur*SR), dtype=np.float32)/SR
    n = rng.standard_normal(len(t)).astype(np.float32)
    n = (n - np.roll(n, 1))*.4
    env = (t/dur)**1.6 if rising else np.exp(-t*max(8, 5/dur))
    env *= np.minimum(1, t/.002)*np.minimum(1, (dur-t)/.012)
    put(at, n*env, gain, pan)


def kick(at, gain=.36):
    t = np.arange(int(.23*SR), dtype=np.float32)/SR
    ph = 2*np.pi*(43*t + 105*.026*(1-np.exp(-t/.026)))
    put(at, np.sin(ph)*np.exp(-t*22)*np.minimum(1,t/.002), gain)


def hit(at, gain=.65):
    kick(at, gain)
    noise(at, .24, gain*.8)
    tone(at, 28, .35, gain*.45, 'sine', slide=-9)
    # A few discrete reflections, with no persistent audio outside the event.
    for delay, g in ((.07,.15),(.14,.07),(.23,.03)):
        noise(at+delay, .18, gain*g, pan=(-.5 if delay == .07 else .5))


def chord(at, notes, dur, gain=.12, kind='brass'):
    for j,n in enumerate(notes):
        tone(at, n, dur, gain, kind, (j-(len(notes)-1)/2)*.3)


def bed(start, dur, mode='full'):
    beat = .4
    light = mode in ('coldblue', 'warm', 'record')
    roots = [40, 36, 43, 38]
    melody = [76, 79, 83, 79, 74, 78, 81, 78, 79, 83, 86, 83, 78, 81, 74, 71]
    for k in range(int(np.ceil(dur/.2))):
        t = k*.2
        remain = dur-t
        if remain <= .02:
            continue
        root = roots[(k//16)%4]
        tone(start+t, root + (12 if k%8 == 6 else 0), min(.17,remain), .19 if light else .27, 'triangle')
        arp = root+24+[0,7,12,15][k%4]
        tone(start+t, arp, min(.13,remain), .065 if light else .085,
             'triangle' if mode == 'warm' else 'square', -.4 if k%2 else .4)
        if not light and k%2 == 0:
            tone(start+t, melody[(k//2)%16], min(.28,remain), .12, 'square', .12)
        if k%2 == 0 and (mode != 'record' or k%4 == 0):
            if k%4 == 0:
                kick(start+t, .22 if light else .36)
            else:
                noise(start+t, min(.14,remain), .19 if light else .32)
                tone(start+t, 50, min(.09,remain), .07, 'triangle')
        if mode != 'record' or k%2 == 0:
            noise(start+t, min(.045,remain), .07 if light else .105, pan=.45)
    if mode == 'full':
        for t in np.arange(0, dur-.5, 3.2):
            chord(start+t, [52,59,64], .32, .06)


def drone(at, dur, gain=.12, eerie=False):
    tone(at, 28 if not eerie else 40, dur, gain, 'sine', vibrato=.3)
    tone(at, 47 if eerie else 35, dur, gain*.45, 'triangle', -.4, vibrato=1.1 if eerie else .1)
    if eerie:
        tone(at, 70, dur, gain*.28, 'sine', .5, vibrato=1.8)


for s in segments:
    name, dur = s['id'], s['frames']/30
    a = starts[name]
    def event(f):
        return a+f/30
    if name == 'cold':
        drone(a, dur, .20)
        for t in np.arange(.5, dur-.5, 1.3):
            kick(a+t, .11)
            kick(a+t+.18, .065)
        noise(a+dur-10/30, 10/30, .32, True)
    elif name == 'title':
        bed(a, dur)
        for f in (14,30,48):
            hit(event(f), .65)
    elif name in ('p1','p2','record'):
        bed(a, dur, {'p1':'coldblue','p2':'warm','record':'record'}[name])
        if name != 'record':
            for j,f in enumerate((40,58,76,94)):
                tone(event(f), [76,79,83,88][j], .095, .24, pan=.2)
    elif name == 'round1':
        drone(a, 26/30, .10)
        chord(event(4), [40,52,59,64], .35, .20)
        chord(event(26), [52,59,64,76], .40, .24)
        bed(event(26), (205-26)/30)
        drone(event(205), (388-205)/30, .18, True)
        for t in np.arange(205/30,388/30,.2):
            tone(a+t, 76 if int(t*5)%2 else 77, .075, .055, 'triangle', .5)
        for f in (300,330):
            tone(event(f), 43, .14, .20, 'triangle', slide=-14)
            noise(event(f), .06, .06)
        hit(event(388), .8)
        bed(event(388), dur-388/30)
        for f in (95,135,420,445,470):
            hit(event(f), .48)
        tone(event(170), 71, .65, .20, 'square', slide=24)
        for j in range(6):
            tone(event(170)+j*.07, 83+j*2, .17, .065, 'bell', (-1)**j*.5)
    elif name == 'ko':
        drone(a, 16/30, .08)
        hit(event(16), 1.05)
        for d,g in ((.25,.09),(.5,.045),(.8,.018)):
            tone(event(16)+d, 28, 1.0, g, 'sine')
        # Rewind: pitched sample-and-hold noise swept down then up, flutter accelerating.
        t = np.arange(round(40/30*SR))/SR
        sweep = np.where(t < 2/3, 1-t*1.25, .1667+(t-2/3)*2.5)
        phase = np.cumsum(2400*sweep)/SR
        table = rng.uniform(-1,1,8192)
        y = table[np.floor(phase).astype(int)%len(table)]
        env = np.sin(np.pi*t/(40/30))**.7
        put(event(120), y*env*(.5+.5*np.sin(2*np.pi*(8*t+17*t*t))), .28)
        drone(event(160), dur-160/30, .09)
        for j,t in enumerate(np.arange(222/30,290/30,.22)):
            tone(a+t, [64,67,71,67][j%4], .075, .12, pan=(-1)**j*.3)
        for j,n in enumerate([76,79,83,88]):
            tone(event(290)+j*.13, n, .23, .18)
    elif name == 'sim':
        drone(a, 40/30, .055)
        scale = [64,67,69,71,74,76,79,81,83]
        for j in range(100):
            # Grid onsets are exactly three frames apart; pitch/brightness rise adds urgency.
            tone(event(40+3*j), int(rng.choice(scale)), .035, .035+.018*j/99, pan=float(rng.uniform(-.6,.6)))
        end = (40+3*100)/30
        for t in np.arange(end,dur,.4):
            tone(a+t, [40,40,43,38][int((t-end)/1.6)%4], min(.38,dur-t), .17, 'triangle')
        drone(a+end, dur-end, .065)
    elif name == 'verdict':
        hit(event(12), .75)
        bed(event(12), dur-12/30-.5, 'warm')
        theme = [64,67,71,76,74,71,79,76,71,69,67,64]
        for j,t in enumerate(np.arange(20/30,dur-1,.6)):
            n = theme[j%len(theme)]
            chord(a+t, [n,n-12,n-5], .42, .115)
        chord(a+dur-1, [52,59,64,67], .85, .13)
    elif name == 'gameover':
        for j,n in enumerate([76,71,67,64,59,52]):
            tone(event(8)+j*.22, n, .29 if j<5 else .65, .20)
        for j,f in enumerate(range(80,201,12)):
            tone(event(f), 83-j*2, .085, .13)
        tone(event(215), 88, 1.2, .23, 'bell', .15)
        tone(event(255), 95, .65, .24, 'bell')
        for j,n in enumerate([64,71,67,76,64]):
            tone(event(255)+.2+j*.16, n, .18 if j<4 else .65, .14, 'square', (-1)**j*.25)

# Short final fade and continuous soft saturation: no hard clipping. Search drive
# against the final peak-normalized RMS rather than assuming a fixed crest factor.
fade = min(SR, N)
mix[-fade:] *= np.linspace(1,0,fade,dtype=np.float32)[:,None]
peak_target = 10**(-1/20)
rms_target = 10**(-16/20)
def measure(drive):
    y = np.tanh(mix*drive)
    peak = float(np.max(np.abs(y)))
    return float(np.sqrt(np.mean(y.astype(np.float64)**2))) * peak_target/peak
low, high = .001, 32.
for _ in range(23):
    mid = (low+high)/2
    if measure(mid) < rms_target:
        low = mid
    else:
        high = mid
mix = np.tanh(mix*((low+high)/2))
mix *= peak_target/float(np.max(np.abs(mix)))
pcm = np.rint(mix*32767).astype('<i2')
output = ROOT/'public/audio/score.wav'
output.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(output), 'wb') as wav:
    wav.setnchannels(2)
    wav.setsampwidth(2)
    wav.setframerate(SR)
    wav.writeframes(pcm.tobytes())
actual = pcm.astype(np.float64)/32768
print(f'Total duration: {N/SR:.6f} seconds ({N} samples)')
print(f'Peak: {20*np.log10(np.max(np.abs(actual))):.4f} dBFS')
print(f'RMS: {20*np.log10(np.sqrt(np.mean(actual**2))):.4f} dBFS')
print('Segment starts (seconds):')
for name, start in starts.items():
    print(f'  {name}: {start:.6f}')
print(f'Wrote {output}')
