"""批量生成关键帧：读 art/frames.json（[{id, chars:[gojo|sukuna|mahoraga|agito], prompt}]），
每张图一个独立的 Luna 任务（独立 CODEX_HOME），N 个并行。生成结果放 scratchpad/gen/frames/<id>/，
挑第一张有效图转成 public/frames/<id>.jpg。已有的跳过（--force 重画，--only id1,id2 只画这些）。
"""
import json, os, subprocess, sys, shutil, threading, queue, time
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCR = '/tmp/claude-0/-home-user-codex-remotion-project/d680fd9a-795b-5f20-aab9-eb13275296d1/scratchpad'
OUT = os.path.join(SCR, 'gen/frames')
PUB = os.path.join(ROOT, 'public/frames')
os.makedirs(OUT, exist_ok=True)
os.makedirs(PUB, exist_ok=True)

STYLE = ("Style (keep exactly): high-budget Japanese anime feature-film KEY FRAME, like a still from a top-tier theatrical action anime. "
         "Crisp clean line art with confident variable line weight; cel shading with 2-3 tone steps plus subtle airbrushed gradients; "
         "dramatic cinematic lighting with strong rim light, glow and atmospheric haze; rich color grading; highly detailed painted background; "
         "dynamic, expressive composition with strong perspective; refined anatomy, correct hands. "
         "Absolutely no text, no letters, no speech bubbles, no sound-effect lettering, no logos, no watermark, no border, no panels. "
         "One single 16:9 landscape image, as large as the tool allows.")
CHARS = {
    'gojo': "the WHITE-HAIRED MAN (see attached reference sheet 'gojo'): very tall slender man, voluminous spiky snow-white hair, luminous sky-blue eyes, no blindfold; fitted black long-sleeve high-neck top, loose wide white trousers, open flowing white short kimono-like outer robe",
    'sukuna': "the BLACK-HAIRED BOY (see attached reference sheet 'sukuna'): lean teenage boy, messy spiky black hedgehog hair, crimson-red eyes, thin black tattoo lines under each eye and small black marks on brow/forehead, sharp-toothed grin; dark navy-black high-collar school uniform, torn at the edges",
    'mahoraga': "the GIANT WHITE SHIKIGAMI (see attached reference sheet 'mahoraga'): colossal muscular pale grey-white humanoid about 2.5x human height, wing-like crests on the sides of the head, a golden eight-spoked dharma wheel floating above its head, right forearm is a long straight broad blade, dark ragged cloth skirt",
    'agito': "the CHIMERA BEAST (see attached reference sheet 'agito'): hulking charcoal-blue beast-man about 2x human height, long flowing silver mane, long toothy snout, small glowing cyan eyes, heavy claws, electric sparks on the forearms",
}


def job(item, force=False):
    jid = item['id']
    d = os.path.join(OUT, jid)
    pub = os.path.join(PUB, jid + '.jpg')
    if os.path.exists(pub) and not force:
        return 'skip'
    if os.path.isdir(d):
        shutil.rmtree(d)
    os.makedirs(d)
    chars = item.get('chars', [])
    for i, c in enumerate(chars):
        shutil.copy(os.path.join(ROOT, 'art/refs', c + '.jpg'), os.path.join(d, f'ref{i + 1}_{c}.jpg'))
    lines = [STYLE, '']
    if chars:
        lines.append('Characters in this image (keep their designs EXACTLY as in the attached reference sheets; do not change hair, eye color, markings or outfit):')
        for c in chars:
            lines.append('- ' + CHARS[c])
        lines.append('')
    else:
        lines.append('No people or creatures in this image unless described below.')
        lines.append('')
    lines.append('Image: ' + item['prompt'])
    lines.append('')
    lines.append(f'Generate exactly ONE image. Save it into the current working directory as {jid}.png, then reply with just the file name.')
    open(os.path.join(d, 'prompt.txt'), 'w').write('\n'.join(lines) + '\n')
    subprocess.run([os.path.join(ROOT, 'scripts/gen_job.sh'), d], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    # 只信这次任务自己生成目录里拷出来的 gen_N.png（取最后一张）；模型自己命名的文件可能是从别处找来的
    gens = sorted([f for f in os.listdir(d) if f.startswith('gen_') and f.endswith('.png')], key=lambda f: int(f[4:-4]))
    cands = [os.path.join(d, f) for f in reversed(gens)]
    for f in cands:
        try:
            im = Image.open(f).convert('RGB')
            if im.width < im.height:
                continue
            # 统一裁成 16:9
            tw = im.width
            th = round(tw * 9 / 16)
            if th > im.height:
                th = im.height
                tw = round(th * 16 / 9)
            x0, y0 = (im.width - tw) // 2, (im.height - th) // 2
            im = im.crop((x0, y0, x0 + tw, y0 + th))
            im.save(pub, quality=92, subsampling=0)
            return f'ok {im.size}'
        except Exception as e:  # noqa
            continue
    return 'FAIL'


def main():
    args = sys.argv[1:]
    force = '--force' in args
    only = None
    nworkers = 10
    for a in args:
        if a.startswith('--only='):
            only = set(a[7:].split(','))
        if a.startswith('-j'):
            nworkers = int(a[2:])
    items = json.load(open(os.path.join(ROOT, 'art/frames.json')))
    if only:
        items = [it for it in items if it['id'] in only]
    q = queue.Queue()
    for it in items:
        q.put(it)
    lock = threading.Lock()
    done = [0]

    def worker():
        while True:
            try:
                it = q.get_nowait()
            except queue.Empty:
                return
            t0 = time.time()
            r = job(it, force)
            with lock:
                done[0] += 1
                print(f'[{done[0]}/{len(items)}] {it["id"]}: {r} ({time.time() - t0:.0f}s)', flush=True)

    ths = [threading.Thread(target=worker) for _ in range(nworkers)]
    for t in ths:
        t.start()
        time.sleep(2)
    for t in ths:
        t.join()


if __name__ == '__main__':
    main()
