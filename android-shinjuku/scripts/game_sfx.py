"""程序合成游戏的技能音效（v0.15）：配方见 notes/音效设计.md（Astra 的设计稿；Luna 的声音参考见 notes/音效资料.md）。

全部离线合成（numpy / scipy，48 kHz 单声道），不采样原作音频。输出：
  app/src/main/res/raw/sfx_<名字>_<变体>.ogg（OGG Vorbis）
  app/src/main/java/com/shinjuku/showdown/SfxBank.java（编号、资源表、时长）
响度：背景音乐是用户给的原曲、原音量（实测 −7.5 LUFS，峰值过满），不能压低它，所以音效整体做得比设计稿的建议响：
普通中等命中（hit_medium）最响 80 ms 窗口的 RMS 定在 REF_DB，其余按设计稿的相对增益排开，峰值限在 CEIL_DB 以内。
用法：python3 scripts/game_sfx.py                      （全部重写；需要 numpy、scipy、soundfile、ffmpeg）
      python3 scripts/game_sfx.py --only=名字,名字      （只重写这几个，SfxBank 照样全量生成）
"""
import os, subprocess, sys, tempfile
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve
from scipy.ndimage import minimum_filter1d
import soundfile as sf

SR = 48000
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, 'app/src/main/res/raw')
JAVA = os.path.join(ROOT, 'app/src/main/java/com/shinjuku/showdown/SfxBank.java')
REF_DB = -13.0   # hit_medium 最响 80 ms 窗口的 RMS（dBFS）
CEIL_DB = -1.0   # 单个资产的峰值上限（dBFS）

_seed = [1]


def seed():
    _seed[0] += 1
    return _seed[0]


# ---------------- 基本工具（和短片的 sfx.py 同一套，随机层全部显式种子） ----------------

def T(d):
    return np.arange(int(round(d * SR))) / SR


def env(n, a=0.005, d=0.2, curve=4.0):
    t = np.arange(n) / SR
    return np.where(t < a, t / max(a, 1e-6), np.exp(-curve * (t - a) / max(d, 1e-6)))


def env_ar(n, a, r):
    t = np.arange(n) / SR
    up = np.clip(t / max(a, 1e-6), 0, 1)
    down = np.clip((n / SR - t) / max(r, 1e-6), 0, 1)
    return up * down


def noise(n, s=None):
    return np.random.default_rng(seed() if s is None else s).standard_normal(n)


def filt(x, kind, f, order=4):
    if kind == 'bp':
        sos = butter(order, [max(20, f[0]), min(SR / 2 - 100, f[1])], btype='bandpass', fs=SR, output='sos')
    else:
        sos = butter(order, min(SR / 2 - 100, max(20, f)), btype='lowpass' if kind == 'lp' else 'highpass', fs=SR, output='sos')
    return sosfilt(sos, x)


def sweep(f0, f1, d, shape='exp'):
    t = T(d)
    f = f0 * (f1 / f0) ** (t / d) if shape == 'exp' else f0 + (f1 - f0) * t / d
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def band_sweep(d, f0, f1, width=0.6, steps=24):
    n = int(round(d * SR))
    x = noise(n)
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
    m = np.max(np.abs(out))
    return out / m if m > 0 else out


def sat(x, drive=2.0):
    return np.tanh(drive * x) / np.tanh(drive)


def mix(*layers):
    n = max(len(l) for l in layers)
    out = np.zeros(n)
    for l in layers:
        out[: len(l)] += l
    return out


def norm1(x):
    m = np.max(np.abs(x))
    return x / m if m > 0 else x


# ---------------- 设计稿里的记法（Astra） ----------------

def C(d, lo, hi):
    n = int(round(d * SR))
    return norm1(filt(noise(n), 'bp', (lo, hi), 2)) * env(n, 0.0005, d)


def N(d, lo, hi, a=0.002):
    n = int(round(d * SR))
    return norm1(filt(noise(n), 'bp', (lo, hi), 2)) * env(n, a, d)


def S(d, f0, f1, a=0.003):
    return sweep(f0, f1, d) * env(int(round(d * SR)), a, d * 0.8)


def W(d, f0, f1, a, r):
    return band_sweep(d, f0, f1, 0.6) * env_ar(int(round(d * SR)), a, r)


def P(d, f, ratios, amps, decay, a=0.003):
    t = T(d)
    g = np.random.default_rng(seed())
    out = np.zeros(len(t))
    for r, am in zip(ratios, amps):
        out += am * np.sin(2 * np.pi * f * r * t + g.random() * 6.28) * np.exp(-t * decay * (0.6 + r * 0.4))
    return out * np.clip(t / max(a, 1e-6), 0, 1)


def B(d, f0=220, f1=160):
    return S(d, f0, f1) + 0.35 * S(d, 2 * f0, 2 * f1) + 0.15 * S(d, 3 * f0, 3 * f1)


def ir(sec, decay, bright):
    t = T(sec)
    h = noise(len(t)) * np.exp(-decay * t)
    h = filt(h, 'lp', bright, 2)
    h[0] = 1.0
    return h / np.max(np.abs(h))


def R(x, wet, tail=0.0):
    if wet <= 0:
        return np.concatenate([x, np.zeros(int(tail * SR))])
    h = ir(0.18, 25, 3500)
    dry = np.concatenate([x, np.zeros(int(tail * SR))])
    y = fftconvolve(dry, h)[: len(dry)]
    y = y / (np.max(np.abs(y)) + 1e-9) * (np.max(np.abs(x)) + 1e-9)
    return dry * (1 - wet) + y * wet


def D(x, t):
    return np.concatenate([np.zeros(int(round(t * SR))), x])


def at(x, t, out_len=None):
    """把 x 放在 t 秒处（返回补零后的数组）。"""
    y = D(x, t)
    return y if out_len is None else np.pad(y, (0, max(0, out_len - len(y))))[:out_len]


