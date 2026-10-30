package com.shinjuku.showdown.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 难度倍率、三轮的十影宿傩、顎吐、魔虚罗、原身（v0.8）。宿傩都不开 AI，意图手动填。 */
public class ShikigamiTest {

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

    /** 数一数这段时间里某种事件出现了几次（kind < 0 表示不管 kind）。 */
    private static int count(World w, long from, int type, int kind) {
        int n = 0;
        for (long i = from; i < w.events.written; i++) {
            int k = (int) (i & Events.MASK);
            if (w.events.type[k] == type && (kind < 0 || w.events.kind[k] == kind)) n++;
        }
        return n;
    }

    /** 召唤魔虚罗（念 120 tick），返回它。 */
    private static Fighter summon(World w, TickInput in) {
        w.sukunaIntent.summon = true;
        run(w, in, Tuning.SUMMON_CHANT + 1);
        assertTrue("魔虚罗显现", w.mahoraga.present);
        return w.mahoraga;
    }

    /** 每高一档 ×1.1：史上最强的宿傩血量 ×1.331，展延下的体术 75 → 99.825；魔虚罗的伤害不吃倍率。 */
    @Test
    public void difficultyTiersScaleSukunaDamageAndHp() {
        float[] hp = {5400f, 5940f, 6534f, 7187.4f};
        for (int level = 0; level < 4; level++) {
            BattleConfig c = new BattleConfig();
            c.sukunaDifficulty(level);
            assertEquals(hp[level], c.sukunaMaxHp, 0.01f);
            assertEquals(Math.pow(1.1, level), c.sukunaDamageMult, 1e-6);
        }
        World w = world(c -> c.sukunaDifficulty(3));
        assertEquals(7_187_400, w.sukuna.maxHpMilli);
        TickInput in = new TickInput();
        w.sukuna.x = w.gojo.x + 80;
        w.sukunaIntent.amp = true;
        run(w, in, Tuning.S_AMP_STARTUP + 2);
        assertTrue(w.ampTicks > 0);
        long hp0 = w.gojo.hpMilli;
        w.sukunaIntent.melee = true;
        run(w, in, Tuning.S_MELEE_STARTUP + Tuning.S_MELEE_ACTIVE + 2);
        assertEquals("75 × 1.331", hp0 - 99_825, w.gojo.hpMilli);
    }

    /** 三轮是十影宿傩：没有解、世界斩、法阵、魔虚罗、原身；有顎吐。 */
    @Test
    public void miwaSukunaIsTenShadowsWithoutShrine() {
        World w = world(c -> c.sukunaDifficulty(0));
        TickInput in = new TickInput();
        assertEquals(World.WHEEL_NONE, w.wheelOn);
        w.sukunaIntent.dismantle = true;
        run(w, in, 2);
        assertEquals("没有解", Fighter.FREE, w.sukuna.state);
        run(w, in, 3000);
        assertEquals("没有法阵：不会适应", 0, w.adaptPoints);
        assertFalse(w.worldUnlocked);
        w.worldUnlocked = true; // 就算硬塞进去也放不出来
        w.sukunaIntent.world = true;
        run(w, in, 2);
        assertEquals("没有世界斩", Fighter.FREE, w.sukuna.state);
        w.sukunaIntent.summon = true;
        run(w, in, 2);
        assertEquals("没有魔虚罗", Fighter.FREE, w.sukuna.state);
        w.sukuna.hpMilli = 1_000_000;
        w.sukunaIntent.transform = true;
        run(w, in, 2);
        assertEquals("不能变原身", 1_000_000, w.sukuna.hpMilli);
        assertFalse(w.sukunaTrueForm);
        w.sukunaIntent.agito = true;
        run(w, in, 2);
        assertEquals("有顎吐", Fighter.S_AGITO, w.sukuna.state);
    }

