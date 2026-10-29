package com.shinjuku.showdown;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.shinjuku.showdown.core.BattleConfig;
import com.shinjuku.showdown.core.World;

/**
 * 战斗：横屏、全屏、常亮。难度从选择界面带过来，先调用它的 configure() 再建世界。
 * 界面是一层 FrameLayout：战斗画面在下，领域展开的过场视频（和它的黑边幕布）在上。
 */
public class BattleActivity extends Activity implements Controls.Actions, BattleLoop.CutsceneHost, CutscenePlayer.Host {
    static final String EXTRA_DIFFICULTY = "difficulty";

    private BattleView view;
    private CutscenePlayer cutscene;
    private Bgm bgm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Difficulty d = parse(getIntent().getStringExtra(EXTRA_DIFFICULTY));
        BattleConfig config = new BattleConfig();
        config.difficulty = d.ordinal();
        d.configure(config);
        bgm = Bgm.get(this);
        FrameLayout root = new FrameLayout(this);
        view = new BattleView(this, new World(config), d, this, bgm, this);
        root.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cutscene = new CutscenePlayer(this, root, this); // 进战斗就开始准备视频
        setContentView(root);
        SystemUi.immersive(this);
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
        cutscene.setForeground(true);
    }

    @Override
    protected void onPause() {
        view.setResumed(false);
        cutscene.setForeground(false);
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
        cutscene.release();
        super.onDestroy();
    }

    /* ---------------- 领域展开的过场（主线程） ---------------- */

    @Override
    public void startCutscene(int castId) {
        if (isFinishing() || isDestroyed()) return;
        view.setInputBlocked(true);
        cutscene.play(castId);
    }

    @Override
    public void cutsceneDone(int castId) {
        view.setInputBlocked(false);
        view.endCutscene(castId);
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
