import React from 'react';
import {AbsoluteFill, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {prog} from './kit';
import {C, F, rnd} from './theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/*
 * 老电影：画面泛黄、颗粒、闪烁、划痕、灰尘、暗角、片门抖动。
 * children 会被染成单色；color 里的东西画在单色层之上、颗粒之下，保留原色（用来放金色）。
 */
export const FilmLook: React.FC<{children: React.ReactNode; color?: React.ReactNode; sepia?: number; flicker?: number}> = ({children, color, sepia = 1, flicker = 1}) => {
  const f = useCurrentFrame();
  const weaveX = (rnd(f * 0.37) - 0.5) * 3;
  const weaveY = (rnd(f * 0.71 + 3) - 0.5) * 4;
  const bright = 1 + (rnd(f + 11) - 0.5) * 0.09 * flicker;
  // 划痕：每隔几帧换位置，偶尔两条。
  const seg = Math.floor(f / 3);
  const scratches = [0, 1].filter((i) => rnd(seg * 7 + i) < (i ? 0.25 : 0.7)).map((i) => ({x: rnd(seg * 13 + i) * 1920, w: 1 + rnd(seg * 3 + i) * 2, o: 0.25 + rnd(seg + i) * 0.35}));
  const dust = Array.from({length: 7}, (_, i) => ({x: rnd(f * 3 + i) * 1920, y: rnd(f * 5 + i * 9) * 1080, r: 1 + rnd(f + i * 17) * 4, show: rnd(f * 11 + i) < 0.35}));
  return (
    <AbsoluteFill style={{backgroundColor: C.black, overflow: 'hidden'}}>
      <AbsoluteFill style={{transform: `translate(${weaveX}px, ${weaveY}px)`}}>
        <AbsoluteFill style={{filter: `sepia(${sepia}) saturate(${1 - 0.3 * sepia}) contrast(1.08) brightness(${bright})`}}>{children}</AbsoluteFill>
        {color}
      </AbsoluteFill>
      <AbsoluteFill
        style={{
          backgroundImage: `url(${staticFile('tex/grain.png')})`,
          backgroundPosition: `${Math.floor(rnd(f) * 512)}px ${Math.floor(rnd(f + 5) * 512)}px`,
          mixBlendMode: 'overlay',
          opacity: 0.55,
        }}
      />
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
        {scratches.map((s, i) => (
          <rect key={i} x={s.x} y={0} width={s.w} height={1080} fill="#f4ead2" opacity={s.o} />
        ))}
        {dust.filter((d) => d.show).map((d, i) => (
          <circle key={i} cx={d.x} cy={d.y} r={d.r} fill={i % 2 ? '#120d08' : '#f4ead2'} opacity={0.6} />
        ))}
      </svg>
      <AbsoluteFill style={{background: 'radial-gradient(ellipse at 50% 50%, transparent 52%, rgba(10,6,2,0.78) 100%)'}} />
    </AbsoluteFill>
  );
};

/* 片头倒计时：扫过的扇形、十字线、数字 5 到 1。 */
export const Leader: React.FC<{from?: number; per?: number}> = ({from = 5, per = 24}) => {
  const f = useCurrentFrame();
  const k = Math.floor(f / per);
  const n = from - k;
  if (n < 1) return <AbsoluteFill style={{backgroundColor: C.black}} />;
  const t = (f % per) / per;
  const a = t * 360;
  const r = 420;
  const x = 960 + Math.sin((a * Math.PI) / 180) * r * 1.5;
  const y = 540 - Math.cos((a * Math.PI) / 180) * r * 1.5;
  const large = a > 180 ? 1 : 0;
  return (
    <AbsoluteFill style={{backgroundColor: '#8a7a62'}}>
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
        <path d={`M960 540 L 960 ${540 - r * 1.5} A ${r * 1.5} ${r * 1.5} 0 ${large} 1 ${x} ${y} Z`} fill="#6a5b46" />
        <circle cx={960} cy={540} r={r} fill="none" stroke="#f0e6cf" strokeWidth={6} />
        <circle cx={960} cy={540} r={r - 40} fill="none" stroke="#f0e6cf" strokeWidth={3} />
        <line x1={0} y1={540} x2={1920} y2={540} stroke="#f0e6cf" strokeWidth={3} />
        <line x1={960} y1={0} x2={960} y2={1080} stroke="#f0e6cf" strokeWidth={3} />
      </svg>
      <div style={{position: 'absolute', left: 960, top: 540, transform: 'translate(-50%, -50%)', fontFamily: F.serif, fontWeight: 900, fontSize: 420, color: '#f0e6cf', lineHeight: 1}}>{n}</div>
    </AbsoluteFill>
  );
};

/* 字幕卡的花边：双线框 + 四角卷草纹 + 上下中饰。 */
export const Border: React.FC<{inset?: number; color?: string; at?: number}> = ({inset = 70, color = C.cream, at = 0}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 14);
  const w = 1920 - inset * 2;
  const h = 1080 - inset * 2;
  const corner = (rot: number, cx: number, cy: number) => (
    <g transform={`translate(${cx} ${cy}) rotate(${rot})`}>
      <path d="M0 0 C 40 0 60 20 60 60 M 0 0 C 0 40 20 60 60 60 M 14 14 C 34 14 46 26 46 46 C 26 46 14 34 14 14 Z" fill="none" stroke={color} strokeWidth={2.5} />
      <circle cx={60} cy={60} r={5} fill={color} />
    </g>
  );
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: p}}>
      <rect x={inset} y={inset} width={w} height={h} fill="none" stroke={color} strokeWidth={3} />
      <rect x={inset + 16} y={inset + 16} width={w - 32} height={h - 32} fill="none" stroke={color} strokeWidth={1.2} />
      {corner(0, inset + 16, inset + 16)}
      {corner(90, inset + w - 16, inset + 16)}
      {corner(180, inset + w - 16, inset + h - 16)}
      {corner(270, inset + 16, inset + h - 16)}
      {[inset, inset + h].map((yy, i) => (
        <g key={i} transform={`translate(960 ${yy}) scale(1 ${i ? -1 : 1})`}>
          <path d="M-120 0 C -60 0 -40 26 0 26 C 40 26 60 0 120 0" fill="none" stroke={color} strokeWidth={2} />
          <circle cx={0} cy={26} r={6} fill={color} />
        </g>
      ))}
    </svg>
  );
};

