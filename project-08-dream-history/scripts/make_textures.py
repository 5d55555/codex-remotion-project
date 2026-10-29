"""生成幕布的纹理：细密的布纹、几处水渍般的深浅、零星的纤维。输出灰度 PNG，叠在幕布上做正片叠底。"""
import numpy as np
import zlib, struct, os

W, H = 1740, 812
rng = np.random.default_rng(7)

def blur(a, k):
    # 盒式模糊若干次，近似高斯
    for _ in range(3):
        c = np.cumsum(np.pad(a, ((0, 0), (k, k)), mode='edge'), axis=1)
        a = (c[:, 2 * k:] - c[:, :-2 * k]) / (2 * k)
        c = np.cumsum(np.pad(a, ((k, k), (0, 0)), mode='edge'), axis=0)
        a = (c[2 * k:, :] - c[:-2 * k, :]) / (2 * k)
    return a

y, x = np.mgrid[0:H, 0:W].astype(np.float32)
weave = 0.5 + 0.25 * np.sin(x * 2.1) * np.sin(y * 2.1) + 0.1 * np.sin(x * 0.9 + np.sin(y * 0.05) * 3)
blot = blur(rng.standard_normal((H, W)).astype(np.float32), 40)
blot = (blot - blot.min()) / (blot.max() - blot.min())
fib = np.zeros((H, W), np.float32)
for _ in range(500):
    x0, y0 = rng.integers(0, W), rng.integers(0, H)
    ln = rng.integers(10, 50)
    ang = rng.uniform(0, np.pi)
    for t in range(ln):
        xi, yi = int(x0 + np.cos(ang) * t), int(y0 + np.sin(ang) * t)
        if 0 <= xi < W and 0 <= yi < H:
            fib[yi, xi] = 1
fib = blur(fib, 1)
img = 0.82 + 0.06 * (weave - 0.5) + 0.14 * (blot - 0.5) - 0.18 * fib
img = np.clip(img, 0, 1)
a = (img * 255).astype(np.uint8)

def png(path, arr):
    h, w = arr.shape
    raw = b''.join(b'\x00' + arr[i].tobytes() for i in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)

os.makedirs('public/tex', exist_ok=True)
png('public/tex/screen.png', a)
print('ok', a.mean())
