import React from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import {prog} from './kit';
import {C, F, rnd} from './theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const svgBox: React.CSSProperties = {position: 'absolute', inset: 0, overflow: 'visible'};

/* 思考云：一串小圆连到一团云，里面是内心独白。 */
export const Thought: React.FC<{x: number; y: number; w: number; h: number; at: number; text: string; size?: number; from: {x: number; y: number}}> = ({x, y, w, h, at, text, size = 34, from}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10);
  if (p <= 0) return null;
  const n = Math.floor(interpolate(f - at, [4, 4 + text.length * 1.5], [0, text.length], clamp));
  const cx = x + w / 2;
  const cy = y + h / 2;
  const puffs = Array.from({length: 14}, (_, i) => {
    const a = (i / 14) * Math.PI * 2;
    return {px: cx + Math.cos(a) * (w / 2 - 20), py: cy + Math.sin(a) * (h / 2 - 16), r: 34 + (i % 3) * 8};
  });
  return (
    <>
      <svg width={1920} height={1080} style={{...svgBox, opacity: p}}>
        {[0.25, 0.5, 0.75].map((k, i) => (
          <circle key={i} cx={from.x + (cx - from.x) * k * 0.6} cy={from.y + (cy - from.y) * k * 0.6} r={8 + i * 6} fill="#fff" stroke={C.ink} strokeWidth={4} />
        ))}
        {puffs.map((q, i) => (
          <circle key={i} cx={q.px} cy={q.py} r={q.r} fill="#fff" stroke={C.ink} strokeWidth={5} />
        ))}
        <ellipse cx={cx} cy={cy} rx={w / 2 - 20} ry={h / 2 - 16} fill="#fff" />
      </svg>
      <div style={{position: 'absolute', left: x + 50, top: y + 30, width: w - 100, height: h - 60, display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', fontFamily: F.sans, fontWeight: 900, fontSize: size, color: C.ink, opacity: p}}>
        <span>
          {text.slice(0, n)}
          <span style={{opacity: 0}}>{text.slice(n)}</span>
        </span>
      </div>
    </>
  );
};

/* 闹钟：指针走到六点。 */
export const Clock: React.FC<{x: number; y: number; r?: number; at: number; label?: string}> = ({x, y, r = 70, at, label = '06:00'}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 30);
  if (f < at) return null;
  const hour = interpolate(p, [0, 1], [120, 180]);
  const minute = interpolate(p, [0, 1], [-720, 0]);
  const ring = p >= 1 && Math.floor(f / 3) % 2 === 0 ? 4 : 0;
  return (
    <svg width={1920} height={1080} style={svgBox}>
      <g transform={`translate(${x + (ring ? (rnd(f) - 0.5) * ring * 2 : 0)} ${y})`}>
        <circle cx={-r * 0.7} cy={-r * 0.85} r={r * 0.3} fill={C.ink} />
        <circle cx={r * 0.7} cy={-r * 0.85} r={r * 0.3} fill={C.ink} />
        <circle r={r} fill="#fff" stroke={C.ink} strokeWidth={7} />
        {Array.from({length: 12}, (_, i) => {
          const a = (i / 12) * Math.PI * 2;
          return <line key={i} x1={Math.sin(a) * r * 0.78} y1={-Math.cos(a) * r * 0.78} x2={Math.sin(a) * r * 0.9} y2={-Math.cos(a) * r * 0.9} stroke={C.ink} strokeWidth={i % 3 ? 3 : 6} />;
        })}
        <line x1={0} y1={0} x2={Math.sin((hour * Math.PI) / 180) * r * 0.5} y2={-Math.cos((hour * Math.PI) / 180) * r * 0.5} stroke={C.ink} strokeWidth={8} strokeLinecap="round" />
        <line x1={0} y1={0} x2={Math.sin((minute * Math.PI) / 180) * r * 0.75} y2={-Math.cos((minute * Math.PI) / 180) * r * 0.75} stroke={C.red} strokeWidth={5} strokeLinecap="round" />
        <circle r={7} fill={C.ink} />
      </g>
      {p >= 1 && (
        <text x={x} y={y + r + 50} textAnchor="middle" fontFamily={F.mono} fontWeight={500} fontSize={40} fill={C.ink}>
          {label}
        </text>
      )}
    </svg>
  );
};

/* 闪电：从上方劈下的银白折线（漫画里用黑描边）。 */
export const Bolt: React.FC<{x1: number; y1: number; x2: number; y2: number; at: number; dur?: number}> = ({x1, y1, x2, y2, at, dur = 14}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || t > dur) return null;
  const pts = Array.from({length: 9}, (_, i) => {
    const k = i / 8;
    const j = i === 0 || i === 8 ? 0 : (rnd(i + at) - 0.5) * 80;
    return `${x1 + (x2 - x1) * k + j},${y1 + (y2 - y1) * k}`;
  }).join(' ');
  return (
    <svg width={1920} height={1080} style={svgBox}>
      <polyline points={pts} fill="none" stroke={C.ink} strokeWidth={22} strokeLinejoin="miter" />
      <polyline points={pts} fill="none" stroke="#fff" strokeWidth={12} strokeLinejoin="miter" />
    </svg>
  );
};

