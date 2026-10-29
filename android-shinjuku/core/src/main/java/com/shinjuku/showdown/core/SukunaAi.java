package com.shinjuku.showdown.core;

/**
 * 宿傩的 AI（标准难度“三轮”的结构；更高难度改 BattleConfig.AiTuning 的参数）。
 * 只输出意图（走、跳、体术、解），能不能做由 World 的动作执行器判断。全部用整数倒计时和自己的固定种子随机数，结果可复现。
 * - 被控住（倒地、硬直、被苍吸住/抓住、领域里、被打飞还没落地）：什么都不想；脱离控制后迟疑一会儿。
 * - 看到新威胁（飞来的赫、茈、正在落下的苍、正在扩张的无指向茈）：过一段反应时间才处理，有一定概率没反应过来；反应过来就后退或跳。
 * - 平时在“贴身压制”和“拉开距离用解”两种打法之间切换，每种保持一段时间；距离管理有滞回，不会左右抖。
 * - 每隔一段时间考虑一次出手：近身多用体术，中距离用解。
 */
final class SukunaAi {
    private final BattleConfig.AiTuning t;
    private final CombatRng rng;

    private int decide, attackTimer, holdTimer, react = -1, jumpCool, hesitate;
    private boolean wasLocked, dodgePlanned;
    private int lastThreat = -1;
    private boolean approachMode = true;
    private int modeTimer;
    private float moveIntent;
    private boolean wantMelee, wantDismantle, wantJump;

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
            wantMelee = wantDismantle = wantJump = false;
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
        if (s.state != Fighter.FREE) return; // 动作执行中，不打断
        if (g.state == Fighter.KO) return;

        float dxs = g.x - s.x, d = Math.abs(dxs);
        int toward = dxs >= 0 ? 1 : -1;

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

        if (--decide <= 0) {
            decide = t.decideInterval;
            if (--modeTimer <= 0) {
                approachMode = rng.nextUnitDouble() < t.approachWeight;
                modeTimer = range(30, 60); // 以决策次数计：约 1.5–3 秒
            }
            // 距离管理（有滞回）
            if (holdTimer <= 0) {
                float lo = approachMode ? t.nearMin : t.midMin, hi = approachMode ? t.nearMax : t.midMax;
                if (d > hi + t.hysteresis) moveIntent = toward;
                else if (d < lo - t.hysteresis) moveIntent = -toward;
                else if (d >= lo && d <= hi) moveIntent = 0;
                holdTimer = range(t.holdMin, t.holdMax) / 2;
            }
            // 出手
            attackTimer -= t.decideInterval;
            if (attackTimer <= 0) {
                attackTimer = range(t.attackMin, t.attackMax);
                if (rng.nextUnitDouble() < t.attackChance) {
                    if (d <= Tuning.S_MELEE_REACH + 6) {
                        if (rng.nextUnitDouble() < t.meleeWeight) wantMelee = true;
                        else wantDismantle = true;
                    } else if (d <= Tuning.DISMANTLE_RANGE) {
                        wantDismantle = true;
                    }
                }
            }
        }
        if (holdTimer > 0) holdTimer--;
        // 背后是墙就别再退
        if (moveIntent * toward < 0 && (s.x < Tuning.BODY_HALF_W + 8 || s.x > Tuning.WIDTH - Tuning.BODY_HALF_W - 8)) moveIntent = 0;

        out.moveX = moveIntent;
        out.jump = wantJump;
        out.melee = wantMelee;
        out.dismantle = wantDismantle;
        // 出手、跳只发一次（执行器当 tick 不收也就算了，下一次决策再说）
        wantJump = wantMelee = wantDismantle = false;
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
