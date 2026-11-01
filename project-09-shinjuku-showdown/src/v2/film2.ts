// 第二版成片：140 张重绘关键帧，按分镜表 532 个切点快切（src/v2/edl.json），每帧只由帧号决定。
import {B, BEAT, FPS, H, W, clamp, ease, hash, kf, lerp} from '../engine/core';
import {Stage} from '../engine/stage';
import * as fx from '../engine/fx';
import EDL from './edl.json';
import FOCUS from './focus.json';
import {Cam, IMPACT, drawStill, moveCam, placement, quake} from './cut';
import {still} from './stills';
import {TEXTS} from './texts';
import FRAME_LIST from './frames_have.json';

const EXISTING = new Set(FRAME_LIST as string[]);

export type Row = {t: number; img: string; crop: string; move: string; fx: string; sfx: string};
type Pts = {main?: number[]; face?: number[]; hand?: number[]; feet?: number[]; orb?: number[]; dir?: number};

// 生图额度用完时还没画出来的关键帧，先用内容最接近的已有插画顶上（额度恢复后补画，再删掉这一表）
export const SUBSTITUTE: Record<string, string> = {
  f093: 'f092', f101: 'f074', f102: 'f043', f103: 'f047', f104: 'f088', f105: 'f037', f106: 'f097', f107: 'f016',
  f108: 'f011', f109: 'f114', f131: 'f127', f135: 'f123', f136: 'f076', f137: 'f134', f138: 'f122', f139: 'f134', f140: 'f022',
};
export const CUTS: Row[] = (EDL as Row[])
  .filter((r, i, a) => i + 1 >= a.length || a[i + 1].t > r.t + 1 / 120) // 同一时刻的两行只留后一行
  .map((r) => ({...r, img: SUBSTITUTE[r.img] && !EXISTING.has(r.img) ? SUBSTITUTE[r.img] : r.img}));
// 标注里的 null 当作“没有”
const FOC: Record<string, Pts> = Object.fromEntries(
  Object.entries(FOCUS as Record<string, Record<string, unknown>>).map(([k, v]) => [k, Object.fromEntries(Object.entries(v).filter(([, x]) => x != null)) as Pts]),
);
export const ALL_IMAGES = [...new Set(CUTS.map((c) => c.img))];

const END = 11830 / FPS;
const ACT2 = B(33), ACT3 = B(49), ACT4 = B(65), ACT5 = B(83), ACT6 = B(98);

/** 裁切提示 → 焦点和额外放大。 */
function cropFocus(img: string, crop: string): {f: {x: number; y: number}; zoom: number} {
  const p = FOC[img] ?? {};
  const pt = (a?: number[], d: number[] = [0.5, 0.45]) => ({x: (a ?? d)[0], y: (a ?? d)[1]});
  const main = pt(p.main);
  if (!crop) return {f: main, zoom: 1};
  if (/眼|瞳/.test(crop)) return {f: pt(p.face ?? p.main), zoom: 1.9};
  if (/脸|面|头|表情/.test(crop)) return {f: pt(p.face ?? p.main), zoom: 1.6};
  if (/手|拳|指|掌|袖/.test(crop)) return {f: pt(p.hand ?? p.main), zoom: 1.6};
  if (/脚|鞋|地|腿|膝/.test(crop)) return {f: pt(p.feet ?? [main.x, Math.min(0.85, main.y + 0.25)]), zoom: 1.5};
  if (/肩|胸|上身|半身/.test(crop)) return {f: {x: (p.face ?? p.main ?? [0.5])[0], y: ((p.face ?? p.main ?? [0, 0.4])[1]) + 0.08}, zoom: 1.4};
  if (/球|光|核|苍|赫|茈/.test(crop)) return {f: pt(p.orb ?? p.main), zoom: 1.5};
  if (/天|远|楼|街|建筑|烟|云|城/.test(crop)) return {f: {x: main.x, y: 0.32}, zoom: 1.3};
  if (/低/.test(crop)) return {f: {x: main.x, y: 0.7}, zoom: 1.3};
  return {f: main, zoom: 1.35};
}

