// 游戏里的像素纸娃娃（PixelScene.figure / part 的移植）：头、身体、两臂、两腿，按姿势角度挂到骨骼上。
// 坐标：脚底中心为原点，向上为负；角度单位是度，正值让手臂往前抬、腿往前迈。
import {SPRITE_META, sprite, spriteDark} from './assets';

export type Pose = {lean: number; armF: number; armB: number; legF: number; legB: number; bob: number; spin: number};
export const P = (o: Partial<Pose> = {}): Pose => ({lean: 0, armF: 0, armB: 0, legF: 0, legB: 0, bob: 0, spin: 0, ...o});
export const mixPose = (a: Pose, b: Pose, k: number): Pose => ({
  lean: a.lean + (b.lean - a.lean) * k,
  armF: a.armF + (b.armF - a.armF) * k,
  armB: a.armB + (b.armB - a.armB) * k,
  legF: a.legF + (b.legF - a.legF) * k,
  legB: a.legB + (b.legB - a.legB) * k,
  bob: a.bob + (b.bob - a.bob) * k,
  spin: a.spin + (b.spin - a.spin) * k,
});

export type RigDef = {
  head: string; torso: string; armF: string; armB: string; leg: string; arm2: string | null;
  hipX: number; hipY: number; torsoY: number; shoulderX: number; shoulderY: number; neckY: number; arm2Y: number; handReach: number;
};
const human = (who: string): RigDef => ({
  head: who + '.head', torso: who + '.torso', armF: who + '.arm', armB: who + '.arm', leg: who + '.leg', arm2: null,
  hipX: 2, hipY: -13, torsoY: -12, shoulderX: 6, shoulderY: -24, neckY: -26, arm2Y: 0, handReach: 9.5,
});
export const RIG = {
  gojo: human('gojo'),
  sukuna: human('sukuna'),
  trueForm: {...human('sukuna_true'), leg: 'sukuna.leg', arm2: 'sukuna_true.arm2', arm2Y: -18} as RigDef,
  maho: {
    head: 'mahoraga.head', torso: 'mahoraga.torso', armF: 'mahoraga.sword', armB: 'mahoraga.arm', leg: 'mahoraga.leg', arm2: null,
    hipX: 3, hipY: -20, torsoY: -18, shoulderX: 10, shoulderY: -37, neckY: -39, arm2Y: 0, handReach: 14,
  } as RigDef,
};

export type FigOpts = {
  face: number;          // 1 朝右，-1 朝左
  hurt?: number;         // 受击闪白 0..1
  sil?: string;          // 冲击帧：整个人涂成这个颜色
  silA?: number;
  tint?: string;         // 残影、受光：叠一层颜色
  tintA?: number;
  alpha?: number;
  noArmF?: boolean;      // 不画前臂（被茈打掉、还没长回来）
  noArmB?: boolean;
};

/** 画完以后可供高清特效使用的点（像素层坐标）。 */
export type FigInfo = {hand: [number, number]; backHand: [number, number]; eyes: [number, number][]; head: [number, number]; chest: [number, number]};

const TMP = 256, OX = 128, OY = 200; // 临时画布：人物脚底放在 (OX, OY)
let tmp: HTMLCanvasElement | null = null;
let tg: CanvasRenderingContext2D | null = null;

