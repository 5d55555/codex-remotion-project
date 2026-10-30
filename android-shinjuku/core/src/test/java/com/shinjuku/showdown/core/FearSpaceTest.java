package com.shinjuku.showdown.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 宿傩的恐惧和空间斩（v0.11）。 */
public class FearSpaceTest {

    private static void run(World w, TickInput in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            w.step(in);
            in.clearEdges();
        }
    }

    /** 里梅（有空间斩），宿傩不开 AI（意图手动填）。 */
    private static World manual(int level) {
        BattleConfig c = new BattleConfig();
        c.sukunaDifficulty(level);
        c.sukunaAi = false;
        c.gojoMaxHp = 100_000;
        return new World(c);
    }

    /** 打满 20 秒、宿傩剩 20% 血：空间斩的硬条件都满足了。 */
    private static void ready(World w, TickInput in) {
        w.gojo.x = 100;
        w.sukuna.x = 1100;
        run(w, in, Tuning.SPACE_MIN_TICKS);
        w.sukuna.hpMilli = w.sukuna.maxHpMilli / 5;
    }

    private static int count(World w, long from, int type, int value) {
        int n = 0;
        for (long i = from; i < w.events.written; i++) {
            int k = (int) (i & Events.MASK);
            if (w.events.type[k] == type && w.events.value[k] == value) n++;
        }
        return n;
    }

    /** 硬条件：没打满 20 秒、血量高于 25%、三轮（没有御厨子），想放也放不出来。 */
    @Test
    public void spaceSlashNeedsTwentySecondsLowHpAndShrine() {
        World w = manual(1);
        TickInput in = new TickInput();
        w.sukuna.hpMilli = w.sukuna.maxHpMilli / 5;
        w.sukunaIntent.space = true;
        run(w, in, 2);
        assertFalse("不到 20 秒", w.spaceUsed);

        World h = manual(1);
        ready(h, in);
        h.sukuna.hpMilli = h.sukuna.maxHpMilli / 2;
        h.sukunaIntent.space = true;
        run(h, in, 2);
        assertFalse("血量还多", h.spaceUsed);

        World m = manual(0);
        ready(m, in);
        m.sukunaIntent.space = true;
        run(m, in, 2);
        assertFalse("三轮没有", m.spaceUsed);
        assertEquals(Fighter.FREE, m.sukuna.state);
    }

    /** 斩中：破无限，五条悟剩多少血都直接斩掉，判负。 */
    @Test
    public void spaceSlashKillsThroughInfinity() {
        World w = manual(1);
        TickInput in = new TickInput();
        ready(w, in);
        w.gojo.x = 600;
        long seen = w.events.written;
        w.sukunaIntent.space = true;
        run(w, in, 1);
        assertEquals(Fighter.S_SPACE, w.sukuna.state);
        assertTrue(w.spaceUsed);
        run(w, in, Tuning.SPACE_WINDUP + 2);
        assertEquals(0, w.gojo.hpMilli);
        assertEquals(Fighter.KO, w.gojo.state);
        assertEquals(World.OUTCOME_LOSE, w.outcome);
        assertEquals(1, count(w, seen, Events.SPACE, 3));
    }

    /** 掐着时间跳过斩带：没斩到，宿傩力竭 3 秒（不能行动），一局只有一次。 */
    @Test
    public void jumpingOverMeansExhaustion() {
        World w = manual(1);
        TickInput in = new TickInput();
        ready(w, in);
        w.gojo.x = 600;
        long hp = w.gojo.hpMilli;
        w.sukunaIntent.space = true;
        run(w, in, Tuning.SPACE_WINDUP - 30);
        in.jump = true;
        run(w, in, 34);
        assertEquals("跳过去了", hp, w.gojo.hpMilli);
        assertEquals(Fighter.S_EXHAUSTED, w.sukuna.state);
        assertFalse(w.sukunaCanAct());
        w.sukunaIntent.melee = true;
        run(w, in, Tuning.SPACE_EXHAUST - 10);
        assertEquals("力竭时什么都做不了", Fighter.S_EXHAUSTED, w.sukuna.state);
        run(w, in, 20);
        assertTrue(w.sukunaCanAct());
        w.sukunaIntent.space = true;
        run(w, in, 2);
        assertTrue("一局一次", w.sukuna.state != Fighter.S_SPACE);
    }

    /** 斩出去的那一刻闪避（无敌）：一样躲开；锁定以后绕到他背后也斩不到。 */
    @Test
    public void dodgeOrGettingBehindAfterTheLock() {
        World w = manual(1);
        TickInput in = new TickInput();
        ready(w, in);
        w.gojo.x = 600;
        w.gojo.facing = -1;
        w.sukunaIntent.space = true;
        run(w, in, Tuning.SPACE_WINDUP - 4);
        in.dodge = true;
        run(w, in, 8);
        assertTrue(w.gojo.hpMilli > 0);
        assertEquals(Fighter.S_EXHAUSTED, w.sukuna.state);

        World b = manual(1);
        ready(b, in);
        b.gojo.x = 900;
        b.sukunaIntent.space = true;
        run(b, in, Tuning.SPACE_LOCK + 2);
        b.gojo.x = 1200; // 锁定以后才绕到背后：斩带朝左，碰不到
        run(b, in, Tuning.SPACE_WINDUP);
        assertTrue(b.gojo.hpMilli > 0);
        assertEquals(Fighter.S_EXHAUSTED, b.sukuna.state);
    }

    /** 起手时挨到任何伤害就断（不用有硬直）：一样力竭。 */
    @Test
    public void anyDamageBreaksTheWindup() {
        World w = manual(1);
        TickInput in = new TickInput();
        ready(w, in);
        w.gojo.x = 800;
        w.gojo.facing = 1;
        long seen = w.events.written;
        w.sukunaIntent.space = true;
        run(w, in, 10);
        in.red = true;
        run(w, in, 40);
        assertEquals(1, count(w, seen, Events.SPACE, -1));
        assertEquals(Fighter.S_EXHAUSTED, w.sukuna.state);
        run(w, in, Tuning.SPACE_WINDUP);
        assertTrue(w.gojo.hpMilli > 0);
    }

    /** 恐惧的结算：多挨了涨、少挨了降（15 血以内算估得准），一次最多涨 24、降 12；60 以上惊惧，45 以下才平复。 */
    @Test
    public void fearFollowsPredictionError() {
        BattleConfig c = new BattleConfig();
        c.sukunaDifficulty(1);
        World w = new World(c);
        SukunaAi ai = w.ai();
        assertEquals(Tuning.FEAR_START, ai.fear());
        ai.settleFear(200, 500);
        assertEquals(Tuning.FEAR_START + Math.round(c.ai.fearUp * 285), ai.fear());
        int f = ai.fear();
        ai.settleFear(200, 210);
        assertEquals("估得准", f, ai.fear());
        ai.settleFear(500, 200);
        assertEquals(f - Math.round(c.ai.fearDown * 285), ai.fear());
        ai.settleFear(0, 3500);
        assertEquals("最多涨 24", f - Math.round(c.ai.fearDown * 285) + 2400, ai.fear());
        for (int i = 0; i < 10; i++) ai.settleFear(0, 3500);
        assertEquals(Tuning.FEAR_MAX, ai.fear());
        for (int i = 0; i < 100; i++) ai.settleFear(3000, 0);
        assertEquals(0, ai.fear());
    }

    /** 窗口首尾相接：一局里每个窗口“实际挨的”加起来就是宿傩挨的全部（每个窗口最多差 1 血的取整），不漏结算那一 tick 的伤害。 */
    @Test
    public void fearWindowsCoverEveryHit() {
        BattleConfig c = new BattleConfig();
        c.sukunaDifficulty(1);
        World w = new World(c);
        GojoBot bot = new GojoBot(GojoBot.STANDARD, 3);
        TickInput in = new TickInput();
        long sum = 0;
        int seen = 0;
        while (w.fightTicks < 60 * Tuning.HZ && w.outcome == World.OUTCOME_NONE) {
            long before = w.damageTaken[Fighter.SUKUNA];
            bot.fill(w, in);
            w.step(in);
            if (w.cutscene) w.endCutscene(w.castId);
            SukunaAi ai = w.ai();
            if (ai.windows != seen) {
                seen = ai.windows;
                sum += ai.lastActual;
                assertTrue("没漏：" + sum + " / " + before / 1000, sum <= before / 1000 && sum >= before / 1000 - seen);
            }
        }
        assertTrue(seen > 10);
    }

    /** 被吓破胆（恐惧 60 以上）：连连后退；没怎么挨打时恐惧往下走。 */
    @Test
    public void panicRetreatsAndCalmsDown() {
        BattleConfig c = new BattleConfig();
        c.sukunaDifficulty(0);
        c.gojoMaxHp = 100_000;
        World w = new World(c);
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 700;
        for (int i = 0; i < 4; i++) w.ai().settleFear(0, 3500);
        assertTrue(w.ai().fear() >= Tuning.FEAR_PANIC);
        float d0 = Math.abs(w.sukuna.x - w.gojo.x);
        run(w, in, 90);
        assertTrue("往后退：" + d0 + " → " + Math.abs(w.sukuna.x - w.gojo.x), Math.abs(w.sukuna.x - w.gojo.x) > d0 + 60);
        int f = w.ai().fear();
        run(w, in, 6 * Tuning.HZ);
        assertTrue("没挨打：恐惧往下走", w.ai().fear() < f);
    }

    /** AI：恐惧低、打了 20 秒以上、血量 25% 以下、离得开，会放空间斩（五条悟站着不动就斩死他）。 */
    @Test
    public void aiSlashesWhenCalmLateAndLow() {
        BattleConfig c = new BattleConfig();
        c.sukunaDifficulty(1);
        c.trueForm = false; // 不让它先变原身
        World w = new World(c);
        TickInput in = new TickInput();
        w.gojo.x = 60;
        w.sukuna.x = 600;
        w.fightTicks = Tuning.SPACE_MIN_TICKS;
        w.sukuna.hpMilli = w.sukuna.maxHpMilli / 5;
        for (int i = 0; i < 120 && !w.spaceUsed && w.outcome == World.OUTCOME_NONE; i++) {
            w.gojo.hpMilli = w.gojo.maxHpMilli;
            w.sukuna.hpMilli = Math.min(w.sukuna.hpMilli, w.sukuna.maxHpMilli / 5); // 反转术式回上去的不算
            run(w, in, Tuning.HZ / 2);
            if (Math.abs(w.sukuna.x - w.gojo.x) < 300) w.sukuna.x = Math.min(1200, w.gojo.x + 500); // 一直离得开
        }
        assertTrue("放了空间斩", w.spaceUsed);
    }
}
