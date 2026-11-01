import React, {useEffect, useLayoutEffect, useMemo, useRef, useState} from 'react';
import {AbsoluteFill, continueRender, delayRender, useCurrentFrame} from 'remotion';
import {Stage, TextItem} from '../engine/stage';
import {TextLayer} from '../text';
import {useFonts} from '../fonts';
import {loadStills, stillsReady} from './stills';
import {ALL_IMAGES, renderV2} from './film2';

let stage: Stage | null = null;

const draw = (st: Stage, frame: number): TextItem[] => {
  st.begin(frame / 60, 0);
  renderV2(st, frame);
  st.flush();
  st.finish();
  return st.texts;
};

export const Film2: React.FC = () => {
  const frame = useCurrentFrame();
  useFonts();
  const [ok, setOk] = useState(stillsReady());
  const [handle] = useState(() => (stillsReady() ? null : delayRender('stills', {timeoutInMilliseconds: 180000})));
  useEffect(() => {
    if (ok) return;
    loadStills(ALL_IMAGES).then(() => {
      setOk(true);
      if (handle !== null) continueRender(handle);
    });
  }, [ok, handle]);
  const host = useRef<HTMLDivElement>(null);
  const texts = useMemo(() => {
    if (!ok) return [];
    stage ??= new Stage();
    return draw(stage, frame);
  }, [frame, ok]);
  useLayoutEffect(() => {
    if (ok && stage && host.current && stage.canvas.parentElement !== host.current) {
      stage.canvas.style.position = 'absolute';
      stage.canvas.style.inset = '0';
      host.current.appendChild(stage.canvas);
    }
  }, [ok]);
  return (
    <AbsoluteFill style={{background: '#000'}}>
      <div ref={host} style={{position: 'absolute', inset: 0}} />
      <TextLayer items={texts} />
    </AbsoluteFill>
  );
};
