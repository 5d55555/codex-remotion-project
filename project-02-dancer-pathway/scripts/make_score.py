#!/usr/bin/env python3
"""Deterministic, offline NumPy score. Run from any working directory."""
import json
import time
import wave
from pathlib import Path

import numpy as np

SR = 48000
ROOT = Path(__file__).resolve().parents[1]
SEED = 20261026


def main():
    started = time.perf_counter()
    rng = np.random.default_rng(SEED)
    timeline = json.loads((ROOT / 'src/timeline.json').read_text())
    fps, overlap = timeline['fps'], timeline['overlap']
    segments = timeline['segments']
    cursor = 0
    for i, segment in enumerate(segments):
        segment['start'] = (cursor - overlap * i) / fps
        segment['end'] = segment['start'] + segment['frames'] / fps
        cursor += segment['frames']
    duration = (cursor - overlap * (len(segments) - 1)) / fps
    n = round(duration * SR)
    if abs(n / SR - duration) > 1e-9:
        raise ValueError('Timeline duration is not representable at 48 kHz')
    mix = np.zeros((n, 2), dtype=np.float32)

    def add(sound, start, gain=1.0, pan=0.0):
        offset = round(start * SR)
        lo, hi = max(0, offset), min(n, offset + len(sound))
        if hi <= lo:
            return
        sound = sound[lo - offset:hi - offset]
        if sound.ndim == 1:
            mix[lo:hi, 0] += sound * (gain * np.sqrt((1 - pan) / 2))
            mix[lo:hi, 1] += sound * (gain * np.sqrt((1 + pan) / 2))
        else:
            mix[lo:hi] += sound * gain

    def bell(frequency, seconds=5.0):
        t = np.arange(round(seconds * SR), dtype=np.float64) / SR
        out = np.zeros(len(t))
        for ratio, amplitude, decay in [(1, 1, 1.6), (2.71, .28, .85),
                                         (4.13, .12, .55), (6.79, .045, .3)]:
            out += amplitude * np.sin(2 * np.pi * frequency * ratio * t) * np.exp(-t / decay)
        return (out * (1 - np.exp(-t / .012))).astype(np.float32)

    chapters = [s for s in segments if s['kind'] == 'chapter']
    # Dm, Bb, Gm, A: sustained through each chapter's figures.
    chords = [(38, 45, 50, 53), (34, 41, 46, 50),
              (31, 38, 43, 46), (33, 40, 45, 49)]
    boundaries = [0.0] + [s['start'] for s in chapters] + [duration]
    for region in range(len(boundaries) - 1):
        a = max(0, boundaries[region] - 1.5)
        b = min(duration, boundaries[region + 1] + 1.5)
        t = np.arange(round((b - a) * SR), dtype=np.float64) / SR
        absolute = t + a
        envelope = np.ones(len(t))
        if region:
            envelope *= np.sin(np.clip((absolute - a) / 3, 0, 1) * np.pi / 2) ** 2
        if region < len(boundaries) - 2:
            envelope *= np.cos(np.clip((absolute - (b - 3)) / 3, 0, 1) * np.pi / 2) ** 2
        envelope *= .8 + .12 * np.sin(2 * np.pi * .071 * absolute) + .08 * np.sin(2 * np.pi * .113 * absolute)
        chord = chords[max(0, region - 1) % len(chords)]
        for channel in range(2):
            pad = np.zeros(len(t))
            for midi in chord:
                frequency = 440 * 2 ** ((midi - 69) / 12)
                for detune in [-.004, 0, .004]:
                    f = frequency * (1 + detune + (channel * 2 - 1) * .0008)
                    phase = rng.uniform(0, 2 * np.pi)
                    # Apply a four-pole 310 Hz low-pass response to each
                    # harmonic directly; no Python per-sample filtering.
                    for harmonic in [1, 2, 3, 4]:
                        level = 1 / (harmonic ** 1.8 * np.sqrt(1 + (f * harmonic / 310) ** 8))
                        pad += level * np.sin(2 * np.pi * f * absolute * harmonic + phase)
            offset = round(a * SR)
            count = min(len(pad), n - offset)
            mix[offset:offset + count, channel] += (.045 * pad[:count] * envelope[:count]).astype(np.float32)

    pentatonic = [62, 65, 67, 69, 72]
    figure_index = 0
    logos = [s for s in segments if s['kind'] == 'logo']
    for segment in segments:
        start, end = segment['start'], segment['end']
        if segment['kind'] == 'chapter':
            t = np.arange(round(3.5 * SR)) / SR
            phase = 2 * np.pi * (39 * t + 24 * .12 * (1 - np.exp(-t / .12)))
            boom = np.sin(phase) * np.exp(-t / .65) * (1 - np.exp(-t / .008))
            add(boom.astype(np.float32), start, 2.6)
            t = np.arange(SR) / SR
            noise = rng.standard_normal(SR)
            spectrum = np.fft.rfft(noise)
            frequencies = np.fft.rfftfreq(SR, 1 / SR)
            spectrum *= 1 / np.sqrt(1 + (frequencies / 650) ** 6)
            swell = np.fft.irfft(spectrum, n=SR)
            swell /= np.sqrt(np.mean(swell ** 2))
            swell *= t ** 2 * np.sin(np.pi * t) ** .5
            add(swell.astype(np.float32), start - 1, .12)
        elif segment['kind'] == 'figure':
            midi = pentatonic[figure_index % len(pentatonic)]
            add(bell(440 * 2 ** ((midi - 69) / 12)), start, .11,
                .35 * (-1) ** figure_index)
            figure_index += 1
            # Suppress ticks wherever an overlapping chapter/logo is visible.
            for tick_time in np.arange(start + .5, end, 1.0):
                if any(s['start'] <= tick_time < s['end'] for s in segments
                       if s['kind'] != 'figure'):
                    continue
                t = np.arange(round(.045 * SR)) / SR
                tick = (rng.standard_normal(len(t)) * .35 +
                        np.sin(2 * np.pi * 2350 * t)) * np.exp(-t / .006)
                tick *= np.minimum(t / .0006, 1)
                add(tick.astype(np.float32), tick_time, .007, .2)
        elif segment['kind'] == 'logo':
            add(bell(293.6648, 6), start + .7, .075, -.3)
            add(bell(440, 5), start + 2.8, .045, .3)

    # Block FFT overlap-add convolution, keeping memory and runtime bounded.
    ir_length = round(2.5 * SR)
    ir_t = np.arange(ir_length) / SR
    block = 262144
    fft_size = 1 << (block + ir_length - 2).bit_length()
    for channel in range(2):
        ir = rng.standard_normal(ir_length) * np.exp(-ir_t / .48)
        ir *= np.minimum(ir_t / .018, 1)
        freq = np.fft.rfftfreq(ir_length, 1 / SR)
        ir = np.fft.irfft(np.fft.rfft(ir) / np.sqrt(1 + (freq / 2300) ** 4), n=ir_length)
        ir /= np.sqrt(np.sum(ir ** 2))
        ir[:round(.025 * SR)] = 0
        transfer = np.fft.rfft(ir, n=fft_size)
        wet = np.zeros(n, dtype=np.float32)
        for offset in range(0, n, block):
            dry = mix[offset:offset + block, channel]
            convolved = np.fft.irfft(np.fft.rfft(dry, n=fft_size) * transfer, n=fft_size)
            count = min(n - offset, len(dry) + ir_length - 1)
            wet[offset:offset + count] += convolved[:count].astype(np.float32)
        mix[:, channel] = .82 * mix[:, channel] + .18 * wet

    # Opening/closing logos contain pad and bells; smoothly clear preceding tails.
    fade_in = round(2 * SR)
    fade_out = round(3 * SR)
    mix[:fade_in] *= np.sin(np.linspace(0, np.pi / 2, fade_in))[:, None] ** 2
    mix[-fade_out:] *= np.sin(np.linspace(np.pi / 2, 0, fade_out))[:, None] ** 2
    mix -= np.mean(mix, axis=0)
    # Soft saturation controls crest factor while preserving quiet detail.
    # Solve simultaneously for -20 dB RMS and -1 dB peak.
    peak_target = 10 ** (-1 / 20)
    crest_target = peak_target / .1
    raw_peak = float(np.max(np.abs(mix)))
    probe = mix[::16].astype(np.float64)
    low, high = .0001, 100.0
    for _ in range(28):
        drive = (low + high) / 2
        rms = np.sqrt(np.mean(np.tanh(probe * drive) ** 2))
        crest = np.tanh(raw_peak * drive) / rms
        if crest > crest_target:
            low = drive
        else:
            high = drive
    np.tanh(mix * ((low + high) / 2), out=mix)
    mix *= peak_target / np.max(np.abs(mix))
    pcm = np.rint(mix * 32767).astype('<i2')
    output = ROOT / 'public/audio/score.wav'
    output.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(output), 'wb') as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(SR)
        wav.writeframes(pcm.tobytes())
    actual = pcm.astype(np.float64) / 32768
    print(f'Total duration: {n / SR:.6f} s ({n} samples)')
    print(f'Peak: {20 * np.log10(np.max(np.abs(actual))):.3f} dBFS')
    print(f'RMS: {20 * np.log10(np.sqrt(np.mean(actual ** 2))):.3f} dBFS')
    for kind in ['chapter', 'figure']:
        first = [f"{s['id']}={s['start']:.3f}s" for s in segments if s['kind'] == kind][:5]
        print(f'{kind} starts (first 5): ' + ', '.join(first))
    print(f'Generated {output.relative_to(ROOT)} in {time.perf_counter() - started:.2f} s')


if __name__ == '__main__':
    main()
