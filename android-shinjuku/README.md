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

横屏全屏。左下浮动摇杆（左半屏任意位置按下即出现），右下 体术 / 跳 / 苍 / 赫，顶部左边返回、右边音乐开关（关掉是暂停，再点接着播），中间是难度。长按难度标签打开/关闭帧率面板（帧率、每帧逻辑耗时、绘制耗时）。

五条悟（玩家）：

| 招式 | 机制 |
|---|---|
| 被动·无限 | 攻击特效照常出，但打到身上没有伤害、击退、硬直。只有领域展开（必中）、领域展延、特殊手段在世界状态里真的成立时才能绕过；攻击方光声明一个标志位不算。现在宿傩没有攻击，裁决接口在 `core/Defense.java`，已有单元测试。 |
| 体术 | 50 伤害。15% 黑闪（伪随机：第 n 次命中的概率 min(1, 0.0322·n)，出了就重置；长期正好 15%，最多连续 31 次不出），黑闪 350 伤害、命中停顿、震屏。只在真正打中时判定，挥空不算。拳、踢、上勾三下轮换。 |
| 苍 | 点按：丢出去，飞 0.15 秒到落点，吸 2.5 秒。落点离出手点不超过战场宽度 30%（也不会出战场）。离中心越近伤害越高，核心里正好每秒 100。按住约 0.35 秒（按键外圈有进度环）就是出力最大：范围和吸力翻倍，前摇 0.3 秒，期间只能慢走。摇杆方向决定丢的方向，不推摇杆就自动瞄宿傩。 |
| 赫 | 前方一段距离内斥飞，150 伤害；能把被苍抓住的宿傩打出来（之后 0.2 秒不会被吸回去）。 |

宿傩：暂时没有技能和 AI，是活靶子；血打空后倒地，3 秒后满血站起来。

招式横幅用原作写法（Luna 查证，见 `notes/招式名称考证.md`）：术式顺转「苍」、术式顺转 · 出力最大「苍」（第 71 话原文是“出力最大”）、术式反转「赫」、黑闪。

数值里用户定下的：体术 50、黑闪 15% / 350、苍最远 30% 战场宽度、苍最高每秒 100、出力最大范围和吸力 ×2、赫 150。其余（冷却：苍 6 秒、出力最大 9 秒、赫 4 秒；宿傩 4000 血、五条悟 1000 血；移动、跳跃、出招帧数等）是第一轮试玩的占位，都在 `core/Tuning.java` 和 `core/BattleConfig.java`。

### 结构

- `core/`：纯 Java 战斗逻辑（不依赖 Android），每秒 120 tick 固定步长，完全确定（同样的输入同样的结果），热路径零分配。`./gradlew :core:test` 跑机制测试（黑闪频率和最长不出、苍核心每秒正好 100、投掷不超过 30%、出力最大翻倍、赫 150 和解除捕获、无限挡伤害和三种绕过、乱按两万 tick 无 NaN 不出界且结果一致等）。
- `app/`：`BattleView`（SurfaceView）+ `BattleLoop`（独立线程，自带 Choreographer，按真实时间推进 tick，渲染插值，`lockHardwareCanvas` GPU 绘制）+ `BattleRenderer`（全部画面）+ `Controls`（触摸，主线程写、战斗线程读，计数器传按键，不丢键）。
- 角色：代码绘制的 Q 版（`Chibi`），战斗里按屏幕实际大小把六个部件烘焙成小位图（`ChibiSprite`），每帧按骨骼贴图。
- 设计文档：`notes/战斗架构.md`（Astra 的方案和实现时的偏差）。

## 性能

- 纯 Java，`android.app.Activity`，不依赖 AndroidX；minSdk 26（Android 8.0），targetSdk 36。主页竖屏，选难度和战斗横屏（声明了 `appCategory="game"`，Android 16 大屏上方向锁定仍生效）。
- 战斗：逻辑和绘制都在一个独立线程上，不经过界面绘制流程；每帧零分配；背景用硬件位图（放在显存里）；角色是预烘焙的部件位图；分辨率特别高的屏幕把缓冲区缩到约 260 万像素以内，由硬件合成器放大；向系统请求屏幕支持的最高刷新率（不超过 120）。卡顿后最多补 8 个 tick，不会“快进”。
- 背景：不动的部分（夜空、楼群、灯窗、双塔）只录一次——Android 10+ 录成 RenderNode（显示列表失效会自动重录），Android 8–9 缓存成有像素预算的位图；会动的部分每帧零分配，星星、灭窗、火星各一次 `drawPoints`。
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
