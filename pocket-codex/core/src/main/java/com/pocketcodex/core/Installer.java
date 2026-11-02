package com.pocketcodex.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 安装 Codex CLI：从 npm 仓库（官方或国内镜像）下载 @openai/codex 的平台包，断点续传，按仓库给的 sha512 校验，
 * 只解出 bin/codex 和 bin/codex-code-mode-host，写到临时目录，全部成功后改名成正式版本目录。
 * <p>
 * 目录布局（root = App 私有目录下的 codex/）：
 * <pre>
 *   root/downloads/codex-0.166.0-linux-arm64.tgz(.part)
 *   root/versions/0.166.0/bin/codex, bin/codex-code-mode-host, codex-package.json
 *   root/current            ← 文本文件，写当前版本号
 * </pre>
 */
public final class Installer {

    /** 默认安装的、和这个 App 一起测过的版本（app-server 协议还是实验性的，新版本可能改字段）。 */
    public static final String TESTED_VERSION = "0.166.0";

    /**
     * 测过的版本在官方 npm 仓库的 sha512（2026-11-02 取自 registry.npmjs.org）。镜像给的元数据必须和这里一致，
     * 防止第三方镜像换包；其他版本只能信任所选仓库自己给的值。
     */
    static final String[][] PINNED = {
            {"0.166.0-linux-arm64", "sha512-w0ScAsBr9aZKtIFrh6HN6KU2yjKKeSCzYfbb7DB0Ccj4BYPzeZtUeQprbycLCwuX3AFQdbdjapmLho2iot74jQ=="},
            {"0.166.0-linux-x64", "sha512-CxDm3+D1cpbGJeHrdzINoG+3wn9BVncP4qpZaaNYkxe8iPtRJOYyCJnnPoVjWRIBvI9vPKbRoifsMSA5xDaUpw=="},
    };

    static String pinned(String platformVersion) {
        for (String[] p : PINNED) if (p[0].equals(platformVersion)) return p[1];
        return null;
    }

    /** 新版本装好后、切换之前的自检（安卓上跑 codex --version）；抛异常就不切换，旧版本保留。 */
    public interface Verifier {
        void verify(File versionDir) throws IOException;
    }

    /** 下载源：npm 官方，或 npmmirror（国内）。 */
    public static final class Source {
        public final String id, label, registry;

        public Source(String id, String label, String registry) {
            this.id = id;
            this.label = label;
            this.registry = registry;
        }
    }

    public static final Source NPM = new Source("npm", "npm 官方（registry.npmjs.org）", "https://registry.npmjs.org");
    public static final Source NPMMIRROR = new Source("npmmirror", "国内镜像（registry.npmmirror.com）", "https://registry.npmmirror.com");

    public static List<Source> sources() {
        List<Source> l = new ArrayList<>();
        l.add(NPM);
        l.add(NPMMIRROR);
        return l;
    }

    public static Source source(String id) {
        for (Source s : sources()) if (s.id.equals(id)) return s;
        return NPM;
    }

    /** 安装进度。 */
    public interface Progress {
        /** stage：METADATA / DOWNLOAD / VERIFY / EXTRACT。done/total 是字节（total 不知道时为 -1）。 */
        void onProgress(Stage stage, long done, long total);

        /** 取消就返回 true（下载、解压循环里会检查）。 */
        boolean cancelled();
    }

    public enum Stage { METADATA, DOWNLOAD, VERIFY, EXTRACT }

    /** 安装失败：kind 给界面挑提示文案。 */
    public static final class InstallException extends IOException {
        public final Kind kind;

        public InstallException(Kind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }
    }

    public enum Kind { NETWORK, NOT_FOUND, NO_SPACE, CORRUPT, UNSUPPORTED_ABI, CANCELLED, EXEC_FAILED, IO }

    /** 平台包的元数据。 */
    public static final class Package {
        public final String version, platformVersion, tarballUrl, integrity;
        public final long size;

        Package(String version, String platformVersion, String tarballUrl, String integrity, long size) {
            this.version = version;
            this.platformVersion = platformVersion;
            this.tarballUrl = tarballUrl;
            this.integrity = integrity;
            this.size = size;
        }
    }

    private final File root;
    private final String npmPlatform;   // linux-arm64 / linux-x64
    private final String triple;        // aarch64-unknown-linux-musl / x86_64-unknown-linux-musl
    private int connectTimeoutMs = 20_000, readTimeoutMs = 30_000;

