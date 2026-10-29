import React from 'react';
import {useCurrentFrame} from 'remotion';
import {Hide, hole} from './puppet';
import {C, F, rnd, SCREEN} from './theme';

/* 道具也是刻花的皮子，用正片叠底贴在幕布上。坐标都是幕布内的坐标。 */
export const Leather: React.FC<{children: React.ReactNode; opacity?: number}> = ({children, opacity = 1}) => (
  <svg width={SCREEN.w} height={SCREEN.h} style={{position: 'absolute', inset: 0, overflow: 'visible', mixBlendMode: 'multiply', opacity}}>
    {children}
  </svg>
);

const circ = (r: number) => `M${-r} 0 a ${r} ${r} 0 1 0 ${2 * r} 0 a ${r} ${r} 0 1 0 ${-2 * r} 0 Z`;

/* ---------- 源质圆章 ---------- */
export type SourceKind = '源堡' | '混沌海' | '永暗之河' | '失序之国' | '母巢' | '暗影世界' | '知识荒野' | '光之钥' | '灾祸之城' | '？';
const SRC_COLOR: Record<SourceKind, string> = {
  源堡: '#8d8a86',
  混沌海: C.blue,
  永暗之河: '#2c3550',
  失序之国: C.red,
  母巢: C.green,
  暗影世界: '#55406a',
  知识荒野: C.yellow,
  光之钥: '#f0c040',
  灾祸之城: '#d0582a',
  '？': '#8a7a66',
};
const icon = (k: SourceKind): string[] => {
  switch (k) {
    case '源堡':
      return ['M-26 18 L -26 -6 L -18 -6 L -18 -18 L -10 -18 L -10 -6 L -4 -6 L -4 -26 L 4 -34 L 12 -26 L 12 -6 L 18 -6 L 18 -18 L 26 -18 L 26 18 Z', hole.cloud(-18, 30, 9), hole.cloud(16, 32, 8)];
    case '混沌海':
      return [hole.wave(-14, -12, 16), hole.wave(14, 8, 16), hole.wave(-12, 28, 12)];
    case '永暗之河':
      return ['M-40 6 C -20 -12 0 22 20 2 C 30 -8 36 -6 40 -2 L 40 10 C 30 6 26 6 20 14 C 0 34 -20 0 -40 18 Z', hole.diamond(-16, -24, 6), hole.diamond(12, -28, 4), hole.diamond(24, -16, 3)];
    case '失序之国':
      return ['M-28 16 L -32 -18 L -16 -2 L 0 -26 L 16 -2 L 32 -18 L 28 16 Z', hole.rect(-28, 22, 56, 8)];
    case '母巢':
      return [hole.leaf(0, -14, 22, 90), hole.leaf(-16, 6, 18, 40), hole.leaf(16, 6, 18, 140), hole.dot(0, 24, 7)];
    case '暗影世界':
      return ['M-30 -10 C -30 -30 30 -30 30 -10 C 30 20 10 30 0 30 C -10 30 -30 20 -30 -10 Z M-18 -8 L -6 -2 L -18 2 Z M18 -8 L 6 -2 L 18 2 Z'];
    case '知识荒野':
      return ['M-34 -4 C -20 -14 -8 -12 0 -4 C 8 -12 20 -14 34 -4 L 34 20 C 20 12 8 14 0 20 C -8 14 -20 12 -34 20 Z', 'M-24 26 L -20 34 L -16 26 Z', 'M-4 28 L 0 38 L 4 28 Z', 'M16 26 L 20 34 L 24 26 Z'];
    case '光之钥':
      return [hole.dot(-18, -4, 14), 'M-6 -8 L 34 -8 L 34 0 L 28 0 L 28 10 L 20 10 L 20 0 L 14 0 L 14 8 L 6 8 L 6 0 L -6 0 Z'];
    case '灾祸之城':
      return ['M-34 20 L -34 -2 L -24 -2 L -24 -14 L -14 -14 L -14 4 L -4 4 L -4 -24 L 8 -24 L 8 0 L 18 0 L 18 -10 L 28 -10 L 28 20 Z', hole.leaf(-20, -30, 9, 90), hole.leaf(2, -38, 10, 90), hole.leaf(24, -24, 8, 90)];
    default:
      return ['M-12 -14 C -12 -30 14 -30 14 -14 C 14 -4 4 -2 4 8 L -4 8 C -4 -6 6 -8 6 -14 C 6 -22 -6 -22 -6 -14 Z', hole.dot(0, 20, 5)];
  }
};

