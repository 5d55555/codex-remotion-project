package com.shinjuku.showdown.core;

/**
 * 宿傩的 AI。三轮（标准）用下面这套基础结构；里梅、万、史上最强在基础结构上再开“战术层”（tacticLevel 1–3，越高越聪明，
 * Astra 的方案，见 notes/宿傩AI.md）：
 * - 里梅：进退试探（走近一下再退开，看五条悟怎么应对）；看到念咒（苍、赫、茈）和瞬移的前摇会去打断；抓长后摇；被连打时想办法脱身；
 *   世界斩只在估计五条悟来不及打断时才念（按看到过的招推算冷却）。
 * - 万：再加开着展延停在边缘骗闪避、骗出招，看到闪避等无敌过了再追；够不着太久就关掉展延改用解。
 * - 史上最强：再加读习惯——记下五条悟在“展延贴近”时的第一反应（闪避、出招、跳、退、不动），习惯明显就针对着改节奏。
 * 每个机会只抽一次签、只排一次反应时间，不会每 tick 重抽。
 * 只输出意图（走、跳、体术、解、开关领域展延、世界斩、反转术式），能不能做由 World 的动作执行器判断。
 * 全部用整数倒计时和自己的固定种子随机数，结果可复现；只看画面上看得到的东西（招式、念咒），不读玩家的输入。
 * - 被控住（倒地、硬直、被苍吸住/抓住、领域里、被打飞还没落地）：什么都不想；脱离控制后迟疑一会儿。
 * - 看到新威胁（飞来的赫、茈、正在落下的苍、正在扩张的无指向茈）：过一段反应时间才处理，有一定概率没反应过来；反应过来就后退或跳。
 * - 看到五条悟念 200% 茈：反应过来就去打断（够得着就打，否则用解；碰到就算打断，被无限挡住也一样）。
 * - 两种打法换着来：开领域展延贴身打（只有展延下的体术能破无限）；或者在中距离用解试探，攒魔虚罗的适应。
 * - 学会世界斩以后，在中远距离按一定概率念世界斩；血少、离得远、没有来招时用反转术式回血。
 * 十种影法术与原身（能不能用看 BattleConfig：三轮只有顎吐，里梅以上还有魔虚罗和原身；Astra 的方案）：
 * - 优先级：血线到了就变原身 → 处理飞来的招 → 安全的时候召唤魔虚罗 → 打断、抓破绽 → 平常的打法。
 * - 原身：血量跌破血线就变（魔虚罗不在场时马上变；在场时先等它，到紧急血线或等太久就不等了）。
 * - 魔虚罗：打了够久、血量不太满、离得够远、估计五条悟来不及打断咏唱时才召唤。
 * - 顎吐：中距离牵制（按难度的概率），或者念咒还剩够长时用来打断；魔虚罗在场时只用来打断。三轮没有解：顎吐冷却时就去贴身。
 * 恐惧（v0.11，四个难度都有；Astra 的方案，见 notes/宿傩AI.md）：每 1 秒一个窗口，开头按看得到的局势估这一秒会挨多少，
 * 结尾和实际挨到的比：多挨了恐惧涨、少挨了降。恐惧越高越不想出手、越攒着技能（展延、解、顎吐、世界斩、召唤、反转术式都打折扣，
 * 被拒绝后过一会儿才再想），60 以上一组一组地连连后退。打断大招（200% 茈、茈·最大输出、瞬移·茈）和脱身不受影响。
 * 空间斩（里梅以上）：恐惧低于 30、打了 20 秒以上、血量 25% 以下才会想；每次认真考虑都“看到代价”涨一点恐惧，恐惧越高越不愿意放。
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
    private boolean wantMelee, wantDismantle, wantJump, wantAmp, wantAmpOff, wantWorld, wantRct, wantAgito, wantSummon, wantTransform;
    private int transformWaitFrom = -1; // 血量第一次跌破变原身血线的时刻（回血也不重置）
    private boolean wantSpace;

    // ---- 恐惧（四个难度都有）----
    private int fear = Tuning.FEAR_START;     // 0–10000
    private int fearTick, fearExpect;         // 窗口走到第几 tick；窗口开头估的这一秒会挨多少（血）
    private boolean fearStarted;
    private long fearDmg0;                    // 窗口开头宿傩已经挨了多少（毫血）
    int lastExpect = -1, lastActual, windows; // 上一个窗口估的和实际挨的（血）、结算过几个窗口（模拟统计用）
    int spaceThinks;                          // 认真考虑过几次空间斩（模拟统计用）
    private boolean panic;                    // 惊惧：60 以上进入、45 以下退出
    private int retreatLeft, retreatTimer, retreatPause, retreatCool; // 这一组还剩几段、这一段还剩多久、段间停顿、两组之间
    private int ampRefuse, summonRefuse, rctRefuse, worldRefuse;      // 怕得不想用：过多久再想
    private int spaceThinkAt = -100_000, spaceThinkFear = Tuning.FEAR_MAX; // 上次认真考虑空间斩的时刻、那时的恐惧

    // ---- 战术层（里梅以上）----
    private int aiClock;                       // think() 跑了多少次（只在逻辑推进时走）
    private int gSeenState = -1, gSince;       // 看到的五条悟动作、从什么时候开始
    private static final int OPP_NONE = 0, OPP_INTERRUPT = 1, OPP_PUNISH = 2, OPP_DODGE = 3;
    private int opp, oppReadyAt, oppUntil, oppGState, oppSince; // 一个待处理的机会（每个只抽一次签）
    private int seenTp = -100_000, seenRed = -100_000, seenBlue = -100_000, seenPurple = -100_000; // 看到五条悟用过（推算冷却）
    private int probePhase, probeTimer, probeCool, baitTimer, baitCool, escapeCool, farTicks, worldRecheck;
    private float prevD = 9999;
    private boolean kiteRolled, kiting, vaultRolled, vaulting;
    // 读习惯（史上最强）：展延贴近时五条悟的第一反应，最近 16 次
    private static final int R_DODGE = 0, R_ATTACK = 1, R_JUMP = 2, R_BACK = 3, R_NONE = 4;
    private final int[] habit = new int[16];
    private int habitN, habitHead, watchUntil = -1;
    private float watchD;

    SukunaAi(BattleConfig.AiTuning t, CombatRng rng) {
        this.t = t;
        this.rng = rng;
        attackTimer = t.attackMin;
    }

    int fear() {
        return fear;
    }

    /** 恐惧 0–1。 */
    private float f() {
        return fear / (float) Tuning.FEAR_MAX;
    }

    void think(World w, SukunaIntent out) {
        out.clear();
        Fighter s = w.sukuna, g = w.gojo;
        if (jumpCool > 0) jumpCool--;
        if (ampRefuse > 0) ampRefuse--;
        if (summonRefuse > 0) summonRefuse--;
        if (rctRefuse > 0) rctRefuse--;
        if (worldRefuse > 0) worldRefuse--;
        if (retreatCool > 0) retreatCool--;
        observe(w);   // 被控住的时候也在看（三轮只记五条悟用过什么招，不登记战术机会）
        fearStep(w);  // 受控、出招中也照样算
        // 变原身的等待从血量第一次跌破血线算起（被控住、正在出招时也记）
        if (transformWaitFrom < 0 && w.config.trueForm && !w.sukunaTrueForm && s.hpMilli <= s.maxHpMilli * t.transformHp) transformWaitFrom = w.fightTicks;
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
        if (wantsTransform(w, s)) { // 变原身：回满血，最优先
            wantTransform = true;
            emit(s, toward, out);
            return;
        }

        // 威胁：看到新的就排一个反应时间
        int threat = threatId(w, s);
        if (threat >= 0 && threat != lastThreat) {
            lastThreat = threat;
            if (rng.nextUnitDouble() >= t.mistake) {
                react = range(t.reactMin, t.reactMax);
                dodgePlanned = rng.nextUnitDouble() < t.dodge + (1 - t.dodge) * 0.3f * f(); // 怕了更愿意躲
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

        // 五条悟在念 200% 茈、茈·最大输出或瞬移·茈的咒词：反应过来就去打断（里梅以上由战术层统一处理苍、赫、茈的咏唱）
        if (t.tacticLevel == 0 && (g.state == Fighter.PURPLE_CHANT || g.state == Fighter.PURPLE_MAX_CHANT
                || g.state == Fighter.TP_WINDUP && w.tpKind() == Attack.PURPLE)) {
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
            } else if (w.config.shrine && d <= Tuning.DISMANTLE_RANGE && w.ampLock == 0) {
                wantDismantle = true;
            } else if (agitoReady(w) && d <= Tuning.AGITO_RANGE
                    && (g.state == Fighter.PURPLE_MAX_CHANT ? MAX_CHANT_TICKS : Tuning.CHANT_PHRASE * Tuning.CHANT_PHRASES) - g.stateTick
                    > Tuning.AGITO_SIGN + Tuning.AGITO_DIVE + 2) {
                wantAgito = true; // 十影宿傩没有解：念咒还剩够长就放顎吐去打断
            } else {
                moveIntent = toward;
                holdTimer = 0;
            }
        }

        boolean heal = wantsHeal(w, s, d);
        // 惊惧：连连后退（大招的打断、被连打时的脱身在战术层里，先让给它们）
        boolean urgent = opp == OPP_INTERRUPT || s.comboHits >= Tuning.COMBO_STUN_HITS && s.comboTimer > 0 && d < 150;
        if (!urgent && retreat(s, d, toward)) {
            emit(s, toward, out);
            return;
        }
        if (wantsSpace(w, s, g, d, threat)) { // 空间斩：赌命的一刀
            wantSpace = true;
            emit(s, toward, out);
            return;
        }
        if (summonRefuse == 0 && wantsSummon(w, s, d, threat, amp)) { // 召唤魔虚罗（只在估计他来不及打断时；怕了就先不召）
            if (rng.nextUnitDouble() < 1 - 0.7f * f()) {
                wantSummon = true;
                emit(s, toward, out);
                return;
            }
            summonRefuse = 240;
        }
        if (t.tacticLevel > 0 && tactic(w, s, g, d, toward, amp, threat)) {
            emit(s, toward, out);
            return;
        }
        if (--decide <= 0) {
            decide = t.decideInterval;
            if (--modeTimer <= 0) {
                approachMode = rng.nextUnitDouble() < (w.worldUnlocked ? t.approachWeight : t.approachWeightAdapting) * (1 - 0.7f * f());
                modeTimer = range(30, 60); // 以决策次数计：约 1.5–3 秒
            }
            if (amp) approachMode = true;
            if (!w.config.shrine && w.agitoCd > 0) approachMode = true; // 十影宿傩没有解：顎吐冷却时别在中距离空等，贴上去
            // 反转术式：血少、离得够远、没有来招（怕了也会舍不得用，但不会完全不回血）
            float fk = 1 - f();
            boolean rctTime = heal && d >= t.rctDistance && threat < 0 && w.rctCd == 0 && rctRefuse == 0;
            if (rctTime && rng.nextUnitDouble() >= 1 - 0.35f * f()) {
                rctTime = false;
                rctRefuse = 240;
            }
            if (rctTime) {
                wantRct = true;
            } else if (w.config.shrine && w.worldUnlocked && w.worldCd == 0 && !amp && w.ampLock == 0 && s.onGround && worldRefuse == 0
                    && d >= t.worldMin && d <= t.worldMax && (t.tacticLevel == 0 ? worldFear() && rng.nextUnitDouble() < t.worldChance : worldChance(w, d, threat))) {
                wantWorld = true;
            } else if (!amp && w.ampCd == 0 && s.onGround && d <= t.ampMax && (approachMode || d < t.ampMin) && ampRefuse == 0) {
                // 准备贴身、或者已经被贴身：开展延（贴身时打到第三下以后五条悟的轻拳就打不断起手）；怕了就先攒着
                if (rng.nextUnitDouble() < 1 - 0.7f * f()) wantAmp = true;
                else ampRefuse = 120;
            }
            // 距离管理（有滞回）：展延中贴身；准备开展延时走到开展延的距离；否则保持中距离；想回血时拉开
            if (holdTimer <= 0) {
                float lo, hi;
                if (heal && w.rctCd == 0) {
                    lo = t.rctDistance;
                    hi = Tuning.WIDTH;
                } else if (w.config.shrine && w.worldUnlocked && w.worldCd == 0 && !amp && worldRefuse == 0) { // 世界斩能放、也愿意放：拉到中距离
                    lo = t.worldMin + 60;
                    hi = t.worldMin + 400;
                } else if (amp) {
                    lo = t.nearMin;
                    hi = t.nearMax;
                    if (fear < Tuning.FEAR_PANIC) { // 怕了：贴得没那么紧
                        lo += 20 * f();
                        hi += 35 * f();
                    }
                } else if (approachMode && w.ampCd == 0) {
                    lo = t.ampMin + 120 * f();
                    hi = t.ampMax + 160 * f();
                } else {
                    lo = t.midMin + 120 * f();
                    hi = Math.min(Tuning.WIDTH - 100, t.midMax + 160 * f());
                }
                if (d > hi + t.hysteresis) moveIntent = toward;
                else if (d < lo - t.hysteresis) moveIntent = -toward;
                else if (d >= lo && d <= hi) moveIntent = 0;
                holdTimer = range(t.holdMin, t.holdMax) / 2;
            }
            if (amp && d > Tuning.S_MELEE_REACH) moveIntent = toward; // 展延中够不着就一直往前贴（不留滞回的死区）
            // 出手：展延中够得着就打（不等出手计时）；没开展延就用解试探（攒适应）
            float attack = t.attackChance * (1 - 0.65f * f()); // 怕了不想出手
            if (amp && d <= Tuning.S_MELEE_REACH + 6 && w.sMeleeCd == 0 && rng.nextUnitDouble() < attack) wantMelee = true;
            attackTimer -= t.decideInterval;
            if (attackTimer <= 0) {
                attackTimer = Math.round(range(t.attackMin, t.attackMax) * (1 + 0.75f * f()));
                if (rng.nextUnitDouble() < attack) {
                    if (amp) {
                        if (d <= Tuning.S_MELEE_REACH + 6) wantMelee = true;
                    } else if (w.ampLock == 0) {
                        poke(w, d); // 中距离试探：顎吐（按概率）或解
                    }
                }
            }
            // 还没学会世界斩：展延冷却的空当里多用解试探
            if (w.config.shrine && !amp && !w.worldUnlocked && w.ampCd > 0 && d <= Tuning.DISMANTLE_RANGE && w.ampLock == 0 && w.dismantleCd == 0
                    && rng.nextUnitDouble() < t.attackChance * 0.25f * (1 - 0.8f * f())) wantDismantle = true;
            // 展延冷却的空当：按难度的概率放顎吐牵制（魔虚罗在场时不放）
            else if (!amp && w.ampCd > 0 && agitoReady(w) && !w.mahoraga.present && d >= 180 && d <= Tuning.AGITO_RANGE
                    && rng.nextUnitDouble() < t.agitoChance * 0.25f * (1 - 0.85f * f())) wantAgito = true;
        }
        emit(s, toward, out);
    }

    private void emit(Fighter s, int toward, SukunaIntent out) {
        if (holdTimer > 0) holdTimer--; // 战术层接管的 tick 也照样计时
        // 背后是墙就别再退
        if (moveIntent * toward < 0 && (s.x < Tuning.BODY_HALF_W + 8 || s.x > Tuning.WIDTH - Tuning.BODY_HALF_W - 8)) moveIntent = 0;
        out.moveX = moveIntent;
        out.jump = wantJump;
        out.melee = wantMelee;
        out.dismantle = wantDismantle;
        out.amp = wantAmp;
        out.ampOff = wantAmpOff;
        out.world = wantWorld;
        out.rct = wantRct;
        out.agito = wantAgito;
        out.summon = wantSummon;
        out.transform = wantTransform;
        out.space = wantSpace;
        // 出手、跳只发一次（执行器当 tick 不收也就算了，下一次决策再说）
        clearWants();
    }

    private boolean wantsHeal(World w, Fighter s, float d) {
        float line = t.tacticLevel > 0 && w.rctUses >= 1 ? t.rctSecondHp : t.rctHp; // 里梅以上：第二次留到更低的血线
        return w.rctUses < Tuning.S_RCT_MAX_USES && w.ampTicks == 0
                && s.hpMilli <= s.maxHpMilli * line && s.maxHpMilli - s.hpMilli >= Tuning.S_RCT_TOTAL_MILLI;
    }

    private void clearWants() {
        wantJump = wantMelee = wantDismantle = wantAmp = wantAmpOff = wantWorld = wantRct = wantAgito = wantSummon = wantTransform = wantSpace = false;
    }

    /* ---------------- 十种影法术与原身 ---------------- */

    /** 顎吐现在放得出来吗（有十种影法术、冷却好了、没在飞、没开展延）。 */
    private static boolean agitoReady(World w) {
        return w.config.agito && !w.sukunaTrueForm && w.agitoCd == 0 && !w.agito.active && w.ampTicks == 0 && w.ampLock == 0;
    }

    /** 中距离试探：魔虚罗不在场时按概率放顎吐（180–560），不然有解就用解。 */
    private void poke(World w, float d) {
        if (agitoReady(w) && !w.mahoraga.present && d >= 180 && d <= Tuning.AGITO_RANGE && rng.nextUnitDouble() < t.agitoChance * (1 - 0.85f * f())) {
            wantAgito = true;
        } else if (w.config.shrine && d <= Tuning.DISMANTLE_RANGE && (fear == 0 || rng.nextUnitDouble() < 1 - 0.8f * f())) {
            wantDismantle = true; // 怕了：不放顎吐也不改成解，攒着
        }
    }

    /** 变原身：血量跌破血线（魔虚罗在场时先等它退场；到紧急血线或等太久就不等了）。 */
    private boolean wantsTransform(World w, Fighter s) {
        if (!w.config.trueForm || w.sukunaTrueForm || s.hpMilli == 0) return false;
        float hp = s.hpMilli / (float) s.maxHpMilli;
        if (hp > t.transformHp) return false;
        if (!w.mahoraga.present) return true;
        return hp <= t.transformHpEmergency || w.fightTicks - transformWaitFrom >= t.transformMaxWait;
    }

    /**
     * 召唤魔虚罗：打了够久、血量不太满、离开一段距离、没有来招、他手上没在起招，赫的射程内看到过他用赫（推算冷却）。
     * 冲过来打断、瞬移、苍、茈都留给玩家（被打断不算用掉，5 秒后再试）：只在“绝对安全”时才召唤的话几乎召不出来。
     */
    private boolean wantsSummon(World w, Fighter s, float d, int threat, boolean amp) {
        if (!w.config.mahoraga || w.mahoragaUsed || w.sukunaTrueForm || w.summonCd > 0 || amp || w.ampLock > 0 || !s.onGround || threat >= 0) {
            return false;
        }
        if (w.fightTicks < t.summonMinTicks || s.hpMilli > s.maxHpMilli * t.summonHp || d < t.summonDistance) return false;
        if (gojoWindingUp(w.gojo)) return false;
        boolean redReady = aiClock - seenRed > Tuning.RED_COOLDOWN && d <= Tuning.RED_REACH;
        return !redReady || Tuning.RED_WINDUP + d / Tuning.RED_SPEED * Tuning.HZ + w.gojo.hitstun > Tuning.SUMMON_CHANT + t.summonMargin;
    }

    /* ---------------- 战术层（里梅以上） ---------------- */

    /** 看五条悟的动作：每开始一个新动作，按种类登记一次机会（只抽一次签），顺便记下他用过哪些招、读习惯。 */
    private void observe(World w) {
        aiClock++;
        if (probeCool > 0) probeCool--;
        if (baitCool > 0) baitCool--;
        if (escapeCool > 0) escapeCool--;
        if (worldRecheck > 0) worldRecheck--;
        Fighter g = w.gojo, s = w.sukuna;
        float d = Math.abs(g.x - s.x);
        if (g.state != gSeenState) {
            gSeenState = g.state;
            gSince = aiClock;
            switch (g.state) {
                case Fighter.TP_WINDUP: // 瞬移·茈先念四段咒词：窗口长得多（大招：怕了也照样去打断）
                    seenTp = aiClock;
                    if (w.tpKind() == Attack.PURPLE) offer(OPP_INTERRUPT, t.interruptChance, Tuning.CHANT_PHRASE * Tuning.CHANT_PHRASES, g.state);
                    else offer(OPP_INTERRUPT, t.interruptChance * (1 - 0.25f * f()), Tuning.TP_WINDUP, g.state);
                    break;
                case Fighter.PURPLE_MAX_CHANT: // 茈·最大输出的十段咒词：一定要打断
                    offer(OPP_INTERRUPT, Math.min(1f, t.interruptChance + 0.1f), MAX_CHANT_TICKS, g.state);
                    break;
                case Fighter.BLUE_CHANT:
                case Fighter.RED_CHANT:
                    offer(OPP_INTERRUPT, t.interruptChance * (1 - 0.25f * f()), Tuning.CHANT3_PHRASE * Tuning.CHANT3_PHRASES, g.state);
                    break;
                case Fighter.PURPLE_CHANT:
                    offer(OPP_INTERRUPT, t.interruptChance, Tuning.CHANT_PHRASE * Tuning.CHANT_PHRASES, g.state);
                    break;
                case Fighter.RED_ACTIVE:
                    seenRed = aiClock;
                    break;
                case Fighter.BLUE_RECOVER:
                    seenBlue = aiClock;
                    break;
                case Fighter.PURPLE_RECOVER:
                    seenPurple = aiClock;
                    offer(OPP_PUNISH, t.punishChance * (1 - 0.5f * f()), Tuning.PURPLE_RECOVER, g.state);
                    break;
                case Fighter.FIST_RECOVER:
                    offer(OPP_PUNISH, t.punishChance * (1 - 0.5f * f()), Tuning.FIST_CANCEL, g.state);
                    break;
                case Fighter.RED_RECOVER:
                    offer(OPP_PUNISH, t.punishChance * (1 - 0.5f * f()), Tuning.RED_CANCEL, g.state);
                    break;
                case Fighter.DODGE:
                    if (t.dodgePunishChance > 0) offer(OPP_DODGE, t.dodgePunishChance * (1 - 0.5f * f()), Tuning.DODGE_COOLDOWN, g.state);
                    record(R_DODGE);
                    break;
                case Fighter.MELEE:
                case Fighter.FIST_WINDUP:
                case Fighter.RED_HOLD:
                case Fighter.BLUE_HOLD:
                case Fighter.PURPLE_WINDUP:
                    record(R_ATTACK);
                    break;
                default:
                    break;
            }
        }
        // 读习惯：开着展延贴近（距离从 145 外进到 145 内）时开一个观察窗口，记五条悟的第一反应
        if (t.learnMinSamples > 0) {
            if (w.ampTicks > 0 && prevD > 145 && d <= 145 && watchUntil < 0) {
                watchUntil = aiClock + t.probeObserveTicks;
                watchD = d;
            }
            if (watchUntil >= 0) {
                if (g.vy > 0 && !g.onGround && g.state == Fighter.FREE) record(R_JUMP);
                else if (d > watchD + 40) record(R_BACK);
                else if (aiClock >= watchUntil) record(R_NONE);
            }
        }
        prevD = d;
    }

    /** 观察窗口里的第一反应（同一个窗口只记一次）。 */
    private void record(int r) {
        if (watchUntil < 0) return;
        watchUntil = -1;
        habit[habitHead] = r;
        habitHead = (habitHead + 1) % habit.length;
        if (habitN < habit.length) habitN++;
    }

    /** 习惯成立了吗：样本够、某一种反应占到阈值以上。返回那种反应，没有就是 -1。 */
    private int habitTag() {
        if (t.learnMinSamples == 0 || habitN < t.learnMinSamples) return -1;
        int[] c = counts;
        java.util.Arrays.fill(c, 0);
        for (int i = 0; i < habitN; i++) c[habit[i]]++;
        int best = 0;
        for (int i = 1; i < c.length; i++) if (c[i] > c[best]) best = i;
        return c[best] * 100 >= habitN * Math.round(t.learnThreshold * 100) ? best : -1;
    }

    private final int[] counts = new int[5];

    /** 登记一个机会：抽一次签决定理不理它，排一个反应时间；优先级 打断 > 追闪避 > 抓后摇。 */
    private void offer(int type, float chance, int window, int gState) {
        if (t.tacticLevel == 0) return; // 三轮没有战术层
        if (opp != OPP_NONE && aiClock < oppUntil && priority(opp) > priority(type)) return; // 已有更要紧的
        if (rng.nextUnitDouble() >= chance) return;
        opp = type;
        oppGState = gState;
        oppSince = aiClock;
        oppReadyAt = aiClock + range(t.reactMin, t.reactMax);
        oppUntil = aiClock + window;
    }

    private static int priority(int type) {
        return type == OPP_INTERRUPT ? 3 : type == OPP_DODGE ? 2 : 1;
    }

    /** 战术层这一 tick 接管了就返回 true（已经填好意图和移动）。 */
    private boolean tactic(World w, Fighter s, Fighter g, float d, int toward, boolean amp, int threat) {
        float reach = Tuning.S_MELEE_REACH + 6;
        boolean canMelee = w.sMeleeCd == 0 && w.sAttackGap == 0;
        // 1. 机会：打断前摇和念咒、追闪避、抓后摇
        if (opp != OPP_NONE) {
            boolean valid = aiClock < oppUntil + (opp == OPP_DODGE ? Tuning.DODGE_TICKS : 0) && (opp == OPP_DODGE || g.state == oppGState);
            if (!valid) {
                opp = OPP_NONE;
            } else if (aiClock >= oppReadyAt) {
                switch (opp) {
                    case OPP_INTERRUPT: { // 碰到就算打断（被无限挡住也一样）；只选来得及的招，来不及就放弃
                        int left = oppUntil - aiClock;
                        if (!amp && w.ampLock == 0 && w.dismantleCd == 0 && w.sAttackGap == 0 && d <= Tuning.DISMANTLE_RANGE
                                && Math.abs(g.centerY() - s.centerY()) <= 70 && left > Tuning.DISMANTLE_WINDUP + 1) {
                            wantDismantle = true; // 解最快：2 tick 就斩到
                            opp = OPP_NONE;
                        } else if (d <= reach && canMelee && left > Tuning.S_MELEE_STARTUP + 1) {
                            wantMelee = true;
                            opp = OPP_NONE;
                        } else if (agitoReady(w) && w.sAttackGap == 0 && d <= Tuning.AGITO_RANGE && left > Tuning.AGITO_SIGN + Tuning.AGITO_DIVE + 1) {
                            wantAgito = true; // 念咒还剩够长：放顎吐去碰他一下
                            opp = OPP_NONE;
                        } else if (amp && t.tacticLevel >= 2 && d > reach + 120 && left > Tuning.S_AMP_EXIT + Tuning.DISMANTLE_WINDUP + 8) {
                            wantAmpOff = true; // 太远：关掉展延改用解（关掉后 12 tick 才能用术式，下一轮再看）
                        } else if (left > Tuning.S_MELEE_STARTUP + 1 + (d - reach) / Tuning.SUKUNA_SPEED * Tuning.HZ) {
                            moveIntent = toward; // 走过去还来得及
                        } else {
                            opp = OPP_NONE; // 来不及了：不白白冲上去
                            return false;
                        }
                        return true;
                    }
                    case OPP_PUNISH:
                        if (!amp) { // 没开展延打不到：顺势开展延（怕得刚拒绝过就不开）
                            if (w.ampCd == 0 && s.onGround && d <= t.ampMax + 40 && ampRefuse == 0) wantAmp = true;
                            opp = OPP_NONE;
                            return wantAmp;
                        }
                        if (d <= reach && canMelee) {
                            wantMelee = true;
                            opp = OPP_NONE;
                        } else {
                            moveIntent = toward;
                        }
                        return true;
                    case OPP_DODGE: // 等闪避的无敌过去，在他闪避冷却好之前打一拳
                        if (!amp) {
                            opp = OPP_NONE;
                            return false;
                        }
                        moveIntent = d > reach - 10 ? toward : 0;
                        if (aiClock >= oppSince + Tuning.DODGE_TICKS && d <= reach && canMelee && g.state != Fighter.DODGE) {
                            wantMelee = true;
                            opp = OPP_NONE;
                        }
                        return true;
                    default:
                        break;
                }
            }
        }
        // 2. 被连打：轻拳打到不再硬直以后，想办法脱身
        if (s.comboHits >= Tuning.COMBO_STUN_HITS && s.comboTimer > 0 && escapeCool == 0 && d < 150) {
            escapeCool = t.escapeCd;
            if (rng.nextUnitDouble() < t.escapeChance + (1 - t.escapeChance) * 0.5f * f()) { // 怕了更想脱身
                if (amp && d <= reach && canMelee) {
                    wantMelee = true;
                } else if (!amp && w.ampCd == 0 && s.onGround) {
                    wantAmp = true;
                } else {
                    moveIntent = -toward;
                    holdTimer = 24;
                    if (s.onGround && jumpCool == 0) {
                        wantJump = true;
                        jumpCool = t.jumpGap;
                    }
                }
                return true;
            }
        }
        // 3. 走位：没开展延时被贴身（体术打不到他、他的拳却打得到我），拉开距离；被逼到墙边就从他头上跳过去
        if (!amp && w.ampCd > 0 && d < 150) {
            if (!kiteRolled) {
                kiteRolled = true;
                kiting = rng.nextUnitDouble() < t.kiteChance;
            }
            if (kiting) {
                float wall = -toward > 0 ? Tuning.WIDTH - s.x : s.x; // 往后退那边离墙多远
                if (wall < 90 && !vaultRolled) {
                    vaultRolled = true;
                    vaulting = rng.nextUnitDouble() < t.vaultChance;
                }
                if (wall < 90 && !vaulting) { // 退不动、也不跳：不再拉扯，交给基础打法
                    kiting = false;
                    return false;
                }
                if (vaulting && wall < 90 && s.onGround && jumpCool == 0) {
                    wantJump = true; // 起跳往他那边冲：越过头顶（一跳能越过 150 以内的人）
                    jumpCool = t.jumpGap / 2;
                    moveIntent = toward;
                    holdTimer = 30;
                } else if (holdTimer <= 0 || moveIntent != toward) {
                    moveIntent = -toward;
                }
                return true;
            }
        } else if (d > 200) {
            kiteRolled = vaultRolled = false;
        }
        // 4. 万以上：开着展延却一直够不着，关掉改用解
        if (amp && t.ampExitFarTicks > 0) {
            farTicks = d > reach + 60 ? farTicks + 1 : 0;
            if (farTicks >= t.ampExitFarTicks) {
                farTicks = 0;
                wantAmpOff = true;
                return true;
            }
        }
        // 5. 万以上：开着展延停在边缘（110–145）等一下，骗他先闪避或出招（史上最强按习惯调整）
        if (baitTimer > 0) {
            baitTimer--;
            moveIntent = 0;
            if (g.state != Fighter.FREE) baitTimer = 0; // 他动了：交给上面的机会处理
            return true;
        }
        if (amp && t.ampBaitChance > 0 && baitCool == 0 && !panic && d >= 110 && d <= 145) {
            baitCool = t.ampBaitCd;
            float chance = t.ampBaitChance;
            int tag = habitTag();
            if (tag >= 0 && rng.nextUnitDouble() < t.learnExploitChance) {
                chance = tag == R_DODGE || tag == R_ATTACK ? 0.95f : tag == R_NONE ? 0 : chance; // 习惯闪或抢招：一定骗；不动：直接压
            }
            if (rng.nextUnitDouble() < chance) {
                baitTimer = range(t.baitWaitMin, t.baitWaitMax);
                moveIntent = 0;
                return true;
            }
        }
        // 6. 进退试探：走近一下（不出手）再退开，看他怎么反应
        if (probePhase != 0) {
            if (probePhase == 1) {
                moveIntent = toward;
                if (--probeTimer <= 0 || d <= 115) {
                    probePhase = 2;
                    probeTimer = t.probeOutTicks;
                }
            } else {
                moveIntent = -toward;
                if (--probeTimer <= 0 || d >= 180) probePhase = 0;
            }
            return true;
        }
        if (!amp && threat < 0 && probeCool == 0 && d >= 150 && d <= 240 && s.onGround) {
            probeCool = t.probeCd;
            if (rng.nextUnitDouble() < t.probeChance * (1 - 0.6f * f())) {
                probePhase = 1;
                probeTimer = t.probeInTicks;
                moveIntent = toward;
                return true;
            }
        }
        return false;
    }

    /* ---------------- 恐惧 ---------------- */

    /** 恐惧的窗口：开头估这一秒会挨多少，结尾拿实际挨到的比（反转术式、变原身回的血不抵消挨的打；魔虚罗挨打不算）。 */
    private void fearStep(World w) {
        long now = w.damageTaken[Fighter.SUKUNA];
        if (!fearStarted) {
            fearStarted = true;
            fearDmg0 = now;
            fearExpect = expectedDamage(w);
        }
        if (++fearTick < Tuning.FEAR_WINDOW) return;
        fearTick = 0;
        // 两次 think 之间结算的伤害（包括 AI 停着的固定动作里挨的）都算进这个窗口；结算点直接交接给下一个窗口，不漏一 tick
        settleFear(fearExpect, (int) ((now - fearDmg0) / 1000));
        fearDmg0 = now;
        fearExpect = expectedDamage(w);
    }

    /** 一个窗口结算：估了 expect、实际挨了 actual（血）。 */
    void settleFear(int expect, int actual) {
        lastExpect = expect;
        lastActual = actual;
        windows++;
        int e = actual - expect; // 15 血以内算估得准
        if (e > 15) fear += Math.min(2400, Math.round(t.fearUp * (e - 15)));
        else if (e < -15) fear -= Math.min(1200, Math.round(t.fearDown * (-e - 15)));
        if (actual == 0 && expect <= 50 && fear > Tuning.FEAR_START) { // 安全的一秒：慢慢回到初值
            fear = Math.max(Tuning.FEAR_START, fear - Math.round(t.fearCalm * 100));
        }
        fear = Math.max(0, Math.min(Tuning.FEAR_MAX, fear));
        if (!panic && fear >= Tuning.FEAR_PANIC) {
            panic = true;
        } else if (panic && fear <= Tuning.FEAR_CALM_DOWN) {
            panic = false;
            retreatLeft = 0;
        }
    }

    /**
     * 这一秒大概会挨多少（血）：只看画面上看得到的——已经在发生的（瞬移连段、飞来的招、苍、无指向茈、念到最后的大招），
     * 加上还没出手但可能会用的（贴身的体术；看到用过就按冷却推算，取最大的两项）。权重是 Astra 给的。
     */
    private int expectedDamage(World w) {
        Fighter g = w.gojo, s = w.sukuna;
        if (g.state == Fighter.KO) return 0;
        float d = Math.abs(g.x - s.x);
        boolean stuck = w.domainTicks > 0 || s.captured || s.scripted || s.knockedAir; // 躲不开
        float hit = stuck ? 1f : 0.45f, done = stuck ? 0.95f : 0.6f;
        boolean facing = (s.x - g.x) * g.facing >= 0;
        float e = 0;
        int busy = 0; // 五条悟手上的动作还要占多久（长咏唱占满这一秒的话，打不了拳、也不会再出别的招）
        switch (g.state) { // 已经在发生的固定连段、念到最后一秒的招
            case Fighter.TP_BLUE:
                return Math.round(Tuning.SMASH_DAMAGE_MILLI / 1000f * Tuning.TP_BLUE_HITS);
            case Fighter.TP_RED:
                return (int) (Tuning.TP_RED_DAMAGE_MILLI / 1000);
            case Fighter.TP_PURPLE:
                return (int) (Tuning.TP_PURPLE_DAMAGE_MILLI / 1000);
            case Fighter.PURPLE_MAX_CHANT:
                busy = MAX_CHANT_TICKS - g.stateTick;
                if (busy <= Tuning.FEAR_WINDOW) e += Tuning.PURPLE_MAX_DAMAGE_MILLI / 1000f * done;
                break;
            case Fighter.PURPLE_CHANT:
                busy = Tuning.CHANT_PHRASE * Tuning.CHANT_PHRASES - g.stateTick;
                if (busy <= Tuning.FEAR_WINDOW) e += Tuning.PURPLE_200_DAMAGE_MILLI / 1000f * done * hit;
                break;
            case Fighter.RED_CHANT:
                busy = Tuning.CHANT3_PHRASE * Tuning.CHANT3_PHRASES - g.stateTick;
                if (busy <= Tuning.FEAR_WINDOW) e += Tuning.RED_MAX_DAMAGE_MILLI / 1000f * done * hit;
                break;
            case Fighter.BLUE_CHANT:
                busy = Tuning.CHANT3_PHRASE * Tuning.CHANT3_PHRASES - g.stateTick;
                break;
            case Fighter.TP_WINDUP: {
                boolean purple = w.tpKind() == Attack.PURPLE;
                int left = (purple ? Tuning.CHANT_PHRASE * Tuning.CHANT_PHRASES : Tuning.TP_WINDUP) - g.stateTick;
                long dmg = purple ? Tuning.TP_PURPLE_DAMAGE_MILLI : w.tpKind() == Attack.RED ? Tuning.TP_RED_DAMAGE_MILLI : Tuning.SMASH_DAMAGE_MILLI * Tuning.TP_BLUE_HITS;
                if (left <= Tuning.FEAR_WINDOW) e += dmg / 1000f * done;
                busy = left;
                break;
            }
            case Fighter.PURPLE_WINDUP:
                if (facing) e += Tuning.PURPLE_DAMAGE_MILLI / 1000f * hit;
                break;
            case Fighter.RED_HOLD:
                if (facing && d <= Tuning.RED_REACH) e += Tuning.RED_DAMAGE_MILLI / 1000f * hit;
                break;
            default:
                break;
        }
        // 飞过来的招、正在扩张的无指向茈、苍
        float fly = stuck ? 1f : 0.6f;
        int me = 1 << Fighter.SUKUNA; // 已经打中过宿傩的茈、无指向茈不会再打他
        if (w.red.active && (s.x - w.red.x) * w.red.dir > 0) e += (w.red.max ? Tuning.RED_MAX_DAMAGE_MILLI : Tuning.RED_DAMAGE_MILLI) / 1000f * fly;
        if (w.purple.active && (w.purple.hitMask & me) == 0 && (s.x - w.purple.x) * w.purple.dir > 0) {
            e += (w.purple.max ? Tuning.PURPLE_200_DAMAGE_MILLI : Tuning.PURPLE_DAMAGE_MILLI) / 1000f * fly;
        }
        if (w.hollow.active && (w.hollow.hitMask & me) == 0) e += Tuning.HOLLOW_DAMAGE_MILLI / 1000f * fly;
        e += blueExpect(w.blue, s);
        // 贴身的体术：五条悟出拳很快（0.12 秒就能接下一拳，平均一下 95：黑闪 15%）。Astra 给的是 190 / 76 / 19，
        // 模拟里实际贴身一秒挨 450 左右，按实测校准成 420 / 160 / 40（不然估得总是偏低，恐惧只涨不跌）。
        // 他手上的动作占掉的那部分时间打不了拳
        float melee = d <= 110 ? 420 : d <= 220 ? 160 : d <= 420 ? 40 : 0;
        melee *= Math.max(0, Tuning.FEAR_WINDOW - Math.max(0, busy)) / (float) Tuning.FEAR_WINDOW;
        e += stuck ? melee * 1.5f : melee;
        // 还没出手、但可能会用的：看到用过就按冷却推算，没看到当作可能能用；只取最大的两项
        if (g.state == Fighter.FREE) {
            float a = 0, b = 0, c;
            c = facing && d <= Tuning.RED_REACH && aiClock - seenRed > Tuning.RED_COOLDOWN ? 30 : 0;
            if (c > a) { b = a; a = c; } else if (c > b) b = c;
            c = facing && d >= 150 && d <= 960 && aiClock - seenPurple > Tuning.PURPLE_COOLDOWN ? 85 : 0;
            if (c > a) { b = a; a = c; } else if (c > b) b = c;
            c = d <= Tuning.BLUE_MAX_THROW + Tuning.BLUE_RADIUS && aiClock - seenBlue > Tuning.BLUE_COOLDOWN ? 5 : 0;
            if (c > a) { b = a; a = c; } else if (c > b) b = c;
            c = aiClock - seenTp > Tuning.TP_COOLDOWN ? 57.5f : 0;
            if (c > a) { b = a; a = c; } else if (c > b) b = c;
            e += a + b * 0.5f;
        }
        return Math.round(e);
    }

    /** 苍这一秒还会吸掉多少：和伤害一样按到中心的距离平方衰减（核心每秒 100），只算还剩的寿命；还在飞的不算。 */
    private static float blueExpect(BlueOrb b, Fighter s) {
        if (b.phase != BlueOrb.PULL) return 0;
        float r = 0;
        if (!s.captured) {
            float dx = b.cx - s.x, dy = b.cy - s.centerY();
            r = (float) Math.sqrt(dx * dx + dy * dy);
            if (r >= b.radius) return 0;
        }
        float q = Math.max(0, Math.min(1, (b.radius - r) / (b.radius - Tuning.BLUE_CORE)));
        float life = Math.max(0, Math.min(Tuning.FEAR_WINDOW, Tuning.BLUE_LIFE - b.tick)) / (float) Tuning.HZ;
        return Tuning.BLUE_MAX_DPS_MILLI / 1000f * q * q * life;
    }

    /** 惊惧：一组一组地往后退（每组 1–3 段，每段 0.2–0.3 秒，段间停一下；退到够远、背后是墙、被贴身连打就停）。 */
    private boolean retreat(Fighter s, float d, int toward) {
        if (!panic) return false;
        float target = Math.min(620, 360 + 5 * (fear - Tuning.FEAR_PANIC) / 100f);
        float wall = -toward > 0 ? Tuning.WIDTH - s.x : s.x;
        if (retreatLeft == 0) {
            if (retreatCool > 0 || d >= target || wall < 90) return false;
            retreatLeft = fear >= 8500 ? 3 : fear >= 7000 ? 2 : 1;
            retreatTimer = range(24, 36);
            retreatPause = 0;
        }
        if (d >= target || wall < 90) { // 够远了，或者退到墙边了：交给别的分支（墙边有跳越）
            endRetreat();
            return false;
        }
        if (retreatPause > 0) {
            retreatPause--;
            moveIntent = 0;
            return true;
        }
        moveIntent = -toward;
        holdTimer = 0;
        if (--retreatTimer <= 0) {
            if (--retreatLeft > 0) {
                retreatTimer = range(24, 36);
                retreatPause = range(6, 12);
            } else {
                endRetreat();
            }
        }
        return true;
    }

    private void endRetreat() {
        retreatLeft = 0;
        retreatCool = 60; // 每组之间留 0.5 秒给平常的防守、反击
    }

    /**
     * 空间斩：恐惧低于 30、打了 20 秒以上、血量 25% 以下（硬条件），再加上中远距离（260–850）、他手上没在起招、没有招飞过来时，
     * 每 3 秒最多认真考虑一次：看到代价（斩不中就力竭）恐惧涨一点，再按恐惧抽签（越怕越不愿意）。不判断必中：五条悟看到预警
     * 照样能冲过来打断、跳过去或闪开，这是赌命的一刀。
     */
    private boolean wantsSpace(World w, Fighter s, Fighter g, float d, int threat) {
        if (!w.config.spaceSlash || w.spaceUsed || fear >= Tuning.SPACE_FEAR_GATE) return false;
        if (w.fightTicks < Tuning.SPACE_MIN_TICKS || s.hpMilli == 0 || s.hpMilli * 4 > s.maxHpMilli) return false;
        if (w.ampTicks > 0 || w.ampLock > 0 || !s.onGround || w.sAttackGap > 0 || w.domainTicks > 0) return false;
        if (threat >= 0 || d < 260 || d > 850 || g.y > 40 || gojoWindingUp(g) || g.state != Fighter.FREE && g.state != Fighter.MELEE_RECOVER) return false;
        if (aiClock - spaceThinkAt < 360) return false;
        spaceThinks++;
        if (fear >= 2600 && spaceThinkFear - fear < 200) return false; // 刚想过又没怎么收心：先别再想（不然一直逼近门槛）
        spaceThinkAt = aiClock;
        int cost = Math.min(500, (Tuning.SPACE_FEAR_GATE - fear) / 2);
        fear += Math.min(cost, Tuning.SPACE_FEAR_GATE - fear - 1); // 看到代价：怕了一点（不会因为想一下就顶过门槛）
        spaceThinkFear = fear;
        // 越怕越不愿意：按离门槛还有多远线性打折（Astra 给的是平方，恐惧 20 时只剩 1.5%，几乎放不出来）
        return rng.nextUnitDouble() < t.spaceChance * (1 - fear / (float) Tuning.SPACE_FEAR_GATE);
    }

    /** 里梅以上的世界斩：估计五条悟最快多久能打断（按看到过的招推算冷却，没看到就当能用），来得及才抽一次签。 */
    private boolean worldChance(World w, float d, int threat) {
        if (worldRecheck > 0 || threat >= 0) return false; // 有招正在飞过来：先别念
        if (gojoWindingUp(w.gojo)) return false;            // 他手上已经在起招：先别念
        int windup = w.worldBound ? Tuning.S_WORLD_WINDUP : Tuning.S_WORLD_FIRST_WINDUP;
        if (fastestInterrupt(w, d) <= windup + t.worldSafetyMargin) return false;
        worldRecheck = t.worldRecheckGap;
        return worldFear() && rng.nextUnitDouble() < t.worldChance;
    }

    /** 怕了就舍不得念世界斩：按（1 − 恐惧）² 抽一次，不愿意就 2 秒内不再想（也不再为它拉距离）。 */
    private boolean worldFear() {
        float fk = 1 - f();
        if (fear == 0 || rng.nextUnitDouble() < fk * fk) return true;
        worldRefuse = 240;
        return false;
    }

    /** 五条悟手上正在起招（出拳、蓄苍拳、苍赫茈的起手和念咒、瞬移前摇、闪避）。 */
    private static final int MAX_CHANT_TICKS = Tuning.CHANT3_PHRASE * 6 + Tuning.CHANT_PHRASE * Tuning.CHANT_PHRASES;

    private static boolean gojoWindingUp(Fighter g) {
        switch (g.state) {
            case Fighter.PURPLE_MAX_CHANT:
            case Fighter.MELEE:
            case Fighter.FIST_WINDUP:
            case Fighter.RED_HOLD:
            case Fighter.RED_CHANT:
            case Fighter.BLUE_HOLD:
            case Fighter.BLUE_CHANT:
            case Fighter.BLUE_WINDUP:
            case Fighter.PURPLE_WINDUP:
            case Fighter.PURPLE_CHANT:
            case Fighter.TP_WINDUP:
            case Fighter.DODGE:
                return true;
            default:
                return false;
        }
    }

    /** 估计五条悟最快多久能碰到宿傩（走过去出拳；按看到过的招推算瞬移、赫、苍、茈的冷却，没看到就当能用）。 */
    private float fastestInterrupt(World w, float d) {
        float fastest = Math.max(0, d - Tuning.MELEE_REACH) / Tuning.MOVE_SPEED * Tuning.HZ + Tuning.MELEE_STARTUP;
        if (aiClock - seenTp > Tuning.TP_COOLDOWN) fastest = Math.min(fastest, Tuning.TP_WINDUP + 2);
        if (aiClock - seenRed > Tuning.RED_COOLDOWN && d <= Tuning.RED_REACH) fastest = Math.min(fastest, Tuning.RED_WINDUP + d / Tuning.RED_SPEED * Tuning.HZ);
        if (aiClock - seenBlue > Tuning.BLUE_COOLDOWN && d <= Tuning.BLUE_MAX_THROW + Tuning.BLUE_RADIUS) fastest = Math.min(fastest, Tuning.BLUE_WINDUP + Tuning.BLUE_FLY);
        if (aiClock - seenPurple > Tuning.PURPLE_COOLDOWN) fastest = Math.min(fastest, Tuning.PURPLE_WINDUP + d / Tuning.PURPLE_SPEED * Tuning.HZ);
        return fastest;
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
