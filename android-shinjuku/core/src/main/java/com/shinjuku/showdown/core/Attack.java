package com.shinjuku.showdown.core;

/**
 * 一次攻击接触的上下文。全世界只有一个复用的实例，每次填好交给 Defense 裁决。
 */
public final class Attack {
    // 攻击种类
    public static final int MELEE = 1, BLACK_FLASH = 2, BLUE_FIELD = 3, RED = 4, PURPLE = 5, HOLLOW = 6,
            BLUE_FIST = 7, S_MELEE = 8, DISMANTLE = 9, WORLD_SLASH = 10, AGITO = 11, M_PUNCH = 12, M_SWORD = 13, PURPLE_MAX = 14, TEST = 99;

    // 攻击声明的特性（声明不等于生效：能不能绕过无限由 Defense 结合世界状态判断）
    public static final int SURE_HIT = 1;              // 领域必中
    public static final int DOMAIN_AMPLIFICATION = 2;  // 领域展延
    public static final int SPECIAL_BYPASS = 4;        // 其他特殊手段

    public int source, target, kind, flags, instanceId;
    public long damageMilli;
    public float impulseX, impulseY;
    public int hitstun;

    void set(int source, int target, int kind, int instanceId, long damageMilli, float ix, float iy, int hitstun, int flags) {
        this.source = source;
        this.target = target;
        this.kind = kind;
        this.instanceId = instanceId;
        this.damageMilli = damageMilli;
        this.impulseX = ix;
        this.impulseY = iy;
        this.hitstun = hitstun;
        this.flags = flags;
    }
}
