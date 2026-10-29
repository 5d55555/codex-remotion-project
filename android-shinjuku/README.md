# 新宿决战（Android）

咒术回战·新宿决战的 2D 横版对战。现在能玩：主页 → 选难度 → 战斗（五条悟 vs 活靶子宿傩）。

## 主页

标题“新宿决战”，从上到下三个选项——开始游戏、空置、空置。

- 开始游戏：背景音乐从头播放并循环（反复点每次从头），进入难度选择。
- 空置：点击弹出 Toast“未开放”（2.5 秒内的重复点击忽略）。
- 背景音乐：`res/raw/bgm.mp3` 是用户给的原曲，原样打包（不转码、不改音量、不压缩存储）。启动后在专用线程上提前准备好；在主页、选难度、战斗之间跳转不断；整个应用切后台暂停、回前台接着播；来电等临时占用音频时暂停，结束后接着播；别的应用长期占用时停下，等下次点开始游戏。
- 帧率监视：长按右下角版本号打开/关闭。
- 背景是代码绘制的新宿夜景（`ShinjukuView`），不用图片素材。

## 选难度

从左到右四张卡：三轮（三轮霞眼中的宿傩，标准难度）、里梅（里梅眼中的宿傩，困难难度）、万（万眼中的宿傩，疯狂难度）、史上最强（史上最强！噩梦难度）。
每个难度在 `Difficulty.java` 里有自己的 `configure(BattleConfig)`，进战斗前调用；现在宿傩是活靶子，里面还是空的，以后宿傩的血量、技能、AI 强度在这里设置。
背景图、音乐图标、四枚难度徽章由 Luna 生成（原图在 `art/`，`python3 scripts/process_art.py` 转成 WebP 资源）。

## 战斗

横屏全屏，像素风（见下）。左下浮动摇杆（左半屏任意位置按下即出现），右下 体术 / 跳 / 苍 / 茈 / 赫，顶部左边返回、右边音乐开关（关掉是暂停，再点接着播），中间是难度。长按难度标签打开/关闭帧率面板（帧率、每帧逻辑耗时 ×100、绘制耗时 ×100，单位毫秒）。

场景是新宿决战原作里的设定：12 月 24 日白天的新宿，楼群被战斗破坏、街道清空（Luna 查证，见 `notes/能力与配色考证.md`）。

五条悟（玩家）：

| 招式 | 机制 |
|---|---|
| 被动·无限 | 攻击特效照常出，但打到身上没有伤害、击退、硬直。只有领域展开（必中）、领域展延、特殊手段在世界状态里真的成立时才能绕过；攻击方光声明一个标志位不算。唯一的例外是自己的无指向茈（原作第 235 话五条被卷进自己的茈里）。裁决在 `core/Defense.java`。 |
| 体术 | 50 伤害。15% 黑闪（伪随机：第 n 次命中的概率 min(1, 0.0322·n)，出了就重置；长期正好 15%，最多连续 31 次不出），黑闪 350 伤害、命中停顿、震屏。只在真正打中时判定，挥空不算。拳、踢、上勾三下轮换。 |
| 苍 | 蓝白色。点按：丢出去，飞 0.15 秒到落点，吸 2.5 秒。落点离出手点不超过战场宽度 30%（也不会出战场）。离中心越近伤害越高，核心里正好每秒 100。按住约 0.35 秒（按键外圈有进度环）就是出力最大：范围和吸力翻倍，前摇 0.3 秒，期间只能慢走。摇杆方向决定丢的方向，不推摇杆就自动瞄宿傩。 |
| 赫 | 红色（亮部偏橙）。从手里飞出一颗红色球，约 0.08 秒飞完射程（战场宽度 16%），碰到人就爆：150 伤害并击飞。 |
| 无指向茈 | 赫飞行途中和苍重叠、或互相在对方的作用范围内（距离不超过两者作用范围中较小的那个；赫的作用范围是战场宽度 8%），两者直接变成无指向茈：在中间向四周扩张，范围内的宿傩 850，范围内的五条悟也挨一半（425），每人一次。范围和苍一样大：苍放得近就会把自己卷进去，苍放远、让赫飞到射程末端再相遇就能躲开。 |
| 茈 | 向当前朝向发射一颗紫色大球，一直飞出战场，打中 850。不推摇杆时五条悟会自动面向宿傩。 |