def L(d, freqs, amps, lo, hi, noise_amp, am_hz, am_depth):
    """无缝循环：正弦取整周期，噪声层循环滤波（三段拼接取中间），调幅取整周期。
    长度取 1024 采样的整数倍：ffmpeg 的 libvorbis 会把最后不满 256 的那一块丢掉，循环就接不上了。"""
    n = int(round(d * SR / 1024)) * 1024
    d = n / SR
    t = np.arange(n) / SR
    tones = np.zeros(n)
    g = np.random.default_rng(seed())
    for f, a in zip(freqs, amps):
        f = round(f * d) / d
        tones += a * np.sin(2 * np.pi * f * t + g.random() * 6.28)
    x = noise(n)
    bed = filt(np.concatenate([x, x, x]), 'bp', (lo, hi), 2)[n:2 * n]
    am_hz = max(1, round(am_hz * d)) / d
    return (tones + noise_amp * norm1(bed) * 0.35) * (1 - am_depth / 2 + am_depth / 2 * np.cos(2 * np.pi * am_hz * t))


# ---------------- 母版处理 ----------------

def finish(x, drive, wet=0.0, tail=0.0, dur=None):
    """先饱和、再混响、按时长补齐或裁掉，末尾 8 ms 余弦淡出。"""
    x = sat(norm1(x) * 0.9, drive) if drive > 1.0 else norm1(x) * 0.9
    x = R(x, wet, tail)
    if dur is not None:
        n = int(round((dur + tail) * SR))
        x = np.pad(x, (0, max(0, n - len(x))))[:n]
    f = min(len(x), int(0.008 * SR))
    if f > 1:
        x[-f:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, f)))
    return x


def master(x, loop=False):
    """去直流、高通 75 Hz、低通 7.2 kHz。循环音按周期滤（三段拼接取中间，接缝不跳）。"""
    if loop:
        n = len(x)
        y = np.concatenate([x, x, x])
        y = filt(filt(y, 'hp', 75, 2), 'lp', 7200, 4)
        return y[n:2 * n]
    x = x - np.mean(x)
    x = filt(filt(np.concatenate([x, np.zeros(int(0.02 * SR))]), 'hp', 75, 2), 'lp', 7200, 4)
    return x


def rms80(x):
    w = int(0.08 * SR)
    if len(x) <= w:
        return np.sqrt(np.mean(x ** 2) * len(x) / w)  # 不到 80 ms 的按 80 ms 窗口算（短点击不硬拉响）
    c = np.cumsum(np.concatenate([[0], x ** 2]))
    return np.sqrt(np.max(c[w:] - c[:-w]) / w)


def limit(x, ceil):
    """1 ms 前瞻、45 ms 释放的简单限幅。"""
    need = np.minimum(1.0, ceil / (np.abs(x) + 1e-12))
    g = minimum_filter1d(need, size=96, origin=0)
    out = np.empty_like(g)
    k = np.exp(-1.0 / (0.045 * SR))
    cur = 1.0
    for i in range(len(g)):  # 释放：只往上慢慢回
        cur = g[i] if g[i] < cur else g[i] + (cur - g[i]) * k
        out[i] = cur
    return x * out


def level(x, gain_db, loop=False):
    target = 10 ** ((REF_DB + gain_db) / 20)
    r = rms80(x) if not loop else np.sqrt(np.mean(x ** 2))
    ceil = 10 ** (CEIL_DB / 20)
    if not loop and len(x) < 0.1 * SR:  # 短点击：不为了追 RMS 硬拉响，峰值到顶就停
        return x * min(target / (r + 1e-12), ceil / (np.max(np.abs(x)) + 1e-12))
    y = x * (target / (r + 1e-12))
    if np.max(np.abs(y)) > ceil:
        y = limit(y, ceil * 0.98)
    return y


# ---------------- 配方 ----------------
# 每个音效：名字 → (生成函数(变体号) → 数组, 相对增益 dB, 变体数, 是否循环)。变体只改噪声种子、主体频率 ±1.5%、时长 ±4%。

def jit(v, base, amt=0.015):
    if v == 0:
        return base
    g = np.random.default_rng(1000 + v * 7919)
    return base * (1 + amt * (2 * g.random() - 1))


def swing(kind, v):
    if kind == 0:
        b = W(jit(v, 0.06, 0.04), jit(v, 900), 1800, 0.012, 0.045)
    elif kind == 1:
        b = W(jit(v, 0.08, 0.04), jit(v, 600), 2200, 0.02, 0.05)
    else:
        b = W(jit(v, 0.10, 0.04), jit(v, 650), 2800, 0.025, 0.06)
    return finish(mix(0.18 * C(0.008, 1200, 3500), b), 1.1)


def sukuna_swing(v):
    return finish(mix(0.28 * C(0.009, 850, 3000), 0.7 * W(0.08, jit(v, 1800), 750, 0.012, 0.05)), 1.25)


def fist_charge(v):
    return finish(mix(0.12 * C(0.006, 1800, 3200), 0.65 * W(0.14, jit(v, 2200), 450, 0.09, 0.025),
                      0.25 * P(0.14, jit(v, 180), [1, 2, 4], [1, 0.4, 0.15], 8)), 1.2, 0.03)


def hold(kind, v):
    blue = W(0.20, 1800, 500, 0.15, 0.03) + 0.2 * P(0.20, 220, [1, 2, 3], [1, 0.3, 0.1], 3)
    red = W(0.20, 500, 1800, 0.15, 0.03) + 0.2 * P(0.20, 220, [1, 2, 3], [1, 0.3, 0.1], 3)
    if kind == 0:
        x = blue
    elif kind == 1:
        x = red
    else:
        x = 0.5 * blue + 0.5 * red + 0.15 * S(0.20, 660, 664, 0.04)
    return finish(x, 1.1)


CHANT_RATIO = [1, 1.05946, 1.12246, 1.18921]


def gojo_chant(color, seg):
    f = {0: 330, 1: 392, 2: 440}[color] * CHANT_RATIO[seg]
    d = 0.34 if color == 2 else 0.22
    body = P(d, f, [1, 2, 3, 5], [1, 0.30, 0.15, 0.08], 8, 0.025) + 0.18 * N(d, 700, 1800, 0.025)
    return finish(mix(0.10 * C(0.008, 1400, 2600), body), 1.15, 0.06, 0.02)


