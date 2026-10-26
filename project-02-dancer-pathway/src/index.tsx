import React from 'react';
import {registerRoot, Composition} from 'remotion';
import {DancerPathway, DANCER_PATHWAY_DURATION} from './DancerPathway';

registerRoot(() => (
  <Composition
    id="DancerPathway"
    component={DancerPathway}
    durationInFrames={DANCER_PATHWAY_DURATION}
    fps={30}
    width={1080}
    height={1920}
  />
));
