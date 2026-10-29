package com.shinjuku.showdown

import android.content.Context
import android.graphics.Canvas
import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.shinjuku.showdown.core.BattleConfig
import com.shinjuku.showdown.core.Events
import com.shinjuku.showdown.core.TickInput
import com.shinjuku.showdown.core.World
import org.junit.Rule
import org.junit.Test

/**
 * 战斗画面截图：用真正的 World 和真正的 BattleRenderer（同一份绘制代码），按脚本输入打出几个场景。
 * 每推进一段就让渲染器读一次事件，模拟中间那些帧，最后画一帧截图。
 */
class BattlePreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, density = Density.XXHIGH),
        theme = "Theme.Shinjuku",
    )

    private val noActions: Controls.Actions = object : Controls.Actions {
        override fun back() {}
        override fun toggleMusic() {}
        override fun toggleStats() {}
    }

    internal inner class Scene(ctx: Context, d: Difficulty, script: Scene.() -> Unit) : View(ctx) {
        val world = World(BattleConfig().also { d.configure(it) })
        internal val controls = Controls(noActions)
        internal val renderer = BattleRenderer(ctx.resources, d, controls, null, false)
        val input = TickInput()
        private val dm = ctx.resources.displayMetrics
        private var setup = script

        fun steps(n: Int) {
            repeat(n) {
                world.step(input)
                input.clearEdges()
                if (world.tick % 2 == 0) renderer.consume(world, world.tick / 120f)
            }
        }

        override fun onDraw(c: Canvas) {
            controls.layout = Controls.Layout(width.toFloat(), height.toFloat(), dm.density, 0f, 0f, 0f, 0f)
            renderer.draw(c, world, 0f, 0f, width, height) // 初始化布局
            setup(this)
            setup = {}
            renderer.draw(c, world, 0f, world.tick / 120f, width, height)
        }
    }

    @Test
    fun blueCapture() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.MIWA) {
            world.gojo.x = 380f
            world.sukuna.x = 640f
            steps(20)
            input.blueDown = true; input.blueHeld = true; input.blueUp = true
            input.blueHeld = false
            steps(12 + 18 + 70)
        })
    }

    @Test
    fun chargedBlue() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.STRONGEST) {
            world.gojo.x = 300f
            world.sukuna.x = 760f
            steps(10)
            input.blueDown = true; input.blueHeld = true
            steps(42 + 36)
            input.blueHeld = false
            steps(18 + 16)
        })
    }

    @Test
    fun red() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.URAUME) {
            world.gojo.x = 420f
            world.sukuna.x = 560f
            steps(10)
            input.red = true
            steps(14 + 9)
        })
    }

    @Test
    fun blackFlash() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.YOROZU) {
            world.gojo.x = 560f
            world.sukuna.x = 620f
            // 一直打到出黑闪为止，然后停在命中后几帧
            var seen = world.events.written
            var flashed = false
            for (round in 0 until 64) {
                world.gojo.x = world.sukuna.x - 60f
                input.melee = true
                for (t in 0 until 40) {
                    steps(1)
                    while (seen < world.events.written) {
                        if (world.events.type[(seen and Events.MASK.toLong()).toInt()] == Events.BLACK_FLASH) flashed = true
                        seen++
                    }
                    if (flashed) break
                }
                if (flashed) break
                world.sukuna.hpMilli = world.sukuna.maxHpMilli
                world.sukuna.x = 620f
                world.sukuna.vx = 0f
            }
            steps(6)
        })
    }
}
