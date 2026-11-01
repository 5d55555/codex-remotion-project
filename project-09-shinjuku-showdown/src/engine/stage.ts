// 一帧的舞台：相机、背景层、像素角色层、高清特效层、后期。
// 世界坐标 = 角色像素（脚底 y=0，向上为负）。相机 z = 一个角色像素在屏幕上占几个像素。
import {H, W, clamp, hash} from './core';
import {PlateName, PortraitName, plate as plateImg, portrait as portraitImg} from './assets';
import {FigInfo, FigOpts, Pose, RigDef, drawFigure} from './rig';
import {sprite} from './assets';

export type Cam = {x: number; y: number; z: number};

export type TextItem = {
  kind: 'card' | 'chant' | 'quote' | 'tech' | 'title' | 'inner' | 'sub';
  jp: string;
  cn?: string;
  age: number;      // 出现了几秒
  dur: number;      // 总共显示几秒
  x?: number;       // 0..1 屏幕位置
  y?: number;
  color?: string;
  size?: number;
  align?: 'left' | 'center' | 'right';
  vertical?: boolean;
  dark?: boolean;   // 亮背景上用深色字
};

export type PostOpts = {
  bloom: number;       // 辉光强度
  bloomTh: number;     // 阈值（contrast 倍数）
  vignette: number;
  grain: number;
  letterbox: number;   // 0..1：1 = 2.39:1 遮幅
  desat: number;       // 0..1 去饱和
  tint: string | null; // soft-light 叠色
  tintA: number;
  lift: number;        // 暗部提亮（雾感），0..1
  fade: string | null; // 最后整帧盖一层颜色（黑场/白场）
  fadeA: number;
  invert: number;      // 负片冲击帧 0..1
  chroma: number;      // 色差（屏幕像素）
};
export const defaultPost = (): PostOpts => ({
  bloom: 0.45, bloomTh: 2.2, vignette: 0.35, grain: 0.07, letterbox: 0, desat: 0, tint: null, tintA: 0,
  lift: 0, fade: null, fadeA: 0, invert: 0, chroma: 0,
});

const mk = (w: number, h: number) => {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return c;
};

export class Stage {
  readonly canvas: HTMLCanvasElement;
  readonly g: CanvasRenderingContext2D;
  cam: Cam = {x: 0, y: 0, z: 1};
  shx = 0;
  shy = 0;
  T = 0;
  t = 0;
  /** 故事板的倍数按 55 像素的小人定，实际画面统一再推近这么多；个别远景镜头自己设回 1。 */
  zoomBoost = 1.3;
  texts: TextItem[] = [];
  post: PostOpts = defaultPost();

  // 像素层
  private pc: HTMLCanvasElement;
  readonly pg: CanvasRenderingContext2D;
  pox = 0;
  poy = 0;
  private pfx = 0;
  private pfy = 0;
  private pw = 0;
  private ph = 0;
  private dirty = false;

  // 后期用的小画布
  private bc = mk(W / 4, H / 4);
  private bg2 = this.bc.getContext('2d')!;
  private blurC = mk(W / 2, H / 2);
  private blurG = this.blurC.getContext('2d')!;
  private vig: HTMLCanvasElement;
  private grains: HTMLCanvasElement[] = [];
  private grainPats: CanvasPattern[] = [];
  readonly copyC = mk(W, H);
  readonly copyG = this.copyC.getContext('2d')!;
  private copy2C = mk(W, H);
  private copy2G = this.copy2C.getContext('2d')!;

  /** 把当前画面拍一张快照到 copyC（切开画面用）。 */
  snapshot() {
    this.copyG.setTransform(1, 0, 0, 1, 0, 0);
    this.copyG.globalCompositeOperation = 'copy';
    this.copyG.globalAlpha = 1;
    this.copyG.drawImage(this.canvas, 0, 0);
    this.copyG.globalCompositeOperation = 'source-over';
  }

