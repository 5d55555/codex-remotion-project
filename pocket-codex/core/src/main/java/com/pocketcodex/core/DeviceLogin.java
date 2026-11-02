package com.pocketcodex.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 按用户的要求：后台跑 `codex login --device-auth`，输出重定向到文件，从文件里解析出网址和一次性代码交给界面；
 * 代码过期（或进程因过期退出）就自动重新获取；登录成功后结束。
 * <p>
 * 线程：start() 起一个后台线程负责整个过程；状态通过 {@link Listener} 在这个线程上回调。
 */
public final class DeviceLogin {

    public enum Phase {
        /** 正在启动 codex / 等它输出代码。 */
        STARTING,
        /** 拿到代码了，等用户去浏览器输入。 */
        WAITING,
        /** 上一个代码过期了，正在换新的。 */
        REFRESHING,
        /** 网络等问题，几秒后自动重试。 */
        RETRYING,
        SUCCESS,
        /** 需要用户处理才能继续（设备码授权没开、连续失败太多次）。 */
        FAILED,
        /** 用户取消。 */
        STOPPED
    }

    public static final class State {
        public final Phase phase;
        public final String url, code;
        /** 代码过期的时刻（按 {@link Clock}），没有代码时为 0。 */
        public final long expiresAtMs;
        /** 代码的总有效期，画倒计时环用。 */
        public final long lifetimeMs;
        /** 出错或重试时给用户看的一句话（中文），以及原始输出的最后几行。 */
        public final String message, detail;
        public final DeviceAuth.Outcome outcome;
        /** 这是第几个代码（自动刷新会加一）。 */
        public final int codeNumber;
        /** RETRYING 时下一次重试的时刻。 */
        public final long retryAtMs;

        public State(Phase phase, String url, String code, long expiresAtMs, long lifetimeMs, String message, String detail,
                     DeviceAuth.Outcome outcome, int codeNumber, long retryAtMs) {
            this.phase = phase;
            this.url = url;
            this.code = code;
            this.expiresAtMs = expiresAtMs;
            this.lifetimeMs = lifetimeMs;
            this.message = message;
            this.detail = detail;
            this.outcome = outcome;
            this.codeNumber = codeNumber;
            this.retryAtMs = retryAtMs;
        }
    }

    public interface Listener {
        void onState(State s);
    }

    /** 时钟：安卓上用 SystemClock.elapsedRealtime()（含休眠、不受改系统时间影响）。State 里的时刻都按它。 */
    public interface Clock {
        long nowMs();
    }

    public static final Clock MONOTONIC = () -> System.nanoTime() / 1_000_000L;
    private Clock clock = MONOTONIC;

    public void setClock(Clock c) {
        clock = c;
    }

    /** 测试和真机都用 ProcessBuilder；测试可以换成假的 codex 脚本。 */
    private final List<String> command;
    private final Map<String, String> env;
    private final File outputFile;
    private final File authFile;
    private final Listener listener;
    /** 提前多久刷新（避免用户刚输完代码就过期）。 */
    private final long refreshMarginMs;
    /** 测试用：覆盖输出里写的有效期。 */
    private final long lifetimeOverrideMs;

    private volatile boolean stopRequested;
    private volatile boolean refreshRequested;
    private volatile Process process;
    private Thread thread;
    private volatile State last;

    public DeviceLogin(List<String> command, Map<String, String> env, File outputFile, File authFile, Listener listener) {
        this(command, env, outputFile, authFile, listener, 20_000, -1);
    }

    public DeviceLogin(List<String> command, Map<String, String> env, File outputFile, File authFile, Listener listener,
                       long refreshMarginMs, long lifetimeOverrideMs) {
        this.command = command;
        this.env = env;
        this.outputFile = outputFile;
        this.authFile = authFile;
        this.listener = listener;
        this.refreshMarginMs = refreshMarginMs;
        this.lifetimeOverrideMs = lifetimeOverrideMs;
    }

    public synchronized void start() {
        if (thread != null) return;
        stopRequested = false;
        thread = new Thread(this::run, "device-login");
        thread.setDaemon(true);
        thread.start();
    }

    /** 用户点“换一个代码”。 */
    public void refreshNow() {
        refreshRequested = true;
        Process p = process;
        if (p != null) p.destroy();
    }

    public void stop() {
        stopRequested = true;
        Process p = process;
        if (p != null) p.destroy();
        Thread t = thread;
        if (t != null) t.interrupt();
    }

    public State lastState() {
        return last;
    }

    private void emit(State s) {
        last = s;
        listener.onState(s);
    }

