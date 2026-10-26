import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {Fog} from './bg';
import {prog} from './kit';
import {C, F, rnd} from './theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
type Pt = {x: number; y: number};

/* ---------- 基础：震屏、闪白、冲击波 ---------- */

export const Shake: React.FC<{hits: [number, number][]; dur?: number; children: React.ReactNode}> = ({hits, dur = 16, children}) => {
  const f = useCurrentFrame();
  const k = hits.reduce((v, [at, amp]) => {
    const t = f - at;
    return t >= 0 && t < dur ? Math.max(v, (1 - t / dur) * amp) : v;
  }, 0);
  return <AbsoluteFill style={{transform: `translate(${(rnd(f) - 0.5) * 2 * k}px, ${(rnd(f + 99) - 0.5) * 2 * k}px)`}}>{children}</AbsoluteFill>;
};

export const Flash: React.FC<{at: number; dur?: number; color?: string; peak?: number}> = ({at, dur = 10, color = '#ffffff', peak = 0.85}) => {
  const f = useCurrentFrame();
  const o = interpolate(f - at, [0, dur], [peak, 0], clamp);
  return f >= at && o > 0 ? <AbsoluteFill style={{backgroundColor: color, opacity: o, mixBlendMode: 'screen'}} /> : null;
};

export const Shock: React.FC<Pt & {at: number; color?: string; r?: number; dur?: number}> = ({x, y, at, color = '#fff', r = 420, dur = 22}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || t > dur) return null;
  const k = t / dur;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      <circle cx={x} cy={y} r={r * (1 - (1 - k) ** 3)} fill="none" stroke={color} strokeWidth={14 * (1 - k) + 1} opacity={1 - k} style={{filter: `drop-shadow(0 0 16px ${color})`}} />
      <circle cx={x} cy={y} r={r * 0.6 * (1 - (1 - k) ** 2)} fill="none" stroke="#fff" strokeWidth={4 * (1 - k)} opacity={1 - k} />
      {Array.from({length: 14}, (_, i) => {
        const a = (i / 14) * Math.PI * 2 + rnd(at + i);
        const r0 = r * 0.2 * k;
        const r1 = r * (0.35 + 0.5 * rnd(at + i * 3)) * k;
        return <line key={i} x1={x + Math.cos(a) * r0} y1={y + Math.sin(a) * r0} x2={x + Math.cos(a) * r1} y2={y + Math.sin(a) * r1} stroke={color} strokeWidth={3} opacity={1 - k} />;
      })}
    </svg>
  );
};

/* ---------- 权柄字形：出现、蓄势、飞出、命中 ---------- */

export const Glyph: React.FC<{
  char: string;
  from: Pt;
  to?: Pt;
  at: number;
  hold?: number;
  fly?: number;
  color: string;
  size?: number;
  font?: string;
}> = ({char, from, to, at, hold = 18, fly = 14, color, size = 220, font = F.serif}) => {
  const f = useCurrentFrame();
  const t = f - at;
  const end = to ? hold + fly : hold + 16;
  if (t < 0 || t > end) return null;
  const appear = interpolate(t, [0, 8], [2.4, 1], {...clamp, easing: (v) => 1 - (1 - v) ** 3});
  const blur = interpolate(t, [0, 8], [16, 0], clamp);
  let x = from.x;
  let y = from.y;
  let o = interpolate(t, [0, 5], [0, 1], clamp);
  let rot = 0;
  const trail: React.ReactNode[] = [];
  if (to && t > hold) {
    const k = Math.min(1, (t - hold) / fly);
    const e = k * k;
    x = from.x + (to.x - from.x) * e;
    y = from.y + (to.y - from.y) * e - Math.sin(Math.PI * k) * 60;
    rot = (to.x > from.x ? 1 : -1) * k * 25;
    for (let j = 1; j <= 4; j++) {
      const kk = Math.max(0, k - j * 0.08);
      const ee = kk * kk;
      trail.push(
        <div
          key={j}
          style={{position: 'absolute', left: from.x + (to.x - from.x) * ee, top: from.y + (to.y - from.y) * ee - Math.sin(Math.PI * kk) * 60, transform: 'translate(-50%, -50%)', fontFamily: font, fontWeight: 900, fontSize: size, color, opacity: 0.18 * (5 - j) / 4, lineHeight: 1}}
        >
          {char}
        </div>,
      );
    }
  }
  if (!to && t > hold) o *= 1 - (t - hold) / 16;
  return (
    <>
      {trail}
      <div
        style={{
          position: 'absolute',
          left: x,
          top: y,
          transform: `translate(-50%, -50%) scale(${appear}) rotate(${rot}deg)`,
          filter: `blur(${blur}px)`,
          opacity: o,
          fontFamily: font,
          fontWeight: 900,
          fontSize: size,
          lineHeight: 1,
          color: '#fff',
          textShadow: `0 0 20px ${color}, 0 0 50px ${color}, 0 0 90px ${color}`,
        }}
      >
        {char}
      </div>
    </>
  );
};

