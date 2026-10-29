package com.shinjuku.showdown;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Q 版角色：代码绘制，所有路径在构造时建好，每帧零分配。
 * 坐标以脚底中心为原点、y 向下，身高约 100 个单位（头大身小）；面朝右，face=-1 时水平翻转。
 * 动作由 Pose 的几个角度驱动：身体前倾、头、前后手臂、前后腿、跳跃时的压扁拉伸。
 */
final class Chibi {

    /** 一帧的姿势。由动画状态机每帧填写，复用同一个对象。 */
    static final class Pose {
        float face = 1;          // 1 面朝右，-1 面朝左
        float lean;              // 身体前倾（度）
        float head;              // 头的转动（度）
        float armFront, armBack; // 手臂相对下垂的角度（度，正值往前抬）
        float legFront, legBack; // 腿相对竖直的角度（度，正值往前迈）
        float squash = 1;        // 竖向缩放：<1 压扁（落地），>1 拉长（起跳）
        float bob;               // 上下浮动（单位）
        float hurt;              // 受击闪白 0..1
        float glow;              // 手上发光 0..1（施术）
        int glowColor = 0xFF6FC3FF;
    }

    static final int GOJO = 0, SUKUNA = 1;
    private static final float OUTLINE = 1.5f;

    private final int who;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hair = new Path(), hairShade = new Path(), torso = new Path(), collar = new Path();
    private final Path arm = new Path(), leg = new Path(), shoe = new Path();
    private final RectF face = new RectF(), eyeL = new RectF(), eyeR = new RectF();

    private final int hairColor, hairShadeColor, skin, cloth, clothHi, eyeColor, accent;

    Chibi(int who) {
        this.who = who;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(0xFF0B0B14);
        line.setStrokeWidth(OUTLINE);
        if (who == GOJO) {
            hairColor = 0xFFF4F6FB;
            hairShadeColor = 0xFFC9D2E6;
            skin = 0xFFF8E3D4;
            cloth = 0xFF17181F;
            clothHi = 0xFF2C2E3A;
            eyeColor = 0xFF5ED0FF;
            accent = 0xFF6FC3FF;
        } else {
            hairColor = 0xFF1C2030;
            hairShadeColor = 0xFF2E3550;
            skin = 0xFFF1D7C6;
            cloth = 0xFF2A2230;
            clothHi = 0xFF453849;
            eyeColor = 0xFFFF3B4E;
            accent = 0xFFB0202E;
        }
        buildPaths();
    }

    private void buildPaths() {
        // 头发：头顶往上、往外支棱的尖刺；前面的刘海在眼睛上方收住（锯齿发梢），两侧鬓发垂到脸颊外侧
        // （以头的中心为原点；眼睛在 y=0..7，所以刘海最低到 y≈-3）
        hair.reset();
        float[][] crown = who == GOJO
                ? new float[][]{{-23, 10}, {-27, -2}, {-34, -8}, {-24, -13}, {-30, -27}, {-16, -24}, {-14, -40}, {-4, -28}, {4, -44}, {10, -27}, {22, -38}, {20, -22}, {34, -24}, {26, -11}, {34, -4}, {24, -1}, {23, 10}}
                : new float[][]{{-23, 10}, {-26, -2}, {-32, -10}, {-22, -15}, {-26, -29}, {-12, -25}, {-8, -38}, {0, -27}, {8, -40}, {12, -26}, {26, -34}, {22, -19}, {32, -16}, {24, -6}, {29, 2}, {23, 10}};
        hair.moveTo(crown[0][0], crown[0][1]);
        for (int i = 1; i < crown.length; i++) hair.lineTo(crown[i][0], crown[i][1]);
        // 刘海：从右鬓往左，一排朝下的尖
        float[][] bangs = {{19, 8}, {17, -6}, {13, -2}, {9, -9}, {5, -3}, {0, -10}, {-4, -3}, {-9, -9}, {-13, -2}, {-17, -6}, {-19, 8}};
        for (float[] b2 : bangs) hair.lineTo(b2[0], b2[1]);
        hair.close();
        // 头发暗部：头顶靠后的一块
        hairShade.reset();
        hairShade.moveTo(-20, -12);
        hairShade.quadTo(0, -24, 22, -10);
        hairShade.quadTo(14, -6, 9, -9);
        hairShade.lineTo(5, -3);
        hairShade.lineTo(0, -10);
        hairShade.lineTo(-4, -3);
        hairShade.lineTo(-9, -9);
        hairShade.quadTo(-16, -6, -20, -12);
        hairShade.close();
        face.set(-19, -14, 19, 20);
        eyeL.set(-12, 0, -4, 7);
        eyeR.set(4, 0, 12, 7);

        // 身体：上窄下宽的短衣（以脖子为原点，向下 30）
        torso.reset();
        torso.moveTo(-11, 0);
        torso.lineTo(11, 0);
        torso.quadTo(15, 14, 16, 30);
        torso.lineTo(-16, 30);
        torso.quadTo(-15, 14, -11, 0);
        torso.close();
        collar.reset();
        collar.moveTo(-11, -2);
        collar.lineTo(11, -2);
        collar.lineTo(9, 6);
        collar.lineTo(-9, 6);
        collar.close();

        // 手臂、腿：圆头的短胶囊（以关节为原点向下）
        arm.reset();
        arm.addRoundRect(new RectF(-4.5f, -2, 4.5f, 20), 4.5f, 4.5f, Path.Direction.CW);
        leg.reset();
        leg.addRoundRect(new RectF(-5.5f, 0, 5.5f, 20), 4, 4, Path.Direction.CW);
        shoe.reset();
        shoe.addRoundRect(new RectF(-6, 16, 9, 23), 3.5f, 3.5f, Path.Direction.CW);
    }

