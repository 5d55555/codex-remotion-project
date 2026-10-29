# 新宿决战（Android）

第一版只有主页：标题“新宿决战”，从上到下三个选项——开始游戏、空置、空置。

- 空置：点击弹出 Toast“未开放”（2.5 秒内的重复点击忽略）。
- 开始游戏：游戏本体还没做；点击后背景音乐从头播放并循环（反复点每次从头）。
- 背景音乐：`res/raw/bgm.mp3` 是用户给的原曲，原样打包（不转码、不改音量、不压缩存储）。启动后在专用线程上提前准备好；切后台暂停、回前台接着播；来电等临时占用音频时暂停，结束后接着播；别的应用长期占用时停下，等下次点开始游戏。
- 帧率监视：长按右下角版本号打开/关闭。显示窗口帧率（含面板自己的刷新）、最慢一帧耗时、超时帧数（Android 12+ 按系统给的截止时间，旧系统按刷新周期近似）、漏报帧数、屏幕刷新率。
- 背景是代码绘制的新宿夜景（`ShinjukuView`）：星空、一蓝一红两道光在标题后方交锋、两层楼群和灯窗、都厅双塔与航空灯、地面红雾和往上飘的火星。不用任何图片素材。
- 进场动画：标题先浮现，三个选项依次升上来。
- 纯 Java，`android.app.Activity`，不依赖 AndroidX；minSdk 26（Android 8.0），targetSdk 36，竖屏（声明了 `appCategory="game"`，Android 16 大屏上竖屏锁定仍生效）。

## 性能

- 背景：不动的部分（夜空、楼群、灯窗、双塔）只录一次——Android 10+ 录成 RenderNode（显示列表失效会自动重录），Android 8–9 缓存成有像素预算的位图；会动的部分每帧零分配，星星、灭窗、火星各一次 `drawPoints`。
- 自己用 Choreographer 调度，按刷新率整数分频（60/90Hz 每帧画，120/144Hz 隔一帧），刷新率运行中变化会跟着调整；只有前台、可见、系统允许动画时才动，停下时动画时间冻结。
- 背景音乐的所有 MediaPlayer 调用都在专用线程上，不碰主线程。
- 测性能请装 release 包（R8 精简、不带 debuggable）；调试包本身就会慢一些。
- 资料与审计记录：`notes/技术选型.md`、`notes/性能审计一.md`。

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

容器里没有 KVM，跑不了模拟器，所以用 Paparazzi（layoutlib，和 Android Studio 预览同一套渲染）在 JVM 上把主页渲染成图片：

```bash
./gradlew recordPaparazziDebug   # 输出 app/src/test/snapshots/images/
```

Kotlin 和 Paparazzi 只用于这个截图测试，不进 APK（`kotlin.stdlib.default.dependency=false`；`android.useAndroidX=true` 只是因为 Paparazzi 带了 androidx.annotation）。
