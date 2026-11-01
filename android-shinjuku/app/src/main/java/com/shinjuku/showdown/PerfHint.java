package com.shinjuku.showdown;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.PerformanceHintManager;
import android.os.Process;
import android.util.Log;

/**
 * ADPF 性能提示（Android 12+，v0.14）：告诉系统战斗线程每帧要在一个刷新周期内做完、实际做了多久，
 * 调度器据此把线程放到合适的核上、提前给够频率，而不是等掉了帧才升频。只在战斗线程上用；设备不支持或出错就安静地不用。
 * 单独一个类：低版本系统不会去加载 PerformanceHintManager。
 */
@TargetApi(Build.VERSION_CODES.S)
final class PerfHint {
    private static final String TAG = "PerfHint";
    private final PerformanceHintManager.Session session;
    private long targetNs;

    private PerfHint(PerformanceHintManager.Session session, long targetNs) {
        this.session = session;
        this.targetNs = targetNs;
    }

    /** 在战斗线程上调用：为当前线程开一个会话。不支持就返回 null。 */
    static PerfHint create(Context context, long targetNs) {
        try {
            PerformanceHintManager m = context.getSystemService(PerformanceHintManager.class);
            if (m == null || m.getPreferredUpdateRateNanos() < 0) return null; // -1：设备不支持
            PerformanceHintManager.Session s = m.createHintSession(new int[]{Process.myTid()}, targetNs);
            return s == null ? null : new PerfHint(s, targetNs);
        } catch (RuntimeException e) {
            Log.w(TAG, "createHintSession", e);
            return null;
        }
    }

    /** 每帧报一次：这一帧的工作量（逻辑 + 画，不含等缓冲区的时间）和当前刷新周期。出错返回 false（之后不再用）。 */
    boolean report(long workNs, long periodNs) {
        try {
            if (Math.abs(periodNs - targetNs) > 250_000) { // 刷新率变了（120 ↔ 60）：跟着改目标
                session.updateTargetWorkDuration(periodNs);
                targetNs = periodNs;
            }
            if (workNs > 0) session.reportActualWorkDuration(workNs);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "report", e);
            return false;
        }
    }

    void close() {
        try {
            session.close();
        } catch (RuntimeException ignored) {
            // 已经不可用
        }
    }
}
