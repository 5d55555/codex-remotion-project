package com.shinjuku.showdown

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.shinjuku.showdown.core.BattleConfig
import com.shinjuku.showdown.core.TickInput
import com.shinjuku.showdown.core.World
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * 游戏里真正用的 GL 后端（GlTarget），在 JVM 上用 Mesa 的软件 GLES 跑一遍，和 Canvas 后端逐像素对照。
 * 同一个 World、同一串时间，两个 PixelScene 各画各的；几个时刻读回两边的像素层比较，GL 的画面存到 build/gl-parity/。
 * 机器上没有 libEGL / libGLESv2 时跳过。
 */
class GlParityTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = W, screenHeight = H, orientation = ScreenOrientation.LANDSCAPE, density = Density.XXHIGH),
        theme = "Theme.Shinjuku",
        useDeviceResolution = true,
    )

    private val noActions: Controls.Actions = object : Controls.Actions {
        override fun back() {}
        override fun toggleMusic() {}
        override fun toggleStats() {}
    }

    @Test
    fun glDrawsTheSameAsCanvas() {
        val gles = try {
            HeadlessGles(W, H)
        } catch (e: Throwable) {
            assumeNoException("没有可用的软件 GLES", e)
            return
        }
        gles.use {
            val ctx = paparazzi.context
            val dp = ctx.resources.displayMetrics.density
            val atlas = Atlas()
            val bg = BitmapFactory.decodeResource(ctx.resources, R.drawable.bg_battle_px, BitmapFactory.Options().apply { inScaled = false })
            val world = World(BattleConfig())
            val ca = Controls(noActions).also { it.layout = Controls.Layout(W.toFloat(), H.toFloat(), dp, 0f, 0f, 0f, 0f) }
            val cb = Controls(noActions).also { it.layout = ca.layout }
            val voidBg = BitmapFactory.decodeResource(ctx.resources, R.drawable.bg_void_px, BitmapFactory.Options().apply { inScaled = false })
            val sc = PixelScene(atlas, bg, voidBg, Difficulty.TEST, ca, null)
            val sg = PixelScene(atlas, bg, voidBg, Difficulty.TEST, cb, null)
            val ct = CanvasTarget(atlas.bitmap, bg, voidBg)
            val gt = GlTarget(LwjglGl(), atlas.bitmap, bg, voidBg)
            gt.initForTest()
            sc.layout(W, H)
            sg.layout(W, H)
            ct.setHud(sc.hudText().bitmap)
            gt.setHud(sg.hudText().bitmap)
            ct.setDestination(Canvas(Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)))
            val input = TickInput()

            fun frame() {
                val t = world.tick / 120f
                sc.draw(ct, world, 0f, t, W, H)
                sg.draw(gt, world, 0f, t, W, H)
            }
            fun steps(n: Int) = repeat(n) {
                world.step(input)
                input.clearEdges()
                if (world.tick % 2 == 0) frame()
            }
            fun check(name: String) {
                frame()
                val lo = ct.scene()
                val w = lo.width
                val h = lo.height
                val a = IntArray(w * h)
                val b = IntArray(w * h)
                lo.getPixels(a, 0, w, 0, 0, w, h)
                gt.readScene(b)
                var bad = 0
                for (i in a.indices) {
                    val d = maxOf(
                        Math.abs((a[i] shr 16 and 255) - (b[i] shr 16 and 255)),
                        Math.abs((a[i] shr 8 and 255) - (b[i] shr 8 and 255)),
                        Math.abs((a[i] and 255) - (b[i] and 255)),
                    )
                    if (d > 40) bad++
                }
                val out = File("build/gl-parity").apply { mkdirs() }
                for ((suffix, px) in listOf("gl" to b, "canvas" to a)) {
                    val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                    File(out, "$name-$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                val ratio = bad / a.size.toFloat()
                println("GL 对照 $name：${w}x$h，差别大的像素 ${"%.2f".format(ratio * 100)}%")
                assertTrue("$name：GL 和 Canvas 差别太大 ${ratio * 100}%", ratio < 0.03f)
            }

            steps(40)
            check("idle")
            world.gojo.x = 300f
            world.sukuna.x = 500f
            input.blueDown = true; input.blueHeld = true; input.blueUp = true
            input.blueHeld = false
            steps(12 + 18 + 50)
            check("blue")
            input.red = true
            for (i in 0 until 40) {
                steps(1)
                if (world.hollow.active) break
            }
            steps(10)
            check("hollow")
            input.purple = true
            steps(60)
            check("purple")
            // 赫·最大输出（大球、尾迹）
            world.gojo.x = 200f
            world.sukuna.x = 1100f
            steps(400)
            input.red = true; input.redHeld = true
            steps(36 + 24 + 8)
            input.redHeld = false
            check("redMax")
            // 无量空处：铺开一半（按行贴图）和铺满
            steps(200)
            input.domain = true
            steps(1)
            world.endCutscene(world.castId)
            steps(42)
            check("voidOpening")
            steps(120)
            check("void")
        }
    }

    companion object {
        const val W = 2340
        const val H = 1080
    }
}