    /** 测试模式（默认配置）没有魔虚罗，也不能变原身。 */
    @Test
    public void defaultConfigHasNoMahoragaOrTrueForm() {
        World w = world(c -> { });
        TickInput in = new TickInput();
        w.sukunaIntent.summon = true;
        run(w, in, Tuning.SUMMON_CHANT + 2);
        assertFalse(w.mahoraga.present);
        w.sukuna.hpMilli = 1_000_000;
        w.sukunaIntent.transform = true;
        run(w, in, 2);
        assertEquals(1_000_000, w.sukuna.hpMilli);
    }

    /** 顎吐：结手影、俯冲到锁定点，被无限挡住（不扣血），但碰到念咒中的五条悟就打断。 */
    @Test
    public void agitoIsBlockedByInfinityButBreaksChants() {
        World w = world(c -> c.agito = true);
        TickInput in = new TickInput();
        w.gojo.x = 400;
        w.sukuna.x = 800;
        in.purple = in.purpleHeld = true;
        run(w, in, Tuning.PURPLE_HOLD + 4);
        assertEquals(Fighter.PURPLE_CHANT, w.gojo.state);
        long seen = w.events.written, hp0 = w.gojo.hpMilli;
        w.sukunaIntent.agito = true;
        run(w, in, Tuning.AGITO_SIGN + Tuning.AGITO_DIVE + Tuning.AGITO_ACTIVE + 1);
        assertEquals("被无限挡住", 1, count(w, seen, Events.INFINITY_BLOCK, Attack.AGITO));
        assertEquals(hp0, w.gojo.hpMilli);
        assertTrue("念咒被打断", w.gojo.state != Fighter.PURPLE_CHANT && w.purpleFailLock > 0);
        assertFalse("电完就消失", w.agito.active);
        assertTrue(w.agitoCd > 0);
    }

    /** 召唤：念完 120 tick 显现在宿傩前方；法阵移到魔虚罗头上；一局只能召唤一次（被打倒以后也不能再召）。 */
    @Test
    public void mahoragaIsSummonedOncePerBattle() {
        World w = world(c -> c.mahoraga = true);
        TickInput in = new TickInput();
        Fighter m = summon(w, in);
        assertEquals(World.WHEEL_MAHORAGA, w.wheelOn);
        assertEquals(850_000, m.hpMilli);
        assertEquals(w.sukuna.x - Tuning.SUMMON_OFFSET, m.x, 0.01f); // 五条悟在左边
        m.hpMilli = 0;
        run(w, in, 1);
        assertFalse("被打倒", m.present);
        assertEquals("法阵跟着没了", World.WHEEL_NONE, w.wheelOn);
        run(w, in, Tuning.SUMMON_RECOVERY + 2);
        w.sukunaIntent.summon = true;
        run(w, in, Tuning.SUMMON_CHANT + 2);
        assertFalse("不能再召唤", m.present);
        assertEquals(Fighter.FREE, w.sukuna.state);
    }

    /** 召唤的咏唱挨打就断：不算用掉，但要过 5 秒才能再念。 */
    @Test
    public void summonChantBreaksOnHitAndCanRetryLater() {
        World w = world(c -> c.mahoraga = true);
        TickInput in = new TickInput();
        w.sukuna.x = w.gojo.x + 80;
        w.sukunaIntent.summon = true;
        run(w, in, 20);
        assertEquals(Fighter.S_SUMMON, w.sukuna.state);
        long seen = w.events.written;
        in.melee = true;
        run(w, in, 16);
        assertTrue("被打断", w.sukuna.state != Fighter.S_SUMMON);
        assertFalse(w.mahoragaUsed);
        assertEquals(Tuning.SUMMON_RETRY, w.summonCd, 20);
        int broken = 0;
        for (long i = seen; i < w.events.written; i++) {
            int k = (int) (i & Events.MASK);
            if (w.events.type[k] == Events.SUMMON && w.events.value[k] < 0) broken++;
        }
        assertEquals(1, broken);
        w.gojo.x = 100;
        run(w, in, Tuning.SUMMON_RETRY);
        summon(w, in);
    }

