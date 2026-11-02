package com.shinjuku.showdown;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 设置（v0.16，主页“设置”）：总音量、音乐音量、技能音效的开关和音量，都存在 SharedPreferences “settings” 里，
 * 默认 100%、开。音量 0–100 按平方换成线性增益（听感更均匀：50% ≈ −12 dB），100% 就是原样。
 * 总音量管音乐、技能音效和领域展开的过场视频；音乐、技能音效各自再乘自己的那一项。
 */
final class Settings {
    static final String PREFS = "settings";
    static final String MASTER = "master_volume", MUSIC = "music_volume", SFX = "sfx_volume", SFX_ON = "sfx_enabled";

    private Settings() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int get(Context c, String key) {
        return clamp(prefs(c).getInt(key, 100));
    }

    static void set(Context c, String key, int pct) {
        prefs(c).edit().putInt(key, clamp(pct)).apply();
    }

    static boolean sfxOn(Context c) {
        return prefs(c).getBoolean(SFX_ON, true);
    }

    static void setSfxOn(Context c, boolean on) {
        prefs(c).edit().putBoolean(SFX_ON, on).apply();
    }

    /** 0–100 → 线性增益（平方律）。 */
    static float gain(int pct) {
        float k = clamp(pct) / 100f;
        return k * k;
    }

    static float musicGain(Context c) {
        return gain(get(c, MASTER)) * gain(get(c, MUSIC));
    }

    static float sfxGain(Context c) {
        return gain(get(c, MASTER)) * gain(get(c, SFX));
    }

    static float videoGain(Context c) {
        return gain(get(c, MASTER));
    }

    private static int clamp(int pct) {
        return Math.max(0, Math.min(100, pct));
    }
}
