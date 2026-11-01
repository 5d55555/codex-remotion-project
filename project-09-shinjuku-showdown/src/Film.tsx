import React, {useEffect, useLayoutEffect, useMemo, useRef, useState} from 'react';
import {AbsoluteFill, continueRender, delayRender, useCurrentFrame} from 'remotion';
import {FPS} from './engine/core';
import {Stage, TextItem} from './engine/stage';
import {assetsReady, loadAssets} from './engine/assets';
import {findShot} from './shots';
import {TextLayer} from './text';
import {useFonts} from './fonts';

let stage: Stage | null = null;

/** 画一帧：找到这一刻的镜头，交给它画，最后统一做后期。返回这一帧要显示的文字。 */
export const renderFrame = (st: Stage, frame: number): TextItem[] => {
  const T = frame / FPS;
  const shot = findShot(T);
  st.begin(T, T - shot.from);
  shot.draw(st, T - shot.from, T);
  st.flush();
  st.finish();
  return st.texts;
};

export const Film: React.FC = () => {
  const frame = useCurrentFrame();
  useFonts();
  const [ok, setOk] = useState(assetsReady());
  const [handle] = useState(() => (assetsReady() ? null : delayRender('assets', {timeoutInMilliseconds: 120000})));
  useEffect(() => {
    if (ok) return;
    loadAssets().then(() => {
      setOk(true);
      if (handle !== null) continueRender(handle);
    });
  }, [ok, handle]);
  const host = useRef<HTMLDivElement>(null);
  const texts = useMemo(() => {
    if (!ok) return [];
    stage ??= new Stage();
    return renderFrame(stage, frame);
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
