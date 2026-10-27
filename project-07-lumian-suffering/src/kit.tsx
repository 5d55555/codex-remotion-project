import {Easing} from 'remotion';

const EASE = Easing.bezier(0.45, 0, 0.2, 1);
export const prog = (f: number, at: number, dur: number) => EASE(Math.min(1, Math.max(0, (f - at) / dur)));

export const pol = (cx: number, cy: number, r: number, deg: number): [number, number] => [
  cx + r * Math.cos((deg * Math.PI) / 180),
  cy + r * Math.sin((deg * Math.PI) / 180),
];
