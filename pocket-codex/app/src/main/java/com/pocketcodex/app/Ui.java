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

    /**
     * 渐隐过渡（ChatGPT 的顶栏和底部输入/搜索框下面，内容是淡出去的，不是一刀切）：
     * fromTop = true 时上面是不透明底色、往下变透明；false 反过来。colorRes 用页面的底色。
     */
    static View fade(Context c, int colorRes, boolean fromTop) {
        View v = new View(c);
        int col = color(c, colorRes);
        int clear = col & 0x00FFFFFF;
        int mid = (col & 0x00FFFFFF) | 0xB0000000;
        GradientDrawable g = new GradientDrawable(fromTop ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{col, col, mid, clear});
        v.setBackground(g);
        v.setClickable(false);
        v.setFocusable(false);
        return v;
    }

    /* ---------------- ChatGPT 风格的设置卡片 ---------------- */

    /** 强调色（设置 → 外观 → 强调色）：发送键、开关、链接。 */
    static final String[] ACCENT_NAMES = {"蓝色", "绿色", "紫色", "橙色", "粉色", "黑色"};
    static final int[] ACCENTS_LIGHT = {0xFF2F6BF2, 0xFF10A37F, 0xFF7C4DFF, 0xFFF26B1D, 0xFFE8457C, 0xFF0D0D0D};
    static final int[] ACCENTS_DARK = {0xFF3B7BFF, 0xFF19C37D, 0xFF9C7BFF, 0xFFFF8A3D, 0xFFFF6B9A, 0xFFECECEC};

    static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    static int accent(Context c) {
        int i = Math.max(0, Math.min(ACCENT_NAMES.length - 1, new Prefs(c).accent()));
        return night(c) ? ACCENTS_DARK[i] : ACCENTS_LIGHT[i];
    }

    /** 强调色上的文字/图标颜色（黑色强调色在深色模式下是浅色，要配深色字）。 */
    static int onAccent(Context c) {
        int i = new Prefs(c).accent();
        return i == ACCENT_NAMES.length - 1 && night(c) ? 0xFF0D0D0D : 0xFFFFFFFF;
    }

    static GradientDrawable circleColor(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** 分组卡片：大圆角 + 细边，里面一行一行。 */
    static LinearLayout group(Context c) {
        LinearLayout l = vbox(c);
        GradientDrawable bg = round(c, R.color.card, 0, 28);
        bg.setStroke(Math.max(1, dp(c, 1) / 2 + 1), color(c, R.color.border));
        l.setBackground(bg);
        l.setClipToOutline(true);
        return l;
    }

    static LinearLayout.LayoutParams groupParams(Context c) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.leftMargin = lp.rightMargin = dp(c, 16);
        lp.bottomMargin = dp(c, 14);
        return lp;
    }

    /** 分组标题（卡片上方的灰字）。 */
    static TextView groupTitle(Context c, String s) {
        TextView t = text(c, s, 15, R.color.text3);
        t.setPadding(dp(c, 32), dp(c, 14), dp(c, 32), dp(c, 8));
        return t;
    }

    /** 卡片里的一行：图标 + 标题（可选说明）+ 右边的值 + 箭头/下拉符号。trailing：0 无，1 “›”，2 “⌄”。 */
    static LinearLayout groupRow(Context c, int icon, CharSequence title, CharSequence sub, CharSequence value, int trailing, View.OnClickListener l) {
        LinearLayout row = hbox(c);
        row.setMinimumHeight(dp(c, sub == null ? 64 : 76));
        row.setPadding(dp(c, icon == 0 ? 24 : 20), dp(c, 10), dp(c, 18), dp(c, 10));
        if (icon != 0) {
            ImageView ic = icon(c, icon, R.color.text, 24);
            LinearLayout.LayoutParams il = (LinearLayout.LayoutParams) ic.getLayoutParams();
            il.rightMargin = dp(c, 18);
            row.addView(ic, il);
        }
        LinearLayout tx = vbox(c);
        TextView t = text(c, title, 17, R.color.text);
        tx.addView(t);
        if (sub != null) {
            TextView s = text(c, sub, 13, R.color.text3);
            tx.addView(s, matchWrap(c, 2));
        }
        row.addView(tx, weight1());
        if (value != null) {
            TextView v = text(c, value, 16, R.color.text3);
            v.setSingleLine();
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            v.setMaxWidth(dp(c, 190));
            v.setTag("value");
            LinearLayout.LayoutParams vl = wrap();
            vl.leftMargin = dp(c, 12);
            row.addView(v, vl);
        }
        if (trailing != 0) {
            ImageView ch = icon(c, trailing == 1 ? R.drawable.ic_chevron_right : R.drawable.ic_chev_down, R.color.text2, trailing == 1 ? 22 : 20);
            LinearLayout.LayoutParams cl = (LinearLayout.LayoutParams) ch.getLayoutParams();
            cl.leftMargin = dp(c, 8);
            ch.setTag("chevron");
            row.addView(ch, cl);
        }
        row.setContentDescription(title + (value == null ? "" : "：" + value));
        if (l != null) {
            ripple(row, false);
            row.setOnClickListener(l);
        }
        return row;
    }

    /** 卡片里行与行之间的细线（左边让开图标）。 */
    static View groupDivider(Context c, boolean afterIcon) {
        View v = new View(c);
        v.setBackgroundColor(color(c, R.color.border));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1) / 2 + 1));
        lp.leftMargin = dp(c, afterIcon ? 62 : 24);
        lp.rightMargin = dp(c, 18);
        v.setLayoutParams(lp);
        return v;
    }

    /** ChatGPT 那种黑色胶囊开关。 */
    static com.google.android.material.materialswitch.MaterialSwitch toggle(Context c, boolean on, android.widget.CompoundButton.OnCheckedChangeListener l) {
        com.google.android.material.materialswitch.MaterialSwitch s = new com.google.android.material.materialswitch.MaterialSwitch(c);
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setTrackTintList(new ColorStateList(states, new int[]{color(c, R.color.primary), color(c, R.color.border)}));
        s.setThumbTintList(new ColorStateList(states, new int[]{color(c, R.color.on_primary), color(c, R.color.card)}));
        s.setTrackDecorationTintList(ColorStateList.valueOf(0x00000000));
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        return s;
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
