"""渲染“两边同时领域展开”的对撞过场 res/raw/domain_clash.mp4（v0.17；分镜是 Astra 的方案，见 notes/领域对撞与简易领域.md）。

素材：res/raw/domain_void.mp4（V，1920×1080 60fps）、res/raw/domain_shrine.mp4（S，1278×720 30fps）。成片 7.50 秒、1920×1080、60fps。
玩家在宿傩的过场里长按时切进来：从“五条悟也开始展开”起镜，宿傩只以结好的印和神龛出现，结尾停在两个领域接触的一刻（胜负在战斗里打）。
  1  0.00–1.10  V 0.95–2.05 拉衣领，缩放 1.00→1.04
  2  1.10–2.30  V 3.25–4.45 眼睛特写，缩放 1.00→1.08（中心 1000,520）
  3  2.30–3.20  V 5.20–6.10 结印；右上画中画 S 5.00 定格（552×312，暗红边，0.1 秒淡入）
  4  3.20–4.70  左右双格：V 5.90–7.40 | S 5.05–6.55（各自完整缩小、上下暗底）
  5  4.70–5.70  斜切双屏（顶 1094 → 底 826）：V 7.35 定格 | S 9.50–10.50，去掉底部字幕区，缩放 1.00→1.02
  6  5.70–6.90  同上，S 12.00–13.20，缩放 1.02→1.06，两边向分界靠拢 24 → 0，分界发亮 2 → 10 px
  7  6.90–7.50  末帧定格：白闪 3 帧（0.35、0.70、0.25），接触时顶进 4% 再回落（相当于 1.06→1.02 的回弹），水平震动 8 → 0（到 7.20）
音轨按分镜表混（V −3/−4、S −9→−4、S −5/−6、接触音），最后统一到约 −10 LUFS、真峰值 ≤ −1 dBTP。
用法：python3 scripts/render_clash.py [--audio-only]（需要 numpy、scipy、Pillow、soundfile、ffmpeg）
"""
import os, subprocess, sys, tempfile
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.signal import butter, sosfilt
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, 'app/src/main/res/raw')
V = os.path.join(RAW, 'domain_void.mp4')
S = os.path.join(RAW, 'domain_shrine.mp4')
OUT = os.path.join(RAW, 'domain_clash.mp4')
W, H, FPS = 1920, 1080, 60
SR = 48000


