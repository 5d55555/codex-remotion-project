import React from 'react';
import {useCurrentFrame} from 'remotion';
import {C, rnd, SCREEN} from './theme';

/*
 * 皮影人偶。坐标以颈部关节为原点、面朝右；s=1 时从冠顶到靴底约 420 像素。
 * 每个部件是一块刻花的“驴皮”：外轮廓 + 镂空花纹（evenodd 挖洞，幕布的光从洞里透出来）。
 * 整个人偶用正片叠底叠在幕布上，看起来像半透明的彩色皮子。
 */

/* ---------- 镂空花纹 ---------- */
const n = (v: number) => Math.round(v * 10) / 10;
export const hole = {
  dot: (x: number, y: number, r: number) => `M${n(x - r)} ${n(y)} a ${r} ${r} 0 1 0 ${n(2 * r)} 0 a ${r} ${r} 0 1 0 ${n(-2 * r)} 0 Z`,
  // 祥云：三瓣云头
  cloud: (x: number, y: number, s: number) =>
    `M${n(x - s)} ${n(y + 0.3 * s)} C ${n(x - 1.35 * s)} ${n(y + 0.3 * s)} ${n(x - 1.3 * s)} ${n(y - 0.35 * s)} ${n(x - 0.85 * s)} ${n(y - 0.35 * s)} C ${n(x - 0.8 * s)} ${n(y - 0.85 * s)} ${n(x - 0.15 * s)} ${n(y - 0.9 * s)} ${n(x)} ${n(y - 0.5 * s)} C ${n(x + 0.2 * s)} ${n(y - 0.95 * s)} ${n(x + 0.9 * s)} ${n(y - 0.8 * s)} ${n(x + 0.88 * s)} ${n(y - 0.3 * s)} C ${n(x + 1.3 * s)} ${n(y - 0.3 * s)} ${n(x + 1.3 * s)} ${n(y + 0.3 * s)} ${n(x + s)} ${n(y + 0.3 * s)} Z`,
  // 海浪：一个卷起的浪头
  wave: (x: number, y: number, s: number) =>
    `M${n(x - s)} ${n(y + 0.4 * s)} C ${n(x - 0.6 * s)} ${n(y - 0.7 * s)} ${n(x + 0.5 * s)} ${n(y - 0.75 * s)} ${n(x + 0.75 * s)} ${n(y - 0.1 * s)} C ${n(x + 0.45 * s)} ${n(y - 0.35 * s)} ${n(x + 0.1 * s)} ${n(y - 0.15 * s)} ${n(x + 0.2 * s)} ${n(y + 0.2 * s)} C ${n(x + 0.5 * s)} ${n(y + 0.1 * s)} ${n(x + 0.85 * s)} ${n(y + 0.25 * s)} ${n(x + s)} ${n(y + 0.4 * s)} Z`,
  // 柳叶
  leaf: (x: number, y: number, s: number, deg = 0) => {
    const r = (deg * Math.PI) / 180;
    const p = (u: number, v: number) => `${n(x + u * Math.cos(r) - v * Math.sin(r))} ${n(y + u * Math.sin(r) + v * Math.cos(r))}`;
    return `M${p(-s, 0)} Q ${p(0, -0.42 * s)} ${p(s, 0)} Q ${p(0, 0.42 * s)} ${p(-s, 0)} Z`;
  },
  diamond: (x: number, y: number, s: number) => `M${n(x)} ${n(y - s)} L ${n(x + 0.6 * s)} ${n(y)} L ${n(x)} ${n(y + s)} L ${n(x - 0.6 * s)} ${n(y)} Z`,
  rect: (x: number, y: number, w: number, h: number) => `M${n(x)} ${n(y)} h ${n(w)} v ${n(h)} h ${n(-w)} Z`,
};

/* 一块皮：外轮廓 + 镂空 */
export const Hide: React.FC<{d: string; holes?: string[]; fill: string; stroke?: string; sw?: number; opacity?: number}> = ({d, holes = [], fill, stroke = C.edge, sw = 2.6, opacity = 0.92}) => (
  <path d={`${d} ${holes.join(' ')}`} fillRule="evenodd" fill={fill} fillOpacity={opacity} stroke={stroke} strokeWidth={sw} strokeLinejoin="round" />
);

/* 关节铆钉 */
const Rivet: React.FC<{x?: number; y?: number}> = ({x = 0, y = 0}) => (
  <g>
    <circle cx={x} cy={y} r={4.5} fill={C.black} />
    <circle cx={x} cy={y} r={1.8} fill="#a07a40" />
  </g>
);

/* ---------- 姿势与正向运动学 ---------- */
export type Arm = {sh: number; el: number}; // 肩、肘角度：正值往前抬
export type Pose = {near?: Arm; far?: Arm; head?: number; lean?: number; apart?: number};