  /**
   * 沿屏幕上的一条直线把画面切开：直线一侧（normal 指向的那侧）整体沿直线方向平移 shift、
   * 垂直方向推开 gap 像素，露出的缝里填 gapColor。世界斩和“解”切开背景都用它。
   */
  slice(x1: number, y1: number, x2: number, y2: number, shift: number, gap: number, gapColor = '#000', gapA = 1, region?: [number, number][]) {
    this.snapshot();
    const g = this.g;
    const dx = x2 - x1, dy = y2 - y1, L = Math.hypot(dx, dy) || 1;
    const ux = dx / L, uy = dy / L, nx = -uy, ny = ux;
    const far = 4000;
    const half: [number, number][] = [
      [x1 - ux * far, y1 - uy * far],
      [x1 + ux * far, y1 + uy * far],
      [x1 + ux * far + nx * far, y1 + uy * far + ny * far],
      [x1 - ux * far + nx * far, y1 - uy * far + ny * far],
    ];
    g.save();
    g.setTransform(1, 0, 0, 1, 0, 0);
    if (region) {
      g.beginPath();
      region.forEach(([x, y], i) => (i ? g.lineTo(x, y) : g.moveTo(x, y)));
      g.closePath();
      g.clip();
    }
    // 缝
    if (gap > 0.2) {
      g.beginPath();
      half.forEach(([x, y], i) => (i ? g.lineTo(x + nx * gap, y + ny * gap) : g.moveTo(x - nx * 2, y - ny * 2)));
      g.closePath();
      g.globalAlpha = gapA;
      g.fillStyle = gapColor;
      g.fill();
      g.globalAlpha = 1;
    }
    // 被推开的那一半
    g.beginPath();
    half.forEach(([x, y], i) => (i ? g.lineTo(x + nx * gap, y + ny * gap) : g.moveTo(x + nx * gap, y + ny * gap)));
    g.closePath();
    g.clip();
    // 平移后屏幕边上会空出一条：把快照绕屏幕中心略放大，刚好盖住
    const ox = ux * shift + nx * gap, oy = uy * shift + ny * gap;
    const k = 1 + 2 * Math.max(Math.abs(ox) / W, Math.abs(oy) / H) + 0.004;
    g.translate(W / 2 + ox, H / 2 + oy);
    g.scale(k, k);
    g.drawImage(this.copyC, -W / 2, -H / 2);
    g.restore();
  }

  constructor() {
    this.canvas = mk(W, H);
    this.g = this.canvas.getContext('2d')!;
    this.pc = mk(W + 4, H + 4);
    this.pg = this.pc.getContext('2d')!;
    // 暗角
    this.vig = mk(W, H);
    const v = this.vig.getContext('2d')!;
    const gr = v.createRadialGradient(W / 2, H / 2, H * 0.35, W / 2, H / 2, H * 1.05);
    gr.addColorStop(0, 'rgba(0,0,0,0)');
    gr.addColorStop(1, 'rgba(0,0,0,1)');
    v.fillStyle = gr;
    v.fillRect(0, 0, W, H);
    // 胶片颗粒：6 张固定噪声，按帧轮换、错位
    for (let k = 0; k < 6; k++) {
      const c = mk(256, 256);
      const x = c.getContext('2d')!;
      const im = x.createImageData(256, 256);
      for (let i = 0; i < 256 * 256; i++) {
        const n = (hash(i, k, 99) + hash(i, k, 7) + hash(i, k, 3)) / 3; // 近似高斯
        const val = Math.round(clamp(0.5 + (n - 0.5) * 2.2) * 255);
        im.data[i * 4] = im.data[i * 4 + 1] = im.data[i * 4 + 2] = val;
        im.data[i * 4 + 3] = 255;
      }
      x.putImageData(im, 0, 0);
      this.grains.push(c);
    }
  }

  // ---------------- 帧 ----------------

  begin(T: number, t: number) {
    this.T = T;
    this.t = t;
    this.texts = [];
    this.post = defaultPost();
    this.zoomBoost = 1.3;
    this.shx = this.shy = 0;
    this.cam = {x: 0, y: 0, z: 1};
    this.pox = this.poy = 0;
    this.pfx = this.pfy = 0;
    this.dirty = false;
    const pg = this.pg;
    pg.setTransform(1, 0, 0, 1, 0, 0);
    pg.globalAlpha = 1;
    pg.globalCompositeOperation = 'source-over';
    pg.clearRect(0, 0, this.pc.width, this.pc.height);
    const g = this.g;
    g.setTransform(1, 0, 0, 1, 0, 0);
    g.globalAlpha = 1;
    g.globalCompositeOperation = 'source-over';
    g.filter = 'none';
    g.fillStyle = '#000';
    g.fillRect(0, 0, W, H);
  }

