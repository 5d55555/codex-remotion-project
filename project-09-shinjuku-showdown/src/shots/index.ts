import {DURATION, FPS} from '../engine/core';
import {Shot} from './types';
import act1 from './act1';
import act2 from './act2';
import act3 from './act3';
import act4 from './act4';
import act5 from './act5';
import act6 from './act6';

const black: Shot = {id: 'black', from: 0, to: DURATION / FPS, draw: (st) => st.fill('#000', 1)};

export const SHOTS: Shot[] = [...act1, ...act2, ...act3, ...act4, ...act5, ...act6];

/** 这一刻在放哪个镜头（镜头首尾相接、按时间排好）。 */
export const findShot = (T: number): Shot => {
  let lo = 0, hi = SHOTS.length - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const s = SHOTS[mid];
    if (T < s.from) hi = mid - 1;
    else if (T >= s.to) lo = mid + 1;
    else return s;
  }
  return black;
};