    /** 茈 850：一发打掉满血的魔虚罗，还穿过去打到后面的宿傩。 */
    @Test
    public void purpleDestroysFullHpMahoragaAndPiercesToSukuna() {
        World w = world(c -> c.mahoraga = true);
        TickInput in = new TickInput();
        Fighter m = summon(w, in);
        w.gojo.x = 300;
        w.gojo.facing = 1;
        long sHp = w.sukuna.hpMilli;
        in.purple = true;
        run(w, in, 120);
        assertFalse("一发打掉", m.present);
        assertEquals("穿过去也打到宿傩", sHp - Tuning.PURPLE_DAMAGE_MILLI, w.sukuna.hpMilli);
    }

    /** 赫打到弹道上第一个就爆：魔虚罗挡在前面，宿傩不挨。 */
    @Test
    public void redHitsOnlyTheFirstTargetOnItsPath() {
        World w = world(c -> c.mahoraga = true);
        TickInput in = new TickInput();
        Fighter m = summon(w, in);
        w.gojo.x = m.x - 300;
        w.gojo.facing = 1;
        long sHp = w.sukuna.hpMilli;
        in.red = true;
        run(w, in, 60);
        assertEquals(Tuning.MAHORAGA_HP_MILLI - Tuning.RED_DAMAGE_MILLI, m.hpMilli);
        assertEquals(sHp, w.sukuna.hpMilli);
    }

    /** 五条悟的体术打够得着的最近一个；魔虚罗有霸体：普通体术照常扣血，但不硬直；赫照样让它硬直。 */
    @Test
    public void meleeHitsNearestAndMahoragaShrugsOffLightPunches() {
        World w = world(c -> c.mahoraga = true);
        TickInput in = new TickInput();
        Fighter m = summon(w, in);
        w.gojo.x = m.x - 80;
        w.gojo.facing = 1;
        long sHp = w.sukuna.hpMilli;
        long seen = w.events.written;
        in.melee = true;
        run(w, in, Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE + 1);
        long dealt = Tuning.MAHORAGA_HP_MILLI - m.hpMilli;
        assertTrue("打到魔虚罗（50 或黑闪 350）", dealt == Tuning.MELEE_DAMAGE_MILLI || dealt == Tuning.BLACK_FLASH_DAMAGE_MILLI);
        assertEquals("宿傩在后面", sHp, w.sukuna.hpMilli);
        if (count(w, seen, Events.BLACK_FLASH, -1) == 0) assertEquals("霸体：不硬直", 0, m.hitstun);
        run(w, in, 60);
        w.gojo.x = m.x - 200;
        w.gojo.facing = 1;
        in.red = true;
        run(w, in, Tuning.RED_WINDUP + 6);
        assertTrue("赫让它硬直", m.hitstun > 0 || m.knockedAir);
    }

    /** 魔虚罗没适应无限时被挡下（法阵转一格）；转满以后攻击能打穿，伤害固定（不吃难度倍率）。 */
    @Test
    public void mahoragaIsBlockedUntilAdaptedThenPierces() {
        World w = world(c -> {
            c.mahoraga = true;
            c.sukunaDamageMult = 1.331f;
        });
        TickInput in = new TickInput();
        Fighter m = summon(w, in);
        w.gojo.x = m.x - 100;
        long seen = w.events.written, hp0 = w.gojo.hpMilli;
        int points = w.adaptPoints;
        for (int i = 0; i < 300 && count(w, seen, Events.INFINITY_BLOCK, -1) == 0; i++) run(w, in, 1);
        assertTrue("被无限挡下", count(w, seen, Events.INFINITY_BLOCK, Attack.M_PUNCH) + count(w, seen, Events.INFINITY_BLOCK, Attack.M_SWORD) == 1);
        assertEquals(hp0, w.gojo.hpMilli);
        assertEquals("法阵转一格", points + 1, w.adaptPoints);
        assertFalse(w.mahoragaAdapted);

        World a = world(c -> {
            c.mahoraga = true;
            c.sukunaDamageMult = 1.331f;
        });
        a.adaptPoints = Tuning.S_ADAPT_POINTS; // 法阵早就转满了：一出来就适应了无限
        Fighter ma = summon(a, in);
        assertTrue(a.mahoragaAdapted);
        a.gojo.x = ma.x - 100;
        long g0 = a.gojo.hpMilli;
        for (int i = 0; i < 300 && a.gojo.hpMilli == g0; i++) run(a, in, 1);
        long dealt = g0 - a.gojo.hpMilli;
        assertTrue("打穿无限，伤害固定 " + dealt, dealt == Tuning.M_PUNCH_DAMAGE_MILLI || dealt == Tuning.M_SWORD_DAMAGE_MILLI);
    }

