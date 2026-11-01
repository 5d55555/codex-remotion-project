package com.shinjuku.showdown.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v0.14：AI 走位的顺滑。宿傩、魔虚罗自己走的速度有加减速、不跳变；不急的变向要先走完一小段，不一抽一抽；
 * 起跳一蹬就是全速。五条悟是玩家，照旧即按即走。
 */
public class AiMotionTest {
    private static final float WALK_STEP = Tuning.S_WALK_DECEL * 2 * Tuning.DT + 0.01f; // 一个 tick 最多变这么多（超速刹车是两倍）

    /** 四个难度对标准脚本各打 20 局：能自己走的时候（自由、在地上、没挨打、没被控、没贴墙），速度每 tick 的变化都不超过一步刹车。 */
    @Test
    public void sukunaWalkSpeedNeverJumps() {
        for (int level = 0; level < 4; level++) {
            for (int seed = 0; seed < 20; seed++) {
                BattleConfig c = new BattleConfig();
                c.sukunaDifficulty(level);
                c.seed = seed;
                World w = new World(c);
                GojoBot bot = new GojoBot(GojoBot.STANDARD, seed);
                TickInput in = new TickInput();
                int lastFight = -1;
                boolean had = false;
                float lastVx = 0;
                while (w.fightTicks < 60 * Tuning.HZ && w.outcome == World.OUTCOME_NONE) {
                    bot.fill(w, in);
                    w.step(in);
                    if (w.cutscene) w.endCutscene(w.castId);
                    if (w.fightTicks == lastFight) continue;
                    lastFight = w.fightTicks;
                    Fighter s = w.sukuna;
                    boolean walkable = AiSmoothness.canWalk(w, s) && w.sStagger == 0
                            && s.x > s.halfW + 1 && s.x < Tuning.WIDTH - s.halfW - 1;
                    if (walkable && had) {
                        assertTrue("难度 " + level + " 种子 " + seed + " 第 " + w.fightTicks + " tick 速度从 " + lastVx + " 跳到 " + s.vx,
                                Math.abs(s.vx - lastVx) <= WALK_STEP);
                    }
                    had = walkable;
                    lastVx = s.vx;
                }
            }
        }
    }

    /** 史上最强对标准脚本：走不到 0.1 秒就停或掉头的“碎步”，每分钟可走时间里不超过 60 次（v0.13 是 170 次）。 */
    @Test
    public void strongestSukunaDoesNotJitter() {
        AiSmoothness.Track t = new AiSmoothness.Track();
        for (int seed = 0; seed < 60; seed++) AiSmoothness.run(3, seed, t, null, null);
        double min = t.ticks / (60.0 * Tuning.HZ);
        assertTrue("样本够多：" + min, min > 0.5);
        assertTrue("碎步每分钟 " + t.shortRuns / min, t.shortRuns / min < 60);
        assertTrue("来回折返每分钟 " + t.reversals / min, t.reversals / min < 70);
        assertTrue("速度突变每分钟 " + t.jolts / min, t.jolts / min < 5);
    }

    /** 宿傩从站着开始走：头几个 tick 逐渐加速，0.07 秒左右到全速；掉头时先减到 0 再加速，不瞬间反向。 */
    @Test
    public void sukunaAcceleratesAndTurnsSmoothly() {
        BattleConfig c = new BattleConfig();
        c.sukunaAi = false;
        World w = new World(c);
        TickInput in = new TickInput();
        w.gojo.x = 200;
        w.sukuna.x = 900;
        w.sukunaIntent.moveX = -1;
        w.step(in);
        assertTrue("第一个 tick 不是全速：" + w.sukuna.vx, w.sukuna.vx < 0 && w.sukuna.vx > -Tuning.SUKUNA_SPEED * 0.3f);
        for (int i = 0; i < 9; i++) {
            w.sukunaIntent.moveX = -1;
            w.step(in);
        }
        assertEquals(-Tuning.SUKUNA_SPEED, w.sukuna.vx, 0.01f);
        w.sukunaIntent.moveX = 1;
        w.step(in);
        assertTrue("掉头的第一个 tick 还在往回走：" + w.sukuna.vx, w.sukuna.vx < -Tuning.SUKUNA_SPEED * 0.6f);
        float prev = w.sukuna.vx;
        for (int i = 0; i < 16; i++) {
            w.sukunaIntent.moveX = 1;
            w.step(in);
            assertTrue("每 tick 只变一点：" + prev + " → " + w.sukuna.vx, Math.abs(w.sukuna.vx - prev) <= WALK_STEP);
            prev = w.sukuna.vx;
        }
        assertEquals(Tuning.SUKUNA_SPEED, w.sukuna.vx, 0.01f);
    }

    /** 起跳一蹬就是全速（墙边从五条悟头上越过去要这一下）。 */
    @Test
    public void sukunaJumpTakesOffAtFullSpeed() {
        BattleConfig c = new BattleConfig();
        c.sukunaAi = false;
        World w = new World(c);
        TickInput in = new TickInput();
        w.gojo.x = 200;
        w.sukuna.x = 900;
        w.sukunaIntent.moveX = -1;
        w.sukunaIntent.jump = true;
        w.step(in);
        assertEquals(-Tuning.SUKUNA_SPEED, w.sukuna.vx, 0.01f);
        assertTrue(!w.sukuna.onGround);
    }
}
