import React from 'react';
import {staticFile, useCurrentFrame} from 'remotion';
import {prog} from './kit';
import {C, rnd} from './theme';

/*
 * 两位神的形象。原作没有写过两位的本体样貌，这里是设计：
 * 天尊——灰雾凝成、坐于云座的巨像，宽袖、莲冠、背后光环，身后是源堡（灰雾之上的古老宫殿）；
 * 上帝——混沌液体凝成、张开双臂的巨像，头部是没有五官的漩涡（取自《宿命之环》《选择》中的显现）。
 */

type FigProps = {
  x: number; // 底部中心
  y: number;
  h?: number; // 高度（像素）
  at?: number;
  crack?: number; // 0—1 裂纹
  fade?: number; // 0—1 消散
  flash?: number; // 0—1 闪白
  lean?: number; // 倾斜角度
  glow?: number; // 0—1 外发光强度
  cast?: number; // 0—1 抬手施法
  id?: string;
};

// 裂纹：从身体中部向外分叉的折线，按 crack 逐条显现。
const cracks = (seed: number, cx: number, top: number, h: number, spread: number) =>
  Array.from({length: 10}, (_, i) => {
    let px = cx + (rnd(seed + i) - 0.5) * spread;
    let py = top + h * rnd(seed + i * 3.1);
    const pts = [`${px},${py}`];
    for (let k = 0; k < 7; k++) {
      px += (rnd(seed + i * 7 + k) - 0.5) * 80;
      py += (rnd(seed + i * 11 + k) - 0.35) * 60;
      pts.push(`${px},${py}`);
    }
    return pts.join(' ');
  });

const Cracks: React.FC<{lines: string[]; amount: number; color: string}> = ({lines, amount, color}) => (
  <>
    {lines.map((p, i) => {
      const k = Math.max(0, Math.min(1, amount * lines.length - i));
      if (k <= 0) return null;
      return <polyline key={i} points={p} fill="none" stroke={color} strokeWidth={3.5} strokeLinejoin="round" strokeDasharray={700} strokeDashoffset={700 * (1 - k)} style={{filter: `drop-shadow(0 0 8px ${color})`}} />;
    })}
  </>
);

// 边缘雾化：静态位移噪声，让轮廓像雾和液体而不是剪纸。
const Wisp: React.FC<{id: string; scale: number; freq?: number}> = ({id, scale, freq = 0.018}) => (
  <filter id={id} x="-20%" y="-20%" width="140%" height="140%">
    <feTurbulence type="fractalNoise" baseFrequency={freq} numOctaves={2} seed={7} result="n" />
    <feDisplacementMap in="SourceGraphic" in2="n" scale={scale} xChannelSelector="R" yChannelSelector="G" />
  </filter>
);

/* ---------------- 天尊 ---------------- */

const TZ = {
  base: 'M40 790 C 30 740 90 712 140 724 C 160 690 230 684 260 708 C 290 680 360 684 380 712 C 420 690 490 700 500 730 C 560 724 590 770 560 800 L 50 800 Z',
  lap: 'M92 724 C 110 640 150 560 196 500 L 404 500 C 450 560 490 640 508 724 Z',
  torso: 'M206 512 C 196 420 196 330 214 272 C 240 250 360 250 386 272 C 404 330 404 420 394 512 Z',
  sleeveL: 'M218 276 C 140 300 86 420 66 650 C 98 676 150 668 182 636 C 186 540 196 450 226 380 Z',
  sleeveR: 'M382 276 C 460 300 514 420 534 650 C 502 676 450 668 418 636 C 414 540 404 450 374 380 Z',
  hands: 'M262 380 C 280 360 320 360 338 380 C 344 404 326 424 300 426 C 274 424 256 404 262 380 Z',
  crown: 'M262 150 L 338 150 L 330 128 L 270 128 Z M 222 122 L 378 114 L 378 124 L 222 132 Z',
};