    public Installer(File root, String abi) throws InstallException {
        this.root = root;
        String a = abi.toLowerCase(Locale.ROOT);
        if (a.startsWith("arm64") || a.equals("aarch64")) {
            npmPlatform = "linux-arm64";
            triple = "aarch64-unknown-linux-musl";
        } else if (a.equals("x86_64") || a.equals("amd64")) {
            npmPlatform = "linux-x64";
            triple = "x86_64-unknown-linux-musl";
        } else {
            throw new InstallException(Kind.UNSUPPORTED_ABI, "这台设备的处理器（" + abi + "）没有官方 Codex CLI 构建，只支持 arm64-v8a 和 x86_64", null);
        }
    }

    /** 测试用：缩短超时。 */
    public void setTimeouts(int connectMs, int readMs) {
        connectTimeoutMs = connectMs;
        readTimeoutMs = readMs;
    }

    public File root() {
        return root;
    }

    /* ---------------- 已安装状态 ---------------- */

    /** 当前安装的版本；没装返回 null。 */
    public String installedVersion() {
        File cur = new File(root, "current");
        if (!cur.isFile()) return null;
        try {
            String v = new String(readAll(cur), StandardCharsets.UTF_8).trim();
            if (v.isEmpty()) return null;
            File bin = new File(versionDir(v), "bin/codex");
            File host = new File(versionDir(v), "bin/codex-code-mode-host");
            return bin.isFile() && host.isFile() ? v : null;
        } catch (IOException e) {
            return null;
        }
    }

    public File versionDir(String version) {
        return new File(root, "versions/" + version);
    }

    public File codexBinary() {
        String v = installedVersion();
        return v == null ? null : new File(versionDir(v), "bin/codex");
    }

    /* ---------------- 元数据 ---------------- */

    /** 查 latest 的版本号（只取 x.y.z）。 */
    public String latestVersion(Source src) throws InstallException {
        Map<String, Object> m = getJson(src.registry + "/@openai/codex/latest");
        String v = Json.str(m, "version");
        if (v == null) throw new InstallException(Kind.NOT_FOUND, "仓库没有返回最新版本号", null);
        return v;
    }

    public Package resolve(Source src, String version) throws InstallException {
        if (!version.matches("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}(-[0-9A-Za-z.]{1,32})?")) {
            throw new InstallException(Kind.NOT_FOUND, "版本号格式不对：" + version, null);
        }
        String pv = version + "-" + npmPlatform;
        Map<String, Object> m = getJson(src.registry + "/@openai/codex/" + pv);
        Map<String, Object> dist = Json.obj(m, "dist");
        String tarball = Json.str(dist, "tarball");
        String integrity = Json.str(dist, "integrity");
        if (tarball == null || integrity == null || !integrity.startsWith("sha512-")) {
            throw new InstallException(Kind.NOT_FOUND, "仓库里没有 " + pv + " 的下载信息", null);
        }
        String pin = pinned(pv);
        if (pin != null && !pin.equals(integrity)) {
            throw new InstallException(Kind.CORRUPT, "这个来源给的安装包和官方发布的不一致，已拒绝（请改用 npm 官方源）", null);
        }
        if (!tarball.startsWith("https://") && !tarball.startsWith("http://127.0.0.1")) {
            throw new InstallException(Kind.NETWORK, "下载地址不是 HTTPS，已拒绝", null);
        }
        return new Package(version, pv, tarball, integrity, Json.num(dist, "unpackedSize", -1));
    }

    private Map<String, Object> getJson(String url) throws InstallException {
        HttpURLConnection c = null;
        try {
            c = open(url, -1);
            int code = c.getResponseCode();
            if (code == 404) throw new InstallException(Kind.NOT_FOUND, "找不到：" + url, null);
            if (code / 100 != 2) throw new InstallException(Kind.NETWORK, "仓库返回 HTTP " + code, null);
            try (InputStream in = c.getInputStream()) {
                return Json.parseObject(new String(readAll(in, 8 << 20), StandardCharsets.UTF_8));
            }
        } catch (InstallException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new InstallException(Kind.NETWORK, "仓库返回的不是 JSON（可能被网络劫持或镜像出错）", e);
        } catch (IOException e) {
            throw new InstallException(Kind.NETWORK, "连不上 " + host(url) + "：" + e.getMessage(), e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private HttpURLConnection open(String url, long rangeFrom) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectTimeoutMs);
        c.setReadTimeout(readTimeoutMs);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "pocket-codex-installer");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (rangeFrom > 0) c.setRequestProperty("Range", "bytes=" + rangeFrom + "-");
        return c;
    }

