package com.shinjuku.showdown.core;

/**
 * 战斗世界：每秒 120 tick 固定步长推进。完全确定：同一个配置 + 同一串输入 = 同一个结果。
 * 一个 tick 的顺序：五条悟的操作与动作 → 宿傩（AI 意图 → 动作执行）→ 魔虚罗（在场时）→ 体术命中（五条悟、宿傩、魔虚罗）
 * → 顎吐 → 苍的位置 → 赫（遇到苍变无指向茈；命中）→ 茈 → 无指向茈的扩张 → 苍的吸力与持续伤害
 * → 宿傩的世界斩（这一 tick 没被打断才斩出去）→ 积分（重力、外力）→ 地面与墙 → 计时器、领域、倒地与复活。
 * 五条悟的招同时对宿傩和魔虚罗：体术打够得着的最近一个，赫打到弹道上第一个就爆，茈穿透都打，无指向茈范围内都打，苍都吸。
 * 领域展开先放过场：cutscene 为真时整个世界停住（时间也不走），直到画面调用 endCutscene()。
 * 热路径不分配对象。
 */
public final class World {
    public final BattleConfig config;
    public final Fighter gojo = new Fighter(Fighter.GOJO);
    public final Fighter sukuna = new Fighter(Fighter.SUKUNA);
    public final BlueOrb blue = new BlueOrb();
    public final Shot red = new Shot();
    public final Shot purple = new Shot();
    public final Hollow hollow = new Hollow();
    public final Events events = new Events();
    public final Domains domains = new Domains();
    public final CombatRng rng;
    public final BlackFlash blackFlash = new BlackFlash();
    private final SukunaAi ai;
    public final SukunaIntent sukunaIntent = new SukunaIntent(); // 没有 AI 时测试（和截图）可以手动填

    public int tick;
    public int hitstop;               // >0 时整个世界停住（打中的顿挫）
    public int meleeCd, blueCd, redCd, purpleCd, domainCd, blueFailLock, purpleFailLock, redFailLock, dodgeCd, tpCd;
    public int blueCdMax = Tuning.BLUE_COOLDOWN, redCdMax = Tuning.RED_COOLDOWN, purpleCdMax = Tuning.PURPLE_COOLDOWN;
    public int sMeleeCd, dismantleCd, sAttackGap;

    // 宿傩：领域展延、魔虚罗的适应、世界斩、反转术式（画面也读）
    public int ampTicks;              // 领域展延还剩多久（>0 时体术能破无限）
    public int ampCd, ampLock;        // 展延冷却（关闭后起算）；刚关闭这段时间不能用术式
    public int adaptPoints;           // 适应点（每 2 点轮转一格，满 8 点）
    public int fightTicks;            // 有效战斗时间（命中停顿、过场不算）
    public boolean worldUnlocked;     // 学会了世界斩
    public boolean worldBound;        // 束缚：第一次世界斩免结印咏唱（用掉就是 true）
    public int worldCd, worldWindup;  // 世界斩冷却；这一次起手要多久（第一次 48，之后 144）
    public float worldY;              // 斩带中心离地高度（指向时锁定）
    public int worldDir;              // 斩的方向（指向时锁定）
    public int rctUses, rctCd;
    public int sStagger;              // 宿傩的招被打断后这么久不能行动（和受击硬直同时计）

    // 十种影法术（顎吐、魔虚罗）与原身
    public final Fighter mahoraga = new Fighter(Fighter.MAHORAGA);
    public final Agito agito = new Agito();
    public static final int WHEEL_NONE = 0, WHEEL_SUKUNA = 1, WHEEL_MAHORAGA = 2;
    public int wheelOn;               // 法阵在谁头上：三轮没有；召出魔虚罗后在它头上；魔虚罗退场、变原身以后没有
    public boolean mahoragaUsed;      // 召唤过魔虚罗了（一局一次，定）
    public boolean mahoragaAdapted;   // 魔虚罗适应了无限：攻击能打穿（原作第 234 话）
    public boolean sukunaTrueForm;    // 变成原身了（十种影法术没了）
    public int summonCd, agitoCd;     // 召唤被打断后的重试冷却、顎吐的冷却
    public int mPunchCd, mSwordCd, mGap; // 魔虚罗两招的冷却、两次出招的间隔
    private int mDecide, mReact, mMove, mSeq = -1, mHitId = -1;
    private boolean mWasLocked;
    // 固定动作（茈·最大输出）：全场暂停，只走这段动画。画面读这些
    public static final int CINE_NONE = 0, CINE_PURPLE_MAX = 1;
    public int cine, cineTick;
    public boolean cineMaho;             // 魔虚罗在场的版本
    public float cineSx, cineSy;         // 空中那个苍的中心（也是茈爆开的地方）
    public float cineBlueR, cinePurpleR; // 苍的半径、茈爆开的半径
    public float cineRedX, cineRedY;     // 赫的位置（PMAX_RED 之后）
    private float cineRx0, cineRy0, cineGx, cineGy0, cineSx0, cineSy0, cineMx0, cineLx, cineLy;
    private boolean cineSelf;            // 五条悟在爆炸范围里（开始时就定下来）

    // 统计（模拟、测试用）：每人实际掉的血（毫血，不算溢出），宿傩反转术式和变原身各回了多少
    public final long[] damageTaken = new long[Fighter.COUNT];
    public long rctHealed, transformHealed;
    private boolean adaptPending, worldLocked, worldPending;
    private int adaptLastTick = -100_000, adaptTimer;

    // 领域
    public boolean cutscene;          // 正在放领域展开的过场：全场暂停
    public int castId;                // 第几次展开（画面结束过场时带回来，旧的回调不算数）
    public int domainTicks;           // 无量空处还剩多久（>0 时宿傩无法主动行动）

    // 招式状态（画面也读）
    public int chantPhase = -1;       // 念到第几段（200% 茈、出力最大的苍、最大输出的赫）
    public int chantKind;             // 在念哪个：Attack.PURPLE / BLUE_FIELD / RED
    public int meleeCombo;            // 第几下体术（画面轮换拳、踢、上勾）
    private boolean pendingCharged, redMax, purple200;
    private boolean meleeReleased, redReleased, purpleReleased, fistAir, fistSmash;
    private int dodgeRecent; // 刚点过闪避（瞬移组合的宽限）
    private boolean redLetGo, purpleLetGo; // 停顿期间也记着：赫、茈松开过（念咒恢复后照样算打断）
    private int dodgeDir = 1, tpKind, tpHits, tpEndAt;
    private float tpBaseY, tpFromX, tpToX;
    private float aimX = 1, aimY;     // 苍的瞄准方向（单位向量）；0 表示自动瞄准
    private int attackSeq;            // 每次出招一个编号，同一招对同一目标只命中一次
    private int meleeHitId = -1, gMeleeSeq = -2, sMeleeHitId = -1;
    private int bufMelee, bufRed, bufJump, bufPurple, bufDomain, bufDodge;
    // 苍的按下/松开按发生顺序排队（停顿期间按的也留着）；只有用得上的状态才取，过一会儿没用上就作废
    private static final int BQ = 8;
    private final boolean[] bqDown = new boolean[BQ];
    private final int[] bqTtl = new int[BQ];
    private int bqHead, bqSize;
    private final Attack attack = new Attack();

    public World(BattleConfig config) {
        this.config = config;
        rng = new CombatRng(config.seed);
        ai = new SukunaAi(config.ai, new CombatRng(config.seed ^ 0x5A5A_A5A5_1234_4321L)); // AI 用自己的随机数，不影响黑闪
        gojo.maxHpMilli = gojo.hpMilli = Math.round(config.gojoMaxHp * 1000);
        sukuna.maxHpMilli = sukuna.hpMilli = Math.round(config.sukunaMaxHp * 1000);
        gojo.infinity = true;
        gojo.x = gojo.px = Tuning.WIDTH * 0.28f;
        sukuna.x = sukuna.px = Tuning.WIDTH * 0.72f;
        sukuna.facing = -1;
        mahoraga.present = false;
        mahoraga.halfW = Tuning.MAHORAGA_HALF_W;
        mahoraga.height = Tuning.MAHORAGA_H;
        mahoraga.maxHpMilli = mahoraga.hpMilli = Tuning.MAHORAGA_HP_MILLI;
        wheelOn = config.wheel ? WHEEL_SUKUNA : WHEEL_NONE;
    }

    public Fighter fighter(int id) {
        return id == Fighter.GOJO ? gojo : id == Fighter.SUKUNA ? sukuna : mahoraga;
    }

    public void step(TickInput in) {
        if (cutscene) return; // 过场：全场暂停
        tick++;
        gojo.px = gojo.x;
        gojo.py = gojo.y;
        sukuna.px = sukuna.x;
        sukuna.py = sukuna.y;
        blue.px = blue.x;
        blue.py = blue.y;
        red.px = red.x;
        red.py = red.y;
        purple.px = purple.x;
        purple.py = purple.y;
        mahoraga.px = mahoraga.x;
        mahoraga.py = mahoraga.y;
        agito.px = agito.x;
        agito.py = agito.y;
        if (cine != CINE_NONE) { // 固定动作：全场暂停（冷却、领域、AI 都不走），按键不收，只走这段动画
            stepCine();
            return;
        }
        latch(in);
        if (hitstop > 0) {
            hitstop--;
            return;
        }
        controlGojo(in);
        gojo.invuln = gojo.state == Fighter.DODGE || gojo.state == Fighter.TP_BLUE || gojo.state == Fighter.TP_RED || gojo.state == Fighter.TP_PURPLE;
        if (cutscene || cine != CINE_NONE) return; // 这一 tick 按了领域展开（停在这里等过场）或开始了固定动作（全场从这一 tick 起就暂停）
        if (config.sukunaAi) ai.think(this, sukunaIntent);
        controlSukuna(sukunaIntent);
        if (!config.sukunaAi) sukunaIntent.clear();
        controlMahoraga();
        resolveMelee();
        resolveSukunaMelee();
        resolveMahoragaAttack();
        updateAgito();
        advanceBlue();
        updateRed();
        updatePurple();
        updateHollow();
        applyBlue();
        resolveWorldSlash();
        integrate(gojo);
        integrate(sukuna);
        if (mahoraga.present) integrate(mahoraga);
        timers();
    }

    /* ---------------- 输入 ---------------- */

    /** 先把这一 tick 的按键记下来（命中停顿期间世界不动，但按键不能丢）。同一 tick 里有按下又有松开时，一定是先按后松。 */
    private void latch(TickInput in) {
        if (in.melee) bufMelee = Tuning.INPUT_BUFFER;
        if (in.red) bufRed = Tuning.INPUT_BUFFER;
        if (in.jump) bufJump = Tuning.INPUT_BUFFER;
        if (in.purple) bufPurple = Tuning.INPUT_BUFFER;
        if (in.domain) bufDomain = Tuning.INPUT_BUFFER;
        if (in.dodge) {
            bufDodge = Tuning.INPUT_BUFFER;
            dodgeRecent = Tuning.TP_LENIENCY;
        }
        if (!in.redHeld) redLetGo = true;
        if (!in.purpleHeld) purpleLetGo = true;
        if (in.blueDown) bluePush(true);
        if (in.blueUp) bluePush(false);
    }

    private void bluePush(boolean down) {
        if (bqSize == BQ) bluePop();
        int i = (bqHead + bqSize) % BQ;
        bqDown[i] = down;
        bqTtl[i] = Tuning.INPUT_BUFFER;
        bqSize++;
    }

    private void bluePop() {
        bqHead = (bqHead + 1) % BQ;
        bqSize--;
    }

    private boolean blueNextIsDown() {
        return bqSize > 0 && bqDown[bqHead];
    }

    private boolean blueNextIsUp() {
        return bqSize > 0 && !bqDown[bqHead];
    }

    /** 苍的队列里有没有还没处理的松开（茈·最大输出只认一直按着的苍：松开过一下就不算）。 */
    private boolean blueUpQueued() {
        for (int i = 0; i < bqSize; i++) if (!bqDown[(bqHead + i) % BQ]) return true;
        return false;
    }

    private void clearInputs() {
        bufMelee = bufRed = bufJump = bufPurple = bufDomain = bufDodge = 0;
        bqSize = 0;
    }

    /* ---------------- 五条悟 ---------------- */