export const Source: React.FC<{kind: SourceKind; x: number; y: number; r?: number; label?: boolean; spin?: number; glow?: number}> = ({kind, x, y, r = 56, label = true, spin = 0, glow = 0}) => {
  const k = r / 56;
  const ic = icon(kind);
  return (
    <g transform={`translate(${x} ${y})`}>
      {glow > 0 && <circle r={r * 1.35} fill={SRC_COLOR[kind]} opacity={0.18 * glow} style={{filter: 'blur(10px)'}} />}
      <g transform={`rotate(${spin}) scale(${k})`}>
        <Hide d={circ(56)} holes={[circ(46), ...Array.from({length: 12}, (_, i) => hole.dot(Math.cos((i * 30 * Math.PI) / 180) * 51, Math.sin((i * 30 * Math.PI) / 180) * 51, 2.4))]} fill={SRC_COLOR[kind]} />
        <Hide d={circ(44)} holes={kind === '暗影世界' || kind === '失序之国' || kind === '源堡' || kind === '永暗之河' || kind === '灾祸之城' || kind === '知识荒野' || kind === '光之钥' || kind === '？' ? [] : ic} fill={SRC_COLOR[kind]} opacity={0.7} />
        {(kind === '暗影世界' || kind === '失序之国' || kind === '源堡' || kind === '永暗之河' || kind === '灾祸之城' || kind === '知识荒野' || kind === '光之钥' || kind === '？') && <path d={ic.join(' ')} fillRule="evenodd" fill={C.black} fillOpacity={0.9} />}
      </g>
      {label && (
        <text y={r + 40} textAnchor="middle" fontFamily={F.kai} fontSize={30 * Math.max(0.8, k)} fill={C.black}>
          {kind}
        </text>
      )}
    </g>
  );
};

/* ---------- 扁担与竹篮 ---------- */
export const Basket: React.FC<{x: number; y: number; s?: number}> = ({x, y, s = 1}) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <path d="M-50 -80 L 0 -150 L 50 -80" fill="none" stroke={C.edge} strokeWidth={2.5} />
    <Hide d="M-58 -80 L 58 -80 L 46 0 L -46 0 Z" holes={[0, 1, 2, 3].flatMap((i) => [0, 1, 2].map((j) => hole.diamond(-36 + i * 24, -60 + j * 22, 7)))} fill="#b07a3a" />
    <path d="M-62 -84 L 62 -84" stroke={C.edge} strokeWidth={6} strokeLinecap="round" />
  </g>
);

/* ---------- 绿帽子 ---------- */
export const GreenHat: React.FC<{x: number; y: number; s?: number; rot?: number}> = ({x, y, s = 1, rot = 0}) => (
  <g transform={`translate(${x} ${y}) scale(${s}) rotate(${rot})`}>
    <Hide d="M-46 10 C -48 -20 -30 -46 0 -48 C 30 -46 48 -20 46 10 Z" holes={[hole.leaf(-14, -22, 10, 60), hole.leaf(14, -22, 10, 120), hole.dot(0, -34, 4)]} fill={C.green} />
    <Hide d="M-70 6 C -40 0 40 0 70 6 C 72 16 60 22 50 20 C 20 14 -20 14 -50 20 C -60 22 -72 16 -70 6 Z" fill={C.green} />
  </g>
);