    /** 画在 (x, y)：脚底中心；scale：每单位多少像素。 */
    void draw(Canvas c, float x, float y, float scale, Pose p) {
        c.save();
        c.translate(x, y - p.bob * scale);
        c.scale(scale * p.face, scale * p.squash);
        line.setStrokeWidth(OUTLINE);

        // 后腿、后手臂（颜色稍暗）
        limb(c, leg, -5, -22, p.legBack, cloth, true);
        shoeAt(c, -5, -22, p.legBack);
        c.save();
        c.rotate(p.lean, 0, -22);
        limb(c, arm, -12, -48, p.armBack, cloth, true);

        // 身体
        c.save();
        c.translate(0, -50);
        fill.setColor(cloth);
        c.drawPath(torso, fill);
        c.drawPath(torso, line);
        fill.setColor(clothHi);
        c.drawRect(-2, 6, 2, 28, fill); // 衣襟
        fill.setColor(who == GOJO ? cloth : accent);
        c.drawPath(collar, fill);
        c.drawPath(collar, line);
        c.restore();

        // 头
        c.save();
        c.translate(0, -72);
        c.rotate(p.head);
        fill.setColor(skin);
        c.drawOval(face, fill);
        c.drawOval(face, line);
        drawEyes(c);
        fill.setColor(hairColor);
        c.drawPath(hair, fill);
        fill.setColor(hairShadeColor);
        c.drawPath(hairShade, fill);
        c.drawPath(hair, line);
        c.restore();

        // 前手臂（施术时手上发光）
        limb(c, arm, 12, -48, p.armFront, cloth, false);
        if (p.glow > 0) {
            c.save();
            c.rotate(-p.armFront, 12, -48);
            glowPaint.setColor((((int) (p.glow * 170)) << 24) | (p.glowColor & 0x00FFFFFF));
            c.drawCircle(12, -28, 7 + 5 * p.glow, glowPaint);
            glowPaint.setColor((((int) (p.glow * 255)) << 24) | 0xFFFFFF);
            c.drawCircle(12, -28, 3.5f, glowPaint);
            c.restore();
        }
        c.restore();

        // 前腿
        limb(c, leg, 5, -22, p.legFront, cloth, false);
        shoeAt(c, 5, -22, p.legFront);

        // 受击闪白：整个人叠一层白
        if (p.hurt > 0) {
            fill.setColor((((int) (p.hurt * 150)) << 24) | 0xFFFFFF);
            c.drawOval(-26, -110, 26, 2, fill);
        }
        c.restore();
    }

    private void drawEyes(Canvas c) {
        fill.setColor(0xFFFFFFFF);
        c.drawOval(eyeL, fill);
        c.drawOval(eyeR, fill);
        float ir = who == GOJO ? 3.3f : 2.9f; // 六眼：虹膜大一些、更亮
        fill.setColor(eyeColor);
        c.drawCircle(-7.8f, 3.8f, ir, fill);
        c.drawCircle(8.2f, 3.8f, ir, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(-6.7f, 2.7f, 1.2f, fill);
        c.drawCircle(9.3f, 2.7f, 1.2f, fill);
        c.drawCircle(-8.8f, 5.2f, 0.6f, fill);
        c.drawCircle(7.2f, 5.2f, 0.6f, fill);
        if (who == SUKUNA) {
            // 宿傩的咒纹：眼下各两道短纹
            line.setColor(0xFF161018);
            line.setStrokeWidth(1.0f);
            c.drawLine(-11.5f, 9.5f, -5.5f, 9.5f, line);
            c.drawLine(-10.5f, 11.8f, -6.5f, 11.8f, line);
            c.drawLine(5.5f, 9.5f, 11.5f, 9.5f, line);
            c.drawLine(6.5f, 11.8f, 10.5f, 11.8f, line);
            line.setColor(0xFF0B0B14);
        }
        // 嘴：五条悟嘴角微微上扬
        line.setStrokeWidth(1.2f);
        if (who == GOJO) {
            c.drawLine(-3, 14.5f, 2, 14.5f, line);
            c.drawLine(2, 14.5f, 4, 13.3f, line);
        } else {
            c.drawLine(-3, 14.5f, 3, 14.5f, line);
        }
        line.setStrokeWidth(OUTLINE);
    }

    private void limb(Canvas c, Path shape, float jx, float jy, float deg, int color, boolean back) {
        c.save();
        c.translate(jx, jy);
        c.rotate(-deg);
        fill.setColor(back ? darker(color) : color);
        c.drawPath(shape, fill);
        c.drawPath(shape, line);
        c.restore();
    }

    private void shoeAt(Canvas c, float jx, float jy, float deg) {
        c.save();
        c.translate(jx, jy);
        c.rotate(-deg);
        fill.setColor(0xFF07070B);
        c.drawPath(shoe, fill);
        c.restore();
    }

    private static int darker(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return 0xFF000000 | ((r * 3 / 4) << 16) | ((g * 3 / 4) << 8) | (b * 3 / 4);
    }
}