  /** 设相机（每帧在画任何世界内容之前调用）。shake 是屏幕像素。 */
  camera(x: number, y: number, z0: number, shake: [number, number] = [0, 0]) {
    const z = z0 * this.zoomBoost;
    this.cam = {x, y, z};
    this.shx = shake[0];
    this.shy = shake[1];
    const X0 = x - (W / 2 + this.shx) / z, Y0 = y - (H / 2 + this.shy) / z;
    this.pox = Math.floor(X0);
    this.poy = Math.floor(Y0);
    this.pfx = X0 - this.pox;
    this.pfy = Y0 - this.poy;
    this.pw = Math.min(this.pc.width, Math.ceil(W / z) + 2);
    this.ph = Math.min(this.pc.height, Math.ceil(H / z) + 2);
    this.pg.setTransform(1, 0, 0, 1, 0, 0);
    this.pg.clearRect(0, 0, this.pw, this.ph);
    this.dirty = false;
  }

  sx(X: number) {
    return (X - this.cam.x) * this.cam.z + W / 2 + this.shx;
  }
  sy(Y: number) {
    return (Y - this.cam.y) * this.cam.z + H / 2 + this.shy;
  }
  /** 世界长度 → 屏幕长度 */
  s(len: number) {
    return len * this.cam.z;
  }
  /** 像素层坐标 → 世界坐标 */
  pw2w(q: [number, number]): [number, number] {
    return [q[0] + this.pox + 0.5, q[1] + this.poy + 0.5];
  }

  // ---------------- 背景 ----------------

  /**
   * 一张背景图挂在深度 d（0 = 无穷远、不随相机动；1 = 角色所在平面）。
   * (x, y) 是图中心在该层的位置，s 是 z=1 时图上一个像素占几个屏幕像素。blur 用缩小再放大近似景深。
   */
  plate(name: PlateName, o: {x?: number; y?: number; s?: number; d?: number; a?: number; blur?: number; op?: GlobalCompositeOperation; crop?: [number, number, number, number]; flip?: boolean}) {
    const im = plateImg(name);
    this.image(im, o);
  }

  portrait(name: PortraitName, o: {x?: number; y?: number; s?: number; d?: number; a?: number; blur?: number; op?: GlobalCompositeOperation; flip?: boolean}) {
    this.image(portraitImg(name), o);
  }

  image(im: CanvasImageSource & {width: number; height: number}, o: {x?: number; y?: number; s?: number; d?: number; a?: number; blur?: number; op?: GlobalCompositeOperation; crop?: [number, number, number, number]; flip?: boolean; screen?: boolean}) {
    const d = o.d ?? 0.4;
    const zl = o.screen ? 1 : Math.pow(this.cam.z, d);
    const [cx0, cy0, cw, ch] = o.crop ?? [0, 0, im.width, im.height];
    const w = cw * (o.s ?? 1) * zl, h = ch * (o.s ?? 1) * zl;
    const cx = o.screen ? (o.x ?? W / 2) : W / 2 + ((o.x ?? 0) - this.cam.x * d) * zl + this.shx * d;
    const cy = o.screen ? (o.y ?? H / 2) : H / 2 + ((o.y ?? 0) - this.cam.y * d) * zl + this.shy * d;
    const g = this.g;
    g.save();
    g.globalAlpha = o.a ?? 1;
    if (o.op) g.globalCompositeOperation = o.op;
    g.imageSmoothingEnabled = true;
    g.imageSmoothingQuality = 'high';
    let x = cx - w / 2, y = cy - h / 2;
    if (o.flip) {
      g.translate(cx, cy);
      g.scale(-1, 1);
      g.translate(-cx, -cy);
    }
    const blur = o.blur ?? 0;
    if (blur > 0.5) {
      // 只处理落在屏幕里的部分：先缩小画到半分辨率画布，再放大回来
      const k = clamp(1 / (1 + blur * 0.6), 0.06, 0.5);
      const bw = Math.ceil(W * k), bh = Math.ceil(H * k);
      const bg = this.blurG;
      bg.setTransform(1, 0, 0, 1, 0, 0);
      bg.clearRect(0, 0, bw + 2, bh + 2);
      bg.imageSmoothingEnabled = true;
      bg.imageSmoothingQuality = 'high';
      bg.drawImage(im, cx0, cy0, cw, ch, x * k, y * k, w * k, h * k);
      g.drawImage(this.blurC, 0, 0, bw, bh, 0, 0, bw / k, bh / k);
    } else {
      g.drawImage(im, cx0, cy0, cw, ch, x, y, w, h);
    }
    g.restore();
  }

