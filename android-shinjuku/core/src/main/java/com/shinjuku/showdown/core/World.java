package com.shinjuku.showdown.core;

/**
 * 战斗世界：每秒 120 tick 固定步长推进。完全确定：同一个配置 + 同一串输入 = 同一个结果。
 * 一个 tick 的顺序：五条悟的操作与动作状态机 → 攻击命中裁决（赫先于苍的吸力）→ 苍的吸力与持续伤害
 * → 积分（重力、外力、阻尼）→ 地面与墙 → 计时器。
 * 热路径不分配对象。
 */
public final class World {
    public final BattleConfig config;
    public final Fighter gojo = new Fighter(Fighter.GOJO);
    public final Fighter sukuna = new Fighter(Fighter.SUKUNA);
    public final BlueOrb blue = new BlueOrb();
    public final Events events = new Events();
    public final Domains domains = new Domains();
    public final CombatRng rng;
    public final BlackFlash blackFlash = new BlackFlash();

    public int tick;
    public int hitstop;               // >0 时整个世界停住（黑闪的命中停顿）
    public int meleeCd, blueCd, redCd, blueFailLock;
    public int blueCdMax = Tuning.BLUE_COOLDOWN;
    private boolean pendingCharged;
    private float aimX = 1, aimY;     // 苍的瞄准方向（单位向量）
    private int attackSeq;            // 每次出招一个编号，同一招对同一目标只命中一次
    private int meleeHitId = -1, redHitId = -1;
    private int koTimer;
    private int bufMelee, bufRed, bufJump;
    // 苍的按下/松开按发生顺序排队（停顿期间按的也留着）；只有用得上的状态才取，过一会儿没用上就作废
    private static final int BQ = 8;
    private final boolean[] bqDown = new boolean[BQ];
    private final int[] bqTtl = new int[BQ];
    private int bqHead, bqSize;
    public int meleeCombo;            // 第几下体术（画面轮换拳、踢、上勾）
    private final Attack attack = new Attack();

    public World(BattleConfig config) {
        this.config = config;
        rng = new CombatRng(config.seed);
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
        tick++;
        gojo.px = gojo.x;
        gojo.py = gojo.y;
        sukuna.px = sukuna.x;
        sukuna.py = sukuna.y;
        blue.px = blue.x;
        blue.py = blue.y;
        latch(in);
        if (hitstop > 0) {
            hitstop--;
            return;
        }
        controlGojo(in);
        resolveAttacks();
        applyBlue();
        integrate(gojo);
        integrate(sukuna);
        timers();
    }

    /* ---------------- 五条悟：操作与动作状态机 ---------------- */

