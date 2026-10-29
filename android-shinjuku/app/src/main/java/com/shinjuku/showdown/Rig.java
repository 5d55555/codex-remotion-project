package com.shinjuku.showdown;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Q 版角色的骨骼：腿、手臂、身体、头六个部件，按固定层级和关节摆放。
 * 坐标以脚底中心为原点、y 向下，身高约 100 个单位；面朝右，face=-1 时水平翻转。
 * 部件怎么画由子类决定：Chibi 直接画矢量路径，ChibiSprite 画预先烘焙好的位图。
 */
abstract class Rig {

    /** 一帧的姿势。由动画每帧填写，复用同一个对象。 */
    static final class Pose {
        float face = 1;          // 1 面朝右，-1 面朝左
        float lean;              // 上身前倾（度，绕胯）
        float head;              // 头的转动（度）
        float armFront, armBack; // 手臂相对下垂的角度（度，正值往前抬）
        float legFront, legBack; // 腿相对竖直的角度（度，正值往前迈）
        float squash = 1;        // 竖向缩放：<1 压扁（落地），>1 拉长（起跳）
        float bob;               // 上下浮动（单位）
        float spin;              // 整个人绕身体中心转（度）：被击飞翻滚、倒地
        float hurt;              // 受击闪白 0..1
        float glow;              // 前手发光 0..1（施术）
        int glowColor = 0xFF6FC3FF;

        void reset() {
            lean = head = armFront = armBack = legFront = legBack = bob = spin = hurt = glow = 0;
            squash = 1;
        }
    }

    static final int LEG_BACK = 0, LEG_FRONT = 1, ARM_BACK = 2, ARM_FRONT = 3, TORSO = 4, HEAD = 5, PARTS = 6;

    /** 每个部件以自己关节为原点的包围盒（左、上、右、下，单位），烘焙位图时用。 */
    static final float[][] BOUNDS = {
            {-8, -2, 11, 25}, {-8, -2, 11, 25},
            {-7, -4, 7, 22}, {-7, -4, 7, 22},
            {-18, -4, 18, 32},
            {-36, -46, 36, 22},
    };

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 在当前坐标系（原点在该部件的关节）里画一个部件。 */
    abstract void part(Canvas c, int part, Pose p);

    /** 叠在整个人上的受击闪白（矢量版用；位图版在 part 里用颜色滤镜做）。 */
    void hurtOverlay(Canvas c, Pose p) {
    }

    /** 画在 (x, y)：脚底中心；scale：每单位多少像素。 */
    final void draw(Canvas c, float x, float y, float scale, Pose p) {
        c.save();
        c.translate(x, y - p.bob * scale);
        c.scale(scale * p.face, scale * p.squash);
        if (p.spin != 0) c.rotate(p.spin, 0, -50);

        joint(c, LEG_BACK, -5, -22, p.legBack, p);
        c.save();
        c.rotate(p.lean, 0, -22);
        joint(c, ARM_BACK, -12, -48, p.armBack, p);
        c.save();
        c.translate(0, -50);
        part(c, TORSO, p);
        c.restore();
        joint(c, HEAD, 0, -72, -p.head, p);
        joint(c, ARM_FRONT, 12, -48, p.armFront, p);
        if (p.glow > 0) {
            c.save();
            c.translate(12, -48);
            c.rotate(-p.armFront);
            glowPaint.setColor((((int) (p.glow * 170)) << 24) | (p.glowColor & 0x00FFFFFF));
            c.drawCircle(0, 20, 7 + 5 * p.glow, glowPaint);
            glowPaint.setColor((((int) (p.glow * 255)) << 24) | 0xFFFFFF);
            c.drawCircle(0, 20, 3.5f, glowPaint);
            c.restore();
        }
        c.restore();
        joint(c, LEG_FRONT, 5, -22, p.legFront, p);
        hurtOverlay(c, p);
        c.restore();
    }

    private void joint(Canvas c, int part, float jx, float jy, float deg, Pose p) {
        c.save();
        c.translate(jx, jy);
        c.rotate(-deg);
        part(c, part, p);
        c.restore();
    }
}
