package com.shinjuku.showdown;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Looper;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowMediaPlayer;

import java.lang.reflect.Field;
import java.time.Duration;

/**
 * 整条路径：主页 → 点开始游戏 → 选难度 → 点每一张卡 → 战斗 → 暂停、恢复、退出。
 * Android 9（旧的系统栏接口）和 Android 16（WindowInsetsController）各跑一遍。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class SmokeTest {

    @Before
    public void setUp() throws Exception {
        ShadowLog.stream = System.out; // 应用里 Log.w 之类的输出也打出来，方便看有没有被吞掉的异常
        // 模拟的 MediaPlayer 不解码文件：告诉它这是一段 2 分钟的音频
        ShadowMediaPlayer.setMediaInfoProvider(ds -> new ShadowMediaPlayer.MediaInfo(120_000, 0));
        // Bgm 是进程级单例，每个测试从头来
        Field f = Bgm.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    /** 点开始游戏：背景音乐真的开始播放。 */
    @Test
    public void startGamePlaysMusic() throws Exception {
        ActivityController<MainActivity> main = Robolectric.buildActivity(MainActivity.class).setup();
        main.get().findViewById(R.id.btn_start).performClick();
        Bgm bgm = Bgm.get(main.get());
        Field pf = Bgm.class.getDeclaredField("player");
        pf.setAccessible(true);
        ShadowMediaPlayer.State state = null;
        for (int i = 0; i < 300 && state != ShadowMediaPlayer.State.STARTED; i++) {
            idle();
            Thread.sleep(10); // bgm 线程是真线程
            MediaPlayer p = (MediaPlayer) pf.get(bgm);
            state = p == null ? null : shadowOf(p).getState();
        }
        assertEquals(ShadowMediaPlayer.State.STARTED, state);
        main.pause().stop().destroy();
    }

    @Test
    public void startGameOpensSelectAndEveryDifficultyOpensBattle() {
        ActivityController<MainActivity> main = Robolectric.buildActivity(MainActivity.class).setup();
        main.get().findViewById(R.id.btn_start).performClick();
        idle();
        Intent toSelect = shadowOf(main.get()).getNextStartedActivity();
        assertNotNull("点开始游戏要打开选难度界面", toSelect);
        assertEquals(SelectActivity.class.getName(), toSelect.getComponent().getClassName());
        main.pause().stop();

        ActivityController<SelectActivity> select = Robolectric.buildActivity(SelectActivity.class, toSelect).setup();
        LinearLayout cards = select.get().findViewById(R.id.cards);
        assertEquals(Difficulty.values().length, cards.getChildCount());
        assertEquals(5, cards.getChildCount());
        for (int i = 0; i < cards.getChildCount(); i++) {
            cards.getChildAt(i).performClick();
            idle();
            Intent toBattle = shadowOf(select.get()).getNextStartedActivity();
            assertNotNull(toBattle);
            assertEquals(Difficulty.values()[i].name(), toBattle.getStringExtra(BattleActivity.EXTRA_DIFFICULTY));
            select.pause().stop();

            ActivityController<BattleActivity> battle = Robolectric.buildActivity(BattleActivity.class, toBattle).setup();
            idle();
            battle.get().onWindowFocusChanged(true);
            battle.pause().resume();
            battle.get().toggleMusic();
            battle.get().toggleMusic();
            battle.get().toggleStats();
            idle();
            battle.pause().stop().destroy();
            idle();

            select.restart().start().resume();
        }
        select.pause().stop().destroy();
        main.restart().start().resume().pause().stop().destroy();
        idle();
    }

    /**
     * 领域展开的过场：开始时盖上幕布、战斗不收触摸；视频起不来（这里没有真的 Surface）时 2 秒后自己结束，
     * 幕布撤掉、战斗接着打。切后台不计时。
     */
    @Test
    public void cutsceneEndsEvenWithoutVideoSurface() throws Exception {
        Intent i = new Intent().putExtra(BattleActivity.EXTRA_DIFFICULTY, Difficulty.TEST.name());
        ActivityController<BattleActivity> battle = Robolectric.buildActivity(BattleActivity.class, i).setup();
        idle();
        Field cf = BattleActivity.class.getDeclaredField("cutscene");
        cf.setAccessible(true);
        CutscenePlayer cutscene = (CutscenePlayer) cf.get(battle.get());
        battle.get().startCutscene(1);
        assertTrue(cutscene.isPlaying());
        // 后台：不计时
        battle.pause();
        for (int k = 0; k < 30; k++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            Thread.sleep(5);
        }
        assertTrue("后台不该超时", cutscene.isPlaying());
        battle.resume();
        for (int k = 0; k < 60 && cutscene.isPlaying(); k++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            Thread.sleep(5); // cutscene 线程是真线程
        }
        assertFalse("首帧超时后应该自己结束", cutscene.isPlaying());
        battle.pause().stop().destroy();
        idle();
    }

    @Test
    public void battleSurvivesRecreateAndMissingExtra() {
        // 没带难度（被系统重建等情况）：按默认难度进
        ActivityController<BattleActivity> battle = Robolectric.buildActivity(BattleActivity.class, new Intent()).setup();
        idle();
        battle.pause().stop().destroy();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
