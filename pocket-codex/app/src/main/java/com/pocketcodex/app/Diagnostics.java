package com.pocketcodex.app;

import android.os.Build;
import com.pocketcodex.core.CodexRuntime;
import com.pocketcodex.core.DeviceAuth;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 自检：真机上没法提前验证的几件事，在这里逐项跑一遍，结果可以复制给开发者。
 * 不含登录代码、代理口令、账号邮箱。
 */
final class Diagnostics {
    interface Callback {
        void onReport(String report);
    }

    private Diagnostics() {
    }

    static String run(CodexRuntimeManager rt, File bin) {
        StringBuilder r = new StringBuilder();
        r.append("Pocket Codex ").append(BuildConfig.VERSION_NAME).append(" 自检\n");
        r.append("设备：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("，Android ").append(Build.VERSION.RELEASE).append("（API ").append(Build.VERSION.SDK_INT).append("）\n");
        r.append("ABI：").append(String.join(",", Build.SUPPORTED_ABIS)).append("；选用 ").append(Paths.abi()).append('\n');
        r.append("页大小：").append(pageSize()).append('\n');
        r.append("可用空间：").append(rt.paths.root.getParentFile().getUsableSpace() >> 20).append(" MB\n");
        r.append("Codex CLI：").append(bin == null ? "未安装" : bin.getParentFile().getParentFile().getName()).append('\n');
        if (bin == null) return r.toString();
        Map<String, String> env;
        try {
            env = rt.envForDiagnostics(bin);
        } catch (Exception e) {
            r.append("✗ 准备环境失败：").append(e.getMessage()).append('\n');
            return r.toString();
        }
        CodexRuntime.Layout l = rt.paths.layout(bin);
        step(r, "codex --version", run(CodexRuntime.command(l, "--version"), env, rt.paths.home, 20));
        File host = new File(bin.getParentFile(), "codex-code-mode-host");
        step(r, "code-mode-host 可执行", host.canExecute() ? new Result(0, host.length() / (1 << 20) + " MB") : new Result(-1, "不可执行"));
        step(r, "bash 兼容链接（-lc）", run(listOf(new File(l.binDir, "bash").getAbsolutePath(), "-lc", "echo ok:$0; echo PATH=$PATH | head -c 200"), env, rt.paths.home, 10));
        step(r, "系统命令", run(listOf(new File(l.binDir, "bash").getAbsolutePath(), "-lc", "ls / | head -3; grep --version 2>&1 | head -1; sed --version 2>&1 | head -1"), env, rt.paths.home, 10));
        step(r, "写临时文件", run(listOf(new File(l.binDir, "bash").getAbsolutePath(), "-lc", "echo hi > \"$TMPDIR/diag.txt\" && cat \"$TMPDIR/diag.txt\" && rm \"$TMPDIR/diag.txt\""), env, rt.paths.home, 10));
        step(r, "Java 网络（chatgpt.com）", httpCheck("https://chatgpt.com/"));
        step(r, "CA 证书", rt.paths.caFile.isFile() ? new Result(0, rt.paths.caFile.length() / 1024 + " KB") : new Result(-1, "没有导出"));
        r.append("本地代理：").append(rt.proxyForDiagnostics() == null ? "未启动" : "端口 " + rt.proxyForDiagnostics().port()
                + "，隧道 " + rt.proxyForDiagnostics().openTunnels.get() + "，下行 " + (rt.proxyForDiagnostics().bytesDown.get() >> 10) + " KB"
                + (rt.proxyForDiagnostics().lastError() == null ? "" : "，最近错误：" + rt.proxyForDiagnostics().lastError())).append('\n');
        r.append("app-server：").append(rt.serverAlive() ? "运行中，initialize " + rt.initializeMs + " ms，版本 " + rt.serverVersion : "未运行").append('\n');
        r.append("登录：").append(rt.paths.authFile().isFile() ? "已保存凭据（" + (rt.accountType == null ? "未验证" : rt.accountType + (rt.accountPlan == null ? "" : " · " + rt.accountPlan)) + "）" : "未登录").append('\n');
        return r.toString();
    }

    private static List<String> listOf(String... s) {
        List<String> l = new ArrayList<>();
        for (String x : s) l.add(x);
        return l;
    }

    static final class Result {
        final int code;
        final String out;

        Result(int code, String out) {
            this.code = code;
            this.out = out;
        }
    }

    private static void step(StringBuilder r, String name, Result res) {
        r.append(res.code == 0 ? "✓ " : "✗ ").append(name);
        String o = res.out == null ? "" : res.out.trim();
        if (!o.isEmpty()) r.append("：").append(o.replace("\n", " ⏎ "));
        r.append('\n');
    }

    private static Result run(List<String> cmd, Map<String, String> env, File cwd, int timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().clear();
            pb.environment().putAll(env);
            pb.directory(cwd);
            cwd.mkdirs();
            pb.redirectErrorStream(true);
            long t0 = System.nanoTime();
            Process p = pb.start();
            p.getOutputStream().close();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            InputStream in = p.getInputStream();
            Thread t = new Thread(() -> {
                byte[] buf = new byte[4096];
                int n;
                try {
                    while ((n = in.read(buf)) >= 0) if (b.size() < 4096) b.write(buf, 0, n);
                } catch (Exception ignored) {
                    // 进程结束
                }
            });
            t.start();
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(-1, "超时");
            }
            t.join(1000);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            String out = DeviceAuth.stripAnsi(new String(b.toByteArray(), StandardCharsets.UTF_8));
            int code = p.exitValue();
            return new Result(code, (code == 0 ? "" : "退出码 " + code + "，") + ms + " ms，" + out);
        } catch (Exception e) {
            return new Result(-1, e.toString());
        }
    }

    private static Result httpCheck(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            c.setInstanceFollowRedirects(false);
            long t0 = System.nanoTime();
            int code = c.getResponseCode();
            c.disconnect();
            return new Result(0, "HTTP " + code + "，" + (System.nanoTime() - t0) / 1_000_000 + " ms");
        } catch (Exception e) {
            return new Result(-1, e.toString());
        }
    }

    private static String pageSize() {
        try {
            long ps = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE);
            return (ps / 1024) + " KB";
        } catch (Throwable t) {
            return "未知";
        }
    }
}