def cast_break(v):
    return finish(mix(C(0.012, 1100, 4200), 0.5 * S(0.10, jit(v, 760), 190, 0.001), 0.25 * N(0.08, 450, 1400)), 1.8)


def blue_launch(mx, v):
    if mx:
        x = mix(0.15 * C(0.008, 1800, 3300), W(0.21, jit(v, 2800), 400, 0.035, 0.08), 0.3 * B(0.09))
    else:
        x = mix(0.15 * C(0.008, 1800, 3300), W(0.14, jit(v, 2400), 550, 0.035, 0.08), 0.2 * B(0.09))
    return finish(x, 1.2, 0.03)


def blue_anchor(mx, v):
    x = mix(0.5 * C(0.012, 900, 3000), 0.7 * S(0.12, 700, 220), 0.35 * B(0.10))
    if mx:
        x = mix(x, 0.2 * P(0.20, 440, [1, 1.5, 2], [1, 0.3, 0.15], 14))
    return finish(x, 1.4, 0.06, 0.03)


def blue_loop(mx):
    if mx:
        x = L(1.0, [180, 360, 720, 724], [0.45, 0.18, 0.06, 0.05], 450, 1400, 0.14, 5, 0.18)
    else:
        x = L(1.0, [180, 360, 720], [0.45, 0.18, 0.06], 450, 1400, 0.10, 5, 0.18)
    return sat(norm1(x) * 0.9, 1.15)


def dissolve(long, v):
    x = W(0.18, jit(v, 1600), 350, 0.025, 0.13)
    if long:
        x = mix(x, 0.15 * P(0.35, 660, [1, 1.5, 2.2], [1, 0.3, 0.1], 15))
    return finish(x, 1.1, 0.05)


def red_fire(kind, v):
    t = C(0.008 if kind == 2 else 0.012, 1200, 4500) * 0.6
    if kind == 0:
        b = mix(0.55 * B(0.11), 0.7 * W(0.16, jit(v, 700), 3300, 0.008, 0.12))
    else:
        b = mix(0.75 * B(0.11), 0.7 * W(0.24, jit(v, 700), 3300, 0.008, 0.12))
    return finish(mix(t, b), 1.55, 0.04, 0.02)


def red_burst(kind, v):
    if kind == 0:
        x = mix(0.8 * C(0.014, 900, 4000), 0.8 * B(0.14, jit(v, 220), 160), 0.5 * N(0.18, 450, 2200))
    else:
        x = mix(0.8 * C(0.014, 900, 4000), 0.8 * B(0.22, jit(v, 220), 160), 0.5 * N(0.27, 450, 2200),
                D(0.25 * C(0.012, 1800, 3500), 0.022))
    return finish(x, 1.7, 0.07, 0.05)


def purple_fire(kind):
    d = 0.40 if kind == 1 else 0.28
    bw = 0.24 if kind == 1 else 0.18
    t = C(0.016, 1000, 4200) * 0.65
    if kind == 2:  # 瞬移茈：起音压到 2 ms
        t = norm1(filt(noise(int(0.016 * SR)), 'bp', (1000, 4200), 2)) * env(int(0.016 * SR), 0.002, 0.016) * 0.65
    b = mix(0.65 * B(bw), 0.5 * W(d, 850, 3200, 0.012, 0.20), 0.15 * P(d + 0.02, 440, [1, 1.01, 2], [1, 0.8, 0.2], 10))
    return finish(mix(t, b), 1.6, 0.08, 0.06)


def hollow_form():
    x = mix(0.5 * C(0.012, 1200, 3500), 0.5 * W(0.12, 2200, 500, 0.07, 0.025),
            D(0.8 * B(0.22), 0.045), D(0.6 * N(0.32, 500, 2800), 0.045))
    return finish(x, 1.6, 0.10, 0.12)


def black_flash(fist, v):
    x = mix(0.7 * C(0.008, 800, 3200), 0.65 * B(0.09, jit(v, 220), 160),
            D(0.8 * C(0.018, 1500, 4300), 0.024), D(0.4 * S(0.14, jit(v, 1900), 380), 0.024))  # 24 ms 后第二道裂声
    if fist:
        x = mix(x, 0.18 * W(0.20, 1200, 420, 0.02, 0.15))
    return finish(x, 2.0, 0.03)


def tp_depart():
    return finish(W(0.055, 1900, 500, 0.035, 0.015), 1.3)


def tp_arrive():
    return finish(D(mix(0.5 * C(0.009, 1100, 3200), 0.4 * B(0.065)), 0.012), 1.3)


def dodge(v):
    return finish(mix(0.1 * C(0.005, 1200, 2600), W(0.10, jit(v, 1600), 650, 0.025, 0.06)), 1.1)


def jump(v):
    return finish(mix(0.2 * N(0.025, 500, 1600), 0.6 * W(0.09, jit(v, 650), 1800, 0.02, 0.06)), 1.1)


def land(heavy, v):
    if heavy:
        x = mix(0.5 * C(0.012, 650, 2400), 0.7 * B(0.12, jit(v, 220), 160), 0.35 * N(0.15, 300, 1400))
    else:
        x = mix(0.3 * C(0.008, 500, 1800), 0.3 * B(0.06, jit(v, 240), 180), 0.2 * N(0.06, 400, 1100))
    return finish(x, 1.4, 0.03)


def void_open():
    x = mix(0.2 * C(0.010, 1400, 3000), 0.35 * W(0.24, 1600, 350, 0.05, 0.15), 0.3 * P(0.45, 420, [1, 1.5, 2.25], [1, 0.4, 0.2], 5, 0.03))
    return finish(x, 1.1, 0.12, 0.05, 0.5)


def void_loop():
    return L(2.0, [420, 630, 940], [0.25, 0.10, 0.06], 900, 1800, 0.015, 0.5, 0.20)