const R = (x: number, y: number, deg: number): [number, number] => {
  const r = (deg * Math.PI) / 180;
  return [x * Math.cos(r) - y * Math.sin(r), x * Math.sin(r) + y * Math.cos(r)];
};
const handPos = (sx: number, sy: number, a: Arm, l1: number, l2: number): [number, number] => {
  const [ex, ey] = R(0, l1, -a.sh);
  const [hx, hy] = R(0, l2, -a.sh - a.el);
  return [sx + ex + hx, sy + ey + hy];
};

/* 散架：每块皮往下掉、转一下（apart 0→1） */
const fall = (i: number, k: number, floor: number) => {
  if (k <= 0) return '';
  const g = k * k;
  const dx = (rnd(i * 3 + 1) - 0.5) * 160 * k;
  const dy = floor * g;
  const dr = (rnd(i * 5 + 2) - 0.5) * 140 * g;
  return `translate(${n(dx)} ${n(dy)}) rotate(${n(dr)})`;
};

/* 操纵杆：从关节伸到台下，离幕布远，所以是虚的 */
const Rods: React.FC<{pts: [number, number][]}> = ({pts}) => (
  <g stroke="#2a170c" strokeLinecap="round" opacity={0.55} style={{filter: 'blur(2.2px)'}}>
    {pts.map(([x, y], i) => (
      <line key={i} x1={x} y1={y} x2={x + (i === 0 ? -120 : 40 - i * 30)} y2={SCREEN.h + 60} strokeWidth={i === 0 ? 7 : 4.5} />
    ))}
  </g>
);

type Common = {x: number; y: number; s?: number; face?: 1 | -1; pose?: Pose; rods?: boolean; opacity?: number; blur?: number};

/*
 * 通用骨架：远侧胳膊 → 背后的东西 → 下裙 → 上身 → 头 → 近侧胳膊。
 * 各角色只提供各部件怎么画。
 */
type Kit = {
  shoulderNear: [number, number];
  shoulderFar: [number, number];
  waist: number;
  l1: number;
  l2: number;
  floor: number; // 散架时掉多远
  back?: (t: number) => React.ReactNode; // 背后（光环、长发、冠带）
  skirt: (t: number) => React.ReactNode;
  torso: (t: number) => React.ReactNode;
  head: (t: number) => React.ReactNode;
  upper: (far: boolean) => React.ReactNode;
  fore: (far: boolean, t: number, a: Arm) => React.ReactNode;
};

const Build: React.FC<Common & {kit: Kit}> = ({x, y, s = 1, face = 1, pose = {}, rods = true, opacity = 1, blur = 0, kit}) => {
  const f = useCurrentFrame();
  const near = pose.near ?? {sh: 8, el: 10};
  const far = pose.far ?? {sh: -6, el: 8};
  const k = pose.apart ?? 0;
  const lean = pose.lean ?? 0;
  const [snx, sny] = kit.shoulderNear;
  const [sfx, sfy] = kit.shoulderFar;
  // 世界坐标里的杆子位置
  const toW = (px: number, py: number): [number, number] => {
    const [lx, ly] = R(px, py - kit.waist, -lean);
    return [x + face * s * lx, y + s * (ly + kit.waist)];
  };
  const hn = handPos(snx, sny, near, kit.l1, kit.l2);
  const hf = handPos(sfx, sfy, far, kit.l1, kit.l2);
  const rodPts = rods && k <= 0 ? [toW(-8, 30), toW(hn[0], hn[1]), toW(hf[0], hf[1])] : [];
  const armG = (isFar: boolean, a: Arm, sx: number, sy: number, i: number) => (
    <g transform={`${fall(i, k, kit.floor)} translate(${sx} ${sy}) rotate(${-a.sh})`}>
      {kit.upper(isFar)}
      <g transform={`translate(0 ${kit.l1}) rotate(${-a.el})`}>
        {kit.fore(isFar, f, a)}
        <Rivet />
      </g>
      <Rivet />
    </g>
  );
  return (
    <svg width={SCREEN.w} height={SCREEN.h} style={{position: 'absolute', inset: 0, overflow: 'visible', mixBlendMode: 'multiply', opacity, filter: blur ? `blur(${blur}px)` : undefined}}>
      <Rods pts={rodPts} />
      <g transform={`translate(${x} ${y}) scale(${face * s} ${s}) translate(0 ${kit.waist}) rotate(${-lean}) translate(0 ${-kit.waist})`}>
        <g opacity={0.85}>{armG(true, far, sfx, sfy, 1)}</g>
        {kit.back && <g transform={fall(2, k, kit.floor)}>{kit.back(f)}</g>}
        <g transform={`${fall(3, k, kit.floor * 0.4)} translate(0 ${kit.waist})`}>{kit.skirt(f)}</g>
        <g transform={fall(4, k, kit.floor * 0.7)}>{kit.torso(f)}</g>
        <g transform={`${fall(5, k, kit.floor * 0.9)} rotate(${-(pose.head ?? 0)})`}>
          {kit.head(f)}
          <Rivet />
        </g>
        {armG(false, near, snx, sny, 6)}
        <Rivet x={0} y={kit.waist} />
      </g>
    </svg>
  );
};

