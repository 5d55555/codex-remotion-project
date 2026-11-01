import {Stage} from '../engine/stage';

export type Cue = [number, string, number?, number?]; // [镜头内秒数, 音效名, 增益 dB?, 声像 -1..1?]
export type Shot = {id: string; from: number; to: number; draw: (st: Stage, t: number, T: number) => void; cues?: Cue[]};

/** 按小节排镜头：给每个镜头开始秒数，结束 = 下一个镜头开始。 */
export const seq = (list: {id: string; at: number; draw: Shot['draw']; cues?: Cue[]}[], end: number): Shot[] =>
  list.map((s, i) => ({id: s.id, from: s.at, to: i + 1 < list.length ? list[i + 1].at : end, draw: s.draw, cues: s.cues}));
