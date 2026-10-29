package com.shinjuku.showdown;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

/** 主页：标题 + 从上到下三个选项（开始游戏、空置、空置）。 */
public class MainActivity extends Activity {

    private Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        drawBehindSystemBars();
        setContentView(R.layout.activity_main);

        // 空置：弹出“未开放”。连点时先撤掉上一条，免得排一长串。
        View.OnClickListener notOpen = v -> {
            if (toast != null) toast.cancel();
            toast = Toast.makeText(this, R.string.not_open, Toast.LENGTH_SHORT);
            toast.show();
        };
        findViewById(R.id.btn_vacant_1).setOnClickListener(notOpen);
        findViewById(R.id.btn_vacant_2).setOnClickListener(notOpen);

        // 开始游戏：游戏本体还没做，这一版先只有主页。
        findViewById(R.id.btn_start).setOnClickListener(v -> { });

        if (savedInstanceState == null) playEntrance();
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

    /** 进场：标题先浮现，三个选项依次从下面升上来。 */
    private void playEntrance() {
        int[] ids = {R.id.title, R.id.subtitle, R.id.btn_start, R.id.btn_vacant_1, R.id.btn_vacant_2};
        float rise = getResources().getDisplayMetrics().density * 24;
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            v.setAlpha(0f);
            v.setTranslationY(rise);
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(150L + i * 110L)
                    .setDuration(520L)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
    }
}
