import {useEffect, useState} from 'react';
import {continueRender, delayRender} from 'remotion';
import '@fontsource/noto-sans-sc/400.css';
import '@fontsource/noto-sans-sc/500.css';
import '@fontsource/noto-sans-sc/700.css';
import '@fontsource/noto-sans-sc/900.css';
import '@fontsource/noto-serif-sc/700.css';
import '@fontsource/noto-serif-sc/900.css';
import '@fontsource/zcool-qingke-huangyou/400.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';
import {GLYPHS} from './glyphs';

const FACES = [
  '400 20px "Noto Sans SC"',
  '500 20px "Noto Sans SC"',
  '700 20px "Noto Sans SC"',
  '900 20px "Noto Sans SC"',
  '700 20px "Noto Serif SC"',
  '900 20px "Noto Serif SC"',
  '400 20px "ZCOOL QingKe HuangYou"',
  '400 20px "IBM Plex Mono"',
  '500 20px "IBM Plex Mono"',
];

let loading: Promise<unknown> | null = null;

// 中文字体按 unicode-range 分片加载；渲染前一次性加载片中用到的全部字形，避免个别帧回退字体。
export const useFonts = () => {
  const [handle] = useState(() => delayRender('fonts', {timeoutInMilliseconds: 120000}));
  useEffect(() => {
    loading ??= Promise.all(FACES.map((face) => document.fonts.load(face, GLYPHS))).then(() => document.fonts.ready);
    loading.then(() => continueRender(handle), () => continueRender(handle));
  }, [handle]);
};
