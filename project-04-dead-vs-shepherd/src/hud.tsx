import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {pol, prog} from './kit';

/* 擂台配色：逝者冷青，牧羊人琥珀，命中亮红。 */
export const H = {
  bg: '#0d0e12',
  ink: '#f4f1ea',
  dim: '#8a8f9c',
  dead: '#7fe3ff',
  deadDeep: '#1f5466',
  shep: '#ffb340',
  shepDeep: '#6b4510',
  hit: '#ff2e4d',
};

export const HF = {
  heavy: "'Noto Sans SC', 'WenQuanYi Zen Hei', sans-serif",
  brush: "'Ma Shan Zheng', 'Noto Serif SC', serif",
  mono: "'IBM Plex Mono', 'DejaVu Sans Mono', monospace",
};

// 确定性的伪随机，保证每次渲染一致。
const rnd = (n: number) => {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
};

/* ---------- 背景：炭黑 + 网点 + 速度线 + 队伍色光 ---------- */

export const Arena: React.FC<{left?: number; right?: number; split?: number; speed?: number}> = ({left = 0.5, right = 0.5, split = 0, speed = 1}) => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill style={{backgroundColor: H.bg}}>
      <AbsoluteFill style={{background: `radial-gradient(ellipse at 12% 50%, rgba(127,227,255,${0.22 * left}) 0%, transparent 55%), radial-gradient(ellipse at 88% 50%, rgba(255,179,64,${0.22 * right}) 0%, transparent 55%)`}} />
      {split > 0 && (
        <>
          <AbsoluteFill style={{background: `linear-gradient(90deg, rgba(31,84,102,0.55), rgba(31,84,102,0.15))`, clipPath: `polygon(0 0, ${50 + 6 * split}% 0, ${50 - 6 * split}% 100%, 0 100%)`, opacity: split}} />
          <AbsoluteFill style={{background: `linear-gradient(270deg, rgba(107,69,16,0.6), rgba(107,69,16,0.15))`, clipPath: `polygon(${50 + 6 * split}% 0, 100% 0, 100% 100%, ${50 - 6 * split}% 100%)`, opacity: split}} />
        </>
      )}
      <AbsoluteFill
        style={{
          backgroundImage: 'repeating-linear-gradient(115deg, transparent 0 46px, rgba(255,255,255,0.035) 46px 48px)',
          backgroundPosition: `${(f * 6 * speed) % 1000}px 0`,
        }}
      />
      <AbsoluteFill
        style={{
          backgroundImage: 'radial-gradient(rgba(255,255,255,0.08) 1.3px, transparent 1.8px)',
          backgroundSize: '16px 16px',
          WebkitMaskImage: 'linear-gradient(180deg, black 0%, transparent 35%, transparent 65%, black 100%)',
          maskImage: 'linear-gradient(180deg, black 0%, transparent 35%, transparent 65%, black 100%)',
        }}
      />
      <AbsoluteFill style={{background: 'radial-gradient(ellipse at 50% 50%, transparent 55%, rgba(0,0,0,0.6) 100%)'}} />
    </AbsoluteFill>
  );
};

/* ---------- 震屏、闪白 ---------- */

export const Shake: React.FC<{at: number; dur?: number; amp?: number; children: React.ReactNode}> = ({at, dur = 12, amp = 18, children}) => {
  const f = useCurrentFrame();
  const t = f - at;
  const k = t >= 0 && t < dur ? (1 - t / dur) * amp : 0;
  return <AbsoluteFill style={{transform: `translate(${(rnd(f) - 0.5) * 2 * k}px, ${(rnd(f + 99) - 0.5) * 2 * k}px)`}}>{children}</AbsoluteFill>;
};

