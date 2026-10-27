import React from 'react';
import {AbsoluteFill} from 'remotion';
import {Lumian} from '../lumian';
import {Bubble, Paper, Panel, Sfx, SpeedLines, Tally, Tone} from '../manga';

const FACES = ['smirk', 'relief', 'pain', 'shock', 'dead', 'angry', 'calm', 'sad'] as const;
export const Test: React.FC = () => (
  <Paper>
    {FACES.map((fc, i) => (
      <Panel key={fc} x={40 + (i % 4) * 470} y={60 + Math.floor(i / 4) * 500} w={440} h={470} at={0}>
        {i === 2 && <SpeedLines w={440} h={470} n={50} />}
        {i === 4 && <Tone opacity={0.35} />}
        <Lumian x={220} y={200} s={0.95} face={fc} bandage={i === 2} sweat={i === 1 || i === 3} gloom={i === 4} hood={i === 7} />
      </Panel>
    ))}
    <Sfx x={700} y={300} text="嘶……" at={0} size={90} />
    <Bubble x={1300} y={420} w={420} h={170} at={0} text="为什么非得安排我？" tail={{x: 1320, y: 640}} />
    <Tally count={7} x={1500} y={900} />
  </Paper>
);
export const X = AbsoluteFill;
