"""把 out/qa 里指定的静帧拼成一张联系表，方便一次看完。用法：python3 scripts/sheet.py OUT.jpg f1 f2 ...（帧号）"""
import sys
from PIL import Image, ImageDraw
out = sys.argv[1]
frames = [int(a) for a in sys.argv[2:]]
cols = 3
tw, th = 640, 360
rows = (len(frames) + cols - 1) // cols
sheet = Image.new('RGB', (cols * tw, rows * th), (20, 20, 20))
d = ImageDraw.Draw(sheet)
for i, f in enumerate(frames):
    im = Image.open(f'out/qa/f{f:05d}.png').convert('RGB').resize((tw, th))
    x, y = (i % cols) * tw, (i // cols) * th
    sheet.paste(im, (x, y))
    d.text((x + 6, y + 4), f'{f} ({f/60:.2f}s)', fill=(255, 255, 0))
sheet.save(out, quality=85)
