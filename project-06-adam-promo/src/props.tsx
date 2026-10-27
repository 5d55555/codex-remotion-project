import React from 'react';
import {AbsoluteFill, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {Chip, Kind} from './reel';
import {prog} from './kit';
import {C, F, rnd} from './theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/* ---------- 城市天际线：楼房、烟囱、尖塔、钟楼，烟囱冒烟 ---------- */

type Bldg = {x: number; w: number; h: number; chimney: number; spire: boolean; clock: boolean};
const BLDGS: Bldg[] = (() => {
  const out: Bldg[] = [];
  let x = -40;
  let i = 0;
  while (x < 1960) {
    const w = 70 + rnd(i * 3.1) * 120;
    const h = 140 + rnd(i * 5.7) * 300;
    out.push({x, w, h, chimney: rnd(i * 7.3) < 0.55 ? 1 + Math.floor(rnd(i * 9.1) * 3) : 0, spire: rnd(i * 2.9) < 0.12, clock: i === 9});
    x += w + 4;
    i++;
  }
  return out;
})();

export const Skyline: React.FC<{base?: number; shade?: string; smoke?: number; windows?: number; scale?: number}> = ({base = 900, shade = '#2a221a', smoke = 1, windows = 0.5, scale = 1}) => {
  const f = useCurrentFrame();
  const puffs: React.ReactNode[] = [];
  BLDGS.forEach((b, i) => {
    for (let c = 0; c < b.chimney; c++) {
      const cx = b.x + 14 + c * 22;
      const top = base - b.h * scale - 46;
      for (let k = 0; k < 7; k++) {
        const life = 90;
        const t = ((f + k * (life / 7) + i * 13 + c * 29) % life) / life;
        puffs.push(<circle key={`${i}-${c}-${k}`} cx={cx + 6 + t * 120 + Math.sin(t * 6 + i) * 10} cy={top - t * 260} r={10 + t * 46} fill="#cfc6b4" opacity={(1 - t) * 0.42 * smoke} />);
      }
    }
  });
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      <g style={{filter: 'blur(6px)'}}>{puffs}</g>
      {BLDGS.map((b, i) => {
        const h = b.h * scale;
        return (
          <g key={i} fill={shade}>
            <rect x={b.x} y={base - h} width={b.w} height={h + 200} />
            {Array.from({length: b.chimney}, (_, c) => (
              <rect key={c} x={b.x + 14 + c * 22} y={base - h - 46} width={14} height={50} />
            ))}
            {b.spire && <path d={`M${b.x} ${base - h} L ${b.x + b.w / 2} ${base - h - 160} L ${b.x + b.w} ${base - h} Z`} />}
            {b.clock && (
              <g>
                <rect x={b.x + b.w / 2 - 34} y={base - h - 220} width={68} height={224} />
                <path d={`M${b.x + b.w / 2 - 40} ${base - h - 220} L ${b.x + b.w / 2} ${base - h - 300} L ${b.x + b.w / 2 + 40} ${base - h - 220} Z`} />
                <circle cx={b.x + b.w / 2} cy={base - h - 170} r={22} fill="#d8cdb4" />
                <line x1={b.x + b.w / 2} y1={base - h - 170} x2={b.x + b.w / 2} y2={base - h - 186} stroke={shade} strokeWidth={3} />
                <line x1={b.x + b.w / 2} y1={base - h - 170} x2={b.x + b.w / 2 + 12} y2={base - h - 170} stroke={shade} strokeWidth={3} />
              </g>
            )}
            {Array.from({length: Math.floor(h / 40)}, (_, r) =>
              Array.from({length: Math.floor(b.w / 26)}, (_, c) =>
                rnd(i * 31 + r * 7 + c) < windows ? <rect key={`${r}-${c}`} x={b.x + 8 + c * 26} y={base - h + 16 + r * 40} width={10} height={16} fill="#e9d9a8" opacity={0.55} /> : null,
              ),
            )}
          </g>
        );
      })}
    </svg>
  );
};

