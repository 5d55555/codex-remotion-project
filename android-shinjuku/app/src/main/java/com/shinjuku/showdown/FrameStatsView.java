package com.shinjuku.showdown;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;

import java.util.Locale;

/**
 * 帧率监视：长按版本号打开。用 FrameMetrics 读窗口每一帧真正的绘制耗时（不是 Choreographer 回调频率），
 * 每半秒汇总一次：窗口帧率（含面板自己每半秒的那一次刷新）、最慢一帧多少毫秒、
 * 超时（超过本帧截止时间）几帧、系统漏报了几帧的数据，以及屏幕刷新率。
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

    /** 一次汇总的结果；整体替换，主线程读到的永远是同一批数。 */
    private static final class Snapshot {
        final float fps, worstMs;
        final int overruns, lost;

        Snapshot(float fps, float worstMs, int overruns, int lost) {
            this.fps = fps;
            this.worstMs = worstMs;
            this.overruns = overruns;
            this.lost = lost;
        }
    }

    // 后台线程累计，半秒一结
    private int frames, overruns, lost;
    private long worstNs, firstVsyncNs, lastVsyncNs;
    private volatile Snapshot shown;
    private volatile float refreshHz = 60f; // 主线程每半秒更新，统计线程读

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Display d = getDisplay();
            if (d != null) refreshHz = d.getRefreshRate(); // 运行中切换刷新率也能跟上
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
                window = null;
            }
            shown = null;
        }
    }

    private void resetWindow() {
        frames = 0;
        overruns = 0;
        lost = 0;
        worstNs = 0;
        firstVsyncNs = 0;
        lastVsyncNs = 0;
    }

    /** 后台线程：每画完一帧系统回调一次。只做数值累计，不分配。 */
    @Override
    public void onFrameMetricsAvailable(Window w, FrameMetrics m, int dropped) {
        lost += dropped; // 系统漏报的帧：耗时不可知，只计数，不算进超时
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return;
        long total = m.getMetric(FrameMetrics.TOTAL_DURATION);
        long vsync = m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP);
        if (total < 0 || vsync <= 0) return;
        long deadline = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? m.getMetric(FrameMetrics.DEADLINE) : 0;
        if (deadline <= 0) deadline = (long) (1e9 / refreshHz); // 旧系统：用刷新周期近似
        if (firstVsyncNs == 0) firstVsyncNs = vsync;
        lastVsyncNs = vsync;
        frames++;
        if (total > worstNs) worstNs = total;
        if (total > deadline) overruns++;
        long span = lastVsyncNs - firstVsyncNs; // 按帧本身的 vsync 时间算，不受回调排队影响
        if (span >= 500_000_000L && frames > 1) {
            shown = new Snapshot((frames - 1) * 1e9f / span, worstNs / 1e6f, overruns, lost);
            resetWindow();
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(resolveSize(Math.round(270 * dp), wSpec), resolveSize(Math.round(44 * dp), hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRoundRect(0, 0, getWidth(), getHeight(), 6 * dp, 6 * dp, bg);
        Snapshot sn = shown;
        String l1, l2;
        if (sn == null) {
            l1 = "测量中…";
            l2 = "";
        } else {
            l1 = String.format(Locale.ROOT, "窗口 %.0f 帧/秒（含面板）· 屏幕 %.0f Hz", sn.fps, refreshHz);
            l2 = String.format(Locale.ROOT, "最慢 %.1f ms · 超时 %d · 漏报 %d", sn.worstMs, sn.overruns, sn.lost);
        }
        c.drawText(l1, 10 * dp, 18 * dp, text);
        c.drawText(l2, 10 * dp, 36 * dp, text);
    }
}
