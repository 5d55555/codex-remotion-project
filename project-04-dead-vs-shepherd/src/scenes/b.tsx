import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {Arena, Bolt, Caption, Flash, Glitch, H, HF, HPBar, Label, Shake, Slam, Stamp, Timer, Typer} from '../hud';
import {prog} from '../kit';
import {PixelChar, PixelPot, PixelSheep} from '../pixel';
import {SOUL, SOULS} from '../souls';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const FEAR = '#b48cff';

// 逐段掉血：给定时间点之后的血量。
const hpAt = (f: number, steps: [number, number][], start = 1) => steps.reduce((v, [t, val]) => (f >= t ? val : v), start);
// 按时间表取当前项：最后一个 t <= f 的值。
const latest = <T,>(f: number, list: [number, T][]): T | undefined => list.reduce<T | undefined>((v, [t, x]) => (f >= t ? x : v), undefined);

const DmgPop: React.FC<{x: number; y: number; at: number; text: string; color?: string}> = ({x, y, at, text, color = H.hit}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || t > 32) return null;
  return (
    <div style={{position: 'absolute', left: x, top: y - t * 1.6, transform: 'translate(-50%, -50%)', fontFamily: HF.mono, fontWeight: 500, fontSize: 54, color, opacity: 1 - t / 32, textShadow: '0 0 12px rgba(0,0,0,0.8)'}}>{text}</div>
  );
};

const Bubble: React.FC<{x: number; y: number; w: number; at: number; until: number; text: string; color: string}> = ({x, y, w, at, until, text, color}) => {
  const f = useCurrentFrame();
  if (f < at || f >= until) return null;
  const p = prog(f, at, 8);
  return (
    <div style={{position: 'absolute', left: x, top: y, width: w, height: 76, transform: `scale(${p})`, transformOrigin: '20% 100%', background: 'rgba(5,7,10,0.88)', border: `3px solid ${color}`}}>
      <Typer x={24} y={36} text={text} at={at + 4} size={32} cps={2} color={color} cursor={false} />
      <div style={{position: 'absolute', left: 60, bottom: -22, width: 0, height: 0, borderLeft: '14px solid transparent', borderRight: '14px solid transparent', borderTop: `20px solid ${color}`}} />
    </div>
  );
};

const HUD: React.FC<{l: number; lt: number; r: number; time: number; fear?: boolean; slow?: boolean; soul?: string; move?: string}> = ({l, lt, r, time, fear, slow, soul, move}) => {
  const s = soul ? SOUL[soul] : undefined;
  return (
    <>
      <HPBar side="L" value={l} trail={lt} name="逝者" tag="SEQ 5 · 命运女神" color={H.dead} />
      <HPBar side="R" value={r} trail={r} name="牧羊人" tag="SEQ 5 · 秘祈人" color={H.shep} />
      <Timer value={time} />
      {s && (
        <div style={{position: 'absolute', right: 120, top: 178, display: 'flex', alignItems: 'center', gap: 12}}>
          <span style={{fontFamily: HF.heavy, fontWeight: 900, fontSize: 24, color: s.color}}>{s.name}</span>
          {move && <span style={{fontFamily: HF.heavy, fontWeight: 700, fontSize: 22, color: H.ink}}>· {move}</span>}
          <span style={{background: H.shep, color: '#0d0e12', fontFamily: HF.heavy, fontWeight: 900, fontSize: 18, padding: '3px 10px', transform: 'skewX(-10deg)'}}>驱使中</span>
        </div>
      )}
      {fear && <div style={{position: 'absolute', right: 130, top: 222, fontFamily: HF.heavy, fontWeight: 900, fontSize: 26, color: FEAR, transform: 'skewX(-8deg)'}}>状态：恐惧</div>}
      {slow && <div style={{position: 'absolute', left: 130, top: 182, fontFamily: HF.heavy, fontWeight: 900, fontSize: 26, color: SOUL.puppet.color, transform: 'skewX(-8deg)'}}>状态：迟缓</div>}
    </>
  );
};

