package com.shinjuku.showdown.core;

/**
 * 一个 tick 的输入：摇杆（已过死区、已量化）和按键。
 * “按下/松开”是边沿，只在一个 tick 里为真；“按住”是持续状态。同一 tick 里先按后松也都会保留。
 */
public final class TickInput {
    public float moveX, moveY;     // -1..1，y 向上
    public boolean jump, melee, red, purple, domain, dodge;
    public boolean blueDown, blueUp, blueHeld;
    public boolean redHeld, purpleHeld;  // 按住：赫、茈念咒
    public boolean jumpHeld, dodgeHeld;  // 按住跳跃时点体术是苍拳；按住闪避时点苍、点赫是瞬移

    public void clearEdges() {
        jump = melee = red = purple = domain = dodge = blueDown = blueUp = false;
    }

    public void clearAll() {
        clearEdges();
        blueHeld = redHeld = purpleHeld = jumpHeld = dodgeHeld = false;
        moveX = moveY = 0;
    }
}