/* ---------- 蒸汽机图纸：飞轮、曲柄、连杆、活塞 ---------- */

export const SteamEngine: React.FC<{at?: number; speed?: number}> = ({at = 0, speed = 0.12}) => {
  const f = useCurrentFrame();
  const draw = prog(f, at, 40);
  const th = (f - at) * speed;
  const cx = 1240;
  const cy = 540;
  const R = 110;
  const L = 360;
  const pin = {x: cx + R * Math.cos(th), y: cy + R * Math.sin(th)};
  const px = pin.x - Math.sqrt(L * L - (pin.y - cy) ** 2);
  const line = '#efe4cc';
  const dash = {strokeDasharray: 3000, strokeDashoffset: 3000 * (1 - draw)};
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      <defs>
        <pattern id="bp" width={40} height={40} patternUnits="userSpaceOnUse">
          <path d="M40 0 L 0 0 0 40" fill="none" stroke="#efe4cc" strokeWidth={0.6} opacity={0.18} />
        </pattern>
      </defs>
      <rect width={1920} height={1080} fill="#2e3a44" />
      <rect width={1920} height={1080} fill="url(#bp)" />
      <g fill="none" stroke={line} strokeWidth={3} style={dash}>
        {/* 锅炉 */}
        <rect x={260} y={420} width={360} height={240} rx={110} />
        <rect x={300} y={330} width={50} height={90} />
        {/* 气缸 */}
        <rect x={640} y={500} width={260} height={80} />
        <line x1={620} y1={540} x2={640} y2={540} />
        {/* 底座 */}
        <rect x={240} y={700} width={1180} height={24} />
      </g>
      <g fill="none" stroke={line} strokeWidth={3} opacity={draw}>
        {/* 活塞与连杆 */}
        <rect x={Math.min(880, Math.max(650, px - 80))} y={506} width={24} height={68} fill="#efe4cc" fillOpacity={0.25} />
        <line x1={px} y1={cy} x2={pin.x} y2={pin.y} strokeWidth={6} />
        <circle cx={pin.x} cy={pin.y} r={10} />
        {/* 飞轮 */}
        <circle cx={cx} cy={cy} r={230} strokeWidth={10} />
        <circle cx={cx} cy={cy} r={200} strokeWidth={2} />
        {Array.from({length: 8}, (_, i) => {
          const a = th + (i * Math.PI) / 4;
          return <line key={i} x1={cx + Math.cos(a) * 24} y1={cy + Math.sin(a) * 24} x2={cx + Math.cos(a) * 200} y2={cy + Math.sin(a) * 200} />;
        })}
        <circle cx={cx} cy={cy} r={24} />
        <line x1={cx} y1={cy} x2={pin.x} y2={pin.y} strokeWidth={8} />
      </g>
      <g fontFamily={F.mono} fontSize={18} fill={line} opacity={prog(f, at + 40, 20)}>
        <text x={270} y={400}>BOILER</text>
        <text x={660} y={490}>CYLINDER</text>
        <text x={1180} y={800}>FLYWHEEL</text>
        <text x={1500} y={300}>Fig. 1</text>
      </g>
    </svg>
  );
};

/* ---------- 火车：机车 + 车厢，冒着烟穿过画面 ---------- */

