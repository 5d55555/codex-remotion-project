import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {prog} from './kit';
import {C, F, rnd} from './theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/* 纸面：米白底 + 极淡的纸纹。 */
export const Paper: React.FC<{children?: React.ReactNode; dark?: boolean}> = ({children, dark}) => (
  <AbsoluteFill style={{backgroundColor: dark ? C.ink : C.paper}}>
    <AbsoluteFill style={{backgroundImage: 'radial-gradient(rgba(0,0,0,0.05) 0.8px, transparent 1.2px)', backgroundSize: '5px 5px', opacity: dark ? 0 : 0.6}} />
    {children}
  </AbsoluteFill>
);

/* 网点：漫画阴影用的圆点网。 */
export const Tone: React.FC<{size?: number; dot?: number; color?: string; opacity?: number; style?: React.CSSProperties}> = ({size = 9, dot = 2.2, color = C.ink, opacity = 0.5, style}) => (
  <AbsoluteFill style={{backgroundImage: `radial-gradient(${color} ${dot}px, transparent ${dot + 0.6}px)`, backgroundSize: `${size}px ${size}px`, opacity, ...style}} />
);

/* 集中线：从画面四周射向中心的放射线。 */
export const SpeedLines: React.FC<{cx?: number; cy?: number; w?: number; h?: number; n?: number; inner?: number; color?: string; seed?: number}> = ({cx, cy, w = 1920, h = 1080, n = 90, inner = 0.42, color = C.ink, seed = 1}) => {
  const f = useCurrentFrame();
  const x0 = cx ?? w / 2;
  const y0 = cy ?? h / 2;
  const R = Math.hypot(w, h);
  const jig = Math.floor(f / 2);
  return (
    <svg width={w} height={h} style={{position: 'absolute', inset: 0}}>
      {Array.from({length: n}, (_, i) => {
        const a = (i / n) * Math.PI * 2 + rnd(i + seed) * 0.05;
        const r0 = Math.min(w, h) * inner * (0.85 + 0.3 * rnd(i * 3 + jig + seed));
        const wid = 2 + rnd(i * 7 + seed) * 7;
        const ex = x0 + Math.cos(a) * R;
        const ey = y0 + Math.sin(a) * R;
        const px = -Math.sin(a) * wid;
        const py = Math.cos(a) * wid;
        return <path key={i} d={`M${x0 + Math.cos(a) * r0} ${y0 + Math.sin(a) * r0} L ${ex + px} ${ey + py} L ${ex - px} ${ey - py} Z`} fill={color} />;
      })}
    </svg>
  );
};

/* 分格：粗黑框，从一侧滑入。内容在框里被裁切。 */
export const Panel: React.FC<{x: number; y: number; w: number; h: number; at: number; from?: 'l' | 'r' | 't' | 'b' | 'z'; children?: React.ReactNode; bg?: string; tilt?: number; border?: number}> = ({
  x,
  y,
  w,
  h,
  at,
  from = 'z',
  children,
  bg = C.paper,
  tilt = 0,
  border = 7,
}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 12);
  if (p <= 0) return null;
  const d = (1 - p) * 120;
  const off = {l: [-d, 0], r: [d, 0], t: [0, -d], b: [0, d], z: [0, 0]}[from];
  const s = from === 'z' ? interpolate(p, [0, 1], [1.12, 1]) : 1;
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y,
        width: w,
        height: h,
        transform: `translate(${off[0]}px, ${off[1]}px) scale(${s}) rotate(${tilt}deg)`,
        opacity: Math.min(1, p * 1.6),
        border: `${border}px solid ${C.ink}`,
        background: bg,
        overflow: 'hidden',
        boxSizing: 'border-box',
        boxShadow: '6px 8px 0 rgba(0,0,0,0.18)',
      }}
    >
      <div style={{position: 'absolute', left: -border, top: -border, width: w, height: h}}>{children}</div>
    </div>
  );
};

/* 拟声字：粗体、白描边、斜着砸进来。 */
export const Sfx: React.FC<{x: number; y: number; text: string; at: number; size?: number; rot?: number; color?: string; stroke?: string; until?: number}> = ({x, y, text, at, size = 120, rot = -8, color = C.ink, stroke = C.paper, until}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || (until !== undefined && f >= until)) return null;
  const s = interpolate(t, [0, 5, 10], [1.8, 0.92, 1], clamp);
  const shake = t < 10 ? (rnd(f) - 0.5) * 8 : 0;
  return (
    <div
      style={{
        position: 'absolute',
        left: x + shake,
        top: y,
        transform: `translate(-50%, -50%) rotate(${rot}deg) scale(${s})`,
        fontFamily: F.pop,
        fontSize: size,
        lineHeight: 1,
        color,
        whiteSpace: 'nowrap',
        WebkitTextStroke: `${Math.max(3, size / 14)}px ${stroke}`,
        paintOrder: 'stroke fill',
        letterSpacing: '0.04em',
      }}
    >
      {text}
    </div>
  );
};