    private static String host(String url) {
        try {
            return new URL(url).getHost();
        } catch (IOException e) {
            return url;
        }
    }

    /* ---------------- 安装 ---------------- */

    /** 需要的空闲空间估计：压缩包 + 解出来的两个程序 + 余量。 */
    public static long requiredSpace(Package p) {
        return (180L << 20) /* 压缩包 */ + (360L << 20) /* 解压 */ + (64L << 20) /* 余量 */;
    }

    /** 完整安装（已经装好同版本就直接返回，除非 force）。成功后返回版本目录。 */
    public File install(Package p, Progress progress) throws InstallException {
        return install(p, progress, null, false);
    }

    public File install(Package p, Progress progress, Verifier verifier, boolean force) throws InstallException {
        String have = installedVersion();
        String keepPrev = p.version.equals(have) ? previousVersion() : have; // 重装同版本时，原来保留的上一版本继续保留
        if (!force && p.version.equals(have)) return versionDir(have);
        File dl = new File(root, "downloads");
        mkdirs(dl);
        long need = requiredSpace(p);
        long free = root.getUsableSpace();
        File tgz = new File(dl, "codex-" + p.platformVersion + ".tgz");
        File part = new File(dl, tgz.getName() + ".part");
        long already = tgz.isFile() ? tgz.length() : part.isFile() ? part.length() : 0;
        if (free > 0 && free + already < need) {
            throw new InstallException(Kind.NO_SPACE, "存储空间不够：还需要约 " + ((need - free - already) >> 20) + " MB（共需约 " + (need >> 20) + " MB）", null);
        }
        if (!tgz.isFile()) {
            download(p, part, progress);
            if (!part.renameTo(tgz)) throw new InstallException(Kind.IO, "改名失败：" + part, null);
        }
        verify(p, tgz, progress);
        File target = versionDir(p.version);
        File tmp = new File(root, "versions/." + p.version + ".tmp");
        deleteTree(tmp);
        mkdirs(new File(tmp, "bin"));
        try {
            extract(tgz, tmp, progress);
            // 装好但还没启用：先在候选目录里自检，过了才切换
            if (verifier != null) {
                try {
                    verifier.verify(tmp);
                } catch (IOException e) {
                    throw new InstallException(Kind.EXEC_FAILED, "安装包已准备好，但这台设备暂时无法启动 Codex：" + e.getMessage(), e);
                }
            }
        } catch (InstallException e) {
            deleteTree(tmp);
            throw e;
        }
        File old = null;
        if (target.exists()) { // 重装同版本：先挪开，失败还能挪回来
            old = new File(root, "versions/." + p.version + ".old");
            deleteTree(old);
            if (!target.renameTo(old)) throw new InstallException(Kind.IO, "替换旧文件失败：" + target, null);
        }
        if (!tmp.renameTo(target)) {
            if (old != null) old.renameTo(target);
            throw new InstallException(Kind.IO, "改名失败：" + tmp, null);
        }
        deleteTree(old);
        writeCurrent(p.version);
        tgz.delete();
        // 只保留当前和上一个版本（新版本万一有问题可以退回）
        File[] vs = new File(root, "versions").listFiles();
        if (vs != null) {
            for (File v : vs) {
                String n = v.getName();
                if (n.equals(p.version) || (keepPrev != null && n.equals(keepPrev))) continue;
                deleteTree(v);
            }
        }
        return target;
    }

    /** 上一个保留的版本（可以退回），没有返回 null。 */
    public String previousVersion() {
        String cur = installedVersion();
        File[] vs = new File(root, "versions").listFiles();
        if (vs == null) return null;
        for (File v : vs) {
            String n = v.getName();
            if (n.startsWith(".") || n.equals(cur)) continue;
            if (new File(v, "bin/codex").isFile() && new File(v, "bin/codex-code-mode-host").isFile()) return n;
        }
        return null;
    }

    /** 切回某个已经装着的版本。 */
    public void activate(String version) throws InstallException {
        File v = versionDir(version);
        if (!new File(v, "bin/codex").isFile()) throw new InstallException(Kind.NOT_FOUND, "没有安装 " + version, null);
        writeCurrent(version);
    }

    private void writeCurrent(String version) throws InstallException {
        File tmp = new File(root, "current.tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write((version + "\n").getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        } catch (IOException e) {
            throw new InstallException(Kind.IO, "写版本文件失败：" + e.getMessage(), e);
        }
        File cur = new File(root, "current");
        if (!tmp.renameTo(cur)) throw new InstallException(Kind.IO, "改名失败：" + cur, null);
    }

