import React from 'react';
import {Composition, registerRoot} from 'remotion';
import {Film, FILM_FRAMES} from './Film';

registerRoot(() => <Composition id="DeadVsShepherd" component={Film} durationInFrames={FILM_FRAMES} fps={30} width={1920} height={1080} />);