    private void controlGojo(TickInput in) {
        Fighter g = gojo;
        g.moveInput = in.moveX;
        if (g.state == Fighter.KO) {
            drive(g, 0);
            return;
        }
        if (g.hitstun > 0) return;
        g.stateTick++;
        int st = g.stateTick;
        switch (g.state) {
            case Fighter.FREE:
                free(g, in);
                break;
            case Fighter.MELEE: // 体术起手：松开就出拳；一直按住到 FIST_HOLD 就蓄成苍拳
                drive(g, 0);
                if (!in.meleeHeld) meleeReleased = true;
                if (!meleeReleased && st >= Tuning.FIST_HOLD) {
                    fistSmash = fistAir = false;
                    enter(g, Fighter.FIST_WINDUP);
                    events.emit(Events.CAST, g.x, g.centerY(), 0, Attack.BLUE_FIST, tick);
                } else if (meleeReleased && st >= Tuning.MELEE_STARTUP) {
                    enter(g, Fighter.MELEE_ACTIVE);
                }
                break;
            case Fighter.MELEE_ACTIVE:
                drive(g, g.facing * Tuning.MELEE_LUNGE);
                if (st >= Tuning.MELEE_ACTIVE) enter(g, Fighter.MELEE_RECOVER);
                break;
            case Fighter.MELEE_RECOVER:
                drive(g, 0);
                if (st >= Tuning.MELEE_RECOVERY) enter(g, Fighter.FREE);
                else if (st >= Tuning.MELEE_CANCEL && wantsToAct(in)) cancelInto(g, in);
                break;
            case Fighter.FIST_WINDUP: // 苍拳的前摇：长按体术的往后收拳；下砸的拳头举过头顶（空中停住）
                drive(g, 0);
                if (fistAir) {
                    g.vy = 0;
                    g.gravityScale = 0;
                }
                if (st >= (fistSmash ? Tuning.SMASH_WINDUP : Tuning.FIST_WINDUP)) {
                    enter(g, Fighter.FIST_ACTIVE);
                    if (fistAir) g.vy = Tuning.SMASH_DIVE_VY;
                }
                break;
            case Fighter.FIST_ACTIVE:
                if (!fistSmash) { // 长按体术：往前冲一拳
                    drive(g, g.facing * Tuning.FIST_LUNGE);
                    if (st >= Tuning.FIST_ACTIVE) enter(g, Fighter.FIST_RECOVER);
                } else if (fistAir) { // 下砸：空中俯冲到落地
                    drive(g, g.facing * Tuning.SMASH_DIVE_VX);
                    g.vy = Math.min(g.vy, Tuning.SMASH_DIVE_VY);
                    if (g.onGround || st >= Tuning.SMASH_DIVE_MAX) enter(g, Fighter.FIST_RECOVER);
                } else {          // 下砸：地上往下劈
                    drive(g, 0);
                    if (st >= Tuning.SMASH_ACTIVE) enter(g, Fighter.FIST_RECOVER);
                }
                break;
            case Fighter.FIST_RECOVER:
                drive(g, 0);
                if (st >= (fistSmash ? Tuning.SMASH_RECOVERY : Tuning.FIST_RECOVERY)) enter(g, Fighter.FREE);
                else if (!fistSmash && st >= Tuning.FIST_CANCEL && wantsToAct(in)) cancelInto(g, in);
                break;
            case Fighter.BLUE_HOLD:
                move(g, in.moveX, 1f);
                updateAim(in);
                if (dodgeIntoTeleport(Attack.BLUE_FIST)) break; // 按着苍点闪避：瞬移
                if (in.redHeld && in.blueHeld && !blueUpQueued() && maxPurpleReady()) { // 苍和赫同时按住：茈·最大输出
                    startMaxChant();
                    break;
                }
                if (bufJump > 0 || bufMelee > 0 || bufRed > 0 || bufPurple > 0 || bufDomain > 0 || bufDodge > 0) {
                    enter(g, Fighter.FREE); // 长按确认前可以取消，不进冷却；缓冲着的那个动作下一 tick 就出
                } else if (blueNextIsUp()) {
                    bluePop();
                    startBlueWindup(false);
                } else if (bqSize == 0 && !in.blueHeld) {
                    startBlueWindup(false); // 保险：松开事件没收到（触屏事件环溢出时）
                } else if (st >= Tuning.BLUE_HOLD_THRESHOLD) {
                    startChant(Fighter.BLUE_CHANT, Attack.BLUE_FIELD); // 一直按着：念咒（位相、黄昏、智慧之瞳）
                }
                break;
            case Fighter.BLUE_CHANT: { // 念咒：不能动；松手或被攻击碰到就打断；方向跟着摇杆
                drive(g, 0);
                updateAim(in);
                if (dodgeIntoTeleport(Attack.BLUE_FIST)) break;
                if (in.redHeld && bufRed > 0 && in.blueHeld && !blueUpQueued() && maxPurpleReady()) { // 念着苍又按住赫：改念茈·最大输出
                    startMaxChant();
                    break;
                }
                if (blueNextIsUp()) {
                    bluePop();
                    breakChant();
                    break;
                }
                if (bqSize == 0 && !in.blueHeld) {
                    breakChant();
                    break;
                }
                if (chantStep(st, Tuning.CHANT3_PHRASE, Tuning.CHANT3_PHRASES)) {
                    chantPhase = -1;
                    events.emit(Events.CAST, g.x, g.centerY(), 1, Attack.BLUE_FIELD, tick);
                    spawnBlue(true);
                    enter(g, Fighter.BLUE_RECOVER);
                }
                break;
            }
            case Fighter.BLUE_WINDUP: {
                if (dodgeIntoTeleport(Attack.BLUE_FIST)) break; // 刚点完苍马上点闪避，也算
                move(g, in.moveX, Tuning.BLUE_WINDUP_MOVE);
                int windup = Tuning.BLUE_WINDUP;
                if (st <= windup - Tuning.BLUE_AIM_LOCK) updateAim(in); // 最后几 tick 锁定方向
                if (st >= windup) {
                    spawnBlue(pendingCharged);
                    enter(g, Fighter.BLUE_RECOVER);
                }
                break;
            }
            case Fighter.BLUE_RECOVER:
                move(g, in.moveX, 0.6f);
                if (st >= Tuning.BLUE_RECOVERY) enter(g, Fighter.FREE);
                break;
            case Fighter.RED_HOLD: // 赫起手：松开就放普通赫；一直按住到 RED_HOLD 就蓄成最大输出
                drive(g, 0);
                if (dodgeIntoTeleport(Attack.RED)) break; // 按着赫（或刚点完赫）点闪避：瞬移
                if (in.redHeld && !redLetGo && in.blueHeld && !blueUpQueued() && maxPurpleReady()) { // 赫和苍同时按住：茈·最大输出
                    startMaxChant();
                    break;
                }
                if (!in.redHeld || redLetGo) redReleased = true;
                if (!redReleased && st >= Tuning.RED_HOLD) {
                    events.emit(Events.CAST, g.x, g.centerY(), 1, Attack.RED, tick);
                    startChant(Fighter.RED_CHANT, Attack.RED); // 一直按着：念咒（位相、波罗蜜、光之柱）
                } else if (redReleased && st >= Tuning.RED_WINDUP) {
                    fireRed(false);
                }
                break;
            case Fighter.RED_CHANT: // 念咒：不能动；松手或被攻击碰到就打断
                drive(g, 0);
                if (dodgeIntoTeleport(Attack.RED)) break;
                if (in.redHeld && !redLetGo && in.blueHeld && blueNextIsDown() && !blueUpQueued() && maxPurpleReady()) { // 念着赫又按住苍
                    startMaxChant();
                    break;
                }
                if (!in.redHeld || redLetGo) {
                    breakChant();
                    break;
                }
                if (chantStep(st, Tuning.CHANT3_PHRASE, Tuning.CHANT3_PHRASES)) {
                    chantPhase = -1;
                    fireRed(true);
                }
                break;
            case Fighter.RED_ACTIVE:
                drive(g, 0);
                if (st >= Tuning.RED_ACTIVE) enter(g, Fighter.RED_RECOVER);
                break;
            case Fighter.RED_RECOVER:
                drive(g, 0);
                if (st >= (redMax ? Tuning.RED_MAX_RECOVERY : Tuning.RED_RECOVERY)) enter(g, Fighter.FREE);
                else if (!redMax && st >= Tuning.RED_CANCEL && wantsToAct(in)) cancelInto(g, in);
                break;
            case Fighter.PURPLE_WINDUP: // 茈起手：松开就是普通茈；一直按住到 PURPLE_HOLD 就开始念咒
                drive(g, 0);
                if (dodgeIntoTeleport(Attack.PURPLE)) break; // 按着茈（或刚点完茈）点闪避：瞬移·茈
                if (!in.purpleHeld || purpleLetGo) purpleReleased = true;
                if (!purpleReleased && st >= Tuning.PURPLE_HOLD) {
                    startChant(Fighter.PURPLE_CHANT, Attack.PURPLE);
                } else if (purpleReleased && st >= Tuning.PURPLE_WINDUP) {
                    firePurple(false);
                }
                break;
            case Fighter.PURPLE_CHANT: { // 念咒：不能移动；松手或被攻击都会打断
                drive(g, 0);
                if (!in.purpleHeld || purpleLetGo) {
                    breakChant();
                    break;
                }
                if (chantStep(st, Tuning.CHANT_PHRASE, Tuning.CHANT_PHRASES)) firePurple(true);
                break;
            }
            case Fighter.PURPLE_MAX_CHANT: { // 茈·最大输出：苍三段、赫三段、茈四段；松开苍或赫、被攻击碰到都会打断
                drive(g, 0);
                if (blueUpQueued() || !in.blueHeld || !in.redHeld || redLetGo) { // 松开过一下（哪怕又按回去）也算松开
                    breakChant();
                    break;
                }
                int phase = maxChantPhase(st);
                if (phase != chantPhase) {
                    chantPhase = phase;
                    events.emit(Events.CHANT, g.x, g.centerY(), phase, Attack.PURPLE_MAX, tick);
                }
                if (st >= Tuning.CHANT3_PHRASE * 6 + Tuning.CHANT_PHRASE * Tuning.CHANT_PHRASES) startPurpleMax();
                break;
            }
            case Fighter.PURPLE_RECOVER:
                drive(g, 0);
                if (st >= (purple200 ? Tuning.PURPLE_200_RECOVER : Tuning.PURPLE_RECOVER)) enter(g, Fighter.FREE);
                else if (!purple200 && st >= Tuning.PURPLE_CANCEL && wantsToAct(in)) cancelInto(g, in);
                break;
            case Fighter.DOMAIN_CAST:
                drive(g, 0);
                break;
            case Fighter.DODGE: // 闪避：无敌，横着冲一段；按住闪避再点苍 / 赫，直接接瞬移
                drive(g, dodgeDir * Tuning.DODGE_SPEED);
                g.vy = 0;
                g.gravityScale = 0;
                if (dodgeCombo(in) && blueNextIsDown() && blueCd == 0 && blueFailLock == 0) {
                    bluePop();
                    startTeleport(Attack.BLUE_FIST);
                } else if (dodgeCombo(in) && bufRed > 0 && redCd == 0 && redFailLock == 0) {
                    bufRed = 0;
                    startTeleport(Attack.RED);
                } else if (dodgeCombo(in) && bufPurple > 0 && purpleCd == 0 && purpleFailLock == 0) {
                    bufPurple = 0;
                    startTeleport(Attack.PURPLE);
                } else if (st >= Tuning.DODGE_TICKS) {
                    enter(g, Fighter.FREE);
                }
                break;
            case Fighter.TP_WINDUP: // 瞬移的前摇：停住，身上聚起术式；被攻击碰到就打断。瞬移·茈先念完茈的四段咒词
                drive(g, 0);
                g.vy = 0;
                g.gravityScale = 0;
                if (sukuna.state == Fighter.KO) {
                    chantPhase = -1;
                    enter(g, Fighter.FREE);
                } else if (tpKind == Attack.PURPLE) {
                    if (chantStep(st, Tuning.CHANT_PHRASE, Tuning.CHANT_PHRASES)) {
                        chantPhase = -1;
                        teleportBehind();
                    }
                } else if (st >= Tuning.TP_WINDUP) {
                    teleportBehind();
                }
                break;
            case Fighter.TP_BLUE:
                scriptBlue(st);
                break;
            case Fighter.TP_RED:
                scriptRed(st);
                break;
            case Fighter.TP_PURPLE:
                scriptPurple(st);
                break;
            default:
                break;
        }
    }

    /** 开始念咒（苍、赫、茈都一样：不能动，松手或被攻击碰到就打断）。 */
    private void startChant(int state, int kind) {
        enter(gojo, state);
        chantKind = kind;
        chantPhase = 0;
        events.emit(Events.CHANT, gojo.x, gojo.centerY(), 0, kind, tick);
    }

    /** 念到第几段了（每段换一次就发一个事件）；念完返回 true。 */
    private boolean chantStep(int st, int phraseTicks, int phrases) {
        int phase = Math.min(phrases - 1, st / phraseTicks);
        if (phase != chantPhase) {
            chantPhase = phase;
            events.emit(Events.CHANT, gojo.x, gojo.centerY(), phase, chantKind, tick);
        }
        return st >= phrases * phraseTicks;
    }

    /* ---------------- 闪避、苍拳、瞬移 ---------------- */

    /** 闪避：推着摇杆就往摇杆的方向，不推就往面朝的方向。 */
    private void startDodge(Fighter g, TickInput in) {
        dodgeDir = in.moveX > 0.3f ? 1 : in.moveX < -0.3f ? -1 : g.facing;
        enter(g, Fighter.DODGE);
        dodgeCd = Tuning.DODGE_COOLDOWN;
        events.emit(Events.DODGE, g.x, g.centerY(), dodgeDir, tick);
    }

    /** 下砸苍拳（按住跳跃时点体术）：在空中就往下俯冲，在地上就举起来往下劈。 */
    private void startFist(Fighter g) {
        fistSmash = true;
        fistAir = !g.onGround;
        if (fistAir) g.vy = 0; // 空中：马上停住
        enter(g, Fighter.FIST_WINDUP);
        meleeCd = Tuning.MELEE_COOLDOWN;
        gMeleeSeq = ++attackSeq;
        events.emit(Events.CAST, g.x, g.centerY(), 0, Attack.BLUE_FIST, tick);
    }

    private boolean canTeleport() {
        return tpCd == 0 && sukuna.state != Fighter.KO;
    }

    /** 闪避这一半凑上了吗：按着闪避，或者刚点过闪避（宽限内）。 */
    private boolean dodgeCombo(TickInput in) {
        return (in.dodgeHeld || dodgeRecent > 0) && canTeleport();
    }

    /** 按着苍 / 赫（或刚点完）时点了闪避：直接接瞬移（先按哪个都行）。 */
    private boolean dodgeIntoTeleport(int kind) {
        if (bufDodge == 0 || !canTeleport()) return false;
        if (kind == Attack.BLUE_FIST ? blueCd != 0 : kind == Attack.RED ? redCd != 0 : purpleCd != 0 || purpleFailLock != 0) return false;
        bufDodge = 0;
        chantPhase = -1;
        startTeleport(kind);
        return true;
    }

    /** 瞬移的前摇（按住闪避时点苍 / 点赫 / 点茈）。瞬移·茈先念茈的四段咒词。 */
    private void startTeleport(int kind) {
        tpKind = kind;
        enter(gojo, Fighter.TP_WINDUP);
        drive(gojo, 0);
        events.emit(Events.CAST, gojo.x, gojo.centerY(), 4, kind, tick);
        if (kind == Attack.PURPLE) {
            chantKind = Attack.PURPLE;
            chantPhase = 0;
            events.emit(Events.CHANT, gojo.x, gojo.centerY(), 0, Attack.PURPLE, tick);
        }
    }

    /** 出现在宿傩背后（背后是墙就出现在另一边），把他定住，进入固定动作。 */
    private void teleportBehind() {
        Fighter g = gojo, s = sukuna;
        float lo = Tuning.BODY_HALF_W, hi = Tuning.WIDTH - Tuning.BODY_HALF_W;
        int side = g.x <= s.x ? 1 : -1;
        float tx = s.x + side * Tuning.TP_BEHIND;
        if (tx < lo || tx > hi) {
            side = -side;
            tx = s.x + side * Tuning.TP_BEHIND;
        }
        tx = clamp(tx, lo, hi);
        events.emit(Events.TELEPORT, g.x, g.centerY(), tx, tpKind, tick);
        g.x = g.px = tx;
        g.y = g.py = s.y;
        g.vx = g.vy = 0;
        g.facing = -side;
        g.scripted = true;
        interruptSukuna();
        s.scripted = true;
        s.captured = false;
        s.knockedAir = false;
        s.vx = s.vy = 0;
        tpCd = Tuning.TP_COOLDOWN;
        if (tpKind == Attack.RED) redCd = redCdMax = Tuning.RED_COOLDOWN;
        else if (tpKind == Attack.PURPLE) purpleCd = purpleCdMax = Tuning.PURPLE_200_COOLDOWN;
        else blueCd = blueCdMax = Tuning.BLUE_COOLDOWN;
        tpHits = 0;
        tpBaseY = s.y;
        if (tpKind == Attack.PURPLE) s.facing = tx > s.x ? -1 : 1; // 背对着五条悟（这样才拍得到肩膀）
        enter(g, tpKind == Attack.RED ? Fighter.TP_RED : tpKind == Attack.PURPLE ? Fighter.TP_PURPLE : Fighter.TP_BLUE);
    }

    private void endScript() {
        gojo.scripted = false;
        sukuna.scripted = false;
    }

