import React from 'react';
import {SCENES_A} from './a';
import {SCENES_B} from './b';
import {Flock} from './flock';

// 各段场景按 timeline.json 的 id 注册。
export const SCENES: Record<string, React.FC> = {...SCENES_A, flock: Flock, ...SCENES_B};
