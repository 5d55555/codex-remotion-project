package com.shinjuku.showdown;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
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
        SystemUi.drawBehindSystemBars(this);
        setContentView(R.layout.activity_main);
        scene = findViewById(R.id.scene);
        stats = findViewById(R.id.frame_stats);
        SystemUi.applySafeInsets(findViewById(R.id.menu_scroll), stats); // 安全区给滚动容器：滚动时内容也不会钻到状态栏下面

        // 背景音乐：现在就开始准备（在它自己的线程上），点开始游戏时就能立刻从头播；
        // 前后台暂停/继续由 App 统一管（在界面之间跳转时音乐不断）
        bgm = Bgm.get(this);
        bgm.prepare();

        // 开始游戏：背景音乐从头放起来（循环），进入难度选择
        findViewById(R.id.btn_start).setOnClickListener(v -> {
            bgm.playFromStart();
            startActivity(new Intent(this, SelectActivity.class));
        });

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
