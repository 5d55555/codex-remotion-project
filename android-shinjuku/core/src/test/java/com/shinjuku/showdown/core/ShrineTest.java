package com.shinjuku.showdown.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 宿傩的领域展开「伏魔御厨子」（v0.13）：结印、过场、必中斩击、崩解、和无量空处的对撞、术式烧断。宿傩不开 AI。 */
public class ShrineTest {

    private static void run(World w, TickInput in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            w.step(in);
            in.clearEdges();
        }
    }

    private static World world(int level) {
        BattleConfig c = new BattleConfig();
        c.sukunaDifficulty(level);
        c.sukunaAi = false;
        c.gojoMaxHp = 100_000;
        World w = new World(c);
        w.gojo.x = 380;
        w.sukuna.x = 860;
        return w;
    }

    private static int count(World w, long from, int value) {
        int n = 0;
        for (long i = from; i < w.events.written; i++) {
            int k = (int) (i & Events.MASK);
            if (w.events.type[k] == Events.SHRINE && w.events.value[k] == value) n++;
        }
        return n;
    }

    /** 结印 1 秒 → 过场（全场停住，放伏魔御厨子的视频）→ 放完展开。 */
    private static void open(World w, TickInput in) {
        w.sukunaIntent.shrine = true;
        run(w, in, 1);
        assertEquals(Fighter.S_DOMAIN_SIGN, w.sukuna.state);
        run(w, in, Tuning.SHRINE_SIGN + 1);
        assertTrue("进了过场", w.cutscene);
        assertEquals(World.CUTSCENE_SHRINE, w.cutsceneKind);
        int t = w.tick;
        run(w, in, 50);
        assertEquals("过场时全场停住", t, w.tick);
        w.endCutscene(w.castId);
        assertEquals(Tuning.SHRINE_DURATION, w.shrineTicks);
    }

    /** 三轮没有；里梅开了以后 6 秒里每 0.5 秒斩一下，无限挡不住：12 下 × 25 × 1.1。一局只能成功开一次。 */
    @Test
    public void shrineSlashesThroughInfinityTwelveTimes() {
        World m = world(0);
        TickInput in = new TickInput();
        m.sukunaIntent.shrine = true;
        run(m, in, 2);
        assertEquals("三轮没有领域", Fighter.FREE, m.sukuna.state);

        World w = world(1);
        long hp = w.gojo.hpMilli;
        long seen = w.events.written;
        open(w, in);
        run(w, in, Tuning.SHRINE_DURATION + 2);
        assertEquals(0, w.shrineTicks);
        assertEquals(12, count(w, seen, Events.SHRINE_SLASH));
        assertEquals(12 * Math.round(Tuning.SHRINE_DAMAGE_MILLI * 1.1), hp - w.gojo.hpMilli);
        assertEquals(1, count(w, seen, Events.SHRINE_END));
        assertTrue("术式烧断", w.shrineBurn > 0);
        run(w, in, Tuning.SHRINE_BURNOUT);
        w.sukunaIntent.shrine = true;
        run(w, in, 2);
        assertTrue("一局一次", w.sukuna.state != Fighter.S_DOMAIN_SIGN);
    }

    /** 结印时挨到任何伤害就断：不算用掉，10 秒后能再结；五条悟抢先开无量空处也会打断结印。 */
    @Test
    public void signBreaksOnAnyHitOrOnUnlimitedVoid() {
        World w = world(1);
        TickInput in = new TickInput();
        w.gojo.x = 600;
        w.gojo.facing = 1;
        long seen = w.events.written;
        w.sukunaIntent.shrine = true;
        run(w, in, 10);
        in.red = true;
        run(w, in, 40);
        assertEquals(1, count(w, seen, Events.SHRINE_SIGN_BROKEN));
        assertFalse(w.shrineUsed);
        assertTrue(w.shrineRetry > 0);
        assertFalse(w.cutscene);

        World v = world(1);
        v.sukunaIntent.shrine = true;
        run(v, in, 10);
        in.domain = true;
        run(v, in, 1);
        v.endCutscene(v.castId);
        assertTrue(v.voidControl());
        assertTrue("结印被无量空处打断", v.sukuna.state != Fighter.S_DOMAIN_SIGN);
        assertFalse(v.shrineUsed);
    }

    /** 结印满的那一刻五条悟被打空：直接分胜负，不进过场（不会先放 13 秒视频，战后也不会展开领域）。 */
    @Test
    public void noCutsceneWhenTheFightEndsThatTick() {
        World w = world(1);
        TickInput in = new TickInput();
        w.sukunaIntent.shrine = true;
        run(w, in, Tuning.SHRINE_SIGN);
        assertEquals(Fighter.S_DOMAIN_SIGN, w.sukuna.state);
        w.gojo.hpMilli = 0;
        run(w, in, 2);
        assertFalse(w.cutscene);
        assertEquals(World.OUTCOME_LOSE, w.outcome);
        assertEquals(0, w.shrineTicks);
        w.endCutscene(w.castId);
        assertEquals("战后也不展开", 0, w.shrineTicks);
    }

    /** 闪避的无敌躲得开一轮；宿傩在硬直里那一轮作废。 */
    @Test
    public void dodgeAndHitstunMissRounds() {
        World w = world(1);
        TickInput in = new TickInput();
        long seen = w.events.written;
        open(w, in);
        run(w, in, Tuning.SHRINE_ROUND - 4);
        in.dodge = true;
        long hp = w.gojo.hpMilli;
        run(w, in, 8);
        assertEquals("闪开了这一轮", hp, w.gojo.hpMilli);
        w.sukuna.hitstun = 70; // 盖住下一轮
        run(w, in, Tuning.SHRINE_ROUND);
        assertEquals("宿傩硬直：这一轮作废", hp, w.gojo.hpMilli);
        int stifled = 0;
        for (long i = seen; i < w.events.written; i++) {
            int k = (int) (i & Events.MASK);
            if (w.events.type[k] == Events.SHRINE && w.events.value[k] == Events.SHRINE_SLASH && w.events.kind[k] == 2) stifled++;
        }
        assertEquals(1, stifled);
    }

    /** 把宿傩抓进瞬移连段：领域崩解，术式烧断（解用不了）。 */
    @Test
    public void teleportComboBreaksTheShrine() {
        World w = world(1);
        TickInput in = new TickInput();
        open(w, in);
        long seen = w.events.written;
        in.dodgeHeld = true;
        in.blueDown = in.blueUp = true;
        run(w, in, 1);
        in.dodgeHeld = false;
        run(w, in, Tuning.TP_WINDUP + 10);
        assertEquals(1, count(w, seen, Events.SHRINE_BROKEN));
        assertEquals(0, w.shrineTicks);
        assertTrue(w.shrineBurn > 0);
        run(w, in, 200);
        int cd = w.dismantleCd;
        w.sukuna.x = w.gojo.x + 200;
        w.sukunaIntent.dismantle = true;
        run(w, in, 2);
        assertEquals("烧断：解用不了", cd == 0 ? 0 : Math.max(0, cd - 2), w.dismantleCd);
    }

    /** 领域对撞：2 秒内必中抵消（不斩），拆不掉的话无量空处的结界被击碎（冷却 40 秒），御厨子接着斩。 */
    @Test
    public void clashLostShattersTheVoid() {
        World w = world(1);
        TickInput in = new TickInput();
        open(w, in);
        run(w, in, 30);
        long seen = w.events.written;
        in.domain = true;
        run(w, in, 1);
        w.endCutscene(w.castId);
        assertEquals(Tuning.SHRINE_CLASH, w.clashTicks);
        assertFalse("对撞时宿傩没被困住", w.voidControl());
        assertTrue(w.sukunaCanAct());
        long hp = w.gojo.hpMilli;
        run(w, in, Tuning.SHRINE_CLASH - 2);
        assertEquals("对撞时不斩", hp, w.gojo.hpMilli);
        run(w, in, 4);
        assertEquals(1, count(w, seen, Events.SHRINE_VOID_BROKEN));
        assertEquals(0, w.domainTicks);
        assertEquals(Tuning.DOMAIN_COOLDOWN, w.domainCd, 4);
        assertTrue(w.shrineTicks > 0);
        run(w, in, Tuning.SHRINE_ROUND + 2);
        assertTrue("御厨子接着斩", w.gojo.hpMilli < hp);
    }

    /** 对撞时把领域拆掉：无量空处赢了，马上困住宿傩。 */
    @Test
    public void clashWonVoidTakesOver() {
        World w = world(1);
        TickInput in = new TickInput();
        open(w, in);
        in.domain = true;
        run(w, in, 1);
        w.endCutscene(w.castId);
        assertTrue(w.clashTicks > 0);
        in.dodgeHeld = true;
        in.blueDown = in.blueUp = true;
        run(w, in, 1);
        in.dodgeHeld = false;
        run(w, in, Tuning.TP_WINDUP + 10);
        assertEquals(0, w.shrineTicks);
        assertEquals(0, w.clashTicks);
        assertTrue("无量空处接管", w.voidControl());
        assertTrue(w.domainTicks > 0);
    }
}
