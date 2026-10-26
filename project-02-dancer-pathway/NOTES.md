# 宿命之环 · 恩赐途径（舞蹈家）动画

横屏 1920×1080、30fps、约 4 分 18 秒（7740 帧）。画风参考“诡秘之主全历史动画”：旧纸底、红黑线稿图解、深蓝放射光、章节红卡、底部时间轴与图号。

## 结构

- 开场标志 →
- 第一章《恩赐》：图01 宿命之环、图02 恩赐途径、图03 卢米安双线、图04 十个序列
- 第二章《舞蹈家》：图05 身体、图06 以舞祈祷、图07 两支舞
- 第三章《案例》（按剧情先后）：图08 第一卷第72章《祭舞》、图09 第一卷第83章《突遇》、图10 第二卷第52章《特质》、图11 第二卷第80章《配合》、图12 代价
- 第四章《晋升》（含终局剧透）：图13—21 序列8→0 各一张
- 图22 一个环 → 片尾标志与资料说明

时间轴在 `src/timeline.json`：视频（`src/Film.tsx`）和配乐（`scripts/make_score.py`）共用这份文件，相邻两段交叉淡化 15 帧。

## 文件

- `src/kit.tsx`：版式与动画组件（背景、栏目条与序列时间轴、模糊渐显文字、逐笔画线、人物、灵体、徽章、章节红卡、标志卡）。
- `src/scenes/intro.tsx`、`dancer.tsx`、`ascend.tsx`：各张图。
- `src/fonts.ts`：思源黑体、思源宋体、站酷庆科黄油体、IBM Plex Mono（fontsource 本地包），渲染前按 `src/glyphs.ts` 预加载全部用到的字。
- `scripts/glyphs.mjs`：扫描源码生成 `src/glyphs.ts`。改了任何文字后，`npm run typecheck`、`qa`、`build` 都会自动重新生成。
- `scripts/qa.mjs`：只打包一次，渲染指定帧的静帧到 `out/qa/`，例如 `node scripts/qa.mjs 535 1335`。
- `scripts/make_score.py`：只依赖 numpy 的配乐合成器，输出 `public/audio/score.wav`（不进仓库，`npm run score` 重新生成）。
- `scripts/make_textures.py`：用 numpy 程序化生成 `public/tex/` 下的旧纸、红纸、深蓝三张底纹（`npm run textures`）。全片没有 AI 生图素材，画面全部由代码绘制。
- `notes/`：研究笔记，以及 `factcheck.md`（对片中全部说法的逐条核查，已按其结论修改）。研究笔记里个别章号与说法有误，以核查结果为准。

## 命令

```bash
npm install
npm run textures   # 生成底纹（约 10 秒）
npm run score      # 生成配乐（约 30 秒）
npm run qa -- 535 1335
npm run build      # 渲染 out/dancer-pathway.mp4
```

## 注意

- 渲染需要指定浏览器（已写进 `build` 与 `qa.mjs`）：`/opt/pw-browsers/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell`。
- 徽章分三类：“正文核对”（对照章节正文后的概括）、“二手整理”（Wiki、百科）、“待核实”。正文来自网络转载本，章节以“卷名＋章名”为准，不同站点的全书编号可能不一致。
