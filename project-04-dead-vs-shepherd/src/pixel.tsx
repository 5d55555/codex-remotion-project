import React from 'react';
import {useCurrentFrame} from 'remotion';
import {prog} from './kit';

/*
 * 像素主角：按 Claw'd 的比例——身体 8×6 格，两侧手臂各 2 格宽，
 * 眼睛在第 2 行，四条腿 2 格高。整体网格 12 列 × 8 行（手臂占第 0—1、10—11 列）。
 */

const ORANGE = '#d97757';
const BODY = [
  '..oooooooo..',
  '..oooooooo..',
  'oooooooooooo',
  'oooooooooooo',
  '..oooooooo..',
  '..oooooooo..',
];
const LEGS = [2, 4, 7, 9];
const EYES = [3, 8];
const COLS = 12;
const ROWS = 8;

type Layer = {rows: string[]; col: number; row: number; pal: Record<string, string>};

// 逝者：深灰兜帽斗篷 + 冷青描边，袖子盖住手臂。
const DEAD: Layer[] = [
  {
    col: 0,
    row: -3,
    pal: {h: '#2d3a46', t: '#7fe3ff'},
    rows: [
      '...tttttt...',
      '..thhhhhht..',
      '.thhhhhhhht.',
      '.th......ht.',
      '.th......ht.',
      'thh......hht',
      'thh......hht',
      '.th......ht.',
      '.th......ht.',
      '.t........t.',
    ],
  },
];

// 牧羊人：草帽（红帽带）、握杖的手、牧杖。
const SHEP: Layer[] = [
  {col: -1, row: -3, pal: {y: '#e8c26a', b: '#c2410c'}, rows: ['....yyyyyy....', '....bbbbbb....', 'yyyyyyyyyyyyyy']},
  {col: 12, row: 3, pal: {o: ORANGE}, rows: ['o']},
  {
    col: 13,
    row: -6,
    pal: {k: '#7a4e14'},
    rows: ['kkk', 'k.k', 'k.k', 'k..', 'k..', 'k..', 'k..', 'k..', 'k..', 'k..', 'k..', 'k..', 'k..', 'k..'],
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

// open：方眼；blink：一条缝；x：×眼；squint：> <；peek：一只×一只缝。
export type Eyes = 'open' | 'x' | 'peek' | 'blink' | 'squint';

const Eye: React.FC<{cx: number; cy: number; cell: number; mode: Eyes; side: 'L' | 'R'}> = ({cx, cy, cell, mode, side}) => {
  const k = '#111111';
  const s = cell / 3;
  if (mode === 'open') return <rect x={cx} y={cy} width={cell} height={cell} fill={k} />;
  if (mode === 'blink') return <rect x={cx} y={cy + cell * 0.4} width={cell} height={cell * 0.25} fill={k} />;
  if (mode === 'squint') {
    const d = side === 'L' ? `M${cx + cell * 0.1} ${cy - cell * 0.15} L${cx + cell * 0.9} ${cy + cell * 0.5} L${cx + cell * 0.1} ${cy + cell * 1.15}` : `M${cx + cell * 0.9} ${cy - cell * 0.15} L${cx + cell * 0.1} ${cy + cell * 0.5} L${cx + cell * 0.9} ${cy + cell * 1.15}`;
    return <path d={d} stroke={k} strokeWidth={cell * 0.3} fill="none" strokeLinejoin="miter" />;
  }
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
  kind: 'dead' | 'shep' | 'plain';
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
  shake?: number;
}> = ({kind, x, y, cell = 22, at = 0, eyes, walk = true, fallen = 0, flash = false, flip = false, tag = true, shake = 0}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10);
  if (p <= 0) return null;
  // 出场缩放按 0.25 一档跳，保持像素感。
  const pop = Math.min(1, Math.round(p * 4) / 4);
  const bob = fallen > 0 ? 0 : (Math.floor((f + (kind === 'dead' ? 0 : 7)) / 10) % 2) * Math.round(cell * 0.25);
  const step = walk && fallen === 0 ? Math.floor(f / 7) % 2 : -1;
  const blink = (f + (kind === 'dead' ? 30 : 0)) % 96 < 4;
  const mode: Eyes = eyes ?? (blink ? 'blink' : 'open');
  const layers = kind === 'dead' ? DEAD : kind === 'shep' ? SHEP : [];
  const W = COLS * cell;
  const jitter = shake ? Math.sin(f * 1.9) * shake : 0;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}} shapeRendering="crispEdges">
      <g transform={`translate(${x + jitter} ${y - (fallen * W) / 2}) rotate(${-90 * fallen}) scale(${pop * (flip ? -1 : 1)} ${pop}) translate(${-W / 2} ${-ROWS * cell - bob})`}>
        {BODY.flatMap((r, ri) =>
          [...r].map((ch, ci) => (ch === 'o' ? <rect key={`b${ri}-${ci}`} x={ci * cell} y={ri * cell} width={cell + 0.5} height={cell + 0.5} fill={flash ? '#ffffff' : ORANGE} /> : null)),
        )}
        {LEGS.map((c, i) => {
          const lifted = step >= 0 && i % 2 === step;
          return <rect key={`l${c}`} x={c * cell} y={6 * cell} width={cell + 0.5} height={(lifted ? 1.5 : 2) * cell} fill={flash ? '#ffffff' : ORANGE} />;
        })}
        {!flash && <Eye cx={EYES[0] * cell} cy={cell} cell={cell} side="L" mode={mode === 'peek' ? 'x' : mode} />}
        {!flash && <Eye cx={EYES[1] * cell} cy={cell} cell={cell} side="R" mode={mode === 'peek' ? 'blink' : mode} />}
        {layers.map((l) => rects(l, cell, flash))}
        {kind === 'dead' && tag && (
          <g>
            <rect x={9.6 * cell} y={7.3 * cell} width={cell * 0.8} height={cell * 0.22} fill="#9aa3ad" />
            <rect x={10.3 * cell} y={6.9 * cell} width={cell * 1.5} height={cell * 1.1} fill={flash ? '#ffffff' : '#f4f1ea'} />
            <rect x={10.55 * cell} y={7.15 * cell} width={cell * 0.3} height={cell * 0.3} fill="#2d3a46" />
          </g>
        )}
      </g>
    </svg>
  );
};