export const Flash: React.FC<{at?: number; dur?: number; color?: string}> = ({at = 0, dur = 8, color = '#ffffff'}) => {
  const f = useCurrentFrame();
  const o = interpolate(f - at, [0, dur], [0.85, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  return o > 0 && f >= at ? <AbsoluteFill style={{backgroundColor: color, opacity: o, mixBlendMode: 'screen'}} /> : null;
};

/* ---------- 文字 ---------- */

type Pos = {x: number; y: number; a?: 'l' | 'c' | 'r'};
const tx = (a?: 'l' | 'c' | 'r') => (a === 'c' ? '-50%' : a === 'r' ? '-100%' : '0');

// 砸字：从放大状态砸进画面，带 RGB 错位。
export const Slam: React.FC<Pos & {text: string; at: number; size: number; color: string; skew?: number; font?: string; rot?: number}> = ({x, y, a = 'c', text, at, size, color, skew = -8, font = HF.heavy, rot = 0}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0) return null;
  const s = interpolate(t, [0, 5, 9], [2.2, 0.94, 1], {extrapolateRight: 'clamp'});
  const o = interpolate(t, [0, 3], [0, 1], {extrapolateRight: 'clamp'});
  const split = interpolate(t, [0, 10], [10, 2], {extrapolateRight: 'clamp'});
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y,
        transform: `translate(${tx(a)}, -50%) scale(${s}) skewX(${skew}deg) rotate(${rot}deg)`,
        fontFamily: font,
        fontWeight: 900,
        fontSize: size,
        lineHeight: 1,
        color,
        opacity: o,
        whiteSpace: 'nowrap',
        textShadow: `${split}px 0 rgba(255,46,77,0.7), ${-split}px 0 rgba(127,227,255,0.7), 0 0 40px rgba(0,0,0,0.6)`,
      }}
    >
      {text}
    </div>
  );
};

// 打字机：逐字出现，带光标。
export const Typer: React.FC<Pos & {text: string; at: number; size?: number; color?: string; cps?: number; font?: string; w?: number; cursor?: boolean}> = ({
  x,
  y,
  a = 'l',
  text,
  at,
  size = 40,
  color = H.ink,
  cps = 2.2,
  font = HF.heavy,
  w = 700,
  cursor = true,
}) => {
  const f = useCurrentFrame();
  const n = Math.max(0, Math.min(text.length, Math.floor((f - at) / cps)));
  const done = n >= text.length;
  const blink = Math.floor(f / 8) % 2 === 0;
  if (f < at) return null;
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(${tx(a)}, -50%)`, fontFamily: font, fontWeight: w, fontSize: size, color, whiteSpace: 'nowrap'}}>
      {text.slice(0, n)}
      {cursor && (!done || blink) && <span style={{color: H.hit}}>▍</span>}
    </div>
  );
};

export const Label: React.FC<Pos & {text: string; at: number; size?: number; color?: string; font?: string; w?: number; ls?: number; width?: number}> = ({x, y, a = 'l', text, at, size = 22, color = H.ink, font = HF.heavy, w = 700, ls, width}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10);
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y,
        transform: `translate(${tx(a)}, -50%) translateY(${(1 - p) * 14}px)`,
        opacity: p,
        fontFamily: font,
        fontWeight: w,
        fontSize: size,
        color,
        whiteSpace: width ? 'normal' : 'nowrap',
        width,
        lineHeight: 1.4,
        textAlign: a === 'c' ? 'center' : a === 'r' ? 'right' : 'left',
        letterSpacing: ls ? `${ls}em` : undefined,
      }}
    >
      {text}
    </div>
  );
};

// 故障字：几帧内横向切片错位 + 色偏。
export const Glitch: React.FC<Pos & {text: string; at: number; size: number; color: string; font?: string; dur?: number}> = ({x, y, a = 'c', text, at, size, color, font = HF.brush, dur = 14}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0) return null;
  const k = t < dur ? 1 - t / dur : 0;
  const base: React.CSSProperties = {position: 'absolute', left: x, top: y, fontFamily: font, fontSize: size, lineHeight: 1, whiteSpace: 'nowrap'};
  const slices = [0, 1, 2, 3, 4];
  return (
    <>
      {k > 0 && <div style={{...base, color: H.dead, opacity: 0.8 * k, transform: `translate(calc(${tx(a)} + ${(rnd(f) - 0.5) * 40 * k}px), -50%)`}}>{text}</div>}
      {k > 0 && <div style={{...base, color: H.hit, opacity: 0.8 * k, transform: `translate(calc(${tx(a)} + ${(rnd(f + 7) - 0.5) * 40 * k}px), -50%)`}}>{text}</div>}
      {slices.map((i) => (
        <div
          key={i}
          style={{
            ...base,
            color,
            clipPath: `inset(${i * 20}% 0 ${80 - i * 20}% 0)`,
            transform: `translate(calc(${tx(a)} + ${(rnd(f * 3 + i) - 0.5) * 60 * k}px), -50%)`,
          }}
        >
          {text}
        </div>
      ))}
    </>
  );
};

// 毛笔印章字：带过冲缩放和墨点。
export const Stamp: React.FC<Pos & {text: string; at: number; size: number; color?: string; rot?: number}> = ({x, y, a = 'c', text, at, size, color = H.hit, rot = -8}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0) return null;
  const s = interpolate(t, [0, 4, 8], [2.6, 0.9, 1], {extrapolateRight: 'clamp'});
  const o = interpolate(t, [0, 2], [0, 1], {extrapolateRight: 'clamp'});
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(${tx(a)}, -50%) rotate(${rot}deg) scale(${s})`, opacity: o}}>
      {[0, 1, 2, 3].map((i) => (
        <div
          key={i}
          style={{
            position: 'absolute',
            left: `${8 + i * 26 + rnd(i + size) * 10}%`,
            top: i % 2 ? '104%' : '-14%',
            width: 6 + rnd(i * 5) * 12,
            height: 6 + rnd(i * 5) * 12,
            borderRadius: '50%',
            background: color,
            opacity: 0.8 * interpolate(t, [2, 8], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}),
          }}
        />
      ))}
      <div style={{position: 'relative', fontFamily: HF.brush, fontSize: size, lineHeight: 1, color, whiteSpace: 'nowrap', textShadow: '0 0 30px rgba(0,0,0,0.5)'}}>{text}</div>
    </div>
  );
};

