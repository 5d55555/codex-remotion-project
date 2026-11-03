---
name: pocket-media
description: 在手机上用命令行剪视频、处理音频图片、做程序化动画：ffmpeg 剪切拼接转码加字幕、ImageMagick/GraphicsMagick、sox、gifski，用 Python Pillow/NumPy 或 SVG 逐帧生成动画再用 ffmpeg 合成。用户要剪辑、转格式、做 GIF、加字幕或生成动画视频时使用。
metadata:
  short-description: 命令行剪辑与程序化动画
---

# 剪辑与程序化动画

需要“剪辑与程序化动画”工具集（ffmpeg、imagemagick、graphicsmagick、sox、mediainfo、gifski、librsvg、python-pillow、python-numpy、字体）；中文字幕还要“中文字体”工具集（Noto Sans CJK）。

## 先看素材

```bash
ffprobe -v error -show_entries format=duration:stream=codec_type,codec_name,width,height,r_frame_rate -of compact in.mp4
mediainfo in.mp4 | head -40
```

## 常用剪辑

```bash
ffmpeg -ss 00:00:05 -to 00:00:12 -i in.mp4 -c copy cut.mp4                      # 快速剪（关键帧对齐）
ffmpeg -ss 5 -to 12 -i in.mp4 -c:v libx264 -crf 20 -preset veryfast -c:a aac cut.mp4   # 精确剪（重新编码）
printf "file 'a.mp4'\nfile 'b.mp4'\n" > list.txt && ffmpeg -f concat -safe 0 -i list.txt -c copy joined.mp4
ffmpeg -i in.mp4 -vf "scale=-2:1080,fps=30" -c:v libx264 -crf 22 -c:a aac out.mp4
ffmpeg -i in.mp4 -filter:v "setpts=0.5*PTS" -filter:a "atempo=2.0" fast.mp4
ffmpeg -i a.mp4 -i b.mp4 -filter_complex "xfade=transition=fade:duration=1:offset=4" -c:v libx264 xfade.mp4
ffmpeg -i video.mp4 -i music.m4a -map 0:v -map 1:a -shortest -c:v copy -c:a aac with_music.mp4
```

字幕（中文需要字体）：`fc-list :lang=zh` 找字体名，然后
`ffmpeg -i in.mp4 -vf "subtitles=sub.srt:force_style='FontName=Noto Sans CJK SC,FontSize=22'" -c:a copy subbed.mp4`。
GIF：`ffmpeg -i in.mp4 -vf "fps=15,scale=480:-1" frames/%04d.png && gifski -o out.gif --fps 15 frames/*.png`。

## 程序化动画

手机上没有 Chrome，**Remotion 这类依赖浏览器渲染的方案跑不了**；用“逐帧画图 → ffmpeg 合成”：

```python
# anim.py：python anim.py && ffmpeg -framerate 30 -i frames/%04d.png -c:v libx264 -pix_fmt yuv420p anim.mp4
from PIL import Image, ImageDraw, ImageFont
import math, os
os.makedirs("frames", exist_ok=True)
W, H, N = 1080, 1080, 90
for i in range(N):
    t = i / N
    im = Image.new("RGB", (W, H), (245, 245, 247)); d = ImageDraw.Draw(im)
    r = 200 + 80 * math.sin(2 * math.pi * t)
    d.ellipse((W/2-r, H/2-r, W/2+r, H/2+r), fill=(47, 107, 242))
    im.save(f"frames/{i:04d}.png")
```

也可以写 SVG 模板逐帧替换参数，用 `rsvg-convert -w 1080 frame.svg -o frame.png` 渲染；ImageMagick `magick` 做合成、文字和特效。复杂补间用 NumPy 计算。

更多：`pycairo`（矢量绘图、抗锯齿文字）、`vips`（大图处理，省内存）、`exiftool`（元数据）、`cwebp`/`img2webp`（WebP 动图）、`avifenc`、`heif-convert`、`pngquant`/`optipng`（压图）、`potrace`（位图转矢量）。纯 Python：`pysubs2` / `srt`（字幕时间轴）、`drawsvg` / `svgwrite`（生成 SVG 帧）。

## 验证输出

每次产出后 `ffprobe` 检查时长、分辨率、帧率、是否有音轨；抽几帧 `ffmpeg -ss 1 -i out.mp4 -frames:v 1 check.png` 给用户看。手机性能有限：先用低分辨率预览，确认后再出成片。