/* 出招标签：权柄名 + 途径 + 序列0 + 领域。 */
export const Invoke: React.FC<{side: 'L' | 'R'; at: number; until: number; name: string; path: string; note?: string; color: string}> = ({side, at, until, name, path, note, color}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10) * (1 - prog(f, until - 10, 10));
  if (p <= 0) return null;
  const L = side === 'L';
  return (
    <div
      style={{
        position: 'absolute',
        top: 168,
        [L ? 'left' : 'right']: 70,
        opacity: p,
        transform: `translateX(${(L ? -1 : 1) * (1 - p) * 30}px)`,
        textAlign: L ? 'left' : 'right',
        borderLeft: L ? `4px solid ${color}` : undefined,
        borderRight: L ? undefined : `4px solid ${color}`,
        padding: '6px 16px',
        background: `linear-gradient(${L ? 90 : 270}deg, rgba(0,0,0,0.6), transparent)`,
      }}
    >
      <div style={{fontFamily: F.serif, fontWeight: 900, fontSize: 40, color: '#fff', textShadow: `0 0 16px ${color}`}}>{name}</div>
      <div style={{fontFamily: F.sans, fontWeight: 500, fontSize: 19, color: C.ink, opacity: 0.85, marginTop: 2}}>{path}</div>
      {note && <div style={{fontFamily: F.sans, fontWeight: 700, fontSize: 16, color, marginTop: 4}}>{note}</div>}
    </div>
  );
};

/* ---------- 源质球 ---------- */

export const Orb: React.FC<Pt & {r?: number; label?: string; kind: 'yuanbao' | 'river' | 'disorder' | 'chaos' | 'calamity' | 'plain'; at?: number; flare?: number; dashed?: boolean; color?: string; labelSide?: 'b' | 't'}> = ({
  x,
  y,
  r = 34,
  label,
  kind,
  at = 0,
  flare = 0,
  dashed,
  color,
  labelSide = 'b',
}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 14);
  if (p <= 0) return null;
  const fill: Record<string, string> = {
    yuanbao: `radial-gradient(circle at 35% 30%, #fff3d0, ${C.bronze} 40%, ${C.bronzeDeep} 100%)`,
    river: 'radial-gradient(circle at 35% 30%, #6c7aa8, #121a30 55%, #000 100%)',
    disorder: `radial-gradient(circle at 35% 30%, #ff8fa8, ${C.disorder} 50%, #2a0610 100%)`,
    chaos: `conic-gradient(from ${f * 4}deg, #ff5f5f, #ffd25f, #6dff8a, #5fd2ff, #9d6bff, #ff5fd2, #ff5f5f)`,
    calamity: `radial-gradient(circle at 35% 30%, #ffd2a0, ${C.calamity} 50%, #3a0c00 100%)`,
    plain: `radial-gradient(circle at 35% 30%, #fff, ${color ?? '#aaa'} 55%, #111 100%)`,
  };
  const glow = color ?? {yuanbao: C.bronze, river: '#7f95ff', disorder: '#ff4f7a', chaos: '#c78bff', calamity: C.calamity, plain: '#fff'}[kind];
  const s = p * (1 + flare * 0.5);
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%, -50%) scale(${s})`, opacity: p}}>
      <div
        style={{
          width: r * 2,
          height: r * 2,
          borderRadius: '50%',
          background: fill[kind],
          boxShadow: `0 0 ${16 + flare * 50}px ${glow}, 0 0 ${4 + flare * 20}px #fff`,
          border: dashed ? `2px dashed ${glow}` : undefined,
          opacity: dashed ? 0.8 : 1,
        }}
      />
      {label && (
        <div style={{position: 'absolute', left: '50%', [labelSide === 'b' ? 'top' : 'bottom']: r * 2 + 8, transform: 'translateX(-50%)', whiteSpace: 'nowrap', fontFamily: F.sans, fontWeight: 700, fontSize: 18, color: C.ink, textShadow: '0 0 6px #000'}}>
          {label}
        </div>
      )}
    </div>
  );
};

