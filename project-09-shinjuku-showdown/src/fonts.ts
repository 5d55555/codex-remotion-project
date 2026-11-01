import {useEffect, useState} from 'react';
import {continueRender, delayRender} from 'remotion';
import '@fontsource/noto-serif-jp/600.css';
import '@fontsource/noto-serif-jp/900.css';
import '@fontsource/noto-serif-sc/700.css';
import '@fontsource/noto-sans-sc/500.css';
import '@fontsource/yuji-syuku/400.css';
import '@fontsource/zen-antique/400.css';
import '@fontsource/dotgothic16/400.css';
import {GLYPHS} from './glyphs';

const FACES = [
  '600 20px "Noto Serif JP"',
  '900 20px "Noto Serif JP"',
  '700 20px "Noto Serif SC"',
  '500 20px "Noto Sans SC"',
  '400 20px "Yuji Syuku"',
  '400 20px "Zen Antique"',
  '400 20px "DotGothic16"',
];

let loading: Promise<unknown> | null = null;

// 中日文字体按 unicode-range 分片；渲染前把片中用到的字形一次加载完，避免个别帧回退字体。
export const useFonts = () => {
  const [handle] = useState(() => delayRender('fonts', {timeoutInMilliseconds: 120000}));
  useEffect(() => {
    loading ??= Promise.all(FACES.map((face) => document.fonts.load(face, GLYPHS))).then(() => document.fonts.ready);
    loading.then(() => continueRender(handle), () => continueRender(handle));
  }, [handle]);
};
