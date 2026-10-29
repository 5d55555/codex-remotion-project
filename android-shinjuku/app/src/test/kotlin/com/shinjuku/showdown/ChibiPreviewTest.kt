package com.shinjuku.showdown

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** 角色造型预览：战斗背景上摆几种姿势，看 Q 版小人画得怎么样。 */
class ChibiPreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, density = Density.XXHIGH),
        theme = "Theme.Shinjuku",
    )

    class Preview(ctx: Context) : View(ctx) {
        private val bg = BitmapFactory.decodeResource(resources, R.drawable.bg_battle)
        private val gojo = Chibi(Chibi.GOJO)
        private val sukuna = Chibi(Chibi.SUKUNA)
        private val pose = Chibi.Pose()

        override fun onDraw(c: Canvas) {
            c.drawBitmap(bg, null, Rect(0, 0, width, height), null)
            val ground = height * 0.86f
            val s = height / 1080f * 2.6f
            fun put(ch: Chibi, x: Float, f: Float, lean: Float, aF: Float, aB: Float, lF: Float, lB: Float, glow: Float = 0f, gc: Int = 0) {
                pose.face = f; pose.lean = lean; pose.armFront = aF; pose.armBack = aB; pose.legFront = lF; pose.legBack = lB
                pose.glow = glow; pose.glowColor = gc; pose.squash = 1f; pose.bob = 0f; pose.head = 0f; pose.hurt = 0f
                ch.draw(c, x, ground, s, pose)
            }
            put(gojo, width * 0.12f, 1f, 0f, 8f, -6f, 4f, -4f)                       // 站立
            put(gojo, width * 0.28f, 1f, 8f, -30f, 35f, 28f, -26f)                   // 跑
            put(gojo, width * 0.44f, 1f, 10f, 85f, -20f, 18f, -12f, 1f, 0xFF4C9BFF.toInt()) // 苍：伸手
            put(gojo, width * 0.60f, 1f, 4f, 95f, 10f, 10f, -8f, 1f, 0xFFFF3B4E.toInt())    // 赫
            put(sukuna, width * 0.80f, -1f, 0f, 10f, -8f, 4f, -4f)                 // 宿傩站立
        }
    }

    /** 特写：两个角色放大，看五官和头发。 */
    class Closeup(ctx: Context) : View(ctx) {
        private val gojo = Chibi(Chibi.GOJO)
        private val sukuna = Chibi(Chibi.SUKUNA)
        private val pose = Chibi.Pose()
        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF2A2350.toInt())
            val s = height / 120f
            pose.armFront = 8f; pose.armBack = -6f; pose.legFront = 4f; pose.legBack = -4f
            pose.face = 1f
            gojo.draw(c, width * 0.3f, height * 0.95f, s, pose)
            pose.face = -1f
            sukuna.draw(c, width * 0.7f, height * 0.95f, s, pose)
        }
    }

    @Test
    fun poses() {
        paparazzi.snapshot(Preview(paparazzi.context))
    }

    @Test
    fun closeup() {
        paparazzi.snapshot(Closeup(paparazzi.context))
    }
}