def void_close():
    return finish(mix(0.7 * W(0.18, 1700, 400, 0.025, 0.13), 0.15 * P(0.25, 420, [1, 1.5], [1, 0.3], 15)), 1.1, 0.05)


def infinity_block(v):
    x = mix(0.22 * S(0.025, 1800, 2400, 0.001), 0.35 * P(0.14, jit(v, 1200), [1, 1.47, 2.03], [1, 0.23, 0.10], 24),
            0.15 * W(0.08, 1200, 450, 0.008, 0.06))
    return finish(x, 1.1, 0.04)


def cine_rise():
    x = mix(0.1 * C(0.012, 900, 2200), 0.45 * W(1.0, 1600, 380, 0.70, 0.20), 0.25 * P(1.0, 180, [1, 2, 4], [1, 0.35, 0.12], 1, 0.15))
    return finish(x, 1.25, 0.06)


def cine_contact():
    x = mix(0.35 * C(0.009, 1400, 3200), 0.4 * P(0.333, 440, [1, 1.01, 1.5, 2], [1, 0.8, 0.25, 0.1], 2, 0.015),
            0.25 * W(0.30, 1700, 500, 0.22, 0.04))
    return finish(x, 1.3, 0, 0, 0.333)


def cine_blast():
    x = mix(0.8 * C(0.018, 900, 4200), 0.9 * B(0.25), 0.65 * N(0.40, 450, 2600), D(0.25 * C(0.015, 1700, 3800), 0.035),
            0.22 * W(0.75, 1400, 350, 0.02, 0.65))
    return finish(x, 1.8, 0.10, 0.10)


def shoulder_tap():
    return finish(mix(0.45 * C(0.006, 650, 1700), 0.25 * N(0.045, 350, 1100)), 1.1)


def head_turn():
    return finish(0.45 * W(0.09, 1400, 700, 0.02, 0.055), 1.0)


def world_sign(crack):
    if crack:
        x = mix(*[D(0.3 * C(0.009, 1400, 3400), t) for t in (0, 0.055, 0.11)], np.zeros(int(0.20 * SR)))
    else:
        x = mix(0.55 * C(0.012, 800, 2400), 0.35 * P(0.18, 270, [1, 2.37, 3.61], [1, 0.25, 0.12], 17))
    return finish(x, 1.4, 0.02, 0, 0.20)


def sukuna_chant(seg):
    f = [220, 207.65, 196][seg]
    x = mix(0.15 * C(0.007, 850, 1800), P(0.18, f, [1, 2, 3, 5], [1, 0.4, 0.18, 0.1], 10, 0.018), 0.2 * N(0.14, 600, 1700, 0.015))
    return finish(x, 1.7)


def slash_lock(space):
    x = mix(0.45 * C(0.006, 1600, 3500), 0.45 * P(0.10, 900, [1, 1.5], [1, 0.25], 35))
    if space:
        x = mix(x, D(0.25 * P(0.08, 1350, [1], [1], 40), 0.035))
    return finish(x, 1.2, 0, 0, 0.14 if space else 0.10)


def slash(kind, v):
    base = mix(0.7 * C(0.009, 1800, 4400), 0.35 * W(0.08, jit(v, 2600), 900, 0.003, 0.06))
    if kind == 0:   # 解
        return finish(base, 1.6, 0.02)
    if kind == 1:   # 世界斩
        return finish(mix(base, 0.45 * S(0.18, jit(v, 900), 220), 0.25 * B(0.10)), 1.6, 0.04, 0, 0.24)
    if kind == 2:   # 空间斩
        return finish(mix(base, 0.45 * S(0.18, 900, 220), 0.25 * B(0.16), 0.3 * N(0.22, 550, 1500)), 1.6, 0.05, 0, 0.30)
    return finish(mix(W(0.15, 800, 2800, 0.008, 0.11), 0.2 * C(0.008, 1800, 3500)), 1.6)  # 固定动作里的穿刺束


def amp_on():
    return finish(mix(0.25 * C(0.009, 700, 2100), 0.5 * W(0.16, 650, 1800, 0.04, 0.08), 0.15 * P(0.14, 330, [1, 2], [1, 0.2], 14)), 1.2)


def amp_loop():
    return sat(norm1(L(1.0, [330, 660], [0.08, 0.03], 600, 1700, 0.12, 2, 0.15)) * 0.9, 1.15)


def amp_off():
    return finish(0.5 * W(0.12, 1500, 500, 0.015, 0.09), 1.1)


def rct_loop():
    x = L(1.0, [300, 600, 900], [0.3, 0.10, 0.04], 1000, 1900, 0.025, 2, 0.12)
    t = np.arange(len(x)) / SR
    n = int(0.18 * SR)
    glide = np.zeros(len(t))
    f = np.linspace(607, 600, n)
    glide[:n] = 0.035 * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.linspace(0, np.pi, n))
    return sat(norm1(x + glide) * 0.9, 1.1)


def agito(kind):
    if kind == 0:
        x = mix(0.25 * C(0.01, 700, 1800), 0.4 * W(0.18, 1200, 400, 0.10, 0.05))
    elif kind == 1:
        x = 0.6 * W(0.25, 450, 2100, 0.05, 0.15)
    else:
        x = mix(*[D(a * C(0.012, 1200, 4000), t) for t, a in ((0, 1), (0.035, 0.65), (0.075, 0.45))], 0.45 * B(0.12), np.zeros(int(0.22 * SR)))
    return finish(x, 1.8, 0.02)


def summon_chant():
    pulses = [D(0.35 * B(0.16, 180, 150) + 0.25 * P(0.16, 150, [1, 2, 3, 5], [1, 0.4, 0.2, 0.1], 9, 0.02), t) for t in (0, 0.32, 0.66)]
    return finish(mix(*pulses, 0.15 * W(1.0, 900, 350, 0.20, 0.20)), 1.5, 0.04, 0, 1.0)