/* 七只灵魂羊排成一排，一次只亮一只。 */
const SoulRow: React.FC<{active?: string; switchAt?: number; y?: number}> = ({active, switchAt = -99, y = 600}) => {
  const f = useCurrentFrame();
  return (
    <>
      {SOULS.map((s, i) => {
        const on = s.id === active;
        const x = 1130 + i * 104;
        const t = f - switchAt;
        return (
          <React.Fragment key={s.id}>
            <PixelSheep x={x} y={y} at={0} cell={6} glow={s.color} acc={s.id} dim={!on} scale={on ? 1.3 : 1} />
            {on && t >= 0 && t < 12 && (
              <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
                <circle cx={x} cy={y - 20} r={20 + t * 6} fill="none" stroke={s.color} strokeWidth={4} opacity={1 - t / 12} />
              </svg>
            )}
          </React.Fragment>
        );
      })}
    </>
  );
};

/* 线：窥命的命运线（冷青），灵体之线（粉）。 */
const Threads: React.FC<{x1: number; x2: number; y: number; at: number; until: number; color: string; arc?: number}> = ({x1, x2, y, at, until, color, arc = 0}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 24);
  if (p <= 0 || f >= until) return null;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {[0, 1, 2].map((i) => {
        const d = Array.from({length: 30}, (_, k) => {
          const u = (k / 29) * p;
          const x = x1 + (x2 - x1) * u;
          const yy = y - i * 22 - Math.sin(Math.PI * u) * arc + Math.sin(k * 0.7 + f * 0.15 + i) * 10;
          return `${k ? 'L' : 'M'}${x} ${yy}`;
        }).join(' ');
        return <path key={i} d={d} stroke={color} strokeWidth={2.5} fill="none" opacity={0.8} style={{filter: `drop-shadow(0 0 6px ${color})`}} />;
      })}
    </svg>
  );
};

/* 说出命运：一圈圈声波从逝者推向对手。 */
const Words: React.FC<{at: number}> = ({at}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || t > 18) return null;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {[0, 1, 2].map((k) => {
        const x = 690 + t * 32 + k * 28;
        return <path key={k} d={`M${x} 700 Q${x + 26} 770 ${x} 840`} stroke={H.dead} strokeWidth={5} fill="none" opacity={1 - t / 18} />;
      })}
    </svg>
  );
};

/* 空气弹：白色气圈从牧羊人飞向逝者，命中炸开。 */
const AirShot: React.FC<{at: number; from: number; to: number; y: number}> = ({at, from, to, y}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || t > 18) return null;
  const fly = Math.min(1, t / 7);
  const x = from + (to - from) * fly;
  const boom = t - 7;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {boom < 0 ? (
        <>
          <line x1={x} y1={y} x2={x + 120} y2={y} stroke="#fff" strokeWidth={3} opacity={0.5} />
          <circle cx={x} cy={y} r={16} fill="none" stroke="#fff" strokeWidth={5} />
        </>
      ) : (
        <circle cx={to} cy={y} r={16 + boom * 6} fill="none" stroke="#fff" strokeWidth={6} opacity={1 - boom / 11} />
      )}
    </svg>
  );
};

/* 神圣之光：先有一道细光瞄准，然后光柱砸下。 */
const Pillar: React.FC<{x: number; aim: number; at: number}> = ({x, aim, at}) => {
  const f = useCurrentFrame();
  if (f < aim || f > at + 22) return null;
  const c = SOUL.light.color;
  const full = f >= at;
  const w = full ? interpolate(f - at, [0, 3, 22], [60, 220, 120], clamp) : 6;
  const o = full ? interpolate(f - at, [0, 14, 22], [1, 0.9, 0], clamp) : prog(f, aim, 18) * 0.7;
  return (
    <div style={{position: 'absolute', left: x - w / 2, top: 0, width: w, height: 900, opacity: o, background: `linear-gradient(90deg, transparent, ${c} 30%, #fffbe6 50%, ${c} 70%, transparent)`, boxShadow: `0 0 60px ${c}`}} />
  );
};

