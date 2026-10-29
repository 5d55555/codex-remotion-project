package com.shinjuku.showdown;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;

/**
 * 帧率监视：长按版本号打开。每半秒统计一次——实际帧率、最差一帧的耗时、
 * 超过 1.5 个刷新周期的帧（掉帧）个数，以及屏幕刷新率。
 * 直接画在画布上，刷新读数不触发布局，不给界面添负担。
 */
public class FrameStatsView extends View implements Choreographer.FrameCallback {

    private final Paint bg = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private long last, windowStart, worst;
    private int frames, janky, totalJanky;
    private String line1 = "测量中…", line2 = "";
    private boolean on;

    public FrameStatsView(Context context) {
        this(context, null);
    }

    public FrameStatsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        bg.setColor(0xB3000000);
        text.setColor(0xFF7CFFB2);
        text.setTextSize(12 * dp);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        setVisibility(GONE);
    }

    public void toggle() {
        on = !on;
        setVisibility(on ? VISIBLE : GONE);
        last = 0;
        windowStart = 0;
        totalJanky = 0;
        Choreographer.getInstance().removeFrameCallback(this);
        if (on) Choreographer.getInstance().postFrameCallback(this);
    }

    private float refreshHz() {
        Display d = getDisplay();
        return d != null ? d.getRefreshRate() : 60f;
    }

    @Override
    public void doFrame(long now) {
        if (!on) return;
        float periodNs = 1e9f / refreshHz();
        if (last != 0) {
            long dt = now - last;
            frames++;
            if (dt > worst) worst = dt;
            if (dt > periodNs * 1.5f) {
                janky++;
                totalJanky++;
            }
        } else {
            windowStart = now;
        }
        last = now;
        if (now - windowStart >= 500_000_000L && frames > 0) {
            float fps = frames * 1e9f / (now - windowStart);
            line1 = String.format(java.util.Locale.ROOT, "%.0f fps / %.0f Hz", fps, refreshHz());
            line2 = String.format(java.util.Locale.ROOT, "最差 %.1f ms  掉帧 %d（累计 %d）", worst / 1e6f, janky, totalJanky);
            frames = 0;
            janky = 0;
            worst = 0;
            windowStart = now;
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension((int) (230 * dp), (int) (44 * dp));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRoundRect(0, 0, getWidth(), getHeight(), 6 * dp, 6 * dp, bg);
        c.drawText(line1, 10 * dp, 18 * dp, text);
        c.drawText(line2, 10 * dp, 36 * dp, text);
    }

    @Override
    protected void onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }
}
