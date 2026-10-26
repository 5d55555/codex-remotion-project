"""程序化生成三张背景纹理：旧纸、红纸、深蓝。只用 numpy + PIL，固定随机种子，结果可复现。

用法：python3 scripts/make_textures.py   输出到 public/tex/{paper,red,navy}.png
"""
from pathlib import Path

import numpy as np
from PIL import Image

W, H = 1920, 1080
OUT = Path(__file__).resolve().parent.parent / 'public' / 'tex'


def blurred_noise(rng, sigma):
    """高斯模糊的白噪声（FFT 实现），归一化到均值 0、标准差 1。"""
    n = rng.standard_normal((H, W)).astype(np.float32)
    fy = np.fft.fftfreq(H)[:, None]
    fx = np.fft.rfftfreq(W)[None, :]
    kernel = np.exp(-2 * (np.pi * sigma) ** 2 * (fx ** 2 + fy ** 2))
    out = np.fft.irfft2(np.fft.rfft2(n) * kernel, s=(H, W)).astype(np.float32)
    return out / (out.std() + 1e-6)


def fibers(rng, count, length):
    """纸张纤维：随机方向的短细线，返回 0—1 的强度图。"""
    img = np.zeros((H, W), np.float32)
    xs = rng.uniform(0, W, count)
    ys = rng.uniform(0, H, count)
    angles = rng.uniform(0, np.pi, count)
    for t in np.linspace(0, 1, length):
        px = np.clip((xs + np.cos(angles) * t * length).astype(int), 0, W - 1)
        py = np.clip((ys + np.sin(angles) * t * length).astype(int), 0, H - 1)
        img[py, px] = 1.0
    return img


def vignette(strength):
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((x - W * 0.52) / (W * 0.62)) ** 2 + ((y - H * 0.48) / (H * 0.62)) ** 2)
    return -strength * np.clip(d - 0.35, 0, None) ** 1.6


def texture(seed, base, blotch, mid, grain, fiber, vig, center):
    rng = np.random.default_rng(seed)
    lum = (
        blotch * blurred_noise(rng, 140)
        + 0.6 * blotch * blurred_noise(rng, 40)
        + mid * blurred_noise(rng, 6)
        + grain * rng.standard_normal((H, W)).astype(np.float32)
        - fiber * fibers(rng, 2500, 14)
        + vignette(vig)
    )
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    lum += center * np.exp(-(((x - W * 0.5) / (W * 0.35)) ** 2 + ((y - H * 0.46) / (H * 0.4)) ** 2))
    rgb = np.array(base, np.float32)[None, None, :] + lum[:, :, None]
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), 'RGB')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    specs = {
        'paper': dict(seed=11, base=(222, 214, 194), blotch=4.5, mid=1.6, grain=3.2, fiber=6, vig=30, center=9),
        'red': dict(seed=23, base=(188, 36, 28), blotch=5.0, mid=1.8, grain=3.0, fiber=5, vig=34, center=6),
        'navy': dict(seed=37, base=(19, 27, 43), blotch=2.6, mid=1.0, grain=2.2, fiber=2, vig=14, center=3),
    }
    for name, spec in specs.items():
        texture(**spec).save(OUT / f'{name}.png', optimize=True)
        print('wrote', OUT / f'{name}.png')


if __name__ == '__main__':
    main()
