package com.pocketcodex.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 浮层：ChatGPT 风格的弹出卡片、底部面板。直接加在 Activity 的 content（FrameLayout）最上面，
 * 不用 PopupWindow/Dialog，这样位置好控制、Robolectric 能截到图、返回键按层关闭。
 */
final class Overlay {
    /** 测试截图时关掉动画。 */
    static boolean animate = true;

    final AppCompatActivity a;
    final FrameLayout layer;
    private final OnBackPressedCallback back;
    private Runnable onClose;
    private boolean closed;

    /** dim：是否压暗背景。点空白处关闭。 */
    Overlay(AppCompatActivity a, boolean dim) {
        this.a = a;
        layer = new FrameLayout(a);
        layer.setTag("overlay");
        if (dim) layer.setBackgroundColor(Ui.color(a, R.color.scrim));
        layer.setClickable(true);
        layer.setOnClickListener(v -> close());
        back = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                close();
            }
        };
    }

    Overlay onClose(Runnable r) {
        onClose = r;
        return this;
    }

    /** 浮层所在的容器（Activity 的 content）。 */
    static FrameLayout host(AppCompatActivity a) {
        return a.findViewById(android.R.id.content);
    }

    void show() {
        host(a).addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        a.getOnBackPressedDispatcher().addCallback(a, back);
        if (animate && layer.getBackground() != null) {
            layer.setAlpha(0f);
            layer.animate().alpha(1f).setDuration(160).start();
        }
    }

    boolean showing() {
        return !closed && layer.getParent() != null;
    }

    void close() {
        if (closed) return;
        closed = true;
        back.remove();
        ViewGroup p = (ViewGroup) layer.getParent();
        if (p != null) p.removeView(layer);
        if (onClose != null) onClose.run();
    }

    /** 加一个卡片：吃掉自己的点击（不会穿透到“点空白关闭”）。 */
    void add(View card, FrameLayout.LayoutParams lp) {
        card.setClickable(true);
        layer.addView(card, lp);
        if (animate) {
            card.setAlpha(0f);
            card.setScaleX(0.96f);
            card.setScaleY(0.96f);
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    /** 底部面板：从下往上滑出。 */
    void addSheet(View sheet) {
        sheet.setClickable(true);
        layer.addView(sheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        if (animate) {
            sheet.setTranslationY(Ui.dp(a, 400));
            sheet.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    /** anchor 在浮层坐标系里的位置（左、上、右、下）。 */
    int[] boundsOf(View anchor) {
        int[] la = new int[2], lh = new int[2];
        anchor.getLocationInWindow(la);
        host(a).getLocationInWindow(lh);
        int x = la[0] - lh[0], y = la[1] - lh[1];
        return new int[]{x, y, x + anchor.getWidth(), y + anchor.getHeight()};
    }

    int layerHeight() {
        int h = host(a).getHeight();
        return h > 0 ? h : a.getResources().getDisplayMetrics().heightPixels;
    }

    int layerWidth() {
        int w = host(a).getWidth();
        return w > 0 ? w : a.getResources().getDisplayMetrics().widthPixels;
    }

    /* ---------------- 卡片小部件 ---------------- */

    /** 白色大圆角卡片 + 阴影。 */
    static LinearLayout card(Context c, float radiusDp) {
        LinearLayout l = Ui.vbox(c);
        GradientDrawable bg = Ui.round(c, R.color.card, 0, radiusDp);
        bg.setStroke(Math.max(1, Ui.dp(c, 1) / 2), Ui.color(c, R.color.border));
        l.setBackground(bg);
        Ui.elevate(l, 14);
        l.setClipToOutline(true); // 滚动的内容也按圆角裁掉
        return l;
    }

    /** “+”菜单那样的一行：灰色圆底图标 + 文字。 */
    static LinearLayout circleRow(Context c, int icon, String label, View.OnClickListener l) {
        LinearLayout row = Ui.hbox(c);
        row.setMinimumHeight(Ui.dp(c, 68));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 24), Ui.dp(c, 6));
        ImageView ic = new ImageView(c);
        ic.setImageResource(icon);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.text)));
        ic.setBackground(Ui.circle(c, R.color.icon_circle));
        int p = Ui.dp(c, 13);
        ic.setPadding(p, p, p, p);
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)));
        TextView t = Ui.text(c, label, 17, R.color.text);
        LinearLayout.LayoutParams lp = Ui.weight1();
        lp.leftMargin = Ui.dp(c, 16);
        row.addView(t, lp);
        Ui.ripple(row, false);
        row.setOnClickListener(l);
        return row;
    }

    /** 普通菜单行：线条图标 + 文字（“更多”菜单）。 */
    static LinearLayout iconRow(Context c, int icon, String label, int tint, View.OnClickListener l) {
        LinearLayout row = Ui.hbox(c);
        row.setMinimumHeight(Ui.dp(c, 52));
        row.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 28), 0);
        row.addView(Ui.icon(c, icon, tint, 22));
        TextView t = Ui.text(c, label, 16, tint == R.color.error ? R.color.error : R.color.text);
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.leftMargin = Ui.dp(c, 16);
        row.addView(t, lp);
        Ui.ripple(row, false);
        row.setOnClickListener(l);
        return row;
    }

    /** 小标题（卡片里的分组名）。 */
    static TextView header(Context c, String s) {
        TextView t = Ui.text(c, s, 14, R.color.text3);
        t.setPadding(Ui.dp(c, 24), Ui.dp(c, 14), Ui.dp(c, 24), Ui.dp(c, 6));
        return t;
    }

    /** 下拉选项：标题 + 可选说明 + 右边的对勾。 */
    static LinearLayout optionRow(Context c, String title, String sub, boolean checked, boolean enabled, View.OnClickListener l) {
        LinearLayout row = Ui.hbox(c);
        row.setMinimumHeight(Ui.dp(c, sub == null ? 58 : 70));
        row.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 8));
        LinearLayout tx = Ui.vbox(c);
        TextView t = Ui.text(c, title, 17, enabled ? R.color.text : R.color.text3);
        tx.addView(t);
        if (sub != null) {
            TextView s = Ui.text(c, sub, 14, R.color.text3);
            tx.addView(s, Ui.matchWrap(c, 2));
        }
        row.addView(tx, Ui.weight1());
        ImageView ck = Ui.icon(c, R.drawable.ic_tick, R.color.text, 24);
        ck.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams cl = (LinearLayout.LayoutParams) ck.getLayoutParams();
        cl.leftMargin = Ui.dp(c, 16);
        row.addView(ck, cl);
        row.setContentDescription(title + (checked ? "，已选中" : ""));
        if (enabled) {
            Ui.ripple(row, false);
            row.setOnClickListener(l);
        }
        return row;
    }

    /* ---------------- 下拉卡片 ---------------- */

    /** 下拉里的一项。pick 为 null 且 enabled 时只关闭。 */
    static final class Opt {
        final String title, sub;
        final boolean checked, enabled;
        final Runnable pick;

        Opt(String title, String sub, boolean checked, boolean enabled, Runnable pick) {
            this.title = title;
            this.sub = sub;
            this.checked = checked;
            this.enabled = enabled;
            this.pick = pick;
        }

        Opt(String title, String sub, boolean checked, Runnable pick) {
            this(title, sub, checked, true, pick);
        }
    }

    /**
     * ChatGPT 的下拉卡片：右边和 anchor 对齐；preferBelow 时出现在 anchor 下方（放不下就到上方），否则在上方。
     * 选了一项先关卡片再执行 pick，最后执行 after。chevron 不为 null 时打开期间转 180°。
     */
    static Overlay dropdown(AppCompatActivity a, View anchor, java.util.List<Opt> opts, boolean preferBelow, android.widget.ImageView chevron, Runnable after) {
        Overlay dd = new Overlay(a, false);
        LinearLayout card = card(a, 28);
        card.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        LinearLayout list = Ui.vbox(a);
        for (Opt o : opts) {
            list.addView(optionRow(a, o.title, o.sub, o.checked, o.enabled, x -> {
                dd.close();
                if (o.pick != null) o.pick.run();
                if (after != null) after.run();
            }), Ui.matchWrap());
        }
        sv.addView(list);
        card.addView(sv);
        int[] b = dd.boundsOf(anchor);
        int w = Math.max(Ui.dp(a, 220), Math.round(dd.layerWidth() * 0.58f));
        card.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int want = card.getMeasuredHeight();
        int margin = Ui.dp(a, 12);
        int spaceBelow = dd.layerHeight() - b[3] - margin, spaceAbove = b[1] - margin;
        boolean below = preferBelow ? spaceBelow >= Math.min(want, Ui.dp(a, 220)) || spaceBelow >= spaceAbove : spaceAbove < Math.min(want, Ui.dp(a, 220)) && spaceBelow > spaceAbove;
        int h = Math.min(want, Math.max(Ui.dp(a, 120), below ? spaceBelow : spaceAbove));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, android.view.Gravity.TOP | android.view.Gravity.START);
        lp.leftMargin = Math.max(margin, Math.min(b[2], dd.layerWidth() - margin) - w);
        lp.topMargin = below ? b[3] - Ui.dp(a, 4) : Math.max(margin, b[1] - h + Ui.dp(a, 6));
        card.setPivotX(w);
        card.setPivotY(below ? 0 : h);
        dd.add(card, lp);
        if (chevron != null) {
            chevron.setRotation(180);
            dd.onClose(() -> chevron.setRotation(0));
        }
        dd.show();
        return dd;
    }

    /** 加粗的大号文字（面板标题等）。 */
    static TextView strong(Context c, String s, float sp) {
        TextView t = Ui.text(c, s, sp, R.color.text);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
}