/* ---------- 解说字幕条 ---------- */

export const Caption: React.FC<{lines: {at: number; text: string}[]; who?: string}> = ({lines, who = '解说'}) => {
  const f = useCurrentFrame();
  const current = [...lines].reverse().find((l) => f >= l.at);
  if (!current) return null;
  const p = prog(f, lines[0].at, 10);
  return (
    <div style={{position: 'absolute', left: 160, right: 160, top: 946, height: 76, transform: `translateY(${(1 - p) * 30}px)`, opacity: p, display: 'flex', alignItems: 'center', gap: 22}}>
      <div style={{background: H.hit, color: '#0d0e12', fontFamily: HF.heavy, fontWeight: 900, fontSize: 22, padding: '8px 14px', transform: 'skewX(-10deg)'}}>{who}</div>
      <div style={{position: 'relative', flex: 1, height: 56, background: 'rgba(0,0,0,0.72)', borderLeft: `4px solid ${H.hit}`}}>
        <Typer key={current.at} x={22} y={28} text={current.text} at={current.at} size={28} cps={1.2} w={700} cursor={false} />
      </div>
    </div>
  );
};

/* ---------- 血条与计时 ---------- */

export const HPBar: React.FC<{side: 'L' | 'R'; value: number; trail: number; name: string; tag: string; color: string}> = ({side, value, trail, name, tag, color}) => {
  const w = 700;
  const L = side === 'L';
  const x = L ? 120 : 1920 - 120 - w;
  const clip = L ? 'polygon(4% 0, 100% 0, 96% 100%, 0 100%)' : 'polygon(0 0, 96% 0, 100% 100%, 4% 100%)';
  const fill = (v: number, c: string) => (
    <div style={{position: 'absolute', top: 0, bottom: 0, [L ? 'right' : 'left']: 0, width: `${Math.max(0, v) * 100}%`, background: c}} />
  );
  return (
    <>
      <div style={{position: 'absolute', left: x, top: 64, width: w, height: 34, clipPath: clip, background: 'rgba(255,255,255,0.12)', border: '2px solid rgba(255,255,255,0.4)'}}>
        {fill(trail, '#ffffff')}
        {fill(value, value < 0.3 ? H.hit : color)}
      </div>
      <div style={{position: 'absolute', left: L ? x + 10 : undefined, right: L ? undefined : 120 + 10, top: 124, display: 'flex', gap: 14, alignItems: 'baseline', flexDirection: L ? 'row' : 'row-reverse'}}>
        <span style={{fontFamily: HF.heavy, fontWeight: 900, fontSize: 34, color, fontStyle: 'italic'}}>{name}</span>
        <span style={{fontFamily: HF.mono, fontSize: 16, color: H.dim, letterSpacing: '0.12em'}}>{tag}</span>
      </div>
    </>
  );
};

