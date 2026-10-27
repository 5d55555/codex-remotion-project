// 打包一次，渲染指定帧的静帧到 out/qa/，用于逐帧检查画面。用法：node scripts/qa.mjs 150 275 ...
import {bundle} from '@remotion/bundler';
import {openBrowser, renderStill, selectComposition} from '@remotion/renderer';

const browserExecutable = '/opt/pw-browsers/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell';
const frames = process.argv.slice(2).map(Number);
const scale = Number(process.env.QA_SCALE ?? 0.5);
const serveUrl = await bundle({entryPoint: 'src/index.tsx'});
const puppeteerInstance = await openBrowser('chrome', {browserExecutable});
const composition = await selectComposition({serveUrl, id: 'LumianSuffering', puppeteerInstance});
for (const frame of frames) {
  await renderStill({composition, serveUrl, frame, output: `out/qa/f${String(frame).padStart(5, '0')}.png`, puppeteerInstance, scale});
  console.log('still', frame);
}
await puppeteerInstance.close({silent: true});