/* 对话框：白底椭圆 + 尾巴；shout 时边缘变成锯齿。 */
export const Bubble: React.FC<{x: number; y: number; w: number; h: number; at: number; text: string; size?: number; tail?: {x: number; y: number}; shout?: boolean; until?: number; font?: string}> = ({
  x,
  y,
  w,
  h,
  at,
  text,
  size = 34,
  tail,
  shout,
  until,
  font = F.sans,
}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 8) * (until !== undefined ? 1 - prog(f, until - 8, 8) : 1);
  if (p <= 0) return null;
  const n = Math.floor(interpolate(f - at, [2, 2 + text.length * 1.4], [0, text.length], clamp));
  const cx = w / 2;
  const cy = h / 2;
  const spikes = shout
    ? Array.from({length: 28}, (_, i) => {
        const a = (i / 28) * Math.PI * 2;
        const r = i % 2 ? 1 : 1.16;
        return `${cx + Math.cos(a) * cx * r},${cy + Math.sin(a) * cy * r}`;
      }).join(' ')
    : '';
  return (
    <div style={{position: 'absolute', left: x, top: y, width: w, height: h, transform: `scale(${p})`, transformOrigin: tail ? `${tail.x - x}px ${tail.y - y}px` : 'center'}}>
      <svg width={w} height={h} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
        {tail && <path d={`M${cx - 26} ${cy + cy * 0.6} L ${tail.x - x} ${tail.y - y} L ${cx + 26} ${cy + cy * 0.6} Z`} fill="#fff" stroke={C.ink} strokeWidth={5} strokeLinejoin="round" />}
        {shout ? <polygon points={spikes} fill="#fff" stroke={C.ink} strokeWidth={5} strokeLinejoin="round" /> : <ellipse cx={cx} cy={cy} rx={cx - 3} ry={cy - 3} fill="#fff" stroke={C.ink} strokeWidth={5} />}
        {tail && <path d={`M${cx - 22} ${cy + cy * 0.55} L ${cx + 22} ${cy + cy * 0.55}`} stroke="#fff" strokeWidth={8} />}
      </svg>
      <div style={{position: 'absolute', inset: `${h * 0.16}px ${w * 0.12}px`, display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', fontFamily: font, fontWeight: 900, fontSize: size, color: C.ink, lineHeight: 1.35}}>
        <span>
          {text.slice(0, n)}
          <span style={{opacity: 0}}>{text.slice(n)}</span>
        </span>
      </div>
    </div>
  );
};

/* 旁白框：方框，黑底白字或白底黑字。 */
export const Caption: React.FC<{x: number; y: number; w?: number; at: number; text: string; size?: number; dark?: boolean; until?: number}> = ({x, y, w, at, text, size = 28, dark = true, until}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10) * (until !== undefined ? 1 - prog(f, until - 8, 8) : 1);
  if (p <= 0) return null;
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y,
        width: w,
        opacity: p,
        transform: `translateY(${(1 - p) * 10}px)`,
        background: dark ? C.ink : '#fff',
        color: dark ? '#fff' : C.ink,
        border: `4px solid ${C.ink}`,
        padding: '10px 18px',
        fontFamily: F.serif,
        fontWeight: 700,
        fontSize: size,
        lineHeight: 1.45,
        boxSizing: 'border-box',
      }}
    >
      {text}
    </div>
  );
};

/* 出处条：标明卷章与证据等级。 */
export type Ev = '正文' | '作者' | 'Wiki' | '读者';
export const Src: React.FC<{x: number; y: number; at: number; ev: Ev; text: string; a?: 'l' | 'r'; until?: number}> = ({x, y, at, ev, text, a = 'l', until}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10) * (until !== undefined ? 1 - prog(f, until - 8, 8) : 1);
  if (p <= 0) return null;
  const solid = ev === '正文' || ev === '作者';
  return (
    <div style={{position: 'absolute', left: a === 'l' ? x : undefined, right: a === 'r' ? 1920 - x : undefined, top: y, opacity: p, display: 'flex', gap: 10, alignItems: 'center'}}>
      <span style={{fontFamily: F.sans, fontWeight: 900, fontSize: 17, padding: '1px 8px', background: solid ? C.ink : 'transparent', color: solid ? '#fff' : C.ink, border: `2px ${solid ? 'solid' : 'dashed'} ${C.ink}`}}>{ev}</span>
      <span style={{fontFamily: F.sans, fontWeight: 700, fontSize: 18, color: C.ink, background: 'rgba(246,243,236,0.9)', padding: '0 6px'}}>{text}</span>
    </div>
  );
};

