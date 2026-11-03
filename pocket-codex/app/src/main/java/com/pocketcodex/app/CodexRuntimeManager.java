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
import com.pocketcodex.core.Toolkit;
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
        /** 可选的速度档（serviceTier id → 说明），一般只有 priority（快速）。 */
        public final List<String> serviceTiers = new ArrayList<>();
        public final List<String> serviceTierNames = new ArrayList<>();
        public final List<String> serviceTierDescriptions = new ArrayList<>();
        /** 和 efforts 一一对应的服务端说明。 */
        public final List<String> effortDescriptions = new ArrayList<>();

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
        // 上次拿到的账号和用量先用上（设置页不用等网络）；连上 Codex 以后再更新
        this.accountEmail = prefs.accountEmail();
        this.accountPlan = prefs.accountPlan();
        this.accountType = prefs.accountType();
        this.lastRateLimits = parseCache(prefs.rateLimitsJson());
    }

    private static Map<String, Object> parseCache(String json) {
        if (json == null) return null;
        try {
            return Json.parseObject(json);
        } catch (Exception e) {
            return null;
        }
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
            proxy.setLocalHandler(this::handleLocalCommand);
            proxy.start();
        }
        checkToolkit();
        installBundledSkills();
        if (!paths.caFile.isFile() || System.currentTimeMillis() - paths.caFile.lastModified() > 24L * 3600_000) {
            Certs.export(paths.caFile, prefs.userCa());
        }
        CodexRuntime.Layout l = paths.layout(codexBin);
        l.toolkitReady = toolkitUsable;
        codexHasToolkit = toolkitUsable;
        CodexRuntime.Config cfg = new CodexRuntime.Config();
        cfg.approvalPolicy = prefs.mode();
        cfg.model = prefs.model();
        cfg.reasoningEffort = prefs.effort();
        CodexRuntime.prepare(l, cfg);
        runtimeConfig = cfg;
        return CodexRuntime.env(l, proxy.url(), System.getenv("PATH"), TimeZone.getDefault().getID());
    }

    /**
     * 随 App 带的技能（assets/skills/pocket-*）复制到 $HOME/.agents/skills/（Codex 的用户级技能目录）。
     * 只管 pocket- 开头的目录，内容没变不重写；用户自己装的技能不碰。在 worker 线程调用。
     */
    void installBundledSkills() {
        File dest = new File(paths.home, ".agents/skills");
        try {
            String[] names = ctx.getAssets().list("skills");
            if (names == null) return;
            for (String n : names) if (n.startsWith("pocket-")) copyAssetTree("skills/" + n, new File(dest, n));
        } catch (IOException e) {
            android.util.Log.w("PocketCodex", "技能复制失败：" + e.getMessage());
        }
    }

    private void copyAssetTree(String asset, File to) throws IOException {
        String[] kids = ctx.getAssets().list(asset);
        if (kids != null && kids.length > 0) {
            if (!to.isDirectory() && !to.mkdirs()) throw new IOException("建目录失败：" + to);
            for (String k : kids) copyAssetTree(asset + "/" + k, new File(to, k));
            return;
        }
        byte[] data;
        try (java.io.InputStream in = ctx.getAssets().open(asset); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            data = bo.toByteArray();
        }
        if (to.isFile() && java.util.Arrays.equals(data, java.nio.file.Files.readAllBytes(to.toPath()))) return;
        File tmp = new File(to.getPath() + ".tmp");
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(tmp)) {
            o.write(data);
        }
        if (!tmp.renameTo(to)) throw new IOException("写入失败：" + to);
    }

    /** 这次启动的 codex 环境里有没有工具包（没有的话工具包装好后要重启它）。 */
    private volatile boolean codexHasToolkit;

    /** 这次启动 codex 用的设置（用 -c 传）。 */
    private volatile CodexRuntime.Config runtimeConfig;

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

    /* ---------------- 开发工具包（node、npm、git、python……） ---------------- */

    public enum ToolkitState { NONE, INSTALLING, READY, BROKEN, FAILED }

    /** 主线程读的状态。 */
    public ToolkitState toolkitState = ToolkitState.NONE;
    public String toolkitStep, toolkitError;
    public long toolkitDone = -1, toolkitTotal = -1;
    public final Map<String, String> toolkitVersions = new java.util.LinkedHashMap<>();
    /** 已装的软件包名（主线程读）；工具集“已安装”按这个判断。 */
    public final java.util.Set<String> toolkitPackages = new java.util.HashSet<>();
    /** 正在装的工具集 id（null = 装的是基础包或没在装）。 */
    public String bundleInstalling;
    /** worker 线程用：自检通过才把工具包放进 Agent 的环境。 */
    private volatile boolean toolkitUsable;
    private volatile boolean toolkitInstalling;
    private volatile boolean toolkitCancel;
    private boolean toolkitAutoTried, toolkitRestartWanted;
    private final ExecutorService toolkitWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "toolkit");
        t.setDaemon(true);
        return t;
    });
    private long toolkitLastUi;

    private File toolkitMarker() {
        return new File(paths.usr, "var/lib/pocket-pkg/selftest.json");
    }

    /** 这台设备 + 这次安装的标识：系统升级或工具包变了就重新自检。 */
    private String toolkitStamp() {
        Toolkit t = paths.toolkit();
        File st = new File(paths.usr, "var/lib/pocket-pkg/status.json");
        return android.os.Build.FINGERPRINT + "|" + st.lastModified() + "|" + (t == null ? "" : t.arch) + "|" + BuildConfig.VERSION_CODE;
    }

    /** worker 线程：读自检结果；没有或过期就现在自检（一两秒）。 */
    private void checkToolkit() {
        Toolkit t = paths.toolkit();
        if (t == null || !t.installed()) {
            toolkitUsable = false;
            onMain(() -> {
                if (toolkitState != ToolkitState.INSTALLING && toolkitState != ToolkitState.FAILED) toolkitState = ToolkitState.NONE;
            });
            return;
        }
        if (toolkitInstalling) return;
        Map<String, String> versions = null;
        boolean ok = false;
        try {
            Map<String, Object> m = Json.parseObject(new String(java.nio.file.Files.readAllBytes(toolkitMarker().toPath()), StandardCharsets.UTF_8));
            if (toolkitStamp().equals(Json.str(m, "stamp")) && Json.bool(m, "ok", false)) {
                ok = true;
                versions = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> e : Json.obj(m, "versions").entrySet()) versions.put(e.getKey(), String.valueOf(e.getValue()));
            }
        } catch (Exception ignored) {
            // 没有自检记录
        }
        String err = null;
        if (versions == null) {
            try {
                versions = toolkitSelfTest();
                ok = versions != null;
                if (!ok) err = "工具包自检没通过（bash 无法运行）";
            } catch (IOException e) {
                err = "工具包自检失败：" + e.getMessage();
            }
        }
        toolkitUsable = ok;
        final boolean fok = ok;
        final Map<String, String> fv = versions;
        final String ferr = err;
        final java.util.Set<String> pk = new java.util.HashSet<>(t.status().keySet());
        onMain(() -> {
            toolkitPackages.clear();
            toolkitPackages.addAll(pk);
            if (toolkitState == ToolkitState.INSTALLING) return;
            toolkitState = fok ? ToolkitState.READY : ToolkitState.BROKEN;
            toolkitError = ferr;
            toolkitVersions.clear();
            if (fv != null) toolkitVersions.putAll(fv);
            changed();
        });
        // v0.3.1 以前装的包少了 update-alternatives 的命令（java、javac、keytool、vim……）：后台补一次，不挡 Codex 启动
        if (ok && !toolkitRepairTried && t.needsAlternativesRepair()) {
            toolkitRepairTried = true;
            toolkitWorker.execute(this::repairOldToolkit);
        }
    }

    private volatile boolean toolkitRepairTried;

    /** toolkit 线程：给旧版装的包补命令链接。失败（没网等）就算了，下次启动再试。 */
    private void repairOldToolkit() {
        Toolkit t = paths.toolkit();
        if (t == null || toolkitInstalling) return;
        try {
            int n = t.repairOldInstalls(preferredToolkitRepo(), new Toolkit.Progress() {
                @Override
                public void step(String text, long done, long total) {
                }

                @Override
                public boolean cancelled() {
                    // 用户点了安装（toolkitInstalling 在主线程先置位，安装任务排在这个后面）：马上让出，下次启动再补
                    return toolkitInstalling;
                }
            });
            if (n <= 0) return;
            android.util.Log.i("PocketCodex", "补建了 " + n + " 个命令链接");
            final Map<String, String> v = toolkitSelfTest();
            onMain(() -> {
                if (v != null) {
                    toolkitVersions.clear();
                    toolkitVersions.putAll(v);
                }
                changed();
            });
        } catch (Exception e) {
            android.util.Log.w("PocketCodex", "补建命令链接失败：" + e.getMessage());
        }
    }

    /** 用 Agent 的同一套环境跑一遍常用命令，记下版本。bash 都跑不起来返回 null。 */
    private Map<String, String> toolkitSelfTest() throws IOException {
        Toolkit t = paths.toolkit();
        File bin = codexBin();
        CodexRuntime.Layout l = paths.layout(bin);
        l.toolkitReady = true;
        Map<String, String> env = CodexRuntime.env(l, proxy == null ? null : proxy.url(), System.getenv("PATH"), TimeZone.getDefault().getID());
        paths.home.mkdirs();
        paths.tmp.mkdirs();
        // 后面几个是工具集里的（装了才测）
        String script = "echo __pocket_ok__; for c in 'node -v' 'npm -v' 'git --version' 'python3 -V' 'pip -V' 'bash --version' 'rg --version' 'curl --version' "
                + "'ffmpeg -version' 'java -version' 'adb version' 'gh --version' 'r2 -v' 'magick -version'; do "
                + "command -v \"${c%% *}\" >/dev/null 2>&1 || continue; "
                + "printf '%s\\t%s\\n' \"${c%% *}\" \"$($c 2>&1 | head -n 1)\"; done";
        ProcessBuilder pb = new ProcessBuilder(t.bin("bash").getAbsolutePath(), "-c", script);
        pb.environment().clear();
        pb.environment().putAll(env);
        pb.directory(paths.home);
        Object[] r = runWithTimeout(pb, null, 90_000);
        String out = (String) r[1];
        if (!out.contains("__pocket_ok__")) return null;
        Map<String, String> v = new java.util.LinkedHashMap<>();
        for (String line : out.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String name = line.substring(0, tab), val = line.substring(tab + 1).trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("v?(\\d+\\.\\d+(\\.\\d+)?)").matcher(val);
            v.put(name, m.find() ? m.group(1) : "不可用");
        }
        Map<String, Object> mark = new java.util.LinkedHashMap<>();
        mark.put("stamp", toolkitStamp());
        mark.put("ok", true);
        mark.put("versions", new java.util.LinkedHashMap<String, Object>(v));
        File f = toolkitMarker();
        f.getParentFile().mkdirs();
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) {
            o.write(Json.write(mark).getBytes(StandardCharsets.UTF_8));
        }
        return v;
    }

    /** 优先的软件源：上次成功的；没有时，codex 用国内镜像装的就先试中科大镜像。 */
    private String preferredToolkitRepo() {
        String r = prefs.toolkitRepo();
        if (r != null) return r;
        return "npmmirror".equals(prefs.source()) ? "ustc" : null;
    }

    /** 安装（或重装）开发工具包。下载约 57 MB、占用约 230 MB；装好自检，再让 Codex 重启用上。 */
    public void startToolkitInstall() {
        if (toolkitState == ToolkitState.INSTALLING) return;
        final Toolkit t = paths.toolkit();
        if (t == null) {
            toolkitState = ToolkitState.FAILED;
            toolkitError = "这台设备的处理器不支持开发工具包";
            changed();
            return;
        }
        toolkitCancel = false;
        toolkitInstalling = true;
        toolkitState = ToolkitState.INSTALLING;
        toolkitError = null;
        toolkitStep = "准备安装…";
        toolkitDone = toolkitTotal = -1;
        prefs.setToolkitDeferred(false);
        changed();
        toolkitWorker.execute(() -> {
            String err = null;
            Map<String, String> versions = null;
            String repo = null;
            try {
                repo = t.installBase(preferredToolkitRepo(), new Toolkit.Progress() {
                    @Override
                    public void step(String text, long done, long total) {
                        onMain(() -> {
                            toolkitStep = text;
                            toolkitDone = done;
                            toolkitTotal = total;
                            long now = SystemClock.elapsedRealtime();
                            if (now - toolkitLastUi > 250) {
                                toolkitLastUi = now;
                                changed();
                            }
                        });
                    }

                    @Override
                    public boolean cancelled() {
                        return toolkitCancel;
                    }
                });
                onMain(() -> {
                    toolkitStep = "检查能不能运行…";
                    changed();
                });
                versions = toolkitSelfTest();
                if (versions == null) err = "装好了，但在这台设备上运行不起来（bash 自检失败）。可以在诊断里查看详情。";
            } catch (Installer.InstallException e) {
                err = e.kind == Installer.Kind.CANCELLED ? "已取消" : e.getMessage();
            } catch (IOException e) {
                err = "自检失败：" + e.getMessage();
            }
            toolkitInstalling = false;
            toolkitUsable = err == null;
            final String ferr = err, frepo = repo;
            final Map<String, String> fv = versions;
            final java.util.Set<String> pk = new java.util.HashSet<>(t.installed() ? t.status().keySet() : java.util.Collections.<String>emptySet());
            onMain(() -> {
                bundleInstalling = null;
                toolkitPackages.clear();
                toolkitPackages.addAll(pk);
                toolkitError = ferr;
                toolkitVersions.clear();
                if (fv != null) toolkitVersions.putAll(fv);
                if (ferr == null) {
                    toolkitState = ToolkitState.READY;
                    if (frepo != null) prefs.setToolkitRepo(frepo);
                    restartForToolkit();
                } else {
                    Toolkit tk = paths.toolkit();
                    toolkitState = tk != null && tk.installed() && fv != null ? ToolkitState.READY : tk != null && tk.installed() ? ToolkitState.BROKEN : ToolkitState.FAILED;
                }
                changed();
            });
        });
    }

    /** 装一个工具集（基础包没装就先装基础包）。下载量见 {@link com.pocketcodex.core.ToolBundles}。 */
    public void startBundleInstall(final String id) {
        final com.pocketcodex.core.ToolBundles.Bundle b = com.pocketcodex.core.ToolBundles.find(id);
        if (b == null || toolkitState == ToolkitState.INSTALLING) return;
        final Toolkit t = paths.toolkit();
        if (t == null) {
            toolkitState = ToolkitState.FAILED;
            toolkitError = "这台设备的处理器不支持开发工具包";
            changed();
            return;
        }
        toolkitCancel = false;
        toolkitInstalling = true;
        toolkitState = ToolkitState.INSTALLING;
        bundleInstalling = id;
        toolkitError = null;
        toolkitStep = "准备安装“" + b.title + "”…";
        toolkitDone = toolkitTotal = -1;
        changed();
        toolkitWorker.execute(() -> {
            String err = null;
            Map<String, String> versions = null;
            boolean baseNew = false;
            String repo = null;
            Toolkit.Progress pr = toolkitProgress();
            try {
                if (!t.installed()) {
                    repo = t.installBase(preferredToolkitRepo(), pr);
                    baseNew = true;
                    // 基础包先单独自检：后面工具集下载失败或取消，基础包照样能用（并且会重启 Codex 用上）
                    onMain(() -> {
                        toolkitStep = "检查基础工具能不能运行…";
                        changed();
                    });
                    versions = toolkitSelfTest();
                    if (versions == null) err = "基础工具包装好了，但在这台设备上运行不起来（bash 自检失败）。可以在诊断里查看详情。";
                    else toolkitUsable = true;
                }
                if (err == null) {
                    Toolkit.Index idx = t.fetchIndex(preferredToolkitRepo(), pr);
                    for (Toolkit.Repo r : Toolkit.REPOS) if (r.url.equals(idx.repoUrl)) repo = r.id;
                    t.install(b.packages, true, idx, pr, null);
                    onMain(() -> {
                        toolkitStep = "检查能不能运行…";
                        changed();
                    });
                    versions = toolkitSelfTest();
                    if (versions == null) err = "装好了，但自检没通过（bash 无法运行）";
                    else {
                        String miss = missingCommands(com.pocketcodex.core.ToolBundles.commands(id));
                        if (!miss.isEmpty()) err = "装好了，但这些命令运行不了：" + miss + "（可以到 自检与诊断 查看）";
                    }
                }
            } catch (Installer.InstallException e) {
                err = e.kind == Installer.Kind.CANCELLED ? "已取消" : e.getMessage();
            } catch (IOException e) {
                err = "自检失败：" + e.getMessage();
            }
            toolkitInstalling = false;
            // versions：最后一次自检的结果（工具集失败时是基础包那次的）
            if (t.installed()) toolkitUsable = versions != null || (toolkitUsable && err != null && !err.startsWith("装好了，但自检") && !err.startsWith("基础工具包装好了"));
            final java.util.Set<String> pk = new java.util.HashSet<>(t.status().keySet());
            final String ferr = err, frepo = repo;
            final Map<String, String> fv = versions;
            final boolean fbase = baseNew;
            onMain(() -> {
                bundleInstalling = null;
                toolkitPackages.clear();
                toolkitPackages.addAll(pk);
                toolkitError = ferr;
                if (fv != null) {
                    toolkitVersions.clear();
                    toolkitVersions.putAll(fv);
                }
                if (frepo != null) prefs.setToolkitRepo(frepo);
                if (ferr == null) {
                    java.util.Set<String> bs = prefs.bundles();
                    bs.add(id);
                    prefs.setBundles(bs);
                }
                toolkitState = t.installed() ? (toolkitUsable ? ToolkitState.READY : ToolkitState.BROKEN) : ToolkitState.FAILED;
                if (ferr != null && t.installed() && toolkitUsable) toolkitState = ToolkitState.READY; // 工具集没装成，基础包还能用
                // 新装的命令、JAVA_HOME 要重启 Codex 才能进它的环境（正在跑的回合结束后再重启）
                if (toolkitUsable && (fbase || ferr == null || !codexHasToolkit)) restartForToolkit();
                changed();
            });
        });
    }

    /** 在 Agent 的同一套环境里看这些命令能不能找到；返回找不到的（空格分隔），都在返回空串。 */
    private String missingCommands(List<String> cmds) throws IOException {
        if (cmds.isEmpty()) return "";
        Toolkit t = paths.toolkit();
        CodexRuntime.Layout l = paths.layout(codexBin());
        l.toolkitReady = true;
        Map<String, String> env = CodexRuntime.env(l, proxy == null ? null : proxy.url(), System.getenv("PATH"), TimeZone.getDefault().getID());
        StringBuilder sc = new StringBuilder("for c in");
        for (String c : cmds) sc.append(' ').append(c);
        sc.append("; do command -v \"$c\" >/dev/null 2>&1 || printf '%s ' \"$c\"; done; echo __done__");
        ProcessBuilder pb = new ProcessBuilder(t.bin("bash").getAbsolutePath(), "-c", sc.toString());
        pb.environment().clear();
        pb.environment().putAll(env);
        pb.directory(paths.home);
        Object[] r = runWithTimeout(pb, null, 30_000);
        String out = String.valueOf(r[1]);
        if (!out.contains("__done__")) return String.join(" ", cmds);
        return out.substring(0, out.indexOf("__done__")).trim();
    }

    /** 工具集是否装齐了。 */
    public boolean bundleInstalled(String id) {
        com.pocketcodex.core.ToolBundles.Bundle b = com.pocketcodex.core.ToolBundles.find(id);
        return b != null && !toolkitPackages.isEmpty() && toolkitPackages.containsAll(b.packages);
    }

    private Toolkit.Progress toolkitProgress() {
        return new Toolkit.Progress() {
            @Override
            public void step(String text, long done, long total) {
                onMain(() -> {
                    toolkitStep = text;
                    toolkitDone = done;
                    toolkitTotal = total;
                    long now = SystemClock.elapsedRealtime();
                    if (now - toolkitLastUi > 250) {
                        toolkitLastUi = now;
                        changed();
                    }
                });
            }

            @Override
            public boolean cancelled() {
                return toolkitCancel;
            }
        };
    }

    public void cancelToolkitInstall() {
        toolkitCancel = true;
    }

    /** 设置里的“重启 Codex”：加了 MCP 服务、技能之后用。正在跑的回合结束后再重启。 */
    public boolean restartCodex() {
        if (runningThreadId != null || pendingApprovalCount() > 0) return false;
        stopServer();
        startServer();
        return true;
    }

    /** 工具包变了：Codex 进程要重启才能拿到新的 PATH。正在跑的回合结束后再重启。 */
    private void restartForToolkit() {
        if (server == null) return;
        if (runningThreadId != null || pendingApprovalCount() > 0) {
            toolkitRestartWanted = true;
            return;
        }
        toolkitRestartWanted = false;
        stopServer();
        startServer();
    }

    /** 连上 Codex 以后：没装过工具包就自动装（移动网络下先问）。 */
    private void maybeAutoInstallToolkit() {
        if (toolkitAutoTried) return;
        toolkitAutoTried = true;
        Toolkit t = paths.toolkit();
        if (t == null || t.installed() || toolkitState == ToolkitState.INSTALLING) return;
        android.net.ConnectivityManager cm = ctx.getSystemService(android.net.ConnectivityManager.class);
        boolean metered = cm == null || cm.isActiveNetworkMetered();
        if (!metered) startToolkitInstall();
    }

    /** 移动网络下：界面上显示“安装开发工具”卡片（没自动装）。 */
    public boolean toolkitOffered() {
        Toolkit t = paths.toolkit();
        return t != null && !t.installed() && toolkitState == ToolkitState.NONE;
    }

    /** LocalProxy 的本机通道：PATH 里的 pkg/apt 命令走这里。 */
    private int handleLocalCommand(String path, List<String> args, java.io.PrintStream out) {
        if (!"/pkg".equals(path)) {
            out.println("未知的本机命令：" + path);
            return 2;
        }
        Toolkit t = paths.toolkit();
        if (t == null) {
            out.println("这台设备不支持开发工具包");
            return 1;
        }
        if (toolkitInstalling) {
            out.println("开发工具包正在安装，装好后再试（口袋工坊 → 设置 → 开发工具 可以看进度）。");
            return 1;
        }
        if (!t.installed()) {
            out.println("开发工具包还没装：请让用户在 口袋工坊 → 设置 → 开发工具 里安装。");
            return 1;
        }
        return t.command(args, preferredToolkitRepo(), out, () -> toolkitCancel);
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
                lastRateLimits = null;
                prefs.clearAccount();
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
                s = AppServer.start(CodexRuntime.command(paths.layout(bin), runtimeConfig, "app-server"), env, cwd, new AppServer.Listener() {
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
                        prefs.clearAccount();
                        lastRateLimits = null;
                        setStage(Stage.NEED_LOGIN);
                        return;
                    }
                    server = ok;
                    generation++;
                    initializeMs = ms;
                    serverVersion = ua.replaceAll(".*?/([0-9][0-9A-Za-z.\\-]*).*", "$1");
                    if (email != null && !email.equals(prefs.accountEmail())) {
                        lastRateLimits = null; // 换了账号：旧账号的用量不能留着
                        prefs.setRateLimitsJson(null);
                    }
                    accountEmail = email;
                    accountPlan = plan;
                    accountType = type;
                    prefs.setAccount(email, plan, type);
                    setStage(Stage.READY);
                    maybeAutoInstallToolkit();
                    loadModels();
                    loadCatalog();
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
                        List<String> efforts = new ArrayList<>(), effortDesc = new ArrayList<>();
                        for (Object e : Json.arr(o, "supportedReasoningEfforts")) {
                            String eff = Json.str(e, "reasoningEffort");
                            if (eff == null) continue;
                            efforts.add(eff);
                            effortDesc.add(Json.str(e, "description", ""));
                        }
                        ModelInfo mi = new ModelInfo(Json.str(o, "id", Json.str(o, "model", "?")), Json.str(o, "displayName", Json.str(o, "id", "?")),
                                Json.str(o, "description", ""), Json.str(o, "defaultReasoningEffort"), efforts, Json.bool(o, "isDefault", false));
                        mi.effortDescriptions.addAll(effortDesc);
                        for (Object t : Json.arr(o, "serviceTiers")) {
                            String tid = Json.str(t, "id");
                            if (tid == null) continue;
                            mi.serviceTiers.add(tid);
                            mi.serviceTierNames.add(Json.str(t, "name", tid));
                            mi.serviceTierDescriptions.add(Json.str(t, "description", ""));
                        }
                        // 旧字段（只有 id 的字符串数组）
                        for (Object t : Json.arr(o, "additionalSpeedTiers")) {
                            if (!(t instanceof String) || mi.serviceTiers.contains(t)) continue;
                            mi.serviceTiers.add((String) t);
                            mi.serviceTierNames.add((String) t);
                            mi.serviceTierDescriptions.add("");
                        }
                        models.add(mi);
                    }
                    changed();
                });
            }

            @Override
            public void onError(AppServer.RpcError error) {
            }
        });
    }

    /** “@”和插件列表的数据（技能 + 已安装且启用的插件），READY 后加载。 */
    public final List<Catalog> catalog = new ArrayList<>();

    public void loadCatalog() {
        if (server == null) return;
        final List<Catalog> skills = new ArrayList<>(), plugins = new ArrayList<>();
        String ws = prefs.workspace();
        Map<String, Object> sp = Json.map("forceReload", false);
        if (ws != null) {
            List<Object> cw = new ArrayList<>();
            cw.add(ws);
            sp.put("cwds", cw);
        }
        server.request("skills/list", sp, new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (Object e : Json.arr(r, "data")) {
                    for (Object sk : Json.arr(e, "skills")) {
                        if (!Json.bool(sk, "enabled", true)) continue;
                        String name = Json.str(sk, "name");
                        if (name == null || !seen.add(name)) continue;
                        Map<String, Object> itf = Json.obj(sk, "interface");
                        skills.add(new Catalog(true, name, Json.str(itf, "displayName", name),
                                Json.str(itf, "shortDescription", Json.str(sk, "shortDescription", Json.str(sk, "description", ""))),
                                Json.str(itf, "iconSmall", Json.str(itf, "iconSmallUrl")), Json.str(itf, "brandColor"), Json.str(sk, "path", "")));
                    }
                }
                onMain(() -> publishCatalog(skills, null));
            }

            @Override
            public void onError(AppServer.RpcError error) {
            }
        });
        server.request("plugin/list", Json.map(), new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                for (Object mk : Json.arr(r, "marketplaces")) {
                    for (Object pl : Json.arr(mk, "plugins")) {
                        if (!Json.bool(pl, "installed", false) || !Json.bool(pl, "enabled", false)) continue;
                        Map<String, Object> itf = Json.obj(pl, "interface");
                        String name = Json.str(pl, "name", Json.str(pl, "id", "?"));
                        String display = Json.str(itf, "displayName", name);
                        plugins.add(new Catalog(false, Json.str(pl, "id", name), display, Json.str(itf, "shortDescription", ""),
                                Json.str(itf, "composerIcon", Json.str(itf, "logo", Json.str(itf, "composerIconUrl"))), Json.str(itf, "brandColor"),
                                "plugin://" + Json.str(pl, "id", name)));
                    }
                }
                onMain(() -> publishCatalog(null, plugins));
            }

            @Override
            public void onError(AppServer.RpcError error) {
            }
        });
    }

    private final List<Catalog> catSkills = new ArrayList<>(), catPlugins = new ArrayList<>();

    private void publishCatalog(List<Catalog> skills, List<Catalog> plugins) {
        if (skills != null) {
            catSkills.clear();
            catSkills.addAll(skills);
        }
        if (plugins != null) {
            catPlugins.clear();
            catPlugins.addAll(plugins);
        }
        catalog.clear();
        // 创建图像放最前（ChatGPT 的顺序），然后插件，再其他技能
        for (Catalog c : catSkills) if (c.id.equals("imagegen")) catalog.add(c);
        catalog.addAll(catPlugins);
        for (Catalog c : catSkills) if (!c.id.equals("imagegen")) catalog.add(c);
        changed();
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

    /**
     * 聊天模式用的模型：账号套餐的默认模型（model/list 里 isDefault 的那个，Plus 实测是 GPT-6.1-Sol，默认推理 low），
     * 不跟随工作模式里选的模型（例如 GPT-6-Astra），也不用“快速”档（服务端标注会增加用量）。
     */
    public ModelInfo planModel() {
        for (ModelInfo m : models) if (m.isDefault) return m;
        return models.isEmpty() ? null : models.get(0);
    }

    /** 聊天模式的推理强度：套餐默认模型自己的默认值；没有就用最低一档。 */
    static String chatEffort(ModelInfo m) {
        if (m == null) return null;
        if (m.defaultEffort != null && (m.efforts.isEmpty() || m.efforts.contains(m.defaultEffort))) return m.defaultEffort;
        return m.efforts.isEmpty() ? null : m.efforts.get(0);
    }

    public interface FileSearchCallback {
        void onFiles(String query, List<String[]> files);
    }

    /** “@”里的文件：codex 的 fuzzyFileSearch（结果是 {相对路径, 文件名, file/directory}）。 */
    public void fileSearch(final String query, String root, final FileSearchCallback cb) {
        if (server == null || root == null || query.isEmpty()) return;
        List<Object> roots = new ArrayList<>();
        roots.add(root);
        server.request("fuzzyFileSearch", Json.map("query", query, "roots", roots, "cancellationToken", "composer"), new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                final List<String[]> out = new ArrayList<>();
                for (Object f : Json.arr(r, "files")) {
                    String path = Json.str(f, "path");
                    if (path == null) continue;
                    out.add(new String[]{path, Json.str(f, "file_name", path), Json.str(f, "match_type", "file")});
                    if (out.size() >= 8) break;
                }
                onMain(() -> cb.onFiles(query, out));
            }

            @Override
            public void onError(AppServer.RpcError error) {
            }
        });
    }

    /** 工具包能不能用（自检通过）。 */
    public boolean toolkitUsable() {
        return toolkitUsable;
    }

    /**
     * 终端用：和 Agent 一样的环境（PATH 里有工具包、pkg、codex；代理、证书都设好）。在后台线程调用。
     * 返回 {环境, shell 路径}。
     */
    Object[] terminalEnv() throws IOException {
        synchronized (this) {
            if (proxy == null) {
                proxy = new LocalProxy(LocalProxy.SYSTEM);
                proxy.setLocalHandler(this::handleLocalCommand);
                proxy.start();
            }
        }
        if (!paths.caFile.isFile()) Certs.export(paths.caFile, prefs.userCa());
        File bin = codexBin();
        CodexRuntime.Layout l = paths.layout(bin);
        l.toolkitReady = toolkitUsable;
        if (bin != null) CodexRuntime.prepareShims(l);
        Map<String, String> env = CodexRuntime.env(l, proxy.url(), System.getenv("PATH"), TimeZone.getDefault().getID());
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("PAGER", "cat");
        env.put("GIT_PAGER", "cat");
        paths.home.mkdirs();
        paths.tmp.mkdirs();
        Toolkit t = paths.toolkit();
        String shell = l.useToolkit() && t != null ? t.bin("bash").getAbsolutePath() : Paths.systemShell();
        return new Object[]{env, shell};
    }

    public void refreshRateLimits() {
        if (server == null) return;
        server.request("account/rateLimits/read", null, new AppServer.Callback() {
            @Override
            public void onResult(Map<String, Object> r) {
                onMain(() -> {
                    lastRateLimits = r;
                    if (r != null && accountEmail != null) prefs.setRateLimitsJson(Json.write(r));
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

    /** 聊天模式的会话放在这个空目录里（不碰工作区）。 */
    File chatDir() {
        File d = new File(paths.root, "chat");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    boolean isChatCwd(String cwd) {
        return cwd != null && cwd.equals(chatDir().getAbsolutePath());
    }

    static final String CHAT_INSTRUCTIONS = "You are in Pocket Codex's chat mode on an Android phone. This is a conversation, not a work session: "
            + "answer the user's questions directly and helpfully. Do not run shell commands and do not create or modify files — such requests "
            + "will be declined automatically. You may use web search when it helps. Reply in the language the user writes in.";

    /** 给 Agent 的环境说明：安卓上的 shell、开发工具包里有什么、缺了怎么办（pkg install）、共享存储的限制。 */
    String developerInstructions(String cwd) {
        StringBuilder b = new StringBuilder();
        b.append("You are running inside Pocket Codex on an Android phone (no root, no sudo). The working directory is ").append(cwd).append(". ");
        if (toolkitUsable) {
            Map<String, String> v = toolkitVersions;
            b.append("Developer toolkit (Termux packages, installed under $PREFIX): GNU bash, coreutils, findutils, grep, sed, gawk, diffutils, patch, "
                    + "tar, gzip, bzip2, xz, zstd, zip/unzip, curl, wget, ripgrep (rg), jq, make, tree, file, less, procps, sqlite3, openssl, ssh; ")
                    .append("Node.js ").append(ver(v, "node")).append(" with npm ").append(ver(v, "npm")).append(" and npx; git ").append(ver(v, "git"))
                    .append("; Python ").append(ver(v, "python3")).append(" with pip; and the `codex` CLI itself. ")
                    .append("If a tool is missing, install it from the Termux repository with `pkg install <name>` (also `pkg search <term>`, `pkg show <name>`, "
                            + "`pkg list-installed`; `apt`/`apt-get` are aliases), e.g. clang, cmake, golang, rust, openjdk-21, php, ruby, perl, ffmpeg, imagemagick. "
                            + "Do not download binaries by hand. ")
                    .append("Notes: npm global installs go to ~/.npm-global/bin (on PATH); `python -m venv .venv` works. "
                            + "npm resolves native binaries for platform \"android\" — when a package has no Android build, prefer a pure-JS alternative or an older release. "
                            + "Shared storage (/storage/emulated/0, /sdcard) cannot execute files or hold symlinks: there use `npm install --no-bin-links`, run tools through npx/node, "
                            + "and expect git to need core.filemode=false; heavy builds work better in the app's private workspace. "
                            + "Servers you start (e.g. a dev server on 127.0.0.1:<port>) can be opened in the phone's browser; run long-lived ones in the background (nohup … &) and tell the user the URL. "
                            + "git has a placeholder identity (Pocket Codex); ask before pushing anywhere, and pushing needs the user's own token. ");
            StringBuilder bs = new StringBuilder();
            for (com.pocketcodex.core.ToolBundles.Bundle tb : com.pocketcodex.core.ToolBundles.ALL) {
                if (!tb.id.equals("recommended") && toolkitPackages.containsAll(tb.packages)) bs.append(bs.length() > 0 ? "; " : "").append(tb.id).append(": ").append(String.join(" ", tb.packages));
            }
            b.append(bs.length() > 0 ? "Extra tool bundles installed — " + bs + ". " : "")
                    .append("More bundles (code/Git extras, APK build & test, reverse engineering, CLI video & procedural animation, CJK fonts, C/C++, Gradle/Kotlin, more languages) "
                            + "can be installed by the user in Pocket Codex → Settings → Developer tools, or package by package with pkg install. ");
        } else {
            String why = toolkitState == ToolkitState.INSTALLING ? "is being installed right now" : toolkitState == ToolkitState.BROKEN ? "failed its self-test on this device" : "is not installed";
            b.append("Shell commands run through Android's /system/bin/sh (mksh, invoked as bash) with toybox utilities only (ls, cat, grep, sed, find, head, tail, wc, sort, tar, …). ")
                    .append("The developer toolkit (Node.js/npm/npx, git, Python/pip, bash, ripgrep, curl, …) ").append(why).append(". ")
                    .append("If the task needs those tools, tell the user to open Pocket Codex → Settings → Developer tools to install it (about 57 MB), then retry; "
                            + "do not try to download binaries yourself. Prefer POSIX shell syntax. ");
        }
        b.append("Skills for this phone are in ~/.agents/skills: $pocket-toolkit (environment, pkg, MCP), $pocket-apk-build, $pocket-apk-test "
                + "(test APKs on this same phone via wireless-debugging adb), $pocket-reverse (apktool/jadx/radare2, leanrea, REA, r2mcp), $pocket-media (ffmpeg, animation); "
                + "read the relevant SKILL.md before such tasks. MCP servers you add under [mcp_servers.*] in $CODEX_HOME/config.toml are kept across restarts "
                + "(only the first managed lines are rewritten); a restart of Codex is needed to load them. ");
        b.append("Edit files with apply_patch. There is no project-level sandbox: stay inside the working directory unless the user asks otherwise. "
                + "Reply in the language the user writes in.");
        return b.toString();
    }

    private static String ver(Map<String, String> v, String k) {
        String x = v.get(k);
        return x == null || x.equals("不可用") ? "" : x;
    }

    private Map<String, Object> threadParams(String cwd) {
        boolean chat = isChatCwd(cwd);
        // 聊天模式一律“每步确认”，好让任何命令/改文件请求都被拦下来自动拒绝
        Map<String, Object> m = Json.map("sandbox", "danger-full-access", "approvalPolicy", chat ? Prefs.MODE_CONFIRM : prefs.mode(), "approvalsReviewer", "user");
        if (cwd != null) {
            m.put("cwd", cwd);
            m.put("developerInstructions", chat ? CHAT_INSTRUCTIONS : developerInstructions(cwd));
        }
        if (chat) {
            ModelInfo pm = planModel(); // 聊天：固定用套餐默认模型
            if (pm != null) m.put("model", pm.id);
        } else {
            ModelInfo mi = currentModel();
            if (prefs.model() != null && mi != null) m.put("model", mi.id);
        }
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
        sendTurn(c, text, null, cb);
    }

    public void sendTurn(final Conversation c, final String text, final List<Attachment> atts, final SendCallback cb) {
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
                        sendTurn(c, text, atts, cb);
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
        List<String> images = new ArrayList<>(), mentions = new ArrayList<>();
        StringBuilder full = new StringBuilder(text);
        List<Object> extra = new ArrayList<>();
        boolean web = false;
        if (atts != null) {
            for (Attachment a : atts) {
                switch (a.kind) {
                    case WEB_SEARCH: // 网页搜索标签：这一轮要先联网查资料（提示只发给 Codex，消息里只显示标签）
                        web = true;
                        mentions.add(Attachment.WEB_NAME);
                        break;
                    case IMAGE:
                        images.add(a.path);
                        extra.add(Json.map("type", "localImage", "path", a.path));
                        break;
                    case FILE: // 文件：工作模式用路径引用（Codex 自己读）；聊天模式不能跑命令，小文本直接附上内容
                        if (a.inline != null) {
                            full.append(full.length() > 0 ? "\n\n" : "").append("附件《").append(a.name).append("》的内容：\n```\n")
                                    .append(a.inline).append(a.inline.endsWith("\n") ? "" : "\n").append("```");
                        } else {
                            full.append(full.length() > 0 ? "\n" : "").append("附件文件：").append(a.path);
                        }
                        mentions.add(a.name);
                        break;
                    case SKILL:
                        mentions.add(a.name);
                        extra.add(Json.map("type", "skill", "name", a.id, "path", a.path));
                        break;
                    case PLUGIN:
                        mentions.add(a.name);
                        extra.add(Json.map("type", "mention", "name", a.name, "path", a.path));
                        break;
                }
            }
        }
        c.addLocalUserMessage(clientId, text, images, mentions);
        Conversation.Update u = new Conversation.Update();
        u.structure = true;
        notifyConversation(c, u);
        if (web) full.insert(0, Attachment.WEB_HINT + (full.length() > 0 ? "\n\n" : ""));
        List<Object> input = new ArrayList<>();
        if (full.length() > 0) input.add(Json.map("type", "text", "text", full.toString()));
        input.addAll(extra);
        boolean chat = isChatCwd(currentCwd(c));
        Map<String, Object> p = Json.map("threadId", c.threadId, "input", input, "clientUserMessageId", clientId,
                "approvalPolicy", chat ? Prefs.MODE_CONFIRM : prefs.mode());
        if (chat) {
            // 聊天：套餐默认模型 + 它自己的默认推理强度，不用“快速”档；工作模式里选的模型和推理不影响聊天
            ModelInfo pm = planModel();
            if (pm != null) p.put("model", pm.id);
            String ce = chatEffort(pm);
            if (ce != null) p.put("effort", ce);
        } else {
            ModelInfo mi = currentModel();
            if (prefs.model() != null && mi != null) p.put("model", mi.id);
            if (prefs.effort() != null) p.put("effort", prefs.effort());
            if (prefs.speed() != null && mi != null && mi.serviceTiers.contains(prefs.speed())) p.put("serviceTier", prefs.speed());
        }
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
            if (toolkitRestartWanted && runningThreadId == null && pendingApprovalCount() == 0) main.postDelayed(this::restartForToolkit, 1500);
        }
        if (u.any()) notifyConversation(c, u);
    }

    private void handleServerRequest(Object id, String method, Map<String, Object> params) {
        String tid = Json.str(params, "threadId");
        Conversation c = tid == null ? null : conversations.get(tid);
        if (c != null && isChatCwd(cwdOf.get(tid)) && !method.equals("item/tool/requestUserInput")
                && (method.equals("item/commandExecution/requestApproval") || method.equals("item/fileChange/requestApproval"))) {
            // 聊天模式不碰文件、不跑命令：自动拒绝，并在对话里说一声
            if (server != null) server.respond(id, Json.map("decision", "decline"));
            Conversation.Update u = c.apply("warning", Json.map("threadId", tid, "message", "聊天模式不会运行命令或修改文件，已自动拒绝。需要动手时切到“工作”。"));
            notifyConversation(c, u);
            return;
        }
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