/* 黎明之剑：一道橙白斩痕。 */
const Slash: React.FC<{x: number; y: number; at: number}> = ({x, y, at}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || t > 12) return null;
  const p = Math.min(1, t / 3);
  const c = SOUL.guard.color;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      <path d={`M${x + 160} ${y - 170} Q${x + 20} ${y - 40} ${x - 150} ${y + 60}`} stroke={c} strokeWidth={22 * (1 - t / 12) + 4} fill="none" strokeLinecap="round" strokeDasharray={600} strokeDashoffset={600 * (1 - p)} style={{filter: `drop-shadow(0 0 16px ${c})`}} />
      <path d={`M${x + 160} ${y - 170} Q${x + 20} ${y - 40} ${x - 150} ${y + 60}`} stroke="#fff" strokeWidth={5} fill="none" strokeLinecap="round" strokeDasharray={600} strokeDashoffset={600 * (1 - p)} opacity={1 - t / 12} />
    </svg>
  );
};

/* 光环：光之祭司的免疫恐惧，金色光圈一层层扩开。 */
const Aura: React.FC<{x: number; y: number; at: number; until: number}> = ({x, y, at, until}) => {
  const f = useCurrentFrame();
  if (f < at || f >= until) return null;
  const c = SOUL.light.color;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      {[0, 1, 2].map((k) => {
        const t = ((f - at + k * 8) % 24) / 24;
        return <circle key={k} cx={x} cy={y} r={60 + t * 140} fill="none" stroke={c} strokeWidth={5} opacity={(1 - t) * 0.9 * prog(f, at, 6)} />;
      })}
    </svg>
  );
};

/* 闪现：原地一道竖光消失，落点一道竖光出现，中间一条绿色残影。 */
const Blink: React.FC<{from: number; to: number; y: number; at: number}> = ({from, to, y, at}) => {
  const f = useCurrentFrame();
  const t = f - at;
  if (t < 0 || t > 14) return null;
  const c = SOUL.travel.color;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
      <rect x={Math.min(from, to)} y={y - 3} width={Math.abs(to - from)} height={6} fill={c} opacity={0.8 * (1 - t / 14)} />
      {[from, to].map((x) => (
        <rect key={x} x={x - 10 + t} y={y - 170} width={Math.max(0, 20 - 2 * t)} height={200} fill={c} opacity={1 - t / 14} style={{filter: `drop-shadow(0 0 12px ${c})`}} />
      ))}
    </svg>
  );
};

/* 冥界之门：门从地面升起、打开，里面是旋转的黑暗。 */
const Door: React.FC<{x: number; y: number; at: number; close: number}> = ({x, y, at, close}) => {
  const f = useCurrentFrame();
  if (f < at || f > close + 10) return null;
  const rise = prog(f, at, 12);
  const open = f < close ? prog(f, at + 12, 10) : 1 - prog(f, close, 8);
  const w = 170;
  const h = 280;
  const bronze = '#8a6a3a';
  return (
    <div style={{position: 'absolute', left: x - w / 2, top: y - h, width: w, height: h, transform: `scaleY(${rise})`, transformOrigin: '50% 100%'}}>
      <div style={{position: 'absolute', inset: 0, background: '#000', border: `10px solid ${bronze}`, overflow: 'hidden'}}>
        <div style={{position: 'absolute', inset: -40, background: `repeating-conic-gradient(from ${f * 9}deg at 50% 50%, rgba(159,176,192,0.35) 0deg 14deg, transparent 14deg 40deg)`, opacity: open}} />
      </div>
      {[0, 1].map((k) => (
        <div
          key={k}
          style={{
            position: 'absolute',
            top: 10,
            bottom: 10,
            [k ? 'right' : 'left']: 10,
            width: (w - 20) / 2,
            background: `repeating-linear-gradient(0deg, #6b4f2a 0 18px, ${bronze} 18px 22px)`,
            transform: `scaleX(${1 - 0.88 * open})`,
            transformOrigin: k ? '100% 50%' : '0% 50%',
          }}
        />
      ))}
    </div>
  );
};

