package com.shinjuku.showdown;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * 屏幕分辨率的文字图集（名字、难度、按键字、招式横幅的小字）：中文在低分辨率下看不清，这些字按屏幕像素画，
 * 白字加深色描边，用的时候按颜色乘。尺寸或密度变了才重建。
 */
final class HudText {
    static final int GOJO = 0, SUKUNA = 1, DIFF_TITLE = 2, DIFF_TIER = 3;
    static final int BTN_MELEE = 4, BTN_JUMP = 5, BTN_BLUE = 6, BTN_RED = 7, BTN_PURPLE = 8, BTN_DOMAIN = 9; // 和 Controls 的键顺序一致
    static final int SUB_BLUE = 10, SUB_BLUE_MAX = 11, SUB_RED = 12, SUB_PURPLE = 13, SUB_HOLLOW = 14;
    static final int SUB_RED_MAX = 15, SUB_PURPLE_200 = 16, SUB_FIST = 17, SUB_DOMAIN = 18;
    static final int CHANT0 = 19;   // 四段咒词：九纲、偏光、乌与声明、表里之间
    static final int DISMANTLE = 23; // 宿傩的“解”
    static final int COUNT = 24;

    final Bitmap bitmap;
    final float dp;
    final int[] x = new int[COUNT], y = new int[COUNT], w = new int[COUNT], h = new int[COUNT];
    final int[] baseline = new int[COUNT]; // 字的基线在图块里的高度

    HudText(float dp, Difficulty d) {
        this.dp = dp;
        String[] text = {"五条悟", "两面宿傩", d.title, d.tier, "体术", "跳", "苍", "赫", "茈", "领域",
                "术式顺转", "术式顺转 · 出力最大", "术式反转", "虚式", "虚式 · 无指向",
                "术式反转 · 最大输出", "虚式 · 200%", "术式顺转 · 苍拳", "领域展开",
                "九纲", "偏光", "乌与声明", "表里之间", "解"};
        float[] size = {13, 13, 16, 10, 15, 15, 22, 22, 22, 14, 15, 15, 15, 15, 15, 15, 15, 15, 15, 17, 17, 17, 17, 16};
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setTypeface(Typeface.DEFAULT_BOLD);
        fill.setColor(0xFFFFFFFF);
        Paint stroke = new Paint(fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xE6000000);
        int pad = (int) Math.ceil(3 * dp) + 2;
        int atlasW = (int) Math.ceil(512 * dp / 2.75f) + 64, cx = 0, cy = 0, rowH = 0;
        int[] tw = new int[COUNT], th = new int[COUNT];
        Paint.FontMetricsInt fm = new Paint.FontMetricsInt();
        for (int i = 0; i < COUNT; i++) {
            fill.setTextSize(size[i] * dp);
            fill.getFontMetricsInt(fm);
            tw[i] = (int) Math.ceil(fill.measureText(text[i])) + pad * 2;
            th[i] = fm.descent - fm.ascent + pad * 2;
            baseline[i] = pad - fm.ascent;
            atlasW = Math.max(atlasW, tw[i]);
        }
        for (int i = 0; i < COUNT; i++) {
            if (cx + tw[i] > atlasW) {
                cx = 0;
                cy += rowH;
                rowH = 0;
            }
            x[i] = cx;
            y[i] = cy;
            w[i] = tw[i];
            h[i] = th[i];
            cx += tw[i];
            rowH = Math.max(rowH, th[i]);
        }
        bitmap = Bitmap.createBitmap(atlasW, Math.max(1, cy + rowH), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        for (int i = 0; i < COUNT; i++) {
            fill.setTextSize(size[i] * dp);
            stroke.setTextSize(size[i] * dp);
            stroke.setStrokeWidth(size[i] * dp * 0.2f);
            float bx = x[i] + pad, by = y[i] + baseline[i];
            c.drawText(text[i], bx, by, stroke);
            c.drawText(text[i], bx, by, fill);
        }
    }
}