    /** 苍：把宿傩吸到空中，自己跟上去，苍拳连打 5 下；最后一下击飞、击倒。 */
    private void scriptBlue(int st) {
        Fighter g = gojo, s = sukuna;
        if (s.state == Fighter.KO) {
            endScript();
            enter(g, Fighter.FREE);
            return;
        }
        if (tpHits < Tuning.TP_BLUE_HITS) {
            float k = Math.min(1f, st / (float) Tuning.TP_BLUE_LIFT), e = k * k * (3 - 2 * k);
            s.y = tpBaseY + (Tuning.TP_BLUE_HEIGHT - tpBaseY) * e;
            s.vx = s.vy = 0;
            g.y = s.y;
            g.x = clamp(s.x - g.facing * 60, Tuning.BODY_HALF_W, Tuning.WIDTH - Tuning.BODY_HALF_W);
            g.vx = g.vy = 0;
            if (st >= Tuning.TP_BLUE_FIRST_HIT && (st - Tuning.TP_BLUE_FIRST_HIT) % Tuning.TP_BLUE_HIT_GAP == 0) {
                tpHits++;
                boolean last = tpHits == Tuning.TP_BLUE_HITS;
                if (last) { // 放开：最后一下把他打飞
                    endScript();
                    s.downTicks = Tuning.DOWN_TICKS;
                }
                attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.BLUE_FIST, ++attackSeq, Tuning.SMASH_DAMAGE_MILLI, // 每下 115
                        last ? g.facing * Tuning.TP_BLUE_LAUNCH_X : 0, last ? Tuning.TP_BLUE_LAUNCH_Y : 0, last ? 60 : 40, 0);
                apply(attack);
                hitstop = last ? Tuning.FIST_HITSTOP : Tuning.TP_BLUE_HITSTOP;
                tpEndAt = st + Tuning.TP_BLUE_AFTER;
            }
        } else if (st >= tpEndAt && g.onGround) {
            enter(g, Fighter.FREE);
        }
    }

    /** 赫：在背后蓄一发很大的赫，把宿傩轰出战场宽度 85%（撞墙就停），落地击倒。 */
    private void scriptRed(int st) {
        Fighter g = gojo, s = sukuna;
        if (s.state == Fighter.KO) {
            endScript();
            if (st > Tuning.TP_RED_CHARGE) enter(g, Fighter.FREE);
        }
        if (st < Tuning.TP_RED_CHARGE) {
            s.vx = s.vy = 0;
            g.vx = g.vy = 0;
        } else if (st == Tuning.TP_RED_CHARGE) {
            float hx = g.x + g.facing * 24;
            events.emit(Events.RED_FIRE, hx, g.y + 56, g.facing, 2, tick);
            tpFromX = s.x;
            tpToX = clamp(s.x + g.facing * Tuning.TP_RED_DISTANCE, Tuning.BODY_HALF_W, Tuning.WIDTH - Tuning.BODY_HALF_W);
            g.scripted = false;
            attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.RED, ++attackSeq, Tuning.TP_RED_DAMAGE_MILLI, 0, 0, Tuning.TP_RED_FLIGHT + 20, 0);
            apply(attack);
            events.emit(Events.RED_BURST, s.x, s.centerY(), g.facing, 2, tick);
            hitstop = Tuning.RED_MAX_HITSTOP;
        } else {
            int ft = st - Tuning.TP_RED_CHARGE;
            if (s.scripted) {
                float k = Math.min(1f, ft / (float) Tuning.TP_RED_FLIGHT);
                s.x = tpFromX + (tpToX - tpFromX) * k;
                s.y = 4 * Tuning.TP_RED_ARC * k * (1 - k);
                s.vx = s.vy = 0;
                if (k >= 1) {
                    s.scripted = false;
                    s.y = 0;
                    s.onGround = true;
                    s.downTicks = Tuning.DOWN_TICKS;
                    events.emit(Events.LAND, s.x, 0, 900, tick);
                }
            }
            if (ft >= Tuning.TP_RED_AFTER && !s.scripted) enter(g, Fighter.FREE);
        }
    }

    /** 茈：拍拍宿傩的肩膀，等他回头，贴脸一发茈把他轰飞（1900）。 */
    private void scriptPurple(int st) {
        Fighter g = gojo, s = sukuna;
        if (s.state == Fighter.KO) {
            endScript();
            enter(g, Fighter.FREE);
            return;
        }
        if (st < Tuning.TP_PURPLE_FIRE) {
            s.vx = s.vy = 0;
            g.vx = g.vy = 0;
            if (st == Tuning.TP_PURPLE_TAP) events.emit(Events.CINE, (g.x + s.x) * 0.5f, s.y + Tuning.BODY_H - 18, Events.CINE_TAP, tick);
            if (st == Tuning.TP_PURPLE_TURN) { // 回头
                s.facing = g.x >= s.x ? 1 : -1;
                events.emit(Events.CINE, s.x, s.centerY(), Events.CINE_TURN, tick);
            }
        } else if (st == Tuning.TP_PURPLE_FIRE) {
            endScript();
            float hx = g.x + g.facing * 24;
            purple.start(++attackSeq, hx, g.y + 56, g.facing);
            purple.max = true;
            purple.hitMask = ~0; // 这一发只打宿傩（贴脸已经结算），飞出去的只是光
            purple200 = true;
            events.emit(Events.PURPLE_FIRE, hx, g.y + 56, g.facing, 2, tick);
            attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.PURPLE, purple.instanceId, Tuning.TP_PURPLE_DAMAGE_MILLI,
                    g.facing * Tuning.TP_PURPLE_IMPULSE_X, Tuning.TP_PURPLE_IMPULSE_Y, Tuning.TP_PURPLE_HITSTUN, 0);
            apply(attack);
            s.downTicks = Tuning.DOWN_TICKS;
            s.pullResist = Tuning.RED_PULL_RESIST;
            hitstop = Tuning.PURPLE_200_HITSTOP;
        } else if (st >= Tuning.TP_PURPLE_FIRE + Tuning.TP_PURPLE_AFTER) {
            enter(g, Fighter.FREE);
        }
    }

    /* ---------------- 茈·最大输出 ---------------- */

    /** 苍、赫、茈都好了才能放（念咒被打断的那一下也算没好）。 */
    private boolean maxPurpleReady() {
        return blueCd == 0 && redCd == 0 && purpleCd == 0 && blueFailLock == 0 && redFailLock == 0 && purpleFailLock == 0
                && sukuna.state != Fighter.KO;
    }

    /** 开始念茈·最大输出的十段咒词（苍三段、赫三段、茈四段）。 */
    private void startMaxChant() {
        Fighter g = gojo;
        enter(g, Fighter.PURPLE_MAX_CHANT);
        drive(g, 0);
        bufRed = bufPurple = bufMelee = bufJump = 0;
        bqSize = 0; // 苍按下 / 松开的排队作废，下面直接看按没按着
        redLetGo = false;
        chantKind = Attack.PURPLE_MAX;
        chantPhase = 0;
        events.emit(Events.CHANT, g.x, g.centerY(), 0, Attack.PURPLE_MAX, tick);
    }

    /** 念到第几段（0–2 苍、3–5 赫、6–9 茈）。 */
    static int maxChantPhase(int st) {
        int b = Tuning.CHANT3_PHRASE * 3;
        if (st < b) return st / Tuning.CHANT3_PHRASE;
        if (st < 2 * b) return 3 + (st - b) / Tuning.CHANT3_PHRASE;
        return 6 + Math.min(Tuning.CHANT_PHRASES - 1, (st - 2 * b) / Tuning.CHANT_PHRASE);
    }

    /** 念完：进入固定动作。宿傩（和魔虚罗）手上的动作作废，都被定住；场上的苍、赫、无指向茈、顎吐收掉。 */
    private void startPurpleMax() {
        Fighter g = gojo, s = sukuna, m = mahoraga;
        interruptSukuna();
        interruptMahoraga();
        endScript();
        cine = CINE_PURPLE_MAX;
        cineTick = 0;
        cineMaho = m.present;
        cineSx = clamp(s.x, 220, Tuning.WIDTH - 220);
        cineSy = Tuning.PMAX_SKY_Y;
        cineGx = g.x;
        cineGy0 = g.y;
        cineSx0 = s.x;
        cineSy0 = s.y;
        cineMx0 = m.x;
        cineSelf = Math.abs(g.x - cineSx) <= Tuning.PMAX_RADIUS;
        cineBlueR = cinePurpleR = 0;
        cineRedX = cineRedY = -1000;
        for (int i = 0; i < Fighter.COUNT; i++) {
            Fighter f = fighter(i);
            f.scripted = true;
            f.captured = false;
            f.knockedAir = false;
            f.vx = f.vy = 0;
            f.hitstun = 0;
        }
        blue.phase = BlueOrb.OFF;
        red.active = false;
        hollow.active = false;
        agito.active = false;
        chantPhase = -1;
        enter(g, Fighter.PURPLE_MAX);
        purpleCd = purpleCdMax = Tuning.PURPLE_MAX_COOLDOWN;
        blueCd = blueCdMax = Tuning.BLUE_COOLDOWN_CHARGED;
        redCd = redCdMax = Tuning.RED_MAX_COOLDOWN;
        clearInputs();
        events.emit(Events.CAST, g.x, g.centerY(), 5, Attack.PURPLE, tick);
    }

    private static float ease(float k) {
        k = k < 0 ? 0 : k > 1 ? 1 : k;
        return k * k * (3 - 2 * k);
    }

    /** 固定动作的一 tick：按时间线摆好每个人、苍、赫、茈的位置；相撞后结算伤害；最后放开，大家掉回地上。 */
    private void stepCine() {
        int t = ++cineTick;
        Fighter g = gojo, s = sukuna, m = mahoraga;
        int side = cineSx >= cineGx ? 1 : -1; // 苍在五条悟的哪一边
        g.stateTick = t;
        // 苍：先在天上张开，追加咏唱时变大，和赫相撞后被吃掉
        if (t <= Tuning.PMAX_RISE) cineBlueR = Tuning.PMAX_BLUE_R * ease(t / (float) Tuning.PMAX_RISE);
        else if (t <= Tuning.PMAX_CHANT) cineBlueR = Tuning.PMAX_BLUE_R;
        else if (t <= Tuning.PMAX_CONTACT) cineBlueR = Tuning.PMAX_BLUE_R + (Tuning.PMAX_BLUE_R_MAX - Tuning.PMAX_BLUE_R) * ease((t - Tuning.PMAX_CHANT) / 300f);
        else cineBlueR = Math.max(0, Tuning.PMAX_BLUE_R_MAX * (1 - (t - Tuning.PMAX_CONTACT) / 30f));
        // 五条悟：原地升到空中，一直看着苍
        g.x = cineGx;
        g.y = cineGy0 + (Tuning.PMAX_GOJO_Y - cineGy0) * ease(t / (float) Tuning.PMAX_RISE);
        g.vx = g.vy = 0;
        g.facing = side;
        // 宿傩：被苍吸到天上 → 挣开冲向赫、斩它 → 被变大的苍吸回去
        float px = cineSx - side * 40, py = cineSy - 90;      // 被吸着的位置（苍的下方）
        float bx = cineSx - side * 10, by = cineSy - 50;      // 被吸回去后（更靠近苍心）
        if (t <= Tuning.PMAX_RISE + 60) {
            float k = ease(t / (float) (Tuning.PMAX_RISE + 60));
            s.x = cineSx0 + (px - cineSx0) * k;
            s.y = cineSy0 + (py - cineSy0) * k;
        } else if (t <= Tuning.PMAX_INTERFERE) {
            s.x = px;
            s.y = py + (float) Math.sin(t * 0.21f) * 3;
        } else if (t <= Tuning.PMAX_PULLBACK) {
            if (t == Tuning.PMAX_INTERFERE + 1) { // 挣开：冲向赫和苍之间
                cineLx = (cineRedX + cineSx) * 0.5f;
                cineLy = Math.max(cineSy - 140, (cineRedY + cineSy) * 0.5f - 30);
            }
            float k = ease((t - Tuning.PMAX_INTERFERE) / 120f);
            s.x = px + (cineLx - px) * k;
            s.y = py + (cineLy - py) * k;
            s.facing = cineRedX >= s.x ? 1 : -1;
            int u = t - Tuning.PMAX_INTERFERE - 40;
            if (u >= 0 && u % 70 == 0 && u <= 210) { // 朝赫斩过去，斩到苍的边上就被吸住（挡下）
                float ex = cineSx - side * cineBlueR * 0.9f;
                events.emit(Events.CINE, s.x, s.centerY(), Events.CINE_SLASH, Math.round(ex), tick);
            }
        } else {
            float k = ease((t - Tuning.PMAX_PULLBACK) / (float) (Tuning.PMAX_CLOSE - Tuning.PMAX_PULLBACK));
            float fx = cineLx, fy = cineLy;
            s.x = fx + (bx - fx) * k;
            s.y = fy + (by - fy) * k + (t > Tuning.PMAX_CLOSE ? (float) Math.sin(t * 0.3f) * 2 : 0);
        }
        s.vx = s.vy = 0;
        // 魔虚罗（在场的版本）：两次跳起来挥剑想劈开苍和赫之间，被苍挡下、吸住
        if (cineMaho) {
            float mx = cineMx0;
            float jx = cineSx - side * (cineBlueR + 20), jy = cineSy - 30;
            if (t > 400 && t <= 520) {
                float k = (t - 400) / 120f;
                m.x = mx + (jx - mx) * k;
                m.y = 4 * 120 * k * (1 - k) + jy * k * k;
                if (t == 500) events.emit(Events.CINE, m.x, m.centerY(), Events.CINE_MAHO_SWING, tick);
                m.facing = cineSx >= m.x ? 1 : -1;
            } else if (t > 520 && t <= 600) {
                float k = (t - 520) / 80f;
                m.x = jx + (mx - jx) * k;
                m.y = jy * (1 - k * k);
            } else if (t > 600 && t <= Tuning.PMAX_PULLBACK) {
                float k = (t - 600) / 120f;
                m.x = mx + (jx - mx) * Math.min(1, k * 1.2f);
                m.y = Math.min(jy, 4 * 160 * k * (1 - k) + jy * k);
                if (t == 690) events.emit(Events.CINE, m.x, m.centerY(), Events.CINE_MAHO_SWING, tick);
                m.facing = cineSx >= m.x ? 1 : -1;
            } else if (t > Tuning.PMAX_PULLBACK) {
                float k = ease((t - Tuning.PMAX_PULLBACK) / 180f); // 被苍吸住，挂在苍的边上
                m.x = jx + (cineSx - side * 60 - jx) * k;
                m.y = jy + (cineSy - 80 - jy) * k;
            } else {
                m.x = mx;
                m.y = 0;
            }
            m.vx = m.vy = 0;
        }
        // 赫：从五条悟手里射上天，弧线朝苍慢慢靠过去
        if (t == Tuning.PMAX_RED) {
            cineRx0 = g.x + side * 20;
            cineRy0 = g.y + 70;
            events.emit(Events.CINE, cineRx0, cineRy0, Events.CINE_RED, tick);
        }
        if (t >= Tuning.PMAX_RED && t < Tuning.PMAX_CONTACT) {
            float k = (t - Tuning.PMAX_RED) / (float) (Tuning.PMAX_CONTACT - Tuning.PMAX_RED);
            k = k < 0.85f ? k : 0.85f + (k - 0.85f) * 1.0f;
            float cx = cineRx0, cy = cineSy + 170, u = 1 - k;
            float ex = cineSx - side * 4, ey = cineSy;
            cineRedX = u * u * cineRx0 + 2 * u * k * cx + k * k * ex;
            cineRedY = u * u * cineRy0 + 2 * u * k * cy + k * k * ey;
        }
        // 追加咏唱：苍变大，挡下干扰
        int c = t - Tuning.PMAX_CHANT;
        if (c >= 0 && c % Tuning.PMAX_CHANT_GAP == 0 && c / Tuning.PMAX_CHANT_GAP < 3) {
            events.emit(Events.CINE, g.x, g.centerY(), Events.CINE_CHANT, c / Tuning.PMAX_CHANT_GAP, tick);
        }
        // 相撞 → 茈爆开 → 结算
        if (t == Tuning.PMAX_CONTACT) {
            cineRedX = cineRedY = -1000;
            events.emit(Events.CINE, cineSx, cineSy, Events.CINE_CONTACT, tick);
        }
        if (t > Tuning.PMAX_CONTACT) {
            cinePurpleR = Tuning.PMAX_RADIUS * ease((t - Tuning.PMAX_CONTACT) / 120f);
            if (t > Tuning.PMAX_END - 90) cinePurpleR *= Math.max(0, (Tuning.PMAX_END - t) / 90f);
        }
        if (t == Tuning.PMAX_HIT) {
            events.emit(Events.CINE, cineSx, cineSy, Events.CINE_BLAST, tick);
            int dir = side;
            attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.PURPLE_MAX, ++attackSeq, Tuning.PURPLE_MAX_DAMAGE_MILLI, 0, 0, Tuning.PMAX_HITSTUN, 0);
            apply(attack);
            if (m.present) {
                attack.set(Fighter.GOJO, Fighter.MAHORAGA, Attack.PURPLE_MAX, attackSeq, Tuning.PURPLE_MAX_DAMAGE_MILLI, 0, 0, Tuning.PMAX_HITSTUN, 0);
                apply(attack);
            }
            if (cineSelf) { // 五条悟在爆炸范围里：自己也挨（无限挡不住自己的术式）
                attack.set(Fighter.GOJO, Fighter.GOJO, Attack.PURPLE_MAX, attackSeq, Tuning.PURPLE_MAX_SELF_DAMAGE_MILLI, 0, 0, 0, 0);
                apply(attack);
            }
            hitstop = 0;
        }
        if (t >= Tuning.PMAX_END) endPurpleMax(side);
    }

    /** 固定动作结束：放开大家，从空中掉下来；宿傩（魔虚罗）被炸飞、落地后倒地。 */
    private void endPurpleMax(int side) {
        Fighter g = gojo, s = sukuna, m = mahoraga;
        cine = CINE_NONE;
        cineBlueR = cinePurpleR = 0;
        for (int i = 0; i < Fighter.COUNT; i++) {
            Fighter f = fighter(i);
            f.scripted = false;
            f.px = f.x;
            f.py = f.y;
            f.vx = f.vy = 0;
        }
        s.vx = side * 260;
        s.onGround = false;
        s.knockedAir = s.y > 0;
        s.downTicks = Tuning.DOWN_TICKS;
        if (m.present) {
            m.onGround = false;
            m.knockedAir = m.y > 0;
        }
        g.onGround = false;
        enter(g, Fighter.FREE);
        clearInputs();
        events.emit(Events.CINE, cineSx, cineSy, Events.CINE_END, tick);
        // 打空血的现在就结算（不然下一 tick 零血的五条悟还能先出招）
        if (m.present && m.hpMilli == 0) removeMahoraga(0);
        for (int i = 0; i < 2; i++) if (fighter(i).hpMilli == 0) knockOut(fighter(i));
    }

    /** 自由状态：走、跳、出招。缓冲着的按键在这里兑现。 */
    private void free(Fighter g, TickInput in) {
        while (blueNextIsUp()) bluePop(); // 没在蓄的时候松开：没有意义
        move(g, in.moveX, 1f);
        faceByInput(g, in.moveX);
        if (bufJump > 0 && g.onGround) {
            bufJump = 0;
            g.vy = Tuning.JUMP_SPEED;
            g.onGround = false;
        }
        if (bufDodge > 0 && dodgeCd == 0) {
            bufDodge = 0;
            startDodge(g, in);
        } else if (bufDomain > 0 && domainCd == 0 && domainTicks == 0) {
            bufDomain = 0;
            startDomain();
        } else if (bufMelee > 0 && meleeCd == 0) {
            bufMelee = 0;
            if (in.jumpHeld) { // 按住跳跃时点体术：下砸苍拳
                startFist(g);
            } else {
                enter(g, Fighter.MELEE);
                meleeReleased = !in.meleeHeld;
                meleeCd = Tuning.MELEE_COOLDOWN;
                gMeleeSeq = ++attackSeq;
                meleeCombo++;
            }
        } else if (bufRed > 0 && redCd == 0 && redFailLock == 0 && dodgeCombo(in)) {
            bufRed = 0;
            startTeleport(Attack.RED); // 按住闪避时点赫：瞬移
        } else if (bufRed > 0 && redCd == 0 && redFailLock == 0) {
            bufRed = 0;
            enter(g, Fighter.RED_HOLD);
            redReleased = !in.redHeld;
            redLetGo = false;
            events.emit(Events.CAST, g.x, g.centerY(), 0, Attack.RED, tick);
        } else if (bufPurple > 0 && purpleCd == 0 && purpleFailLock == 0 && dodgeCombo(in)) {
            bufPurple = 0;
            startTeleport(Attack.PURPLE); // 按住闪避时点茈：瞬移·茈
        } else if (bufPurple > 0 && purpleCd == 0 && purpleFailLock == 0) {
            bufPurple = 0;
            enter(g, Fighter.PURPLE_WINDUP);
            purpleReleased = !in.purpleHeld;
            purpleLetGo = false;
            events.emit(Events.CAST, g.x, g.centerY(), 0, Attack.PURPLE, tick);
        } else if (blueNextIsDown() && blueCd == 0 && blueFailLock == 0 && dodgeCombo(in)) {
            bluePop();
            if (blueNextIsUp()) bluePop();
            startTeleport(Attack.BLUE_FIST); // 按住闪避时点苍：瞬移
        } else if (blueNextIsDown() && blueCd == 0 && blueFailLock == 0 && !blue.active()) {
            bluePop();
            enter(g, Fighter.BLUE_HOLD);
            bufMelee = bufRed = bufJump = bufPurple = 0; // 更早按的那些被这次苍取代，不会马上把蓄力取消掉
            updateAim(in);
            if (blueNextIsUp()) { // 同一 tick 里按下又松开：普通苍
                bluePop();
                startBlueWindup(false);
            }
        }
    }

    /** 后摇里想动了（推摇杆、按了别的键）：提前结束后摇，这一 tick 就按自由状态处理。 */
    private void cancelInto(Fighter g, TickInput in) {
        enter(g, Fighter.FREE);
        free(g, in);
    }

    private boolean wantsToAct(TickInput in) {
        return Math.abs(in.moveX) > 0.3f || bufJump > 0 || bufMelee > 0 || bufRed > 0 || bufPurple > 0 || bufDomain > 0 || bufDodge > 0 || blueNextIsDown();
    }

    private void enter(Fighter f, int state) {
        f.state = state;
        f.stateTick = 0;
    }

    private void move(Fighter f, float stick, float mult) {
        drive(f, stick * Tuning.MOVE_SPEED * mult);
    }

    private static void drive(Fighter f, float vx) {
        f.vx = vx;
        f.selfMove = vx != 0;
    }

    private void faceByInput(Fighter f, float stick) {
        if (stick > 0.2f) f.facing = 1;
        else if (stick < -0.2f) f.facing = -1;
        else f.facing = sukuna.x >= f.x ? 1 : -1; // 不动时面向宿傩
    }

    private void updateAim(TickInput in) {
        float mx = in.moveX, my = in.moveY;
        float m = (float) Math.sqrt(mx * mx + my * my);
        if (m > 0.3f) {
            aimX = mx / m;
            aimY = my / m;
        } else {
            aimX = 0; // 0 表示“自动瞄准宿傩”
            aimY = 0;
        }
    }

    private void startBlueWindup(boolean charged) {
        pendingCharged = charged;
        enter(gojo, Fighter.BLUE_WINDUP);
        events.emit(Events.CAST, gojo.x, gojo.centerY(), charged ? 1 : 0, Attack.BLUE_FIELD, tick);
    }

    /** 投出苍：落点距投出点不超过战场宽度 30%，并限制在战场内。 */
    private void spawnBlue(boolean charged) {
        Fighter g = gojo;
        float ox = clamp(g.x + g.facing * 22, Tuning.BODY_HALF_W, Tuning.WIDTH - Tuning.BODY_HALF_W), oy = g.y + 56;
        float dx, dy, len;
        if (aimX == 0 && aimY == 0) {
            // 自动瞄准：朝向一侧有宿傩就瞄宿傩的身体中心，否则朝正前方
            float tx = sukuna.x - ox, ty = sukuna.centerY() - oy;
            boolean ahead = tx * g.facing > 0;
            if (ahead) {
                len = (float) Math.sqrt(tx * tx + ty * ty);
                dx = len > 0 ? tx / len : g.facing;
                dy = len > 0 ? ty / len : 0;
            } else {
                dx = g.facing;
                dy = 0;
                len = Tuning.BLUE_MAX_THROW;
            }
        } else {
            dx = aimX;
            dy = aimY;
            len = Tuning.BLUE_MAX_THROW;
        }
        len = Math.min(len, Tuning.BLUE_MAX_THROW);
        float cx = clamp(ox + dx * len, Tuning.BODY_HALF_W, Tuning.WIDTH - Tuning.BODY_HALF_W);
        float cy = clamp(oy + dy * len, Tuning.BODY_H * 0.5f, Tuning.CEILING);
        BlueOrb b = blue;
        b.phase = BlueOrb.FLY;
        b.charged = charged;
        b.tick = 0;
        b.instanceId = ++attackSeq;
        b.ox = b.x = b.px = ox;
        b.oy = b.y = b.py = oy;
        b.cx = cx;
        b.cy = cy;
        b.radius = Tuning.BLUE_RADIUS * (charged ? Tuning.BLUE_RANGE_MULT_CHARGED : 1f);
        b.pull = charged ? Tuning.BLUE_PULL_MULT_CHARGED : 1f;
        b.accel = Tuning.BLUE_ACCEL * b.pull;
        java.util.Arrays.fill(b.remainder, 0);
        java.util.Arrays.fill(b.shownMilli, 0);
        blueCd = blueCdMax = charged ? Tuning.BLUE_COOLDOWN_CHARGED : Tuning.BLUE_COOLDOWN;
        events.emit(Events.BLUE_SPAWN, ox, oy, charged ? 1 : 0, tick);
    }

    private void fireRed(boolean max) {
        Fighter g = gojo;
        enter(g, Fighter.RED_ACTIVE);
        redMax = max;
        redCd = redCdMax = max ? Tuning.RED_MAX_COOLDOWN : Tuning.RED_COOLDOWN;
        float hx = clamp(g.x + g.facing * 24, 0, Tuning.WIDTH);
        red.start(++attackSeq, hx, g.y + 56, g.facing);
        red.max = max;
        events.emit(Events.RED_FIRE, hx, g.y + 56, g.facing, max ? 1 : 0, tick);
    }

    private void firePurple(boolean is200) {
        Fighter g = gojo;
        enter(g, Fighter.PURPLE_RECOVER);
        purple200 = is200;
        chantPhase = -1;
        purpleCd = purpleCdMax = is200 ? Tuning.PURPLE_200_COOLDOWN : Tuning.PURPLE_COOLDOWN;
        float hx = clamp(g.x + g.facing * 30, 0, Tuning.WIDTH);
        purple.start(++attackSeq, hx, g.y + 56, g.facing);
        purple.max = is200;
        if (is200) events.emit(Events.CAST, g.x, g.centerY(), 2, Attack.PURPLE, tick);
        events.emit(Events.PURPLE_FIRE, hx, g.y + 56, g.facing, is200 ? 1 : 0, tick);
    }

    /**
     * 念咒被打断（松手、被攻击；按住苍、赫、茈还没开始念的起手也算）：什么都不放，短时间内不能再按。
     * 苍拳和瞬移的前摇也一样，被攻击碰到就断（“容易被打断”）。
     */
    private void breakChant() {
        Fighter g = gojo;
        int kind;
        switch (g.state) {
            case Fighter.PURPLE_CHANT:
                kind = Attack.PURPLE;
                purpleFailLock = Tuning.CHANT_BROKEN_LOCK;
                break;
            case Fighter.PURPLE_WINDUP:
                if (purpleReleased) return; // 点按的普通茈不受影响
                kind = Attack.PURPLE;
                purpleFailLock = Tuning.CHANT_BROKEN_LOCK;
                break;
            case Fighter.BLUE_CHANT:
            case Fighter.BLUE_HOLD:
                kind = Attack.BLUE_FIELD;
                blueFailLock = Tuning.CHANT_BROKEN_LOCK;
                break;
            case Fighter.RED_CHANT:
                kind = Attack.RED;
                redFailLock = Tuning.CHANT_BROKEN_LOCK;
                break;
            case Fighter.RED_HOLD:
                if (redReleased) return;    // 点按的普通赫不受影响
                kind = Attack.RED;
                redFailLock = Tuning.CHANT_BROKEN_LOCK;
                break;
            case Fighter.FIST_WINDUP:
                if (!fistSmash) return; // 长按体术的苍拳：挨打才断（interruptGojo）
                enter(g, Fighter.FREE);
                events.emit(Events.CHANT, g.x, g.centerY(), -1, Attack.BLUE_FIST, tick);
                return;
            case Fighter.TP_WINDUP:
                enter(g, Fighter.FREE);
                if (tpKind == Attack.PURPLE) { // 瞬移·茈的咒词被打断
                    chantPhase = -1;
                    purpleFailLock = Tuning.CHANT_BROKEN_LOCK;
                    events.emit(Events.CHANT, g.x, g.centerY(), -1, Attack.PURPLE, tick);
                } else {
                    events.emit(Events.CHANT, g.x, g.centerY(), -1, Attack.BLUE_FIST, tick);
                }
                return;
            case Fighter.PURPLE_MAX_CHANT:
                kind = Attack.PURPLE_MAX;
                blueFailLock = redFailLock = purpleFailLock = Tuning.CHANT_BROKEN_LOCK;
                break;
            default:
                return;
        }
        enter(g, Fighter.FREE);
        chantPhase = -1;
        events.emit(Events.CHANT, g.x, g.centerY(), -1, kind, tick);
    }

    /** 五条悟挨打（硬直）：手上的动作作废（念咒算打断；蓄苍被打断短时间内不能再按）。还没放出去的招不进冷却。 */
    private void interruptGojo() {
        Fighter g = gojo;
        switch (g.state) {
            case Fighter.FREE:
            case Fighter.KO:
            case Fighter.DOMAIN_CAST:
                return;
            case Fighter.TP_BLUE:
            case Fighter.TP_RED:
            case Fighter.TP_PURPLE:
            case Fighter.PURPLE_MAX:
                return; // 固定动作里无敌
            case Fighter.PURPLE_CHANT:
            case Fighter.PURPLE_WINDUP:
            case Fighter.BLUE_CHANT:
            case Fighter.RED_CHANT:
            case Fighter.RED_HOLD:
            case Fighter.FIST_WINDUP:
            case Fighter.TP_WINDUP:
            case Fighter.PURPLE_MAX_CHANT:
                breakChant();
                break;
            case Fighter.BLUE_HOLD:
            case Fighter.BLUE_WINDUP:
                blueFailLock = Tuning.BLUE_FAIL_LOCK;
                break;
            default:
                break;
        }
        if (g.state != Fighter.FREE) enter(g, Fighter.FREE);
    }

    /* ---------------- 领域展开 ---------------- */

    /** 按下领域展开：世界停住，等画面把过场放完（或者放不了）再调 endCutscene。 */
    private void startDomain() {
        enter(gojo, Fighter.DOMAIN_CAST);
        drive(gojo, 0);
        cutscene = true;
        castId++;
        clearInputs();
        events.emit(Events.DOMAIN_CUTSCENE, gojo.x, gojo.centerY(), castId, tick);
    }

    /** 过场结束（播完、出错、超时都走这里；同一次只算一次，旧的忽略）：无量空处展开。 */
    public void endCutscene(int id) {
        if (!cutscene || id != castId) return;
        cutscene = false;
        enter(gojo, Fighter.FREE);
        clearInputs();
        domainTicks = Tuning.DOMAIN_DURATION;
        domains.sureHit[Fighter.GOJO][Fighter.SUKUNA] = true;
        domains.sureHit[Fighter.GOJO][Fighter.MAHORAGA] = true;
        interruptSukuna();
        interruptMahoraga(); // 魔虚罗在场也一样被困住
        if (ampTicks > 0) closeAmp(); // 无量空处生效：展延被迫关闭
        events.emit(Events.DOMAIN_OPEN, gojo.x, gojo.centerY(), Tuning.DOMAIN_DURATION, tick);
    }

    /* ---------------- 宿傩：动作执行 ---------------- */

    /** 宿傩现在能不能主动行动（倒地、硬直、被苍吸住/抓住、被领域困住时不能）。 */
    public boolean sukunaCanAct() {
        Fighter s = sukuna;
        return s.state != Fighter.KO && s.hitstun == 0 && !s.captured && domainTicks == 0 && !inBlueField(s) && !s.knockedAir
                && !s.scripted && s.downTicks == 0;
    }

    /** f 在苍的吸引范围里（被吸着，不能主动行动）。 */
    public boolean inBlueField(Fighter f) {
        if (blue.phase != BlueOrb.PULL || f.pullResist > 0) return false;
        float dx = blue.cx - f.x, dy = blue.cy - f.centerY();
        return dx * dx + dy * dy < blue.radius * blue.radius;
    }

    private void controlSukuna(SukunaIntent i) {
        Fighter s = sukuna;
        if (s.state == Fighter.KO) return;
        if (!sukunaCanAct()) {
            // 反转术式、变原身不可打断：被控住也照样走完（变身在无量空处里计时暂停）
            if (s.state == Fighter.S_TRANSFORM && domainTicks > 0) return;
            if (s.state != Fighter.S_RCT && s.state != Fighter.S_TRANSFORM) {
                interruptSukuna(); // 被控住：手上的动作作废；击飞、吸引的速度不去动它
                return;
            }
        }
        s.stateTick++;
        int st = s.stateTick;
        switch (s.state) {
            case Fighter.FREE: {
                if (sStagger > 0) { // 招被打断的硬直
                    drive(s, 0);
                    return;
                }
                if (!config.sukunaAi && i.moveX == 0 && !i.melee && !i.dismantle && !i.jump && !i.amp && !i.ampOff && !i.world && !i.rct
                        && !i.agito && !i.summon && !i.transform) return; // 活靶子：什么都不做
                drive(s, i.moveX * Tuning.SUKUNA_SPEED);
                if (i.jump && s.onGround) {
                    s.vy = Tuning.JUMP_SPEED;
                    s.onGround = false;
                }
                boolean amp = ampTicks > 0, technique = !amp && ampLock == 0; // 展延期间、刚关掉时不能用生得术式
                boolean tenShadows = technique && !sukunaTrueForm;              // 十种影法术（原身以后没有）
                if (i.ampOff && amp) {
                    closeAmp();
                } else if (i.transform && config.trueForm && !sukunaTrueForm && s.hpMilli > 0) {
                    startTransform();
                } else if (sAttackGap == 0 && i.melee && sMeleeCd == 0) {
                    enter(s, Fighter.S_MELEE);
                    drive(s, 0);
                    sMeleeCd = Tuning.S_MELEE_COOLDOWN;
                    sAttackGap = Tuning.S_ATTACK_GAP;
                    attackSeq++;
                    sMeleeHitId = -1;
                    sMeleeSeq = attackSeq;
                } else if (technique && config.shrine && sAttackGap == 0 && i.dismantle && dismantleCd == 0) {
                    enter(s, Fighter.S_DISMANTLE);
                    drive(s, 0);
                    dismantleCd = Tuning.DISMANTLE_COOLDOWN;
                    sAttackGap = Tuning.S_ATTACK_GAP;
                    adaptPending = false;
                } else if (tenShadows && config.agito && sAttackGap == 0 && i.agito && agitoCd == 0 && !agito.active) {
                    enter(s, Fighter.S_AGITO);
                    drive(s, 0);
                    agitoCd = Tuning.AGITO_COOLDOWN; // 从起手算，被打断不退
                    sAttackGap = Tuning.S_ATTACK_GAP;
                } else if (tenShadows && config.mahoraga && i.summon && !mahoragaUsed && summonCd == 0 && s.onGround) {
                    enter(s, Fighter.S_SUMMON);
                    drive(s, 0);
                    events.emit(Events.SUMMON, s.x, s.centerY(), 0, tick);
                } else if (i.amp && !amp && ampCd == 0 && s.onGround && !agito.active) { // 顎吐还在飞：不能开展延（十种影法术和展延不能同时用）
                    enter(s, Fighter.S_AMP_START);
                    drive(s, 0);
                } else if (technique && config.shrine && i.world && worldUnlocked && worldCd == 0 && s.onGround) {
                    startWorldSlash();
                } else if (technique && i.rct && rctUses < Tuning.S_RCT_MAX_USES && rctCd == 0 && s.onGround && s.hpMilli < s.maxHpMilli) {
                    enter(s, Fighter.S_RCT);
                    drive(s, 0);
                    rctCd = Tuning.S_RCT_COOLDOWN;
                }
                break;
            }
            case Fighter.S_MELEE: {
                boolean active = st > Tuning.S_MELEE_STARTUP && st <= Tuning.S_MELEE_STARTUP + Tuning.S_MELEE_ACTIVE;
                drive(s, active ? s.facing * Tuning.S_MELEE_LUNGE : 0);
                if (st >= Tuning.S_MELEE_STARTUP + Tuning.S_MELEE_ACTIVE + Tuning.S_MELEE_RECOVERY) enter(s, Fighter.FREE);
                break;
            }
            case Fighter.S_DISMANTLE:
                drive(s, 0);
                if (st == Tuning.DISMANTLE_WINDUP) fireDismantle();
                if (st >= Tuning.DISMANTLE_WINDUP + Tuning.DISMANTLE_RECOVERY) {
                    enter(s, Fighter.FREE);
                    settleAdaptation(); // 收完招才算一次有效的试探
                }
                break;
            case Fighter.S_AMP_START:
                drive(s, 0);
                if (st >= Tuning.S_AMP_STARTUP) {
                    enter(s, Fighter.FREE);
                    ampTicks = Tuning.S_AMP_DURATION;
                    domains.amplification[Fighter.SUKUNA] = true;
                    events.emit(Events.AMP, s.x, s.centerY(), 1, tick);
                }
                break;
            case Fighter.S_WORLD: {
                drive(s, 0);
                int lockAt = worldWindup - (worldWindup == Tuning.S_WORLD_FIRST_WINDUP ? worldWindup : Tuning.S_WORLD_POINT);
                if (!worldLocked) s.facing = gojo.x >= s.x ? 1 : -1; // 锁定之前跟着五条悟转
                if (!worldLocked && st >= lockAt) lockWorldSlash();
                if (worldWindup == Tuning.S_WORLD_WINDUP) { // 咏唱版：结印 → 龙鳞 → 反发 → 番之流星 → 指向
                    int t = st - Tuning.S_WORLD_SIGN;
                    if (t >= 0 && t < Tuning.S_WORLD_CHANT_PHRASE * Tuning.S_WORLD_CHANT_PHRASES && t % Tuning.S_WORLD_CHANT_PHRASE == 0) {
                        events.emit(Events.WORLD_CHANT, s.x, s.centerY(), 1 + t / Tuning.S_WORLD_CHANT_PHRASE, Attack.WORLD_SLASH, tick);
                    }
                }
                if (st >= worldWindup) worldPending = true; // 这一 tick 结束前没被打断才斩出去（resolveWorldSlash）
                break;
            }
            case Fighter.S_WORLD_RECOVER:
                drive(s, 0);
                if (st >= Tuning.S_WORLD_RECOVERY) enter(s, Fighter.FREE);
                break;
            case Fighter.S_RCT: {
                if (sukunaCanAct()) drive(s, 0); // 被控时照样回血，但不去动击飞、吸引的速度
                int healFrom = Tuning.S_RCT_STARTUP, healTo = healFrom + Tuning.S_RCT_DURATION;
                if (st == healFrom + 1) {
                    rctUses++; // 进入回复阶段就算用掉一次
                    events.emit(Events.RCT, s.x, s.centerY(), 1, tick);
                }
                if (st > healFrom && st <= healTo && s.hpMilli > 0) { // 每秒 170：按累计量取整，不丢零头；这一 tick 已经被打空就不回
                    long k = st - healFrom;
                    long d = Tuning.S_RCT_HEAL_PER_SEC_MILLI * k / Tuning.HZ - Tuning.S_RCT_HEAL_PER_SEC_MILLI * (k - 1) / Tuning.HZ;
                    rctHealed += Math.min(d, s.maxHpMilli - s.hpMilli);
                    s.hpMilli = Math.min(s.maxHpMilli, s.hpMilli + d);
                }
                if (st == healTo) events.emit(Events.RCT, s.x, s.centerY(), 0, tick);
                if (st >= healTo + Tuning.S_RCT_RECOVERY) enter(s, Fighter.FREE);
                break;
            }
            case Fighter.S_AGITO: // 结手影 → 顎吐飞出去 → 收手
                drive(s, 0);
                if (st == Tuning.AGITO_SIGN) launchAgito();
                if (st >= Tuning.AGITO_SIGN + Tuning.AGITO_RECOVERY) enter(s, Fighter.FREE);
                break;
            case Fighter.S_SUMMON: // 念「布瑠部由良由良」→ 魔虚罗显现 → 收招
                drive(s, 0);
                if (st == Tuning.SUMMON_CHANT) summonMahoraga();
                if (st >= Tuning.SUMMON_CHANT + Tuning.SUMMON_RECOVERY) enter(s, Fighter.FREE);
                break;
            case Fighter.S_TRANSFORM: // 变原身：不能动，不可打断，照常挨打
                if (sukunaCanAct()) drive(s, 0);
                if (st >= Tuning.TRANSFORM_TICKS) {
                    enter(s, Fighter.FREE);
                    events.emit(Events.TRANSFORM, s.x, s.centerY(), 0, tick);
                }
                break;
            default:
                break;
        }
    }

    /** 宿傩的动作被打断（受击硬直、被苍控制、领域生效）：起手、咏唱作废；世界斩的咏唱被打断还要僵一会儿。反转术式不可打断。 */
    private void interruptSukuna() {
        Fighter s = sukuna;
        switch (s.state) {
            case Fighter.S_WORLD:
                sStagger = Tuning.S_WORLD_BREAK_LOCK;
                events.emit(Events.WORLD_CHANT, s.x, s.centerY(), -1, Attack.WORLD_SLASH, tick);
                break;
            case Fighter.S_RCT:
            case Fighter.S_TRANSFORM:
                return; // 反转术式（定）、变原身不可打断
            case Fighter.S_SUMMON:
                if (s.stateTick < Tuning.SUMMON_CHANT) { // 咏唱被打断：不算用掉，但要僵一会儿、过一阵才能再召
                    sStagger = Tuning.SUMMON_BREAK_LOCK;
                    summonCd = Tuning.SUMMON_RETRY;
                    events.emit(Events.SUMMON, s.x, s.centerY(), -1, tick);
                }
                break;
            case Fighter.S_AGITO: // 结手影时被打断：顎吐不出来（冷却不退）
            case Fighter.S_MELEE:
            case Fighter.S_DISMANTLE:
            case Fighter.S_AMP_START:
            case Fighter.S_WORLD_RECOVER:
                break;
            default:
                return;
        }
        enter(s, Fighter.FREE);
        adaptPending = false;
        worldPending = false;
    }

    private void closeAmp() {
        ampTicks = 0;
        domains.amplification[Fighter.SUKUNA] = false;
        ampCd = Tuning.S_AMP_COOLDOWN;
        ampLock = Tuning.S_AMP_EXIT;
        events.emit(Events.AMP, sukuna.x, sukuna.centerY(), 0, tick);
    }

    /** 解碰到了无限、而且收完了招：魔虚罗的法阵转一格（展延期间不会走到这里）。 */
    private void settleAdaptation() {
        if (!adaptPending) return;
        adaptPending = false;
        turnWheel();
    }

    /** 法阵转一格（不管是自己转、解被挡下还是魔虚罗被挡下，两格之间至少隔 1 秒）。转满时魔虚罗在场就适应了无限。 */
    private void turnWheel() {
        if (wheelOn == WHEEL_NONE || adaptPoints >= Tuning.S_ADAPT_POINTS || fightTicks - adaptLastTick < Tuning.S_ADAPT_POINT_GAP) return;
        adaptPoints++;
        adaptLastTick = fightTicks;
        adaptTimer = 0;
        Fighter h = wheelOn == WHEEL_MAHORAGA ? mahoraga : sukuna;
        events.emit(Events.ADAPT, h.x, h.y + h.height + 20, adaptPoints, 0, tick);
        if (adaptPoints >= Tuning.S_ADAPT_POINTS && wheelOn == WHEEL_MAHORAGA) adaptMahoraga();
    }

    /** 魔虚罗适应了无限：之后它的攻击能打穿（原作第 234 话）。 */
    private void adaptMahoraga() {
        if (mahoragaAdapted) return;
        mahoragaAdapted = true;
        domains.specialBypass[Fighter.MAHORAGA] = true;
        events.emit(Events.ADAPT, mahoraga.x, mahoraga.y + mahoraga.height + 20, adaptPoints, 2, tick);
    }

    /* ---------------- 十种影法术：顎吐、魔虚罗；原身 ---------------- */

    /** 变原身：开始时回满血（定），十种影法术没了（顎吐、魔虚罗、法阵；学会了的世界斩保留）。 */
    private void startTransform() {
        Fighter s = sukuna;
        sukunaTrueForm = true;
        transformHealed += s.maxHpMilli - s.hpMilli;
        s.hpMilli = s.maxHpMilli;
        agito.active = false;
        removeMahoraga(1);
        wheelOn = WHEEL_NONE;
        adaptPending = false;
        enter(s, Fighter.S_TRANSFORM);
        drive(s, 0);
        events.emit(Events.TRANSFORM, s.x, s.centerY(), 1, tick);
    }

    /** 顎吐从宿傩的影子里飞出去：锁定五条悟此刻的身体中心（最远 AGITO_RANGE），从上方俯冲下去。 */
    private void launchAgito() {
        Fighter s = sukuna, g = gojo;
        Agito a = agito;
        a.dir = g.x >= s.x ? 1 : -1;
        a.active = true;
        a.tick = 0;
        a.instanceId = ++attackSeq;
        a.contacted = false;
        a.x0 = a.x = a.px = s.x + a.dir * 10;
        a.y0 = a.y = a.py = s.y + 10;
        a.tx = s.x + clamp(g.x - s.x, -Tuning.AGITO_RANGE, Tuning.AGITO_RANGE);
        a.ty = g.centerY();
        events.emit(Events.AGITO, a.x, a.y, 0, tick);
    }

    /** 顎吐：沿弧线飞到锁定点上方再扑下来（二次贝塞尔，控制点在落点正上方），到了以后电击一下就消失。 */
    private void updateAgito() {
        Agito a = agito;
        if (!a.active) return;
        a.tick++;
        if (a.tick <= Tuning.AGITO_DIVE) {
            float k = a.tick / (float) Tuning.AGITO_DIVE, u = 1 - k;
            a.x = u * u * a.x0 + (2 * u * k + k * k) * a.tx;
            a.y = u * u * a.y0 + 2 * u * k * (a.ty + Tuning.AGITO_DROP) + k * k * a.ty;
            if (a.tick == Tuning.AGITO_DIVE) events.emit(Events.AGITO, a.x, a.y, 1, tick);
        }
        Fighter g = gojo;
        if (a.tick >= Tuning.AGITO_DIVE && !a.contacted && g.state != Fighter.KO && !g.invuln && touches(a.x, a.y, Tuning.AGITO_RADIUS, g)) {
            a.contacted = true; // 被无限挡住（原作没有穿透的依据），但碰到了就会打断念咒和前摇
            attack.set(Fighter.SUKUNA, Fighter.GOJO, Attack.AGITO, a.instanceId, Tuning.AGITO_DAMAGE_MILLI,
                    a.dir * Tuning.AGITO_IMPULSE_X, 0, Tuning.AGITO_HITSTUN, 0);
            apply(attack);
        }
        if (a.tick >= Tuning.AGITO_DIVE + Tuning.AGITO_ACTIVE) {
            a.active = false;
            events.emit(Events.AGITO, a.x, a.y, 2, tick);
        }
    }

    /** 魔虚罗在宿傩前方（朝五条悟那边）显现：一局一次（定）；法阵移到它头上，进度共用。 */
    private void summonMahoraga() {
        Fighter s = sukuna, m = mahoraga;
        int dir = gojo.x >= s.x ? 1 : -1;
        mahoragaUsed = true;
        m.present = true;
        m.hpMilli = m.maxHpMilli;
        m.x = m.px = clamp(s.x + dir * Tuning.SUMMON_OFFSET, m.halfW, Tuning.WIDTH - m.halfW);
        m.y = m.py = 0;
        m.vx = m.vy = 0;
        m.onGround = true;
        m.facing = dir;
        m.hitstun = m.pullResist = m.downTicks = m.comboHits = m.comboTimer = 0;
        m.captured = m.knockedAir = m.scripted = false;
        enter(m, Fighter.M_RISE);
        mPunchCd = mSwordCd = mGap = mMove = 0;
        mDecide = 0;
        mReact = Tuning.MAHORAGA_REACT;
        if (wheelOn == WHEEL_SUKUNA) wheelOn = WHEEL_MAHORAGA;
        events.emit(Events.SUMMON, m.x, m.y, 1, tick);
        if (adaptPoints >= Tuning.S_ADAPT_POINTS && wheelOn == WHEEL_MAHORAGA) adaptMahoraga(); // 法阵早就转满了
    }

    /** 魔虚罗退场（why 0 被打倒，1 宿傩倒下或变原身）：之后不能再召唤；法阵跟着它没了（没学会的世界斩再也学不会）。 */
    private void removeMahoraga(int why) {
        Fighter m = mahoraga;
        if (!m.present) return;
        m.present = false;
        m.captured = false;
        enter(m, Fighter.FREE);
        if (wheelOn == WHEEL_MAHORAGA) wheelOn = WHEEL_NONE;
        mahoragaAdapted = false;
        domains.specialBypass[Fighter.MAHORAGA] = false;
        events.emit(Events.MAHORAGA_GONE, m.x, m.centerY(), why, tick);
    }

    /** 宿傩开着领域展延（从开始起手到关闭后的术式锁定结束）：十种影法术暂停，魔虚罗不动、不出手（原作第 228 话）。 */
    public boolean tenShadowsPaused() {
        return ampTicks > 0 || ampLock > 0 || sukuna.state == Fighter.S_AMP_START;
    }

    /** 魔虚罗现在能不能行动（显现中、被控住、宿傩开着展延时不能）。 */
    public boolean mahoragaCanAct() {
        Fighter m = mahoraga;
        return m.present && m.state != Fighter.M_RISE && m.hitstun == 0 && !m.captured && domainTicks == 0 && !inBlueField(m)
                && !m.knockedAir && !m.scripted && !tenShadowsPaused();
    }

    /** 魔虚罗自己的 AI（各难度一样，没有随机）：每 12 tick 想一次；够得着就出拳，再远一点挥剑，否则往五条悟那边走。 */
    private void controlMahoraga() {
        Fighter m = mahoraga, g = gojo;
        if (!m.present || m.hpMilli == 0) return;
        if (m.state == Fighter.M_RISE) { // 刚显现：站着（可以挨打）
            m.stateTick++;
            if (m.hitstun == 0 && !m.knockedAir && !m.captured) drive(m, 0);
            if (m.stateTick >= Tuning.MAHORAGA_RISE) enter(m, Fighter.FREE);
            return;
        }
        if (!mahoragaCanAct()) {
            interruptMahoraga(); // 起手被控住：作废（冷却不退）
            boolean controlled = m.hitstun > 0 || m.captured || m.knockedAir || domainTicks > 0 || inBlueField(m);
            if (!controlled) drive(m, 0); // 展延暂停：站住
            mWasLocked = true;
            return;
        }
        if (mWasLocked) { // 恢复以后先缓一下再想
            mWasLocked = false;
            mReact = Tuning.MAHORAGA_REACT;
            mMove = 0;
        }
        m.stateTick++;
        int st = m.stateTick;
        if (m.state == Fighter.M_PUNCH || m.state == Fighter.M_SWORD) {
            drive(m, 0);
            boolean sword = m.state == Fighter.M_SWORD;
            int total = sword ? Tuning.M_SWORD_STARTUP + Tuning.M_SWORD_ACTIVE + Tuning.M_SWORD_RECOVERY
                    : Tuning.M_PUNCH_STARTUP + Tuning.M_PUNCH_ACTIVE + Tuning.M_PUNCH_RECOVERY;
            if (st >= total) {
                enter(m, Fighter.FREE);
                mGap = Tuning.M_ATTACK_GAP;
            }
            return;
        }
        if (g.state == Fighter.KO) {
            drive(m, 0);
            return;
        }
        float dx = g.x - m.x, d = Math.abs(dx);
        int toward = dx >= 0 ? 1 : -1;
        m.facing = toward;
        if (mReact > 0) {
            mReact--;
            drive(m, 0);
            return;
        }
        if (--mDecide <= 0) {
            mDecide = Tuning.MAHORAGA_DECIDE;
            float dy = Math.abs(g.centerY() - m.centerY());
            if (mGap == 0 && mPunchCd == 0 && d <= Tuning.M_PUNCH_REACH && dy <= Tuning.M_PUNCH_DY) {
                startMahoragaAttack(Fighter.M_PUNCH);
                return;
            }
            if (mGap == 0 && mSwordCd == 0 && d <= Tuning.M_SWORD_REACH && dy <= Tuning.M_SWORD_DY) {
                startMahoragaAttack(Fighter.M_SWORD);
                return;
            }
            mMove = d > Tuning.M_PUNCH_REACH - 10 ? toward : 0; // 两招都用不了：贴到拳头够得着的地方等
        }
        drive(m, mMove * Tuning.MAHORAGA_SPEED);
    }

    private void startMahoragaAttack(int state) {
        Fighter m = mahoraga;
        enter(m, state);
        drive(m, 0);
        mMove = 0;
        mSeq = ++attackSeq;
        if (state == Fighter.M_PUNCH) mPunchCd = Tuning.M_PUNCH_COOLDOWN;
        else mSwordCd = Tuning.M_SWORD_COOLDOWN;
    }

    /** 魔虚罗出招被打断（挨了能让它硬直的招、被控住、展延暂停）：作废，冷却不退。 */
    private void interruptMahoraga() {
        Fighter m = mahoraga;
        if (m.state == Fighter.M_PUNCH || m.state == Fighter.M_SWORD) {
            enter(m, Fighter.FREE);
            mGap = Tuning.M_ATTACK_GAP;
        }
    }

    /** 魔虚罗的重拳、退魔之剑：在有效帧里碰到五条悟就判一次；没适应时被无限挡下（挡下也让法阵转一格）。 */
    private void resolveMahoragaAttack() {
        Fighter m = mahoraga, g = gojo;
        if (!m.present || m.hpMilli == 0 || (m.state != Fighter.M_PUNCH && m.state != Fighter.M_SWORD) || mHitId == mSeq || g.state == Fighter.KO) return;
        boolean sword = m.state == Fighter.M_SWORD;
        int su = sword ? Tuning.M_SWORD_STARTUP : Tuning.M_PUNCH_STARTUP, act = sword ? Tuning.M_SWORD_ACTIVE : Tuning.M_PUNCH_ACTIVE;
        int st = m.stateTick;
        if (st <= su || st > su + act) return;
        float dx = (g.x - m.x) * m.facing, dy = Math.abs(g.centerY() - m.centerY());
        if (dx < -10 || dx > (sword ? Tuning.M_SWORD_REACH : Tuning.M_PUNCH_REACH) || dy > (sword ? Tuning.M_SWORD_DY : Tuning.M_PUNCH_DY)) return;
        mHitId = mSeq;
        if (sword) {
            attack.set(Fighter.MAHORAGA, Fighter.GOJO, Attack.M_SWORD, mSeq, Tuning.M_SWORD_DAMAGE_MILLI,
                    m.facing * Tuning.M_SWORD_IMPULSE_X, Tuning.M_SWORD_IMPULSE_Y, Tuning.M_SWORD_HITSTUN, Attack.SPECIAL_BYPASS);
        } else {
            attack.set(Fighter.MAHORAGA, Fighter.GOJO, Attack.M_PUNCH, mSeq, Tuning.M_PUNCH_DAMAGE_MILLI,
                    m.facing * Tuning.M_PUNCH_IMPULSE_X, 0, Tuning.M_PUNCH_HITSTUN, Attack.SPECIAL_BYPASS);
        }
        if ((apply(attack) & Defense.SHOW_INFINITY) != 0) turnWheel();
    }

    private void startWorldSlash() {
        Fighter s = sukuna;
        enter(s, Fighter.S_WORLD);
        boolean first = !worldBound;
        worldBound = true; // 束缚的“第一次”起手就用掉，被打断也不退
        worldWindup = first ? Tuning.S_WORLD_FIRST_WINDUP : Tuning.S_WORLD_WINDUP;
        worldCd = Tuning.S_WORLD_COOLDOWN;
        worldLocked = worldPending = false;
        s.facing = gojo.x >= s.x ? 1 : -1;
        if (first) lockWorldSlash(); // 第一次：起手就锁定
        events.emit(Events.CAST, s.x, s.centerY(), first ? 3 : 1, Attack.WORLD_SLASH, tick);
        events.emit(Events.WORLD_CHANT, s.x, s.centerY(), 0, Attack.WORLD_SLASH, tick);
    }

    private void lockWorldSlash() {
        Fighter s = sukuna;
        worldLocked = true;
        worldDir = s.facing;
        worldY = s.y + Tuning.S_WORLD_HEIGHT;
        if (worldWindup == Tuning.S_WORLD_WINDUP) events.emit(Events.WORLD_CHANT, s.x, worldY, 4, Attack.WORLD_SLASH, tick);
    }

    /** 世界斩：前方一道水平的斩带，斩的是空间（能破无限）；五条悟跳过斩带高度就躲开。 */
    private void resolveWorldSlash() {
        if (!worldPending) return;
        worldPending = false;
        Fighter s = sukuna, g = gojo;
        if (s.state != Fighter.S_WORLD) return; // 这一 tick 里被打断了
        enter(s, Fighter.S_WORLD_RECOVER);
        float sx = s.x + worldDir * 20;
        float dx = (g.x - sx) * worldDir;
        boolean hit = g.state != Fighter.KO && dx >= -Tuning.BODY_HALF_W && dx <= Tuning.S_WORLD_RANGE + Tuning.BODY_HALF_W
                && g.y <= worldY + Tuning.S_WORLD_HALF_THICKNESS && g.y + Tuning.BODY_H >= worldY - Tuning.S_WORLD_HALF_THICKNESS;
        float endX = clamp(sx + worldDir * Tuning.S_WORLD_RANGE, 0, Tuning.WIDTH);
        events.emit(Events.SLASH, sx, worldY, endX, Attack.WORLD_SLASH, tick);
        if (hit) {
            attack.set(Fighter.SUKUNA, Fighter.GOJO, Attack.WORLD_SLASH, ++attackSeq, Tuning.S_WORLD_DAMAGE_MILLI,
                    worldDir * Tuning.S_WORLD_IMPULSE_X, Tuning.S_WORLD_IMPULSE_Y, Tuning.S_WORLD_HITSTUN, Attack.SPECIAL_BYPASS);
            apply(attack);
        }
    }

    private int sMeleeSeq = -1;

    private void resolveSukunaMelee() {
        Fighter s = sukuna, g = gojo;
        if (s.state != Fighter.S_MELEE || g.state == Fighter.KO || sMeleeHitId == sMeleeSeq) return;
        int st = s.stateTick;
        if (st <= Tuning.S_MELEE_STARTUP || st > Tuning.S_MELEE_STARTUP + Tuning.S_MELEE_ACTIVE) return;
        float dx = (g.x - s.x) * s.facing, dy = Math.abs(g.y - s.y);
        if (dx >= -10 && dx <= Tuning.S_MELEE_REACH && dy <= 80) {
            sMeleeHitId = sMeleeSeq;
            // 声明领域展延；只有展延真的开着（Domains.amplification）才中和得了无限
            attack.set(Fighter.SUKUNA, Fighter.GOJO, Attack.S_MELEE, sMeleeSeq, Tuning.S_MELEE_DAMAGE_MILLI,
                    s.facing * Tuning.S_MELEE_IMPULSE, 0, Tuning.S_MELEE_HITSTUN, Attack.DOMAIN_AMPLIFICATION);
            apply(attack);
        }
    }

    /** 解：几乎没有前摇的斩击，锁定出手时的方向，在射程内的线段上瞬间判定。 */
    private void fireDismantle() {
        Fighter s = sukuna, g = gojo;
        float sx = s.x + s.facing * 20, sy = s.centerY() + 6;
        float dx = (g.x - s.x) * s.facing, dy = Math.abs(g.centerY() - s.centerY());
        boolean hit = g.state != Fighter.KO && dx >= 0 && dx <= Tuning.DISMANTLE_RANGE && dy <= 70;
        float endX = hit ? g.x : clamp(sx + s.facing * Tuning.DISMANTLE_RANGE, 0, Tuning.WIDTH);
        events.emit(Events.SLASH, sx, sy, endX, Attack.DISMANTLE, tick);
        if (hit) {
            attack.set(Fighter.SUKUNA, Fighter.GOJO, Attack.DISMANTLE, ++attackSeq, Tuning.DISMANTLE_DAMAGE_MILLI, 0, 0, Tuning.DISMANTLE_HITSTUN, 0);
            apply(attack);
        }
    }

    /* ---------------- 命中裁决 ---------------- */

    private void resolveMelee() {
        Fighter g = gojo;
        if (meleeHitId == gMeleeSeq) return;
        boolean fist = g.state == Fighter.FIST_ACTIVE;
        if (g.state != Fighter.MELEE_ACTIVE && !fist) return;
        boolean smash = fist && fistSmash;
        Fighter s = meleeTarget(sukuna, null, fist, smash); // 宿傩和魔虚罗都够得着：打近的那个（一样近打宿傩）
        s = meleeTarget(mahoraga, s, fist, smash);
        if (s == null) return;
        meleeHitId = gMeleeSeq;
        boolean bf = blackFlash.roll(rng); // 只在真正命中时判定（苍拳也算）
        int kind;
        if (bf) {
            kind = Attack.BLACK_FLASH;
            attack.set(Fighter.GOJO, s.id, kind, gMeleeSeq,
                    smash ? Tuning.SMASH_BLACK_FLASH_DAMAGE_MILLI : fist ? Tuning.FIST_BLACK_FLASH_DAMAGE_MILLI : Tuning.BLACK_FLASH_DAMAGE_MILLI,
                    g.facing * Tuning.BLACK_FLASH_IMPULSE_X, Tuning.BLACK_FLASH_IMPULSE_Y, Tuning.BLACK_FLASH_HITSTUN, 0);
        } else if (fist) {
            kind = Attack.BLUE_FIST;
            if (smash) {
                attack.set(Fighter.GOJO, s.id, kind, gMeleeSeq, Tuning.SMASH_DAMAGE_MILLI,
                        g.facing * Tuning.SMASH_IMPULSE_X, Tuning.SMASH_IMPULSE_Y, Tuning.SMASH_HITSTUN, 0);
            } else {
                attack.set(Fighter.GOJO, s.id, kind, gMeleeSeq, Tuning.FIST_DAMAGE_MILLI,
                        g.facing * Tuning.FIST_IMPULSE_X, Tuning.FIST_IMPULSE_Y, Tuning.FIST_HITSTUN, 0);
            }
        } else {
            kind = Attack.MELEE;
            attack.set(Fighter.GOJO, s.id, kind, gMeleeSeq, Tuning.MELEE_DAMAGE_MILLI,
                    g.facing * Tuning.MELEE_IMPULSE, 0, Tuning.MELEE_HITSTUN, 0);
        }
        int mask = apply(attack);
        if ((mask & Defense.ALLOW_DAMAGE) == 0) return;
        hitstop = bf ? Tuning.BLACK_FLASH_HITSTOP : fist ? Tuning.FIST_HITSTOP : Tuning.MELEE_HITSTOP;
        if (bf) events.emit(Events.BLACK_FLASH, (g.x + s.x) * 0.5f, s.centerY(), g.facing, fist ? Attack.BLUE_FIST : Attack.BLACK_FLASH, tick);
    }

    /** 五条悟这一下够得着 t 吗（在前方、距离和高度都合适；大个子离得远一点也打得到）；够得着而且比 best 近就换成它。 */
    private Fighter meleeTarget(Fighter t, Fighter best, boolean fist, boolean smash) {
        Fighter g = gojo;
        if (!t.present || t.state == Fighter.KO) return best;
        float extra = t.halfW - Tuning.BODY_HALF_W, tall = t.height - Tuning.BODY_H;
        float dx = (t.x - g.x) * g.facing, rel = g.y - t.y;
        if (smash && fistAir) { // 俯冲：在五条悟前下方
            if (dx < -30 - extra || dx > Tuning.SMASH_REACH + extra || rel < -40 || rel > 160 + tall) return best;
        } else if (dx < -10 - extra || dx > (smash ? Tuning.SMASH_REACH : fist ? Tuning.FIST_REACH : Tuning.MELEE_REACH) + extra
                || rel < -80 || rel > 80 + tall) {
            return best;
        }
        return best == null || Math.abs(t.x - g.x) < Math.abs(best.x - g.x) ? t : best;
    }

    /** 一次攻击接触：交给防御裁决，再按允许的部分生效。返回裁决位掩码。 */
    public int apply(Attack a) {
        Fighter t = fighter(a.target);
        if (t.invuln || !t.present) return 0; // 闪避、瞬移的固定动作：碰不到
        int mask = Defense.resolve(this, a);
        // 念咒、苍拳和瞬移的前摇：被攻击就打断（被无限挡住也算：攻击碰到了）
        if (t == gojo && a.source != Fighter.GOJO) breakChant();
        if ((mask & Defense.SHOW_INFINITY) != 0) {
            events.emit(Events.INFINITY_BLOCK, t.x, t.centerY(), a.kind, a.kind, tick);
            if (a.kind == Attack.DISMANTLE && sukuna.state == Fighter.S_DISMANTLE) adaptPending = true; // 收完招再结算
            return mask;
        }
        // 魔虚罗的霸体：五条悟的普通体术（不是黑闪）照常扣血，但不硬直、不击退、不打断
        if (t == mahoraga && a.kind == Attack.MELEE) mask &= ~(Defense.ALLOW_IMPULSE | Defense.ALLOW_STUN);
        // 难度倍率只乘宿傩自己的招（式神的伤害固定）
        long dmg = a.source == Fighter.SUKUNA && a.kind != Attack.AGITO ? Math.round(a.damageMilli * (double) config.sukunaDamageMult) : a.damageMilli;
        if ((mask & Defense.ALLOW_DAMAGE) != 0 && dmg > 0) {
            damageTaken[t.id] += Math.min(dmg, t.hpMilli);
            t.hpMilli = Math.max(0, t.hpMilli - dmg);
            if (a.kind != Attack.BLUE_FIELD) events.emit(Events.DAMAGE, t.x, t.y + t.height + 10, dmg / 1000f, a.kind, tick);
        }
        if ((mask & Defense.ALLOW_IMPULSE) != 0 && (a.impulseX != 0 || a.impulseY != 0)) {
            t.vx = clamp(t.vx + a.impulseX, -Tuning.MAX_VX, Tuning.MAX_VX);
            t.vy = Math.min(t.vy + a.impulseY, Tuning.MAX_VY);
            if (a.impulseY > 0) {
                t.onGround = false;
                t.knockedAir = true; // 被打飞了：落地之前不能主动行动（不去改飞行的速度）
            }
        }
        if ((mask & Defense.ALLOW_STUN) != 0 && a.hitstun > 0) {
            // 连段衰减：同一串连击里，轻拳（双方的体术）打到第 COMBO_STUN_HITS 下以后只扣血，不再造成硬直、也不打断动作
            // （有意的设计，双方一样：被贴身连打时第 4 下起能顶着出招，打不成无限连）。只数轻拳，重招不占名额
            boolean light = a.kind == Attack.MELEE || a.kind == Attack.S_MELEE;
            if (light) {
                t.comboHits = t.comboTimer > 0 ? t.comboHits + 1 : 1;
                t.comboTimer = Tuning.COMBO_RESET;
            }
            if (!light || t.comboHits <= Tuning.COMBO_STUN_HITS) {
                t.hitstun = Math.max(t.hitstun, a.hitstun);
                if (t == sukuna) interruptSukuna(); // 宿傩出招、咏唱被打断（反转术式、变原身不会）
                else if (t == gojo) interruptGojo();
                else interruptMahoraga();
            } else if (t == sukuna && (sukuna.state == Fighter.S_WORLD || sukuna.state == Fighter.S_SUMMON)) {
                interruptSukuna(); // 世界斩、召唤魔虚罗的咏唱挨打就断（不管有没有硬直）
            }
        }
        if (a.kind != Attack.BLUE_FIELD) {
            t.lastHitTick = tick;
            t.lastHitKind = a.kind;
            events.emit(Events.HIT, t.x, t.centerY(), a.kind, a.kind, tick);
        }
        return mask;
    }

    /* ---------------- 赫：飞行、遇到苍变成无指向茈、命中 ---------------- */

    private void updateRed() {
        Shot r = red;
        if (!r.active) return;
        r.tick++;
        r.x += r.dir * Tuning.RED_SPEED * Tuning.DT;
        // 赫与苍重叠，或互相在对方的作用范围内（距离不超过两者作用范围中较小的那个）：直接变成无指向茈。
        // 按这一 tick 两者的相对运动线段求交（擦边飞过也算），在第一次进入范围的位置合成
        if (blue.active()) {
            float range = Math.min(r.max ? Tuning.RED_MAX_RANGE : Tuning.RED_RANGE, blue.radius);
            float x0 = r.px - blue.px, y0 = r.py - blue.py;
            float dx = (r.x - blue.x) - x0, dy = (r.y - blue.y) - y0;
            float t = firstContact(x0, y0, dx, dy, range);
            if (t >= 0) {
                float rx = r.px + (r.x - r.px) * t, ry = r.py + (r.y - r.py) * t;
                float bx = blue.px + (blue.x - blue.px) * t, by = blue.py + (blue.y - blue.py) * t;
                formHollow((rx + bx) * 0.5f, (ry + by) * 0.5f);
                return;
            }
        }
        // 打到弹道上第一个（这一 tick 两个都碰到：沿飞行方向先碰到的那个；一样就是宿傩）
        float orb = r.max ? Tuning.RED_MAX_ORB_R : Tuning.RED_ORB_R;
        Fighter s = null;
        if (!r.hit) {
            s = shotTarget(r, sukuna, null, orb);
            s = shotTarget(r, mahoraga, s, orb);
        }
        if (s != null) {
            r.hit = true;
            if (r.max) {
                attack.set(Fighter.GOJO, s.id, Attack.RED, r.instanceId, Tuning.RED_MAX_DAMAGE_MILLI,
                        r.dir * Tuning.RED_MAX_IMPULSE_X, Tuning.RED_MAX_IMPULSE_Y, Tuning.RED_MAX_HITSTUN, 0);
            } else {
                attack.set(Fighter.GOJO, s.id, Attack.RED, r.instanceId, Tuning.RED_DAMAGE_MILLI,
                        r.dir * Tuning.RED_IMPULSE_X, Tuning.RED_IMPULSE_Y, Tuning.RED_HITSTUN, 0);
            }
            if ((apply(attack) & Defense.ALLOW_IMPULSE) != 0) {
                s.captured = false;                    // 赫解除苍的捕获
                s.pullResist = Tuning.RED_PULL_RESIST; // 免得刚击飞又被吸回去
                if (r.max) hitstop = Tuning.RED_MAX_HITSTOP;
            }
            burstRed();
            return;
        }
        boolean end = r.max ? (r.x < 0 || r.x > Tuning.WIDTH) : (r.tick >= Tuning.RED_TRAVEL || r.x < 0 || r.x > Tuning.WIDTH);
        if (end) burstRed();
    }

    /** 飞行的球碰到 t 了吗；碰到了而且沿飞行方向比 best 先碰到就换成它（一样早就是编号小的，宿傩在前）。 */
    private Fighter shotTarget(Shot r, Fighter t, Fighter best, float radius) {
        if (!t.present || t.state == Fighter.KO || !touches(r.x, r.y, radius, t)) return best;
        if (best == null) return t;
        return entry(r, t, radius) < entry(r, best, radius) ? t : best;
    }

    /** 水平飞行的圆（半径 radius）第一次碰到 t 的身体时，球心沿飞行方向走过的距离（从上一 tick 的位置算）。 */
    private static float entry(Shot r, Fighter t, float radius) {
        float dy = r.y - clamp(r.y, t.y, t.y + t.height); // 球心高出（低于）身体多少
        float reach = (float) Math.sqrt(Math.max(0, radius * radius - dy * dy));
        return (t.x - r.dir * (t.halfW + reach) - r.px) * r.dir;
    }

    private void burstRed() {
        red.active = false;
        events.emit(Events.RED_BURST, red.x, red.y, red.dir, red.max ? 1 : 0, tick);
    }

    /* ---------------- 茈：一直飞到战场外，打中一次 ---------------- */

    private void updatePurple() {
        Shot p = purple;
        if (!p.active) return;
        p.tick++;
        float r = p.max ? Tuning.PURPLE_200_R : Tuning.PURPLE_R;
        p.x += p.dir * (p.max ? Tuning.PURPLE_200_SPEED : Tuning.PURPLE_SPEED) * Tuning.DT;
        purpleHit(p, sukuna, r); // 穿透：宿傩、魔虚罗各打一次
        purpleHit(p, mahoraga, r);
        if (p.x < -r * 2 || p.x > Tuning.WIDTH + r * 2) p.active = false;
    }

    private void purpleHit(Shot p, Fighter s, float r) {
        int bit = 1 << s.id;
        if ((p.hitMask & bit) != 0 || !s.present || s.state == Fighter.KO || !touches(p.x, p.y, r, s)) return;
        p.hitMask |= bit;
        if (p.max) {
            attack.set(Fighter.GOJO, s.id, Attack.PURPLE, p.instanceId, Tuning.PURPLE_200_DAMAGE_MILLI,
                    p.dir * Tuning.PURPLE_200_IMPULSE_X, Tuning.PURPLE_200_IMPULSE_Y, Tuning.PURPLE_200_HITSTUN, 0);
        } else {
            attack.set(Fighter.GOJO, s.id, Attack.PURPLE, p.instanceId, Tuning.PURPLE_DAMAGE_MILLI,
                    p.dir * Tuning.PURPLE_IMPULSE_X, Tuning.PURPLE_IMPULSE_Y, Tuning.PURPLE_HITSTUN, 0);
        }
        if ((apply(attack) & Defense.ALLOW_IMPULSE) != 0) {
            s.captured = false;
            s.pullResist = Tuning.RED_PULL_RESIST;
            if (p.max) hitstop = Tuning.PURPLE_200_HITSTOP;
        }
    }

    /* ---------------- 无指向茈：向四周扩张，扩到谁就打谁（五条悟一半） ---------------- */

    private void formHollow(float x, float y) {
        Hollow h = hollow;
        h.active = true;
        h.tick = 0;
        h.instanceId = ++attackSeq;
        h.x = x;
        h.y = y;
        h.radius = 0;
        h.hitMask = 0;
        red.active = false;
        blue.phase = BlueOrb.OFF;      // 苍和赫都被吃掉了
        sukuna.captured = false;
        mahoraga.captured = false;
        events.emit(Events.HOLLOW_FORM, x, y, Tuning.HOLLOW_RADIUS, Attack.HOLLOW, tick);
    }

    private void updateHollow() {
        Hollow h = hollow;
        if (!h.active) return;
        h.tick++;
        float k = Math.min(1f, h.tick / (float) Tuning.HOLLOW_GROW);
        h.radius = Tuning.HOLLOW_RADIUS * (1 - (1 - k) * (1 - k)); // 先快后慢
        Fighter g = gojo;
        hollowHit(h, sukuna);
        hollowHit(h, mahoraga);
        int gb = 1 << Fighter.GOJO;
        if ((h.hitMask & gb) == 0 && g.state != Fighter.KO && touches(h.x, h.y, h.radius, g)) {
            h.hitMask |= gb;
            float dir = g.x >= h.x ? 1 : -1;
            attack.set(Fighter.GOJO, Fighter.GOJO, Attack.HOLLOW, h.instanceId, Tuning.HOLLOW_SELF_DAMAGE_MILLI,
                    dir * Tuning.HOLLOW_SELF_IMPULSE_X, Tuning.HOLLOW_SELF_IMPULSE_Y, Tuning.HOLLOW_SELF_HITSTUN, 0);
            apply(attack);
        }
        if (h.tick >= Tuning.HOLLOW_LIFE) {
            h.active = false;
            events.emit(Events.HOLLOW_END, h.x, h.y, 0, Attack.HOLLOW, tick);
        }
    }

    private void hollowHit(Hollow h, Fighter s) {
        int bit = 1 << s.id;
        if ((h.hitMask & bit) != 0 || !s.present || s.state == Fighter.KO || !touches(h.x, h.y, h.radius, s)) return;
        h.hitMask |= bit;
        float dir = s.x >= h.x ? 1 : -1;
        attack.set(Fighter.GOJO, s.id, Attack.HOLLOW, h.instanceId, Tuning.HOLLOW_DAMAGE_MILLI,
                dir * Tuning.HOLLOW_IMPULSE_X, Tuning.HOLLOW_IMPULSE_Y, Tuning.HOLLOW_HITSTUN, 0);
        if ((apply(attack) & Defense.ALLOW_IMPULSE) != 0) s.pullResist = Tuning.RED_PULL_RESIST;
    }

    /** 从 (x0, y0) 沿 (dx, dy) 走一段（t 从 0 到 1），第一次进入以原点为圆心、半径 r 的圆时的 t；进不去返回 -1。 */
    static float firstContact(float x0, float y0, float dx, float dy, float r) {
        // double 算，判别式和 [0,1] 边界都留一点和数值大小相称的余量：正好相切、正好在这一 tick 末尾碰到也算
        double c = (double) x0 * x0 + (double) y0 * y0 - (double) r * r;
        if (c <= 0) return 0;
        double a = (double) dx * dx + (double) dy * dy;
        if (a < 1e-12) return -1;
        double b = 2 * ((double) x0 * dx + (double) y0 * dy);
        double disc = b * b - 4 * a * c;
        double eps = 1e-6 * (b * b + Math.abs(4 * a * c));
        if (disc < -eps) return -1;
        double t = (-b - Math.sqrt(Math.max(0, disc))) / (2 * a);
        if (t < -1e-6 || t > 1 + 1e-6) return -1;
        return (float) Math.max(0, Math.min(1, t));
    }

    /** 圆（cx, cy, r）是否碰到人的身体（一个竖着的矩形）。 */
    private static boolean touches(float cx, float cy, float r, Fighter f) {
        float nx = clamp(cx, f.x - f.halfW, f.x + f.halfW);
        float ny = clamp(cy, f.y, f.y + f.height);
        float dx = cx - nx, dy = cy - ny;
        return dx * dx + dy * dy <= r * r;
    }

    /* ---------------- 苍：飞行、吸引、持续伤害 ---------------- */

    /** 苍这一 tick 的位置先推进（飞行中往落点走），赫的相遇判定要用到。 */
    private void advanceBlue() {
        BlueOrb b = blue;
        if (!b.active()) return;
        b.tick++;
        if (b.phase == BlueOrb.FLY) {
            float k = Math.min(1f, b.tick / (float) Tuning.BLUE_FLY);
            float e = 1 - (1 - k) * (1 - k); // 先快后慢
            b.x = b.ox + (b.cx - b.ox) * e;
            b.y = b.oy + (b.cy - b.oy) * e;
            if (b.tick >= Tuning.BLUE_FLY) {
                b.phase = BlueOrb.PULL;
                b.tick = 0;
                events.emit(Events.BLUE_LAND, b.cx, b.cy, b.charged ? 1 : 0, tick);
            }
        }
    }

    private void applyBlue() {
        BlueOrb b = blue;
        if (b.phase != BlueOrb.PULL || b.tick == 0) return; // 刚落地这一 tick 先不吸
        if (b.tick > Tuning.BLUE_LIFE) { // 到期放在赫的判定之后：到期那一 tick 赫还能和它合成
            endBlue();
            return;
        }
        blueOn(b, sukuna, 1f);
        blueOn(b, mahoraga, Tuning.MAHORAGA_PULL); // 魔虚罗：吸力减半，伤害不减
    }

    /** 苍对一个目标：吸引（pullMult 乘在最后的加速度上）和持续伤害，各算各的。 */
    private void blueOn(BlueOrb b, Fighter s, float pullMult) {
        if (!s.present || s.state == Fighter.KO) return;
        float dx = b.cx - s.x, dy = b.cy - s.centerY();
        float r = s.captured ? 0f : (float) Math.sqrt(dx * dx + dy * dy);
        if (r >= b.radius) return;
        // 吸力：边缘也有一点，越近越强；远处全速冲向中心，近处按距离减速，不会冲过头来回抖
        if (!s.captured && s.pullResist == 0 && r > 0) {
            float q = 1f - r / b.radius;
            float amax = b.accel * (0.25f + 0.75f * q * q);
            // 出力最大：整个吸引都 ×2（加速度上限、跟随强度、目标速度），同样的位置吸得快一倍
            float want = Math.min(Tuning.BLUE_MAX_SPEED * b.pull, Tuning.BLUE_ARRIVE * b.pull * r) / r;
            float follow = Tuning.BLUE_FOLLOW * b.pull;
            float axx = follow * (dx * want - s.vx), ayy = follow * (dy * want - s.vy);
            float m = (float) Math.sqrt(axx * axx + ayy * ayy);
            if (m > amax) {
                axx *= amax / m;
                ayy *= amax / m;
            }
            s.ax += axx * pullMult;
            s.ay += ayy * pullMult;
            s.pulled = true;
            s.gravityScale = Math.min(s.gravityScale, 1f - q); // 越靠近中心越失重
        }
        // 持续伤害：离中心越近越高，核心最高每秒 100；按余数累计，不因截断丢血
        float qd = clamp((b.radius - r) / (b.radius - Tuning.BLUE_CORE), 0f, 1f);
        long dpsMilli = Math.round(Tuning.BLUE_MAX_DPS_MILLI * (double) (qd * qd));
        int id = s.id;
        b.remainder[id] += dpsMilli;
        long dmg = b.remainder[id] / Tuning.HZ;
        b.remainder[id] %= Tuning.HZ;
        if (dmg > 0) {
            attack.set(Fighter.GOJO, id, Attack.BLUE_FIELD, b.instanceId, dmg, 0, 0, 0, 0);
            if ((apply(attack) & Defense.ALLOW_DAMAGE) != 0) b.shownMilli[id] += dmg;
        }
        if (b.tick % 30 == 0 && b.shownMilli[id] > 0) {
            events.emit(Events.DAMAGE, s.x, s.y + s.height + 10, b.shownMilli[id] / 1000f, Attack.BLUE_FIELD, tick);
            b.shownMilli[id] = 0;
        }
    }

    private void endBlue() {
        blue.phase = BlueOrb.OFF;
        sukuna.captured = false;
        mahoraga.captured = false;
        events.emit(Events.BLUE_END, blue.cx, blue.cy, 0, tick);
    }

    /* ---------------- 积分、地面、墙 ---------------- */

    private void integrate(Fighter f) {
        float dt = Tuning.DT;
        if (f.captured && blue.phase != BlueOrb.PULL) f.captured = false; // 苍没了，捕获也没了
        if (f.scripted) {
            f.onGround = f.y <= 0;
        } else if (f.captured) {
            f.x = blue.cx;
            f.y = blue.cy - f.height * 0.5f;
            f.vx = f.vy = 0;
            f.onGround = false;
        } else {
            float ox = f.x, oy = f.centerY();
            float ay = f.ay - Tuning.GRAVITY * f.gravityScale;
            f.vx += f.ax * dt;
            f.vy += ay * dt;
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            // 防穿过：这一 tick 的移动线段经过苍的核心，就被抓住
            if (f.id != Fighter.GOJO && blue.phase == BlueOrb.PULL && f.pullResist == 0
                    && segmentNear(ox, oy, f.x, f.centerY(), blue.cx, blue.cy, Tuning.BLUE_CORE)) {
                f.captured = true;
                f.x = blue.cx;
                f.y = blue.cy - f.height * 0.5f;
                f.vx = f.vy = 0;
                f.knockedAir = false;
            }
            if (f.y <= 0) {
                if (!f.onGround && f.vy < -400) events.emit(Events.LAND, f.x, 0, -f.vy, tick); // 重重落地：扬尘
                f.knockedAir = false;
                f.y = 0;
                if (f.vy < 0) f.vy = 0;
                f.onGround = true;
            } else {
                f.onGround = false;
            }
            // 地上滑行减速：不是自己在走（被打飞、倒地、站着）就减速；被苍吸着时不减
            if (f.onGround && !f.selfMove && !f.pulled) {
                float dec = Tuning.GROUND_FRICTION * dt;
                f.vx = Math.abs(f.vx) <= dec ? 0 : f.vx - Math.signum(f.vx) * dec;
            }
        }
        // 墙：夹在战场内，清掉朝墙外的速度
        float lo = f.halfW, hi = Tuning.WIDTH - f.halfW;
        if (f.x < lo) {
            f.x = lo;
            if (f.vx < 0) f.vx = 0;
        } else if (f.x > hi) {
            f.x = hi;
            if (f.vx > 0) f.vx = 0;
        }
        if (f.y > Tuning.CEILING) {
            f.y = Tuning.CEILING;
            if (f.vy > 0) f.vy = 0;
        }
        f.ax = f.ay = 0;
        f.pulled = false;
        f.selfMove = false;
        f.gravityScale = 1f;
    }

    private static boolean segmentNear(float x0, float y0, float x1, float y1, float cx, float cy, float rad) {
        float vx = x1 - x0, vy = y1 - y0;
        float wx = cx - x0, wy = cy - y0;
        float len2 = vx * vx + vy * vy;
        float t = len2 > 0 ? clamp((wx * vx + wy * vy) / len2, 0f, 1f) : 0f;
        float px = x0 + vx * t - cx, py = y0 + vy * t - cy;
        return px * px + py * py <= rad * rad;
    }

    /* ---------------- 计时器、领域、倒地与复活 ---------------- */

    private void timers() {
        if (meleeCd > 0) meleeCd--;
        if (blueCd > 0) blueCd--;
        if (redCd > 0) redCd--;
        if (purpleCd > 0) purpleCd--;
        if (blueFailLock > 0) blueFailLock--;
        if (purpleFailLock > 0) purpleFailLock--;
        if (redFailLock > 0) redFailLock--;
        if (dodgeCd > 0) dodgeCd--;
        if (dodgeRecent > 0) dodgeRecent--;
        if (tpCd > 0) tpCd--;
        if (sMeleeCd > 0) sMeleeCd--;
        if (dismantleCd > 0) dismantleCd--;
        if (sAttackGap > 0) sAttackGap--;
        fightTicks++;
        if (ampTicks > 0 && --ampTicks == 0) closeAmp();
        else if (ampTicks == 0 && ampCd > 0) ampCd--;
        if (ampLock > 0) ampLock--;
        if (worldCd > 0) worldCd--;
        if (rctCd > 0) rctCd--;
        if (sStagger > 0) sStagger--;
        if (summonCd > 0) summonCd--;
        if (agitoCd > 0) agitoCd--;
        if (mPunchCd > 0) mPunchCd--;
        if (mSwordCd > 0) mSwordCd--;
        if (mGap > 0) mGap--;
        // 魔虚罗被打空：先退场（不会复活，也不能再召唤），再算法阵（法阵跟着它没了）
        if (mahoraga.present && mahoraga.hpMilli == 0) removeMahoraga(0);
        boolean sukunaUp = sukuna.state != Fighter.KO && sukuna.hpMilli > 0;
        // 法阵自己慢慢转（有法阵、十种影没被展延暂停、宿傩没倒下时）
        if (wheelOn != WHEEL_NONE && adaptPoints < Tuning.S_ADAPT_POINTS && !tenShadowsPaused() && sukunaUp
                && ++adaptTimer >= Tuning.S_ADAPT_PASSIVE) turnWheel();
        // 学会世界斩：转满而且打了够久；这时候法阵还得在（魔虚罗退场、变了原身就没了），宿傩也得站着
        if (!worldUnlocked && config.shrine && wheelOn != WHEEL_NONE && sukunaUp && adaptPoints >= Tuning.S_ADAPT_POINTS
                && fightTicks >= Tuning.S_ADAPT_UNLOCK_MIN_TICKS) {
            worldUnlocked = true; // 适应完成：学会了世界斩
            domains.specialBypass[Fighter.SUKUNA] = true;
            events.emit(Events.ADAPT, sukuna.x, sukuna.centerY() + 70, adaptPoints, 1, tick);
        }
        if (domainTicks > 0 && --domainTicks == 0) {
            domains.sureHit[Fighter.GOJO][Fighter.SUKUNA] = false;
            domains.sureHit[Fighter.GOJO][Fighter.MAHORAGA] = false;
            domainCd = Tuning.DOMAIN_COOLDOWN;
            events.emit(Events.DOMAIN_CLOSE, gojo.x, gojo.centerY(), 0, tick);
        } else if (domainTicks == 0 && domainCd > 0) {
            domainCd--;
        }
        // 闪避中按的键留到闪避结束再兑现（闪避 0.15 秒比按键缓冲长；瞬移冷却中按“闪避 + 苍 / 赫”就是闪避完放普通的苍 / 赫）
        if (gojo.state != Fighter.DODGE) {
            if (bufMelee > 0) bufMelee--;
            if (bufRed > 0) bufRed--;
            if (bufJump > 0) bufJump--;
            if (bufPurple > 0) bufPurple--;
            if (bufDomain > 0) bufDomain--;
            if (bufDodge > 0) bufDodge--;
            for (int i = 0; i < bqSize; i++) bqTtl[(bqHead + i) % BQ]--;
            while (bqSize > 0 && bqTtl[bqHead] <= 0) bluePop(); // 越早的越先到期，到期的总在队头
        }
        for (int i = 0; i < Fighter.COUNT; i++) {
            Fighter f = fighter(i);
            if (!f.present) continue;
            if (f.hitstun > 0) f.hitstun--;
            if (f.comboTimer > 0 && --f.comboTimer == 0) f.comboHits = 0;
            if (f.downTicks > 0 && f.onGround && !f.knockedAir && !f.scripted) f.downTicks--; // 被击倒：落地后才开始算
            if (f.pullResist > 0) f.pullResist--;
        }
        // 打空血量就倒地，过一会儿满血站起来（训练：宿傩是靶子；五条悟被自己的无指向茈打空也一样）
        for (int i = 0; i < 2; i++) {
            Fighter f = fighter(i);
            if (f.state != Fighter.KO && f.hpMilli == 0) {
                knockOut(f);
            } else if (f.state == Fighter.KO && --f.koTimer <= 0) {
                f.state = Fighter.FREE;
                f.stateTick = 0;
                f.hpMilli = f.maxHpMilli;
                events.emit(Events.REVIVE, f.x, f.centerY(), 0, f.id, tick);
            }
        }
        // 宿傩自由的时候面向五条悟（出招时锁定方向）
        Fighter s = sukuna;
        // 瞬移·茈：拍肩前背对着、拍肩后才回头（朝向由脚本定）
        if (s.state == Fighter.FREE && s.hitstun == 0 && !(s.scripted && gojo.state == Fighter.TP_PURPLE)) s.facing = gojo.x >= s.x ? 1 : -1;
    }

    private void knockOut(Fighter f) {
        if (f == gojo) breakChant(); // 念咒（或按住茈的起手）中倒下：打断
        endScript();
        f.downTicks = 0;
        if (f != gojo) {
            interruptSukuna();
            if (ampTicks > 0) closeAmp();
            agito.active = false;
            removeMahoraga(1); // 宿傩倒下：式神跟着消失（一局只能召一次，复活也不会回来）
        }
        f.state = Fighter.KO;
        f.stateTick = 0;
        f.captured = false;
        f.koTimer = Tuning.KO_TICKS;
        events.emit(Events.KO, f.x, f.centerY(), 0, f.id, tick);
    }

    /* ---------------- 给画面读的状态 ---------------- */

    public boolean pendingCharged() {
        return pendingCharged;
    }

    public boolean redMax() {
        return redMax;
    }

    public boolean purple200() {
        return purple200;
    }

    /** 这次苍拳是下砸（按住跳跃 + 体术）还是长按体术的前冲一拳。 */
    public boolean fistSmash() {
        return fistSmash;
    }

    /** 下砸苍拳是在空中（往下俯冲）还是地上（往下劈）。 */
    public boolean fistAir() {
        return fistAir;
    }

    /** 瞬移的是哪个：Attack.BLUE_FIST（苍）或 Attack.RED（赫）。 */
    public int tpKind() {
        return tpKind;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