  fill(color: string, a = 1, op: GlobalCompositeOperation = 'source-over') {
    const g = this.g;
    g.save();
    g.setTransform(1, 0, 0, 1, 0, 0);
    g.globalAlpha = a;
    g.globalCompositeOperation = op;
    g.fillStyle = color;
    g.fillRect(0, 0, W, H);
    g.restore();
  }

  /** 竖直渐变（天空、地面雾）：屏幕坐标 y0..y1。 */
  vgrad(y0: number, y1: number, stops: [number, string][], a = 1, op: GlobalCompositeOperation = 'source-over') {
    const g = this.g;
    g.save();
    g.globalAlpha = a;
    g.globalCompositeOperation = op;
    const gr = g.createLinearGradient(0, y0, 0, y1);
    for (const [k, c] of stops) gr.addColorStop(k, c);
    g.fillStyle = gr;
    g.fillRect(0, Math.min(y0, y1), W, Math.abs(y1 - y0));
    g.restore();
  }

  // ---------------- 像素角色层 ----------------

  figure(rig: RigDef, pose: Pose, X: number, Y: number, o: FigOpts): FigInfo {
    this.dirty = true;
    const info = drawFigure(this.pg, rig, pose, X - this.pox, Y - this.poy, o);
    const w = (q: [number, number]) => this.pw2w(q);
    return {hand: w(info.hand), backHand: w(info.backHand), eyes: info.eyes.map(w), head: w(info.head), chest: w(info.chest)};
  }

  /** 单个像素部件（顎吐、剑之类整张贴图）。 */
  pixSprite(name: string, X: number, Y: number, o: {face?: number; deg?: number; alpha?: number; px?: number; py?: number; scale?: number} = {}) {
    this.dirty = true;
    const im = sprite(name) as HTMLImageElement;
    const g = this.pg;
    g.save();
    g.imageSmoothingEnabled = false;
    g.globalAlpha = o.alpha ?? 1;
    g.translate(Math.round(X - this.pox), Math.round(Y - this.poy));
    if (o.deg) g.rotate((o.deg * Math.PI) / 180);
    if (o.scale && o.scale !== 1) g.scale(o.scale, o.scale);
    if ((o.face ?? 1) < 0) g.scale(-1, 1);
    g.drawImage(im, -(o.px ?? im.width / 2), -(o.py ?? im.height));
    g.restore();
  }

  /** 像素层上画实心矩形（世界坐标、像素对齐）。 */
  pixRect(X: number, Y: number, w: number, h: number, color: string, a = 1) {
    this.dirty = true;
    const g = this.pg;
    g.globalAlpha = a;
    g.fillStyle = color;
    g.fillRect(Math.round(X - this.pox), Math.round(Y - this.poy), Math.max(1, Math.round(w)), Math.max(1, Math.round(h)));
    g.globalAlpha = 1;
  }

  /** 像素层直线（Bresenham 风格：一格一格点出来，保持硬边）。 */
  pixLine(X1: number, Y1: number, X2: number, Y2: number, color: string, a = 1, thick = 1) {
    this.dirty = true;
    const g = this.pg;
    g.globalAlpha = a;
    g.fillStyle = color;
    let x = Math.round(X1 - this.pox), y = Math.round(Y1 - this.poy);
    const x2 = Math.round(X2 - this.pox), y2 = Math.round(Y2 - this.poy);
    const dx = Math.abs(x2 - x), dy = -Math.abs(y2 - y), sx = x < x2 ? 1 : -1, sy = y < y2 ? 1 : -1;
    let err = dx + dy, n = 0;
    const lim = this.pw + this.ph + dx + Math.abs(dy) + 4;
    while (n++ < lim) {
      g.fillRect(x - (thick >> 1), y - (thick >> 1), thick, thick);
      if (x === x2 && y === y2) break;
      const e2 = 2 * err;
      if (e2 >= dy) {
        err += dy;
        x += sx;
      }
      if (e2 <= dx) {
        err += dx;
        y += sy;
      }
    }
    g.globalAlpha = 1;
  }

