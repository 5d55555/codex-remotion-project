package com.pocketcodex.app;

import android.content.Context;
import android.os.Build;
import com.pocketcodex.core.CodexRuntime;
import com.pocketcodex.core.Installer;
import java.io.File;

/**
 * 私有目录布局（R = noBackupFilesDir/runtime，不参与系统备份）：
 * <pre>
 *   R/versions/0.166.0/bin/{codex,codex-code-mode-host}   安装的 Codex CLI（Installer 管）
 *   R/current                                            当前版本
 *   R/codex-home/   CODEX_HOME（auth.json、config.toml、会话记录）
 *   R/home/         HOME
 *   R/shim/bin/bash → /system/bin/sh
 *   R/certs/ca.pem  导出的 CA 证书
 *   R/tmp/ R/logs/ R/downloads/
 *   R/workspaces/<名字>/   App 私有的工作区
 * </pre>
 */
public final class Paths {
    public final File root, codexHome, home, shimBin, certs, caFile, tmp, logs, workspaces;

    public Paths(Context c) {
        root = new File(c.getNoBackupFilesDir(), "runtime");
        codexHome = new File(root, "codex-home");
        home = new File(root, "home");
        shimBin = new File(root, "shim/bin");
        certs = new File(root, "certs");
        caFile = new File(certs, "ca.pem");
        tmp = new File(root, "tmp");
        logs = new File(root, "logs");
        workspaces = new File(root, "workspaces");
    }

    /** 测试用：假装成别的处理器。 */
    static String abiOverride;

    /** 这台设备用哪个构建：只认 64 位 ARM 和 x86_64。 */
    public static String abi() {
        if (abiOverride != null) return abiOverride;
        for (String a : Build.SUPPORTED_ABIS) {
            if (a.equals("arm64-v8a") || a.equals("x86_64")) return a;
        }
        return Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
    }

    public Installer installer() throws Installer.InstallException {
        return new Installer(root, abi());
    }

    public CodexRuntime.Layout layout(File codexBin) {
        return new CodexRuntime.Layout(codexBin, codexHome, home, shimBin, tmp, caFile, logs, systemShell());
    }

    /** 安卓的 shell；极少数系统没有 /system/bin/sh 时退回 toybox 的 sh。 */
    public static String systemShell() {
        return new File("/system/bin/sh").exists() ? "/system/bin/sh" : "/system/bin/toybox";
    }

    public File authFile() {
        return new File(codexHome, "auth.json");
    }
}
