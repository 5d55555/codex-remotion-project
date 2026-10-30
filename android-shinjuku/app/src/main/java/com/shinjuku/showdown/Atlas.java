package com.shinjuku.showdown;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

/**
 * 像素图集：启动时把所有像素部件（PixelArt）、带 1 像素描边的数字和图标、像素大字横幅打包成一张图。
 * GL 后端只上传一次；Canvas 后端直接从里面裁。所有图都是 1:1 的虚拟像素，没有缩放。
 */
final class Atlas {
    // 角色部件
    static final int GOJO_HEAD = 0, GOJO_TORSO = 1, GOJO_ARM = 2, GOJO_LEG = 3;
    static final int SUKUNA_HEAD = 4, SUKUNA_TORSO = 5, SUKUNA_ARM = 6, SUKUNA_LEG = 7;
    // 数字 0–9、斜杠
    static final int DIGIT0 = 8, DIGIT_SLASH = 18;
    // 图标
    static final int ICON_BACK = 19, ICON_NOTE = 20;
    // 像素大字（白字黑边，画的时候按颜色乘）
    static final int BIG_BLUE = 21, BIG_RED = 22, BIG_PURPLE = 23, BIG_BLACK_FLASH = 24, BIG_KO = 25, BIG_FIST = 26, BIG_VOID = 27,
            BIG_DISMANTLE = 28;
    // 宿傩的原身（四臂：arm2 是多出来的一对）、魔虚罗（前手是退魔之剑）、嵌合兽「顎吐」
    static final int TRUE_HEAD = 29, TRUE_TORSO = 30, TRUE_ARM = 31, TRUE_ARM2 = 32;
    static final int MAHO_HEAD = 33, MAHO_TORSO = 34, MAHO_ARM = 35, MAHO_SWORD = 36, MAHO_LEG = 37, AGITO = 38;
    static final int BIG_MAHORAGA = 39, BIG_TRUE_FORM = 40; // 「魔虚罗」「原身」
    static final int COUNT = 41;

    static final String[] BIG_TEXT = {"「苍」", "「赫」", "「茈」", "黑闪", "击倒", "「苍拳」", "「无量空处」", "「解」"};
    static final int[] BIG_SIZE = {28, 28, 28, 30, 15, 28, 28, 28};

    final Bitmap bitmap;
    final int[] x = new int[COUNT], y = new int[COUNT], w = new int[COUNT], h = new int[COUNT];
    final int[] px = new int[COUNT], py = new int[COUNT]; // 挂点

    private static final int PAD = 2;

