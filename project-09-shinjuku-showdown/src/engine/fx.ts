// 特效库。全部是“时间 → 画面”的纯函数：粒子位置按出生时间解析计算，不做逐帧模拟。
import {H, W, clamp, hash, hrange, lerp, noise1} from './core';
import {Stage} from './stage';

const TAU = Math.PI * 2;

// ---------------- 氛围：雪、雾、余烬、尘埃 ----------------

/** 屏幕空间的雪。depth 0..1：越近越大越快，随相机平移的视差越大。 */
export function snow(st: Stage, t: number, o: {n?: number; depth?: number; speed?: number; wind?: number; size?: number; a?: number; seed?: number; freeze?: number} = {}) {
  const n = o.n ?? 220, depth = o.depth ?? 0.5, seed = o.seed ?? 1;
  const g = st.g;
  const tt = o.freeze != null ? o.freeze : t;
  const panX = st.cam.x * Math.pow(st.cam.z, 0.5) * depth * 0.9, panY = st.cam.y * Math.pow(st.cam.z, 0.5) * depth * 0.9;
  const size = (o.size ?? 3) * (0.6 + depth * 1.6);
  const sp = (o.speed ?? 60) * (0.5 + depth);
  const wind = (o.wind ?? 18) * (0.5 + depth);
  g.save();
  g.globalCompositeOperation = 'source-over';
  g.fillStyle = '#f2f5fb';
  const PW = W + 200, PH = H + 200;
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, seed), h2 = hash(i, seed, 2), h3 = hash(i, seed, 3);
    const v = sp * (0.7 + h3 * 0.6);
    let x = h1 * PW + wind * tt + Math.sin(tt * (0.6 + h2) + i) * 14 * (0.5 + depth) - panX;
    let y = h2 * PH + v * tt - panY;
    x = ((x % PW) + PW) % PW - 100;
    y = ((y % PH) + PH) % PH - 100;
    const s = Math.max(1, Math.round(size * (0.6 + h3 * 0.8)));
    g.globalAlpha = (o.a ?? 0.8) * (0.45 + 0.55 * h3);
    g.fillRect(Math.round(x), Math.round(y), s, s);
  }
  g.restore();
}

/** 漂浮的余烬/灰（屏幕空间，往上飘）。 */
export function embers(st: Stage, t: number, o: {n?: number; color?: string; a?: number; seed?: number; rise?: number; size?: number} = {}) {
  const g = st.g, n = o.n ?? 80, seed = o.seed ?? 5;
  g.save();
  g.globalCompositeOperation = 'lighter';
  g.fillStyle = o.color ?? '#ff9a4a';
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, seed), h2 = hash(i, seed, 2), h3 = hash(i, seed, 3);
    const life = 3 + h3 * 4, ph = (t / life + h2) % 1;
    const x = h1 * W + Math.sin(t * 1.3 + i) * 30 + ph * 60;
    const y = H * (1.05 - ph * (o.rise ?? 1.2)) + h2 * 80;
    const s = Math.max(1, Math.round((o.size ?? 3) * (0.5 + h3)));
    g.globalAlpha = (o.a ?? 0.8) * Math.sin(ph * Math.PI) * (0.5 + 0.5 * Math.sin(t * 9 + i));
    g.fillRect(Math.round(x), Math.round(y), s, s);
  }
  g.restore();
}

/** 光里的浮尘（机场）。 */
export function motes(st: Stage, t: number, o: {n?: number; a?: number; seed?: number} = {}) {
  const g = st.g, n = o.n ?? 70;
  g.save();
  g.globalCompositeOperation = 'lighter';
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, o.seed ?? 8), h2 = hash(i, o.seed ?? 8, 2), h3 = hash(i, o.seed ?? 8, 3);
    const x = h1 * W + noise1(t * 0.2 + i, 3) * 60;
    const y = ((h2 * H + noise1(t * 0.15 + i, 4) * 50 - t * 6) % H + H) % H;
    const r = 1.5 + h3 * 3.5;
    const gr = g.createRadialGradient(x, y, 0, x, y, r * 3);
    gr.addColorStop(0, 'rgba(255,248,225,0.9)');
    gr.addColorStop(1, 'rgba(255,248,225,0)');
    g.globalAlpha = (o.a ?? 0.35) * (0.4 + 0.6 * Math.abs(Math.sin(t * 0.7 + i)));
    g.fillStyle = gr;
    g.fillRect(x - r * 3, y - r * 3, r * 6, r * 6);
  }
  g.restore();
}

/** 贴地的雾带（屏幕空间，缓慢横移的软椭圆）。 */
export function fog(st: Stage, t: number, o: {y?: number; h?: number; color?: string; a?: number; n?: number; seed?: number; drift?: number} = {}) {
  const g = st.g, n = o.n ?? 14, y0 = o.y ?? H * 0.8, hh = o.h ?? 160;
  g.save();
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, o.seed ?? 3), h2 = hash(i, o.seed ?? 3, 2);
    const w = 300 + h2 * 500;
    const x = ((h1 * (W + w) + t * (o.drift ?? 14) * (0.5 + h2) - st.cam.x * st.cam.z * 0.3) % (W + w) + (W + w)) % (W + w) - w / 2;
    const y = y0 + (h2 - 0.5) * hh;
    const gr = g.createRadialGradient(x, y, 0, x, y, w / 2);
    gr.addColorStop(0, o.color ?? 'rgba(200,210,225,0.55)');
    gr.addColorStop(1, 'rgba(200,210,225,0)');
    g.globalAlpha = (o.a ?? 0.35) * (0.6 + 0.4 * h1);
    g.fillStyle = gr;
    g.setTransform(1, 0, 0, 0.35, 0, y * 0.65);
    g.fillRect(x - w / 2, y - w / 2, w, w);
  }
  g.restore();
}