/* 快速切换支流：横向扫描线。 */
const Static: React.FC<{at: number; dur: number}> = ({at, dur}) => {
  const f = useCurrentFrame();
  if (f < at || f >= at + dur) return null;
  return (
    <AbsoluteFill style={{backgroundColor: 'rgba(0,0,0,0.55)', backgroundImage: 'repeating-linear-gradient(0deg, rgba(255,255,255,0.12) 0 2px, transparent 2px 6px)', backgroundPosition: `0 ${f * 17}px`}}>
      <div style={{position: 'absolute', left: 80, top: 230, fontFamily: HF.mono, fontSize: 52, color: '#fff'}}>◀◀</div>
    </AbsoluteFill>
  );
};

/* 第一回合 */
const HITS: [number, number][] = [
  [95, 0.82],
  [135, 0.66],
  [420, 0.44],
  [445, 0.24],
  [470, 0.1],
];
const ACTIVE: [number, string][] = [
  [60, 'sea'],
  [112, 'puppet'],
  [290, 'light'],
  [436, 'guard'],
  [458, 'luck'],
];
const MOVES: [number, string][] = [
  [80, '雷击'],
  [112, '灵体之线'],
  [126, '空气弹'],
  [292, '免疫恐惧？'],
  [396, '神圣之光'],
  [438, '黎明之剑'],
  [460, '引来灾祸'],
];

const Round1: React.FC = () => {
  const f = useCurrentFrame();
  const l = hpAt(f, HITS);
  const lt = hpAt(f - 14, HITS);
  const fear = f >= 240 && f < 330;
  const hurt = HITS.some(([t]) => f >= t && f < t + 14);
  const soul = latest(f, ACTIVE);
  const switchAt = latest(f, ACTIVE.map(([t]) => [t, t] as [number, number])) ?? -99;
  const potY = interpolate(f, [458, 470], [-60, 690], clamp);
  return (
    <AbsoluteFill>
      <Shake at={95} amp={14}>
        <Shake at={135} amp={14}>
          <Shake at={420} amp={18}>
            <Shake at={470} amp={14}>
              <Arena left={0.7} right={0.7} speed={fear ? 0.2 : 1.6} />
              <Pillar x={600} aim={396} at={420} />
              <PixelChar kind="dead" x={600} y={880} cell={18} eyes={hurt ? 'squint' : undefined} flash={f >= 420 && f < 426} />
              <PixelChar kind="shep" x={1320} y={880} cell={18} flip eyes={fear ? 'squint' : undefined} shake={fear ? 5 : 0} walk={!fear} />
              <SoulRow active={soul} switchAt={switchAt} />
              <Threads x1={1240} x2={650} y={780} at={112} until={290} color={SOUL.puppet.color} arc={120} />
              <Threads x1={680} x2={1240} y={850} at={170} until={290} color={H.dead} />
              <Bolt x1={620} y1={110} x2={600} y2={770} at={92} color={SOUL.sea.color} />
              <Bolt x1={560} y1={130} x2={610} y2={770} at={94} color={SOUL.sea.color} />
              <AirShot at={128} from={1230} to={660} y={790} />
              <Words at={240} />
              <Words at={262} />
              <Aura x={1320} y={800} at={292} until={336} />
              <Slash x={600} y={790} at={443} />
              {f >= 458 && f < 470 && <PixelPot x={600} y={potY} cell={14} rot={(f - 458) * 12} />}
            </Shake>
          </Shake>
        </Shake>
      </Shake>
      {f >= 470 && f < 490 && (
        <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
          {[-1, -0.4, 0.4, 1].map((k) => (
            <rect key={k} x={600 + k * (f - 470) * 9} y={690 - (f - 470) * 6 + (f - 470) ** 2 * 0.9} width={10} height={10} fill={k > 0 ? '#c0603a' : '#4caf50'} />
          ))}
        </svg>
      )}
      <HUD l={l} lt={lt} r={1} time={99 - f / 12} fear={fear} slow={f >= 126 && f < 290} soul={soul} move={latest(f, MOVES)} />
      {f < 56 && <Slam x={960} y={440} text="ROUND 1" at={4} size={150} color={H.ink} />}
      {f < 56 && <Slam x={960} y={580} text="FIGHT!" at={26} size={110} color={H.hit} rot={-4} />}
      {f >= 172 && f < 290 && <Label x={600} y={640} a="c" text="窥命" at={172} size={30} color={H.dead} />}
      <Bubble x={300} y={470} w={560} at={205} until={290} text="我看见你最害怕的那一天了。" color={H.dead} />
      {fear && f < 290 && <Slam x={1320} y={440} text="！！" at={242} size={70} color={FEAR} skew={0} />}
      {f >= 292 && f < 330 && <Slam x={1320} y={440} text="免疫恐惧？" at={292} size={54} color={SOUL.light.color} />}
      {f >= 292 && f < 420 && <Label x={960} y={905} a="c" text="推演：免疫恐惧挡不挡得住“说出命运”，原作没写" at={296} size={19} color={H.dim} />}
      <DmgPop x={1440} y={660} at={256} text="-0" color={H.dead} />
      <DmgPop x={1440} y={660} at={278} text="-0" color={H.dead} />
      {f >= 330 && f < 384 && <Slam x={1320} y={440} text="挣脱" at={330} size={70} color={H.shep} />}
      {HITS.map(([t], i) => (
        <DmgPop key={i} x={600} y={620} at={t} text={`-${Math.round(((i ? HITS[i - 1][1] : 1) - HITS[i][1]) * 100)}%`} />
      ))}
      {HITS.map(([t]) => (
        <Flash key={t} at={t} dur={5} color={H.hit} />
      ))}
      <Flash at={330} dur={8} color={SOUL.light.color} />
      <Caption
        lines={[
          {at: 60, text: '开局换羊：海洋歌者，雷击。'},
          {at: 112, text: '换秘偶大师：灵体之线缠上去，人会变慢。'},
          {at: 180, text: '逝者出招：先窥命，再把看到的命运说出口。'},
          {at: 245, text: '牧羊人被吓住了。说出命运不掉血，但能让人动不了。'},
          {at: 292, text: '换光之祭司：免疫恐惧。挡不挡得住“说出命运”，原作没写。'},
          {at: 340, text: '挣脱。光柱、剑、灾祸——一个灵魂一招。'},
        ]}
      />
    </AbsoluteFill>
  );
};

