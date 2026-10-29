package com.shinjuku.showdown

import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/** 主页截图：在 JVM 上用 layoutlib 渲染主页布局，结果在 app/src/test/snapshots/images/。 */
class HomeScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.Shinjuku")

    @Test
    fun homePixel5() {
        paparazzi.snapshot(paparazzi.inflate<View>(R.layout.activity_main))
    }

    @Test
    fun homeNexus5() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5)
        paparazzi.snapshot(paparazzi.inflate<View>(R.layout.activity_main))
    }
}
