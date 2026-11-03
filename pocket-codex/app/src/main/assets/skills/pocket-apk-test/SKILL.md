---
name: pocket-apk-test
description: 在同一部手机上测试 APK：通过“无线调试”让手机自己 adb 连自己，然后安装、启动、截图、读 UI 树、模拟点击输入、抓 logcat 崩溃、跑 monkey 和 androidTest 插桩测试。用户要测试/验证 App、看崩溃、做自动化点击时使用。
metadata:
  short-description: 无线调试自连 adb，在本机测试 APK
---

# 在本机测试 APK（无需电脑）

需要 `adb`（“APK 构建与测试”工具集里的 android-tools）。手机里跑安卓模拟器不现实（没有 KVM，QEMU 纯软件模拟太慢），**用手机本身当测试设备**。

## 一次性设置（要用户手动操作）

请用户：开发者选项 → 打开“无线调试”（需要连 Wi-Fi）→ 点“使用配对码配对设备”，把**配对端口和六位配对码**告诉你；无线调试主页上还有一个**连接端口**（和配对端口不同）。最好分屏，配对窗口切走会关掉。

```bash
adb start-server
adb pair 127.0.0.1:<配对端口> <六位配对码>
adb connect 127.0.0.1:<连接端口>
export ANDROID_SERIAL=127.0.0.1:<连接端口>
adb shell id        # 应看到 uid=2000(shell)
```

`127.0.0.1` 不通就用设置页显示的手机 Wi-Fi IP。配对密钥在 `~/.android/adbkey*`，别删。重启手机或断 Wi-Fi 后连接端口会变：`adb mdns services` 找 `_adb-tls-connect._tcp`，再 `adb connect`。

## 测试流程

```bash
APP=com.example.demo; ACT=com.example.demo/.MainActivity; APK=build/app-debug.apk
OUT="$HOME/test-results/$(date +%Y%m%d-%H%M%S)"; mkdir -p "$OUT"
adb install -r -t "$APK"
adb logcat -c; adb logcat -b main -b system -b crash -v threadtime > "$OUT/logcat.txt" 2>&1 & LP=$!
adb shell am force-stop "$APP"; adb shell am start -W -n "$ACT"
timeout 20 adb shell uiautomator dump /data/local/tmp/ui.xml && adb pull /data/local/tmp/ui.xml "$OUT/ui.xml"
adb exec-out screencap -p > "$OUT/screen.png"
adb shell input tap <x> <y>          # 坐标从 ui.xml 的 bounds 算，别写死
adb shell input text 'hello%sworld'  # %s 是空格
adb shell input keyevent KEYCODE_BACK
adb logcat -b crash -d -v threadtime > "$OUT/crash.txt"; kill $LP
```

- 用资源 ID / 文字 / content-desc 定位元素，等状态满足再操作；不要靠固定 sleep。
- `am start` 成功不等于测试通过：检查 UI 树里的文字、截图（可以把截图给用户看）、crash 日志。
- 压力测试：`adb shell monkey -p $APP -s 20261103 --throttle 100 --pct-syskeys 0 -v 2000 > "$OUT/monkey.txt"`。
- 批量操作也可以用纯 Python 的 `pure-python-adb`（连现有的 adb server）；网络接口调试用 `socat`、`nmap`（`ncat`）、`wrk`/`vegeta` 压测、`testssl.sh` 查 TLS（“网络调试与压测”工具集）。
- 正式回归：Gradle `assembleDebugAndroidTest`，两个 APK 都装上后 `adb shell am instrument -w -r <测试包>/androidx.test.runner.AndroidJUnitRunner`。
- 测试时屏幕要亮着、已解锁；口袋工坊自己也在前台跑着，点击可能落到口袋工坊上——先 `am start` 目标 App。

## 不用 adb 的替代

装了 Shizuku 的用户可以用 `rish`（Shizuku → 在终端应用中使用，导出 rish 和 rish_shizuku.dex 到 `$HOME/rish/`，`export RISH_APPLICATION_ID=com.pocketcodex.app`，`sh ~/rish/rish -c 'am start …'`）。
