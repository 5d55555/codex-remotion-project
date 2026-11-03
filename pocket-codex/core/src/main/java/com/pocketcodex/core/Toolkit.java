package com.pocketcodex.core;

import com.pocketcodex.core.Installer.InstallException;
import com.pocketcodex.core.Installer.Kind;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * 开发工具包：把 Termux 官方仓库（termux-main）的 .deb 装进 App 私有目录里的前缀（例如 …/runtime/usr），
 * 给 Agent 用 node/npm/npx、git、bash、python/pip、rg 等。
 * <p>
 * Termux 的包是为 /data/data/com.termux/files/usr 编译的，搬到别的目录要做这些（都在模拟的安卓 bionic 环境里实测过）：
 * <ul>
 * <li>ELF 的 RUNPATH 改成 $ORIGIN 相对路径（{@link Elf}），程序能找到自己的库，不用 LD_LIBRARY_PATH；</li>
 * <li>ELF 里编译进去的 “前缀/bin/sh” 改成等长的 “//…/system/bin/sh”（Node 的 child_process.exec、make、awk 的 system()）；</li>
 * <li>文本文件（脚本的 #!、配置）里的旧前缀换成新前缀；指向旧前缀的符号链接改成相对链接；</li>
 * <li>运行时用 termux-exec 的 LD_PRELOAD 处理 #!/usr/bin/env node、#!/bin/sh（环境变量见 {@link #env}）。</li>
 * </ul>
 * 完整性：InRelease 用内置的 Termux 公钥验签（{@link OpenPgp}）→ 里面的 Packages.gz 摘要 → 每个 .deb 的 SHA256。所以镜像也能放心用。
 */
public final class Toolkit {
    public static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    private static final byte[] OLD = TERMUX_PREFIX.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OLD_SH = (TERMUX_PREFIX + "/bin/sh").getBytes(StandardCharsets.US_ASCII);

    public static final class Repo {
        public final String id, label, url;

        public Repo(String id, String label, String url) {
            this.id = id;
            this.label = label;
            this.url = url;
        }
    }

    public static final Repo OFFICIAL = new Repo("termux", "Termux 官方", "https://packages.termux.dev/apt/termux-main");
    /** 官方 + 镜像（索引有签名，哪个能连上用哪个）。 */
    public static final List<Repo> REPOS = Collections.unmodifiableList(Arrays.asList(
            OFFICIAL,
            new Repo("ustc", "中科大镜像", "https://mirrors.ustc.edu.cn/termux/apt/termux-main"),
            new Repo("tuna", "清华镜像", "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main"),
            new Repo("bfsu", "北外镜像", "https://mirrors.bfsu.edu.cn/termux/apt/termux-main"),
            new Repo("aliyun", "阿里云镜像", "https://mirrors.aliyun.com/termux/termux-main"),
            new Repo("nju", "南大镜像", "https://mirrors.nju.edu.cn/termux/apt/termux-main"),
            new Repo("grimler", "grimler.se（欧洲）", "https://grimler.se/termux/termux-main")));

    /** 基础工具包。 */
    public static final List<String> BASE = Collections.unmodifiableList(Arrays.asList(
            "bash", "coreutils", "findutils", "grep", "sed", "gawk", "diffutils", "patch", "tar", "gzip", "bzip2", "xz-utils", "zstd",
            "unzip", "zip", "curl", "wget", "git", "nodejs-lts", "npm", "python", "python-pip", "ripgrep", "jq", "make", "tree", "file",
            "less", "nano", "procps", "psmisc", "which", "openssl-tool", "openssh", "sqlite", "dash", "ncurses-utils", "termux-exec",
            "ca-certificates", "resolv-conf"));

