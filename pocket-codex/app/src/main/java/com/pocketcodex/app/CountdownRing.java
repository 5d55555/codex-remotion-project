package com.pocketcodex.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 登录代码的倒计时环（剩余比例；最后 60 秒变成警告色）。 */
final class CountdownRing extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float fraction = 1f;
    private boolean warn;

    CountdownRing(Context c) {
        super(c);
        float w = Ui.dp(c, 3);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(Ui.color(c, R.color.border));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // 旁边有文字倒计时
    }

    void set(float fraction, boolean warn) {
        this.fraction = Math.max(0, Math.min(1, fraction));
        this.warn = warn;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float pad = track.getStrokeWidth();
        box.set(pad, pad, getWidth() - pad, getHeight() - pad);
        c.drawOval(box, track);
        arc.setColor(Ui.color(getContext(), warn ? R.color.warning : R.color.primary));
        c.drawArc(box, -90, 360 * fraction, false, arc);
    }
}
