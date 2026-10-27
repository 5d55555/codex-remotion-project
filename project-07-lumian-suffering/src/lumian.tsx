import React from 'react';
import {useCurrentFrame} from 'remotion';
import {C} from './theme';

/*
 * Q 版卢米安：黑色短碎发、浅蓝眼睛（外貌依据见 notes）。
 * 原点是脸的中心，s=1 时脸宽约 200 像素。
 */
export type Face = 'smirk' | 'relief' | 'pain' | 'shock' | 'dead' | 'angry' | 'calm' | 'sad';

const INK = C.ink;

const Eye: React.FC<{x: number; mode: Face; side: -1 | 1; blink: boolean}> = ({x, mode, side, blink}) => {
  const y = 6;
  if (blink && (mode === 'calm' || mode === 'smirk' || mode === 'sad')) return <path d={`M${x - 22} ${y + 4} Q ${x} ${y + 12} ${x + 22} ${y + 4}`} stroke={INK} strokeWidth={5} fill="none" strokeLinecap="round" />;
  switch (mode) {
    case 'relief':
      return <path d={`M${x - 22} ${y + 6} Q ${x} ${y - 10} ${x + 22} ${y + 6}`} stroke={INK} strokeWidth={6} fill="none" strokeLinecap="round" />;
    case 'pain':
      return <path d={`M${x - 20 * side} ${y - 14} L ${x + 18 * side} ${y + 2} L ${x - 20 * side} ${y + 16}`} stroke={INK} strokeWidth={7} fill="none" strokeLinecap="round" strokeLinejoin="round" />;
    case 'shock':
      return (
        <g>
          <ellipse cx={x} cy={y} rx={22} ry={27} fill="#fff" stroke={INK} strokeWidth={5} />
          <circle cx={x} cy={y} r={5} fill={INK} />
        </g>
      );
    case 'dead':
      return (
        <g>
          <ellipse cx={x} cy={y} rx={22} ry={24} fill="#fff" stroke={INK} strokeWidth={5} />
          <path d={`M${x - 14} ${y - 4} L ${x + 14} ${y - 4}`} stroke={INK} strokeWidth={3} />
        </g>
      );
    default: {
      const lid = mode === 'smirk' ? 10 : mode === 'angry' ? 8 : 0;
      return (
        <g>
          <ellipse cx={x} cy={y} rx={22} ry={26} fill="#fff" stroke={INK} strokeWidth={5} />
          <ellipse cx={x + side * 2} cy={y + 4} rx={15} ry={19} fill={C.blue} />
          <ellipse cx={x + side * 2} cy={y + 6} rx={8} ry={11} fill={INK} />
          <circle cx={x - 5} cy={y - 4} r={5} fill="#fff" />
          {lid > 0 && <path d={`M${x - 26} ${y - 26} L ${x + 26} ${y - 26} L ${x + 26} ${y - 26 + lid * (mode === 'angry' ? 1.6 : 2.2)} Q ${x} ${y - 20 + lid * 2.2} ${x - 26} ${y - 26 + lid * (mode === 'angry' ? 2.8 : 2.2)} Z`} fill="#fff" stroke={INK} strokeWidth={4} transform={mode === 'angry' ? `rotate(${side * -12} ${x} ${y})` : undefined} />}
          {mode === 'sad' && <path d={`M${x + side * 10} ${y + 24} Q ${x + side * 14} ${y + 40} ${x + side * 8} ${y + 48}`} stroke={C.blue} strokeWidth={5} fill="none" strokeLinecap="round" />}
        </g>
      );
    }
  }
};

const Brow: React.FC<{x: number; mode: Face; side: -1 | 1}> = ({x, mode, side}) => {
  const y = -34;
  const tilt = {smirk: side === 1 ? -8 : 2, relief: 4, pain: 10, shock: -10, dead: 0, angry: 14, calm: 0, sad: -10}[mode];
  return <path d={`M${x - 20} ${y + tilt * side * -0.5} L ${x + 20} ${y - tilt * side * -0.5}`} stroke={INK} strokeWidth={6} strokeLinecap="round" transform={`rotate(${tilt * side} ${x} ${y})`} />;
};

