plugins {
    id("com.android.application") version "8.13.2" apply false
    // 下面两个只用于截图测试（Paparazzi 用 layoutlib 在 JVM 上渲染界面），不进 APK
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("app.cash.paparazzi") version "2.0.0-alpha05" apply false
}
