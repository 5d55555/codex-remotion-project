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
        run(w, in, Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE + 2);
        assertEquals(Fighter.MELEE_RECOVER, w.gojo.state);
        in.blueDown = in.blueHeld = true;
        run(w, in, 10);
        assertEquals(Fighter.BLUE_HOLD, w.gojo.state);
    }

    /** 太早按的苍（超过缓冲时间）作废，不会过很久才突然出来（这里在苍拳的长后摇里按）。 */
    @Test
    public void staleBluePressExpires() {
        World w = world();
        TickInput in = new TickInput();
        in.melee = in.meleeHeld = true;
        run(w, in, Tuning.FIST_HOLD + 2);
        in.meleeHeld = false;
        assertEquals(Fighter.FIST_WINDUP, w.gojo.state);
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

    /* ---------------- 手感：前后摇更短、后摇能推摇杆走开 ---------------- */

    @Test
    public void meleeRecoveryCanBeMovedOutOf() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 1100;
        in.melee = true;
        run(w, in, Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE + Tuning.MELEE_CANCEL + 1);
        in.moveX = 1;
        w.step(in);
        assertEquals("推摇杆提前结束后摇", Fighter.FREE, w.gojo.state);
        assertTrue(w.gojo.vx > 0);
        assertTrue("整套体术不到 0.2 秒", Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE + Tuning.MELEE_RECOVERY <= 24);
    }

    /* ---------------- 苍拳 ---------------- */

    /** 按住体术变苍拳：伤害是体术的 1.7 倍（85），黑闪也是 1.7 倍（595）；后摇比体术大。 */
    @Test
    public void heldMeleeBecomesBlueFist() {
        assertEquals(85_000, Tuning.FIST_DAMAGE_MILLI);
        assertEquals(595_000, Tuning.FIST_BLACK_FLASH_DAMAGE_MILLI);
        assertTrue(Tuning.FIST_RECOVERY > Tuning.MELEE_RECOVERY);
        World w = world();
        TickInput in = new TickInput();
        int fists = 0, flashes = 0;
        for (int round = 0; round < 120; round++) {
            w.gojo.x = w.sukuna.x - 70;
            w.sukuna.vx = w.sukuna.vy = 0;
            w.sukuna.y = 0;
            w.sukuna.hpMilli = w.sukuna.maxHpMilli;
            in.melee = in.meleeHeld = true;
            boolean sawFist = false;
            for (int i = 0; i < Tuning.FIST_HOLD + Tuning.FIST_WINDUP + 4; i++) {
                w.step(in);
                in.clearEdges();
                if (w.gojo.state == Fighter.FIST_WINDUP) sawFist = true;
            }
            in.meleeHeld = false;
            run(w, in, Tuning.FIST_ACTIVE + Tuning.FIST_RECOVERY + 16);
            assertTrue("按住变成了苍拳", sawFist);
            long dealt = w.sukuna.maxHpMilli - w.sukuna.hpMilli;
            if (dealt == Tuning.FIST_BLACK_FLASH_DAMAGE_MILLI) flashes++;
            else assertEquals(Tuning.FIST_DAMAGE_MILLI, dealt);
            fists++;
        }
        assertTrue("苍拳也会出黑闪 " + flashes + "/" + fists, flashes > 5);
    }

    /** 点一下体术还是普通体术（松开得早，不会多等）。 */
    @Test
    public void tappedMeleeIsNormalAndNotDelayed() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = w.sukuna.x - 60;
        in.melee = in.meleeHeld = true;
        run(w, in, 3);
        in.meleeHeld = false;
        int hitAt = -1;
        for (int i = 0; i < 20 && hitAt < 0; i++) {
            w.step(in);
            if (w.sukuna.hpMilli < w.sukuna.maxHpMilli) hitAt = w.tick;
        }
        assertTrue(hitAt > 0);
        assertTrue("和直接点一样快", hitAt <= Tuning.MELEE_STARTUP + 2);
    }

    /* ---------------- 赫：射程翻倍、最大输出 ---------------- */

    @Test
    public void redRangeDoubled() {
        assertEquals(0.32f * Tuning.WIDTH, Tuning.RED_REACH, 0.01f);
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 200;
        w.sukuna.x = 200 + 0.3f * Tuning.WIDTH; // 原来的赫打不到这么远
        in.red = true;
        run(w, in, Tuning.RED_WINDUP + Tuning.RED_TRAVEL + 2);
        assertEquals(w.sukuna.maxHpMilli - Tuning.RED_DAMAGE_MILLI, w.sukuna.hpMilli);
    }

    /** 长按赫：最大输出，飞满整个战场，550 伤害，击飞比普通赫猛。 */
    @Test
    public void heldRedIsMaxOutput() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 60;
        w.sukuna.x = 1220; // 战场另一头
        in.red = in.redHeld = true;
        run(w, in, Tuning.RED_HOLD + 2);
        assertEquals(Fighter.RED_WINDUP, w.gojo.state);
        in.redHeld = false;
        float vx = 0;
        for (int i = 0; i < Tuning.RED_MAX_WINDUP + 60 && w.sukuna.hpMilli == w.sukuna.maxHpMilli; i++) {
            w.step(in);
            vx = w.sukuna.vx;
        }
        assertEquals(w.sukuna.maxHpMilli - Tuning.RED_MAX_DAMAGE_MILLI, w.sukuna.hpMilli);
        assertTrue("排斥比普通赫强", vx > Tuning.RED_IMPULSE_X);
        assertTrue(w.redMax());
    }

    /* ---------------- 200% 茈 ---------------- */

    /** 长按茈：念完四段咒词才放；200% 的伤害（1700）。 */
    @Test
    public void heldPurpleChantsFourPhrasesThenFires200() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 200;
        w.sukuna.x = 900;
        in.purple = in.purpleHeld = true;
        int phases = 0;
        long seen = w.events.written;
        for (int i = 0; i < Tuning.PURPLE_HOLD + Tuning.CHANT_PHRASES * Tuning.CHANT_PHRASE + 4; i++) {
            w.step(in);
            in.clearEdges();
            for (; seen < w.events.written; seen++) {
                int k = (int) (seen & Events.MASK);
                if (w.events.type[k] == Events.CHANT && w.events.value[k] == phases) phases++;
            }
            if (w.gojo.state == Fighter.PURPLE_CHANT) assertEquals("念咒时不能动", 0, w.gojo.vx, 0);
        }
        assertEquals("九纲、偏光、乌与声明、表里之间", 4, phases);
        assertTrue(w.purple.active);
        assertTrue(w.purple.max);
        in.purpleHeld = false;
        run(w, in, 90);
        assertEquals(w.sukuna.maxHpMilli - Tuning.PURPLE_200_DAMAGE_MILLI, w.sukuna.hpMilli);
        assertEquals(1_700_000, Tuning.PURPLE_200_DAMAGE_MILLI);
    }

    /** 念咒中途松手：被打断，什么都不放。 */
    @Test
    public void releasingDuringChantBreaksIt() {
        World w = world();
        TickInput in = new TickInput();
        in.purple = in.purpleHeld = true;
        run(w, in, Tuning.PURPLE_HOLD + Tuning.CHANT_PHRASE + 4);
        assertEquals(Fighter.PURPLE_CHANT, w.gojo.state);
        in.purpleHeld = false;
        run(w, in, 4);
        assertEquals(Fighter.FREE, w.gojo.state);
        assertFalse(w.purple.active);
        assertEquals("没进冷却", 0, w.purpleCd);
    }

    /** 念咒时被宿傩攻击：被打断（哪怕被无限挡住没掉血）。 */
    @Test
    public void attackDuringChantBreaksItEvenThroughInfinity() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 600;
        w.sukuna.x = 900;
        in.purple = in.purpleHeld = true;
        run(w, in, Tuning.PURPLE_HOLD + 20);
        assertEquals(Fighter.PURPLE_CHANT, w.gojo.state);
        w.sukunaIntent.dismantle = true;
        run(w, in, Tuning.DISMANTLE_WINDUP + 2);
        assertEquals(Fighter.FREE, w.gojo.state);
        assertEquals("无限挡住了，没掉血", w.gojo.maxHpMilli, w.gojo.hpMilli);
        assertFalse(w.purple.active);
    }

    /** 点一下茈还是普通茈。 */
    @Test
    public void tappedPurpleIsNormal() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 800;
        in.purple = true;
        run(w, in, Tuning.PURPLE_WINDUP + 60);
        assertEquals(w.sukuna.maxHpMilli - Tuning.PURPLE_DAMAGE_MILLI, w.sukuna.hpMilli);
    }

    /* ---------------- 领域展开：无量空处 ---------------- */

    /** 按领域展开：先停住等过场；过场结束才展开，展开期间宿傩无法主动行动；时间到了恢复。 */
    @Test
    public void domainWaitsForCutsceneThenLocksSukuna() {
        BattleConfig c = new BattleConfig();
        c.sukunaAi = true;
        World w = new World(c);
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 560;
        in.domain = true;
        w.step(in);
        in.clearEdges();
        assertTrue(w.cutscene);
        int t0 = w.tick;
        float sx = w.sukuna.x;
        run(w, in, 300);
        assertEquals("过场期间全场暂停", t0, w.tick);
        assertEquals(sx, w.sukuna.x, 0);
        w.endCutscene(w.castId - 1); // 旧的回调不算数
        assertTrue(w.cutscene);
        w.endCutscene(w.castId);
        assertFalse(w.cutscene);
        assertEquals(Tuning.DOMAIN_DURATION, w.domainTicks);
        long seen = w.events.written;
        boolean sukunaActed = false;
        for (int i = 0; i < Tuning.DOMAIN_DURATION - 2; i++) {
            w.step(in);
            in.clearEdges();
            if (w.sukuna.state == Fighter.S_MELEE || w.sukuna.state == Fighter.S_DISMANTLE || Math.abs(w.sukuna.vx) > 1) sukunaActed = true;
        }
        assertFalse("领域里宿傩无法主动行动", sukunaActed);
        run(w, in, 10);
        assertEquals(0, w.domainTicks);
        assertTrue("领域结束开始冷却", w.domainCd > 0);
        boolean actedAfter = false;
        for (int i = 0; i < 600 && !actedAfter; i++) {
            w.step(in);
            in.clearEdges();
            if (w.sukuna.state == Fighter.S_MELEE || w.sukuna.state == Fighter.S_DISMANTLE) actedAfter = true;
        }
        assertTrue("领域结束后宿傩又能行动", actedAfter);
    }

    /* ---------------- 宿傩：体术 75、解 90；被无限挡住 ---------------- */

    @Test
    public void sukunaMeleeAndDismantleDamage() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.infinity = false; // 先看伤害本身
        w.gojo.x = 500;
        w.sukuna.x = 570;
        w.sukunaIntent.melee = true;
        run(w, in, Tuning.S_MELEE_STARTUP + Tuning.S_MELEE_ACTIVE + Tuning.S_MELEE_RECOVERY + 2);
        assertEquals(w.gojo.maxHpMilli - Tuning.S_MELEE_DAMAGE_MILLI, w.gojo.hpMilli);
        run(w, in, Tuning.S_ATTACK_GAP + 40);
        w.gojo.x = 300;
        w.sukuna.x = 300 + Tuning.DISMANTLE_RANGE - 40; // 射程不俗
        long before = w.gojo.hpMilli;
        w.sukunaIntent.dismantle = true;
        run(w, in, Tuning.DISMANTLE_WINDUP + 2);
        assertEquals("解几乎没有前摇，固定 90", before - Tuning.DISMANTLE_DAMAGE_MILLI, w.gojo.hpMilli);
    }

    @Test
    public void infinityBlocksSukunaButShowsEffect() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 570;
        long seen = w.events.written;
        w.sukunaIntent.melee = true;
        run(w, in, Tuning.S_ATTACK_GAP + 4); // 两次出手至少隔 0.4 秒
        w.sukunaIntent.dismantle = true;
        run(w, in, 60);
        assertEquals(w.gojo.maxHpMilli, w.gojo.hpMilli);
        int blocks = 0;
        for (; seen < w.events.written; seen++) if (w.events.type[(int) (seen & Events.MASK)] == Events.INFINITY_BLOCK) blocks++;
        assertEquals("两下都被无限挡住，特效照出", 2, blocks);
    }

    /** 三轮难度的 AI：会走、会出手（体术和解都用），同一个种子结果完全一样。 */
    @Test
    public void sukunaAiActsAndIsDeterministic() {
        BattleConfig c = new BattleConfig();
        c.sukunaAi = true;
        World a = new World(c), b = new World(c);
        TickInput in = new TickInput();
        int melee = 0, dismantle = 0;
        float minD = Float.MAX_VALUE;
        for (int i = 0; i < 120 * 20; i++) {
            int sa = a.sukuna.state;
            a.step(in);
            b.step(in);
            if (a.sukuna.state != sa && a.sukuna.state == Fighter.S_MELEE) melee++;
            if (a.sukuna.state != sa && a.sukuna.state == Fighter.S_DISMANTLE) dismantle++;
            minD = Math.min(minD, Math.abs(a.sukuna.x - a.gojo.x));
            if (i == 120 * 10) a.gojo.x = b.gojo.x = 200; // 中途换个位置
        }
        assertTrue("会靠近", minD < 120);
        assertTrue("用了体术 " + melee, melee > 3);
        assertTrue("用了解 " + dismantle, dismantle > 3);
        assertEquals(a.sukuna.x, b.sukuna.x, 0);
        assertEquals(a.gojo.hpMilli, b.gojo.hpMilli);
    }

    /** 测试模式（没有 AI）：宿傩一直站着不动。 */
    @Test
    public void noAiMeansDummy() {
        World w = world();
        TickInput in = new TickInput();
        float x = w.sukuna.x;
        run(w, in, 600);
        assertEquals(x, w.sukuna.x, 0);
        assertEquals(Fighter.FREE, w.sukuna.state);
    }

    /** 赫：150 伤害并击飞。 */
    @Test
    public void redDealsHundredFiftyAndKnocksBack() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 400;
        w.sukuna.x = 520;
        in.red = true;
        run(w, in, Tuning.RED_WINDUP + Tuning.RED_TRAVEL + 2);
        assertEquals(w.sukuna.maxHpMilli - Tuning.RED_DAMAGE_MILLI, w.sukuna.hpMilli);
        assertFalse(w.sukuna.captured);
        assertTrue(w.sukuna.vx > 300);
        run(w, in, 60);
        assertTrue("被击飞了", w.sukuna.x > 700);
        assertEquals("同一招只命中一次", w.sukuna.maxHpMilli - Tuning.RED_DAMAGE_MILLI, w.sukuna.hpMilli);
    }

    /** 苍把宿傩抓在近处，再放赫：赫与苍相遇，直接变成无指向茈。宿傩 850；五条悟在范围内，挨一半 425（无限挡不住自己的茈）。 */
    @Test
    public void redMeetingBlueBecomesHollowPurple() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 500;
        in.blueDown = in.blueHeld = in.blueUp = true;
        run(w, in, Tuning.BLUE_WINDUP + Tuning.BLUE_FLY + 60);
        in.blueHeld = false;
        assertTrue("被苍抓住", w.sukuna.captured);
        long sBefore = w.sukuna.hpMilli, gBefore = w.gojo.hpMilli;
        in.red = true;
        for (int i = 0; i < Tuning.RED_WINDUP + Tuning.RED_TRAVEL + 2 && !w.hollow.active; i++) {
            w.step(in);
            in.clearEdges();
        }
        assertTrue("变成了无指向茈", w.hollow.active);
        assertFalse(w.blue.active());
        assertFalse(w.red.active);
        assertFalse(w.sukuna.captured);
        run(w, in, Tuning.HOLLOW_GROW + 2);
        long dealt = sBefore - w.sukuna.hpMilli; // 850，加上赫前摇那 0.12 秒里苍的持续伤害（核心每秒 100）
        assertTrue("宿傩挨了 850：" + dealt, dealt >= Tuning.HOLLOW_DAMAGE_MILLI && dealt <= Tuning.HOLLOW_DAMAGE_MILLI + 20_000);
        assertEquals("五条悟挨了一半", Tuning.HOLLOW_SELF_DAMAGE_MILLI, gBefore - w.gojo.hpMilli);
        assertEquals(425_000, Tuning.HOLLOW_SELF_DAMAGE_MILLI);
        run(w, in, Tuning.HOLLOW_LIFE);
        assertFalse(w.hollow.active);
        assertEquals("每人只挨一次", Tuning.HOLLOW_SELF_DAMAGE_MILLI, gBefore - w.gojo.hpMilli);
    }

    /** 苍放得远、赫飞到射程末端才相遇：无指向茈照样打宿傩，但扩不到五条悟身上。 */
    @Test
    public void hollowFarFromGojoSparesHim() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 620;
        in.blueDown = in.blueHeld = in.blueUp = true;
        run(w, in, Tuning.BLUE_WINDUP + Tuning.BLUE_FLY + 90);
        in.blueHeld = false;
        assertTrue(w.sukuna.captured);
        long sBefore = w.sukuna.hpMilli;
        in.red = true;
        run(w, in, Tuning.RED_WINDUP + Tuning.RED_TRAVEL + Tuning.HOLLOW_LIFE + 4);
        assertTrue("宿傩挨了无指向茈", sBefore - w.sukuna.hpMilli >= Tuning.HOLLOW_DAMAGE_MILLI);
        assertEquals("五条悟不在范围内，没受伤", w.gojo.maxHpMilli, w.gojo.hpMilli);
    }

    /** 赫擦着苍的作用范围边缘飞过（这一 tick 两头都在范围外，中间进了范围）：也要变成无指向茈。 */
    @Test
    public void grazingRedStillMeetsBlue() {
        World w = world();
        float range = Math.min(Tuning.RED_RANGE, Tuning.BLUE_RADIUS);
        BlueOrb b = w.blue;
        b.phase = BlueOrb.PULL;
        b.tick = 10;
        b.radius = Tuning.BLUE_RADIUS;
        b.cx = b.x = b.px = 640;
        b.cy = b.y = b.py = 200;
        float step = Tuning.RED_SPEED * Tuning.DT;
        w.red.start(99, 640 - step * 0.5f, 200 + range - 0.1f, 1); // 这一 tick 从中线左边飞到右边
        float end = (float) Math.hypot(step * 0.5f, range - 0.1f);
        assertTrue("两头都在范围外", end > range);
        w.step(new TickInput());
        assertTrue(w.hollow.active);
        assertEquals("在刚进入范围的地方合成（离最近点不远）", 640, w.hollow.x, 5f);
    }

    /** 线段求交的边界：正好相切、正好在末尾碰到都算；碰不到的不算。 */
    @Test
    public void firstContactEdges() {
        assertTrue("正好相切", World.firstContact(-10f, 102.4f, 20.48f, 0f, 102.4f) >= 0);
        assertEquals("末尾刚碰到", 1f, World.firstContact(-112.4f, 0f, 10f, 0f, 102.4f), 1e-4f);
        assertEquals("一开始就在里面", 0f, World.firstContact(5f, 5f, 1f, 0f, 102.4f), 0f);
        assertEquals("擦不到", -1f, World.firstContact(-10f, 103f, 20f, 0f, 102.4f), 0f);
        assertEquals("背着走", -1f, World.firstContact(-110f, 0f, -10f, 0f, 102.4f), 0f);
    }

    /** 苍和赫离得远（不在作用范围内）：各打各的，不会变成茈。 */
    @Test
    public void farRedAndBlueStaySeparate() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 700;
        w.sukuna.x = 1200;
        in.moveX = -1; // 苍朝左丢到最远，赫朝右放
        in.blueDown = in.blueHeld = in.blueUp = true;
        run(w, in, Tuning.BLUE_WINDUP + 2);
        in.moveX = 0;
        in.blueHeld = false;
        run(w, in, Tuning.BLUE_FLY + 4);
        in.moveX = 1;
        in.red = true;
        run(w, in, Tuning.RED_WINDUP + Tuning.RED_TRAVEL + 4);
        assertFalse(w.hollow.active);
        assertTrue(w.blue.active());
    }

    /** 茈：向朝向发射，打中 850，一直飞出战场。 */
    @Test
    public void purpleFiresForwardForEightFifty() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 800;
        in.purple = true;
        run(w, in, Tuning.PURPLE_WINDUP + 2);
        assertTrue(w.purple.active);
        assertEquals(1, w.purple.dir, 0);
        assertTrue(w.purpleCd > 0);
        run(w, in, 60);
        assertEquals(w.sukuna.maxHpMilli - Tuning.PURPLE_DAMAGE_MILLI, w.sukuna.hpMilli);
        run(w, in, 60);
        assertFalse("飞出战场了", w.purple.active);
        assertEquals("同一发只打一次", w.sukuna.maxHpMilli - Tuning.PURPLE_DAMAGE_MILLI, w.sukuna.hpMilli);
        assertEquals("茈不伤五条悟", w.gojo.maxHpMilli, w.gojo.hpMilli);
    }

    /** 茈朝人物当前的朝向发射：背对宿傩就打不到他。 */
    @Test
    public void purpleFollowsFacing() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 600;
        w.sukuna.x = 900;
        in.moveX = -1; // 推着摇杆朝左（不推的时候会自动面向宿傩）
        in.purple = true;
        w.step(in);
        in.clearEdges();
        in.moveX = 0;
        run(w, in, Tuning.PURPLE_WINDUP + 2);
        assertEquals(-1, w.purple.dir, 0);
        run(w, in, 120);
        assertEquals(w.sukuna.maxHpMilli, w.sukuna.hpMilli);
    }

    /** 五条悟被自己的无指向茈打空血量：倒地，过一会儿满血站起来（训练模式）。 */
    @Test
    public void gojoKnockedOutBySelfHollowRevives() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 500;
        w.gojo.hpMilli = 100_000;
        in.blueDown = in.blueHeld = in.blueUp = true;
        run(w, in, Tuning.BLUE_WINDUP + Tuning.BLUE_FLY + 60);
        in.blueHeld = false;
        in.red = true;
        run(w, in, Tuning.RED_WINDUP + Tuning.RED_TRAVEL + Tuning.HOLLOW_GROW + 4);
        assertEquals(Fighter.KO, w.gojo.state);
        run(w, in, Tuning.KO_TICKS + 2);
        assertEquals(Fighter.FREE, w.gojo.state);
        assertEquals(w.gojo.maxHpMilli, w.gojo.hpMilli);
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
        fuzz(false);
        fuzz(true);
    }

    private void fuzz(boolean withAi) {
        BattleConfig c = new BattleConfig();
        c.sukunaAi = withAi;
        World a = new World(c), b = new World(c);
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
            in.purple = r.nextUnitDouble() < 0.003;
            in.domain = r.nextUnitDouble() < 0.0005;
            in.meleeHeld = r.nextUnitDouble() < 0.3;
            in.redHeld = r.nextUnitDouble() < 0.4;
            in.purpleHeld = r.nextUnitDouble() < 0.6;
            boolean held = in.blueHeld;
            in.blueHeld = r.nextUnitDouble() < (held ? 0.98 : 0.01);
            in.blueDown = in.blueHeld && !held;
            in.blueUp = held && !in.blueHeld;
            a.step(in);
            b.step(in);
            in.clearEdges();
            if (a.cutscene) a.endCutscene(a.castId);
            if (b.cutscene) b.endCutscene(b.castId);
            for (Fighter f : new Fighter[]{a.gojo, a.sukuna}) {
                assertTrue(Float.isFinite(f.x) && Float.isFinite(f.y) && Float.isFinite(f.vx) && Float.isFinite(f.vy));
                assertTrue(f.x >= 0 && f.x <= Tuning.WIDTH && f.y >= 0 && f.y <= Tuning.CEILING);
            }
        }
        assertEquals(a.sukuna.hpMilli, b.sukuna.hpMilli);
        assertEquals(a.sukuna.x, b.sukuna.x, 0);
        assertEquals(a.rng.state(), b.rng.state());
        assertEquals(a.gojo.hpMilli, b.gojo.hpMilli);
        assertTrue(a.gojo.hpMilli >= 0 && a.sukuna.hpMilli >= 0);
    }
}
