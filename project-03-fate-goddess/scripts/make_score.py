#!/usr/bin/env python3
"""Deterministic cartoon score. Only NumPy and the standard library; no assets."""
import json
import time
import wave
from pathlib import Path
import numpy as np

SR = 48000
BEAT = 60 / 108
ROOT = Path(__file__).resolve().parents[1]
SEED = 20261026


def main():
    started = time.perf_counter()
    rng = np.random.default_rng(SEED)
    timeline = json.loads((ROOT / 'src/timeline.json').read_text())
    assert timeline['fps'] == 30
    segments = timeline['segments']
    cursor = 0
    for i, s in enumerate(segments):
        s['start'] = (cursor - timeline['overlap'] * i) / 30
        cursor += s['frames']
    frames = cursor - timeline['overlap'] * (len(segments) - 1)
    n = frames * 1600
    duration = n / SR
    starts = {s['id']: s['start'] for s in segments}
    mix = np.zeros((n, 2), np.float32)

    def clock(d):
        return np.arange(round(d * SR), dtype=np.float64) / SR

    def hz(m):
        return 440 * 2 ** ((m - 69) / 12)

    def env(t, d, attack=.006, release=.035):
        return (1 - np.exp(-t / attack)) * np.minimum((d-t)/release, 1)

    def tone(m, d, kind='pluck'):
        t = clock(d)
        f = hz(m)
        phase = 2*np.pi*f*t + .025*np.sin(2*np.pi*5.3*t)
        if kind == 'pluck':
            a = sum(np.sin(h*phase) * np.exp(-t*(7+4*h)) / h**1.2
                    for h in range(1, 7))
            a += rng.standard_normal(len(t))*.035*np.exp(-t/.009)
        elif kind == 'bassoon':
            a = sum(np.sin(h*phase) / h**1.5 for h in (1, 3, 5, 7))
            a *= np.exp(-t/ .16)
        elif kind == 'brass':
            a = sum(np.sin(h*phase + .04*h*np.sin(2*np.pi*5*t))
                    / h**1.15 for h in range(1, 9)) * .52
        elif kind == 'choir':
            # Harmonic source with vowel-like resonances at 650 and 1150 Hz.
            a = sum(np.sin(h*phase) * (.05 + np.exp(-((h*f-650)/180)**2)
                    + .6*np.exp(-((h*f-1150)/220)**2)) / h**.6
                    for h in range(1, min(35, int(7000/f)))) * .5
        else:  # Inharmonic, fast-decaying glockenspiel.
            a = sum(v*np.sin(phase*r)*np.exp(-t/tau)
                    for r, v, tau in ((1, 1, .4), (2.76, .28, .18), (5.4, .08, .09)))
        return (a * env(t, d)).astype(np.float32)

    def noise(d, low, high):
        t = clock(d)
        f = np.fft.rfftfreq(len(t), 1/SR)
        a = np.fft.irfft(np.fft.rfft(rng.standard_normal(len(t))) *
                        (1-np.exp(-(f/low)**4)) / np.sqrt(1+(f/high)**8), n=len(t))
        return a / (np.sqrt(np.mean(a*a))+1e-12)

    def percussion(kind):
        d = {'wood': .12, 'snare': .19, 'cymbal': .8, 'gong': 2.8,
             'clank': .65, 'thud': .6, 'timpani': .6}[kind]
        t = clock(d)
        if kind in ('wood', 'timpani', 'thud'):
            f = {'wood': 950, 'timpani': 78, 'thud': 49}[kind]
            a = np.sin(2*np.pi*(f*t+f*.055*(1-np.exp(-t/.025))))
            a *= np.exp(-t/({'wood': .025, 'timpani': .18, 'thud': .12}[kind]))
        elif kind == 'snare':
            a = noise(d, 1800, 9000)*np.exp(-t/.04)*.45
        elif kind == 'cymbal':
            a = noise(d, 4500, 15000)*np.exp(-t/.18)*.4
        else:
            a = sum(np.sin(2*np.pi*f*t)*np.exp(-t/(d*.3)) for f in
                    ((73, 121, 197, 311, 499, 817) if kind == 'gong'
                     else (93, 287, 731, 1523, 2489))) / 3
            a += noise(d, 1700, 11000)*np.exp(-t/.075)*.25
        return (a*env(t, d, .001, .025)).astype(np.float32)

    drums = {k: percussion(k) for k in ('wood', 'snare', 'cymbal', 'gong', 'clank', 'thud', 'timpani')}
    cache = {}

    def note(m, d, kind):
        key = (m, d, kind)
        if key not in cache:
            cache[key] = tone(m, d, kind)
        return cache[key]

    def add(a, at, gain=.1, pan=0, room=True):
        # Short, quiet stereo reflections leave the centre clear for explanation.
        for delay, level, p in ((0, 1, pan), (.071, .065, -pan-.15), (.113, .045, pan+.15)):
            if delay and not room:
                continue
            offset = round((at+delay)*SR)
            lo, hi = max(0, offset), min(n, offset+len(a))
            if hi <= lo:
                continue
            p = np.clip(p, -.9, .9)
            balances = np.sqrt(np.array([1-p, 1+p])/2)
            mix[lo:hi] += (a[lo-offset:hi-offset, None] *
                           (gain*level*balances)).astype(np.float32)

    def slide(d, low, high, curve=1, vibrato=0):
        t = clock(d)
        x = t/d
        f = low*(high/low)**(x**curve)
        f *= 2**(vibrato*np.sin(2*np.pi*6.7*t)/12)
        phase = 2*np.pi*np.cumsum(f)/SR
        return (np.sin(phase)*env(t, d, .018, .08)).astype(np.float32)

    # Logo: ominous little pizzicati, bells, then a raised cartoon eyebrow.
    for j, m in enumerate((38, 45, 41, 44)):
        add(note(m, .5, 'pluck'), .3+j*1.15, .28, -.2)
    add(note(86, 1, 'bell'), 1.1, .09, .35)
    add(note(85, 1, 'bell'), 3.35, .065, -.3)
    add(slide(.6, 420, 1050, 1.8), starts['ch1']-.85, .17, .2)

    # Swing eighths: 2:1 timing, sparse upper register, comic chromatic neighbours.
    minor = ((38, 62, 65, 69), (43, 62, 67, 70), (45, 61, 64, 69), (38, 62, 65, 68))
    melody = (0, 2, 1, 2, 0, 1, 2, 1, 0, 2, 1, 0, 2, 1, 0, 1)
    for b, at in enumerate(np.arange(starts['ch1'], starts['ch3']-.3, BEAT)):
        chord = minor[(b//8) % 4]
        add(note(chord[0]+(7 if b%2 else 0), .24, 'bassoon'), at, .23, -.13)
        add(drums['wood'], at+BEAT*2/3, .055, .48*(-1)**b)
        if b%4 in (1, 3):
            add(drums['snare'], at, .055, -.3)
        for sub, off in enumerate((0, BEAT*2/3)):
            if (b*2+sub)%8 == 5:
                continue
            m = chord[1+melody[(b*2+sub)%16]]+12
            if b%16 == 14 and sub:
                m += 1
            add(note(m, .26, 'pluck'), at+off, .14 if sub == 0 else .10, .28)
        if b%8 == 6:
            add(note(chord[2]+24, .65, 'bell'), at+BEAT*2/3, .045, -.4)

    # Abrupt, deliberately pompous D-major march, with breathing room between stabs.
    major = ((38, 62, 66, 69), (43, 62, 67, 71), (45, 61, 64, 69), (38, 62, 66, 74))
    for b, at in enumerate(np.arange(starts['ch3'], starts['f17']-.3, BEAT)):
        chord = major[(b//8)%4]
        add(note(chord[0], .28, 'bassoon'), at, .22, -.2)
        if b%2 == 0:
            add(drums['timpani'], at, .23)
            for m in chord[1:]:
                add(note(m, .34 if b%4 else .58, 'brass'), at, .095, .3*(m%3-1))
        else:
            add(drums['snare'], at, .15, -.35)
        if b%8 == 7:
            for r in range(6):
                add(drums['snare'], at+r*BEAT/6, .035+.007*r, -.3)
        if b%4 == 2:
            add(note(chord[3]+12, .22, 'brass'), at+BEAT*.66, .09, .35)

    # Chapter punchlines are two short brass hits, without a preceding build.
    for s in segments:
        at = s['start']
        if s['kind'] == 'chapter':
            for off, g in ((0, .17), (.25, .23)):
                for m in (50, 57, 66 if s['id'] == 'ch3' else 65):
                    add(note(m, .19, 'brass'), at+off, g)
                add(drums['cymbal'], at+off, .2, -.4)
        elif s['kind'] == 'figure':
            add(note(86 if at < starts['ch3'] else 90, .55, 'bell'), at, .045, .4)

    cues = [
        ('f03', 90, 1.0, 'table ding + falling whistle'),
        ('f04', 150, 2.6, 'sad trombone'),
        ('f09', 100, 1.5, 'theremin resurrection'),
        ('f10', 190, 1.0, 'thud + falling whistle'),
        ('f10', 240, 2.0, 'tiptoe escape'),
        ('f12', 140, 1.0, 'metal clank'),
        ('f16', 150, 2.6, 'ancestral brass choir + gong'),
        ('f17', 60, 4.4, 'curve whistle + victory trumpet'),
    ]
    # Duck existing accompaniment locally; effects are added afterwards.
    for sid, frame, length, _ in cues:
        at = starts[sid]+frame/30
        lo, hi = max(0, round((at-.06)*SR)), min(n, round((at+length+.22)*SR))
        t = np.arange(hi-lo)/SR + lo/SR-at
        duck = 1-.62*np.minimum(np.clip((t+.06)/.06, 0, 1),
                                 np.clip((length+.22-t)/.22, 0, 1))
        mix[lo:hi] *= duck.astype(np.float32)[:, None]

    at = starts['f03']+90/30
    add(note(98, .8, 'bell'), at, .42)
    add(slide(.52, 1300, 310), at+.13, .30, .2)
    at = starts['f04']+150/30
    for j, m in enumerate((58, 57, 56, 55)):
        d = .37 if j < 3 else 1.35
        t = clock(d)
        f = hz(m)*2**((-.25*t/d+.19*np.sin(2*np.pi*5.5*t)*(j == 3))/12)
        phase = 2*np.pi*np.cumsum(f)/SR
        a = sum(np.sin(h*phase)/h**1.25 for h in range(1, 7))*.6*env(t, d, .035, .09)
        add(a, at+j*.41, .40, -.16)
    add(slide(1.5, 240, 820, .65, 1.25), starts['f09']+100/30, .42, .25)
    at = starts['f10']+190/30
    add(drums['thud'], at, .72)
    add(slide(.68, 880, 110), at+.03, .35, -.2)
    at = starts['f10']+240/30
    for j, m in enumerate((74, 50, 77, 53, 73, 49, 74, 50)):
        add(note(m, .22, 'pluck'), at+j*.24, .42*.82**j, .3*(-1)**j)
    at = starts['f12']+140/30
    add(drums['clank'], at, .72)
    add(drums['thud'], at, .6)
    at = starts['f16']+150/30
    for m in (38, 50, 62, 66, 69, 74):
        add(note(m, 1.3, 'brass'), at, .18, .16*(m%3-1))
        add(note(m+12, 2.35, 'choir'), at+.04, .12, .4*(m%3-1))
    add(drums['gong'], at, .49, -.2)

    # Curve: exactly 60 frames after f17, for exactly 90 frames.
    # First 60% rises only 3%; the remaining 40% climbs more than three octaves.
    at = starts['f17']+60/30
    t = clock(3)
    x = t/3
    progress = .012*np.minimum(x/.6, 1) + .988*np.clip((x-.6)/.4, 0, 1)**2.2
    f = 210*(2400/210)**progress
    whistle = np.sin(2*np.pi*np.cumsum(f)/SR)*env(t, 3, .02, .035)
    add(whistle, at, .43, .1)
    for off, m, d in ((0, 74, .18), (.22, 78, .18), (.44, 81, .2), (.72, 86, .58)):
        add(note(m, d, 'brass'), at+3+off, .36, -.15)
    # A few self-satisfied plucks after the curve, leaving the ending space.
    for j in range(4):
        add(note((62, 69, 66, 74)[j], .3, 'pluck'), at+5+j*.65, .12, .25)

    # Shave and a haircut, two bits: seven notes, with the answering pair delayed.
    at = starts['outro']+.15
    for beat, m in zip((0, 1, 1.5, 2, 3, 5, 6), (74, 69, 69, 71, 69, 73, 74)):
        when = at+beat*.44
        add(note(m, .23 if beat != 6 else .5, 'brass'), when, .27)
        add(drums['wood'], when, .13, -.2)
    # Only a quiet high bell tail remains, fading to silence for the final two seconds.
    add(note(86, 2.8, 'bell'), duration-3.3, .025, .35)
    fade = 2*SR
    mix[-fade:] *= (np.cos(np.linspace(0, np.pi/2, fade))**2).astype(np.float32)[:, None]
    mix[:480] *= np.linspace(0, 1, 480, dtype=np.float32)[:, None]
    mix[-1] = 0

    # Smooth peak control, never hard clipping. Solve drive on the entire signal's
    # amplitude distribution, then measure the actual quantized stereo output.
    peak_target, rms_target = 10**(-1/20), 10**(-17/20)
    peak = float(np.max(np.abs(mix)))
    hist, edges = np.histogram(np.abs(mix), bins=32768, range=(0, peak))
    centres = (edges[:-1]+edges[1:])*.5
    lo, hi = 1e-6, 1e4
    for _ in range(50):
        drive = (lo+hi)/2
        rms = np.sqrt(np.sum(hist*np.tanh(centres*drive)**2)/mix.size)
        if rms*peak_target/np.tanh(peak*drive) < rms_target:
            lo = drive
        else:
            hi = drive
    mix *= (lo+hi)/2
    np.tanh(mix, out=mix)
    mix *= peak_target/np.max(np.abs(mix))
    pcm = np.rint(mix*32768).astype('<i2')
    output = ROOT / 'public/audio/score.wav'
    with wave.open(str(output), 'wb') as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(SR)
        wav.writeframes(pcm.tobytes())
    peak_pcm = max(abs(int(pcm.min())), abs(int(pcm.max())))/32768
    rms_pcm = np.sqrt(np.mean(np.square(pcm, dtype=np.float64)))/32768
    assert len(pcm) == n and peak_pcm < 1
    assert abs(20*np.log10(rms_pcm)+17) < .15
    print(f'Total duration: {duration:.6f} s ({frames} frames; {n} samples)')
    print(f'Peak: {20*np.log10(peak_pcm):.3f} dBFS')
    print(f'RMS: {20*np.log10(rms_pcm):.3f} dBFS')
    for sid, frame, _, name in cues:
        print(f'{sid} + {frame:3d} frames: {starts[sid]+frame/30:.6f} s — {name}')
    print(f'Generated {output.relative_to(ROOT)} in {time.perf_counter()-started:.2f} s')
    print('Structure: mysterious plucked logo → swung D-minor tiptoeing → abrupt D-major mock-epic march.')
    print('Comic brass chapter stings; quiet figure bells; ducked cartoon effects; accelerating curve whistle.')
    print('Ending: victory trumpet → shave-and-a-haircut → quiet two-second fade.')


if __name__ == '__main__':
    main()
