package com.shinjuku.showdown;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * 游戏里的音效输出（v0.15）：一个 SoundPool，进战斗时把 SfxBank 里的全部 OGG 预先解码。
 * play / loop / stopLoop / stopAll 在战斗线程上调用；setEnabled、pause、resume、release 在主线程上调用（SoundPool 自己加锁）。
 * 还没解码好的音效直接跳过（不排队，晚响比不响更糟）。触发路径零分配。
 */
final class SoundPoolOut implements SfxOut {
    private static final int MAX_STREAMS = 20, RECENT = 24;

    private final SoundPool pool;
    private final int[][] ids;          // [cue][变体] → SoundPool 的 sampleId
    private final boolean[] ready;      // 按 sampleId：解码好了
    private volatile int loaded;        // 解码好的个数（写 ready 之后再写它：战斗线程先读它再读 ready，保证看得到）
    private volatile boolean enabled = true;
    private volatile boolean flush;     // 开关刚切过：战斗线程下一次调用时把流全停掉（流的记录只在战斗线程上动）
    private final int[] loopStream = new int[SfxBank.LOOP_SLOTS], loopCue = new int[SfxBank.LOOP_SLOTS];
    private final float[] loopL = new float[SfxBank.LOOP_SLOTS], loopR = new float[SfxBank.LOOP_SLOTS];
    private final int[] recent = new int[RECENT]; // 最近的单发 streamId（stopAll 要停它们；SoundPool 自己没有“全停”）
    private int recentHead;

    SoundPoolOut(Context context) {
        pool = new SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        int total = 0;
        for (int[] v : SfxBank.RES) total += v.length;
        ready = new boolean[total + 2];
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            if (status == 0 && sampleId > 0 && sampleId < ready.length) {
                ready[sampleId] = true;
                loaded++;
            }
        });
        ids = new int[SfxBank.RES.length][];
        Context app = context.getApplicationContext();
        for (int c = 0; c < SfxBank.RES.length; c++) {
            ids[c] = new int[SfxBank.RES[c].length];
            for (int v = 0; v < ids[c].length; v++) ids[c][v] = pool.load(app, SfxBank.RES[c][v], 1);
        }
        java.util.Arrays.fill(loopCue, -1);
    }

    private boolean ready(int sampleId) {
        return loaded > 0 && sampleId > 0 && sampleId < ready.length && ready[sampleId];
    }

    @Override
    public int play(int cue, int variant, float pan, float gain, float rate, int priority) {
        if (flush) flushNow();
        if (!enabled) return 0;
        int[] v = ids[cue];
        int id = v[variant % v.length];
        if (!ready(id)) return 0;
        float g = Math.max(0, Math.min(1, gain));
        int s = pool.play(id, g * Math.min(1, 1 - pan), g * Math.min(1, 1 + pan), priority, 0, Math.max(0.5f, Math.min(2, rate)));
        if (s != 0) {
            recent[recentHead] = s;
            recentHead = (recentHead + 1) % RECENT;
        }
        return s;
    }

    @Override
    public void stop(int handle) {
        if (handle != 0) pool.stop(handle);
    }

    @Override
    public void loop(int slot, int cue, float pan, float gain) {
        if (flush) flushNow();
        if (!enabled) return;
        float g = Math.max(0, Math.min(1, gain));
        float l = g * Math.min(1, 1 - pan), r = g * Math.min(1, 1 + pan);
        if (loopCue[slot] == cue && loopStream[slot] != 0) {
            if (Math.abs(l - loopL[slot]) > 0.01f || Math.abs(r - loopR[slot]) > 0.01f) {
                pool.setVolume(loopStream[slot], l, r);
                loopL[slot] = l;
                loopR[slot] = r;
            }
            return;
        }
        stopLoop(slot);
        int id = ids[cue][0];
        if (!ready(id)) return; // 还没解码好：下一 tick 再试
        int s = pool.play(id, l, r, 2, -1, 1f);
        if (s == 0) return;
        loopStream[slot] = s;
        loopCue[slot] = cue;
        loopL[slot] = l;
        loopR[slot] = r;
    }

    @Override
    public void stopLoop(int slot) {
        if (loopStream[slot] != 0) pool.stop(loopStream[slot]);
        loopStream[slot] = 0;
        loopCue[slot] = -1;
    }

    @Override
    public void stopAll() {
        for (int i = 0; i < loopStream.length; i++) stopLoop(i);
        for (int i = 0; i < RECENT; i++) {
            if (recent[i] != 0) pool.stop(recent[i]);
            recent[i] = 0;
        }
    }

    private void flushNow() {
        flush = false;
        stopAll();
    }

    /** 音效开关（主线程）：关掉时先整体暂停（立刻没声），战斗线程随后把流都停掉；打开后循环音由 SfxDirector 按状态重新起。 */
    void setEnabled(boolean on) {
        enabled = on;
        flush = true;
        if (!on) pool.autoPause();
        else pool.autoResume();
    }

    boolean isEnabled() {
        return enabled;
    }

    /** 切后台 / 回前台（主线程）。 */
    void pause() {
        pool.autoPause();
    }

    void resume() {
        if (enabled) pool.autoResume();
    }

    void release() {
        pool.release();
    }
}
