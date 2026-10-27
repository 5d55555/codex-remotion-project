import React from 'react';
import {useCurrentFrame} from 'remotion';
import {C} from './theme';

/*
 * 卢米安（日漫Q版画法）：黑色短碎发、浅蓝色双眸、五官深刻（第一卷第1章）；
 * 衣着取第五卷第144章“白色衬衣、黑色马甲”。原点是脸的中心，s=1 时脸宽约 190 像素。
 */
export type Face = 'smirk' | 'relief' | 'pain' | 'shock' | 'dead' | 'angry' | 'calm' | 'sad';

const INK = '#16161a';
const SKIN = '#fdf5ec';
const SHADE = '#efd8c6';
const HAIR = '#1b1c24';
const HAIR_LINE = '#3a3d52';
const HAIR_HI = '#5a6180';
const IRIS_RING = '#183553';

/*
 * 眼睛在局部坐标里画（原点为眼睛中心，+x 朝耳朵一侧），左眼用镜像。
 * c1/c2 是上眼睑两个控制点的高度：c1 靠内眼角，c2 靠外眼角；low 是下眼睑的深度。
 */
type EyeP = {c1: number; c2: number; low: number; iris: number; hl: boolean};
const EYE: Partial<Record<Face, EyeP>> = {
  calm: {c1: -26, c2: -34, low: 32, iris: 1, hl: true},
  smirk: {c1: -4, c2: -10, low: 24, iris: 1, hl: true},
  shock: {c1: -38, c2: -44, low: 38, iris: 0.5, hl: false},
  dead: {c1: -22, c2: -28, low: 32, iris: 1, hl: false},
  angry: {c1: -6, c2: -32, low: 28, iris: 0.92, hl: true},
  sad: {c1: -34, c2: -16, low: 32, iris: 1, hl: true},
};

