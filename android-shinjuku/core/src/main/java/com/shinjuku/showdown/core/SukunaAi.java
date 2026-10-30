package com.shinjuku.showdown.core;

/**
 * 宿傩的 AI（标准难度“三轮”的结构；更高难度改 BattleConfig.AiTuning 的参数）。
 * 只输出意图（走、跳、体术、解、开关领域展延、世界斩、反转术式），能不能做由 World 的动作执行器判断。
 * 全部用整数倒计时和自己的固定种子随机数，结果可复现；只看画面上看得到的东西（招式、念咒），不读玩家的输入。
 * - 被控住（倒地、硬直、被苍吸住/抓住、领域里、被打飞还没落地）：什么都不想；脱离控制后迟疑一会儿。
 * - 看到新威胁（飞来的赫、茈、正在落下的苍、正在扩张的无指向茈）：过一段反应时间才处理，有一定概率没反应过来；反应过来就后退或跳。
 * - 看到五条悟念 200% 茈：反应过来就去打断（够得着就打，否则用解；碰到就算打断，被无限挡住也一样）。
 * - 两种打法换着来：开领域展延贴身打（只有展延下的体术能破无限）；或者在中距离用解试探，攒魔虚罗的适应。
 * - 学会世界斩以后，在中远距离按一定概率念世界斩；血少、离得远、没有来招时用反转术式回血。
 */
final class SukunaAi {
    private final BattleConfig.AiTuning t;
    private final CombatRng rng;

    private int decide, attackTimer, holdTimer, react = -1, jumpCool, hesitate, chantReact = -1;
    private boolean wasLocked, dodgePlanned, chantSeen;
    private int lastThreat = -1;
    private boolean approachMode = true;
    private int modeTimer;
    private float moveIntent;
    private boolean wantMelee, wantDismantle, wantJump, wantAmp, wantWorld, wantRct;

    SukunaAi(BattleConfig.AiTuning t, CombatRng rng) {
        this.t = t;
        this.rng = rng;
        attackTimer = t.attackMin;
    }