export const Timer: React.FC<{value: number}> = ({value}) => (
  <div style={{position: 'absolute', left: 960, top: 82, transform: 'translate(-50%, -50%)', width: 110, height: 86, clipPath: 'polygon(12% 0, 88% 0, 100% 50%, 88% 100%, 12% 100%, 0 50%)', background: '#000', border: '2px solid #fff', display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
    <span style={{fontFamily: HF.mono, fontWeight: 500, fontSize: 46, color: '#fff'}}>{String(Math.max(0, Math.round(value))).padStart(2, '0')}</span>
  </div>
);

/* ---------- 雷达图与技能格 ---------- */

export const Radar: React.FC<{cx: number; cy: number; r: number; axes: string[]; values: number[]; color: string; at: number; max?: number}> = ({cx, cy, r, axes, values, color, at, max = 6}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 24);
  const n = axes.length;
  const ring = (k: number) => Array.from({length: n}, (_, i) => pol(cx, cy, r * k, -90 + (360 / n) * i).join(',')).join(' ');
  const shape = values.map((v, i) => pol(cx, cy, (r * v * p) / max, -90 + (360 / n) * i).join(',')).join(' ');
  return (
    <>
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
        {[1, 0.66, 0.33].map((k) => (
          <polygon key={k} points={ring(k)} fill="none" stroke="rgba(255,255,255,0.18)" strokeWidth={1.5} />
        ))}
        {axes.map((_, i) => {
          const [x2, y2] = pol(cx, cy, r, -90 + (360 / n) * i);
          return <line key={i} x1={cx} y1={cy} x2={x2} y2={y2} stroke="rgba(255,255,255,0.12)" />;
        })}
        <polygon points={shape} fill={color} fillOpacity={0.28} stroke={color} strokeWidth={3} />
      </svg>
      {axes.map((ax, i) => {
        const [x, y] = pol(cx, cy, r + 34, -90 + (360 / n) * i);
        return <Label key={ax} x={x} y={y} a="c" text={ax} at={at + 4 + i * 2} size={20} color={H.dim} />;
      })}
    </>
  );
};

export const Skill: React.FC<{x: number; y: number; at: number; glyph: string; name: string; desc: string; color: string; locked?: boolean}> = ({x, y, at, glyph, name, desc, color, locked}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10);
  return (
    <div style={{position: 'absolute', left: x, top: y, display: 'flex', gap: 18, alignItems: 'center', opacity: p, transform: `translateX(${(1 - p) * -30}px)`}}>
      <div
        style={{
          width: 84,
          height: 84,
          border: `3px solid ${locked ? '#555' : color}`,
          background: locked ? 'repeating-linear-gradient(45deg, #222 0 8px, #2c2c2c 8px 16px)' : 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontFamily: HF.brush,
          fontSize: 54,
          color: locked ? '#666' : color,
          transform: 'skewX(-6deg)',
        }}
      >
        {glyph}
      </div>
      <div>
        <div style={{fontFamily: HF.heavy, fontWeight: 900, fontSize: 28, color: locked ? '#777' : H.ink}}>{name}</div>
        <div style={{fontFamily: HF.heavy, fontWeight: 500, fontSize: 19, color: H.dim, marginTop: 4, maxWidth: 460}}>{desc}</div>
      </div>
    </div>
  );
};

export const Bolt: React.FC<{x1: number; y1: number; x2: number; y2: number; at: number; color?: string}> = ({x1, y1, x2, y2, at, color = H.shep}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || t > 14) return null;
  const p = Math.min(1, t / 5);
  const pts = Array.from({length: 9}, (_, i) => {
    const k = (i / 8) * p;
    const jx = i === 0 || i === 8 ? 0 : (rnd(i + at) - 0.5) * 50;
    const jy = i === 0 || i === 8 ? 0 : (rnd(i * 7 + at) - 0.5) * 50;
    return `${x1 + (x2 - x1) * k + jx},${y1 + (y2 - y1) * k + jy}`;
  }).join(' ');
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      <polyline points={pts} fill="none" stroke={color} strokeWidth={6} opacity={1 - t / 14} style={{filter: `drop-shadow(0 0 14px ${color})`}} />
      <polyline points={pts} fill="none" stroke="#fff" strokeWidth={2} opacity={1 - t / 14} />
    </svg>
  );
};
