// 全片常量、节拍网格、插值与确定性随机数。所有画面只由“第几帧”决定，不依赖上一帧的状态（Remotion 多标签乱序渲染）。

export const FPS = 60;
export const W = 1920;
export const H = 1080;
export const DURATION = 11830; // 197.17 s，配乐原长

// 实测节拍（notes/音乐节拍.md）：第 n 拍在 T0 + n·BEAT 秒；第 m 小节从第 4m+2 拍开始。
export const BEAT = 0.426145;
export const T0 = 0.0144;
export const BAR = BEAT * 4;

/** 第 m 小节第 b 拍（b 从 1 起，可带小数）的秒数。 */
export const B = (m: number, b = 1) => T0 + BEAT * (2 + 4 * m + (b - 1));
export const sec2f = (t: number) => Math.round(t * FPS);

export const clamp = (v: number, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
export const lerp = (a: number, b: number, k: number) => a + (b - a) * k;
export const inv = (a: number, b: number, v: number) => clamp((v - a) / (b - a));
export const smooth = (k: number) => k * k * (3 - 2 * k);

export type Ease = (k: number) => number;
export const ease = {
  lin: (k: number) => k,
  in2: (k: number) => k * k,
  out2: (k: number) => 1 - (1 - k) * (1 - k),
  io2: (k: number) => (k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2),
  in3: (k: number) => k * k * k,
  out3: (k: number) => 1 - Math.pow(1 - k, 3),
  io3: (k: number) => (k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2),
  out5: (k: number) => 1 - Math.pow(1 - k, 5),
  in5: (k: number) => Math.pow(k, 5),
  outBack: (k: number) => 1 + 2.2 * Math.pow(k - 1, 3) + 1.2 * Math.pow(k - 1, 2),
  step: (k: number) => (k < 1 ? 0 : 1),
  smooth,
};

/** 关键帧：[[时间, 值, 进入这一段用的缓动?], ...]，时间递增。 */
export type Key = [number, number, Ease?];
export const kf = (t: number, keys: Key[]): number => {
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    const [t1, v1, e] = keys[i];
    if (t <= t1) {
      const [t0, v0] = keys[i - 1];
      const k = t1 > t0 ? (t - t0) / (t1 - t0) : 1;
      return v0 + (v1 - v0) * (e ?? ease.io2)(k);
    }
  }
  return keys[keys.length - 1][1];
};

/** 整数哈希 → [0,1)。 */
export const hash = (a: number, b = 0, c = 0) => {
  let h = (a * 374761393 + b * 668265263 + c * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h = h ^ (h >>> 16);
  return (h >>> 0) / 4294967296;
};
export const hrange = (lo: number, hi: number, a: number, b = 0, c = 0) => lo + (hi - lo) * hash(a, b, c);

/** 一维平滑噪声（相机手持晃动、火光闪烁）。 */
export const noise1 = (x: number, seed = 0) => {
  const i = Math.floor(x), f = x - i;
  const a = hash(i, seed) * 2 - 1, b = hash(i + 1, seed) * 2 - 1;
  return a + (b - a) * smooth(f);
};

/** 衰减震动：从 t0 开始，幅度 amp，持续 dur 秒。返回 [dx, dy]。 */
export const shakeAt = (t: number, t0: number, amp: number, dur: number, seed = 1): [number, number] => {
  const u = t - t0;
  if (u < 0 || u > dur) return [0, 0];
  const a = amp * Math.pow(1 - u / dur, 2);
  return [a * noise1(u * 38, seed), a * noise1(u * 38, seed + 7)];
};

export const rgba = (hex: string, a: number) => {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
};