// ---------------- 光 ----------------

/** 从屏幕上一点射出的体积光（加色扇形）。 */
export function rays(st: Stage, x: number, y: number, t: number, o: {n?: number; len?: number; color?: string; a?: number; spread?: number; dir?: number; seed?: number; width?: number} = {}) {
  const g = st.g, n = o.n ?? 9, len = o.len ?? 1400, dir = o.dir ?? Math.PI / 2, spread = o.spread ?? 0.9;
  g.save();
  g.globalCompositeOperation = 'lighter';
  for (let i = 0; i < n; i++) {
    const h = hash(i, o.seed ?? 4);
    const a = dir + (h - 0.5) * spread + Math.sin(t * 0.3 + i) * 0.02;
    const w = (o.width ?? 0.035) * (0.5 + hash(i, 9));
    const gr = g.createLinearGradient(x, y, x + Math.cos(a) * len, y + Math.sin(a) * len);
    gr.addColorStop(0, o.color ?? 'rgba(255,245,220,0.5)');
    gr.addColorStop(1, 'rgba(255,245,220,0)');
    g.globalAlpha = (o.a ?? 0.5) * (0.5 + 0.5 * Math.sin(t * 0.8 + i * 1.7));
    g.fillStyle = gr;
    g.beginPath();
    g.moveTo(x, y);
    g.lineTo(x + Math.cos(a - w) * len, y + Math.sin(a - w) * len);
    g.lineTo(x + Math.cos(a + w) * len, y + Math.sin(a + w) * len);
    g.closePath();
    g.fill();
  }
  g.restore();
}

/** 屏幕空间的放射速度线（冲击、突进瞬间）。 */
export function speedLines(st: Stage, cx: number, cy: number, t: number, o: {n?: number; r0?: number; color?: string; a?: number; seed?: number; width?: number} = {}) {
  const g = st.g, n = o.n ?? 70, r0 = o.r0 ?? 260;
  const fr = Math.floor(t * 30); // 每两帧换一次线（一拍二）
  g.save();
  g.globalCompositeOperation = 'source-over';
  g.fillStyle = o.color ?? '#ffffff';
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, fr, o.seed ?? 1), h2 = hash(i, fr, 7);
    const a = (i / n) * TAU + h1 * 0.08;
    const r1 = r0 + h2 * 220, r2 = r1 + 600 + h1 * 900;
    const w = (o.width ?? 6) * (0.4 + h2);
    g.globalAlpha = (o.a ?? 0.85) * (0.4 + 0.6 * h1);
    g.beginPath();
    g.moveTo(cx + Math.cos(a) * r1, cy + Math.sin(a) * r1);
    g.lineTo(cx + Math.cos(a + w / r2) * r2, cy + Math.sin(a + w / r2) * r2);
    g.lineTo(cx + Math.cos(a - w / r2) * r2, cy + Math.sin(a - w / r2) * r2);
    g.closePath();
    g.fill();
  }
  g.restore();
}

/** 横向速度线（突进、横摇）。 */
export function streaks(st: Stage, t: number, o: {n?: number; dir?: number; color?: string; a?: number; seed?: number; y0?: number; y1?: number} = {}) {
  const g = st.g, n = o.n ?? 40, dir = o.dir ?? 1;
  const fr = Math.floor(t * 30);
  g.save();
  g.fillStyle = o.color ?? '#ffffff';
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, fr, o.seed ?? 3), h2 = hash(i, fr, 5), h3 = hash(i, 11);
    const y = lerp(o.y0 ?? 0, o.y1 ?? H, h3);
    const len = 200 + h2 * 700;
    const x = h1 * (W + len) - len;
    g.globalAlpha = (o.a ?? 0.5) * (0.3 + 0.7 * h2);
    g.fillRect(dir > 0 ? x : W - x - len, y, len, 1 + Math.round(h1 * 3));
  }
  g.restore();
}

// ---------------- 术式 ----------------

/** 苍：白核、钴蓝辉光、向内卷的吸附线、被吸进去的碎屑。r 是世界单位。 */
export function blue(st: Stage, X: number, Y: number, r: number, t: number, o: {a?: number; pull?: number; seed?: number; debris?: number} = {}) {
  const a = o.a ?? 1;
  if (a <= 0.01) return;
  const pull = o.pull ?? 1;
  st.glow(X, Y, r * 7, 'rgba(23,70,200,0.55)', a * 0.9);
  st.glow(X, Y, r * 3.2, 'rgba(57,141,255,0.8)', a);
  // 吸附弧：从外圈卷向中心
  const g = st.g, cx = st.sx(X), cy = st.sy(Y), R = st.s(r);
  g.save();
  g.globalCompositeOperation = 'lighter';
  g.lineCap = 'round';
  const arcs = 14;
  for (let i = 0; i < arcs; i++) {
    const h = hash(i, o.seed ?? 2);
    const ph = (t * (0.9 + h * 0.6) * pull + h) % 1;     // 0 外 → 1 内
    const rad = R * (5.5 - ph * 4.6);
    const a0 = h * TAU + t * 2.2 + ph * 2.5;
    g.globalAlpha = a * Math.sin(ph * Math.PI) * 0.75;
    g.strokeStyle = h > 0.5 ? '#b9e8ff' : '#398dff';
    g.lineWidth = Math.max(1, R * (0.06 + 0.1 * ph));
    g.beginPath();
    g.arc(cx, cy, rad, a0, a0 + 0.9 + h * 0.8);
    g.stroke();
  }
  // 碎屑
  const nd = o.debris ?? 26;
  g.fillStyle = '#cfe6ff';
  for (let i = 0; i < nd; i++) {
    const h1 = hash(i, 31), h2 = hash(i, 32);
    const ph = (t * (0.5 + h2 * 0.7) * pull + h1) % 1;
    const rad = R * (7 - ph * 6.6), ang = h1 * TAU + ph * 4;
    const s = Math.max(1.5, R * 0.12 * (1 - ph * 0.6));
    g.globalAlpha = a * (0.3 + 0.7 * Math.sin(ph * Math.PI));
    g.fillRect(cx + Math.cos(ang) * rad - s / 2, cy + Math.sin(ang) * rad - s / 2, s, s);
  }
  g.restore();
  st.disc(X, Y, r * 0.9, '#e8f6ff', a, 'lighter');
  st.glow(X, Y, r * 1.6, 'rgba(255,255,255,0.95)', a);
}

