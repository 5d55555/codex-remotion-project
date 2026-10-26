import React, {createContext, useContext} from 'react';
import {AbsoluteFill, Easing, Img, interpolate, staticFile, useCurrentFrame} from 'remotion';

export const C = {
  ink: '#1d1a16',
  soft: '#4b453b',
  gray: '#7b7366',
  red: '#c3261c',
  gold: '#e2ad45',
  cream: '#efe8d8',
  navy: '#121a2a',
  water: 'rgba(96,120,132,0.22)',
};

export const F = {
  sans: "'Noto Sans SC', 'WenQuanYi Zen Hei', sans-serif",
  serif: "'Noto Serif SC', 'WenQuanYi Zen Hei', serif",
  display: "'ZCOOL QingKe HuangYou', 'Noto Sans SC', sans-serif",
  mono: "'IBM Plex Mono', 'DejaVu Sans Mono', monospace",
};

export type Theme = 'paper' | 'navy';
const ThemeCtx = createContext<Theme>('paper');
export const useTheme = () => useContext(ThemeCtx);

export const tc = (t: Theme) =>
  t === 'paper'
    ? {ink: C.ink, soft: C.soft, mute: C.gray, accent: C.red, line: '#2b2620', faint: 'rgba(43,38,32,0.22)'}
    : {ink: C.cream, soft: '#d2c9b4', mute: '#968f7c', accent: C.gold, line: '#e3dac4', faint: 'rgba(239,232,216,0.22)'};

const EASE = Easing.bezier(0.45, 0, 0.2, 1);
export const prog = (f: number, at: number, dur: number) => EASE(Math.min(1, Math.max(0, (f - at) / dur)));

export const pol = (cx: number, cy: number, r: number, deg: number): [number, number] => [
  cx + r * Math.cos((deg * Math.PI) / 180),
  cy + r * Math.sin((deg * Math.PI) / 180),
];

// 从 start 角度开始的整圆路径，便于逐笔画出。
export const circ = (cx: number, cy: number, r: number, start = -90) => {
  const [x1, y1] = pol(cx, cy, r, start);
  const [x2, y2] = pol(cx, cy, r, start + 180);
  return `M${x1} ${y1} A${r} ${r} 0 1 1 ${x2} ${y2} A${r} ${r} 0 1 1 ${x1} ${y1}`;
};

export const arc = (cx: number, cy: number, r: number, a0: number, a1: number) => {
  const [x1, y1] = pol(cx, cy, r, a0);
  const [x2, y2] = pol(cx, cy, r, a1);
  return `M${x1} ${y1} A${r} ${r} 0 ${Math.abs(a1 - a0) > 180 ? 1 : 0} ${a1 > a0 ? 1 : 0} ${x2} ${y2}`;
};

/* ---------- 文字 ---------- */

export const Blur: React.FC<{text: string; at: number; stagger?: number; dur?: number}> = ({text, at, stagger = 2, dur = 12}) => {
  const f = useCurrentFrame();
  return (
    <>
      {[...text].map((ch, i) => {
        const p = prog(f, at + i * stagger, dur);
        return (
          <span key={i} style={{display: 'inline-block', whiteSpace: 'pre', opacity: p, filter: `blur(${(1 - p) * 10}px)`, transform: `translateX(${(1 - p) * 10}px)`}}>
            {ch}
          </span>
        );
      })}
    </>
  );
};

type Anchor = 'l' | 'c' | 'r';

export const Txt: React.FC<{
  x: number;
  y: number;
  text: string;
  at?: number;
  a?: Anchor;
  size?: number;
  font?: string;
  w?: number;
  color?: string;
  ls?: number;
  fade?: boolean;
  stagger?: number;
  width?: number;
  lh?: number;
}> = ({x, y, text, at = 0, a = 'l', size = 22, font = F.sans, w = 500, color, ls, fade, stagger = 1.6, width, lh = 1.3}) => {
  const f = useCurrentFrame();
  const t = tc(useTheme());
  const tx = a === 'c' ? '-50%' : a === 'r' ? '-100%' : '0';
  const style: React.CSSProperties = {
    position: 'absolute',
    left: x,
    top: y,
    transform: `translate(${tx}, -50%)`,
    fontFamily: font,
    fontSize: size,
    fontWeight: w,
    color: color ?? t.ink,
    letterSpacing: ls ? `${ls}em` : undefined,
    whiteSpace: width ? 'normal' : 'nowrap',
    width,
    lineHeight: lh,
    textAlign: a === 'c' ? 'center' : a === 'r' ? 'right' : 'left',
  };
  if (fade) return <div style={{...style, opacity: prog(f, at, 14)}}>{text}</div>;
  return (
    <div style={style}>
      <Blur text={text} at={at} stagger={stagger} />
    </div>
  );
};

