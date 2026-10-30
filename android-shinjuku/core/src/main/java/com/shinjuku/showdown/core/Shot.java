package com.shinjuku.showdown.core;

/** 飞行的术式球：赫（短程）和茈（一直飞到战场外）共用。 */
public final class Shot {
    public boolean active;
    public int tick, instanceId;
    public float x, y, px, py;   // 当前位置、上一 tick 位置（画面插值用）
    public float dir;            // +1 向右，-1 向左
    public boolean max;          // 赫的最大输出 / 200% 的茈
    boolean hit;                 // 赫：已经打中过（打到第一个就爆）
    int hitMask;                 // 茈：已经打中过谁（按 Fighter.id 的位；穿透，每人一次）

    void start(int id, float x, float y, float dir) {
        active = true;
        tick = 0;
        instanceId = id;
        this.x = px = x;
        this.y = py = y;
        this.dir = dir;
        hit = false;
        hitMask = 0;
        max = false;
    }
}
