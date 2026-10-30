package com.shinjuku.showdown

import android.content.Context
import android.graphics.BitmapFactory
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
 * 战斗画面截图：真正的 World + 真正的 PixelScene（和游戏里 GL 画的是同一份场景代码，这里走 Canvas 后端），
 * 按脚本输入打出几个场景。每推进 1/60 秒让场景读一次事件、更新一次粒子，模拟中间那些帧，最后画一帧截图。
 */
class BattlePreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, density = Density.XXHIGH),
        theme = "Theme.Shinjuku",
        useDeviceResolution = true, // 不缩小：像素画要按原始分辨率看
    )

    private val noActions: Controls.Actions = object : Controls.Actions {
        override fun back() {}
        override fun toggleMusic() {}
        override fun toggleStats() {}
    }

    internal inner class Scene(ctx: Context, d: Difficulty, script: Scene.() -> Unit) : View(ctx) {
        val world = World(BattleConfig().also { d.configure(it) })
        internal val controls = Controls(noActions)
        private val atlas = Atlas()
        private val bg = BitmapFactory.decodeResource(ctx.resources, R.drawable.bg_battle_px, BitmapFactory.Options().apply { inScaled = false })
        private val voidBg = BitmapFactory.decodeResource(ctx.resources, R.drawable.bg_void_px, BitmapFactory.Options().apply { inScaled = false })
        internal val scene = PixelScene(atlas, bg, voidBg, d, controls, null)
        private val target = CanvasTarget(atlas.bitmap, bg, voidBg)
        private val scratch = Canvas(android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888))
        val input = TickInput()
        private val dm = ctx.resources.displayMetrics
        private var setup = script
        private var w = 0
        private var h = 0

        /** 一直推进到出现某种事件（最多 limit 个 tick），返回是否出现了。 */
        fun stepsUntil(type: Int, limit: Int): Boolean {
            var seen = world.events.written
            repeat(limit) {
                steps(1)
                while (seen < world.events.written) {
                    if (world.events.type[(seen and Events.MASK.toLong()).toInt()] == type) return true
                    seen++
                }
            }
            return false
        }

        /** 按领域展开：过场直接算放完（截图里没有视频），无量空处展开。 */
        fun openDomain() {
            input.domain = true
            steps(1)
            world.endCutscene(world.castId)
        }

        fun steps(n: Int) {
            repeat(n) {
                world.step(input)
                input.clearEdges()
                if (world.tick % 2 == 0) frame(scratch) // 模拟游戏里的一帧（读事件、跑粒子），画到一张不要的小图上
            }
        }

        private fun frame(c: Canvas) {
            target.setDestination(c)
            scene.draw(target, world, 0f, world.tick / 120f, w, h)
        }

        override fun onDraw(c: Canvas) {
            w = width
            h = height
            controls.layout = Controls.Layout(width.toFloat(), height.toFloat(), dm.density, 0f, 0f, 0f, 0f)
            scene.layout(w, h)
            target.setHud(scene.hudText().bitmap)
            setup(this)
            setup = {}
            frame(c)
        }
    }

    @Test
    fun idle() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.MIWA) { steps(40) })
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
            world.sukuna.x = 600f
            steps(10)
            input.red = true
            steps(14 + 5)
        })
    }

    @Test
    fun purple() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.YOROZU) {
            world.gojo.x = 260f
            world.sukuna.x = 900f
            steps(10)
            input.purple = true
            steps(40 + 14)
        })
    }

    @Test
    fun purpleWindup() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.YOROZU) {
            world.gojo.x = 400f
            world.sukuna.x = 800f
            steps(10)
            input.purple = true
            steps(32)
        })
    }

    @Test
    fun hollowPurple() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.STRONGEST) {
            world.gojo.x = 300f
            world.sukuna.x = 500f
            steps(10)
            input.blueDown = true; input.blueHeld = true; input.blueUp = true
            input.blueHeld = false
            steps(12 + 18 + 50)
            input.red = true
            var formed = false
            for (i in 0 until 40) {
                steps(1)
                if (world.hollow.active && !formed) { formed = true; steps(9); break }
            }
        })
    }

    @Test
    fun blueFistCharging() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 520f
            world.sukuna.x = 640f
            steps(10)
            input.melee = true; input.meleeHeld = true
            steps(18 + 10)
        })
    }

    @Test
    fun blueFistAirWindup() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 540f
            world.sukuna.x = 640f
            steps(10)
            input.jump = true; input.jumpHeld = true
            steps(20)
            input.melee = true
            steps(10)
        })
    }

    @Test
    fun blueFistHit() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 560f
            world.sukuna.x = 640f
            steps(10)
            input.jump = true; input.jumpHeld = true
            steps(16)
            input.melee = true
            stepsUntil(Events.HIT, 80)
            input.jumpHeld = false
            steps(3)
        })
    }

    @Test
    fun blueChant() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 400f
            world.sukuna.x = 800f
            steps(10)
            input.blueDown = true; input.blueHeld = true
            steps(24 + 36 + 20)
        })
    }

    @Test
    fun redChant() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 300f
            world.sukuna.x = 900f
            steps(10)
            input.red = true; input.redHeld = true
            steps(24 + 72 + 20)
        })
    }

    @Test
    fun dodge() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 500f
            world.sukuna.x = 800f
            steps(10)
            input.dodge = true; input.moveX = -1f
            steps(8)
        })
    }

    @Test
    fun teleportBlueCombo() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 300f
            world.sukuna.x = 700f
            steps(10)
            input.dodgeHeld = true; input.blueDown = true; input.blueUp = true
            steps(1)
            input.dodgeHeld = false
            steps(24 + 36 + 14 * 2 + 3)
        })
    }

    @Test
    fun teleportRedCharge() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 300f
            world.sukuna.x = 1100f
            steps(10)
            input.dodgeHeld = true; input.red = true
            steps(1)
            input.dodgeHeld = false
            steps(24 + 18)
        })
    }

    @Test
    fun teleportRedFlight() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 300f
            world.sukuna.x = 1100f
            steps(10)
            input.dodgeHeld = true; input.red = true
            steps(1)
            input.dodgeHeld = false
            steps(24 + 24 + 22)
        })
    }

    @Test
    fun sukunaAmplification() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.MIWA) {
            world.gojo.x = 400f
            world.sukuna.x = 800f
            stepsUntil(Events.AMP, 4000)
            steps(40)
        })
    }

    @Test
    fun worldSlashChant() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.MIWA) {
            world.gojo.x = 300f
            world.sukuna.x = 800f
            var n = 0
            while (n < 20000 && !(world.sukuna.state == com.shinjuku.showdown.core.Fighter.S_WORLD && world.worldWindup == 144 && world.sukuna.stateTick >= 64)) {
                steps(1); n++
                if (world.gojo.hpMilli < world.gojo.maxHpMilli / 2) world.gojo.hpMilli = world.gojo.maxHpMilli
            }
        })
    }

    @Test
    fun worldSlashFired() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.MIWA) {
            world.gojo.x = 300f
            world.sukuna.x = 800f
            var seen = world.events.written
            var fired = false
            var n = 0
            while (n < 20000 && !fired) {
                steps(1); n++
                if (world.gojo.hpMilli < world.gojo.maxHpMilli / 2) world.gojo.hpMilli = world.gojo.maxHpMilli
                while (seen < world.events.written) {
                    val i = (seen and Events.MASK.toLong()).toInt()
                    if (world.events.type[i] == Events.SLASH && world.events.kind[i] == com.shinjuku.showdown.core.Attack.WORLD_SLASH) fired = true
                    seen++
                }
            }
            steps(4)
        })
    }

    @Test
    fun sukunaReverseTechnique() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.MIWA) {
            world.gojo.x = 100f
            world.sukuna.x = 1100f
            world.sukuna.hpMilli = world.sukuna.maxHpMilli / 3
            stepsUntil(Events.RCT, 6000)
            steps(60)
        })
    }

    @Test
    fun meleeHit() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 580f
            world.sukuna.x = 640f
            steps(10)
            input.melee = true
            stepsUntil(Events.HIT, 30)
            steps(3)
        })
    }

    @Test
    fun redMax() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 200f
            world.sukuna.x = 1000f
            steps(10)
            input.red = true; input.redHeld = true
            steps(36 + 24 + 8)
            input.redHeld = false
        })
    }

    @Test
    fun redMaxBurst() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 200f
            world.sukuna.x = 700f
            steps(10)
            input.red = true; input.redHeld = true
            stepsUntil(Events.RED_BURST, 200)
            input.redHeld = false
            steps(10)
        })
    }

    @Test
    fun purpleChant() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 360f
            world.sukuna.x = 900f
            steps(10)
            input.purple = true; input.purpleHeld = true
            steps(24 + 54 * 2 + 30)
        })
    }

    @Test
    fun purple200() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 300f
            world.sukuna.x = 1000f
            steps(10)
            input.purple = true; input.purpleHeld = true
            stepsUntil(Events.PURPLE_FIRE, 400)
            steps(24)
        })
    }

    @Test
    fun domainOpening() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.TEST) {
            world.gojo.x = 420f
            world.sukuna.x = 820f
            steps(10)
            openDomain()
            steps(40)
        })
    }

    @Test
    fun domainVoid() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.MIWA) {
            world.gojo.x = 420f
            world.sukuna.x = 820f
            steps(10)
            openDomain()
            steps(200)
        })
    }

    @Test
    fun dismantle() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.MIWA) {
            world.gojo.x = 400f
            world.sukuna.x = 800f
            stepsUntil(Events.SLASH, 3000)
            steps(4)
        })
    }

    @Test
    fun blackFlash() {
        paparazzi.snapshot(Scene(paparazzi.context, Difficulty.YOROZU) {
            world.gojo.x = 560f
            world.sukuna.x = 620f
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