/** 插画坐标 → 屏幕坐标（和 drawStill 的变换一致，不含旋转）。 */
function toScreen(img: string, cam: Cam, u: number, v: number, keep?: number[]): [number, number] {
  const im = still(img);
  if (!im) return [W / 2, H / 2];
  const {x, y, w, h} = placement(im, cam, keep);
  return [x + u * w, y + v * h];
}

/** 找到 T 所在的切点。 */
export function cutIndex(T: number) {
  let lo = 0, hi = CUTS.length - 1, ans = 0;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (CUTS[mid].t <= T) {
      ans = mid;
      lo = mid + 1;
    } else hi = mid - 1;
  }
  return ans;
}

const has = (r: Row, re: RegExp) => re.test(r.fx);

/** 屏幕空间的粗略“世界”：让 Stage 的世界坐标 = 屏幕坐标，好复用高清特效。 */
const screenSpace = (st: Stage, shake: [number, number] = [0, 0]) => {
  st.zoomBoost = 1;
  st.camera(W / 2, H / 2, 1, shake);
};

export function renderV2(st: Stage, frame: number) {
  const T = frame / FPS;
  const i = cutIndex(T);
  const r = CUTS[i];
  const next = i + 1 < CUTS.length ? CUTS[i + 1].t : END;
  const u = T - r.t, dur = Math.max(1 / FPS, next - r.t), k = clamp(u / dur);
  const f = Math.floor(u * FPS + 1e-6); // 本切点的第几帧
  const p = FOC[r.img] ?? {};
  const dir = p.dir && p.dir !== 0 ? p.dir : i % 2 ? -1 : 1;
  const {f: foc, zoom} = cropFocus(r.img, r.crop);
  const cam0 = moveCam(r.move, k, dur, BEAT, foc, dir);
  const cam: Cam = {...cam0, s: cam0.s * zoom};
  const keep = p.face && !/手|拳|指|掌|袖|脚|鞋|地|腿|膝|天|远|楼|街|建筑|烟|云|城|低|球|光|核/.test(r.crop) ? p.face : undefined;

  // 震动：击、冲白、冲反、黑闪、震
  let amp = 0, qdur = 0.1;
  if (r.move === '击') (amp = 10), (qdur = 0.12);
  if (has(r, /震/)) (amp = Math.max(amp, 11.5)), (qdur = 5 / 60);
  if (has(r, /冲白|冲反/)) (amp = Math.max(amp, 14)), (qdur = 0.14);
  if (has(r, /黑闪/)) (amp = 26), (qdur = 0.25);
  const sh = quake(u, amp, qdur, i);

  screenSpace(st, sh);
  const impact = f < 3 ? (has(r, /黑闪/) ? IMPACT.black : has(r, /冲反/) || (r.fx.includes('裂') && f < 3) ? IMPACT.invert : has(r, /冲白/) ? IMPACT.white : '') : '';
  // 拖影：影、冲、掠
  if ((has(r, /影/) || r.move === '冲' || r.move === '掠') && !impact) {
    for (let n = 3; n >= 1; n--) drawStill(st, r.img, cam, {alpha: 0.28 * (1 - (n - 1) / 3), dx: -dir * 0.012 * W * n + sh[0], dy: sh[1], keep});
  }
  drawStill(st, r.img, cam, {filter: impact || undefined, dx: sh[0], dy: sh[1], keep});
  if (impact === IMPACT.black) {
    // 黑闪：黑底白轮廓上叠一层暗红
    st.fill('#8b1027', 0.35, 'multiply');
  }

  // 特效挂在插画上的点：主点 / 光球
  const [mx, my] = toScreen(r.img, cam, (p.main ?? [0.5])[0], (p.main ?? [0, 0.45])[1], keep);
  const orbPt = p.orb ? toScreen(r.img, cam, p.orb[0], p.orb[1], keep) : [mx, my];
  overlays(st, r, i, u, f, dur, T, [mx + sh[0], my + sh[1]], [orbPt[0] + sh[0], orbPt[1] + sh[1]], dir);

  // 世界斩：整帧沿斜线错开（98.2 起，持续到这一段结束）
  worldCut(st, T);

  grade(st, T, r);
  for (const tx of TEXTS) {
    const age = T - tx.at;
    if (age >= 0 && age <= tx.dur) st.text({...tx.item, age, dur: tx.dur});
  }
}

