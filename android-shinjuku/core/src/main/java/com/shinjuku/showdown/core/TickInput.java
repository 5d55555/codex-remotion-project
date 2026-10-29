package com.shinjuku.showdown.core;

/**
 * 一个 tick 的输入：摇杆（已过死区、已量化）和按键。
 * “按下/松开”是边沿，只在一个 tick 里为真；“按住”是持续状态。同一 tick 里先按后松也都会保留。
 */
public final class TickInput {
    public float moveX, moveY;     // -1..1，y 向上
    public boolean jump, melee, red, purple;
    public boolean blueDown, blueUp, blueHeld;

    public void clearEdges() {
        jump = melee = red = purple = blueDown = blueUp = false;
    }

    public void clearAll() {
        clearEdges();
        blueHeld = false;
        moveX = moveY = 0;
    }
}
