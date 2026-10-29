import React from 'react';
import {AbsoluteFill, Sequence, useCurrentFrame} from 'remotion';
import {useFonts} from './fonts';
import {prog} from './kit';
import timeline from './timeline.json';
import {SCENES} from './scenes';

const OVERLAP = timeline.overlap;

export const SEGMENTS = timeline.segments.reduce<{id: string; from: number; frames: number}[]>((acc, s, i) => {
  const from = i === 0 ? 0 : acc[i - 1].from + acc[i - 1].frames - OVERLAP;
  return [...acc, {id: s.id, from, frames: s.frames}];
}, []);

export const FILM_FRAMES = SEGMENTS[SEGMENTS.length - 1].from + SEGMENTS[SEGMENTS.length - 1].frames;

const FadeIn: React.FC<{enabled: boolean; children: React.ReactNode}> = ({enabled, children}) => {
  const f = useCurrentFrame();
  return <AbsoluteFill style={{opacity: enabled && OVERLAP > 0 ? prog(f, 0, OVERLAP) : 1}}>{children}</AbsoluteFill>;
};

export const Film: React.FC = () => {
  useFonts();
  return (
    <AbsoluteFill style={{backgroundColor: '#120a06'}}>
      {SEGMENTS.map((s, i) => {
        const Scene = SCENES[s.id];
        return (
          <Sequence key={s.id} from={s.from} durationInFrames={s.frames} name={s.id}>
            <FadeIn enabled={i > 0}>{Scene ? <Scene /> : null}</FadeIn>
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