    /** 先把这一 tick 的按键记下来（命中停顿期间世界不动，但按键不能丢）。同一 tick 里有按下又有松开时，一定是先按后松。 */
    private void latch(TickInput in) {
        if (in.melee) bufMelee = Tuning.INPUT_BUFFER;
        if (in.red) bufRed = Tuning.INPUT_BUFFER;
        if (in.jump) bufJump = Tuning.INPUT_BUFFER;
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

    private void controlGojo(TickInput in) {
        Fighter g = gojo;
        g.moveInput = in.moveX;
        if (g.hitstun > 0) return;
        g.stateTick++;
        switch (g.state) {
            case Fighter.FREE:
                while (blueNextIsUp()) bluePop(); // 没在蓄的时候松开：没有意义
                move(g, in.moveX, 1f);
                faceByInput(g, in.moveX);
                if (bufJump > 0 && g.onGround) {
                    bufJump = 0;
                    g.vy = Tuning.JUMP_SPEED;
                    g.onGround = false;
                }
                if (bufMelee > 0 && meleeCd == 0) {
                    bufMelee = 0;
                    enter(g, Fighter.MELEE);
                    meleeCd = Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE + Tuning.MELEE_RECOVERY;
                    attackSeq++;
                    meleeCombo++;
                } else if (bufRed > 0 && redCd == 0) {
                    bufRed = 0;
                    enter(g, Fighter.RED_WINDUP);
                    attackSeq++;
                    events.emit(Events.CAST, g.x, g.centerY(), 0, Attack.RED, tick);
                } else if (blueNextIsDown() && blueCd == 0 && blueFailLock == 0 && !blue.active()) {
                    bluePop();
                    enter(g, Fighter.BLUE_HOLD);
                    bufMelee = bufRed = bufJump = 0; // 更早按的那些被这次苍取代，不会马上把蓄力取消掉
                    updateAim(in);
                    if (blueNextIsUp()) { // 同一 tick 里按下又松开：普通苍
                        bluePop();
                        startBlueWindup(false);
                    }
                }
                break;
            case Fighter.MELEE:
                move(g, 0, 0);
                if (g.stateTick >= Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE + Tuning.MELEE_RECOVERY) enter(g, Fighter.FREE);
                break;
            case Fighter.BLUE_HOLD:
                move(g, in.moveX, 1f);
                updateAim(in);
                if (bufJump > 0 || bufMelee > 0 || bufRed > 0) {
                    enter(g, Fighter.FREE); // 长按确认前可以取消，不进冷却；缓冲着的那个动作下一 tick 就出
                } else if (blueNextIsUp()) {
                    bluePop();
                    startBlueWindup(false);
                } else if (bqSize == 0 && !in.blueHeld) {
                    startBlueWindup(false); // 保险：松开事件没收到（触屏事件环溢出时）
                } else if (g.stateTick >= Tuning.BLUE_HOLD_THRESHOLD) {
                    startBlueWindup(true);
                }
                break;
            case Fighter.BLUE_WINDUP: {
                move(g, in.moveX, Tuning.BLUE_WINDUP_MOVE);
                int windup = pendingCharged ? Tuning.BLUE_WINDUP_CHARGED : Tuning.BLUE_WINDUP;
                if (g.stateTick <= windup - Tuning.BLUE_AIM_LOCK) updateAim(in); // 最后 12 tick 锁定方向
                if (g.stateTick >= windup) {
                    spawnBlue(pendingCharged);
                    enter(g, Fighter.BLUE_RECOVER);
                }
                break;
            }
            case Fighter.BLUE_RECOVER:
                move(g, in.moveX, 0.5f);
                if (g.stateTick >= 10) enter(g, Fighter.FREE);
                break;
            case Fighter.RED_WINDUP:
                move(g, 0, 0);
                if (g.stateTick >= Tuning.RED_WINDUP) {
                    enter(g, Fighter.RED_ACTIVE);
                    redCd = Tuning.RED_COOLDOWN;
                    events.emit(Events.RED_BURST, g.x + g.facing * 30, g.y + 52, g.facing, tick);
                }
                break;
            case Fighter.RED_ACTIVE:
                move(g, 0, 0);
                if (g.stateTick >= Tuning.RED_ACTIVE) enter(g, Fighter.RED_RECOVER);
                break;
            case Fighter.RED_RECOVER:
                move(g, 0, 0);
                if (g.stateTick >= Tuning.RED_RECOVERY) enter(g, Fighter.FREE);
                break;
            default:
                break;
        }
    }

    private void enter(Fighter f, int state) {
        f.state = state;
        f.stateTick = 0;
    }

    private void move(Fighter f, float stick, float mult) {
        f.vx = stick * Tuning.MOVE_SPEED * mult;
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

    /* ---------------- 命中裁决 ---------------- */

    private void resolveAttacks() {
        Fighter g = gojo, s = sukuna;
        if (s.state == Fighter.KO) return;
        // 体术：有效帧内碰到就算一次（同一招只算一次）
        if (g.state == Fighter.MELEE && g.stateTick > Tuning.MELEE_STARTUP
                && g.stateTick <= Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE && meleeHitId != attackSeq) {
            float dx = (s.x - g.x) * g.facing, dy = Math.abs(s.y - g.y);
            if (dx >= -10 && dx <= Tuning.MELEE_REACH && dy <= 80) {
                meleeHitId = attackSeq;
                boolean bf = blackFlash.roll(rng); // 只在真正命中时判定
                if (bf) {
                    attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.BLACK_FLASH, attackSeq, Tuning.BLACK_FLASH_DAMAGE_MILLI,
                            g.facing * Tuning.BLACK_FLASH_IMPULSE_X, Tuning.BLACK_FLASH_IMPULSE_Y, Tuning.BLACK_FLASH_HITSTUN, 0);
                } else {
                    attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.MELEE, attackSeq, Tuning.MELEE_DAMAGE_MILLI,
                            g.facing * Tuning.MELEE_IMPULSE, 0, Tuning.MELEE_HITSTUN, 0);
                }
                int mask = apply(attack);
                if (bf && (mask & Defense.ALLOW_DAMAGE) != 0) {
                    hitstop = Tuning.BLACK_FLASH_HITSTOP;
                    events.emit(Events.BLACK_FLASH, (g.x + s.x) * 0.5f, s.centerY(), g.facing, Attack.BLACK_FLASH, tick);
                }
            }
        }
        // 赫：有效帧内前方一段距离内就击飞（同一招只算一次）；先于苍的吸力结算
        if (g.state == Fighter.RED_ACTIVE && redHitId != attackSeq) {
            float dx = (s.x - g.x) * g.facing, dy = Math.abs(s.centerY() - g.centerY());
            if (dx >= -10 && dx <= Tuning.RED_REACH + 2 * Tuning.BODY_HALF_W && dy <= 120) {
                redHitId = attackSeq;
                attack.set(Fighter.GOJO, Fighter.SUKUNA, Attack.RED, attackSeq, Tuning.RED_DAMAGE_MILLI,
                        g.facing * Tuning.RED_IMPULSE_X, Tuning.RED_IMPULSE_Y, Tuning.RED_HITSTUN, 0);
                if ((apply(attack) & Defense.ALLOW_IMPULSE) != 0) {
                    s.captured = false;            // 赫解除苍的捕获
                    s.pullResist = Tuning.RED_PULL_RESIST; // 免得刚击飞又被吸回去
                }
            }
        }
    }