const Eye: React.FC<{side: -1 | 1; face: Face; blink: boolean; id: string; lashes?: boolean}> = ({side, face, blink, id, lashes}) => {
  const tf = `translate(${46 * side} 12) scale(${side} 1)`;
  if (face === 'relief') {
    return (
      <g transform={tf} stroke={INK} strokeWidth={5.5} fill="none" strokeLinecap="round">
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
      <g transform={tf} stroke={INK} strokeWidth={5} fill="none" strokeLinecap="round">
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
          <stop offset="0" stopColor={dull ? '#4a525c' : '#1f4a78'} />
          <stop offset="0.55" stopColor={dull ? '#6f7882' : '#3f86c4'} />
          <stop offset="1" stopColor={dull ? '#a3acb5' : '#a6e0ff'} />
        </linearGradient>
      </defs>
      <path d={opening} fill="#fff" />
      <g clipPath={`url(#${key}-eye)`}>
        <ellipse cx={2} cy={5} rx={19 * ir} ry={25 * ir} fill={`url(#${key}-iris)`} stroke={IRIS_RING} strokeWidth={2.5} />
        <ellipse cx={2} cy={7} rx={8 * ir} ry={11 * ir} fill="#0f1d30" />
        {/* 虹膜下方的反光 */}
        {!dull && <ellipse cx={2} cy={5 + 16 * ir} rx={11 * ir} ry={5 * ir} fill="#d4f1ff" opacity={0.55} />}
        {/* 上眼睑投下的阴影 */}
        <path d={`M-30 2 C -18 ${c1} 14 ${c2} 32 -4 L 32 6 C 14 ${c2 + 12} -18 ${c1 + 12} -30 12 Z`} fill={IRIS_RING} opacity={0.28} />
        {e.hl && (
          <>
            <ellipse cx={-8 * side} cy={-6} rx={6.5} ry={7.5} fill="#fff" />
            <circle cx={9 * side} cy={17} r={3} fill="#fff" />
          </>
        )}
      </g>
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
  return <path transform={`translate(${46 * side} -38) scale(${side} 1)`} d={`M-24 ${inn} Q 4 ${mid - 9} 32 ${out} Q 4 ${mid - 2} -24 ${inn + 8} Z`} fill={INK} />;
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

/* 刘海：长短不一的发束，左右各自往外弯，中间一缕垂到两眼之间，两侧发盖住耳朵。 */
const TIPS: [number, number][] = [
  [-114, -70],
  [-104, 50],
  [-96, -40],
  [-88, -16],
  [-72, -80],
  [-58, -56],
  [-44, -86],
  [-32, -54],
  [-18, -80],
  [-4, -8],
  [8, -84],
  [24, -58],
  [38, -88],
  [54, -54],
  [68, -78],
  [90, -18],
  [96, -40],
  [104, 50],
  [114, -70],
];
const BANGS = (() => {
  let d = `M${TIPS[0][0]} ${TIPS[0][1]}`;
  for (let i = 1; i < TIPS.length; i++) {
    const [x0, y0] = TIPS[i - 1];
    const [x1, y1] = TIPS[i];
    const mx = (x0 + x1) / 2;
    d += ` Q ${mx + (mx < 0 ? -7 : 7)} ${(y0 + y1) / 2} ${x1} ${y1}`;
  }
  return d;
})();
// 填充用：沿头顶的弧线闭合，不描边，免得在后发上留下一道框
const BANGS_FILL = `${BANGS} C 120 -150 70 -192 0 -192 C -70 -192 -120 -150 -114 -70 Z`;
// 发束之间的分缝：从每个发根往上延伸一小段
const PARTS = TIPS.filter((_, i) => i % 2 === 0 && i > 0 && i < TIPS.length - 1).map(([x, y]) => `M${x} ${y} Q ${x + (x < 0 ? 4 : -4)} ${y - 18} ${x + (x < 0 ? 10 : -10)} ${y - 34}`);

const BACK = 'M-108 40 C -130 -40 -128 -150 -62 -188 C -20 -210 40 -210 82 -182 C 130 -148 132 -40 108 40 Q 0 60 -108 40 Z';
// 两侧往外翘的几撮，加头顶一根呆毛
const TUFTS = [
  'M-112 -112 C -132 -118 -148 -116 -158 -106 C -140 -104 -126 -94 -118 -78 Z',
  'M-122 -54 C -140 -50 -152 -40 -156 -26 C -140 -32 -128 -28 -118 -18 Z',
  'M112 -120 C 134 -132 152 -130 162 -120 C 142 -116 128 -104 120 -88 Z',
  'M122 -60 C 142 -58 154 -46 158 -32 C 142 -38 130 -34 120 -24 Z',
  'M-6 -200 C 4 -236 40 -248 64 -230 C 40 -228 22 -218 14 -198 Z',
];
// 头发高光：沿头顶一道月牙形的亮带，内缘是锯齿（“天使环”）
const SHINE = (() => {
  const n = 16;
  const pt = (r: number, deg: number) => {
    const t = (deg * Math.PI) / 180;
    return `${(Math.cos(t) * r).toFixed(1)} ${(-60 + Math.sin(t) * r * 0.92).toFixed(1)}`;
  };
  const outer: string[] = [];
  const inner: string[] = [];
  for (let i = 0; i <= n; i++) {
    const k = i / n;
    const deg = 212 + k * 116;
    const ro = 104 + Math.sin(Math.PI * k) * 4;
    const depth = Math.sin(Math.PI * k) * (i % 2 ? 16 : 6);
    outer.push(pt(ro, deg));
    inner.push(pt(ro - 2 - depth, deg));
  }
  return `M${outer.join(' L ')} L ${inner.reverse().join(' L ')} Z`;
})();
// 女身形态（第五卷第145章）：黑色长直发，背后一大片，肩前各垂一缕
const LONG = 'M-110 -60 C -150 40 -164 170 -152 268 L 152 268 C 164 170 150 40 110 -60 Z';
const LONG_FRONT = 'M-96 -10 C -110 80 -118 170 -110 246 L -96 222 L -88 250 C -92 170 -88 80 -78 10 Z M 96 -10 C 110 80 118 170 110 246 L 96 222 L 88 250 C 92 170 88 80 78 10 Z';
const FACE = 'M-96 -110 L -96 -40 C -98 0 -96 34 -84 58 C -66 92 -30 110 0 114 C 30 110 66 92 84 58 C 96 34 98 0 96 -40 L 96 -110 Z';

export const Lumian: React.FC<{
  x: number;
  y: number;
  s?: number;
  face?: Face;
  hood?: boolean;
  bandage?: boolean;
  sweat?: boolean;
  gloom?: boolean; // 额头上的黑线（绝望）
  tilt?: number;
  shake?: number;
  body?: boolean;
  longHair?: boolean;
}> = ({x, y, s = 1, face = 'calm', hood, bandage, sweat, gloom, tilt = 0, shake = 0, body = true, longHair}) => {
  const f = useCurrentFrame();
  const blink = f % 110 < 4;
  const jx = shake ? Math.sin(f * 2.1) * shake : 0;
  const bob = Math.sin(f * 0.08) * 3;
  const id = `lm${Math.round(x)}-${Math.round(y)}-${Math.round(s * 100)}`;
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
        {longHair && <path d={LONG} fill={HAIR} />}
        {hood && (
          <g>
            {/* 兜帽：外层布料、帽檐内侧的暗面、两道褶 */}
            <path d="M-172 150 C -198 -50 -134 -240 0 -244 C 134 -240 198 -50 172 150 Q 0 178 -172 150 Z" fill="#3d3c48" stroke={INK} strokeWidth={4} />
            <path d="M-136 142 C -152 -40 -112 -206 0 -210 C 112 -206 152 -40 136 142 Z" fill="#141419" />
            <path d="M-158 60 Q -170 -60 -124 -170 M 158 60 Q 170 -60 124 -170" stroke="#2a2a33" strokeWidth={4} fill="none" strokeLinecap="round" />
          </g>
        )}
        {body && (
          <g>
            {/* 肩与马甲 */}
            <path d="M-170 270 C -160 180 -110 140 -46 130 L 46 130 C 110 140 160 180 170 270 Z" fill={hood ? '#202128' : '#24252c'} stroke={INK} strokeWidth={4} />
            {/* 衬衫领口与领子 */}
            <path d="M-44 130 L 0 204 L 44 130 Z" fill="#fff" stroke={INK} strokeWidth={3.5} strokeLinejoin="round" />
            <path d="M-44 128 L -8 174 L -30 186 L -58 140 Z M 44 128 L 8 174 L 30 186 L 58 140 Z" fill="#fff" stroke={INK} strokeWidth={3.5} strokeLinejoin="round" />
            {!hood && <path d="M-16 204 L -2 270 M 16 204 L 2 270" stroke="#3a3b44" strokeWidth={3} />}
            {!hood && [222, 246].map((by) => <circle key={by} cx={0} cy={by} r={4} fill="#9a9aa4" />)}
          </g>
        )}
        {longHair && <path d={LONG_FRONT} fill={HAIR} stroke={INK} strokeWidth={3} strokeLinejoin="round" />}
        {/* 脖子 */}
        <path d="M-22 92 L -22 142 Q 0 150 22 142 L 22 92 Z" fill={SKIN} stroke={INK} strokeWidth={3.5} />
        <path d="M-22 98 Q 0 126 22 98 L 22 114 Q 0 138 -22 114 Z" fill={SHADE} />
        {/* 后发与翘起的发梢 */}
        <path d={BACK} fill={HAIR} stroke={INK} strokeWidth={3} />
        {TUFTS.slice(0, hood ? 0 : 5).map((d) => (
          <path key={d} d={d} fill={HAIR} stroke={INK} strokeWidth={3} strokeLinejoin="round" />
        ))}
        {/* 耳朵 */}
        {[-1, 1].map((sd) => (
          <g key={sd} transform={`scale(${sd} 1)`}>
            <path d="M90 -2 C 114 -12 120 28 92 38" fill={SKIN} stroke={INK} strokeWidth={3.5} />
            <path d="M96 8 C 106 8 106 22 96 26" stroke="#d8b9a2" strokeWidth={2.5} fill="none" />
          </g>
        ))}
        {/* 脸：Q版的圆脸颊、尖下巴 */}
        <path d={FACE} fill={SKIN} stroke={INK} strokeWidth={4} strokeLinejoin="round" />
        <g clipPath={`url(#${id}-face)`}>
          {/* 侧面阴影 */}
          <path d="M96 -40 C 98 20 92 54 70 86 C 80 50 84 10 80 -40 Z" fill={SHADE} />
          {/* 刘海投在额头上的影子 */}
          <path d={BANGS_FILL} transform="translate(4 10)" fill={SHADE} />
          {face !== 'dead' && [-1, 1].map((sd) => <ellipse key={sd} cx={sd * 58} cy={48} rx={17} ry={7} fill="#f3a2a2" opacity={0.3} />)}
          {gloom && (
            <g>
              <rect x={-100} y={-110} width={200} height={130} fill={`url(#${id}-gloom)`} />
              {Array.from({length: 8}, (_, i) => (
                <line key={i} x1={-70 + i * 20} y1={-40} x2={-70 + i * 20} y2={-14 + (i % 2) * 8} stroke={INK} strokeWidth={3} />
              ))}
            </g>
          )}
        </g>
        <Brow side={-1} face={face} />
        <Brow side={1} face={face} />
        <Eye side={-1} face={face} blink={blink} id={id} lashes={longHair} />
        <Eye side={1} face={face} blink={blink} id={id} lashes={longHair} />
        {/* 鼻子：一笔 */}
        <path d="M5 46 L 1 54" stroke="#c9a892" strokeWidth={3} strokeLinecap="round" />
        <Mouth face={face} />
        {(face === 'pain' || face === 'shock' || face === 'relief') && (
          <g stroke="#e07f7f" strokeWidth={3} strokeLinecap="round">
            {[-1, 1].map((sd) => (
              <g key={sd}>
                {[0, 1, 2].map((k) => (
                  <line key={k} x1={sd * (48 + k * 9)} y1={52} x2={sd * (54 + k * 9)} y2={42} />
                ))}
              </g>
            ))}
          </g>
        )}
        {/* 前发 */}
        <path d={BANGS_FILL} fill={HAIR} />
        <path d={BANGS} fill="none" stroke={INK} strokeWidth={3} strokeLinejoin="round" />
        <path d={PARTS.join(' ')} stroke={HAIR_LINE} strokeWidth={2.5} fill="none" strokeLinecap="round" />
        {/* 头发高光 */}
        <path d={SHINE} fill={HAIR_HI} />
        {face === 'angry' && (
          <g transform="translate(112 -150)" fill="none" strokeLinecap="round">
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
        {sweat && <path d="M120 -40 C 110 -20 112 -4 122 0 C 134 -4 134 -20 120 -40 Z" fill="#dff3ff" stroke={INK} strokeWidth={2.5} />}
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
