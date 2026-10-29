package com.shinjuku.showdown;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

/** 主页：标题 + 从上到下三个选项（开始游戏、空置、空置）。 */
public class MainActivity extends Activity {

    private static final long TOAST_GAP_MS = 2500; // 连点时这段时间内不重复弹
    private static final int[] ENTRANCE = {R.id.title, R.id.subtitle, R.id.btn_start, R.id.btn_vacant_1, R.id.btn_vacant_2};

    private ShinjukuView scene;
    private FrameStatsView stats;
    private Bgm bgm;
    private Toast toast;
    private long toastAt = -TOAST_GAP_MS;
    private ValueAnimator.DurationScaleChangeListener scaleListener; // API 33+

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        drawBehindSystemBars();
        setContentView(R.layout.activity_main);
        scene = findViewById(R.id.scene);
        stats = findViewById(R.id.frame_stats);
        applySafeInsets(findViewById(R.id.content), stats);

        // 背景音乐：第一帧画完再开始准备（在它自己的线程上），点开始游戏时就能立刻从头播
        bgm = Bgm.get(this);
        getWindow().getDecorView().post(bgm::prepare);

        // 开始游戏：游戏本体还没做；先把背景音乐从头放起来（循环）
        findViewById(R.id.btn_start).setOnClickListener(v -> bgm.playFromStart());

        // 空置：弹出“未开放”。2.5 秒内的重复点击直接忽略；要弹新的先撤掉旧的。
        View.OnClickListener notOpen = v -> {
            long now = SystemClock.uptimeMillis();
            if (now - toastAt < TOAST_GAP_MS) return;
            toastAt = now;
            if (toast != null) toast.cancel();
            toast = Toast.makeText(this, R.string.not_open, Toast.LENGTH_SHORT);
            toast.show();
        };
        findViewById(R.id.btn_vacant_1).setOnClickListener(notOpen);
        findViewById(R.id.btn_vacant_2).setOnClickListener(notOpen);

        // 长按版本号：打开/关闭帧率监视
        findViewById(R.id.version).setOnLongClickListener(v -> {
            stats.toggle();
            return true;
        });

        if (savedInstanceState == null && ValueAnimator.areAnimatorsEnabled()) playEntrance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scene.setAnimationsAllowed(ValueAnimator.areAnimatorsEnabled());
        scene.setResumed(true);
        stats.setResumed(true);
        bgm.setHostPaused(false);
        // 前台期间系统动画设置变了（关掉动画、进省电模式），背景跟着停/走
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (scaleListener == null) scaleListener = scale -> scene.setAnimationsAllowed(ValueAnimator.areAnimatorsEnabled());
            ValueAnimator.registerDurationScaleChangeListener(scaleListener);
        }
    }

    @Override
    protected void onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && scaleListener != null) {
            ValueAnimator.unregisterDurationScaleChangeListener(scaleListener);
        }
        scene.setResumed(false);
        stats.setResumed(false);
        bgm.setHostPaused(true);
        if (toast != null) {
            toast.cancel();
            toast = null;
        }
        finishEntrance();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) bgm.release();
        super.onDestroy();
    }

    /** 夜景背景铺满整屏，连状态栏和导航栏后面也画上。 */
    @SuppressWarnings("deprecation")
    private void drawBehindSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    /** 内容层避开状态栏、导航栏和刘海；背景不避让，照样铺满。帧率面板跟着顶部安全区下移。 */
    @SuppressWarnings("deprecation")
    private static void applySafeInsets(View content, View overlay) {
        final int l = content.getPaddingLeft(), t = content.getPaddingTop();
        final int r = content.getPaddingRight(), b = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int sl, st, sr, sb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets s = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                sl = s.left;
                st = s.top;
                sr = s.right;
                sb = s.bottom;
            } else {
                sl = insets.getSystemWindowInsetLeft();
                st = insets.getSystemWindowInsetTop();
                sr = insets.getSystemWindowInsetRight();
                sb = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cut = insets.getDisplayCutout();
                    if (cut != null) {
                        sl = Math.max(sl, cut.getSafeInsetLeft());
                        st = Math.max(st, cut.getSafeInsetTop());
                        sr = Math.max(sr, cut.getSafeInsetRight());
                        sb = Math.max(sb, cut.getSafeInsetBottom());
                    }
                }
            }
            v.setPadding(l + sl, t + st, r + sr, b + sb);
            overlay.setTranslationX(sl);
            overlay.setTranslationY(st);
            return insets;
        });
        content.requestApplyInsets();
    }

    /** 进场：标题先浮现，三个选项依次升上来；全部到位约 0.75 秒。 */
    private void playEntrance() {
        float rise = getResources().getDisplayMetrics().density * 20;
        for (int i = 0; i < ENTRANCE.length; i++) {
            View v = findViewById(ENTRANCE[i]);
            v.setAlpha(0f);
            v.setTranslationY(rise);
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(60L + i * 70L)
                    .setDuration(420L)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .withLayer() // 动画期间走硬件层，透明度变化不必每帧重画按钮
                    .start();
        }
    }

    /** 离开时直接把进场动画跳到终点，不留半透明的控件。 */
    private void finishEntrance() {
        for (int id : ENTRANCE) {
            View v = findViewById(id);
            v.animate().cancel();
            v.setAlpha(1f);
            v.setTranslationY(0f);
        }
    }
}