/* 裂纹：从一点向外扩的红色裂缝。 */
export const Cracks: React.FC<{x: number; y: number; at: number; dur?: number; n?: number; len?: number}> = ({x, y, at, dur = 30, n = 10, len = 220}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, dur);
  if (p <= 0) return null;
  return (
    <svg width={1920} height={1080} style={svgBox}>
      {Array.from({length: n}, (_, i) => {
        const a = (i / n) * Math.PI * 2 + rnd(i) * 0.4;
        let px = x;
        let py = y;
        const pts = [`${px},${py}`];
        for (let k = 0; k < 5; k++) {
          px += Math.cos(a + (rnd(i * 9 + k) - 0.5) * 0.9) * (len / 5) * p;
          py += Math.sin(a + (rnd(i * 7 + k) - 0.5) * 0.9) * (len / 5) * p;
          pts.push(`${px},${py}`);
        }
        return <polyline key={i} points={pts.join(' ')} fill="none" stroke={C.red} strokeWidth={6 - i % 3} strokeLinejoin="round" />;
      })}
    </svg>
  );
};

/* 阿蒙的剪影：尖顶帽、单片眼镜反光。 */
export const AmonSilhouette: React.FC<{x: number; y: number; s?: number; at?: number}> = ({x, y, s = 1, at = 0}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 12);
  if (p <= 0) return null;
  const glint = Math.floor(f / 20) % 4 === 0;
  return (
    <svg width={1920} height={1080} style={{...svgBox, opacity: p}}>
      <g transform={`translate(${x} ${y}) scale(${s})`}>
        <path d="M-150 260 C -140 170 -90 130 -40 122 L 40 122 C 90 130 140 170 150 260 Z" fill={C.ink} />
        <ellipse cx={0} cy={0} rx={96} ry={112} fill={C.ink} />
        <path d="M-120 -70 L 120 -70 L 70 -100 L 30 -230 L -30 -100 L -70 -100 Z" fill={C.ink} />
        <circle cx={34} cy={4} r={26} fill="none" stroke="#fff" strokeWidth={5} />
        <line x1={60} y1={4} x2={84} y2={110} stroke="#fff" strokeWidth={2} />
        {glint && <path d="M24 -6 L 44 -6 L 34 14 Z" fill="#fff" />}
        <path d="M-30 60 Q 0 76 30 60" stroke="#fff" strokeWidth={4} fill="none" />
      </g>
    </svg>
  );
};

/* 左胸的封印：一圈发光的纹路。 */
export const SealMark: React.FC<{x: number; y: number; at: number; r?: number}> = ({x, y, at, r = 40}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 20);
  if (p <= 0) return null;
  return (
    <svg width={1920} height={1080} style={svgBox}>
      <g transform={`translate(${x} ${y}) rotate(${f * 2})`} opacity={p}>
        <circle r={r} fill="none" stroke={C.red} strokeWidth={5} />
        <circle r={r * 0.62} fill="none" stroke={C.red} strokeWidth={3} strokeDasharray="6 6" />
        {Array.from({length: 6}, (_, i) => {
          const a = (i / 6) * Math.PI * 2;
          return <line key={i} x1={Math.cos(a) * r * 0.62} y1={Math.sin(a) * r * 0.62} x2={Math.cos(a) * r} y2={Math.sin(a) * r} stroke={C.red} strokeWidth={3} />;
        })}
      </g>
    </svg>
  );
};

/* 狗皮：一条狗的剪影从中间裂开。 */
export const DogSkin: React.FC<{x: number; y: number; at: number; s?: number}> = ({x, y, at, s = 1}) => {
  const f = useCurrentFrame();
  const split = prog(f, at, 20);
  const dog = 'M-150 40 C -150 -30 -90 -60 -20 -60 L 90 -60 L 120 -110 L 140 -60 C 180 -50 200 -20 190 10 L 150 20 L 150 120 L 120 120 L 110 50 L -80 50 L -90 120 L -120 120 L -120 60 C -160 70 -190 40 -200 0 Z';
  return (
    <svg width={1920} height={1080} style={svgBox}>
      <g transform={`translate(${x} ${y}) scale(${s})`}>
        <g transform={`translate(${-split * 120} ${split * 40}) rotate(${-split * 18})`} opacity={1 - split * 0.4}>
          <clipPath id="dogL">
            <rect x={-260} y={-200} width={260} height={400} />
          </clipPath>
          <path d={dog} fill="#8a6a3e" stroke={C.ink} strokeWidth={5} clipPath="url(#dogL)" />
        </g>
        <g transform={`translate(${split * 120} ${split * 40}) rotate(${split * 18})`} opacity={1 - split * 0.4}>
          <clipPath id="dogR">
            <rect x={0} y={-200} width={260} height={400} />
          </clipPath>
          <path d={dog} fill="#8a6a3e" stroke={C.ink} strokeWidth={5} clipPath="url(#dogR)" />
        </g>
      </g>
    </svg>
  );
};

