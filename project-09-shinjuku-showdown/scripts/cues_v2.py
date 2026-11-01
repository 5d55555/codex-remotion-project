"""第二版音效提示：分镜表每个切点的音效码 → 合成音效名，再加几处大场面的专门音效，写 out/cues.json。"""
import json, os, re
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
rows = json.load(open(os.path.join(ROOT, 'src/v2/edl.json')))
rows = [r for i, r in enumerate(rows) if i + 1 >= len(rows) or rows[i + 1]['t'] > r['t'] + 1 / 120]
T0, BEAT = 0.0144, 0.426145
B = lambda m, b=1: T0 + BEAT * (2 + 4 * m + (b - 1))

MAP = [  # (正则, 音效, 增益 dB)
    (r'切＋极短低频', None, 0),
    (r'^切|切尾', 'slash', -6),
    (r'电流|断电', 'fizzle', -8),
    (r'^电', 'black_flash', -2),
    (r'砰', 'punch_heavy', -4),
    (r'^拳|握拳', 'punch', -6),
    (r'嗖', 'whoosh', -11),
    (r'^吸', 'suck_short', -15),
    (r'低频', 'pulse', -10),
    (r'裂|碎石|碎片', 'glass_crack', -8),
    (r'低鸣', 'blue_hum', -13),
    (r'铃', 'wheel', -6),
    (r'金属', 'sword', -6),
    (r'咏唱', 'chant', -10),
    (r'布', 'swish', -15),
    (r'汽', 'rct', -12),
    (r'鞋底|脚步', 'step', -13),
    (r'水', 'jet', -8),
    (r'风', 'wind', -16),
    (r'耳鸣', 'ring', -18),
]
cues = []
for r in rows:
    s = r['sfx']
    for pat, name, gain in MAP:
        if re.search(pat, s):
            if name:
                cues.append([round(r['t'], 4), name, gain, 0, r['img']])
            break
    # 冲击帧再垫一记低频
    if re.search('冲白|冲反', r['fx']) and r['move'] == '击':
        cues.append([round(r['t'], 4), 'thud', -12, 0, r['img']])
    if '黑闪' in r['fx']:
        cues.append([round(r['t'], 4), 'black_flash', -2, 0, r['img']])

SPECIAL = [
    (0.0, 'wind', -10), (B(0), 'swell', -14),
    (B(32, 2), 'suck', -6),
    (B(33), 'purple_hit', 0), (B(33) + 0.02, 'subdrop', -2), (B(33, 3), 'collapse', -6),
    (B(48, 4), 'sign', -6), (B(48, 4.5), 'domain_void', -2), (B(49, 2), 'domain_shrine', -2),
    (B(64, 2), 'rise', -6), (B(64, 4), 'glass_big', 0),
    (B(83), 'red_fire', -2),
    (B(93), 'purple_form', -3), (B(93, 3), 'reverse_suck', -6),
    (B(94), 'purple_boom', 1), (B(94) + 0.02, 'subdrop', 0), (B(94, 2.5), 'shockwave', -3), (B(94, 4), 'rumble', -6),
    (B(97, 3), 'heartbeat', -8),
    (B(98, 2), 'world_cut', 1), (B(98, 2) + 0.02, 'subdrop', -2),
    (B(104, 4), 'airport_air', -14),
]
for t, n, g in SPECIAL:
    cues.append([round(t, 4), n, g, 0, 'special'])
cues.sort(key=lambda c: c[0])
json.dump(cues, open(os.path.join(ROOT, 'out/cues.json'), 'w'))
from collections import Counter
print(len(cues), 'cues', Counter(c[1] for c in cues).most_common())
