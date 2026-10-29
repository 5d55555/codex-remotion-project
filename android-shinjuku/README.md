# 新宿决战（Android）

第一版只有主页：标题“新宿决战”，从上到下三个选项——开始游戏、空置、空置。

- 空置：点击弹出 Toast“未开放”（连点时先撤掉上一条再弹）。
- 开始游戏：游戏本体还没做，暂时点了没有反应。
- 背景是代码绘制的新宿夜景（`ShinjukuView`）：星空、一蓝一红两道光在标题后方交锋、两层楼群和灯窗、都厅双塔与航空灯、地面红雾和往上飘的火星。不用任何图片素材。
- 进场动画：标题先浮现，三个选项依次升上来。
- 纯 Java，`android.app.Activity`，不依赖 AndroidX；minSdk 26（Android 8.0），targetSdk 36，竖屏。

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
./gradlew assembleDebug        # 输出 app/build/outputs/apk/debug/app-debug.apk
```

## 截图检查

容器里没有 KVM，跑不了模拟器，所以用 Paparazzi（layoutlib，和 Android Studio 预览同一套渲染）在 JVM 上把主页渲染成图片：

```bash
./gradlew recordPaparazziDebug   # 输出 app/src/test/snapshots/images/
```

Kotlin 和 Paparazzi 只用于这个截图测试，不进 APK（`kotlin.stdlib.default.dependency=false`；`android.useAndroidX=true` 只是因为 Paparazzi 带了 androidx.annotation）。
