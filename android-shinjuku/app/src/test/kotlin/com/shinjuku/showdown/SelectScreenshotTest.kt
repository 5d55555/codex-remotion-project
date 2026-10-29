package com.shinjuku.showdown

import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** 选难度界面截图：和 SelectActivity 用同一个布局、同一个建卡片的方法。 */
class SelectScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, density = Density.XXHIGH),
        theme = "Theme.Shinjuku",
    )

    @Test
    fun select() {
        val root = FrameLayout(paparazzi.context)
        LayoutInflater.from(paparazzi.context).inflate(R.layout.activity_select, root, true)
        val row = root.findViewById<LinearLayout>(R.id.cards)
        for (d in Difficulty.values()) row.addView(SelectActivity.card(paparazzi.context, d) {})
        paparazzi.snapshot(root)
    }
}
