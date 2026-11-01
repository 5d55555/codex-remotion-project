"""程序合成全片音效，按 out/cues.json 的时间排成一条音效轨 out/sfx.wav（48 kHz 立体声）。

每种音效是几层振荡器 / 噪声 + 包络 + 滤波 + 失真 + 混响的组合（配方见 notes/research/L4_audio.md）。
用法：python3 scripts/sfx.py [总秒数]
"""
import json, os, sys
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve
import soundfile as sf

SR = 48000
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
rng = np.random.default_rng(7)


def T(d):
    return np.arange(int(d * SR)) / SR


def env(n, a=0.005, d=0.2, curve=4.0):
    """起音 a 秒线性上升，之后指数衰减，d 是衰减到约 2% 的时间。"""
    t = np.arange(n) / SR
    e = np.where(t < a, t / max(a, 1e-6), np.exp(-curve * (t - a) / max(d, 1e-6)))
    return e


def env_ar(n, a, r):
    t = np.arange(n) / SR
    up = np.clip(t / max(a, 1e-6), 0, 1)
    down = np.clip((n / SR - t) / max(r, 1e-6), 0, 1)
    return up * down


def noise(n, seed=None):
    g = np.random.default_rng(seed) if seed is not None else rng
    return g.standard_normal(n)


def filt(x, kind, f, order=4):
    if kind == 'bp':
        sos = butter(order, [max(20, f[0]), min(SR / 2 - 100, f[1])], btype='bandpass', fs=SR, output='sos')
    else:
        sos = butter(order, min(SR / 2 - 100, max(20, f)), btype='lowpass' if kind == 'lp' else 'highpass', fs=SR, output='sos')
    return sosfilt(sos, x)


def sweep(f0, f1, d, shape='exp'):
    t = T(d)
    if shape == 'exp':
        f = f0 * (f1 / f0) ** (t / d)
    else:
        f = f0 + (f1 - f0) * t / d
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def band_sweep(d, f0, f1, width=0.5, seed=None, steps=24):
    """噪声的带通中心频率从 f0 扫到 f1（分段滤波后交叉淡化）。"""
    n = int(d * SR)
    x = noise(n, seed)
    out = np.zeros(n)
    seg = n // steps + 1
    for i in range(steps):
        fc = f0 * (f1 / f0) ** (i / max(1, steps - 1))
        y = filt(x, 'bp', (fc * (1 - width / 2), fc * (1 + width / 2)), 2)
        w = np.zeros(n)
        a, b = max(0, (i - 1) * seg), min(n, (i + 2) * seg)
        c = i * seg + seg // 2
        idx = np.arange(a, b)
        w[a:b] = np.clip(1 - np.abs(idx - c) / (seg * 1.2), 0, 1)
        out += y * w
    return out


def sat(x, drive=2.0):
    return np.tanh(drive * x) / np.tanh(drive)


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def ir(sec=1.2, decay=3.5, seed=3, bright=6000):
    t = T(sec)
    h = noise(len(t), seed) * np.exp(-decay * t)
    h = filt(h, 'lp', bright, 2)
    h[0] = 1.0
    return h / np.max(np.abs(h))


IR_ROOM, IR_HALL, IR_BIG = ir(0.6, 6, 1), ir(1.8, 2.6, 2, 5000), ir(3.5, 1.4, 3, 3500)


def verb(x, wet=0.2, h=None, tail=0):
    h = IR_HALL if h is None else h
    y = fftconvolve(np.concatenate([x, np.zeros(int(tail * SR))]), h)[: len(x) + int(tail * SR)]
    y = y / (np.max(np.abs(y)) + 1e-9) * np.max(np.abs(x))
    dry = np.concatenate([x, np.zeros(int(tail * SR))])
    return dry * (1 - wet) + y * wet


def pad(x, d):
    n = int(d * SR)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def mix(*layers):
    n = max(len(l) for l in layers)
    out = np.zeros(n)
    for l in layers:
        out[: len(l)] += l
    return out


def thump(f0=90, f1=40, d=0.3, a=0.002):
    return sweep(f0, f1, d) * env(int(d * SR), a, d * 0.8)