  /** 把像素层贴到主画布（最近邻放大 + 亚像素平移）。 */
  flush(a = 1, op: GlobalCompositeOperation = 'source-over') {
    if (!this.dirty) return;
    const g = this.g, z = this.cam.z;
    g.save();
    g.setTransform(1, 0, 0, 1, 0, 0);
    g.imageSmoothingEnabled = false;
    g.globalAlpha = a;
    g.globalCompositeOperation = op;
    g.drawImage(this.pc, 0, 0, this.pw, this.ph, -this.pfx * z, -this.pfy * z, this.pw * z, this.ph * z);
    g.restore();
    this.pg.setTransform(1, 0, 0, 1, 0, 0);
    this.pg.clearRect(0, 0, this.pw, this.ph);
    this.dirty = false;
  }

  // ---------------- 高清图元（世界坐标） ----------------

  glow(X: number, Y: number, r: number, color: string, a = 1, op: GlobalCompositeOperation = 'lighter', inner = 0) {
    const g = this.g;
    const x = this.sx(X), y = this.sy(Y), R = Math.max(1, this.s(r));
    if (a <= 0.003 || x + R < 0 || x - R > W || y + R < 0 || y - R > H) return;
    g.save();
    g.globalCompositeOperation = op;
    g.globalAlpha = clamp(a);
    const gr = g.createRadialGradient(x, y, R * inner, x, y, R);
    gr.addColorStop(0, color);
    gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr;
    g.fillRect(x - R, y - R, R * 2, R * 2);
    g.restore();
  }

  /** 屏幕坐标的径向光（不随相机）。 */
  glowScreen(x: number, y: number, R: number, color: string, a = 1, op: GlobalCompositeOperation = 'lighter') {
    const g = this.g;
    g.save();
    g.globalCompositeOperation = op;
    g.globalAlpha = clamp(a);
    const gr = g.createRadialGradient(x, y, 0, x, y, R);
    gr.addColorStop(0, color);
    gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr;
    g.fillRect(x - R, y - R, R * 2, R * 2);
    g.restore();
  }

  disc(X: number, Y: number, r: number, color: string, a = 1, op: GlobalCompositeOperation = 'source-over') {
    const g = this.g;
    g.save();
    g.globalCompositeOperation = op;
    g.globalAlpha = clamp(a);
    g.fillStyle = color;
    g.beginPath();
    g.arc(this.sx(X), this.sy(Y), Math.max(0.5, this.s(r)), 0, Math.PI * 2);
    g.fill();
    g.restore();
  }

  ring(X: number, Y: number, r: number, width: number, color: string, a = 1, op: GlobalCompositeOperation = 'lighter', a0 = 0, a1 = Math.PI * 2) {
    if (r <= 0 || a <= 0.003) return;
    const g = this.g;
    g.save();
    g.globalCompositeOperation = op;
    g.globalAlpha = clamp(a);
    g.strokeStyle = color;
    g.lineWidth = Math.max(0.5, this.s(width));
    g.beginPath();
    g.arc(this.sx(X), this.sy(Y), this.s(r), a0, a1);
    g.stroke();
    g.restore();
  }

  line(X1: number, Y1: number, X2: number, Y2: number, width: number, color: string, a = 1, op: GlobalCompositeOperation = 'lighter', cap: CanvasLineCap = 'round') {
    if (a <= 0.003) return;
    const g = this.g;
    g.save();
    g.globalCompositeOperation = op;
    g.globalAlpha = clamp(a);
    g.strokeStyle = color;
    g.lineCap = cap;
    g.lineWidth = Math.max(0.5, this.s(width));
    g.beginPath();
    g.moveTo(this.sx(X1), this.sy(Y1));
    g.lineTo(this.sx(X2), this.sy(Y2));
    g.stroke();
    g.restore();
  }