/* K.O. → 倒带 → 逝者窥命，看三条逃跑支流 */
const KO: React.FC = () => {
  const f = useCurrentFrame();
  const rewind = f >= 120 && f < 160;
  const alt = f >= 160;
  const fallen = alt ? 0 : rewind ? interpolate(f, [120, 155], [1, 0], clamp) : prog(f, 12, 6);
  const branch = f < 205 ? '' : f < 317 ? 'A' : f < 419 ? 'B' : 'C';

  // 逝者位置：A 跑到 400 被堵，B 跑到 350 被吸进门；C 一路跑出画面。
  let deadX = 600;
  if (branch === 'A') deadX = 600 - Math.max(0, Math.min(f, 262) - 212) * 4;
  if (branch === 'B') deadX = f < 372 ? 600 - Math.max(0, f - 322) * 5 : interpolate(f, [372, 392], [350, 170], clamp);
  if (branch === 'C') deadX = Math.max(-200, 600 - Math.max(0, f - 425) * 7);
  const running = (branch === 'A' && f >= 212 && f < 262) || (branch === 'B' && f >= 322 && f < 372) || (branch === 'C' && f >= 425);
  const deadCell = branch === 'B' ? interpolate(f, [372, 392], [18, 3], clamp) : 18;
  const showDead = !(branch === 'B' && f >= 392);

  // 牧羊人：A 支流 240 换光之祭司抗住恐惧、256 闪现到逝者前面；B 支流 338 抗住恐惧、346 换看门人开门；C 一直被吓住。
  const fearEnd = branch === 'A' ? 240 : branch === 'B' ? 338 : 9999;
  const fear = alt && f < fearEnd;
  const blinked = branch === 'A' && f >= 262;
  const shepVisible = !(branch === 'A' && f >= 256 && f < 262);
  const soul = !alt
    ? 'luck'
    : branch === 'A'
      ? latest(f, [[0, 'puppet'], [240, 'light'], [250, 'travel']] as [number, string][])
      : branch === 'B'
        ? latest(f, [[0, 'puppet'], [338, 'light'], [346, 'gate']] as [number, string][])
        : 'puppet';
  const move = branch === 'A' && f >= 250 ? '闪现' : branch === 'B' && f >= 346 ? '冥界之门' : (branch === 'A' && f >= 240) || (branch === 'B' && f >= 338) ? '免疫恐惧' : undefined;
  const switchAt = branch === 'A' ? (f >= 250 ? 250 : 240) : branch === 'B' ? (f >= 346 ? 346 : 338) : -99;

  return (
    <AbsoluteFill>
      <Shake at={16} amp={26}>
        <Shake at={262} amp={14}>
          <Arena left={0.6} right={0.8} speed={alt ? 0.2 : 0} />
          {branch === 'C' && <AbsoluteFill style={{backgroundColor: FEAR, opacity: 0.08}} />}
          <Door x={170} y={880} at={350} close={392} />
          {showDead && <PixelChar kind="dead" x={deadX} y={880} cell={deadCell} fallen={fallen} eyes={alt ? (f >= 262 && branch === 'A' ? 'squint' : undefined) : 'x'} walk={running} flip={running || (branch !== '' && f >= 212)} />}
          {shepVisible && <PixelChar kind="shep" x={blinked ? 130 : 1320} y={880} cell={18} flip={!blinked} eyes={fear ? 'squint' : undefined} shake={fear ? 5 : 0} walk={!alt} />}
          <SoulRow active={soul} switchAt={switchAt} />
          <Aura x={1320} y={800} at={240} until={256} />
          <Aura x={1320} y={800} at={338} until={360} />
          <Blink from={1320} to={130} y={800} at={256} />
        </Shake>
      </Shake>
      <HUD l={alt ? 0.66 : hpAt(f, [[14, 0]], 0.1)} lt={alt ? 0.66 : hpAt(f - 14, [[14, 0]], 0.1)} r={1} time={alt ? 79 : 57} fear={fear} soul={soul} move={move} />
      {f < 120 && <Stamp x={960} y={480} text="K.O." at={16} size={300} />}
      {rewind && (
        <AbsoluteFill style={{backgroundImage: 'repeating-linear-gradient(0deg, rgba(255,255,255,0.08) 0 2px, transparent 2px 7px)', backgroundPosition: `0 ${f * 13}px`}}>
          <div style={{position: 'absolute', left: 80, top: 230, fontFamily: HF.mono, fontSize: 52, color: '#fff'}}>◀◀ 倒带</div>
        </AbsoluteFill>
      )}
      {alt && branch === '' && <Label x={960} y={270} a="c" text="窥命 · 往后看三条支流" at={162} size={38} color={H.ink} />}
      <Bubble x={320} y={470} w={360} at={166} until={205} text="……我看见了。" color={H.dead} />
      {branch && <Glitch key={branch} x={130} y={270} a="l" text={`支流 ${branch}`} at={branch === 'A' ? 205 : branch === 'B' ? 317 : 419} size={56} color={branch === 'C' ? H.dead : H.ink} font={HF.heavy} />}
      {branch === 'A' && f >= 272 && <Stamp x={960} y={470} text="被堵住" at={272} size={130} color={H.hit} rot={-6} />}
      {branch === 'B' && f >= 390 && <Stamp x={960} y={470} text="进门了" at={390} size={130} color={H.hit} rot={-6} />}
      {branch === 'C' && f >= 495 && <Stamp x={960} y={470} text="逃脱成功" at={495} size={140} color={H.dead} rot={-6} />}
      <Static at={305} dur={12} />
      <Static at={407} dur={12} />
      <Flash at={16} dur={10} />
      <Flash at={160} dur={6} />
      <Caption
        lines={[
          {at: 30, text: '正面硬打，逝者倒下。'},
          {at: 165, text: '倒带。逝者窥命，往后看了几条支流——'},
          {at: 210, text: '支流A：往左跑。旅行家闪现，堵在前面。'},
          {at: 320, text: '支流B：再跑。看门人把冥界之门开在路上。'},
          {at: 424, text: '支流C：恐惧没被挡住，他多愣了几秒——'},
          {at: 480, text: '跑掉了。这样的支流，不多。'},
        ]}
      />
    </AbsoluteFill>
  );
};

