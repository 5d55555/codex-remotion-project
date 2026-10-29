package com.shinjuku.showdown.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CombatTest {

    private static World world() {
        return new World(new BattleConfig());
    }

    private static void run(World w, TickInput in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            w.step(in);
            in.clearEdges();
        }
    }

    /** 黑闪：长期频率 15%，最多连续 31 次不出。 */
    @Test
    public void blackFlashIsPseudoRandomFifteenPercent() {
        CombatRng rng = new CombatRng(12345);
        BlackFlash bf = new BlackFlash();
        int n = 2_000_000, hits = 0, streak = 0, worst = 0;
        for (int i = 0; i < n; i++) {
            if (bf.roll(rng)) {
                hits++;
                streak = 0;
            } else {
                worst = Math.max(worst, ++streak);
            }
        }
        assertEquals(0.15, hits / (double) n, 0.002);
        assertTrue("连续不出 " + worst, worst <= 31);
    }

    /** 体术：每次命中 50，黑闪 350；只有命中才判定黑闪。 */
    @Test
    public void meleeDealsFiftyOrBlackFlash() {
        World w = world();
        TickInput in = new TickInput();
        int hits = 0, flashes = 0;
        for (int i = 0; i < 400; i++) {
            w.gojo.x = w.sukuna.x - 60;
            w.sukuna.vx = w.sukuna.vy = 0;
            w.sukuna.y = 0;
            w.sukuna.hpMilli = w.sukuna.maxHpMilli;
            long before = w.sukuna.hpMilli;
            in.melee = true;
            run(w, in, Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE + Tuning.MELEE_RECOVERY + 2);
            run(w, in, 12); // 等黑闪命中停顿结束
            long dealt = before - w.sukuna.hpMilli;
            if (dealt == Tuning.BLACK_FLASH_DAMAGE_MILLI) flashes++;
            else assertEquals(Tuning.MELEE_DAMAGE_MILLI, dealt);
            hits++;
        }
        assertTrue("黑闪 " + flashes + "/" + hits, flashes > 30 && flashes < 100);
    }

    /** 挥空不消耗黑闪的判定次数。 */
    @Test
    public void whiffDoesNotRollBlackFlash() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 100;
        w.sukuna.x = 1200;
        in.melee = true;
        run(w, in, 60);
        assertEquals(1, w.blackFlash.attempt());
        assertEquals(w.sukuna.maxHpMilli, w.sukuna.hpMilli);
    }

    /** 苍：落点离投出点不超过战场 30%，并且在战场内。 */
    @Test
    public void blueThrowNeverExceedsThirtyPercent() {
        float[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0.7f, 0.7f}, {-0.7f, 0.7f}, {0, 0}, {1, -1}};
        for (float[] d : dirs) {
            for (float gx : new float[]{30, 640, 1250}) {
                World w = world();
                TickInput in = new TickInput();
                w.gojo.x = gx;
                w.sukuna.x = 1250;
                in.moveX = d[0];
                in.moveY = d[1];
                in.blueDown = in.blueHeld = in.blueUp = true;
                run(w, in, Tuning.BLUE_WINDUP + 2);
                assertTrue(w.blue.active());
                float dx = w.blue.cx - w.blue.ox, dy = w.blue.cy - w.blue.oy;
                assertTrue((float) Math.sqrt(dx * dx + dy * dy) <= Tuning.BLUE_MAX_THROW + 0.01f);
                assertTrue(w.blue.cx >= 0 && w.blue.cx <= Tuning.WIDTH);
            }
        }
    }

    /** 苍的核心：每秒正好 100 血（按余数累计，不丢不多）。 */
    @Test
    public void blueCoreDealsExactlyHundredPerSecond() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 500;
        in.blueDown = in.blueHeld = in.blueUp = true;
        run(w, in, Tuning.BLUE_WINDUP + Tuning.BLUE_FLY + 2);
        assertEquals(BlueOrb.PULL, w.blue.phase);
        // 直接放进核心
        w.sukuna.captured = true;
        w.step(in);
        long before = w.sukuna.hpMilli;
        run(w, in, Tuning.HZ);
        assertEquals(Tuning.BLUE_MAX_DPS_MILLI, before - w.sukuna.hpMilli);
    }

    /** 苍：吸力把宿傩拉进核心；离中心越近伤害越高。 */
    @Test
    public void bluePullsTargetIntoCore() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 820;
        in.blueDown = in.blueHeld = in.blueUp = true;
        run(w, in, Tuning.BLUE_WINDUP + Tuning.BLUE_FLY + 2);
        float startDist = Math.abs(w.sukuna.x - w.blue.cx);
        assertTrue("一开始在范围内", startDist < Tuning.BLUE_RADIUS);
        long hp0 = w.sukuna.hpMilli;
        run(w, in, 30);
        long early = hp0 - w.sukuna.hpMilli;
        run(w, in, 120);
        assertTrue("被拉近了", Math.abs(w.sukuna.x - w.blue.cx) < startDist * 0.2f);
        long hp1 = w.sukuna.hpMilli;
        run(w, in, 30);
        long late = hp1 - w.sukuna.hpMilli;
        assertTrue("越近伤害越高 " + early + " < " + late, late > early);
    }

    /** 长按苍：范围和吸力翻倍，前摇更长。 */
    @Test
    public void chargedBlueDoublesRangeAndPull() {
        World w = world();
        TickInput in = new TickInput();
        in.blueDown = in.blueHeld = true;
        w.step(in);
        in.clearEdges();
        run(w, in, Tuning.BLUE_HOLD_THRESHOLD);
        assertEquals(Fighter.BLUE_WINDUP, w.gojo.state);
        assertTrue(w.pendingCharged());
        run(w, in, Tuning.BLUE_WINDUP_CHARGED - 2);
        assertFalse("前摇还没结束", w.blue.active());
        run(w, in, 4);
        assertTrue(w.blue.charged);
        assertEquals(Tuning.BLUE_RADIUS * 2, w.blue.radius, 0.01f);
        assertEquals(Tuning.BLUE_ACCEL * 2, w.blue.accel, 0.01f);
    }

    /** 出力最大：同样相对位置（距离按范围等比）上，实际吸引加速度正好是普通苍的 2 倍。 */
    @Test
    public void chargedBlueActuallyPullsTwiceAsHard() {
        float normal = firstTickPull(false, 100);
        float charged = firstTickPull(true, 200);
        assertTrue(normal > 0);
        assertEquals(2 * normal, charged, normal * 0.01f);
    }

    private static float firstTickPull(boolean charged, float dist) {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 200;
        w.sukuna.x = 1200;
        in.blueDown = in.blueHeld = true;
        in.blueUp = !charged;
        w.step(in);
        in.clearEdges();
        in.blueHeld = charged;
        for (int i = 0; i < 200 && w.blue.phase != BlueOrb.PULL; i++) {
            w.step(in);
            in.clearEdges();
            if (w.gojo.state == Fighter.BLUE_WINDUP) in.blueHeld = false;
        }
        assertEquals(BlueOrb.PULL, w.blue.phase);
        assertEquals(charged, w.blue.charged);
        w.sukuna.x = w.blue.cx + dist;
        w.sukuna.y = w.blue.cy - Tuning.BODY_H * 0.5f;
        w.sukuna.vx = w.sukuna.vy = 0;
        w.step(in);
        return -w.sukuna.vx;
    }

    /** 黑闪的命中停顿期间按的键不丢：停顿一结束就出招。 */
    @Test
    public void inputDuringHitstopIsKept() {
        World w = world();
        TickInput in = new TickInput();
        w.hitstop = 8;
        w.step(in);
        in.melee = true;
        w.step(in);
        in.clearEdges();
        assertEquals(Fighter.FREE, w.gojo.state);
        run(w, in, 8);
        assertEquals(Fighter.MELEE, w.gojo.state);

        World v = world();
        v.hitstop = 8;
        in.blueDown = in.blueHeld = true;
        v.step(in);
        in.clearEdges();
        run(v, in, 8);
        assertEquals("停顿里按下的苍，停顿后开始蓄", Fighter.BLUE_HOLD, v.gojo.state);
    }

    /** 体术收招前提前按苍（在缓冲时间内）：一收招就开始蓄。 */
    @Test
    public void blueBufferedNearEndOfMelee() {
        World w = world();
        TickInput in = new TickInput();
        in.melee = true;
        run(w, in, Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE + Tuning.MELEE_RECOVERY - 8);
        assertEquals(Fighter.MELEE, w.gojo.state);
        in.blueDown = in.blueHeld = true;
        run(w, in, 10);
        assertEquals(Fighter.BLUE_HOLD, w.gojo.state);
    }

    /** 太早按的苍（超过缓冲时间）作废，不会过很久才突然出来。 */
    @Test
    public void staleBluePressExpires() {
        World w = world();
        TickInput in = new TickInput();
        in.melee = true;
        w.step(in);
        in.clearEdges();
        in.blueDown = true;
        in.blueHeld = false;
        in.blueUp = true;
        run(w, in, 60);
        assertEquals(Fighter.FREE, w.gojo.state);
        assertFalse(w.blue.active());
    }

    /** 蓄苍时遇到停顿：停顿里“松开→再按下”按顺序处理——先放出普通苍，后来的按下因为冷却作废。 */
    @Test
    public void releaseThenPressDuringHitstopKeepsOrder() {
        World w = world();
        TickInput in = new TickInput();
        in.blueDown = in.blueHeld = true;
        run(w, in, 5);
        assertEquals(Fighter.BLUE_HOLD, w.gojo.state);
        w.hitstop = 8;
        in.blueUp = true;
        in.blueHeld = false;
        w.step(in);
        in.clearEdges();
        in.blueDown = in.blueHeld = true;
        w.step(in);
        in.clearEdges();
        run(w, in, 8);
        assertEquals(Fighter.BLUE_WINDUP, w.gojo.state);
        assertFalse("松开发生在长按确认之前：普通苍", w.pendingCharged());
        run(w, in, Tuning.BLUE_WINDUP + 40);
        assertTrue(w.blue.active());
        assertFalse(w.blue.charged);
    }

    /** 蓄苍时遇到停顿：停顿里按的体术会取消蓄力并出拳。 */
    @Test
    public void meleeDuringHitstopCancelsBlueHold() {
        World w = world();
        TickInput in = new TickInput();
        in.blueDown = in.blueHeld = true;
        run(w, in, 5);
        w.hitstop = 8;
        in.melee = true;
        w.step(in);
        in.clearEdges();
        run(w, in, 10);
        assertEquals(Fighter.MELEE, w.gojo.state);
        assertFalse(w.blue.active());
    }

    /** 赫：150 伤害并击飞。 */
    @Test
    public void redDealsHundredFiftyAndKnocksBack() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 400;
        w.sukuna.x = 520;
        in.red = true;
        run(w, in, Tuning.RED_WINDUP + 2);
        assertEquals(w.sukuna.maxHpMilli - Tuning.RED_DAMAGE_MILLI, w.sukuna.hpMilli);
        assertFalse(w.sukuna.captured);
        assertTrue(w.sukuna.vx > 300);
        run(w, in, 60);
        assertTrue("被击飞了", w.sukuna.x > 700);
        assertEquals("同一招只命中一次", w.sukuna.maxHpMilli - Tuning.RED_DAMAGE_MILLI, w.sukuna.hpMilli);
    }

    /** 赫打中被苍抓住的宿傩：解除捕获，短时间内不会被吸回去。 */
    @Test
    public void redReleasesBlueCapture() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 500;
        in.blueDown = in.blueHeld = in.blueUp = true;
        run(w, in, Tuning.BLUE_WINDUP + Tuning.BLUE_FLY + 60);
        in.blueHeld = false;
        assertTrue("被苍抓住", w.sukuna.captured);
        in.red = true;
        long before = 0;
        for (int i = 0; i < Tuning.RED_WINDUP + 4 && w.sukuna.lastHitKind != Attack.RED; i++) {
            before = w.sukuna.hpMilli;
            w.step(in);
            in.clearEdges();
        }
        assertEquals(Attack.RED, w.sukuna.lastHitKind);
        long dealt = before - w.sukuna.hpMilli;
        assertTrue("赫 150 + 这一 tick 苍的持续伤害：" + dealt, dealt >= Tuning.RED_DAMAGE_MILLI && dealt < Tuning.RED_DAMAGE_MILLI + 1000);
        assertFalse(w.sukuna.captured);
        assertTrue(w.sukuna.pullResist > 0);
        assertTrue(w.sukuna.vx > 300);
    }

    /** 苍贴墙投出也不超过 30%，苍消失后不会残留捕获。 */
    @Test
    public void captureEndsWithBlue() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 500;
        in.blueDown = in.blueHeld = in.blueUp = true;
        run(w, in, Tuning.BLUE_WINDUP + Tuning.BLUE_FLY + Tuning.BLUE_LIFE + 10);
        assertFalse(w.blue.active());
        assertFalse(w.sukuna.captured);
        run(w, in, 120);
        assertEquals("落回地面", 0, w.sukuna.y, 0.01f);
    }

    /** 无限：打到身上没有伤害、击退、硬直；只有世界里真的成立的领域/展延/特殊手段才能绕过。 */
    @Test
    public void infinityBlocksUnlessValidBypass() {
        World w = world();
        Attack a = new Attack();
        long full = w.gojo.hpMilli;
        a.set(Fighter.SUKUNA, Fighter.GOJO, Attack.TEST, 1, 100_000, 500, 300, 30, 0);
        int mask = w.apply(a);
        assertEquals(Defense.SHOW_INFINITY, mask);
        assertEquals(full, w.gojo.hpMilli);
        assertEquals(0, w.gojo.vx, 0);
        assertEquals(0, w.gojo.hitstun);

        // 只声明不算数
        a.set(Fighter.SUKUNA, Fighter.GOJO, Attack.TEST, 2, 100_000, 0, 0, 0,
                Attack.SURE_HIT | Attack.DOMAIN_AMPLIFICATION | Attack.SPECIAL_BYPASS);
        w.apply(a);
        assertEquals(full, w.gojo.hpMilli);

        // 领域必中：宿傩的领域确实罩住五条悟
        w.domains.sureHit[Fighter.SUKUNA][Fighter.GOJO] = true;
        a.set(Fighter.SUKUNA, Fighter.GOJO, Attack.TEST, 3, 100_000, 0, 0, 0, Attack.SURE_HIT);
        w.apply(a);
        assertEquals(full - 100_000, w.gojo.hpMilli);
        w.domains.clear();

        // 领域展延
        w.domains.amplification[Fighter.SUKUNA] = true;
        a.set(Fighter.SUKUNA, Fighter.GOJO, Attack.TEST, 4, 100_000, 0, 0, 0, Attack.DOMAIN_AMPLIFICATION);
        w.apply(a);
        assertEquals(full - 200_000, w.gojo.hpMilli);
        w.domains.clear();

        // 特殊手段
        w.domains.specialBypass[Fighter.SUKUNA] = true;
        a.set(Fighter.SUKUNA, Fighter.GOJO, Attack.TEST, 5, 100_000, 0, 0, 0, Attack.SPECIAL_BYPASS);
        w.apply(a);
        assertEquals(full - 300_000, w.gojo.hpMilli);

        // 无限挡下时仍然发出表现事件
        boolean shown = false;
        for (int i = 0; i < Events.CAP; i++) if (w.events.type[i] == Events.INFINITY_BLOCK) shown = true;
        assertTrue(shown);
    }

    /** 活靶子：打空血量后倒地，过一会儿满血站起来。 */
    @Test
    public void dummyRevivesAfterKo() {
        World w = world();
        TickInput in = new TickInput();
        w.sukuna.hpMilli = 10_000;
        w.gojo.x = w.sukuna.x - 60;
        in.melee = true;
        run(w, in, 40);
        assertEquals(Fighter.KO, w.sukuna.state);
        run(w, in, Tuning.KO_TICKS + 2);
        assertEquals(Fighter.FREE, w.sukuna.state);
        assertEquals(w.sukuna.maxHpMilli, w.sukuna.hpMilli);
    }

    /** 乱按一万多 tick：没有 NaN，人一直在战场里；同样输入结果完全一样。 */
    @Test
    public void fuzzStaysFiniteInBoundsAndDeterministic() {
        World a = world(), b = world();
        TickInput in = new TickInput();
        CombatRng r = new CombatRng(99);
        for (int i = 0; i < 20_000; i++) {
            if (i % 7 == 0) {
                in.moveX = (float) (r.nextUnitDouble() * 2 - 1);
                in.moveY = (float) (r.nextUnitDouble() * 2 - 1);
            }
            in.jump = r.nextUnitDouble() < 0.02;
            in.melee = r.nextUnitDouble() < 0.05;
            in.red = r.nextUnitDouble() < 0.01;
            boolean held = in.blueHeld;
            in.blueHeld = r.nextUnitDouble() < (held ? 0.98 : 0.01);
            in.blueDown = in.blueHeld && !held;
            in.blueUp = held && !in.blueHeld;
            a.step(in);
            b.step(in);
            in.clearEdges();
            for (Fighter f : new Fighter[]{a.gojo, a.sukuna}) {
                assertTrue(Float.isFinite(f.x) && Float.isFinite(f.y) && Float.isFinite(f.vx) && Float.isFinite(f.vy));
                assertTrue(f.x >= 0 && f.x <= Tuning.WIDTH && f.y >= 0 && f.y <= Tuning.CEILING);
            }
        }
        assertEquals(a.sukuna.hpMilli, b.sukuna.hpMilli);
        assertEquals(a.sukuna.x, b.sukuna.x, 0);
        assertEquals(a.rng.state(), b.rng.state());
        assertEquals(a.gojo.maxHpMilli, a.gojo.hpMilli); // 宿傩没有攻击，五条悟始终满血
    }
}
