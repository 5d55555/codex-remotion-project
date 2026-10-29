package com.shinjuku.showdown.core;

/**
 * 给画面用的表现事件（命中火花、黑闪、赫的爆发、无限挡下、伤害数字……）。
 * 固定容量的环；逻辑只写，画面按序号读，读漏了也不影响战斗结果。
 */
public final class Events {
    public static final int HIT = 1, BLACK_FLASH = 2, RED_BURST = 3, BLUE_SPAWN = 4, BLUE_LAND = 5, BLUE_END = 6,
            INFINITY_BLOCK = 7, DAMAGE = 8, KO = 9, REVIVE = 10, CAST = 11;

    public static final int CAP = 128, MASK = CAP - 1;
    public final int[] type = new int[CAP];
    public final float[] x = new float[CAP], y = new float[CAP], value = new float[CAP];
    public final int[] tick = new int[CAP];
    public final int[] kind = new int[CAP]; // 相关的攻击种类（Attack.*），没有就是 0
    public long written;

    void emit(int t, float ex, float ey, float v, int atTick) {
        emit(t, ex, ey, v, 0, atTick);
    }

    void emit(int t, float ex, float ey, float v, int k, int atTick) {
        int i = (int) (written & MASK);
        type[i] = t;
        kind[i] = k;
        x[i] = ex;
        y[i] = ey;
        value[i] = v;
        tick[i] = atTick;
        written++;
    }
}