const Mouth: React.FC<{mode: Face}> = ({mode}) => {
  const y = 58;
  switch (mode) {
    case 'smirk':
      return <path d={`M-18 ${y} Q 4 ${y + 10} 24 ${y - 8}`} stroke={INK} strokeWidth={5} fill="none" strokeLinecap="round" />;
    case 'relief':
      return <path d={`M-20 ${y} Q -10 ${y - 8} 0 ${y} Q 10 ${y + 8} 20 ${y}`} stroke={INK} strokeWidth={5} fill="none" strokeLinecap="round" />;
    case 'pain':
      return (
        <g>
          <rect x={-26} y={y - 12} width={52} height={22} rx={6} fill="#fff" stroke={INK} strokeWidth={5} />
          {[-13, 0, 13].map((dx) => (
            <line key={dx} x1={dx} y1={y - 12} x2={dx} y2={y + 10} stroke={INK} strokeWidth={3} />
          ))}
          <line x1={-26} y1={y - 1} x2={26} y2={y - 1} stroke={INK} strokeWidth={3} />
        </g>
      );
    case 'shock':
      return <ellipse cx={0} cy={y + 2} rx={14} ry={20} fill={INK} />;
    case 'dead':
      return <line x1={-16} y1={y} x2={16} y2={y} stroke={INK} strokeWidth={5} strokeLinecap="round" />;
    case 'angry':
      return <path d={`M-22 ${y + 6} L -8 ${y - 4} L 8 ${y - 4} L 22 ${y + 6}`} stroke={INK} strokeWidth={5} fill="#fff" strokeLinejoin="round" />;
    case 'sad':
      return <path d={`M-16 ${y + 6} Q 0 ${y - 6} 16 ${y + 6}`} stroke={INK} strokeWidth={5} fill="none" strokeLinecap="round" />;
    default:
      return <path d={`M-14 ${y} Q 0 ${y + 6} 14 ${y}`} stroke={INK} strokeWidth={5} fill="none" strokeLinecap="round" />;
  }
};

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
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
      <g transform={`translate(${x + jx} ${y + bob}) scale(${s}) rotate(${tilt})`}>
        {body && (
          <g>
            <path d="M-150 260 C -140 170 -90 130 -40 122 L 40 122 C 90 130 140 170 150 260 Z" fill={hood ? INK : '#2a2a30'} stroke={INK} strokeWidth={5} />
            <path d="M-40 122 L 0 190 L 40 122" fill="#fff" stroke={INK} strokeWidth={5} />
            <path d="M0 190 L 0 260" stroke={hood ? '#555' : INK} strokeWidth={4} />
          </g>
        )}
        {longHair && <path d="M-118 -20 C -150 80 -130 150 -96 180 L -70 100 Z M 118 -20 C 150 80 130 150 96 180 L 70 100 Z" fill={C.hair} />}
        {hood && <path d="M-150 120 C -170 -60 -110 -170 0 -172 C 110 -170 170 -60 150 120 C 120 70 100 20 92 -30 C 60 -110 -60 -110 -92 -30 C -100 20 -120 70 -150 120 Z" fill={INK} />}
        {/* 脸 */}
        <path d="M-100 -30 C -102 40 -70 104 0 112 C 70 104 102 40 100 -30 C 96 -96 -96 -96 -100 -30 Z" fill="#fff" stroke={INK} strokeWidth={6} />
        {/* 头发：尖碎的刘海与发顶 */}
        <path
          d="M-112 -6 C -126 -84 -86 -140 -20 -148 C 40 -156 104 -128 118 -54 C 122 -30 118 -10 112 6 L 96 -34 L 88 -6 L 70 -48 L 52 -14 L 36 -60 L 14 -18 L -6 -66 L -26 -18 L -46 -60 L -62 -16 L -78 -50 L -90 -4 L -100 -36 Z"
          fill={C.hair}
          stroke={INK}
          strokeWidth={4}
          strokeLinejoin="round"
        />
        <path d="M-40 -128 L -20 -168 L 0 -132 L 26 -170 L 44 -126" fill={C.hair} stroke={INK} strokeWidth={4} strokeLinejoin="round" />
        <path d="M-60 -110 Q -20 -130 30 -118" stroke="#555" strokeWidth={3} fill="none" />
        {gloom && (
          <g>
            {Array.from({length: 7}, (_, i) => (
              <line key={i} x1={-60 + i * 20} y1={-20} x2={-60 + i * 20} y2={-56 + (i % 2) * 10} stroke={INK} strokeWidth={4} />
            ))}
          </g>
        )}
        <Brow x={-42} mode={face} side={-1} />
        <Brow x={42} mode={face} side={1} />
        <Eye x={-42} mode={face} side={-1} blink={blink} />
        <Eye x={42} mode={face} side={1} blink={blink} />
        <Mouth mode={face} />
        {(face === 'pain' || face === 'shock') && (
          <g stroke={C.red} strokeWidth={4} strokeLinecap="round">
            <line x1={-70} y1={34} x2={-56} y2={30} />
            <line x1={-68} y1={44} x2={-54} y2={40} />
            <line x1={56} y1={30} x2={70} y2={34} />
            <line x1={54} y1={40} x2={68} y2={44} />
          </g>
        )}
        {bandage && (
          <g transform="rotate(-20 64 38)">
            <rect x={44} y={30} width={44} height={16} rx={6} fill="#fff4dc" stroke={INK} strokeWidth={3} />
            <line x1={60} y1={30} x2={60} y2={46} stroke={INK} strokeWidth={2} />
            <line x1={72} y1={30} x2={72} y2={46} stroke={INK} strokeWidth={2} />
          </g>
        )}
        {sweat && <path d="M104 -54 C 96 -36 98 -22 108 -18 C 118 -22 118 -36 104 -54 Z" fill="#dff3ff" stroke={INK} strokeWidth={3} />}
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