/** 赫：红核、向外的放射光、闪烁。 */
export function red(st: Stage, X: number, Y: number, r: number, t: number, o: {a?: number; seed?: number} = {}) {
  const a = o.a ?? 1;
  if (a <= 0.01) return;
  const fl = 0.85 + 0.15 * Math.sin(t * 47);
  st.glow(X, Y, r * 6.5, 'rgba(165,20,38,0.6)', a * fl);
  st.glow(X, Y, r * 3, 'rgba(240,68,62,0.85)', a);
  const g = st.g, cx = st.sx(X), cy = st.sy(Y), R = st.s(r);
  g.save();
  g.globalCompositeOperation = 'lighter';
  const n = 16, fr = Math.floor(t * 30);
  for (let i = 0; i < n; i++) {
    const h = hash(i, fr, o.seed ?? 4);
    const ang = (i / n) * TAU + t * 1.5 + h * 0.3;
    const r1 = R * 1.1, r2 = R * (2.4 + h * 2.6);
    g.globalAlpha = a * (0.35 + 0.5 * h);
    g.strokeStyle = h > 0.6 ? '#ffd2c4' : '#f0443e';
    g.lineWidth = Math.max(1, R * 0.12);
    g.beginPath();
    g.moveTo(cx + Math.cos(ang) * r1, cy + Math.sin(ang) * r1);
    g.lineTo(cx + Math.cos(ang) * r2, cy + Math.sin(ang) * r2);
    g.stroke();
  }
  g.restore();
  st.disc(X, Y, r * 0.85, '#fff0ea', a, 'lighter');
  st.glow(X, Y, r * 1.5, 'rgba(255,240,230,0.95)', a);
}

/** 茈的核：紫色球、亮边、中间一道暗缝（蓝红交界）、电弧。 */
export function purple(st: Stage, X: number, Y: number, r: number, t: number, o: {a?: number; seam?: number; seed?: number} = {}) {
  const a = o.a ?? 1;
  if (a <= 0.01) return;
  st.glow(X, Y, r * 8, 'rgba(85,25,184,0.55)', a);
  st.glow(X, Y, r * 3.5, 'rgba(168,77,255,0.85)', a);
  st.disc(X, Y, r, '#2a0b4a', a * 0.85);
  st.ring(X, Y, r * 0.96, r * 0.18, '#e0b7ff', a, 'lighter');
  st.ring(X, Y, r * 1.08, r * 0.06, '#ffffff', a * 0.8, 'lighter');
  crackle(st, X, Y, r * 1.6, Math.floor(t * 20) + (o.seed ?? 0), '#e0b7ff', 7, a * 0.8);
  const seam = o.seam ?? 0;
  if (seam > 0.01) {
    st.line(X, Y - r * 0.9, X, Y + r * 0.9, r * 0.12, '#05010a', a * seam, 'source-over');
  }
}

/** 电弧：从中心往外的锯齿折线。 */
export function crackle(st: Stage, X: number, Y: number, R: number, seed: number, color: string, n: number, a = 1, width = 1.2) {
  const g = st.g;
  g.save();
  g.globalCompositeOperation = 'lighter';
  g.strokeStyle = color;
  g.lineWidth = Math.max(1, st.s(width) * 0.5);
  g.globalAlpha = clamp(a);
  for (let i = 0; i < n; i++) {
    const ang = hash(i, seed) * TAU;
    let x = X + Math.cos(ang) * R * 0.5, y = Y + Math.sin(ang) * R * 0.5;
    g.beginPath();
    g.moveTo(st.sx(x), st.sy(y));
    const steps = 4;
    for (let k = 1; k <= steps; k++) {
      const rr = R * (0.5 + (k / steps) * 0.7);
      const aa = ang + (hash(i, seed, k) - 0.5) * 0.9;
      x = X + Math.cos(aa) * rr;
      y = Y + Math.sin(aa) * rr;
      g.lineTo(st.sx(x), st.sy(y));
    }
    g.stroke();
  }
  g.restore();
}