/* ---------- 图形 ---------- */

export const Svg: React.FC<{children: React.ReactNode}> = ({children}) => (
  <svg width={1920} height={1080} viewBox="0 0 1920 1080" style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
    {children}
  </svg>
);

export const P: React.FC<{d: string; at: number; dur?: number; c?: string; w?: number; dash?: string; op?: number}> = ({d, at, dur = 24, c, w = 3, dash, op = 1}) => {
  const f = useCurrentFrame();
  const t = tc(useTheme());
  const p = prog(f, at, dur);
  if (p <= 0) return null;
  const col = c ?? t.line;
  if (dash) return <path d={d} stroke={col} strokeWidth={w} strokeDasharray={dash} fill="none" opacity={op * p} strokeLinecap="round" />;
  return (
    <path d={d} pathLength={1} stroke={col} strokeWidth={w} fill="none" strokeDasharray="1 1" strokeDashoffset={1 - p} opacity={op} strokeLinecap="round" strokeLinejoin="round" />
  );
};

// 填充形状：淡入。
export const Fill: React.FC<{d: string; at: number; dur?: number; c: string; op?: number}> = ({d, at, dur = 16, c, op = 1}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, dur);
  return p > 0 ? <path d={d} fill={c} opacity={op * p} /> : null;
};

export const Dot: React.FC<{x: number; y: number; at: number; r?: number; c?: string; ring?: string}> = ({x, y, at, r = 9, c, ring}) => {
  const f = useCurrentFrame();
  const t = tc(useTheme());
  const p = prog(f, at, 12);
  if (p <= 0) return null;
  const s = interpolate(p, [0, 0.7, 1], [0, 1.2, 1]);
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      {ring && <circle r={r + 9} stroke={ring} strokeWidth={2} fill="none" />}
      <circle r={r} fill={c ?? t.ink} />
    </g>
  );
};

export const Arrow: React.FC<{pts: [number, number][]; at: number; dur?: number; c?: string; w?: number; dash?: string}> = ({pts, at, dur = 22, c, w = 3, dash}) => {
  const f = useCurrentFrame();
  const t = tc(useTheme());
  const col = c ?? t.line;
  const d = pts.length === 3 ? `M${pts[0]} Q${pts[1]} ${pts[2]}` : `M${pts.map((p) => p.join(' ')).join(' L')}`;
  const [ex, ey] = pts[pts.length - 1];
  const [px, py] = pts[pts.length - 2];
  const ang = (Math.atan2(ey - py, ex - px) * 180) / Math.PI;
  const hp = prog(f, at + dur * 0.8, 8);
  return (
    <g>
      <P d={d} at={at} dur={dur} c={col} w={w} dash={dash} />
      {hp > 0 && <polygon points="0,0 -16,-8 -16,8" fill={col} opacity={hp} transform={`translate(${ex} ${ey}) rotate(${ang})`} />}
    </g>
  );
};

export const Foot: React.FC<{x: number; y: number; ang: number; at: number; c?: string}> = ({x, y, ang, at, c}) => {
  const f = useCurrentFrame();
  const t = tc(useTheme());
  const p = prog(f, at, 8);
  return p > 0 ? (
    <g transform={`translate(${x} ${y}) rotate(${ang})`} opacity={p * 0.85} fill={c ?? t.soft}>
      <ellipse cx={-6} cy={0} rx={4} ry={8} />
      <ellipse cx={6} cy={3} rx={4} ry={8} />
    </g>
  ) : null;
};

