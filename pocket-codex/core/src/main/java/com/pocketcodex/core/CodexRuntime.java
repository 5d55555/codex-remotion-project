package com.pocketcodex.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在安卓上跑 musl 静态 codex 需要的运行环境：目录、环境变量、config.toml、shell 兼容层。
 * <p>
 * 实测（chroot 模拟安卓文件系统）：
 * <ul>
 *   <li>没有 /etc/resolv.conf → 用 HTTPS_PROXY 指向 App 里的 {@link LocalProxy}（数字 IP，不需要 DNS）；</li>
 *   <li>没有 /etc/ssl → SSL_CERT_FILE / CODEX_CA_CERTIFICATE 指向导出的 CA 证书；</li>
 *   <li>没有 /etc/passwd 和 /bin → codex 忽略 $SHELL，先在 PATH 里找 bash，找不到就用不存在的 /bin/sh。
 *       所以在 PATH 最前面放一个叫 bash 的符号链接指向系统 shell（安卓上是 /system/bin/sh，即 mksh）。</li>
 * </ul>
 */
public final class CodexRuntime {

    /** 所有路径都在 App 私有目录里。 */
    public static final class Layout {
        public final File codexBin;      // .../codex/versions/x/bin/codex
        public final File codexHome;     // CODEX_HOME：auth.json、config.toml、sessions
        public final File homeDir;       // HOME（和 CODEX_HOME 分开，Agent 的 ~ 不直接落在凭据目录）
        public final File binDir;        // 放 bash 兼容链接，排在 PATH 最前
        public final File tmpDir;        // TMPDIR
        public final File caFile;        // PEM 证书
        public final File logDir;        // 登录输出等
        public final String systemShell; // /system/bin/sh
        /** 开发工具包（node、git、bash……）；null 或没通过自检时 Agent 只有系统的 toybox 命令。 */
        public Toolkit toolkit;
        public boolean toolkitReady;
        /** App 的数据目录（/data/user/0/包名），给 termux-exec 用。 */
        public String appDataDir;

        public boolean useToolkit() {
            return toolkit != null && toolkitReady && toolkit.bin("bash").isFile();
        }

        public Layout(File codexBin, File codexHome, File homeDir, File binDir, File tmpDir, File caFile, File logDir, String systemShell) {
            this.codexBin = codexBin;
            this.codexHome = codexHome;
            this.homeDir = homeDir;
            this.binDir = binDir;
            this.tmpDir = tmpDir;
            this.caFile = caFile;
            this.logDir = logDir;
            this.systemShell = systemShell;
        }
    }

    private CodexRuntime() {
    }

