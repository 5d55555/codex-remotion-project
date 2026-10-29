"""像素角色的源文件 art/pixel/characters.txt → 预览图 / Java 代码。

源文件格式（# 开头是注释）：
  palette 名字
    字符 RRGGBB  说明
  end
  sprite 名字 palette 调色板名 pivot x y
    一行一行的像素（. 是透明）
  end

用法：
  python3 scripts/pixel_art.py preview OUT.png [缩放]   # 所有部件排成一张放大的预览图
  python3 scripts/pixel_art.py java                     # 生成 app/src/main/java/com/shinjuku/showdown/PixelArt.java
"""
import sys, os, re
from PIL import Image, ImageDraw

SRC = 'art/pixel/characters.txt'


def parse(path=SRC):
    palettes, sprites = {}, {}
    cur = None
    for raw in open(path, encoding='utf-8'):
        line = raw.rstrip('\n')
        if not line.strip() or line.strip().startswith('#'):
            continue
        t = line.split()
        if cur is None:
            if t[0] == 'palette':
                cur = ('p', t[1], {})
            elif t[0] == 'sprite':
                kv = dict(zip(t[2::2], t[3::2])) if len(t) > 2 else {}
                args = t[2:]
                pal = args[args.index('palette') + 1]
                pi = args.index('pivot')
                cur = ('s', t[1], {'palette': pal, 'pivot': (int(args[pi + 1]), int(args[pi + 2])), 'rows': []})
            continue
        if t[0] == 'end':
            if cur[0] == 'p':
                palettes[cur[1]] = cur[2]
            else:
                rows = cur[2]['rows']
                w = max(len(r) for r in rows)
                cur[2]['rows'] = [r.ljust(w, '.') for r in rows]
                sprites[cur[1]] = cur[2]
            cur = None
            continue
        if cur[0] == 'p':
            cur[2][t[0]] = (int(t[1], 16), ' '.join(t[2:]))
        else:
            cur[2]['rows'].append(line.strip())
    return palettes, sprites


def to_image(sp, palettes):
    pal = palettes[sp['palette']]
    rows = sp['rows']
    im = Image.new('RGBA', (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == '.':
                continue
            if ch not in pal:
                raise SystemExit(f'{sp}: 调色板里没有字符 {ch!r}')
            c = pal[ch][0]
            im.putpixel((x, y), ((c >> 16) & 255, (c >> 8) & 255, c & 255, 255))
    return im


def preview(out, scale=8):
    palettes, sprites = parse()
    ims = [(n, to_image(s, palettes), s) for n, s in sprites.items()]
    pad = 4
    W = sum(i.width for _, i, _ in ims) + pad * (len(ims) + 1)
    H = max(i.height for _, i, _ in ims) + pad * 2
    sheet = Image.new('RGBA', (W, H), (96, 110, 140, 255))
    x = pad
    for n, im, s in ims:
        sheet.alpha_composite(im, (x, pad))
        x += im.width + pad
    big = sheet.resize((W * scale, H * scale), Image.NEAREST)
    d = ImageDraw.Draw(big)
    x = pad
    for n, im, s in ims:
        px, py = s['pivot']
        cx, cy = (x + px) * scale + scale // 2, (pad + py) * scale + scale // 2
        d.rectangle([cx - 2, cy - 2, cx + 2, cy + 2], outline=(255, 0, 0, 255))
        d.text((x * scale, (pad + im.height) * scale + 2), n, fill=(255, 255, 255, 255))
        x += im.width + pad
    big.save(out)
    print(out, big.size)


def java():
    palettes, sprites = parse()
    out = ['package com.shinjuku.showdown;', '',
           '// 由 scripts/pixel_art.py 从 art/pixel/characters.txt 生成，不要手改。',
           '', '/** 像素角色的部件：每个部件一行行的字符，每个字符对应调色板里的一个颜色（. 是透明）。 */',
           'final class PixelArt {', '    private PixelArt() {', '    }', '']
    for pn, pal in palettes.items():
        keys = ''.join(pal.keys())
        cols = ', '.join('0xFF%06X' % pal[k][0] for k in pal)
        out.append(f'    static final String PAL_{pn.upper()}_KEYS = "{keys}";')
        out.append(f'    static final int[] PAL_{pn.upper()} = {{{cols}}};')
    out.append('')
    for sn, sp in sprites.items():
        name = re.sub(r'[^A-Za-z0-9]', '_', sn).upper()
        out.append(f'    static final int {name}_PX = {sp["pivot"][0]}, {name}_PY = {sp["pivot"][1]};')
        out.append(f'    static final String {name}_PAL = "{sp["palette"]}";')
        out.append(f'    static final String[] {name} = {{')
        for r in sp['rows']:
            out.append(f'            "{r}",')
        out.append('    };')
        out.append('')
    out.append('}')
    path = 'app/src/main/java/com/shinjuku/showdown/PixelArt.java'
    open(path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
    print(path, len(sprites), 'sprites')


if __name__ == '__main__':
    if sys.argv[1] == 'preview':
        preview(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 8)
    else:
        java()
