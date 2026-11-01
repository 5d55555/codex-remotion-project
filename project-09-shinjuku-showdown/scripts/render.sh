#!/usr/bin/env bash
# 分幕渲染（无声）到 out/parts/actN.mp4，最后无损拼接成 out/silent.mp4。改了哪一幕只需重渲那一幕：
#   bash scripts/render.sh 3 5     # 只重渲第 3、5 幕再拼接
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out/parts
# 各幕的帧范围（含首不含尾）：B(33)、B(49)、B(65)、B(83)、B(98) 对应的帧
BOUNDS=(0 3427 5064 6700 8541 10075 11830)
ACTS=("$@")
[ ${#ACTS[@]} -eq 0 ] && ACTS=(1 2 3 4 5 6)
node scripts/glyphs.mjs
for a in "${ACTS[@]}"; do
  s=${BOUNDS[$((a-1))]}; e=$((${BOUNDS[$a]} - 1))
  echo "== act $a frames $s-$e $(date +%T)"
  npx remotion render src/index.tsx Shinjuku "out/parts/act$a.mp4" --frames=$s-$e \
    --concurrency=3 --image-format=png --crf=16 --x264-preset=slow --pixel-format=yuv420p --color-space=bt709 --muted \
    --browser-executable=/opt/pw-browsers/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell --log=error
done
: > out/parts/list.txt
for a in 1 2 3 4 5 6; do [ -f "out/parts/act$a.mp4" ] && echo "file 'act$a.mp4'" >> out/parts/list.txt; done
ffmpeg -v error -y -f concat -safe 0 -i out/parts/list.txt -c copy out/silent.mp4
ffprobe -v error -count_frames -select_streams v:0 -show_entries stream=nb_read_frames,duration -of csv=p=0 out/silent.mp4
echo "done $(date +%T)"