    /** Termux App 专用、在这里没用（或会误导）的包：依赖里出现也不装。 */
    static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "termux-tools", "termux-am", "termux-am-socket", "termux-keyring", "termux-licenses", "termux-core", "termux-auth",
            "termux-api", "termux-services", "termux-exec-dummy", "termux-x11-nightly"));

    /** 不解压的目录（手册、文档、翻译）：省一百多 MB 空间和时间。 */
    private static final String[] DROP = {"share/man/", "share/doc/", "share/info/", "share/locale/", "share/examples/", "share/gtk-doc/"};

    /* ---------------- 索引 ---------------- */

    public static final class Pkg {
        public final String name, version, filename, sha256, description;
        public final long size, installedKb;
        final List<String[]> depends;
        final List<String> provides;

        Pkg(Map<String, String> f) {
            name = f.get("Package");
            version = f.containsKey("Version") ? f.get("Version") : "";
            filename = f.get("Filename");
            sha256 = f.containsKey("SHA256") ? f.get("SHA256").toLowerCase(Locale.ROOT) : "";
            description = f.containsKey("Description") ? f.get("Description") : "";
            size = parseLong(f.get("Size"));
            installedKb = parseLong(f.get("Installed-Size"));
            depends = new ArrayList<>();
            for (String key : new String[]{"Pre-Depends", "Depends"}) {
                String d = f.get(key);
                if (d == null) continue;
                for (String grp : d.split(",")) {
                    grp = grp.trim();
                    if (grp.isEmpty()) continue;
                    String[] alts = grp.split("\\|");
                    for (int i = 0; i < alts.length; i++) alts[i] = stripVersion(alts[i]);
                    depends.add(alts);
                }
            }
            provides = new ArrayList<>();
            String p = f.get("Provides");
            if (p != null) for (String x : p.split(",")) if (!stripVersion(x).isEmpty()) provides.add(stripVersion(x));
        }

        private static String stripVersion(String s) {
            int i = s.indexOf('(');
            if (i >= 0) s = s.substring(0, i);
            i = s.indexOf(':'); // 多架构限定 name:any
            if (i >= 0) s = s.substring(0, i);
            return s.trim();
        }
    }

    public static final class Index {
        public final Map<String, Pkg> pkgs;
        final Map<String, String> provides;
        public final String repoUrl, date;

        Index(Map<String, Pkg> pkgs, String repoUrl, String date) {
            this.pkgs = pkgs;
            this.repoUrl = repoUrl;
            this.date = date;
            provides = new HashMap<>();
            for (Pkg p : pkgs.values()) for (String v : p.provides) if (!provides.containsKey(v)) provides.put(v, p.name);
        }

        public Pkg find(String name) {
            Pkg p = pkgs.get(name);
            if (p == null && provides.containsKey(name)) p = pkgs.get(provides.get(name));
            return p;
        }
    }

    /* ---------------- 进度 ---------------- */

    public interface Progress {
        /** 一句给人看的进度；done/total 是字节（不知道为 -1）。 */
        void step(String text, long done, long total);

        boolean cancelled();
    }

    public static final Progress SILENT = new Progress() {
        @Override
        public void step(String text, long done, long total) {
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    };

    /* ---------------- 实例 ---------------- */

    public final File prefix;
    final File work;
    public final String arch;
    private final Object lock = new Object();
    private int connectTimeoutMs = 20_000, readTimeoutMs = 30_000;
    private Index cachedIndex;
    /** 测试用：替换仓库列表、基础包列表、信任的公钥。 */
    List<Repo> repos = REPOS;
    List<String> base = BASE;
    List<OpenPgp.Key> trustedKeys;

    public Toolkit(File prefix, File work, String abi) throws InstallException {
        this.prefix = prefix;
        this.work = work;
        String a = abi.toLowerCase(Locale.ROOT);
        if (a.startsWith("arm64") || a.equals("aarch64")) arch = "aarch64";
        else if (a.equals("x86_64") || a.equals("amd64")) arch = "x86_64";
        else throw new InstallException(Kind.UNSUPPORTED_ABI, "这台设备的处理器（" + abi + "）不支持开发工具包", null);
    }

    void setTimeouts(int connectMs, int readMs) {
        connectTimeoutMs = connectMs;
        readTimeoutMs = readMs;
    }

    public void setRepos(List<Repo> r) {
        repos = r;
    }

    /** 装好了（整体替换成功后才有状态文件）。 */
    public boolean installed() {
        return statusFile(prefix).isFile();
    }

    public File bin(String name) {
        return new File(prefix, "bin/" + name);
    }

    /* ---------------- 状态（装了哪些包、各自的文件） ---------------- */

    public static final class Installed {
        public final String name, version;
        public final boolean explicit;
        final List<String> files;
        /** 包里 postinst 的 update-alternatives 链接已经处理过（v0.3.1 以前装的包没有处理，见 {@link #repairAlternatives}）。 */
        final boolean alt;

        Installed(String name, String version, boolean explicit, List<String> files, boolean alt) {
            this.name = name;
            this.version = version;
            this.explicit = explicit;
            this.files = files;
            this.alt = alt;
        }
    }

    static File statusFile(File prefix) {
        return new File(prefix, "var/lib/pocket-pkg/status.json");
    }

    public Map<String, Installed> status() {
        return readStatus(prefix);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Installed> readStatus(File prefix) {
        Map<String, Installed> out = new TreeMap<>();
        File f = statusFile(prefix);
        if (!f.isFile()) return out;
        try {
            Map<String, Object> root = Json.parseObject(new String(Installer.readAll(f), StandardCharsets.UTF_8));
            Map<String, Object> pk = Json.obj(root, "packages");
            if (pk == null) return out;
            for (Map.Entry<String, Object> e : pk.entrySet()) {
                if (!(e.getValue() instanceof Map)) continue;
                Object v = e.getValue();
                List<String> files = new ArrayList<>();
                List<Object> fl = Json.arr(v, "files");
                if (fl != null) for (Object o : fl) if (o instanceof String) files.add((String) o);
                String ver = Json.str(v, "version", "");
                out.put(e.getKey(), new Installed(e.getKey(), ver, Json.bool(v, "explicit", false), files, Json.bool(v, "alt", false)));
            }
        } catch (Exception ignored) {
            // 状态坏了：当作空的（重装工具包能修好）
        }
        return out;
    }

    static void writeStatus(File prefix, Map<String, Installed> st, String repo) throws IOException {
        Map<String, Object> pk = new TreeMap<>();
        for (Installed i : st.values()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("version", i.version);
            v.put("explicit", i.explicit);
            v.put("files", new ArrayList<Object>(i.files));
            v.put("alt", i.alt);
            pk.put(i.name, v);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", 1);
        root.put("repo", repo);
        root.put("packages", pk);
        File f = statusFile(prefix);
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(Json.write(root).getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        }
        if (!tmp.renameTo(f)) throw new IOException("写状态文件失败");
    }

    /* ---------------- 网络 ---------------- */

    private HttpURLConnection open(String url, long from) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectTimeoutMs);
        c.setReadTimeout(readTimeoutMs);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "pocket-codex-toolkit");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (from > 0) c.setRequestProperty("Range", "bytes=" + from + "-");
        return c;
    }

    private byte[] getSmall(String url, int limit) throws IOException {
        HttpURLConnection c = open(url, 0);
        try {
            int code = c.getResponseCode();
            if (code == 404) throw new java.io.FileNotFoundException(url);
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                return Installer.readAll(in, limit);
            }
        } finally {
            c.disconnect();
        }
    }

    /** 下载并校验 SHA256；file 已存在且校验通过就不下。支持续传，网络错误重试。 */
    void download(List<String> urls, File file, String sha256, long size, String label, Progress p) throws InstallException {
        if (file.isFile() && file.length() == size && sha256.equals(sha256Hex(file))) return;
        File part = new File(file.getPath() + ".part");
        int attempts = 0;
        int which = 0;
        while (true) {
            if (p.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
            String url = urls.get(which % urls.size());
            long have = part.isFile() ? part.length() : 0;
            if (have > size) {
                part.delete();
                have = 0;
            }
            HttpURLConnection c = null;
            try {
                if (have < size) {
                    c = open(url, have);
                    int code = c.getResponseCode();
                    boolean append;
                    if (code == 206) append = true;
                    else if (code == 200) {
                        append = false;
                        have = 0;
                    } else if (code == 404) throw new java.io.FileNotFoundException("HTTP 404");
                    else throw new IOException("HTTP " + code);
                    try (InputStream in = new BufferedInputStream(c.getInputStream(), 64 * 1024);
                         OutputStream out = new FileOutputStream(part, append)) {
                        byte[] buf = new byte[64 * 1024];
                        long done = have, last = 0;
                        int n;
                        while ((n = in.read(buf)) >= 0) {
                            if (p.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
                            if (done + n > size) throw new IOException("下载的大小不对");
                            out.write(buf, 0, n);
                            done += n;
                            if (done - last >= (256 << 10)) {
                                last = done;
                                p.step(label, done, size);
                            }
                        }
                        if (done < size) throw new IOException("连接提前断开");
                    }
                }
                if (!sha256.equals(sha256Hex(part))) {
                    part.delete();
                    throw new IOException("校验不通过");
                }
                if (file.exists()) file.delete();
                if (!part.renameTo(file)) throw new IOException("改名失败");
                return;
            } catch (InstallException e) {
                throw e;
            } catch (IOException e) {
                if (p.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", e);
                if (Installer.noSpace(e)) throw new InstallException(Kind.NO_SPACE, "存储空间不够", e);
                attempts++;
                which++; // 换下一个源
                if (attempts > Math.max(4, urls.size() * 2)) {
                    throw new InstallException(Kind.NETWORK, label + " 下载失败：" + e.getMessage(), e);
                }
            } finally {
                if (c != null) c.disconnect();
            }
            sleep(Math.min(6000, 500L << Math.min(attempts, 4)), p);
        }
    }

    private static void sleep(long ms, Progress p) throws InstallException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (p.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new InstallException(Kind.CANCELLED, "已取消", e);
            }
        }
    }

    static String sha256Hex(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
            return hex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return "";
        }
    }

    static String sha256Hex(byte[] b) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    /* ---------------- 取索引 ---------------- */

    /** 依次试各个源，返回第一个签名和摘要都对的索引；全失败抛最后一个错误。preferred 排在最前（可为 null）。 */
    public Index fetchIndex(String preferredRepoId, Progress p) throws InstallException {
        List<Repo> order = new ArrayList<>(repos);
        if (preferredRepoId != null) {
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).id.equals(preferredRepoId)) {
                    order.add(0, order.remove(i));
                    break;
                }
            }
        }
        InstallException last = null;
        for (Repo r : order) {
            if (p.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
            p.step("读取软件包索引（" + r.label + "）", -1, -1);
            try {
                Index idx = fetchIndex(r);
                synchronized (lock) {
                    cachedIndex = idx;
                }
                return idx;
            } catch (InstallException e) {
                last = e;
            }
        }
        throw last != null ? last : new InstallException(Kind.NETWORK, "没有可用的软件源", null);
    }

    Index fetchIndex(Repo r) throws InstallException {
        byte[] inRelease;
        try {
            inRelease = getSmall(r.url + "/dists/stable/InRelease", 1 << 20);
        } catch (IOException e) {
            throw new InstallException(Kind.NETWORK, r.label + "：连不上（" + e.getMessage() + "）", e);
        }
        String rel = "main/binary-" + arch + "/Packages.gz";
        String[] want = verifyRelease(inRelease, rel);
        byte[] gz;
        try {
            gz = getSmall(r.url + "/dists/stable/" + rel, 32 << 20);
        } catch (IOException e) {
            throw new InstallException(Kind.NETWORK, r.label + "：索引下载失败（" + e.getMessage() + "）", e);
        }
        if (!sha256Hex(gz).equals(want[0]) || gz.length != Long.parseLong(want[1])) {
            throw new InstallException(Kind.CORRUPT, r.label + "：索引和签名里的摘要不符（镜像可能正在同步）", null);
        }
        Index idx = parseIndex(gunzip(gz), r.url, want[2]);
        work.mkdirs();
        try {
            writeAtomic(new File(work, "InRelease-" + arch), inRelease);
            writeAtomic(new File(work, "Packages-" + arch + ".gz"), gz);
            writeAtomic(new File(work, "repo-" + arch), r.url.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 缓存写不了不影响这次
        }
        return idx;
    }

    /** 验签并找出 Packages.gz 的 {sha256, size, date}。 */
    String[] verifyRelease(byte[] inRelease, String path) throws InstallException {
        OpenPgp.Verified v;
        try {
            String text = new String(inRelease, StandardCharsets.UTF_8);
            v = trustedKeys != null ? OpenPgp.verifyClearsigned(text, trustedKeys) : OpenPgp.verifyClearsigned(text);
        } catch (IOException e) {
            throw new InstallException(Kind.CORRUPT, "软件源签名验证失败：" + e.getMessage(), e);
        }
        String date = "";
        boolean inSha = false;
        for (String line : v.text.split("\n")) {
            if (line.startsWith("Date:")) date = line.substring(5).trim();
            if (!line.startsWith(" ")) {
                inSha = line.startsWith("SHA256:");
                continue;
            }
            if (!inSha) continue;
            String[] f = line.trim().split("\\s+");
            if (f.length == 3 && f[2].equals(path)) return new String[]{f[0].toLowerCase(Locale.ROOT), f[1], date};
        }
        throw new InstallException(Kind.NOT_FOUND, "软件源里没有 " + path, null);
    }

    /** 本地缓存的索引（重新验签），没有或坏了返回 null。 */
    public Index cachedIndex() {
        synchronized (lock) {
            if (cachedIndex != null) return cachedIndex;
        }
        try {
            File ir = new File(work, "InRelease-" + arch), gz = new File(work, "Packages-" + arch + ".gz"), repo = new File(work, "repo-" + arch);
            if (!ir.isFile() || !gz.isFile()) return null;
            String[] want = verifyRelease(Installer.readAll(ir), "main/binary-" + arch + "/Packages.gz");
            byte[] g = Installer.readAll(gz);
            if (!sha256Hex(g).equals(want[0])) return null;
            String url = repo.isFile() ? new String(Installer.readAll(repo), StandardCharsets.UTF_8).trim() : OFFICIAL.url;
            Index idx = parseIndex(gunzip(g), url, want[2]);
            synchronized (lock) {
                cachedIndex = idx;
            }
            return idx;
        } catch (Exception e) {
            return null;
        }
    }

    /** 缓存的索引多久了（毫秒）；没有返回 Long.MAX_VALUE。 */
    public long cachedIndexAgeMs() {
        File gz = new File(work, "Packages-" + arch + ".gz");
        return gz.isFile() ? Math.max(0, System.currentTimeMillis() - gz.lastModified()) : Long.MAX_VALUE;
    }

    private static byte[] gunzip(byte[] gz) throws InstallException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return Installer.readAll(in, 64 << 20);
        } catch (IOException e) {
            throw new InstallException(Kind.CORRUPT, "索引解压失败", e);
        }
    }

    static Index parseIndex(byte[] text, String repoUrl, String date) {
        Map<String, Pkg> out = new HashMap<>();
        Map<String, String> cur = new HashMap<>();
        String s = new String(text, StandardCharsets.UTF_8);
        int i = 0, n = s.length();
        while (i <= n) {
            int e = s.indexOf('\n', i);
            if (e < 0) e = n;
            String line = s.substring(i, e);
            i = e + 1;
            if (line.isEmpty()) {
                if (cur.containsKey("Package") && cur.containsKey("Filename") && cur.containsKey("SHA256")) {
                    Pkg p = new Pkg(cur);
                    out.put(p.name, p);
                }
                cur = new HashMap<>();
                if (e >= n) break;
                continue;
            }
            if (line.charAt(0) == ' ' || line.charAt(0) == '\t') continue;
            int c = line.indexOf(':');
            if (c > 0) cur.put(line.substring(0, c), line.substring(c + 1).trim());
            if (e >= n) {
                if (cur.containsKey("Package") && cur.containsKey("Filename") && cur.containsKey("SHA256")) {
                    Pkg p = new Pkg(cur);
                    out.put(p.name, p);
                }
                break;
            }
        }
        return new Index(out, repoUrl, date);
    }

    /* ---------------- 依赖 ---------------- */

    /** 按依赖顺序（依赖在前）列出要装的包；找不到的顶层包抛 NOT_FOUND，找不到的依赖跳过（记到 missing）。 */
    public static List<Pkg> resolve(Index idx, Collection<String> want, Set<String> have, List<String> missing) throws InstallException {
        LinkedHashMap<String, Pkg> out = new LinkedHashMap<>();
        Set<String> visiting = new HashSet<>();
        Set<String> wantSet = new HashSet<>(want);
        for (String w : want) {
            if (SKIP.contains(w)) continue;
            Pkg p = idx.find(w);
            if (p == null) throw new InstallException(Kind.NOT_FOUND, "找不到软件包：" + w + suggest(idx, w), null);
            add(idx, p, out, visiting, wantSet, have, missing);
        }
        return new ArrayList<>(out.values());
    }

    private static void add(Index idx, Pkg p, LinkedHashMap<String, Pkg> out, Set<String> visiting, Set<String> want, Set<String> have, List<String> missing) {
        if (out.containsKey(p.name) || !visiting.add(p.name)) return;
        for (String[] alts : p.depends) {
            String pick = null;
            for (String a : alts) if (out.containsKey(a) || want.contains(a) || have.contains(a)) pick = pick == null ? a : pick;
            if (pick == null) for (String a : alts) if (idx.find(a) != null) {
                pick = a;
                break;
            }
            if (pick == null) pick = alts[0];
            if (SKIP.contains(pick)) continue;
            Pkg d = idx.find(pick);
            if (d == null) {
                if (missing != null && !missing.contains(pick)) missing.add(pick);
                continue;
            }
            add(idx, d, out, visiting, want, have, missing);
        }
        out.put(p.name, p);
    }

    private static String suggest(Index idx, String w) {
        List<String> close = new ArrayList<>();
        String lw = w.toLowerCase(Locale.ROOT);
        for (String n : idx.pkgs.keySet()) if (n.contains(lw) || lw.contains(n) && n.length() > 2) close.add(n);
        Collections.sort(close, (a, b) -> a.length() != b.length() ? a.length() - b.length() : a.compareTo(b));
        if (close.isEmpty()) return "（可以用 pkg search 关键词 找）";
        return "（相近的：" + String.join("、", close.subList(0, Math.min(6, close.size()))) + "）";
    }

    /* ---------------- 安装 ---------------- */

    /** 装基础工具包（加上以前用 pkg 装过的包）：先装到旁边的临时目录，全部成功再整体替换，失败不影响原来的。返回用的软件源 id。 */
    public String installBase(String preferredRepoId, Progress p) throws InstallException {
        synchronized (lock) {
            Index idx = fetchIndex(preferredRepoId, p);
            Map<String, Installed> old = readStatus(prefix);
            Set<String> want = new LinkedHashSet<>(base);
            for (Installed i : old.values()) if (i.explicit) want.add(i.name);
            List<String> missing = new ArrayList<>();
            List<Pkg> plan;
            try {
                plan = resolve(idx, want, Collections.<String>emptySet(), missing);
            } catch (InstallException e) {
                // 以前装的某个包仓库里没了：只装基础包
                plan = resolve(idx, base, Collections.<String>emptySet(), missing);
            }
            File staging = new File(prefix.getParentFile(), prefix.getName() + ".new");
            Installer.deleteTree(staging);
            if (!staging.mkdirs()) throw new InstallException(Kind.IO, "建目录失败：" + staging, null);
            Map<String, Installed> st = new TreeMap<>();
            installInto(idx, plan, staging, st, new HashSet<>(want), p);
            postInstall(staging);
            try {
                writeStatus(staging, st, idx.repoUrl);
            } catch (IOException e) {
                throw new InstallException(Kind.IO, "写状态文件失败：" + e.getMessage(), e);
            }
            // 整体替换（正在运行的程序拿着旧文件的 inode，不受影响）
            File trash = new File(prefix.getParentFile(), prefix.getName() + ".old");
            Installer.deleteTree(trash);
            if (prefix.exists() && !prefix.renameTo(trash)) throw new InstallException(Kind.IO, "替换旧工具包失败", null);
            if (!staging.renameTo(prefix)) {
                trash.renameTo(prefix);
                throw new InstallException(Kind.IO, "启用新工具包失败", null);
            }
            Installer.deleteTree(trash);
            clearDebs();
            for (Repo r : repos) if (r.url.equals(idx.repoUrl)) return r.id;
            return null;
        }
    }

    /** pkg install：装到现有前缀里（逐个文件原子替换）。返回实际装了的包。 */
    public List<Pkg> install(Collection<String> names, boolean explicit, Index idx, Progress p, PrintStream log) throws InstallException {
        synchronized (lock) {
            if (!installed()) throw new InstallException(Kind.IO, "开发工具包还没装好", null);
            Map<String, Installed> st = readStatus(prefix);
            List<String> missing = new ArrayList<>();
            List<Pkg> plan = resolve(idx, names, st.keySet(), missing);
            List<Pkg> todo = new ArrayList<>(), repair = new ArrayList<>();
            for (Pkg k : plan) {
                Installed i = st.get(k.name);
                if (i == null || !i.version.equals(k.version)) todo.add(k);
                else if (!i.alt) repair.add(k); // 旧版装的、版本没变：不重装，只补 alternatives 链接
            }
            // 已经装了的显式包：只标记 explicit
            boolean changed = false;
            for (String n : names) {
                Installed i = st.get(n);
                if (i != null && !i.explicit && explicit) {
                    st.put(n, new Installed(n, i.version, true, i.files, i.alt));
                    changed = true;
                }
            }
            if (log != null && !missing.isEmpty()) log.println("提示：仓库里没有这些依赖，已跳过：" + String.join(" ", missing));
            if (todo.isEmpty() && repair.isEmpty()) {
                if (changed) saveStatus(st, idx);
                return todo;
            }
            long dl = 0, inst = 0;
            for (Pkg k : todo) {
                dl += k.size;
                inst += k.installedKb;
            }
            if (log != null && !todo.isEmpty()) {
                StringBuilder sb = new StringBuilder("将安装 " + todo.size() + " 个包（下载 " + mb(dl) + "，占用约 " + mb(inst * 1024) + "）：");
                for (Pkg k : todo) sb.append(' ').append(k.name);
                log.println(sb);
            }
            Set<String> explicitSet = new HashSet<>();
            if (explicit) explicitSet.addAll(names);
            if (!todo.isEmpty()) {
                installInto(idx, todo, prefix, st, explicitSet, p);
                postInstall(prefix);
            }
            if (!repair.isEmpty()) {
                int n = repairAlternatives(idx, repair, st, p);
                if (log != null && n > 0) log.println("补建了 " + n + " 个命令链接（java、javac、keytool、vi 之类）");
            }
            saveStatus(st, idx);
            clearDebs();
            return todo;
        }
    }

    private void saveStatus(Map<String, Installed> st, Index idx) throws InstallException {
        try {
            writeStatus(prefix, st, idx.repoUrl);
        } catch (IOException e) {
            throw new InstallException(Kind.IO, "写状态文件失败：" + e.getMessage(), e);
        }
    }

    /** pkg remove：删掉包的文件（基础包不让删）。 */
    public List<String> remove(Collection<String> names, PrintStream log) throws InstallException {
        synchronized (lock) {
            Map<String, Installed> st = readStatus(prefix);
            List<String> done = new ArrayList<>();
            for (String n : names) {
                Installed i = st.get(n);
                if (i == null) {
                    if (log != null) log.println(n + "：没有安装");
                    continue;
                }
                if (base.contains(n)) {
                    if (log != null) log.println(n + "：属于基础工具包，不能卸载");
                    continue;
                }
                // 只删不被别的包共用的文件
                Set<String> shared = new HashSet<>();
                for (Installed o : st.values()) if (!o.name.equals(n)) shared.addAll(o.files);
                for (String f : i.files) if (!shared.contains(f)) new File(prefix, f).delete();
                st.remove(n);
                done.add(n);
            }
            if (!done.isEmpty()) {
                try {
                    writeStatus(prefix, st, "");
                } catch (IOException e) {
                    throw new InstallException(Kind.IO, "写状态文件失败：" + e.getMessage(), e);
                }
            }
            return done;
        }
    }

    private void installInto(Index idx, List<Pkg> plan, File dest, Map<String, Installed> st, Set<String> explicit, Progress p) throws InstallException {
        File debs = new File(work, "debs");
        debs.mkdirs();
        long total = 0, done = 0;
        for (Pkg k : plan) total += k.size;
        List<String> bases = mirrors(idx);
        int i = 0;
        for (Pkg k : plan) {
            i++;
            if (p.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
            List<String> urls = new ArrayList<>();
            for (String b : bases) urls.add(b + "/" + k.filename);
            File deb = new File(debs, k.sha256 + ".deb");
            final long base = done, all = total;
            final String label = "下载 " + k.name + "（" + i + "/" + plan.size() + "）";
            download(urls, deb, k.sha256, k.size, label, new Progress() {
                @Override
                public void step(String text, long d, long t) {
                    p.step(text, base + d, all);
                }

                @Override
                public boolean cancelled() {
                    return p.cancelled();
                }
            });
            done += k.size;
            p.step("解压 " + k.name + "（" + i + "/" + plan.size() + "）", done, total);
            List<String> files = new ArrayList<>();
            try {
                extractDeb(deb, dest, prefix.getAbsolutePath(), files, p);
                // 我们不执行包的安装脚本：postinst 里 update-alternatives 建的链接（java、javac、keytool、editor、vi……）自己建
                String postinst = null;
                try (InputStream raw = new BufferedInputStream(new FileInputStream(deb), 64 * 1024)) {
                    postinst = Deb.controlFile(raw, "postinst");
                } catch (IOException ignored) {
                    // 没有 control.tar 或读不了：没有 alternatives
                }
                // 升级时这个包以前建的链接可以换成新的；新版本自己带的文件不动
                Installed before = st.get(k.name);
                Set<String> mine = new HashSet<>();
                if (before != null && dest.equals(prefix)) mine.addAll(before.files);
                mine.removeAll(files);
                for (String l : applyAlternatives(postinst, dest, mine, null)) if (!files.contains(l)) files.add(l);
            } catch (InstallException e) {
                throw e;
            } catch (IOException e) {
                if (Installer.noSpace(e)) throw new InstallException(Kind.NO_SPACE, "存储空间不够", e);
                throw new InstallException(Kind.CORRUPT, k.name + " 解压失败：" + e.getMessage(), e);
            }
            Installed old = st.get(k.name);
            if (old != null && dest.equals(prefix)) {
                // 升级：删掉新版本里没有的旧文件
                Set<String> keep = new HashSet<>(files);
                for (Installed o : st.values()) if (!o.name.equals(k.name)) keep.addAll(o.files);
                for (String f : old.files) if (!keep.contains(f)) new File(dest, f).delete();
            }
            boolean ex = explicit.contains(k.name) || (old != null && old.explicit);
            st.put(k.name, new Installed(k.name, k.version, ex, files, true));
        }
    }

    private void clearDebs() {
        Installer.deleteTree(new File(work, "debs"));
    }

    /** 下载 .deb 用的地址：索引所在的源在前，其余源备用。 */
    private List<String> mirrors(Index idx) {
        List<String> bases = new ArrayList<>();
        bases.add(idx.repoUrl);
        for (Repo r : repos) if (!bases.contains(r.url)) bases.add(r.url);
        return bases;
    }

    /**
     * v0.3.1 以前装的包没处理 postinst 里的 update-alternatives（所以装了 OpenJDK 却没有 java 命令）。
     * 版本没变的包不用重新下载：只读 .deb 开头的 control.tar（Range 请求，几 KB），按 postinst 补建链接，
     * 并且只链接到这个包自己装过的文件。读不到的包下次安装时再试。返回新建的链接数。
     */
    private int repairAlternatives(Index idx, List<Pkg> pkgs, Map<String, Installed> st, Progress p) throws InstallException {
        final List<String> bases = mirrors(idx);
        // 每个包一次小请求（主要是 TLS 握手的时间）：几个一起取
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(Math.min(6, pkgs.size()), r -> {
            Thread t = new Thread(r, "pkg-postinst");
            t.setDaemon(true);
            return t;
        });
        int made = 0;
        try {
            List<java.util.concurrent.Future<String[]>> fs = new ArrayList<>();
            for (final Pkg k : pkgs) fs.add(ex.submit(() -> new String[]{fetchPostinst(bases, k.filename)}));
            for (int i = 0; i < pkgs.size(); i++) {
                Pkg k = pkgs.get(i);
                if (p.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
                p.step("检查命令链接：" + k.name + "（" + (i + 1) + "/" + pkgs.size() + "）", i + 1, pkgs.size());
                String postinst;
                try {
                    postinst = fs.get(i).get()[0];
                } catch (java.util.concurrent.ExecutionException e) {
                    continue; // 网络问题：保持未处理，下次再试
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InstallException(Kind.CANCELLED, "已取消", e);
                }
                Installed old = st.get(k.name);
                if (old == null) continue;
                Set<String> owned = new HashSet<>(old.files);
                List<String> files = new ArrayList<>(old.files);
                for (String l : applyAlternatives(postinst, prefix, owned, owned)) {
                    if (!files.contains(l)) {
                        files.add(l);
                        made++;
                    }
                }
                st.put(k.name, new Installed(k.name, old.version, old.explicit, files, true));
            }
        } finally {
            ex.shutdownNow();
        }
        return made;
    }

    /** 有没有 v0.3.1 以前装的、还没处理 alternatives 的包（只读状态文件，不联网）。 */
    public boolean needsAlternativesRepair() {
        if (!installed()) return false;
        for (Installed i : readStatus(prefix).values()) if (!i.alt) return true;
        return false;
    }

    /**
     * 给旧版装的包补 alternatives 链接（例如 v0.3 装的 OpenJDK 没有 java、javac，vim 没有 vim 命令）。
     * 只补版本和索引一致的包（这样 .deb 文件名对得上）；版本不同的包下次升级时自然会处理。返回新建的链接数。
     */
    public int repairOldInstalls(String preferredRepoId, Progress p) throws InstallException {
        synchronized (lock) {
            Map<String, Installed> st = readStatus(prefix);
            List<String> want = new ArrayList<>();
            for (Installed i : st.values()) if (!i.alt) want.add(i.name);
            if (want.isEmpty()) return 0;
            Index idx = freshIndex(preferredRepoId, p, 24L * 3600 * 1000);
            List<Pkg> todo = new ArrayList<>();
            for (String n : want) {
                Pkg k = idx.find(n);
                if (k != null && k.version.equals(st.get(n).version)) todo.add(k);
            }
            if (todo.isEmpty()) return 0;
            int n = repairAlternatives(idx, todo, st, p);
            saveStatus(st, idx);
            return n;
        }
    }

    /** 只取 .deb 开头（ar 里 control.tar 排在 data.tar 前面）读出 postinst；包里没有 postinst 返回 null。所有源都失败抛异常。 */
    String fetchPostinst(List<String> bases, String filename) throws IOException {
        try {
            return fetchPostinst(bases, filename, 32 << 10);
        } catch (EOFException e) {
            return fetchPostinst(bases, filename, CONTROL_HEAD); // control.tar 比较大：多取一些
        }
    }

    private String fetchPostinst(List<String> bases, String filename, int head) throws IOException {
        IOException last = null;
        for (String b : bases) {
            HttpURLConnection c = null;
            try {
                c = open(b + "/" + filename, 0);
                c.setRequestProperty("Range", "bytes=0-" + (head - 1));
                int code = c.getResponseCode();
                if (code != 200 && code != 206) throw new IOException("HTTP " + code);
                try (InputStream in = new Bounded(new BufferedInputStream(c.getInputStream(), 16 * 1024), head)) {
                    return Deb.controlFile(in, "postinst");
                }
            } catch (EOFException e) {
                throw e;           // 取的太少，不是源的问题
            } catch (IOException e) {
                last = e;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw last != null ? last : new IOException("没有可用的软件源");
    }

    /** 读 control.tar 最多取 .deb 开头这么多字节。 */
    static final int CONTROL_HEAD = 256 << 10;

    static List<String> applyAlternatives(String postinst, File dest) {
        return applyAlternatives(postinst, dest, Collections.<String>emptySet(), null);
    }

    /**
     * 按 postinst 里的 update-alternatives（Termux 自动生成的格式：--install "链接" "名字" "目标" 优先级，--slave "链接" "名字" "目标"）
     * 建相对路径的符号链接。返回这个包应当拥有的链接（相对前缀）：新建的，以及本来就指向同一目标的（升级、重复安装时要算进文件清单，
     * 不然升级清理会把它删掉）。
     * 不动的：目标不存在（例如被我们省略的手册页）、真文件、别的包的有效链接。mine 里的（这个包以前建的）链接可以替换；
     * targets 不为 null 时只链接到其中的文件。
     */
    static List<String> applyAlternatives(String postinst, File dest, Set<String> mine, Set<String> targets) {
        List<String> made = new ArrayList<>();
        if (postinst == null || !postinst.contains("update-alternatives")) return made;
        String joined = postinst.replace("\\\n", " ");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("--(?:install|slave)\\s+\"?([^\"\\s]+)\"?\\s+\"?([^\"\\s]+)\"?\\s+\"?([^\"\\s]+)\"?").matcher(joined);
        String pre = TERMUX_PREFIX + "/";
        while (m.find()) {
            String link = m.group(1), target = m.group(3);
            if (!link.startsWith(pre) || !target.startsWith(pre)) continue;
            String lr = link.substring(pre.length()), tr = target.substring(pre.length());
            if (!safeRel(lr) || !safeRel(tr) || dropped(lr) || made.contains(lr)) continue;
            if (targets != null && !targets.contains(tr)) continue;
            File lf = new File(dest, lr), tf = new File(dest, tr);
            if (!tf.exists()) continue;
            File dir = lf.getParentFile();
            try {
                java.nio.file.Path rel = dir.toPath().relativize(tf.toPath());
                if (isSymlink(lf)) {
                    if (Files.readSymbolicLink(lf.toPath()).equals(rel)) {
                        made.add(lr);                     // 已经是这个链接
                        continue;
                    }
                    if (lf.exists() && !mine.contains(lr)) continue; // 别的包的有效链接
                    Files.delete(lf.toPath());             // 断掉的，或者是这个包旧版本的
                } else if (lf.exists()) {
                    continue;                              // 真文件
                }
                if (!dir.isDirectory() && !dir.mkdirs()) continue;
                Files.createSymbolicLink(lf.toPath(), rel);
                made.add(lr);
            } catch (IOException | UnsupportedOperationException ignored) {
                // 建不了就算了：程序本身还在原位置
            }
        }
        return made;
    }

    /** 装完后的整理：bin/sh、tmp、给 python -m venv 用的 pip wheel。 */
    static void postInstall(File dest) {
        try {
            ensurePipWheel(dest);
        } catch (IOException ignored) {
            // 没有也只是 venv 要加 --without-pip
        }
        File sh = new File(dest, "bin/sh");
        if (!sh.exists() && !isSymlink(sh)) {
            try {
                Files.createSymbolicLink(sh.toPath(), new File(new File(dest, "bin/dash").isFile() ? "dash" : "/system/bin/sh").toPath());
            } catch (IOException | UnsupportedOperationException ignored) {
                // 没有 sh 的话 termux-exec 会退回系统 sh
            }
        }
        new File(dest, "tmp").mkdirs();
    }

    /**
     * Termux 的 python 去掉了 ensurepip 自带的 pip wheel（pip 单独成包），所以 python -m venv 会失败。
     * 用已安装的 pip 现做一个 wheel 放进 ensurepip/_bundled（wheel 就是 zip：包目录 + dist-info）。
     */
    static void ensurePipWheel(File dest) throws IOException {
        File[] pys = new File(dest, "lib").listFiles((d, n) -> n.matches("python3\\.\\d+"));
        if (pys == null) return;
        for (File py : pys) {
            File ens = new File(py, "ensurepip"), site = new File(py, "site-packages");
            File pip = new File(site, "pip");
            if (!ens.isDirectory() || !pip.isDirectory()) continue;
            File[] infos = site.listFiles((d, n) -> n.startsWith("pip-") && n.endsWith(".dist-info"));
            if (infos == null || infos.length == 0) continue;
            String ver = infos[0].getName().substring(4, infos[0].getName().length() - ".dist-info".length());
            // ensurepip 只认它自己记的版本号（_PIP_VERSION）：改成实际装的 pip 版本，pip 会核对 wheel 文件名和元数据是否一致
            File init = new File(ens, "__init__.py");
            if (init.isFile()) {
                String src = new String(Files.readAllBytes(init.toPath()), StandardCharsets.UTF_8);
                String fixed = src.replaceFirst("(?m)^_PIP_VERSION = \"[^\"]*\"", "_PIP_VERSION = \"" + ver.replace("$", "") + "\"");
                if (!fixed.equals(src)) {
                    File t = new File(init.getPath() + ".tmp");
                    Files.write(t.toPath(), fixed.getBytes(StandardCharsets.UTF_8));
                    if (!t.renameTo(init)) throw new IOException("改名失败");
                    Installer.deleteTree(new File(ens, "__pycache__"));
                }
            }
            File bundled = new File(ens, "_bundled");
            File wheel = new File(bundled, "pip-" + ver + "-py3-none-any.whl");
            if (wheel.isFile()) continue;
            bundled.mkdirs();
            File[] old = bundled.listFiles((d, n) -> n.startsWith("pip-") && n.endsWith(".whl"));
            if (old != null) for (File o : old) o.delete();
            File tmp = new File(wheel.getPath() + ".tmp");
            try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new FileOutputStream(tmp))) {
                zipTree(z, pip, "pip/");
                zipTree(z, infos[0], infos[0].getName() + "/");
            }
            if (!tmp.renameTo(wheel)) throw new IOException("改名失败");
        }
    }

    private static void zipTree(java.util.zip.ZipOutputStream z, File dir, String prefix) throws IOException {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            if (f.getName().equals("__pycache__")) continue;
            if (Files.isSymbolicLink(f.toPath()) && !f.isFile()) continue; // 指向文件的链接（certifi 的 cacert.pem → 系统证书）按内容打包
            if (f.isDirectory()) {
                zipTree(z, f, prefix + f.getName() + "/");
            } else {
                String name = prefix + f.getName();
                z.putNextEntry(new java.util.zip.ZipEntry(name));
                if (name.equals("pip/_internal/commands/install.py")) {
                    // Termux 给系统 pip 加了“不许装 pip 自己”的保护；venv 里的 pip 不需要（ensurepip 正是要装 pip）
                    String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    z.write(src.replace("while reqs_list.count(\"pip\") != 0:", "while False and reqs_list.count(\"pip\") != 0:").getBytes(StandardCharsets.UTF_8));
                } else {
                    Files.copy(f.toPath(), z);
                }
                z.closeEntry();
            }
        }
    }

    private static boolean isSymlink(File f) {
        return Files.isSymbolicLink(f.toPath());
    }

    /* ---------------- 解 .deb ---------------- */

    /** 解一个 .deb 的 data.tar.* 到 dest（finalPrefix 是最终路径，用来改写文本文件）。files 收集写出的相对路径。 */
    static void extractDeb(File deb, File dest, String finalPrefix, List<String> files, Progress p) throws IOException {
        try (InputStream raw = new BufferedInputStream(new FileInputStream(deb), 128 * 1024)) {
            InputStream data = Deb.dataTar(raw);
            TarReader tar = new TarReader(data);
            tar.cancel = p::cancelled;
            String pre = TERMUX_PREFIX.substring(1) + "/";
            byte[] newPrefix = finalPrefix.getBytes(StandardCharsets.UTF_8);
            TarReader.Entry e;
            List<String[]> hardlinks = new ArrayList<>();
            while ((e = tar.next()) != null) {
                if (p.cancelled()) throw new InterruptedIOException("已取消");
                String name = e.name;
                if (name.startsWith("./")) name = name.substring(2);
                if (!name.startsWith(pre)) continue; // 前缀以外的文件（例如 home 里的）不要
                String rel = name.substring(pre.length());
                while (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
                if (rel.isEmpty() || !safeRel(rel) || dropped(rel)) continue;
                File out = new File(dest, rel);
                if (e.isDir()) {
                    if (!out.isDirectory() && !out.mkdirs()) throw new IOException("建目录失败：" + rel);
                    continue;
                }
                File parent = out.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("建目录失败：" + parent);
                if (e.isSymlink()) {
                    String target = e.link;
                    if (target.startsWith(TERMUX_PREFIX + "/")) target = relativeLink(rel, target.substring(TERMUX_PREFIX.length() + 1));
                    else if (target.equals(TERMUX_PREFIX)) target = relativeLink(rel, "");
                    replaceWithSymlink(out, target);
                    files.add(rel);
                    continue;
                }
                if (e.isHardlink()) {
                    String t = e.link.startsWith("./") ? e.link.substring(2) : e.link;
                    if (t.startsWith("/")) t = t.substring(1);
                    if (t.startsWith(pre)) hardlinks.add(new String[]{rel, t.substring(pre.length())});
                    continue;
                }
                if (!e.isFile()) continue; // 设备文件、FIFO 之类不要
                writeFile(tar, e, out, rel, newPrefix);
                files.add(rel);
            }
            for (String[] h : hardlinks) {
                File src = new File(dest, h[1]), out = new File(dest, h[0]);
                if (!src.isFile()) continue;
                File tmp = new File(out.getPath() + ".pkgtmp");
                tmp.delete();
                Files.copy(src.toPath(), tmp.toPath());
                if (src.canExecute()) tmp.setExecutable(true, true);
                if (!tmp.renameTo(out)) throw new IOException("改名失败：" + h[0]);
                files.add(h[0]);
            }
        }
    }

    static boolean safeRel(String rel) {
        if (rel.startsWith("/")) return false;
        for (String part : rel.split("/")) if (part.equals("..") || part.equals(".") || part.isEmpty()) return false;
        return true;
    }

    private static boolean dropped(String rel) {
        for (String d : DROP) if (rel.startsWith(d)) return true;
        return false;
    }

    /** 从链接所在目录到 targetRel（都相对前缀）的相对路径。 */
    static String relativeLink(String linkRel, String targetRel) {
        int depth = 0;
        for (int i = 0; i < linkRel.length(); i++) if (linkRel.charAt(i) == '/') depth++;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("../");
        if (targetRel.isEmpty()) return depth == 0 ? "." : sb.substring(0, sb.length() - 1);
        return sb.append(targetRel).toString();
    }

    private static void replaceWithSymlink(File out, String target) throws IOException {
        File tmp = new File(out.getPath() + ".pkgtmp");
        Files.deleteIfExists(tmp.toPath());
        Files.createSymbolicLink(tmp.toPath(), new File(target).toPath());
        if (out.isDirectory() && !Files.isSymbolicLink(out.toPath())) Installer.deleteTree(out);
        if (!tmp.renameTo(out)) throw new IOException("改名失败：" + out);
    }

    private static final int TEXT_LIMIT = 8 << 20;

    private static void writeFile(TarReader tar, TarReader.Entry e, File out, String rel, byte[] newPrefix) throws IOException {
        File tmp = new File(out.getPath() + ".pkgtmp");
        byte[] head = new byte[(int) Math.min(8192, e.size)];
        int hn = 0;
        while (hn < head.length) {
            int r = tar.read(head, hn, head.length - hn);
            if (r < 0) throw new EOFException("数据被截断：" + rel);
            hn += r;
        }
        boolean elf = Elf.isElf(head, hn);
        boolean text = !elf && e.size <= TEXT_LIMIT && !hasNul(head, hn);
        if (text) {
            ByteArrayOutputStream b = new ByteArrayOutputStream((int) e.size);
            b.write(head, 0, hn);
            copy(tar, b, e.size - hn);
            byte[] data = b.toByteArray();
            if (indexOf(data, OLD, 0) >= 0) data = replaceAll(data, OLD, newPrefix);
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(data);
            }
        } else {
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(head, 0, hn);
                copy(tar, o, e.size - hn);
            }
            if (elf) patchElf(tmp, rel);
        }
        if ((e.mode & 0111) != 0 && !tmp.setExecutable(true, true)) throw new IOException("设置可执行权限失败：" + rel);
        if (out.isDirectory() && !Files.isSymbolicLink(out.toPath())) Installer.deleteTree(out);
        if (!tmp.renameTo(out)) throw new IOException("改名失败：" + rel);
    }

    /** RUNPATH → $ORIGIN 相对路径；“旧前缀/bin/sh” → “////…/system/bin/sh”（等长）。 */
    static void patchElf(File f, String rel) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            Elf.patchRunpath(raf, Elf.originPath(rel));
            // 找 “旧前缀/bin/sh” 且后面不是文件名字符的位置
            long len = raf.length();
            byte[] buf = new byte[1 << 20];
            int overlap = OLD_SH.length + 1;
            List<Long> hits = new ArrayList<>();
            long pos = 0;
            while (pos < len) {
                raf.seek(pos);
                int n = raf.read(buf, 0, (int) Math.min(buf.length, len - pos));
                if (n <= 0) break;
                int from = 0;
                while (true) {
                    int k = indexOf(buf, n, OLD_SH, from);
                    if (k < 0) break;
                    int after = k + OLD_SH.length;
                    if (after >= n && pos + n < len) break; // 后一个字节在下一块里：下一轮从这里重新找
                    if (after >= n || !nameChar(buf[after])) hits.add(pos + k);
                    from = k + 1;
                }
                if (pos + n >= len) break;
                pos += Math.max(1, n - overlap);
            }
            if (hits.isEmpty()) return;
            byte[] rep = new byte[OLD_SH.length];
            byte[] sys = "/system/bin/sh".getBytes(StandardCharsets.US_ASCII);
            Arrays.fill(rep, (byte) '/');
            System.arraycopy(sys, 0, rep, rep.length - sys.length, sys.length);
            long lastHit = -1;
            for (long h : hits) {
                if (h == lastHit) continue;
                lastHit = h;
                raf.seek(h);
                raf.write(rep);
            }
        }
    }

    private static boolean nameChar(byte b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') || b == '_' || b == '.' || b == '+' || b == '-';
    }

    private static boolean hasNul(byte[] b, int n) {
        for (int i = 0; i < n; i++) if (b[i] == 0) return true;
        return false;
    }

    private static void copy(TarReader tar, OutputStream o, long left) throws IOException {
        byte[] buf = new byte[256 * 1024];
        while (left > 0) {
            int n = tar.read(buf, 0, (int) Math.min(buf.length, left));
            if (n < 0) throw new EOFException("数据被截断");
            o.write(buf, 0, n);
            left -= n;
        }
    }

    static int indexOf(byte[] hay, byte[] needle, int from) {
        return indexOf(hay, hay.length, needle, from);
    }

    static int indexOf(byte[] hay, int len, byte[] needle, int from) {
        byte first = needle[0];
        int max = len - needle.length;
        outer:
        for (int i = from; i <= max; i++) {
            if (hay[i] != first) continue;
            for (int j = 1; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    static byte[] replaceAll(byte[] data, byte[] from, byte[] to) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(data.length + 256);
        int i = 0;
        while (true) {
            int k = indexOf(data, from, i);
            if (k < 0) break;
            o.write(data, i, k - i);
            o.write(to, 0, to.length);
            i = k + from.length;
        }
        o.write(data, i, data.length - i);
        return o.toByteArray();
    }

    private static void writeAtomic(File f, byte[] data) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(data);
        }
        if (!tmp.renameTo(f)) throw new IOException("改名失败：" + f);
    }

    static long parseLong(String s) {
        if (s == null) return 0;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1e6);
    }

    /* ---------------- 运行环境 ---------------- */

    /**
     * Agent 进程要加的环境变量（工具包装好了才调用）。appDataDir 是 App 的数据目录（/data/user/0/包名），
     * home 是 Agent 的 HOME，caFile 是导出的系统证书。
     */
    public Map<String, String> env(String appDataDir, File home, File caFile) {
        Map<String, String> e = new LinkedHashMap<>();
        String p = prefix.getAbsolutePath();
        e.put("PREFIX", p);
        e.put("TERMUX__PREFIX", p);
        e.put("TERMUX__ROOTFS", prefix.getParentFile().getAbsolutePath());
        e.put("TERMUX_APP__DATA_DIR", appDataDir);
        File preload = new File(prefix, "lib/libtermux-exec-direct-ld-preload.so");
        // 预加载的库不存在时安卓的链接器会让所有程序都起不来，所以只在文件在的时候设
        if (preload.isFile()) {
            e.put("LD_PRELOAD", preload.getAbsolutePath());
            e.put("TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE", "disable");
        }
        e.put("GIT_EXEC_PATH", p + "/libexec/git-core");
        e.put("GIT_TEMPLATE_DIR", p + "/share/git-core/templates");
        e.put("GIT_CONFIG_NOSYSTEM", "1");
        e.put("GIT_PAGER", "cat");
        e.put("GIT_EDITOR", ":");
        e.put("PAGER", "cat");
        if (caFile != null) {
            e.put("GIT_SSL_CAINFO", caFile.getAbsolutePath());
            e.put("CURL_CA_BUNDLE", caFile.getAbsolutePath());
            e.put("NODE_EXTRA_CA_CERTS", caFile.getAbsolutePath());
            e.put("REQUESTS_CA_BUNDLE", caFile.getAbsolutePath());
        }
        e.put("MAGIC", p + "/share/misc/magic.mgc");
        e.put("TERMINFO", p + "/share/terminfo");
        e.put("npm_config_prefix", new File(home, ".npm-global").getAbsolutePath());
        e.put("npm_config_update_notifier", "false");
        e.put("npm_config_fund", "false");
        e.put("PIP_DISABLE_PIP_VERSION_CHECK", "1");
        e.put("PYTHONUNBUFFERED", "1");
        File jdk = javaHome(prefix);
        if (jdk != null) e.put("JAVA_HOME", jdk.getAbsolutePath()); // Gradle、apktool、jadx 这些要 JAVA_HOME
        return e;
    }

    /** 装了 OpenJDK 时的 JAVA_HOME（优先 21，再 17，再随便一个）。 */
    static File javaHome(File prefix) {
        File jvm = new File(prefix, "lib/jvm");
        for (String n : new String[]{"java-21-openjdk", "java-17-openjdk"}) {
            File d = new File(jvm, n);
            if (new File(d, "bin/java").exists()) return d;
        }
        File[] all = jvm.listFiles();
        if (all != null) for (File d : all) if (new File(d, "bin/java").exists()) return d;
        return null;
    }

    /** PATH 里要放在系统目录前面的目录（按顺序）。 */
    public List<String> pathDirs(File home) {
        return Arrays.asList(new File(home, ".npm-global/bin").getAbsolutePath(), new File(home, ".local/bin").getAbsolutePath(),
                new File(prefix, "bin").getAbsolutePath());
    }

    /* ---------------- pkg 命令 ---------------- */

    /**
     * PATH 里的 pkg / apt / apt-get：把参数交给 App（经 LocalProxy 的本机通道，需要代理口令），App 下载、校验、安装，
     * 输出原样流回来，最后一行是退出码。用工具包里的 curl；工具包没装好时给出提示。
     */
    public static final String PKG_SCRIPT = "#!/system/bin/sh\n"
            + "# 口袋工坊的软件包命令：由 App 从 Termux 官方仓库（签名校验）安装到 $PREFIX。apt / apt-get 是同一个命令。\n"
            + "proxy=\"${http_proxy:-$HTTP_PROXY}\"\n"
            + "curl_bin=\"${PREFIX:-/nonexistent}/bin/curl\"\n"
            + "if [ -z \"$proxy\" ] || [ ! -x \"$curl_bin\" ]; then\n"
            + "  echo \"pkg：开发工具包还没装好，暂时不能装软件包。请让用户在 口袋工坊 → 设置 → 开发工具 里安装（或等自动安装完成）。\" >&2\n"
            + "  exit 1\n"
            + "fi\n"
            + "case \"$(basename \"$0\")\" in\n"
            + "  apt|apt-get)\n"
            + "    case \"$1\" in\n"
            + "      list) shift; if [ \"$1\" = --installed ]; then set -- list-installed; else set -- search \"$@\"; fi;;\n"
            + "      full-upgrade|dist-upgrade) shift; set -- upgrade \"$@\";;\n"
            + "    esac;;\n"
            + "esac\n"
            + "q=\"\"\n"
            + "for a in \"$@\"; do\n"
            + "  case \"$a\" in\n"
            + "    *[!A-Za-z0-9+._-]*) echo \"pkg：参数里有不支持的字符：$a\" >&2; exit 2;;\n"
            + "  esac\n"
            + "  q=\"$q&a=$a\"\n"
            + "done\n"
            + "exec 3>&1\n"
            + "code=$(\"$curl_bin\" -sS -N --noproxy '' --max-time 7200 -x \"$proxy\" \"http://pocket-codex.invalid/pkg?v=1$q\" 2>&1 | {\n"
            + "  rc=1\n"
            + "  while IFS= read -r l; do\n"
            + "    case \"$l\" in\n"
            + "      \"@@exit \"*) rc=${l#@@exit };;\n"
            + "      *) printf '%s\\n' \"$l\" >&3;;\n"
            + "    esac\n"
            + "  done\n"
            + "  echo \"$rc\"\n"
            + "})\n"
            + "exec 3>&-\n"
            + "exit \"${code:-1}\"\n";

    /** 处理 pkg/apt 命令（经 LocalProxy 的本机通道调用）。返回退出码。 */
    public int command(List<String> argv, String preferredRepoId, PrintStream out, final java.util.function.BooleanSupplier cancelled) {
        List<String> args = new ArrayList<>();
        for (String a : argv) if (!a.startsWith("-")) args.add(a);
        String cmd = args.isEmpty() ? "help" : args.remove(0);
        Progress p = new Progress() {
            String lastText;
            long lastTime;

            @Override
            public void step(String text, long done, long total) {
                long now = System.currentTimeMillis();
                if (text.equals(lastText) && now - lastTime < 2000) return;
                lastText = text;
                lastTime = now;
                out.println(total > 0 ? text + " " + mb(done) + "/" + mb(total) : text);
            }

            @Override
            public boolean cancelled() {
                return cancelled.getAsBoolean() || out.checkError();
            }
        };
        try {
            switch (cmd) {
                case "install":
                case "add":
                case "i": {
                    if (args.isEmpty()) {
                        out.println("用法：pkg install 包名…");
                        return 2;
                    }
                    for (String a : args) if (!a.matches("[a-z0-9][a-z0-9+._-]*")) {
                        out.println("包名不合法：" + a);
                        return 2;
                    }
                    Index idx = freshIndex(preferredRepoId, p, 6L * 3600_000);
                    List<Pkg> got = install(args, true, idx, p, out);
                    if (got.isEmpty()) out.println("都已经装好了：" + String.join(" ", args));
                    else out.println("完成：装好了 " + got.size() + " 个包。");
                    return 0;
                }
                case "upgrade": {
                    Index idx = freshIndex(preferredRepoId, p, 0);
                    Map<String, Installed> st = status();
                    List<String> names = new ArrayList<>();
                    for (Installed i : st.values()) {
                        Pkg k = idx.pkgs.get(i.name);
                        if (k != null && !k.version.equals(i.version)) names.add(i.name);
                    }
                    if (names.isEmpty()) {
                        out.println("都是最新的。");
                        return 0;
                    }
                    install(names, false, idx, p, out);
                    out.println("完成：升级了 " + names.size() + " 个包。");
                    return 0;
                }
                case "update": {
                    Index idx = fetchIndex(preferredRepoId, p);
                    out.println("索引已更新（" + idx.pkgs.size() + " 个包，" + idx.date + "）。");
                    return 0;
                }
                case "remove":
                case "uninstall":
                case "rm":
                case "purge":
                case "autoremove": {
                    if (args.isEmpty()) {
                        out.println("用法：pkg uninstall 包名…");
                        return 2;
                    }
                    List<String> d = remove(args, out);
                    if (!d.isEmpty()) out.println("已卸载：" + String.join(" ", d));
                    return d.size() == args.size() ? 0 : 1;
                }
                case "search": {
                    if (args.isEmpty()) {
                        out.println("用法：pkg search 关键词");
                        return 2;
                    }
                    Index idx = freshIndex(preferredRepoId, p, 24L * 3600_000);
                    String q = String.join(" ", args).toLowerCase(Locale.ROOT);
                    Map<String, Installed> st = status();
                    List<Pkg> hits = new ArrayList<>();
                    for (Pkg k : idx.pkgs.values()) {
                        if (k.name.contains(q) || k.description.toLowerCase(Locale.ROOT).contains(q)) hits.add(k);
                    }
                    Collections.sort(hits, (a, b) -> {
                        int sa = a.name.equals(q) ? 0 : a.name.startsWith(q) ? 1 : a.name.contains(q) ? 2 : 3;
                        int sb = b.name.equals(q) ? 0 : b.name.startsWith(q) ? 1 : b.name.contains(q) ? 2 : 3;
                        return sa != sb ? sa - sb : a.name.compareTo(b.name);
                    });
                    for (Pkg k : hits.subList(0, Math.min(60, hits.size()))) {
                        out.println(k.name + " " + k.version + (st.containsKey(k.name) ? " [已安装]" : "") + " — " + k.description);
                    }
                    if (hits.size() > 60) out.println("……共 " + hits.size() + " 个，换个更具体的关键词看看");
                    if (hits.isEmpty()) out.println("没找到。");
                    return 0;
                }
                case "show":
                case "info": {
                    Index idx = freshIndex(preferredRepoId, p, 24L * 3600_000);
                    int rc = 0;
                    for (String a : args) {
                        Pkg k = idx.find(a);
                        if (k == null) {
                            out.println(a + "：找不到");
                            rc = 1;
                            continue;
                        }
                        Installed i = status().get(k.name);
                        out.println("包名：" + k.name + "\n版本：" + k.version + (i != null ? "（已安装 " + i.version + "）" : "") + "\n下载：" + mb(k.size)
                                + "  占用：" + mb(k.installedKb * 1024) + "\n说明：" + k.description);
                        StringBuilder d = new StringBuilder();
                        for (String[] alt : k.depends) d.append(d.length() > 0 ? ", " : "").append(String.join(" | ", alt));
                        if (d.length() > 0) out.println("依赖：" + d);
                        out.println();
                    }
                    return rc;
                }
                case "list-installed":
                case "list":
                case "list-all": {
                    for (Installed i : status().values()) out.println(i.name + " " + i.version + (i.explicit && !base.contains(i.name) ? " [手动安装]" : ""));
                    return 0;
                }
                case "files":
                case "listfiles": {
                    int rc = 0;
                    for (String a : args) {
                        Installed i = status().get(a);
                        if (i == null) {
                            out.println(a + "：没有安装");
                            rc = 1;
                            continue;
                        }
                        for (String f : i.files) out.println(new File(prefix, f).getPath());
                    }
                    return rc;
                }
                default:
                    out.println("pkg：口袋工坊的软件包命令（Termux 官方仓库，签名校验）。\n"
                            + "  pkg install 包名…     安装（自动装依赖）\n"
                            + "  pkg uninstall 包名…   卸载（基础工具包里的不能卸）\n"
                            + "  pkg search 关键词     搜索\n"
                            + "  pkg show 包名         详情\n"
                            + "  pkg list-installed    已安装的包\n"
                            + "  pkg files 包名        包里的文件\n"
                            + "  pkg update / upgrade  刷新索引 / 升级已安装的包\n"
                            + "装到 " + prefix + "。没有 root，不能装系统级的东西；apt / apt-get 是同一个命令。");
                    return cmd.equals("help") || cmd.equals("--help") ? 0 : 2;
            }
        } catch (InstallException e) {
            out.println("失败：" + e.getMessage());
            return e.kind == Kind.NOT_FOUND ? 3 : 1;
        }
    }

    /** 缓存的索引不太旧就用缓存，否则联网刷新；联网失败但有缓存就退回缓存。 */
    private Index freshIndex(String preferredRepoId, Progress p, long maxAgeMs) throws InstallException {
        Index c = cachedIndex();
        if (c != null && cachedIndexAgeMs() <= maxAgeMs) return c;
        try {
            return fetchIndex(preferredRepoId, p);
        } catch (InstallException e) {
            if (c != null && e.kind != Kind.CANCELLED) return c;
            throw e;
        }
    }

    /* ---------------- .deb ---------------- */

    /** .deb 是 ar 归档：找到 data.tar.xz / .gz / 未压缩的 data.tar，返回它解压后的流。 */
    static final class Deb {
        static InputStream dataTar(InputStream in) throws IOException {
            byte[] magic = new byte[8];
            readFully(in, magic);
            if (!new String(magic, StandardCharsets.US_ASCII).equals("!<arch>\n")) throw new IOException("不是 .deb 文件");
            byte[] h = new byte[60];
            while (true) {
                int first = in.read();
                if (first < 0) throw new IOException(".deb 里没有 data.tar");
                if (first == '\n') first = in.read(); // 上一个成员的对齐字节
                if (first < 0) throw new IOException(".deb 里没有 data.tar");
                h[0] = (byte) first;
                readFully(in, h, 1, 59);
                String name = new String(h, 0, 16, StandardCharsets.US_ASCII).trim();
                if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                long size = parseLong(new String(h, 48, 10, StandardCharsets.US_ASCII));
                if (size <= 0 || size > (4L << 30)) throw new IOException(".deb 成员大小不对");
                if (name.startsWith("data.tar")) {
                    InputStream body = new Bounded(in, size);
                    if (name.endsWith(".xz")) return new org.tukaani.xz.XZInputStream(body, 256 << 20);
                    if (name.endsWith(".gz")) return new GZIPInputStream(body, 64 * 1024);
                    if (name.equals("data.tar")) return body;
                    throw new IOException("不支持的压缩格式：" + name);
                }
                skipFully(in, size + (size & 1));
            }
        }

        /** control.tar.* 里的一个文件（例如 postinst）；没有就返回 null。 */
        static String controlFile(InputStream in, String wanted) throws IOException {
            byte[] magic = new byte[8];
            readFully(in, magic);
            if (!new String(magic, StandardCharsets.US_ASCII).equals("!<arch>\n")) throw new IOException("不是 .deb 文件");
            byte[] h = new byte[60];
            while (true) {
                int first = in.read();
                if (first == '\n') first = in.read();
                if (first < 0) return null;
                h[0] = (byte) first;
                readFully(in, h, 1, 59);
                String name = new String(h, 0, 16, StandardCharsets.US_ASCII).trim();
                if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                long size = parseLong(new String(h, 48, 10, StandardCharsets.US_ASCII));
                if (size < 0 || size > (4L << 30)) throw new IOException(".deb 成员大小不对");
                if (name.startsWith("control.tar")) {
                    InputStream body = new Bounded(in, size, true); // 只取了 .deb 开头时，control.tar 不完整要报出来（不能当成“没有 postinst”）
                    InputStream tarIn = name.endsWith(".xz") ? new org.tukaani.xz.XZInputStream(body, 64 << 20)
                            : name.endsWith(".gz") ? new GZIPInputStream(body, 16 * 1024) : body;
                    TarReader t = new TarReader(tarIn);
                    TarReader.Entry e;
                    while ((e = t.next()) != null) {
                        String n = e.name.startsWith("./") ? e.name.substring(2) : e.name;
                        if (!n.equals(wanted) || e.size > (1 << 20)) continue;
                        byte[] buf = new byte[(int) e.size];
                        int off = 0;
                        while (off < buf.length) {
                            int r = t.read(buf, off, buf.length - off);
                            if (r < 0) break;
                            off += r;
                        }
                        return new String(buf, 0, off, StandardCharsets.UTF_8);
                    }
                    return null;
                }
                skipFully(in, size + (size & 1));
            }
        }

        private static void readFully(InputStream in, byte[] b) throws IOException {
            readFully(in, b, 0, b.length);
        }

        private static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                int n = in.read(b, off, len);
                if (n < 0) throw new EOFException(".deb 被截断");
                off += n;
                len -= n;
            }
        }

        private static void skipFully(InputStream in, long n) throws IOException {
            byte[] buf = new byte[64 * 1024];
            while (n > 0) {
                int r = in.read(buf, 0, (int) Math.min(buf.length, n));
                if (r < 0) throw new EOFException(".deb 被截断");
                n -= r;
            }
        }
    }

    /** 只读 n 个字节的流。 */
    static final class Bounded extends FilterInputStream {
        private long left;
        /** 底层流在 n 个字节之前就结束了：抛 EOFException（而不是当成正常结束）。 */
        private final boolean strict;

        Bounded(InputStream in, long n) {
            this(in, n, false);
        }

        Bounded(InputStream in, long n, boolean strict) {
            super(in);
            left = n;
            this.strict = strict;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) return -1;
            int c = super.read();
            if (c >= 0) left--;
            else if (strict) throw new EOFException("数据不完整");
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            if (len == 0) return 0;
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            else if (n < 0 && strict) throw new EOFException("数据不完整");
            return n;
        }

        @Override
        public void close() {
            // 不关底层流
        }
    }

    /** 检查 path 是不是符号链接（不跟随）。 */
    static boolean exists(File f) {
        return Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS);
    }
}
