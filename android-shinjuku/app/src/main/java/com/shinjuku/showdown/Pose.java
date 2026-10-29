package com.shinjuku.showdown;

/** 一帧的姿势：由画面按角色状态每帧填写，复用同一个对象。角度单位是度。 */
final class Pose {
    float face = 1;          // 1 面朝右，-1 面朝左
    float lean;              // 上身前倾（度；像素画里换算成头和身体的整数像素偏移）
    float armFront, armBack; // 手臂相对下垂的角度（正值往前抬）
    float legFront, legBack; // 腿相对竖直的角度（正值往前迈）
    float bob;               // 整个人往上抬几个像素
    float spin;              // 整个人绕身体中心转（被击飞翻滚、倒地）
    float hurt;              // 受击闪白 0..1

    void reset() {
        lean = armFront = armBack = legFront = legBack = bob = spin = hurt = 0;
    }
}
