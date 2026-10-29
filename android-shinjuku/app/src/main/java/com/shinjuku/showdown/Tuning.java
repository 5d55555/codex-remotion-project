package com.shinjuku.showdown;

/**
 * 战斗数值。用户给定的写明“定”，其余是可调的占位（标“占位”）。
 */
final class Tuning {
    private Tuning() {
    }

    // 体术（定）
    static final float MELEE_DAMAGE = 50f;
    static final float BLACK_FLASH_CHANCE = 0.15f;   // 平均触发率（伪随机，见 Prd）
    static final float BLACK_FLASH_DAMAGE = 350f;

    // 苍（定）：吸力；投掷最远不超过战场宽度的 30%；离中心越近伤害越高，最高每秒 100
    static final float BLUE_MAX_THROW_FRACTION = 0.30f;
    static final float BLUE_MAX_DPS = 100f;
    // 苍：长按蓄满（定）——波及范围和吸力都翻倍，但有前摇
    static final float BLUE_CHARGED_RANGE_MULT = 2f;
    static final float BLUE_CHARGED_PULL_MULT = 2f;

    // 赫（定）：斥力击飞，直接 150 伤害
    static final float RED_DAMAGE = 150f;

    // 以下为占位，可调
    static final float MELEE_COOLDOWN_S = 0.35f;     // 占位
    static final float BLUE_COOLDOWN_S = 4f;         // 占位
    static final float RED_COOLDOWN_S = 5f;          // 占位
    static final float BLUE_DURATION_S = 2.5f;       // 占位：苍持续多久
    static final float BLUE_CHARGE_WINDUP_S = 0.6f;  // 占位：长按蓄满的前摇
}