export const Train: React.FC<{at: number; dur?: number; y?: number}> = ({at, dur = 160, y = 900}) => {
  const f = useCurrentFrame();
  const k = prog(f, at, dur);
  if (f < at) return null;
  const x = -900 + k * 3000;
  const shade = '#1a140f';
  const puffs = Array.from({length: 14}, (_, i) => {
    const life = 40;
    const t = ((f + i * 3) % life) / life;
    return <circle key={i} cx={x + 640 - t * 260} cy={y - 150 - t * 120} r={14 + t * 40} fill="#d8cfbd" opacity={(1 - t) * 0.5} />;
  });
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      <g style={{filter: 'blur(5px)'}}>{puffs}</g>
      <g fill={shade}>
        {[0, 1, 2].map((c) => (
          <g key={c}>
            <rect x={x + c * 190} y={y - 110} width={170} height={80} />
            {[0, 1, 2, 3].map((w) => (
              <rect key={w} x={x + c * 190 + 14 + w * 38} y={y - 96} width={22} height={24} fill="#e9d9a8" opacity={0.6} />
            ))}
            <circle cx={x + c * 190 + 30} cy={y - 22} r={16} />
            <circle cx={x + c * 190 + 140} cy={y - 22} r={16} />
          </g>
        ))}
        <rect x={x + 580} y={y - 130} width={70} height={100} />
        <rect x={x + 650} y={y - 100} width={160} height={70} rx={30} />
        <rect x={x + 690} y={y - 150} width={22} height={50} />
        <path d={`M${x + 810} ${y - 30} L ${x + 850} ${y - 30} L ${x + 810} ${y - 70} Z`} />
        {[0, 1, 2].map((w) => (
          <circle key={w} cx={x + 620 + w * 70} cy={y - 26} r={26} />
        ))}
      </g>
      <rect x={0} y={y - 4} width={1920} height={4} fill={shade} />
    </svg>
  );
};

/* ---------- 飞艇空袭：飞艇、探照灯、落弹、爆炸 ---------- */

export const Airships: React.FC<{at: number; bombs?: number[]}> = ({at, bombs = []}) => {
  const f = useCurrentFrame();
  if (f < at) return null;
  const t = f - at;
  const ships = [
    {y: 200, s: 1, v: 3.2, x0: -500},
    {y: 300, s: 0.7, v: 2.4, x0: -900},
    {y: 150, s: 0.55, v: 2.0, x0: -200},
  ];
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {[300, 900, 1500].map((bx, i) => {
        const a = Math.sin(t * 0.03 + i * 2) * 25 - 90;
        const rad = (a * Math.PI) / 180;
        const len = 1100;
        const ex = bx + Math.cos(rad) * len;
        const ey = 900 + Math.sin(rad) * len;
        const px = -Math.sin(rad) * 60;
        const py = Math.cos(rad) * 60;
        return <path key={i} d={`M${bx} 900 L ${ex + px} ${ey + py} L ${ex - px} ${ey - py} Z`} fill="#fff6dc" opacity={0.16} />;
      })}
      {ships.map((s, i) => {
        const x = s.x0 + t * s.v;
        return (
          <g key={i} transform={`translate(${x} ${s.y}) scale(${s.s})`} fill="#1a140f">
            <ellipse cx={0} cy={0} rx={260} ry={64} />
            <path d="M-250 0 L -320 -60 L -300 0 L -320 60 Z" />
            <rect x={-60} y={58} width={120} height={30} rx={6} />
            {[-180, -90, 0, 90, 180].map((rx) => (
              <line key={rx} x1={rx} y1={-60} x2={rx} y2={60} stroke="#3a2f24" strokeWidth={2} />
            ))}
          </g>
        );
      })}
      {bombs.map((b, i) => {
        const bt = t - b;
        if (bt < 0 || bt > 40) return null;
        const bx = 300 + rnd(i * 7) * 1300;
        if (bt < 14) return <circle key={i} cx={bx} cy={260 + bt * 44} r={6} fill="#1a140f" />;
        const k = (bt - 14) / 26;
        return (
          <g key={i}>
            <circle cx={bx} cy={880} r={40 + k * 220} fill="#fff1c8" opacity={(1 - k) * 0.85} />
            <circle cx={bx} cy={880} r={20 + k * 120} fill="#ffffff" opacity={(1 - k) * 0.9} />
          </g>
        );
      })}
    </svg>
  );
};

/* ---------- 号外：旋转着砸进画面的报纸 ---------- */