    void think(World w, SukunaIntent out) {
        out.clear();
        Fighter s = w.sukuna, g = w.gojo;
        if (jumpCool > 0) jumpCool--;
        if (!w.sukunaCanAct()) {
            wasLocked = true;
            react = -1;
            clearWants();
            return;
        }
        if (wasLocked) { // 脱离控制后迟疑一会儿
            wasLocked = false;
            hesitate = range(t.recoverMin, t.recoverMax);
        }
        if (hesitate > 0) {
            hesitate--;
            return;
        }
        if (s.state != Fighter.FREE || w.sStagger > 0) return; // 动作执行中，不打断
        if (g.state == Fighter.KO) return;

        float dxs = g.x - s.x, d = Math.abs(dxs);
        int toward = dxs >= 0 ? 1 : -1;
        boolean amp = w.ampTicks > 0;

        // 威胁：看到新的就排一个反应时间
        int threat = threatId(w, s);
        if (threat >= 0 && threat != lastThreat) {
            lastThreat = threat;
            if (rng.nextUnitDouble() >= t.mistake) {
                react = range(t.reactMin, t.reactMax);
                dodgePlanned = rng.nextUnitDouble() < t.dodge;
            }
        }
        if (react > 0 && --react == 0 && dodgePlanned) {
            if (s.onGround && jumpCool == 0 && rng.nextUnitDouble() < 0.5) {
                wantJump = true;
                jumpCool = t.jumpGap;
            } else {
                moveIntent = -toward;
                holdTimer = range(t.holdMin, t.holdMax);
            }
        }

        // 五条悟在念 200% 茈：反应过来就去打断
        if (g.state == Fighter.PURPLE_CHANT) {
            if (!chantSeen) {
                chantSeen = true;
                chantReact = rng.nextUnitDouble() < t.mistake ? -1 : range(t.reactMin, t.reactMax);
            }
        } else {
            chantSeen = false;
            chantReact = -1;
        }
        if (chantReact > 0) chantReact--;
        if (chantSeen && chantReact == 0) {
            if (d <= Tuning.S_MELEE_REACH + 6) {
                wantMelee = true;
            } else if (amp) {
                moveIntent = toward; // 展延中：贴上去打
                holdTimer = 0;
            } else if (d <= Tuning.DISMANTLE_RANGE && w.ampLock == 0) {
                wantDismantle = true;
            } else {
                moveIntent = toward;
                holdTimer = 0;
            }
        }

        boolean heal = wantsHeal(w, s, d);
        if (--decide <= 0) {
            decide = t.decideInterval;
            if (--modeTimer <= 0) {
                approachMode = rng.nextUnitDouble() < (w.worldUnlocked ? t.approachWeight : t.approachWeightAdapting);
                modeTimer = range(30, 60); // 以决策次数计：约 1.5–3 秒
            }
            if (amp) approachMode = true;
            // 反转术式：血少、离得够远、没有来招
            if (heal && d >= t.rctDistance && threat < 0 && w.rctCd == 0) {
                wantRct = true;
            } else if (w.worldUnlocked && w.worldCd == 0 && !amp && w.ampLock == 0 && s.onGround
                    && d >= t.worldMin && d <= t.worldMax && rng.nextUnitDouble() < t.worldChance) {
                wantWorld = true;
            } else if (!amp && w.ampCd == 0 && s.onGround && d <= t.ampMax && (approachMode || d < t.ampMin)) {
                wantAmp = true; // 准备贴身、或者已经被贴身：开展延（贴身时打到第三下以后五条悟的轻拳就打不断起手）
            }
            // 距离管理（有滞回）：展延中贴身；准备开展延时走到开展延的距离；否则保持中距离；想回血时拉开
            if (holdTimer <= 0) {
                float lo, hi;
                if (heal && w.rctCd == 0) {
                    lo = t.rctDistance;
                    hi = Tuning.WIDTH;
                } else if (w.worldUnlocked && w.worldCd == 0 && !amp) { // 世界斩能放：拉到中距离
                    lo = t.worldMin + 60;
                    hi = t.worldMin + 400;
                } else if (amp) {
                    lo = t.nearMin;
                    hi = t.nearMax;
                } else if (approachMode && w.ampCd == 0) {
                    lo = t.ampMin;
                    hi = t.ampMax;
                } else {
                    lo = t.midMin;
                    hi = t.midMax;
                }
                if (d > hi + t.hysteresis) moveIntent = toward;
                else if (d < lo - t.hysteresis) moveIntent = -toward;
                else if (d >= lo && d <= hi) moveIntent = 0;
                holdTimer = range(t.holdMin, t.holdMax) / 2;
            }
            if (amp && d > Tuning.S_MELEE_REACH) moveIntent = toward; // 展延中够不着就一直往前贴（不留滞回的死区）
            // 出手：展延中够得着就打（不等出手计时）；没开展延就用解试探（攒适应）
            if (amp && d <= Tuning.S_MELEE_REACH + 6 && w.sMeleeCd == 0 && rng.nextUnitDouble() < t.attackChance) wantMelee = true;
            attackTimer -= t.decideInterval;
            if (attackTimer <= 0) {
                attackTimer = range(t.attackMin, t.attackMax);
                if (rng.nextUnitDouble() < t.attackChance) {
                    if (amp) {
                        if (d <= Tuning.S_MELEE_REACH + 6) wantMelee = true;
                    } else if (d <= Tuning.DISMANTLE_RANGE && w.ampLock == 0) {
                        wantDismantle = true;
                    }
                }
            }
            // 还没学会世界斩：展延冷却的空当里多用解试探
            if (!amp && !w.worldUnlocked && w.ampCd > 0 && d <= Tuning.DISMANTLE_RANGE && w.ampLock == 0 && w.dismantleCd == 0
                    && rng.nextUnitDouble() < t.attackChance * 0.25f) wantDismantle = true;
        }
        if (holdTimer > 0) holdTimer--;
        // 背后是墙就别再退
        if (moveIntent * toward < 0 && (s.x < Tuning.BODY_HALF_W + 8 || s.x > Tuning.WIDTH - Tuning.BODY_HALF_W - 8)) moveIntent = 0;

        out.moveX = moveIntent;
        out.jump = wantJump;
        out.melee = wantMelee;
        out.dismantle = wantDismantle;
        out.amp = wantAmp;
        out.world = wantWorld;
        out.rct = wantRct;
        // 出手、跳只发一次（执行器当 tick 不收也就算了，下一次决策再说）
        clearWants();
    }

    private boolean wantsHeal(World w, Fighter s, float d) {
        return w.rctUses < Tuning.S_RCT_MAX_USES && w.ampTicks == 0
                && s.hpMilli <= s.maxHpMilli * t.rctHp && s.maxHpMilli - s.hpMilli >= Tuning.S_RCT_HEAL_PER_TICK_MILLI * Tuning.S_RCT_DURATION;
    }

    private void clearWants() {
        wantJump = wantMelee = wantDismantle = wantAmp = wantWorld = wantRct = false;
    }

    /** 当前朝宿傩来的威胁的编号（没有返回 -1）。 */
    private static int threatId(World w, Fighter s) {
        if (w.red.active && (s.x - w.red.x) * w.red.dir > 0) return w.red.instanceId;
        if (w.purple.active && (s.x - w.purple.x) * w.purple.dir > 0) return w.purple.instanceId;
        if (w.blue.phase == BlueOrb.FLY) return w.blue.instanceId;
        if (w.hollow.active) return w.hollow.instanceId;
        return -1;
    }

    private int range(int lo, int hi) {
        return lo + (int) (rng.nextUnitDouble() * (hi - lo + 1));
    }
}