/* 灵魂羊：被放牧的灵，带光晕；七个具名灵魂各有一件配饰。羊头朝左。 */
const SHEEP = ['..wwwww..', '.wwwwwww.', 'kkwwwwwww', 'kkwwwwwww', '.wwwwwww.', '..k...k..'];

// 配饰用 c 代表灵魂色，其余字母是固定色。
const ACC: Record<string, Layer> = {
  light: {col: 2, row: -4, pal: {}, rows: ['.ccc.', 'c...c', '.ccc.']},
  sea: {col: 7, row: -5, pal: {}, rows: ['.cc', '.c.', '.c.', 'cc.', 'cc.']},
  puppet: {col: 1, row: -5, pal: {}, rows: ['ccccccc', '.c...c.', '.c...c.', '.c...c.', '.c...c.']},
  travel: {col: 10, row: -2, pal: {x: '#0a1a10', g: '#f4f1ea'}, rows: ['cccc', 'cxxc', 'cxxc', 'cxxc', 'cxgc', 'cxxc', 'cxxc', 'cxxc']},
  gate: {col: 1, row: -4, pal: {}, rows: ['cc.....', 'c.ccccc', 'cc..c.c']},
  luck: {col: 3, row: -5, pal: {p: '#1a1a1a'}, rows: ['cccc', 'cpcc', 'ccpc', 'cccc']},
  guard: {col: -3, row: 1, pal: {w: '#f4f1ea'}, rows: ['ccc', 'cwc', 'ccc', '.c.']},
};

export const PixelSheep: React.FC<{x: number; y: number; at: number; cell?: number; glow?: string; dim?: boolean; acc?: string; scale?: number}> = ({
  x,
  y,
  at,
  cell = 10,
  glow = '#ffb340',
  dim = false,
  acc,
  scale = 1,
}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 10);
  if (p <= 0) return null;
  const bob = (Math.floor((f + x) / 9) % 2) * Math.round(cell * 0.5);
  const layer = acc ? ACC[acc] : undefined;
  return (
    <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}} shapeRendering="crispEdges">
      <g transform={`translate(${x} ${y - bob}) scale(${(Math.round(p * 4) / 4) * scale}) translate(${-4.5 * cell} ${-6 * cell})`} opacity={dim ? 0.35 : 0.95} style={{filter: dim ? undefined : `drop-shadow(0 0 8px ${glow})`}}>
        {SHEEP.flatMap((r, ri) =>
          [...r].map((ch, ci) => (ch === '.' ? null : <rect key={`${ri}-${ci}`} x={ci * cell} y={ri * cell} width={cell + 0.5} height={cell + 0.5} fill={ch === 'w' ? '#f4f1ea' : '#1a1a1a'} />)),
        )}
        {layer && rects({...layer, pal: {c: glow, ...layer.pal}}, cell, false)}
      </g>
    </svg>
  );
};

/* 花盆：赢家“引来灾祸”的像素示意。 */
const POT = ['.gg.g', 'gggg.', 'rrrrr', '.rrr.', '.rrr.'];

export const PixelPot: React.FC<{x: number; y: number; cell?: number; rot?: number}> = ({x, y, cell = 10, rot = 0}) => (
  <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, overflow: 'visible'}} shapeRendering="crispEdges">
    <g transform={`translate(${x} ${y}) rotate(${rot}) translate(${-2.5 * cell} ${-2.5 * cell})`}>
      {POT.flatMap((r, ri) =>
        [...r].map((ch, ci) => (ch === '.' ? null : <rect key={`${ri}-${ci}`} x={ci * cell} y={ri * cell} width={cell + 0.5} height={cell + 0.5} fill={ch === 'g' ? '#4caf50' : '#c0603a'} />)),
      )}
    </g>
  </svg>
);
