# 宿命之环 · 恩赐途径（舞蹈家）动画

横屏 1920×1080、30fps、约 4 分 18 秒（7740 帧）。画风参考“诡秘之主全历史动画”：旧纸底、红黑线稿图解、深蓝放射光、章节红卡、底部时间轴与图号。

## 结构

- 开场标志 →
- 第一章《恩赐》：图01 宿命之环、图02 恩赐途径、图03 卢米安双线、图04 十个序列
- 第二章《舞蹈家》：图05 身体、图06 以舞祈祷、图07 两支舞
- 第三章《案例》：图08 第80章、图09 第52章、图10 第72章、图11 第83章、图12 代价
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
- `public/tex/`：旧纸、红纸、深蓝三张底纹（由 Codex 生图生成）。
- `notes/`：研究笔记，每条注明【原文】【摘要】【二手】。

## 命令

```bash
npm install
npm run score      # 生成配乐（约 30 秒）
npm run qa -- 535 1335
npm run build      # 渲染 out/dancer-pathway.mp4
```

## 注意

- 渲染需要指定浏览器（已写进 `build` 与 `qa.mjs`）：`/opt/pw-browsers/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell`。
- 片中“原文”徽章表示研究时读到了章节正文，但正文来自网络转载站，不同站点的章节编号（卷内/全书）可能不一致；“摘要”只见搜索摘要，待原著核实。
- Codex 生图：模型生成的图片保存在 `~/.codex/generated_images/<会话ID>/`，不要依赖模型自己复制文件。