/* ---------- 门：一圈门框打开，吞进去，再从另一处吐出来 ---------- */

export const Doors: React.FC<Pt & {at: number; until: number; n?: number; r?: number; color?: string}> = ({x, y, at, until, n = 7, r = 170, color = '#b9a6ff'}) => {
  const f = useCurrentFrame();
  if (f < at || f > until + 10) return null;
  const open = prog(f, at, 14) * (1 - prog(f, until - 6, 14));
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {Array.from({length: n}, (_, i) => {
        const a = (i / n) * Math.PI * 2 + f * 0.01;
        const cx = x + Math.cos(a) * r * 0.55;
        const cy = y + Math.sin(a) * r;
        const w = 64 * open;
        const h = 128 * open;
        return (
          <g key={i} transform={`translate(${cx} ${cy})`} style={{filter: `drop-shadow(0 0 14px ${color})`}}>
            <rect x={-w / 2} y={-h / 2} width={w} height={h} fill="#05040a" stroke={color} strokeWidth={3} />
            <rect x={-w / 2 + 6} y={-h / 2 + 6} width={Math.max(0, w - 12)} height={Math.max(0, h - 12)} fill={color} opacity={0.35 + 0.25 * Math.sin(f * 0.3 + i)} />
          </g>
        );
      })}
    </svg>
  );
};

/* ---------- 风暴：旋转风线 + 落雷 ---------- */

export const Bolt: React.FC<{x1: number; y1: number; x2: number; y2: number; at: number; color?: string; width?: number}> = ({x1, y1, x2, y2, at, color = '#9fd4ff', width = 7}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || t > 12) return null;
  const p = Math.min(1, t / 4);
  const pts = Array.from({length: 11}, (_, i) => {
    const k = (i / 10) * p;
    const j = i === 0 || i === 10 ? 0 : 1;
    return `${x1 + (x2 - x1) * k + j * (rnd(i + at) - 0.5) * 70},${y1 + (y2 - y1) * k + j * (rnd(i * 7 + at) - 0.5) * 40}`;
  }).join(' ');
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      <polyline points={pts} fill="none" stroke={color} strokeWidth={width} opacity={1 - t / 12} style={{filter: `drop-shadow(0 0 18px ${color})`}} />
      <polyline points={pts} fill="none" stroke="#fff" strokeWidth={width / 3} opacity={1 - t / 12} />
    </svg>
  );
};

export const Storm: React.FC<{from: Pt; to: Pt; at: number; travel: number; until: number; color?: string}> = ({from, to, at, travel, until, color = '#8fc8ff'}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  const k = prog(f, at, travel);
  const cx = from.x + (to.x - from.x) * k;
  const cy = from.y + (to.y - from.y) * k;
  const o = prog(f, at, 10) * (1 - prog(f, until - 12, 12));
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: o}}>
      {Array.from({length: 9}, (_, i) => {
        const pts = Array.from({length: 40}, (_, j) => {
          const t = j / 39;
          const a = i * 0.7 + t * 9 - f * 0.25;
          const rr = 30 + t * 240;
          return `${cx + Math.cos(a) * rr},${cy + Math.sin(a) * rr * 0.45}`;
        }).join(' ');
        return <polyline key={i} points={pts} fill="none" stroke={color} strokeWidth={2.5} opacity={0.55} style={{filter: `drop-shadow(0 0 6px ${color})`}} />;
      })}
    </svg>
  );
};

/* ---------- 太阳：光柱从天而降 ---------- */