export function drawFigure(g: CanvasRenderingContext2D, r: RigDef, p: Pose, fx: number, fy: number, o: FigOpts): FigInfo {
  const plain = !o.hurt && !o.sil && !o.tint && (o.alpha ?? 1) >= 1;
  let c = g, bx = 0, by = 0;
  if (!plain) {
    if (!tmp) {
      tmp = document.createElement('canvas');
      tmp.width = TMP;
      tmp.height = TMP;
      tg = tmp.getContext('2d')!;
    }
    c = tg!;
    c.setTransform(1, 0, 0, 1, 0, 0);
    c.clearRect(0, 0, TMP, TMP);
    bx = OX - Math.round(fx);
    by = OY - Math.round(fy);
  }
  c.imageSmoothingEnabled = false;
  const face = o.face;
  const figX = Math.round(fx) + bx, figY = Math.round(fy - p.bob) + by;
  const sp = (p.spin * Math.PI) / 180, cs = Math.cos(sp), sn = Math.sin(sp);
  const pivot = -r.neckY;
  const toW = (lx: number, ly: number): [number, number] => {
    const dx = lx, dy = ly + pivot;
    const rx = dx * cs - dy * sn, ry = dx * sn + dy * cs - pivot;
    return [figX + face * rx, figY + ry];
  };
  const part = (name: string, jx: number, jy: number, deg: number, back: boolean) => {
    const m = SPRITE_META[name];
    let [x, y] = toW(jx, jy);
    const wdeg = face * (p.spin + deg);
    if (wdeg === 0) {
      x = Math.round(x);
      y = Math.round(y);
    }
    c.save();
    c.translate(x, y);
    if (wdeg !== 0) c.rotate((wdeg * Math.PI) / 180);
    if (face < 0) c.scale(-1, 1);
    c.drawImage(back ? spriteDark(name) : sprite(name), -m.px, -m.py);
    c.restore();
  };
  const lean = p.lean;
  const dTorso = Math.round(lean * 0.12), dHead = Math.round(lean * 0.2), dShoulder = Math.round(lean * 0.16);
  const sx = r.shoulderX;
  if (r.arm2) part(r.arm2, -sx + dTorso, r.arm2Y, -p.armB * 0.6 - 20, true);
  if (!o.noArmB) part(r.armB, -sx + dShoulder, r.shoulderY, -p.armB, true);
  part(r.leg, -r.hipX, r.hipY, -p.legB, true);
  part(r.leg, r.hipX, r.hipY, -p.legF, false);
  part(r.torso, dTorso, r.torsoY, 0, false);
  const headY = r.neckY + Math.round(Math.abs(lean) * 0.04);
  part(r.head, dHead, headY, 0, false);
  if (!o.noArmF) part(r.armF, sx + dShoulder, r.shoulderY, -p.armF, false);
  if (r.arm2) part(r.arm2, sx + dTorso, r.arm2Y, -p.armF * 0.6 + 20, false);

  if (!plain) {
    c.globalCompositeOperation = 'source-atop';
    if (o.sil) {
      c.globalAlpha = o.silA ?? 1;
      c.fillStyle = o.sil;
      c.fillRect(0, 0, TMP, TMP);
    }
    if (o.tint) {
      c.globalAlpha = o.tintA ?? 0.5;
      c.fillStyle = o.tint;
      c.fillRect(0, 0, TMP, TMP);
    }
    if (o.hurt) {
      c.globalAlpha = Math.min(1, o.hurt) * 0.85;
      c.fillStyle = '#ffffff';
      c.fillRect(0, 0, TMP, TMP);
    }
    c.globalAlpha = 1;
    c.globalCompositeOperation = 'source-over';
    g.save();
    g.globalAlpha = o.alpha ?? 1;
    g.imageSmoothingEnabled = false;
    g.drawImage(tmp!, -bx, -by);
    g.restore();
  }

  // 给特效用的点，换回像素层坐标
  const un = (q: [number, number]): [number, number] => [q[0] - bx, q[1] - by];
  const hand = (shX: number, deg: number) => {
    const a = (deg * Math.PI) / 180;
    return un(toW(shX - r.handReach * Math.sin(a), r.shoulderY + r.handReach * Math.cos(a)));
  };
  const hm = SPRITE_META[r.head];
  const eyes = (hm.eyes ?? []).map((e) => un(toW(dHead + e[0], headY + e[1])));
  return {
    hand: hand(sx + dShoulder, -p.armF),
    backHand: hand(-sx + dShoulder, -p.armB),
    eyes,
    head: un(toW(dHead, headY - 12)),
    chest: un(toW(dTorso, r.torsoY - 7)),
  };
}

// ---------------- 姿势库（数值取自游戏里的 poseGojo / poseSukuna） ----------------