  /** 屏幕坐标多边形 */
  polyScreen(pts: [number, number][], color: string, a = 1, op: GlobalCompositeOperation = 'source-over') {
    const g = this.g;
    g.save();
    g.globalCompositeOperation = op;
    g.globalAlpha = clamp(a);
    g.fillStyle = color;
    g.beginPath();
    pts.forEach(([x, y], i) => (i ? g.lineTo(x, y) : g.moveTo(x, y)));
    g.closePath();
    g.fill();
    g.restore();
  }

  // ---------------- 文字 ----------------

  text(item: TextItem) {
    if (item.age >= 0 && item.age <= item.dur) this.texts.push(item);
  }

  // ---------------- 后期 ----------------

  finish() {
    const g = this.g, p = this.post;
    g.setTransform(1, 0, 0, 1, 0, 0);
    g.globalAlpha = 1;
    g.filter = 'none';
    g.globalCompositeOperation = 'source-over';
    // 色差：红通道往右、蓝通道往左错开（冲击瞬间）
    if (p.chroma > 0.3) {
      const only = (cg: CanvasRenderingContext2D, color: string) => {
        cg.setTransform(1, 0, 0, 1, 0, 0);
        cg.globalCompositeOperation = 'source-over';
        cg.drawImage(this.canvas, 0, 0);
        cg.globalCompositeOperation = 'multiply';
        cg.fillStyle = color;
        cg.fillRect(0, 0, W, H);
      };
      only(this.copyG, '#ff0000');
      only(this.copy2G, '#0000ff');
      this.fill('#00ff00', 1, 'multiply');
      g.save();
      g.globalCompositeOperation = 'lighter';
      g.drawImage(this.copyC, p.chroma, 0);
      g.drawImage(this.copy2C, -p.chroma, 0);
      g.restore();
    }
    // 辉光：缩到 1/4，提对比当阈值，模糊，加回去
    if (p.bloom > 0.01) {
      const b = this.bg2;
      b.setTransform(1, 0, 0, 1, 0, 0);
      b.globalCompositeOperation = 'source-over';
      b.clearRect(0, 0, W / 4, H / 4);
      b.filter = `brightness(0.85) contrast(${p.bloomTh}) blur(5px)`;
      b.drawImage(this.canvas, 0, 0, W / 4, H / 4);
      b.filter = 'none';
      g.save();
      g.globalCompositeOperation = 'lighter';
      g.globalAlpha = clamp(p.bloom);
      g.imageSmoothingEnabled = true;
      g.drawImage(this.bc, 0, 0, W, H);
      g.restore();
    }
    if (p.desat > 0.01) this.fill('#808080', clamp(p.desat), 'saturation');
    if (p.tint && p.tintA > 0.01) this.fill(p.tint, p.tintA, 'soft-light');
    if (p.lift > 0.01) this.fill('#1a1c24', p.lift, 'lighten');
    if (p.invert > 0.01) this.fill('#ffffff', clamp(p.invert), 'difference');
    if (p.vignette > 0.01) {
      g.save();
      g.globalAlpha = clamp(p.vignette);
      g.drawImage(this.vig, 0, 0);
      g.restore();
    }
    if (p.grain > 0.005) {
      const k = Math.floor(this.T * 60) % 6;
      if (!this.grainPats.length) this.grainPats = this.grains.map((c) => g.createPattern(c, 'repeat')!);
      const pat = this.grainPats[k];
      const ox = Math.floor(hash(Math.floor(this.T * 60), 1) * 256), oy = Math.floor(hash(Math.floor(this.T * 60), 2) * 256);
      g.save();
      g.globalCompositeOperation = 'overlay';
      g.globalAlpha = p.grain;
      g.translate(-ox, -oy);
      g.fillStyle = pat;
      g.fillRect(ox, oy, W, H);
      g.restore();
    }
    if (p.fade && p.fadeA > 0.002) this.fill(p.fade, clamp(p.fadeA));
    if (p.letterbox > 0.001) {
      const bar = ((H - W / 2.39) / 2) * clamp(p.letterbox);
      g.fillStyle = '#000';
      g.fillRect(0, 0, W, bar);
      g.fillRect(0, H - bar, W, bar);
    }
  }
}
