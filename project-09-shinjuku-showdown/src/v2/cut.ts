// 一个切点 = 一张插画 + 镜头运动 + 冲击滤镜 + 叠加特效。运动码、特效码照分镜表（notes/research/A2_board.md 的 (B) 节）。
import {H, W, clamp, ease, hash, lerp, noise1} from '../engine/core';
import {Stage} from '../engine/stage';
import * as fx from '../engine/fx';
import {still} from './stills';

export type Focus = {x: number; y: number}; // 插画上的归一化坐标 0..1
export type Cam = {s: number; fx: number; fy: number; rot: number}; // 缩放、焦点、旋转（度）

/** 把插画画满屏幕：s=1 时正好盖满；焦点 (fx,fy) 放在屏幕中心附近（受边界限制，不露底）。 */
/** 图在屏幕上的摆放：缩放后的宽高和左上角；keep 是必须留在画面里的点（脸），尽量不被裁掉。 */
export function placement(im: {width: number; height: number}, cam: Cam, keep?: number[]) {
  const base = Math.max(W / im.width, H / im.height);
  const s = base * Math.max(1, cam.s);
  const w = im.width * s, h = im.height * s;
  let x = W / 2 - cam.fx * w, y = H / 2 - cam.fy * h;
  if (keep) {
    const kx = x + keep[0] * w, ky = y + keep[1] * h;
    const mx = W * 0.1, my = H * 0.14;
    if (ky < my) y += my - ky;
    if (ky > H - my) y -= ky - (H - my);
    if (kx < mx) x += mx - kx;
    if (kx > W - mx) x -= kx - (W - mx);
  }
  x = Math.min(0, Math.max(W - w, x));
  y = Math.min(0, Math.max(H - h, y));
  return {x, y, w, h};
}

export function drawStill(st: Stage, id: string, cam: Cam, o: {flip?: boolean; filter?: string; alpha?: number; op?: GlobalCompositeOperation; dx?: number; dy?: number; keep?: number[]} = {}) {
  const im = still(id);
  const g = st.g;
  if (!im) {
    g.fillStyle = '#111';
    g.fillRect(0, 0, W, H);
    return;
  }
  const {x, y, w, h} = placement(im, cam, o.keep);
  g.save();
  g.globalAlpha = o.alpha ?? 1;
  if (o.op) g.globalCompositeOperation = o.op;
  if (o.filter) g.filter = o.filter;
  g.imageSmoothingEnabled = true;
  g.imageSmoothingQuality = 'high';
  g.translate(W / 2 + (o.dx ?? 0), H / 2 + (o.dy ?? 0));
  if (cam.rot) g.rotate((cam.rot * Math.PI) / 180);
  // 旋转时略放大，避免角落露底
  const extra = cam.rot ? 1 + Math.abs(Math.sin((cam.rot * Math.PI) / 180)) * 0.9 : 1;
  g.scale(extra * (o.flip ? -1 : 1), extra);
  g.translate(-W / 2, -H / 2);
  g.drawImage(im, x, y, w, h);
  g.restore();
}