def crack(d=0.03, lo=2500, hi=9000, seed=None):
    n = int(d * SR)
    return filt(noise(n, seed), 'bp', (lo, hi), 2) * env(n, 0.0005, d)


def burst(d=0.15, lo=200, hi=1500, seed=None, a=0.002):
    n = int(d * SR)
    return filt(noise(n, seed), 'bp', (lo, hi), 2) * env(n, a, d)


def partials(base, ratios, amps, d, decay, detune=0.0, seed=None):
    t = T(d)
    g = np.random.default_rng(seed)
    out = np.zeros(len(t))
    for r, a in zip(ratios, amps):
        f = base * r * (1 + detune * (g.random() - 0.5))
        out += a * np.sin(2 * np.pi * f * t + g.random() * 6.28) * np.exp(-t * decay * (0.6 + r * 0.4))
    return out


def clicks(d, n_clicks, lo, hi, seed=None, dur=0.004):
    g = np.random.default_rng(seed)
    out = np.zeros(int(d * SR))
    for _ in range(n_clicks):
        p = int(g.random() * (len(out) - int(dur * SR) - 1))
        c = crack(dur * (0.5 + g.random()), lo, hi, int(g.integers(1e6))) * (0.3 + g.random())
        out[p : p + len(c)] += c
    return out


# ---------------- 配方 ----------------

def s_wind():
    n = int(4 * SR)
    x = filt(noise(n, 1), 'bp', (250, 2400), 2)
    am = 0.6 + 0.4 * np.sin(2 * np.pi * 0.35 * T(4) + 1) * np.sin(2 * np.pi * 0.13 * T(4))
    return x * am * env_ar(n, 1.0, 1.5)


def s_swell():
    t = T(3.2)
    x = 0.6 * np.sin(2 * np.pi * 55 * t) + 0.4 * np.sin(2 * np.pi * 82.5 * t + 1) + 0.3 * filt(noise(len(t), 2), 'lp', 300)
    return x * env_ar(len(t), 1.8, 1.2)


def s_pulse(red=False):
    x = thump(62, 34, 0.7, 0.01)
    if red:
        t = T(0.7)
        saw = filt(2 * ((70 * t) % 1) - 1, 'lp', 500) * env(len(t), 0.01, 0.6)
        x = mix(x, 0.4 * saw)
    return x


def s_whoosh(d=0.55, f0=400, f1=4000):
    n = int(d * SR)
    return band_sweep(d, f0, f1, 0.8, 3) * env_ar(n, d * 0.55, d * 0.45)


def s_curtain():
    d = 2.4
    n = int(d * SR)
    x = band_sweep(d, 3000, 250, 0.9, 4) * env_ar(n, 0.4, 1.6)
    low = sweep(85, 45, d) * env_ar(n, 0.3, 1.5) * 0.5
    return verb(mix(x, low), 0.3, IR_HALL)


def s_bell(base=220, d=2.6, wet=0.35):
    x = partials(base, [1, 2.01, 2.74, 3.91, 5.13, 6.8], [1, 0.55, 0.3, 0.18, 0.1, 0.05], d, 1.6, 0.004, 5)
    x *= env_ar(len(x), 0.003, 0.2)
    return verb(x, wet, IR_HALL)


def s_charge(d=1.2, f0=200, f1=900):
    n = int(d * SR)
    tone = sweep(f0, f1, d) * 0.5 + sweep(f0 * 1.5, f1 * 1.5, d) * 0.25
    x = mix(tone, band_sweep(d, 400, 5000, 0.6, 6) * 0.6) * env_ar(n, d * 0.8, 0.12)
    return sat(x, 1.8)


def s_chant(low=False):
    d = 1.8
    t = T(d)
    f = (73.4, 110) if low else (98, 147)
    saw = sum(a * (2 * ((ff * (1 + dt) * t) % 1) - 1) for ff, a, dt in [(f[0], 0.6, 0), (f[0], 0.4, 0.006), (f[1], 0.35, -0.004)])
    x = filt(saw, 'lp', 520 if low else 700)
    breath = filt(noise(len(t), 8), 'bp', (500, 1600), 2) * 0.25 * (0.6 + 0.4 * np.sin(2 * np.pi * 3 * t))
    return verb((x + breath) * env_ar(len(t), 0.25, 0.8), 0.3)


