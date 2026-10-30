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
            SLASH = 21,            // 宿傩的解 / 世界斩（kind 区分）：从 (x, y) 斩到 value 那个 x
            AMP = 22,              // 宿傩的领域展延：value 1 开、0 关
            ADAPT = 23,            // 魔虚罗的适应：value 是适应点数；kind 1 表示学会了世界斩
            WORLD_CHANT = 24,      // 世界斩的起手：value 0 结印（第一次是裂纹预警）、1..3 咏唱、4 指向（锁定）、-1 被打断
            RCT = 25,              // 宿傩的反转术式：value 1 开始回复、0 结束或被打断
            DODGE = 26,            // 五条悟闪避：value 是方向
            TELEPORT = 27,         // 五条悟瞬移到宿傩背后：从 (x, y) 到 value 那个 x；kind 是 Attack.BLUE_FIST（苍）或 Attack.RED（赫）
            SUMMON = 28,           // 召唤魔虚罗：value 0 开始念「布瑠部由良由良」、1 魔虚罗在 (x, y) 显现、-1 被打断
            AGITO = 29,            // 顎吐：value 0 从 (x, y) 飞出、1 在 (x, y) 俯冲电击、2 消失
            TRANSFORM = 30,        // 宿傩变原身：value 1 开始（回满血）、0 变完
            MAHORAGA_GONE = 31,    // 魔虚罗退场：value 0 被打倒、1 宿傩倒下或变原身（十种影法术没了）
            CINE = 32,             // 固定动作里的节点：value 见下面的 CINE_*，(x, y) 是发生的位置
            SPACE = 33;            // 宿傩的空间斩：value 0 起手、1 锁定、3 斩死五条悟、-1 起手被打断、4 力竭开始、5 力竭结束；kind 是方向（±1）
    // 空间斩斩出去的那一下另发一个 SLASH（kind 是 Attack.SPACE_SLASH）
    public static final int CINE_RED = 1, CINE_SLASH = 2, CINE_MAHO_SWING = 3, CINE_CHANT = 4, CINE_CONTACT = 5, CINE_BLAST = 6,
            CINE_END = 7, CINE_TAP = 10, CINE_TURN = 11;
    // CINE_SLASH：宿傩朝赫斩出去，kind 是斩到哪里停下的 x（被苍挡下）；CINE_CHANT：kind 是第几段追加咏唱
    // ADAPT 的 kind：0 法阵转了一格，1 宿傩学会了世界斩，2 魔虚罗适应了无限（攻击能打穿）
    // CHANT 的 kind：Attack.PURPLE（200% 茈，四段）、Attack.BLUE_FIELD（出力最大的苍，三段）、Attack.RED（最大输出的赫，三段）
    // CAST 的 value：0 普通，1 苍出力最大 / 赫最大输出 / 世界斩（咏唱版），2 茈 200%；世界斩第一次（束缚，免咏唱）是 3；
    // 4 瞬移；5 茈·最大输出（固定动作开始）
    // RED_FIRE / RED_BURST 的 kind：0 普通，1 最大输出，2 瞬移后的大赫；PURPLE_FIRE 的 kind：0 普通，1 200%，2 瞬移·茈（贴脸）
    // CHANT 的 kind 还有 Attack.PURPLE_MAX（茈·最大输出的十段：value 0–2 苍、3–5 赫、6–9 茈）

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