/** 运动码 → 镜头关键帧（起、止），以及缓动；dir 是动作方向（+1 向右，-1 向左）。 */
export function moveCam(code: string, k: number, dur: number, beat: number, f0: Focus, dir: number): Cam {
  const fxy = (dx: number, dy: number) => ({fx: f0.x + dx, fy: f0.y + dy});
  const q = (a: number) => clamp(a);
  switch (code) {
    case '推': {
      const e = ease.out2(k);
      return {s: lerp(1.0, 1.1, e), ...fxy(0, 0), rot: 0};
    }
    case '拉': {
      const e = ease.out3(k);
      return {s: lerp(1.14, 1.0, e), ...fxy(0, 0), rot: 0};
    }
    case '左':
    case '右': {
      const sgn = code === '右' ? 1 : -1;
      return {s: 1.12, ...fxy(lerp(-0.04, 0.04, ease.io2(k)) * sgn, 0), rot: 0};
    }
    case '上':
    case '下': {
      const sgn = code === '下' ? 1 : -1;
      return {s: 1.12, ...fxy(0, lerp(-0.04, 0.04, ease.io2(k)) * sgn), rot: 0};
    }
    case '斜':
      return {s: lerp(1.04, 1.12, ease.io2(k)), ...fxy(lerp(0, 0.03, k), lerp(0, -0.03, k)), rot: lerp(-2, 2, ease.io2(k))};
    case '预': {
      // 半拍内蓄势：往出招反方向缩一点
      const e = ease.out2(q((k * dur) / (beat * 0.5)));
      return {s: lerp(1.02, 1.07, e), ...fxy(-0.02 * dir * e, 0), rot: 0};
    }
    case '冲': {
      const e = ease.in3(q((k * dur) / (beat * 0.25)));
      return {s: lerp(1.03, 1.19, e), ...fxy(0.06 * dir * e, 0), rot: 0};
    }
    case '击': {
      // 首 3 帧 1.12→1.20→1.13，之后慢慢回到 1.1
      const f = k * dur * 60;
      const s = f < 1 ? 1.12 : f < 2 ? 1.2 : f < 3 ? 1.13 : lerp(1.13, 1.09, ease.out2(q((f - 3) / 40)));
      return {s, ...fxy(0, 0), rot: 0};
    }
    case '退': {
      const e = ease.out3(q((k * dur) / (beat * 0.5)));
      return {s: lerp(1.16, 1.03, e), ...fxy(0.05 * dir * e, 0), rot: 0};
    }
    case '接': {
      // 前半建立空间，末半拍蓄势，末四分之一拍突进
      const t = k * dur, rest = dur - t;
      if (rest > beat * 0.5) return {s: lerp(1.0, 1.04, ease.io2(q(t / Math.max(0.01, dur - beat * 0.5)))), ...fxy(0, 0), rot: 0};
      if (rest > beat * 0.25) return {s: 1.04 + 0.03 * ease.out2(q((beat * 0.5 - rest) / (beat * 0.25))), ...fxy(-0.015 * dir, 0), rot: 0};
      const e = ease.in3(q((beat * 0.25 - rest) / (beat * 0.25)));
      return {s: lerp(1.07, 1.2, e), ...fxy(0.05 * dir * e, 0), rot: 0};
    }
    case '掠': {
      const e = ease.io3(q((k * dur) / (beat * 0.5)));
      return {s: 1.12, ...fxy(lerp(-0.04, 0.04, e) * dir, 0), rot: 0};
    }
    case '静推':
      return {s: lerp(1.0, 1.035, k), ...fxy(0, 0), rot: 0};
    default:
      return {s: lerp(1.0, 1.05, k), ...fxy(0, 0), rot: 0};
  }
}

/** 冲击帧滤镜（CSS filter）。 */
export const IMPACT = {
  white: 'grayscale(1) contrast(5) brightness(1.06)',     // 冲白：高反差黑白
  invert: 'grayscale(1) contrast(5) invert(1)',          // 冲反：反色
  black: 'grayscale(1) contrast(9) brightness(0.55) invert(1)', // 黑闪：黑底白轮廓
};

/** 动作方向（拖影、速度线、冲的平移）：从切点描述里猜，猜不到按左右交替。 */
export const guessDir = (text: string, i: number) => {
  if (/向左|朝左|往左|左冲|←/.test(text)) return -1;
  if (/向右|朝右|往右|右冲|→/.test(text)) return 1;
  return i % 2 ? -1 : 1;
};

/** 拖影：同一张图往运动反方向偏移几次、半透明叠加。 */
export function smear(st: Stage, id: string, cam: Cam, dir: number, n = 3, gap = 0.012, alpha = 0.35, flip = false) {
  for (let i = n; i >= 1; i--) {
    drawStill(st, id, cam, {flip, alpha: alpha * (1 - (i - 1) / n), dx: -dir * gap * W * i, op: 'source-over'});
  }
}

/** 屏幕坐标的短震。 */
export const quake = (t: number, amp: number, dur: number, seed = 1): [number, number] => {
  if (t < 0 || t > dur) return [0, 0];
  const a = amp * Math.pow(1 - t / dur, 2);
  return [a * noise1(t * 45, seed), a * noise1(t * 45, seed + 9)];
};

export {fx, hash};
