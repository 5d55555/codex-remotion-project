package com.shinjuku.showdown.core;

/**
 * 给画面用的表现事件（命中火花、黑闪、赫的爆发、无限挡下、伤害数字……）。
 * 固定容量的环；逻辑只写，画面按序号读，读漏了也不影响战斗结果。
 */
public final class Events {
    public static final int HIT = 1, BLACK_FLASH = 2, RED_BURST = 3, BLUE_SPAWN = 4, BLUE_LAND = 5, BLUE_END = 6,
            INFINITY_BLOCK = 7, DAMAGE = 8, KO = 9, REVIVE = 10, CAST = 11, RED_FIRE = 12, PURPLE_FIRE = 13,
            HOLLOW_FORM = 14, HOLLOW_END = 15, LAND = 16,
            CHANT = 17,            // value：第几段咒词（0..3），-1 被打断
            DOMAIN_CUTSCENE = 18,  // 要放过场了（世界停住，等 endCutscene）
            DOMAIN_OPEN = 19, DOMAIN_CLOSE = 20,
            SLASH = 21;            // 宿傩的解：从 (x, y) 斩到 value 那个 x
    // CAST 的 value：0 普通，1 苍出力最大 / 赫最大输出，2 茈 200%

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
