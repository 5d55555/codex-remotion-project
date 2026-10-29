package com.shinjuku.showdown.core;

/** 一个角色的逻辑状态。所有字段直接读写，没有对象分配。 */
public final class Fighter {
    public static final int GOJO = 0, SUKUNA = 1;

    // 动作状态
    // 五条悟：MELEE 是体术起手（按住会蓄成苍拳），RED_HOLD 是赫起手（按住会蓄成最大输出，RED_WINDUP 是最大输出的前摇），
    // PURPLE_WINDUP 是茈起手（按住进入 PURPLE_CHANT 念咒）。宿傩：S_MELEE 体术、S_DISMANTLE 解。
    public static final int FREE = 0, MELEE = 1, BLUE_HOLD = 2, BLUE_WINDUP = 3, RED_WINDUP = 4, RED_ACTIVE = 5,
            RED_RECOVER = 6, BLUE_RECOVER = 7, KO = 8, PURPLE_WINDUP = 9, PURPLE_RECOVER = 10,
            MELEE_ACTIVE = 11, MELEE_RECOVER = 12, FIST_WINDUP = 13, FIST_ACTIVE = 14, FIST_RECOVER = 15,
            RED_HOLD = 16, PURPLE_CHANT = 17, DOMAIN_CAST = 18, S_MELEE = 19, S_DISMANTLE = 20;

    public final int id;
    public float x, y, vx, vy;   // y 是离地高度
    public float px, py;         // 上一 tick 的位置（画面插值用）
    public int facing = 1;
    public boolean onGround = true;

    public long hpMilli, maxHpMilli;
    public int state = FREE, stateTick;
    public int hitstun;          // >0 时不能主动行动
    public int pullResist;       // >0 时不受苍吸引
    public boolean infinity;     // 五条悟的被动“无限”
    public boolean captured;     // 被苍的核心抓住

    // 每 tick 由外力累加（苍），积分后清零
    public float ax, ay, gravityScale = 1f;
    public boolean pulled;       // 这一 tick 被苍吸着（不受地面摩擦，限速）
    boolean selfMove;            // 这一 tick 是自己在走（走路、出拳往前带）：不受地面摩擦
    boolean knockedAir;          // 被打飞到空中，还没落地

    // 给画面用
    public float moveInput;      // 本 tick 的移动输入（跑步动画）
    public int lastHitTick = -9999, lastHitKind;
    int koTimer;

    Fighter(int id) {
        this.id = id;
    }

    public float centerY() {
        return y + Tuning.BODY_H * 0.5f;
    }

    public float hp() {
        return hpMilli / 1000f;
    }
}
