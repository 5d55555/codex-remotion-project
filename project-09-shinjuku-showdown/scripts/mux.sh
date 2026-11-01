#!/usr/bin/env bash
# 无声成片 + 混好的音轨 → out/新宿決戦.mp4（视频流直接拷贝，不再二次压缩）。
set -euo pipefail
cd "$(dirname "$0")/.."
ffmpeg -v error -y -i out/silent.mp4 -i out/mix.wav -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 320k -ar 48000 -ac 2 \
  -movflags +faststart -metadata title="新宿決戦" -metadata artist="五条悟 vs 两面宿傩 · 同人短片" "out/新宿決戦.mp4"
ffprobe -v error -show_entries format=duration,size,bit_rate:stream=codec_name,profile,width,height,r_frame_rate,pix_fmt,color_space -of default=nw=1 "out/新宿決戦.mp4"