    /** 子进程的完整环境（不继承 App 进程的）。basePath 是系统原来的 PATH（安卓上 System.getenv("PATH")）。 */
    public static Map<String, String> env(Layout l, String proxyUrl, String basePath, String timeZone) {
        Map<String, String> e = new LinkedHashMap<>();
        String path = l.binDir.getAbsolutePath();
        boolean tk = l.useToolkit();
        if (tk) for (String d : l.toolkit.pathDirs(l.homeDir)) path += ":" + d;
        if (basePath != null && !basePath.isEmpty()) path += ":" + basePath;
        else path += ":/system/bin:/system/xbin:/vendor/bin";
        e.put("PATH", path);
        e.put("HOME", l.homeDir.getAbsolutePath());
        e.put("CODEX_HOME", l.codexHome.getAbsolutePath());
        String tmp = l.tmpDir.getAbsolutePath();
        e.put("TMPDIR", tmp);
        e.put("TMP", tmp);
        e.put("TEMP", tmp);
        e.put("SHELL", new File(l.binDir, "bash").getAbsolutePath());
        e.put("TERM", "dumb");
        e.put("LANG", "C.UTF-8");
        e.put("LC_ALL", "C.UTF-8");
        e.put("NO_COLOR", "1");
        if (timeZone != null && !timeZone.isEmpty()) e.put("TZ", timeZone);
        if (l.caFile != null && l.caFile.isFile()) {
            e.put("SSL_CERT_FILE", l.caFile.getAbsolutePath());
            e.put("CODEX_CA_CERTIFICATE", l.caFile.getAbsolutePath());
        }
        if (proxyUrl != null) {
            // 不设 ALL_PROXY：不同工具对它的解释不一致（Astra）
            e.put("HTTPS_PROXY", proxyUrl);
            e.put("HTTP_PROXY", proxyUrl);
            e.put("https_proxy", proxyUrl);
            e.put("http_proxy", proxyUrl);
            e.put("NO_PROXY", "localhost,127.0.0.1,::1");
            e.put("no_proxy", "localhost,127.0.0.1,::1");
        }
        if (tk) e.putAll(l.toolkit.env(l.appDataDir != null ? l.appDataDir : "", l.homeDir, l.caFile != null && l.caFile.isFile() ? l.caFile : null));
        // 安卓自己的几个变量：toybox、mksh 等系统命令需要（白名单，不继承 LD_PRELOAD 之类）
        for (String k : new String[]{"ANDROID_ROOT", "ANDROID_DATA", "ANDROID_STORAGE", "EXTERNAL_STORAGE", "ANDROID_ART_ROOT",
                "ANDROID_I18N_ROOT", "ANDROID_TZDATA_ROOT", "BOOTCLASSPATH", "ANDROID_ASSETS"}) {
            String v = System.getenv(k);
            if (v != null) e.put(k, v);
        }
        return e;
    }

    /** codex 命令行（口袋工坊的设置用 -c 传，见 {@link #overrides}）。 */
    public static List<String> command(Layout l, String... args) {
        return command(l, null, args);
    }

    public static List<String> command(Layout l, Config cfg, String... args) {
        List<String> c = new ArrayList<>();
        c.add(l.codexBin.getAbsolutePath());
        c.addAll(overrides(cfg == null ? new Config() : cfg));
        for (String a : args) c.add(a);
        return c;
    }

    /**
     * 口袋工坊管的设置，用命令行 -c 传给 codex（优先级高于 config.toml）。这样 config.toml 完全留给用户和 Agent
     * （例如 [mcp_servers.*]），我们不再改写它，也就不会写坏或丢掉别人的配置。
     */
    public static List<String> overrides(Config cfg) {
        List<String> o = new ArrayList<>();
        // 安卓上没有系统钥匙串：凭据存文件
        o.add("-c");
        o.add("cli_auth_credentials_store=\"file\"");
        // 没有 bubblewrap / 用户命名空间：codex 自己的沙箱用不了，由安卓的应用沙箱兜底（见 App 的说明）
        o.add("-c");
        o.add("sandbox_mode=\"danger-full-access\"");
        o.add("-c");
        o.add("check_for_update_on_startup=false");
        if (cfg.model != null && !cfg.model.isEmpty()) {
            o.add("-c");
            o.add("model=" + tomlString(cfg.model));
        }
        if (cfg.reasoningEffort != null && !cfg.reasoningEffort.isEmpty()) {
            o.add("-c");
            o.add("model_reasoning_effort=" + tomlString(cfg.reasoningEffort));
        }
        o.add("-c");
        o.add("web_search=" + tomlString(cfg.webSearch ? "live" : "disabled"));
        // shell 快照会用 bash 专有的命令探测环境；安卓的 mksh 冒充 bash 时没有意义，关掉省一次子进程
        o.add("-c");
        o.add("features.shell_snapshot=false");
        o.add("-c");
        o.add("shell_environment_policy.inherit=\"all\"");
        // 不传 approval_policy：0.166 起 "untrusted" 写进配置会拒绝启动（实测），审批模式在每次 thread/start、turn/start 里显式传
        return o;
    }

