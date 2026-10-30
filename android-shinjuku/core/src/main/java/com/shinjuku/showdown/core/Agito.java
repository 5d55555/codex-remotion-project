package com.shinjuku.showdown.core;

/**
 * 嵌合兽「顎吐」的一次突袭：从宿傩的影子里飞出，从锁定点上方俯冲下来，到了以后电击一下（AGITO_ACTIVE tick 内碰到一次）就消失。
 * 同时最多一只。
 */
public final class Agito {
    public boolean active;
    public int tick, instanceId;
    public float x, y, px, py;   // 当前位置、上一 tick 位置（画面插值用）
    public float x0, y0, tx, ty; // 起点、锁定的落点
    public int dir;              // 朝哪边飞
    boolean contacted;           // 已经碰到过五条悟
}
