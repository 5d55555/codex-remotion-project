package com.shinjuku.showdown

import android.view.LayoutInflater
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/** 主页截图：在 JVM 上用 layoutlib 渲染主页布局，结果在 app/src/test/snapshots/images/。 */
class HomeScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.Shinjuku")

    // 主页布局的根是 <merge>，要挂到一个 FrameLayout 上（和 Activity 的内容容器一样）
    private fun home(): FrameLayout {
        val root = FrameLayout(paparazzi.context)
        LayoutInflater.from(paparazzi.context).inflate(R.layout.activity_main, root, true)
        return root
    }

    @Test
    fun homePixel5() {
        paparazzi.snapshot(home())
    }

    @Test
    fun homeNexus5() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5)
        paparazzi.snapshot(home())
    }
}
