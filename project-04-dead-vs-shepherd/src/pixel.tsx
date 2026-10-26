import React from 'react';
import {useCurrentFrame} from 'remotion';
import {prog} from './kit';

/* 像素主角：橙色小怪物（14×9 格），两套服饰叠在上面。 */

const ORANGE = '#d97757';
const BODY = [
  '.oooooooooooo.',
  '.oooooooooooo.',
  'oooooooooooooo',
  'oooooooooooooo',
  '.oooooooooooo.',
  '.oooooooooooo.',
];
// 四条腿：第 2、4、9、11 列；走路时两组交替抬起。
const LEGS = [2, 4, 9, 11];

type Layer = {rows: string[]; col: number; row: number; pal: Record<string, string>};

// 逝者：深灰兜帽斗篷 + 冷青描边。
const DEAD: Layer[] = [
  {
    col: 0,
    row: -4,
    pal: {h: '#2d3a46', t: '#7fe3ff'},
    rows: [
      '....tttttt....',
      '...thhhhhht...',
      '..thhhhhhhht..',
      '.thhhhhhhhhht.',
      'thhh......hhht',
      'thh........hht',
      'th..........ht',
      'th..........ht',
      'thh........hht',
      'thh........hht',
      'tt..........tt',
    ],
  },
];

// 牧羊人：草帽（红帽带）、牧杖、握杖的手。
const SHEP: Layer[] = [
  {
    col: -2,
    row: -3,
    pal: {y: '#e8c26a', b: '#c2410c'},
    rows: ['.....yyyyyyyy.....', '.....bbbbbbbb.....', 'yyyyyyyyyyyyyyyyyy'],
  },
  {col: 14, row: 3, pal: {o: ORANGE}, rows: ['o']},
  {
    col: 15,
    row: -7,
    pal: {k: '#7a4e14'},
    rows: ['kkk.', 'k..k', 'k..k', 'k.k.', 'k...', 'k...', 'k...', 'k...', 'k...', 'k...', 'k...', 'k...', 'k...', 'k...', 'k...', 'k...'],
  },
];

const rects = (layer: Layer, cell: number, white: boolean) =>
  layer.rows.flatMap((r, ri) =>
    [...r].map((ch, ci) =>
      ch === '.' ? null : (
        <rect key={`${layer.col}-${layer.row}-${ri}-${ci}`} x={(layer.col + ci) * cell} y={(layer.row + ri) * cell} width={cell + 0.5} height={cell + 0.5} fill={white ? '#ffffff' : layer.pal[ch]} />
      ),
    ),
  );

export type Eyes = 'open' | 'x' | 'peek' | 'blink';

const Eye: React.FC<{cx: number; cy: number; cell: number; mode: Eyes}> = ({cx, cy, cell, mode}) => {
  const k = '#111111';
  const s = cell / 3;
  if (mode === 'open') return <rect x={cx} y={cy} width={cell} height={cell} fill={k} />;
  if (mode === 'blink') return <rect x={cx} y={cy + cell * 0.4} width={cell} height={cell * 0.25} fill={k} />;
  // × 眼：3×3 子像素。
  return (
    <>
      {[
        [0, 0],
        [2, 0],
        [1, 1],
        [0, 2],
        [2, 2],
      ].map(([i, j]) => (
        <rect key={`${i}${j}`} x={cx + i * s} y={cy + j * s} width={s + 0.3} height={s + 0.3} fill={k} />
      ))}
    </>
  );
};

