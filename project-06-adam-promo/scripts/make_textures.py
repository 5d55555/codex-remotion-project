"""程序化生成老电影质感用的纹理：胶片颗粒、新闻纸、海报纸。只用 numpy + PIL，固定随机种子。

用法：python3 scripts/make_textures.py   输出到 public/tex/{grain,newsprint,poster}.png
"""
from pathlib import Path

import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / 'public' / 'tex'
rng = np.random.default_rng(1352)


def blur_noise(h, w, sigma):
    n = rng.standard_normal((h, w)).astype(np.float32)
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.rfftfreq(w)[None, :]
    k = np.exp(-2 * (np.pi * sigma) ** 2 * (fx ** 2 + fy ** 2))
    out = np.fft.irfft2(np.fft.rfft2(n) * k, s=(h, w)).astype(np.float32)
    return out / (out.std() + 1e-6)


def grain(n=512):
    # 中性灰底上的细颗粒，叠加时用 overlay，透明度在片中控制。
    g = rng.standard_normal((n, n)).astype(np.float32) * 0.55 + blur_noise(n, n, 1.2) * 0.45
    v = np.clip(0.5 + g * 0.16, 0, 1)
    return np.dstack([v, v, v])


def paper(w, h, base, fiber=0.04, blot=0.06):
    img = np.ones((h, w, 3), np.float32) * np.array(base, np.float32)
    shade = blur_noise(h, w, 60) * blot + blur_noise(h, w, 6) * fiber * 0.6 + rng.standard_normal((h, w)).astype(np.float32) * fiber * 0.4
    img *= (1 + shade)[..., None]
    # 边缘发黄变暗
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.maximum(np.abs(x / w - 0.5), np.abs(y / h - 0.5)) * 2
    img *= (1 - 0.22 * np.clip(d - 0.75, 0, 1) / 0.25)[..., None]
    return np.clip(img, 0, 1)


def save(arr, name):
    OUT.mkdir(parents=True, exist_ok=True)
    Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), 'RGB').save(OUT / name, optimize=True)
    print('wrote', OUT / name)


save(grain(), 'grain.png')
save(paper(1400, 1000, (0.93, 0.89, 0.80)), 'newsprint.png')
save(paper(1200, 1600, (0.90, 0.84, 0.70), fiber=0.05, blot=0.09), 'poster.png')
