import React from 'react';
import {Composition, registerRoot} from 'remotion';
import {Film} from './Film';
import {DURATION, FPS, H, W} from './engine/core';

registerRoot(() => <Composition id="Shinjuku" component={Film} durationInFrames={DURATION} fps={FPS} width={W} height={H} />);