/** 黑闪：黑红闪电从接触点炸开。k 0..1 是这次闪光的进度。 */
export function blackFlash(st: Stage, X: number, Y: number, R: number, k: number, seed = 1) {
  if (k <= 0 || k >= 1) return;
  const g = st.g;
  const f = 1 - k;
  // 背景压暗（以接触点为中心）
  const cx = st.sx(X), cy = st.sy(Y);
  g.save();
  const gr = g.createRadialGradient(cx, cy, 0, cx, cy, Math.max(W, H));
  gr.addColorStop(0, 'rgba(0,0,0,0)');
  gr.addColorStop(0.25, `rgba(10,0,4,${0.55 * f})`);
  gr.addColorStop(1, `rgba(0,0,0,${0.8 * f})`);
  g.fillStyle = gr;
  g.fillRect(0, 0, W, H);
  g.restore();
  const n = 9;
  for (let pass = 0; pass < 2; pass++) {
    g.save();
    g.globalCompositeOperation = pass ? 'lighter' : 'source-over';
    g.strokeStyle = pass ? '#f23b4b' : '#060208';
    g.lineJoin = 'miter';
    g.lineWidth = (pass ? 2.5 : 7) * (0.5 + f) * Math.min(1.6, Math.max(0.7, st.cam.z / 3));
    g.globalAlpha = pass ? f : Math.min(1, f * 1.6);
    for (let i = 0; i < n; i++) {
      const ang = hash(i, seed) * TAU;
      const len = R * (0.6 + hash(i, seed, 3) * 0.9) * (0.4 + k * 1.2);
      let x = X, y = Y;
      g.beginPath();
      g.moveTo(st.sx(x), st.sy(y));
      const steps = 6;
      for (let s = 1; s <= steps; s++) {
        const aa = ang + (hash(i, seed, s + 10) - 0.5) * 1.3;
        x += (Math.cos(aa) * len) / steps;
        y += (Math.sin(aa) * len) / steps;
        g.lineTo(st.sx(x), st.sy(y));
      }
      g.stroke();
    }
    g.restore();
  }
  st.glow(X, Y, R * 0.5, 'rgba(242,59,75,0.9)', f);
  st.disc(X, Y, R * 0.12 * f, '#000000', 1);
}

/** 粗光束（定向茈）：外辉光 + 紫 + 白芯，边缘有湍动。p 是“多粗”的比例 0..1。 */
export function beam(st: Stage, X1: number, Y1: number, X2: number, Y2: number, w: number, t: number, o: {a?: number; core?: string; mid?: string; outer?: string} = {}) {
  const a = o.a ?? 1;
  if (a <= 0.01) return;
  const g = st.g;
  const x1 = st.sx(X1), y1 = st.sy(Y1), x2 = st.sx(X2), y2 = st.sy(Y2);
  const L = Math.hypot(x2 - x1, y2 - y1), ang = Math.atan2(y2 - y1, x2 - x1);
  const Wd = st.s(w);
  g.save();
  g.translate(x1, y1);
  g.rotate(ang);
  g.globalCompositeOperation = 'lighter';
  const layers: [number, string, number][] = [
    [2.6, o.outer ?? 'rgba(85,25,184,0.5)', 0.8],
    [1.5, o.mid ?? 'rgba(168,77,255,0.85)', 1],
    [0.75, 'rgba(224,183,255,0.95)', 1],
    [0.38, o.core ?? '#ffffff', 1],
  ];
  for (const [k, c, aa] of layers) {
    const hw = (Wd * k) / 2;
    const gr = g.createLinearGradient(0, -hw, 0, hw);
    gr.addColorStop(0, 'rgba(0,0,0,0)');
    gr.addColorStop(0.3, c);
    gr.addColorStop(0.7, c);
    gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr;
    g.globalAlpha = a * aa;
    g.beginPath();
    const seg = 24;
    for (let i = 0; i <= seg; i++) {
      const u = (i / seg) * L;
      const wob = 1 + 0.12 * noise1(u * 0.01 - t * 9, 3 + k);
      g.lineTo(u, -hw * wob);
    }
    for (let i = seg; i >= 0; i--) {
      const u = (i / seg) * L;
      const wob = 1 + 0.12 * noise1(u * 0.01 - t * 9, 9 + k);
      g.lineTo(u, hw * wob);
    }
    g.closePath();
    g.fill();
  }
  // 沿光束流动的亮片
  g.fillStyle = '#ffffff';
  for (let i = 0; i < 60; i++) {
    const h = hash(i, 77);
    const u = ((h * L + t * 2600 * (0.7 + hash(i, 78) * 0.6)) % L);
    const v = (hash(i, 79) - 0.5) * Wd * 1.6;
    g.globalAlpha = a * 0.7;
    g.fillRect(u, v, 18 + h * 40, 2 + h * 3);
  }
  g.restore();
}

/** 冲击波环：从 t0 起扩张到 r1，持续 dur 秒。 */
export function shock(st: Stage, X: number, Y: number, t: number, t0: number, dur: number, r1: number, o: {color?: string; width?: number; a?: number; flat?: number} = {}) {
  const u = (t - t0) / dur;
  if (u < 0 || u > 1) return;
  const k = 1 - Math.pow(1 - u, 3);
  const g = st.g;
  g.save();
  g.globalCompositeOperation = 'lighter';
  g.strokeStyle = o.color ?? '#ffffff';
  g.globalAlpha = (o.a ?? 1) * (1 - u);
  g.lineWidth = Math.max(1, st.s((o.width ?? 6) * (1 - u * 0.7)));
  g.beginPath();
  g.ellipse(st.sx(X), st.sy(Y), st.s(r1 * k), st.s(r1 * k * (o.flat ?? 1)), 0, 0, TAU);
  g.stroke();
  g.restore();
}