/* ================= 天尊：老生，玄黄两色（取尊名“福生玄黄”） ================= */

const TZ_HEAD = (t: number) => {
  const sway = Math.sin(t * 0.07) * 2;
  return (
    <g>
      {/* 冠带：从冠后垂下的两条红带 */}
      <path d={`M-18 -118 C -40 -90 -58 -40 ${-62 + sway} 30 L ${-50 + sway} 28 C -44 -30 -30 -84 -8 -116 Z`} fill={C.red} fillOpacity={0.9} stroke={C.edge} strokeWidth={2.2} />
      {/* 后脑与发髻：玄 */}
      <Hide d="M-6 2 C -30 -4 -46 -28 -44 -60 C -42 -90 -24 -104 0 -106 L 8 -106 L 6 -88 C -4 -82 -8 -68 -4 -54 C 0 -40 -2 -20 8 -6 Z" holes={[hole.leaf(-26, -40, 10, 70)]} fill={C.black} />
      {/* 耳朵 */}
      <path d="M-4 -62 C -14 -66 -16 -50 -6 -46" fill="none" stroke={C.black} strokeWidth={3} strokeLinecap="round" />
      {/* 空脸：只留一道脸的轮廓 */}
      <path d="M6 -104 C 16 -100 24 -92 26 -84 C 30 -76 34 -70 40 -62 L 48 -54 C 46 -50 42 -49 38 -50 L 41 -44 C 38 -42 36 -41 34 -41 L 38 -36 C 32 -30 30 -24 24 -18 C 20 -10 16 -4 12 0" fill="none" stroke={C.black} strokeWidth={5} strokeLinecap="round" strokeLinejoin="round" />
      {/* 眉、眼：丹凤眼，眼线长长拖到鬓角 */}
      <path d="M2 -86 C 12 -94 26 -94 34 -86 C 26 -89 14 -88 4 -82 Z" fill={C.black} />
      <path d="M-2 -72 C 10 -80 24 -80 36 -71" fill="none" stroke={C.black} strokeWidth={3.2} strokeLinecap="round" />
      <path d="M22 -74 C 28 -77 33 -75 36 -71 C 31 -69 26 -70 22 -74 Z" fill={C.black} />
      {/* 三绺长髯：玄，随风轻摆 */}
      <g transform={`rotate(${Math.sin(t * 0.09) * 3} 34 -46)`} fill={C.black} stroke={C.edge} strokeWidth={1.6} strokeLinejoin="round">
        <path d="M22 -42 C 16 -20 12 10 10 40 C 8 62 4 80 0 94 C 8 70 14 42 18 14 C 20 -6 22 -24 26 -40 Z" />
        <path d="M42 -44 C 54 -36 60 -12 58 20 C 56 44 52 62 48 76 C 46 56 46 30 44 10 C 43 -10 42 -28 38 -42 Z" />
        <path d="M30 -44 C 38 -28 42 8 40 56 C 39 86 36 108 34 124 C 28 102 26 72 26 42 C 25 10 25 -20 30 -44 Z" />
      </g>
      {/* 道冠：黄，刻一圈珠子和一颗火珠 */}
      <Hide
        d="M-26 -100 L -22 -130 C -18 -150 -4 -170 8 -180 C 20 -170 32 -150 34 -130 L 38 -100 C 20 -106 -8 -106 -26 -100 Z"
        holes={[hole.dot(6, -146, 7), hole.dot(-12, -116, 4), hole.dot(0, -118, 4), hole.dot(12, -118, 4), hole.dot(24, -116, 4), hole.leaf(6, -164, 6, 90), hole.diamond(-14, -134, 6), hole.diamond(26, -134, 6)]}
        fill={C.yellow}
      />
      <path d="M-40 -126 L 52 -126" stroke={C.black} strokeWidth={5} strokeLinecap="round" />
      <circle cx={52} cy={-126} r={5} fill={C.red} stroke={C.edge} strokeWidth={2} />
      <circle cx={-40} cy={-126} r={5} fill={C.red} stroke={C.edge} strokeWidth={2} />
    </g>
  );
};

