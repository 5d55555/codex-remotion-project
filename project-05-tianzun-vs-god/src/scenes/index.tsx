import React from 'react';
import {SCENES_FIGHT} from './fight';
import {SCENES_INTRO} from './intro';
import {SCENES_OUTRO} from './outro';

// 各段场景按 timeline.json 的 id 注册。
export const SCENES: Record<string, React.FC> = {...SCENES_INTRO, ...SCENES_FIGHT, ...SCENES_OUTRO};