/** 火花：从 (X,Y) 在 t0 时炸开，带重力。 */
export function sparks(st: Stage, X: number, Y: number, t: number, t0: number, o: {n?: number; speed?: number; life?: number; color?: string; color2?: string; seed?: number; dir?: number; spread?: number; grav?: number; len?: number; a?: number} = {}) {
  const u = t - t0;
  const life = o.life ?? 0.45;
  if (u < 0 || u > life * 1.4) return;
  const g = st.g, n = o.n ?? 18;
  g.save();
  g.globalCompositeOperation = 'lighter';
  g.lineCap = 'round';
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, o.seed ?? 6), h2 = hash(i, o.seed ?? 6, 2), h3 = hash(i, o.seed ?? 6, 3);
    const lf = life * (0.5 + h3 * 0.8);
    if (u > lf) continue;
    const ang = (o.dir ?? 0) + (h1 - 0.5) * (o.spread ?? TAU);
    const v = (o.speed ?? 260) * (0.4 + h2);
    const drag = Math.exp(-u * 3);
    const dist = (v * (1 - drag)) / 3;
    const x = X + Math.cos(ang) * dist;
    const y = Y + Math.sin(ang) * dist + 0.5 * (o.grav ?? 120) * u * u;
    const vx = Math.cos(ang) * v * drag, vy = Math.sin(ang) * v * drag + (o.grav ?? 120) * u;
    const len = (o.len ?? 0.04);
    g.globalAlpha = (o.a ?? 1) * (1 - u / lf);
    g.strokeStyle = h3 > 0.5 ? (o.color ?? '#ffffff') : (o.color2 ?? o.color ?? '#ffd27a');
    g.lineWidth = Math.max(1, st.s(1.1 * (1 - u / lf)));
    g.beginPath();
    g.moveTo(st.sx(x), st.sy(y));
    g.lineTo(st.sx(x - vx * len), st.sy(y - vy * len));
    g.stroke();
  }
  g.restore();
}

/** 碎块（像素层）：从 (X,Y) 抛出，带重力；地面 y=ground 处停住。 */
export function debris(st: Stage, X: number, Y: number, t: number, t0: number, o: {n?: number; speed?: number; seed?: number; dir?: number; spread?: number; grav?: number; colors?: string[]; size?: number; ground?: number; life?: number} = {}) {
  const u = t - t0;
  if (u < 0 || u > (o.life ?? 3)) return;
  const n = o.n ?? 16, cols = o.colors ?? ['#5b6170', '#3b404c', '#8a909c', '#272a33'];
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, o.seed ?? 9), h2 = hash(i, o.seed ?? 9, 2), h3 = hash(i, o.seed ?? 9, 3);
    const ang = (o.dir ?? -Math.PI / 2) + (h1 - 0.5) * (o.spread ?? 2.4);
    const v = (o.speed ?? 200) * (0.35 + h2);
    let x = X + Math.cos(ang) * v * u;
    let y = Y + Math.sin(ang) * v * u + 0.5 * (o.grav ?? 400) * u * u;
    const gnd = o.ground ?? 1e9;
    if (y > gnd) {
      y = gnd;
      // 落地后不再横移太远
      const tl = solveLand(Y, Math.sin(ang) * v, o.grav ?? 400, gnd);
      x = X + Math.cos(ang) * v * Math.min(u, tl + 0.15);
    }
    const s = Math.max(1, Math.round((o.size ?? 3) * (0.5 + h3)));
    st.pixRect(x - s / 2, y - s, s, s, cols[i % cols.length], u > (o.life ?? 3) - 0.5 ? ((o.life ?? 3) - u) * 2 : 1);
  }
}
const solveLand = (y0: number, vy: number, g: number, gnd: number) => {
  // y0 + vy t + g t²/2 = gnd
  const a = g / 2, b = vy, c = y0 - gnd;
  const d = b * b - 4 * a * c;
  if (d < 0) return 1e9;
  return (-b + Math.sqrt(d)) / (2 * a);
};

/** 烟尘团：一堆软圆在 t0 后膨胀、上浮、变淡。 */
export function dust(st: Stage, X: number, Y: number, t: number, t0: number, o: {n?: number; r?: number; spread?: number; rise?: number; life?: number; color?: string; a?: number; seed?: number; dir?: number} = {}) {
  const u = t - t0, life = o.life ?? 2.2;
  if (u < 0 || u > life) return;
  const n = o.n ?? 12, R = o.r ?? 18, g = st.g;
  const k = 1 - Math.pow(1 - u / life, 2);
  g.save();
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, o.seed ?? 12), h2 = hash(i, o.seed ?? 12, 2), h3 = hash(i, o.seed ?? 12, 3);
    const ang = (o.dir ?? -Math.PI / 2) + (h1 - 0.5) * Math.PI * 1.6;
    const dist = (o.spread ?? 40) * (0.3 + h2) * Math.pow(k, 0.6);
    const x = X + Math.cos(ang) * dist, y = Y + Math.sin(ang) * dist * 0.5 - (o.rise ?? 12) * k * (0.5 + h3);
    const r = R * (0.5 + h3) * (0.5 + k * 1.2);
    const sx = st.sx(x), sy = st.sy(y), sr = st.s(r);
    const gr = g.createRadialGradient(sx, sy, 0, sx, sy, sr);
    const col = o.color ?? '150,152,160';
    gr.addColorStop(0, `rgba(${col},0.9)`);
    gr.addColorStop(0.6, `rgba(${col},0.5)`);
    gr.addColorStop(1, `rgba(${col},0)`);
    g.globalAlpha = (o.a ?? 0.7) * (1 - u / life) * (0.6 + 0.4 * h2);
    g.fillStyle = gr;
    g.fillRect(sx - sr, sy - sr, sr * 2, sr * 2);
  }
  g.restore();
}

