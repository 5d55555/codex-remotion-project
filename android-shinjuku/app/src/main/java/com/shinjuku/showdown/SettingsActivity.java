package com.shinjuku.showdown;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 设置（v0.16，主页原来的“空置”）：总音量、音乐音量、技能音效的开关和音量，改了马上生效、自动记住（Settings）。
 * 音乐正在放的话拖动时直接听得到；调技能音效（和总音量）松手时放一声普通命中试听；技能音效关掉时音量条变暗。
 * 战斗里右上角的喇叭按钮和这里的开关是同一个设置。
 */
public class SettingsActivity extends Activity {
    private ShinjukuView scene;
    private Bgm bgm;
    private SoundPool preview;
    private int previewId;
    private volatile boolean previewReady;
    private SeekBar sfxSeek;
    private TextView sfxValue;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable previewLater = this::playPreview;
    private boolean touching; // 正在用手指拖滑块（松手时试听）；方向键、读屏调的在停下 0.3 秒后试听

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemUi.drawBehindSystemBars(this);
        setContentView(R.layout.activity_settings);
        scene = findViewById(R.id.scene);
        SystemUi.applySafeInsets(findViewById(R.id.settings_scroll), null);
        bgm = Bgm.get(this);

        preview = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        preview.setOnLoadCompleteListener((p, id, status) -> previewReady = status == 0);
        previewId = preview.load(this, R.raw.sfx_hit_medium_0, 1);

        bind(R.id.master_seek, R.id.master_value, Settings.MASTER, true);
        bind(R.id.music_seek, R.id.music_value, Settings.MUSIC, false);
        sfxSeek = bind(R.id.sfx_seek, R.id.sfx_value, Settings.SFX, true);
        sfxValue = findViewById(R.id.sfx_value);

        Switch sw = findViewById(R.id.sfx_switch);
        int blue = getColor(R.color.blue), dim = 0xFF4A5070, text = getColor(R.color.text);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{text, 0xFF9AA3C7}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{blue, dim}));
        sw.setChecked(Settings.sfxOn(this));
        sfxEnabledLook(sw.isChecked());
        sw.setOnCheckedChangeListener((b, on) -> {
            Settings.setSfxOn(this, on);
            sfxEnabledLook(on);
            if (on) playPreview();
        });

        findViewById(R.id.btn_settings_back).setOnClickListener(v -> finish());

        // 面板最宽 300dp，窗口窄（分屏、小窗）时跟着缩，两边各留 20dp
        View scroll = findViewById(R.id.settings_scroll), panel = findViewById(R.id.settings_panel);
        float dp = getResources().getDisplayMetrics().density;
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int avail = v.getWidth() - v.getPaddingLeft() - v.getPaddingRight() - Math.round(40 * dp);
            int w = Math.max(Math.round(200 * dp), Math.min(Math.round(300 * dp), avail));
            if (panel.getLayoutParams().width != w) {
                panel.getLayoutParams().width = w;
                panel.post(panel::requestLayout);
            }
        });
    }

    /** 一条滑块：显示当前值、拖动时保存并马上生效；preview 为 true 的松手时放一声试听。 */
    private SeekBar bind(int seekId, int valueId, String key, boolean preview) {
        SeekBar seek = findViewById(seekId);
        TextView value = findViewById(valueId);
        int v = Settings.get(this, key);
        seek.setProgress(v);
        value.setText(getString(R.string.percent, v));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                value.setText(getString(R.string.percent, progress));
                if (!fromUser) return;
                Settings.set(SettingsActivity.this, key, progress);
                bgm.setVolume(Settings.musicGain(SettingsActivity.this)); // 音乐在放的话马上听得到
                if (preview && !touching) { // 方向键、读屏调的：停下 0.3 秒后放一声（连着调只放一次）
                    main.removeCallbacks(previewLater);
                    main.postDelayed(previewLater, 300);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                touching = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                touching = false;
                if (preview) playPreview();
            }
        });
        return seek;
    }

    private void sfxEnabledLook(boolean on) {
        sfxSeek.setEnabled(on);
        sfxSeek.setAlpha(on ? 1f : 0.35f);
        sfxValue.setAlpha(on ? 1f : 0.35f);
    }

    private void playPreview() {
        if (!previewReady || !Settings.sfxOn(this)) return;
        float g = Settings.sfxGain(this);
        if (g > 0) preview.play(previewId, g, g, 1, 0, 1f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scene.setAnimationsAllowed(ValueAnimator.areAnimatorsEnabled());
        scene.setResumed(true);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(previewLater);
        scene.setResumed(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        preview.release();
        super.onDestroy();
    }
}
