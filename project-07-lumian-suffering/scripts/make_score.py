#!/usr/bin/env python3
"""Deterministic anime-style underscore and frame-accurate cartoon Foley.

Only Python's standard library and numpy are required. All cues use timeline IDs
and local frames. Run from any directory; output is always public/audio/score.wav.
"""
import json
import math
from pathlib import Path
import wave

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SR = 48000
SEED = 721903
RNG = np.random.default_rng(SEED)
TAU = 2 * np.pi


def clock(duration):
    return np.arange(max(1, round(duration * SR)), dtype=np.float64) / SR


def envelope(x, attack=0.006, release=0.06):
    """Cosine edges on every sound, including noise and effect tails."""
    x = np.asarray(x, dtype=np.float64).copy()
    a = min(len(x), max(2, round(attack * SR)))
    r = min(len(x), max(2, round(release * SR)))
    x[:a] *= np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    x[-r:] *= np.sin(np.linspace(np.pi / 2, 0, r)) ** 2
    return x


def hz(midi):
    return 440 * 2 ** ((midi - 69) / 12)


def band_noise(duration, lo, hi):
    n = max(1, round(duration * SR))
    freqs = np.fft.rfftfreq(n, 1 / SR)
    spec = np.fft.rfft(RNG.standard_normal(n))
    # Smooth spectral skirts avoid the ringing of rectangular band limits.
    mask = (1 - np.exp(-(freqs / max(lo, 1)) ** 4)) * np.exp(-(freqs / hi) ** 6)
    x = np.fft.irfft(spec * mask, n)
    return x / max(np.std(x), 1e-9)


def instrument(midi, duration, kind='piano'):
    t = clock(duration)
    f = hz(midi)
    if kind == 'piano':
        x = sum(a * np.sin(TAU * f * h * (1 + 0.00017 * h * h) * t)
                * np.exp(-t * (1.8 + h * 0.9))
                for h, a in [(1, 1), (2, .44), (3, .20), (4, .10), (6, .035)])
        x += .018 * band_noise(duration, 700, 4200) * np.exp(-t * 65)
    elif kind == 'pizz':
        x = sum(np.sin(TAU * f * h * t) / h ** 1.5 for h in range(1, 8))
        x *= np.exp(-t * 11)
        x += .03 * band_noise(duration, 500, 3000) * np.exp(-t * 80)
    elif kind == 'bell':
        x = sum(a * np.sin(TAU * f * h * t) * np.exp(-t * d)
                for h, a, d in [(1, 1, 3), (2.76, .35, 5), (5.4, .11, 8)])
    elif kind in ('strings', 'tremolo'):
        phase = TAU * f * t + .024 * np.sin(TAU * 5.3 * t)
        x = sum((np.sin(h * phase + .13 * np.sin(TAU * .7 * t))
                 + .7 * np.sin(h * phase * 1.0019)) / h ** 1.65
                for h in range(1, 9)) / 1.8
        if kind == 'tremolo':
            x *= .45 + .55 * np.sin(TAU * 7.8 * t) ** 2
        return envelope(x * .75, min(.35, duration / 4), min(.6, duration / 3))
    elif kind == 'wood':
        x = (np.sin(TAU * f * t) + .5 * np.sin(TAU * f * 1.61 * t)) * np.exp(-t * 48)
    else:
        raise ValueError(kind)
    return envelope(x, .004, min(.14, duration / 3))


