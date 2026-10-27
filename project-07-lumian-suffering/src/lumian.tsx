import React, {useContext} from 'react';
import {useCurrentFrame} from 'remotion';
import {PanelClock} from './manga';
import {C} from './theme';

/*
 * 卢米安（日漫Q版画法）：黑色短碎发、浅蓝色双眸、五官深刻（第一卷第1章）；
 * 衣着取第五卷第144章“白色衬衣、黑色马甲”——所以肩膀和胳膊是白袖子，马甲只盖住身前。
 * 原点是脸的中心，s=1 时脸宽约 190 像素。
 * 头发由一缕一缕的发束叠出来（后发 → 头顶 → 刘海），发梢会轻轻晃；
 * 呆毛是一根按弹簧摆动的细发，跟着表情变形，换表情或分格出场时会弹一下。
 */
export type Face = 'smirk' | 'relief' | 'pain' | 'shock' | 'dead' | 'angry' | 'calm' | 'sad';

const INK = '#15151b';
const SKIN = '#fdf4ea';
const SHADE = '#f1d7c4';
const SHADE2 = '#dcb59c';
const HAIR = '#1c1d26';
const HAIR_LINE = '#3a3e56';
const HAIR_HI = '#606889';
const IRIS_RING = '#173250';
const SHIRT = '#fbfbf7';
const VEST = '#272831';

type Pt = [number, number];
const r1 = (v: number) => Math.round(v * 10) / 10;
const P = (x: number, y: number) => `${r1(x)} ${r1(y)}`;

/* ---------- 发束 ---------- */

// 一缕头发：发根 r（宽 w）到尖尖的发梢 t；b 是整缕往法线方向鼓出的弯度。
type Lock = [Pt, Pt, number, number];
const lock = ([r, t, w, b]: Lock, dx = 0) => {
  const tx = t[0] + dx;
  const ty = t[1];
  const vx = tx - r[0];
  const vy = ty - r[1];
  const L = Math.hypot(vx, vy) || 1;
  const nx = -vy / L;
  const ny = vx / L;
  const mx = (r[0] + tx) / 2 + nx * b;
  const my = (r[1] + ty) / 2 + ny * b;
  const h = w / 2;
  const a = P(r[0] + nx * h, r[1] + ny * h);
  const z = P(r[0] - nx * h, r[1] - ny * h);
  const c1 = P(mx + nx * h * 0.6, my + ny * h * 0.6);
  const c2 = P(mx - nx * h * 0.6, my - ny * h * 0.6);
  // 发束里的一道细线：贴着一侧，从靠近发梢处往上走
  const on = (u: number, off: number) => {
    const x = (1 - u) * (1 - u) * tx + 2 * (1 - u) * u * mx + u * u * r[0];
    const y = (1 - u) * (1 - u) * ty + 2 * (1 - u) * u * my + u * u * r[1];
    return P(x + nx * off * u, y + ny * off * u);
  };
  return {
    fill: `M${a} Q ${c1} ${P(tx, ty)} Q ${c2} ${z} Z`,
    edge: `M${a} Q ${c1} ${P(tx, ty)} Q ${c2} ${z}`,
    line: `M${on(0.18, h * 0.5)} Q ${on(0.36, h * 0.6)} ${on(0.58, h * 0.55)}`,
  };
};
const mir = ([r, t, w, b]: Lock): Lock => [[-r[0], r[1]], [-t[0], t[1]], w, -b];