/* ---------- 囍、红烛 ---------- */
export const Xi: React.FC<{x: number; y: number; s?: number}> = ({x, y, s = 1}) => (
  <g transform={`translate(${x} ${y}) scale(${s}) rotate(45)`}>
    <Hide d="M-90 -90 L 90 -90 L 90 90 L -90 90 Z" holes={[hole.dot(-74, -74, 6), hole.dot(74, -74, 6), hole.dot(-74, 74, 6), hole.dot(74, 74, 6)]} fill={C.red} />
    <text transform="rotate(-45)" y={42} textAnchor="middle" fontFamily={F.kai} fontSize={120} fill="#fff3d8">
      囍
    </text>
  </g>
);
export const Candle: React.FC<{x: number; y: number; s?: number}> = ({x, y, s = 1}) => {
  const f = useCurrentFrame();
  const fl = Math.sin(f * 0.5) * 2 + Math.sin(f * 1.3) * 1.2;
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <ellipse cx={0} cy={-128} rx={18} ry={26} fill="#ffd27a" opacity={0.35} style={{filter: 'blur(6px)'}} />
      <path d={`M0 ${-150 + fl} C 10 -132 10 -118 0 -112 C -10 -118 -10 -132 0 ${-150 + fl} Z`} fill="#f4b03c" stroke={C.edge} strokeWidth={1.6} />
      <Hide d="M-16 -110 L 16 -110 L 16 0 L -16 0 Z" holes={[hole.leaf(0, -70, 12, 90), hole.dot(0, -36, 4)]} fill={C.red} />
      <Hide d="M-30 0 L 30 0 L 22 20 L -22 20 Z" fill="#c98d22" />
    </g>
  );
};

/* ---------- 打戏：闪电、浪、灰雾、心 ---------- */
export const Bolt: React.FC<{x: number; y: number; s?: number; rot?: number}> = ({x, y, s = 1, rot = 0}) => (
  <g transform={`translate(${x} ${y}) scale(${s}) rotate(${rot})`}>
    <Hide d="M-10 -120 L 34 -120 L 6 -36 L 40 -36 L -30 110 L -6 6 L -40 6 Z" holes={[hole.leaf(10, -80, 10, 110), hole.leaf(-8, 30, 10, 110)]} fill="#f0c040" />
  </g>
);
export const WaveBand: React.FC<{y: number; amp?: number; color?: string; phase?: number}> = ({y, amp = 1, color = C.blue, phase = 0}) => {
  const f = useCurrentFrame();
  const t = f * 0.05 + phase;
  const pts = Array.from({length: 23}, (_, i) => {
    const x = i * 80;
    const yy = y + Math.sin(i * 0.9 + t) * 14 * amp;
    return `${x} ${yy.toFixed(1)}`;
  });
  return (
    <Hide
      d={`M-40 ${SCREEN.h + 20} L -40 ${y} L ${pts.join(' L ')} L ${SCREEN.w + 40} ${y} L ${SCREEN.w + 40} ${SCREEN.h + 20} Z`}
      holes={Array.from({length: 11}, (_, i) => hole.wave(60 + i * 160 + Math.sin(t + i) * 10, y + 60, 26))}
      fill={color}
    />
  );
};
export const Fog: React.FC<{x: number; y: number; s?: number; op?: number}> = ({x, y, s = 1, op = 0.75}) => {
  const f = useCurrentFrame();
  return (
    <g transform={`translate(${x + Math.sin(f * 0.03) * 20} ${y}) scale(${s})`} opacity={op}>
      {[0, 1, 2, 3, 4].map((i) => (
        <path key={i} d={hole.cloud((i - 2) * 90, Math.sin(i * 1.7) * 30, 60 + (i % 2) * 20)} fill="#8d8a86" fillOpacity={0.6} stroke={C.edge} strokeWidth={2} />
      ))}
    </g>
  );
};
export const Heart: React.FC<{x: number; y: number; s?: number; rot?: number}> = ({x, y, s = 1, rot = 0}) => (
  <g transform={`translate(${x} ${y}) scale(${s}) rotate(${rot})`}>
    <Hide d="M0 30 C -40 4 -46 -28 -22 -34 C -10 -36 -2 -28 0 -20 C 2 -28 10 -36 22 -34 C 46 -28 40 4 0 30 Z" holes={[hole.leaf(-14, -16, 7, 40)]} fill={C.red} />
  </g>
);