def s_redin():
    d = 0.6
    n = int(d * SR)
    return mix(sweep(1200, 2600, d) * 0.4, filt(noise(n, 9), 'hp', 3000) * 0.3) * env_ar(n, 0.2, 0.3)


def s_contact():
    return mix(clicks(0.8, 30, 2000, 9000, 10) * 0.6, thump(110, 50, 0.4) * 0.8, burst(0.3, 300, 2500, 11) * 0.4)


def s_riser(d=3.2):
    n = int(d * SR)
    k = (np.arange(n) / n) ** 2.2
    x = band_sweep(d, 200, 7000, 0.7, 12) * k + sweep(100, 900, d) * 0.3 * k
    return x


def s_lock():
    return mix(crack(0.01, 1500, 6000, 13), partials(1760, [1, 2.4], [0.4, 0.2], 0.5, 9, 0, 14))


def s_suck(d=1.6):
    n = int(d * SR)
    k = (np.arange(n) / n) ** 3
    x = filt(noise(n, 15), 'hp', 900) * k + sweep(400, 60, d) * 0.3 * k
    return x * env_ar(n, 0.01, 0.02)


def s_impact_big(seed=16, sub=(70, 28), boom=0.7, tail=2.5, crackle=True):
    sub_l = thump(sub[0], sub[1], boom, 0.002) * 1.0
    b = burst(0.35, 60, 9000, seed, 0.001) * 0.9
    mid = burst(0.25, 300, 2500, seed + 1) * 0.6
    cr = clicks(1.2, 60, 2000, 9000, seed + 2) * 0.35 if crackle else np.zeros(1)
    x = mix(sub_l, b, mid, cr)
    return sat(verb(x, 0.35, IR_BIG, tail), 1.6)


def s_subdrop(d=1.3):
    return thump(68, 24, d, 0.008)


def s_collapse():
    d = 2.8
    n = int(d * SR)
    rumble = filt(noise(n, 17), 'lp', 180) * env(n, 0.05, 2.4) * 1.2
    deb = clicks(d, 80, 300, 3000, 18, 0.01) * np.linspace(1, 0.2, n)
    return verb(mix(rumble, deb * 0.8, thump(90, 40, 0.5) * 0.7), 0.25, IR_HALL)


def s_rct():
    t = T(1.3)
    x = sum(np.sin(2 * np.pi * f * t + i) for i, f in enumerate([2093, 2637, 3136, 3951])) / 4
    x *= (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t)) * env_ar(len(t), 0.15, 0.8)
    return verb(mix(x * 0.5, filt(noise(len(t), 19), 'hp', 6000) * 0.15 * env_ar(len(t), 0.1, 1)), 0.4)


def s_punch(scale=1.0, seed=20):
    x = mix(thump(95, 42, 0.22 * scale + 0.08) * 1.0, burst(0.1, 250, 1300, seed) * 0.8, crack(0.025, 2500, 7000, seed + 1) * 0.6)
    return sat(verb(x, 0.08, IR_ROOM), 2.0)


def s_slash(seed=22):
    d = 0.3
    n = int(d * SR)
    x = band_sweep(d, 2200, 8000, 0.5, seed) * env(n, 0.003, 0.22)
    whistle = np.sin(2 * np.pi * (5800 + 600 * T(d) / d) * T(d)) * env(n, 0.002, 0.15) * 0.15
    return verb(filt(mix(x, whistle), 'hp', 1200), 0.08, IR_ROOM)


def s_step():
    return mix(thump(120, 70, 0.08) * 0.6, burst(0.08, 800, 3000, 23) * 0.3)


def s_block():
    res = filt(noise(int(0.25 * SR), 24), 'bp', (1300, 1700), 4) * env(int(0.25 * SR), 0.001, 0.15)
    return mix(thump(110, 60, 0.2) * 0.8, res * 1.2, crack(0.02, 2000, 6000, 25) * 0.5)


def s_run():
    out = np.zeros(int(1.0 * SR))
    for i in range(5):
        st = s_step() * (0.7 + 0.3 * (i % 2))
        p = int(i * 0.2 * SR)
        out[p : p + len(st)] += st[: len(out) - p]
    return out


