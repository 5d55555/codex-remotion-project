"""关键帧联系表：python3 scripts/frames_sheet.py OUT.jpg f001 f002 …（或 'all'、或范围 f001-f020）"""
import os, sys
from PIL import Image, ImageDraw
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = sys.argv[1]
args = sys.argv[2:]
have = sorted(f[:-4] for f in os.listdir(os.path.join(ROOT, 'public/frames')) if f.endswith('.jpg'))
ids = []
for a in args:
    if a == 'all':
        ids += have
    elif '-' in a:
        lo, hi = a.split('-')
        ids += [i for i in have if lo <= i <= hi]
    else:
        ids.append(a)
ids = [i for i in ids if i in have]
cols, tw, th = 4, 480, 270
rows = (len(ids) + cols - 1) // cols
sheet = Image.new('RGB', (cols * tw, max(1, rows) * th), (20, 20, 20))
d = ImageDraw.Draw(sheet)
for n, i in enumerate(ids):
    im = Image.open(os.path.join(ROOT, 'public/frames', i + '.jpg')).convert('RGB').resize((tw, th))
    x, y = (n % cols) * tw, (n // cols) * th
    sheet.paste(im, (x, y))
    d.rectangle([x, y, x + 46, y + 16], fill=(0, 0, 0))
    d.text((x + 4, y + 3), i, fill=(255, 255, 0))
sheet.save(out, quality=82)
print(len(ids), 'frames')
