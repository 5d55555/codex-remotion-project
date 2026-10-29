package com.shinjuku.showdown;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 难度选择：从左到右 三轮（标准）、里梅（困难）、万（疯狂）、史上最强（噩梦）。 */
public class SelectActivity extends Activity {

    private LinearLayout row;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemUi.immersive(this);
        setContentView(R.layout.activity_select);
        SystemUi.applySafeInsets(findViewById(R.id.select_content), null);
        findViewById(R.id.back).setOnClickListener(v -> finish());

        row = findViewById(R.id.cards);
        for (Difficulty d : Difficulty.values()) row.addView(card(this, d, v -> onDifficultyChosen(d)));
        if (savedInstanceState == null && ValueAnimator.areAnimatorsEnabled()) playEntrance();
    }

    /** 选中某个难度：带着它进入战斗。 */
    void onDifficultyChosen(Difficulty d) {
        Intent i = new Intent(this, BattleActivity.class);
        i.putExtra(BattleActivity.EXTRA_DIFFICULTY, d.name());
        startActivity(i);
    }

    /** 一张难度卡（截图测试也用它）。 */
    static View card(Context ctx, Difficulty d, View.OnClickListener onClick) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean nightmare = d == Difficulty.STRONGEST;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding((int) (10 * dp), (int) (14 * dp), (int) (10 * dp), (int) (14 * dp));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                nightmare ? new int[]{0xE6240407, 0xF2080104} : new int[]{0xD9120F2A, 0xF0070614});
        bg.setCornerRadius(10 * dp);
        bg.setStroke((int) Math.max(2, 1.8f * dp), nightmare ? 0xFFE0B25A : (d.accent & 0x00FFFFFF) | 0xAA000000);
        card.setBackground(bg);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(10 * dp);
        mask.setColor(Color.WHITE);
        card.setForeground(new RippleDrawable(ColorStateList.valueOf((d.accent & 0x00FFFFFF) | 0x55000000), null, mask));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(d.title + "，" + d.tier + "，" + d.subtitle);
        card.setOnClickListener(onClick);

        ImageView emblem = new ImageView(ctx);
        emblem.setImageResource(d.emblem);
        emblem.setScaleType(ImageView.ScaleType.FIT_CENTER);
        emblem.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(emblem, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView title = text(ctx, d.title, nightmare ? 30 : 32, Color.WHITE, true);
        title.setShadowLayer(8 * dp, 0, 0, d.accent);
        int glow = (int) (10 * dp); // 给光晕留地方，不然会被 TextView 自己的边界裁成方块
        title.setPadding(glow, glow / 2, glow, glow / 2);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = (int) (1 * dp);
        tl.bottomMargin = -glow / 2;
        card.addView(title, tl);

        card.addView(text(ctx, d.subtitle, 13, 0xCCDDE2F5, false));

        TextView tier = text(ctx, d.tier, 13, nightmare ? 0xFFFFE6A8 : Color.WHITE, true);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(99 * dp);
        pill.setColor(nightmare ? 0xFF8E0F1C : (d.accent & 0x00FFFFFF) | 0x66000000);
        if (nightmare) pill.setStroke((int) Math.max(1, dp), 0xFFE0B25A);
        tier.setBackground(pill);
        tier.setPadding((int) (14 * dp), (int) (3 * dp), (int) (14 * dp), (int) (4 * dp));
        tier.setLetterSpacing(0.15f);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pl.topMargin = (int) (10 * dp);
        card.addView(tier, pl);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins((int) (7 * dp), 0, (int) (7 * dp), 0);
        card.setLayoutParams(lp);
        return card;
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

    /** 进场：四张卡从左到右依次升起。 */
    private void playEntrance() {
        float rise = getResources().getDisplayMetrics().density * 28;
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(rise);
            v.animate().alpha(1f).translationY(0f).setStartDelay(40L + i * 70L).setDuration(380L)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).withLayer().start();
        }
    }

    @Override
    protected void onPause() {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            v.animate().cancel();
            v.setAlpha(1f);
            v.setTranslationY(0f);
        }
        super.onPause();
    }
}