export const SunPillars: React.FC<{xs: number[]; at: number; dur?: number}> = ({xs, at, dur = 50}) => {
  const f = useCurrentFrame();
  if (f < at || f > at + dur + 20) return null;
  return (
    <>
      {xs.map((x, i) => {
        const t = f - at - i * 5;
        if (t < 0) return null;
        const w = interpolate(t, [0, 6, dur], [10, 140, 60], clamp);
        const o = interpolate(t, [0, 4, dur - 10, dur + 10], [0, 1, 0.9, 0], clamp);
        return (
          <div
            key={i}
            style={{position: 'absolute', left: x - w / 2, top: 0, width: w, height: 1080, opacity: o, background: `linear-gradient(90deg, transparent, ${C.sun} 30%, #fffbe8 50%, ${C.sun} 70%, transparent)`, boxShadow: `0 0 80px ${C.sun}`}}
          />
        );
      })}
    </>
  );
};

/* ---------- 窃取：丝线伸进对方，拽回一块碎片 ---------- */

export const Theft: React.FC<{from: Pt; to: Pt; at: number; reach?: number; pull?: number; until: number}> = ({from, to, at, reach = 40, pull = 40, until}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  const r = prog(f, at, reach);
  const back = prog(f, at + reach + 10, pull);
  const tip = {x: from.x + (to.x - from.x) * r * (1 - back), y: from.y + (to.y - from.y) * r * (1 - back)};
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {[0, 1, 2, 3].map((i) => {
        const mx = (from.x + tip.x) / 2;
        const my = (from.y + tip.y) / 2 - 120 + i * 40 + Math.sin(f * 0.1 + i) * 20;
        return <path key={i} d={`M${from.x} ${from.y} Q ${mx} ${my} ${tip.x} ${tip.y}`} fill="none" stroke={C.bronze} strokeWidth={2} opacity={0.85} style={{filter: `drop-shadow(0 0 6px ${C.bronze})`}} />;
      })}
      {f >= at + reach && (
        <g transform={`translate(${tip.x} ${tip.y}) rotate(${f * 6})`} style={{filter: 'drop-shadow(0 0 16px #fff)'}}>
          <polygon points="0,-26 18,0 0,30 -16,4" fill={`hsl(${(f * 9) % 360}, 90%, 70%)`} stroke="#fff" strokeWidth={2} />
        </g>
      )}
    </svg>
  );
};

/* ---------- 幻象：画面里浮出许多半透明的分身 ---------- */

export const Phantoms: React.FC<{at: number; until: number; children: (i: number) => React.ReactNode; n?: number}> = ({at, until, children, n = 4}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  const o = prog(f, at, 16) * (1 - prog(f, until - 12, 12));
  return (
    <>
      {Array.from({length: n}, (_, i) => (
        <AbsoluteFill key={i} style={{opacity: o * 0.38, transform: `translateX(${(i - (n - 1) / 2) * 260 + Math.sin(f * 0.05 + i) * 20}px) scale(${0.8 + 0.1 * (i % 2)})`, filter: `hue-rotate(${i * 70 + f * 3}deg) saturate(2)`}}>
          {children(i)}
        </AbsoluteFill>
      ))}
    </>
  );
};

/* ---------- 永暗之河：带着星点的黑色长河漫过战场 ---------- */

