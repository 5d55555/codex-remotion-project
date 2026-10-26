import React from 'react';
import {AbsoluteFill, Audio, getStaticFiles, Sequence, staticFile, useCurrentFrame} from 'remotion';
import {useFonts} from './fonts';
import {prog} from './kit';
import timeline from './timeline.json';
import * as intro from './scenes/intro';
import * as dancer from './scenes/dancer';
import * as ascend from './scenes/ascend';

const SCENES: Record<string, React.FC> = {
  logo: intro.Logo,
  ch1: intro.Ch1,
  f01: intro.F01,
  f02: intro.F02,
  f03: intro.F03,
  f04: intro.F04,
  ...dancer.SCENES,
  ...ascend.SCENES,
};

const OVERLAP = timeline.overlap;

export const SEGMENTS = timeline.segments.reduce<{id: string; from: number; frames: number}[]>((acc, s, i) => {
  const from = i === 0 ? 0 : acc[i - 1].from + acc[i - 1].frames - OVERLAP;
  return [...acc, {id: s.id, from, frames: s.frames}];
}, []);

export const FILM_FRAMES = SEGMENTS[SEGMENTS.length - 1].from + SEGMENTS[SEGMENTS.length - 1].frames;

const FadeIn: React.FC<{enabled: boolean; children: React.ReactNode}> = ({enabled, children}) => {
  const f = useCurrentFrame();
  return <AbsoluteFill style={{opacity: enabled ? prog(f, 0, OVERLAP) : 1}}>{children}</AbsoluteFill>;
};

const hasScore = getStaticFiles().some((file) => file.name === 'audio/score.wav');

export const Film: React.FC = () => {
  useFonts();
  return (
    <AbsoluteFill style={{backgroundColor: '#e2dbc7'}}>
      {SEGMENTS.map((s, i) => {
        const Scene = SCENES[s.id];
        return (
          <Sequence key={s.id} from={s.from} durationInFrames={s.frames} name={s.id}>
            <FadeIn enabled={i > 0}>{Scene ? <Scene /> : null}</FadeIn>
          </Sequence>
        );
      })}
      {hasScore && <Audio src={staticFile('audio/score.wav')} />}
    </AbsoluteFill>
  );
};