function overlays(st: Stage, r: Row, i: number, u: number, f: number, dur: number, T: number, m: number[], orb: number[], dir: number) {
  const [mx, my] = m;
  if (has(r, /雪/)) {
    fx.snow(st, T, {n: 200, depth: 0.2, size: 2, a: 0.6, seed: 1});
    fx.snow(st, T, {n: 80, depth: 0.7, size: 3, a: 0.75, seed: 2});
    fx.snow(st, T, {n: 18, depth: 1, size: 6, a: 0.5, seed: 3});
  }
  if (has(r, /尘|烟/)) {
    fx.fog(st, T, {y: H * 0.8, a: 0.22, h: 220, color: 'rgba(190,195,205,0.5)'});
    fx.dust(st, mx, my + 120, u, 0, {n: 10, r: 90, spread: 320, rise: 30, life: Math.max(1, dur + 0.5), color: '170,172,180', a: 0.35, seed: i});
  }
  if (has(r, /苍/)) {
    st.glowScreen(orb[0], orb[1], 260, 'rgba(57,141,255,0.6)', 0.55 + 0.15 * Math.sin(T * 9));
    fx.blue(st, orb[0], orb[1], 26, T, {a: 0.55, pull: 1.5, debris: 30});
  }
  if (has(r, /赫/)) {
    st.glowScreen(orb[0], orb[1], 240, 'rgba(240,68,62,0.6)', 0.5 + 0.15 * Math.sin(T * 13));
    fx.red(st, orb[0], orb[1], 20, T, {a: 0.5});
  }
  if (has(r, /紫/)) {
    st.glowScreen(orb[0], orb[1], 420, 'rgba(150,70,255,0.55)', 0.6 + 0.2 * Math.sin(T * 7));
    fx.crackle(st, orb[0], orb[1], 160, Math.floor(T * 20), '#e0b7ff', 6, 0.7, 3);
  }
  if (has(r, /速/) && f < 10) {
    if (r.move === '推' || r.move === '冲' || r.move === '击') fx.speedLines(st, mx, my, T, {n: 70, r0: 240, a: 0.7 * (1 - f / 10), color: '#ffffff', width: 7});
    else fx.streaks(st, T, {n: 46, a: 0.6 * (1 - f / 10), dir, color: '#ffffff'});
  }
  if (has(r, /修/)) {
    st.glowScreen(mx, my, 200, 'rgba(210,255,225,0.5)', 0.5 + 0.2 * Math.sin(T * 12));
    for (let n = 0; n < 24; n++) {
      const h1 = hash(n, i), h2 = hash(n, i, 2);
      const ph = (u * (0.8 + h2) + h1) % 1;
      st.glowScreen(mx + (h1 - 0.5) * 260, my + 90 - ph * 260, 10, 'rgba(240,255,245,0.9)', Math.sin(ph * Math.PI) * 0.8);
    }
  }
  if (has(r, /轮/) && f < 18) {
    st.glowScreen(W * 0.5, H * 0.2, 420, 'rgba(255,214,120,0.55)', 1 - f / 18);
  }
  if (has(r, /斩/) && f < 4) {
    const y0 = H * (0.3 + 0.4 * hash(i, 7)), y1 = H * (0.3 + 0.4 * hash(i, 8));
    const g = st.g;
    g.save();
    g.strokeStyle = '#000';
    g.lineWidth = 10;
    g.globalAlpha = 0.8;
    g.beginPath();
    g.moveTo(-50, y0);
    g.lineTo(W + 50, y1);
    g.stroke();
    g.strokeStyle = '#ffffff';
    g.lineWidth = 3;
    g.globalAlpha = 1;
    g.stroke();
    g.restore();
  }
  if (has(r, /黑闪|黑红电/)) fx.blackFlash(st, mx, my, 260, clamp(u / 0.45), i);
  if (has(r, /青电|电丝|电光/)) fx.crackle(st, mx, my, 180, Math.floor(T * 30), '#6fe0ff', 7, 0.8, 3);
  if (has(r, /火花/)) fx.sparks(st, mx, my, u, 0, {n: 30, speed: 600, color: '#ffffff', color2: '#ffd27a', seed: i, grav: 400});
  if (has(r, /碎玻璃|碎片|碎石/)) fx.shards(st, mx, my, u, 0, {n: 26, speed: 700, size: 18, seed: i, life: 1.2, color: 'rgba(200,210,225,0.85)'});
  if (has(r, /冲击波|扩张/)) fx.shock(st, mx, my, u, 0, 0.5, 900, {color: '#ffffff', width: 10});
  if (has(r, /展延/)) st.glowScreen(mx, my, 260, 'rgba(180,220,255,0.45)', 0.5);
  if (has(r, /机场/)) {
    fx.motes(st, T, {n: 70, a: 0.4});
    fx.rays(st, W * 0.1, -80, T, {n: 8, len: 1900, a: 0.12, dir: Math.PI * 0.3, spread: 0.5});
  }
  if (has(r, /尾|余烬/) || (T > ACT6 && /f13[3-9]|f140/.test(r.img))) fx.embers(st, T, {n: 50, color: '#ffa060', a: 0.6});
  // 每次硬切的第一帧给一点点闪亮，让切点“咬”在拍子上
  if (f === 0 && (r.move === '击' || has(r, /冲白/))) st.fill('#ffffff', 0.25);
}