export const PixelChar: React.FC<{
  kind: 'dead' | 'shep';
  x: number;
  y: number;
  cell?: number;
  at?: number;
  eyes?: Eyes;
  walk?: boolean;
  fallen?: number;
  flash?: boolean;
  flip?: boolean;
  tag?: boolean;
}> = ({kind, x, y, cell = 22, at = 0, eyes, walk = true, fallen = 0, flash = false, flip = false, tag = true}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10);
  if (p <= 0) return null;
  // 出场缩放按 0.25 一档跳，保持像素感。
  const pop = Math.min(1, Math.round(p * 4) / 4 + (p > 0.6 ? 0.1 * Math.sin(p * Math.PI) : 0));
  const bob = fallen > 0 ? 0 : (Math.floor((f + (kind === 'dead' ? 0 : 7)) / 10) % 2) * Math.round(cell * 0.25);
  const step = walk && fallen === 0 ? Math.floor(f / 7) % 2 : -1;
  const blink = (f + (kind === 'dead' ? 30 : 0)) % 96 < 4;
  const mode: Eyes = eyes ?? (blink ? 'blink' : 'open');
  const layers = kind === 'dead' ? DEAD : SHEP;
  const W = 14 * cell;
  const H9 = 9 * cell;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}} shapeRendering="crispEdges">
      <g transform={`translate(${x} ${y}) rotate(${-90 * fallen}) scale(${pop * (flip ? -1 : 1)} ${pop}) translate(${-W / 2} ${-H9 - bob})`}>
        {BODY.flatMap((r, ri) =>
          [...r].map((ch, ci) => (ch === 'o' ? <rect key={`b${ri}-${ci}`} x={ci * cell} y={ri * cell} width={cell + 0.5} height={cell + 0.5} fill={flash ? '#ffffff' : ORANGE} /> : null)),
        )}
        {LEGS.map((c, i) => {
          const lifted = step >= 0 && i % 2 === step;
          return <rect key={`l${c}`} x={c * cell} y={6 * cell} width={cell + 0.5} height={(lifted ? 2 : 3) * cell} fill={flash ? '#ffffff' : ORANGE} />;
        })}
        {!flash && <Eye cx={2 * cell} cy={2 * cell} cell={cell} mode={mode === 'peek' ? 'x' : mode} />}
        {!flash && <Eye cx={11 * cell} cy={2 * cell} cell={cell} mode={mode === 'peek' ? 'blink' : mode} />}
        {layers.map((l) => rects(l, cell, flash))}
        {kind === 'dead' && tag && (
          <g>
            <rect x={12 * cell} y={8.2 * cell} width={cell * 0.9} height={cell * 0.25} fill="#9aa3ad" />
            <rect x={12.8 * cell} y={7.7 * cell} width={cell * 1.6} height={cell * 1.2} fill={flash ? '#ffffff' : '#f4f1ea'} />
            <rect x={13.05 * cell} y={7.95 * cell} width={cell * 0.3} height={cell * 0.3} fill="#2d3a46" />
          </g>
        )}
      </g>
    </svg>
  );
};

/* 灵魂羊：被放牧的灵，带琥珀色光晕。 */
const SHEEP = ['..wwwww..', '.wwwwwww.', 'kkwwwwwww', 'kkwwwwwww', '.wwwwwww.', '..k...k..'];

export const PixelSheep: React.FC<{x: number; y: number; at: number; cell?: number; glow?: string}> = ({x, y, at, cell = 10, glow = '#ffb340'}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10);
  if (p <= 0) return null;
  const bob = (Math.floor((f + x) / 9) % 2) * Math.round(cell * 0.5);
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}} shapeRendering="crispEdges">
      <g transform={`translate(${x} ${y - bob}) scale(${Math.round(p * 4) / 4}) translate(${-4.5 * cell} ${-6 * cell})`} opacity={0.92} style={{filter: `drop-shadow(0 0 8px ${glow})`}}>
        {SHEEP.flatMap((r, ri) =>
          [...r].map((ch, ci) => (ch === '.' ? null : <rect key={`${ri}-${ci}`} x={ci * cell} y={ri * cell} width={cell + 0.5} height={cell + 0.5} fill={ch === 'w' ? '#f4f1ea' : '#1a1a1a'} />)),
        )}
      </g>
    </svg>
  );
};