const TZ_TORSO = () => (
  <g>
    <Hide d="M-12 4 C -40 10 -56 30 -56 62 L -52 150 L 46 150 C 48 110 50 70 42 40 C 36 20 24 8 12 4 Z" holes={[hole.cloud(-30, 66, 13), hole.cloud(-34, 116, 11), hole.cloud(20, 124, 11), hole.dot(-8, 136, 4)]} fill={C.yellow} />
    {/* 交领 */}
    <Hide d="M-14 2 L 14 2 L 34 72 L 20 78 L 2 22 L -12 44 L -22 36 Z" fill={C.black} />
    {/* 胸前太极 */}
    <circle cx={6} cy={98} r={17} fill={C.yellow} stroke={C.edge} strokeWidth={2.4} />
    <path d="M6 81 A 17 17 0 0 1 6 115 A 8.5 8.5 0 0 1 6 98 A 8.5 8.5 0 0 0 6 81 Z" fill={C.black} />
    <circle cx={6} cy={89.5} r={3} fill={C.black} />
    <circle cx={6} cy={106.5} r={3} fill={C.yellow} stroke={C.black} strokeWidth={1} />
    {/* 腰带与玉佩 */}
    <Hide d="M-54 138 L 48 138 L 48 156 L -54 156 Z" holes={[hole.rect(-44, 143, 12, 8), hole.rect(-24, 143, 12, 8), hole.rect(-4, 143, 12, 8), hole.rect(16, 143, 12, 8)]} fill={C.black} />
    <path d="M40 156 L 44 196" stroke={C.red} strokeWidth={4} />
    <circle cx={44} cy={200} r={7} fill={C.green} stroke={C.edge} strokeWidth={2} />
  </g>
);

const TZ_SKIRT = (t: number) => {
  const sw = Math.sin(t * 0.06) * 3;
  return (
    <g>
      <Hide
        d={`M-52 0 L 46 0 C 54 60 62 140 ${72 + sw} 220 L ${-66 + sw} 220 C -62 150 -56 70 -52 0 Z`}
        holes={[hole.cloud(-22, 40, 14), hole.cloud(22, 70, 14), hole.cloud(-30, 112, 16), hole.cloud(18, 150, 16), hole.cloud(-34, 178, 12), hole.cloud(40, 190, 10)]}
        fill={C.black}
      />
      <Hide d={`M${-66 + sw} 206 L ${72 + sw} 206 L ${74 + sw} 230 L ${-68 + sw} 230 Z`} holes={[0, 1, 2, 3, 4, 5].map((i) => hole.diamond(-50 + i * 22 + sw, 218, 6))} fill={C.yellow} />
      <path d={`M${26 + sw} 228 L ${64 + sw} 228 C ${80 + sw} 230 ${86 + sw} 238 ${84 + sw} 246 L ${24 + sw} 246 Z`} fill={C.black} stroke={C.edge} strokeWidth={2} />
    </g>
  );
};

const TZ_UPPER = (far: boolean) => (
  <Hide d="M-15 -8 C -2 -14 16 -8 18 6 L 16 78 L -14 78 Z" holes={[hole.cloud(1, 36, 8)]} fill={far ? '#b98226' : C.yellow} />
);

const TZ_FORE = (far: boolean, t: number, a: Arm) => {
  const g = a.sh + a.el; // 前臂相对“自然下垂”转过的角度；反转回来，袖子和拂尘就总是往下垂
  const col = far ? '#b98226' : C.yellow;
  return (
    <g>
      {/* 大袖：从袖口垂下来的一大片 */}
      <g transform={`translate(2 54) rotate(${g * 0.85})`}>
        <Hide d="M-20 -6 C -42 14 -50 60 -38 92 C -28 112 0 110 12 96 C 22 80 22 30 18 -6 Z" holes={[hole.cloud(-16, 40, 10), hole.cloud(-12, 76, 8)]} fill={col} />
        <path d="M-38 92 C -28 112 0 110 12 96" fill="none" stroke={C.black} strokeWidth={7} strokeLinecap="round" />
      </g>
      {/* 袖筒 */}
      <Hide d="M-14 0 L 16 0 L 20 56 L -18 56 Z" holes={[hole.cloud(1, 26, 7)]} fill={col} />
      <path d="M-18 50 L 20 50" stroke={C.black} strokeWidth={7} />
      {/* 手：空心 */}
      <path d="M-6 58 C -10 70 -4 82 6 82 C 14 80 16 70 12 58" fill="none" stroke={C.black} strokeWidth={3.5} strokeLinecap="round" />
      {!far && (
        <g>
          {/* 拂尘：柄斜着伸出去，马尾毛始终往下垂 */}
          <path d="M4 72 L 34 18" stroke="#7a3a18" strokeWidth={5} strokeLinecap="round" />
          <circle cx={34} cy={18} r={4} fill={C.red} stroke={C.edge} strokeWidth={1.5} />
          <g transform={`translate(34 18) rotate(${g + Math.sin(t * 0.11) * 5})`}>
            <path d="M-8 0 C -14 36 -12 80 -4 124 C 0 118 4 116 8 124 C 14 80 14 36 8 0 Z" fill="#f4e9cf" fillOpacity={0.9} stroke={C.edge} strokeWidth={2} />
            {[-5, -1, 3].map((dx) => (
              <path key={dx} d={`M${dx} 8 C ${dx - 2} 44 ${dx - 1} 84 ${dx + 1} 116`} stroke={C.edge} strokeWidth={1.1} fill="none" opacity={0.7} />
            ))}
          </g>
        </g>
      )}
    </g>
  );
};

