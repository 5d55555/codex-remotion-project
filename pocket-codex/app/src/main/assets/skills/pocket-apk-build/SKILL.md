---
name: pocket-apk-build
description: 在安卓手机上（口袋工坊）直接构建并签名 APK：小项目用 aapt2 + javac/ecj + d8 + apksigner 手工流程，常规项目用 Gradle 并覆盖成手机版 aapt2。用户要做 APK、打包、签名、改 App 后重新出包时使用。
metadata:
  short-description: 在手机上构建、签名 APK
---

# 在手机上构建 APK

需要“APK 构建与测试”工具集（`openjdk-21 aapt aapt2 d8 apksigner ecj android-tools`）；没有就请用户在 设置 → 开发工具 安装，或 `pkg install openjdk-21 aapt aapt2 d8 apksigner`。

## 1. 平台 android.jar（必需）

aapt2 链接资源要**完整的** SDK 平台 `android.jar`（ecj 包里带的精简版没有资源表，不能用）。用官方 command-line tools 下载（只用其中的 Java 工具和平台文件，桌面版 ELF 程序要换成手机版）：

```bash
export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools" "$HOME/sdk-download"
curl -fL https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -o "$HOME/sdk-download/clt.zip"
unzip -q "$HOME/sdk-download/clt.zip" -d "$HOME/sdk-download/u" && mv "$HOME/sdk-download/u/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
yes | bash "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses
bash "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "platforms;android-36" "build-tools;35.0.0"
for t in aapt aapt2 zipalign; do command -v $t >/dev/null && ln -sf "$(command -v $t)" "$ANDROID_HOME/build-tools/35.0.0/$t"; done
export ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"
```

（下载地址的版本号可能更新；失败时让用户确认网络或换最新的 commandlinetools 文件名。）

## 2. 小项目：手工流程（Java + 资源）

```bash
set -euo pipefail
B=build-manual; rm -rf "$B"; mkdir -p "$B/gen" "$B/classes" "$B/dex"
aapt2 compile --dir res -o "$B/res.zip"
aapt2 link -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java "$B/gen" \
  --min-sdk-version 26 --target-sdk-version 36 -0 arsc -o "$B/res.apk" "$B/res.zip"
find src "$B/gen" -name '*.java' > "$B/src.txt"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d "$B/classes" @"$B/src.txt"
jar cf "$B/classes.jar" -C "$B/classes" .
d8 --min-api 26 --lib "$ANDROID_JAR" --output "$B/dex" "$B/classes.jar"
cp "$B/res.apk" "$B/unsigned.apk" && zip -qj "$B/unsigned.apk" "$B"/dex/classes*.dex
if command -v zipalign >/dev/null; then zipalign -f 4 "$B/unsigned.apk" "$B/aligned.apk"; else cp "$B/unsigned.apk" "$B/aligned.apk"; fi
[ -f "$HOME/android-debug.keystore" ] || keytool -genkeypair -keystore "$HOME/android-debug.keystore" -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
apksigner sign --ks "$HOME/android-debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$B/app-debug.apk" "$B/aligned.apk"
apksigner verify --verbose "$B/app-debug.apk"
```

要点：zipalign 在签名**之前**；签名后不能再往 APK 里加文件；调试密钥要一直复用，否则不能覆盖安装。没有 javac 时可用 ecj：`ecj -8 -proc:none -encoding UTF-8 -bootclasspath "$ANDROID_JAR" -d "$B/classes" @"$B/src.txt"`。

## 3. 常规项目：Gradle（AndroidX、Kotlin、依赖库）

需要“Gradle 与 Kotlin”工具集或项目自带的 `gradlew`。关键是让 AGP 用手机版 aapt2：

```bash
bash ./gradlew "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" \
  "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" -Dorg.gradle.vfs.watch=false \
  --no-daemon --max-workers=2 :app:assembleDebug
```

`local.properties` 写 `sdk.dir=$ANDROID_HOME` 的展开路径。手机内存有限：`--max-workers=2`、限制堆大小；构建慢是正常的。NDK/JNI 项目需要手机版 NDK（另行安装），默认不支持。

产出 APK 后：用 `pocket-apk-test` 技能安装到本机测试，或告诉用户 APK 的路径（共享存储里的工作区可以直接在文件管理器点开安装）。
