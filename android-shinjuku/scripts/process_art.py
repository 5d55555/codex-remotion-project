"""把 art/ 里 Luna 生成的原图处理成应用资源：缩放到实际需要的尺寸，转成 WebP。
背景：有损 WebP（q=82），宽度不超过 1920；图标、徽章：带透明通道的 WebP（q=90），缩到够用的边长。
用法：python3 scripts/process_art.py
"""
import os
from PIL import Image

ART = 'art'
OUT = 'app/src/main/res/drawable-nodpi'
os.makedirs(OUT, exist_ok=True)

JOBS = {
    # 文件名: (最大边长, 是否保留透明)
    'bg_select.png': (1920, False),
    'emblem_miwa.png': (360, True),
    'emblem_uraume.png': (360, True),
    'emblem_yorozu.png': (360, True),
    'emblem_strongest.png': (360, True),
}

def pixel_background():
    """像素战斗背景：Luna 的“像素风”原图像素并不对齐网格，这里按面积缩到 266 高（每个像素块就是游戏里的一个虚拟像素），
    量化到 64 色、不抖动，边缘锐利；上面再补 80 行分档的天空，给比较高的屏幕用。存成无损 PNG。"""
    src = os.path.join(ART, 'bg_battle_px.png')
    if not os.path.exists(src):
        print('缺少', src)
        return
    im = Image.open(src).convert('RGB')
    H, SKY = 266, 80
    W = round(im.width * H / im.height)
    small = im.resize((W, H), Image.BOX)
    q = small.quantize(colors=64, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert('RGB')
    top = [q.getpixel((x, 0)) for x in range(0, W, 7)]
    avg = tuple(sum(c[i] for c in top) // len(top) for i in range(3))
    deep = (max(0, avg[0] - 34), max(0, avg[1] - 30), min(255, avg[2] - 6))
    out = Image.new('RGB', (W, H + SKY), avg)
    bands = 5
    for b in range(bands):
        k = b / (bands - 1)
        c = tuple(round(deep[i] + (avg[i] - deep[i]) * k) for i in range(3))
        y0, y1 = round(SKY * b / bands), round(SKY * (b + 1) / bands)
        out.paste(c, (0, y0, W, y1))
    out.paste(q, (0, SKY))
    dst = os.path.join(OUT, 'bg_battle_px.png')
    out.save(dst, optimize=True)
    print(f'{dst}  {out.width}x{out.height}  {os.path.getsize(dst) // 1024} KB')


pixel_background()

for name, (edge, alpha) in JOBS.items():
    src = os.path.join(ART, name)
    if not os.path.exists(src):
        print('缺少', src)
        continue
    im = Image.open(src)
    im = im.convert('RGBA' if alpha else 'RGB')
    if alpha:
        # 裁掉四周全透明的边，让图标内容撑满
        box = im.getchannel('A').point(lambda a: 255 if a > 8 else 0).getbbox()
        if box:
            im = im.crop(box)
    s = min(1.0, edge / max(im.size))
    if s < 1.0:
        im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    dst = os.path.join(OUT, os.path.splitext(name)[0] + '.webp')
    im.save(dst, 'WEBP', quality=90 if alpha else 82, method=6)
    print(f'{dst}  {im.width}x{im.height}  {os.path.getsize(dst) // 1024} KB')
