package com.pocketcodex.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.pocketcodex.core.AppServer;
import com.pocketcodex.core.CodexRuntime;
import com.pocketcodex.core.Conversation;
import com.pocketcodex.core.DeviceLogin;
import com.pocketcodex.core.Installer;
import com.pocketcodex.core.Json;
import com.pocketcodex.core.LocalProxy;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 本地运行时的唯一所有者：安装 Codex CLI、设备码登录、本地代理、codex app-server、各个会话的状态。
 * <p>
 * 线程：对外的方法和所有状态都在主线程上；耗时操作在 worker 线程，结果切回主线程；
 * app-server 的通知在它的读线程收到后 post 到主线程，再交给 {@link Conversation}。
 */
public final class CodexRuntimeManager {

    public enum Stage {
        /** 刚启动，正在检查安装和登录状态。 */
        CHECKING,
        NOT_INSTALLED, INSTALLING, INSTALL_FAILED,
        NEED_LOGIN, LOGGING_IN,
        /** 正在启动 app-server。 */
        STARTING,
        READY,
        /** app-server 起不来或者意外退出。 */
        ERROR
    }

    public interface Listener {
        /** 阶段、安装进度、登录状态、账号、模型列表、会话列表等任何全局状态变了。 */
        void onRuntimeChanged();
    }

    public interface ConversationListener {
        void onConversationUpdate(Conversation c, Conversation.Update u);
    }

    /** 安装进度（界面显示 MB、速度、剩余时间）。 */
    public static final class InstallProgress {
        public Installer.Stage stage = Installer.Stage.METADATA;
        public long done, total = -1;
        public double bytesPerSec;
        public String sourceLabel;
    }

    /** 会话列表的一项。 */
    public static final class ThreadSummary {
        public final String id, name, preview, cwd;
        public final long updatedAt;

        ThreadSummary(String id, String name, String preview, String cwd, long updatedAt) {
            this.id = id;
            this.name = name;
            this.preview = preview;
            this.cwd = cwd;
            this.updatedAt = updatedAt;
        }

        public String title() {
            if (name != null && !name.trim().isEmpty()) return name.trim();
            if (preview != null && !preview.trim().isEmpty()) return preview.trim().replace('\n', ' ');
            return "新对话";
        }
    }

    public static final class ModelInfo {
        public final String id, displayName, description, defaultEffort;
        public final List<String> efforts;
        public final boolean isDefault;

