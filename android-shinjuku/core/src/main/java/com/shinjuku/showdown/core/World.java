package com.shinjuku.showdown.core;

/**
 * 战斗世界：每秒 120 tick 固定步长推进。完全确定：同一个配置 + 同一串输入 = 同一个结果。
 * 一个 tick 的顺序：五条悟的操作与动作 → 宿傩（AI 意图 → 动作执行）→ 体术命中 → 苍的位置 → 赫（遇到苍变无指向茈；命中）
 * → 茈 → 无指向茈的扩张 → 苍的吸力与持续伤害 → 积分（重力、外力）→ 地面与墙 → 计时器、领域、倒地与复活。
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
    final SukunaIntent sukunaIntent = new SukunaIntent(); // 没有 AI 时测试可以手动填

    public int tick;
    public int hitstop;               // >0 时整个世界停住（打中的顿挫）
    public int meleeCd, blueCd, redCd, purpleCd, domainCd, blueFailLock, purpleFailLock;
    public int blueCdMax = Tuning.BLUE_COOLDOWN, redCdMax = Tuning.RED_COOLDOWN, purpleCdMax = Tuning.PURPLE_COOLDOWN;
    public int sMeleeCd, dismantleCd, sAttackGap;

    // 领域
    public boolean cutscene;          // 正在放领域展开的过场：全场暂停
    public int castId;                // 第几次展开（画面结束过场时带回来，旧的回调不算数）
    public int domainTicks;           // 无量空处还剩多久（>0 时宿傩无法主动行动）

    // 招式状态（画面也读）
    public int chantPhase = -1;       // 200% 茈念到第几段
    public int meleeCombo;            // 第几下体术（画面轮换拳、踢、上勾）
    private boolean pendingCharged, redMax, purple200;
    private boolean meleeReleased, redReleased, purpleReleased;
    private float aimX = 1, aimY;     // 苍的瞄准方向（单位向量）；0 表示自动瞄准
    private int attackSeq;            // 每次出招一个编号，同一招对同一目标只命中一次
    private int meleeHitId = -1, gMeleeSeq = -2, sMeleeHitId = -1;
    private int bufMelee, bufRed, bufJump, bufPurple, bufDomain;
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
    }

    public Fighter fighter(int id) {
        return id == Fighter.GOJO ? gojo : sukuna;
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
        latch(in);
        if (hitstop > 0) {
            hitstop--;
            return;
        }
        controlGojo(in);
        if (cutscene) return; // 这一 tick 按了领域展开：停在这里等过场
        if (config.sukunaAi) ai.think(this, sukunaIntent);
        controlSukuna(sukunaIntent);
        if (!config.sukunaAi) sukunaIntent.clear();
        resolveMelee();
        resolveSukunaMelee();
        advanceBlue();
        updateRed();
        updatePurple();
        updateHollow();
        applyBlue();
        integrate(gojo);
        integrate(sukuna);
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

    private void clearInputs() {
        bufMelee = bufRed = bufJump = bufPurple = bufDomain = 0;
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
            case Fighter.FIST_WINDUP:
                drive(g, 0);
                if (st >= Tuning.FIST_WINDUP) enter(g, Fighter.FIST_ACTIVE);
                break;
            case Fighter.FIST_ACTIVE:
                drive(g, g.facing * Tuning.FIST_LUNGE);
                if (st >= Tuning.FIST_ACTIVE) enter(g, Fighter.FIST_RECOVER);
                break;
            case Fighter.FIST_RECOVER:
                drive(g, 0);
                if (st >= Tuning.FIST_RECOVERY) enter(g, Fighter.FREE);
                else if (st >= Tuning.FIST_CANCEL && wantsToAct(in)) cancelInto(g, in);
                break;
            case Fighter.BLUE_HOLD:
                move(g, in.moveX, 1f);
                updateAim(in);
                if (bufJump > 0 || bufMelee > 0 || bufRed > 0 || bufPurple > 0 || bufDomain > 0) {
                    enter(g, Fighter.FREE); // 长按确认前可以取消，不进冷却；缓冲着的那个动作下一 tick 就出
                } else if (blueNextIsUp()) {
                    bluePop();
                    startBlueWindup(false);
                } else if (bqSize == 0 && !in.blueHeld) {
                    startBlueWindup(false); // 保险：松开事件没收到（触屏事件环溢出时）
                } else if (st >= Tuning.BLUE_HOLD_THRESHOLD) {
                    startBlueWindup(true);
                }
                break;
            case Fighter.BLUE_WINDUP: {
                move(g, in.moveX, Tuning.BLUE_WINDUP_MOVE);
                int windup = pendingCharged ? Tuning.BLUE_WINDUP_CHARGED : Tuning.BLUE_WINDUP;
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
                if (!in.redHeld) redReleased = true;
                if (!redReleased && st >= Tuning.RED_HOLD) {
                    enter(g, Fighter.RED_WINDUP);
                    events.emit(Events.CAST, g.x, g.centerY(), 1, Attack.RED, tick);
                } else if (redReleased && st >= Tuning.RED_WINDUP) {
                    fireRed(false);
                }
                break;
            case Fighter.RED_WINDUP: // 最大输出的前摇
                drive(g, 0);
                if (st >= Tuning.RED_MAX_WINDUP) fireRed(true);
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
                if (!in.purpleHeld) purpleReleased = true;
                if (!purpleReleased && st >= Tuning.PURPLE_HOLD) {
                    enter(g, Fighter.PURPLE_CHANT);
                    chantPhase = 0;
                    events.emit(Events.CHANT, g.x, g.centerY(), 0, Attack.PURPLE, tick);
                } else if (purpleReleased && st >= Tuning.PURPLE_WINDUP) {
                    firePurple(false);
                }
                break;
            case Fighter.PURPLE_CHANT: { // 念咒：不能移动；松手或被攻击都会打断
                drive(g, 0);
                if (!in.purpleHeld) {
                    breakChant();
                    break;
                }
                int phase = Math.min(Tuning.CHANT_PHRASES - 1, st / Tuning.CHANT_PHRASE);
                if (phase != chantPhase) {
                    chantPhase = phase;
                    events.emit(Events.CHANT, g.x, g.centerY(), phase, Attack.PURPLE, tick);
                }
                if (st >= Tuning.CHANT_PHRASES * Tuning.CHANT_PHRASE) firePurple(true);
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
            default:
                break;
        }
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
        if (bufDomain > 0 && domainCd == 0 && domainTicks == 0) {
            bufDomain = 0;
            startDomain();
        } else if (bufMelee > 0 && meleeCd == 0) {
            bufMelee = 0;
            enter(g, Fighter.MELEE);
            meleeReleased = !in.meleeHeld;
            meleeCd = Tuning.MELEE_COOLDOWN;
            gMeleeSeq = ++attackSeq;
            meleeCombo++;
        } else if (bufRed > 0 && redCd == 0) {
            bufRed = 0;
            enter(g, Fighter.RED_HOLD);
            redReleased = !in.redHeld;
            events.emit(Events.CAST, g.x, g.centerY(), 0, Attack.RED, tick);
        } else if (bufPurple > 0 && purpleCd == 0 && purpleFailLock == 0) {
            bufPurple = 0;
            enter(g, Fighter.PURPLE_WINDUP);
            purpleReleased = !in.purpleHeld;
            events.emit(Events.CAST, g.x, g.centerY(), 0, Attack.PURPLE, tick);
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
        return Math.abs(in.moveX) > 0.3f || bufJump > 0 || bufMelee > 0 || bufRed > 0 || bufPurple > 0 || bufDomain > 0 || blueNextIsDown();
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
        b.remainder = 0;
        b.shownMilli = 0;
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

    /** 念咒被打断（松手、被攻击；按住茈还没开始念的起手也算）：什么都不放，短时间内不能再按。 */
    private void breakChant() {
        if (gojo.state != Fighter.PURPLE_CHANT && !(gojo.state == Fighter.PURPLE_WINDUP && !purpleReleased)) return;
        enter(gojo, Fighter.FREE);
        chantPhase = -1;
        purpleFailLock = Tuning.CHANT_BROKEN_LOCK;
        events.emit(Events.CHANT, gojo.x, gojo.centerY(), -1, Attack.PURPLE, tick);
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
        if (sukuna.state == Fighter.S_MELEE || sukuna.state == Fighter.S_DISMANTLE) enter(sukuna, Fighter.FREE);
        events.emit(Events.DOMAIN_OPEN, gojo.x, gojo.centerY(), Tuning.DOMAIN_DURATION, tick);
    }

    /* ---------------- 宿傩：动作执行 ---------------- */

    /** 宿傩现在能不能主动行动（倒地、硬直、被苍吸住/抓住、被领域困住时不能）。 */
    public boolean sukunaCanAct() {
        Fighter s = sukuna;
        return s.state != Fighter.KO && s.hitstun == 0 && !s.captured && domainTicks == 0 && !inBlueField(s) && !s.knockedAir;
    }

    boolean inBlueField(Fighter f) {
        if (blue.phase != BlueOrb.PULL || f.pullResist > 0) return false;
        float dx = blue.cx - f.x, dy = blue.cy - f.centerY();
        return dx * dx + dy * dy < blue.radius * blue.radius;
    }

    private void controlSukuna(SukunaIntent i) {
        Fighter s = sukuna;
        if (s.state == Fighter.KO) return;
        if (!sukunaCanAct()) {
            // 被控住：手上的动作作废；击飞、吸引的速度不去动它
            if (s.state == Fighter.S_MELEE || s.state == Fighter.S_DISMANTLE) enter(s, Fighter.FREE);
            return;
        }
        s.stateTick++;
        int st = s.stateTick;
        switch (s.state) {
            case Fighter.FREE:
                if (!config.sukunaAi && i.moveX == 0 && !i.melee && !i.dismantle && !i.jump) return; // 活靶子：什么都不做
                drive(s, i.moveX * Tuning.SUKUNA_SPEED);
                if (i.jump && s.onGround) {
                    s.vy = Tuning.JUMP_SPEED;
                    s.onGround = false;
                }
                if (sAttackGap == 0 && i.melee && sMeleeCd == 0) {
                    enter(s, Fighter.S_MELEE);
                    drive(s, 0);
                    sMeleeCd = Tuning.S_MELEE_COOLDOWN;
                    sAttackGap = Tuning.S_ATTACK_GAP;
                    attackSeq++;
                    sMeleeHitId = -1;
                    sMeleeSeq = attackSeq;
                } else if (sAttackGap == 0 && i.dismantle && dismantleCd == 0) {
                    enter(s, Fighter.S_DISMANTLE);
                    drive(s, 0);
                    dismantleCd = Tuning.DISMANTLE_COOLDOWN;
                    sAttackGap = Tuning.S_ATTACK_GAP;
                }
                break;
            case Fighter.S_MELEE:
                drive(s, st > Tuning.S_MELEE_STARTUP && st <= Tuning.S_MELEE_STARTUP + Tuning.S_MELEE_ACTIVE ? s.facing * 120f : 0);
                if (st >= Tuning.S_MELEE_STARTUP + Tuning.S_MELEE_ACTIVE + Tuning.S_MELEE_RECOVERY) enter(s, Fighter.FREE);
                break;
            case Fighter.S_DISMANTLE:
                drive(s, 0);
                if (st == Tuning.DISMANTLE_WINDUP) fireDismantle();
                if (st >= Tuning.DISMANTLE_WINDUP + Tuning.DISMANTLE_RECOVERY) enter(s, Fighter.FREE);
                break;
            default:
                break;
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
            attack.set(Fighter.SUKUNA, Fighter.GOJO, Attack.S_MELEE, sMeleeSeq, Tuning.S_MELEE_DAMAGE_MILLI,
                    s.facing * Tuning.S_MELEE_IMPULSE, 0, Tuning.S_MELEE_HITSTUN, 0);
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
            attack.set(Fighter.SUKUNA, Fighter.GOJO, Attack.DISMANTLE, ++attackSeq, Tuning.DISMANTLE_DAMAGE_MILLI, 0, 0, 8, 0);
            apply(attack);
        }
    }

    /* ---------------- 命中裁决 ---------------- */

    private void resolveMelee() {
        Fighter g = gojo, s = sukuna;
        if (s.state == Fighter.KO || meleeHitId == gMeleeSeq) return;
        boolean fist = g.state == Fighter.FIST_ACTIVE;
        if (g.state != Fighter.MELEE_ACTIVE && !fist) return;
        float dx = (s.x - g.x) * g.facing, dy = Math.abs(s.y - g.y);
        if (dx < -10 || dx > (fist ? Tuning.FIST_REACH : Tuning.MELEE_REACH) || dy > 80) return;
        meleeHitId = gMeleeSeq;
        boolean bf = blackFlash.roll(rng); // 只在真正命中时判定（苍拳也算）
        int kind;
        if (bf) {
            kind = Attack.BLACK_FLASH;
            attack.set(Fighter.GOJO, Fighter.SUKUNA, kind, gMeleeSeq, fist ? Tuning.FIST_BLACK_FLASH_DAMAGE_MILLI : Tuning.BLACK_FLASH_DAMAGE_MILLI,
                    g.facing * Tuning.BLACK_FLASH_IMPULSE_X, Tuning.BLACK_FLASH_IMPULSE_Y, Tuning.BLACK_FLASH_HITSTUN, 0);
        } else if (fist) {
            kind = Attack.BLUE_FIST;
            attack.set(Fighter.GOJO, Fighter.SUKUNA, kind, gMeleeSeq, Tuning.FIST_DAMAGE_MILLI,
                    g.facing * Tuning.FIST_IMPULSE_X, Tuning.FIST_IMPULSE_Y, Tuning.FIST_HITSTUN, 0);
        } else {
            kind = Attack.MELEE;
            attack.set(Fighter.GOJO, Fighter.SUKUNA, kind, gMeleeSeq, Tuning.MELEE_DAMAGE_MILLI,
                    g.facing * Tuning.MELEE_IMPULSE, 0, Tuning.MELEE_HITSTUN, 0);
        }
        int mask = apply(attack);
        if ((mask & Defense.ALLOW_DAMAGE) == 0) return;
        hitstop = bf ? Tuning.BLACK_FLASH_HITSTOP : fist ? Tuning.FIST_HITSTOP : Tuning.MELEE_HITSTOP;
        if (bf) events.emit(Events.BLACK_FLASH, (g.x + s.x) * 0.5f, s.centerY(), g.facing, fist ? Attack.BLUE_FIST : Attack.BLACK_FLASH, tick);
    }

    /** 一次攻击接触：交给防御裁决，再按允许的部分生效。返回裁决位掩码。 */
    public int apply(Attack a) {
        Fighter t = fighter(a.target);
        int mask = Defense.resolve(this, a);
        // 200% 茈的念咒：被攻击就打断（被无限挡住也算：攻击碰到了）
        if (t == gojo && a.source == Fighter.SUKUNA) breakChant();
        if ((mask & Defense.SHOW_INFINITY) != 0) {
            events.emit(Events.INFINITY_BLOCK, t.x, t.centerY(), a.kind, a.kind, tick);
            return mask;
        }
        if ((mask & Defense.ALLOW_DAMAGE) != 0 && a.damageMilli > 0) {
            t.hpMilli = Math.max(0, t.hpMilli - a.damageMilli);
            if (a.kind != Attack.BLUE_FIELD) events.emit(Events.DAMAGE, t.x, t.y + Tuning.BODY_H + 10, a.damageMilli / 1000f, a.kind, tick);
        }
        if ((mask & Defense.ALLOW_IMPULSE) != 0 && (a.impulseX != 0 || a.impulseY != 0)) {
            t.vx = clamp(t.vx + a.impulseX, -Tuning.MAX_VX, Tuning.MAX_VX);
            t.vy = Math.min(t.vy + a.impulseY, Tuning.MAX_VY);
            if (a.impulseY > 0) {
                t.onGround = false;
                t.knockedAir = true; // 被打飞了：落地之前不能主动行动（不去改飞行的速度）
            }
        }
        if ((mask & Defense.ALLOW_STUN) != 0) {
            t.hitstun = Math.max(t.hitstun, a.hitstun);
            if (t.state == Fighter.S_MELEE || t.state == Fighter.S_DISMANTLE) enter(t, Fighter.FREE); // 宿傩出招被打断
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
        Fighter s = sukuna;
        if (!r.hit && s.state != Fighter.KO && touches(r.x, r.y, r.max ? Tuning.RED_MAX_ORB_R : Tuning.RED_ORB_R, s)) {
            r.hit = true;
            if (r.max) {
                attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.RED, r.instanceId, Tuning.RED_MAX_DAMAGE_MILLI,
                        r.dir * Tuning.RED_MAX_IMPULSE_X, Tuning.RED_MAX_IMPULSE_Y, Tuning.RED_MAX_HITSTUN, 0);
            } else {
                attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.RED, r.instanceId, Tuning.RED_DAMAGE_MILLI,
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
        Fighter s = sukuna;
        if (!p.hit && s.state != Fighter.KO && touches(p.x, p.y, r, s)) {
            p.hit = true;
            if (p.max) {
                attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.PURPLE, p.instanceId, Tuning.PURPLE_200_DAMAGE_MILLI,
                        p.dir * Tuning.PURPLE_200_IMPULSE_X, Tuning.PURPLE_200_IMPULSE_Y, Tuning.PURPLE_200_HITSTUN, 0);
            } else {
                attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.PURPLE, p.instanceId, Tuning.PURPLE_DAMAGE_MILLI,
                        p.dir * Tuning.PURPLE_IMPULSE_X, Tuning.PURPLE_IMPULSE_Y, Tuning.PURPLE_HITSTUN, 0);
            }
            if ((apply(attack) & Defense.ALLOW_IMPULSE) != 0) {
                s.captured = false;
                s.pullResist = Tuning.RED_PULL_RESIST;
                if (p.max) hitstop = Tuning.PURPLE_200_HITSTOP;
            }
        }
        if (p.x < -r * 2 || p.x > Tuning.WIDTH + r * 2) p.active = false;
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
        h.hitSukuna = h.hitGojo = false;
        red.active = false;
        blue.phase = BlueOrb.OFF;      // 苍和赫都被吃掉了
        sukuna.captured = false;
        events.emit(Events.HOLLOW_FORM, x, y, Tuning.HOLLOW_RADIUS, Attack.HOLLOW, tick);
    }

    private void updateHollow() {
        Hollow h = hollow;
        if (!h.active) return;
        h.tick++;
        float k = Math.min(1f, h.tick / (float) Tuning.HOLLOW_GROW);
        h.radius = Tuning.HOLLOW_RADIUS * (1 - (1 - k) * (1 - k)); // 先快后慢
        Fighter s = sukuna, g = gojo;
        if (!h.hitSukuna && s.state != Fighter.KO && touches(h.x, h.y, h.radius, s)) {
            h.hitSukuna = true;
            float dir = s.x >= h.x ? 1 : -1;
            attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.HOLLOW, h.instanceId, Tuning.HOLLOW_DAMAGE_MILLI,
                    dir * Tuning.HOLLOW_IMPULSE_X, Tuning.HOLLOW_IMPULSE_Y, Tuning.HOLLOW_HITSTUN, 0);
            if ((apply(attack) & Defense.ALLOW_IMPULSE) != 0) s.pullResist = Tuning.RED_PULL_RESIST;
        }
        if (!h.hitGojo && g.state != Fighter.KO && touches(h.x, h.y, h.radius, g)) {
            h.hitGojo = true;
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
        float nx = clamp(cx, f.x - Tuning.BODY_HALF_W, f.x + Tuning.BODY_HALF_W);
        float ny = clamp(cy, f.y, f.y + Tuning.BODY_H);
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
        Fighter s = sukuna;
        if (s.state == Fighter.KO) return;
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
            s.ax += axx;
            s.ay += ayy;
            s.pulled = true;
            s.gravityScale = Math.min(s.gravityScale, 1f - q); // 越靠近中心越失重
        }
        // 持续伤害：离中心越近越高，核心最高每秒 100；按余数累计，不因截断丢血
        float qd = clamp((b.radius - r) / (b.radius - Tuning.BLUE_CORE), 0f, 1f);
        long dpsMilli = Math.round(Tuning.BLUE_MAX_DPS_MILLI * (double) (qd * qd));
        b.remainder += dpsMilli;
        long dmg = b.remainder / Tuning.HZ;
        b.remainder %= Tuning.HZ;
        if (dmg > 0) {
            attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.BLUE_FIELD, b.instanceId, dmg, 0, 0, 0, 0);
            if ((apply(attack) & Defense.ALLOW_DAMAGE) != 0) b.shownMilli += dmg;
        }
        if (b.tick % 30 == 0 && b.shownMilli > 0) {
            events.emit(Events.DAMAGE, s.x, s.y + Tuning.BODY_H + 10, b.shownMilli / 1000f, Attack.BLUE_FIELD, tick);
            b.shownMilli = 0;
        }
    }

    private void endBlue() {
        blue.phase = BlueOrb.OFF;
        sukuna.captured = false;
        events.emit(Events.BLUE_END, blue.cx, blue.cy, 0, tick);
    }

    /* ---------------- 积分、地面、墙 ---------------- */

    private void integrate(Fighter f) {
        float dt = Tuning.DT;
        if (f.captured && blue.phase != BlueOrb.PULL) f.captured = false; // 苍没了，捕获也没了
        if (f.captured) {
            f.x = blue.cx;
            f.y = blue.cy - Tuning.BODY_H * 0.5f;
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
            if (f.id == Fighter.SUKUNA && blue.phase == BlueOrb.PULL && f.pullResist == 0
                    && segmentNear(ox, oy, f.x, f.centerY(), blue.cx, blue.cy, Tuning.BLUE_CORE)) {
                f.captured = true;
                f.x = blue.cx;
                f.y = blue.cy - Tuning.BODY_H * 0.5f;
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
        float lo = Tuning.BODY_HALF_W, hi = Tuning.WIDTH - Tuning.BODY_HALF_W;
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
        if (sMeleeCd > 0) sMeleeCd--;
        if (dismantleCd > 0) dismantleCd--;
        if (sAttackGap > 0) sAttackGap--;
        if (domainTicks > 0 && --domainTicks == 0) {
            domains.sureHit[Fighter.GOJO][Fighter.SUKUNA] = false;
            domainCd = Tuning.DOMAIN_COOLDOWN;
            events.emit(Events.DOMAIN_CLOSE, gojo.x, gojo.centerY(), 0, tick);
        } else if (domainTicks == 0 && domainCd > 0) {
            domainCd--;
        }
        if (bufMelee > 0) bufMelee--;
        if (bufRed > 0) bufRed--;
        if (bufJump > 0) bufJump--;
        if (bufPurple > 0) bufPurple--;
        if (bufDomain > 0) bufDomain--;
        for (int i = 0; i < bqSize; i++) bqTtl[(bqHead + i) % BQ]--;
        while (bqSize > 0 && bqTtl[bqHead] <= 0) bluePop(); // 越早的越先到期，到期的总在队头
        for (int i = 0; i < 2; i++) {
            Fighter f = fighter(i);
            if (f.hitstun > 0) f.hitstun--;
            if (f.pullResist > 0) f.pullResist--;
        }
        // 打空血量就倒地，过一会儿满血站起来（训练：宿傩是靶子；五条悟被自己的无指向茈打空也一样）
        for (int i = 0; i < 2; i++) {
            Fighter f = fighter(i);
            if (f.state != Fighter.KO && f.hpMilli == 0) {
                if (f == gojo) breakChant(); // 念咒（或按住茈的起手）中倒下：打断
                f.state = Fighter.KO;
                f.stateTick = 0;
                f.captured = false;
                f.koTimer = Tuning.KO_TICKS;
                events.emit(Events.KO, f.x, f.centerY(), 0, f.id, tick);
            } else if (f.state == Fighter.KO && --f.koTimer <= 0) {
                f.state = Fighter.FREE;
                f.stateTick = 0;
                f.hpMilli = f.maxHpMilli;
                events.emit(Events.REVIVE, f.x, f.centerY(), 0, f.id, tick);
            }
        }
        // 宿傩自由的时候面向五条悟（出招时锁定方向）
        Fighter s = sukuna;
        if (s.state == Fighter.FREE && s.hitstun == 0) s.facing = gojo.x >= s.x ? 1 : -1;
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

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