    /** 宿傩开领域展延（展延和十种影法术不能同时用）：魔虚罗站住不动、不出手。无量空处里也不能动。 */
    @Test
    public void amplificationAndUnlimitedVoidStopMahoraga() {
        World w = world(c -> c.mahoraga = true);
        TickInput in = new TickInput();
        Fighter m = summon(w, in);
        run(w, in, Tuning.MAHORAGA_RISE + Tuning.MAHORAGA_REACT + 2);
        w.sukunaIntent.amp = true;
        run(w, in, 2);
        assertTrue(w.tenShadowsPaused());
        float x0 = m.x;
        run(w, in, 120);
        assertEquals("展延期间不动", x0, m.x, 0.01f);
        assertEquals(Fighter.FREE, m.state);
        assertFalse(w.mahoragaCanAct());

        World v = world(c -> c.mahoraga = true);
        Fighter mv = summon(v, in);
        in.domain = true;
        run(v, in, 1);
        v.endCutscene(v.castId);
        run(v, in, 2);
        assertFalse("无量空处里不能动", v.mahoragaCanAct());
        assertTrue(mv.present);
    }

    /** 原身：开始时回满血；魔虚罗、法阵、顎吐都没了；变身挨打不断；之后不能再用十种影法术，也不能再变。 */
    @Test
    public void trueFormHealsFullyAndEndsTenShadows() {
        World w = world(c -> {
            c.mahoraga = true;
            c.agito = true;
            c.trueForm = true;
        });
        TickInput in = new TickInput();
        summon(w, in);
        run(w, in, Tuning.SUMMON_RECOVERY + 2);
        w.sukuna.hpMilli = 2_000_000;
        long seen = w.events.written;
        w.sukunaIntent.transform = true;
        run(w, in, 1);
        assertEquals("回满血", w.sukuna.maxHpMilli, w.sukuna.hpMilli);
        assertTrue(w.sukunaTrueForm);
        assertFalse("魔虚罗没了", w.mahoraga.present);
        assertEquals(World.WHEEL_NONE, w.wheelOn);
        assertEquals(1, count(w, seen, Events.MAHORAGA_GONE, -1));
        assertEquals(Fighter.S_TRANSFORM, w.sukuna.state);
        w.gojo.x = w.sukuna.x - 80;
        w.gojo.facing = 1;
        in.melee = true;
        run(w, in, 20);
        assertTrue("照常挨打", w.sukuna.hpMilli < w.sukuna.maxHpMilli);
        assertEquals("不可打断", Fighter.S_TRANSFORM, w.sukuna.state);
        run(w, in, Tuning.TRANSFORM_TICKS);
        assertEquals(Fighter.FREE, w.sukuna.state);
        w.sukunaIntent.agito = true;
        run(w, in, 2);
        assertEquals("没有顎吐了", Fighter.FREE, w.sukuna.state);
        w.sukuna.hpMilli = 1_000_000;
        w.sukunaIntent.transform = true;
        run(w, in, 2);
        assertEquals("一局一次", 1_000_000, w.sukuna.hpMilli);
    }

