package com.shinjuku.showdown.core;

/**
 * 一个 tick 的输入：摇杆（已过死区、已量化）和按键。
 * “按下/松开”是边沿，只在一个 tick 里为真；“按住”是持续状态。同一 tick 里先按后松也都会保留。
 */
public final class TickInput {
    public float moveX, moveY;     // -1..1，y 向上
    public boolean jump, melee, red, purple, domain;
    public boolean blueDown, blueUp, blueHeld;
    public boolean meleeHeld, redHeld, purpleHeld; // 按住（体术→苍拳、赫→最大输出、茈→念咒）

    public void clearEdges() {
        jump = melee = red = purple = domain = blueDown = blueUp = false;
    }

    public void clearAll() {
        clearEdges();
        blueHeld = meleeHeld = redHeld = purpleHeld = false;
        moveX = moveY = 0;
    }
}
