#!/usr/bin/env bash
# 先做动画、最后配音乐：把 public/audio/music.mp3（用户给的《青衣》DJ版，原文件是 mp4，按要求直接改了后缀）
# 接到 out/silent.mp4 上；音乐比动画长的部分直接剪掉，只在末尾留 0.6 秒淡出，免得断口爆音。
# 原曲压得很满（-8 LUFS，真峰值已过 0 dB），重编码成 AAC 会过冲：先 16 kHz 低通、降 3 dB、限幅到 -3 dB。
set -euo pipefail
cd "$(dirname "$0")/.."
DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 out/silent.mp4)
FADE=$(python3 -c "print(round($DUR - 0.6, 3))")
ffmpeg -loglevel error -y -i out/silent.mp4 -i public/audio/music.mp3 -map 0:v -map 1:a \
  -c:v libx264 -crf 22 -preset medium -pix_fmt yuv420p \
  -af "atrim=0:$DUR,lowpass=f=16000,volume=-3dB,afade=t=out:st=$FADE:d=0.6,alimiter=limit=0.7:level=false:attack=1" \
  -c:a aac -b:a 192k -t "$DUR" -movflags +faststart out/dream-history.mp4
ffprobe -v error -show_entries format=duration,size -of default=nw=1 out/dream-history.mp4