def frames(src, start, n, w, h):
    """从 start 秒起读 n 帧（统一成 60fps、w×h、RGB）。"""
    cmd = ['ffmpeg', '-v', 'error', '-ss', f'{start:.3f}', '-i', src, '-vf', f'fps={FPS},scale={w}:{h}:flags=bicubic',
           '-frames:v', str(n), '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    arr = np.frombuffer(raw, np.uint8).reshape(-1, h, w, 3)
    if len(arr) < n:  # 片尾不够：重复最后一帧
        arr = np.concatenate([arr, np.repeat(arr[-1:], n - len(arr), axis=0)])
    return [Image.fromarray(a) for a in arr[:n]]


def zoom(img, z, cx, cy, out_w=W, out_h=H, dx=0.0):
    """以 (cx, cy) 为中心放大 z 倍（裁出 out/z 的窗口再放大），dx 是水平平移（像素）。"""
    iw, ih = img.size
    cw, ch = iw / z, ih / z
    x0 = min(max(cx - cw / 2 - dx * iw / out_w / z, 0), iw - cw)
    y0 = min(max(cy - ch / 2, 0), ih - ch)
    return img.resize((out_w, out_h), Image.BICUBIC, box=(x0, y0, x0 + cw, y0 + ch))


def cover(img, box_w, box_h, fx=0.5, fy=0.5, cut_bottom=0.12, z=1.0, dx=0.0):
    """去掉底部字幕区，按比例铺满 box（中心取源图的 fx, fy），再放大 z 倍、平移 dx。"""
    iw, ih = img.size
    ih2 = ih * (1 - cut_bottom)
    s = max(box_w / iw, box_h / ih2) * z
    cw, ch = box_w / s, box_h / s
    x0 = min(max(fx * iw - cw / 2 - dx / s, 0), iw - cw)
    y0 = min(max(fy * ih - ch / 2, 0), ih2 - ch)
    return img.resize((box_w, box_h), Image.BICUBIC, box=(x0, y0, x0 + cw, y0 + ch))


def ease(k):
    return k * k * (3 - 2 * k)


# 斜切：顶 (1094, 0) → 底 (826, 1080)
YY, XX = np.mgrid[0:H, 0:W]
LINE_X = 1094 + (826 - 1094) * YY / H
LEFT_MASK = Image.fromarray(((XX < LINE_X) * 255).astype(np.uint8))
DIST = np.abs(XX - LINE_X) * np.cos(np.arctan2(1094 - 826, H))  # 到分界线的距离（像素）


def diagonal(left_img, right_img, glow=0.0):
    out = right_img.copy()
    out.paste(left_img, (0, 0), LEFT_MASK)
    a = np.asarray(out).astype(np.float32)
    w = max(1.0, glow)
    core = np.clip(1 - DIST / w, 0, 1)[..., None]
    halo = np.clip(1 - DIST / (w * 4), 0, 1)[..., None] * 0.35
    col = np.array([235, 245, 255], np.float32)
    a = a * (1 - core) + col * core
    a = a + (col - a) * halo
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def render_video(path):
    enc = subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
                            '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', path],
                           stdin=subprocess.PIPE)
    n = 0

    def emit(img):
        nonlocal n
        enc.stdin.write(np.asarray(img.convert('RGB')).tobytes())
        n += 1

    # 1 拉衣领
    for i, f in enumerate(frames(V, 0.95, 66, W, H)):
        emit(zoom(f, 1.0 + 0.04 * i / 65, 960, 540))
    # 2 眼睛特写
    for i, f in enumerate(frames(V, 3.25, 72, W, H)):
        emit(zoom(f, 1.0 + 0.08 * i / 71, 1000, 520))
    # 3 结印 + 宿傩画中画
    pip = frames(S, 5.00, 1, 552, 312)[0]
    pipb = Image.new('RGB', (558, 318), (122, 10, 20))
    pipb.paste(pip, (3, 3))
    for i, f in enumerate(frames(V, 5.20, 54, W, H)):
        a = min(1.0, i / 6)
        if a > 0:
            region = f.crop((1269, 93, 1269 + 558, 93 + 318))
            f.paste(Image.blend(region, pipb, a), (1269, 93))
        emit(f)
    # 4 左右双格
    lv = frames(V, 5.90, 90, 960, 540)
    rs = frames(S, 5.05, 90, 960, 540)
    for i in range(90):
        f = Image.new('RGB', (W, H), (10, 10, 18))
        f.paste(Image.new('RGB', (960, H), (18, 6, 10)), (960, 0))
        f.paste(lv[i], (0, 270))
        f.paste(rs[i], (960, 270))
        ImageDraw.Draw(f).rectangle((958, 0, 961, H), fill=(200, 210, 230))
        emit(f)
    # 5、6 斜切双屏
    vfreeze = frames(V, 7.35, 1, W, H)[0]
    s5 = frames(S, 9.50, 60, 1278, 720)
    s6 = frames(S, 12.00, 72, 1278, 720)
    bw = 1094  # 左右各自的包围框宽
    last = None
    for i in range(60):
        z = 1.0 + 0.02 * i / 59
        lft = Image.new('RGB', (W, H))
        lft.paste(cover(vfreeze, bw, H, 0.5, 0.48, z=z), (0, 0))
        rgt = Image.new('RGB', (W, H))
        rgt.paste(cover(s5[i], bw, H, 0.5, 0.5, z=z), (W - bw, 0))
        last = diagonal(lft, rgt, 2.0)
        emit(last)
    for i in range(72):
        k = ease(i / 71)
        z = 1.02 + 0.04 * k
        lft = Image.new('RGB', (W, H))
        lft.paste(cover(vfreeze, bw, H, 0.5, 0.48, z=z, dx=-24 * (1 - k)), (0, 0))
        rgt = Image.new('RGB', (W, H))
        rgt.paste(cover(s6[i], bw, H, 0.5, 0.5, z=z, dx=24 * (1 - k)), (W - bw, 0))
        last = diagonal(lft, rgt, 2.0 + 8.0 * k)
        emit(last)
    # 7 接触：白闪、回弹、震动
    flash = [0.35, 0.70, 0.25]
    white = Image.new('RGB', (W, H), (255, 255, 255))
    for i in range(36):
        t = 6.90 + i / FPS
        # 末帧已经是 1.06 倍：接触那一下再顶进 4%，然后回落到末帧（等效“1.06 → 1.02”的回弹，不用露出画外）
        z = 1.0 + 0.04 * (1 - ease(i / 35))
        shake = 8 * max(0.0, 1 - (t - 6.90) / 0.30) * (1 if i % 2 == 0 else -1)
        f = zoom(last, z, 960, 540, dx=shake)
        if i < 3:
            f = Image.blend(f, white, flash[i])
        emit(f)
    enc.stdin.close()
    enc.wait()
    assert n == 450, n


# ---------------- 音轨 ----------------

def audio(src):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', src, '-vn', '-ac', '2', '-ar', str(SR), '-f', 'f32le', '-'],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1, 2).copy()


def seg(a, start, dur):
    i0 = int(round(start * SR))
    n = int(round(dur * SR))
    out = np.zeros((n, 2), np.float32)
    part = a[i0:i0 + n]
    out[:len(part)] = part
    f = int(0.010 * SR)  # 每段首尾 10 ms 防爆音
    ramp = np.linspace(0, 1, f, dtype=np.float32)[:, None]
    out[:f] *= ramp
    out[-f:] *= ramp[::-1]
    return out


def db(x):
    return 10 ** (x / 20)


def env_lin(n, pts):
    """分段线性增益：pts = [(相对秒, dB), …]。"""
    t = np.arange(n) / SR
    xs = [p[0] for p in pts]
    ys = [db(p[1]) for p in pts]
    return np.interp(t, xs, ys).astype(np.float32)[:, None]


