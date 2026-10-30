package com.shinjuku.showdown.core;

/**
 * 平衡模拟用的五条悟脚本：只看画面上看得到的东西，按固定策略按键。三档（Astra 的验收方案）：
 * - 基础：贴身打体术，近中距离赫，偶尔苍；不躲世界斩、不用领域、不念咒、不闪避、不瞬移。
 * - 标准：24–36 tick 反应；看到世界斩起手就跳（或贴着就打断）；苍赫配合；每局开一次领域，领域里念 200% 茈；
 *   远了才念最大输出的赫；开着展延的宿傩贴身出拳时一半概率闪开；瞬移连段好了就用。
 * - 熟练：和标准一样的打法，但 12–18 tick 反应；开着展延的宿傩出拳每次都闪开，贴上来先用苍或赫控住、推开。
 * 魔虚罗（Astra 的方案）：基础不管它（体术照样打到够得着的最近一个）。标准看到它 24–36 tick 后把它当优先目标：茈好了就转过去放
 * （850 正好打掉）；领域里也先用普通茈打它。熟练 12–18 tick 就反应，先等最多 60 tick，找宿傩和魔虚罗在同一边、一发茈穿两个的机会。
 * 适应了无限的魔虚罗出招：标准一半概率闪开，熟练都闪。最大输出赫的“安全”也要看魔虚罗和顎吐。
 * v0.9：标准、熟练的瞬移在茈好了时优先用瞬移·茈（念完咒词闪到背后贴脸一发）；领域里苍、赫、茈都好了就念茈·最大输出（同时按住苍和赫）。
 */
final class GojoBot {
    static final int BASIC = 0, STANDARD = 1, SKILLED = 2;
    static final String[] NAMES = {"基础", "标准", "熟练"};

    private final int level, reactMin, reactMax;
    private final CombatRng rng;
    private int worldSeenAt = -1, worldReactAt, purpleHold, redHold, maxHold, punchSeenAt = -1, punchReactAt;
    private boolean punchDodge;
    private int mahoSeenAt = -1, mahoReactAt, mHitReactAt;
    private boolean mDodge, mSwingSeen;
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

        // 按住着的招：茈·最大输出（苍和赫一起按着）、茈（念咒）、赫（最大输出）
        if (maxHold > 0) {
            if (g.state == Fighter.RED_HOLD || g.state == Fighter.BLUE_HOLD || g.state == Fighter.PURPLE_MAX_CHANT) {
                in.blueHeld = in.redHeld = true;
                return;
            }
            maxHold = 0;
        }
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
        // 魔虚罗（标准、熟练）：看到它过一会儿才反应；适应了无限的魔虚罗出招时闪开
        Fighter m = w.mahoraga;
        boolean maho = m.present && level != BASIC;
        if (maho) {
            if (mahoSeenAt < 0) {
                mahoSeenAt = w.tick;
                mahoReactAt = w.tick + (level == SKILLED ? range(12, 18) : range(24, 36));
            }
            boolean swinging = m.state == Fighter.M_PUNCH || m.state == Fighter.M_SWORD;
            if (!swinging) mSwingSeen = false;
            if (swinging && w.mahoragaAdapted) {
                if (!mSwingSeen) { // 每一招只抽一次签
                    mSwingSeen = true;
                    mHitReactAt = w.tick + range(reactMin, reactMax) / 2;
                    mDodge = level == SKILLED || rng.nextUnitDouble() < 0.5;
                }
                int su = m.state == Fighter.M_SWORD ? Tuning.M_SWORD_STARTUP : Tuning.M_PUNCH_STARTUP;
                float reach = m.state == Fighter.M_SWORD ? Tuning.M_SWORD_REACH : Tuning.M_PUNCH_REACH;
                if (mDodge && w.tick >= mHitReactAt && w.dodgeCd == 0 && m.stateTick <= su && Math.abs(m.x - g.x) <= reach + 40) {
                    in.dodge = true;
                    in.moveX = m.x > g.x ? -1 : 1;
                    mDodge = false;
                    return;
                }
            }
        } else {
            mahoSeenAt = -1;
        }
        boolean mahoTarget = maho && w.tick >= mahoReactAt;
        if (mahoTarget && w.purpleCd == 0 && purpleSafe(w)) {
            int mdir = m.x >= g.x ? 1 : -1;
            boolean both = (s.x - g.x) * mdir > 0 && s.state != Fighter.KO; // 宿傩也在同一边：一发穿两个
            if (both || level != SKILLED || w.tick >= mahoReactAt + 60) {
                in.moveX = mdir; // 这一 tick 转过去再放
                in.purple = true;
                return;
            }
        }

        // 瞬移连段：标准、熟练好了就用（茈好了就按住闪避点茈；不然点苍；苍在冷却就点赫）
        if (level != BASIC && w.tpCd == 0 && !sukunaStuck && s.state != Fighter.KO) {
            if (w.purpleCd == 0 && w.purpleFailLock == 0 && purpleSafe(w) && !(w.domainTicks > 0 && maxReady(w))) {
                in.dodgeHeld = true;
                in.purple = true;
                return;
            }
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
        // 茈·最大输出：领域里苍、赫、茈都好了就念（宿傩动不了，十段咒词念得完）
        if (level != BASIC && w.domainTicks > 500 && maxReady(w)) {
            in.blueDown = in.blueHeld = true;
            in.red = in.redHeld = true;
            maxHold = 1;
            return;
        }
        // 200% 茈：标准在领域里念；熟练只在宿傩被控时念（魔虚罗在场时先用普通茈打它）
        boolean chantOk = level != BASIC && w.domainTicks > 300 && !maho;
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
            boolean safe = level != BASIC && d > 600 && w.ampTicks == 0 && !w.agito.active
                    && (!m.present || Math.abs(m.x - g.x) > 400);
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

    private static boolean maxReady(World w) {
        return w.blueCd == 0 && w.redCd == 0 && w.purpleCd == 0 && w.blueFailLock == 0 && w.redFailLock == 0 && w.purpleFailLock == 0;
    }

    /** 放茈的起手（30 tick）会不会被打断：开着展延的宿傩或者适应了无限的魔虚罗贴得太近就先别放。 */
    private static boolean purpleSafe(World w) {
        Fighter g = w.gojo, s = w.sukuna, m = w.mahoraga;
        if (w.ampTicks > 0 && Math.abs(s.x - g.x) < 160) return false;
        return !(m.present && w.mahoragaAdapted && Math.abs(m.x - g.x) < 240);
    }

    private int range(int lo, int hi) {
        return lo + (int) (rng.nextUnitDouble() * (hi - lo + 1));
    }
}
