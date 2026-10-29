package com.shinjuku.showdown.core;

/** 宿傩这一 tick 想做什么（AI 填，或测试里手动填）。能不能做由 World 的动作执行器决定。 */
public final class SukunaIntent {
    public float moveX;
    public boolean jump, melee, dismantle;

    void clear() {
        moveX = 0;
        jump = melee = dismantle = false;
    }
}