/* 命运推演 ×100（我的判断） */
const rnd = (n: number) => {
  const x = Math.sin(n * 91.7 + 13.3) * 43758.5453;
  return x - Math.floor(x);
};
const OUTCOMES = [...Array(82).fill('S'), ...Array(15).fill('E'), ...Array(3).fill('D')]
  .map((o, i) => ({o, k: rnd(i)}))
  .sort((a, b) => a.k - b.k)
  .map((x) => x.o as 'S' | 'E' | 'D');
const OC = {S: H.shep, E: H.dead, D: H.hit};

const Sim: React.FC = () => {
  const f = useCurrentFrame();
  const shown = (i: number) => f >= 40 + i * 3;
  const count = (o: 'S' | 'E' | 'D') => OUTCOMES.filter((x, i) => x === o && shown(i)).length;
  const rows: {o: 'S' | 'E' | 'D'; name: string; text: string}[] = [
    {o: 'S', name: '牧羊人胜', text: '光之祭司扛恐惧，旅行家、海洋歌者、看门人负责追和堵；逝者没有伤害手段'},
    {o: 'E', name: '逝者脱身', text: '恐惧没被挡住，又在愣住的几秒里跑出了闪现和风的范围'},
    {o: 'D', name: '逝者胜', text: '要有外来的伤害来源——像腓力身兼收割者；或者恐惧正中心魔、久久不散'},
  ];
  return (
    <AbsoluteFill>
      <Arena left={0.4} right={0.4} speed={0.3} />
      <Slam x={960} y={140} text="命运推演 ×100" at={4} size={78} color={H.ink} />
      {OUTCOMES.map((o, i) => {
        const x = 180 + (i % 10) * 64;
        const y = 250 + Math.floor(i / 10) * 64;
        const on = shown(i);
        const pop = on ? interpolate(f - (40 + i * 3), [0, 4, 8], [1.35, 0.9, 1], clamp) : 1;
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: x,
              top: y,
              width: 56,
              height: 56,
              border: `2px solid ${on ? OC[o] : 'rgba(255,255,255,0.15)'}`,
              background: on ? (o === 'E' ? 'transparent' : OC[o]) : 'transparent',
              boxShadow: on ? `0 0 12px ${OC[o]}` : 'none',
              transform: `scale(${pop})`,
            }}
          />
        );
      })}
      {rows.map((r, i) => (
        <React.Fragment key={r.o}>
          <div style={{position: 'absolute', left: 960, top: 290 + i * 200, width: 30, height: 30, background: r.o === 'E' ? 'transparent' : OC[r.o], border: `3px solid ${OC[r.o]}`, opacity: prog(f, 20 + i * 8, 10)}} />
          <Label x={1010} y={305 + i * 200} text={r.name} at={20 + i * 8} size={36} color={OC[r.o]} />
          <div style={{position: 'absolute', right: 120, top: 270 + i * 200, fontFamily: HF.mono, fontWeight: 500, fontSize: 76, color: OC[r.o], opacity: prog(f, 30, 10)}}>{count(r.o)}</div>
          <Label x={1010} y={378 + i * 200} text={r.text} at={340 + i * 20} size={21} color={H.ink} width={760} />
        </React.Fragment>
      ))}
      <Label x={960} y={912} a="c" text="以上为我的判断：示意推演，不是原作数据" at={420} size={20} color={H.dim} />
      <Caption lines={[{at: 470, text: '一百条命运支流，八成以上走向同一个结局。'}]} />
      <Flash at={0} dur={6} />
    </AbsoluteFill>
  );
};

