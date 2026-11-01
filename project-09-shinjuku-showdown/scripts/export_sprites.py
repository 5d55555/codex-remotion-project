"""游戏的像素角色源文件 → public/sprites/*.png + src/data/sprites.json（尺寸、关节点）。

源文件是 android-shinjuku/art/pixel/characters.txt，解析器复用 android-shinjuku/scripts/pixel_art.py。
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GAME = os.path.join(os.path.dirname(ROOT), 'android-shinjuku')
sys.path.insert(0, os.path.join(GAME, 'scripts'))
import pixel_art  # noqa: E402

palettes, sprites = pixel_art.parse(os.path.join(GAME, 'art/pixel/characters.txt'))
out = {}
os.makedirs(os.path.join(ROOT, 'public/sprites'), exist_ok=True)
for name, sp in sprites.items():
    if name.startswith('digit') or name.startswith('font'):
        continue
    im = pixel_art.to_image(sp, palettes)
    im.save(os.path.join(ROOT, 'public/sprites', name + '.png'))
    out[name] = {'w': im.width, 'h': im.height, 'px': sp['pivot'][0], 'py': sp['pivot'][1]}
    if name.endswith('.head'):  # 眼睛（调色板里 B/L 两种颜色）的左右两团中心，相对关节点
        pts = [(x, y) for y, r in enumerate(sp['rows']) for x, ch in enumerate(r) if ch in 'BL']
        if pts:
            mid = sum(x for x, _ in pts) / len(pts)
            eyes = []
            for side in (lambda x: x < mid, lambda x: x >= mid):
                q = [(x, y) for x, y in pts if side(x)]
                if q:
                    eyes.append([round(sum(x for x, _ in q) / len(q) + 0.5 - sp['pivot'][0], 1),
                                 round(sum(y for _, y in q) / len(q) + 0.5 - sp['pivot'][1], 1)])
            out[name]['eyes'] = eyes
json.dump(out, open(os.path.join(ROOT, 'src/data/sprites.json'), 'w'), indent=1)
print(len(out), 'sprites:', ' '.join(out))