export const Newspaper: React.FC<{at: number; until?: number; date: string; headline: string; subs: string[]; note?: string; rot?: number}> = ({at, until, date, headline, subs, note, rot = -3}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || (until !== undefined && f >= until)) return null;
  const spin = interpolate(t, [0, 18], [720, rot], {...clamp, easing: (v) => 1 - (1 - v) ** 3});
  const s = interpolate(t, [0, 18], [0.08, 1], {...clamp, easing: (v) => 1 - (1 - v) ** 3});
  const o = until !== undefined ? 1 - prog(f, until - 10, 10) : 1;
  return (
    <div
      style={{
        position: 'absolute',
        left: 960,
        top: 520,
        width: 1300,
        height: 860,
        transform: `translate(-50%, -50%) rotate(${spin}deg) scale(${s})`,
        opacity: o,
        backgroundImage: `url(${staticFile('tex/newsprint.png')})`,
        backgroundSize: 'cover',
        boxShadow: '0 30px 80px rgba(0,0,0,0.7)',
        color: C.ink,
        padding: '40px 60px',
        boxSizing: 'border-box',
      }}
    >
      <div style={{textAlign: 'center', fontFamily: F.serif, fontWeight: 900, fontSize: 96, letterSpacing: '0.5em', borderBottom: `4px double ${C.ink}`, paddingBottom: 6}}>号 外</div>
      <div style={{display: 'flex', justifyContent: 'space-between', fontFamily: F.serif, fontWeight: 700, fontSize: 22, borderBottom: `2px solid ${C.ink}`, padding: '6px 0'}}>
        <span>{date}</span>
        <span>贝克兰德</span>
      </div>
      <div style={{textAlign: 'center', fontFamily: F.serif, fontWeight: 900, fontSize: 132, lineHeight: 1.15, marginTop: 20}}>{headline}</div>
      {subs.map((s) => (
        <div key={s} style={{textAlign: 'center', fontFamily: F.serif, fontWeight: 700, fontSize: 40, marginTop: 10}}>
          {s}
        </div>
      ))}
      <div style={{display: 'flex', gap: 24, marginTop: 28}}>
        {[0, 1, 2, 3].map((c) => (
          <div key={c} style={{flex: 1}}>
            {Array.from({length: 9}, (_, r) => (
              <div key={r} style={{height: 8, marginBottom: 10, background: C.ink, opacity: 0.35, width: `${70 + rnd(c * 20 + r) * 30}%`}} />
            ))}
          </div>
        ))}
      </div>
      {note && <div style={{position: 'absolute', left: 60, right: 60, bottom: 28, fontFamily: F.sans, fontWeight: 500, fontSize: 20, color: '#5a4a36'}}>{note}</div>}
    </div>
  );
};

/* ---------- 羽毛笔：在纸上写出一行潦草的字迹 ---------- */

// 连笔字迹：摆线带出一个个小圈，振幅随“字”起伏，中间断开两处当作词间空格。
const SCRIBBLE = Array.from({length: 420}, (_, i) => {
  const t = i / 419;
  const amp = 16 + 10 * Math.sin(t * 23) + 6 * Math.sin(t * 7);
  return {x: 470 + t * 960 - amp * Math.sin(t * 74), y: 600 - amp * 1.3 * Math.cos(t * 74) + Math.sin(t * 5) * 6, gap: (t > 0.31 && t < 0.34) || (t > 0.66 && t < 0.69)};
});

export const Quill: React.FC<{at: number; dur?: number}> = ({at, dur = 120}) => {
  const f = useCurrentFrame();
  const k = prog(f, at, dur);
  const n = Math.max(1, Math.floor(k * (SCRIBBLE.length - 1)));
  const tip = SCRIBBLE[n];
  const d = SCRIBBLE.slice(0, n + 1)
    .map((p, i) => `${i === 0 || p.gap ? 'M' : 'L'}${p.x} ${p.y}`)
    .join(' ');
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      <path d={d} fill="none" stroke={C.ink} strokeWidth={4} strokeLinecap="round" strokeLinejoin="round" />
      <g transform={`translate(${tip.x} ${tip.y}) rotate(-38)`}>
        <path d="M0 0 L 6 -40 C 30 -120 70 -260 40 -420 C 0 -300 -30 -160 -6 -40 Z" fill="#f4ecd8" stroke={C.ink} strokeWidth={3} />
        <path d="M0 0 L 30 -380" stroke={C.ink} strokeWidth={2} />
        {Array.from({length: 12}, (_, i) => (
          <line key={i} x1={8 + i * 2} y1={-80 - i * 26} x2={40 + i * 1.5} y2={-110 - i * 26} stroke={C.ink} strokeWidth={1.2} opacity={0.6} />
        ))}
      </g>
    </svg>
  );
};