// 后发：脸两侧、耳后垂下来的几缕，加两侧往外翘的发梢
const BACK_L: Lock[] = [
  [[-96, -80], [-128, 30], 46, 8],
  [[-90, -30], [-112, 72], 34, 6],
  [[-102, -120], [-136, -66], 34, 10],
  [[-106, -64], [-130, -6], 28, 8],
];
const BACK: Lock[] = [...BACK_L, ...BACK_L.map(mir)];
// 头顶边缘顺着头型往下走的几缕，只露出一点，把蛋形的轮廓打碎
const EDGE: Lock[] = [
  [[-50, -178], [-112, -150], 40, 10],
  [[-20, -192], [-86, -184], 34, 8],
  [[60, -176], [120, -140], 40, -10],
  [[36, -190], [98, -178], 32, -8],
];
// 刘海：分缝在右边，整体往左扫；中间一缕垂到两眼之间
const BANGS: Lock[] = [
  [[-60, -136], [-108, -6], 50, 12],
  [[-30, -146], [-76, -40], 46, -10],
  [[0, -150], [-42, -46], 44, -12],
  [[56, -146], [26, -46], 40, -8],
  [[76, -136], [66, -40], 42, 8],
  [[92, -120], [108, -6], 44, -10],
  [[28, -150], [-8, 6], 36, -12],
];
// 耳前的两缕鬓发
const SIDE: Lock[] = [
  [[-98, -84], [-104, 56], 30, 6],
  [[98, -84], [104, 52], 30, -6],
];
const CROWN = 'M-118 -40 C -132 -130 -78 -204 4 -206 C 86 -204 134 -128 118 -40 C 70 -80 -70 -80 -118 -40 Z';
const FACE = 'M-96 -120 L -96 -40 C -98 2 -95 36 -83 60 C -64 94 -30 111 0 114 C 30 111 64 94 83 60 C 95 36 98 2 96 -40 L 96 -120 Z';

// 头发高光：沿头顶一道月牙形的亮带，内缘是锯齿（“天使环”）
const SHINE = (() => {
  const n = 16;
  const pt = (r: number, deg: number) => {
    const t = (deg * Math.PI) / 180;
    return P(Math.cos(t) * r, -70 + Math.sin(t) * r * 0.92);
  };
  const outer: string[] = [];
  const inner: string[] = [];
  for (let i = 0; i <= n; i++) {
    const k = i / n;
    const deg = 214 + k * 112;
    const ro = 100 + Math.sin(Math.PI * k) * 4;
    const depth = Math.sin(Math.PI * k) * (i % 2 ? 15 : 5);
    outer.push(pt(ro, deg));
    inner.push(pt(ro - 2 - depth, deg));
  }
  return `M${outer.join(' L ')} L ${inner.reverse().join(' L ')} Z`;
})();

/* ---------- 呆毛 ---------- */

// 角度以“竖直向上”为 0，顺时针为正。curl 是从根到梢一共转过的角度。
type Ah = {base: number; curl: number; zig?: number; sharp?: boolean; wob: number; len: number; buzz?: number};
const AHOGE: Record<Face, Ah> = {
  calm: {base: -18, curl: 118, wob: 8, len: 118},
  smirk: {base: -10, curl: 150, wob: 7, len: 116},
  relief: {base: -24, curl: 340, wob: 5, len: 124},
  shock: {base: 0, curl: 0, wob: 2, len: 118, buzz: 7},
  pain: {base: 4, curl: 0, zig: 36, wob: 3, len: 108},
  angry: {base: 0, curl: 0, zig: 56, sharp: true, wob: 2, len: 112, buzz: 3},
  dead: {base: 70, curl: 130, wob: 3, len: 144},
  sad: {base: 44, curl: 125, wob: 4, len: 120},
};
const tri = (x: number) => 2 * Math.abs(2 * (x - Math.floor(x + 0.5))) - 1;
const smooth = (pts: Pt[]) => {
  let d = `M${P(...pts[0])}`;
  for (let i = 1; i < pts.length - 1; i++) {
    const [x, y] = pts[i];
    const [x2, y2] = pts[i + 1];
    d += ` Q ${P(x, y)} ${P((x + x2) / 2, (y + y2) / 2)}`;
  }
  return `${d} L ${P(...pts[pts.length - 1])}`;
};
const ahoge = (p: Ah, f: number, shake: number, boing: number, root: Pt) => {
  const N = 28;
  const seg = p.len / N;
  const pts: Pt[] = [root];
  const dirs: number[] = [];
  let [x, y] = root;
  for (let i = 0; i < N; i++) {
    const u = (i + 0.5) / N;
    let a = p.base + p.curl * u;
    if (p.zig) a += p.sharp ? p.zig * Math.sign(Math.sin(u * Math.PI * 5 + 0.4)) : p.zig * tri(u * 3);
    a += p.wob * Math.sin(f * 0.12 - u * 2.4) * (0.3 + u); // 自己晃
    a += 5 * Math.cos(f * 0.08 - u * 1.6) * u; // 跟着头的上下浮动，梢比根慢半拍
    a += shake * 3 * Math.sin(f * 1.9 - u * 3) * u; // 头在抖
    a += (p.buzz ?? 0) * Math.sin(f * 2.6) * u; // 吓得发颤
    a += boing * u;
    const t = (a * Math.PI) / 180;
    x += Math.sin(t) * seg;
    y -= Math.cos(t) * seg;
    pts.push([x, y]);
    dirs.push(t);
  }
  const left: Pt[] = [];
  const right: Pt[] = [];
  pts.forEach(([px, py], i) => {
    const t = dirs[Math.min(i, N - 1)];
    const hw = (12 * Math.pow(1 - i / N, 0.75) + 1.2) / 2;
    left.push([px - Math.cos(t) * hw, py - Math.sin(t) * hw]);
    right.push([px + Math.cos(t) * hw, py + Math.sin(t) * hw]);
  });
  return `${smooth(left)} L ${smooth(right.reverse()).slice(1)} Z`;
};

