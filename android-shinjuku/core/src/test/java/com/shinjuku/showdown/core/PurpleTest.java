package com.shinjuku.showdown.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 茈·最大输出（同时按住苍和赫）和瞬移·茈（按住闪避 + 茈）（v0.9）。宿傩都不开 AI。 */
public class PurpleTest {

    private static World world(java.util.function.Consumer<BattleConfig> f) {
        BattleConfig c = new BattleConfig();
        f.accept(c);
        c.sukunaAi = false;
        return new World(c);
    }

    private static void run(World w, TickInput in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            w.step(in);
            in.clearEdges();
        }
    }

    private static final int CHANT = Tuning.CHANT3_PHRASE * 6 + Tuning.CHANT_PHRASE * Tuning.CHANT_PHRASES;

    /** 同时按住苍和赫，念到固定动作开始（返回时已经在固定动作里）。 */
    private static void chantMax(World w, TickInput in) {
        in.blueDown = in.blueHeld = true;
        in.red = in.redHeld = true;
        run(w, in, 3);
        assertEquals(Fighter.PURPLE_MAX_CHANT, w.gojo.state);
        in.blueHeld = in.redHeld = true;
        for (int i = 0; i < CHANT + 10 && w.cine == World.CINE_NONE; i++) run(w, in, 1);
        assertEquals(World.CINE_PURPLE_MAX, w.cine);
        in.blueHeld = in.redHeld = false;
    }

    /** 十段咒词（苍三、赫三、茈四）念完放 11 秒的固定动作：全场暂停，宿傩 3500；五条悟离得远就不挨。 */
    @Test
    public void maxPurpleChantsTenPhrasesThenDeals3500() {
        World w = world(c -> { });
        TickInput in = new TickInput();
        w.gojo.x = 100;
        w.sukuna.x = 1000;
        long sHp = w.sukuna.hpMilli, gHp = w.gojo.hpMilli;
        int phases = 0;
        long seen = w.events.written;
        chantMax(w, in);
        w.sukunaIntent.amp = true; // 固定动作里宿傩的意图不会被执行
        int dCd = w.domainCd, tpCd = w.tpCd;
        for (long i = seen; i < w.events.written; i++) {
            int k = (int) (i & Events.MASK);
            if (w.events.type[k] == Events.CHANT && w.events.kind[k] == Attack.PURPLE_MAX && w.events.value[k] >= 0) phases++;
        }
        assertEquals("十段咒词", 10, phases);
        int start = w.tick, fight = w.fightTicks;
        assertTrue("宿傩被定住", w.sukuna.scripted);
        while (w.cine != World.CINE_NONE) run(w, in, 1);
        assertTrue("10 秒以上", w.tick - start >= 10 * Tuning.HZ);
        assertEquals("固定动作期间时间不走", fight, w.fightTicks);
        assertEquals("冷却也停着", dCd, w.domainCd);
        assertEquals(tpCd, w.tpCd);
        assertEquals("宿傩没开展延", 0, w.ampTicks);
        assertEquals(sHp - Tuning.PURPLE_MAX_DAMAGE_MILLI, w.sukuna.hpMilli);
        assertEquals("离得远：五条悟不挨", gHp, w.gojo.hpMilli);
        assertEquals("冷却 60 秒（开始那一 tick 走了一格，固定动作里不走）", Tuning.PURPLE_MAX_COOLDOWN - 1, w.purpleCd);
        assertTrue(w.blueCd > 0 && w.redCd > 0);
        run(w, in, 400);
        assertTrue("宿傩掉回地上", w.sukuna.onGround);
        assertEquals(Fighter.FREE, w.gojo.state);
    }

    /** 五条悟在爆炸范围里：自己挨 700（无限挡不住自己的术式）。 */
    @Test
    public void maxPurpleHurtsGojoWithinTheBlast() {
        World w = world(c -> { });
        TickInput in = new TickInput();
        w.gojo.x = 500;
        w.sukuna.x = 700;
        long gHp = w.gojo.hpMilli;
        chantMax(w, in);
        while (w.cine != World.CINE_NONE) run(w, in, 1);
        assertEquals(gHp - Tuning.PURPLE_MAX_SELF_DAMAGE_MILLI, w.gojo.hpMilli);
    }

    /** 魔虚罗在场的版本：它也挨 3500，被打掉。 */
    @Test
    public void maxPurpleAlsoDestroysMahoraga() {
        World w = world(c -> c.mahoraga = true);
        TickInput in = new TickInput();
        w.sukunaIntent.summon = true;
        run(w, in, Tuning.SUMMON_CHANT + Tuning.SUMMON_RECOVERY + 2);
        assertTrue(w.mahoraga.present);
        w.gojo.x = 100;
        in.domain = true; // 先开领域把宿傩和魔虚罗定住，不然魔虚罗走过来一碰就打断咒词
        run(w, in, 1);
        w.endCutscene(w.castId);
        chantMax(w, in);
        assertTrue("魔虚罗的版本", w.cineMaho);
        while (w.cine != World.CINE_NONE) run(w, in, 1);
        run(w, in, 2);
        assertFalse("魔虚罗被打掉", w.mahoraga.present);
    }

    /** 念的时候松开赫（或被攻击碰到）就断：苍、赫、茈都短时间不能再按，不进冷却。 */
    @Test
    public void maxPurpleChantBreaksOnReleaseOrContact() {
        World w = world(c -> { });
        TickInput in = new TickInput();
        in.blueDown = in.blueHeld = true;
        in.red = in.redHeld = true;
        run(w, in, 3);
        in.blueHeld = in.redHeld = true;
        run(w, in, 100);
        assertEquals(Fighter.PURPLE_MAX_CHANT, w.gojo.state);
        in.redHeld = false;
        run(w, in, 1);
        assertTrue(w.gojo.state != Fighter.PURPLE_MAX_CHANT);
        assertTrue(w.blueFailLock > 0 && w.redFailLock > 0 && w.purpleFailLock > 0);
        assertEquals("不进冷却", 0, w.purpleCd);

        World h = world(c -> { });
        TickInput hi = new TickInput();
        h.gojo.x = 400;
        h.sukuna.x = 800;
        hi.blueDown = hi.blueHeld = true;
        hi.red = hi.redHeld = true;
        run(h, hi, 3);
        hi.blueHeld = hi.redHeld = true;
        run(h, hi, 60);
        h.sukunaIntent.dismantle = true; // 解被无限挡住，但碰到了就断
        run(h, hi, 5);
        assertTrue(h.gojo.state != Fighter.PURPLE_MAX_CHANT);
        assertEquals(World.CINE_NONE, h.cine);
    }

    /** 茈在冷却：同时按住苍和赫只是普通的苍（不会念茈·最大输出）。 */
    @Test
    public void maxPurpleNeedsBlueRedAndPurpleReady() {
        World w = world(c -> { });
        TickInput in = new TickInput();
        w.purpleCd = 100;
        in.blueDown = in.blueHeld = true;
        in.red = in.redHeld = true;
        run(w, in, 3);
        in.blueHeld = in.redHeld = true;
        run(w, in, 40);
        assertTrue(w.gojo.state != Fighter.PURPLE_MAX_CHANT);
        assertEquals(World.CINE_NONE, w.cine);
    }

    /** 瞬移·茈：按住闪避点茈 → 念茈的四段咒词 → 闪到背后拍肩 → 宿傩回头 → 贴脸一发茈 1900，击飞。 */
    @Test
    public void teleportPurpleTapsTheShoulderAndBlasts1900() {
        World w = world(c -> { });
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 700;
        long sHp = w.sukuna.hpMilli;
        in.dodgeHeld = true;
        in.purple = true;
        run(w, in, 1);
        in.dodgeHeld = false;
        assertEquals(Fighter.TP_WINDUP, w.gojo.state);
        run(w, in, Tuning.CHANT_PHRASE * Tuning.CHANT_PHRASES + 1);
        assertEquals(Fighter.TP_PURPLE, w.gojo.state);
        assertTrue("闪到背后", w.gojo.x > w.sukuna.x);
        assertEquals("宿傩背对着他", -1, w.sukuna.facing);
        run(w, in, Tuning.TP_PURPLE_TURN);
        assertEquals("回头", 1, w.sukuna.facing);
        float x0 = w.sukuna.x;
        while (w.gojo.state == Fighter.TP_PURPLE) run(w, in, 1);
        assertEquals(sHp - Tuning.TP_PURPLE_DAMAGE_MILLI, w.sukuna.hpMilli);
        assertTrue("被轰飞", w.sukuna.x < x0 - 100);
        assertEquals(Tuning.TP_COOLDOWN, w.tpCd, 200);
        assertTrue(w.purpleCd > Tuning.PURPLE_COOLDOWN);
    }

    /** 先点茈再点闪避也算；咒词念的时候被碰到就断（不瞬移、不进冷却）。 */
    @Test
    public void teleportPurpleAnyOrderAndFragileChant() {
        World w = world(c -> { });
        TickInput in = new TickInput();
        w.gojo.x = 300;
        w.sukuna.x = 700;
        in.purple = true;
        run(w, in, 3);
        assertEquals(Fighter.PURPLE_WINDUP, w.gojo.state);
        in.dodge = true;
        run(w, in, 1);
        assertEquals(Fighter.TP_WINDUP, w.gojo.state);
        assertEquals(w.chantPhase, 0);
        run(w, in, 60);
        w.sukunaIntent.dismantle = true;
        run(w, in, 5);
        assertTrue("咒词被打断", w.gojo.state != Fighter.TP_WINDUP && w.gojo.state != Fighter.TP_PURPLE);
        assertEquals("不进瞬移冷却", 0, w.tpCd);
        assertTrue(w.purpleFailLock > 0);
    }
}
