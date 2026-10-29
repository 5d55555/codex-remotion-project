package com.shinjuku.showdown;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

import com.shinjuku.showdown.core.BattleConfig;
import com.shinjuku.showdown.core.World;

/** 战斗：横屏、全屏、常亮。难度从选择界面带过来，先调用它的 configure() 再建世界。 */
public class BattleActivity extends Activity implements Controls.Actions {
    static final String EXTRA_DIFFICULTY = "difficulty";

    private BattleView view;
    private Bgm bgm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemUi.immersive(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Difficulty d = parse(getIntent().getStringExtra(EXTRA_DIFFICULTY));
        BattleConfig config = new BattleConfig();
        config.difficulty = d.ordinal();
        d.configure(config);
        bgm = Bgm.get(this);
        view = new BattleView(this, new World(config), d, this, bgm);
        setContentView(view);
    }

    private static Difficulty parse(String name) {
        if (name != null) {
            for (Difficulty d : Difficulty.values()) if (d.name().equals(name)) return d;
        }
        return Difficulty.MIWA;
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.setResumed(true);
    }

    @Override
    protected void onPause() {
        view.setResumed(false);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) SystemUi.immersive(this); // 从边缘划出来的系统栏收回去
    }

    @Override
    protected void onDestroy() {
        view.release();
        super.onDestroy();
    }

    /* ---------------- 顶部按钮（主线程） ---------------- */

    @Override
    public void back() {
        finish();
    }

    @Override
    public void toggleMusic() {
        bgm.setMuted(!bgm.isMuted());
    }

    @Override
    public void toggleStats() {
        view.toggleStats();
    }
}
