"""让 Luna 看图标注每张关键帧的焦点（主点、脸、手、脚、光球、动作方向），合并进 src/v2/focus.json。
已标注的跳过；每 12 张一个任务，4 个并行。用法：CODEX_MODEL=<模型> python3 scripts/annotate.py [--all]
"""
import json, os, subprocess, sys, threading
from PIL import Image

MODEL = os.environ.get('CODEX_MODEL') or sys.exit('先设置 CODEX_MODEL（看图用的模型名）')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCR = '/tmp/claude-0/-home-user-codex-remotion-project/d680fd9a-795b-5f20-aab9-eb13275296d1/scratchpad/annot'
os.makedirs(SCR, exist_ok=True)
FOC = os.path.join(ROOT, 'src/v2/focus.json')
focus = json.load(open(FOC)) if os.path.exists(FOC) else {}
ids = sorted(f[:-4] for f in os.listdir(os.path.join(ROOT, 'public/frames')) if f.endswith('.jpg'))
if '--all' not in sys.argv:
    ids = [i for i in ids if i not in focus]
batches = [ids[i:i + 12] for i in range(0, len(ids), 12)]
lock = threading.Lock()

PROMPT = """You are annotating anime key frames for a video editor. The attached images are, in order: {names}.
For EACH image return its annotation. Coordinates are normalized: x from 0 (left edge) to 1 (right edge), y from 0 (top) to 1 (bottom).
- main: the single most important point to frame (the impact / contact point of an attack, the glowing technique, or the main character's face if nothing else).
- face: center of the most important character's face (null if no face visible).
- hand: center of the most important hand or fist (null if none).
- feet: center point between the main character's feet (null if not visible).
- orb: center of a glowing energy orb / beam core (blue, red or purple) if one is painted (null otherwise).
- dir: direction of the main motion or attack: -1 = toward the left, 1 = toward the right, 0 = none / toward the camera.
Reply with ONLY a JSON object (no markdown fences), mapping each image name to {{"main":[x,y],"face":[x,y]|null,"hand":[x,y]|null,"feet":[x,y]|null,"orb":[x,y]|null,"dir":-1|0|1}}."""


def run(batch, n):
    d = os.path.join(SCR, f'b{n}')
    os.makedirs(d, exist_ok=True)
    args = []
    for i in batch:
        p = os.path.join(d, i + '.jpg')
        im = Image.open(os.path.join(ROOT, 'public/frames', i + '.jpg'))
        im.thumbnail((768, 432))
        im.save(p, quality=85)
        args += ['-i', p]
    prompt = PROMPT.format(names=', '.join(batch))
    out = os.path.join(d, 'out.json')
    subprocess.run(['codex', 'exec', '-m', MODEL, '-s', 'read-only', '--skip-git-repo-check', *args, '-o', out, '-'],
                   input=prompt, text=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        txt = open(out).read().strip()
        dec = json.JSONDecoder()
        res, pos = {}, 0
        while True:
            i = txt.find('{', pos)
            if i < 0:
                break
            try:
                obj, end = dec.raw_decode(txt, i)
            except json.JSONDecodeError:
                pos = i + 1
                continue
            if isinstance(obj, dict):
                res.update(obj)
            pos = end
        if not res:
            raise ValueError('no json')
    except Exception as e:  # noqa
        print('batch', n, 'failed', e, flush=True)
        return
    with lock:
        cur = json.load(open(FOC)) if os.path.exists(FOC) else {}
        for k, v in res.items():
            k = k.replace('.jpg', '').strip()
            if k in batch:
                cur[k] = v
        json.dump(cur, open(FOC, 'w'), indent=0)
        print('batch', n, 'ok', len(res), flush=True)


sem = threading.Semaphore(4)


def worker(b, n):
    with sem:
        run(b, n)


ths = [threading.Thread(target=worker, args=(b, n)) for n, b in enumerate(batches)]
for t in ths:
    t.start()
for t in ths:
    t.join()
print('annotated', len(json.load(open(FOC))))