def s_blue_pull(d=1.3):
    n = int(d * SR)
    x = band_sweep(d, 3000, 350, 0.7, 26) * np.linspace(0.3, 1, n) + np.sin(2 * np.pi * 70 * T(d)) * 0.3
    return x * env_ar(n, 0.3, 0.2)


def s_blue_hum():
    t = T(2.4)
    x = np.sin(2 * np.pi * 55 * t) * 0.6 + np.sin(2 * np.pi * 56.5 * t) * 0.4 + filt(noise(len(t), 27), 'bp', (2000, 6000), 2) * 0.08
    return x * env_ar(len(t), 0.6, 0.8)


def s_black_flash():
    x = mix(crack(0.012, 500, 12000, 28) * 1.5, thump(85, 38, 0.45) * 1.0,
            clicks(0.5, 40, 2000, 9000, 29, 0.006) * 0.7 * np.linspace(1, 0, int(0.5 * SR)),
            burst(0.12, 200, 2000, 30) * 0.7)
    return sat(verb(x, 0.15, IR_ROOM), 2.4)


def s_slide():
    d = 0.7
    n = int(d * SR)
    x = filt(noise(n, 31), 'bp', (700, 3200), 2) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 23 * T(d)))) * env(n, 0.01, 0.6)
    return x


def s_sign():
    return mix(burst(0.06, 1000, 4000, 32) * 0.7, thump(150, 90, 0.08) * 0.4)


def s_eyes():
    return verb(partials(2637, [1, 1.5, 2.01], [0.5, 0.3, 0.2], 0.6, 6, 0, 33), 0.4)


def s_domain_void():
    d = 3.2
    n = int(d * SR)
    boom = thump(60, 28, 0.9) * 1.0
    inward = band_sweep(d, 6000, 300, 0.8, 34) * env_ar(n, 0.05, 2.5) * 0.6
    t = T(d)
    sparkle = sum(np.sin(2 * np.pi * f * t) * np.exp(-t * 0.8) for f in [1567, 2093, 2349, 3135, 4186]) * 0.08
    return verb(mix(boom, inward, sparkle), 0.45, IR_BIG, 1.0)


def s_domain_shrine():
    d = 3.2
    n = int(d * SR)
    t = T(d)
    creak = partials(62, [1, 1.47, 2.13, 3.3, 4.7], [1, 0.6, 0.4, 0.25, 0.15], d, 0.9, 0.02, 35)
    creak *= (0.7 + 0.3 * np.sign(np.sin(2 * np.pi * 11 * t)))  # 粗糙的调幅
    scrape = filt(noise(n, 36), 'bp', (1800, 4500), 2) * env_ar(n, 0.4, 1.8) * 0.2
    return verb(mix(thump(55, 30, 1.0), creak * 0.5, scrape), 0.4, IR_BIG, 1.0)


def s_slash_rain():
    d = 1.6
    out = np.zeros(int(d * SR))
    g = np.random.default_rng(37)
    for i in range(14):
        s = s_slash(40 + i) * (0.4 + 0.5 * g.random())
        p = int(g.random() * (len(out) - len(s)))
        out[p : p + len(s)] += s
    return out


def s_glass(big=False, seed=38):
    d = 2.2 if big else 1.1
    n = int(d * SR)
    g = np.random.default_rng(seed)
    t = T(d)
    tinkle = np.zeros(n)
    for _ in range(40 if big else 22):
        f = 900 + g.random() * 7000
        st0 = int(g.random() * n * 0.6)
        dec = 4 + g.random() * 12
        seg = np.sin(2 * np.pi * f * t[: n - st0]) * np.exp(-dec * t[: n - st0]) * (0.2 + g.random() * 0.4)
        tinkle[st0:] += seg
    x = mix(crack(0.03, 1500, 12000, seed) * 1.2, tinkle * 0.5, clicks(d, 50, 3000, 11000, seed + 1) * 0.4)
    if big:
        x = mix(x, thump(70, 30, 0.8) * 1.0)
    return verb(x, 0.3, IR_HALL)