/** “解”的斩线：一瞬间出现的细白线（黑边），随后变细消失。 */
export function slashLine(st: Stage, X1: number, Y1: number, X2: number, Y2: number, t: number, t0: number, o: {life?: number; w?: number; color?: string; a?: number; draw?: number} = {}) {
  const u = t - t0, life = o.life ?? 0.28;
  if (u < 0 || u > life) return;
  const drawT = o.draw ?? 0.035;
  const k = clamp(u / drawT);
  const xe = lerp(X1, X2, k), ye = lerp(Y1, Y2, k);
  const f = 1 - u / life;
  const w = (o.w ?? 1.6) * (0.3 + 0.7 * f);
  st.line(X1, Y1, xe, ye, w * 3.2, '#000000', (o.a ?? 1) * f * 0.85, 'source-over', 'butt');
  st.line(X1, Y1, xe, ye, w, o.color ?? '#ffffff', (o.a ?? 1) * Math.min(1, f * 1.5), 'source-over', 'butt');
  st.line(X1, Y1, xe, ye, w * 4, 'rgba(220,224,228,0.5)', (o.a ?? 1) * f * 0.5, 'lighter', 'butt');
}

/** 穿血：细高压血束。 */
export function jet(st: Stage, X1: number, Y1: number, X2: number, Y2: number, t: number, o: {a?: number; w?: number} = {}) {
  const a = o.a ?? 1;
  if (a <= 0.01) return;
  const w = o.w ?? 1.6;
  st.line(X1, Y1, X2, Y2, w * 5, 'rgba(201,30,50,0.45)', a, 'lighter');
  st.line(X1, Y1, X2, Y2, w * 2, '#c91e32', a, 'source-over');
  st.line(X1, Y1, X2, Y2, w * 0.7, '#ff6670', a, 'lighter');
  // 沿线飞溅的血珠
  for (let i = 0; i < 18; i++) {
    const h = hash(i, Math.floor(t * 30));
    const x = lerp(X1, X2, h), y = lerp(Y1, Y2, h);
    st.disc(x + (hash(i, 3) - 0.5) * 6, y + (hash(i, 4) - 0.5) * 6, 0.5 + h * 0.6, '#ff6670', a * 0.8, 'lighter');
  }
}

/** 反转术式：绿白微光 + 上升的亮点。 */
export function rct(st: Stage, X: number, Y: number, r: number, t: number, a = 1) {
  if (a <= 0.01) return;
  st.glow(X, Y, r * 2.2, 'rgba(160,255,200,0.55)', a * (0.7 + 0.3 * Math.sin(t * 12)));
  st.glow(X, Y, r, 'rgba(240,255,245,0.8)', a);
  for (let i = 0; i < 16; i++) {
    const h1 = hash(i, 51), h2 = hash(i, 52);
    const ph = (t * (0.8 + h2) + h1) % 1;
    st.disc(X + (h1 - 0.5) * r * 2.4, Y + r * 0.8 - ph * r * 2.5, 0.6 + h2 * 0.5, '#d8ffe6', a * Math.sin(ph * Math.PI), 'lighter');
  }
}

// ---------------- 领域 ----------------

/** 结界球（从外面看）：暗蓝内部、亮边、星点；cracks 0..1 是裂纹扩散程度。 */
export function barrierSphere(st: Stage, X: number, Y: number, r: number, t: number, o: {a?: number; cracks?: number; seed?: number; color?: string} = {}) {
  const a = o.a ?? 1;
  const g = st.g, cx = st.sx(X), cy = st.sy(Y), R = st.s(r);
  g.save();
  g.globalAlpha = a;
  const gr = g.createRadialGradient(cx - R * 0.3, cy - R * 0.35, R * 0.1, cx, cy, R);
  gr.addColorStop(0, '#1a2a6a');
  gr.addColorStop(0.7, '#070b22');
  gr.addColorStop(0.97, '#263a7a');
  gr.addColorStop(1, 'rgba(183,215,255,0.9)');
  g.fillStyle = gr;
  g.beginPath();
  g.arc(cx, cy, R, 0, TAU);
  g.fill();
  // 星点
  g.clip();
  g.fillStyle = '#b7d7ff';
  for (let i = 0; i < 120; i++) {
    const h1 = hash(i, 61), h2 = hash(i, 62), h3 = hash(i, 63);
    const ang = h1 * TAU + t * 0.05 * (h3 - 0.5), rr = Math.sqrt(h2) * R;
    g.globalAlpha = a * (0.3 + 0.7 * h3) * (0.6 + 0.4 * Math.sin(t * 3 + i));
    const s = 1 + h3 * 2.5;
    g.fillRect(cx + Math.cos(ang) * rr, cy + Math.sin(ang) * rr, s, s);
  }
  g.restore();
  st.glow(X, Y, r * 1.25, 'rgba(70,120,255,0.35)', a);
  st.ring(X, Y, r, r * 0.02, '#cfe3ff', a * 0.9, 'lighter');
  const c = o.cracks ?? 0;
  if (c > 0.01) sphereCracks(st, X, Y, r, c, o.seed ?? 3, a);
}

export function sphereCracks(st: Stage, X: number, Y: number, r: number, k: number, seed: number, a = 1) {
  const g = st.g;
  g.save();
  g.lineJoin = 'miter';
  for (let pass = 0; pass < 2; pass++) {
    g.globalCompositeOperation = pass ? 'lighter' : 'source-over';
    g.strokeStyle = pass ? '#e8f2ff' : '#02040c';
    g.lineWidth = pass ? Math.max(1, st.s(r * 0.008)) : Math.max(2, st.s(r * 0.02));
    g.globalAlpha = a;
    for (let i = 0; i < 12; i++) {
      // 从边上的落点往里裂
      const ang = hash(i, seed) * TAU;
      let x = X + Math.cos(ang) * r, y = Y + Math.sin(ang) * r;
      const steps = 7, len = r * (0.5 + hash(i, seed, 2) * 0.7) * k;
      g.beginPath();
      g.moveTo(st.sx(x), st.sy(y));
      for (let s = 1; s <= steps; s++) {
        const aa = ang + Math.PI + (hash(i, seed, s + 3) - 0.5) * 1.4;
        x += (Math.cos(aa) * len) / steps;
        y += (Math.sin(aa) * len) / steps;
        g.lineTo(st.sx(x), st.sy(y));
      }
      g.stroke();
    }
  }
  g.restore();
}

