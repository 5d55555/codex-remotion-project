// 各幕共用的场景与角色小工具。
import {H, W, clamp, hash, lerp} from '../engine/core';
import {plate} from '../engine/assets';
import {Stage} from '../engine/stage';
import {FigInfo, FigOpts, POSE, Pose, RIG, RigDef} from '../engine/rig';
import * as fx from '../engine/fx';

export const BLUE = 'rgba(90,184,242,0.95)';
/** 第四幕末到第五幕“留在战场上的苍”的位置（跨镜头共用，保证空间连贯）。 */
export const BLUE_P: [number, number] = [230, -230];
export const REDEYE = 'rgba(224,48,63,0.95)';
export const GOJO_C = '#5ab8f2';
export const SUKUNA_C = '#e0303f';

// ---------------- 角色 ----------------

export type Actor = {rig: RigDef; pose: Pose; x: number; y: number; face: number; opts?: Partial<FigOpts>; eyes?: number; eyeColor?: string; shadow?: number; ground?: number};

/** 画一个角色：地面阴影 → 像素人 → 眼光（眼光在 flush 之后由调用方决定画不画在前面）。 */
export function actor(st: Stage, a: Actor): FigInfo {
  if ((a.shadow ?? 1) > 0) {
    const gy = a.ground ?? 0;
    const air = clamp((gy - a.y) / 80);
    fx.shadow(st, a.x, gy, (a.rig === RIG.maho ? 26 : 16) * (1 - air * 0.5), (a.shadow ?? 0.5) * (1 - air * 0.7));
  }
  const info = st.figure(a.rig, a.pose, a.x, a.y, {face: a.face, ...(a.opts ?? {})});
  return info;
}

/** 残影：沿着一串位置画半透明、带颜色的身影。 */
export function ghosts(st: Stage, rig: RigDef, pose: Pose, pts: [number, number][], face: number, color: string, a0 = 0.45) {
  pts.forEach(([x, y], i) => st.figure(rig, pose, x, y, {face, tint: color, tintA: 0.85, alpha: a0 * (1 - i / pts.length)}));
}

export const gojoEyes = (st: Stage, info: FigInfo, a: number, r = 2.2) => fx.eyeGlow(st, info.eyes, BLUE, a, r);
export const sukunaEyes = (st: Stage, info: FigInfo, a: number, r = 2.2) => fx.eyeGlow(st, info.eyes, REDEYE, a, r);

// ---------------- 新宿街道 ----------------
// street.jpg 3255×1088：人行道边在 y≈800，路面到底；角色脚底放在 y=930。

const ROAD_TOP = 780, FEET = 930;
let roadC: HTMLCanvasElement | null = null;
const road = () => {
  if (roadC) return roadC;
  const im = plate('street');
  const c = document.createElement('canvas');
  c.width = im.width;
  c.height = im.height - ROAD_TOP;
  const g = c.getContext('2d')!;
  g.drawImage(im, 0, -ROAD_TOP);
  g.globalCompositeOperation = 'destination-in';
  const gr = g.createLinearGradient(0, 0, 0, 60);
  gr.addColorStop(0, 'rgba(0,0,0,0)');
  gr.addColorStop(1, 'rgba(0,0,0,1)');
  g.fillStyle = gr;
  g.fillRect(0, 0, c.width, c.height);
  roadC = c;
  return c;
};

/**
 * 街道：远处楼群（深度 far）+ 路面（与角色同一平面）。base 是“两层对齐”的相机位置。
 * s 是路面上一个图像像素对应几个世界单位。
 */
export function street(st: Stage, o: {base?: [number, number]; far?: number; blur?: number; s?: number; x?: number; dim?: number} = {}) {
  const s = o.s ?? 1, d = o.far ?? 0.55;
  const im = plate('street');
  const [bx, by] = o.base ?? [0, -150];
  const rx = (o.x ?? 0), ry = (im.height / 2 - FEET) * s; // 路面层：图中心的世界坐标（脚底 y=0）
  st.plate('street', {x: rx - (1 - d) * bx, y: ry - (1 - d) * by, s, d, blur: o.blur ?? 0});
  const rc = road();
  st.image(rc, {x: rx, y: (ROAD_TOP + rc.height / 2 - FEET) * s, s, d: 1});
  groundBelow(st, st.sy((im.height - FEET) * s));
  if (o.dim) st.fill('#0a0d14', o.dim, 'multiply');
}

/** 近处路面：图底下几行缩小、模糊成没有标线的沥青纹理，拉到屏幕底，越近越暗。 */
let groundC: HTMLCanvasElement | null = null;
export function groundBelow(st: Stage, top: number) {
  if (top >= H) return;
  if (!groundC) {
    const im = plate('street');
    groundC = document.createElement('canvas');
    groundC.width = 256;
    groundC.height = 32;
    const gg = groundC.getContext('2d')!;
    gg.filter = 'blur(2px)';
    gg.drawImage(im, 0, im.height - 90, im.width, 70, -8, -4, 272, 40);
  }
  const g = st.g;
  g.save();
  g.imageSmoothingEnabled = true;
  g.globalAlpha = 1;
  g.drawImage(groundC, 0, 2, 256, 28, -40, top - 6, W + 80, H - top + 12);
  g.restore();
  st.vgrad(top - 6, H, [[0, 'rgba(10,12,16,0.1)'], [1, 'rgba(10,12,16,0.6)']]);
}

