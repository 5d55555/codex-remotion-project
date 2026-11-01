import React from 'react';
import {Composition, registerRoot} from 'remotion';
import {Film2} from './v2/Film2';
import {DURATION, FPS, H, W} from './engine/core';

// 第二版：重绘关键帧 + 快切（第一版像素小人版本的代码保留在 src/shots，供参考）
registerRoot(() => <Composition id="Shinjuku" component={Film2} durationInFrames={DURATION} fps={FPS} width={W} height={H} />);