/* 肩上长出的另一颗脑袋：没有五官的小圆头。 */
export const ExtraHead: React.FC<{x: number; y: number; at: number; s?: number}> = ({x, y, at, s = 1}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 16);
  if (p <= 0) return null;
  const wob = Math.sin(f * 0.3) * 4;
  return (
    <svg width={1920} height={1080} style={svgBox}>
      <g transform={`translate(${x} ${y + (1 - p) * 60}) scale(${s * p}) rotate(${wob})`}>
        <ellipse cx={0} cy={0} rx={56} ry={64} fill="#fff" stroke={C.ink} strokeWidth={5} />
        <path d="M-56 -10 C -60 -60 60 -60 56 -10 C 40 -40 -40 -40 -56 -10 Z" fill={C.gray} />
        <circle cx={-18} cy={4} r={5} fill={C.ink} />
        <circle cx={18} cy={4} r={5} fill={C.ink} />
        <path d="M-12 30 Q 0 22 12 30" stroke={C.ink} strokeWidth={4} fill="none" />
      </g>
    </svg>
  );
};

/* 风暴：一圈圈卷起来的风线。 */
export const Storm: React.FC<{cx: number; cy: number; at: number; w?: number}> = ({cx, cy, at, w = 900}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 20);
  if (p <= 0) return null;
  return (
    <svg width={1920} height={1080} style={{...svgBox, opacity: p}}>
      {Array.from({length: 9}, (_, i) => {
        const pts = Array.from({length: 40}, (_, j) => {
          const t = j / 39;
          const a = i * 0.8 + t * 8 - f * 0.18;
          const r = 30 + t * w * 0.45;
          return `${cx + Math.cos(a) * r},${cy + Math.sin(a) * r * 0.4}`;
        }).join(' ');
        return <polyline key={i} points={pts} fill="none" stroke={C.ink} strokeWidth={3} opacity={0.6} />;
      })}
    </svg>
  );
};

/* 回忆小窗：带圆角虚线框的小格子，用来回放第一卷那一格。 */
export const Flashback: React.FC<{x: number; y: number; w: number; h: number; at: number; label?: string; children: React.ReactNode}> = ({x, y, w, h, at, label = '回忆', children}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 12);
  if (p <= 0) return null;
  return (
    <div style={{position: 'absolute', left: x, top: y, width: w, height: h, opacity: p, transform: `scale(${0.8 + 0.2 * p}) rotate(-3deg)`, border: `4px dashed ${C.ink}`, borderRadius: 18, background: '#ece8df', overflow: 'hidden'}}>
      <div style={{position: 'absolute', left: 0, top: 0, width: 1920, height: 1080, filter: 'grayscale(1) contrast(0.9)'}}>{children}</div>
      <div style={{position: 'absolute', left: 10, top: 6, fontFamily: F.sans, fontWeight: 900, fontSize: 18, background: C.ink, color: '#fff', padding: '0 8px'}}>{label}</div>
    </div>
  );
};

/* 报告纸：打字机字体的一页文件。 */
export const Report: React.FC<{x: number; y: number; w: number; h: number; at: number; title: string; lines: string[]; size?: number}> = ({x, y, w, h, at, title, lines, size = 26}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 12);
  if (p <= 0) return null;
  return (
    <div style={{position: 'absolute', left: x, top: y, width: w, height: h, opacity: p, transform: `rotate(${-2 + (1 - p) * 6}deg) translateY(${(1 - p) * 30}px)`, background: '#fff', border: `3px solid ${C.ink}`, boxShadow: '6px 8px 0 rgba(0,0,0,0.18)', padding: '20px 28px', boxSizing: 'border-box'}}>
      <div style={{fontFamily: F.sans, fontWeight: 900, fontSize: size + 4, color: C.ink, borderBottom: `3px solid ${C.ink}`, paddingBottom: 6, marginBottom: 12}}>{title}</div>
      {lines.map((l, i) => (
        <div key={i} style={{fontFamily: F.serif, fontWeight: 700, fontSize: size, color: C.ink, lineHeight: 1.5, opacity: prog(f, at + 10 + i * 12, 10)}}>
          {l}
        </div>
      ))}
    </div>
  );
};