/* ---------- 行军队列：一排排戴军帽、扛枪的剪影 ---------- */

export const March: React.FC<{at?: number; rows?: number; speed?: number}> = ({at = 0, rows = 4, speed = 3}) => {
  const f = useCurrentFrame();
  if (f < at) return null;
  const t = f - at;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {Array.from({length: rows}, (_, r) => {
        const y = 760 + r * 70;
        const s = 0.7 + r * 0.15;
        const shade = r === rows - 1 ? '#120d08' : `rgba(18,13,8,${0.5 + r * 0.15})`;
        return (
          <g key={r} fill={shade}>
            {Array.from({length: 26}, (_, i) => {
              const x = ((i * 90 + t * speed * s + r * 40) % 2400) - 240;
              const bob = Math.abs(Math.sin((t * 0.2 + i) * 1.2)) * 6;
              return (
                <g key={i} transform={`translate(${x} ${y - bob}) scale(${s})`}>
                  <circle cx={0} cy={-120} r={18} />
                  <rect x={-20} y={-146} width={40} height={12} rx={4} />
                  <rect x={-24} y={-100} width={48} height={90} rx={10} />
                  <rect x={-20} y={-12} width={16} height={60} />
                  <rect x={4} y={-12} width={16} height={60} />
                  <rect x={22} y={-190} width={5} height={130} transform="rotate(12 24 -100)" />
                </g>
              );
            })}
          </g>
        );
      })}
    </svg>
  );
};

/* ---------- 天上的神战：几团巨大的光在云间相撞 ---------- */

export const SkyWar: React.FC<{at?: number}> = ({at = 0}) => {
  const f = useCurrentFrame();
  if (f < at) return null;
  const t = f - at;
  const orbs = [0, 1, 2, 3, 4].map((i) => ({
    x: 960 + Math.cos(t * 0.02 + i * 1.3) * (380 + i * 40),
    y: 300 + Math.sin(t * 0.03 + i * 2.1) * 120,
    r: 60 + i * 14 + Math.sin(t * 0.2 + i) * 10,
  }));
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {orbs.map((o, i) => (
        <g key={i}>
          <circle cx={o.x} cy={o.y} r={o.r * 2.4} fill="#fff4d6" opacity={0.08} />
          <circle cx={o.x} cy={o.y} r={o.r} fill="#fff4d6" opacity={0.75} style={{filter: 'blur(4px)'}} />
        </g>
      ))}
      {orbs.slice(1).map((o, i) => {
        if (Math.floor((t + i * 7) / 9) % 3) return null;
        const a = orbs[i];
        const pts = Array.from({length: 9}, (_, k) => {
          const u = k / 8;
          return `${a.x + (o.x - a.x) * u + (k % 8 ? (rnd(t + k + i) - 0.5) * 60 : 0)},${a.y + (o.y - a.y) * u + (k % 8 ? (rnd(t * 3 + k) - 0.5) * 60 : 0)}`;
        });
        return <polyline key={i} points={pts.join(' ')} fill="none" stroke="#ffffff" strokeWidth={4} opacity={0.85} />;
      })}
    </svg>
  );
};

/* ---------- 黄昏隐士会徽记（本片设计）：落在地平线上的半个太阳 ---------- */