export const Tianzun: React.FC<FigProps> = ({x, y, h = 760, at = 0, crack = 0, fade = 0, flash = 0, lean = 0, glow = 0.6, cast = 0, id = 'tz'}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 30);
  if (p <= 0 || fade >= 1) return null;
  const s = h / 800;
  const drift = f * 0.5;
  // 飘带：从双肩向身后飘，正弦起伏。
  const ribbon = (side: 1 | -1, k: number) => {
    const pts = Array.from({length: 24}, (_, i) => {
      const t = i / 23;
      const px = 300 + side * (120 + t * 300);
      const py = 290 - t * 120 + Math.sin(t * 4 + f * 0.05 + k) * 30 * t;
      return `${px},${py}`;
    });
    return <polyline key={`${side}${k}`} points={pts.join(' ')} fill="none" stroke={`url(#${id}-fog)`} strokeWidth={34 - k * 10} strokeLinecap="round" opacity={0.32} />;
  };
  const castLift = cast * 40;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible', opacity: p * (1 - fade), filter: fade > 0 ? `blur(${fade * 16}px)` : undefined}}>
      <defs>
        <pattern id={`${id}-fog`} patternUnits="userSpaceOnUse" width={420} height={420} patternTransform={`translate(${drift} ${-drift * 0.4})`}>
          <rect width={420} height={420} fill="#262a31" />
          <image href={staticFile('tex/fog.png')} width={420} height={420} opacity={0.9} />
        </pattern>
        <radialGradient id={`${id}-shade`} cx="0.5" cy="0.35" r="0.75">
          <stop offset="0" stopColor="#000" stopOpacity={0} />
          <stop offset="1" stopColor="#000" stopOpacity={0.55} />
        </radialGradient>
        <Wisp id={`${id}-wisp`} scale={14} />
      </defs>
      <g transform={`translate(${x} ${y - fade * 80}) rotate(${lean}) scale(${s}) translate(-300 -800)`}>
        {/* 光环：双圈青铜，缓慢转动的刻度 */}
        <g opacity={0.9} style={{filter: `drop-shadow(0 0 ${14 + 30 * glow}px rgba(201,164,92,0.8))`}}>
          <circle cx={300} cy={190} r={150} fill="none" stroke={C.bronze} strokeWidth={3} />
          <circle cx={300} cy={190} r={168} fill="none" stroke={C.bronze} strokeWidth={1.5} strokeDasharray="4 14" transform={`rotate(${f * 0.4} 300 190)`} />
        </g>
        <g style={{filter: `drop-shadow(0 0 ${8 + 26 * glow}px rgba(201,164,92,${0.3 + 0.4 * glow}))`}}>
        <g filter={`url(#${id}-wisp)`}>
          {ribbon(-1, 0)}
          {ribbon(1, 0)}
          {ribbon(-1, 1.6)}
          {ribbon(1, 1.6)}
          <path d={TZ.base} fill={`url(#${id}-fog)`} />
          <path d={TZ.lap} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={2.5} />
          <path d={TZ.torso} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={2.5} />
          <g transform={`rotate(${-cast * 18} 218 290)`}>
            <path d={TZ.sleeveL} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={2.5} />
          </g>
          <g transform={`rotate(${cast * 18} 382 290)`}>
            <path d={TZ.sleeveR} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={2.5} />
          </g>
          <path d={TZ.hands} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={2} transform={`translate(0 ${-castLift})`} />
          <path d="M300 280 L 300 500" stroke={C.bronze} strokeWidth={2} opacity={0.7} />
          <circle cx={300} cy={196} r={56} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={2.5} />
          <path d={TZ.crown} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={2.5} />
          {/* 冕旒：冕板前沿垂下的珠串，遮住面容 */}
          {Array.from({length: 9}, (_, i) => {
            const bx = 232 + i * 17;
            const sway = Math.sin(f * 0.05 + i * 0.6) * 2;
            return (
              <g key={i}>
                <line x1={bx} y1={130} x2={bx + sway} y2={226} stroke={C.bronze} strokeWidth={1.2} opacity={0.8} />
                {[148, 168, 188, 208, 226].map((by) => (
                  <circle key={by} cx={bx + (sway * (by - 130)) / 96} cy={by} r={3.2} fill={C.bronze} />
                ))}
              </g>
            );
          })}
          <path d={`${TZ.lap} ${TZ.torso}`} fill={`url(#${id}-shade)`} />
        </g>
        </g>
        <Cracks lines={cracks(11, 300, 260, 460, 220)} amount={crack} color="#fff1c8" />
        {flash > 0 && (
          <g opacity={flash}>
            <path d={TZ.lap} fill="#fff" />
            <path d={TZ.torso} fill="#fff" />
            <path d={TZ.sleeveL} fill="#fff" />
            <path d={TZ.sleeveR} fill="#fff" />
            <circle cx={300} cy={196} r={56} fill="#fff" />
          </g>
        )}
      </g>
    </svg>
  );
};

