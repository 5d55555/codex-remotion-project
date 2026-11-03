package com.pocketcodex.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;

/** 统一的小部件（按 Astra 的规格：卡片圆角 16dp、边框 1dp、正文 16sp、说明 14sp……）。 */
final class Ui {
    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static int color(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }

    static TextView text(Context c, CharSequence s, float sp, int colorRes) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(c, colorRes));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static TextView title(Context c, CharSequence s) {
        TextView t = text(c, s, 24, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView body(Context c, CharSequence s) {
        return text(c, s, 16, R.color.text);
    }

    static TextView caption(Context c, CharSequence s) {
        return text(c, s, 14, R.color.text2);
    }

    static TextView small(Context c, CharSequence s) {
        return text(c, s, 12, R.color.text2);
    }

    static TextView mono(Context c, CharSequence s) {
        TextView t = text(c, s, 13, R.color.text);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    static TextView section(Context c, CharSequence s) {
        TextView t = text(c, s, 13, R.color.text2);
        t.setPadding(dp(c, 20), dp(c, 24), dp(c, 20), dp(c, 6));
        return t;
    }

    /** 圆角背景（填充色 + 可选 1dp 边框）。 */
    static GradientDrawable round(Context c, int fillRes, int strokeRes, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color(c, fillRes));
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeRes != 0) d.setStroke(dp(c, 1), color(c, strokeRes));
        return d;
    }

    /** 圆形底。 */
    static GradientDrawable circle(Context c, int fillRes) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color(c, fillRes));
        return d;
    }

    /** 按下时的水波纹（加在前景上，不改背景）。 */
    static void ripple(View v, boolean borderless) {
        TypedValue tv = new TypedValue();
        v.getContext().getTheme().resolveAttribute(borderless ? android.R.attr.selectableItemBackgroundBorderless : android.R.attr.selectableItemBackground, tv, true);
        v.setForeground(androidx.core.content.ContextCompat.getDrawable(v.getContext(), tv.resourceId));
        v.setClickable(true);
    }

    /** 阴影（圆角按背景的轮廓走）。 */
    static void elevate(View v, float dp) {
        v.setElevation(dp(v.getContext(), dp));
        v.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            int sh = color(v.getContext(), R.color.shadow);
            v.setOutlineAmbientShadowColor(sh);
            v.setOutlineSpotShadowColor(sh);
        }
    }

    /** ChatGPT 风格的圆形按钮：白底细边 + 轻阴影，中间一个线条图标。 */
    static ImageView roundButton(Context c, int icon, String desc, View.OnClickListener l) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(color(c, R.color.text)));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = dp(c, 12);
        b.setPadding(p, p, p, p);
        GradientDrawable bg = circle(c, R.color.card);
        bg.setStroke(Math.max(1, dp(c, 1) / 2), color(c, R.color.border));
        b.setBackground(bg);
        elevate(b, 1.5f);
        ripple(b, true);
        b.setContentDescription(desc);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 46), dp(c, 46)));
        return b;
    }

    /** 无底色的图标按钮（输入框里的 +、麦克风）。 */
    static ImageView plainIcon(Context c, int icon, int tintRes, int sizeDp, String desc, View.OnClickListener l) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(color(c, tintRes)));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setContentDescription(desc);
        b.setOnClickListener(l);
        ripple(b, true);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return b;
    }

    static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** 卡片：surface 底、outline 边、圆角 16dp、内边距 16dp。 */
    static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(round(c, R.color.card, R.color.border, 16));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    static ImageView icon(Context c, int res, int tintRes, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setImageTintList(ColorStateList.valueOf(color(c, tintRes)));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static MaterialButton primary(Context c, CharSequence s, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    static MaterialButton outlined(Context c, CharSequence s, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(s);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 48));
        b.setTextColor(enabledColors(c, R.color.primary));
        b.setStrokeColor(ColorStateList.valueOf(color(c, R.color.border)));
        b.setOnClickListener(l);
        return b;
    }

    /** 可用时 colorRes，禁用时淡色（不要看起来能点）。 */
    static ColorStateList enabledColors(Context c, int colorRes) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{(color(c, R.color.text2) & 0x00FFFFFF) | 0x61000000, color(c, colorRes)});
    }

    static MaterialButton textButton(Context c, CharSequence s, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.borderlessButtonStyle);
        b.setText(s);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 48));
        b.setTextColor(color(c, R.color.primary));
        b.setOnClickListener(l);
        return b;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams matchWrap(Context c, int topDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weight1() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    static View space(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        return v;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(color(c, R.color.border));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1) / 2 + 0)));
        return v;
    }

    /** 小标签（如“已验证版本”“推荐”）。 */
    static TextView chip(Context c, CharSequence s, int fillRes, int textRes) {
        TextView t = text(c, s, 12, textRes);
        t.setBackground(round(c, fillRes, 0, 8));
        t.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        return t;
    }

    /** 错误/警告卡片：图标 + 标题 + 说明。 */
    static LinearLayout alert(Context c, boolean error, CharSequence title, CharSequence msg) {
        return alert(c, error ? 2 : 1, title, msg);
    }

    /** level：0 成功（主色浅底）/ 1 警告 / 2 错误。 */
    static LinearLayout alert(Context c, int level, CharSequence title, CharSequence msg) {
        LinearLayout card = vbox(c);
        card.setBackground(round(c, level == 2 ? R.color.error_soft : level == 1 ? R.color.warning_soft : R.color.primary_soft, 0, 12));
        int p = dp(c, 16);
        card.setPadding(p, p, p, p);
        LinearLayout row = hbox(c);
        row.addView(icon(c, level == 2 ? R.drawable.ic_warning : level == 1 ? R.drawable.ic_info : R.drawable.ic_check,
                level == 2 ? R.color.error : level == 1 ? R.color.warning : R.color.primary, 20));
        TextView t = text(c, title, 16, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = weight1();
        lp.leftMargin = dp(c, 8);
        row.addView(t, lp);
        card.addView(row);
        if (msg != null && msg.length() > 0) {
            TextView m = caption(c, msg);
            m.setTextColor(color(c, R.color.text));
            card.addView(m, matchWrap(c, 6));
        }
        return card;
    }
}