/* 结论 */
const Verdict: React.FC = () => {
  const f = useCurrentFrame();
  const cards = [
    {t: '正面硬打', c: H.shep, b: '七个灵魂轮换＋阴影＋血肉修复；逝者没有伤害手段，吓住了也打不死'},
    {t: '逝者最优解', c: H.dead, b: '还是吓住就跑。但光之祭司可能免疫恐惧，旅行家、海洋歌者、看门人专管追和堵'},
    {t: '翻盘条件', c: H.hit, b: '外来的伤害（腓力身兼收割者），或者恐惧正好没被挡住、跑得够远'},
  ];
  return (
    <AbsoluteFill>
      <Shake at={12} amp={22}>
        <Arena left={0.3} right={1} split={0.3} />
      </Shake>
      <Label x={960} y={150} a="c" text="大概率" at={6} size={40} color={H.dim} />
      <Stamp x={960} y={300} text="牧羊人胜" at={12} size={190} color={H.shep} rot={-4} />
      {cards.map((c, i) => (
        <div key={c.t} style={{position: 'absolute', left: 150 + i * 560, top: 480, width: 500}}>
          <Label x={0} y={0} text={c.t} at={60 + i * 16} size={34} color={c.c} />
          <div style={{position: 'absolute', left: 0, top: 30, width: 500, height: 4, background: c.c, opacity: prog(f, 64 + i * 16, 10)}} />
          <Label x={0} y={120} text={c.b} at={70 + i * 16} size={25} color={H.ink} width={480} />
        </div>
      ))}
      <Label x={960} y={840} a="c" text="我的判断：依据双方能力资料推演，原作没有这场对战；七个灵魂各三种能力是我替牧羊人挑的" at={200} size={22} color={H.dim} />
      <PixelChar kind="dead" x={220} y={1000} cell={9} at={120} />
      <PixelChar kind="shep" x={1700} y={1000} cell={9} flip at={120} />
      {SOULS.map((s, i) => (
        <PixelSheep key={s.id} x={1560 + i * 52} y={i % 2 ? 850 : 830} at={130 + i * 4} cell={4} glow={s.color} acc={s.id} />
      ))}
      <Flash at={12} dur={8} color={H.shep} />
    </AbsoluteFill>
  );
};