    /** 下载到 .part，支持断点续传（服务器不支持 Range 时从头下）。网络断了自动重试几次。 */
    void download(Package p, File part, Progress progress) throws InstallException {
        int attempts = 0;
        while (true) {
            long have = part.isFile() ? part.length() : 0;
            HttpURLConnection c = null;
            try {
                c = open(p.tarballUrl, have);
                int code = c.getResponseCode();
                long total;
                boolean append;
                if (code == 206) {
                    // 必须从我们要的位置续上，否则丢弃重下
                    String cr = c.getHeaderField("Content-Range");
                    long start = -1, full = -1;
                    if (cr != null) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)").matcher(cr);
                        if (m.find()) {
                            start = Long.parseLong(m.group(1));
                            if (!m.group(3).equals("*")) full = Long.parseLong(m.group(3));
                        }
                    }
                    if (start != have) {
                        part.delete();
                        throw new IOException("服务器续传位置不对，从头下载");
                    }
                    append = true;
                    long len = c.getContentLengthLong();
                    total = full > 0 ? full : len > 0 ? have + len : -1;
                } else if (code == 200) {
                    append = false;
                    have = 0;
                    total = c.getContentLengthLong();
                } else if (code == 416) { // 本地已经和远端一样长（或更长）：交给校验；校验不过会删掉重下
                    return;
                } else if (code == 404) {
                    throw new InstallException(Kind.NOT_FOUND, "下载地址不存在（HTTP 404）", null);
                } else {
                    throw new IOException("HTTP " + code);
                }
                try (InputStream in = new BufferedInputStream(c.getInputStream(), 64 * 1024);
                     OutputStream out = new FileOutputStream(part, append)) {
                    byte[] buf = new byte[64 * 1024];
                    long done = have;
                    long lastReport = 0;
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (progress.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
                        out.write(buf, 0, n);
                        done += n;
                        if (done - lastReport >= (256 << 10) || (total > 0 && done == total)) {
                            lastReport = done;
                            progress.onProgress(Stage.DOWNLOAD, done, total);
                        }
                    }
                    if (total > 0 && done < total) throw new IOException("连接提前断开（" + done + "/" + total + "）");
                }
                return;
            } catch (InstallException e) {
                throw e;
            } catch (InterruptedIOException e) {
                if (progress.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", e);
                if (++attempts > 5) throw new InstallException(Kind.NETWORK, "下载超时：" + e.getMessage(), e);
            } catch (IOException e) {
                if (progress.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", e);
                if (noSpace(e)) throw new InstallException(Kind.NO_SPACE, "存储空间不够，下载暂停（已下载的部分会保留）", e);
                if (++attempts > 5) throw new InstallException(Kind.NETWORK, "下载失败：" + e.getMessage(), e);
            } finally {
                if (c != null) c.disconnect();
            }
            sleep(Math.min(8000, 1000L << attempts), progress);
        }
    }

