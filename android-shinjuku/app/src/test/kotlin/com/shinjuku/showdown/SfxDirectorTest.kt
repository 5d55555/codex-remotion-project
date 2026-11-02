package com.shinjuku.showdown

import com.shinjuku.showdown.core.BattleConfig
import com.shinjuku.showdown.core.TickInput
import com.shinjuku.showdown.core.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 技能音效的导演（v0.15）：用假的输出端记下它要播什么，在 JVM 上跑脚本和乱按的对局。
 * 检查每个招式都有对应的音效、过场期间没有声音、不刷屏、不影响战斗结果、每 tick 零分配。
 */
class SfxDirectorTest {
    /** 记录调用的假输出端。 */
    private class FakeOut(val world: () -> World?) : SfxOut {
        val plays = ArrayList<Int>()
        val handles = ArrayList<Int>()      // 每个单发的句柄（和 plays 一一对应）
        val stopped = HashSet<Int>()        // 被掐掉的句柄
        val playTicks = ArrayList<Int>()
        val pans = ArrayList<Float>()
        val loopStarts = ArrayList<Int>()
        val loops = IntArray(SfxBank.LOOP_SLOTS) { -1 }
        var stopAlls = 0
        var playsInCutscene = 0
        var loopsInCutscene = 0
        private var handle = 0
        override fun play(cue: Int, variant: Int, pan: Float, gain: Float, rate: Float, priority: Int): Int {
            val w = world()
            if (w != null && w.cutscene) playsInCutscene++
            assertTrue("变体越界", variant in 0 until SfxBank.RES[cue].size)
            assertTrue("声像越界", pan in -1f..1f)
            assertTrue("增益越界", gain in 0f..1f)
            plays.add(cue)
            playTicks.add(w?.tick ?: 0)
            pans.add(pan)
            handles.add(++handle)
            return handle
        }

        override fun stop(handle: Int) {
            if (handle != 0) stopped.add(handle)
        }

        /** 最后一个 cue 的单发被掐掉了吗。 */
        fun lastStopped(cue: Int): Boolean {
            val i = plays.lastIndexOf(cue)
            return i >= 0 && stopped.contains(handles[i])
        }
        override fun loop(slot: Int, cue: Int, pan: Float, gain: Float) {
            val w = world()
            if (w != null && w.cutscene) loopsInCutscene++
            if (loops[slot] != cue) loopStarts.add(cue)
            loops[slot] = cue
        }

        override fun stopLoop(slot: Int) {
            loops[slot] = -1
        }

        override fun stopAll() {
            stopAlls++
            loops.fill(-1)
        }
    }