/* GAME OVER：逝者对观众“说出命运” */
const GameOver: React.FC = () => {
  const f = useCurrentFrame();
  const n = Math.max(0, 9 - Math.floor((f - 80) / 12));
  return (
    <AbsoluteFill style={{backgroundColor: '#000'}}>
      <Arena left={0.15} right={0.15} speed={0} />
      {f < 200 && <Glitch x={960} y={330} text="GAME OVER" at={8} size={170} color={H.hit} font={HF.heavy} />}
      {f < 200 && <Label x={960} y={520} a="c" text="CONTINUE?" at={60} size={56} color={H.ink} font={HF.mono} />}
      {f >= 80 && f < 200 && <div style={{position: 'absolute', left: 960, top: 660, transform: 'translate(-50%, -50%)', fontFamily: HF.mono, fontSize: 130, color: H.hit}}>{n}</div>}
      <PixelChar kind="dead" x={960} y={900} cell={14} at={200} />
      {f >= 215 && <Stamp x={960} y={400} text="我看见你的命运了——" at={215} size={80} color={H.dead} rot={-3} />}
      {f >= 255 && <Stamp x={960} y={560} text="你会点赞。" at={255} size={120} color={H.hit} rot={-5} />}
      <Label
        x={960}
        y={1030}
        a="c"
        text="资料：《宿命之环》第三卷《灾祸巨人》《狂乱》《命运的嘲弄》、第四卷《“骗”》《巢内之物》；《诡秘之主》《能力的搭配》《刺杀》《“0”级封印物在行动》《莫名的微笑》《灵体之线》《面具之后》；七个灵魂的能力另据起点《诡秘之主》设定集与诡秘之主 Wiki（太阳、水手、占卜家、学徒、收尸人、怪物、战士途径）。胜负为推演，原作没有这场对战。"
        at={280}
        size={14}
        color={H.dim}
        width={1700}
      />
    </AbsoluteFill>
  );
};

export const SCENES_B: Record<string, React.FC> = {round1: Round1, ko: KO, sim: Sim, verdict: Verdict, gameover: GameOver};