    /**
     * 建目录、PATH 最前面的几个命令、config.toml。每次启动 codex 前调用（很快）。
     * <ul>
     * <li>bash：工具包可用时指向真的 bash，否则指向系统 shell（mksh）；</li>
     * <li>codex：指向当前安装的 codex，Agent 里也能直接用 codex 命令；</li>
     * <li>pkg / apt / apt-get：让 App 从 Termux 仓库装软件包（{@link Toolkit#PKG_SCRIPT}）。</li>
     * </ul>
     */
    public static void prepare(Layout l, Config cfg) throws IOException {
        prepareShims(l);
        ensureConfig(l.codexHome);
    }

    /** 只建目录和 PATH 最前面的命令（不动 config.toml）：App 里的终端也用这套环境。 */
    public static void prepareShims(Layout l) throws IOException {
        for (File d : new File[]{l.codexHome, l.homeDir, l.binDir, l.tmpDir, l.logDir}) {
            if (!d.isDirectory() && !d.mkdirs()) throw new IOException("建目录失败：" + d);
        }
        ensureLink(new File(l.binDir, "bash"), l.useToolkit() ? l.toolkit.bin("bash").getAbsolutePath() : l.systemShell);
        if (l.codexBin != null) ensureLink(new File(l.binDir, "codex"), l.codexBin.getAbsolutePath());
        for (String n : new String[]{"pkg", "apt", "apt-get"}) writeIfChanged(new File(l.binDir, n), Toolkit.PKG_SCRIPT, true);
        if (l.useToolkit()) {
            File gc = new File(l.homeDir, ".gitconfig");
            if (!gc.exists()) writeIfChanged(gc, DEFAULT_GITCONFIG, false);
        }
    }

    /** 第一次有工具包时写到 HOME 的 git 配置（用户或 Agent 之后可以改）。 */
    static final String DEFAULT_GITCONFIG = "# 口袋工坊生成的默认配置，可以随意修改。\n"
            + "[user]\n\tname = Pocket Codex\n\temail = pocket-codex@localhost\n"
            + "[safe]\n\t# 共享存储里的文件属于别的用户 ID，不加这一行 git 会拒绝操作\n\tdirectory = *\n"
            + "[init]\n\tdefaultBranch = main\n"
            + "[core]\n\tpager = cat\n"
            + "[credential]\n\thelper = store\n";