    private static void sleep(long ms, Progress progress) throws InstallException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (progress.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new InstallException(Kind.CANCELLED, "已取消", e);
            }
        }
    }

    /** 按 npm 的 integrity（sha512-<base64>）校验；不对就删掉压缩包，下次从头下。 */
    void verify(Package p, File tgz, Progress progress) throws InstallException {
        byte[] want;
        try {
            want = Base64.getDecoder().decode(p.integrity.substring("sha512-".length()));
        } catch (IllegalArgumentException e) {
            throw new InstallException(Kind.CORRUPT, "仓库给的校验值格式不对", e);
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new InstallException(Kind.IO, "没有 SHA-512", e);
        }
        long total = tgz.length(), done = 0, last = 0;
        try (InputStream in = new FileInputStream(tgz)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (progress.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
                md.update(buf, 0, n);
                done += n;
                if (done - last >= (4 << 20) || done == total) {
                    last = done;
                    progress.onProgress(Stage.VERIFY, done, total);
                }
            }
        } catch (InstallException e) {
            throw e;
        } catch (IOException e) {
            throw new InstallException(Kind.IO, "读压缩包失败：" + e.getMessage(), e);
        }
        if (!MessageDigest.isEqual(md.digest(), want)) {
            tgz.delete();
            throw new InstallException(Kind.CORRUPT, "下载的文件校验不通过（已删除，请重试；如果用的是镜像，可以换成官方源）", null);
        }
    }

    /** 从 tar.gz 里解出需要的文件；拿齐了就停（后面的语音组件等不解）。 */
    void extract(File tgz, File dest, Progress progress) throws InstallException {
        String prefix = "package/vendor/" + triple + "/";
        List<String> wanted = new ArrayList<>();
        Collections.addAll(wanted, "bin/codex", "bin/codex-code-mode-host", "codex-package.json");
        java.util.Set<String> got = new java.util.HashSet<>();
        long total = tgz.length();
        try (CountingInputStream raw = new CountingInputStream(new FileInputStream(tgz));
             InputStream gz = new GZIPInputStream(new BufferedInputStream(raw, 256 * 1024), 64 * 1024)) {
            TarReader tar = new TarReader(gz);
            tar.cancel = progress::cancelled;
            TarReader.Entry e;
            while ((e = tar.next()) != null) {
                if (progress.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
                if (!e.isFile() || !e.name.startsWith(prefix)) continue;
                String rel = e.name.substring(prefix.length());
                if (!wanted.contains(rel)) continue;
                if (!got.add(rel)) throw new IOException("压缩包里有重复的 " + rel);
                if (e.size > (2L << 30)) throw new IOException("条目大得不正常：" + rel);
                File out = new File(dest, rel);
                mkdirs(out.getParentFile());
                File tmp = new File(out.getPath() + ".tmp");
                try (FileOutputStream o = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[256 * 1024];
                    long left = e.size, last = 0;
                    while (left > 0) {
                        int n = tar.read(buf, 0, (int) Math.min(buf.length, left));
                        if (n < 0) throw new IOException("压缩包被截断：" + rel);
                        o.write(buf, 0, n);
                        left -= n;
                        if (raw.count - last >= (2 << 20)) {
                            last = raw.count;
                            progress.onProgress(Stage.EXTRACT, raw.count, total);
                            if (progress.cancelled()) throw new InstallException(Kind.CANCELLED, "已取消", null);
                        }
                    }
                    o.getFD().sync();
                }
                if (!tmp.renameTo(out)) throw new IOException("改名失败：" + out);
                if (rel.startsWith("bin/") && !(out.setExecutable(true, true) && out.setReadable(true, true))) {
                    throw new IOException("设置可执行权限失败：" + out);
                }
                if (got.size() == wanted.size()) break;
            }
            progress.onProgress(Stage.EXTRACT, total, total);
        } catch (InstallException ex) {
            throw ex;
        } catch (IOException ex) {
            if (noSpace(ex)) throw new InstallException(Kind.NO_SPACE, "存储空间不够，解压没有完成", ex);
            throw new InstallException(Kind.CORRUPT, "解压失败：" + ex.getMessage(), ex);
        }
        if (!new File(dest, "bin/codex").isFile() || !new File(dest, "bin/codex-code-mode-host").isFile()) {
            throw new InstallException(Kind.CORRUPT, "压缩包里没有 " + triple + " 的 codex 程序", null);
        }
    }

    static boolean noSpace(IOException e) {
        String m = String.valueOf(e.getMessage());
        return m.contains("ENOSPC") || m.contains("No space left");
    }

    /** 删掉下载缓存（设置页“清理”用）。 */
    public void clearDownloads() {
        deleteTree(new File(root, "downloads"));
    }

    /** 卸载 Codex CLI（保留登录信息和会话）。 */
    public void uninstall() {
        new File(root, "current").delete();
        deleteTree(new File(root, "versions"));
        clearDownloads();
    }

    /* ---------------- 小工具 ---------------- */

    private static void mkdirs(File d) throws InstallException {
        if (!d.isDirectory() && !d.mkdirs()) throw new InstallException(Kind.IO, "建目录失败：" + d, null);
    }

    public static void deleteTree(File f) {
        if (f == null) return;
        boolean link = java.nio.file.Files.isSymbolicLink(f.toPath());
        if (!link && !f.exists()) return;
        if (!link && f.isDirectory()) { // 符号链接不跟进去，只删链接本身
            File[] cs = f.listFiles();
            if (cs != null) for (File c : cs) deleteTree(c);
        }
        f.delete();
    }

    static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in, 1 << 20);
        }
    }

    static byte[] readAll(InputStream in, int limit) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            b.write(buf, 0, n);
            if (b.size() > limit) throw new IOException("内容太大");
        }
        return b.toByteArray();
    }

    /** 记录读了多少压缩前的字节（解压进度）。 */
    static final class CountingInputStream extends java.io.FilterInputStream {
        long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int c = super.read();
            if (c >= 0) count++;
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long s = super.skip(n);
            count += s;
            return s;
        }
    }
}