/* 源堡：灰雾之上的古老宫殿，石柱、穹顶，远景。 */
export const Yuanbao: React.FC<{x: number; y: number; w?: number; at?: number; opacity?: number; id?: string}> = ({x, y, w = 1100, at = 0, opacity = 0.5, id = 'yb'}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 40);
  if (p <= 0) return null;
  const s = w / 600;
  const pillars = Array.from({length: 13}, (_, i) => 92 + i * 34.6);
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible', opacity: p * opacity, filter: 'blur(1.2px)'}}>
      <defs>
        <pattern id={`${id}-fog`} patternUnits="userSpaceOnUse" width={512} height={512} patternTransform={`translate(${f * 0.3} 0)`}>
          <rect width={512} height={512} fill="#1a1d23" />
          <image href={staticFile('tex/fog.png')} width={512} height={512} opacity={0.6} />
        </pattern>
        <linearGradient id={`${id}-fade`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0.5" stopColor="#fff" stopOpacity={1} />
          <stop offset="1" stopColor="#fff" stopOpacity={0} />
        </linearGradient>
        <mask id={`${id}-mask`}>
          <rect x={-50} y={-120} width={700} height={420} fill={`url(#${id}-fade)`} />
        </mask>
      </defs>
      <g transform={`translate(${x} ${y + (1 - p) * 40}) scale(${s}) translate(-300 -150)`} mask={`url(#${id}-mask)`} style={{filter: 'drop-shadow(0 0 22px rgba(201,164,92,0.4))'}}>
        <path d="M170 80 Q 300 -70 430 80 Z" fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={1.5} />
        <path d="M230 80 Q 300 -10 370 80" fill="none" stroke={C.bronze} strokeWidth={1} opacity={0.6} />
        <rect x={296} y={-50} width={8} height={30} fill={C.bronze} />
        {[40, 520].map((tx) => (
          <g key={tx}>
            <rect x={tx} y={-10} width={40} height={200} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={1.2} />
            <path d={`M${tx - 4} -10 L ${tx + 20} -70 L ${tx + 44} -10 Z`} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={1.2} />
          </g>
        ))}
        <rect x={80} y={80} width={440} height={12} fill={C.bronze} opacity={0.7} />
        {pillars.map((px) => (
          <rect key={px} x={px} y={92} width={14} height={110} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={0.8} />
        ))}
        <rect x={60} y={202} width={480} height={12} fill={C.bronze} opacity={0.7} />
        <rect x={30} y={214} width={540} height={14} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={1} />
        <rect x={0} y={228} width={600} height={16} fill={`url(#${id}-fog)`} stroke={C.bronze} strokeWidth={1} />
      </g>
    </svg>
  );
};

/* 青铜长桌与高背椅：源堡里的陈设。 */
export const BronzeTable: React.FC<{x: number; y: number; w?: number; at?: number}> = ({x, y, w = 900, at = 0}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 24);
  if (p <= 0) return null;
  const chairs = Array.from({length: 11}, (_, i) => i);
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible', opacity: p}}>
      <g transform={`translate(${x} ${y})`} style={{filter: 'drop-shadow(0 0 12px rgba(201,164,92,0.5))'}}>
        {chairs.map((i) => {
          const cx = -w / 2 + 80 + (i * (w - 160)) / 10;
          const back = i === 0 ? 150 : 96;
          return <rect key={i} x={cx - 16} y={-back - 8} width={32} height={back} rx={6} fill="#2a2216" stroke={C.bronze} strokeWidth={2} opacity={0.92} />;
        })}
        <path d={`M${-w / 2} 0 L ${w / 2} 0 L ${w / 2 - 60} 46 L ${-w / 2 + 60} 46 Z`} fill="#3a2e1c" stroke={C.bronze} strokeWidth={3} />
      </g>
    </svg>
  );
};

/* ---------------- 上帝 ---------------- */

const GD = {
  body: 'M206 252 C 236 236 364 236 394 252 C 404 320 390 400 372 452 C 420 560 470 700 488 880 L 112 880 C 130 700 180 560 228 452 C 210 400 196 320 206 252 Z',
  armL: 'M0 0 C -40 10 -120 20 -250 34 C -262 44 -256 58 -244 60 C -120 54 -40 46 6 40 Z',
};

/* 漩涡脸：没有眼睛、鼻子、嘴巴，由包容所有颜色的混沌液体构成。 */
export const Vortex: React.FC<{cx: number; cy: number; r: number; id: string; spin?: number}> = ({cx, cy, r, id, spin = 1}) => {
  const f = useCurrentFrame();
  const arms = Array.from({length: 6}, (_, a) => {
    const pts = Array.from({length: 48}, (_, k) => {
      const t = k / 47;
      const ang = a * ((Math.PI * 2) / 6) + t * 5.6 + f * 0.05 * spin;
      const rr = r * t;
      return `${cx + Math.cos(ang) * rr},${cy + Math.sin(ang) * rr}`;
    });
    return pts.join(' ');
  });
  return (
    <>
      <defs>
        <clipPath id={`${id}-clip`}>
          <circle cx={cx} cy={cy} r={r} />
        </clipPath>
        <radialGradient id={`${id}-eye`} cx="0.5" cy="0.5" r="0.5">
          <stop offset="0" stopColor="#000" stopOpacity={0.95} />
          <stop offset="0.25" stopColor="#000" stopOpacity={0.4} />
          <stop offset="1" stopColor="#000" stopOpacity={0} />
        </radialGradient>
      </defs>
      <g clipPath={`url(#${id}-clip)`}>
        <rect x={cx - r * 1.5} y={cy - r * 1.5} width={r * 3} height={r * 3} fill={`url(#${id}-chaos)`} transform={`rotate(${f * 2.2 * spin} ${cx} ${cy})`} />
        <circle cx={cx} cy={cy} r={r * 0.66} fill={`url(#${id}-chaos)`} transform={`rotate(${-f * 3.4 * spin} ${cx} ${cy})`} opacity={0.9} />
        {arms.map((d, i) => (
          <polyline key={i} points={d} fill="none" stroke="#ffffff" strokeWidth={2.5} opacity={0.4} />
        ))}
        <circle cx={cx} cy={cy} r={r} fill={`url(#${id}-eye)`} />
      </g>
      <circle cx={cx} cy={cy} r={r} fill="none" stroke="#ffffff" strokeWidth={2} opacity={0.55} />
    </>
  );
};