宿傩：暂时没有技能和 AI，是活靶子；血打空后倒地，3 秒后满血站起来。五条悟被自己的无指向茈打空血也一样（训练模式）。

招式横幅用原作写法（Luna 查证，见 `notes/招式名称考证.md`、`notes/能力与配色考证.md`）：术式顺转「苍」、术式顺转 · 出力最大「苍」（第 71 话原文是“出力最大”）、术式反转「赫」、虚式「茈」、黑闪。“无指向茈”不是原作术语，横幅写作“虚式 · 无指向「茈」”。

数值里用户定下的：体术 50、黑闪 15% / 350、苍最远 30% 战场宽度、苍最高每秒 100、出力最大范围和吸力 ×2、赫 150、茈和无指向茈 850、无指向茈打到自己一半。其余（冷却：苍 6 秒、出力最大 9 秒、赫 4 秒、茈 15 秒；茈的前摇 0.33 秒；无指向茈的范围；宿傩 4000 血、五条悟 1000 血；移动、跳跃、出招帧数等）是试玩用的占位，都在 `core/Tuning.java` 和 `core/BattleConfig.java`。

### 像素风

- 整个战场（背景、角色、特效、界面的方块部分）画在 512 个虚拟像素宽的像素层上，最近邻放大铺满屏幕，所有东西在同一个像素网格上；中文字（名字、难度、按键、招式小字）在屏幕分辨率层上画，保证清楚。
- 角色是手画的像素画：`art/pixel/characters.txt` 里一个字符一个像素（调色板见文件开头），头、身体、手臂、腿分开画，按骨骼组装；头和身体只做整数像素的平移，手脚绕关节转。改完运行 `python3 scripts/pixel_art.py java` 生成 `PixelArt.java`。
- 背景是 Luna 画的像素风新宿，`scripts/process_art.py` 把它缩到 266 像素高（每块就是一个虚拟像素）、量化到 64 色、不抖动。
- 宿傩现在是伏黑惠的身体（黑色刺猬头、深色高领制服、黑色咒纹）；红眼是推测（官方没有这套造型的彩色资料）。

### 结构

- `core/`：纯 Java 战斗逻辑（不依赖 Android），每秒 120 tick 固定步长，完全确定（同样的输入同样的结果），热路径零分配。
- `app/`：`BattleView`（SurfaceView）+ `BattleLoop`（独立线程，自带 Choreographer，按真实时间推进 tick，渲染插值）+ `PixelScene`（全部画面，只调用 `PixelTarget` 的几个基本操作）+ `Controls`（触摸；按键事件按时间排进预分配的事件环，不合并、不丢键）。
- 画面后端：`GlTarget`（游戏里用：GLES2，低分辨率帧缓冲 + GL_NEAREST 放大，贴图和几何共用一个着色器、攒批提交，静态图一直在显存里）；`CanvasTarget`（截图测试用，GL 起不来时兜底）。
- 设计文档：`notes/战斗架构.md`（Astra 的方案和实现时的偏差）；技术选型和审计：`notes/战斗审计.md`。

### 测试

