import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {Arena, Bolt, Caption, Flash, Glitch, H, HF, HPBar, Label, Shake, Slam, Stamp, Timer, Typer} from '../hud';
import {prog} from '../kit';
import {PixelChar, PixelSheep} from '../pixel';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const FEAR = '#b48cff';

// 逐段掉血：给定时间点之后的血量。
const hpAt = (f: number, steps: [number, number][], start = 1) => steps.reduce((v, [t, val]) => (f >= t ? val : v), start);

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

const HUD: React.FC<{l: number; lt: number; r: number; time: number; fear?: boolean}> = ({l, lt, r, time, fear}) => (
  <>
    <HPBar side="L" value={l} trail={lt} name="逝者" tag="SEQ 5 · 命运女神" color={H.dead} />
    <HPBar side="R" value={r} trail={r} name="牧羊人" tag="SEQ 5 · 秘祈人" color={H.shep} />
    <Timer value={time} />
    {fear && <div style={{position: 'absolute', right: 130, top: 182, fontFamily: HF.heavy, fontWeight: 900, fontSize: 26, color: FEAR, transform: 'skewX(-8deg)'}}>状态：恐惧</div>}
  </>
);

/* 第一回合 */
const HITS: [number, number][] = [
  [95, 0.82],
  [135, 0.66],
  [420, 0.44],
  [445, 0.24],
  [470, 0.1],
];
const SHEEP: [number, number][] = [
  [1060, 640],
  [1130, 540],
  [990, 560],
];

const Round1: React.FC = () => {
  const f = useCurrentFrame();
  const l = hpAt(f, HITS);
  const lt = hpAt(f - 14, HITS);
  const fear = f >= 240 && f < 390;
  const hurt = HITS.some(([t]) => f >= t && f < t + 14);
  const threads = prog(f, 170, 30);
  return (
    <AbsoluteFill>
      <Shake at={95} amp={14}>
        <Shake at={135} amp={14}>
          <Shake at={420} amp={18}>
            <Arena left={0.7} right={0.7} speed={fear ? 0.2 : 1.6} />
            <PixelChar kind="dead" x={600} y={880} cell={18} eyes={hurt ? 'squint' : undefined} />
            <PixelChar kind="shep" x={1320} y={880} cell={18} flip eyes={fear ? 'squint' : undefined} shake={fear ? 5 : 0} walk={!fear} />
            {SHEEP.map(([x, y], i) => (
              <PixelSheep key={i} x={x} y={y} at={62 + i * 6} cell={8} dim={fear} />
            ))}
            {threads > 0 && f < 330 && (
              <svg width={1920} height={1080} style={{position: 'absolute', inset: 0}}>
                {[0, 1, 2].map((i) => {
                  const d = Array.from({length: 30}, (_, k) => {
                    const x = 680 + (560 * k * threads) / 29;
                    const y = 760 - i * 26 + Math.sin(k * 0.7 + f * 0.15 + i) * 12;
                    return `${k ? 'L' : 'M'}${x} ${y}`;
                  }).join(' ');
                  return <path key={i} d={d} stroke={H.dead} strokeWidth={2.5} fill="none" opacity={0.75} style={{filter: `drop-shadow(0 0 6px ${H.dead})`}} />;
                })}
              </svg>
            )}
            <Bolt x1={SHEEP[0][0]} y1={SHEEP[0][1] - 30} x2={640} y2={760} at={92} />
            <Bolt x1={SHEEP[1][0]} y1={SHEEP[1][1] - 30} x2={640} y2={760} at={132} />
            <Bolt x1={650} y1={760} x2={1260} y2={760} at={296} color={H.dead} />
            <Bolt x1={650} y1={760} x2={1260} y2={760} at={326} color={H.dead} />
            <Bolt x1={SHEEP[2][0]} y1={SHEEP[2][1] - 30} x2={640} y2={760} at={417} />
            <Bolt x1={SHEEP[0][0]} y1={SHEEP[0][1] - 30} x2={640} y2={760} at={442} />
            <Bolt x1={SHEEP[1][0]} y1={SHEEP[1][1] - 30} x2={640} y2={760} at={467} />
          </Shake>
        </Shake>
      </Shake>
      <HUD l={l} lt={lt} r={1} time={99 - f / 12} fear={fear} />
      {f < 56 && <Slam x={960} y={440} text="ROUND 1" at={4} size={150} color={H.ink} />}
      {f < 56 && <Slam x={960} y={580} text="FIGHT!" at={26} size={110} color={H.hit} rot={-4} />}
      {f >= 64 && f < 160 && <Label x={1060} y={430} a="c" text="放牧" at={64} size={30} color={H.shep} />}
      {f >= 172 && f < 330 && <Label x={600} y={640} a="c" text="窥命" at={172} size={30} color={H.dead} />}
      <Bubble x={300} y={470} w={560} at={205} until={330} text="我看见你最害怕的那一天了。" color={H.dead} />
      {fear && <Slam x={1320} y={600} text="！！" at={242} size={70} color={FEAR} skew={0} />}
      <DmgPop x={1440} y={660} at={300} text="-0" color={H.dead} />
      <DmgPop x={1440} y={660} at={330} text="-0" color={H.dead} />
      {f >= 388 && f < 440 && <Slam x={1320} y={600} text="挣脱" at={388} size={70} color={H.shep} />}
      {HITS.map(([t], i) => (
        <DmgPop key={i} x={600} y={620} at={t} text={`-${Math.round(((i ? HITS[i - 1][1] : 1) - HITS[i][1]) * 100)}%`} />
      ))}
      {HITS.map(([t]) => (
        <Flash key={t} at={t} dur={5} color={H.hit} />
      ))}
      <Flash at={388} dur={8} color={H.shep} />
      <Caption
        lines={[
          {at: 60, text: '开局：牧羊人放羊。'},
          {at: 140, text: '一只羊打完换一只——换羊不用一秒。'},
          {at: 200, text: '逝者出招：先窥命，再把看到的命运说出口。'},
          {at: 280, text: '牧羊人被吓住了。可逝者手里，没有能打死他的东西。'},
          {at: 395, text: '恐惧挣脱，羊群继续。'},
        ]}
      />
    </AbsoluteFill>
  );
};