    Atlas() {
        Bitmap[] parts = new Bitmap[COUNT];
        parts[GOJO_HEAD] = part(PixelArt.GOJO_HEAD, PixelArt.PAL_GOJO_KEYS, PixelArt.PAL_GOJO);
        parts[GOJO_TORSO] = part(PixelArt.GOJO_TORSO, PixelArt.PAL_GOJO_KEYS, PixelArt.PAL_GOJO);
        parts[GOJO_ARM] = part(PixelArt.GOJO_ARM, PixelArt.PAL_GOJO_KEYS, PixelArt.PAL_GOJO);
        parts[GOJO_LEG] = part(PixelArt.GOJO_LEG, PixelArt.PAL_GOJO_KEYS, PixelArt.PAL_GOJO);
        parts[SUKUNA_HEAD] = part(PixelArt.SUKUNA_HEAD, PixelArt.PAL_SUKUNA_KEYS, PixelArt.PAL_SUKUNA);
        parts[SUKUNA_TORSO] = part(PixelArt.SUKUNA_TORSO, PixelArt.PAL_SUKUNA_KEYS, PixelArt.PAL_SUKUNA);
        parts[SUKUNA_ARM] = part(PixelArt.SUKUNA_ARM, PixelArt.PAL_SUKUNA_KEYS, PixelArt.PAL_SUKUNA);
        parts[SUKUNA_LEG] = part(PixelArt.SUKUNA_LEG, PixelArt.PAL_SUKUNA_KEYS, PixelArt.PAL_SUKUNA);
        int[][] piv = {
                {PixelArt.GOJO_HEAD_PX, PixelArt.GOJO_HEAD_PY}, {PixelArt.GOJO_TORSO_PX, PixelArt.GOJO_TORSO_PY},
                {PixelArt.GOJO_ARM_PX, PixelArt.GOJO_ARM_PY}, {PixelArt.GOJO_LEG_PX, PixelArt.GOJO_LEG_PY},
                {PixelArt.SUKUNA_HEAD_PX, PixelArt.SUKUNA_HEAD_PY}, {PixelArt.SUKUNA_TORSO_PX, PixelArt.SUKUNA_TORSO_PY},
                {PixelArt.SUKUNA_ARM_PX, PixelArt.SUKUNA_ARM_PY}, {PixelArt.SUKUNA_LEG_PX, PixelArt.SUKUNA_LEG_PY},
        };
        for (int i = 0; i < piv.length; i++) {
            px[i] = piv[i][0];
            py[i] = piv[i][1];
        }
        extra(parts, TRUE_HEAD, PixelArt.SUKUNA_TRUE_HEAD, PixelArt.PAL_SUKUNA_TRUE_KEYS, PixelArt.PAL_SUKUNA_TRUE, PixelArt.SUKUNA_TRUE_HEAD_PX, PixelArt.SUKUNA_TRUE_HEAD_PY);
        extra(parts, TRUE_TORSO, PixelArt.SUKUNA_TRUE_TORSO, PixelArt.PAL_SUKUNA_TRUE_KEYS, PixelArt.PAL_SUKUNA_TRUE, PixelArt.SUKUNA_TRUE_TORSO_PX, PixelArt.SUKUNA_TRUE_TORSO_PY);
        extra(parts, TRUE_ARM, PixelArt.SUKUNA_TRUE_ARM, PixelArt.PAL_SUKUNA_TRUE_KEYS, PixelArt.PAL_SUKUNA_TRUE, PixelArt.SUKUNA_TRUE_ARM_PX, PixelArt.SUKUNA_TRUE_ARM_PY);
        extra(parts, TRUE_ARM2, PixelArt.SUKUNA_TRUE_ARM2, PixelArt.PAL_SUKUNA_TRUE_KEYS, PixelArt.PAL_SUKUNA_TRUE, PixelArt.SUKUNA_TRUE_ARM2_PX, PixelArt.SUKUNA_TRUE_ARM2_PY);
        extra(parts, MAHO_HEAD, PixelArt.MAHORAGA_HEAD, PixelArt.PAL_MAHORAGA_KEYS, PixelArt.PAL_MAHORAGA, PixelArt.MAHORAGA_HEAD_PX, PixelArt.MAHORAGA_HEAD_PY);
        extra(parts, MAHO_TORSO, PixelArt.MAHORAGA_TORSO, PixelArt.PAL_MAHORAGA_KEYS, PixelArt.PAL_MAHORAGA, PixelArt.MAHORAGA_TORSO_PX, PixelArt.MAHORAGA_TORSO_PY);
        extra(parts, MAHO_ARM, PixelArt.MAHORAGA_ARM, PixelArt.PAL_MAHORAGA_KEYS, PixelArt.PAL_MAHORAGA, PixelArt.MAHORAGA_ARM_PX, PixelArt.MAHORAGA_ARM_PY);
        extra(parts, MAHO_SWORD, PixelArt.MAHORAGA_SWORD, PixelArt.PAL_MAHORAGA_KEYS, PixelArt.PAL_MAHORAGA, PixelArt.MAHORAGA_SWORD_PX, PixelArt.MAHORAGA_SWORD_PY);
        extra(parts, MAHO_LEG, PixelArt.MAHORAGA_LEG, PixelArt.PAL_MAHORAGA_KEYS, PixelArt.PAL_MAHORAGA, PixelArt.MAHORAGA_LEG_PX, PixelArt.MAHORAGA_LEG_PY);
        extra(parts, AGITO, PixelArt.AGITO_BODY, PixelArt.PAL_AGITO_KEYS, PixelArt.PAL_AGITO, PixelArt.AGITO_BODY_PX, PixelArt.AGITO_BODY_PY);
        String[][] digits = {PixelArt.DIGIT_0, PixelArt.DIGIT_1, PixelArt.DIGIT_2, PixelArt.DIGIT_3, PixelArt.DIGIT_4,
                PixelArt.DIGIT_5, PixelArt.DIGIT_6, PixelArt.DIGIT_7, PixelArt.DIGIT_8, PixelArt.DIGIT_9, PixelArt.DIGIT_SLASH};
        for (int i = 0; i < digits.length; i++) parts[DIGIT0 + i] = outlined(part(digits[i], PixelArt.PAL_FONT_KEYS, PixelArt.PAL_FONT));
        parts[ICON_BACK] = outlined(part(PixelArt.ICON_BACK, PixelArt.PAL_FONT_KEYS, PixelArt.PAL_FONT));
        parts[ICON_NOTE] = outlined(part(PixelArt.ICON_NOTE, PixelArt.PAL_FONT_KEYS, PixelArt.PAL_FONT));
        // 大字描两圈边（2 像素），在亮的天空上也看得清
        for (int i = 0; i < BIG_TEXT.length; i++) parts[BIG_BLUE + i] = outlined(outlined(pixelText(BIG_TEXT[i], BIG_SIZE[i])));
        parts[BIG_MAHORAGA] = outlined(outlined(pixelText("「魔虚罗」", 28)));
        parts[BIG_TRUE_FORM] = outlined(outlined(pixelText("「原身」", 28)));

        // 简单的分行装箱
        int atlasW = 256, cx = PAD, cy = PAD, rowH = 0;
        for (int i = 0; i < COUNT; i++) {
            Bitmap b = parts[i];
            if (cx + b.getWidth() + PAD > atlasW) {
                cx = PAD;
                cy += rowH + PAD;
                rowH = 0;
            }
            x[i] = cx;
            y[i] = cy;
            w[i] = b.getWidth();
            h[i] = b.getHeight();
            cx += b.getWidth() + PAD;
            rowH = Math.max(rowH, b.getHeight());
        }
        int atlasH = 1;
        while (atlasH < cy + rowH + PAD) atlasH <<= 1;
        bitmap = Bitmap.createBitmap(atlasW, atlasH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        for (int i = 0; i < COUNT; i++) {
            c.drawBitmap(parts[i], x[i], y[i], null);
            parts[i].recycle();
        }
    }

    private void extra(Bitmap[] parts, int i, String[] rows, String keys, int[] pal, int pivotX, int pivotY) {
        parts[i] = part(rows, keys, pal);
        px[i] = pivotX;
        py[i] = pivotY;
    }

    /** 字符画 → 位图。 */
    static Bitmap part(String[] rows, String keys, int[] pal) {
        int h = rows.length, w = rows[0].length();
        int[] px = new int[w * h];
        for (int yy = 0; yy < h; yy++) {
            String r = rows[yy];
            for (int xx = 0; xx < w; xx++) {
                char ch = r.charAt(xx);
                px[yy * w + xx] = ch == '.' ? 0 : pal[keys.indexOf(ch)];
            }
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    /** 加 1 像素黑色描边（8 邻域），原来的像素不动。 */
    static Bitmap outlined(Bitmap src) {
        int w = src.getWidth() + 2, h = src.getHeight() + 2;
        int[] in = new int[src.getWidth() * src.getHeight()];
        src.getPixels(in, 0, src.getWidth(), 0, 0, src.getWidth(), src.getHeight());
        int sw = src.getWidth();
        int[] out = new int[w * h];
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int sx = xx - 1, sy = yy - 1;
                int v = (sx >= 0 && sy >= 0 && sx < sw && sy < src.getHeight()) ? in[sy * sw + sx] : 0;
                if ((v >>> 24) > 127) {
                    out[yy * w + xx] = v;
                    continue;
                }
                boolean edge = false;
                for (int dy = -1; dy <= 1 && !edge; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = sx + dx, ny = sy + dy;
                        if (nx >= 0 && ny >= 0 && nx < sw && ny < src.getHeight() && (in[ny * sw + nx] >>> 24) > 127) {
                            edge = true;
                            break;
                        }
                    }
                }
                out[yy * w + xx] = edge ? 0xFF05040A : 0;
            }
        }
        src.recycle();
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(out, 0, w, 0, 0, w, h);
        return b;
    }

    /** 不抗锯齿地画一行字（像素字），裁到刚好的大小；像素只有全白和全透明两种。 */
    static Bitmap pixelText(String s, int size) {
        Paint p = new Paint();
        p.setAntiAlias(false);
        p.setSubpixelText(false);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(size);
        p.setColor(0xFFFFFFFF);
        Rect r = new Rect();
        p.getTextBounds(s, 0, s.length(), r);
        int w = Math.max(1, r.width() + 2), h = Math.max(1, r.height() + 2);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawText(s, 1 - r.left, 1 - r.top, p);
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] = (px[i] >>> 24) > 100 ? 0xFFFFFFFF : 0;
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }
}