def contact_hit():
    """180→110 Hz 下扫 120 ms + 360 Hz 泛音；12 ms 800 Hz–3 kHz 噪声瞬态；250 ms 干金属尾。"""
    rng = np.random.default_rng(17)
    n = int(0.40 * SR)
    t = np.arange(n) / SR
    f = 180 * (110 / 180) ** np.clip(t / 0.12, 0, 1)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.07)
    body += 0.35 * np.sin(2 * np.pi * 360 * t) * np.exp(-t / 0.05)
    nn = int(0.012 * SR)
    tr = np.zeros(n)
    tr[:nn] = sosfilt(butter(2, [800, 3000], 'bandpass', fs=SR, output='sos'), rng.standard_normal(nn)) * np.exp(-np.arange(nn) / (0.004 * SR))
    tail = sum(a * np.sin(2 * np.pi * fr * t + rng.random() * 6) for fr, a in ((1340, 0.5), (1880, 0.3), (2710, 0.2))) * np.exp(-t / 0.08)
    tail *= np.clip((t - 0.015) / 0.005, 0, 1)
    x = 0.9 * body + 1.2 * tr / (np.max(np.abs(tr)) + 1e-9) + 0.35 * tail
    x = np.tanh(1.6 * x) / np.tanh(1.6)
    return np.stack([x, x], 1).astype(np.float32)


def pressure(dur):
    """5.70–6.90 的聚压：带通噪声慢慢收窄上扬。"""
    rng = np.random.default_rng(23)
    n = int(dur * SR)
    x = sosfilt(butter(2, [300, 1400], 'bandpass', fs=SR, output='sos'), rng.standard_normal(n))
    x = x / (np.max(np.abs(x)) + 1e-9) * np.linspace(0.05, 0.35, n) ** 1.5
    return np.stack([x, x], 1).astype(np.float32)


def render_audio(path):
    va, sa = audio(V), audio(S)
    total = int(7.50 * SR)
    mix = np.zeros((total, 2), np.float32)

    def put(x, at):
        i = int(round(at * SR))
        mix[i:i + len(x)] += x[: max(0, total - i)]

    put(seg(va, 0.95, 1.10) * db(-3), 0.00)
    put(seg(va, 3.25, 1.20) * db(-3), 1.10)
    put(seg(va, 5.20, 0.90) * db(-3), 2.30)
    n4 = int(1.50 * SR)
    put(seg(va, 5.90, 1.50) * env_lin(n4, [(0, -4), (1.25, -4), (1.50, -12)]), 3.20)
    put(seg(sa, 5.05, 1.50) * env_lin(n4, [(0, -9), (1.25, -9), (1.50, -4)]), 3.20)
    put(seg(sa, 9.50, 1.00) * db(-5), 4.70)
    n6 = int(1.20 * SR)
    put(seg(sa, 12.00, 1.20) * env_lin(n6, [(0, -6), (1.00, -6), (1.20, -12)]), 5.70)
    put(pressure(1.20) * db(-14), 5.70)
    n7 = int(0.60 * SR)
    put(seg(sa, 13.20, 0.60) * env_lin(n7, [(0, -18), (0.45, -18), (0.60, -60)]), 6.90)
    put(contact_hit() * db(-6), 6.90)
    end = int(0.15 * SR)  # 最后 0.15 秒淡出
    mix[-end:] *= np.linspace(1, 0, end, dtype=np.float32)[:, None]
    tmp = tempfile.mktemp(suffix='.wav')
    sf.write(tmp, mix, SR, subtype='FLOAT')
    # 两遍 loudnorm 到 −10 LUFS、真峰值 −3 dBTP（AAC 编码会冒出 1–2 dB，成片要 ≤ −1 dBTP）
    meas = subprocess.run(['ffmpeg', '-hide_banner', '-nostats', '-i', tmp, '-af', 'loudnorm=I=-10:TP=-3:LRA=11:print_format=json', '-f', 'null', '-'],
                          capture_output=True, text=True).stderr
    import json
    j = json.loads(meas[meas.rindex('{'):meas.rindex('}') + 1])
    af = (f"loudnorm=I=-10:TP=-3:LRA=11:measured_I={j['input_i']}:measured_TP={j['input_tp']}:measured_LRA={j['input_lra']}"
          f":measured_thresh={j['input_thresh']}:offset={j['target_offset']}:linear=true,aresample=44100")
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', tmp, '-af', af, '-c:a', 'pcm_s16le', path], check=True)
    os.remove(tmp)


def main():
    work = tempfile.mkdtemp()
    vid, aud = os.path.join(work, 'v.mp4'), os.path.join(work, 'a.wav')
    if '--audio-only' in sys.argv:  # 只重混音轨，画面从现有成片原样拷贝
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', OUT, '-an', '-c:v', 'copy', vid], check=True)
    else:
        render_video(vid)
    render_audio(aud)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', vid, '-i', aud, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
                    '-t', '7.5', '-movflags', '+faststart', OUT], check=True)
    print('写好了', OUT, os.path.getsize(OUT) // 1024, 'KB')


if __name__ == '__main__':
    main()
