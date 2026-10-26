import React from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import {prog} from './kit';
import {C, F} from './theme';

type Pos = {x: number; y: number; a?: 'l' | 'c' | 'r'};
const tx = (a?: 'l' | 'c' | 'r') => (a === 'c' ? '-50%' : a === 'r' ? '-100%' : '0');
const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

// 打字机：逐字出现。
export const Typer: React.FC<Pos & {text: string; at: number; size?: number; color?: string; cps?: number; font?: string; w?: number; until?: number}> = ({
  x,
  y,
  a = 'l',
  text,
  at,
  size = 40,
  color = C.ink,
  cps = 2.4,
  font = F.serif,
  w = 700,
  until,
}) => {
  const f = useCurrentFrame();
  if (f < at || (until !== undefined && f >= until)) return null;
  const n = Math.max(0, Math.min(text.length, Math.floor((f - at) / cps)));
  const out = until !== undefined ? interpolate(f, [until - 10, until], [1, 0], clamp) : 1;
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(${tx(a)}, -50%)`, fontFamily: font, fontWeight: w, fontSize: size, color, whiteSpace: 'nowrap', opacity: out}}>
      <span>{text.slice(0, n)}</span>
      <span style={{opacity: 0}}>{text.slice(n)}</span>
    </div>
  );
};

// 淡入的文字，可换行，可设淡出时间。
export const Label: React.FC<
  Pos & {text: string; at: number; size?: number; color?: string; font?: string; w?: number; ls?: number; width?: number; until?: number; glow?: string; bg?: boolean}
> = ({x, y, a = 'l', text, at, size = 22, color = C.ink, font = F.sans, w = 500, ls, width, until, glow, bg}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 12) * (until !== undefined ? 1 - prog(f, until - 12, 12) : 1);
  if (p <= 0) return null;
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y,
        transform: `translate(${tx(a)}, -50%) translateY(${(1 - p) * 10}px)`,
        opacity: p,
        fontFamily: font,
        fontWeight: w,
        fontSize: size,
        color,
        whiteSpace: width ? 'normal' : 'nowrap',
        width,
        lineHeight: 1.45,
        textAlign: a === 'c' ? 'center' : a === 'r' ? 'right' : 'left',
        letterSpacing: ls ? `${ls}em` : undefined,
        textShadow: glow ? `0 0 18px ${glow}, 0 0 4px rgba(0,0,0,0.9)` : '0 0 6px rgba(0,0,0,0.9)',
        background: bg ? 'linear-gradient(90deg, transparent, rgba(0,0,0,0.72) 8%, rgba(0,0,0,0.72) 92%, transparent)' : undefined,
        padding: bg ? '2px 40px' : undefined,
      }}
    >
      {text}
    </div>
  );
};

// 电影式字幕：底部居中，逐句切换。
export const Sub: React.FC<{lines: {at: number; text: string}[]; end?: number}> = ({lines, end}) => {
  const f = useCurrentFrame();
  const i = lines.reduce((v, l, k) => (f >= l.at ? k : v), -1);
  if (i < 0 || (end !== undefined && f >= end)) return null;
  const cur = lines[i];
  const p = prog(f, cur.at, 8);
  const out = end !== undefined ? 1 - prog(f, end - 10, 10) : 1;
  return (
    <div style={{position: 'absolute', left: 0, right: 0, top: 960, display: 'flex', justifyContent: 'center', opacity: p * out}}>
      <div style={{fontFamily: F.sans, fontWeight: 500, fontSize: 32, color: C.ink, letterSpacing: '0.04em', padding: '6px 26px', background: 'linear-gradient(90deg, transparent, rgba(0,0,0,0.55) 15%, rgba(0,0,0,0.55) 85%, transparent)', textShadow: '0 0 8px #000'}}>
        {cur.text}
      </div>
    </div>
  );
};

// 角标：标明这一段是推演还是正文。
export const Tag: React.FC<{text: string; at?: number; color?: string; x?: number; y?: number}> = ({text, at = 0, color = C.tag, x = 60, y = 52}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10);
  return (
    <div style={{position: 'absolute', left: x, top: y, opacity: p, display: 'flex', alignItems: 'center', gap: 10, fontFamily: F.sans, fontWeight: 700, fontSize: 19, color: C.ink}}>
      <span style={{width: 10, height: 10, background: color, boxShadow: `0 0 10px ${color}`}} />
      {text}
    </div>
  );
};

// 大标题：从模糊放大中定格。
export const Title: React.FC<Pos & {text: string; at: number; size: number; color?: string; font?: string; glow?: string; ls?: number}> = ({x, y, a = 'c', text, at, size, color = C.ink, font = F.serif, glow, ls = 0.08}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0) return null;
  const s = interpolate(t, [0, 18], [1.25, 1], {...clamp, easing: (v) => 1 - (1 - v) ** 3});
  const blur = interpolate(t, [0, 14], [18, 0], clamp);
  const o = interpolate(t, [0, 10], [0, 1], clamp);
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y,
        transform: `translate(${tx(a)}, -50%) scale(${s})`,
        filter: `blur(${blur}px)`,
        opacity: o,
        fontFamily: font,
        fontWeight: 900,
        fontSize: size,
        lineHeight: 1,
        color,
        whiteSpace: 'nowrap',
        letterSpacing: `${ls}em`,
        textShadow: glow ? `0 0 40px ${glow}, 0 0 12px ${glow}` : undefined,
      }}
    >
      {text}
    </div>
  );
};
