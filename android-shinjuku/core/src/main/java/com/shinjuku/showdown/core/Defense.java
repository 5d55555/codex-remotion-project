package com.shinjuku.showdown.core;

/**
 * 防御裁决：决定一次攻击接触里，伤害、冲量、硬直哪些生效。返回位掩码，不分配对象。
 * “无限”：攻击特效照常出，但伤害、击退、硬直都不生效；只有领域必中、领域展延、特殊手段
 * 在世界状态确实成立时才能绕过（攻击方光声明一个标志位不算数）。
 */
public final class Defense {
    public static final int ALLOW_DAMAGE = 1, ALLOW_IMPULSE = 2, ALLOW_STUN = 4, SHOW_INFINITY = 8;
    public static final int ALLOW_ALL = ALLOW_DAMAGE | ALLOW_IMPULSE | ALLOW_STUN;

    private Defense() {
    }

    public static int resolve(World w, Attack a) {
        Fighter t = w.fighter(a.target);
        if (t.infinity && !bypassesInfinity(w, a)) return SHOW_INFINITY;
        return ALLOW_ALL;
    }

    static boolean bypassesInfinity(World w, Attack a) {
        // 自己的无指向茈：无限挡不住自己的术式（原作第 235 话五条被卷进自己的茈里）
        if ((a.kind == Attack.HOLLOW || a.kind == Attack.PURPLE_MAX) && a.source == a.target) return true;
        Domains d = w.domains;
        if ((a.flags & Attack.SURE_HIT) != 0 && d.sureHit[a.source][a.target]) return true;
        if ((a.flags & Attack.DOMAIN_AMPLIFICATION) != 0 && d.amplification[a.source]) return true;
        return (a.flags & Attack.SPECIAL_BYPASS) != 0 && d.specialBypass[a.source];
    }
}