    /** 宿傩倒下：魔虚罗跟着消失；复活以后也不能再召唤。 */
    @Test
    public void sukunaKnockoutDismissesMahoragaForGood() {
        World w = world(c -> c.mahoraga = true);
        TickInput in = new TickInput();
        Fighter m = summon(w, in);
        w.sukuna.hpMilli = 0;
        run(w, in, 1);
        assertFalse(m.present);
        run(w, in, Tuning.KO_TICKS + 2);
        assertEquals("满血站起来", w.sukuna.maxHpMilli, w.sukuna.hpMilli);
        w.sukunaIntent.summon = true;
        run(w, in, Tuning.SUMMON_CHANT + 2);
        assertFalse(m.present);
    }

    private static BattleConfig cfg(int level) {
        BattleConfig c = new BattleConfig();
        c.sukunaDifficulty(level);
        return c;
    }

    /** 四个难度的 AI 都会用新的招：都会放顎吐（对基础脚本；高难度对标准脚本多半贴身开展延），里梅以上会召唤魔虚罗、会变原身；结果可复现。 */
    @Test
    public void everyDifficultyUsesItsKitAndIsDeterministic() {
        for (int level = 0; level < 4; level++) {
            int agito = 0, summons = 0, transforms = 0;
            for (long seed = 0; seed < 40; seed++) {
                BattleConfig c = cfg(level), c2 = cfg(level);
                c.gojoMaxHp = c2.gojoMaxHp = 3000; // 打久一点：高难度的宿傩往往在跌到变身血线之前就赢了
                c.sukunaMaxHp = c2.sukunaMaxHp = 2 * c.sukunaMaxHp; // v0.10 宿傩血量减半后：这里看的是 AI 会不会用这些招，不是平衡
                BalanceSim.Result r = BalanceSim.play(GojoBot.STANDARD, seed, c);
                BalanceSim.Result r2 = BalanceSim.play(GojoBot.STANDARD, seed, c2);
                assertEquals(r.ticks, r2.ticks);
                assertEquals(r.winner, r2.winner);
                BattleConfig cb = cfg(level);
                cb.sukunaMaxHp *= 2;
                BalanceSim.Result b = BalanceSim.play(GojoBot.BASIC, seed, cb);
                agito += b.agitoCast;
                // 标准脚本开局就放茈·最大输出，宿傩常常直接跌到变身血线（变原身以后不能召唤）：两档脚本合起来看
                if (r.summonTick >= 0 || b.summonTick >= 0) summons++;
                if (r.transformTick >= 0) transforms++;
            }
            assertTrue("level " + level + " 顎吐", agito > 0);
            if (level == 0) {
                assertEquals(0, summons);
                assertEquals(0, transforms);
            } else {
                assertTrue("level " + level + " 召唤魔虚罗", summons > 0);
                assertTrue("level " + level + " 变原身", transforms > 0);
            }
        }
    }

    /** 魔虚罗在出招的有效帧里被打空：这一下不会再打到五条悟（先倒下的不能出手），而且马上退场。 */
    @Test
    public void deadMahoragaCannotFinishItsSwing() {
        World w = world(c -> c.mahoraga = true);
        TickInput in = new TickInput();
        w.adaptPoints = Tuning.S_ADAPT_POINTS; // 适应了无限：打得穿
        Fighter m = summon(w, in);
        w.gojo.x = m.x - 100;
        for (int i = 0; i < 400 && !(m.state == Fighter.M_PUNCH && m.stateTick == Tuning.M_PUNCH_STARTUP); i++) run(w, in, 1);
        assertEquals(Fighter.M_PUNCH, m.state);
        long hp0 = w.gojo.hpMilli;
        m.hpMilli = 0; // 这一 tick 前被打空了
        run(w, in, 1);
        assertEquals("没打到", hp0, w.gojo.hpMilli);
        assertFalse("退场", m.present);
    }
}
