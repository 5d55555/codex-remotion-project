package com.shinjuku.showdown.core;

/**
 * 平衡模拟用的五条悟脚本：只看画面上看得到的东西，按固定策略按键。三档（Astra 的验收方案）：
 * - 基础：贴身打体术，近中距离赫，偶尔苍；不躲世界斩、不用领域、不念咒、不闪避、不瞬移。
 * - 标准：24–36 tick 反应；看到世界斩起手就跳（或贴着就打断）；苍赫配合；每局开一次领域，领域里念 200% 茈；
 *   远了才念最大输出的赫；开着展延的宿傩贴身出拳时一半概率闪开；瞬移连段好了就用。
 * - 熟练：和标准一样的打法，但 12–18 tick 反应；开着展延的宿傩出拳每次都闪开，贴上来先用苍或赫控住、推开。
 */
final class GojoBot {
    static final int BASIC = 0, STANDARD = 1, SKILLED = 2;
    static final String[] NAMES = {"基础", "标准", "熟练"};

    private final int level, reactMin, reactMax;
    private final CombatRng rng;
    private int worldSeenAt = -1, worldReactAt, purpleHold, redHold, punchSeenAt = -1, punchReactAt;
    private boolean punchDodge;
    private boolean domainUsed;

    GojoBot(int level, long seed) {
        this.level = level;
        rng = new CombatRng(seed * 31 + 7);
        reactMin = level == SKILLED ? 12 : 24;
        reactMax = level == SKILLED ? 18 : 36;
    }

    void fill(World w, TickInput in) {
        in.clearEdges();
        in.moveX = in.moveY = 0;
        in.blueHeld = false;
        Fighter g = w.gojo, s = w.sukuna;
        float dx = s.x - g.x, d = Math.abs(dx);
        int toward = dx >= 0 ? 1 : -1;
        boolean free = g.state == Fighter.FREE && g.hitstun == 0;
        boolean sukunaStuck = !w.sukunaCanAct() || s.state == Fighter.KO;

        // 按住着的招：茈（念咒）、赫（最大输出）
        if (purpleHold > 0) {
            if (g.state == Fighter.PURPLE_WINDUP || g.state == Fighter.PURPLE_CHANT) {
                in.purpleHeld = true;
                return;
            }
            purpleHold = 0;
        }
        if (redHold > 0) {
            redHold--;
            in.redHeld = g.state == Fighter.RED_HOLD || g.state == Fighter.RED_CHANT;
            if (in.redHeld) return;
        }

        // 世界斩：看到起手，过了反应时间就处理
        if (level != BASIC && s.state == Fighter.S_WORLD) {
            if (worldSeenAt < 0) {
                worldSeenAt = w.tick;
                worldReactAt = w.tick + range(reactMin, reactMax);
            }
            if (w.tick >= worldReactAt) {
                int remaining = w.worldWindup - s.stateTick;
                if (d <= Tuning.MELEE_REACH && free && w.meleeCd == 0) {
                    in.melee = true; // 贴着：打断
                    return;
                }
                if (g.onGround && remaining <= 30 && remaining >= 15) {
                    in.jump = true; // 算好时间跳过斩带
                    return;
                }
                if (remaining < 15 && free && w.redCd == 0 && d < Tuning.RED_REACH) in.red = true; // 来不及跳：试试用赫打断
                in.moveX = 0;
                return;
            }
        } else {
            worldSeenAt = -1;
        }

        if (!free) return;

        // 开着展延的宿傩出拳：看到起手、过了反应时间就闪开（标准一半概率，熟练都闪）
        if (level != BASIC && s.state == Fighter.S_MELEE && w.ampTicks > 0) {
            if (punchSeenAt < 0) {
                punchSeenAt = w.tick;
                punchReactAt = w.tick + range(reactMin, reactMax) / 3; // 出拳很快：只有起手那几 tick
                punchDodge = level == SKILLED || rng.nextUnitDouble() < 0.5;
            }
            if (punchDodge && w.tick >= punchReactAt && w.dodgeCd == 0 && s.stateTick <= Tuning.S_MELEE_STARTUP) {
                in.dodge = true;
                in.moveX = -toward;
                punchDodge = false;
                return;
            }
        } else {
            punchSeenAt = -1;
        }
        // 瞬移连段：标准、熟练好了就用（按住闪避点苍；苍在冷却就点赫）
        if (level != BASIC && w.tpCd == 0 && !sukunaStuck && s.state != Fighter.KO) {
            if (w.blueCd == 0) {
                in.dodgeHeld = true;
                in.blueDown = in.blueUp = true;
                return;
            }
            if (w.redCd == 0) {
                in.dodgeHeld = true;
                in.red = true;
                return;
            }
        }

        // 熟练：开着展延的宿傩贴上来，先用苍或赫把他控住、推开
        if (level == SKILLED && w.ampTicks > 0 && d < 200 && !sukunaStuck) {
            if (w.blueCd == 0 && w.blueFailLock == 0) {
                in.blueDown = in.blueUp = true;
                return;
            }
            if (w.redCd == 0 && g.facing == toward) {
                in.red = true;
                return;
            }
        }

        // 领域：标准、熟练每局开一次
        if (level != BASIC && !domainUsed && w.domainCd == 0 && w.domainTicks == 0 && d < 600) {
            in.domain = true;
            domainUsed = true;
            return;
        }
        // 200% 茈：标准在领域里念；熟练只在宿傩被控时念
        boolean chantOk = level != BASIC && w.domainTicks > 300;
        if (chantOk && w.purpleCd == 0 && w.purpleFailLock == 0) {
            in.purple = in.purpleHeld = true;
            purpleHold = 1;
            return;
        }
        // 普通茈：朝着宿傩就放
        if (w.purpleCd == 0 && g.facing == toward && d > 150) {
            in.purple = true;
            return;
        }
        // 赫：射程内点按，远了按住放最大输出
        if (w.redCd == 0 && g.facing == toward) {
            if (d < Tuning.RED_REACH) {
                in.red = true;
                return;
            }
            boolean safe = level != BASIC && d > 600 && w.ampTicks == 0;
            if (safe) { // 最大输出要念 0.9 秒：远了（或宿傩被控住）才念
                in.red = in.redHeld = true;
                redHold = Tuning.RED_HOLD + Tuning.CHANT3_PHRASE * Tuning.CHANT3_PHRASES + 8;
                return;
            }
        }
        // 苍：中距离丢过去（基础偶尔）
        if (w.blueCd == 0 && w.blueFailLock == 0 && d > 140 && d < 380 && (level != BASIC || rng.nextUnitDouble() < 0.02)) {
            in.blueDown = in.blueUp = true;
            return;
        }
        // 体术：够得着就打；熟练在宿傩回血或世界斩收招时贴上去
        if (d <= Tuning.MELEE_REACH) {
            if (w.meleeCd == 0) in.melee = true;
            return;
        }
        in.moveX = toward;
    }

    private int range(int lo, int hi) {
        return lo + (int) (rng.nextUnitDouble() * (hi - lo + 1));
    }
}