/* ---------- 亵渎石板 ---------- */
export const Slab: React.FC<{x: number; y: number; s?: number; text?: boolean}> = ({x, y, s = 1, text}) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <Hide d="M-80 0 L -76 -200 C -60 -230 60 -230 76 -200 L 80 0 Z" holes={Array.from({length: 7}, (_, i) => hole.rect(-52, -180 + i * 24, 104 - (i % 3) * 16, 5))} fill="#7d7268" />
    {text && (
      <text x={0} y={-238} textAnchor="middle" fontFamily={F.kai} fontSize={32} fill={C.red}>
        婚书
      </text>
    )}
  </g>
);

/* ---------- 西大陆、封印 ---------- */
export const Continent: React.FC<{x: number; y: number; s?: number}> = ({x, y, s = 1}) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <Hide
      d="M-220 40 C -230 -20 -190 -70 -130 -80 C -100 -120 -40 -130 0 -110 C 40 -140 120 -130 150 -90 C 210 -80 240 -30 220 20 C 210 70 150 90 90 80 C 40 110 -40 110 -90 84 C -150 100 -210 90 -220 40 Z"
      holes={[hole.cloud(-110, -20, 30), hole.cloud(60, -40, 34), hole.cloud(130, 30, 24), hole.cloud(-30, 50, 26)]}
      fill="#a08050"
    />
    <path d="M-60 -60 L -30 -120 L 0 -70 L 20 -110 L 50 -60 Z" fill={C.green} fillOpacity={0.85} stroke={C.edge} strokeWidth={2.4} strokeLinejoin="round" />
  </g>
);

/* ---------- 蝴蝶（化蝶） ---------- */
export const Butterfly: React.FC<{x: number; y: number; s?: number; color?: string; phase?: number}> = ({x, y, s = 1, color = C.yellow, phase = 0}) => {
  const f = useCurrentFrame();
  const flap = 0.35 + 0.65 * Math.abs(Math.sin(f * 0.32 + phase));
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      {[-1, 1].map((sd) => (
        <g key={sd} transform={`scale(${sd * flap} 1)`}>
          <Hide d="M0 -4 C 20 -50 70 -56 74 -26 C 76 -4 40 8 0 4 Z" holes={[hole.dot(46, -28, 9), hole.leaf(24, -14, 10, 20)]} fill={color} />
          <Hide d="M0 4 C 30 10 56 24 48 50 C 40 70 14 52 0 12 Z" holes={[hole.dot(28, 32, 6)]} fill={color} />
        </g>
      ))}
      <path d="M0 -14 L 0 40" stroke={C.edge} strokeWidth={5} strokeLinecap="round" />
      <path d="M0 -14 C -6 -30 -14 -36 -20 -38 M 0 -14 C 6 -30 14 -36 20 -38" stroke={C.edge} strokeWidth={2} fill="none" />
    </g>
  );
};

/* ---------- 水仙花 ---------- */
export const Narcissus: React.FC<{x: number; y: number; s?: number}> = ({x, y, s = 1}) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <path d="M0 0 C 4 60 -6 120 0 200" stroke={C.green} strokeWidth={8} fill="none" />
    <Hide d="M2 200 C -40 120 -46 60 -34 20 C -20 60 -10 120 2 200 Z" fill={C.green} />
    <Hide d="M2 200 C 40 130 50 80 40 40 C 26 80 14 130 2 200 Z" fill={C.green} />
    {Array.from({length: 6}, (_, i) => (
      <path key={i} transform={`rotate(${i * 60})`} d="M0 0 C 16 -14 18 -44 0 -60 C -18 -44 -16 -14 0 0 Z" fill="#fff6dc" fillOpacity={0.6} stroke={C.edge} strokeWidth={2.4} />
    ))}
    <Hide d={circ(18)} holes={[circ(10)]} fill={C.yellow} />
  </g>
);

