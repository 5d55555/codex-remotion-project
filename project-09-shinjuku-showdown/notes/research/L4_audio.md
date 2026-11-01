下面给一套能直接起步的方案。合成参数是**制作起点**，不是唯一标准：先按参数生成素材，再根据画面动作把起音、长度和音量对齐。混音时要注意，**-10 dBFS RMS 不等于 -10 LUFS**；它说明音乐平均电平很高，但不能单独决定最终响度或峰值。

## 1. 用 Python + NumPy 合成战斗音效

### 通用合成框架

建议用 **48 kHz、float32** 生成，最后写成 24-bit PCM WAV，避免多次处理时的量化损失。每个音效都可以看成若干层相加：

```python
import numpy as np
from scipy.signal import butter, sosfilt

SR = 48000

def env_exp(n, attack=0.005, decay=0.15, sustain=0.0, release=0.05):
    """简易 A-D-S-R 包络；时间单位为秒。"""
    N = max(1, int(n))
    a = min(N, int(attack * SR))
    d = min(max(0, N - a), int(decay * SR))
    r = min(max(0, N - a - d), int(release * SR))
    s = max(0, N - a - d - r)
    e = np.concatenate([
        np.linspace(0, 1, a, endpoint=False),
        np.linspace(1, sustain, d, endpoint=False),
        np.full(s, sustain),
        np.linspace(sustain, 0, r, endpoint=True),
    ])
    return e[:N]

def noise(n, rng):
    return rng.normal(0, 1, n).astype(np.float32)

def lp(x, cutoff, sr=SR, order=4):
    return sosfilt(butter(order, cutoff, fs=sr, output="sos"), x)

def hp(x, cutoff, sr=SR, order=4):
    return sosfilt(butter(order, cutoff, fs=sr, btype="highpass", output="sos"), x)

def bp(x, low, high, sr=SR, order=3):
    return sosfilt(butter(order, [low, high], fs=sr, btype="bandpass", output="sos"), x)

def sat(x, drive=2.0):
    return np.tanh(drive * x) / np.tanh(drive)

def pan_stereo(x, pan=0.0):
    # pan: -1 左，0 中，+1 右
    theta = (pan + 1) * np.pi / 4
    return np.column_stack([x * np.cos(theta), x * np.sin(theta)]).astype(np.float32)
```

滤波器用 Butterworth 起步足够；要更尖锐的音色，可提高阶数或把噪声层的带通范围收窄。失真可先用 `tanh`，再叠一点高频噪声；不要把每层都削成方波，否则混音容易刺耳、拥挤。

**卷积混响**可以用短脉冲响应实现：从带衰减包络的噪声生成一条 IR，归一化后卷积。对单个素材很方便，但对大量素材批量生成，最好使用 FFT 卷积或把混响留到 FFmpeg/DAW 处理。

```python
def synthetic_ir(seconds=0.8, decay=4.0, rng=None):
    rng = rng or np.random.default_rng(1)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = rng.normal(0, 1, n) * np.exp(-decay * t)
    ir[0] += 1.0
    return (ir / np.max(np.abs(ir))).astype(np.float32)

def reverb(x, ir, wet=0.18):
    wet_sig = np.convolve(x, ir, mode="full")[:len(x)]
    return (1 - wet) * x + wet * wet_sig
```

### 音效配方

以下频段指主要能量范围，不是硬性截止频率。每个音效里的“层”可以单独输出 WAV，也可以在生成时直接相加；建议先单独导出，方便混音和按画面移动声像。

