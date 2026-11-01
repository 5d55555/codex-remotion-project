// 把所有镜头里写的音效提示导出成 out/cues.json：[[秒, 名字, 增益dB, 声像], ...]
import {build} from 'esbuild';
import {mkdirSync, writeFileSync} from 'node:fs';

const res = await build({
  entryPoints: ['src/shots/index.ts'], bundle: true, platform: 'node', format: 'esm', write: false,
  loader: {'.json': 'json'}, logLevel: 'error',
});
const code = res.outputFiles[0].text;
const url = 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const {SHOTS} = await import(url);
const out = [];
for (const s of SHOTS) for (const [t, name, gain = 0, pan = 0] of s.cues ?? []) out.push([+(s.from + t).toFixed(4), name, gain, pan, s.id]);
out.sort((a, b) => a[0] - b[0]);
mkdirSync('out', {recursive: true});
writeFileSync('out/cues.json', JSON.stringify(out, null, 0));
const names = [...new Set(out.map((c) => c[1]))].sort();
console.log(out.length, 'cues,', names.length, 'kinds:', names.join(' '));
