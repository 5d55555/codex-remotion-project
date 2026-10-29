import React from 'react';
import {AbsoluteFill, Img, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {prog} from './kit';
import {C, F, noise1, SCREEN} from './theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/* 回纹：戏台木雕上的一圈方折花边 */
const Fret: React.FC<{x: number; y: number; w: number; h: number}> = ({x, y, w, h}) => {
  const n = Math.floor(w / h);
  const u = h / 6;
  return (
    <g transform={`translate(${x} ${y})`} stroke={C.gold} strokeWidth={2} fill="none" opacity={0.65}>
      {Array.from({length: n}, (_, i) => (
        <path key={i} d={`M${i * h + u} ${5 * u} L ${i * h + u} ${u} L ${i * h + 5 * u} ${u} L ${i * h + 5 * u} ${4 * u} L ${i * h + 2.5 * u} ${4 * u} L ${i * h + 2.5 * u} ${2.5 * u} L ${i * h + 3.8 * u} ${2.5 * u}`} />
      ))}
    </g>
  );
};

/*
 * 戏台：上方匾额、木框里的幕布（油灯在幕后，火苗一跳一跳）、下方说书板。
 * light 是灯亮的程度（0 全黑，1 正常）；dark 让灯光转冷转暗（悲剧段落）。
 */
export const Stage: React.FC<{light?: number; dim?: number; children?: React.ReactNode; narr?: React.ReactNode; plaque?: string; warp?: number}> = ({light = 1, dim = 0, children, narr, plaque = '梦境占卜 · 第一纪秘史', warp = 0}) => {
  const f = useCurrentFrame();
  const flick = 0.9 + 0.07 * noise1(f * 0.21) + 0.03 * noise1(f * 0.83 + 9);
  const L = light * flick * (1 - dim * 0.45);
  const hx = 50 + (noise1(f * 0.05) - 0.5) * 6;
  const {x, y, w, h} = SCREEN;
  return (
    <AbsoluteFill style={{backgroundColor: C.night}}>
      {/* 匾额与木框 */}
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
        <defs>
          <linearGradient id="wood" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#4a1e10" />
            <stop offset="0.5" stopColor={C.wood} />
            <stop offset="1" stopColor="#24100a" />
          </linearGradient>
        </defs>
        <rect x={0} y={0} width={1920} height={50} fill="url(#wood)" />
        <Fret x={20} y={12} w={1880} h={26} />
        <rect x={760} y={4} width={400} height={44} rx={4} fill="#2a0f08" stroke={C.gold} strokeWidth={2.5} />
        <rect x={x - 22} y={y - 22} width={w + 44} height={h + 44} fill="url(#wood)" />
        <rect x={x - 22} y={y - 22} width={w + 44} height={h + 44} fill="none" stroke={C.woodHi} strokeWidth={3} />
        <rect x={x - 6} y={y - 6} width={w + 12} height={h + 12} fill="none" stroke={C.gold} strokeWidth={2.5} opacity={0.8} />
        {[
          [x - 22, y - 22],
          [x + w + 22, y - 22],
          [x - 22, y + h + 22],
          [x + w + 22, y + h + 22],
        ].map(([cx, cy], i) => (
          <circle key={i} cx={cx} cy={cy} r={12} fill={C.gold} stroke="#5a2a10" strokeWidth={3} />
        ))}
        <rect x={0} y={y + h + 26} width={1920} height={1080 - y - h - 26} fill="#1b0b06" />
        <rect x={60} y={y + h + 40} width={1800} height={1080 - y - h - 60} rx={6} fill="#22100a" stroke={C.gold} strokeWidth={2} opacity={0.95} />
      </svg>
      <div style={{position: 'absolute', left: 760, top: 4, width: 400, height: 44, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: F.narr, fontSize: 26, color: C.gold, letterSpacing: '0.18em'}}>{plaque}</div>
      {/* 幕布 */}
      <div style={{position: 'absolute', left: x, top: y, width: w, height: h, overflow: 'hidden', background: '#140904'}}>
        <div style={{position: 'absolute', inset: 0, opacity: L, background: `radial-gradient(ellipse 70% 80% at ${hx}% 62%, ${C.screenHot} 0%, ${C.screen} 42%, #dd9c50 72%, ${C.screenEdge} 100%)`}} />
        <Img src={staticFile('tex/screen.png')} style={{position: 'absolute', inset: 0, width: w, height: h, mixBlendMode: 'multiply', opacity: 0.55 * light}} />
        <div style={{position: 'absolute', inset: 0, filter: warp ? `url(#dreamwarp)` : undefined}}>{children}</div>
        {dim > 0 && <div style={{position: 'absolute', inset: 0, background: '#16304a', mixBlendMode: 'multiply', opacity: dim * 0.55}} />}
        <div style={{position: 'absolute', inset: 0, boxShadow: 'inset 0 0 120px 30px rgba(40,14,4,0.75)', opacity: Math.max(0.2, light)}} />
        <div style={{position: 'absolute', inset: 0, background: '#000', opacity: 1 - Math.min(1, light * 1.15)}} />
        {warp > 0 && (
          <svg width={0} height={0} style={{position: 'absolute'}}>
            <filter id="dreamwarp">
              <feTurbulence type="fractalNoise" baseFrequency={`0.004 ${0.012}`} numOctaves={2} seed={Math.floor(f / 2)} />
              <feDisplacementMap in="SourceGraphic" scale={warp * 60} xChannelSelector="R" yChannelSelector="G" />
            </filter>
          </svg>
        )}
      </div>
      {/* 说书板 */}
      <div style={{position: 'absolute', left: 60, top: y + h + 40, width: 1800, height: 1080 - y - h - 60, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>{narr}</div>
    </AbsoluteFill>
  );
};

/* 说书：按帧切换的一句句旁白，一本正经。 */
export type Line = [number, number, string];
export const Narr: React.FC<{lines: Line[]; size?: number}> = ({lines, size = 46}) => {
  const f = useCurrentFrame();
  const cur = lines.find(([a, b]) => f >= a && f < b);
  if (!cur) return null;
  const [a, b, text] = cur;
  const op = Math.min(prog(f, a, 8), 1 - prog(f, b - 6, 6));
  return (
    <div style={{fontFamily: F.narr, fontSize: size, color: C.cream, letterSpacing: '0.06em', opacity: op, transform: `translateY(${(1 - prog(f, a, 10)) * 10}px)`, textAlign: 'center', lineHeight: 1.3, textShadow: '0 2px 0 #000'}}>
      {text}
    </div>
  );
};

/* 证据印章：“正文”盖黑印，“梦”盖红印，旁边一行小字注明出处或说明。 */
export const Seal: React.FC<{at: number; kind: '梦' | '正文'; text: string; x: number; y: number; until?: number; w?: number}> = ({at, kind, text, x, y, until, w = 640}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || (until !== undefined && f >= until)) return null;
  const s = interpolate(t, [0, 4, 8], [1.9, 0.92, 1], clamp);
  const out = until !== undefined ? 1 - prog(f, until - 8, 8) : 1;
  const dream = kind === '梦';
  const bg = dream ? C.seal : '#2b1a10';
  return (
    <div style={{position: 'absolute', left: x, top: y, display: 'flex', alignItems: 'center', gap: 16, opacity: Math.min(1, t / 3) * out}}>
      <div style={{width: 64, height: 64, flex: 'none', transform: `scale(${s}) rotate(${dream ? -6 : 4}deg)`, background: bg, borderRadius: 6, boxShadow: `inset 0 0 0 4px ${bg}, inset 0 0 0 6px ${dream ? '#f3d9c0' : '#d9b97a'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: F.kai, fontSize: dream ? 46 : 30, lineHeight: 1, color: dream ? '#fbe8d6' : '#f0d49a', writingMode: dream ? undefined : 'vertical-rl'}}>
        {dream ? '梦' : '正文'}
      </div>
      <div style={{fontFamily: F.serif, fontWeight: 700, fontSize: 24, color: '#3a1a0a', maxWidth: w, lineHeight: 1.35, opacity: prog(f, at + 6, 8), background: 'rgba(255,240,205,0.55)', padding: '4px 12px', borderRadius: 4}}>{text}</div>
    </div>
  );
};

/* 毛笔大字：砸到幕布上 */
export const Big: React.FC<{at: number; text: string; x: number; y: number; size?: number; color?: string; rot?: number; until?: number; font?: string}> = ({at, text, x, y, size = 160, color = C.black, rot = -4, until, font = F.brush}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || (until !== undefined && f >= until)) return null;
  const s = interpolate(t, [0, 5, 10], [1.5, 0.95, 1], clamp);
  const out = until !== undefined ? 1 - prog(f, until - 8, 8) : 1;
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%, -50%) scale(${s}) rotate(${rot}deg)`, fontFamily: font, fontSize: size, color, opacity: Math.min(1, t / 3) * out, whiteSpace: 'nowrap', mixBlendMode: 'multiply', lineHeight: 1}}>
      {text}
    </div>
  );
};

/* 戏牌：每一折开场，从幕布上沿垂下一块红牌子 */
export const ActCard: React.FC<{at: number; no: string; title: string; until: number; x?: number}> = ({at, no, title, until, x = 870}) => {
  const f = useCurrentFrame();
  if (f < at || f >= until) return null;
  const drop = interpolate(f - at, [0, 14, 20, 26], [-420, 18, -6, 0], clamp);
  const up = prog(f, until - 14, 14) * -480;
  const sw = Math.sin((f - at) * 0.12) * 2 * Math.exp(-(f - at) / 40);
  return (
    <div style={{position: 'absolute', left: x - 110, top: drop + up, width: 220, transformOrigin: '50% 0', transform: `rotate(${sw}deg)`}}>
      <svg width={220} height={60} style={{display: 'block'}}>
        <line x1={50} y1={0} x2={50} y2={60} stroke={C.black} strokeWidth={4} />
        <line x1={170} y1={0} x2={170} y2={60} stroke={C.black} strokeWidth={4} />
      </svg>
      <div style={{background: C.red, border: `6px solid ${C.black}`, boxShadow: `inset 0 0 0 4px ${C.gold}`, padding: '18px 0 26px', display: 'flex', flexDirection: 'column', alignItems: 'center', mixBlendMode: 'multiply'}}>
        <div style={{fontFamily: F.kai, fontSize: 34, color: '#fff1d6'}}>{no}</div>
        <div style={{fontFamily: F.brush, fontSize: 96, color: '#fff1d6', writingMode: 'vertical-rl', lineHeight: 1.05, marginTop: 6}}>{title}</div>
      </div>
    </div>
  );
};