/**
 * 平视的街道（中景、近景用）：整张图当远景，地平线约在角色头部高度；每帧按当前相机把图上 y=930 对到脚底，
 * 所以角色永远站在路面上，路面从脚下一直延伸到远处。
 */
export function streetEye(st: Stage, o: {s?: number; d?: number; blur?: number; ax?: number; px?: number; feetY?: number; panPar?: number} = {}) {
  const im = plate('street');
  const d = o.d ?? 0.85;
  const zl = Math.pow(st.cam.z, d);
  const s = Math.max(o.s ?? 0.45, (W * 1.2) / (im.width * zl)); // 横向至少盖满屏幕
  const feet = o.feetY ?? FEET;
  const [Lx, Ly] = anchor(st, d, s, im, o.px ?? im.width / 2, feet, o.ax ?? 0, 0);
  // 横向保留一点视差：相机横移时远景少走一些
  const par = (o.panPar ?? 0.35) * (st.cam.x - (o.ax ?? 0));
  st.vgrad(0, H, [[0, '#a7b0c0'], [1, '#4a515e']]);
  st.plate('street', {x: Lx + par, y: Ly, s, d, blur: o.blur ?? 0});
  // 图的下边缘以下：接上没有标线的近处路面
  const S = s * zl;
  groundBelow(st, st.sy(0) + (im.height - feet) * S);
}

/** 边缘羽化过的背景图（嵌进别的画面用），按名字缓存。 */
const softCache = new Map<string, HTMLCanvasElement>();
export function soft(name: Parameters<typeof plate>[0]) {
  const hit = softCache.get(name);
  if (hit) return hit;
  const im = plate(name);
  const c = document.createElement('canvas');
  c.width = im.width;
  c.height = im.height;
  const g = c.getContext('2d')!;
  g.drawImage(im, 0, 0);
  g.globalCompositeOperation = 'destination-in';
  const gr = g.createRadialGradient(c.width / 2, c.height / 2, Math.min(c.width, c.height) * 0.2, c.width / 2, c.height / 2, Math.max(c.width, c.height) * 0.52);
  gr.addColorStop(0, 'rgba(0,0,0,1)');
  gr.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = gr;
  g.fillRect(0, 0, c.width, c.height);
  softCache.set(name, c);
  return c;
}

/** 让图上 (px,py) 在当前相机下正好落在世界 (wx,wy)：返回该层图中心的层坐标。 */
export function anchor(st: Stage, d: number, s: number, im: {width: number; height: number}, px: number, py: number, wx: number, wy: number): [number, number] {
  const {x: cx, y: cy, z} = st.cam;
  const zl = Math.pow(z, d);
  return [
    ((wx - cx) * z - (px - im.width / 2) * s * zl) / zl + cx * d,
    ((wy - cy) * z - (py - im.height / 2) * s * zl) / zl + cy * d,
  ];
}

/** 冬日阴天的空气：远雾、三层雪。 */
export function winterAir(st: Stage, t: number, o: {snow?: number; fogY?: number; fogA?: number; freeze?: number; wind?: number} = {}) {
  const k = o.snow ?? 1;
  if (o.fogA !== 0) fx.fog(st, t, {y: o.fogY ?? H * 0.72, a: o.fogA ?? 0.22, h: 200});
  if (k > 0) {
    fx.snow(st, t, {n: Math.round(260 * k), depth: 0.15, size: 2, a: 0.55, seed: 1, freeze: o.freeze, wind: o.wind});
    fx.snow(st, t, {n: Math.round(120 * k), depth: 0.55, size: 3, a: 0.7, seed: 2, freeze: o.freeze, wind: o.wind});
  }
}
export function winterNear(st: Stage, t: number, o: {snow?: number; freeze?: number; wind?: number} = {}) {
  const k = o.snow ?? 1;
  if (k > 0) fx.snow(st, t, {n: Math.round(26 * k), depth: 1, size: 5, a: 0.55, seed: 3, freeze: o.freeze, wind: o.wind});
}

/** 冬日阴天的调色：轻微去饱和、冷色。 */
export function gradeWinter(st: Stage, k = 1) {
  st.post.desat = 0.18 * k;
  st.post.tint = '#7f9cc4';
  st.post.tintA = 0.22 * k;
  st.post.vignette = 0.42;
  st.post.bloom = 0.35;
}

// ---------------- 楼顶 ----------------
// rooftop.jpg 2974×1190：右侧女儿墙顶端在 (1466, 888)，五条站在这里。

export const ROOF = {feetX: 1466, feetY: 888, s: 0.62};
export function rooftop(st: Stage, o: {blur?: number; d?: number} = {}) {
  const im = plate('rooftop');
  const d = o.d ?? 1;
  const [x, y] = anchor(st, d, ROOF.s, im, ROOF.feetX, ROOF.feetY, 0, 0); // 五条脚下永远是女儿墙顶
  st.plate('rooftop', {x, y, s: ROOF.s, d, blur: o.blur});
}

// ---------------- 其它 ----------------

/** 全屏白场/黑场等过渡用。 */
export const fade = (st: Stage, color: string, a: number) => {
  st.post.fade = color;
  st.post.fadeA = Math.max(st.post.fadeA, clamp(a));
};

/** 冲击帧：整个画面变成两色剪影的一帧（由镜头自己把人物画成剪影，这里负责底色）。 */
export const impactBg = (st: Stage, color = '#ffffff') => st.fill(color, 1);

export {lerp, hash, W, H, POSE, RIG};
