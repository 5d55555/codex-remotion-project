package com.shinjuku.showdown

import android.graphics.BitmapFactory
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.shinjuku.showdown.core.BattleConfig
import com.shinjuku.showdown.core.TickInput
import com.shinjuku.showdown.core.World
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * 绘制开销（不是截图，也不判对错，只记录）：真正的 World + PixelScene，画进一个只计数的 PixelTarget，
 * 史上最强的宿傩对一段乱按的五条悟打两分钟，每 2 tick 画一帧（60 帧 / 秒）。记下每帧 PixelScene.draw 的 CPU 时间、
 * 每帧的四边形数，以及按 GlTarget 的规则（换贴图、换混合方式就要提交一批）算出来的批数，还有逻辑和绘制每帧分配了多少字节
 * （应该是 0）、进战斗前的准备时间。结果写到 app/build/drawcost.txt。平时跳过，要测时：
 *   DRAW_COST=1 ./gradlew :app:testDebugUnitTest --tests '*DrawCostTest*'
 */
class DrawCostTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, density = Density.XXHIGH),
        theme = "Theme.Shinjuku",
        useDeviceResolution = true,
    )

    /** 只计数：四边形、批（换贴图 / 换混合 / present 都会提交一批）。 */
    private class Counter : PixelTarget {
        var quads = 0; var batches = 0; var pending = 0; var tex = -1; var add = false; var wantAdd = false
        private fun q(t: Int) {
            if (t != tex) { flush(); tex = t }
            if (wantAdd != add) { flush(); add = wantAdd }
            quads++; pending++
        }
        private fun flush() { if (pending > 0) { batches++; pending = 0 } }
        fun reset() { quads = 0; batches = 0; pending = 0; tex = -1; add = false; wantAdd = false }
        override fun begin(vw: Int, vh: Int, sw: Int, sh: Int, scale: Float) {}
        override fun sprite(tex: Int, sx: Int, sy: Int, w: Int, h: Int, x: Float, y: Float, pivotX: Float, pivotY: Float, deg: Float, scaleX: Float, scaleY: Float, mul: Int, add: Int) = q(tex)
        override fun rect(x: Float, y: Float, w: Float, h: Float, argb: Int) = q(tex)
        override fun shape(cx: Float, cy: Float, rOut: Float, rIn: Float, a0: Float, aLen: Float, argb: Int) = q(tex)
        override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, argb: Int) = q(tex)
        override fun additive(on: Boolean) { wantAdd = on }
        override fun present() { flush(); tex = 100; wantAdd = false; q(100) }
        override fun present(x: Int, y: Int, w: Int, h: Int) = present()
        override fun hud(sx: Int, sy: Int, w: Int, h: Int, x: Float, y: Float, mul: Int) = q(200)
        override fun hudRect(x: Float, y: Float, w: Float, h: Float, argb: Int) = q(tex)
        override fun end() = flush()
    }

    private class Rng(var s: Long) {
        fun next(): Double { s = s * 6364136223846793005L + 1442695040888963407L; return ((s ushr 11).toDouble()) / (1L shl 53).toDouble() }
    }

    @Test
    fun drawCost() {
        org.junit.Assume.assumeTrue("设置 DRAW_COST=1 才跑", System.getenv("DRAW_COST") != null)
        val ctx = paparazzi.context
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bg = BitmapFactory.decodeResource(ctx.resources, R.drawable.bg_battle_px, opts)
        val voidBg = BitmapFactory.decodeResource(ctx.resources, R.drawable.bg_void_px, opts)
        val shrineBg = BitmapFactory.decodeResource(ctx.resources, R.drawable.bg_shrine_px, opts)
        val controls = Controls(object : Controls.Actions {
            override fun back() {}
            override fun toggleMusic() {}
            override fun toggleStats() {}
        })
        val sw = 2340; val sh = 1080
        controls.layout = Controls.Layout(sw.toFloat(), sh.toFloat(), 2.75f, 0f, 0f, 0f, 0f)
        val out = StringBuilder()
        for (round in 0 until 3) { // 第 0 轮预热 JIT，后两轮记录
            for (d in listOf(Difficulty.STRONGEST, Difficulty.URAUME)) {
                val tA = System.nanoTime()
                val atlas = Atlas()
                val tB = System.nanoTime()
                val scene = PixelScene(atlas, bg, voidBg, shrineBg, d, controls, null)
                val tB2 = System.nanoTime()
                scene.layout(sw, sh)
                val tC = System.nanoTime()
                if (round > 0) out.append(String.format("准备：Atlas %.1f ms，PixelScene %.1f ms，文字图集 %.1f ms%n", (tB - tA) / 1e6, (tB2 - tB) / 1e6, (tC - tB2) / 1e6))
                val world = World(BattleConfig().also { d.configure(it); it.seed = 7L + round })
                val input = TickInput()
                val rng = Rng(11L + round)
                val c = Counter()
                val times = LongArray(8000); var n = 0
                var maxQuads = 0; var sumQuads = 0L; var maxBatches = 0; var sumBatches = 0L
                var move = 0f
                val mxb = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
                val am = Class.forName("com.sun.management.ThreadMXBean").getMethod("getCurrentThreadAllocatedBytes")
                fun alloc(): Long = am.invoke(mxb) as Long
                var base = Long.MAX_VALUE
                repeat(50) { val x0 = alloc(); base = minOf(base, alloc() - x0) } // 反射调用本身的分配
                var allocStep = 0L; var allocDraw = 0L; var allocFrames = 0
                for (tick in 0 until 120 * 120) {
                    if (tick % 60 == 0) move = listOf(-1f, 0f, 1f, 1f, -1f)[(rng.next() * 5).toInt()]
                    input.moveX = move
                    val r = rng.next()
                    when {
                        r < 0.012 -> input.melee = true
                        r < 0.016 -> input.red = true
                        r < 0.019 -> { input.blueDown = true; input.blueUp = true }
                        r < 0.021 -> input.purple = true
                        r < 0.024 -> input.dodge = true
                        r < 0.028 -> input.jump = true
                        r < 0.0284 -> input.domain = true
                    }
                    val a0 = alloc()
                    world.step(input)
                    if (tick > 240) allocStep += alloc() - a0 - base
                    input.clearEdges()
                    if (world.cutscene) world.endCutscene(world.castId)
                    if (world.outcome != World.OUTCOME_NONE) break
                    if (tick % 2 == 0 && n < times.size) {
                        c.reset()
                        val t0 = System.nanoTime()
                        val b0 = alloc()
                        scene.draw(c, world, 0.5f, tick / 120f, sw, sh)
                        times[n++] = System.nanoTime() - t0
                        if (tick > 240) { allocDraw += alloc() - b0 - base; allocFrames++ }
                        maxQuads = maxOf(maxQuads, c.quads); sumQuads += c.quads
                        maxBatches = maxOf(maxBatches, c.batches); sumBatches += c.batches
                    }
                }
                if (round == 0) continue
                val t = times.copyOf(n).sorted()
                out.append(String.format("%s 第%d轮 %d 帧：draw 平均 %.3f ms  中位 %.3f  99%% %.3f  最大 %.3f；四边形 平均 %d 最多 %d；批 平均 %.1f 最多 %d；分配 逻辑 %d 字节 绘制 %d 字节（%d 帧）%n",
                    d.name, round, n, t.average() / 1e6, t[n / 2] / 1e6, t[(n * 0.99).toInt()] / 1e6, t[n - 1] / 1e6,
                    sumQuads / n, maxQuads, sumBatches / n.toDouble(), maxBatches, allocStep, allocDraw, allocFrames))
            }
        }
        File("build/drawcost.txt").writeText(out.toString())
    }
}
