"""闪光检查：逐帧平均亮度，找相邻帧亮度跳变 ≥ 20% 的地方，统计任意 1 秒内“来回”闪的次数（WCAG：一秒内不超过 3 次）。
用法：python3 scripts/flashcheck.py out/silent.mp4
"""
import subprocess, sys
import numpy as np

src = sys.argv[1]
cmd = ['ffmpeg', '-v', 'error', '-i', src, '-vf', 'scale=160:90,format=gray', '-f', 'rawvideo', '-']
raw = subprocess.run(cmd, capture_output=True, check=True).stdout
fr = np.frombuffer(raw, np.uint8).reshape(-1, 90, 160).astype(np.float32) / 255
y = fr.mean(axis=(1, 2))
d = np.diff(y)
big = np.where(np.abs(d) >= 0.2)[0] + 1
print('frames', len(y), 'big jumps', len(big))
# 一次闪 = 先亮后暗（或先暗后亮）的一对跳变
events = [(i, np.sign(d[i - 1])) for i in big]
worst, worst_t = 0, 0
for k, (i, s) in enumerate(events):
    win = [e for e in events if i <= e[0] < i + 60]
    flips = sum(1 for a, b in zip(win, win[1:]) if a[1] != b[1])
    pairs = (flips + 1) // 2
    if pairs > worst:
        worst, worst_t = pairs, i / 60
print('max flash pairs in 1 s:', worst, 'at %.2fs' % worst_t)
for i in big:
    print('  %.3fs  %+.2f' % (i / 60, d[i - 1]))
