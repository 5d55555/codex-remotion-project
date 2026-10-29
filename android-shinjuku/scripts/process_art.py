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
    'bg_battle.png': (1920, False),
    'bg_select.png': (1920, False),
    'icon_music.png': (192, True),
    'emblem_miwa.png': (360, True),
    'emblem_uraume.png': (360, True),
    'emblem_yorozu.png': (360, True),
    'emblem_strongest.png': (360, True),
}

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
