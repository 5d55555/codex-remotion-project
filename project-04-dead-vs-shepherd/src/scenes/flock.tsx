import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {Arena, Caption, Flash, H, HF, Label, Slam} from '../hud';
import {prog} from '../kit';
import {PixelSheep} from '../pixel';
import {SOULS, Src} from '../souls';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const W = 420;
const HGT = 318;
const GAP = 24;
const STEP = 66;
const at = (i: number) => 24 + i * STEP;

// 四张一排、三张一排，居中。
const pos = (i: number) => {
  const row = i < 4 ? 0 : 1;
  const n = row ? 3 : 4;
  const k = row ? i - 4 : i;
  const x0 = 960 - (n * W + (n - 1) * GAP) / 2;
  return {x: x0 + k * (W + GAP), y: 196 + row * (HGT + 22)};
};

const SRC_COLOR: Record<Src, string> = {正文: H.ink, 设定集: '#c9b98a', Wiki: H.dim};

const Card: React.FC<{i: number}> = ({i}) => {
  const f = useCurrentFrame();
  const s = SOULS[i];
  const {x, y} = pos(i);
  const p = prog(f, at(i), 10);
  if (p <= 0) return null;
  const focus = interpolate(f - at(i), [0, 8, STEP, STEP + 20], [1, 1, 1, 0.25], clamp);
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y,
        width: W,
        height: HGT,
        opacity: p,
        transform: `translateY(${(1 - p) * 30}px) scale(${interpolate(p, [0, 1], [1.08, 1])})`,
        background: 'rgba(5,7,10,0.84)',
        border: `3px solid ${s.color}`,
        boxShadow: `0 0 ${10 + 30 * focus}px ${s.color}${focus > 0.5 ? '88' : '44'}`,
      }}
    >
      <div style={{position: 'absolute', left: 130, top: 16, fontFamily: HF.heavy, fontWeight: 900, fontSize: 34, color: H.ink}}>{s.name}</div>
      <div style={{position: 'absolute', left: 132, top: 64, fontFamily: HF.heavy, fontWeight: 500, fontSize: 17, color: H.dim}}>{s.path} · 序列5</div>
      <div style={{position: 'absolute', right: 16, top: 20, background: s.color, color: '#0d0e12', fontFamily: HF.heavy, fontWeight: 900, fontSize: 20, padding: '4px 10px', transform: 'skewX(-10deg)'}}>{s.role}</div>
      <div style={{position: 'absolute', left: 16, right: 16, top: 104, height: 2, background: s.color, opacity: 0.5}} />
      {s.abilities.map((a, j) => {
        const q = prog(f, at(i) + 12 + j * 6, 8);
        return (
          <div key={a.name} style={{position: 'absolute', left: 18, right: 16, top: 116 + j * 64, opacity: q}}>
            <div style={{display: 'flex', alignItems: 'baseline', gap: 10}}>
              <span style={{fontFamily: HF.heavy, fontWeight: 900, fontSize: 22, color: s.color}}>{a.name}</span>
              {a.seq && <span style={{fontFamily: HF.heavy, fontWeight: 500, fontSize: 14, color: H.dim}}>{a.seq}</span>}
              <span style={{marginLeft: 'auto', fontFamily: HF.heavy, fontWeight: 700, fontSize: 13, color: SRC_COLOR[a.src], border: `1.5px solid ${SRC_COLOR[a.src]}`, padding: '0 6px', lineHeight: '20px'}}>{a.src}</span>
            </div>
            <div style={{fontFamily: HF.heavy, fontWeight: 500, fontSize: 17, color: H.ink, opacity: 0.85, marginTop: 3}}>{a.desc}</div>
          </div>
        );
      })}
    </div>
  );
};

/* 羊圈：七个灵魂逐个登场 */
export const Flock: React.FC = () => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill>
      <Arena left={0.12} right={0.8} speed={0.4} />
      <Slam x={960} y={100} text="满配羊圈" at={2} size={72} color={H.shep} />
      <Label x={960} y={160} a="c" text="七个序列5的灵魂 · 每个灵魂挑三种能力 · 一次驱使一个" at={12} size={21} color={H.dim} />
      {SOULS.map((_, i) => (
        <Card key={i} i={i} />
      ))}
      {SOULS.map((s, i) => {
        const {x, y} = pos(i);
        return <PixelSheep key={s.id} x={x + 64} y={y + 92} at={at(i) + 4} cell={6} glow={s.color} acc={s.id} />;
      })}
      <Label x={960} y={898} a="c" text="三种能力是我替牧羊人挑的（推演），可以挑低序列能力（Wiki 有记载）；能力描述按出处标注：正文 / 设定集 / Wiki" at={at(6) + 40} size={19} color={H.dim} />
      <Caption
        lines={[
          {at: at(0) + 6, text: '光之祭司：冲着“免疫恐惧”去的——逝者只有吓人这一张牌。'},
          {at: at(1) + 6, text: '海洋歌者：风缠住他的腿，风推着自己跑。'},
          {at: at(2) + 6, text: '秘偶大师：灵体之线让人变慢；同级秘偶化要时间。'},
          {at: at(3) + 6, text: '旅行家：闪现堵路。但它不会自动追踪逃跑的人。'},
          {at: at(4) + 6, text: '看门人：把冥界之门开在逃跑路线上。'},
          {at: at(5) + 6, text: '赢家：关键时刻的好运，再加一点灾祸。'},
          {at: at(6) + 6, text: '守护者：逝者没有伤害，防御用不上——所以拿来当打手。'},
          {at: at(6) + 90, text: '七只羊，三件事：扛住恐惧，追上去，堵住路。'},
        ]}
      />
      <Flash at={0} dur={6} />
    </AbsoluteFill>
  );
};
