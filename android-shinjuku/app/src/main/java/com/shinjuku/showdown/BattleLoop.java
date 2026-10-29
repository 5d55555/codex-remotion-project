package com.shinjuku.showdown;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceHolder;

import com.shinjuku.showdown.core.TickInput;
import com.shinjuku.showdown.core.Tuning;
import com.shinjuku.showdown.core.World;

/**
 * 战斗线程：自己的 Looper + Choreographer，跟着屏幕刷新走。
 * 每帧：按真实经过的时间推进若干个固定 1/120 秒的逻辑 tick（一帧最多 8 个，卡顿后不快进），
 * 再用剩下的零头做插值画一帧（lockHardwareCanvas，GPU 绘制）。逻辑和画面都在这个线程，World 不跨线程。
 * Surface 的生死由主线程通知；画的时候持有 surfaceLock，surfaceDestroyed 会等这一帧画完才返回。
 */
final class BattleLoop implements Choreographer.FrameCallback {
    private static final String TAG = "BattleLoop";
    private static final long TICK_NS = 1_000_000_000L / Tuning.HZ;
    private static final int MAX_STEPS = 8;

    private final World world;
    private final Controls controls;
    private final BattleRenderer renderer;
    private final TickInput input = new TickInput();
    private final HandlerThread thread = new HandlerThread("battle");
    private final Handler handler;
    private final Object surfaceLock = new Object();

    // surfaceLock 保护
    private SurfaceHolder holder;
    private int surfaceW, surfaceH;

    // 只在战斗线程
    private Choreographer choreographer;
    private boolean resumed, hasSurface, scheduled;
    private long lastFrameNs, accNs, simNs; // simNs：最近一个 tick 对应的时刻（和触摸事件同一个时钟）
    private float renderTime;           // 画面时钟（秒），暂停期间不走
    private float tickMsAvg, drawMsAvg;

    BattleLoop(World world, Controls controls, BattleRenderer renderer) {
        this.world = world;
        this.controls = controls;
        this.renderer = renderer;
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(() -> {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
            } catch (RuntimeException e) {
                Log.w(TAG, "priority", e);
            }
            choreographer = Choreographer.getInstance();
        });
    }

    /* ---------------- 主线程调用 ---------------- */

    void surfaceReady(SurfaceHolder h, int w, int hgt) {
        synchronized (surfaceLock) {
            holder = h;
            surfaceW = w;
            surfaceH = hgt;
        }
        handler.post(() -> {
            hasSurface = true;
            update();
        });
    }

    /** 阻塞到正在画的这一帧结束；返回后战斗线程不会再碰这个 Surface。 */
    void surfaceGone() {
        synchronized (surfaceLock) {
            holder = null;
        }
        handler.post(() -> {
            hasSurface = false;
            update();
        });
    }

    void setResumed(boolean r) {
        handler.post(() -> {
            resumed = r;
            update();
        });
    }

    void quit() {
        handler.post(() -> {
            resumed = false;
            update();
            renderer.release();
            thread.quitSafely();
        });
    }

    /* ---------------- 战斗线程 ---------------- */

    private void update() {
        boolean run = resumed && hasSurface;
        if (run && !scheduled) {
            lastFrameNs = 0; // 恢复后从这一帧重新计时，不补暂停期间的 tick
            scheduled = true;
            choreographer.postFrameCallback(this);
        } else if (!run && scheduled) {
            scheduled = false;
            choreographer.removeFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!scheduled) return;
        if (lastFrameNs == 0) {
            lastFrameNs = frameTimeNanos;
            simNs = frameTimeNanos;
            accNs = 0;
        }
        long raw = Math.max(0, frameTimeNanos - lastFrameNs);
        lastFrameNs = frameTimeNanos;
        long dt = Math.min(raw, MAX_STEPS * TICK_NS); // 卡了一下：最多补 8 个 tick
        simNs += raw - dt;                            // 丢掉的那段时间直接跳过
        renderTime += dt * 1e-9f;

        long t0 = System.nanoTime();
        accNs += dt;
        while (accNs >= TICK_NS) {
            accNs -= TICK_NS;
            simNs += TICK_NS;
            // 补多个 tick 时按事件发生的时间分到各个 tick；这一帧最后一个 tick 把已经到的全取走，不额外延迟
            controls.sample(input, accNs >= TICK_NS ? simNs : Long.MAX_VALUE);
            world.step(input);
            input.clearEdges();
        }
        long t1 = System.nanoTime();
        draw(accNs / (float) TICK_NS);
        long t2 = System.nanoTime();
        tickMsAvg += ((t1 - t0) * 1e-6f - tickMsAvg) * 0.05f;
        drawMsAvg += ((t2 - t1) * 1e-6f - drawMsAvg) * 0.05f;
        renderer.reportFrame(dt, tickMsAvg, drawMsAvg);

        choreographer.postFrameCallback(this);
    }

    private void draw(float alpha) {
        synchronized (surfaceLock) {
            SurfaceHolder h = holder;
            if (h == null) return;
            Canvas c;
            try {
                c = h.lockHardwareCanvas();
            } catch (RuntimeException e) {
                return; // Surface 正在销毁
            }
            if (c == null) return;
            try {
                renderer.draw(c, world, alpha, renderTime, surfaceW, surfaceH);
            } finally {
                try {
                    h.unlockCanvasAndPost(c);
                } catch (RuntimeException e) {
                    Log.w(TAG, "post", e);
                }
            }
        }
    }
}
