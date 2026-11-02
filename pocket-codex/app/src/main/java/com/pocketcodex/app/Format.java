package com.pocketcodex.app;

import java.io.File;
import java.util.Locale;

/** 显示用的格式化。 */
final class Format {
    private Format() {
    }

    /** 十进制 MB（和下载站显示的一致）。 */
    static String mb(long bytes) {
        if (bytes < 0) return "?";
        double m = bytes / 1e6;
        return m >= 100 ? String.format(Locale.ROOT, "%.0f MB", m) : String.format(Locale.ROOT, "%.1f MB", m);
    }

    static String speed(double bps) {
        if (bps <= 0) return "";
        double m = bps / 1e6;
        return m >= 1 ? String.format(Locale.ROOT, "%.1f MB/s", m) : String.format(Locale.ROOT, "%.0f KB/s", bps / 1e3);
    }

    /** 剩余时间：数据不够时返回空串。 */
    static String eta(long done, long total, double bps) {
        if (total <= 0 || bps <= 1024 || done >= total) return "";
        long s = (long) ((total - done) / bps);
        if (s < 60) return "约 " + s + " 秒";
        if (s < 3600) return "约 " + (s / 60) + " 分 " + (s % 60) + " 秒";
        return "约 " + (s / 3600) + " 小时";
    }

    /** mm:ss。 */
    static String clock(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    static String duration(long ms) {
        if (ms < 1000) return ms + " ms";
        if (ms < 60_000) return String.format(Locale.ROOT, "%.1f 秒", ms / 1000.0);
        return (ms / 60_000) + " 分 " + (ms / 1000 % 60) + " 秒";
    }

    /** 相对时间：刚刚 / 5 分钟前 / 3 小时前 / 昨天 / 10月30日。 */
    static String ago(long epochSec) {
        if (epochSec <= 0) return "";
        long now = System.currentTimeMillis() / 1000;
        long d = now - epochSec;
        if (d < 60) return "刚刚";
        if (d < 3600) return (d / 60) + " 分钟前";
        if (d < 86400) return (d / 3600) + " 小时前";
        if (d < 2 * 86400) return "昨天";
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(epochSec * 1000);
        return (c.get(java.util.Calendar.MONTH) + 1) + "月" + c.get(java.util.Calendar.DAY_OF_MONTH) + "日";
    }

    static String workspaceName(CodexRuntimeManager rt) {
        String w = rt.prefs.workspace();
        return w == null ? "工作区" : new File(w).getName();
    }

    /** 把 /storage/emulated/0/… 显示成“手机存储/…”，私有工作区显示成“App 内/…”。 */
    static String prettyPath(CodexRuntimeManager rt, String path) {
        if (path == null) return "";
        String priv = rt.paths.workspaces.getAbsolutePath();
        if (path.startsWith(priv)) return "App 内" + path.substring(priv.length());
        if (path.startsWith("/storage/emulated/0")) return "手机存储" + path.substring("/storage/emulated/0".length());
        if (path.startsWith("/sdcard")) return "手机存储" + path.substring("/sdcard".length());
        return path;
    }
}