| 音效 | 时长与频段 | 包络、层次和处理起点 |
|---|---|---|
| **重拳命中** | 120–260 ms；低频 45–120 Hz，中频 150–900 Hz，高频 1.5–6 kHz | ① 50–90 Hz 正弦下滑 1–2 个八度，80–160 ms；② 60–180 ms 带通噪声，150–900 Hz；③ 2–5 kHz 短噪声点，10–35 ms。总包络 2–8 ms 起音、80–170 ms 衰减。低层轻微软削波，混响 wet 5–12%。 |
| **斩击「解」：细而尖的咻声** | 90–240 ms；约 1.5–10 kHz | 白噪声快速带通扫频，例如中心频率从 2.2 kHz 上扫至 7.5 kHz，带宽 1.5–3 kHz；用 3–8 ms 起音、70–200 ms 衰减。叠极轻的 5–8 kHz 窄带正弦或噪声哨音。高通 1.2 kHz，混响 3–8%，声像跟刀的方向走。 |
| **能量蓄力：苍** | 0.8–2.0 s；主体 70–900 Hz，泛音 1–4 kHz | 70–150 Hz 正弦加轻微失谐（±2–5 Hz），缓慢上升音高；叠低通噪声，截止频率从 200 Hz 扫到 1.2 kHz。包络 200–600 ms 渐强，末端突然切断或留 100–250 ms 尾音。轻微饱和，混响 15–25%。 |
| **能量蓄力：赫** | 0.5–1.5 s；约 100 Hz–8 kHz | 低中频锯齿波/脉冲波 120–500 Hz，叠 1–4 kHz 带通噪声和 4–8 kHz 稀疏电火花。用 50–200 ms 逐步增强，末端 20–80 ms 急促抬升；驱动量 1.5–3 的 `tanh` 软削波，混响 10–20%。 |
| **能量蓄力：茈** | 0.8–2.5 s；约 25 Hz–10 kHz | ① 30–90 Hz 的低频正弦/三角波；② 300–1,200 Hz 的缓慢调幅嗡鸣；③ 2–6 kHz 高通噪声；④ 极轻 6–10 kHz 颗粒噪声。让低频缓慢起涨，高频层在最后 200–500 ms 加入；结尾加 50–150 ms 的抽吸或反向噪声。多层轻微失谐，混响 20–35%。 |
| **大爆炸：茈爆发** | 爆点 200–600 ms，尾响 1.5–4 s；爆点 30–12,000 Hz | ① 35–90 Hz 正弦下坠 1–2 八度，150–350 ms；② 50–400 Hz 低通噪声，250–500 ms；③ 600 Hz–5 kHz 带通噪声爆裂，80–250 ms；④ 5–12 kHz 短促碎屑；⑤ 反向吸气/上升噪声 150–400 ms，放在爆点前。爆点可软削波，尾巴做 0.8–2.5 s 卷积混响，wet 15–30%；让低频尾响比高频更长。 |
| **冲击波** | 300 ms–1.2 s；50 Hz–8 kHz | 低频正弦快速下滑 50–120 Hz，叠 300 Hz–2 kHz 带通噪声脉冲，再加一层 2–8 kHz 的短促空气撕裂声。主峰 20–60 ms 到达，后续用 150–800 ms 指数衰减。可在立体声上用 10–40 ms 延迟制造扩张感；混响 10–25%。 |
| **玻璃/结界碎裂** | 250 ms–1.5 s；1–14 kHz，少量 200–800 Hz 低层 | 先加 5–20 ms 的尖锐裂纹噪声，再叠 6–20 个短正弦/非谐和部分音，频率可在 700 Hz–8 kHz 随机取值，衰减 150–900 ms；最后用 2–10 kHz 噪声碎片扫落。碎片层轻微随机声像和延迟；混响 15–35%。 |
| **领域展开低频轰鸣** | 2–8 s；主体 25–250 Hz，谐波 250 Hz–1.5 kHz | 30–90 Hz 的正弦/三角低音 + 40–180 Hz 低通噪声，慢速振幅调制 0.2–1 Hz；用 0.5–1.5 s 淡入、0.5–2 s 淡出。适度饱和；低频尽量保持单声道，混响主体控制在 10–20%，高频谐波可加更宽的 20–35% 混响。 |
| **超低频砰：sub drop** | 180–650 ms；25–80 Hz | 正弦从 65–90 Hz 下滑到 25–35 Hz，可叠少量 35–100 Hz 低通噪声。10–30 ms 起音，120–500 ms 指数衰减，适度饱和。低于 25–30 Hz 的能量通常可用高通清掉，避免浪费余量，也避免在手机上只剩下“没听到但占空间”的低频。 |
| **风声/呼啸** | 0.3–3 s；200 Hz–12 kHz | 白噪声经带通滤波，中心频率从 400 Hz 扫至 6–10 kHz 或反向扫；带宽 1–4 kHz。包络 30–150 ms 渐入、200 ms–2 s 渐出。用缓慢低频调制 2–8 Hz；立体声可用缓慢自动声像，但低频部分保持居中。 |
| **电流噼啪：黑闪** | 30–300 ms；1–12 kHz，偶尔叠 60–250 Hz | 生成 1–8 个随机脉冲，间隔 8–50 ms；每个脉冲可用 2–8 kHz 带通噪声或短暂频率跳变正弦，脉冲长度 1–12 ms。叠 80–200 Hz 的 30–80 ms 低频冲击。总长度 80–250 ms；失真较强但单层音量低，避免持续刺耳。 |
| **心跳** | 单次 250–550 ms；约 35–180 Hz | 两个衰减正弦/三角波：主击 50–90 Hz，次击 35–70 Hz，间隔 70–140 ms；每击 8–20 ms 起音，100–250 ms 衰减。叠一点 100–300 Hz 低通噪声，混响很少（0–8%），可随镜头逐渐加快至每分钟 90–140 拍。 |
| **咒词钟声** | 1.5–5 s；基频约 100–600 Hz，泛音至 8 kHz | 用 3–8 个互不整齐的正弦部分音，例如基频 220 Hz，其他部分音分别为基频的 2.01、2.74、3.91、5.13 倍；振幅按 1、0.55、0.3、0.18、0.1 递减。起音 2–10 ms，衰减 1–4 s；对部分音做轻微频率调制 ±1–4 Hz，混响 wet 20–40%。 |

