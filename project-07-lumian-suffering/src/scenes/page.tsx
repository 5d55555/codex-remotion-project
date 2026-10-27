import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {Paper, Tally, VolTag} from '../manga';
import {C} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/* 翻页：上一页像纸一样从右往左翻走，露出这一页。 */
const PageTurn: React.FC = () => {
  const f = useCurrentFrame();
  if (f > 14) return null;
  const x = interpolate(f, [0, 14], [0, -2000], {...clamp, easing: (v) => v * v});
  const skew = interpolate(f, [0, 14], [0, -12], clamp);
  return (
    <AbsoluteFill style={{transform: `translateX(${x}px) skewY(${skew}deg)`, transformOrigin: 'left center'}}>
      <AbsoluteFill style={{backgroundColor: C.paper, boxShadow: '30px 0 60px rgba(0,0,0,0.35)'}} />
    </AbsoluteFill>
  );
};

/*
 * 一页漫画：卷标、受苦计数、翻页。
 * base 是这一页开始时的计数，events 是这一页里每次 +1 的帧号。
 */
export const Page: React.FC<{vol?: string; name?: string; base: number; events?: number[]; dark?: boolean; turn?: boolean; children: React.ReactNode}> = ({vol, name, base, events = [], dark, turn = true, children}) => {
  const f = useCurrentFrame();
  const done = events.filter((t) => f >= t);
  const count = base + done.length;
  const bumpAt = done.length ? done[done.length - 1] : -99;
  return (
    <Paper dark={dark}>
      {children}
      {vol && name && <VolTag vol={vol} name={name} at={6} />}
      <Tally count={count} right={40} y={30} bumpAt={bumpAt} />
      {turn && <PageTurn />}
    </Paper>
  );
};
