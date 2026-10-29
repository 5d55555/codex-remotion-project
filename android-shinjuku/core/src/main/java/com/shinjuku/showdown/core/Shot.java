package com.shinjuku.showdown.core;

/** 飞行的术式球：赫（短程）和茈（一直飞到战场外）共用。 */
public final class Shot {
    public boolean active;
    public int tick, instanceId;
    public float x, y, px, py;   // 当前位置、上一 tick 位置（画面插值用）
    public float dir;            // +1 向右，-1 向左
    boolean hit;                 // 已经打中过宿傩（同一发只算一次）

    void start(int id, float x, float y, float dir) {
        active = true;
        tick = 0;
        instanceId = id;
        this.x = px = x;
        this.y = py = y;
        this.dir = dir;
        hit = false;
    }
}
