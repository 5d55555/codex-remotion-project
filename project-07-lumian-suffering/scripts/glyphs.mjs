// 扫描 src 下所有源码，收集片中出现的字符，生成 src/glyphs.ts 供字体预加载使用。
import {readdirSync, readFileSync, statSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';

const walk = (dir) => readdirSync(dir).flatMap((name) => {
  const p = join(dir, name);
  return statSync(p).isDirectory() ? walk(p) : [p];
});

const files = walk('src').filter((p) => /\.(tsx?|json)$/.test(p) && !p.endsWith('glyphs.ts'));
const chars = new Set();
for (let c = 32; c < 127; c++) chars.add(String.fromCharCode(c));
for (const file of files) for (const ch of readFileSync(file, 'utf8')) if (ch.codePointAt(0) > 127) chars.add(ch);

const text = [...chars].join('');
writeFileSync('src/glyphs.ts', `// 由 scripts/glyphs.mjs 生成，请勿手改。\nexport const GLYPHS = ${JSON.stringify(text)};\n`);
console.log(`glyphs: ${chars.size} chars from ${files.length} files`);