/** 圆形遮罩里画一张背景（领域吞没画面）。 */
export function revealCircle(st: Stage, X: number, Y: number, r: number, draw: () => void, rim = '#b7d7ff', rimA = 1) {
  const g = st.g;
  g.save();
  g.beginPath();
  g.arc(st.sx(X), st.sy(Y), Math.max(0, st.s(r)), 0, TAU);
  g.clip();
  draw();
  g.restore();
  if (rimA > 0.01) {
    st.ring(X, Y, r, Math.max(1.2, r * 0.012), rim, rimA, 'lighter');
    st.glow(X, Y, r * 1.08, 'rgba(120,170,255,0.25)', rimA * 0.6, 'lighter', 0.85);
  }
}

/** 无量空处里的信息流：从中心向外喷涌的细光点（屏幕空间）。 */
export function infoStream(st: Stage, cx: number, cy: number, t: number, o: {n?: number; a?: number; color?: string; speed?: number; seed?: number} = {}) {
  const g = st.g, n = o.n ?? 260;
  g.save();
  g.globalCompositeOperation = 'lighter';
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, o.seed ?? 71), h2 = hash(i, o.seed ?? 71, 2), h3 = hash(i, o.seed ?? 71, 3);
    const ph = (t * (o.speed ?? 0.35) * (0.6 + h2) + h1) % 1;
    const ang = h3 * TAU;
    const rr = Math.pow(ph, 2.2) * 1400;
    const x = cx + Math.cos(ang) * rr, y = cy + Math.sin(ang) * rr * 0.75;
    const len = 4 + ph * 50;
    g.globalAlpha = (o.a ?? 0.8) * Math.min(1, ph * 4) * (1 - ph);
    g.strokeStyle = h2 > 0.7 ? '#ffffff' : (o.color ?? '#9cc8ff');
    g.lineWidth = 1 + ph * 2.5;
    g.beginPath();
    g.moveTo(x, y);
    g.lineTo(x - Math.cos(ang) * len, y - Math.sin(ang) * len * 0.75);
    g.stroke();
  }
  g.restore();
}

/** 魔虚罗头上的八辐车轮（法阵）。ang 是转了多少度。 */
export function wheel(st: Stage, X: number, Y: number, r: number, ang: number, o: {a?: number; lit?: number} = {}) {
  const a = o.a ?? 1;
  if (a <= 0.01) return;
  const g = st.g, cx = st.sx(X), cy = st.sy(Y), R = st.s(r);
  const lit = o.lit ?? 0;
  if (lit > 0.01) st.glow(X, Y, r * 2.2, 'rgba(194,154,78,0.6)', a * lit);
  g.save();
  g.translate(cx, cy);
  g.rotate((ang * Math.PI) / 180);
  g.globalAlpha = a;
  const gold = '#c29a4e', dark = '#3b2a12', hi = '#f3d58a';
  g.lineCap = 'round';
  // 外圈
  g.strokeStyle = dark;
  g.lineWidth = R * 0.16;
  g.beginPath();
  g.arc(0, 0, R * 0.82, 0, TAU);
  g.stroke();
  g.strokeStyle = gold;
  g.lineWidth = R * 0.09;
  g.stroke();
  // 八根辐条 + 末端圆头
  for (let i = 0; i < 8; i++) {
    const aa = (i / 8) * TAU;
    const ex = Math.cos(aa) * R, ey = Math.sin(aa) * R;
    g.strokeStyle = dark;
    g.lineWidth = R * 0.12;
    g.beginPath();
    g.moveTo(Math.cos(aa) * R * 0.18, Math.sin(aa) * R * 0.18);
    g.lineTo(ex * 0.95, ey * 0.95);
    g.stroke();
    g.strokeStyle = gold;
    g.lineWidth = R * 0.06;
    g.stroke();
    g.fillStyle = gold;
    g.beginPath();
    g.arc(ex, ey, R * 0.1, 0, TAU);
    g.fill();
    g.fillStyle = hi;
    g.beginPath();
    g.arc(ex - R * 0.03, ey - R * 0.03, R * 0.04, 0, TAU);
    g.fill();
  }
  // 轮毂
  g.fillStyle = dark;
  g.beginPath();
  g.arc(0, 0, R * 0.2, 0, TAU);
  g.fill();
  g.fillStyle = gold;
  g.beginPath();
  g.arc(0, 0, R * 0.13, 0, TAU);
  g.fill();
  g.restore();
  if (lit > 0.01) st.ring(X, Y, r * 0.82, r * 0.05, hi, a * lit, 'lighter');
}