def s_glass_crack():
    return mix(clicks(0.7, 45, 1500, 9000, 39, 0.006) * 0.8, crack(0.02, 800, 6000, 40), thump(130, 60, 0.2) * 0.5)


def s_thud():
    return thump(80, 40, 0.4) * 0.9


def s_wheel_appear():
    d = 1.4
    x = partials(140, [1, 2.76, 5.4, 8.9], [1, 0.5, 0.3, 0.15], d, 1.2, 0.01, 41) * env_ar(int(d * SR), 0.4, 0.6)
    return verb(x, 0.4)


def s_wheel():
    d = 2.0
    hit = partials(118, [1, 2.43, 3.65, 5.0, 7.1], [1, 0.7, 0.5, 0.3, 0.2], d, 2.4, 0.01, 42)
    bell = partials(296, [1, 2.01, 2.74, 3.91], [0.6, 0.35, 0.2, 0.1], d, 1.1, 0.003, 43)
    x = mix(hit, bell * 0.6, crack(0.015, 1000, 6000, 44) * 0.6)
    return verb(x, 0.35, IR_HALL)


def s_rise():
    d = 1.5
    n = int(d * SR)
    return mix(band_sweep(d, 120, 1200, 0.8, 45) * np.linspace(0, 1, n), sweep(40, 80, d) * 0.5 * np.linspace(0, 1, n)) * env_ar(n, 0.2, 0.1)


def s_sword():
    d = 0.6
    n = int(d * SR)
    w = band_sweep(d, 300, 2500, 0.8, 46) * env_ar(n, 0.25, 0.3)
    ring = partials(820, [1, 2.7, 4.2], [0.3, 0.15, 0.08], d, 5, 0, 47)
    return mix(w, ring)


def s_sword_hum():
    t = T(1.8)
    return (np.sin(2 * np.pi * 440 * t) + 0.6 * np.sin(2 * np.pi * 443 * t) + 0.3 * np.sin(2 * np.pi * 662 * t)) * env_ar(len(t), 0.5, 0.6) * 0.4


def s_sword_cut():
    x = mix(s_slash(48) * 1.2, s_glass_crack() * 0.6, thump(70, 30, 0.6) * 0.8, crack(0.015, 3000, 12000, 49))
    return sat(x, 1.5)


def s_fizzle():
    d = 0.9
    n = int(d * SR)
    return clicks(d, 70, 2000, 8000, 50, 0.003) * np.linspace(1, 0, n) * 0.8


def s_land_heavy():
    return verb(mix(thump(75, 30, 0.6) * 1.1, burst(0.25, 100, 1200, 51) * 0.6, clicks(0.7, 30, 400, 3000, 52, 0.01) * 0.4), 0.2, IR_HALL)


def s_jump():
    return s_whoosh(0.3, 600, 3500)


def s_red_charge():
    d = 1.0
    n = int(d * SR)
    t = T(d)
    f = 300 * (4 ** (t / d))
    saw = filt(2 * ((np.cumsum(f) / SR) % 1) - 1, 'lp', 3000)
    return sat(mix(saw * 0.4, clicks(d, 50, 3000, 9000, 53) * np.linspace(0, 1, n) * 0.4) * env_ar(n, 0.6, 0.08), 1.8)


def s_red_fire():
    return sat(mix(thump(120, 45, 0.45) * 1.0, s_whoosh(0.6, 800, 7000) * 0.8, crack(0.02, 2000, 10000, 54), burst(0.2, 400, 3000, 55) * 0.6), 1.8)


def s_jet():
    d = 1.4
    n = int(d * SR)
    t = T(d)
    hiss = filt(noise(n, 56), 'bp', (4000, 9500), 2)
    whistle = np.sin(2 * np.pi * (3000 + 200 * np.sin(2 * np.pi * 7 * t)) * t) * 0.15
    return (hiss * 0.8 + whistle) * env_ar(n, 0.02, 0.4)


def s_swallow():
    d = 0.7
    n = int(d * SR)
    return mix(sweep(900, 110, d) * 0.6 * env(n, 0.01, 0.6), filt(noise(n, 57), 'bp', (500, 3000), 2) * 0.3 * env_ar(n, 0.3, 0.1))