实用调音顺序：先把每种音效单独听到“像什么”，再与音乐一起听；先调**起音和时长**，再调音量，最后才加失真和混响。冲击类音效不需要每层都很响：通常让低频层撑重量，中高频层负责听清“打中”或“裂开”。

## 2. 音效与音乐混音

### 响度和动态

- **成片可以把 -14 LUFS integrated、-1 dBTP 当作网络平台的起始目标**，但它是常用目标，不是所有平台的强制规格。YouTube 的上传建议列出了 H.264、AAC-LC、48 kHz 和 60 fps 等格式参数；官方推荐表对 1080p60 的 SDR 上传参考码率是 12 Mbps。[YouTube 上传设置](https://support.google.com/youtube/answer/1722171?hl=en-GB)
- 你的音乐约 **-10 dBFS RMS**，建议先把它作为音乐母带的起点，听音效叠加后成片的 integrated LUFS 和 true peak，再决定是否整体衰减。不要仅凭“RMS 数字”就把音乐一刀切到 -14；RMS 和 LUFS 不是同一种测量。
- 配乐在有对白、关键动作或强音效的地方，可以从 **-3 到 -6 dB** 的 ducking 开始；爆点或台词密集段可试 **-6 到 -9 dB**。用 **5–15 ms attack、120–300 ms release** 起步，比较自然；若只要瞬态让位，缩短 release 到 80–150 ms。
- 若音乐节奏本身很满，避免对整条音乐做过度压缩。先给音效留频段和时间空隙：比如斩击的 3–8 kHz 高频，避免音乐中的高频合成器同时最响；sub drop 与音乐的低音鼓/贝斯则要错开或自动化。

### FFmpeg：侧链压低音乐

假设 `music.wav` 是音乐、`sfx.wav` 是**已经按时间排好的完整音效轨**，以下图把音效作为 sidechain 检测信号，压低音乐约 5 dB，再与音效相加。`sidechaincompress` 的第一个输入是被压缩信号，第二个输入是 sidechain 信号；滤镜细节见 [FFmpeg Filters 文档](https://ffmpeg.org/ffmpeg-filters.html)。

```bash
ffmpeg -i music.wav -i sfx.wav \
-filter_complex \
"[1:a]asplit=2[sfx_sc][sfx_mix]; \
 [0:a][sfx_sc]sidechaincompress=threshold=0.03:ratio=3:attack=8:release=180:makeup=1[ducked]; \
 [ducked][sfx_mix]amix=inputs=2:duration=longest:normalize=0[mix]" \
-map "[mix]" -ar 48000 -c:a pcm_s24le mix.wav
```

这里阈值 `0.03` 是线性振幅（约 -30.5 dBFS）；音效素材电平不同，需调整阈值。若压低太多，可把 `ratio=3` 降到 `2`，或者把阈值调高；若压得不够，降低阈值或提高 ratio。先用耳朵把压低量调到合适，再看增益衰减表。

若要对最终混音做两遍响度归一化：

```bash
# 第 1 遍：读取 loudnorm 输出的 measured_I、measured_LRA、measured_TP、measured_thresh、offset
ffmpeg -i mix.wav \
-af "loudnorm=I=-14:LRA=8:TP=-1:print_format=json" \
-f null -

# 第 2 遍：将上一遍测得的数值填入 measured_* 字段
ffmpeg -i mix.wav \
-af "loudnorm=I=-14:LRA=8:TP=-1:measured_I=填入:measured_LRA=填入:measured_TP=填入:measured_thresh=填入:offset=填入:linear=true:print_format=summary" \
-ar 48000 -c:a pcm_s24le mix_master.wav
```

`loudnorm` 支持 EBU R128 归一化、linear/dynamic 模式和 true-peak 目标；两遍时必须把第一遍输出的测量值填回去。[FFmpeg `loudnorm` 说明](https://ffmpeg.org/ffmpeg-filters.html#loudnorm)

在最终 AAC 编码前加限制器作为安全网：

```bash
-af "alimiter=limit=0.891:attack=5:release=80"
```

`0.891` 约等于 -1 dBFS 的**采样峰值**限制，不是严格的 true-peak 测量；所以先用 `loudnorm` 设 -1 dBTP，再用 limiter 防止滤波/编码链的意外峰值。`alimiter` 使用 lookahead，attack 参数也会引入相应延迟。[FFmpeg `alimiter` 说明](https://ffmpeg.org/ffmpeg-filters.html#alimiter)

### 低频和声像

- **30–35 Hz 以下**可从大多数音效层适度高通；sub drop 可保留到 25–30 Hz，但要听手机和耳机两种系统。
- 低频（约 120 Hz 以下）尽量居中、保持单声道相容。斩击、碎片、风声可以在中高频上左右移动；不要把所有层都大幅度扩宽。
- 声像跟随画面运动，但避免连续硬切到另一侧；短音效可用 5–30 ms 的自动化过渡，长尾混响则保持较宽、较平稳。

## 3. Remotion 4：1080p60 导出设置

### 编码与渲染参数

常规 MP4 输出可从下面这组参数开始：

```bash
npx remotion render src/index.ts Fight out/fight.mp4 \
  --codec=h264 \
  --crf=17 \
  --x264-preset=slow \
  --pixel-format=yuv420p \
  --image-format=png \
  --concurrency=2
```

把 composition 设为 **1920×1080、60 fps**，时长约 3 分钟就是 **10,800 帧**。CRF 越低画质越高、文件通常越大；Remotion 的 H.264 默认 CRF 是 18。`veryslow` 会增加编码时间，但不保证主观画质明显胜过 `slow`；先用 `slow`，需要更小文件时再比较 `veryslow`。Remotion CLI 支持 `--codec`、`--crf`、`--x264-preset`、`--pixel-format` 和 `--concurrency` 等选项。[Remotion 编码指南](https://convert.remotion.dev/docs/encoding) [Remotion render CLI](https://convert.remotion.dev/docs/cli/render)

4 核、15 GB 内存的机器建议从 **`--concurrency=2`** 开始；若镜头轻、内存占用低，可以试 3。并发越高不必然越快：每个 Chromium 页面和图像资产都占资源。Remotion 的并发参数代表使用的 CPU 线程数，官方也说明多标签渲染要求每一帧都能由帧号确定，不能依赖随机状态或渲染顺序。[Remotion CLI 并发说明](https://convert.remotion.dev/docs/cli/render) [避免渲染闪烁](https://www.remotion.dev/docs/flickering)

### PNG、JPEG 与视频帧

`--image-format` 控制 Remotion 截屏帧的格式，不是输出视频的像素格式。像素画边缘敏感，**先用 PNG**，避免中间 JPEG 压缩把边缘变糊；如渲染速度或临时帧体积成为瓶颈，再试高质量 JPEG（质量 95 左右）逐帧检查。若渲染管线走共享内存捕获，截图编码路径可能不同；具体行为取决于 Remotion 版本和渲染方式。[Remotion 图像格式选项](https://www.remotion.dev/docs/options/video-image-format) [共享内存捕获](https://www.remotion.dev/docs/shared-memory-capture)

几千张 PNG 做素材序列时：

- 不要在 React 里一次性把所有帧 `<Img>` 都挂载出来；只让当前需要的帧进入时间线。
- 让路径和帧号都由 `useCurrentFrame()` 确定，并预先生成连续编号或清单。
- `<Img>` 适合普通静态图像；视频素材优先用 Remotion 的 `<OffthreadVideo>` 或新版 `@remotion/media` 的 `<Video>`。`<OffthreadVideo>` 渲染时由 FFmpeg 提取精确帧，再交给 `<Img>` 显示，官方建议仅在确实需要透明视频时启用 `transparent`，因为这会变慢。[Remotion `<OffthreadVideo>` 文档](https://www.remotion.dev/docs/offthreadvideo)

如果 PNG 序列实际上是动画帧，通常建议先合并为中间视频或精灵图/图集，减少逐帧文件读取、路径处理和大量 DOM 节点开销；最后抽查动画边界和透明通道是否正确。

### 色彩和像素画缩放

- 一般手机兼容目标用 **`yuv420p`**，避免输出 `yuvj420p`。`yuv420p` 是常见 8-bit 4:2:0 格式；`yuvj420p` 常与 full-range 表示关联，不应为了“颜色更好”随意强制它。BT.709 是 SDR 视频常用的色彩标记；如果源内容本身是 SDR，可在导出时标注 `bt709` 和 limited range（电视范围）。YouTube 的非 HDR 视频说明也建议使用 BT.709。[YouTube 色彩说明](https://support.google.com/youtube/answer/58134)
- 像素画放大优先用整数倍缩放：例如 320×180 画布放大到 1920×1080 是 6 倍；如果源尺寸不能整除输出尺寸，可能出现像素宽度不均。CSS 可用 `image-rendering: pixelated`，但它只影响浏览器栅格图像缩放；如果 SVG、canvas、CSS transform 或后续编码又做了一次非整数缩放，仍可能变糊或出现不均匀像素。MDN 的像素画指南也强调绘制尺寸与整数倍缩放的关系。[MDN `image-rendering`](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/image-rendering) [MDN 像素画缩放指南](https://developer.mozilla.org/en-US/docs/Games/Techniques/Crisp_pixel_art_look)

## 4. 目标文件大小和手机兼容

文件大小主要由平均码率和时长决定。**2 分 30 秒**时，150–300 MB 大约对应总码率 **8–16 Mbps**；**3 分钟**时约为 **6.7–13.3 Mbps**。这是把文件容量按十进制 MB 估算出来的区间，实际文件还会受音频和容器开销影响。

像素画静帧较多、细节边缘清晰时，可先试 **H.264 CRF 16–18、preset slow**。想优先控大小，可试 CRF 19–21；若目标严格限制在 150–300 MB，按目标平均码率控制通常更可预测，建议视频码率约 **6–12 Mbps**，AAC 音频 **192–256 kbps**。这和 YouTube 对 1080p60 SDR 的 12 Mbps 视频参考值相近，但平台会自行转码；如果本地交付看重画质，可以保留更高码率。[YouTube 推荐上传编码设置](https://support.google.com/youtube/answer/1722171?hl=en-GB)

例如最终由 FFmpeg 把已渲染的视频和混好的 WAV 合成：

```bash
ffmpeg -i remotion-video.mp4 -i mix_master.wav \
  -map 0:v:0 -map 1:a:0 \
  -c:v libx264 -preset slow -crf 18 \
  -profile:v high -level:v 4.2 \
  -pix_fmt yuv420p \
  -color_primaries bt709 -color_trc bt709 -colorspace bt709 -color_range tv \
  -c:a aac -b:a 256k -ar 48000 -ac 2 \
  -movflags +faststart \
  fight-final.mp4
```

H.264 **High Profile、Level 4.2、yuv420p、AAC-LC、48 kHz、faststart** 是一个实用的手机兼容组合；60 fps 需确保渲染输出是 60 fps，最终转码不要降帧。YouTube 建议上传时保持原帧率，并推荐 H.264 High Profile、AAC-LC、48 kHz 和 MP4 fast-start。[YouTube 上传编码设置](https://support.google.com/youtube/answer/1722171?hl=en-GB)

如果 Remotion 已经输出目标编码，尽量别再无必要地二次压视频。可在 Remotion 阶段直接指定 `--codec=h264`、`--crf`、preset 和像素格式；需要单独混音时，可将最终音轨与视频合并，并在确认没有滤镜需求后考虑视频流复制。

## 来源链接

- [FFmpeg Filters：`sidechaincompress`、`loudnorm`、`alimiter`](https://ffmpeg.org/ffmpeg-filters.html)
- [Remotion Encoding Guide](https://convert.remotion.dev/docs/encoding)
- [Remotion `render` CLI](https://convert.remotion.dev/docs/cli/render)
- [Remotion 视频图像格式选项](https://www.remotion.dev/docs/options/video-image-format)
- [Remotion `<OffthreadVideo>`](https://www.remotion.dev/docs/offthreadvideo)
- [Remotion 并行编码说明](https://convert.remotion.dev/docs/parallel-encoding)
- [YouTube 推荐上传编码设置](https://support.google.com/youtube/answer/1722171?hl=en-GB)
- [YouTube 上传色彩问题说明](https://support.google.com/youtube/answer/58134)
- [MDN `image-rendering`](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/image-rendering)
- [MDN 像素画缩放指南](https://developer.mozilla.org/en-US/docs/Games/Techniques/Crisp_pixel_art_look)