const TZ: Kit = {
  shoulderNear: [24, 26],
  shoulderFar: [-30, 28],
  waist: 150,
  l1: 76,
  l2: 62,
  floor: 360,
  head: TZ_HEAD,
  torso: TZ_TORSO,
  skirt: TZ_SKIRT,
  upper: TZ_UPPER,
  fore: TZ_FORE,
};
export const TianZun: React.FC<Common> = (p) => <Build {...p} kit={TZ} />;

/* ================= 上帝：青衣（混沌海的浪纹、水袖、脑后一圈光环） ================= */

const GOD_BACK = (t: number) => {
  const sp = t * 0.25;
  return (
    <g>
      {/* 光环：一圈放射的光芒，慢慢转 */}
      <g transform={`translate(-6 -64) rotate(${sp})`}>
        <Hide
          d={`M-86 0 a 86 86 0 1 0 172 0 a 86 86 0 1 0 -172 0 Z M-64 0 a 64 64 0 1 1 128 0 a 64 64 0 1 1 -128 0 Z`}
          holes={Array.from({length: 18}, (_, i) => hole.leaf(Math.cos((i * 20 * Math.PI) / 180) * 75, Math.sin((i * 20 * Math.PI) / 180) * 75, 7, i * 20))}
          fill="#f0c040"
          sw={2.2}
        />
      </g>
      {/* 背后的长发 */}
      <Hide d="M-22 -40 C -46 -10 -50 60 -42 130 C -38 150 -30 170 -26 176 C -16 150 -14 90 -10 40 C -8 10 -10 -20 -4 -40 Z" holes={[hole.leaf(-32, 70, 18, 86), hole.leaf(-30, 120, 16, 88)]} fill={C.black} />
    </g>
  );
};

const GOD_HEAD = (t: number) => {
  const bead = Math.sin(t * 0.13) * 4;
  return (
    <g>
      {/* 发髻 */}
      <Hide d="M-4 0 C -26 -6 -42 -28 -42 -58 C -42 -92 -22 -112 2 -114 C 18 -114 26 -108 28 -100 C 16 -100 6 -96 4 -84 C 2 -70 6 -56 2 -44 C -2 -30 0 -14 10 -4 Z" holes={[hole.leaf(-24, -74, 10, 60)]} fill={C.black} />
      {/* 片子：沿鬓角一串小弯 */}
      {[0, 1, 2, 3].map((i) => (
        <path key={i} d={`M${6 - i * 1} ${-98 + i * 12} c 8 2 10 8 4 12 c -2 -4 -6 -6 -6 -12 Z`} fill={C.black} />
      ))}
      {/* 空脸 */}
      <path d="M8 -98 C 18 -96 24 -90 26 -82 C 30 -74 36 -68 42 -62 L 46 -58 C 44 -55 41 -54 38 -54 L 41 -48 C 39 -46 37 -45 36 -45 L 39 -41 C 36 -36 34 -32 28 -26 C 22 -20 16 -10 12 -2" fill="none" stroke={C.black} strokeWidth={4.5} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M4 -88 C 14 -95 26 -94 33 -87" fill="none" stroke={C.black} strokeWidth={2.6} strokeLinecap="round" />
      <path d="M0 -76 C 12 -82 26 -82 37 -73 L 41 -77" fill="none" stroke={C.black} strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M23 -76 C 29 -79 34 -77 37 -73 C 32 -71 27 -72 23 -76 Z" fill={C.black} />
      <path d="M10 -72 C 18 -66 26 -66 32 -68" fill="none" stroke={C.red} strokeWidth={4} opacity={0.6} />
      <circle cx={37} cy={-44} r={2.6} fill={C.red} />
      {/* 头面：一串泡子、一朵大花、一支凤钗和三串步摇 */}
      {[0, 1, 2, 3, 4, 5].map((i) => (
        <circle key={i} cx={-14 + i * 6.5} cy={-108 + Math.abs(i - 2.5) * 2} r={3.6} fill="#9fd3e6" stroke={C.edge} strokeWidth={1.4} />
      ))}
      <Hide d="M-46 -98 C -52 -112 -40 -122 -30 -116 C -26 -128 -10 -126 -12 -112 C -2 -110 -4 -94 -16 -94 C -18 -82 -34 -84 -34 -94 C -46 -88 -54 -96 -46 -98 Z" holes={[hole.dot(-28, -104, 5)]} fill={C.red} />
      <path d="M-12 -110 L -58 -126" stroke="#c98d22" strokeWidth={3.5} strokeLinecap="round" />
      <Hide d="M-58 -126 C -66 -136 -60 -146 -50 -142 C -52 -134 -48 -128 -58 -126 Z" fill="#c98d22" sw={1.6} />
      {[0, 1, 2].map((i) => (
        <g key={i} transform={`rotate(${bead * (1 + i * 0.3)} ${-40 - i * 6} -96)`}>
          <line x1={-40 - i * 6} y1={-96} x2={-40 - i * 6} y2={-66 + i * 6} stroke={C.edge} strokeWidth={1.4} />
          {[0, 1, 2].map((j) => (
            <circle key={j} cx={-40 - i * 6} cy={-88 + j * 9 + i * 2} r={2.6} fill={j === 2 ? C.red : '#9fd3e6'} stroke={C.edge} strokeWidth={1} />
          ))}
        </g>
      ))}
    </g>
  );
};

