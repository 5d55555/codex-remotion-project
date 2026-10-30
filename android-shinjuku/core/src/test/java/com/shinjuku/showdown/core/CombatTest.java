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

    /** 长按苍：念完三段咒词（位相、黄昏、智慧之瞳）才放，范围和吸力翻倍；念的时候不能动。 */
    @Test
    public void chargedBlueChantsThenDoublesRangeAndPull() {
        World w = world();
        TickInput in = new TickInput();
        in.blueDown = in.blueHeld = true;
        w.step(in);
        in.clearEdges();
        run(w, in, Tuning.BLUE_HOLD_THRESHOLD);
        assertEquals(Fighter.BLUE_CHANT, w.gojo.state);
        assertEquals(Attack.BLUE_FIELD, w.chantKind);
        in.moveX = 1;
        float x = w.gojo.x;
        run(w, in, Tuning.CHANT3_PHRASE * Tuning.CHANT3_PHRASES - 4);
        assertEquals("念的时候不能动", x, w.gojo.x, 0.01f);
        assertEquals(2, w.chantPhase);
        assertFalse("还没念完", w.blue.active());
        in.moveX = 0;
        run(w, in, 6);
        assertTrue(w.blue.charged);
        assertEquals(Tuning.BLUE_RADIUS * 2, w.blue.radius, 0.01f);
        assertEquals(Tuning.BLUE_ACCEL * 2, w.blue.accel, 0.01f);
    }

    /** 苍、赫的咏唱：松手就断（什么都不放）；被宿傩的攻击碰到也断（哪怕被无限挡住）。 */
    @Test
    public void blueAndRedChantsBreakOnReleaseOrAttack() {
        World w = world();
        TickInput in = new TickInput();
        in.blueDown = in.blueHeld = true;
        w.step(in);
        in.clearEdges();
        run(w, in, Tuning.BLUE_HOLD_THRESHOLD + 10);
        assertEquals(Fighter.BLUE_CHANT, w.gojo.state);
        in.blueHeld = false;
        in.blueUp = true;
        run(w, in, 2);
        assertEquals(Fighter.FREE, w.gojo.state);
        run(w, in, 200);
        assertFalse("松手：什么都没放", w.blue.active());

        World v = world();
        v.gojo.x = 400;
        v.sukuna.x = 700;
        in.red = in.redHeld = true;
        run(v, in, Tuning.RED_HOLD + 10);
        assertEquals(Fighter.RED_CHANT, v.gojo.state);
        v.sukunaIntent.dismantle = true;
        run(v, in, Tuning.DISMANTLE_WINDUP + 2);
        assertEquals("被解碰到（无限挡住了）也断", Fighter.FREE, v.gojo.state);
        assertEquals(v.gojo.maxHpMilli, v.gojo.hpMilli);
        run(v, in, 200);
        assertFalse(v.red.active);
        assertEquals("没放出去", v.sukuna.maxHpMilli, v.sukuna.hpMilli);
        in.redHeld = false;
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
        in.jumpHeld = true;
        in.melee = true;
        run(w, in, 2);
        in.jumpHeld = false;
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

    /** 长按体术还是苍拳（保留）：往前冲一拳，伤害是体术的 1.7 倍（85），黑闪也是 1.7 倍（595）；后摇比体术大。 */
    @Test
    public void heldMeleeIsStillBlueFist() {
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
            run(w, in, Tuning.FIST_ACTIVE + Tuning.FIST_RECOVERY + 140);
            assertTrue("按住变成了苍拳", sawFist);
            long dealt = w.sukuna.maxHpMilli - w.sukuna.hpMilli;
            if (dealt == Tuning.FIST_BLACK_FLASH_DAMAGE_MILLI) flashes++;
            else assertEquals(Tuning.FIST_DAMAGE_MILLI, dealt);
            fists++;
        }
        assertTrue("苍拳也会出黑闪 " + flashes + "/" + fists, flashes > 5);
    }

    /** 苍拳：按住跳跃时点体术。地上是举起来往下劈，115；黑闪按同样倍数（805）；有前摇和后摇。 */
    @Test
    public void jumpHeldMeleeIsBlueFist() {
        assertEquals(115_000, Tuning.SMASH_DAMAGE_MILLI);
        assertTrue(Tuning.SMASH_RECOVERY > Tuning.MELEE_RECOVERY);
        World w = world();
        TickInput in = new TickInput();
        int fists = 0, flashes = 0;
        for (int round = 0; round < 120; round++) {
            w.gojo.x = w.sukuna.x - 70;
            w.gojo.y = 0;
            w.sukuna.vx = w.sukuna.vy = 0;
            w.sukuna.y = 0;
            w.sukuna.hpMilli = w.sukuna.maxHpMilli;
            in.jumpHeld = true;
            in.melee = true;
            boolean sawFist = false;
            for (int i = 0; i < Tuning.SMASH_WINDUP + 4; i++) {
                w.step(in);
                in.clearEdges();
                if (w.gojo.state == Fighter.FIST_WINDUP) sawFist = true;
            }
            in.jumpHeld = false;
            run(w, in, Tuning.SMASH_ACTIVE + Tuning.SMASH_RECOVERY + 140);
            assertTrue("按住跳跃时点体术：下砸苍拳", sawFist);
            long dealt = w.sukuna.maxHpMilli - w.sukuna.hpMilli;
            if (dealt == Tuning.SMASH_BLACK_FLASH_DAMAGE_MILLI) flashes++;
            else assertEquals(Tuning.SMASH_DAMAGE_MILLI, dealt);
            fists++;
        }
        assertTrue("苍拳也会出黑闪 " + flashes + "/" + fists, flashes > 5);
    }

    /** 苍拳在空中：停一下，然后往下俯冲砸到下面的宿傩。 */
    @Test
    public void airBlueFistDivesDown() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = w.sukuna.x - 90;
        in.jump = in.jumpHeld = true;
        run(w, in, 18);
        assertFalse(w.gojo.onGround);
        float y = w.gojo.y;
        in.melee = true;
        run(w, in, Tuning.SMASH_WINDUP - 2);
        assertEquals(Fighter.FIST_WINDUP, w.gojo.state);
        assertEquals("前摇：停在空中", y, w.gojo.y, 1f);
        run(w, in, 40);
        in.jumpHeld = false;
        assertTrue("俯冲落地", w.gojo.onGround);
        long dealt = w.sukuna.maxHpMilli - w.sukuna.hpMilli;
        assertTrue("砸中了：" + dealt, dealt == Tuning.SMASH_DAMAGE_MILLI || dealt == Tuning.SMASH_BLACK_FLASH_DAMAGE_MILLI);
    }

    /** 苍拳的前摇容易被打断：被宿傩的攻击碰到（被无限挡住也算）就断。 */
    @Test
    public void blueFistWindupIsFragile() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 400;
        w.sukuna.x = 700;
        in.jumpHeld = true;
        in.melee = true;
        run(w, in, 3);
        assertEquals(Fighter.FIST_WINDUP, w.gojo.state);
        w.sukunaIntent.dismantle = true;
        run(w, in, Tuning.DISMANTLE_WINDUP + 1);
        assertEquals(Fighter.FREE, w.gojo.state);
        in.jumpHeld = false;
    }

    /** 点一下体术就是普通体术（不再有长按蓄力，不会多等）。 */
    @Test
    public void tappedMeleeIsNormalAndNotDelayed() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = w.sukuna.x - 60;
        in.melee = true;
        int hitAt = -1;
        for (int i = 0; i < 20 && hitAt < 0; i++) {
            w.step(in);
            in.clearEdges();
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

    /** 长按赫：念完三段咒词（位相、波罗蜜、光之柱）放最大输出，飞满整个战场，550 伤害，击飞比普通赫猛。 */
    @Test
    public void heldRedChantsThenMaxOutput() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 60;
        w.sukuna.x = 1220; // 战场另一头
        in.red = in.redHeld = true;
        run(w, in, Tuning.RED_HOLD + 2);
        assertEquals(Fighter.RED_CHANT, w.gojo.state);
        assertEquals(Attack.RED, w.chantKind);
        run(w, in, Tuning.CHANT3_PHRASE * Tuning.CHANT3_PHRASES - 6);
        assertEquals(Fighter.RED_CHANT, w.gojo.state);
        float vx = 0;
        for (int i = 0; i < 80 && w.sukuna.hpMilli == w.sukuna.maxHpMilli; i++) {
            w.step(in);
            vx = w.sukuna.vx;
        }
        in.redHeld = false;
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

    /** 按住茈、还没开始念（起手）时挨打也算打断；点按的普通茈不受影响。 */
    @Test
    public void attackDuringHeldPurpleWindupBreaksIt() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 600;
        w.sukuna.x = 900;
        in.purple = in.purpleHeld = true;
        run(w, in, 4);
        assertEquals(Fighter.PURPLE_WINDUP, w.gojo.state);
        w.sukunaIntent.dismantle = true;
        run(w, in, Tuning.DISMANTLE_WINDUP + 2);
        assertEquals(Fighter.FREE, w.gojo.state);
        run(w, in, Tuning.PURPLE_HOLD + Tuning.CHANT_PHRASES * Tuning.CHANT_PHRASE + 40);
        assertFalse("被打断了：不会接着念、也不会放", w.purple.active);
        assertEquals(w.sukuna.maxHpMilli, w.sukuna.hpMilli);

        World v = world();
        TickInput tap = new TickInput();
        v.gojo.x = 600;
        v.sukuna.x = 900;
        tap.purple = true;
        run(v, tap, 4);
        v.sukunaIntent.dismantle = true;
        run(v, tap, Tuning.PURPLE_WINDUP + 4);
        assertTrue("点按的普通茈照常放出", v.purple.active || v.sukuna.hpMilli < v.sukuna.maxHpMilli);
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
            in.dodge = r.nextUnitDouble() < 0.01;
            in.jumpHeld = r.nextUnitDouble() < 0.2;
            in.dodgeHeld = r.nextUnitDouble() < 0.2;
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

    /* ---------------- 宿傩的技能组：领域展延、魔虚罗的适应、世界斩、反转术式 ---------------- */

    private static void ampOn(World w, TickInput in) {
        w.sukunaIntent.amp = true;
        run(w, in, Tuning.S_AMP_STARTUP + 2);
        assertTrue("展延开了", w.ampTicks > 0);
    }

    /** 没开展延，宿傩的体术被无限挡住；开了展延，体术破无限，正好 75。解永远被挡住（展延时还用不了）。 */
    @Test
    public void amplifiedMeleeBypassesInfinityButDismantleNever() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 570;
        w.sukunaIntent.melee = true;
        run(w, in, Tuning.S_MELEE_STARTUP + Tuning.S_MELEE_ACTIVE + Tuning.S_MELEE_RECOVERY + Tuning.S_ATTACK_GAP);
        assertEquals("没展延：挡住", w.gojo.maxHpMilli, w.gojo.hpMilli);
        ampOn(w, in);
        w.sukunaIntent.dismantle = true;
        run(w, in, 10);
        assertEquals("展延时不能用解", Fighter.FREE, w.sukuna.state);
        w.gojo.x = w.sukuna.x - 70;
        w.sukunaIntent.melee = true;
        run(w, in, Tuning.S_MELEE_STARTUP + Tuning.S_MELEE_ACTIVE + 2);
        assertEquals("展延：破无限，75", w.gojo.maxHpMilli - Tuning.S_MELEE_DAMAGE_MILLI, w.gojo.hpMilli);
        // 关掉展延：刚关的一小段时间也不能用术式；之后解照样被无限挡住
        run(w, in, Tuning.S_ATTACK_GAP);
        w.sukunaIntent.ampOff = true;
        run(w, in, 1);
        assertEquals(0, w.ampTicks);
        assertFalse(w.domains.amplification[Fighter.SUKUNA]);
        w.sukunaIntent.dismantle = true;
        run(w, in, 1);
        assertEquals("刚关展延：不能马上用解", Fighter.FREE, w.sukuna.state);
        run(w, in, Tuning.S_AMP_EXIT);
        long before = w.gojo.hpMilli;
        w.sukunaIntent.dismantle = true;
        run(w, in, Tuning.DISMANTLE_WINDUP + Tuning.DISMANTLE_RECOVERY + 2);
        assertEquals("解永远被无限挡住", before, w.gojo.hpMilli);
        // 展延到时间自己关，冷却从关闭开始算
        ampOnAfterCooldown(w, in);
        run(w, in, Tuning.S_AMP_DURATION);
        assertEquals(0, w.ampTicks);
        assertEquals(Tuning.S_AMP_COOLDOWN, w.ampCd, 2);
    }

    private static void ampOnAfterCooldown(World w, TickInput in) {
        run(w, in, Tuning.S_AMP_COOLDOWN + 2);
        ampOn(w, in);
    }

    /** 魔虚罗的法阵：没开展延时每 4 秒自己转一格；解碰到无限能马上转一格（两格至少隔 1 秒）；转满 4 格且打满 12 秒才学会世界斩。 */
    @Test
    public void wheelTurnsAndUnlocksWorldSlash() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 800;
        w.sukunaIntent.dismantle = true;
        run(w, in, Tuning.DISMANTLE_WINDUP + Tuning.DISMANTLE_RECOVERY + 1);
        assertEquals("解被挡住、收完招：转一格", 1, w.adaptPoints);
        w.sukunaIntent.dismantle = true;
        run(w, in, Tuning.DISMANTLE_WINDUP + Tuning.DISMANTLE_RECOVERY + Tuning.DISMANTLE_COOLDOWN);
        assertTrue("两格至少隔 1 秒", w.adaptPoints <= 2);
        while (w.adaptPoints < Tuning.S_ADAPT_POINTS) run(w, in, 1);
        assertTrue("转满了，但打满 12 秒才学会：" + w.fightTicks, w.fightTicks < Tuning.S_ADAPT_UNLOCK_MIN_TICKS || w.worldUnlocked);
        run(w, in, Tuning.S_ADAPT_UNLOCK_MIN_TICKS);
        assertTrue(w.worldUnlocked);
        assertTrue("世界斩是被规则认可的破无限手段", w.domains.specialBypass[Fighter.SUKUNA]);
        // 展延期间法阵不转
        World v = world();
        ampOn(v, in);
        run(v, in, Tuning.S_AMP_DURATION - 10);
        assertEquals(0, v.adaptPoints);
    }

    private static World unlocked(TickInput in) {
        World w = world();
        w.gojo.x = 300;
        w.sukuna.x = 700;
        while (!w.worldUnlocked) run(w, in, 1);
        w.gojo.hpMilli = w.gojo.maxHpMilli;
        return w;
    }

    /** 世界斩：第一次靠束缚只要 48 tick；之后要结印 + 三段咏唱 + 指向，共 144 tick。斩的是空间，破无限，400。 */
    @Test
    public void worldSlashFirstIsBoundThenChanted() {
        TickInput in = new TickInput();
        World w = unlocked(in);
        w.sukunaIntent.world = true;
        run(w, in, Tuning.S_WORLD_FIRST_WINDUP - 1);
        assertEquals("第一次：还没斩出去", w.gojo.maxHpMilli, w.gojo.hpMilli);
        run(w, in, 2);
        assertEquals("第一次：48 tick 斩出去，破无限 400", w.gojo.maxHpMilli - Tuning.S_WORLD_DAMAGE_MILLI, w.gojo.hpMilli);
        run(w, in, Tuning.S_WORLD_COOLDOWN + 40);
        w.gojo.hpMilli = w.gojo.maxHpMilli;
        w.gojo.x = 300;
        w.sukuna.x = 700;
        run(w, in, 60);
        long seen = w.events.written;
        w.sukunaIntent.world = true;
        run(w, in, Tuning.S_WORLD_FIRST_WINDUP + 4);
        assertEquals("之后：要咏唱，48 tick 时还没斩", w.gojo.maxHpMilli, w.gojo.hpMilli);
        assertEquals(Fighter.S_WORLD, w.sukuna.state);
        run(w, in, Tuning.S_WORLD_WINDUP - Tuning.S_WORLD_FIRST_WINDUP);
        int phrases = 0, locks = 0;
        for (long k = Math.max(seen, w.events.written - Events.CAP); k < w.events.written; k++) {
            int i = (int) (k & Events.MASK);
            if (w.events.type[i] == Events.WORLD_CHANT && w.events.value[i] >= 1 && w.events.value[i] <= 3) phrases++;
            if (w.events.type[i] == Events.WORLD_CHANT && w.events.value[i] == 4) locks++;
        }
        assertEquals("龙鳞、反发、番之流星", 3, phrases);
        assertEquals("指向：锁定一次", 1, locks);
        assertEquals("144 tick 斩出去", w.gojo.maxHpMilli - Tuning.S_WORLD_DAMAGE_MILLI, w.gojo.hpMilli);
    }

    /** 世界斩是一道水平的斩带：算好时间跳过去就躲开；咏唱时挨一拳就断，还要僵一会儿。 */
    @Test
    public void worldSlashCanBeJumpedOrInterrupted() {
        TickInput in = new TickInput();
        World w = unlocked(in);
        w.sukunaIntent.world = true;
        run(w, in, Tuning.S_WORLD_FIRST_WINDUP - 25);
        in.jump = true;
        run(w, in, 40);
        assertEquals("跳过斩带：没事", w.gojo.maxHpMilli, w.gojo.hpMilli);

        World v = unlocked(in);
        v.gojo.x = v.sukuna.x - 70;
        v.gojo.facing = 1;
        v.sukunaIntent.world = true;
        run(v, in, 10);
        in.melee = true;
        run(v, in, 20);
        assertEquals("被打断", Fighter.FREE, v.sukuna.state);
        assertTrue("打断后僵住", v.sStagger > 0 || v.sukuna.hitstun > 0);
        run(v, in, Tuning.S_WORLD_FIRST_WINDUP + 20);
        assertEquals("没斩出去", v.gojo.maxHpMilli, v.gojo.hpMilli);
        assertTrue("束缚的“第一次”起手就用掉了", v.worldBound);
    }

    /** 反转术式：起手 36，之后每秒回 170（1.5 秒共 255）；不可打断（挨打、被苍吸住都照样回完）；一局最多两次。 */
    @Test
    public void reverseTechniqueHeals170PerSecondAndCannotBeInterrupted() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 100;
        w.sukuna.x = 1100;
        w.sukuna.hpMilli = w.sukuna.maxHpMilli - 1_000_000;
        long hp0 = w.sukuna.hpMilli;
        w.sukunaIntent.rct = true;
        run(w, in, Tuning.S_RCT_STARTUP + Tuning.HZ + 1); // 第 1 tick 进入起手
        assertEquals("一秒正好 170", hp0 + 170_000, w.sukuna.hpMilli);
        run(w, in, Tuning.S_RCT_DURATION - Tuning.HZ + Tuning.S_RCT_RECOVERY + 2);
        assertEquals(hp0 + Tuning.S_RCT_TOTAL_MILLI, w.sukuna.hpMilli);
        assertEquals(255_000, Tuning.S_RCT_TOTAL_MILLI);
        assertEquals(1, w.rctUses);
        run(w, in, Tuning.S_RCT_COOLDOWN);
        w.gojo.x = w.sukuna.x - 70;
        w.gojo.facing = 1;
        long hp1 = w.sukuna.hpMilli, seen = w.events.written, dealt = 0;
        w.sukunaIntent.rct = true;
        run(w, in, Tuning.S_RCT_STARTUP + 20);
        for (int i = 0; i < 4; i++) { // 挨几拳
            in.melee = true;
            run(w, in, 16);
            for (; seen < w.events.written; seen++) {
                int k = (int) (seen & Events.MASK);
                if (w.events.type[k] == Events.DAMAGE) dealt += Math.round(w.events.value[k] * 1000);
            }
        }
        assertTrue("真的挨打了", dealt > 0);
        assertEquals("挨打也不断", Fighter.S_RCT, w.sukuna.state);
        run(w, in, Tuning.S_RCT_DURATION + Tuning.S_RCT_RECOVERY);
        assertEquals("照样回满 255", hp1 - dealt + Tuning.S_RCT_TOTAL_MILLI, w.sukuna.hpMilli);
        assertEquals(2, w.rctUses);
        run(w, in, Tuning.S_RCT_COOLDOWN + 10);
        w.gojo.x = 100;
        w.sukunaIntent.rct = true;
        run(w, in, 5);
        assertEquals("用完两次了", Fighter.FREE, w.sukuna.state);
    }

    /** 反转术式回血中挨了致死的瞬移连段：照样倒下，不会被同一 tick 的回血救回来。 */
    @Test
    public void lethalTeleportDuringReverseTechniqueStillKnocksOut() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 700;
        w.sukuna.hpMilli = 1_000;
        w.sukunaIntent.rct = true;
        run(w, in, Tuning.S_RCT_STARTUP + 2);
        assertEquals(Fighter.S_RCT, w.sukuna.state);
        w.sukunaIntent.rct = false;
        in.dodgeHeld = true;
        in.blueDown = in.blueUp = true;
        run(w, in, 1);
        in.dodgeHeld = false;
        boolean ko = false;
        for (int i = 0; i < 400 && !ko; i++) {
            w.step(in);
            in.clearEdges();
            ko = w.sukuna.state == Fighter.KO;
        }
        assertTrue("苍拳连段打空了血就倒下", ko);
    }

    /** 难度参数：先设成史上最强再设回三轮，要完全回到三轮的默认值。 */
    @Test
    public void presetZeroRestoresBaseTuning() {
        BattleConfig.AiTuning base = new BattleConfig.AiTuning(), t = new BattleConfig.AiTuning();
        t.preset(3);
        assertTrue(t.reactMin != base.reactMin && t.tacticLevel == 3);
        t.preset(0);
        for (java.lang.reflect.Field f : BattleConfig.AiTuning.class.getFields()) {
            try {
                assertEquals(f.getName(), f.get(base), f.get(t));
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }

    /** 连段衰减：同一串连击里第 4 下起，轻拳不再造成硬直（但照样扣血）；1 秒没挨打重新算。 */
    @Test
    public void lightMeleeComboStopsStunningAfterThreeHits() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 560;
        int stunned = 0, hits = 0;
        long seen = w.events.written;
        for (int t = 0; t < 120 * 3 && hits < 6; t++) {
            in.melee = w.meleeCd == 0 && w.gojo.state == Fighter.FREE;
            int before = w.sukuna.hitstun;
            w.step(in);
            in.clearEdges();
            w.sukuna.x = w.gojo.x + 60; // 一直贴着
            for (; seen < w.events.written; seen++) {
                int i = (int) (seen & Events.MASK);
                if (w.events.type[i] == Events.HIT && w.events.kind[i] == Attack.MELEE) {
                    hits++;
                    if (w.sukuna.hitstun > before) stunned++;
                }
            }
        }
        assertTrue("打了好几下：" + hits, hits >= 5);
        assertEquals("只有前三下有硬直", Tuning.COMBO_STUN_HITS, stunned);
    }

    /** 五条悟挨打：手上的招作废（没放出去的不进冷却）。 */
    @Test
    public void gojoIsInterruptedWhenHit() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 570;
        ampOn(w, in);
        in.red = in.redHeld = true;
        run(w, in, 4);
        assertEquals(Fighter.RED_HOLD, w.gojo.state);
        w.sukunaIntent.melee = true;
        run(w, in, Tuning.S_MELEE_STARTUP + 2);
        assertTrue(w.gojo.hitstun > 0);
        assertEquals("赫的起手被打断", Fighter.FREE, w.gojo.state);
        assertEquals("没放出去，不进冷却", 0, w.redCd);
    }

    /** 三轮的 AI 对固定的五条悟脚本：会开展延打出伤害，结果可复现。 */
    @Test
    public void sukunaAiUsesAmplificationAgainstABot() {
        int sukunaWins = 0, gojoWins = 0;
        for (int seed = 0; seed < 20; seed++) {
            BattleConfig c = new BattleConfig();
            c.sukunaAi = true;
            BalanceSim.Result a = BalanceSim.play(GojoBot.STANDARD, seed, c);
            BattleConfig c2 = new BattleConfig();
            c2.sukunaAi = true;
            BalanceSim.Result b = BalanceSim.play(GojoBot.STANDARD, seed, c2);
            assertEquals("可复现", a.ticks, b.ticks);
            assertEquals(a.winner, b.winner);
            if (a.winner == 1) sukunaWins++;
            if (a.winner == 0) gojoWins++;
            assertTrue("宿傩打出了伤害", a.ampMelee > 0);
        }
        assertTrue("宿傩赢得了几局：" + sukunaWins, sukunaWins >= 3);
        assertTrue("五条悟也赢得了几局：" + gojoWins, gojoWins >= 3);
    }

    /* ---------------- 闪避、瞬移 ---------------- */

    /** 闪避：不推摇杆往面朝的方向，推着往摇杆的方向；闪的时候无敌（展延的体术也打不到）；冷却 0.5 秒。 */
    @Test
    public void dodgeFollowsStickAndIsInvulnerable() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 800;
        run(w, in, 2);
        assertEquals(1, w.gojo.facing);
        float x0 = w.gojo.x;
        in.dodge = true;
        run(w, in, Tuning.DODGE_TICKS + 2);
        assertTrue("面朝右：往右闪", w.gojo.x > x0 + 100);
        run(w, in, Tuning.DODGE_COOLDOWN);
        x0 = w.gojo.x;
        in.moveX = -1;
        in.dodge = true;
        run(w, in, Tuning.DODGE_TICKS);
        in.moveX = 0;
        assertTrue("推着往左：往左闪", w.gojo.x < x0 - 100);
        run(w, in, Tuning.DODGE_COOLDOWN);
        // 冷却：刚闪完马上再按，闪不出来
        in.dodge = true;
        run(w, in, 1);
        assertEquals(Fighter.DODGE, w.gojo.state);
        run(w, in, Tuning.DODGE_TICKS);
        in.dodge = true;
        run(w, in, 1);
        assertTrue("冷却中", w.gojo.state != Fighter.DODGE || w.dodgeCd > 0);
        // 无敌：展延的体术打不到闪避中的五条悟
        World v = world();
        v.gojo.x = 500;
        v.sukuna.x = 570;
        v.sukunaIntent.amp = true;
        run(v, in, Tuning.S_AMP_STARTUP + 2);
        v.gojo.x = v.sukuna.x - 70;
        v.sukunaIntent.melee = true;
        run(v, in, Tuning.S_MELEE_STARTUP - 1);
        in.dodge = true;
        in.moveX = -1;
        run(v, in, 3);
        in.moveX = 0;
        run(v, in, 20);
        assertEquals("闪避中无敌", v.gojo.maxHpMilli, v.gojo.hpMilli);
    }

    /** 按住闪避点苍：前摇后出现在宿傩背后，把他吸起来，苍拳连打 5 下（每下 115），最后击飞、击倒。 */
    @Test
    public void dodgeHeldBlueTeleportsBehindAndCombos() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 700;
        in.dodgeHeld = true;
        in.blueDown = in.blueUp = true;
        run(w, in, 1);
        assertEquals(Fighter.TP_WINDUP, w.gojo.state);
        in.dodgeHeld = false;
        run(w, in, Tuning.TP_WINDUP + 1);
        assertEquals(Fighter.TP_BLUE, w.gojo.state);
        assertTrue("出现在背后（另一边）", w.gojo.x > w.sukuna.x);
        assertEquals("面向宿傩", -1, w.gojo.facing);
        boolean wasDown = false;
        for (int i = 0; i < 400 && w.gojo.state != Fighter.FREE; i++) {
            w.step(in);
            in.clearEdges();
            if (w.sukuna.downTicks > 0) wasDown = true;
        }
        assertEquals(w.sukuna.maxHpMilli - 5 * Tuning.SMASH_DAMAGE_MILLI, w.sukuna.hpMilli);
        assertTrue("击倒", wasDown);
        assertTrue("被打飞了", w.sukuna.x < 700);
        assertTrue(w.tpCd > 0 && w.blueCd > 0);
    }

    /** 按住闪避点赫：出现在背后放一发很大的赫，把宿傩轰出战场宽度 85%（这里有足够的空间），击倒。 */
    @Test
    public void dodgeHeldRedBlastsAcrossTheField() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 40;
        w.sukuna.x = 1180; // 宿傩在右边，五条悟瞬移到他右边（背后），往左轰
        run(w, in, 2);
        in.dodgeHeld = true;
        in.red = true;
        run(w, in, 1);
        in.dodgeHeld = false;
        assertEquals(Fighter.TP_WINDUP, w.gojo.state);
        float x0 = w.sukuna.x;
        boolean wasDown = false;
        for (int i = 0; i < 300 && (w.gojo.state != Fighter.FREE || w.sukuna.scripted); i++) {
            w.step(in);
            in.clearEdges();
            if (w.sukuna.downTicks > 0) wasDown = true;
        }
        assertEquals(w.sukuna.maxHpMilli - Tuning.TP_RED_DAMAGE_MILLI, w.sukuna.hpMilli);
        assertEquals("轰出 85%", Tuning.TP_RED_DISTANCE, x0 - w.sukuna.x, 2f);
        assertTrue(wasDown);
    }

    /** 瞬移的前摇可以被打断（被宿傩的攻击碰到就断），不进冷却。 */
    @Test
    public void teleportWindupIsInterruptible() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 400;
        w.sukuna.x = 700;
        in.dodgeHeld = true;
        in.red = true;
        run(w, in, 3);
        in.dodgeHeld = false;
        assertEquals(Fighter.TP_WINDUP, w.gojo.state);
        w.sukunaIntent.dismantle = true;
        run(w, in, Tuning.DISMANTLE_WINDUP + 1);
        assertEquals(Fighter.FREE, w.gojo.state);
        assertEquals(0, w.tpCd);
        run(w, in, 60);
        assertEquals(w.sukuna.maxHpMilli, w.sukuna.hpMilli);
    }

    /** 冷却中按的闪避超过缓冲时间就作废，不会过很久自己闪出去。 */
    @Test
    public void staleDodgePressExpires() {
        World w = world();
        TickInput in = new TickInput();
        in.dodge = true;
        run(w, in, Tuning.DODGE_TICKS + 2);
        in.dodge = true; // 冷却中再按
        run(w, in, 1);
        run(w, in, Tuning.DODGE_COOLDOWN + 30);
        int dodges = 0;
        for (int i = 0; i < 60; i++) {
            w.step(in);
            if (w.gojo.state == Fighter.DODGE) dodges++;
        }
        assertEquals("过期的闪避不会自己出来", 0, dodges);
    }

    /** 赫念咒时，命中停顿里松开再按住，恢复后照样算松手（打断）。 */
    @Test
    public void releaseDuringHitstopStillBreaksRedChant() {
        World w = world();
        TickInput in = new TickInput();
        in.red = in.redHeld = true;
        run(w, in, Tuning.RED_HOLD + 6);
        assertEquals(Fighter.RED_CHANT, w.gojo.state);
        w.hitstop = 6;
        in.redHeld = false;
        run(w, in, 2);
        in.redHeld = true;
        run(w, in, 6);
        assertEquals("停顿里松过手：打断", Fighter.FREE, w.gojo.state);
        in.redHeld = false;
    }

    /** 连段衰减只数轻拳：重招（这里是瞬移连段）不占轻拳的三下名额。 */
    @Test
    public void comboDecayCountsOnlyLightHits() {
        World w = world();
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 700;
        in.dodgeHeld = true;
        in.blueDown = in.blueUp = true;
        run(w, in, 1);
        in.dodgeHeld = false;
        for (int i = 0; i < 400 && w.gojo.state != Fighter.FREE; i++) run(w, in, 1);
        assertEquals("苍拳五连不算轻拳", 0, w.sukuna.comboHits);
    }

    /** 瞬移先按哪个都行：按着苍点闪避、按着赫点闪避、刚点过闪避（0.25 秒内）再点苍，都能接上；冷却好了可以一次次用。 */
    @Test
    public void teleportComboWorksInAnyOrderAndRepeatedly() {
        TickInput in = new TickInput();
        // 按着苍（还在起手 / 念咒），点闪避
        World a = world();
        a.gojo.x = 300;
        a.sukuna.x = 700;
        in.blueDown = in.blueHeld = true;
        run(a, in, 30);
        assertEquals(Fighter.BLUE_CHANT, a.gojo.state);
        in.dodge = true;
        run(a, in, 2);
        assertEquals("按着苍点闪避", Fighter.TP_WINDUP, a.gojo.state);
        in.blueHeld = false;
        in.blueUp = true;
        run(a, in, 1);
        // 按着赫，点闪避
        World b = world();
        b.gojo.x = 300;
        b.sukuna.x = 700;
        in.red = in.redHeld = true;
        run(b, in, 5);
        in.dodge = true;
        run(b, in, 2);
        in.redHeld = false;
        assertEquals("按着赫点闪避", Fighter.TP_WINDUP, b.gojo.state);
        // 点一下闪避（闪出去、已经松开），马上点苍
        World c = world();
        c.gojo.x = 300;
        c.sukuna.x = 700;
        in.dodge = true;
        run(c, in, 4);
        in.blueDown = in.blueUp = true;
        run(c, in, 4);
        assertEquals("刚点过闪避再点苍", Fighter.TP_WINDUP, c.gojo.state);
        // 连着用：冷却好了再来一次也行
        for (int round = 0; round < 3; round++) {
            World d = round == 0 ? world() : null;
            if (d == null) break;
            for (int k = 0; k < 3; k++) {
                d.gojo.x = 300;
                d.sukuna.x = 700;
                d.sukuna.hpMilli = d.sukuna.maxHpMilli;
                run(d, in, Tuning.TP_COOLDOWN + 200);
                in.dodge = in.dodgeHeld = true;
                run(d, in, 6);
                in.blueDown = in.blueUp = true;
                run(d, in, 2);
                in.dodgeHeld = false;
                assertEquals("第 " + (k + 1) + " 次", Fighter.TP_WINDUP, d.gojo.state);
            }
        }
    }

    /* ---------------- 各难度的 AI ---------------- */

    private static World aiWorld(int level) {
        BattleConfig c = new BattleConfig();
        c.sukunaAi = true;
        c.ai.preset(level);
        return new World(c);
    }

    /** 四个难度的 AI 都会出手、开展延，同一个种子结果完全一样；被控住时不发任何意图。 */
    @Test
    public void everyDifficultyAiActsAndIsDeterministic() {
        for (int level = 0; level < 4; level++) {
            BattleConfig c = new BattleConfig();
            c.sukunaAi = true;
            c.ai.preset(level);
            BalanceSim.Result a = BalanceSim.play(GojoBot.STANDARD, 7, c);
            BattleConfig c2 = new BattleConfig();
            c2.sukunaAi = true;
            c2.ai.preset(level);
            BalanceSim.Result b = BalanceSim.play(GojoBot.STANDARD, 7, c2);
            assertEquals("难度 " + level + " 可复现", a.ticks, b.ticks);
            assertEquals(a.winner, b.winner);
            assertTrue("难度 " + level + " 开了展延", a.amps > 0);
        }
    }

    /** 里梅以上：看到五条悟念咒（这里是长按赫），过了反应时间就用解去打断（碰到就断，被无限挡住也一样）。 */
    @Test
    public void higherDifficultyInterruptsChants() {
        int broken = 0;
        for (int seed = 0; seed < 20; seed++) {
            BattleConfig c = new BattleConfig();
            c.sukunaAi = true;
            c.ai.preset(2);
            c.seed = seed;
            World w = new World(c);
            TickInput in = new TickInput();
            w.gojo.x = 400;
            w.sukuna.x = 750;
            w.ampCd = 10_000; // 不让它开展延，专门看打断
            in.red = in.redHeld = true;
            for (int i = 0; i < Tuning.RED_HOLD + Tuning.CHANT3_PHRASE * Tuning.CHANT3_PHRASES + 4; i++) {
                w.step(in);
                in.clearEdges();
                if (w.gojo.state == Fighter.FREE && i > Tuning.RED_HOLD) {
                    broken++;
                    break;
                }
            }
            in.redHeld = false;
        }
        assertTrue("万的宿傩大多能打断念咒：" + broken + "/20", broken >= 12);
    }

    /** 万以上：没开展延时被贴身就拉开距离；被逼到墙边就从五条悟头上跳过去。 */
    @Test
    public void higherDifficultyKitesAndVaultsAtTheWall() {
        World w = aiWorld(2);
        TickInput in = new TickInput();
        w.sukuna.x = 600;
        w.gojo.x = 540;
        w.ampCd = 10_000;
        float x0 = w.sukuna.x;
        for (int i = 0; i < 60; i++) {
            w.step(in);
            if (w.cutscene) w.endCutscene(w.castId);
        }
        assertTrue("往后拉开：" + w.sukuna.x, w.sukuna.x > x0 + 40);
        World v = aiWorld(3);
        v.sukuna.x = Tuning.WIDTH - 40;
        v.gojo.x = Tuning.WIDTH - 120;
        v.ampCd = 10_000;
        boolean jumped = false, passed = false;
        for (int i = 0; i < 240; i++) {
            v.gojo.x = Math.max(v.gojo.x, Tuning.WIDTH - 140); // 五条悟一直堵在墙边
            v.step(in);
            if (!v.sukuna.onGround) jumped = true;
            if (v.sukuna.x < v.gojo.x) passed = true;
        }
        assertTrue("被逼到墙边会起跳", jumped);
        assertTrue("从五条悟头上越过去", passed);
    }
}
