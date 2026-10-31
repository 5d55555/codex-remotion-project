package com.shinjuku.showdown.core;

/** 宿傩这一 tick 想做什么（AI 填，或测试里手动填）。能不能做由 World 的动作执行器决定。 */
public final class SukunaIntent {
    public float moveX;
    public boolean jump, melee, dismantle;
    public boolean amp, ampOff, world, rct; // 开 / 关领域展延、世界斩、反转术式
    public boolean agito, summon, transform; // 顎吐、召唤魔虚罗、变原身
    public boolean space;                    // 空间斩
    public boolean shrine;                   // 领域展开「伏魔御厨子」（结印）

    void clear() {
        moveX = 0;
        jump = melee = dismantle = amp = ampOff = world = rct = agito = summon = transform = space = shrine = false;
    }
}