    static void writeIfChanged(File f, String content, boolean executable) throws IOException {
        byte[] b = content.getBytes(StandardCharsets.UTF_8);
        if (f.isFile() && !Files.isSymbolicLink(f.toPath()) && java.util.Arrays.equals(Files.readAllBytes(f.toPath()), b)) {
            if (executable && !f.canExecute()) f.setExecutable(true, true);
            return;
        }
        if (Files.isSymbolicLink(f.toPath())) Files.delete(f.toPath());
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(b);
        }
        if (executable && !tmp.setExecutable(true, true)) throw new IOException("设置可执行权限失败：" + f);
        if (!tmp.renameTo(f)) throw new IOException("写入失败：" + f);
    }

    /** 让 name 指向 target（已经对就不动）。 */
    static void ensureLink(File link, String target) throws IOException {
        Path p = link.toPath();
        if (Files.isSymbolicLink(p)) {
            if (Files.readSymbolicLink(p).toString().equals(target)) return;
            Files.delete(p);
        } else if (link.exists()) {
            if (!link.delete()) throw new IOException("删不掉：" + link);
        }
        Files.createSymbolicLink(p, new File(target).toPath());
    }

    /** 写进 config.toml 的几项（每次覆盖写；用户在 App 里改设置）。 */
    public static final class Config {
        /** 审批：untrusted（每步确认，默认）/ never（完全自动）。每次开会话、发消息时也会显式传。 */
        public String approvalPolicy = "untrusted";
        public String model;            // null = codex 默认
        public String reasoningEffort;  // null = 默认
        public boolean webSearch = true;

        public Config() {
        }
    }

    static final String HEADER = "# 口袋工坊的设置（模型、沙箱、凭据存储……）在启动时用命令行 -c 传给 Codex，不写在这里。\n"
            + "# 这个文件留给你和 Agent：例如在下面加 [mcp_servers.名字]。改完在 设置 → 技能与插件 → 重启 Codex。\n";
    /** v0.2 及以前：整份都是我们生成的（每次启动覆盖）。 */
    static final String LEGACY_V02 = "# 由 Pocket Codex 生成，每次启动会覆盖。";
    /** v0.3：开头几行是我们写的，后面保留用户的。 */
    static final String LEGACY_V03 = "# 由口袋工坊生成：下面几行每次启动会重写；";
    static final java.util.Set<String> V03_KEYS = new java.util.HashSet<>(java.util.Arrays.asList(
            "cli_auth_credentials_store", "sandbox_mode", "check_for_update_on_startup", "model", "model_reasoning_effort", "web_search",
            "features.shell_snapshot", "shell_environment_policy.inherit"));

    /** 没有 config.toml 就建一个只有说明的；旧版口袋工坊写的内容迁移掉（只动我们自己写过的行）。 */
    static void ensureConfig(File codexHome) throws IOException {
        File f = new File(codexHome, "config.toml");
        String old = f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        String now = migrateConfig(old);
        if (old != null && now.equals(old)) return;
        File bak = new File(codexHome, "config.toml.bak");
        if (old != null && !old.trim().isEmpty()) Files.write(bak.toPath(), old.getBytes(StandardCharsets.UTF_8)); // 迁移前留一份
        File tmp = new File(codexHome, "config.toml.tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(now.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) throw new IOException("写 config.toml 失败");
    }

    /** v0.3 第二行说明。 */
    static final String LEGACY_V03_2 = "# 之后的内容（你或 Agent 加的配置，例如 [mcp_servers.*]）会原样保留。";

    /**
     * 返回应该写入的内容：用户自己的文件原样返回。
     * 旧版口袋工坊写的文件只删能确认是我们写的行：开头那一两行说明、第一个表之前我们管的单行键、
     * [features] 里的 shell_snapshot = false 和 [shell_environment_policy] 里的 inherit = "all"（删空了连表头一起删）。
     * 多行字符串里的内容一律不动；用户在后面加的东西（MCP 等）全部保留。删掉的设置每次启动都用 -c 传，效果不变。
     */
    static String migrateConfig(String old) {
        if (old == null) return HEADER;
        if (!old.startsWith(LEGACY_V02) && !old.startsWith(LEGACY_V03)) return old;
        List<String> out = new ArrayList<>();
        String table = null;       // 当前表头（null = 顶层）
        int tableAt = -1;          // 当前表头在 out 里的位置
        boolean tableHasContent = false, header = true;
        TomlScan sc = new TomlScan();
        for (String line : old.split("\n", -1)) {
            String t = line.trim();
            boolean free = sc.ml == null && sc.depth == 0;   // 这一行从顶层开始（不在字符串、数组、内联表里）
            sc.scan(line);
            boolean complete = sc.ml == null && sc.depth == 0; // 这一行结束时也回到顶层
            if (!free) {           // 多行字符串、跨行数组的内容：原样保留
                out.add(line);
                if (table != null) tableHasContent = true;
                continue;
            }
            if (header) {
                if (t.equals(LEGACY_V02) || t.equals(LEGACY_V03) || t.equals(LEGACY_V03_2)) continue;
                header = false;
            }
            if (t.startsWith("[") && complete) {
                if (table != null && !tableHasContent && isOurTable(table)) dropTable(out, tableAt);
                table = t.replace(" ", "");
                tableAt = out.size();
                tableHasContent = false;
                out.add(line);
                continue;
            }
            int eq = t.startsWith("#") ? -1 : t.indexOf('=');
            if (eq > 0 && complete) {
                String key = t.substring(0, eq).trim(), value = t.substring(eq + 1).trim();
                if (table == null && V03_KEYS.contains(key)) continue;
                if ("[features]".equals(table) && key.equals("shell_snapshot") && value.equals("false")) continue;
                if ("[shell_environment_policy]".equals(table) && key.equals("inherit") && value.equals("\"all\"")) continue;
            }
            if (table == null && t.isEmpty() && out.isEmpty()) continue; // 开头的空行
            if (table != null && !t.isEmpty()) tableHasContent = true;
            out.add(line);
        }
        if (table != null && !tableHasContent && isOurTable(table)) dropTable(out, tableAt);
        StringBuilder b = new StringBuilder(HEADER);
        for (String line : out) b.append(line).append('\n');
        String r = b.toString();
        while (r.endsWith("\n\n")) r = r.substring(0, r.length() - 1);
        return r;
    }

    /**
     * 逐行扫 TOML 用的跨行状态：是否在多行字符串里（定界符）、方括号/花括号的深度。
     * 处理基本字符串里的转义（\" 和 \"""不算结束）、字面量字符串（没有转义）、注释。
     */
    static final class TomlScan {
        String ml;
        int depth;

        void scan(String line) {
            int i = 0, n = line.length();
            while (i < n) {
                if (ml != null) {
                    if (ml.equals("\"\"\"")) {
                        char c = line.charAt(i);
                        if (c == '\\') {
                            i += 2;                     // 转义：下一个字符（包括引号）不算
                            continue;
                        }
                        if (line.startsWith("\"\"\"", i)) {
                            i += 3;
                            while (i < n && line.charAt(i) == '"') i++; // 结束符前最多可以再有两个引号
                            ml = null;
                            continue;
                        }
                        i++;
                    } else {
                        int j = line.indexOf("\'\'\'", i);
                        if (j < 0) return;
                        i = j + 3;
                        while (i < n && line.charAt(i) == '\'') i++;
                        ml = null;
                    }
                    continue;
                }
                char c = line.charAt(i);
                if (c == '#') return;                   // 注释到行尾
                if (line.startsWith("\"\"\"", i)) {
                    ml = "\"\"\"";
                    i += 3;
                    continue;
                }
                if (line.startsWith("\'\'\'", i)) {
                    ml = "\'\'\'";
                    i += 3;
                    continue;
                }
                if (c == '"') {                         // 单行基本字符串
                    i++;
                    while (i < n) {
                        char d = line.charAt(i);
                        if (d == '\\') {
                            i += 2;
                            continue;
                        }
                        i++;
                        if (d == '"') break;
                    }
                    continue;
                }
                if (c == '\'') {                        // 单行字面量字符串
                    int j = line.indexOf('\'', i + 1);
                    i = j < 0 ? n : j + 1;
                    continue;
                }
                if (c == '[' || c == '{') depth++;
                else if ((c == ']' || c == '}') && depth > 0) depth--;
                i++;
            }
        }
    }

    private static boolean isOurTable(String table) {
        return table.equals("[features]") || table.equals("[shell_environment_policy]");
    }

    /** 删掉 from 开始的表头和它后面的空行/注释（这个表只剩我们删过的东西）。 */
    private static void dropTable(List<String> out, int from) {
        while (out.size() > from) out.remove(out.size() - 1);
        while (!out.isEmpty() && out.get(out.size() - 1).trim().isEmpty()) out.remove(out.size() - 1);
    }

    static String tomlString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    /** 有没有保存的凭据（auth.json 在且非空）。是否真的登录有效要用 account/read 确认。 */
    public static boolean hasStoredCredentials(Layout l) {
        File a = new File(l.codexHome, "auth.json");
        return a.isFile() && a.length() > 2;
    }
}
