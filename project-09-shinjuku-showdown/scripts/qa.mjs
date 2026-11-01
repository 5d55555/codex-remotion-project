// 打包一次，渲染指定帧的静帧到 out/qa/，并报每帧耗时。用法：node scripts/qa.mjs 150 275 ...（也可写秒：12.5s）
import {bundle} from '@remotion/bundler';
import {openBrowser, renderStill, selectComposition} from '@remotion/renderer';

const browserExecutable = '/opt/pw-browsers/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell';
const frames = process.argv.slice(2).map((a) => (a.endsWith('s') ? Math.round(parseFloat(a) * 60) : Number(a)));
const scale = Number(process.env.QA_SCALE ?? 0.5);
const serveUrl = await bundle({entryPoint: 'src/index.tsx'});
const puppeteerInstance = await openBrowser('chrome', {browserExecutable});
const composition = await selectComposition({serveUrl, id: 'Shinjuku', puppeteerInstance});
for (const frame of frames) {
  const t0 = Date.now();
  await renderStill({composition, serveUrl, frame, output: `out/qa/f${String(frame).padStart(5, '0')}.png`, puppeteerInstance, scale, imageFormat: 'png'});
  console.log('still', frame, (frame / 60).toFixed(2) + 's', Date.now() - t0, 'ms');
}
await puppeteerInstance.close({silent: true});