// 一圈脚印：舞步。
export const Steps: React.FC<{cx: number; cy: number; r: number; n: number; at: number; every?: number; rx?: number; c?: string}> = ({cx, cy, r, n, at, every = 5, rx, c}) => (
  <>
    {Array.from({length: n}, (_, i) => {
      const a = -90 + (360 / n) * i;
      const x = cx + (rx ?? r) * Math.cos((a * Math.PI) / 180);
      const y = cy + r * Math.sin((a * Math.PI) / 180);
      return <Foot key={i} x={x} y={y} ang={a + 180} at={at + i * every} c={c} />;
    })}
  </>
);

export const ghostD = (x: number, y: number, s: number) =>
  `M${x - s} ${y + s * 0.9} L${x - s} ${y} C${x - s} ${y - s * 1.35} ${x + s} ${y - s * 1.35} ${x + s} ${y} L${x + s} ${y + s * 0.9} ` +
  `Q${x + s * 0.66} ${y + s * 0.55} ${x + s * 0.33} ${y + s * 0.9} Q${x} ${y + s * 0.55} ${x - s * 0.33} ${y + s * 0.9} Q${x - s * 0.66} ${y + s * 0.55} ${x - s} ${y + s * 0.9} Z`;

export const Ghost: React.FC<{x: number; y: number; s: number; at: number; c?: string; op?: number}> = ({x, y, s, at, c, op = 1}) => {
  const t = tc(useTheme());
  const col = c ?? t.line;
  return (
    <g>
      <Fill d={ghostD(x, y, s)} at={at + 10} c={col} op={0.08 * op} />
      <P d={ghostD(x, y, s)} at={at} dur={26} c={col} w={2.5} op={op} />
      <Dot x={x - s * 0.35} y={y - s * 0.15} r={s * 0.09} at={at + 20} c={col} />
      <Dot x={x + s * 0.35} y={y - s * 0.15} r={s * 0.09} at={at + 22} c={col} />
    </g>
  );
};

// 简笔人物：pose 决定四肢。
export type Pose = 'stand' | 'up' | 'dance' | 'slump' | 'reach';
export const Person: React.FC<{x: number; y: number; at: number; pose?: Pose; s?: number; c?: string; w?: number}> = ({x, y, at, pose = 'stand', s = 1, c, w = 3}) => {
  const k = (dx: number, dy: number) => `${x + dx * s} ${y + dy * s}`;
  const head = pose === 'slump' ? [12, -96] : [0, -110];
  const limbs: Record<Pose, string[]> = {
    stand: [`M${k(0, -88)} L${k(0, -20)}`, `M${k(0, -74)} L${k(-30, -36)}`, `M${k(0, -74)} L${k(30, -36)}`, `M${k(0, -20)} L${k(-20, 50)}`, `M${k(0, -20)} L${k(20, 50)}`],
    up: [`M${k(0, -88)} L${k(0, -20)}`, `M${k(0, -74)} L${k(-34, -126)}`, `M${k(0, -74)} L${k(34, -126)}`, `M${k(0, -20)} L${k(-20, 50)}`, `M${k(0, -20)} L${k(20, 50)}`],
    dance: [
      `M${k(0, -88)} C${k(2, -60)} ${k(-2, -40)} ${k(-4, -20)}`,
      `M${k(0, -76)} C${k(26, -100)} ${k(44, -120)} ${k(52, -150)}`,
      `M${k(0, -76)} C${k(-30, -70)} ${k(-56, -66)} ${k(-80, -74)}`,
      `M${k(-4, -20)} L${k(0, 50)}`,
      `M${k(-4, -20)} C${k(-40, -14)} ${k(-70, -24)} ${k(-96, -50)}`,
    ],
    slump: [`M${k(10, -76)} C${k(4, -50)} ${k(0, -36)} ${k(-2, -20)}`, `M${k(6, -66)} L${k(30, -24)}`, `M${k(6, -66)} L${k(-16, -26)}`, `M${k(-2, -20)} L${k(-18, 50)}`, `M${k(-2, -20)} L${k(16, 50)}`],
    reach: [`M${k(0, -88)} L${k(0, -20)}`, `M${k(0, -74)} L${k(52, -96)}`, `M${k(0, -74)} L${k(-30, -36)}`, `M${k(0, -20)} L${k(-20, 50)}`, `M${k(0, -20)} L${k(20, 50)}`],
  };
  return (
    <g>
      <P d={circ(x + head[0] * s, y + head[1] * s, 20 * s)} at={at} dur={16} c={c} w={w} />
      {limbs[pose].map((d, i) => (
        <P key={i} d={d} at={at + 8 + i * 4} dur={14} c={c} w={w} />
      ))}
    </g>
  );
};

