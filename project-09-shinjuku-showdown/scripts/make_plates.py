"""背景与立绘的预处理：Luna 生成的原图（scratchpad，不进仓库）→ public/plates、public/portraits。

- 绘画背景：Lanczos 放大 1.5 倍（运行时基本 1:1 绘制，少一次双线性放大）。
- 游戏像素背景（伏魔御厨子、无量空处）：最近邻放大 3 倍保持像素边缘。
- 立绘：纯绿底抠像 + 去绿边。
"""
import os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/-home-user-codex-remotion-project/d680fd9a-795b-5f20-aab9-eb13275296d1/scratchpad/film/img'
GAME = os.path.join(os.path.dirname(ROOT), 'android-shinjuku/app/src/main/res/drawable-nodpi')
os.makedirs(os.path.join(ROOT, 'public/plates'), exist_ok=True)
os.makedirs(os.path.join(ROOT, 'public/portraits'), exist_ok=True)

PAINT = {'aerial': 'aerial/aerial.png', 'street': 'street/street.png', 'rooftop': 'rooftop/rooftop.png',
         'crater': 'crater/crater.png', 'airport': 'airport/airport.png',
         'skyplate': 'skyplate/skyplate.png', 'skysunset': 'skysunset/skysunset.png', 'voidplate': 'voidplate/voidplate.png'}
for name, rel in PAINT.items():
    p = os.path.join(SRC, rel)
    out = os.path.join(ROOT, 'public/plates', name + '.jpg')
    if not os.path.exists(p):
        if not os.path.exists(out):
            print('missing', name)
        continue
    im = Image.open(p).convert('RGB')
    im = im.resize((round(im.width * 1.5), round(im.height * 1.5)), Image.LANCZOS)
    im.save(out, quality=93, subsampling=0)
    print(name, im.size)

for name, f in {'shrine': 'bg_shrine_px.png', 'void': 'bg_void_px.png', 'battle': 'bg_battle_px.png'}.items():
    im = Image.open(os.path.join(GAME, f)).convert('RGB')
    im = im.resize((im.width * 3, im.height * 3), Image.NEAREST)
    im.save(os.path.join(ROOT, 'public/plates', name + '.png'), compress_level=3)
    print(name, im.size)

for name in ('gojo', 'sukuna'):
    p = os.path.join(SRC, f'portrait_{name}/portrait_{name}.png')
    if not os.path.exists(p):
        continue
    a = np.asarray(Image.open(p).convert('RGB')).astype(np.float32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    greenness = g - np.maximum(r, b)          # 纯绿底约 255
    alpha = np.clip(1 - (greenness - 40) / 120, 0, 1)
    spill = np.clip(greenness / 255, 0, 1)
    g2 = np.where(greenness > 0, np.maximum(r, b) + (g - np.maximum(r, b)) * (1 - spill), g)  # 去绿边
    rgba = np.dstack([r, g2, b, alpha * 255]).clip(0, 255).astype(np.uint8)
    im = Image.fromarray(rgba, 'RGBA')
    bbox = im.getbbox()
    im = im.crop(bbox)
    im.save(os.path.join(ROOT, 'public/portraits', name + '.png'))
    print('portrait', name, im.size)
