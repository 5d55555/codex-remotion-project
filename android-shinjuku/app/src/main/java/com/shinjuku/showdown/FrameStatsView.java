package com.shinjuku.showdown;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;

import java.util.Locale;

/**
 * 帧率监视：长按版本号打开。用 FrameMetrics 读窗口每一帧真正的绘制耗时（不是 Choreographer 回调频率），
 * 每半秒汇总一次：画出了多少帧、最慢一帧多少毫秒、超时（超过本帧截止时间）几帧，以及屏幕刷新率。
 * 统计在后台线程，面板每半秒才重画一次，不给界面添负担。
 * 注意：这是应用这一侧的帧耗时，最终有没有准时上屏，要用 Perfetto 的 FrameTimeline 确认。
 */
public class FrameStatsView extends View implements Window.OnFrameMetricsAvailableListener {

    private static HandlerThread statsThread;

    private final Paint bg = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private boolean on, resumed, visibleNow, listening;
    private Window window;
    private Handler statsHandler;

    // 后台线程累计，半秒一结
    private int frames, overruns;
    private long worstNs, windowStartMs;
    // 结果（主线程读）
    private volatile int shownFrames = -1, shownOverruns;
    private volatile float shownWorstMs, shownFps;
    private volatile long lastFrameAtMs;
    private float refreshHz = 60f;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (listening) postDelayed(this, 500);
        }
    };

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
        sync();
    }

    /** Activity onResume/onPause 调用。 */
    public void setResumed(boolean r) {
        resumed = r;
        sync();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visibleNow = isVisible;
        sync();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sync();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sync();
    }

    /** 开关、前台、可见、已挂载四个条件都满足才统计；所有入口都走这里。 */
    private void sync() {
        boolean want = on && resumed && visibleNow && isAttachedToWindow() && getContext() instanceof Activity;
        if (want == listening) return;
        if (want) {
            window = ((Activity) getContext()).getWindow();
            Display d = getDisplay();
            refreshHz = d != null ? d.getRefreshRate() : 60f;
            synchronized (FrameStatsView.class) {
                if (statsThread == null) {
                    statsThread = new HandlerThread("frame-stats");
                    statsThread.start();
                }
            }
            if (statsHandler == null) statsHandler = new Handler(statsThread.getLooper());
            statsHandler.post(this::resetWindow);
            window.addOnFrameMetricsAvailableListener(this, statsHandler);
            listening = true;
            post(tick);
        } else {
            listening = false;
            removeCallbacks(tick);
            if (window != null) {
                try {
                    window.removeOnFrameMetricsAvailableListener(this);
                } catch (IllegalArgumentException ignored) {
                    // 已经移除过
                }
            }
            shownFrames = -1;
        }
    }

    private void resetWindow() {
        frames = 0;
        overruns = 0;
        worstNs = 0;
        windowStartMs = SystemClock.uptimeMillis();
    }

    /** 后台线程：每画完一帧系统回调一次。只做数值累计，不分配。 */
    @Override
    public void onFrameMetricsAvailable(Window w, FrameMetrics m, int dropped) {
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return;
        long total = m.getMetric(FrameMetrics.TOTAL_DURATION);
        if (total < 0) return;
        long deadline = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? m.getMetric(FrameMetrics.DEADLINE) : 0;
        if (deadline <= 0) deadline = (long) (1e9 / refreshHz);
        frames++;
        if (total > worstNs) worstNs = total;
        if (total > deadline) overruns++;
        long now = SystemClock.uptimeMillis();
        lastFrameAtMs = now;
        long span = now - windowStartMs;
        if (span >= 500) {
            shownFps = frames * 1000f / span;
            shownFrames = frames;
            shownWorstMs = worstNs / 1e6f;
            shownOverruns = overruns;
            resetWindow();
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(resolveSize(Math.round(250 * dp), wSpec), resolveSize(Math.round(44 * dp), hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRoundRect(0, 0, getWidth(), getHeight(), 6 * dp, 6 * dp, bg);
        String l1, l2;
        if (shownFrames < 0) {
            l1 = "测量中…";
            l2 = "";
        } else if (SystemClock.uptimeMillis() - lastFrameAtMs > 1000) {
            l1 = String.format(Locale.ROOT, "静止：没有新帧 · 屏幕 %.0f Hz", refreshHz);
            l2 = "";
        } else {
            l1 = String.format(Locale.ROOT, "绘制 %.0f 帧/秒 · 屏幕 %.0f Hz", shownFps, refreshHz);
            l2 = String.format(Locale.ROOT, "最慢一帧 %.1f ms · 超时 %d 帧", shownWorstMs, shownOverruns);
        }
        c.drawText(l1, 10 * dp, 18 * dp, text);
        c.drawText(l2, 10 * dp, 36 * dp, text);
    }
}
