#!/usr/bin/env bash
# 原曲（不剪不改）+ 合成音效 → out/mix.wav：音效大的地方轻压一点音乐（≤3 dB），两遍 loudnorm 到 -14 LUFS / -1 dBTP。
set -euo pipefail
cd "$(dirname "$0")/.."
BGM=../android-shinjuku/app/src/main/res/raw/bgm.mp3
ffmpeg -v error -y -i "$BGM" -ar 48000 -ac 2 -c:a pcm_f32le out/bgm48.wav
GRAPH="[1:a]asplit=2[sc][s];[0:a][sc]sidechaincompress=threshold=0.25:ratio=2.5:attack=5:release=250:makeup=1[m];[m][s]amix=inputs=2:duration=first:normalize=0,volume=0.82"
ffmpeg -v error -y -i out/bgm48.wav -i out/sfx.wav -filter_complex "$GRAPH" -c:a pcm_f32le out/premix.wav
J=$(ffmpeg -hide_banner -i out/premix.wav -af loudnorm=I=-14:TP=-1.0:LRA=11:print_format=json -f null - 2>&1 | sed -n '/^{/,/^}/p')
get() { echo "$J" | python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"; }
ffmpeg -v error -y -i out/premix.wav -af "loudnorm=I=-14:TP=-1.0:LRA=11:measured_I=$(get input_i):measured_TP=$(get input_tp):measured_LRA=$(get input_lra):measured_thresh=$(get input_thresh):offset=$(get target_offset):linear=true,alimiter=limit=0.89:attack=3:release=60:level=false" -ar 48000 -c:a pcm_s24le out/mix.wav
echo "premix: I=$(get input_i) TP=$(get input_tp)"
ffmpeg -hide_banner -i out/mix.wav -af ebur128=peak=true -f null - 2>&1 | grep -E "^\s+(I:|Peak:)" | tail -2
