#!/usr/bin/env bash
# 跑一个 Luna 生图任务：独立的 CODEX_HOME（避免并行任务互相拿错图），工作目录里放 prompt.txt 和参考图 ref*.png。
# 用法：CODEX_MODEL=<模型> gen_job.sh <任务目录>    完成后在目录里留下 done（以及模型生成的 png）
set -uo pipefail
MODEL="${CODEX_MODEL:?先设置 CODEX_MODEL（生图用的模型名）}"
JOB=$(cd "$1" && pwd)
SCR=/tmp/claude-0/-home-user-codex-remotion-project/d680fd9a-795b-5f20-aab9-eb13275296d1/scratchpad
HOME_DIR="$SCR/codexhome/$(basename "$JOB")-$$"
rm -rf "$HOME_DIR"; cp -r "$SCR/codexhome/template" "$HOME_DIR"
REFS=()
for f in "$JOB"/ref*.png "$JOB"/ref*.jpg; do [ -f "$f" ] && REFS+=(-i "$f"); done
cd "$JOB"
CODEX_HOME="$HOME_DIR" codex exec -m "$MODEL" -s workspace-write -C "$JOB" --skip-git-repo-check "${REFS[@]}" - < prompt.txt > log 2>&1
# 不管模型有没有自己拷图，都把它这次生成的所有图按顺序拷出来（gen_1.png、gen_2.png…），坏文件删掉
i=0; for f in $(ls -tr "$HOME_DIR"/generated_images/*/*.png 2>/dev/null); do i=$((i+1)); cp "$f" "$JOB/gen_$i.png"; done
for f in "$JOB"/*.png; do [ -s "$f" ] || rm -f "$f"; done
rm -rf "$HOME_DIR"   # 每个任务的 CODEX_HOME 有上百 MB，用完就删
touch done