export const POSE = {
  idle: P({armF: 8, armB: -8}),
  stand: P({armF: 4, armB: -4, legF: 6, legB: -6}),
  air: P({legF: 35, legB: -15, armF: 50, armB: -40}),
  fall: P({legF: 20, legB: -30, armF: 120, armB: 100, lean: -10}),
  hurt: P({lean: -16, armF: 60, armB: -50, legF: 20}),
  hurtBig: P({lean: -24, armF: 100, armB: 80, legF: 30, legB: -10}),
  jabWind: P({armF: -35, armB: -25, lean: -6, legF: 20, legB: -18}),
  jab: P({armF: 95, armB: -30, lean: 18, legF: 22, legB: -20}),
  kickWind: P({legF: 25, legB: -12, armF: 45, armB: -45, lean: -4}),
  kick: P({legF: 95, legB: -12, armF: 45, armB: -45, lean: -16}),
  upper: P({armF: 160, armB: -30, lean: 6, legF: 16, legB: -16, bob: 3}),
  punchWind: P({armF: -65, armB: 30, lean: -12, legF: 26, legB: -24}),
  dashPunch: P({armF: 95, armB: -45, lean: 22, legF: 32, legB: -30}),
  smashWind: P({armF: 175, armB: -30, lean: -10, legF: 20, legB: -18}),
  smash: P({armF: 50, armB: -40, lean: 24, legF: 28, legB: -26}),
  dodgeF: P({lean: 24, armF: -30, armB: 60, legF: 30, legB: -30, bob: -2}),
  dodgeB: P({lean: -18, armF: 40, armB: -60, legF: 30, legB: -30, bob: -2}),
  sign: P({armF: 125, armB: 115, lean: -3}),          // 两手合在胸前结印
  signHigh: P({armF: 150, armB: -10}),                  // 单手举起结印
  blueHold: P({armF: 85, armB: -20, lean: 4}),
  redCharge: P({armF: 90, armB: -50, lean: -6, legF: 30, legB: -28}),
  redFire: P({armF: 95, armB: -50, lean: -14, legF: 30, legB: -28}),
  skyArm: P({armB: 150, armF: 20, lean: -6, legF: 26, legB: -14}),
  skyFling: P({armF: 150, armB: 60, lean: -10, legF: 26, legB: -14}),
  spread: P({armF: 100, armB: -100, lean: -8, legF: 26, legB: -24}),
  guard: P({armF: 75, armB: 60, lean: -4, legF: 18, legB: -14}),
  slashWind: P({armF: 150, armB: -20, lean: -6, legF: 20, legB: -16}),
  slash: P({armF: 30, armB: -40, lean: 14, legF: 24, legB: -20}),
  point: P({armF: 90, armB: -10, lean: 2, legF: 10, legB: -8}),
  crouch: P({armF: 40, armB: -30, lean: 20, legF: 40, legB: -40, bob: -4}),
  ko: P({spin: -90, bob: -18, armF: 60, armB: -50, legF: 12, legB: -8}),
  sit: P({legF: 70, legB: 70, armF: 10, armB: -6, lean: 5, bob: -6}),
};

/** 跑步循环：phase 按“走过的像素”算，跟游戏一样 sin(x·0.05)。 */
export const runPose = (dist: number): Pose => {
  const s = Math.sin(dist * 0.05);
  return P({legF: s * 34, legB: -s * 34, armF: -s * 30, armB: s * 30, lean: 10, bob: Math.abs(s) > 0.7 ? 1 : 0});
};

/** 站着呼吸：每 ~1.2 秒上下 1 像素。 */
export const breathe = (t: number, base: Pose = POSE.idle): Pose => ({...base, bob: base.bob + (Math.sin(t * 2.6) > 0.3 ? 1 : 0)});

/** 姿势关键帧：[[时间, 姿势, 缓动?], ...] */
export type PoseKey = [number, Pose, ((k: number) => number)?];
export const poseAt = (t: number, keys: PoseKey[]): Pose => {
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    const [t1, p1, e] = keys[i];
    if (t <= t1) {
      const [t0, p0] = keys[i - 1];
      const k = t1 > t0 ? (t - t0) / (t1 - t0) : 1;
      return mixPose(p0, p1, e ? e(k) : k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2);
    }
  }
  return keys[keys.length - 1][1];
};
