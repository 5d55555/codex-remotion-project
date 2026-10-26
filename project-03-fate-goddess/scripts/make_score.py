#!/usr/bin/env python3
"""Offline, deterministic 120 BPM trailer score; stdlib and NumPy only."""
import json
import time
import wave
from pathlib import Path

import numpy as np

SR = 48000
BPM = 120
BEAT = 60 / BPM
ROOT = Path(__file__).resolve().parents[1]
SEED = 20261026


def main():
    started = time.perf_counter()
    rng = np.random.default_rng(SEED)
    timeline = json.loads((ROOT / 'src/timeline.json').read_text())
    segments = timeline['segments']
    cursor = 0
    for i, segment in enumerate(segments):
        segment['start'] = (cursor - timeline['overlap'] * i) / timeline['fps']
        segment['end'] = segment['start'] + segment['frames'] / timeline['fps']
        cursor += segment['frames']
    duration = (cursor - timeline['overlap'] * (len(segments) - 1)) / timeline['fps']
    n = round(duration * SR)
    if abs(n / SR - duration) > 1e-9:
        raise ValueError('Timeline duration cannot be represented at 48 kHz')
    chapters = [s for s in segments if s['kind'] == 'chapter']
    origin = chapters[0]['start']
    outro = next((s['start'] for s in segments if s['kind'] == 'logo' and s['start'] > origin), duration)
    mix = np.zeros((n, 2), np.float32)
    send = np.zeros_like(mix)

    def add(sound, start, gain=1., pan=0., wet=.2):
        offset = round(start * SR)
        lo, hi = max(0, offset), min(n, offset + len(sound))
        if hi <= lo:
            return
        sound = sound[lo - offset:hi - offset]
        for channel, balance in enumerate((np.sqrt((1-pan)/2), np.sqrt((1+pan)/2))):
            signal = (sound * (gain * balance)).astype(np.float32)
            mix[lo:hi, channel] += signal
            send[lo:hi, channel] += signal * wet

    def clock(seconds):
        return np.arange(round(seconds * SR), dtype=np.float64) / SR

    def hz(midi):
        return 440 * 2 ** ((midi - 69) / 12)

    def noise(seconds, low, high):
        t = clock(seconds)
        f = np.fft.rfftfreq(len(t), 1/SR)
        response = (1 - np.exp(-(f / low)**4)) / np.sqrt(1 + (f/high)**8)
        a = np.fft.irfft(np.fft.rfft(rng.standard_normal(len(t))) * response, n=len(t))
        return a / (np.sqrt(np.mean(a*a)) + 1e-12)

    def strings(midi, seconds, bright=1600, attack=.012):
        t = clock(seconds)
        f = hz(midi)
        out = np.zeros(len(t))
        # Band-limited detuned saw harmonics, with low-pass response and vibrato.
        for detune in (-.0035, 0., .0035):
            phase = 2*np.pi*f*(1+detune)*t + .035*np.sin(2*np.pi*5.2*t)
            cutoff = bright * (.5 + .5 * (1-np.exp(-t/.12)))
            for h in range(1, min(17, int((SR/2-1)/(f*(1+abs(detune))))+1)):
                out += np.sin(h*phase) / (h * np.sqrt(1+(h*f/cutoff)**6))
        env = (1-np.exp(-t/attack)) * np.minimum((seconds-t)/min(.16, seconds/2), 1)
        return (out * env / 3).astype(np.float32)

    def bell(midi):
        t = clock(3.5)
        a = sum(level*np.sin(2*np.pi*hz(midi)*ratio*t)*np.exp(-t/decay)
                for ratio, level, decay in ((1, 1, 1.2), (2.71, .3, .65), (4.13, .12, .35)))
        return (a * (1-np.exp(-t/.003))).astype(np.float32)

    t = clock(1.4)
    kick = (np.sin(2*np.pi*(43*t + 65*.035*(1-np.exp(-t/.035)))) * np.exp(-t/.32)
            + .15*noise(1.4, 120, 2300)*np.exp(-t/.018)) * (1-np.exp(-t/.0015))
    t = clock(.28)
    snare = (noise(.28, 650, 6500)*np.exp(-t/.055) + .25*np.sin(2*np.pi*185*t)*np.exp(-t/.045))*(1-np.exp(-t/.001))
    t = clock(.065)
    tick = (noise(.065, 5500, 14000)*.65 + .35*np.sin(2*np.pi*3900*t))*np.exp(-t/.009)*(1-np.exp(-t/.0005))
    t = clock(3)
    boom = np.sin(2*np.pi*(32*t+38*.12*(1-np.exp(-t/.12))))*np.exp(-t/.7)*(1-np.exp(-t/.006))
    t = clock(2.5)
    crash = noise(2.5, 3100, 15500)*np.exp(-t/.55)*(1-np.exp(-t/.003))
    chords = ((38, 50, 53, 57), (34, 50, 53, 58), (41, 48, 53, 57), (36, 48, 52, 55))
    arp_cache = {m: strings(m, .22) for chord in chords for m in chord[1:]}
    high_cache = {m: strings(m+12, .19, 2600) for m in arp_cache}
    bass_cache = {}
    for chord in chords:
        midi = chord[0]
        t = clock(.32)
        bass_cache[midi] = ((np.sin(2*np.pi*hz(midi)*t)+.22*np.sin(4*np.pi*hz(midi)*t))
                            *(1-np.exp(-t/.007))*np.exp(-t/.15)*np.minimum((.32-t)/.04, 1)).astype(np.float32)

    def chapter_level(at):
        return sum(s['start'] <= at for s in chapters)

    def card(at):
        return any(s['start'] <= at < s['end'] for s in chapters)

    # Constant global grid: a new chord every eight beats (two bars).
    for step, at in enumerate(np.arange(origin, outro, BEAT/4)):
        level = chapter_level(at)
        chord = chords[(step//64) % len(chords)]
        accent = (1., .62, .78, .66)[step % 4]
        strength = .76 + .08*level
        space = 1.18 if card(at) else .83
        add(arp_cache[chord[1+(step % 6 if step % 6 < 3 else 5-step % 6)]], at,
            .105*accent*strength*space, -.28, .27)
        if level >= 2:
            add(high_cache[chord[1+step % 3]], at, .044*accent*space*(1.25 if level == 4 else 1), .38, .3)
        add(tick, at, .028*accent*strength, .42*(-1)**step, .025)
        if step % 2 == 0:
            add(bass_cache[chord[0]], at, .29*strength, 0, .035)
        if step % 4 == 0:
            beat = (step//4) % 4
            if beat in (0, 2):
                add(kick, at, .65*strength*space, 0, .045)
            else:
                add(snare, at, .19*strength*space, -.08, .075)
        if level >= 3 and step % 32 == 0:
            for midi in chord[1:]:
                add(strings(midi, min(3.9, outro-at), 950, .22), at,
                    .048*(1.6 if level == 4 else 1)*space, .15, .32)
        if level == 4 and step % 16 == 0:
            add(crash, at, .045, -.4, .18)

    # Sparse, dark pad throughout; no rhythmic layers under the closing logo.
    for at in np.arange(0, duration, 4):
        index = max(0, int((at-origin)//4)) % len(chords)
        chord = chords[index] if origin <= at < outro else chords[0]
        for midi in (chord[0]+12, chord[2]):
            add(strings(midi, min(5.5, duration-at), 350, .6), at, .065, -.3 if midi % 2 else .3, .38)
    for at in np.arange(0, origin-1, 1.5):
        add(kick, at, .22, 0, .06)
        add(kick, at+.23, .11, 0, .04)
    add(bell(74), .7, .13, -.25, .32)
    add(bell(81), 3.1, .075, .3, .32)

    for index, segment in enumerate(chapters):
        at = segment['start']
        t = clock(2)
        # Rising high-pass and opening low-pass, applied with FFT in short blocks.
        rise = noise(2, 350, 11000)
        phase = 2*np.pi*(300*t+1100*t*t)
        rise = (rise*.65 + .35*np.sin(phase))*(t/2)**2*np.sin(np.minimum(t/.04,1)*np.pi/2)
        add(rise, at-2, .19, .1, .22)
        for roll in range(24):
            progress = roll/24
            add(snare, at-2+2*progress, .018+.15*progress**2, .18*(-1)**roll, .08)
        chord = chords[int(max(0, (at-origin)//4)) % len(chords)]
        for midi in chord[1:]:
            add(strings(midi, 3.8, 3200, .07), at, .28+.025*index, .22*(midi % 3-1), .34)
            add(strings(midi+12, 2.6, 2500, .12), at, .065, -.2, .38)
        add(boom, at, 1., 0, .04)
        add(crash, at, .24, -.35, .25)
    scale = (74, 77, 79, 81, 82, 84, 86)
    for index, segment in enumerate(s for s in segments if s['kind'] == 'figure'):
        add(bell(scale[index % len(scale)]), segment['start'], .16, .3*(-1)**index, .3)
    if outro < duration:
        for midi in (50, 53, 57, 62):
            add(strings(midi, min(5.5, duration-outro), 1800, .10), outro, .20, .1, .4)
        add(boom, outro, .65, 0, .04)
        add(crash, outro, .12, .3, .25)

    # Two-second synthetic stereo room, block FFT overlap-add; sends keep drums dry.
    ir_t = clock(2)
    block = 131072
    fft_size = 1 << (block + len(ir_t) - 2).bit_length()
    for channel in range(2):
        ir = noise(2, 180, 4800)*np.exp(-ir_t/.40)*np.minimum(ir_t/.025, 1)
        ir[:round(.018*SR)] = 0
        ir /= np.sqrt(np.sum(ir*ir))
        transfer = np.fft.rfft(ir, n=fft_size)
        for offset in range(0, n, block):
            dry = send[offset:offset+block, channel]
            wet = np.fft.irfft(np.fft.rfft(dry, n=fft_size)*transfer, n=fft_size)
            count = min(n-offset, len(dry)+len(ir)-1)
            mix[offset:offset+count, channel] += wet[:count].astype(np.float32)
    del send
    mix -= np.mean(mix, axis=0)
    mix[:SR] *= np.linspace(0, 1, SR, dtype=np.float32)[:, None]
    fade = min(n, 4*SR)
    mix[-fade:] *= (np.cos(np.linspace(0, np.pi/2, fade))**2).astype(np.float32)[:, None]
    mix[-1] = 0
    # Solve soft-saturation drive for -15 dBFS RMS after -1 dBFS peak normalization.
    peak_target, rms_target = 10**(-1/20), 10**(-15/20)
    peak = float(np.max(np.abs(mix)))
    probe = mix[::16].astype(np.float64)
    low, high = .00001, 1000.
    for _ in range(32):
        drive = (low+high)/2
        rms = np.sqrt(np.mean(np.tanh(probe*drive)**2))
        if rms*peak_target/np.tanh(peak*drive) < rms_target:
            low = drive
        else:
            high = drive
    mix *= (low+high)/2
    np.tanh(mix, out=mix)
    mix *= peak_target/np.max(np.abs(mix))
    pcm = np.rint(mix*32767).astype('<i2')
    output = ROOT / 'public/audio/score.wav'
    output.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(output), 'wb') as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(SR)
        wav.writeframes(pcm.tobytes())
    peak_pcm = max(abs(int(pcm.min())), abs(int(pcm.max())))/32768
    rms_pcm = np.sqrt(np.mean(np.square(pcm, dtype=np.float64)))/32768
    print(f'Total duration: {n/SR:.6f} s ({n} samples)')
    print(f'Peak: {20*np.log10(peak_pcm):.3f} dBFS')
    print(f'RMS: {20*np.log10(rms_pcm):.3f} dBFS')
    for segment in chapters:
        print(f"Chapter {segment['id']}: {segment['start']:.6f} s")
    print(f'Generated {output.relative_to(ROOT)} in {time.perf_counter()-started:.2f} s')


if __name__ == '__main__':
    main()