export const River: React.FC<{at: number; until: number; y?: number}> = ({at, until, y = 760}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  const reach = prog(f, at, 50);
  const o = (1 - prog(f, until - 20, 20)) * 0.82;
  const right = 1920 * reach;
  const top = Array.from({length: 33}, (_, i) => `${(i / 32) * right}px ${y - 120 + Math.sin(i * 0.8 + f * 0.08) * 16}px`).join(', ');
  const bot = Array.from({length: 33}, (_, i) => `${((32 - i) / 32) * right}px ${y + 120 + Math.sin((32 - i) * 0.6 - f * 0.07) * 14}px`).join(', ');
  return (
    <AbsoluteFill style={{opacity: o}}>
      <AbsoluteFill style={{clipPath: `polygon(${top}, ${bot})`, background: 'linear-gradient(180deg, #0a0f1f, #01030a 50%, #0a0f1f)', boxShadow: 'inset 0 0 40px #7f95ff'}}>
        {Array.from({length: 70}, (_, i) => (
          <div key={i} style={{position: 'absolute', left: ((rnd(i) * 1920 + f * (2 + rnd(i + 5) * 3)) % 1920), top: y - 110 + rnd(i * 3) * 220, width: 2 + rnd(i * 7) * 3, height: 2 + rnd(i * 7) * 3, borderRadius: '50%', background: '#cfd8ff', opacity: 0.4 + 0.6 * rnd(i * 9)}} />
        ))}
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

/* ---------- 灾祸之城：燃烧的城影从海里升起，火雨落下 ---------- */

export const FireCity: React.FC<{at: number; until: number; x0?: number; x1?: number}> = ({at, until, x0 = 1060, x1 = 1920}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  const rise = prog(f, at, 40);
  const o = 1 - prog(f, until - 20, 20);
  const towers = Array.from({length: 14}, (_, i) => {
    const w = 40 + rnd(i) * 50;
    const h = 120 + rnd(i * 3) * 280;
    const x = x0 + ((x1 - x0) * i) / 14;
    return {x, w, h};
  });
  return (
    <AbsoluteFill style={{opacity: o}}>
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
        <defs>
          <linearGradient id="fc" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#ff9a3a" />
            <stop offset="0.5" stopColor="#6a1400" />
            <stop offset="1" stopColor="#120200" />
          </linearGradient>
        </defs>
        {towers.map((t, i) => (
          <g key={i} transform={`translate(0 ${(1 - rise) * 420})`} style={{filter: `drop-shadow(0 0 18px ${C.calamity})`}}>
            <rect x={t.x} y={900 - t.h} width={t.w} height={t.h} fill="url(#fc)" />
            <path d={`M${t.x} ${900 - t.h} L ${t.x + t.w / 2} ${900 - t.h - 30 - rnd(i * 5) * 40} L ${t.x + t.w} ${900 - t.h} Z`} fill="#ff7a2a" opacity={0.8 + 0.2 * Math.sin(f * 0.4 + i)} />
          </g>
        ))}
        {Array.from({length: 60}, (_, i) => {
          const sx = x0 - 900 + rnd(i) * 1800;
          const sy = ((rnd(i * 3) * 1080 + f * (14 + rnd(i) * 10)) % 1180) - 100;
          return <line key={i} x1={sx} y1={sy} x2={sx - 14} y2={sy + 40} stroke="#ff4a2a" strokeWidth={2} opacity={0.6 * rise} />;
        })}
      </svg>
    </AbsoluteFill>
  );
};

/* ---------- 律令锁链：从八方射来，缠住目标 ---------- */

export const Chains: React.FC<Pt & {at: number; until: number; snap?: number; color?: string}> = ({x, y, at, until, snap, color = '#e0546f'}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  const k = prog(f, at, 30);
  const broken = snap !== undefined && f >= snap;
  const fall = broken ? (f - snap!) : 0;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {Array.from({length: 8}, (_, i) => {
        const a = (i / 8) * Math.PI * 2 + 0.3;
        const sx = x + Math.cos(a) * 1100;
        const sy = y + Math.sin(a) * 700;
        const ex = sx + (x + Math.cos(a) * 60 - sx) * k;
        const ey = sy + (y + Math.sin(a) * 90 - sy) * k + fall * fall * 0.6;
        const links = 18;
        return (
          <g key={i} opacity={broken ? Math.max(0, 1 - fall / 30) : 1} style={{filter: `drop-shadow(0 0 8px ${color})`}}>
            {Array.from({length: links}, (_, j) => {
              const t = j / links;
              const lx = sx + (ex - sx) * t;
              const ly = sy + (ey - sy) * t + (broken ? (rnd(i * 20 + j) - 0.5) * fall * 4 : 0);
              return <ellipse key={j} cx={lx} cy={ly} rx={14} ry={7} fill="none" stroke={j % 2 ? C.bronze : color} strokeWidth={3.5} transform={`rotate(${(a * 180) / Math.PI + (j % 2) * 90} ${lx} ${ly})`} />;
            })}
          </g>
        );
      })}
    </svg>
  );
};

/* ---------- 全知之眼：天幕上睁开许多眼睛，扫描线读过战场 ---------- */

export const Eyes: React.FC<{at: number; until: number; n?: number}> = ({at, until, n = 22}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  const o = 1 - prog(f, until - 16, 16);
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: o}}>
      {Array.from({length: n}, (_, i) => {
        const cx = 120 + rnd(i * 5) * 1680;
        const cy = 90 + rnd(i * 9) * 520;
        const s = 0.5 + rnd(i * 13) * 0.9;
        const open = prog(f, at + i * 2, 10);
        const look = Math.sin(f * 0.08 + i) * 8;
        return (
          <g key={i} transform={`translate(${cx} ${cy}) scale(${s})`} style={{filter: 'drop-shadow(0 0 10px #fff)'}}>
            <path d={`M-60 0 Q 0 ${-40 * open} 60 0 Q 0 ${40 * open} -60 0 Z`} fill="#f4f2ea" stroke="#fff" strokeWidth={2} />
            {open > 0.3 && <circle cx={look} cy={0} r={16 * open} fill={`hsl(${(i * 47 + f * 2) % 360}, 80%, 55%)`} />}
            {open > 0.3 && <circle cx={look} cy={0} r={6 * open} fill="#000" />}
          </g>
        );
      })}
      <rect x={0} y={((f - at) * 22) % 1080} width={1920} height={3} fill="#fff" opacity={0.5} />
    </svg>
  );
};

