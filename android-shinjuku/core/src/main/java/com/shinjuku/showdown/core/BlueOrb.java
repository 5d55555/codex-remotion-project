package com.shinjuku.showdown.core;

/** 苍：同时最多一个。先飞向落点（不吸、不伤），到了以后吸引并按距离造成持续伤害。 */
public final class BlueOrb {
    public static final int OFF = 0, FLY = 1, PULL = 2;

    public int phase = OFF;
    public boolean charged;
    public int tick, instanceId;
    public float ox, oy;   // 投出点
    public float cx, cy;   // 落点（吸力中心）
    public float x, y;     // 当前位置（画面用）
    public float px, py;   // 上一 tick 的位置（画面插值用）
    public float radius, accel;
    public float pull = 1; // 吸力倍率：出力最大为 2
    // 每个目标各自一份（宿傩、魔虚罗）
    final long[] remainder = new long[Fighter.COUNT];  // 持续伤害的余数（毫血 × tick），保证核心里每 120 tick 正好 100 血
    final long[] shownMilli = new long[Fighter.COUNT]; // 还没显示成伤害数字的持续伤害（每 0.25 秒汇总一次）

    public boolean active() {
        return phase != OFF;
    }
}