def mahoraga_rise():
    x = mix(0.5 * C(0.018, 700, 2800), 0.65 * B(0.24, 210, 150), 0.45 * W(0.45, 400, 1800, 0.10, 0.25),
            0.2 * P(0.45, 550, [1, 1.71, 2.41], [1, 0.3, 0.15], 10))
    return finish(x, 1.5, 0.06, 0, 0.6)


def transform(done):
    if done:
        x = mix(0.4 * C(0.01, 800, 2200), 0.4 * B(0.12))
    else:
        x = mix(0.3 * C(0.015, 650, 2200), 0.6 * W(1.0, 350, 1800, 0.30, 0.45), 0.4 * B(0.25))
    return finish(x, 1.8, 0.03)


def space_windup():
    x = mix(0.2 * C(0.009, 1400, 3200), 0.4 * W(0.50, 1800, 650, 0.35, 0.10), 0.2 * P(0.50, 180, [1, 2.03, 4.01], [1, 0.35, 0.12], 2, 0.05))
    return finish(x, 1.4)


def space_kill():
    x = mix(0.45 * C(0.010, 900, 3000), 0.6 * S(0.18, 500, 160), 0.25 * N(0.16, 450, 1200), 0.12 * P(0.26, 750, [1, 1.41], [1, 0.2], 20))
    return finish(x, 1.6)


def exhaust():
    return finish(mix(0.1 * C(0.008, 600, 1600), 0.55 * W(0.35, 1200, 320, 0.02, 0.28), 0.2 * S(0.20, 280, 170)), 1.2, 0, 0, 0.38)


def shrine_sign():
    hits = [D(0.4 * C(0.012, 750, 2400) + 0.25 * P(0.16, 240, [1, 2.4, 3.5], [1, 0.25, 0.1], 18)[: int(0.012 * SR)], t) for t in (0, 0.25)]
    tones = [D(0.25 * P(0.16, 240, [1, 2.4, 3.5], [1, 0.25, 0.1], 18), t) for t in (0, 0.25)]
    return finish(mix(*hits, *tones, 0.2 * W(1.0, 450, 1400, 0.7, 0.15)), 1.4, 0.02, 0, 1.0)


def shrine_open():
    x = mix(0.5 * C(0.018, 750, 2600), 0.6 * B(0.22, 200, 150), 0.35 * P(0.40, 240, [1, 2.4, 3.5], [1, 0.35, 0.15], 8))
    return finish(x, 1.5, 0.05, 0.05, 0.5)


def shrine_warn():
    x = mix(0.35 * P(0.055, 1050, [1, 2], [1, 0.10], 35, 0.002), D(0.35 * P(0.055, 1400, [1, 2], [1, 0.10], 35, 0.002), 0.085),
            0.18 * W(0.16, 700, 1800, 0.025, 0.10))
    return finish(x, 1.1, 0, 0, 0.18)


def shrine_slash(hit, v):
    if hit:
        x = mix(0.7 * C(0.010, 1400, 3800), 0.35 * B(0.075, jit(v, 220), 160), 0.25 * N(0.09, 650, 1800))
    else:
        x = mix(0.7 * C(0.010, 1400, 3800), 0.25 * N(0.055, 650, 1800))
    return finish(x, 1.5)


def clash_loop():
    return sat(norm1(L(1.0, [240, 244, 480, 630], [0.20, 0.16, 0.06, 0.04], 700, 1500, 0.06, 4, 0.12)) * 0.9, 1.3)


def domain_break(void):
    tr = mix(*[D(a * C(0.015, 650, 2800), t) for t, a in ((0, 0.7), (0.04, 0.4), (0.09, 0.2))])
    body = P(0.32, 950, [1, 1.41, 1.93], [1, 0.35, 0.2], 13) if void else mix(0.5 * B(0.18), 0.35 * N(0.28, 400, 1800))
    return finish(mix(tr, body), 1.5, 0.06, 0, 0.40 if void else 0.45)


def shrine_end():
    return finish(mix(0.15 * C(0.01, 900, 2400), 0.5 * W(0.28, 1600, 350, 0.03, 0.20), 0.2 * P(0.20, 240, [1, 2.4], [1, 0.25], 17)), 1.2, 0.03)


def restore(exhaust_end):
    f = 440 if exhaust_end else 660
    return finish(mix(0.1 * C(0.005, 1200, 2400), 0.35 * P(0.13, f, [1, 1.5], [1, 0.25], 25, 0.005)), 1.1)


def wheel_click(v):
    x = mix(*[D(a * C(0.008, 1000, 3200), t) for t, a in ((0, 0.2), (0.025, 0.3), (0.043, 0.6))],
            D(0.25 * P(0.07, jit(v, 850), [1, 1.73, 2.52], [1, 0.25, 0.1], 35), 0.043))
    return finish(x, 1.5)


def adapt_complete(inf):
    b = P(0.25, 550, [1, 1.71, 2], [1, 0.3, 0.15], 12) if inf else P(0.25, 660, [1, 1.5], [1, 0.35], 12)
    return finish(mix(0.4 * C(0.010, 1000, 3000), b), 1.3, 0.04)


def maho_windup(kind):
    if kind == 0:
        x = mix(W(0.25, 500, 1600, 0.15, 0.07), 0.2 * B(0.08))
    elif kind == 1:
        x = mix(0.4 * P(0.40, 1100, [1, 1.71, 2.4], [1, 0.3, 0.12], 7, 0.035), 0.3 * W(0.40, 900, 2400, 0.25, 0.10))
    else:
        x = mix(W(0.22, 900, 3000, 0.04, 0.14), 0.3 * C(0.012, 1500, 3600))
    return finish(x, 1.3, 0.03)


def mahoraga_fall():
    x = mix(0.4 * C(0.018, 850, 3000), 0.5 * B(0.18), 0.4 * N(0.25, 500, 2200), 0.2 * P(0.45, 700, [1, 1.7, 2.3], [1, 0.3, 0.1], 12))
    return finish(x, 1.5, 0.06, 0, 0.5)