export const Emblem: React.FC<{x: number; y: number; r?: number; at?: number; color?: string}> = ({x, y, r = 120, at = 0, color = C.cream}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 20);
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: p}}>
      <g transform={`translate(${x} ${y}) rotate(${(1 - p) * -30})`} fill="none" stroke={color}>
        <circle r={r} strokeWidth={3} />
        <circle r={r - 12} strokeWidth={1.2} />
        <path d={`M${-r * 0.55} ${r * 0.12} A ${r * 0.55} ${r * 0.55} 0 0 1 ${r * 0.55} ${r * 0.12} Z`} fill={color} />
        <line x1={-r * 0.8} y1={r * 0.12} x2={r * 0.8} y2={r * 0.12} strokeWidth={3} />
        {Array.from({length: 9}, (_, i) => {
          const a = Math.PI + (i * Math.PI) / 8;
          return <line key={i} x1={Math.cos(a) * r * 0.62} y1={r * 0.12 + Math.sin(a) * r * 0.62} x2={Math.cos(a) * r * 0.8} y2={r * 0.12 + Math.sin(a) * r * 0.8} strokeWidth={2.5} />;
        })}
        {Array.from({length: 3}, (_, i) => (
          <line key={i} x1={-r * 0.5 + i * 10} y1={r * 0.3 + i * 12} x2={r * 0.5 - i * 10} y2={r * 0.3 + i * 12} strokeWidth={1.5} />
        ))}
      </g>
    </svg>
  );
};

/* ---------- 宗旨文书：海报纸、花边、引文、火漆印 ---------- */

export const Charter: React.FC<{at: number; title: string; children: React.ReactNode}> = ({at, title, children}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 20);
  return (
    <div
      style={{
        position: 'absolute',
        left: 960,
        top: 540,
        width: 1240,
        height: 920,
        transform: `translate(-50%, -50%) translateY(${(1 - p) * 60}px) rotate(${(1 - p) * 4 - 1}deg)`,
        opacity: p,
        backgroundImage: `url(${staticFile('tex/poster.png')})`,
        backgroundSize: 'cover',
        boxShadow: '0 30px 80px rgba(0,0,0,0.7)',
        color: C.ink,
        border: `14px double ${C.ink}`,
        boxSizing: 'border-box',
        padding: '56px 90px',
      }}
    >
      <div style={{textAlign: 'center', fontFamily: F.serif, fontWeight: 900, fontSize: 70, letterSpacing: '0.4em'}}>{title}</div>
      <div style={{height: 3, background: C.ink, margin: '14px 120px 30px'}} />
      {children}
    </div>
  );
};

/* 文书里的一条：引文 + 标签 + 出处。 */
export const Clause: React.FC<{at: number; text: string; kind: Kind; who: string; src: string; size?: number}> = ({at, text, kind, who, src, size = 34}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 14);
  return (
    <div style={{opacity: p, transform: `translateY(${(1 - p) * 12}px)`, marginBottom: 26}}>
      <div style={{fontFamily: F.serif, fontWeight: 900, fontSize: size, lineHeight: 1.5}}>{text}</div>
      <div style={{display: 'flex', gap: 12, alignItems: 'center', marginTop: 6}}>
        <span style={{filter: 'invert(1)'}}>
          <Chip kind={kind} />
        </span>
        <span style={{fontFamily: F.sans, fontWeight: 700, fontSize: 19}}>{who}</span>
        <span style={{fontFamily: F.sans, fontWeight: 500, fontSize: 17, color: '#5a4a36'}}>{src}</span>
      </div>
    </div>
  );
};

/* ---------- 小教堂：拱窗、光束、十字、长椅 ---------- */

