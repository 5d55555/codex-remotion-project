"""程序化生成三张纹理：灰雾、混沌海、星空。只用 numpy + PIL，固定随机种子，结果可复现。

灰雾和混沌海是可平铺的（FFT 噪声天然周期），片中靠平移图案做流动。
用法：python3 scripts/make_textures.py   输出到 public/tex/{fog,chaos,stars}.png
"""
from pathlib import Path

import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / 'public' / 'tex'
rng = np.random.default_rng(20261027)


def fractal(n, beta):
    """1/f^beta 噪声，n×n，可平铺，归一化到均值 0、标准差 1。"""
    w = rng.standard_normal((n, n)).astype(np.float32)
    fy = np.fft.fftfreq(n)[:, None]
    fx = np.fft.rfftfreq(n)[None, :]
    f = np.sqrt(fx ** 2 + fy ** 2)
    f[0, 0] = 1
    out = np.fft.irfft2(np.fft.rfft2(w) * f ** (-beta / 2), s=(n, n)).astype(np.float32)
    return (out - out.mean()) / (out.std() + 1e-6)


def warp(field, dx, dy):
    """按位移场双线性重新采样（环绕取值，保持可平铺）。"""
    n = field.shape[0]
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    sx, sy = x + dx, y + dy
    x0, y0 = np.floor(sx).astype(int), np.floor(sy).astype(int)
    fx, fy = sx - x0, sy - y0
    at = lambda yy, xx: field[yy % n, xx % n]
    top = at(y0, x0) * (1 - fx) + at(y0, x0 + 1) * fx
    bot = at(y0 + 1, x0) * (1 - fx) + at(y0 + 1, x0 + 1) * fx
    return top * (1 - fy) + bot * fy


def hsv(h, s, v):
    i = np.floor(h * 6).astype(int) % 6
    f = h * 6 - np.floor(h * 6)
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    r = np.choose(i, [v, q, p, p, t, v])
    g = np.choose(i, [t, v, v, q, p, p])
    b = np.choose(i, [p, p, t, v, v, q])
    return np.stack([r, g, b], -1)


def fog(n=1024):
    a = fractal(n, 3.6)
    a = warp(a, 60 * fractal(n, 4.2), 60 * fractal(n, 4.2))
    alpha = np.clip((a + 0.5) / 2.4, 0, 1) ** 1.6
    rgb = np.ones((n, n, 3), np.float32) * np.array([0.86, 0.87, 0.9])
    return np.dstack([rgb, alpha])


def chaos(n=1024):
    # 域扭曲出大理石般的流纹；色相随流纹遍历全部颜色，暗纹把色带隔开。
    base = fractal(n, 4.0)
    m = warp(base, 140 * fractal(n, 4.4), 140 * fractal(n, 4.4))
    m = warp(m, 80 * fractal(n, 4.2), 80 * fractal(n, 4.2))
    h = (m * 0.3) % 1.0
    band = (0.5 + 0.5 * np.sin(m * 4.5)) ** 1.5
    v = 0.18 + 0.72 * band
    s = 0.75 + 0.2 * band
    return hsv(h, s, v)


def stars(w=1920, h=1080):
    img = np.zeros((h, w, 3), np.float32)
    # 两侧各一团极淡的星云：左边灰青，右边多彩。
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    neb = fractal(1024, 3.4)
    neb = np.array(Image.fromarray(((neb - neb.min()) / (np.ptp(neb)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)) / 255.0
    left = np.exp(-(((x - w * 0.2) / (w * 0.35)) ** 2 + ((y - h * 0.5) / (h * 0.6)) ** 2))
    right = np.exp(-(((x - w * 0.8) / (w * 0.35)) ** 2 + ((y - h * 0.5) / (h * 0.6)) ** 2))
    img += (neb ** 2 * left)[..., None] * np.array([0.16, 0.18, 0.22])
    img += (neb ** 3 * right)[..., None] * hsv((x / w * 0.8 + neb * 0.5) % 1, np.full_like(x, 0.7), np.full_like(x, 0.26))
    count = 2600
    sx, sy = rng.integers(0, w, count), rng.integers(0, h, count)
    br = rng.uniform(0.3, 1.0, count) ** 2
    img[sy, sx] += br[:, None]
    for _ in range(60):
        cx, cy = rng.integers(2, w - 2), rng.integers(2, h - 2)
        img[cy, cx - 2:cx + 3] += 0.35
        img[cy - 2:cy + 3, cx] += 0.35
    return np.clip(img, 0, 1)


def save(arr, name):
    OUT.mkdir(parents=True, exist_ok=True)
    mode = 'RGBA' if arr.shape[-1] == 4 else 'RGB'
    Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), mode).save(OUT / name, optimize=True)
    print('wrote', OUT / name)


save(fog(), 'fog.png')
save(chaos(), 'chaos.png')
save(stars(), 'stars.png')