/* K.O. → 倒带 → 另一条支流：吓住就跑 */
const KO: React.FC = () => {
  const f = useCurrentFrame();
  const rewind = f >= 120 && f < 160;
  const alt = f >= 160;
  const fallen = alt ? 0 : rewind ? interpolate(f, [120, 155], [1, 0], clamp) : prog(f, 12, 6);
  const runX = interpolate(f, [222, 320], [600, -180], clamp);
  return (
    <AbsoluteFill>
      <Shake at={16} amp={26}>
        <Arena left={0.6} right={0.8} speed={alt ? 0.2 : 0} />
        <PixelChar kind="dead" x={alt ? runX : 600} y={880} cell={18} fallen={fallen} eyes={alt ? undefined : 'x'} walk={alt && f >= 222} />
        <PixelChar kind="shep" x={1320} y={880} cell={18} flip eyes={alt ? 'squint' : undefined} shake={alt ? 5 : 0} walk={!alt} />
      </Shake>
      <HUD l={alt ? 0.66 : hpAt(f, [[14, 0]], 0.1)} lt={alt ? 0.66 : hpAt(f - 14, [[14, 0]], 0.1)} r={1} time={alt ? 79 : 57} fear={alt} />
      {f < 120 && <Stamp x={960} y={480} text="K.O." at={16} size={300} />}
      {rewind && (
        <AbsoluteFill style={{backgroundImage: 'repeating-linear-gradient(0deg, rgba(255,255,255,0.08) 0 2px, transparent 2px 7px)', backgroundPosition: `0 ${f * 13}px`}}>
          <div style={{position: 'absolute', left: 80, top: 230, fontFamily: HF.mono, fontSize: 52, color: '#fff'}}>◀◀ 倒带</div>
        </AbsoluteFill>
      )}
      {alt && <Label x={960} y={260} a="c" text="另一条命运支流" at={162} size={38} color={H.ink} />}
      <Bubble x={320} y={470} w={360} at={170} until={222} text="……我看见了。" color={H.dead} />
      {f >= 290 && <Stamp x={960} y={500} text="逃脱成功" at={290} size={140} color={H.dead} rot={-6} />}
      <Flash at={16} dur={10} />
      <Flash at={160} dur={6} />
      <Caption
        lines={[
          {at: 30, text: '正面硬打，逝者倒下。'},
          {at: 165, text: '但还有一条路：趁他被吓住的那几秒——'},
          {at: 236, text: '跑。'},
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
const OUTCOMES = [...Array(70).fill('S'), ...Array(25).fill('E'), ...Array(5).fill('D')]
  .map((o, i) => ({o, k: rnd(i)}))
  .sort((a, b) => a.k - b.k)
  .map((x) => x.o as 'S' | 'E' | 'D');
const OC = {S: H.shep, E: H.dead, D: H.hit};

const Sim: React.FC = () => {
  const f = useCurrentFrame();
  const shown = (i: number) => f >= 40 + i * 3;
  const count = (o: 'S' | 'E' | 'D') => OUTCOMES.filter((x, i) => x === o && shown(i)).length;
  const rows: {o: 'S' | 'E' | 'D'; name: string; text: string}[] = [
    {o: 'S', name: '牧羊人胜', text: '被吓住几秒后缓过来：七个灵魂轮换＋阴影＋血肉修复，逝者没有伤害手段'},
    {o: 'E', name: '逝者脱身', text: '先窥命，再说出他的恐惧，趁他发愣脱离战斗'},
    {o: 'D', name: '逝者胜', text: '恐惧正中心魔且久久不散，或者有外来伤害来源——像腓力身兼收割者'},
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
      <Caption lines={[{at: 470, text: '一百条命运支流，七成走向同一个结局。'}]} />
      <Flash at={0} dur={6} />
    </AbsoluteFill>
  );
};

/* 结论 */
const Verdict: React.FC = () => {
  const f = useCurrentFrame();
  const cards = [
    {t: '正面硬打', c: H.shep, b: '七个灵魂轮换＋阴影＋血肉修复；逝者没有伤害手段，吓住了也打不死'},
    {t: '逝者最优解', c: H.dead, b: '先窥命，再说出他的恐惧，趁他发愣的几秒脱离战斗'},
    {t: '翻盘条件', c: H.hit, b: '需要外来的伤害：腓力身兼猎人途径的收割者，还有队友和物品'},
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
          <Label x={0} y={110} text={c.b} at={70 + i * 16} size={25} color={H.ink} width={480} />
        </div>
      ))}
      <Label x={960} y={840} a="c" text="我的判断：依据双方正文能力与战绩推演，原作没有这场对战" at={200} size={22} color={H.dim} />
      <PixelChar kind="dead" x={220} y={1000} cell={9} at={120} />
      <PixelChar kind="shep" x={1700} y={1000} cell={9} flip at={120} />
      <PixelSheep x={1560} y={1010} at={130} cell={5} />
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
      <PixelChar kind="dead" x={960} y={920} cell={14} at={200} />
      {f >= 215 && <Stamp x={960} y={400} text="我看见你的命运了——" at={215} size={80} color={H.dead} rot={-3} />}
      {f >= 255 && <Stamp x={960} y={560} text="你会点赞。" at={255} size={120} color={H.hit} rot={-5} />}
      <Label
        x={960}
        y={1040}
        a="c"
        text="资料：《宿命之环》第三卷《灾祸巨人》《狂乱》《命运的嘲弄》、第四卷《“骗”》《巢内之物》；《诡秘之主》第一卷《能力的搭配》、第二卷《刺杀》《“0”级封印物在行动》《莫名的微笑》。胜负为推演，原作没有这场对战。"
        at={280}
        size={14}
        color={H.dim}
        width={1700}
      />
    </AbsoluteFill>
  );
};

export const SCENES_B: Record<string, React.FC> = {round1: Round1, ko: KO, sim: Sim, verdict: Verdict, gameover: GameOver};