/* 正字计数：每 5 次一个“正”。按笔画出现。 */
const ZHENG = [
  'M10 8 L 50 8',
  'M30 8 L 30 52',
  'M30 30 L 46 30',
  'M14 30 L 14 52',
  'M6 52 L 54 52',
];
export const Tally: React.FC<{count: number; x?: number; y?: number; right?: number; label?: string; bumpAt?: number}> = ({count, x = 1560, y = 40, right, label = '受苦计数', bumpAt = -99}) => {
  const f = useCurrentFrame();
  const bump = f - bumpAt >= 0 && f - bumpAt < 12 ? 1 + 0.25 * (1 - (f - bumpAt) / 12) : 1;
  const groups = Math.ceil(count / 5);
  return (
    <div style={{position: 'absolute', left: right === undefined ? x : undefined, right, top: y, background: '#fff', border: `5px solid ${C.ink}`, padding: '8px 14px', transform: `scale(${bump})`, transformOrigin: 'right top', boxShadow: '5px 6px 0 rgba(0,0,0,0.2)'}}>
      <div style={{fontFamily: F.sans, fontWeight: 900, fontSize: 18, color: C.ink, letterSpacing: '0.2em'}}>{label}</div>
      <div style={{display: 'flex', gap: 6, alignItems: 'center', marginTop: 4}}>
        <svg width={Math.max(60, groups * 62)} height={60}>
          {Array.from({length: groups}, (_, g) => (
            <g key={g} transform={`translate(${g * 62} 0)`}>
              {ZHENG.slice(0, Math.min(5, count - g * 5)).map((d, i) => (
                <path key={i} d={d} stroke={C.red} strokeWidth={7} strokeLinecap="round" fill="none" />
              ))}
            </g>
          ))}
        </svg>
        <span style={{fontFamily: F.mono, fontWeight: 500, fontSize: 34, color: C.ink}}>{count}</span>
      </div>
    </div>
  );
};

/* 印章：盖上去的方框字，例如“立 flag”“回收”。 */
export const Stamp: React.FC<{x: number; y: number; text: string; at: number; color?: string; rot?: number; size?: number}> = ({x, y, text, at, color = C.red, rot = -10, size = 64}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0) return null;
  const s = interpolate(t, [0, 4, 8], [2.4, 0.9, 1], clamp);
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(-50%, -50%) rotate(${rot}deg) scale(${s})`, opacity: interpolate(t, [0, 3], [0, 0.92], clamp), border: `6px solid ${color}`, color, padding: '4px 18px', fontFamily: F.sans, fontWeight: 900, fontSize: size, lineHeight: 1.1, whiteSpace: 'nowrap', mixBlendMode: 'multiply'}}>
      {text}
    </div>
  );
};

/* 卷标：左上角“第一卷 梦魇”样式的章节标签。 */
export const VolTag: React.FC<{vol: string; name: string; at?: number}> = ({vol, name, at = 0}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10);
  return (
    <div style={{position: 'absolute', left: 40, top: 36, opacity: p, transform: `translateX(${(1 - p) * -30}px)`, display: 'flex', alignItems: 'stretch', border: `5px solid ${C.ink}`, boxShadow: '5px 6px 0 rgba(0,0,0,0.2)'}}>
      <div style={{background: C.ink, color: '#fff', fontFamily: F.sans, fontWeight: 900, fontSize: 26, padding: '6px 14px'}}>{vol}</div>
      <div style={{background: '#fff', color: C.ink, fontFamily: F.serif, fontWeight: 900, fontSize: 30, padding: '4px 18px'}}>{name}</div>
    </div>
  );
};

/* 血点：红墨飞溅。 */
export const Splat: React.FC<{x: number; y: number; at: number; r?: number; seed?: number}> = ({x, y, at, r = 60, seed = 1}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 6);
  if (p <= 0) return null;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
      <g transform={`translate(${x} ${y}) scale(${p})`} fill={C.red}>
        <circle r={r * 0.55} />
        {Array.from({length: 11}, (_, i) => {
          const a = rnd(i + seed) * Math.PI * 2;
          const d = r * (0.6 + rnd(i * 3 + seed) * 0.9);
          return <circle key={i} cx={Math.cos(a) * d} cy={Math.sin(a) * d} r={r * (0.06 + rnd(i * 5 + seed) * 0.16)} />;
        })}
      </g>
    </svg>
  );
};