    private void run() {
        int codeNumber = 0;
        int failures = 0;
        boolean refreshing = false;
        try {
            while (!stopRequested) {
                emit(new State(refreshing ? Phase.REFRESHING : Phase.STARTING, null, null, 0, 0, null, null, null, codeNumber, 0));
                refreshRequested = false;
                File parent = outputFile.getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.environment().clear();
                pb.environment().putAll(env);
                pb.redirectErrorStream(true);
                pb.redirectOutput(outputFile); // 按要求：输出重定向到文件，App 再读文件
                Process p;
                try {
                    p = pb.start();
                } catch (IOException e) {
                    emit(new State(Phase.FAILED, null, null, 0, 0, "启动 Codex CLI 失败：" + e.getMessage(), null, DeviceAuth.Outcome.FAILED, codeNumber, 0));
                    return;
                }
                process = p;
                DeviceAuth.Info info = null;
                long expiresAt = 0, lifetime = 0;
                boolean expiredByClock = false;
                long startedAt = clock.nowMs();
                // 盯着文件：拿到代码后等进程结束或到期
                while (true) {
                    if (stopRequested) {
                        kill(p);
                        emit(new State(Phase.STOPPED, null, null, 0, 0, null, null, DeviceAuth.Outcome.CANCELLED, codeNumber, 0));
                        return;
                    }
                    boolean exited = p.waitFor(250, TimeUnit.MILLISECONDS);
                    if (info == null) {
                        DeviceAuth.Info i = DeviceAuth.parse(readFile(outputFile));
                        if (i != null) {
                            info = i;
                            codeNumber++;
                            lifetime = lifetimeOverrideMs > 0 ? lifetimeOverrideMs : i.expiresInSec * 1000L;
                            // 有效期从 codex 打印代码时算；我们读到时最多晚 0.25 秒
                            expiresAt = clock.nowMs() + lifetime;
                            emit(new State(Phase.WAITING, i.url, i.code, expiresAt, lifetime, null, null, null, codeNumber, 0));
                        } else if (!exited && clock.nowMs() - startedAt > 60_000) {
                            kill(p); // 一分钟还没拿到代码：多半是网络卡住了
                            break;
                        }
                    }
                    if (exited) break;
                    if (refreshRequested) {
                        kill(p);
                        break;
                    }
                    if (info != null && clock.nowMs() >= expiresAt - refreshMarginMs) {
                        expiredByClock = true;
                        kill(p);
                        break;
                    }
                }
                process = null;
                String out = readFile(outputFile);
                int code = exitValue(p);
                boolean auth = authFile.isFile() && authFile.length() > 2;
                if (code == 0 && auth) {
                    emit(new State(Phase.SUCCESS, null, null, 0, 0, null, null, DeviceAuth.Outcome.SUCCESS, codeNumber, 0));
                    return;
                }
                if (stopRequested) continue;
                if (refreshRequested || expiredByClock) { // 到期或用户要求：直接换新代码
                    if (expiredByClock) failures = 0;
                    refreshing = true;
                    continue;
                }
                DeviceAuth.Outcome o = DeviceAuth.classify(code, out, auth);
                String tail = DeviceAuth.tail(out, 6);
                // 真正的过期至少要等很久；代码刚出来没多久就“过期”退出，按失败处理（退避），免得死循环
                boolean genuineExpiry = info != null && clock.nowMs() >= expiresAt - lifetime / 2;
                if (o == DeviceAuth.Outcome.EXPIRED && genuineExpiry) {
                    failures = 0; // 正常等满后过期：不算失败
                    refreshing = true;
                    continue;
                }
                if (o == DeviceAuth.Outcome.CANCELLED) { // 用户在网页上取消/拒绝：停下来，让用户决定要不要重来
                    emit(new State(Phase.FAILED, null, null, 0, 0, "本次授权已取消。需要的话点“重新登录”。", tail, o, codeNumber, 0));
                    return;
                }
                if (o == DeviceAuth.Outcome.DEVICE_AUTH_DISABLED) {
                    emit(new State(Phase.FAILED, null, null, 0, 0,
                            "这个账号还没开启“设备码授权”。请在 ChatGPT 网页版：设置 → 安全 → 开启 Codex 的设备码授权（团队账号需要管理员允许），然后点重试。",
                            tail, o, codeNumber, 0));
                    return;
                }
                failures++;
                if (failures >= 6) {
                    emit(new State(Phase.FAILED, null, null, 0, 0, failureMessage(o), tail, o, codeNumber, 0));
                    return;
                }
                long wait = Math.min(30_000, 2000L << (failures - 1));
                long retryAt = clock.nowMs() + wait;
                emit(new State(Phase.RETRYING, null, null, 0, 0, failureMessage(o), tail, o, codeNumber, retryAt));
                while (!stopRequested && !refreshRequested && clock.nowMs() < retryAt) Thread.sleep(100);
                refreshing = false;
            }
            emit(new State(Phase.STOPPED, null, null, 0, 0, null, null, DeviceAuth.Outcome.CANCELLED, codeNumber, 0));
        } catch (InterruptedException e) {
            Process p = process;
            if (p != null) kill(p);
            emit(new State(Phase.STOPPED, null, null, 0, 0, null, null, DeviceAuth.Outcome.CANCELLED, codeNumber, 0));
        } finally {
            synchronized (this) {
                thread = null;
            }
        }
    }

    static String failureMessage(DeviceAuth.Outcome o) {
        switch (o) {
            case NETWORK:
                return "连不上 OpenAI 的登录服务。请检查网络（需要能访问 auth.openai.com），稍后自动重试。";
            case CANCELLED:
                return "授权被取消或拒绝了，正在重新获取代码。";
            default:
                return "登录没有成功，正在重新获取代码。";
        }
    }

    private static void kill(Process p) {
        p.destroy();
        try {
            if (!p.waitFor(1500, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                p.waitFor(1500, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static int exitValue(Process p) {
        try {
            return p.exitValue();
        } catch (IllegalThreadStateException e) {
            return -1;
        }
    }

    static String readFile(File f) {
        if (!f.isFile()) return "";
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[256 * 1024];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