const GOD_TORSO = () => (
  <g>
    <Hide d="M-10 4 C -32 12 -40 34 -38 64 L -34 140 L 34 140 C 36 100 36 64 30 40 C 26 20 18 8 10 4 Z" holes={[hole.wave(-14, 96, 12), hole.wave(14, 120, 11), hole.wave(-18, 126, 9)]} fill={C.blue} />
    {/* 云肩：搭在肩上的一圈如意云头 */}
    <Hide
      d="M-34 26 C -40 12 -22 0 0 0 C 22 0 40 12 34 26 C 40 40 30 56 18 52 C 16 66 0 70 -6 60 C -16 72 -32 64 -30 52 C -44 52 -46 34 -34 26 Z"
      holes={[hole.cloud(0, 30, 9), hole.dot(-22, 40, 4), hole.dot(20, 38, 4), hole.dot(-6, 52, 3)]}
      fill={C.red}
    />
    {/* 护领：白，透光 */}
    <path d="M-8 2 L 10 2 L 4 18 Z" fill="#fff7e8" stroke={C.edge} strokeWidth={2} />
  </g>
);

const GOD_SKIRT = (t: number) => {
  const sw = Math.sin(t * 0.07 + 1) * 4;
  return (
    <g>
      <Hide
        d={`M-36 0 L 34 0 C 42 70 50 160 ${58 + sw} 236 L ${-58 + sw} 236 C -50 160 -42 70 -36 0 Z`}
        holes={[hole.wave(-10, 40, 13), hole.wave(16, 84, 14), hole.wave(-24, 120, 15), hole.wave(10, 164, 16), hole.wave(-30, 200, 12), hole.wave(34, 210, 10)]}
        fill={C.blue}
      />
      <Hide d={`M${-58 + sw} 222 L ${58 + sw} 222 L ${60 + sw} 242 L ${-60 + sw} 242 Z`} holes={[0, 1, 2, 3, 4].map((i) => hole.dot(-44 + i * 22 + sw, 232, 4.5))} fill={C.teal} />
    </g>
  );
};

const GOD_UPPER = (far: boolean) => <Hide d="M-12 -8 C 0 -14 14 -8 14 6 L 12 70 L -10 70 Z" holes={[hole.wave(1, 36, 7)]} fill={far ? '#2d6890' : C.blue} />;

const GOD_FORE = (far: boolean, t: number, arm: Arm) => {
  // 水袖：长长一截白绸，飘；抬手时也往下坠一些
  const a = Math.sin(t * 0.09 + (far ? 1.5 : 0));
  const b = Math.sin(t * 0.13 + (far ? 0.4 : 2));
  const g = (arm.sh + arm.el) * 0.55;
  return (
    <g>
      <Hide d="M-10 0 L 12 0 L 10 54 L -8 54 Z" fill={far ? '#2d6890' : C.blue} />
      <path d="M-10 46 L 12 46 L 12 56 L -10 56 Z" fill={C.red} stroke={C.edge} strokeWidth={2} />
      <path
        transform={`rotate(${g} 1 56)`}
        d={`M-10 56 C ${-16 + a * 10} 100 ${-34 + b * 16} 150 ${-24 + a * 22} 206 C ${-14 + a * 22} 218 ${4 + a * 20} 214 ${10 + a * 18} 202 C ${20 + b * 10} 150 ${16 + a * 6} 100 12 56 Z`}
        fill={C.sleeve}
        fillOpacity={0.55}
        stroke={C.edge}
        strokeWidth={2.2}
      />
      <path transform={`rotate(${g} 1 56)`} d={`M0 70 C ${-4 + b * 8} 120 ${-8 + a * 14} 160 ${-6 + a * 20} 200`} stroke={C.edge} strokeWidth={1.2} fill="none" opacity={0.5} />
    </g>
  );
};

