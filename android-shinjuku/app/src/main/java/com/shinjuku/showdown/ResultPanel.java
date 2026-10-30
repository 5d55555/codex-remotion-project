package com.shinjuku.showdown;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 结算（测试模式以外）：宿傩打空“胜利”，五条悟打空“失败”；再来一局（同一难度）或返回选难度。截图测试也用它。 */
final class ResultPanel {
    private static final int WIN = 0xFFFFD36A, LOSE = 0xFFFF3B4E;

    private ResultPanel() {
    }

    static View build(Context ctx, boolean won, Difficulty d, View.OnClickListener again, View.OnClickListener back) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        int accent = won ? WIN : LOSE;

        FrameLayout root = new FrameLayout(ctx);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                won ? new int[]{0x99070614, 0xD9070614} : new int[]{0x99140306, 0xE0080104}));
        root.setClickable(true); // 盖住战斗画面：触摸不漏下去

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = text(ctx, won ? "胜利" : "失败", 72, won ? 0xFFFFF4D6 : 0xFFFFE0E3, true);
        title.setShadowLayer(18 * dp, 0, 0, accent);
        title.setLetterSpacing(0.3f);
        int glow = (int) (18 * dp); // 给光晕留地方
        title.setPadding(glow, glow / 2, glow, 0);
        col.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sub = text(ctx, d.title + " · " + d.tier, 15, 0xCCDDE2F5, false);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.bottomMargin = (int) (26 * dp);
        col.addView(sub, sl);

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(button(ctx, "再来一局", accent, true, again), buttonParams(dp));
        buttons.addView(button(ctx, "返回", accent, false, back), buttonParams(dp));
        col.addView(buttons, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return root;
    }

    private static LinearLayout.LayoutParams buttonParams(float dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams((int) (150 * dp), (int) (48 * dp));
        p.setMargins((int) (10 * dp), 0, (int) (10 * dp), 0);
        return p;
    }

    private static TextView button(Context ctx, String label, int accent, boolean primary, View.OnClickListener onClick) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        TextView b = text(ctx, label, 18, primary ? 0xFF1A0F02 : Color.WHITE, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(99 * dp);
        bg.setColor(primary ? accent : 0x66000000);
        bg.setStroke((int) Math.max(2, 1.6f * dp), primary ? accent : (accent & 0x00FFFFFF) | 0xAA000000);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(99 * dp);
        mask.setColor(Color.WHITE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), bg, mask));
        b.setLetterSpacing(0.15f);
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        return b;
    }

    private static TextView text(Context ctx, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