- `./gradlew :core:test`：机制测试（黑闪频率和最长不出、苍核心每秒正好 100、投掷不超过 30%、出力最大实际吸力正好 ×2、赫 150、赫遇苍变无指向茈且宿傩 850 / 五条悟 425、苍放远时躲得开、离得远时不合成、茈朝朝向飞 850、无限挡伤害和各种绕过、停顿期间不丢键、乱按两万 tick 无 NaN 不出界且结果一致等）。
- `./gradlew :smoke:testDebugUnitTest`：冒烟测试，用 Robolectric（真实的 Android 框架代码）在 Android 9 和 16 上把“主页 → 开始游戏（音乐真的开始播）→ 选难度 → 每个难度进战斗 → 暂停恢复退出”整条路径跑一遍。v0.3 点开始游戏就闪退（`Window.getInsetsController()` 在 DecorView 建好之前调用会空指针），就是这个测试抓到并验证修好的。
- `./gradlew :app:testDebugUnitTest --tests '*GlParityTest*'`：把游戏里真正用的 GL 后端在桌面 Mesa 软件 GLES 上跑一遍，和 Canvas 后端逐像素对照（需要 `libEGL.so.1`、`libGLESv2.so.2`，没有就跳过）。
- `./gradlew :app:recordPaparazziDebug`：截图（主页、选难度、战斗各个招式的画面）。

## 性能

- 纯 Java，`android.app.Activity`，不依赖 AndroidX；minSdk 26（Android 8.0），targetSdk 36。主页竖屏，选难度和战斗横屏（声明了 `appCategory="game"`，Android 16 大屏上方向锁定仍生效）。
- 战斗：逻辑和绘制都在一个独立线程上，不经过界面绘制流程；每帧零分配；GLES2 画进 512 宽的低分辨率帧缓冲再放大，图集和背景一直在显存里，每帧不上传纹理，绘制调用很少；Surface 保持原生尺寸（交给合成器缩放可能变糊）；向系统请求屏幕支持的最高刷新率（不超过 120）。卡顿后最多补 8 个 tick，不会“快进”。
- 主页背景：不动的部分（夜空、楼群、灯窗、双塔）只录一次——Android 10+ 录成 RenderNode（显示列表失效会自动重录），Android 8–9 缓存成有像素预算的位图；会动的部分每帧零分配，星星、灭窗、火星各一次 `drawPoints`。
- 自己用 Choreographer 调度，按刷新率整数分频（60/90Hz 每帧画，120/144Hz 隔一帧），刷新率运行中变化会跟着调整；只有前台、可见、系统允许动画时才动，停下时动画时间冻结。
- 背景音乐的所有 MediaPlayer 调用都在专用线程上，不碰主线程。
- 测性能请装 release 包（R8 精简、不带 debuggable）；调试包本身就会慢一些。
- 资料与审计记录：`notes/技术选型.md`、`notes/性能审计一.md`～`三.md`（主页和背景音乐）、`notes/战斗审计.md`（战斗）。

## 构建

工具链：JDK 21、Gradle 8.14.3（仓库里带 wrapper）、Android Gradle Plugin 8.13.2、Android SDK（platforms;android-36、build-tools;36.0.0）。

```bash
# 没有 Android SDK 的话：
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
unzip commandlinetools-linux-*_latest.zip -d cmdline-tools && mv cmdline-tools/cmdline-tools cmdline-tools/latest
yes | cmdline-tools/latest/bin/sdkmanager --licenses
cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0"

# 构建
cd android-shinjuku
echo "sdk.dir=$HOME/android-sdk" > local.properties
./gradlew assembleRelease      # 输出 app/build/outputs/apk/release/app-release.apk（测性能用这个）
./gradlew assembleDebug        # 输出 app/build/outputs/apk/debug/app-debug.apk

# 签名：两个包都用仓库里的测试签名 signing/test.jks，保证新包能覆盖安装旧包。只用于测试，不用于上架。
```

## 截图检查

容器里没有 KVM，跑不了模拟器，所以用 Paparazzi（layoutlib，和 Android Studio 预览同一套渲染）在 JVM 上把主页、角色和战斗画面渲染成图片（战斗截图用的是真正的 World 和 BattleRenderer）：

```bash
./gradlew recordPaparazziDebug   # 输出 app/src/test/snapshots/images/
```

Kotlin 和 Paparazzi 只用于这个截图测试，不进 APK（`kotlin.stdlib.default.dependency=false`；`android.useAndroidX=true` 只是因为 Paparazzi 带了 androidx.annotation）。