const GOD: Kit = {
  shoulderNear: [20, 24],
  shoulderFar: [-24, 26],
  waist: 140,
  l1: 70,
  l2: 54,
  floor: 380,
  back: GOD_BACK,
  head: GOD_HEAD,
  torso: GOD_TORSO,
  skirt: GOD_SKIRT,
  upper: GOD_UPPER,
  fore: GOD_FORE,
};
export const God: React.FC<Common> = (p) => <Build {...p} kit={GOD} />;

/* ================= 本人：书生（小生），戴一副不合时代的圆眼镜 ================= */

const SC_HEAD = (t: number) => {
  const rib = Math.sin(t * 0.1) * 4;
  return (
    <g>
      <path d={`M-20 -96 C -36 -80 -46 -50 ${-50 + rib} -14 L ${-42 + rib} -14 C -38 -48 -28 -76 -14 -94 Z`} fill={C.black} stroke={C.edge} strokeWidth={1.6} />
      <path d={`M-14 -98 C -24 -74 -30 -44 ${-30 + rib} -8 L ${-22 + rib} -8 C -22 -42 -16 -72 -8 -96 Z`} fill={C.black} stroke={C.edge} strokeWidth={1.6} />
      <Hide d="M-4 0 C -26 -6 -38 -28 -36 -56 C -34 -82 -18 -96 4 -98 L 8 -90 C 0 -82 -2 -68 0 -54 C 2 -40 0 -20 10 -6 Z" fill={C.black} />
      <path d="M8 -96 C 18 -94 24 -88 26 -80 C 30 -72 34 -66 40 -60 L 45 -55 C 43 -52 40 -51 37 -51 L 40 -46 C 37 -44 35 -43 34 -43 L 37 -39 C 34 -33 32 -28 27 -22 C 22 -16 16 -8 12 -2" fill="none" stroke={C.black} strokeWidth={4.5} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6 -84 C 14 -90 24 -90 32 -84" fill="none" stroke={C.black} strokeWidth={3} strokeLinecap="round" />
      <path d="M22 -72 C 27 -75 32 -73 35 -69 C 30 -67 26 -68 22 -72 Z" fill={C.black} />
      {/* 圆眼镜 */}
      <circle cx={29} cy={-70} r={10} fill="#fff7e0" fillOpacity={0.35} stroke={C.black} strokeWidth={2.6} />
      <path d="M19 -70 L -6 -66" stroke={C.black} strokeWidth={2.4} />
      {/* 书生巾 */}
      <Hide d="M-30 -86 C -30 -110 -12 -122 10 -120 C 24 -118 28 -106 26 -96 C 10 -98 -10 -96 -30 -86 Z" holes={[hole.diamond(-4, -106, 6), hole.dot(12, -108, 3.5)]} fill={C.green} />
      <path d="M-30 -86 C -10 -96 10 -98 26 -96" fill="none" stroke={C.black} strokeWidth={4} />
    </g>
  );
};
const SC_TORSO = () => (
  <g>
    <Hide d="M-10 4 C -32 12 -42 34 -40 64 L -36 136 L 36 136 C 38 100 38 64 32 40 C 28 20 18 8 10 4 Z" holes={[hole.leaf(-16, 70, 14, 60), hole.leaf(10, 104, 12, 120), hole.dot(-20, 116, 4)]} fill={C.green} />
    <path d="M-10 2 L 12 2 L 26 60 L 18 62 L 2 18 L -8 30 Z" fill="#fff4dc" stroke={C.edge} strokeWidth={2} />
  </g>
);
const SC_SKIRT = (t: number) => {
  const sw = Math.sin(t * 0.06 + 2) * 3;
  return (
    <g>
      <Hide d={`M-38 0 L 36 0 C 44 70 52 150 ${60 + sw} 220 L ${-56 + sw} 220 C -50 150 -44 70 -38 0 Z`} holes={[hole.leaf(-10, 60, 18, 70), hole.leaf(16, 120, 18, 110), hole.leaf(-22, 170, 16, 70)]} fill={C.green} />
      <path d={`M${-56 + sw} 210 L ${60 + sw} 210`} stroke={C.black} strokeWidth={8} />
      <path d={`M${22 + sw} 218 L ${54 + sw} 218 C ${68 + sw} 220 ${72 + sw} 228 ${70 + sw} 234 L ${20 + sw} 234 Z`} fill={C.black} />
    </g>
  );
};
const SC_UPPER = (far: boolean) => <Hide d="M-12 -8 C 0 -14 14 -8 14 6 L 12 66 L -10 66 Z" fill={far ? '#2e6e40' : C.green} />;
const SC_FORE = (far: boolean) => (
  <g>
    <Hide d="M-12 0 C -30 14 -36 56 -26 82 C -16 96 2 92 12 80 L 12 0 Z" fill={far ? '#2e6e40' : C.green} />
    <path d="M-4 54 C -8 64 -4 74 4 74 C 12 72 14 64 10 54" fill="none" stroke={C.black} strokeWidth={3.2} strokeLinecap="round" />
    {!far && (
      <g transform="translate(6 66) rotate(-20)">
        <Hide d="M-4 -26 L 30 -30 L 34 22 L 0 26 Z" holes={[hole.rect(6, -18, 20, 3), hole.rect(6, -10, 20, 3), hole.rect(6, -2, 20, 3)]} fill="#c99a52" />
      </g>
    )}
  </g>
);
const SC: Kit = {
  shoulderNear: [20, 24],
  shoulderFar: [-24, 26],
  waist: 136,
  l1: 66,
  l2: 56,
  floor: 360,
  head: SC_HEAD,
  torso: SC_TORSO,
  skirt: SC_SKIRT,
  upper: SC_UPPER,
  fore: SC_FORE,
};
export const Scholar: React.FC<Common> = (p) => <Build {...p} kit={SC} />;