/* ---------- 五官 ---------- */

// 眼睛在局部坐标里画（原点为眼睛中心，+x 朝耳朵一侧），左眼用镜像。
// c1/c2 是上眼睑两个控制点的高度（c1 靠内眼角，c2 靠外眼角）；low 是下眼睑的深度。
type EyeP = {c1: number; c2: number; low: number; iris: number; hl: boolean};
const EYE: Partial<Record<Face, EyeP>> = {
  calm: {c1: -26, c2: -34, low: 32, iris: 1, hl: true},
  smirk: {c1: -4, c2: -10, low: 24, iris: 1, hl: true},
  shock: {c1: -38, c2: -44, low: 38, iris: 0.5, hl: false},
  dead: {c1: -20, c2: -26, low: 32, iris: 1, hl: false},
  angry: {c1: -6, c2: -32, low: 28, iris: 0.92, hl: true},
  sad: {c1: -34, c2: -16, low: 32, iris: 1, hl: true},
};

const Eye: React.FC<{side: -1 | 1; face: Face; blink: boolean; id: string; lashes?: boolean}> = ({side, face, blink, id, lashes}) => {
  const tf = `translate(${46 * side} 12) scale(${side} 1)`;
  if (face === 'relief') {
    return (
      <g transform={tf} stroke={INK} strokeWidth={5.5} fill="none" strokeLinecap="round" strokeLinejoin="round">
        <path d="M-26 8 Q 2 -18 30 4 L 38 -1" />
      </g>
    );
  }
  if (face === 'pain') {
    return (
      <g transform={tf}>
        <path d="M26 -14 L -14 4 L 26 22" stroke={INK} strokeWidth={6.5} fill="none" strokeLinecap="round" strokeLinejoin="round" />
      </g>
    );
  }
  const e = EYE[face] ?? EYE.calm!;
  if (blink && face !== 'shock' && face !== 'dead') {
    return (
      <g transform={tf} stroke={INK} strokeWidth={5} fill="none" strokeLinecap="round" strokeLinejoin="round">
        <path d="M-26 4 Q 2 18 30 0 L 38 -4" />
      </g>
    );
  }
  const {c1, c2, low} = e;
  const opening = `M-28 2 C -18 ${c1} 14 ${c2} 30 -4 C 26 ${low} -14 ${low + 2} -28 2 Z`;
  const ir = e.iris;
  const dull = face === 'dead';
  const key = `${id}-${side}`;
  return (
    <g transform={tf}>
      <defs>
        <clipPath id={`${key}-eye`}>
          <path d={opening} />
        </clipPath>
        <linearGradient id={`${key}-iris`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={dull ? '#4a525c' : '#1d4877'} />
          <stop offset="0.5" stopColor={dull ? '#6f7882' : '#3d86c6'} />
          <stop offset="1" stopColor={dull ? '#a3acb5' : '#aee4ff'} />
        </linearGradient>
      </defs>
      <path d={opening} fill="#fff" />
      <g clipPath={`url(#${key}-eye)`}>
        <ellipse cx={2} cy={5} rx={19 * ir} ry={25 * ir} fill={`url(#${key}-iris)`} stroke={IRIS_RING} strokeWidth={2.5} />
        <ellipse cx={2} cy={7} rx={8 * ir} ry={11 * ir} fill="#0e1b2d" />
        {!dull && <ellipse cx={2} cy={5 + 16 * ir} rx={11 * ir} ry={5 * ir} fill="#d8f3ff" opacity={0.6} />}
        {/* 上眼睑投在眼白和虹膜上的阴影 */}
        <path d={`M-30 2 C -18 ${c1} 14 ${c2} 32 -4 L 32 6 C 14 ${c2 + 12} -18 ${c1 + 12} -30 12 Z`} fill={IRIS_RING} opacity={0.26} />
        {e.hl && (
          <>
            <ellipse cx={-8 * side} cy={-6} rx={6.5} ry={7.5} fill="#fff" />
            <circle cx={9 * side} cy={17} r={3} fill="#fff" />
          </>
        )}
      </g>
      {/* 双眼皮的细线 */}
      <path d={`M-8 ${0.75 * c1 + 0.25 * c2 - 9} Q 12 ${c2 - 9} 28 ${c2 * 0.55 - 7}`} stroke={INK} strokeWidth={2} fill="none" strokeLinecap="round" opacity={0.7} />
      {/* 上睫毛线：内细外粗，外眼角上挑 */}
      <path d={`M-30 4 C -19 ${c1 - 5} 14 ${c2 - 10} 36 -9 L 45 -13 L 32 0 C 14 ${c2} -18 ${c1} -28 4 Z`} fill={INK} />
      {/* 下睫毛：只画外侧半截 */}
      <path d={`M30 -4 C 28 ${(low - 4) / 2} 17 ${(3 * low - 2) / 4} 5 ${(6 * low + 4) / 8}`} stroke={INK} strokeWidth={2.6} fill="none" strokeLinecap="round" />
      {lashes && <path d="M40 -10 L 52 -20 M 30 -16 L 40 -28" stroke={INK} strokeWidth={3} strokeLinecap="round" />}
      {face === 'sad' && <path d={`M10 ${low - 4} Q 14 ${low + 14} 8 ${low + 34}`} stroke="#8fd3ff" strokeWidth={5.5} fill="none" strokeLinecap="round" />}
    </g>
  );
};

// 眉毛内端、外端的上下偏移（局部坐标，右眉 side=1）
const browOff = (face: Face, side: -1 | 1): [number, number] => {
  switch (face) {
    case 'smirk':
      return side === 1 ? [-8, -14] : [4, 2];
    case 'relief':
      return [-2, 4];
    case 'pain':
      return [-8, 2];
    case 'shock':
      return [-12, -10];
    case 'dead':
      return [2, 2];
    case 'angry':
      return [12, -8];
    case 'sad':
      return [-12, 6];
    default:
      return [0, -2];
  }
};

const Brow: React.FC<{side: -1 | 1; face: Face}> = ({side, face}) => {
  const [inn, out] = browOff(face, side);
  const mid = (inn + out) / 2;
  return <path transform={`translate(${46 * side} -30) scale(${side} 1)`} d={`M-24 ${inn} Q 4 ${mid - 9} 32 ${out} Q 4 ${mid - 2} -24 ${inn + 7} Z`} fill={INK} />;
};

const Mouth: React.FC<{face: Face}> = ({face}) => {
  const y = 74;
  const line = {stroke: INK, strokeWidth: 3.5, fill: 'none', strokeLinecap: 'round' as const};
  switch (face) {
    case 'smirk':
      return <path d={`M-12 ${y} Q 4 ${y + 5} 20 ${y - 8} L 23 ${y - 11}`} {...line} />;
    case 'relief':
      return <path d={`M-14 ${y - 2} Q 0 ${y + 14} 14 ${y - 2} Z`} fill="#7a2f33" stroke={INK} strokeWidth={3} strokeLinejoin="round" />;
    case 'pain':
      return (
        <g>
          <path d={`M-20 ${y - 7} Q 0 ${y - 11} 20 ${y - 7} L 18 ${y + 7} Q 0 ${y + 11} -18 ${y + 7} Z`} fill="#fff" stroke={INK} strokeWidth={3.5} strokeLinejoin="round" />
          <line x1={-19} y1={y} x2={19} y2={y} stroke={INK} strokeWidth={2} />
        </g>
      );
    case 'shock':
      return <ellipse cx={0} cy={y + 2} rx={9} ry={13} fill="#6a2a2e" stroke={INK} strokeWidth={3} />;
    case 'dead':
      return <path d={`M-10 ${y} Q -5 ${y - 3} 0 ${y} Q 5 ${y + 3} 10 ${y}`} {...line} />;
    case 'angry':
      return (
        <g>
          <path d={`M-18 ${y - 6} L 18 ${y - 6} Q 14 ${y + 14} 0 ${y + 14} Q -14 ${y + 14} -18 ${y - 6} Z`} fill="#6a2a2e" stroke={INK} strokeWidth={3} strokeLinejoin="round" />
          <path d={`M-16 ${y - 5} L 16 ${y - 5} L 15 ${y + 1} L -15 ${y + 1} Z`} fill="#fff" />
        </g>
      );
    case 'sad':
      return <path d={`M-12 ${y + 4} Q 0 ${y - 4} 12 ${y + 4}`} {...line} />;
    default:
      return <path d={`M-9 ${y} Q 0 ${y + 3} 9 ${y}`} {...line} />;
  }
};

/* ---------- 身体 ---------- */

const Body: React.FC = () => (
  <g strokeLinejoin="round">
    {/* 白衬衫：肩膀和两条胳膊 */}
    <path d="M-18 136 C -44 144 -72 148 -88 162 C -102 176 -106 230 -108 340 L 108 340 C 106 230 102 176 88 162 C 72 148 44 144 18 136 Z" fill={SHIRT} stroke={INK} strokeWidth={3.5} />
    <path d="M-80 204 C -82 250 -84 300 -84 340 M 80 204 C 82 250 84 300 84 340" stroke={INK} strokeWidth={3} fill="none" />
    <path d="M-99 236 Q -91 244 -94 258 M 99 236 Q 91 244 94 258" stroke="#b9b9c4" strokeWidth={2.5} fill="none" strokeLinecap="round" />
    {/* 黑马甲：V 领，三颗扣子，两道口袋 */}
    <path d="M-22 144 C -38 150 -52 153 -62 158 C -70 192 -74 250 -78 340 L 78 340 C 74 250 70 192 62 158 C 52 153 38 150 22 144 L 0 228 Z" fill={VEST} stroke={INK} strokeWidth={3.5} />
    <path d="M-19 152 L -2 224 M 19 152 L 2 224" stroke="#41424f" strokeWidth={3} />
    {[254, 284, 314].map((by) => (
      <circle key={by} cx={0} cy={by} r={4} fill="#8e8f9c" stroke={INK} strokeWidth={1.5} />
    ))}
    <path d="M-58 272 L -36 270 M 36 270 L 58 272" stroke="#4a4b58" strokeWidth={3} strokeLinecap="round" />
    {/* 敞开的领口与衬衫领 */}
    <path d="M-12 138 L 0 170 L 12 138 Z" fill={SKIN} />
    <path d="M0 170 L 0 226" stroke="#d2d2da" strokeWidth={2} />
    <path d="M-20 128 C -28 140 -40 150 -48 160 L -34 178 L -3 160 Z" fill={SHIRT} stroke={INK} strokeWidth={3} />
    <path d="M20 128 C 28 140 40 150 48 160 L 34 178 L 3 160 Z" fill={SHIRT} stroke={INK} strokeWidth={3} />
  </g>
);

/* ---------- 卢米安 ---------- */

export const Lumian: React.FC<{
  x: number;
  y: number;
  s?: number;
  face?: Face;
  since?: number; // 换表情的帧号：呆毛在这一刻弹一下
  hood?: boolean;
  bandage?: boolean;
  sweat?: boolean;
  gloom?: boolean; // 额头上的黑线（绝望）
  tilt?: number;
  shake?: number;
  body?: boolean;
  longHair?: boolean;
}> = ({x, y, s = 1, face = 'calm', since = -999, hood, bandage, sweat, gloom, tilt = 0, shake = 0, body = true, longHair}) => {
  const f = useCurrentFrame();
  const enter = useContext(PanelClock);
  const blink = f % 110 < 4;
  const jx = shake ? Math.sin(f * 2.1) * shake : 0;
  const bob = Math.sin(f * 0.08) * 3;
  const id = `lm${Math.round(x)}-${Math.round(y)}-${Math.round(s * 100)}`;
  // 呆毛的“弹一下”：分格出场或换表情之后，衰减振荡一秒多
  const t0 = Math.max(enter + 4, since);
  const bt = f - t0;
  const boing = bt >= 0 && bt < 50 ? 42 * Math.exp(-bt / 9) * Math.sin(bt * 0.8) : 0;
  const sway = (i: number, amp: number) => amp * Math.sin(f * 0.09 + i * 1.7);
  const hairLocks = (list: Lock[], amp: number, key: string, lines = false) =>
    list.map((l, i) => {
      const p = lock(l, sway(i, amp));
      return (
        <g key={`${key}${i}`}>
          <path d={p.fill} fill={HAIR} />
          <path d={p.edge} fill="none" stroke={INK} strokeWidth={2.6} strokeLinejoin="round" />
          {lines && <path d={p.line} fill="none" stroke={HAIR_LINE} strokeWidth={1.8} strokeLinecap="round" />}
        </g>
      );
    });
  const ah = ahoge(AHOGE[face], f, shake, boing, hood ? [8, -230] : [8, -194]);
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
      <defs>
        <clipPath id={`${id}-face`}>
          <path d={FACE} />
        </clipPath>
        <linearGradient id={`${id}-gloom`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#2c2f3a" stopOpacity={0.85} />
          <stop offset="1" stopColor="#2c2f3a" stopOpacity={0} />
        </linearGradient>
      </defs>
      <g transform={`translate(${x + jx} ${y + bob}) scale(${s}) rotate(${tilt})`}>
        {hood && (
          <g>
            {/* 兜帽背面与帽内的暗处 */}
            <path d="M-122 204 C -178 176 -188 -40 -150 -150 C -120 -230 -50 -258 4 -258 C 58 -258 128 -230 158 -150 C 196 -40 186 176 130 204 Z" fill="#33323d" stroke={INK} strokeWidth={4} strokeLinejoin="round" />
            <path d="M-140 190 C -150 -30 -110 -214 4 -218 C 116 -214 158 -30 148 190 Z" fill="#111116" />
          </g>
        )}
        {longHair && <path d="M-118 -70 C -140 40 -146 190 -140 330 L 140 330 C 146 190 140 40 118 -70 Z" fill={HAIR} stroke={INK} strokeWidth={3} />}
        {body && <Body />}
        {longHair && hairLocks([[[-98, -60], [-118, 270], 38, 8], [[98, -60], [118, 270], 38, -8]], 2.5, 'lf')}
        {/* 脖子 */}
        <path d="M-18 92 L -18 152 L 18 152 L 18 92 Z" fill={SKIN} />
        <path d="M-18 92 L -18 140 M 18 92 L 18 140" stroke={INK} strokeWidth={3} />
        <path d="M-18 96 Q 0 128 18 96 L 18 118 Q 0 140 -18 118 Z" fill={SHADE} />
        {/* 后发 */}
        {hairLocks(hood ? BACK.filter((_, i) => i % 4 < 2) : BACK, 1.6, 'b')}
        {/* 耳朵 */}
        {[-1, 1].map((sd) => (
          <g key={sd} transform={`scale(${sd} 1)`}>
            <path d="M90 -2 C 114 -12 120 28 92 38" fill={SKIN} stroke={INK} strokeWidth={3.2} />
            <path d="M96 8 C 106 8 106 22 96 26" stroke={SHADE2} strokeWidth={2.5} fill="none" />
          </g>
        ))}
        {/* 脸 */}
        <path d={FACE} fill={SKIN} stroke={INK} strokeWidth={3.2} strokeLinejoin="round" />
        <path d="M-62 88 C -38 106 -16 113 0 115 C 16 113 38 106 62 88" stroke={INK} strokeWidth={5} fill="none" strokeLinecap="round" />
        <g clipPath={`url(#${id}-face)`}>
          {/* 刘海和鬓发投在脸上的影子 */}
          <g transform="translate(4 10)" fill={SHADE}>
            {[...BANGS, ...SIDE].map((l, i) => (
              <path key={i} d={lock(l).fill} />
            ))}
            <path d={CROWN} />
          </g>
          {face !== 'dead' && [-1, 1].map((sd) => <ellipse key={sd} cx={sd * 60} cy={48} rx={18} ry={8} fill="#f3a0a0" opacity={0.32} />)}
          {gloom && (
            <g>
              <rect x={-100} y={-120} width={200} height={140} fill={`url(#${id}-gloom)`} />
              {Array.from({length: 8}, (_, i) => (
                <line key={i} x1={-70 + i * 20} y1={-36} x2={-70 + i * 20} y2={-12 + (i % 2) * 8} stroke={INK} strokeWidth={3} />
              ))}
            </g>
          )}
        </g>
        <Brow side={-1} face={face} />
        <Brow side={1} face={face} />
        <Eye side={-1} face={face} blink={blink} id={id} lashes={longHair} />
        <Eye side={1} face={face} blink={blink} id={id} lashes={longHair} />
        <path d="M4 44 Q 7 50 1 54" stroke={SHADE2} strokeWidth={2.8} fill="none" strokeLinecap="round" />
        <Mouth face={face} />
        {(face === 'pain' || face === 'shock' || face === 'relief') && (
          <g stroke="#e07f7f" strokeWidth={3} strokeLinecap="round">
            {[-1, 1].map((sd) => (
              <g key={sd}>
                {[0, 1, 2].map((k) => (
                  <line key={k} x1={sd * (48 + k * 9)} y1={54} x2={sd * (54 + k * 9)} y2={44} />
                ))}
              </g>
            ))}
          </g>
        )}
        {/* 头顶：呆毛在发团后面长出来 */}
        {!hood && <path d={ah} fill={HAIR} stroke={INK} strokeWidth={2.4} strokeLinejoin="round" />}
        {!hood && hairLocks(EDGE, 1, 'e')}
        <path d={CROWN} fill={HAIR} stroke={INK} strokeWidth={3.5} />
        {/* 高光画在刘海下面，被一缕缕刘海切开 */}
        <path d={SHINE} fill={HAIR_HI} />
        {/* 刘海与鬓发 */}
        {hairLocks(SIDE, 2.2, 's')}
        {hairLocks(BANGS, 1.4, 'f', true)}
        {hood && (
          <g>
            {/* 帽檐：压住头顶和两侧的头发 */}
            <path d="M-152 170 C -170 -40 -120 -234 4 -238 C 126 -234 172 -40 152 170 L 128 170 C 138 -20 112 -112 4 -128 C -106 -112 -134 -20 -126 170 Z" fill="#3c3b47" stroke={INK} strokeWidth={3.5} strokeLinejoin="round" />
            <path d="M-140 60 Q -150 -60 -110 -170 M 140 60 Q 150 -60 110 -170" stroke="#2a2a33" strokeWidth={3.5} fill="none" strokeLinecap="round" />
            {/* 呆毛照样从兜帽里钻出来 */}
            <path d={ah} fill={HAIR} stroke={INK} strokeWidth={2.4} strokeLinejoin="round" />
          </g>
        )}
        {face === 'angry' && (
          <g transform="translate(122 -150)" fill="none" strokeLinecap="round">
            {['M-14 -4 Q -6 -6 -4 -14', 'M4 -14 Q 6 -6 14 -4', 'M14 4 Q 6 6 4 14', 'M-4 14 Q -6 6 -14 4'].map((d) => (
              <g key={d}>
                <path d={d} stroke="#fff" strokeWidth={10} />
                <path d={d} stroke={C.red} strokeWidth={5} />
              </g>
            ))}
          </g>
        )}
        {bandage && (
          <g transform="rotate(-18 60 44)">
            <rect x={40} y={36} width={42} height={15} rx={6} fill="#fff1d8" stroke={INK} strokeWidth={2.5} />
            <line x1={55} y1={36} x2={55} y2={51} stroke={INK} strokeWidth={1.5} />
            <line x1={67} y1={36} x2={67} y2={51} stroke={INK} strokeWidth={1.5} />
          </g>
        )}
        {sweat && <path d="M124 -44 C 114 -24 116 -8 126 -4 C 138 -8 138 -24 124 -44 Z" fill="#dff3ff" stroke={INK} strokeWidth={2.5} />}
      </g>
    </svg>
  );
};

/* 奥萝尔的剪影：只画长发的轮廓，不画五官。 */
export const SisterSilhouette: React.FC<{x: number; y: number; s?: number; opacity?: number; color?: string}> = ({x, y, s = 1, opacity = 1, color = C.ink}) => (
  <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible', opacity}}>
    <g transform={`translate(${x} ${y}) scale(${s})`} fill={color}>
      <path d="M-96 -20 C -110 -110 -60 -150 0 -150 C 60 -150 110 -110 96 -20 C 104 60 120 150 130 230 L -130 230 C -120 150 -104 60 -96 -20 Z" />
      <path d="M-150 420 C -140 300 -90 250 -40 240 L 40 240 C 90 250 140 300 150 420 Z" />
    </g>
  </svg>
);