export const God: React.FC<FigProps> = ({x, y, h = 820, at = 0, crack = 0, fade = 0, flash = 0, lean = 0, glow = 0.6, cast = 0, id = 'gd'}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 30);
  if (p <= 0 || fade >= 1) return null;
  const s = h / 900;
  // 下摆是不断滴落的混沌液体。
  const drips = Array.from({length: 15}, (_, i) => {
    const dx = 120 + i * 26;
    const len = 24 + 36 * (0.5 + 0.5 * Math.sin(f * 0.07 + i * 1.7));
    return <path key={i} d={`M${dx - 11} 876 Q ${dx} ${876 + len * 1.7} ${dx + 11} 876 Z`} fill={`url(#${id}-chaos)`} />;
  });
  // 背后放射的彩色光芒，缓慢转动。
  const rays = Array.from({length: 28}, (_, i) => {
    const a = (i / 28) * Math.PI * 2 + f * 0.004;
    const len = 150 + (i % 2) * 70 + Math.sin(f * 0.05 + i) * 16;
    return <line key={i} x1={300 + Math.cos(a) * 104} y1={150 + Math.sin(a) * 104} x2={300 + Math.cos(a) * len} y2={150 + Math.sin(a) * len} stroke={`hsl(${(i * 37 + f * 2) % 360}, 90%, 70%)`} strokeWidth={3} opacity={0.55} />;
  });
  const armAngle = 28 - cast * 58;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible', opacity: p * (1 - fade), filter: fade > 0 ? `blur(${fade * 16}px)` : undefined}}>
      <defs>
        <pattern id={`${id}-chaos`} patternUnits="userSpaceOnUse" width={460} height={460} patternTransform={`translate(${-f * 0.8} ${f * 0.5})`}>
          <image href={staticFile('tex/chaos.png')} width={460} height={460} />
        </pattern>
        <linearGradient id={`${id}-dark`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#000" stopOpacity={0.45} />
          <stop offset="1" stopColor="#000" stopOpacity={0.8} />
        </linearGradient>
        <Wisp id={`${id}-wisp`} scale={18} freq={0.014} />
      </defs>
      <g transform={`translate(${x} ${y - fade * 60}) rotate(${lean}) scale(${s}) translate(-300 -880)`}>
        <g style={{filter: `hue-rotate(${(f * 1.2) % 360}deg)`}}>{rays}</g>
        <g style={{filter: `drop-shadow(0 0 ${12 + 30 * glow}px rgba(200,120,255,${0.3 + 0.45 * glow}))`}}>
        <g filter={`url(#${id}-wisp)`}>
          <g transform={`translate(212 268) rotate(${armAngle})`}>
            <path d={GD.armL} fill={`url(#${id}-chaos)`} stroke="#fff" strokeOpacity={0.35} strokeWidth={1.5} />
            <path d={GD.armL} fill="#000" opacity={0.55} />
          </g>
          <g transform={`translate(388 268) scale(-1 1) rotate(${armAngle})`}>
            <path d={GD.armL} fill={`url(#${id}-chaos)`} stroke="#fff" strokeOpacity={0.35} strokeWidth={1.5} />
            <path d={GD.armL} fill="#000" opacity={0.55} />
          </g>
          <path d={GD.body} fill={`url(#${id}-chaos)`} stroke="#fff" strokeOpacity={0.4} strokeWidth={2} />
          <path d={GD.body} fill={`url(#${id}-dark)`} />
          {drips}
          <path d="M236 252 Q 300 300 364 252" fill="none" stroke="#fff" strokeOpacity={0.35} strokeWidth={2} />
        </g>
          <Vortex cx={300} cy={150} r={100} id={id} />
        </g>
        <Cracks lines={cracks(47, 300, 260, 560, 200)} amount={crack} color="#ffffff" />
        {flash > 0 && <path d={GD.body} fill="#fff" opacity={flash} />}
      </g>
    </svg>
  );
};