/* ---------- 堕落阴影：黑色触须从下方升起，往下拖 ---------- */

export const Shadows: React.FC<{x0: number; x1: number; at: number; until: number}> = ({x0, x1, at, until}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  const k = prog(f, at, 30);
  const o = 1 - prog(f, until - 14, 14);
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: o}}>
      {Array.from({length: 11}, (_, i) => {
        const bx = x0 + ((x1 - x0) * i) / 10;
        const h = (260 + rnd(i) * 360) * k;
        const pts = Array.from({length: 20}, (_, j) => {
          const t = j / 19;
          return `${bx + Math.sin(t * 6 + f * 0.12 + i) * 30 * t},${1080 - t * h}`;
        }).join(' ');
        return <polyline key={i} points={pts} fill="none" stroke="#000" strokeWidth={28 - i % 3 * 6} strokeLinecap="round" style={{filter: 'drop-shadow(0 0 12px #4a1a6a)'}} />;
      })}
    </svg>
  );
};

/* ---------- 雾幕：愚弄，整片战场被灰雾吞没 ---------- */

export const FogVeil: React.FC<{at: number; until: number; peak?: number}> = ({at, until, peak = 0.85}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  const o = prog(f, at, 24) * (1 - prog(f, until - 24, 24)) * peak;
  return (
    <AbsoluteFill style={{opacity: o}}>
      <Fog opacity={1} speed={3} scale={1.6} />
      <Fog opacity={0.8} speed={-2} scale={0.9} />
    </AbsoluteFill>
  );
};

/* ---------- 汇聚：许多光点被吸向中心 ---------- */

export const Converge: React.FC<Pt & {at: number; until: number; colors: string[]}> = ({x, y, at, until, colors}) => {
  const f = useCurrentFrame();
  if (f < at || f > until) return null;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {Array.from({length: 90}, (_, i) => {
        const a = rnd(i) * Math.PI * 2;
        const life = 40;
        const t = ((f - at + rnd(i * 3) * life) % life) / life;
        const r = (1 - t) * (500 + rnd(i * 5) * 500);
        const px = x + Math.cos(a + t * 1.5) * r;
        const py = y + Math.sin(a + t * 1.5) * r * 0.6;
        return <circle key={i} cx={px} cy={py} r={2 + rnd(i * 7) * 4} fill={colors[i % colors.length]} opacity={t} />;
      })}
    </svg>
  );
};

/* ---------- 光核：最初造物主的残影，白热的一点 ---------- */

export const Core: React.FC<Pt & {at: number; until?: number; r?: number}> = ({x, y, at, until, r = 60}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 20) * (until !== undefined ? 1 - prog(f, until - 12, 12) : 1);
  if (p <= 0) return null;
  const pulse = 1 + 0.08 * Math.sin(f * 0.3);
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y,
        width: r * 2,
        height: r * 2,
        borderRadius: '50%',
        transform: `translate(-50%, -50%) scale(${p * pulse})`,
        background: 'radial-gradient(circle, #ffffff 0%, #fff8e6 30%, rgba(255,240,220,0.35) 60%, transparent 72%)',
        boxShadow: '0 0 120px 40px rgba(255,250,235,0.55)',
      }}
    />
  );
};