/** 玻璃/结界碎片：三角形向外飞、旋转、淡出（屏幕空间中心 + 世界尺度）。 */
export function shards(st: Stage, X: number, Y: number, t: number, t0: number, o: {n?: number; speed?: number; size?: number; color?: string; seed?: number; life?: number; a?: number} = {}) {
  const u = t - t0, life = o.life ?? 1.4;
  if (u < 0 || u > life) return;
  const g = st.g, n = o.n ?? 40;
  g.save();
  for (let i = 0; i < n; i++) {
    const h1 = hash(i, o.seed ?? 21), h2 = hash(i, o.seed ?? 21, 2), h3 = hash(i, o.seed ?? 21, 3);
    const ang = h1 * TAU, v = (o.speed ?? 300) * (0.3 + h2);
    const d = (v * (1 - Math.exp(-u * 2.5))) / 2.5;
    const x = st.sx(X + Math.cos(ang) * d), y = st.sy(Y + Math.sin(ang) * d + 40 * u * u);
    const s = st.s((o.size ?? 8) * (0.4 + h3));
    g.save();
    g.translate(x, y);
    g.rotate(h1 * 6 + u * (h2 - 0.5) * 12);
    g.globalAlpha = (o.a ?? 0.9) * (1 - u / life);
    g.fillStyle = o.color ?? 'rgba(183,215,255,0.8)';
    g.beginPath();
    g.moveTo(0, -s);
    g.lineTo(s * 0.7, s * 0.6);
    g.lineTo(-s * 0.5, s * 0.4);
    g.closePath();
    g.fill();
    g.globalCompositeOperation = 'lighter';
    g.strokeStyle = '#ffffff';
    g.lineWidth = 1;
    g.stroke();
    g.restore();
  }
  g.restore();
}

/** 地面阴影（角色脚下的椭圆）。 */
export function shadow(st: Stage, X: number, Y: number, w: number, a = 0.45) {
  const g = st.g, cx = st.sx(X), cy = st.sy(Y), R = st.s(w);
  g.save();
  g.globalAlpha = a;
  const gr = g.createRadialGradient(cx, cy, 0, cx, cy, R);
  gr.addColorStop(0, 'rgba(0,0,0,0.9)');
  gr.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = gr;
  g.setTransform(1, 0, 0, 0.28, 0, cy * 0.72);
  g.fillRect(cx - R, cy - R, R * 2, R * 2);
  g.restore();
}

/** 眼睛发光（一对）。 */
export function eyeGlow(st: Stage, eyes: [number, number][], color: string, a: number, r = 3) {
  if (a <= 0.01) return;
  for (const [x, y] of eyes) {
    st.glow(x, y, r * 2.4, color, a * 0.6);
    st.glow(x, y, r * 0.9, '#ffffff', a * 0.8);
  }
}

/** 地面上的增幅阵纹（椭圆环 + 刻度），屏幕空间压扁。 */
export function sigil(st: Stage, X: number, Y: number, r: number, t: number, o: {a?: number; color?: string; rot?: number} = {}) {
  const a = o.a ?? 1;
  if (a <= 0.01) return;
  const g = st.g, cx = st.sx(X), cy = st.sy(Y), R = st.s(r);
  g.save();
  g.translate(cx, cy);
  g.scale(1, 0.32);
  g.globalCompositeOperation = 'lighter';
  g.globalAlpha = a;
  g.strokeStyle = o.color ?? '#9fd0ff';
  g.lineWidth = 2;
  for (const k of [1, 0.82, 0.55]) {
    g.beginPath();
    g.arc(0, 0, R * k, 0, TAU);
    g.stroke();
  }
  const rot = (o.rot ?? 0.4) * t;
  for (let i = 0; i < 24; i++) {
    const aa = (i / 24) * TAU + rot;
    g.beginPath();
    g.moveTo(Math.cos(aa) * R * 0.84, Math.sin(aa) * R * 0.84);
    g.lineTo(Math.cos(aa) * R * (i % 3 === 0 ? 0.98 : 0.92), Math.sin(aa) * R * (i % 3 === 0 ? 0.98 : 0.92));
    g.stroke();
  }
  for (let i = 0; i < 6; i++) {
    const aa = (i / 6) * TAU - rot * 0.7;
    g.beginPath();
    g.moveTo(Math.cos(aa) * R * 0.55, Math.sin(aa) * R * 0.55);
    g.lineTo(Math.cos(aa + (TAU / 6) * 2) * R * 0.55, Math.sin(aa + (TAU / 6) * 2) * R * 0.55);
    g.stroke();
  }
  g.restore();
  st.glow(X, Y, r * 0.9, 'rgba(120,180,255,0.3)', a);
}

/** 帐：从天上往下罩的半透明黑色穹顶（屏幕空间），k 0..1 是落下的进度。 */
export function curtain(st: Stage, cx: number, k: number, o: {a?: number; w?: number} = {}) {
  if (k <= 0) return;
  const g = st.g, w = o.w ?? 2400;
  const top = -H * 0.2, bottom = lerp(-H * 0.2, H * 0.78, k);
  g.save();
  g.globalAlpha = o.a ?? 0.55;
  const gr = g.createLinearGradient(0, top, 0, bottom);
  gr.addColorStop(0, 'rgba(8,6,14,0.95)');
  gr.addColorStop(0.85, 'rgba(14,10,26,0.75)');
  gr.addColorStop(1, 'rgba(40,30,70,0.2)');
  g.fillStyle = gr;
  g.beginPath();
  g.ellipse(cx, bottom, w / 2, bottom - top, 0, Math.PI, TAU);
  g.closePath();
  g.fill();
  // 落下的边缘一道亮线
  g.globalCompositeOperation = 'lighter';
  g.strokeStyle = 'rgba(150,130,220,0.6)';
  g.lineWidth = 3;
  g.beginPath();
  g.ellipse(cx, bottom, w / 2, bottom - top, 0, Math.PI, TAU);
  g.stroke();
  g.restore();
}