/* 默片字幕卡：黑底、花边、居中衬线大字。 */
export const Intertitle: React.FC<{lines: {at: number; text: string; size?: number; color?: string}[]; foot?: {at: number; text: string}; bg?: string}> = ({lines, foot, bg = C.black}) => {
  const f = useCurrentFrame();
  const total = lines.reduce((v, l) => v + (l.size ?? 72) * 1.5, 0);
  let y = 540 - total / 2;
  return (
    <AbsoluteFill style={{backgroundColor: bg}}>
      <Border />
      {lines.map((l, i) => {
        const size = l.size ?? 72;
        const top = y + (size * 1.5) / 2;
        y += size * 1.5;
        const p = prog(f, l.at, 14);
        return (
          <div key={i} style={{position: 'absolute', left: 0, right: 0, top, transform: `translateY(-50%) translateY(${(1 - p) * 10}px)`, opacity: p, textAlign: 'center', fontFamily: F.serif, fontWeight: 900, fontSize: size, color: l.color ?? C.cream, letterSpacing: '0.08em'}}>
            {l.text}
          </div>
        );
      })}
      {foot && (
        <div style={{position: 'absolute', left: 0, right: 0, bottom: 120, textAlign: 'center', fontFamily: F.sans, fontWeight: 500, fontSize: 20, color: C.sepia, opacity: prog(f, foot.at, 14)}}>
          {foot.text}
        </div>
      )}
    </AbsoluteFill>
  );
};

/* 标签：标明一句话是谁说的、什么性质。 */
export type Kind = '原话' | '原文' | '推断' | '宣传词' | '史实';
const KIND_STYLE: Record<Kind, {bg: string; fg: string; border: string}> = {
  原话: {bg: C.cream, fg: C.ink, border: C.cream},
  原文: {bg: 'transparent', fg: C.cream, border: C.cream},
  推断: {bg: 'transparent', fg: C.cream, border: C.cream},
  宣传词: {bg: C.ink, fg: C.cream, border: C.sepia},
  史实: {bg: 'transparent', fg: C.cream, border: C.sepia},
};

export const Chip: React.FC<{kind: Kind; text?: string}> = ({kind, text}) => {
  const s = KIND_STYLE[kind];
  return (
    <span style={{display: 'inline-block', fontFamily: F.sans, fontWeight: 700, fontSize: 18, color: s.fg, background: s.bg, border: `2px ${kind === '推断' ? 'dashed' : 'solid'} ${s.border}`, padding: '2px 10px', letterSpacing: '0.06em'}}>
      {text ?? kind}
    </span>
  );
};

