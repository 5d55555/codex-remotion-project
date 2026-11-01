// 立绘插镜：高精度像素立绘（由游戏像素小人重绘）+ 色底 + 流线，用在台词和咏唱的重点瞬间。
import {H, W, clamp, hash, kf, ease} from '../engine/core';
import {portrait} from '../engine/assets';
import {Stage} from '../engine/stage';

const EYES = {gojo: [[684, 611], [952, 696]], sukuna: [[374, 619], [636, 568]]} as const;

export function cutIn(
  st: Stage, who: 'gojo' | 'sukuna', t: number, dur: number,
  o: {x?: number; y?: number; s?: number; eyes?: number; flip?: boolean; bg?: [string, string]; streaks?: number; slide?: number; drift?: number; darken?: number} = {},
) {
  const g = st.g;
  const [c0, c1] = o.bg ?? (who === 'gojo' ? ['#06142e', '#1a4f8f'] : ['#1a0306', '#7a1020']);
  // 底：斜向渐变
  const gr = g.createLinearGradient(0, 0, W, H);
  gr.addColorStop(0, c0);
  gr.addColorStop(1, c1);
  g.save();
  g.fillStyle = gr;
  g.fillRect(0, 0, W, H);
  g.restore();
  // 流线
  const n = o.streaks ?? 60;
  const fr = Math.floor(t * 30);
  g.save();
  g.globalCompositeOperation = 'lighter';
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, 5), h2 = hash(i, 6), h3 = hash(i, fr, 7);
    const y = h1 * H;
    const len = 300 + h2 * 900;
    const x = ((h2 * W * 2 - t * (900 + h1 * 1400)) % (W + len) + (W + len)) % (W + len) - len;
    g.globalAlpha = 0.05 + 0.12 * h3;
    g.fillStyle = who === 'gojo' ? '#9fd0ff' : '#ff7a7a';
    g.fillRect(o.flip ? W - x - len : x, y, len, 1 + Math.round(h2 * 3));
  }
  g.restore();
  // 立绘：滑进来，再慢慢漂
  const im = portrait(who);
  const s = o.s ?? 0.92;
  const slide = kf(t, [[0, o.slide ?? 260], [0.28, 0, ease.out5]]);
  const drift = (o.drift ?? 18) * (t / Math.max(0.1, dur));
  const dir = who === 'gojo' ? 1 : -1;
  const cx = (o.x ?? (who === 'gojo' ? 0.66 : 0.34) * W) - dir * (slide + drift);
  const cy = (o.y ?? 0.52) * H;
  const w = im.width * s, h = im.height * s;
  const x0 = cx - w / 2, y0 = cy - h / 2;
  g.save();
  g.imageSmoothingEnabled = true;
  g.imageSmoothingQuality = 'high';
  g.globalAlpha = clamp(t / 0.12);
  // 黑色剪影描边感：先画一层偏移的黑影
  g.filter = 'brightness(0)';
  g.globalAlpha *= 0.5;
  g.drawImage(im, x0 + dir * 22, y0 + 14, w, h);
  g.filter = 'none';
  g.globalAlpha = clamp(t / 0.12);
  g.drawImage(im, x0, y0, w, h);
  g.restore();
  if (o.darken) st.fill('#000000', o.darken);
  // 眼光
  const ea = o.eyes ?? 1;
  if (ea > 0.01) {
    for (const [ex, ey] of EYES[who]) {
      const x = x0 + ex * s, y = y0 + ey * s;
      st.glowScreen(x, y, 120 * s, who === 'gojo' ? 'rgba(90,184,242,0.9)' : 'rgba(224,48,63,0.9)', ea * 0.75);
      st.glowScreen(x, y, 34 * s, 'rgba(255,255,255,0.95)', ea * 0.6);
    }
  }
}