    private val names: Map<Int, String> = SfxBank::class.java.declaredFields
        .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && it.name != "COUNT" && it.name != "LOOP_SLOTS" }
        .associate { it.isAccessible = true; it.getInt(null) to it.name }

    private fun FakeOut.names() = plays.map { names[it] ?: "?$it" }

    private inner class Rig(cfg: BattleConfig = BattleConfig()) {
        val world = World(cfg)
        val out = FakeOut { world }
        val sfx = SfxDirector(out)
        val input = TickInput()

        fun steps(n: Int) {
            repeat(n) {
                world.step(input)
                sfx.update(world)
                input.clearEdges()
            }
        }
    }

    @Test
    fun meleeChainSwingsAndHits() {
        val r = Rig()
        r.world.gojo.x = 400f
        r.world.sukuna.x = 470f
        r.steps(5)
        repeat(3) {
            r.input.melee = true
            r.steps(30)
        }
        val n = r.out.names()
        val swings = n.filter { it.startsWith("SWING_") }
        assertEquals("拳、踢、上勾：$n", listOf("SWING_JAB", "SWING_KICK", "SWING_UPPER"), swings)
        assertTrue("打中有受击声：$n", n.any { it.startsWith("HIT_") || it.startsWith("BLACK_FLASH") })
        assertTrue("开场提示：$n", n.first() == "SIG_START")
    }

    @Test
    fun blueRedPurple() {
        val r = Rig()
        r.world.gojo.x = 380f
        r.world.sukuna.x = 640f
        r.steps(5)
        r.input.blueDown = true; r.input.blueHeld = true; r.input.blueUp = true
        r.input.blueHeld = false
        var t = 0
        while (t++ < 1200 && !(r.world.blue.phase == com.shinjuku.showdown.core.BlueOrb.OFF && r.out.plays.contains(SfxBank.BLUE_ANCHOR))) r.steps(1)
        r.steps(5)
        var n = r.out.names()
        assertTrue("苍：掷出、落点：$n", n.contains("BLUE_LAUNCH") && n.contains("BLUE_ANCHOR"))
        assertTrue("苍的吸引是循环音：${r.out.loopStarts}", r.out.loopStarts.contains(SfxBank.BLUE_LOOP))
        assertTrue("苍结束：循环停、碎光：$n", r.out.loops[0] == -1 && n.contains("DISSOLVE"))

        val r2 = Rig()
        r2.world.gojo.x = 420f
        r2.world.sukuna.x = 600f
        r2.steps(10)
        r2.input.red = true
        r2.steps(60)
        n = r2.out.names()
        assertTrue("赫：推出、爆发：$n", n.contains("RED_FIRE") && n.contains("RED_BURST"))
        assertFalse("赫的爆发替代了通用受击：$n", n.any { it.startsWith("HIT_") })

        val r3 = Rig()
        r3.world.gojo.x = 300f
        r3.world.sukuna.x = 700f
        r3.steps(10)
        r3.input.purple = true
        r3.steps(120)
        n = r3.out.names()
        assertTrue("茈：发射、重受击：$n", n.contains("PURPLE_FIRE") && n.contains("HIT_HEAVY"))
    }

    @Test
    fun dodgeJumpLand() {
        val r = Rig()
        r.world.gojo.x = 400f
        r.world.sukuna.x = 900f
        r.steps(5)
        r.input.dodge = true
        r.steps(80)
        r.input.jump = true
        r.steps(120)
        val n = r.out.names()
        assertTrue("闪避、跳、落地：$n", n.contains("DODGE") && n.contains("JUMP") && n.contains("LAND_LIGHT"))
    }

    /** 长按的招：出力最大的苍（三段咒词）、200% 茈（四段）、苍拳；茈·最大输出的十段咒词和 11 秒固定动作的各个节点。 */
    @Test
    fun chargedMovesAndPurpleMax() {
        val r = Rig()
        r.world.gojo.x = 300f
        r.world.sukuna.x = 760f
        r.steps(10)
        r.input.blueDown = true; r.input.blueHeld = true
        var h = 0
        while (h++ < 600 && !r.out.plays.contains(SfxBank.BLUE_LAUNCH_MAX)) r.steps(1)
        r.input.blueHeld = false; r.input.blueUp = true
        r.steps(200)
        var n = r.out.names()
        assertTrue("苍出力最大：三段咒词、掷出、落点、循环：$n ${r.out.loopStarts}",
            n.count { it == "CHANT_BLUE" } >= 3 && n.contains("BLUE_LAUNCH_MAX") && n.contains("BLUE_ANCHOR_MAX") && r.out.loopStarts.contains(SfxBank.BLUE_LOOP_MAX))

        val p = Rig()
        p.world.gojo.x = 200f
        p.world.sukuna.x = 900f
        p.steps(10)
        p.input.purple = true; p.input.purpleHeld = true
        var t = 0
        while (t++ < 600 && !p.out.plays.contains(SfxBank.PURPLE_FIRE_200)) p.steps(1)
        p.input.purpleHeld = false
        n = p.out.names()
        assertTrue("200% 茈：四段咒词、发射：$n", n.count { it == "CHANT_PURPLE" } == 4 && n.contains("PURPLE_FIRE_200"))

        val f = Rig()
        f.world.gojo.x = 400f
        f.world.sukuna.x = 470f
        f.steps(5)
        f.input.jumpHeld = true; f.input.melee = true
        f.steps(60)
        n = f.out.names()
        assertTrue("苍拳：蓄、出手：$n", n.contains("FIST_CHARGE") && n.contains("BLUE_LAUNCH"))

        val m = Rig()
        m.world.gojo.x = 100f
        m.world.sukuna.x = 1000f
        m.steps(5)
        m.input.blueDown = true; m.input.blueHeld = true; m.input.red = true; m.input.redHeld = true
        t = 0
        while (t++ < 900 && m.world.cine == World.CINE_NONE) m.steps(1)
        assertEquals("进了茈·最大输出的固定动作", World.CINE_PURPLE_MAX, m.world.cine)
        m.input.blueHeld = false; m.input.redHeld = false
        val chants = m.out.names().count { it.startsWith("CHANT_") }
        assertEquals("十段咒词", 10, chants)
        t = 0
        while (t++ < 1500 && m.world.cine != World.CINE_NONE) m.steps(1)
        m.steps(5)
        n = m.out.names()
        for (must in listOf("CINE_RISE", "RED_FIRE_MAX", "SLASH_CINE", "CINE_CONTACT", "CINE_BLAST", "DISSOLVE_LONG"))
            assertTrue("固定动作的节点 $must：$n", n.contains(must))
        assertEquals("追加咏唱三段", 13, n.count { it.startsWith("CHANT_") })
        assertTrue("固定动作里苍的循环起来过：${r.out.loopStarts}", m.out.loopStarts.contains(SfxBank.BLUE_LOOP_MAX))
        assertFalse("爆开的那一下不再叠重受击：$n", n.subList(n.indexOf("CINE_BLAST"), n.size).contains("HIT_HEAVY"))
    }

    /** 念到一半松手：补一声“断”，正在响的那段咒词立刻掐掉；茈·最大输出的固定动作里切后台再回来，苍的循环接得上。 */
    @Test
    fun breaksCutTheChantAndLoopsResume() {
        val p = Rig()
        p.world.gojo.x = 200f
        p.world.sukuna.x = 900f
        p.steps(10)
        p.input.purple = true; p.input.purpleHeld = true
        var t = 0
        while (t++ < 600 && p.out.plays.count { it == SfxBank.CHANT_PURPLE } < 2) p.steps(1)
        p.steps(5)
        p.input.purpleHeld = false
        p.steps(10)
        val n = p.out.names()
        assertTrue("松手打断：$n", n.contains("CAST_BREAK") && !n.contains("PURPLE_FIRE_200"))
        assertTrue("咒词被掐掉", p.out.lastStopped(SfxBank.CHANT_PURPLE))

        val m = Rig()
        m.world.gojo.x = 100f
        m.world.sukuna.x = 1000f
        m.steps(5)
        m.input.blueDown = true; m.input.blueHeld = true; m.input.red = true; m.input.redHeld = true
        t = 0
        while (t++ < 900 && m.world.cine == World.CINE_NONE) m.steps(1)
        m.input.blueHeld = false; m.input.redHeld = false
        m.steps(300)
        assertEquals("固定动作里苍在响", SfxBank.BLUE_LOOP_MAX, m.out.loops[0])
        m.sfx.setPaused(true) // 切后台
        assertEquals("后台全停", -1, m.out.loops[0])
        m.sfx.setPaused(false)
        m.steps(1)
        assertEquals("回来以后接上", SfxBank.BLUE_LOOP_MAX, m.out.loops[0])
    }

    /** 瞬移苍连段：离开、背后扣合、五下连打（只有第 1、5 下带低频），宿傩被击倒摔地是倒地闷响，不是普通落地。 */
    @Test
    fun teleportComboEndsWithDownThud() {
        val r = Rig()
        r.world.gojo.x = 300f
        r.world.sukuna.x = 700f
        r.steps(10)
        r.input.dodgeHeld = true
        r.input.blueDown = true; r.input.blueHeld = true; r.input.blueUp = true
        r.steps(2)
        r.input.blueHeld = false
        r.steps(3)
        r.input.dodgeHeld = false
        var t = 0
        while (t++ < 600 && !r.out.plays.contains(SfxBank.DOWN_THUD)) r.steps(1)
        val n = r.out.names()
        assertTrue("瞬移：$n", n.contains("TP_DEPART") && n.contains("TP_ARRIVE"))
        assertTrue("被击倒摔地：$n", n.contains("DOWN_THUD"))
        val i = n.indexOf("DOWN_THUD")
        val o = r.out
        val same = n.indices.filter { (n[it] == "LAND_LIGHT" || n[it] == "LAND_HEAVY") && o.playTicks[it] == o.playTicks[i] && o.pans[it] == o.pans[i] }
        assertTrue("同一个人同一次落地不叠普通落地：$n", same.isEmpty())
    }

    /** 简易领域（v0.17）：开圈、挡两轮被削、第三轮削光碎掉；被中和的斩击只有轻切口，不出受击声。 */
    @Test
    fun simpleDomainSounds() {
        val cfg = BattleConfig().also { Difficulty.URAUME.configure(it); it.sukunaAi = false; it.gojoMaxHp = 100_000f }
        val r = Rig(cfg)
        r.world.gojo.x = 380f
        r.world.sukuna.x = 860f
        r.steps(5)
        r.world.sukunaIntent.shrine = true
        r.steps(com.shinjuku.showdown.core.Tuning.SHRINE_SIGN + 2)
        assertTrue(r.world.cutscene)
        assertEquals("过场里没声音", 0, r.out.playsInCutscene)
        r.world.endCutscene(r.world.castId)
        r.sfx.update(r.world)
        r.input.jump = true; r.input.jumpHeld = true
        r.steps(com.shinjuku.showdown.core.Tuning.SIMPLE_DOMAIN_HOLD)
        r.input.jumpHeld = false
        r.steps(com.shinjuku.showdown.core.Tuning.SHRINE_ROUND * 3 + 2)
        val n = r.out.names()
        assertEquals("开圈一次：$n", 1, n.count { it == "SIMPLE_OPEN" })
        assertEquals("被削两层：$n", 2, n.count { it == "SIMPLE_ERODE" })
        assertEquals("第三轮碎掉：$n", 1, n.count { it == "SIMPLE_BREAK" })
        assertFalse("挡下的斩击不出受击声：$n", n.contains("SHRINE_SLASH_HIT"))
    }

    /** 宿傩的过场里长按反制：整个过场（换片前后）一声都没有，放完以后同时响两边的展开音、对撞的循环。 */
    @Test
    fun counterClashIsSilentUntilBothOpen() {
        val cfg = BattleConfig().also { Difficulty.URAUME.configure(it); it.sukunaAi = false }
        val r = Rig(cfg)
        r.world.gojo.x = 380f
        r.world.sukuna.x = 860f
        r.steps(5)
        r.world.sukunaIntent.shrine = true
        r.steps(com.shinjuku.showdown.core.Tuning.SHRINE_SIGN + 2)
        assertTrue(r.world.tryCounterDomain(r.world.castId))
        r.steps(10)
        assertEquals(0, r.out.playsInCutscene)
        assertEquals(0, r.out.loopsInCutscene)
        r.world.endCutscene(r.world.castId)
        r.sfx.setPaused(false)
        r.sfx.update(r.world)
        r.steps(2)
        val n = r.out.names()
        assertTrue("两边的展开音：$n", n.contains("VOID_OPEN") && n.contains("SHRINE_OPEN"))
        assertEquals("对撞的循环", SfxBank.CLASH_LOOP, r.out.loops[1])
    }

    /** 无量空处：过场期间一声都没有（全停），视频结束后才有展开音，困住宿傩时是循环音。 */
    @Test
    fun domainCutsceneIsSilent() {
        val r = Rig()
        r.world.gojo.x = 400f
        r.world.sukuna.x = 700f
        r.steps(5)
        r.input.domain = true
        var n = 0
        while (!r.world.cutscene && n++ < 30) r.steps(1)
        assertTrue("进了过场", r.world.cutscene)
        val stops = r.out.stopAlls
        r.steps(1) // 过场里世界不动，导演也不出声
        assertTrue("过场开始时全停", stops >= 1)
        assertEquals(0, r.out.playsInCutscene)
        assertEquals(0, r.out.loopsInCutscene)
        r.world.endCutscene(r.world.castId)
        r.sfx.setPaused(false)
        r.sfx.update(r.world)
        assertTrue("视频结束后展开音：${r.out.names()}", r.out.names().contains("VOID_OPEN"))
        r.steps(10)
        assertEquals("宿傩被困住：无量空处的循环", SfxBank.VOID_LOOP, r.out.loops[1])
    }

    private class Rng(var s: Long) {
        fun next(): Double {
            s = s * 6364136223846793005L + 1442695040888963407L
            return (s ushr 11).toDouble() / (1L shl 53).toDouble()
        }
    }

    private fun randomInput(rng: Rng, input: TickInput, tick: Int, move: FloatArray) {
        if (tick % 60 == 0) move[0] = floatArrayOf(-1f, 0f, 1f, 1f, -1f)[(rng.next() * 5).toInt()]
        input.moveX = move[0]
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
    }

    /** 史上最强、里梅的宿傩对乱按的五条悟各打两分钟：不刷屏、过场没声音、用到的音效够多。 */
    @Test
    fun randomFightsStayCleanAndVaried() {
        val used = HashSet<Int>()
        for (d in listOf(Difficulty.STRONGEST, Difficulty.URAUME, Difficulty.MIWA)) {
            for (seed in 0 until 3) {
                val cfg = BattleConfig().also { d.configure(it); it.seed = seed.toLong() }
                val r = Rig(cfg)
                val rng = Rng(seed * 31L + d.ordinal)
                val move = FloatArray(1)
                for (tick in 0 until 120 * 120) {
                    randomInput(rng, r.input, tick, move)
                    r.steps(1)
                    if (r.world.cutscene) {
                        r.world.endCutscene(r.world.castId)
                        r.sfx.update(r.world)
                    }
                    if (r.world.outcome != World.OUTCOME_NONE && r.world.tick - r.world.outcomeTick > 200) break
                }
                assertEquals("过场里没有单发", 0, r.out.playsInCutscene)
                assertEquals("过场里没有循环", 0, r.out.loopsInCutscene)
                // 任意 1 秒（120 tick）里最多 24 个单发
                val t = r.out.playTicks
                var j = 0
                for (i in t.indices) {
                    while (t[i] - t[j] >= 120) j++
                    assertTrue("${d.name} 种子 $seed：1 秒里 ${i - j + 1} 个音", i - j + 1 <= 24)
                }
                used.addAll(r.out.plays)
            }
        }
        val usedNames = used.map { names[it] }
        for (must in listOf("SUKUNA_SWING", "SLASH_DISMANTLE", "INFINITY_BLOCK", "RED_FIRE", "PURPLE_FIRE", "BLUE_LAUNCH", "HIT_LIGHT", "DODGE", "JUMP"))
            assertTrue("用到了 $must：$usedNames", usedNames.contains(must))
        assertTrue("用到的音效种类：${used.size}", used.size >= 25)
    }

    /** 覆盖面（不判对错，只记录；设置 SFX_COVERAGE=1 才跑）：四个难度各 20 局乱按，每个音效响了几次，写到 app/build/sfxcoverage.txt。 */
    @Test
    fun coverageReport() {
        org.junit.Assume.assumeTrue(System.getenv("SFX_COVERAGE") != null)
        val count = IntArray(SfxBank.COUNT)
        for (d in Difficulty.values()) {
            for (seed in 0 until 20) {
                val cfg = BattleConfig().also { d.configure(it); it.seed = seed.toLong() }
                val r = Rig(cfg)
                val rng = Rng(seed * 131L + d.ordinal)
                val move = FloatArray(1)
                for (tick in 0 until 120 * 150) {
                    randomInput(rng, r.input, tick, move)
                    if (rng.next() < 0.004) { r.input.redHeld = true }
                    r.steps(1)
                    if (r.world.cutscene) { r.world.endCutscene(r.world.castId); r.sfx.update(r.world) }
                    if (r.world.outcome != World.OUTCOME_NONE && r.world.tick - r.world.outcomeTick > 200) break
                }
                for (c in r.out.plays) count[c]++
                for (c in r.out.loopStarts) count[c]++
            }
        }
        java.io.File("build/sfxcoverage.txt").writeText((0 until SfxBank.COUNT).joinToString("\n") { "${names[it]} ${count[it]}" })
    }

    /** 音效只读世界：开着导演和不开，同样的输入每 tick 的结果完全一样。 */
    @Test
    fun directorDoesNotChangeTheFight() {
        val cfg1 = BattleConfig().also { Difficulty.STRONGEST.configure(it); it.seed = 5 }
        val cfg2 = BattleConfig().also { Difficulty.STRONGEST.configure(it); it.seed = 5 }
        val a = World(cfg1)
        val b = World(cfg2)
        val out = FakeOut { a }
        val sfx = SfxDirector(out)
        val ia = TickInput()
        val ib = TickInput()
        val rngA = Rng(9)
        val rngB = Rng(9)
        val ma = FloatArray(1)
        val mb = FloatArray(1)
        for (tick in 0 until 120 * 60) {
            randomInput(rngA, ia, tick, ma)
            randomInput(rngB, ib, tick, mb)
            a.step(ia); sfx.update(a); ia.clearEdges()
            b.step(ib); ib.clearEdges()
            if (a.cutscene) { a.endCutscene(a.castId); sfx.update(a) }
            if (b.cutscene) b.endCutscene(b.castId)
            assertEquals(b.gojo.x, a.gojo.x, 0f)
            assertEquals(b.sukuna.x, a.sukuna.x, 0f)
            assertEquals(b.sukuna.hpMilli, a.sukuna.hpMilli)
            assertEquals(b.gojo.hpMilli, a.gojo.hpMilli)
        }
        assertTrue("真的出过声", out.plays.size > 50)
    }

    /** 导演每 tick 零分配（JIT 预热以后；HotSpot 上量，ART 上的分配另靠代码审查）。 */
    @Test
    fun directorDoesNotAllocate() {
        val mxb = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val am = Class.forName("com.sun.management.ThreadMXBean").getMethod("getCurrentThreadAllocatedBytes")
        fun alloc(): Long = am.invoke(mxb) as Long
        var base = Long.MAX_VALUE
        repeat(50) { val x0 = alloc(); base = minOf(base, alloc() - x0) }
        val cfg = BattleConfig().also { Difficulty.STRONGEST.configure(it); it.seed = 3 }
        val w = World(cfg)
        val out = object : SfxOut {
            override fun play(cue: Int, variant: Int, pan: Float, gain: Float, rate: Float, priority: Int) = 1
            override fun stop(handle: Int) {}
            override fun loop(slot: Int, cue: Int, pan: Float, gain: Float) {}
            override fun stopLoop(slot: Int) {}
            override fun stopAll() {}
        }
        val sfx = SfxDirector(out)
        val input = TickInput()
        val rng = Rng(4)
        val move = FloatArray(1)
        var bytes = 0L
        for (tick in 0 until 120 * 90) {
            randomInput(rng, input, tick, move)
            w.step(input)
            val a0 = alloc()
            sfx.update(w)
            if (tick > 120 * 30) bytes += alloc() - a0 - base
            input.clearEdges()
            if (w.cutscene) w.endCutscene(w.castId)
        }
        assertEquals("导演每 tick 零分配", 0L, bytes)
    }
}