// 宿命之环的小图标：圆环 + 十二刻度。
export const RingIcon: React.FC<{cx: number; cy: number; r: number; at: number; c?: string; spin?: number}> = ({cx, cy, r, at, c, spin = 0.3}) => {
  const f = useCurrentFrame();
  const t = tc(useTheme());
  const col = c ?? t.line;
  const tp = prog(f, at + 18, 18);
  return (
    <g>
      <P d={circ(cx, cy, r)} at={at} dur={26} c={col} w={3} />
      <P d={circ(cx, cy, r * 0.62)} at={at + 8} dur={22} c={col} w={1.5} />
      <g transform={`rotate(${f * spin} ${cx} ${cy})`} opacity={tp}>
        {Array.from({length: 12}, (_, i) => {
          const [x1, y1] = pol(cx, cy, r * 0.78, i * 30);
          const [x2, y2] = pol(cx, cy, r * 0.92, i * 30);
          return <line key={i} x1={x1} y1={y1} x2={x2} y2={y2} stroke={col} strokeWidth={i % 3 === 0 ? 3 : 1.5} />;
        })}
      </g>
    </g>
  );
};

export const Badge: React.FC<{x: number; y: number; at: number; kind: '原文' | '摘要' | '未找到'; src: string; a?: Anchor}> = ({x, y, at, kind, src, a = 'l'}) => {
  const f = useCurrentFrame();
  const t = tc(useTheme());
  const col = kind === '原文' ? t.accent : t.mute;
  const tx = a === 'c' ? '-50%' : a === 'r' ? '-100%' : '0';
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(${tx}, -50%)`, display: 'flex', alignItems: 'center', gap: 12, opacity: prog(f, at, 14), whiteSpace: 'nowrap'}}>
      <span style={{border: `2px ${kind === '原文' ? 'solid' : 'dashed'} ${col}`, color: col, fontFamily: F.serif, fontWeight: 900, fontSize: 17, padding: '1px 10px', letterSpacing: '0.1em'}}>{kind}</span>
      <span style={{fontFamily: F.sans, fontWeight: 500, fontSize: 17, color: t.soft}}>{src}</span>
    </div>
  );
};

/* ---------- 背景 ---------- */

export const PaperBG: React.FC = () => (
  <AbsoluteFill style={{backgroundColor: '#e2dbc7'}}>
    <Img src={staticFile('tex/paper.png')} style={{width: '100%', height: '100%', objectFit: 'cover', filter: 'saturate(0.5) brightness(1.04) contrast(0.92)'}} />
    <AbsoluteFill style={{background: 'radial-gradient(ellipse at 52% 48%, rgba(250,246,233,0.42) 0%, rgba(250,246,233,0) 55%, rgba(110,98,76,0.20) 100%)'}} />
  </AbsoluteFill>
);

export const NavyBG: React.FC<{rays?: {cx: number; cy: number}}> = ({rays}) => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill style={{backgroundColor: C.navy}}>
      <Img src={staticFile('tex/navy.png')} style={{width: '100%', height: '100%', objectFit: 'cover', opacity: 0.75}} />
      {rays && (
        <>
          <AbsoluteFill
            style={{
              background: `repeating-conic-gradient(from ${f * 0.06}deg at ${rays.cx}px ${rays.cy}px, rgba(226,173,69,0.13) 0deg 5deg, rgba(226,173,69,0) 5deg 15deg)`,
              filter: 'blur(7px)',
              WebkitMaskImage: `radial-gradient(circle at ${rays.cx}px ${rays.cy}px, black 0%, black 35%, transparent 78%)`,
              maskImage: `radial-gradient(circle at ${rays.cx}px ${rays.cy}px, black 0%, black 35%, transparent 78%)`,
            }}
          />
          <AbsoluteFill style={{background: `radial-gradient(circle at ${rays.cx}px ${rays.cy}px, rgba(226,173,69,0.42) 0%, rgba(226,173,69,0.12) 16%, rgba(226,173,69,0) 34%)`}} />
        </>
      )}
      <AbsoluteFill style={{background: 'radial-gradient(ellipse at 50% 50%, rgba(0,0,0,0) 55%, rgba(0,0,0,0.35) 100%)'}} />
    </AbsoluteFill>
  );
};

export const RedBG: React.FC = () => (
  <AbsoluteFill style={{backgroundColor: '#c0261d'}}>
    <Img src={staticFile('tex/red.png')} style={{width: '100%', height: '100%', objectFit: 'cover', filter: 'hue-rotate(-5deg) saturate(0.88) brightness(0.9)'}} />
    <AbsoluteFill style={{background: 'radial-gradient(ellipse at 50% 45%, rgba(0,0,0,0) 45%, rgba(60,0,0,0.25) 100%)'}} />
  </AbsoluteFill>
);

/* ---------- 版式：栏目条 + 序列时间轴 ---------- */

const NODES = ['舞蹈家', '托钵僧侣', '受契之人', '苦修士', '猎命师', '环中人', '受难者', '罪人', '救赎天使', '永劫者', '旧日'];
export const nodeX = (i: number) => (i < 10 ? 560 + i * 80 : 1400);

export type ChromeProps = {section: string; value: string; node: number; prev: number; fig: number; total: number};

export const Chrome: React.FC<ChromeProps> = ({section, value, node, prev, fig, total}) => {
  const f = useCurrentFrame();
  const t = tc(useTheme());
  const mx = interpolate(prog(f, 6, 30), [0, 1], [nodeX(prev), nodeX(node)]);
  const ly = 1016;
  return (
    <AbsoluteFill>
      <div style={{position: 'absolute', left: 56, top: 52, transform: 'translateY(-50%)', display: 'flex', alignItems: 'baseline', gap: 16, whiteSpace: 'nowrap'}}>
        <span style={{fontFamily: F.sans, fontWeight: 900, fontSize: 23, color: t.ink}}>宿命</span>
        <span style={{fontFamily: F.mono, fontSize: 12, letterSpacing: '0.35em', color: t.mute}}>SUMING</span>
        <span style={{fontFamily: F.sans, fontWeight: 700, fontSize: 21, color: t.ink}}>{section}</span>
      </div>
      <div style={{position: 'absolute', right: 56, top: 52, transform: 'translateY(-50%)', fontFamily: F.mono, fontSize: 15, letterSpacing: '0.22em', color: t.soft, whiteSpace: 'nowrap'}}>
        宿命之环 · CIRCLE
      </div>
      <div style={{position: 'absolute', left: 56, top: 1034, transform: 'translateY(-50%)', display: 'flex', alignItems: 'baseline', gap: 16, whiteSpace: 'nowrap'}}>
        <span style={{fontFamily: F.sans, fontSize: 13, color: t.mute}}>序列</span>
        <span style={{fontFamily: F.sans, fontWeight: 700, fontSize: 24, color: t.ink}}>{value}</span>
      </div>
      <div style={{position: 'absolute', right: 56, top: 1034, transform: 'translateY(-50%)', display: 'flex', alignItems: 'baseline', gap: 18, whiteSpace: 'nowrap'}}>
        <span style={{fontFamily: F.sans, fontSize: 13, color: t.mute}}>图</span>
        <span style={{fontFamily: F.sans, fontWeight: 700, fontSize: 26, color: t.ink}}>
          {String(fig).padStart(2, '0')} / {total}
        </span>
      </div>
      <Svg>
        <line x1={540} y1={ly} x2={1306} y2={ly} stroke={t.faint} strokeWidth={2} />
        <line x1={1332} y1={ly} x2={1400} y2={ly} stroke={t.faint} strokeWidth={2} />
        <line x1={1302} y1={ly + 7} x2={1312} y2={ly - 7} stroke={t.mute} strokeWidth={1.5} />
        <line x1={1326} y1={ly + 7} x2={1336} y2={ly - 7} stroke={t.mute} strokeWidth={1.5} />
        <line x1={540} y1={ly} x2={Math.min(mx, 1306)} y2={ly} stroke={t.ink} strokeWidth={2} />
        {mx > 1332 && <line x1={1332} y1={ly} x2={mx} y2={ly} stroke={t.ink} strokeWidth={2} />}
        {NODES.map((_, i) => (
          <line key={i} x1={nodeX(i)} y1={ly - 5} x2={nodeX(i)} y2={ly + 5} stroke={t.mute} strokeWidth={1.5} />
        ))}
        <polygon points={`${mx - 8},${ly - 22} ${mx + 8},${ly - 22} ${mx},${ly - 8}`} fill={t.ink} />
      </Svg>
      {NODES.map((n, i) => (
        <div key={n} style={{position: 'absolute', left: nodeX(i), top: ly + 22, transform: 'translate(-50%, -50%)', fontFamily: F.sans, fontSize: 14, color: i === node ? t.ink : t.soft, fontWeight: i === node ? 700 : 400, whiteSpace: 'nowrap'}}>
          {n}
        </div>
      ))}
    </AbsoluteFill>
  );
};

/* ---------- 一张“图” ---------- */

export const Figure: React.FC<{theme?: Theme; chrome: ChromeProps; tag: string[]; title: string; sub: string; rays?: {cx: number; cy: number}; children: React.ReactNode}> = ({
  theme = 'paper',
  chrome,
  tag,
  title,
  sub,
  rays,
  children,
}) => {
  const f = useCurrentFrame();
  const t = tc(theme);
  return (
    <ThemeCtx.Provider value={theme}>
      <AbsoluteFill>
        {theme === 'paper' ? <PaperBG /> : <NavyBG rays={rays} />}
        <AbsoluteFill>{children}</AbsoluteFill>
        <div style={{position: 'absolute', left: 128, top: 330, transform: 'translateY(-50%)', display: 'flex', alignItems: 'center', gap: 14, opacity: prog(f, 4, 14), whiteSpace: 'nowrap'}}>
          <span style={{width: 14, height: 14, background: C.red, display: 'inline-block'}} />
          <span style={{fontFamily: F.sans, fontWeight: 500, fontSize: 20, color: t.soft, letterSpacing: '0.04em'}}>{tag.join('  ·  ')}</span>
        </div>
        <div style={{position: 'absolute', left: 124, top: 422, transform: 'translateY(-50%)', fontFamily: F.sans, fontWeight: 900, fontSize: 80, color: t.ink, whiteSpace: 'nowrap', letterSpacing: '0.02em'}}>
          <Blur text={title} at={10} stagger={3} dur={14} />
        </div>
        <div style={{position: 'absolute', left: 128, top: 532, transform: 'translateY(-50%)', fontFamily: F.serif, fontWeight: 900, fontSize: 50, color: t.accent, whiteSpace: 'nowrap'}}>
          <Blur text={sub} at={26} stagger={2.2} dur={12} />
        </div>
        <Chrome {...chrome} />
      </AbsoluteFill>
    </ThemeCtx.Provider>
  );
};

/* ---------- 章节红卡 ---------- */

export const ChapterCard: React.FC<{glyphs: string; no: string; name: string; line1: string; foot: string}> = ({glyphs, no, name, line1, foot}) => {
  const f = useCurrentFrame();
  const dark = '#1a1411';
  const bar = prog(f, 4, 34);
  return (
    <AbsoluteFill>
      <RedBG />
      <div style={{position: 'absolute', left: 56, top: 52, transform: 'translateY(-50%)', display: 'flex', gap: 14, alignItems: 'baseline', opacity: 0.45}}>
        <span style={{fontFamily: F.sans, fontWeight: 900, fontSize: 23, color: dark}}>宿命</span>
        <span style={{fontFamily: F.mono, fontSize: 12, letterSpacing: '0.35em', color: dark}}>SUMING</span>
      </div>
      {[...glyphs].map((g, i) => {
        const p = prog(f, 6 + i * 12, 22);
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: 150 + i * 300,
              top: 612,
              transform: `translateY(-50%) scale(${1.05 - 0.05 * p})`,
              fontFamily: F.display,
              fontSize: 330,
              lineHeight: 1,
              color: dark,
              opacity: p,
              filter: `blur(${(1 - p) * 26}px)`,
            }}
          >
            {g}
          </div>
        );
      })}
      <div style={{position: 'absolute', left: 0, top: 926, height: 10, width: 1920 * bar, background: dark}} />
      <div style={{position: 'absolute', right: 120, top: 846, transform: 'translateY(-50%)', fontFamily: F.serif, fontWeight: 900, fontSize: 38, color: dark, letterSpacing: '0.14em', whiteSpace: 'nowrap'}}>
        <Blur text={`${no} · ${name}`} at={26} stagger={2.5} />
      </div>
      <div style={{position: 'absolute', right: 120, top: 893, transform: 'translateY(-50%)', fontFamily: F.sans, fontWeight: 700, fontSize: 21, color: dark, opacity: prog(f, 40, 14), whiteSpace: 'nowrap'}}>
        {line1}
      </div>
      <div style={{position: 'absolute', right: 120, top: 972, transform: 'translateY(-50%)', fontFamily: F.sans, fontWeight: 500, fontSize: 19, color: dark, opacity: 0.8 * prog(f, 50, 14), whiteSpace: 'nowrap'}}>
        {foot}
      </div>
    </AbsoluteFill>
  );
};

/* ---------- 标志卡 ---------- */

export const LogoCard: React.FC<{question: string; under?: React.ReactNode}> = ({question, under}) => {
  const f = useCurrentFrame();
  const cp = prog(f, 0, 16);
  const sp = prog(f, 8, 16);
  const g2 = prog(f, 20, 22);
  return (
    <ThemeCtx.Provider value="paper">
      <AbsoluteFill>
        <PaperBG />
        <div style={{position: 'absolute', left: 830 - 147, top: 440 - 147, width: 294, height: 294, borderRadius: '50%', background: C.red, transform: `scale(${interpolate(cp, [0, 0.75, 1], [0.6, 1.04, 1])})`, opacity: cp}} />
        <div style={{position: 'absolute', left: 943, top: 293, width: 295 * sp, height: 295, background: '#1b1714'}} />
        <div style={{position: 'absolute', left: 830, top: 440, transform: 'translate(-50%, -52%)', fontFamily: F.display, fontSize: 200, color: '#f2e9d7', opacity: prog(f, 12, 14), lineHeight: 1}}>宿</div>
        <div style={{position: 'absolute', left: 1090, top: 440, transform: 'translate(-50%, -52%)', fontFamily: F.display, fontSize: 200, color: '#f2e9d7', opacity: g2, filter: `blur(${(1 - g2) * 22}px)`, lineHeight: 1}}>命</div>
        <div style={{position: 'absolute', left: 960, top: 668, transform: 'translate(-50%, -50%)', fontFamily: F.mono, fontWeight: 500, fontSize: 22, letterSpacing: '0.34em', color: '#2b2620', opacity: prog(f, 34, 16), whiteSpace: 'nowrap'}}>
          SUMING · CIRCLE OF INEVITABILITY
        </div>
        <div style={{position: 'absolute', left: 960, top: 710, transform: 'translate(-50%, -50%)', fontFamily: F.sans, fontWeight: 500, fontSize: 19, letterSpacing: '0.22em', color: '#5b5446', opacity: prog(f, 44, 16), whiteSpace: 'nowrap'}}>
          旧日 · 恩赐途径 · 序列九至零
        </div>
        <div style={{position: 'absolute', left: 960, top: 815, transform: 'translate(-50%, -50%)', fontFamily: F.serif, fontWeight: 900, fontSize: 50, color: C.red, whiteSpace: 'nowrap'}}>
          <Blur text={question} at={64} stagger={3} />
        </div>
        {under}
      </AbsoluteFill>
    </ThemeCtx.Provider>
  );
};

export const Themed: React.FC<{theme: Theme; children: React.ReactNode}> = ({theme, children}) => <ThemeCtx.Provider value={theme}>{children}</ThemeCtx.Provider>;