/* ---------- 楔子：油灯、书案、纸、梦泡 ---------- */
export const Desk: React.FC<{x: number; y: number}> = ({x, y}) => (
  <g transform={`translate(${x} ${y})`}>
    <Hide d="M-150 0 L 150 0 L 150 20 L -150 20 Z" holes={[hole.rect(-130, 6, 60, 6), hole.rect(70, 6, 60, 6)]} fill="#8a4a22" />
    <Hide d="M-136 20 L -120 20 L -116 150 L -132 150 Z" fill="#8a4a22" />
    <Hide d="M120 20 L 136 20 L 132 150 L 116 150 Z" fill="#8a4a22" />
  </g>
);
export const OilLamp: React.FC<{x: number; y: number; lit?: number}> = ({x, y, lit = 1}) => {
  const f = useCurrentFrame();
  const fl = Math.sin(f * 0.6) * 2 + Math.sin(f * 1.7) * 1;
  return (
    <g transform={`translate(${x} ${y})`}>
      {lit > 0 && (
        <g opacity={lit}>
          <ellipse cx={0} cy={-44} rx={30} ry={40} fill="#ffd27a" opacity={0.4} style={{filter: 'blur(8px)'}} />
          <path d={`M0 ${-74 + fl} C 12 -56 10 -40 0 -34 C -10 -40 -12 -56 0 ${-74 + fl} Z`} fill="#f4b03c" stroke={C.edge} strokeWidth={1.6} />
        </g>
      )}
      <Hide d="M-40 -30 C -40 -8 40 -8 40 -30 Z" holes={[hole.dot(-18, -20, 4), hole.dot(0, -18, 4), hole.dot(18, -20, 4)]} fill="#b0782e" />
      <Hide d="M-8 -14 L 8 -14 L 12 30 L -12 30 Z" fill="#b0782e" />
      <Hide d="M-30 30 L 30 30 L 24 40 L -24 40 Z" fill="#b0782e" />
    </g>
  );
};
export const Zzz: React.FC<{x: number; y: number; at: number}> = ({x, y, at}) => {
  const f = useCurrentFrame();
  if (f < at) return null;
  return (
    <g>
      {[0, 1, 2].map((i) => {
        const t = ((f - at - i * 18) % 60) / 60;
        if (f - at < i * 18) return null;
        return (
          <text key={i} x={x + t * 60 + i * 10} y={y - t * 120} fontFamily={F.brush} fontSize={40 + i * 14} fill={C.black} opacity={Math.sin(t * Math.PI)}>
            Z
          </text>
        );
      })}
    </g>
  );
};

/* 剪纸气泡：角色说话 */
export const Say: React.FC<{x: number; y: number; at: number; until?: number; text: string; size?: number; tail?: 'l' | 'r'}> = ({x, y, at, until, text, size = 40, tail = 'l'}) => {
  const f = useCurrentFrame();
  if (f < at || (until !== undefined && f >= until)) return null;
  const p = Math.min(1, (f - at) / 6);
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%, -50%) scale(${0.6 + 0.4 * p})`, opacity: p, mixBlendMode: 'multiply'}}>
      <div style={{position: 'relative', background: '#fff4dc', border: `4px solid ${C.edge}`, borderRadius: 40, padding: '10px 30px', fontFamily: F.kai, fontSize: size, color: C.black, whiteSpace: 'nowrap'}}>
        {text}
        <div style={{position: 'absolute', bottom: -22, [tail === 'l' ? 'left' : 'right']: 30, width: 0, height: 0, borderLeft: '14px solid transparent', borderRight: '14px solid transparent', borderTop: `22px solid ${C.edge}`}} />
      </div>
    </div>
  );
};

/* 眼泪：一滴滴往下落 */
export const Tears: React.FC<{x: number; y: number; at: number}> = ({x, y, at}) => {
  const f = useCurrentFrame();
  if (f < at) return null;
  return (
    <g>
      {[0, 1, 2, 3].map((i) => {
        const t = ((f - at + i * 11) % 44) / 44;
        return <path key={i} transform={`translate(${x + (rnd(i) - 0.5) * 16} ${y + t * 120})`} d="M0 -12 C 6 -2 8 4 0 10 C -8 4 -6 -2 0 -12 Z" fill={C.blue} fillOpacity={0.8} stroke={C.edge} strokeWidth={1.4} opacity={1 - t} />;
      })}
    </g>
  );
};
