package com.pocketcodex.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.pocketcodex.core.Installer;

/** 用户设置（SharedPreferences）。 */
public final class Prefs {
    /** 运行模式：每步确认（untrusted）/ 完全自动（never）。 */
    public static final String MODE_CONFIRM = "untrusted", MODE_AUTO = "never";

    private final SharedPreferences p;

    public Prefs(Context c) {
        p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public String source() {
        return p.getString("source", "auto");
    }

    public void setSource(String s) {
        p.edit().putString("source", s).apply();
    }

    /** 要安装的版本：默认是测试过的版本。 */
    public String targetVersion() {
        return p.getString("version", Installer.TESTED_VERSION);
    }

    public void setTargetVersion(String v) {
        p.edit().putString("version", v).apply();
    }

    public String mode() {
        return p.getString("mode", MODE_CONFIRM);
    }

    public void setMode(String m) {
        p.edit().putString("mode", m).apply();
    }

    public String model() {
        return p.getString("model", null);
    }

    public void setModel(String m) {
        p.edit().putString("model", m).apply();
    }

    public String effort() {
        return p.getString("effort", null);
    }

    public void setEffort(String e) {
        p.edit().putString("effort", e).apply();
    }

    /** 速度：null = 标准，"priority" = 快速（用量更高）。 */
    public String speed() {
        return p.getString("speed", null);
    }

    public void setSpeed(String s) {
        p.edit().putString("speed", s).apply();
    }

    /** 顶部的“聊天 / 工作”：true = 工作（Agent 在工作区里改文件跑命令）。 */
    public boolean workMode() {
        return p.getBoolean("work_mode", true);
    }

    public void setWorkMode(boolean b) {
        p.edit().putBoolean("work_mode", b).apply();
    }

    public boolean userCa() {
        return p.getBoolean("user_ca", false);
    }

    public void setUserCa(boolean b) {
        p.edit().putBoolean("user_ca", b).apply();
    }

    public String workspace() {
        return p.getString("workspace", null);
    }

    public void setWorkspace(String path) {
        p.edit().putString("workspace", path).apply();
    }

    public int theme() {
        return p.getInt("theme", 0); // 0 跟随系统，1 浅色，2 深色
    }

    public void setTheme(int t) {
        p.edit().putInt("theme", t).apply();
    }

    /** 开发工具包上次成功用的软件源（Toolkit.Repo.id）。 */
    public String toolkitRepo() {
        return p.getString("toolkitRepo", null);
    }

    public void setToolkitRepo(String id) {
        p.edit().putString("toolkitRepo", id).apply();
    }

    /** 用户在移动网络下选了“以后再装”。 */
    public boolean toolkitDeferred() {
        return p.getBoolean("toolkitDeferred", false);
    }

    public void setToolkitDeferred(boolean v) {
        p.edit().putBoolean("toolkitDeferred", v).apply();
    }

    public boolean introSeen() {
        return p.getBoolean("intro_seen", false);
    }

    public void setIntroSeen() {
        p.edit().putBoolean("intro_seen", true).apply();
    }

    public boolean sandboxNoticeSeen() {
        return p.getBoolean("sandbox_notice", false);
    }

    public void setSandboxNoticeSeen() {
        p.edit().putBoolean("sandbox_notice", true).apply();
    }
}
