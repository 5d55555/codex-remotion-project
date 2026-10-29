package com.shinjuku.showdown;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.shinjuku.showdown.core.TickInput;
import com.shinjuku.showdown.core.Tuning;
import com.shinjuku.showdown.core.World;

import java.util.concurrent.CountDownLatch;

/**
 * 战斗线程：自己的 Looper + Choreographer，跟着屏幕刷新走。
 * 每帧：按真实经过的时间推进若干个固定 1/120 秒的逻辑 tick（一帧最多 8 个，卡顿后不快进），
 * 再用剩下的零头做插值画一帧。逻辑和画面都在这个线程，World 不跨线程。
 * 画面用 GLES2（GlTarget）；GL 起不来就用 Canvas（lockHardwareCanvas + CanvasTarget）兜底。
 * Surface 的生死由主线程通知；surfaceGone() 会等战斗线程放开这个 Surface 才返回。
 */
final class BattleLoop implements Choreographer.FrameCallback {
    private static final String TAG = "BattleLoop";
    private static final long TICK_NS = 1_000_000_000L / Tuning.HZ;
    private static final int MAX_STEPS = 8;

    private final World world;
    private final Controls controls;
    private final PixelScene scene;
    private final GlTarget gl;
    private final CanvasTarget canvasTarget;
    private final TickInput input = new TickInput();
    private final HandlerThread thread = new HandlerThread("battle");
    private final Handler handler;
    private final Object surfaceLock = new Object();
    private SurfaceHolder holder; // surfaceLock 保护（只给 Canvas 兜底用）
    private volatile boolean surfaceValid; // 主线程在 surfaceDestroyed 一开始就清掉：战斗线程画之前看一眼
    private static final int MAX_GL_FAILURES = 3;
    private int glFailures;

    // 只在战斗线程
    private Choreographer choreographer;
    private Surface surface;
    private int surfaceW, surfaceH;
    private boolean resumed, hasSurface, scheduled, useCanvas, needAttach;
    private long lastFrameNs, accNs, simNs; // simNs：最近一个 tick 对应的时刻（和触摸事件同一个时钟）
    private float renderTime;               // 画面时钟（秒），暂停期间不走
    private float tickMsAvg, drawMsAvg;

    BattleLoop(World world, Controls controls, PixelScene scene, GlTarget gl, CanvasTarget canvasTarget) {
        this.world = world;
        this.controls = controls;
        this.scene = scene;
        this.gl = gl;
        this.canvasTarget = canvasTarget;
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
        }
        surfaceValid = true;
        Surface s = h.getSurface();
        handler.post(() -> {
            boolean fresh = s != surface || !hasSurface;
            surface = s;
            surfaceW = w;
            surfaceH = hgt;
            if (fresh) needAttach = true;
            hasSurface = true;
            update();
        });
    }

    /**
     * 阻塞到战斗线程确认放开这个 Surface（GL 的窗口表面销毁、Canvas 的这一帧画完）才返回：
     * 返回之后战斗线程一定不会再碰它。和 GLSurfaceView 一样不设超时（超时就返回等于没有保证）。
     */
    void surfaceGone() {
        surfaceValid = false;
        synchronized (surfaceLock) {
            holder = null;
        }
        CountDownLatch done = new CountDownLatch(1);
        boolean posted = handler.post(() -> {
            try {
                hasSurface = false;
                surface = null;
                update();
                if (!useCanvas) gl.detachSurface();
            } finally {
                done.countDown();
            }
        });
        if (!posted) return; // 线程已经退出（退出前已经放开了）
        boolean interrupted = false;
        while (true) {
            try {
                done.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    void setResumed(boolean r) {
        handler.post(() -> {
            resumed = r;
            update();
        });
    }

    void quit() {
        handler.post(() -> {
            try {
                resumed = false;
                update();
                gl.release();
            } finally {
                thread.quitSafely();
            }
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
        scene.reportFrame(dt, tickMsAvg, drawMsAvg);

        choreographer.postFrameCallback(this);
    }

    private void draw(float alpha) {
        if (!hasSurface || surface == null || !surfaceValid) return;
        if (!useCanvas && needAttach) {
            try {
                gl.attach(surface);
                needAttach = false;
            } catch (RuntimeException e) {
                glFailed(e);
            }
        }
        if (scene.layout(surfaceW, surfaceH) || scene.hudText() != null && !hudSet) {
            HudText ht = scene.hudText();
            if (ht != null) {
                gl.setHud(ht.bitmap);
                canvasTarget.setHud(ht.bitmap);
                hudSet = true;
            }
        }
        if (!useCanvas) {
            try {
                scene.draw(gl, world, alpha, renderTime, surfaceW, surfaceH);
                int r = gl.swap();
                if (r == GlTarget.SWAP_OK) {
                    glFailures = 0;
                } else if (++glFailures >= MAX_GL_FAILURES) {
                    glFailed(new IllegalStateException("eglSwapBuffers 连续失败"));
                } else {
                    needAttach = true; // 窗口表面坏了或上下文丢了：下一帧重新接上（上下文丢了连纹理一起重建）
                }
            } catch (RuntimeException e) {
                glFailed(e); // FBO 建不起来、着色器出错……都改用 Canvas，不让异常把应用带崩
            }
            return;
        }
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
                canvasTarget.setDestination(c);
                scene.draw(canvasTarget, world, alpha, renderTime, surfaceW, surfaceH);
            } finally {
                try {
                    h.unlockCanvasAndPost(c);
                } catch (RuntimeException e) {
                    Log.w(TAG, "post", e);
                }
            }
        }
    }

    private boolean hudSet;

    /** GL 这条路走不通：放开 GL（之后同一个 Surface 可以用硬件 Canvas 画），改用 Canvas。 */
    private void glFailed(RuntimeException e) {
        Log.w(TAG, "GL 画不了，改用 Canvas", e);
        try {
            gl.release();
        } catch (RuntimeException ignored) {
            // 已经坏了
        }
        useCanvas = true;
    }
}
