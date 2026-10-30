package com.shinjuku.showdown;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowInsets;

import com.shinjuku.showdown.core.World;

/**
 * 战斗画面的 SurfaceView：自己一块不透明的缓冲区，由战斗线程直接用 GPU 画，不经过界面的绘制流程。
 * 缓冲区保持屏幕原生尺寸（像素风的放大由 GL 用最近邻做；交给合成器缩放可能会变糊）。
 * 触摸在主线程交给 Controls。
 */
@SuppressLint("ViewConstructor")
final class BattleView extends SurfaceView implements SurfaceHolder.Callback {
    private final Controls controls;
    private final PixelScene scene;
    private final BattleLoop loop;
    private float safeL, safeT, safeR, safeB;

    BattleView(Context context, World world, Difficulty difficulty, Controls.Actions actions, Bgm bgm, BattleLoop.Host host) {
        super(context);
        controls = new Controls(actions);
        Atlas atlas = new Atlas();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false; // 像素背景必须 1:1
        Bitmap bg = BitmapFactory.decodeResource(getResources(), R.drawable.bg_battle_px, o);
        Bitmap voidBg = BitmapFactory.decodeResource(getResources(), R.drawable.bg_void_px, o);
        scene = new PixelScene(atlas, bg, voidBg, difficulty, controls, bgm);
        loop = new BattleLoop(world, controls, scene, new GlTarget(new AndroidGl(), atlas.bitmap, bg, voidBg),
                new CanvasTarget(atlas.bitmap, bg, voidBg), host);
        getHolder().setFormat(PixelFormat.OPAQUE);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    void setResumed(boolean resumed) {
        if (!resumed) controls.releaseAll();
        loop.setResumed(resumed);
    }

    /** 放过场时战斗不收触摸（按着的全部松开）。 */
    void setInputBlocked(boolean blocked) {
        controls.setBlocked(blocked);
    }

    void endCutscene(int castId, Runnable resumed) {
        loop.endCutscene(castId, resumed);
    }

    void toggleStats() {
        scene.statsOn = !scene.statsOn;
    }

    void release() {
        loop.quit();
    }

    @Override
    @SuppressWarnings("deprecation")
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // 横屏时刘海、挖孔在左右两侧：按键和血条都避开
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets c = insets.getInsets(WindowInsets.Type.displayCutout());
            safeL = c.left;
            safeT = c.top;
            safeR = c.right;
            safeB = c.bottom;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout cut = insets.getDisplayCutout();
            if (cut != null) {
                safeL = cut.getSafeInsetLeft();
                safeT = cut.getSafeInsetTop();
                safeR = cut.getSafeInsetRight();
                safeB = cut.getSafeInsetBottom();
            }
        }
        relayout(getWidth(), getHeight());
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        relayout(w, h);
    }

    private void relayout(int w, int h) {
        if (w <= 0 || h <= 0) return;
        float dp = getResources().getDisplayMetrics().density;
        controls.layout = new Controls.Layout(w, h, dp, safeL, safeT, safeR, safeB);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility") // 游戏操控区，没有可点击的语义
    public boolean onTouchEvent(MotionEvent e) {
        return controls.onTouch(e);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        requestFrameRate(holder.getSurface());
        loop.surfaceReady(holder, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        loop.surfaceGone();
    }

    /** 告诉系统这块画面想要屏幕支持的最高刷新率（不超过 120），高刷屏上更顺。 */
    private void requestFrameRate(Surface s) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Display d = getDisplay();
        if (d == null) return;
        float best = d.getRefreshRate();
        for (Display.Mode m : d.getSupportedModes()) {
            float r = m.getRefreshRate();
            if (r > best && r <= 120.5f) best = r;
        }
        try {
            s.setFrameRate(best, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        } catch (RuntimeException ignored) {
            // 个别设备不接受，用默认刷新率
        }
    }
}