/* 引文卡：大号引文 + 标签 + 出处。 */
export const Quote: React.FC<{x?: number; y: number; at: number; until?: number; text: string; kind: Kind; who: string; src: string; size?: number; width?: number; color?: string}> = ({
  x = 960,
  y,
  at,
  until,
  text,
  kind,
  who,
  src,
  size = 52,
  width = 1500,
  color = C.cream,
}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 16) * (until !== undefined ? 1 - prog(f, until - 12, 12) : 1);
  if (p <= 0) return null;
  const n = Math.floor(interpolate(f - at, [0, text.length * 1.6], [0, text.length], clamp));
  return (
    <div style={{position: 'absolute', left: x, top: y, width, transform: 'translate(-50%, -50%)', opacity: p, textAlign: 'center'}}>
      <div style={{fontFamily: F.serif, fontWeight: 900, fontSize: size, color, lineHeight: 1.45, textShadow: '0 0 12px rgba(0,0,0,0.8)'}}>
        <span>{text.slice(0, n)}</span>
        <span style={{opacity: 0}}>{text.slice(n)}</span>
      </div>
      <div style={{marginTop: 22, display: 'flex', gap: 14, justifyContent: 'center', alignItems: 'center', opacity: prog(f, at + 20, 12)}}>
        <Chip kind={kind} />
        <span style={{fontFamily: F.sans, fontWeight: 700, fontSize: 22, color: C.cream}}>{who}</span>
      </div>
      <div style={{marginTop: 10, fontFamily: F.sans, fontWeight: 500, fontSize: 18, color: C.sepia, opacity: prog(f, at + 26, 12)}}>{src}</div>
    </div>
  );
};

/* 日期戳：左上角的年份字样，像新闻片的字卡。 */
export const DateStamp: React.FC<{text: string; at?: number; sub?: string}> = ({text, at = 0, sub}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 12);
  return (
    <div style={{position: 'absolute', left: 110, top: 96, opacity: p, transform: `translateX(${(1 - p) * -20}px)`, background: 'rgba(12,8,4,0.72)', padding: '8px 22px 10px', borderLeft: `4px solid ${C.cream}`}}>
      <div style={{fontFamily: F.serif, fontWeight: 900, fontSize: 46, color: C.cream, letterSpacing: '0.1em', textShadow: '0 0 10px #000'}}>{text}</div>
      {sub && <div style={{fontFamily: F.sans, fontWeight: 500, fontSize: 20, color: C.cream, opacity: 0.8, marginTop: 4}}>{sub}</div>}
    </div>
  );
};

/* 宣传口号：大字砸进画面，下方小字注明是本片宣传词。 */
export const Slogan: React.FC<{text: string; at: number; until?: number; y?: number; size?: number; note?: boolean}> = ({text, at, until, y = 540, size = 96, note = true}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || (until !== undefined && f >= until)) return null;
  const s = interpolate(t, [0, 6, 12], [1.6, 0.96, 1], clamp);
  const o = interpolate(t, [0, 4], [0, 1], clamp) * (until !== undefined ? 1 - prog(f, until - 10, 10) : 1);
  return (
    <div style={{position: 'absolute', left: 0, right: 0, top: y, transform: 'translateY(-50%)', textAlign: 'center', opacity: o}}>
      <div style={{display: 'inline-block', transform: `scale(${s})`, fontFamily: F.serif, fontWeight: 900, fontSize: size, color: C.cream, letterSpacing: '0.1em', textShadow: '0 0 24px rgba(0,0,0,0.9), 0 4px 0 rgba(0,0,0,0.5)'}}>{text}</div>
      {note && (
        <div style={{marginTop: 16, opacity: prog(f, at + 14, 10)}}>
          <Chip kind="宣传词" text="宣传词 · 本片所写" />
        </div>
      )}
    </div>
  );
};

/* 底部旁白条：新闻片的说明字幕。 */
export const Narr: React.FC<{lines: {at: number; text: string}[]; end?: number}> = ({lines, end}) => {
  const f = useCurrentFrame();
  const i = lines.reduce((v, l, k) => (f >= l.at ? k : v), -1);
  if (i < 0 || (end !== undefined && f >= end)) return null;
  const p = prog(f, lines[i].at, 8);
  return (
    <div style={{position: 'absolute', left: 0, right: 0, top: 968, display: 'flex', justifyContent: 'center', opacity: p}}>
      <div style={{fontFamily: F.serif, fontWeight: 700, fontSize: 32, color: C.cream, background: 'rgba(12,8,4,0.78)', padding: '6px 28px', border: `1px solid ${C.sepia}`}}>{lines[i].text}</div>
    </div>
  );
};

/* 出处角标：右下角一行小字，带性质标签。 */
export const Src: React.FC<{at: number; until?: number; kind?: Kind; text: string; y?: number}> = ({at, until, kind = '史实', text, y = 912}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 12) * (until !== undefined ? 1 - prog(f, until - 10, 10) : 1);
  if (p <= 0) return null;
  return (
    <div style={{position: 'absolute', right: 110, top: y, opacity: p, display: 'flex', gap: 10, alignItems: 'center'}}>
      <Chip kind={kind} />
      <span style={{fontFamily: F.sans, fontWeight: 500, fontSize: 18, color: C.cream, textShadow: '0 0 6px #000'}}>{text}</span>
    </div>
  );
};
