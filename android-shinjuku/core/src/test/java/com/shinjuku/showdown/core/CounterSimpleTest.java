package com.shinjuku.showdown.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v0.17：宿傩的领域展开过场里长按反制（两边同时展开、直接对撞），以及伏魔御厨子里的简易领域
 * （长按跳跃开，没有冷却；自己领域展开时立刻没；自己没领域时也能用；地上的圆，挡三轮必中后被削掉）。宿傩不开 AI。
 */
public class CounterSimpleTest {

    private static void run(World w, TickInput in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            w.step(in);
            in.clearEdges();
        }
    }

    private static World world() {
        BattleConfig c = new BattleConfig();
        c.sukunaDifficulty(1);
        c.sukunaAi = false;
        c.gojoMaxHp = 100_000;
        World w = new World(c);
        w.gojo.x = 380;
        w.sukuna.x = 860;
        return w;
    }

    /** 结印 1 秒，进到宿傩的过场（不放完）。 */
    private static void toShrineCutscene(World w, TickInput in) {
        w.sukunaIntent.shrine = true;
        run(w, in, 1);
        run(w, in, Tuning.SHRINE_SIGN + 1);
        assertTrue(w.cutscene);
        assertEquals(World.CUTSCENE_SHRINE, w.cutsceneKind);
    }

    private static int count(World w, long from, int type, int value) {
        int n = 0;
        for (long i = from; i < w.events.written; i++) {
            int k = (int) (i & Events.MASK);
            if (w.events.type[k] == type && w.events.value[k] == value) n++;
        }
        return n;
    }

    /* ---------------- 反制 ---------------- */

    /** 宿傩的过场里长按：接受一次，castId 换新、过场换成对撞的那一段，世界照样停着、没有先开领域。放完一次建好两个领域并对撞。 */
    @Test
    public void counterOpensBothDomainsIntoClash() {
        World w = world();
        TickInput in = new TickInput();
        toShrineCutscene(w, in);
        int id = w.castId, t = w.tick;
        long sHp = w.sukuna.hpMilli, gHp = w.gojo.hpMilli;
        assertEquals(0, w.counterBlocker());
        assertTrue(w.tryCounterDomain(id));
        assertFalse("同一次只接受一次", w.tryCounterDomain(id));
        assertFalse(w.tryCounterDomain(w.castId));
        assertEquals(id + 1, w.castId);
        assertEquals(World.CUTSCENE_CLASH, w.cutsceneKind);
        assertTrue(w.cutscene);
        assertEquals(Fighter.DOMAIN_CAST, w.gojo.state);
        run(w, in, 30);
        assertEquals("对撞的过场里世界也停着", t, w.tick);
        assertEquals(0, w.shrineTicks);
        assertEquals(0, w.domainTicks);
        w.endCutscene(id); // 宿傩那段的旧回调：对不上，忽略
        assertTrue(w.cutscene);
        long seen = w.events.written;
        w.endCutscene(w.castId);
        assertFalse(w.cutscene);
        assertEquals(Tuning.SHRINE_DURATION, w.shrineTicks);
        assertEquals(Tuning.DOMAIN_DURATION, w.domainTicks);
        assertEquals(Tuning.SHRINE_CLASH, w.clashTicks);
        assertEquals(Fighter.FREE, w.gojo.state);
        assertFalse("对撞时双方必中抵消：宿傩不被困住", w.voidControl());
        assertTrue("宿傩照样能动", w.sukunaCanAct());
        assertEquals(1, count(w, seen, Events.SHRINE, Events.SHRINE_OPEN));
        assertEquals(1, count(w, seen, Events.SHRINE, Events.SHRINE_CLASH));
        assertEquals(sHp, w.sukuna.hpMilli);
        assertEquals(gHp, w.gojo.hpMilli);
        assertTrue("宿傩的领域照样算用掉了", w.shrineUsed);
        w.endCutscene(w.castId); // 重复通知：不再展开
        assertEquals(Tuning.SHRINE_DURATION, w.shrineTicks);
        // 2 秒内没拆掉御厨子：无量空处的结界被外面击碎，冷却 40 秒，御厨子接着斩
        run(w, in, Tuning.SHRINE_CLASH + 1);
        assertEquals(0, w.domainTicks);
        assertTrue(w.domainCd > 0);
        assertTrue(w.shrineTicks > 0);
    }

    /** 不反制：照旧只开伏魔御厨子。 */
    @Test
    public void withoutCounterOnlyShrineOpens() {
        World w = world();
        TickInput in = new TickInput();
        toShrineCutscene(w, in);
        w.endCutscene(w.castId);
        assertTrue(w.shrineTicks > 0);
        assertEquals(0, w.domainTicks);
        assertEquals(0, w.clashTicks);
    }

    /** 冷却中、倒下、被控、不是宿傩的过场都不能反制；提示给出原因。 */
    @Test
    public void counterNeedsReadyDomainAndFreeGojo() {
        World w = world();
        TickInput in = new TickInput();
        toShrineCutscene(w, in);
        w.domainCd = 100;
        assertEquals(1, w.counterBlocker());
        assertFalse(w.tryCounterDomain(w.castId));
        w.domainCd = 0;
        w.gojo.hitstun = 5;
        assertEquals(2, w.counterBlocker());
        assertFalse(w.tryCounterDomain(w.castId));
        w.gojo.hitstun = 0;
        assertFalse("ID 不对", w.tryCounterDomain(w.castId - 1));
        assertTrue(w.tryCounterDomain(w.castId));

        World v = world();
        in.domain = true;
        run(v, in, 2);
        assertTrue(v.cutscene);
        assertEquals("自己的过场里不能反制", 3, v.counterBlocker());
        assertFalse(v.tryCounterDomain(v.castId));
    }

    /** 念咒中反制：咒词照“松手”作废（锁定照旧），不在过场里发“断”的事件；瞬移连段这类固定动作里不能反制。 */
    @Test
    public void counterDropsChantButNotFixedMotion() {
        World w = world();
        TickInput in = new TickInput();
        toShrineCutscene(w, in);
        w.gojo.state = Fighter.BLUE_CHANT;
        w.chantPhase = 1;
        long seen = w.events.written;
        assertTrue(w.tryCounterDomain(w.castId));
        assertEquals(Fighter.DOMAIN_CAST, w.gojo.state);
        assertEquals(-1, w.chantPhase);
        assertTrue("打断的锁定照旧", w.blueFailLock > 0);
        assertEquals("过场里不出“断”", 0, count(w, seen, Events.CHANT, -1));

        World v = world();
        toShrineCutscene(v, in);
        v.gojo.state = Fighter.TP_BLUE;
        assertEquals(2, v.counterBlocker());
        assertFalse(v.tryCounterDomain(v.castId));
    }

    /* ---------------- 简易领域 ---------------- */

    private static World inShrine() {
        World w = world();
        TickInput in = new TickInput();
        toShrineCutscene(w, in);
        w.endCutscene(w.castId);
        assertTrue(w.shrineTicks > 0);
        return w;
    }

    private static void hold(World w, TickInput in, int ticks) {
        in.jump = true;
        in.jumpHeld = true;
        run(w, in, ticks);
    }

    /** 按住跳跃满 30 tick 开圈（29 tick 不开）；开圈不跳；松手也不跳。没开圈的松手就是普通的跳。 */
    @Test
    public void holdJumpOpensSimpleDomainShortPressJumps() {
        World w = inShrine();
        TickInput in = new TickInput();
        hold(w, in, Tuning.SIMPLE_DOMAIN_HOLD - 1);
        assertEquals(0, w.simpleDomainRounds);
        assertTrue("按着的时候没起跳", w.gojo.onGround);
        run(w, in, 1);
        assertEquals(Tuning.SIMPLE_DOMAIN_ROUNDS, w.simpleDomainRounds);
        assertEquals(w.gojo.x, w.simpleDomainX, 0.01f);
        in.jumpHeld = false;
        run(w, in, 3);
        assertTrue("长按开圈以后松手不跳", w.gojo.onGround);
        assertEquals(Tuning.SIMPLE_DOMAIN_ROUNDS, w.simpleDomainRounds);

        World j = inShrine();
        in.jump = true;
        in.jumpHeld = true;
        run(j, in, 5);
        in.jumpHeld = false;
        run(j, in, 2);
        assertFalse("短按松手就跳", j.gojo.onGround);
        assertEquals(0, j.simpleDomainRounds);

        World q = inShrine();
        in.jump = true;
        in.jumpHeld = false; // 同一 tick 按下又松开
        run(q, in, 2);
        assertFalse(q.gojo.onGround);
    }

    /** 敌方领域外：跳跃照旧按下就跳。 */
    @Test
    public void outsideShrineJumpIsInstant() {
        World w = world();
        TickInput in = new TickInput();
        in.jump = true;
        in.jumpHeld = true;
        run(w, in, 2);
        assertFalse(w.gojo.onGround);
        assertEquals(0, w.simpleDomainRounds);
    }

    /** 按着跳跃点体术：下砸苍拳优先，不开圈、不跳。 */
    @Test
    public void fistComboWinsOverSimpleDomain() {
        World w = inShrine();
        TickInput in = new TickInput();
        hold(w, in, 10);
        in.melee = true;
        run(w, in, 1);
        assertEquals(Fighter.FIST_WINDUP, w.gojo.state);
        run(w, in, Tuning.SIMPLE_DOMAIN_HOLD + 5);
        assertEquals(0, w.simpleDomainRounds);
    }

    /** 按着跳跃时在命中停顿里点了领域：领域照常展开，按着的跳跃作废，过场放完松手不会自己跳起来。 */
    @Test
    public void skillPressedDuringHitstopCancelsPendingJump() {
        World w = inShrine();
        TickInput in = new TickInput();
        w.domainCd = 0;
        hold(w, in, 5);
        w.hitstop = 3;
        in.domain = true;
        run(w, in, 1);
        run(w, in, 4);
        assertTrue("停顿结束后领域展开", w.cutscene);
        w.endCutscene(w.castId);
        in.jumpHeld = false;
        run(w, in, 3);
        assertTrue("没有自己跳起来", w.gojo.onGround);
    }

    /** 简易领域中和必中：三轮都不伤，第三轮后被削掉；第四轮照旧斩到。没有冷却：马上再按 30 tick 又能开。 */
    @Test
    public void simpleDomainBlocksThreeRoundsThenErodes() {
        World w = inShrine();
        TickInput in = new TickInput();
        w.shrineNext = Tuning.SHRINE_ROUND; // 第一轮在 60 tick 后
        hold(w, in, Tuning.SIMPLE_DOMAIN_HOLD);
        in.jumpHeld = false;
        long hp = w.gojo.hpMilli;
        long dealt = w.shrineDealt, seen = w.events.written;
        run(w, in, Tuning.SHRINE_ROUND * 3);
        assertEquals("三轮全挡", hp, w.gojo.hpMilli);
        assertEquals(dealt, w.shrineDealt);
        assertEquals(0, w.simpleDomainRounds);
        assertEquals(2, count(w, seen, Events.SIMPLE_DOMAIN, Events.SIMPLE_ERODE));
        assertEquals(1, count(w, seen, Events.SIMPLE_DOMAIN, Events.SIMPLE_CLOSE));
        run(w, in, Tuning.SHRINE_ROUND);
        assertTrue("第四轮没重开：斩到", w.gojo.hpMilli < hp);
        // 没有冷却：马上重开（开成的那一 tick 正好赶上下一轮也挡得住：这里错开它，好数层数）
        assertTrue(w.canStartSimpleDomain());
        run(w, in, 5);
        hold(w, in, Tuning.SIMPLE_DOMAIN_HOLD);
        assertEquals(Tuning.SIMPLE_DOMAIN_ROUNDS, w.simpleDomainRounds);
    }

    /** 无量空处冷却中、被击碎以后照样能开（用户定：自己没领域的时候还能用）。 */
    @Test
    public void simpleDomainWorksWhileOwnDomainOnCooldown() {
        World w = inShrine();
        TickInput in = new TickInput();
        w.domainCd = 4000;
        assertTrue(w.canStartSimpleDomain());
        hold(w, in, Tuning.SIMPLE_DOMAIN_HOLD);
        assertTrue(w.simpleDomainRounds > 0);
    }

    /** 自己领域展开开始时简易领域立刻没；走出圈、跳起来也没了。 */
    @Test
    public void ownDomainOrLeavingTheCircleEndsIt() {
        World w = inShrine();
        TickInput in = new TickInput();
        hold(w, in, Tuning.SIMPLE_DOMAIN_HOLD);
        in.jumpHeld = false;
        run(w, in, 1);
        assertTrue(w.simpleDomainRounds > 0);
        in.domain = true;
        run(w, in, 1);
        assertTrue("自己的过场开始了", w.cutscene);
        assertEquals("自己领域展开：简易领域立刻没", 0, w.simpleDomainRounds);

        World m = inShrine();
        hold(m, in, Tuning.SIMPLE_DOMAIN_HOLD);
        in.jumpHeld = false;
        in.moveX = 1;
        run(m, in, 60);
        in.moveX = 0;
        assertEquals("走出半径 120 的圈", 0, m.simpleDomainRounds);

        World j = inShrine();
        hold(j, in, Tuning.SIMPLE_DOMAIN_HOLD);
        in.jumpHeld = false;
        run(j, in, 1);
        in.jump = true;
        in.jumpHeld = false;
        run(j, in, 3);
        assertEquals("开着圈再按跳就是普通的跳：离地就没了", 0, j.simpleDomainRounds);
        assertFalse(j.gojo.onGround);
    }

    /** 敌方领域结束，圈跟着没；对撞中自己有领域时不能开。 */
    @Test
    public void endsWithShrineAndNotDuringClash() {
        World w = inShrine();
        TickInput in = new TickInput();
        hold(w, in, Tuning.SIMPLE_DOMAIN_HOLD);
        in.jumpHeld = false;
        run(w, in, Tuning.SHRINE_DURATION);
        assertEquals(0, w.shrineTicks);
        assertEquals(0, w.simpleDomainRounds);
        assertFalse(w.canStartSimpleDomain());

        World c = world();
        toShrineCutscene(c, in);
        assertTrue(c.tryCounterDomain(c.castId));
        c.endCutscene(c.castId);
        assertTrue(c.clashTicks > 0);
        assertFalse("对撞中自己有领域：不能叠开", c.canStartSimpleDomain());
    }
}
