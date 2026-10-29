package com.shinjuku.showdown

import com.shinjuku.showdown.core.TickInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 按键事件环：按时间顺序取，不合并，同一 tick 里的点按保留。 */
class ControlsTest {
    private val actions: Controls.Actions = object : Controls.Actions {
        override fun back() {}
        override fun toggleMusic() {}
        override fun toggleStats() {}
    }
    private val c = Controls(actions)
    private val input = TickInput()

    private fun tick(limit: Long = Long.MAX_VALUE): TickInput {
        input.clearEdges()
        c.sample(input, limit)
        return input
    }

    @Test
    fun tapInsideOneTickKeepsBothEdges() {
        c.push(Controls.BLUE, true, 10)
        c.push(Controls.BLUE, false, 12)
        val t = tick()
        assertTrue(t.blueDown)
        assertTrue(t.blueUp)
        assertFalse(t.blueHeld)
    }

    @Test
    fun tapThenHoldIsNotMerged() {
        c.push(Controls.BLUE, true, 10)
        c.push(Controls.BLUE, false, 11)
        c.push(Controls.BLUE, true, 12)
        val a = tick()
        assertTrue(a.blueDown && a.blueUp && !a.blueHeld)
        val b = tick()
        assertTrue("第二次按下顺延到下一个 tick", b.blueDown)
        assertFalse(b.blueUp)
        assertTrue(b.blueHeld)
    }

    @Test
    fun repeatedPressesSpreadOverTicks() {
        repeat(3) { c.push(Controls.MELEE, true, 10L + it); c.push(Controls.MELEE, false, 10L + it) }
        var n = 0
        repeat(5) { if (tick().melee) n++ }
        assertEquals(3, n)
    }

    @Test
    fun eventsWaitForTheirTick() {
        c.push(Controls.RED, true, 100) // 100 ms
        assertFalse(tick(50_000_000L).red)
        assertTrue(tick(100_000_000L).red)
    }

    @Test
    fun holdsAreReportedForMeleeRedPurple() {
        c.push(Controls.MELEE, true, 10)
        c.push(Controls.RED, true, 10)
        c.push(Controls.PURPLE, true, 10)
        val a = tick()
        assertTrue(a.melee && a.meleeHeld && a.red && a.redHeld && a.purple && a.purpleHeld)
        val b = tick()
        assertTrue("按住不放：下一个 tick 还是按着，但没有新的按下", b.meleeHeld && b.redHeld && b.purpleHeld && !b.melee && !b.red && !b.purple)
        c.push(Controls.RED, false, 20)
        val d = tick()
        assertFalse(d.redHeld)
        assertTrue(d.meleeHeld && d.purpleHeld)
    }

    @Test
    fun domainIsAnEdgeAndPendingEventsCanBeDiscarded() {
        c.push(Controls.DOMAIN, true, 10)
        c.push(Controls.DOMAIN, false, 11)
        assertTrue(tick().domain)
        assertFalse(tick().domain)
        // 过场期间积压的按键：恢复时全丢掉
        c.push(Controls.MELEE, true, 30)
        c.push(Controls.PURPLE, true, 31)
        c.discardPending()
        val t = tick()
        assertFalse(t.melee || t.purple || t.meleeHeld || t.purpleHeld)
    }
}