export const Chapel: React.FC<{light?: number}> = ({light = 1}) => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill style={{backgroundColor: '#1a140e'}}>
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
        <defs>
          <linearGradient id="beam" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#fff2d0" stopOpacity={0.45 * light} />
            <stop offset="1" stopColor="#fff2d0" stopOpacity={0} />
          </linearGradient>
        </defs>
        <rect x={0} y={0} width={1920} height={1080} fill="#241b13" />
        {[420, 1380].map((wx) => (
          <g key={wx}>
            <path d={`M${wx - 90} 520 L ${wx - 90} 260 A 90 90 0 0 1 ${wx + 90} 260 L ${wx + 90} 520 Z`} fill="#e9dcb8" opacity={0.8 * light} />
            <line x1={wx} y1={170} x2={wx} y2={520} stroke="#241b13" strokeWidth={8} />
            <line x1={wx - 90} y1={360} x2={wx + 90} y2={360} stroke="#241b13" strokeWidth={8} />
            <path d={`M${wx - 90} 520 L ${wx + 90} 520 L ${wx + 520} 1080 L ${wx + 160} 1080 Z`} fill="url(#beam)" style={{filter: 'blur(10px)'}} />
          </g>
        ))}
        <rect x={930} y={120} width={60} height={300} fill="#3a2d20" />
        <rect x={860} y={190} width={200} height={50} fill="#3a2d20" />
        <rect x={640} y={720} width={640} height={60} fill="#3a2d20" />
        <rect x={680} y={780} width={560} height={140} fill="#2e2318" />
        {[0, 1, 2].map((r) => (
          <g key={r}>
            <rect x={120} y={900 + r * 60} width={620} height={34} fill="#120d08" />
            <rect x={1180} y={900 + r * 60} width={620} height={34} fill="#120d08" />
          </g>
        ))}
        {Array.from({length: 30}, (_, i) => {
          const x = 300 + rnd(i) * 1300;
          const y = ((rnd(i * 3) * 700 + f * (0.3 + rnd(i) * 0.4)) % 700) + 200;
          return <circle key={i} cx={x} cy={y} r={1.5 + rnd(i * 5) * 2} fill="#fff2d0" opacity={0.35 * light} />;
        })}
      </svg>
    </AbsoluteFill>
  );
};

/*
 * 神父（亚当）：简朴白袍、淡金胡须、银十字吊坠——正文里的样子。
 * lift 0—1 举起杯子；drink 0—1 杯子送到嘴边。手的位置直接插值，手臂画成从肩到手的袖子。
 */
const handAt = (lift: number, drink: number) => {
  const rest = {x: 140, y: -170};
  const up = {x: 170, y: -380};
  const mouth = {x: 40, y: -384};
  const a = {x: rest.x + (up.x - rest.x) * lift, y: rest.y + (up.y - rest.y) * lift};
  return {x: a.x + (mouth.x - a.x) * drink, y: a.y + (mouth.y - a.y) * drink};
};

export const Priest: React.FC<{x?: number; y?: number; s?: number; lift?: number; drink?: number}> = ({x = 960, y = 760, s = 1, lift = 0, drink = 0}) => {
  const h = handAt(lift, drink);
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
      <g transform={`translate(${x} ${y}) scale(${s})`}>
        <path d="M-150 0 C -140 -160 -120 -300 -70 -350 L 70 -350 C 120 -300 140 -160 150 0 Z" fill="#f2ecdc" stroke="#6a5a44" strokeWidth={3} />
        <path d="M0 -350 L 0 0" stroke="#c8bca2" strokeWidth={3} />
        <path d="M-70 -350 C -110 -300 -150 -200 -170 -120 L -130 -110 C -110 -190 -90 -260 -60 -300 Z" fill="#ebe3d0" stroke="#6a5a44" strokeWidth={3} />
        <ellipse cx={0} cy={-424} rx={54} ry={64} fill="#e8cfae" stroke="#6a5a44" strokeWidth={3} />
        <path d="M-56 -432 C -60 -500 60 -500 56 -432 C 50 -462 30 -470 0 -470 C -30 -470 -50 -462 -56 -432 Z" fill="#e3cd8e" stroke="#9a8650" strokeWidth={2} />
        <path d="M-32 -446 L -10 -449 M 10 -449 L 32 -446" stroke="#9a8650" strokeWidth={3} strokeLinecap="round" />
        <ellipse cx={-20} cy={-434} rx={6} ry={4.5} fill="#3a2e22" />
        <ellipse cx={20} cy={-434} rx={6} ry={4.5} fill="#3a2e22" />
        <path d="M0 -428 L -5 -406 L 3 -404" fill="none" stroke="#8a7458" strokeWidth={2} />
        <path d="M-44 -392 C -44 -352 -18 -330 0 -328 C 18 -330 44 -352 44 -392 C 30 -380 18 -376 0 -378 C -18 -376 -30 -380 -44 -392 Z" fill="#e3cd8e" stroke="#9a8650" strokeWidth={2} />
        <path d="M-26 -394 C -14 -402 -4 -398 0 -394 C 4 -398 14 -402 26 -394" fill="none" stroke="#c9b36e" strokeWidth={6} strokeLinecap="round" />
        <line x1={0} y1={-316} x2={0} y2={-250} stroke="#bfc3c6" strokeWidth={2} />
        <path d="M-6 -250 L 6 -250 L 6 -236 L 18 -236 L 18 -224 L 6 -224 L 6 -196 L -6 -196 L -6 -224 L -18 -224 L -18 -236 L -6 -236 Z" fill="#d4d8db" stroke="#7a7e82" strokeWidth={1.5} />
        <line x1={64} y1={-330} x2={h.x} y2={h.y} stroke="#6a5a44" strokeWidth={46} strokeLinecap="round" />
        <line x1={64} y1={-330} x2={h.x} y2={h.y} stroke="#ebe3d0" strokeWidth={40} strokeLinecap="round" />
        <circle cx={h.x} cy={h.y} r={17} fill="#e8cfae" stroke="#6a5a44" strokeWidth={2} />
      </g>
    </svg>
  );
};