/** 98.2 世界斩：一条白线贯穿整帧，上下两半错开；之后几个切点里“裂”的画面继续错位。 */
function worldCut(st: Stage, T: number) {
  const CUT = B(98, 2);
  if (T < CUT || T > B(102)) return;
  const u = T - CUT;
  const x1 = 0, y1 = H * 0.62, x2 = W, y2 = H * 0.38;
  const shift = 48 * ease.out3(clamp(u / 0.06)) + 6 * u;
  const gap = 6 * ease.out3(clamp(u / 0.1)) + 2 * u;
  st.slice(x1, y1, x2, y2, -shift, gap, '#000', 1);
  const g = st.g;
  if (u < 2 / FPS) st.fill('#ffffff', 0.5);
  g.save();
  g.globalCompositeOperation = 'lighter';
  g.strokeStyle = '#ffffff';
  g.globalAlpha = clamp(1 - u / 1.5) * 0.9;
  g.lineWidth = u < 0.05 ? 4 : 2;
  g.beginPath();
  g.moveTo(x1, y1);
  g.lineTo(x2, y2);
  g.stroke();
  g.restore();
}

function grade(st: Stage, T: number, r: Row) {
  const p = st.post;
  p.bloom = 0.32;
  p.bloomTh = 2.4;
  p.vignette = 0.38;
  p.grain = 0.05;
  if (T < ACT2) {
    p.tint = '#7f9cc4';
    p.tintA = 0.1;
  } else if (T < ACT3) {
    p.bloom = 0.36;
  } else if (T < ACT4) {
    p.bloom = 0.4;
  } else if (T < ACT5) {
    p.desat = 0.08;
  } else if (T < ACT6) {
    p.bloom = 0.45;
  }
  if (r.fx.includes('忆')) {
    p.desat = 0.7;
    p.tint = '#6f86b0';
    p.tintA = 0.3;
    p.grain = 0.1;
    p.vignette = 0.7;
  }
  if (r.fx.includes('机场')) {
    p.tint = '#ffe6c8';
    p.tintA = 0.12;
    p.bloom = 0.3;
  }
  // 片尾压黑
  p.fade = '#000';
  p.fadeA = Math.max(kf(T, [[0, 1], [0.6, 0]]), kf(T, [[END - 1.0, 0], [END - 0.15, 1]]));
}