def hit(tier, body, v):
    if tier == 0:
        x = [0.55 * C(0.007, 900, 3000), 0.3 * N(0.055, 600, 1800)]
        if body:
            x.append(0.20 * B(0.045, jit(v, 240), 180))
        return finish(mix(*x), 1.4)
    if tier == 1:
        x = [0.6 * C(0.009, 850, 3200), 0.35 * N(0.09, 500, 2000)]
        if body:
            x.append(0.55 * B(0.085, jit(v, 220), 160))
        return finish(mix(*x), 1.5, 0.02)
    return finish(mix(0.65 * C(0.012, 800, 3500), 0.75 * B(0.14, jit(v, 220), 160), 0.4 * N(0.16, 450, 2100)), 1.65, 0.05, 0.03)


def down_thud(v):
    x = mix(0.35 * C(0.008, 500, 1700), 0.55 * B(0.11, jit(v, 220), 160), 0.2 * N(0.12, 350, 1100))
    return finish(x, 1.4, 0.02)


def simple_open():
    """简易领域张开（v0.17，Astra）：220 Hz 正弦带弱泛音，15 ms 起音；900→500 Hz 窄带气流收拢；末尾一点清鸣。"""
    x = mix(P(0.24, 220, [1, 2, 3], [1, 0.3, 0.15], 6, 0.015), 0.5 * W(0.24, 900, 500, 0.03, 0.12),
            D(0.15 * P(0.10, 1320, [1, 1.5], [1, 0.3], 25, 0.004), 0.15))
    return finish(x, 1.1, 0.02)


def simple_erode(v):
    """被削一层：1.2–3 kHz 噪声 8 ms 瞬态，叠 680 / 940 Hz 非整数双音很快衰减；没有爆炸的低频。"""
    return finish(mix(0.6 * C(0.008, 1200, 3000), 0.4 * P(0.10, jit(v, 680), [1, 940 / 680], [1, 0.7], 30, 0.002)), 1.2)


def simple_break():
    """削光碎掉：三下相隔 18 ms 的干裂，叠 700→350 Hz 下扫，一股短气流散开。"""
    x = mix(*[D(a * C(0.010, 900, 3200), t) for t, a in ((0, 0.8), (0.018, 0.6), (0.036, 0.45))], 0.45 * S(0.16, 700, 350),
            D(0.3 * W(0.14, 1400, 500, 0.01, 0.10), 0.05))
    return finish(x, 1.4, 0, 0, 0.22)


def ko():
    return finish(mix(0.3 * C(0.010, 650, 2200), 0.45 * S(0.20, 430, 170), 0.25 * N(0.18, 450, 1400)), 1.4, 0.02)


def signal(notes, gap):
    x = mix(*[D(P(0.18, f, [1, 2, 3], [1, 0.2, 0.08], 16, 0.008), i * gap) for i, f in enumerate(notes)])
    return finish(x, 1.1, 0.05, 0.08)