class Score:
    def __init__(self, timeline):
        assert timeline['fps'] == 30 and timeline['overlap'] == 0
        self.starts, self.frames = {}, {}
        total = 0
        for segment in timeline['segments']:
            sid = segment['id']
            if sid in self.starts:
                raise ValueError('Duplicate segment: ' + sid)
            self.starts[sid] = total
            self.frames[sid] = segment['frames']
            total += segment['frames']
        self.n = total * SR // 30
        assert total * SR % 30 == 0
        self.audio = np.zeros((self.n, 2), np.float32)

    def put(self, sid, frame, sound, gain=.15, pan=0, room=.12):
        assert 0 <= frame < self.frames[sid], (sid, frame)
        start = round((self.starts[sid] + frame) * SR / 30)
        # Equal-power dry pan, short stereo early reflections, no feedback.
        for delay, level, p in [(0, 1, pan), (.047, room, -.7),
                                 (.079, room * .75, .7), (.137, room * .36, -pan)]:
            pos = start + round(delay * SR)
            n = min(len(sound), self.n - pos)
            if n <= 0:
                continue
            g = gain * level * np.array([math.cos((p + 1) * np.pi / 4),
                                        math.sin((p + 1) * np.pi / 4)])
            self.audio[pos:pos + n] += (sound[:n, None] * g).astype(np.float32)

    def note(self, sid, frame, midi, duration, kind='piano', gain=.12, pan=0):
        self.put(sid, frame, instrument(midi, duration, kind), gain, pan)

    def chord(self, sid, frame, notes, duration, kind='strings', gain=.08):
        for i, note in enumerate(notes):
            self.note(sid, frame, note, duration, kind, gain / len(notes) ** .55,
                      (i / max(1, len(notes) - 1) - .5) * .9)

    def fx(self, sid, frame, kind, gain=.22, duration=None):
        d = duration or {'page': .42, 'stamp': .30, 'mark': .27,
            'hit': 1.65, 'thunder': 2.7, 'soft': .8, 'roll': .65,
            'up': .45, 'down': .65, 'alarm': .68, 'sparkle': 1.35,
            'crackle': 1.0, 'rustle': .6, 'fart': .38, 'boing': .9,
            'trombone': 2.0, 'gulp': .65, 'gurgle': 1.3,
            'sting': .8, 'wind': 3.7, 'riser': 2.0, 'tick': .09}[kind]
        t = clock(d)
        if kind in ('page', 'rustle'):
            noise = band_noise(d, 600, 7500)
            sweep = np.sin(np.pi * t / d) ** 1.6
            x = noise * sweep * (.45 + .55 * np.sin(TAU * (30 * t + 110 * t * t)) ** 2)
        elif kind == 'mark':
            x = .5 * band_noise(d, 1600, 6000) * np.exp(-t * 22)
            x += .30 * np.sin(TAU * (2100 * t - 1800 * t * t)) * np.exp(-t * 29)
            u = np.maximum(t - .15, 0)
            x += (t >= .15) * (.8 * np.sin(TAU * 570 * u)
                                + .2 * band_noise(d, 300, 2400)) * np.exp(-u * 65)
        elif kind in ('hit', 'thunder', 'soft', 'stamp'):
            decay = {'hit': 4, 'thunder': 2.3, 'soft': 7, 'stamp': 23}[kind]
            base = 48 if kind == 'thunder' else 65 if kind == 'hit' else 100
            phase = TAU * (base * t + 70 * (1 - np.exp(-t * 25)) / 25)
            x = np.sin(phase) * np.exp(-t * decay)
            x += .35 * band_noise(d, 40, 1600) * np.exp(-t * decay * 1.7)
            if kind in ('hit', 'thunder'):
                x += .34 * band_noise(d, 2200, 13000) * np.exp(-t * 3.3)
            if kind == 'thunder':
                x += .55 * band_noise(d, 90, 5000) * np.exp(-t * 2) * (.5 + .5 * np.sin(25 * t) ** 2)
        elif kind == 'roll':
            x = band_noise(d, 800, 10000) * (.15 + .85 * (t / d))
            x *= (.15 + .85 * np.maximum(0, np.sin(TAU * 23 * t)) ** 6)
        elif kind in ('up', 'down'):
            f0, f1 = (650, 2100) if kind == 'up' else (1800, 400)
            phase = TAU * (f0 * t + .5 * (f1 - f0) / d * t * t)
            x = .8 * np.sin(phase + .09 * np.sin(TAU * 7 * t))
        elif kind == 'alarm':
            x = (np.sin(TAU * 1660 * t) + .5 * np.sin(TAU * 2230 * t))
            x *= .25 + .75 * np.sin(TAU * 13 * t) ** 2
        elif kind == 'sparkle':
            x = np.zeros_like(t)
            for k, f in enumerate([880, 1320, 1760, 2640, 3520, 4400]):
                u = np.maximum(t - k * .075, 0)
                x += .35 * (t >= k * .075) * np.sin(TAU * f * u) * (1 - np.exp(-u * 150)) * np.exp(-u * 5)
        elif kind == 'crackle':
            x = np.zeros_like(t)
            for _ in range(24):
                onset = RNG.uniform(0, d - .04)
                u = np.maximum(t - onset, 0)
                x += (t >= onset) * RNG.uniform(.1, .6) * band_noise(d, 1000, 11000) * np.exp(-u * 150)
        elif kind == 'fart':
            phase = TAU * (125 * t - 90 * t * t)
            x = np.tanh(3 * np.sin(phase + 2 * np.sin(TAU * 28 * t))) * np.exp(-t * 8)
        elif kind == 'boing':
            f = 240 + 420 * np.exp(-t * 4) * np.cos(TAU * 8 * t)
            phase = TAU * np.cumsum(f) / SR
            x = (np.sin(phase) + .22 * np.sin(phase * 2)) * np.exp(-t * 3.8)
        elif kind == 'trombone':
            # Four voiced wah syllables, falling in pitch, with a final long tail.
            f = 220 * 2 ** (-7 * t / d / 12)
            phase = TAU * np.cumsum(f) / SR
            mouth = .5 + .5 * np.sin(TAU * 2.3 * t + .5)
            x = sum(np.sin(h * phase) / h * (.15 + mouth ** (h / 2)) for h in range(1, 9))
            x *= np.exp(-t * .55) * (.4 + .6 * mouth)
        elif kind in ('gulp', 'gurgle'):
            f = 110 + 150 * np.sin(TAU * (3.5 * t + .9 * t * t))
            phase = TAU * np.cumsum(f) / SR
            bubbles = (.2 + .8 * np.sin(TAU * 5 * t) ** 6)
            x = (np.sin(phase) + .35 * np.sin(phase * 2.7)) * bubbles
            x += .16 * band_noise(d, 180, 1100) * bubbles
        elif kind == 'sting':
            x = sum(np.sin(TAU * hz(m) * t) for m in [66, 67, 72, 78]) / 2
            x *= np.exp(-t * 4)
        elif kind == 'wind':
            x = band_noise(d, 180, 5200) * np.sin(np.pi * t / d) ** 1.5
            x *= .5 + .3 * np.sin(TAU * .8 * t) + .2 * np.sin(TAU * 3.1 * t)
        elif kind == 'riser':
            phase = TAU * (140 * t + 100 * t * t + 40 * t ** 3)
            x = (.6 * np.sin(phase) + .3 * np.sin(phase * 1.03)
                 + .4 * band_noise(d, 500, 8500)) * (t / d) ** 1.4
        elif kind == 'tick':
            x = (np.sin(TAU * 1900 * t) + .5 * np.sin(TAU * 3100 * t)) * np.exp(-t * 90)
        self.put(sid, frame, envelope(x, .004 if kind != 'wind' else .15,
                                    min(.12, d / 4)), gain, room=.07)

    def bed(self, sid, first, last, mood):
        """Four-bar phrases; motifs return in minor, major and sparse variations."""
        end = min(last, self.frames[sid])
        upbeat = mood in ('play', 'hope')
        step = {'play': 9, 'sad': 30, 'tense': 12, 'dark': 24,
                'mystery': 18, 'hope': 15}[mood]
        minor = mood != 'hope'
        chords = ([48, 51, 55], [44, 48, 51], [46, 50, 53], [43, 47, 50]) if minor else (
            [48, 52, 55], [45, 48, 52], [41, 45, 48], [43, 47, 50])
        motif = [0, 7, 12, 10, 7, 3, 5, 7, 0, 3, 7, 14, 12, 7, 5, 3]
        if mood == 'hope':
            motif = [0, 4, 7, 12, 11, 7, 9, 7, 4, 7, 12, 16, 14, 12, 7, 4]
        for j, frame in enumerate(range(first, end, step)):
            chord = chords[(j // 8) % 4]
            remain = (end - frame) / 30
            if upbeat:
                pitch = chord[0] + 12 + motif[j % 16]
                self.note(sid, frame, pitch, min(.65, remain), 'pizz', .105, -.30)
                if j % 2 == 0:
                    self.note(sid, frame, pitch + 12, min(.8, remain), 'bell', .048, .45)
                if j % 4 == 0:
                    self.note(sid, frame, chord[0], min(.7, remain), 'piano', .14, -.15)
                if mood == 'play' and j % 2 == 1:
                    self.note(sid, frame, 81 if j % 4 == 1 else 76, min(.12, remain), 'wood', .045, .25)
                if mood == 'hope' and j % 8 == 0:
                    self.chord(sid, frame, chord, min(step * 8 / 30, remain), 'strings', .12)
            elif mood == 'sad':
                self.note(sid, frame, chord[j % 3] + 12, min(2.7, remain), 'piano', .14, -.25 + .15 * (j % 4))
                if j % 4 == 0:
                    self.chord(sid, frame, chord, min(4, remain), 'strings', .09)
                    self.note(sid, frame, chord[0] - 12, min(3, remain), 'piano', .12)
            elif mood == 'tense':
                self.note(sid, frame, chord[j % 3], min(.55, remain), 'tremolo', .09, -.25)
                if j % 2 == 0:
                    self.fx(sid, frame, 'soft', .12, min(.55, remain))
                if j % 8 == 4:
                    self.note(sid, frame, 43, min(.7, remain), 'piano', .12)
            else:
                if j % 4 == 0:
                    self.chord(sid, frame, [chord[0] - 12, chord[0], chord[1]],
                               min(3.6, remain), 'strings', .12)
                if mood == 'mystery':
                    self.note(sid, frame, chord[j % 3], min(1.3, remain), 'piano', .11)
                    self.fx(sid, frame, 'tick', .035)
                elif j % 2 == 0:
                    self.note(sid, frame, chord[1], min(1.8, remain), 'piano', .065)


def arrange(s):
    # The page turn is tied to every segment boundary except the cover.
    for sid in s.starts:
        if sid != 'cover':
            s.fx(sid, 0, 'page', .16)
    beds = {
        'cover': [(0, 150, 'play')],
        'p1': [(10, 240, 'play'), (240, 380, 'tense'), (380, 500, 'dark'), (500, 660, 'sad')],
        'p2': [(0, 660, 'sad')],
        'p3': [(0, 380, 'tense'), (380, 600, 'dark')],
        'p4': [(0, 250, 'play'), (300, 390, 'hope')],
        'p5': [(0, 720, 'mystery')],
        'p6': [(120, 280, 'tense'), (300, 470, 'play'), (470, 720, 'sad')],
        'p7': [(0, 200, 'sad'), (320, 420, 'play')],
        'p8': [(0, 190, 'sad'), (190, 380, 'dark'), (400, 540, 'mystery'), (600, 780, 'sad')],
        'end': [(0, 300, 'hope')],
        'credits': [(0, 330, 'play')],
    }
    for sid, sections in beds.items():
        for start, end, mood in sections:
            s.bed(sid, start, end, mood)

    cues = {
        'cover': [(4, 'roll', .16), (4, 'hit', .14), (40, 'stamp', .3)],
        'p1': [(110, 'up', .19), (200, 'sparkle', .18), (200, 'roll', .14),
               (262, 'hit', .43), (470, 'thunder', .55)],
        'p2': [(40, 'soft', .19), (120, 'crackle', .19), (130, 'hit', .26),
               (210, 'rustle', .14), (330, 'sparkle', .17), (450, 'alarm', .12)],
        'p3': [(60, 'hit', .4), (170, 'alarm', .17), (220, 'thunder', .45)],
        'p4': [(40, 'fart', .25), (110, 'stamp', .19), (250, 'hit', .4), (250, 'sting', .19)],
        'p5': [(60, 'sparkle', .13), (150, 'hit', .27), (260, 'sting', .25)],
        'p6': [(100, 'gulp', .26), (120, 'hit', .5), (280, 'page', .18),
               (300, 'boing', .25), (300, 'sting', .19), (360, 'sparkle', .14),
               (380, 'stamp', .3), (380, 'trombone', .21)],
        'p7': [(250, 'up', .15), (270, 'down', .16)],
        'p8': [(190, 'wind', .27), (380, 'gurgle', .29), (540, 'riser', .22), (560, 'sting', .23)],
        'end': [(10, 'stamp', .26)],
    }
    for sid, events in cues.items():
        for frame, kind, gain in events:
            s.fx(sid, frame, kind, gain)
    marks = {'p1': [262, 472], 'p2': [130, 340, 430], 'p3': [60, 220, 400],
             'p4': [250], 'p5': [150, 270], 'p6': [300], 'p7': [250],
             'p8': [200, 380, 560]}
    for sid, frames in marks.items():
        for frame in frames:
            s.fx(sid, frame, 'mark', .23)
    # Specific sustained, dramatic and comedy phrases.
    for f in range(384, 600, 24):
        s.fx('p3', f, 'soft', .075, .7)
    s.chord('p2', 500, [45, 52, 60, 64], 5.2, 'piano', .24)
    for f in range(420, 450, 5):
        s.fx('p2', f, 'tick', .085)
    s.chord('p5', 400, [31, 43, 50], 4.0, 'strings', .22)
    s.chord('p5', 490, [36, 43, 48, 51, 58, 63], 3.0, 'strings', .36)
    s.fx('p5', 490, 'hit', .32)
    # The long snare build ends precisely on the gulp setup.
    for f in range(0, 100, 5):
        s.fx('p6', f, 'roll', .035 + .12 * (f / 100) ** 1.5, .19)
    s.chord('p6', 170, [48, 55, 60, 64, 67], 1.2, 'strings', .24)
    for j, f in enumerate(range(250, 311, 6)):
        s.note('p7', f, [72, 79][j % 2], .19, 'pizz', .18, -.65 if j % 2 == 0 else .65)
    s.chord('p8', 30, [45, 52, 60, 64], 3.8, 'piano', .21)
    # Separate rising strings add harmonic pressure over the continuous riser.
    for j, f in enumerate(range(540, 600, 6)):
        s.chord('p8', f, [55 + j, 61 + j], .45, 'tremolo', .06 + .012 * j)
    # End-card crescendo and a deliberate final resolution.
    for pitch in [48, 55, 60, 64]:
        swell = instrument(pitch, 6.3, 'strings')
        swell *= np.linspace(.15, 1, len(swell))
        s.put('end', 80, swell, .12)
    s.chord('end', 240, [48, 52, 55, 60], 2.0, 'piano', .22)
    # Credits repeat the cover motif at half speed in the piano; taper the entire page.
    for j, offset in enumerate([0, 7, 12, 10, 7, 3, 5, 0]):
        s.note('credits', j * 36, 60 + offset, 1.7, 'piano', .12)
    begin = round(s.starts['credits'] * SR / 30)
    s.audio[begin:] *= np.linspace(1, .12, s.n - begin, dtype=np.float32)[:, None]


def lowpass(audio):
    """15 kHz, 257-tap windowed-sinc FIR; overlap-add, zero-phase alignment.

    Applied AFTER tanh and BEFORE final peak normalization. The smooth transition
    removes saturation harmonics and provides headroom for later AAC encoding.
    """
    taps = 257
    k = np.arange(taps) - (taps - 1) / 2
    h = 2 * 15000 / SR * np.sinc(2 * 15000 / SR * k) * np.kaiser(taps, 9)
    h /= h.sum()
    block, fftsize = 32768, 65536
    response = np.fft.rfft(h, fftsize)
    out = np.zeros((len(audio) + taps - 1, 2), np.float32)
    for start in range(0, len(audio), block):
        chunk = audio[start:start + block]
        z = np.fft.irfft(np.fft.rfft(chunk, fftsize, axis=0) * response[:, None],
                         fftsize, axis=0)
        size = len(chunk) + taps - 1
        out[start:start + size] += z[:size].astype(np.float32)
    delay = (taps - 1) // 2
    return out[delay:delay + len(audio)].copy()


def master(audio):
    # Final one-second fade is included in all loudness measurements.
    audio[-SR:] *= np.linspace(1, 0, SR, dtype=np.float32)[:, None]
    audio[:480] *= np.linspace(0, 1, 480, dtype=np.float32)[:, None]
    target_peak = 10 ** (-1 / 20)
    target_rms = 10 ** (-17 / 20)
    # Solve soft-saturation drive for a 16 dB crest factor. A deterministic
    # decimated probe makes the search fast; final metrics use every sample.
    probe = audio[::13].astype(np.float64)
    dry_peak = float(np.max(np.abs(audio)))
    desired_ratio = target_rms / target_peak
    probe_ratio = desired_ratio
    result = None
    # Calibrate against the full filtered waveform: filter/true sample peaks
    # can differ from the decimated probe, especially on short noise transients.
    for _ in range(4):
        lo, hi = .001, 1000.0
        for _ in range(34):
            drive = (lo + hi) / 2
            y = np.tanh(probe * drive)
            ratio = np.sqrt(np.mean(y * y)) / np.tanh(dry_peak * drive)
            if ratio < probe_ratio:
                lo = drive
            else:
                hi = drive
        result = lowpass(np.tanh(audio * ((lo + hi) / 2)))
        actual_ratio = float(np.sqrt(np.mean(result.astype(np.float64) ** 2))) / float(np.max(np.abs(result)))
        if abs(20 * np.log10(actual_ratio / desired_ratio)) < .025:
            break
        probe_ratio *= desired_ratio / actual_ratio
    audio = result
    audio *= target_peak / float(np.max(np.abs(audio)))
    return audio


def main():
    timeline = json.loads((ROOT / 'src/timeline.json').read_text())
    score = Score(timeline)
    arrange(score)
    audio = master(score.audio)
    pcm = np.rint(audio * 32767).astype('<i2')
    path = ROOT / 'public/audio/score.wav'
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), 'wb') as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(SR)
        wav.writeframes(pcm.tobytes())
    # Report quantized, actual output rather than an intermediate float buffer.
    measured = pcm.astype(np.float64) / 32768
    peak = 20 * np.log10(np.max(np.abs(measured)))
    rms = 20 * np.log10(np.sqrt(np.mean(measured ** 2)))
    with wave.open(str(path), 'rb') as wav:
        assert wav.getnframes() == score.n
        assert wav.getnchannels() == 2 and wav.getsampwidth() == 2
        assert wav.getframerate() == SR
    assert abs(peak + 1) < .01, peak
    assert abs(rms + 17) < .1, rms
    assert np.isfinite(audio).all()
    print(f'Output: {path}')
    print(f'Duration: {score.n / SR:.6f} s ({sum(score.frames.values())} frames, {score.n} samples/channel)')
    print(f'Format: {SR} Hz, 16-bit PCM, stereo; seed={SEED}')
    print(f'Peak: {peak:.3f} dBFS; RMS: {rms:.3f} dBFS')
    print('Segment starts:')
    for sid, frame in score.starts.items():
        print(f'  {sid:8s} {frame / 30:10.6f} s (frame {frame})')


if __name__ == '__main__':
    main()