    /** 一次攻击接触：交给防御裁决，再按允许的部分生效。返回裁决位掩码。 */
    public int apply(Attack a) {
        Fighter t = fighter(a.target);
        int mask = Defense.resolve(this, a);
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
            if (a.impulseY > 0) t.onGround = false;
        }
        if ((mask & Defense.ALLOW_STUN) != 0) t.hitstun = Math.max(t.hitstun, a.hitstun);
        if (a.kind != Attack.BLUE_FIELD) {
            t.lastHitTick = tick;
            t.lastHitKind = a.kind;
            events.emit(Events.HIT, t.x, t.centerY(), a.kind, a.kind, tick);
        }
        return mask;
    }

    /* ---------------- 苍：飞行、吸引、持续伤害 ---------------- */

    private void applyBlue() {
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
            return;
        }
        if (b.tick > Tuning.BLUE_LIFE) {
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
            }
            if (f.y <= 0) {
                f.y = 0;
                if (f.vy < 0) f.vy = 0;
                f.onGround = true;
            } else {
                f.onGround = false;
            }
            // 地上滑行减速（被击飞落地后）；五条悟自己控制速度，被苍吸着时也不减
            if (f.onGround && f.id == Fighter.SUKUNA && !f.pulled) {
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

    /* ---------------- 计时器、活靶子复活 ---------------- */

    private void timers() {
        if (meleeCd > 0) meleeCd--;
        if (blueCd > 0) blueCd--;
        if (redCd > 0) redCd--;
        if (blueFailLock > 0) blueFailLock--;
        if (bufMelee > 0) bufMelee--;
        if (bufRed > 0) bufRed--;
        if (bufJump > 0) bufJump--;
        for (int i = 0; i < bqSize; i++) bqTtl[(bqHead + i) % BQ]--;
        while (bqSize > 0 && bqTtl[bqHead] <= 0) bluePop(); // 越早的越先到期，到期的总在队头
        for (int i = 0; i < 2; i++) {
            Fighter f = fighter(i);
            if (f.hitstun > 0) f.hitstun--;
            if (f.pullResist > 0) f.pullResist--;
        }
        Fighter s = sukuna;
        if (s.state != Fighter.KO && s.hpMilli == 0) {
            s.state = Fighter.KO;
            s.captured = false;
            koTimer = Tuning.KO_TICKS;
            events.emit(Events.KO, s.x, s.centerY(), 0, tick);
        } else if (s.state == Fighter.KO && --koTimer <= 0) {
            s.state = Fighter.FREE;
            s.hpMilli = s.maxHpMilli;
            events.emit(Events.REVIVE, s.x, s.centerY(), 0, tick);
        }
        // 宿傩没有 AI：只是面向五条悟
        if (s.state != Fighter.KO && s.hitstun == 0) s.facing = gojo.x >= s.x ? 1 : -1;
    }

    /* ---------------- 给画面读的状态 ---------------- */

    /** 苍的冷却剩余比例 0..1。 */
    public float blueCooldown01() {
        return blueCd / (float) blueCdMax;
    }

    public boolean pendingCharged() {
        return pendingCharged;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