# 编号（Java 常量名）、文件名、生成函数、相对增益（dB，相对普通中等命中）、变体数、循环
CUES = [
    ('SWING_JAB', 'swing_jab', lambda v: swing(0, v), -8, 2, False),
    ('SWING_KICK', 'swing_kick', lambda v: swing(1, v), -8, 2, False),
    ('SWING_UPPER', 'swing_upper', lambda v: swing(2, v), -8, 2, False),
    ('SUKUNA_SWING', 'sukuna_swing', sukuna_swing, -8, 2, False),
    ('FIST_CHARGE', 'fist_charge', fist_charge, -6, 2, False),
    ('HOLD_BLUE', 'hold_blue', lambda v: hold(0, v), -10, 1, False),
    ('HOLD_RED', 'hold_red', lambda v: hold(1, v), -10, 1, False),
    ('HOLD_PURPLE', 'hold_purple', lambda v: hold(2, v), -10, 1, False),
    ('CHANT_BLUE', 'chant_blue', lambda v: gojo_chant(0, v), -6, 4, False),   # 变体 = 第几段（不随机）
    ('CHANT_RED', 'chant_red', lambda v: gojo_chant(1, v), -6, 4, False),
    ('CHANT_PURPLE', 'chant_purple', lambda v: gojo_chant(2, v), -6, 4, False),
    ('CAST_BREAK', 'cast_break', cast_break, -3, 2, False),
    ('BLUE_LAUNCH', 'blue_launch', lambda v: blue_launch(False, v), -3, 2, False),
    ('BLUE_LAUNCH_MAX', 'blue_launch_max', lambda v: blue_launch(True, v), 0, 2, False),
    ('BLUE_ANCHOR', 'blue_anchor', lambda v: blue_anchor(False, v), -1, 1, False),
    ('BLUE_ANCHOR_MAX', 'blue_anchor_max', lambda v: blue_anchor(True, v), 1, 1, False),
    ('BLUE_LOOP', 'blue_loop', lambda v: blue_loop(False), -16, 1, True),
    ('BLUE_LOOP_MAX', 'blue_loop_max', lambda v: blue_loop(True), -13, 1, True),
    ('DISSOLVE', 'dissolve', lambda v: dissolve(False, v), -9, 2, False),
    ('DISSOLVE_LONG', 'dissolve_long', lambda v: dissolve(True, v), -9, 1, False),
    ('RED_FIRE', 'red_fire', lambda v: red_fire(0, v), 0, 2, False),
    ('RED_FIRE_MAX', 'red_fire_max', lambda v: red_fire(1, v), 2, 2, False),
    ('RED_FIRE_TP', 'red_fire_tp', lambda v: red_fire(2, v), 2, 2, False),
    ('RED_BURST', 'red_burst', lambda v: red_burst(0, v), 1, 2, False),
    ('RED_BURST_MAX', 'red_burst_max', lambda v: red_burst(1, v), 3, 2, False),
    ('RED_BURST_TP', 'red_burst_tp', lambda v: red_burst(2, v), 3, 2, False),
    ('PURPLE_FIRE', 'purple_fire', lambda v: purple_fire(0), 2, 1, False),
    ('PURPLE_FIRE_200', 'purple_fire_200', lambda v: purple_fire(1), 3, 1, False),
    ('PURPLE_FIRE_TP', 'purple_fire_tp', lambda v: purple_fire(2), 3, 1, False),
    ('HOLLOW_FORM', 'hollow_form', lambda v: hollow_form(), 3, 1, False),
    ('BLACK_FLASH', 'black_flash', lambda v: black_flash(False, v), 3, 2, False),
    ('BLACK_FLASH_FIST', 'black_flash_fist', lambda v: black_flash(True, v), 3, 2, False),
    ('TP_DEPART', 'tp_depart', lambda v: tp_depart(), -3, 1, False),
    ('TP_ARRIVE', 'tp_arrive', lambda v: tp_arrive(), -3, 1, False),
    ('DODGE', 'dodge', dodge, -7, 2, False),
    ('JUMP', 'jump', jump, -10, 2, False),
    ('LAND_LIGHT', 'land_light', lambda v: land(False, v), -8, 2, False),
    ('LAND_HEAVY', 'land_heavy', lambda v: land(True, v), -2, 2, False),
    ('VOID_OPEN', 'void_open', lambda v: void_open(), 4, 1, False),   # v0.16：领域展开接在视频后面，再响一些
    ('VOID_LOOP', 'void_loop', lambda v: void_loop(), -18, 1, True),
    ('VOID_CLOSE', 'void_close', lambda v: void_close(), -5, 1, False),
    ('INFINITY_BLOCK', 'infinity_block', infinity_block, -2, 3, False),
    ('CINE_RISE', 'cine_rise', lambda v: cine_rise(), -5, 1, False),
    ('CINE_CONTACT', 'cine_contact', lambda v: cine_contact(), 1, 1, False),
    ('CINE_BLAST', 'cine_blast', lambda v: cine_blast(), 4, 1, False),
    ('SHOULDER_TAP', 'shoulder_tap', lambda v: shoulder_tap(), -7, 1, False),
    ('HEAD_TURN', 'head_turn', lambda v: head_turn(), -10, 1, False),
    ('WORLD_SIGN', 'world_sign', lambda v: world_sign(False), -3, 1, False),
    ('WORLD_SIGN_CRACK', 'world_sign_crack', lambda v: world_sign(True), -3, 1, False),
    ('SUKUNA_CHANT', 'sukuna_chant', lambda v: sukuna_chant(v), -4, 3, False),  # 变体 = 第几段
    ('LOCK_WORLD', 'lock_world', lambda v: slash_lock(False), 0, 1, False),
    ('LOCK_SPACE', 'lock_space', lambda v: slash_lock(True), 1, 1, False),
    ('SLASH_DISMANTLE', 'slash_dismantle', lambda v: slash(0, v), -1, 2, False),
    ('SLASH_WORLD', 'slash_world', lambda v: slash(1, v), 2, 2, False),
    ('SLASH_SPACE', 'slash_space', lambda v: slash(2, v), 4, 1, False),
    ('SLASH_CINE', 'slash_cine', lambda v: slash(3, v), -2, 1, False),
    ('AMP_ON', 'amp_on', lambda v: amp_on(), -5, 1, False),
    ('AMP_LOOP', 'amp_loop', lambda v: amp_loop(), -18, 1, True),
    ('AMP_OFF', 'amp_off', lambda v: amp_off(), -8, 1, False),
    ('RCT_LOOP', 'rct_loop', lambda v: rct_loop(), -12, 1, True),
    ('AGITO_SIGN', 'agito_sign', lambda v: agito(0), -7, 1, False),
    ('AGITO_FLY', 'agito_fly', lambda v: agito(1), -4, 1, False),
    ('AGITO_ZAP', 'agito_zap', lambda v: agito(2), 0, 1, False),
    ('SUMMON_CHANT', 'summon_chant', lambda v: summon_chant(), -5, 1, False),
    ('MAHORAGA_RISE', 'mahoraga_rise', lambda v: mahoraga_rise(), 1, 1, False),
    ('TRANSFORM_START', 'transform_start', lambda v: transform(False), 1, 1, False),
    ('TRANSFORM_DONE', 'transform_done', lambda v: transform(True), -2, 1, False),
    ('SPACE_WINDUP', 'space_windup', lambda v: space_windup(), -3, 1, False),
    ('SPACE_KILL', 'space_kill', lambda v: space_kill(), 2, 1, False),
    ('EXHAUST', 'exhaust', lambda v: exhaust(), -5, 1, False),
    ('SHRINE_SIGN', 'shrine_sign', lambda v: shrine_sign(), -4, 1, False),
    ('SHRINE_OPEN', 'shrine_open', lambda v: shrine_open(), 4, 1, False),
    ('SHRINE_WARN', 'shrine_warn', lambda v: shrine_warn(), -3, 1, False),
    ('SHRINE_SLASH_HIT', 'shrine_slash_hit', lambda v: shrine_slash(True, v), 0, 2, False),
    ('SHRINE_SLASH_MISS', 'shrine_slash_miss', lambda v: shrine_slash(False, v), -5, 2, False),
    ('CLASH_LOOP', 'clash_loop', lambda v: clash_loop(), -10, 1, True),
    ('BREAK_SHRINE', 'break_shrine', lambda v: domain_break(False), 1, 1, False),
    ('BREAK_VOID', 'break_void', lambda v: domain_break(True), 1, 1, False),
    ('SHRINE_END', 'shrine_end', lambda v: shrine_end(), -5, 1, False),
    ('RESTORE_BURN', 'restore_burn', lambda v: restore(False), -8, 1, False),
    ('RESTORE_EXHAUST', 'restore_exhaust', lambda v: restore(True), -8, 1, False),
    ('WHEEL_CLICK', 'wheel_click', wheel_click, -2, 3, False),
    ('ADAPT_WORLD', 'adapt_world', lambda v: adapt_complete(False), 0, 1, False),
    ('ADAPT_INFINITY', 'adapt_infinity', lambda v: adapt_complete(True), 0, 1, False),
    ('MAHO_PUNCH_WINDUP', 'maho_punch_windup', lambda v: maho_windup(0), -5, 1, False),
    ('MAHO_SWORD_WINDUP', 'maho_sword_windup', lambda v: maho_windup(1), -4, 1, False),
    ('MAHO_CINE_SWING', 'maho_cine_swing', lambda v: maho_windup(2), -3, 1, False),
    ('MAHORAGA_FALL', 'mahoraga_fall', lambda v: mahoraga_fall(), 1, 1, False),
    ('HIT_LIGHT', 'hit_light', lambda v: hit(0, True, v), -4, 3, False),
    ('HIT_LIGHT_NB', 'hit_light_nb', lambda v: hit(0, False, v), -4, 2, False),
    ('HIT_MEDIUM', 'hit_medium', lambda v: hit(1, True, v), 0, 3, False),
    ('HIT_MEDIUM_NB', 'hit_medium_nb', lambda v: hit(1, False, v), 0, 2, False),
    ('HIT_HEAVY', 'hit_heavy', lambda v: hit(2, True, v), 2, 2, False),
    ('KO', 'ko', lambda v: ko(), -2, 1, False),
    ('DOWN_THUD', 'down_thud', down_thud, -4, 2, False),
    ('SIMPLE_OPEN', 'simple_open', lambda v: simple_open(), -7, 1, False),   # v0.17 简易领域
    ('SIMPLE_ERODE', 'simple_erode', simple_erode, -9, 2, False),
    ('SIMPLE_BREAK', 'simple_break', lambda v: simple_break(), -5, 1, False),
    ('SIG_START', 'sig_start', lambda v: signal([440, 660], 0.16), -5, 1, False),
    ('SIG_REVIVE', 'sig_revive', lambda v: signal([330, 440, 660], 0.09), -5, 1, False),
    ('SIG_WIN', 'sig_win', lambda v: signal([440, 554.37, 660], 0.17), -2, 1, False),
    ('SIG_LOSE', 'sig_lose', lambda v: signal([440, 369.99, 293.66], 0.14), -4, 1, False),
]