        ModelInfo(String id, String displayName, String description, String defaultEffort, List<String> efforts, boolean isDefault) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.defaultEffort = defaultEffort;
            this.efforts = efforts;
            this.isDefault = isDefault;
        }
    }

    private static CodexRuntimeManager instance;

    /** 测试用：每个测试用新的实例（Robolectric 每个测试一个新的 Application）。 */
    static synchronized void resetForTest() {
        instance = null;
    }

    public static synchronized CodexRuntimeManager get(Context c) {
        if (instance == null) instance = new CodexRuntimeManager(c.getApplicationContext());
        return instance;
    }

    final Context ctx;
    public final Paths paths;
    public final Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "codex-worker");
        t.setDaemon(true);
        return t;
    });
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<ConversationListener> convListeners = new CopyOnWriteArrayList<>();

    /* ---- 状态（主线程） ---- */
    public Stage stage = Stage.CHECKING;
    public String errorMessage, errorDetail;
    public Installer.Kind installErrorKind;
    public final InstallProgress install = new InstallProgress();
    public DeviceLogin.State login;
    public String installedVersion;
    public String accountEmail, accountPlan, accountType;
    public final List<ModelInfo> models = new ArrayList<>();
    public final List<ThreadSummary> threads = new ArrayList<>();
    public boolean threadsLoaded;
    public long initializeMs = -1;
    public String serverVersion;
    private final Map<String, Conversation> conversations = new HashMap<>();
    /** 正在跑的回合所在的会话（全局同一时间只跑一个回合）。 */
    public String runningThreadId;

    /* ---- 运行时对象 ---- */
    private LocalProxy proxy;
    private AppServer server;
    private DeviceLogin deviceLogin;
    private volatile boolean installCancel;
    private int serverRestarts;
    /** 每次 app-server 启动成功加一；会话要在当前这一代里 resume 过才能发消息。 */
    private int generation;
    /** 每次开始启动 / 停止都加一：旧进程（或被放弃的启动）的回调对不上就丢掉。 */
    private int startToken;
    private boolean starting;
    private Runnable pendingRestart;
    /** 点了停止但还没拿到 turn id 的会话：turn/started 一到就中断。 */
    private final java.util.Set<String> interruptWanted = new java.util.HashSet<>();
    private final Map<String, Integer> resumedIn = new HashMap<>();
    private long firstRestartAt;

    private CodexRuntimeManager(Context ctx) {
        this.ctx = ctx;
        this.paths = new Paths(ctx);
        this.prefs = new Prefs(ctx);
    }

    /* ---------------- 监听 ---------------- */

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void addConversationListener(ConversationListener l) {
        convListeners.add(l);
    }

    public void removeConversationListener(ConversationListener l) {
        convListeners.remove(l);
    }

    private void changed() {
        for (Listener l : listeners) l.onRuntimeChanged();
        RuntimeService.sync(ctx, this);
    }

    private void onMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else main.post(r);
    }

    /* ---------------- 启动检查 ---------------- */

    /** App 打开时调用：看装没装、登没登录；都好了就启动 app-server。 */
    public void check() {
        if (stage == Stage.READY || stage == Stage.STARTING || stage == Stage.INSTALLING || stage == Stage.LOGGING_IN) return;
        worker.execute(() -> {
            String v = null;
            try {
                v = paths.installer().installedVersion();
            } catch (Installer.InstallException ignored) {
                // 不支持的 ABI：当成没装，安装时再报错
            }
            final String ver = v;
            final boolean auth = paths.authFile().isFile() && paths.authFile().length() > 2;
            onMain(() -> {
                installedVersion = ver;
                if (ver == null) setStage(Stage.NOT_INSTALLED);
                else if (!auth) setStage(Stage.NEED_LOGIN);
                else startServer();
            });
        });
    }

    private void setStage(Stage s) {
        stage = s;
        changed();
    }

    /* ---------------- 安装 ---------------- */

    private boolean forceInstall;

    /** 重新安装（同版本也重新下载校验；或者装设置里选的新版本）。 */
    public void reinstall() {
        stopServer();
        forceInstall = true;
        startInstall();
    }

    public interface LatestCallback {
        void onLatest(String version, String error);
    }

    /** 查 npm 上的最新版本号。 */
    public void checkLatest(LatestCallback cb) {
        worker.execute(() -> {
            String v = null, err = null;
            try {
                Installer.Source src = "npmmirror".equals(prefs.source()) ? Installer.NPMMIRROR : Installer.NPM;
                v = paths.installer().latestVersion(src);
            } catch (Installer.InstallException e) {
                err = e.getMessage();
            }
            final String v2 = v, e2 = err;
            onMain(() -> cb.onLatest(v2, e2));
        });
    }

    /** 新版本解压后、切换前的自检：能不能在这台设备上启动（codex --version）。 */
    private Installer.Verifier verifier() {
        return dir -> {
            File bin = new File(dir, "bin/codex");
            ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath(), "--version");
            pb.environment().put("HOME", paths.home.getAbsolutePath());
            pb.environment().put("CODEX_HOME", paths.codexHome.getAbsolutePath());
            pb.environment().put("TMPDIR", paths.tmp.getAbsolutePath());
            paths.home.mkdirs();
            paths.codexHome.mkdirs();
            paths.tmp.mkdirs();
            Object[] r = runWithTimeout(pb, null, 20_000);
            String text = ((String) r[1]).trim();
            if ((Integer) r[0] != 0) throw new IOException("codex --version 退出码 " + r[0] + (text.isEmpty() ? "" : "：" + text));
            if (!text.contains("codex")) throw new IOException("输出不对：" + text);
        };
    }

    public void startInstall() {
        if (stage == Stage.INSTALLING) return;
        installCancel = false;
        errorMessage = errorDetail = null;
        installErrorKind = null;
        install.done = 0;
        install.total = -1;
        install.bytesPerSec = 0;
        install.stage = Installer.Stage.METADATA;
        setStage(Stage.INSTALLING);
        final String sourcePref = prefs.source();
        final String version = prefs.targetVersion();
        worker.execute(() -> {
            List<Installer.Source> order = new ArrayList<>();
            if (sourcePref.equals("npm")) order.add(Installer.NPM);
            else if (sourcePref.equals("npmmirror")) order.add(Installer.NPMMIRROR);
            else {
                order.add(Installer.NPM);
                order.add(Installer.NPMMIRROR);
            }
            Installer.InstallException last = null;
            for (Installer.Source src : order) {
                try {
                    Installer ins = paths.installer();
                    final String label = src.label;
                    onMain(() -> {
                        install.sourceLabel = label;
                        changed();
                    });
                    Installer.Package p = ins.resolve(src, version);
                    final boolean force = forceInstall;
                    ins.install(p, new Installer.Progress() {
                        long lastPost, lastBytes, lastT = SystemClock.elapsedRealtime();
                        double speed;

                        @Override
                        public void onProgress(Installer.Stage stage, long done, long total) {
                            long now = SystemClock.elapsedRealtime();
                            if (stage == Installer.Stage.DOWNLOAD && now - lastT >= 500) {
                                double inst = (done - lastBytes) * 1000.0 / (now - lastT);
                                speed = speed <= 0 ? inst : speed * 0.8 + inst * 0.2; // 约 5 秒的平滑
                                lastBytes = done;
                                lastT = now;
                            }
                            if (now - lastPost < 200 && done != total) return;
                            lastPost = now;
                            final double sp = speed;
                            onMain(() -> {
                                install.stage = stage;
                                install.done = done;
                                install.total = total;
                                install.bytesPerSec = stage == Installer.Stage.DOWNLOAD ? sp : 0;
                                changed();
                            });
                        }

                        @Override
                        public boolean cancelled() {
                            return installCancel;
                        }
                    }, verifier(), force);
                    forceInstall = false;
                    final String v = ins.installedVersion();
                    onMain(() -> {
                        installedVersion = v;
                        boolean auth = paths.authFile().isFile();
                        if (auth) startServer();
                        else setStage(Stage.NEED_LOGIN);
                    });
                    return;
                } catch (Installer.InstallException e) {
                    last = e;
                    // 网络和找不到：换下一个源；其它错误（空间、校验、取消）直接报
                    if (e.kind != Installer.Kind.NETWORK && e.kind != Installer.Kind.NOT_FOUND) break;
                }
            }
            final Installer.InstallException err = last;
            onMain(() -> {
                installErrorKind = err == null ? Installer.Kind.IO : err.kind;
                errorMessage = err == null ? "安装失败" : err.getMessage();
                setStage(err != null && err.kind == Installer.Kind.CANCELLED ? Stage.NOT_INSTALLED : Stage.INSTALL_FAILED);
            });
        });
    }

    public void cancelInstall() {
        installCancel = true;
    }

    /* ---------------- 环境 ---------------- */

    /** 准备证书、代理、目录、config.toml，返回子进程环境。在 worker 线程调用。 */
    private Map<String, String> prepareEnv(File codexBin) throws IOException {
        if (proxy == null) {
            proxy = new LocalProxy(LocalProxy.SYSTEM);
            proxy.start();
        }
        if (!paths.caFile.isFile() || System.currentTimeMillis() - paths.caFile.lastModified() > 24L * 3600_000) {
            Certs.export(paths.caFile, prefs.userCa());
        }
        CodexRuntime.Layout l = paths.layout(codexBin);
        CodexRuntime.Config cfg = new CodexRuntime.Config();
        cfg.approvalPolicy = prefs.mode();
        cfg.model = prefs.model();
        cfg.reasoningEffort = prefs.effort();
        CodexRuntime.prepare(l, cfg);
        return CodexRuntime.env(l, proxy.url(), System.getenv("PATH"), TimeZone.getDefault().getID());
    }

    /** 证书设置变了：下次启动重新导出。 */
    public void invalidateCerts() {
        paths.caFile.delete();
    }

    private File codexBin() {
        try {
            return paths.installer().codexBinary();
        } catch (Installer.InstallException e) {
            return null;
        }
    }

    /* ---------------- 登录 ---------------- */

    /** 按用户要求：后台跑 codex login --device-auth，输出写文件，解析出网址和代码。过期自动换新。 */
    public void startLogin() {
        if (deviceLogin != null) return;
        final File bin = codexBin();
        if (bin == null) {
            setStage(Stage.NOT_INSTALLED);
            return;
        }
        stopServer(); // 登录期间不跑 app-server，避免凭据竞争（也取消待执行的自动重启）
        login = null;
        errorMessage = errorDetail = null;
        setStage(Stage.LOGGING_IN);
        worker.execute(() -> {
            try {
                Map<String, String> env = prepareEnv(bin);
                CodexRuntime.Layout l = paths.layout(bin);
                File out = new File(paths.logs, "device-auth.log");
                DeviceLogin dl = new DeviceLogin(CodexRuntime.command(l, "login", "--device-auth"), env, out, paths.authFile(),
                        s -> onMain(() -> onLoginState(s)));
                dl.setClock(SystemClock::elapsedRealtime); // 倒计时用含休眠的单调时钟（界面也用它）
                onMain(() -> {
                    deviceLogin = dl;
                    dl.start();
                });
            } catch (IOException e) {
                onMain(() -> fail("准备运行环境失败", e.getMessage()));
            }
        });
    }

    private void onLoginState(DeviceLogin.State s) {
        login = s;
        if (s.phase == DeviceLogin.Phase.SUCCESS) {
            deviceLogin = null;
            new File(paths.logs, "device-auth.log").delete(); // 成功后删掉含代码的日志
            startServer();
            return;
        }
        if (s.phase == DeviceLogin.Phase.FAILED || s.phase == DeviceLogin.Phase.STOPPED) {
            deviceLogin = null;
            stage = Stage.NEED_LOGIN; // 不再“等待登录”：前台服务可以停，界面显示原因和重试
        }
        changed();
    }

    public void refreshLoginCode() {
        if (deviceLogin != null) deviceLogin.refreshNow();
        else startLogin();
    }

    public void cancelLogin() {
        if (deviceLogin != null) deviceLogin.stop();
        deviceLogin = null;
        login = new DeviceLogin.State(DeviceLogin.Phase.STOPPED, null, null, 0, 0, null, null, null, 0, 0);
        setStage(Stage.NEED_LOGIN);
    }

    /** 备用：API Key 登录（codex login --with-api-key，密钥从 stdin 写入，不进命令行和日志）。 */
    public void loginWithApiKey(final String key, final Runnable onDone) {
        final File bin = codexBin();
        if (bin == null) return;
        if (deviceLogin != null) deviceLogin.stop();
        deviceLogin = null;
        stopServer();
        setStage(Stage.LOGGING_IN);
        worker.execute(() -> {
            String err = null;
            try {
                Map<String, String> env = prepareEnv(bin);
                ProcessBuilder pb = new ProcessBuilder(CodexRuntime.command(paths.layout(bin), "login", "--with-api-key"));
                pb.environment().clear();
                pb.environment().putAll(env);
                Object[] r = runWithTimeout(pb, (key.trim() + "\n").getBytes(StandardCharsets.UTF_8), 30_000);
                if ((Integer) r[0] != 0) err = com.pocketcodex.core.DeviceAuth.tail((String) r[1], 4);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String e2 = err;
            onMain(() -> {
                if (e2 == null) startServer();
                else {
                    errorMessage = "API Key 登录失败";
                    errorDetail = e2;
                    setStage(Stage.NEED_LOGIN);
                }
                if (onDone != null) onDone.run();
            });
        });
    }

    /** 跑一个短命令：输出在另一个线程读，控制线程有期限地等；超时就杀。返回 [退出码, 输出]。 */
    static Object[] runWithTimeout(ProcessBuilder pb, byte[] stdin, long timeoutMs) throws IOException {
        pb.redirectErrorStream(true);
        final Process p = pb.start();
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            byte[] buf = new byte[8192];
            int n;
            try (java.io.InputStream in = p.getInputStream()) {
                while ((n = in.read(buf)) >= 0) {
                    synchronized (out) {
                        if (out.size() < 64 * 1024) out.write(buf, 0, n);
                    }
                }
            } catch (IOException ignored) {
                // 进程结束
            }
        }, "short-cmd");
        reader.setDaemon(true);
        reader.start();
        try (OutputStream o = p.getOutputStream()) {
            if (stdin != null) o.write(stdin);
        } catch (IOException ignored) {
            // 进程不读 stdin
        }
        try {
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                throw new IOException("超时（" + timeoutMs / 1000 + " 秒）");
            }
            reader.join(1000);
        } catch (InterruptedException e) {
            p.destroyForcibly();
            throw new IOException("被中断");
        }
        synchronized (out) {
            return new Object[]{p.exitValue(), new String(out.toByteArray(), StandardCharsets.UTF_8)};
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) if (b.size() < 64 * 1024) b.write(buf, 0, n);
        return b.toByteArray();
    }

    public void logout() {
        if (server != null && server.alive()) server.request("account/logout", null, null);
        worker.execute(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                // 继续
            }
            paths.authFile().delete();
            onMain(() -> {
                stopServer();
                accountEmail = accountPlan = accountType = null;
                setStage(Stage.NEED_LOGIN);
            });
        });
    }

    /* ---------------- app-server ---------------- */

    public void startServer() {
        final File bin = codexBin();
        if (bin == null) {
            setStage(Stage.NOT_INSTALLED);
            return;
        }
        if (server != null && server.alive()) {
            setStage(Stage.READY);
            return;
        }
        if (starting) return;                 // 已经在启动
        if (deviceLogin != null) return;      // 登录进行中，不和登录抢凭据
        cancelPendingRestart();
        starting = true;
        final int token = ++startToken;
        errorMessage = errorDetail = null;
        setStage(Stage.STARTING);
        worker.execute(() -> {
            AppServer s = null;
            try {
                Map<String, String> env = prepareEnv(bin);
                long t0 = SystemClock.elapsedRealtime();
                File cwd = paths.home;
                s = AppServer.start(CodexRuntime.command(paths.layout(bin), "app-server"), env, cwd, new AppServer.Listener() {
                    @Override
                    public void onNotification(String method, Map<String, Object> params) {
                        onMain(() -> {
                            if (token == startToken) handleNotification(method, params);
                        });
                    }

                    @Override
                    public void onServerRequest(Object id, String method, Map<String, Object> params) {
                        onMain(() -> {
                            if (token == startToken) handleServerRequest(id, method, params);
                        });
                    }

                    @Override
                    public void onExit(int code, String stderrTail) {
                        onMain(() -> {
                            if (token == startToken) handleServerExit(code, stderrTail); // 旧进程晚到的退出通知不影响新进程
                        });
                    }
                });
                Map<String, Object> init = s.call("initialize", Json.map("clientInfo", Json.map("name", "pocket_codex", "title", "Pocket Codex", "version", BuildConfig.VERSION_NAME)), 30_000);
                s.notify("initialized", null);
                final long ms = SystemClock.elapsedRealtime() - t0;
                final String ua = Json.str(init, "userAgent", "");
                Map<String, Object> acct = s.call("account/read", Json.map("refreshToken", false), 30_000);
                Map<String, Object> a = Json.obj(acct, "account");
                final boolean needsAuth = a.isEmpty() && Json.bool(acct, "requiresOpenaiAuth", true);
                final String email = Json.str(a, "email"), plan = Json.str(a, "planType"), type = Json.str(a, "type");
                final AppServer ok = s;
                onMain(() -> {
                    starting = false;
                    if (token != startToken) { // 启动期间被停止（登录、退出、关闭）：丢弃
                        ok.close();
                        return;
                    }
                    if (needsAuth) {
                        ok.close();
                        paths.authFile().delete();
                        setStage(Stage.NEED_LOGIN);
                        return;
                    }
                    server = ok;
                    generation++;
                    initializeMs = ms;
                    serverVersion = ua.replaceAll(".*?/([0-9][0-9A-Za-z.\\-]*).*", "$1");
                    accountEmail = email;
                    accountPlan = plan;
                    accountType = type;
                    setStage(Stage.READY);
                    loadModels();
                    refreshThreads();
                    refreshRateLimits();
                });
            } catch (Exception e) {
                if (s != null) s.close(); // 初始化或账号检查失败：别把进程留着
                final String msg = e.getMessage();
                onMain(() -> {
                    starting = false;
                    if (token == startToken) fail("Codex 启动失败", msg);
                });
            }
        });
    }

    private void cancelPendingRestart() {
        if (pendingRestart != null) main.removeCallbacks(pendingRestart);
        pendingRestart = null;
    }

    private void fail(String msg, String detail) {
        errorMessage = msg;
        errorDetail = detail;
        if (server != null) {
            server.close();
            server = null;
        }
        setStage(Stage.ERROR);
    }

    public void stopServer() {
        cancelPendingRestart();
        startToken++; // 在途的启动作废，旧进程的回调也作废
        starting = false;
        if (server != null) {
            server.close();
            server = null;
        }
        runningThreadId = null;
        interruptWanted.clear();
        for (Conversation c : conversations.values()) {
            if (c.clearApprovals()) {
                Conversation.Update u = new Conversation.Update();
                u.approvals = true;
                notifyConversation(c, u);
            }
        }
    }

    /** 整个运行时停下（App 退出、长时间空闲）。 */
    public void shutdown() {
        stopServer(); // 也取消待执行的自动重启
        if (deviceLogin != null) deviceLogin.stop();
        deviceLogin = null;
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }
        if (stage == Stage.READY || stage == Stage.STARTING) stage = Stage.CHECKING;
        changed();
    }

    public boolean serverAlive() {
        return server != null && server.alive();
    }

    private void handleServerExit(int code, String tail) {
        boolean wasReady = stage == Stage.READY;
        server = null;
        for (Conversation c : conversations.values()) {
            if (c.clearApprovals()) {
                Conversation.Update u = new Conversation.Update();
                u.approvals = true;
                notifyConversation(c, u);
            }
        }
        if (runningThreadId != null) {
            Conversation c = conversations.get(runningThreadId);
            if (c != null) {
                Conversation.Update u = c.apply("error", Json.map("threadId", c.threadId, "willRetry", false,
                        "error", Json.map("message", "Codex 进程意外退出（" + code + "）。这一轮没有完成，文件可能已经改了一部分；不会自动重发。")));
                c.turnStatus = "failed";
                u.status = true;
                u.turnEnded = true;
                notifyConversation(c, u);
            }
            runningThreadId = null;
        }
        if (!wasReady) return;
        long now = SystemClock.elapsedRealtime();
        if (now - firstRestartAt > 10 * 60_000) {
            firstRestartAt = now;
            serverRestarts = 0;
        }
        if (serverRestarts < 3) { // 10 分钟内最多自动重启 3 次
            serverRestarts++;
            long delay = serverRestarts == 1 ? 1000 : serverRestarts == 2 ? 3000 : 10_000;
            stage = Stage.STARTING;
            changed();
            pendingRestart = this::startServer;
            main.postDelayed(pendingRestart, delay);
        } else {
            fail("Codex 进程反复退出", tail);
        }
    }

    /* ---------------- 模型、额度、会话列表 ---------------- */

    private void loadModels() {
        if (server == null) return;
        server.request("model/list", Json.map("includeHidden", false), new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                onMain(() -> {
                    models.clear();
                    for (Object o : Json.arr(r, "data")) {
                        if (Json.bool(o, "hidden", false)) continue;
                        List<String> efforts = new ArrayList<>();
                        for (Object e : Json.arr(o, "supportedReasoningEfforts")) {
                            String eff = Json.str(e, "reasoningEffort");
                            if (eff != null) efforts.add(eff);
                        }
                        models.add(new ModelInfo(Json.str(o, "id", Json.str(o, "model", "?")), Json.str(o, "displayName", Json.str(o, "id", "?")),
                                Json.str(o, "description", ""), Json.str(o, "defaultReasoningEffort"), efforts, Json.bool(o, "isDefault", false)));
                    }
                    changed();
                });
            }

            @Override
            public void onError(AppServer.RpcError error) {
            }
        });
    }

    public ModelInfo currentModel() {
        String want = prefs.model();
        ModelInfo def = null;
        for (ModelInfo m : models) {
            if (m.id.equals(want)) return m;
            if (m.isDefault) def = m;
        }
        return def != null ? def : models.isEmpty() ? null : models.get(0);
    }

    public void refreshRateLimits() {
        if (server == null) return;
        server.request("account/rateLimits/read", null, new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                onMain(() -> {
                    lastRateLimits = r;
                    for (Conversation c : conversations.values()) c.setRateLimits(r);
                    changed();
                });
            }

            @Override
            public void onError(AppServer.RpcError error) {
            }
        });
    }

    public Map<String, Object> lastRateLimits;

    public void refreshThreads() {
        if (server == null) return;
        server.request("thread/list", Json.map("limit", 50, "sortKey", "updated_at", "sortDirection", "desc"), new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                onMain(() -> {
                    threads.clear();
                    for (Object o : Json.arr(r, "data")) {
                        String cwd = Json.str(o, "cwd");
                        if (cwd == null) {
                            List<Object> envs = Json.arr(o, "environments");
                            if (!envs.isEmpty()) cwd = Json.str(envs.get(0), "cwd");
                        }
                        threads.add(new ThreadSummary(Json.str(o, "id"), Json.str(o, "name"), Json.str(o, "preview"), cwd,
                                Json.num(o, "updatedAt", Json.num(o, "createdAt", 0))));
                    }
                    threadsLoaded = true;
                    changed();
                });
            }

            @Override
            public void onError(AppServer.RpcError error) {
                onMain(() -> {
                    threadsLoaded = true;
                    changed();
                });
            }
        });
    }

    /* ---------------- 会话 ---------------- */

    public Conversation conversation(String threadId) {
        return conversations.get(threadId);
    }

    /** 给 Agent 的环境说明（安卓上的 shell 和工具）。 */
    static String developerInstructions(String cwd) {
        return "You are running inside Pocket Codex on an Android phone. The working directory is " + cwd + ". "
                + "Shell commands run through Android's /system/bin/sh (mksh, invoked as bash) with Android toybox utilities "
                + "(ls, cat, grep, sed, find, head, tail, wc, sort, tar, …). git, python, node, rg and compilers are NOT installed; "
                + "do not try to install them. Prefer POSIX shell syntax. Edit files with apply_patch. "
                + "There is no project-level sandbox: stay inside the working directory unless the user asks otherwise. "
                + "Reply in the language the user writes in.";
    }

    private Map<String, Object> threadParams(String cwd) {
        Map<String, Object> m = Json.map("sandbox", "danger-full-access", "approvalPolicy", prefs.mode(), "approvalsReviewer", "user");
        if (cwd != null) {
            m.put("cwd", cwd);
            m.put("developerInstructions", developerInstructions(cwd));
        }
        ModelInfo mi = currentModel();
        if (prefs.model() != null && mi != null) m.put("model", mi.id);
        return m;
    }

    public interface ThreadCallback {
        void onThread(Conversation c, String error);
    }

    public void newThread(final File workspace, final ThreadCallback cb) {
        if (server == null) {
            cb.onThread(null, "Codex 还没准备好");
            return;
        }
        if (!workspace.isDirectory()) workspace.mkdirs();
        server.request("thread/start", threadParams(workspace.getAbsolutePath()), new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                onMain(() -> {
                    Map<String, Object> t = Json.obj(r, "thread");
                    String id = Json.str(t, "id");
                    Conversation c = new Conversation(id);
                    conversations.put(id, c);
                    resumedIn.put(id, generation);
                    cwdOf.put(id, workspace.getAbsolutePath());
                    if (lastRateLimits != null) c.setRateLimits(lastRateLimits);
                    cb.onThread(c, null);
                    refreshThreads();
                });
            }

            @Override
            public void onError(AppServer.RpcError e) {
                onMain(() -> cb.onThread(null, e.getMessage()));
            }
        });
    }

    /** 打开已有会话：thread/resume 把它载入 app-server，再取历史回合（完整 item）。 */
    public void openThread(final String threadId, final String cwd, final ThreadCallback cb) {
        if (cwd != null) cwdOf.put(threadId, cwd);
        Conversation existing = conversations.get(threadId);
        if (existing != null) {
            cb.onThread(existing, null);
            return;
        }
        if (server == null) {
            cb.onThread(null, "Codex 还没准备好");
            return;
        }
        Map<String, Object> p = threadParams(cwd);
        p.put("threadId", threadId);
        p.put("excludeTurns", true);
        server.request("thread/resume", p, new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                server.request("thread/turns/list", Json.map("threadId", threadId, "itemsView", "full", "limit", 30, "sortDirection", "desc"),
                        new AppServer.Callback() {
                            @Override
                            public void onResult(Map<String, Object> tr) {
                                onMain(() -> {
                                    Conversation c = new Conversation(threadId);
                                    c.olderCursor = Json.str(tr, "nextCursor");
                                    List<Object> turns = new ArrayList<>(Json.arr(tr, "data"));
                                    java.util.Collections.reverse(turns);
                                    c.loadTurns(turns);
                                    if (lastRateLimits != null) c.setRateLimits(lastRateLimits);
                                    conversations.put(threadId, c);
                                    resumedIn.put(threadId, generation);
                                    cb.onThread(c, null);
                                });
                            }

                            @Override
                            public void onError(AppServer.RpcError e) {
                                onMain(() -> {
                                    Conversation c = new Conversation(threadId);
                                    conversations.put(threadId, c);
                                    cb.onThread(c, "历史记录没能载入：" + e.getMessage());
                                });
                            }
                        });
            }

            @Override
            public void onError(AppServer.RpcError e) {
                onMain(() -> cb.onThread(null, e.getMessage()));
            }
        });
    }

    /** 加载更早的历史（每次 30 轮）。 */
    public void loadOlder(final Conversation c, final SendCallback cb) {
        if (server == null || c.olderCursor == null) {
            cb.onSent(server == null ? "Codex 还没准备好" : null);
            return;
        }
        server.request("thread/turns/list", Json.map("threadId", c.threadId, "itemsView", "full", "limit", 30, "sortDirection", "desc", "cursor", c.olderCursor),
                new AppServer.Callback() {
                    @Override
                    public void onResult(Map<String, Object> tr) {
                        onMain(() -> {
                            List<Object> turns = new ArrayList<>(Json.arr(tr, "data"));
                            java.util.Collections.reverse(turns);
                            c.olderCursor = Json.str(tr, "nextCursor");
                            notifyConversation(c, c.prependTurns(turns));
                            cb.onSent(null);
                        });
                    }

                    @Override
                    public void onError(AppServer.RpcError e) {
                        onMain(() -> cb.onSent("较早记录加载失败：" + e.getMessage()));
                    }
                });
    }

    public interface SendCallback {
        void onSent(String error);
    }

    /** 发一轮。app-server 重启过的话先把这个会话 resume 回来。 */
    public void sendTurn(final Conversation c, final String text, final SendCallback cb) {
        if (server == null) {
            cb.onSent("Codex 还没准备好");
            return;
        }
        Integer g = resumedIn.get(c.threadId);
        if (g == null || g != generation) {
            Map<String, Object> p = threadParams(currentCwd(c));
            p.put("threadId", c.threadId);
            p.put("excludeTurns", true);
            final int gen = generation;
            server.request("thread/resume", p, new AppServer.Callback() {
                @Override
                public void onResult(Map<String, Object> r) {
                    onMain(() -> {
                        resumedIn.put(c.threadId, gen);
                        sendTurn(c, text, cb);
                    });
                }

                @Override
                public void onError(AppServer.RpcError e) {
                    onMain(() -> cb.onSent("无法恢复这个会话：" + e.getMessage()));
                }
            });
            return;
        }
        if (runningThreadId != null && !runningThreadId.equals(c.threadId)) {
            cb.onSent("另一个对话正在运行，请等它结束或先停止它");
            return;
        }
        String clientId = UUID.randomUUID().toString();
        c.addLocalUserMessage(clientId, text);
        Conversation.Update u = new Conversation.Update();
        u.structure = true;
        notifyConversation(c, u);
        List<Object> input = new ArrayList<>();
        input.add(Json.map("type", "text", "text", text));
        Map<String, Object> p = Json.map("threadId", c.threadId, "input", input, "clientUserMessageId", clientId, "approvalPolicy", prefs.mode());
        ModelInfo mi = currentModel();
        if (prefs.model() != null && mi != null) p.put("model", mi.id);
        if (prefs.effort() != null) p.put("effort", prefs.effort());
        runningThreadId = c.threadId;
        c.turnStatus = "inProgress";
        changed();
        server.request("turn/start", p, new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                onMain(() -> {
                    String tid = Json.str(Json.obj(r, "turn"), "id");
                    if (tid != null && c.activeTurnId == null) c.activeTurnId = tid;
                    cb.onSent(null);
                });
            }

            @Override
            public void onError(AppServer.RpcError e) {
                onMain(() -> {
                    runningThreadId = null;
                    c.turnStatus = "failed";
                    Conversation.Update u2 = c.apply("error", Json.map("threadId", c.threadId, "willRetry", false, "error", Json.map("message", "发送失败：" + e.getMessage())));
                    u2.status = true;
                    notifyConversation(c, u2);
                    changed();
                    cb.onSent(e.getMessage());
                });
            }
        });
    }

    private final Map<String, String> cwdOf = new HashMap<>();

    /** 会话的工作目录（打开时记下；没有就用当前工作区）。 */
    private String currentCwd(Conversation c) {
        String w = cwdOf.get(c.threadId);
        return w != null ? w : prefs.workspace();
    }

    /** 运行中追加指令（turn/steer）。 */
    public void steer(final Conversation c, String text, final SendCallback cb) {
        if (server == null || c.activeTurnId == null) {
            cb.onSent("本轮已经结束，点发送开始下一轮");
            return;
        }
        List<Object> input = new ArrayList<>();
        input.add(Json.map("type", "text", "text", text));
        String clientId = UUID.randomUUID().toString();
        server.request("turn/steer", Json.map("threadId", c.threadId, "expectedTurnId", c.activeTurnId, "input", input, "clientUserMessageId", clientId),
                new AppServer.Callback() {
                    @Override
                    public void onResult(Map<String, Object> r) {
                        onMain(() -> {
                            c.addLocalUserMessage(clientId, text);
                            Conversation.Update u = new Conversation.Update();
                            u.structure = true;
                            notifyConversation(c, u);
                            cb.onSent(null);
                        });
                    }

                    @Override
                    public void onError(AppServer.RpcError e) {
                        onMain(() -> cb.onSent("本轮已经结束，点发送开始下一轮"));
                    }
                });
    }

    public void interrupt(Conversation c) {
        if (server == null) return;
        if (c.activeTurnId == null) { // 回合还没真正开始：等 turn/started 一到就停
            interruptWanted.add(c.threadId);
            return;
        }
        server.request("turn/interrupt", Json.map("threadId", c.threadId, "turnId", c.activeTurnId), null);
    }

    /** 审批的回答：accept / acceptForSession / decline / cancel。 */
    public boolean answerApproval(Conversation c, Conversation.Approval a, String decision) {
        boolean sent = server != null && server.alive() && server.respond(a.requestId, Json.map("decision", decision));
        if (sent) {
            a.submitting = true; // 等 serverRequest/resolved 再移除
            a.decision = decision;
        }
        Conversation.Update u = new Conversation.Update();
        u.approvals = true;
        notifyConversation(c, u);
        RuntimeService.sync(ctx, this);
        return sent;
    }

    /** 回答 Agent 的提问：answers 是 question id → 选中的选项/输入的文字。 */
    public boolean answerInput(Conversation c, Conversation.InputRequest r, Map<String, List<String>> answers) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : answers.entrySet()) out.put(e.getKey(), Json.map("answers", new ArrayList<Object>(e.getValue())));
        boolean sent = server != null && server.alive() && server.respond(r.requestId, Json.map("answers", out));
        if (sent) r.submitting = true;
        Conversation.Update u = new Conversation.Update();
        u.approvals = true;
        notifyConversation(c, u);
        return sent;
    }

    public void renameThread(String threadId, String name) {
        if (server == null) return;
        server.request("thread/name/set", Json.map("threadId", threadId, "name", name), new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                onMain(CodexRuntimeManager.this::refreshThreads);
            }

            @Override
            public void onError(AppServer.RpcError error) {
            }
        });
    }

    public void archiveThread(String threadId) {
        if (server == null) return;
        conversations.remove(threadId);
        server.request("thread/archive", Json.map("threadId", threadId), new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                onMain(CodexRuntimeManager.this::refreshThreads);
            }

            @Override
            public void onError(AppServer.RpcError error) {
                onMain(CodexRuntimeManager.this::refreshThreads);
            }
        });
    }

    /** 等待用户审批的总数（通知用）。 */
    public int pendingApprovalCount() {
        int n = 0;
        for (Conversation c : conversations.values()) n += c.pendingApprovals().size() + c.pendingInputs().size();
        return n;
    }

    public Conversation runningConversation() {
        return runningThreadId == null ? null : conversations.get(runningThreadId);
    }

    /* ---------------- 分发 ---------------- */

    private void handleNotification(String method, Map<String, Object> params) {
        if (method.equals("account/rateLimits/updated")) {
            for (Conversation c : conversations.values()) {
                Conversation.Update u = c.apply(method, params);
                if (u.any()) notifyConversation(c, u);
            }
            return;
        }
        if (method.equals("account/updated") || method.equals("thread/started") || method.equals("thread/name/updated")) {
            if (!method.equals("account/updated")) refreshThreads();
        }
        String tid = Json.str(params, "threadId");
        if (tid == null) {
            if (method.equals("thread/started")) return;
            return;
        }
        Conversation c = conversations.get(tid);
        if (c == null) return;
        Conversation.Update u = c.apply(method, params);
        if (method.equals("turn/started")) {
            runningThreadId = tid;
            if (interruptWanted.remove(tid)) interrupt(c);
            changed();
        }
        if (u.turnEnded) {
            if (tid.equals(runningThreadId)) runningThreadId = null;
            String title = "对话";
            for (ThreadSummary t : threads) if (t.id.equals(tid)) title = t.title();
            if ("failed".equals(c.turnStatus)) RuntimeService.notifyTurnEnded(ctx, "任务未完成", title + " · 已发生的改动可能保留");
            else if ("completed".equals(c.turnStatus)) RuntimeService.notifyTurnEnded(ctx, "任务已完成", title + " · 查看结果");
            refreshThreads();
            changed();
        }
        if (u.any()) notifyConversation(c, u);
    }

    private void handleServerRequest(Object id, String method, Map<String, Object> params) {
        String tid = Json.str(params, "threadId");
        Conversation c = tid == null ? null : conversations.get(tid);
        if (c != null && Conversation.handles(method)) c.onServerRequest(id, method, params);
        if (c == null || !Conversation.handles(method)) {
            // 不认识的请求（或不属于打开的会话）：明确拒绝，不能让它永远挂着
            if (server != null) server.respondError(id, -32601, "Pocket Codex 不支持这个请求：" + method);
            return;
        }
        Conversation.Update u = new Conversation.Update();
        u.approvals = true;
        notifyConversation(c, u);
        RuntimeService.sync(ctx, this);
    }

    private void notifyConversation(Conversation c, Conversation.Update u) {
        for (ConversationListener l : convListeners) l.onConversationUpdate(c, u);
    }

    /** 自检（诊断页）在 worker 线程跑，结果回调到主线程。 */
    public void runDiagnostics(Diagnostics.Callback cb) {
        final File bin = codexBin();
        worker.execute(() -> {
            String report = Diagnostics.run(this, bin);
            onMain(() -> cb.onReport(report));
        });
    }

    /** 测试和截图用：直接放一个会话、改阶段并通知界面。 */
    void putConversationForTest(Conversation c) {
        conversations.put(c.threadId, c);
    }

    void handleNotificationForTest(String method, Map<String, Object> params) {
        handleNotification(method, params);
    }

    void setStageForTest(Stage s) {
        stage = s;
        changed();
    }

    /** 给诊断用：在 worker 线程准备好环境。 */
    Map<String, String> envForDiagnostics(File bin) throws IOException {
        return prepareEnv(bin);
    }

    LocalProxy proxyForDiagnostics() {
        return proxy;
    }
}