def s_purple_form():
    d = 1.8
    n = int(d * SR)
    t = T(d)
    swell = (np.sin(2 * np.pi * 45 * t) + 0.5 * np.sin(2 * np.pi * 67 * t)) * np.linspace(0.2, 1, n)
    shimmer = sum(np.sin(2 * np.pi * f * t) for f in [1046, 1318, 1567, 2093]) * 0.05 * (0.5 + 0.5 * np.sin(2 * np.pi * 6 * t))
    x = mix(swell * 0.7, shimmer, clicks(d, 60, 2000, 9000, 58, 0.004) * 0.4, crack(0.02, 500, 9000, 59) * 1.2)
    return verb(x, 0.35, IR_HALL)


def s_reverse_suck():
    d = 2.3
    n = int(d * SR)
    k = (np.arange(n) / n) ** 3.2
    x = filt(noise(n, 60), 'hp', 500) * k * 0.9 + sweep(60, 220, d) * k * 0.3
    return x * env_ar(n, 0.01, 0.015)


def s_purple_boom():
    d = 5.5
    n = int(d * SR)
    sub_l = thump(60, 22, 2.2, 0.003) * 1.2
    b = burst(0.6, 40, 12000, 61, 0.001) * 1.0
    mid = burst(0.5, 200, 3000, 62) * 0.7
    rumble = filt(noise(n, 63), 'lp', 140) * env(n, 0.2, 5) * 1.2
    deb = clicks(d, 220, 500, 6000, 64, 0.01) * np.exp(-np.arange(n) / SR * 0.9) * 0.35
    x = mix(sub_l, b, mid, rumble, deb)
    return sat(verb(x, 0.4, IR_BIG, 1.5), 1.4)


def s_shockwave():
    d = 1.4
    n = int(d * SR)
    return mix(band_sweep(d, 5000, 300, 0.9, 65) * env(n, 0.01, 1.2), thump(70, 35, 0.8) * 0.6)


def s_rumble():
    d = 4.5
    n = int(d * SR)
    return filt(noise(n, 66), 'lp', 120) * env_ar(n, 0.3, 2.5) * 1.3


def s_dissolve():
    d = 2.6
    n = int(d * SR)
    g = np.random.default_rng(67)
    t = T(d)
    out = np.zeros(n)
    for i in range(60):
        f = 3000 * (0.5 ** (i / 30)) * (0.8 + 0.4 * g.random())
        st0 = int((i / 60) * n * 0.8)
        seg = np.sin(2 * np.pi * f * t[: n - st0]) * np.exp(-14 * t[: n - st0]) * 0.3
        out[st0:] += seg
    return verb(out, 0.5)


def s_heartbeat():
    out = np.zeros(int(1.3 * SR))
    for p, a in [(0, 1.0), (0.11, 0.7), (0.65, 0.9), (0.76, 0.6)]:
        b = thump(70, 40, 0.18) * a
        i = int(p * SR)
        out[i : i + len(b)] += b
    return filt(out, 'lp', 300)


def s_world_cut():
    d = 1.6
    n = int(d * SR)
    t = T(d)
    edge = np.sin(2 * np.pi * 9000 * t) * env(n, 0.0005, 0.12) * 0.5
    hiss = filt(noise(n, 68), 'hp', 5000) * env(n, 0.0005, 0.2) * 0.9
    sub_l = thump(55, 22, 1.4, 0.004) * 1.2
    x = mix(edge, hiss, sub_l, crack(0.01, 200, 12000, 69) * 1.5)
    x[int(1.2 * SR):] *= np.linspace(1, 0, n - int(1.2 * SR))
    return sat(x, 1.3)


def s_memory():
    x = verb(burst(0.4, 300, 4000, 70) * 0.6, 0.8, IR_HALL, 1.2)
    return x[::-1].copy()  # 反向的混响


def s_airport():
    d = 6
    n = int(d * SR)
    return filt(noise(n, 71), 'lp', 1400) * 0.25 * env_ar(n, 1.5, 2)