/* ================= 最初的造物主：一身两头，左半边玄黄（天尊）、右半边青（上帝） ================= */
export const Creator: React.FC<{x: number; y: number; s?: number; split?: number; opacity?: number; arms?: number}> = ({x, y, s = 1, split = 0, opacity = 1, arms = 1}) => {
  const f = useCurrentFrame();
  const d = split * 90;
  const tilt = split * 8;
  const up: Arm = {sh: 40 + 50 * arms, el: 20};
  return (
    <svg width={SCREEN.w} height={SCREEN.h} style={{position: 'absolute', inset: 0, overflow: 'visible', mixBlendMode: 'multiply', opacity}}>
      <defs>
        <clipPath id="crL">
          <rect x={-500} y={-500} width={500} height={1100} />
        </clipPath>
        <clipPath id="crR">
          <rect x={0} y={-500} width={500} height={1100} />
        </clipPath>
      </defs>
      <g transform={`translate(${x} ${y}) scale(${s})`}>
        {/* 背后一圈大光环 */}
        <g opacity={1 - split}>
          <circle cx={0} cy={-64} r={150} fill="none" stroke="#f0c040" strokeWidth={14} strokeOpacity={0.8} />
          <circle cx={0} cy={-64} r={150} fill="none" stroke={C.edge} strokeWidth={2} />
          <circle cx={0} cy={-64} r={136} fill="none" stroke={C.edge} strokeWidth={2} />
        </g>
        <g transform={`translate(${-d} 0) rotate(${-tilt} 0 140)`}>
          <g clipPath="url(#crL)">
            <g transform="scale(-1 1)">
              <g transform={`translate(${TZ.shoulderFar[0]} ${TZ.shoulderFar[1]}) rotate(${-10})`} opacity={0.85}>
                {TZ.upper(true)}
              </g>
              <g transform="translate(0 150)">{TZ_SKIRT(f)}</g>
              {TZ_TORSO()}
              {TZ_HEAD(f)}
              <g transform={`translate(${TZ.shoulderNear[0]} ${TZ.shoulderNear[1]}) rotate(${-up.sh})`}>
                {TZ.upper(false)}
                <g transform={`translate(0 ${TZ.l1}) rotate(${-up.el})`}>{TZ.fore(false, f, up)}</g>
              </g>
            </g>
          </g>
        </g>
        <g transform={`translate(${d} 0) rotate(${tilt} 0 140)`}>
          <g clipPath="url(#crR)">
            <g transform="translate(0 0)">
              <g transform="translate(0 140)">{GOD_SKIRT(f)}</g>
              {GOD_TORSO()}
              {GOD_HEAD(f)}
              <g transform={`translate(${GOD.shoulderNear[0]} ${GOD.shoulderNear[1]}) rotate(${-up.sh})`}>
                {GOD.upper(false)}
                <g transform={`translate(0 ${GOD.l1}) rotate(${-up.el})`}>{GOD.fore(false, f, up)}</g>
              </g>
            </g>
          </g>
        </g>
        <line x1={0} y1={-130} x2={0} y2={400} stroke={C.edge} strokeWidth={3} opacity={1 - split} />
      </g>
    </svg>
  );
};
