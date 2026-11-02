package com.shinjuku.showdown

import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 设置页（v0.16）截图 + 音量换算。 */
class SettingsScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.Shinjuku")

    private fun page(music: Int, sfxOn: Boolean): FrameLayout {
        val root = FrameLayout(paparazzi.context)
        LayoutInflater.from(paparazzi.context).inflate(R.layout.activity_settings, root, true)
        fun set(seek: Int, value: Int, v: Int) {
            root.findViewById<SeekBar>(seek).progress = v
            root.findViewById<TextView>(value).text = "$v%"
        }
        set(R.id.master_seek, R.id.master_value, 100)
        set(R.id.music_seek, R.id.music_value, music)
        set(R.id.sfx_seek, R.id.sfx_value, 80)
        root.findViewById<Switch>(R.id.sfx_switch).isChecked = sfxOn
        if (!sfxOn) {
            root.findViewById<SeekBar>(R.id.sfx_seek).alpha = 0.35f
            root.findViewById<TextView>(R.id.sfx_value).alpha = 0.35f
        }
        return root
    }

    @Test
    fun settings() {
        paparazzi.snapshot(page(70, true))
    }

    @Test
    fun settingsSfxOff() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5)
        paparazzi.snapshot(page(100, false))
    }

    /** 0–100 按平方换成线性增益：100% 原样、50% 约 −12 dB、0 静音，越界夹住。 */
    @Test
    fun gainCurve() {
        assertEquals(1f, Settings.gain(100), 0f)
        assertEquals(0.25f, Settings.gain(50), 1e-6f)
        assertEquals(0f, Settings.gain(0), 0f)
        assertEquals(1f, Settings.gain(150), 0f)
        assertEquals(0f, Settings.gain(-5), 0f)
    }
}
