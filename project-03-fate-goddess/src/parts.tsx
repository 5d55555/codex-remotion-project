import React from 'react';
import {useCurrentFrame} from 'remotion';
import {Badge, C, F, Figure, P, prog, Svg, Theme, Txt} from './kit';

export const TOTAL = 17;

export type Src = {kind: '正文' | '整理' | '待核' | '观点'; src: string};

// 战力星级：n 颗实心，满分 5。
export const stars = (n: number) => '★'.repeat(n) + '☆'.repeat(Math.max(0, 5 - n));

// 命运之线：一条随时间轻微摆动的波浪线。
export const Thread: React.FC<{x1: number; x2: number; y: number; amp?: number; phase?: number; at: number; c?: string; w?: number; op?: number}> = ({x1, x2, y, amp = 10, phase = 0, at, c, w = 1.6, op = 0.7}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 30);
  if (p <= 0) return null;
  const n = 40;
  const end = x1 + (x2 - x1) * p;
  const d = Array.from({length: n + 1}, (_, i) => {
    const x = x1 + ((end - x1) * i) / n;
    const yy = y + Math.sin(x / 60 + phase + f * 0.04) * amp;
    return `${i ? 'L' : 'M'}${x} ${yy}`;
  }).join(' ');
  return <path d={d} stroke={c ?? C.gold} strokeWidth={w} fill="none" opacity={op} />;
};

// 序列页：左侧标题，右侧图标 + 能力清单，底部锐评行与来源。
export const SeqFigure: React.FC<{
  seq: number;
  name: string;
  sub: string;
  rank: string;
  power: number;
  fig: number;
  theme?: Theme;
  abilities: string[];
  source: Src;
  note?: {text: string} & Src;
  children: React.ReactNode;
}> = ({seq, name, sub, rank, power, fig, theme = 'paper', abilities, source, note, children}) => {
  const node = 9 - seq;
  const accent = theme === 'paper' ? C.red : C.gold;
  return (
    <Figure
      theme={theme}
      rays={theme === 'navy' ? {cx: 1130, cy: 480} : undefined}
      chrome={{section: rank, value: `${seq} · ${name}`, node, prev: Math.max(0, node - 1), fig, total: TOTAL}}
      tag={[`序列${seq}`, name, `辈分：${rank}`]}
      title={name}
      sub={sub}
    >
      {children}
      <Svg>
        <P d="M1000 752 L1860 752" at={80} dur={24} w={1.2} op={0.5} />
      </Svg>
      {abilities.map((a, i) => (
        <React.Fragment key={a}>
          <Txt x={1360} y={300 + i * 78} text={String(i + 1).padStart(2, '0')} at={36 + i * 14} size={18} font={F.mono} w={500} color={accent} fade />
          <Txt x={1410} y={300 + i * 78} text={a} at={38 + i * 14} size={26} font={F.serif} w={900} stagger={1} />
        </React.Fragment>
      ))}
      <Badge x={1000} y={798} at={96} kind="观点" src="" />
      <Txt x={1090} y={798} text={`辈分：${rank}`} at={100} size={26} font={F.serif} w={900} color={accent} />
      <Txt x={1290} y={798} text={`战力  ${stars(power)}`} at={110} size={26} font={F.serif} w={900} color={accent} stagger={2} />
      <Badge x={1000} y={848} at={126} kind={source.kind} src={source.src} />
      {note && <Txt x={1000} y={896} text={note.text} at={140} size={20} stagger={0.6} />}
      {note && <Badge x={1000} y={934} at={156} kind={note.kind} src={note.src} />}
    </Figure>
  );
};