SFX = {
    'wind': (s_wind, -16), 'swell': (s_swell, -10), 'pulse': (lambda: s_pulse(), -8), 'pulse_red': (lambda: s_pulse(True), -8),
    'whoosh': (s_whoosh, -10), 'curtain': (s_curtain, -6), 'chime': (lambda: s_bell(880, 1.8, 0.4), -12), 'chime_hi': (lambda: s_bell(1320, 1.4, 0.4), -10),
    'bell': (lambda: s_bell(220, 2.6), -9), 'charge': (s_charge, -10), 'chant': (lambda: s_chant(False), -12), 'chant_low': (lambda: s_chant(True), -10),
    'redin': (s_redin, -12), 'contact': (s_contact, -8), 'riser': (s_riser, -9), 'lock': (s_lock, -10), 'suck': (s_suck, -8),
    'purple_hit': (lambda: s_impact_big(16, (72, 26), 0.8, 2.5), 0), 'subdrop': (s_subdrop, -4), 'collapse': (s_collapse, -6),
    'rct': (s_rct, -12), 'dash': (lambda: s_whoosh(0.35, 800, 5500), -8), 'punch_heavy': (lambda: s_punch(1.0, 20), -3), 'punch': (lambda: s_punch(0.7, 21), -6),
    'jab': (lambda: s_punch(0.5, 22), -9), 'slash': (s_slash, -5), 'swish': (lambda: s_whoosh(0.3, 1000, 4000), -12), 'step': (s_step, -12),
    'block': (s_block, -6), 'run': (s_run, -12), 'blue_pull': (s_blue_pull, -8), 'blue_hum': (s_blue_hum, -10), 'black_flash': (s_black_flash, -1),
    'slide': (s_slide, -10), 'sign': (s_sign, -8), 'eyes': (s_eyes, -12), 'domain_void': (s_domain_void, -3), 'domain_shrine': (s_domain_shrine, -3),
    'slash_rain': (s_slash_rain, -7), 'glass_crack': (s_glass_crack, -5), 'glass': (lambda: s_glass(False), -7), 'glass_big': (lambda: s_glass(True, 72), -2),
    'thud': (s_thud, -8), 'wheel_appear': (s_wheel_appear, -8), 'wheel': (s_wheel, -4), 'rise': (s_rise, -8), 'sword': (s_sword, -6),
    'sword_hum': (s_sword_hum, -14), 'sword_cut': (s_sword_cut, -2), 'fizzle': (s_fizzle, -10), 'land_heavy': (s_land_heavy, -4), 'jump': (s_jump, -10),
    'red_charge': (s_red_charge, -8), 'red_fire': (s_red_fire, -3), 'jet': (s_jet, -8), 'swallow': (s_swallow, -6), 'purple_form': (s_purple_form, -4),
    'reverse_suck': (s_reverse_suck, -6), 'purple_boom': (s_purple_boom, 0), 'shockwave': (s_shockwave, -5), 'rumble': (s_rumble, -8),
    'dissolve': (s_dissolve, -10), 'heartbeat': (s_heartbeat, -6), 'world_cut': (s_world_cut, 0), 'memory': (s_memory, -12), 'airport_air': (s_airport, -18),
}


def main():
    total = float(sys.argv[1]) if len(sys.argv) > 1 else 197.17
    cues = json.load(open(os.path.join(ROOT, 'out/cues.json')))
    cache = {}
    out = np.zeros((int(total * SR) + SR * 6, 2))
    missing = set()
    for t, name, gain, pan, *_ in cues:
        if name not in SFX:
            missing.add(name)
            continue
        if name not in cache:
            fn, base = SFX[name]
            cache[name] = (norm(np.asarray(fn(), dtype=np.float64)), base)
        x, base = cache[name]
        amp = 10 ** ((base + gain - 2) / 20)
        th = (pan + 1) * np.pi / 4
        i = int(t * SR)
        seg = x[: len(out) - i] * amp
        out[i : i + len(seg), 0] += seg * np.cos(th) * 1.414
        out[i : i + len(seg), 1] += seg * np.sin(th) * 1.414
    out = out[: int(total * SR)]
    sf.write(os.path.join(ROOT, 'out/sfx.wav'), out.astype(np.float32), SR, subtype='FLOAT')
    print('sfx.wav', out.shape, 'peak', float(np.max(np.abs(out))), 'kinds', len(cache), 'missing', sorted(missing))


if __name__ == '__main__':
    main()