/* 金色：杯中的魔药、眼中的金光、扩散的光——全片唯一保留的颜色。 */
export const GoldLayer: React.FC<{x?: number; y?: number; s?: number; lift?: number; drink?: number; eyes?: number; flood?: number; cup?: number}> = ({x = 960, y = 760, s = 1, lift = 0, drink = 0, eyes = 0, flood = 0, cup = 1}) => {
  const h = handAt(lift, drink);
  const cx = x + h.x * s;
  const cy = y + (h.y - 30) * s;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}}>
      <defs>
        <radialGradient id="flood" cx="0.5" cy="0.5" r="0.5">
          <stop offset="0" stopColor={C.gold} stopOpacity={0.85} />
          <stop offset="0.5" stopColor={C.goldDeep} stopOpacity={0.35} />
          <stop offset="1" stopColor={C.goldDeep} stopOpacity={0} />
        </radialGradient>
      </defs>
      {flood > 0 && <circle cx={x} cy={y - 420 * s} r={200 + flood * 1400} fill="url(#flood)" opacity={Math.min(1, flood * 1.4)} style={{mixBlendMode: 'screen'}} />}
      {cup > 0 && (
        <g opacity={cup} transform={`translate(${cx} ${cy}) scale(${s}) rotate(${-drink * 60})`}>
          <path d="M-28 -40 L 28 -40 L 18 10 L -18 10 Z" fill="none" stroke="#e8e0cc" strokeWidth={3} />
          <path d={`M-25 ${-32 + drink * 34} L 25 ${-32 + drink * 34} L 18 10 L -18 10 Z`} fill={C.gold} style={{filter: `drop-shadow(0 0 16px ${C.gold})`}} />
          <rect x={-3} y={10} width={6} height={30} fill="#e8e0cc" />
          <rect x={-18} y={40} width={36} height={5} fill="#e8e0cc" />
        </g>
      )}
      {eyes > 0 && (
        <g style={{filter: `drop-shadow(0 0 ${10 + eyes * 20}px ${C.gold})`}} opacity={eyes}>
          <ellipse cx={x - 20 * s} cy={y - 434 * s} rx={7 * s} ry={5.5 * s} fill={C.gold} />
          <ellipse cx={x + 20 * s} cy={y - 434 * s} rx={7 * s} ry={5.5 * s} fill={C.gold} />
        </g>
      )}
    </svg>
  );
};

export const Fog: React.FC<{amount: number; tint?: string}> = ({amount, tint = '#d8ccb0'}) => (
  <AbsoluteFill style={{background: `linear-gradient(180deg, transparent ${100 - amount * 100}%, ${tint} ${110 - amount * 60}%)`, opacity: Math.min(1, amount * 1.2)}} />
);