LOOP_SLOTS = 3  # 循环位：0 苍的吸引、1 领域（无量空处 / 对撞）、2 宿傩身上（展延 / 反转术式）


def main():
    # --only a,b：只重写这几个音效的文件（其余照样合成一遍，用来算时长、写 SfxBank，但不动已有的文件）
    only = None
    for a in sys.argv[1:]:
        if a.startswith('--only='):
            only = set(a[len('--only='):].split(','))
    os.makedirs(RAW, exist_ok=True)
    if only is None:
        for f in os.listdir(RAW):
            if f.startswith('sfx_') and (f.endswith('.ogg') or f.endswith('.wav')):
                os.remove(os.path.join(RAW, f))
    names, durs, total_bytes, total_sec = [], [], 0, 0.0
    report = []
    tmp = tempfile.mkdtemp()
    for idx, (const, name, fn, gain, nvar, loop) in enumerate(CUES):
        files, dmax = [], 0.0
        for v in range(nvar):
            _seed[0] = 1000 + 37 * idx + 1000 * v
            x = np.asarray(fn(v), dtype=np.float64)
            assert np.all(np.isfinite(x)), name
            x = master(x, loop)
            x = level(x, gain, loop)
            files.append(f'sfx_{name}_{v}')
            dmax = max(dmax, len(x) / SR)
            if only is not None and name not in only:
                continue
            wav = os.path.join(tmp, f'{name}_{v}.wav')
            sf.write(wav, x.astype(np.float32), SR, subtype='FLOAT')
            if loop:  # 循环音存 16 位 WAV：libvorbis 编码会丢掉末尾一块，循环就接不上了
                ogg = os.path.join(RAW, f'sfx_{name}_{v}.wav')
                sf.write(ogg, np.clip(x, -1, 1), SR, subtype='PCM_16')
            else:
                ogg = os.path.join(RAW, f'sfx_{name}_{v}.ogg')
                # bitexact：Ogg 流编号不随机，同样的声音每次生成的文件一字不差（不然重新生成会让全部文件都“变了”）
                subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-fflags', '+bitexact', '-flags:a', '+bitexact',
                                '-c:a', 'libvorbis', '-q:a', '3', '-ac', '1', '-ar', str(SR), ogg], check=True)
            total_bytes += os.path.getsize(ogg)
            total_sec += len(x) / SR
            pk = 20 * np.log10(np.max(np.abs(x)) + 1e-12)
            report.append(f'{name}_{v}: {len(x) / SR:.3f}s peak {pk:.1f} dBFS rms80 {20 * np.log10(rms80(x) + 1e-12):.1f}')
        names.append((const, files, loop))
        durs.append(dmax)
    write_java(names, durs)
    print('\n'.join(report))
    print(f'{len(CUES)} 个音效，{sum(len(f) for _, f, _ in names)} 个文件，共 {total_sec:.1f} 秒，{total_bytes / 1024:.0f} KB')


def write_java(names, durs):
    lines = ['package com.shinjuku.showdown;', '',
             '/** 由 scripts/game_sfx.py 生成，请勿手改：音效编号、每个编号的资源（变体）、时长（毫秒）、是否循环。 */',
             'final class SfxBank {', '    private SfxBank() {', '    }', '']
    consts = ', '.join(f'{c} = {i}' for i, (c, _, _) in enumerate(names))
    lines.append(f'    static final int {consts};')
    lines.append(f'    static final int COUNT = {len(names)}, LOOP_SLOTS = {LOOP_SLOTS};')
    lines.append('    static final int[][] RES = {')
    for c, files, _ in names:
        lines.append('            {' + ', '.join(f'R.raw.{f}' for f in files) + '},')
    lines.append('    };')
    lines.append('    static final int[] MS = {' + ', '.join(str(int(round(d * 1000))) for d in durs) + '};')
    lines.append('    static final boolean[] LOOP = {' + ', '.join('true' if l else 'false' for _, _, l in names) + '};')
    lines.append('}')
    open(JAVA, 'w').write('\n'.join(lines) + '\n')


if __name__ == '__main__':
    main()
