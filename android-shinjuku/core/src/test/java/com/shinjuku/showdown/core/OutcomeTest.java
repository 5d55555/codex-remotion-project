package com.shinjuku.showdown.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 胜负（测试模式以外：宿傩打空胜、五条悟打空负）和五条悟的机动性（v0.10）。 */
public class OutcomeTest {

    private static void run(World w, TickInput in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            w.step(in);
            in.clearEdges();
        }
    }

    private static World difficulty(int level) {
        BattleConfig c = new BattleConfig();
        c.sukunaDifficulty(level);
        return new World(c);
    }

    /** 宿傩打空：胜，不再站起来；之后双方都不动。 */
    @Test
    public void sukunaEmptiedIsAWin() {
        World w = difficulty(0);
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 540;
        w.sukuna.hpMilli = 1000;
        in.melee = true;
        for (int i = 0; i < 60 && w.outcome == World.OUTCOME_NONE; i++) run(w, in, 1);
        assertEquals(World.OUTCOME_WIN, w.outcome);
        assertEquals(Fighter.KO, w.sukuna.state);
        run(w, in, 30); // 打到一半的那一拳收完
        assertEquals(Fighter.FREE, w.gojo.state);
        float gx = w.gojo.x;
        long gHp = w.gojo.hpMilli;
        in.moveX = 1;
        run(w, in, Tuning.KO_TICKS + 60);
        assertEquals("不会站起来", Fighter.KO, w.sukuna.state);
        assertEquals("五条悟也不再动", gx, w.gojo.x, 0.01f);
        assertEquals(gHp, w.gojo.hpMilli);
        assertEquals(World.OUTCOME_WIN, w.outcome);
    }

    /** 五条悟打空：负；宿傩（和 AI）不再出手。 */
    @Test
    public void gojoEmptiedIsALoss() {
        World w = difficulty(1);
        TickInput in = new TickInput();
        w.gojo.hpMilli = 1;
        w.sukuna.x = w.gojo.x + 60;
        w.sukunaIntent.dismantle = true;
        for (int i = 0; i < 600 && w.outcome == World.OUTCOME_NONE; i++) run(w, in, 1);
        // 解被无限挡住：等 AI 自己开展延打到他
        assertEquals(World.OUTCOME_LOSE, w.outcome);
        assertEquals(Fighter.KO, w.gojo.state);
        long sHp = w.sukuna.hpMilli;
        int amps = 0;
        long seen = w.events.written;
        run(w, in, Tuning.KO_TICKS + 120);
        for (long i = seen; i < w.events.written; i++) {
            int k = (int) (i & Events.MASK);
            if (w.events.type[k] == Events.AMP && w.events.value[k] > 0) amps++;
        }
        assertEquals("不会站起来", Fighter.KO, w.gojo.state);
        assertEquals("宿傩不再出招", 0, amps);
        assertEquals(sHp, w.sukuna.hpMilli);
    }

    /** 测试模式照旧：倒下过一会儿满血站起来，不分胜负。 */
    @Test
    public void trainingStillRevives() {
        World w = new World(new BattleConfig());
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 540;
        w.sukuna.hpMilli = 1000;
        in.melee = true;
        run(w, in, 30);
        assertEquals(Fighter.KO, w.sukuna.state);
        run(w, in, Tuning.KO_TICKS + 2);
        assertEquals(Fighter.FREE, w.sukuna.state);
        assertEquals(w.sukuna.maxHpMilli, w.sukuna.hpMilli);
        assertEquals(World.OUTCOME_NONE, w.outcome);
    }

    /** 茈·最大输出同时打空两个人：算赢。 */
    @Test
    public void bothEmptiedAtOnceIsAWin() {
        BattleConfig c = new BattleConfig();
        c.training = false;
        World w = new World(c);
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 700;
        w.gojo.hpMilli = Tuning.PURPLE_MAX_SELF_DAMAGE_MILLI;
        w.sukuna.hpMilli = Tuning.PURPLE_MAX_DAMAGE_MILLI;
        in.blueDown = in.blueHeld = true;
        in.red = in.redHeld = true;
        for (int i = 0; i < 600 && w.cine == World.CINE_NONE; i++) {
            run(w, in, 1);
            in.blueHeld = in.redHeld = true;
        }
        in.blueHeld = in.redHeld = false;
        while (w.cine != World.CINE_NONE) run(w, in, 1);
        assertEquals(Fighter.KO, w.gojo.state);
        assertEquals(Fighter.KO, w.sukuna.state);
        assertEquals(World.OUTCOME_WIN, w.outcome);
    }

    /** 分出胜负时手上按着的招不会被当成松手放出去；宿傩的反转术式也停下、不再回血。 */
    @Test
    public void endingStopsEverythingInProgress() {
        BattleConfig c = new BattleConfig();
        c.training = false;
        World w = new World(c);
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 900;
        in.red = in.redHeld = true;
        run(w, in, 4);
        assertEquals(Fighter.RED_HOLD, w.gojo.state);
        w.sukuna.hpMilli = 0;
        long seen = w.events.written;
        run(w, in, 1);
        assertEquals(World.OUTCOME_WIN, w.outcome);
        in.redHeld = false;
        run(w, in, 120);
        assertEquals(Fighter.FREE, w.gojo.state);
        assertEquals("赫没放出去（没进冷却）", 0, w.redCd);
        for (long i = seen; i < w.events.written; i++) {
            int k = (int) (i & Events.MASK);
            assertTrue("没有新的出招", w.events.type[k] != Events.CAST);
        }

        World r = new World(c);
        TickInput ri = new TickInput();
        r.gojo.x = 200;
        r.sukuna.x = 900;
        r.sukuna.hpMilli = r.sukuna.maxHpMilli / 2;
        r.sukunaIntent.rct = true;
        run(r, ri, Tuning.S_RCT_STARTUP + 10);
        assertEquals(Fighter.S_RCT, r.sukuna.state);
        r.gojo.hpMilli = 0;
        run(r, ri, 1);
        assertEquals(World.OUTCOME_LOSE, r.outcome);
        long hp = r.sukuna.hpMilli;
        run(r, ri, 240);
        assertEquals("反转术式停了", hp, r.sukuna.hpMilli);
    }

    /** 茈·最大输出炸死宿傩：炸到的那一 tick 就定胜负（结算等动画放完）。 */
    @Test
    public void maxPurpleDecidesAtTheBlast() {
        BattleConfig c = new BattleConfig();
        c.training = false;
        World w = new World(c);
        TickInput in = new TickInput();
        w.gojo.x = 100;
        w.sukuna.x = 1000;
        w.sukuna.hpMilli = 1000;
        in.blueDown = in.blueHeld = true;
        in.red = in.redHeld = true;
        for (int i = 0; i < 600 && w.cine == World.CINE_NONE; i++) {
            run(w, in, 1);
            in.blueHeld = in.redHeld = true;
        }
        in.blueHeld = in.redHeld = false;
        int start = w.tick;
        while (w.cine != World.CINE_NONE && w.outcome == World.OUTCOME_NONE) run(w, in, 1);
        assertEquals(World.OUTCOME_WIN, w.outcome);
        assertTrue("动画还在放", w.cine != World.CINE_NONE);
        assertEquals(Tuning.PMAX_HIT, w.outcomeTick - start, 2);
        while (w.cine != World.CINE_NONE) run(w, in, 1);
        assertEquals(Fighter.KO, w.sukuna.state);
    }

    /** 机动性：走 420 / 秒（比宿傩的 330 快）、闪 210、跳得比宿傩高。 */
    @Test
    public void gojoIsMoreMobile() {
        World w = new World(new BattleConfig());
        TickInput in = new TickInput();
        w.gojo.x = 200;
        w.sukuna.x = 1200;
        in.moveX = 1;
        run(w, in, Tuning.HZ);
        assertEquals(200 + Tuning.MOVE_SPEED, w.gojo.x, 6f);
        assertTrue(Tuning.MOVE_SPEED > Tuning.SUKUNA_SPEED);

        World d = new World(new BattleConfig());
        TickInput di = new TickInput();
        d.gojo.x = 200;
        d.sukuna.x = 1200;
        d.gojo.facing = 1;
        di.dodge = true;
        run(d, di, Tuning.DODGE_TICKS + 2);
        assertEquals(410, d.gojo.x, 12f);

        World j = new World(new BattleConfig());
        TickInput ji = new TickInput();
        j.sukuna.x = 1200;
        ji.jump = true;
        float top = 0;
        for (int i = 0; i < Tuning.HZ; i++) {
            run(j, ji, 1);
            top = Math.max(top, j.gojo.y);
        }
        float sukunaTop = Tuning.JUMP_SPEED * Tuning.JUMP_SPEED / (2 * Tuning.GRAVITY);
        assertTrue("跳得比宿傩高：" + top, top > sukunaTop * 1.3f);
    }
}
