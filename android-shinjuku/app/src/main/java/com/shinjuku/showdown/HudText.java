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
    static final int BTN_MELEE = 4, BTN_JUMP = 5, BTN_BLUE = 6, BTN_RED = 7, BTN_PURPLE = 8, BTN_DOMAIN = 9, BTN_DODGE = 10; // 和 Controls 的键顺序一致
    static final int SUB_BLUE = 11, SUB_BLUE_MAX = 12, SUB_RED = 13, SUB_PURPLE = 14, SUB_HOLLOW = 15;
    static final int SUB_RED_MAX = 16, SUB_PURPLE_200 = 17, SUB_FIST = 18, SUB_DOMAIN = 19;
    static final int CHANT0 = 20;   // 茈的四段咒词：九纲、偏光、乌与声明、表里之间
    static final int DISMANTLE = 24; // 宿傩的“解”
    static final int AMP = 25, RCT = 26, ADAPTED = 27; // 宿傩头上的小字：领域展延、反转术式、适应完成
    static final int WORLD_CHANT0 = 28;              // 世界斩的三段咏唱：龙鳞、反发、番之流星
    static final int SUB_WORLD = 31, SUB_WORLD_BOUND = 32;
    static final int BLUE_CHANT0 = 33;               // 苍的三段咒词：位相、黄昏、智慧之瞳
    static final int RED_CHANT0 = 36;                // 赫的三段咒词：位相、波罗蜜、光之柱
    static final int MAHORAGA = 39, SUMMON_CHANT = 40, SUB_SUMMON = 41, AGITO = 42, TRUE_FORM = 43, SUB_TRUE_FORM = 44, MAHO_ADAPTED = 45;
    static final int SUB_PURPLE_MAX = 46, SUB_TP_PURPLE = 47, PIERCING = 48; // 茈·最大输出、瞬移·茈的横幅小字；宿傩的「穿血」
    static final int SPACE_SLASH = 49, SUB_SPACE_SLASH = 50, EXHAUSTED = 51; // 宿傩的空间斩：头上的字、横幅小字；打空后的「力竭」
    /**
     * 台词（Luna 查证的原作台词，见 notes/招式名称考证.md；头两句是用户指定的）。
     * 五条悟：0 挑战者（第 224 话）……9 无量空处后（第 229 话）、10 第 235 话的大范围茈。
     * 宿傩：0 凡夫（第 230 话）……7 召出魔虚罗（第 230 话）、8 赢了以后（第 236 话）。
     */
    static final String[] GOJO_QUOTES = {"你才是挑战者", "哎呀，真险真险", "总会有办法的", "不错嘛，来劲了", "我开始觉得有趣了",
            "接下来我要认真了", "还能继续吧", "倒数吧，直到你笑不出来", "我可是正义的伙伴", "至少先打穿心脏！", "我赢了"};
    static final String[] SUKUNA_QUOTES = {"你只不过是没有诞生在我时代的凡夫罢了", "真是的，咒术师何时都麻烦", "真是个好时代啊",
            "连解闷都不够", "自豪吧，你很强", "好啊，再让我看看", "只是比别的鱼鲜活些", "接下来边切碎你，边适应无下限", "五条悟，你值得我铭记一生"};
    static final int QUOTE_G0 = 52, QUOTE_S0 = QUOTE_G0 + GOJO_QUOTES.length;
    static final int COUNT = QUOTE_S0 + SUKUNA_QUOTES.length;

    final Bitmap bitmap;
    final float dp;
    final int[] x = new int[COUNT], y = new int[COUNT], w = new int[COUNT], h = new int[COUNT];
    final int[] baseline = new int[COUNT]; // 字的基线在图块里的高度

    HudText(float dp, Difficulty d) {
        this.dp = dp;
        String[] text = {"五条悟", "两面宿傩", d.title, d.tier, "体术", "跳", "苍", "赫", "茈", "领域", "闪避",
                "术式顺转", "术式顺转 · 出力最大", "术式反转", "虚式", "虚式 · 无指向",
                "术式反转 · 最大输出", "虚式 · 200%", "术式顺转 · 苍拳", "领域展开",
                "九纲", "偏光", "乌与声明", "表里之间", "解",
                "领域展延", "反转术式", "适应完成",
                "龙鳞", "反发", "番之流星",
                "斩断世界的斩击", "斩断世界的斩击 · 束缚",
                "位相", "黄昏", "智慧之瞳", "位相", "波罗蜜", "光之柱",
                "魔虚罗", "布瑠部由良由良", "八握剑异戒神将 · 魔虚罗", "十种影法术 · 顎吐", "原身", "两面宿傩 · 原身", "适应无限",
                "虚式 · 最大输出", "瞬移 · 虚式", "穿血", "空间斩", "斩断空间的斩击", "力竭"};
        float[] size = {13, 13, 16, 10, 15, 15, 22, 22, 22, 14, 14, 15, 15, 15, 15, 15, 15, 15, 15, 15, 17, 17, 17, 17, 16,
                13, 13, 14, 17, 17, 17, 15, 15, 17, 17, 17, 17, 17, 17,
                11, 17, 15, 13, 14, 15, 14,
                15, 15, 14, 17, 15, 15};
        if (text.length != QUOTE_G0 || size.length != QUOTE_G0) throw new IllegalStateException("HudText 的字和字号要一一对应");
        String[] base = text;
        float[] baseSize = size;
        text = new String[COUNT];
        size = new float[COUNT];
        System.arraycopy(base, 0, text, 0, base.length);
        System.arraycopy(baseSize, 0, size, 0, baseSize.length);
        for (int i = 0; i < GOJO_QUOTES.length; i++) text[QUOTE_G0 + i] = GOJO_QUOTES[i];
        for (int i = 0; i < SUKUNA_QUOTES.length; i++) text[QUOTE_S0 + i] = SUKUNA_QUOTES[i];
        for (int i = QUOTE_G0; i < COUNT; i++) size[i] = 14;
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
