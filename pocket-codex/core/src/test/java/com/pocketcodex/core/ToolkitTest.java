package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

/**
 * 开发工具包：自己签名的假 Termux 仓库（本地 HTTP），走完 验签 → 索引 → 依赖 → 下载校验 → 解 .deb → 修补 → 整体替换，
 * 再测 pkg install / remove / search。另外用真实的 Termux InRelease 验内置公钥。
 */
public class ToolkitTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String OLD = Toolkit.TERMUX_PREFIX;
    private HttpServer server;
    private File repoDir;
    private KeyPair kp;
    private List<OpenPgp.Key> keys;
    private final AtomicInteger hits = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        repoDir = tmp.newFolder("repo");
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        kp = g.generateKeyPair();
        keys = OpenPgp.parseKeys(packet(6, publicKeyBody((RSAPublicKey) kp.getPublic())));
        assertEquals(1, keys.size());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            File f = new File(repoDir, ex.getRequestURI().getPath().replaceFirst("^/", ""));
            if (!f.isFile()) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            byte[] b = Files.readAllBytes(f.toPath());
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(b);
            }
        });
        server.start();
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    /* ---------------- 真实 InRelease ---------------- */

    @Test
    public void realTermuxInReleaseVerifiesWithBuiltinKeys() throws Exception {
        String text = resource("termux/InRelease.txt");
        OpenPgp.Verified v = OpenPgp.verifyClearsigned(text);
        assertEquals("5A897D96E57CF20C", v.signer);
        assertTrue(v.text.contains("main/binary-aarch64/Packages.gz"));
        // 改一个字节就不行
        String tampered = text.replaceFirst("Packages.gz", "Packages.gZ");
        try {
            OpenPgp.verifyClearsigned(tampered);
            fail("改过的索引不应该通过");
        } catch (java.io.IOException expected) {
            // ok
        }
        // 别的钥匙签的也不行
        try {
            OpenPgp.verifyClearsigned(text, keys);
            fail("不认识的签名者不应该通过");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("签名者"));
        }
        Toolkit t = new Toolkit(tmp.newFolder("p1"), tmp.newFolder("w1"), "arm64-v8a");
        String[] w = t.verifyRelease(text.getBytes(StandardCharsets.UTF_8), "main/binary-aarch64/Packages.gz");
        assertEquals(64, w[0].length());
    }

    /* ---------------- 假仓库端到端 ---------------- */

    @Test
    public void installBaseEndToEndAndPkgCommands() throws Exception {
        buildRepo(false);
        File prefix = new File(tmp.getRoot(), "runtime/usr");
        Toolkit t = toolkit(prefix, Arrays.asList(new Toolkit.Repo("dead", "坏源", "http://127.0.0.1:1/nope"), repo()));
        List<String> steps = new ArrayList<>();
        t.installBase(null, progress(steps));
        assertTrue(t.installed());
        assertFalse(new File(prefix.getPath() + ".new").exists());

        // 文本：shebang 里的旧前缀换成新前缀
        String script = new String(Files.readAllBytes(new File(prefix, "bin/hello").toPath()), StandardCharsets.UTF_8);
        assertTrue(script, script.startsWith("#!" + prefix.getAbsolutePath() + "/bin/sh\n"));
        assertTrue(new File(prefix, "bin/hello").canExecute());
        // 符号链接：绝对旧前缀 → 相对
        assertEquals("../bin/hello", Files.readSymbolicLink(new File(prefix, "libexec/hi").toPath()).toString());
        assertEquals("hello", Files.readSymbolicLink(new File(prefix, "bin/hey").toPath()).toString());
        // 硬链接变成复制
        assertArrayEquals(new File(prefix, "bin/hello"), new File(prefix, "bin/hello2"));
        // 手册被丢掉
        assertFalse(new File(prefix, "share/man/man1/hello.1").exists());
        // ELF：RUNPATH 和 /bin/sh
        File elf = new File(prefix, "bin/tool");
        try (RandomAccessFile raf = new RandomAccessFile(elf, "r")) {
            assertEquals(Collections.singletonList("$ORIGIN/../lib"), Elf.readRunpaths(raf));
        }
        byte[] eb = Files.readAllBytes(elf.toPath());
        String es = new String(eb, StandardCharsets.ISO_8859_1);
        assertFalse("旧的 /bin/sh 应该被替换", es.contains(OLD + "/bin/sh\0"));
        String sys = new String(new char[(OLD + "/bin/sh").length() - "system/bin/sh".length()]).replace('\0', '/') + "system/bin/sh";
        assertEquals((OLD + "/bin/sh").length(), sys.length());
        assertTrue(es.contains(sys + "\0"));
        assertTrue(es.contains("'" + sys + "'"));
        assertTrue("sha256sum 不能被误改", es.contains(OLD + "/bin/sha256sum"));
        assertEquals(eb.length, elfBytes().length);
        try (RandomAccessFile raf = new RandomAccessFile(new File(prefix, "lib/libfoo.so"), "r")) {
            assertEquals(Collections.singletonList("$ORIGIN"), Elf.readRunpaths(raf));
        }
        try (RandomAccessFile raf = new RandomAccessFile(new File(prefix, "libexec/git-core/git-thing"), "r")) {
            assertEquals(Collections.singletonList("$ORIGIN/../../lib"), Elf.readRunpaths(raf));
        }
        // 状态：依赖进来了，跳过的 termux-tools 没装
        Map<String, Toolkit.Installed> st = t.status();
        assertTrue(st.containsKey("hello"));
        assertTrue(st.containsKey("libfoo"));
        assertFalse(st.containsKey("termux-tools"));
        assertTrue(st.get("hello").explicit);
        assertFalse(st.get("libfoo").explicit);
        assertTrue(new File(prefix, "bin/sh").exists() || Files.isSymbolicLink(new File(prefix, "bin/sh").toPath()));

        // pkg install（增量，依赖 hello 已装）
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rc = t.command(Arrays.asList("install", "-y", "extra"), null, new PrintStream(out, true, "UTF-8"), () -> false);
        String log = out.toString("UTF-8");
        assertEquals(log, 0, rc);
        assertTrue(log, log.contains("将安装 1 个包"));
        assertTrue(new File(prefix, "bin/extra").isFile());
        assertTrue(t.status().get("extra").explicit);
        // 再装一次：已经装好
        out.reset();
        assertEquals(0, t.command(Arrays.asList("install", "extra"), null, new PrintStream(out, true, "UTF-8"), () -> false));
        assertTrue(out.toString("UTF-8").contains("都已经装好了"));
        // 搜索、列表
        out.reset();
        t.command(Arrays.asList("search", "greet"), null, new PrintStream(out, true, "UTF-8"), () -> false);
        assertTrue(out.toString("UTF-8"), out.toString("UTF-8").startsWith("hello 1.0 [已安装]"));
        out.reset();
        t.command(Collections.singletonList("list-installed"), null, new PrintStream(out, true, "UTF-8"), () -> false);
        assertTrue(out.toString("UTF-8").contains("extra 2.0 [手动安装]"));
        // 找不到的包有提示
        out.reset();
        assertEquals(3, t.command(Arrays.asList("install", "hell"), null, new PrintStream(out, true, "UTF-8"), () -> false));
        assertTrue(out.toString("UTF-8"), out.toString("UTF-8").contains("hello"));
        // 基础包不能卸；extra 能卸
        out.reset();
        assertEquals(1, t.command(Arrays.asList("remove", "hello"), null, new PrintStream(out, true, "UTF-8"), () -> false));
        assertTrue(new File(prefix, "bin/hello").isFile());
        out.reset();
        assertEquals(0, t.command(Arrays.asList("uninstall", "extra"), null, new PrintStream(out, true, "UTF-8"), () -> false));
        assertFalse(new File(prefix, "bin/extra").exists());
        assertFalse(t.status().containsKey("extra"));
        // 不合法的包名
        out.reset();
        assertEquals(2, t.command(Arrays.asList("install", "../x"), null, new PrintStream(out, true, "UTF-8"), () -> false));

        // 重新装基础包：手动装过的包保留
        t.command(Arrays.asList("install", "extra"), null, new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"), () -> false);
        t.installBase(null, progress(steps));
        assertTrue(t.status().containsKey("extra"));
        assertTrue(new File(prefix, "bin/extra").isFile());
    }

    @Test
    public void corruptDebKeepsOldToolkit() throws Exception {
        buildRepo(false);
        File prefix = new File(tmp.getRoot(), "runtime/usr");
        Toolkit t = toolkit(prefix, Collections.singletonList(repo()));
        t.installBase(null, progress(new ArrayList<>()));
        // 仓库里的 hello.deb 被换成坏的（索引里的 SHA256 没变）
        File deb = new File(repoDir, "apt/pool/hello.deb");
        byte[] b = Files.readAllBytes(deb.toPath());
        b[b.length / 2] ^= 1;
        Files.write(deb.toPath(), b);
        try {
            t.installBase(null, progress(new ArrayList<>()));
            fail("坏包应该失败");
        } catch (Installer.InstallException e) {
            assertEquals(Installer.Kind.NETWORK, e.kind); // 校验不过会重下几次，最后报下载失败
        }
        assertTrue("原来的工具包还在", new File(prefix, "bin/hello").isFile());
        assertTrue(t.installed());
    }

    @Test
    public void unsignedIndexRejected() throws Exception {
        buildRepo(true);
        Toolkit t = toolkit(new File(tmp.getRoot(), "runtime/usr"), Collections.singletonList(repo()));
        try {
            t.installBase(null, progress(new ArrayList<>()));
            fail("签名不对应该失败");
        } catch (Installer.InstallException e) {
            assertEquals(Installer.Kind.CORRUPT, e.kind);
        }
        assertFalse(t.installed());
    }

    @Test
    public void relativeLinksAndOriginPaths() {
        assertEquals("../bin/x", Toolkit.relativeLink("libexec/x", "bin/x"));
        assertEquals("x", Toolkit.relativeLink("x2", "x"));
        assertEquals("../../lib/a", Toolkit.relativeLink("lib/b/c", "lib/a"));
        assertEquals("$ORIGIN/../lib", Elf.originPath("bin/node"));
        assertEquals("$ORIGIN", Elf.originPath("lib/libz.so"));
        assertEquals("$ORIGIN/../../lib", Elf.originPath("lib/python3.14/x.so"));
        assertFalse(Toolkit.safeRel("a/../b"));
        assertFalse(Toolkit.safeRel("/abs"));
        assertTrue(Toolkit.safeRel("bin/x"));
    }

    /* ---------------- 假仓库 ---------------- */

    private Toolkit.Repo repo() {
        return new Toolkit.Repo("test", "测试源", "http://127.0.0.1:" + server.getAddress().getPort() + "/apt");
    }

    private Toolkit toolkit(File prefix, List<Toolkit.Repo> repos) throws Exception {
        Toolkit t = new Toolkit(prefix, new File(tmp.getRoot(), "runtime/pkg"), "x86_64");
        t.setTimeouts(2000, 5000);
        t.repos = repos;
        t.base = Arrays.asList("hello", "skipme");
        t.trustedKeys = keys;
        return t;
    }

    private void buildRepo(boolean badSignature) throws Exception {
        File pool = new File(repoDir, "apt/pool");
        pool.mkdirs();
        String p = "./data/data/com.termux/files/usr/";
        // hello：脚本、链接、硬链接、ELF、手册
        ByteArrayOutputStream t1 = new ByteArrayOutputStream();
        tar(t1, "./", '5', 0755, "", new byte[0]);
        tar(t1, p + "bin/", '5', 0755, "", new byte[0]);
        tar(t1, p + "bin/hello", '0', 0755, "", ("#!" + OLD + "/bin/sh\necho hello from " + OLD + "\n").getBytes(StandardCharsets.UTF_8));
        tar(t1, p + "bin/hey", '2', 0777, "hello", new byte[0]);
        tar(t1, p + "bin/hello2", '1', 0755, p + "bin/hello", new byte[0]);
        tar(t1, p + "libexec/hi", '2', 0777, OLD + "/bin/hello", new byte[0]);
        tar(t1, p + "bin/tool", '0', 0755, "", elfBytes());
        tar(t1, p + "libexec/git-core/git-thing", '0', 0755, "", elfBytes());
        tar(t1, p + "share/man/man1/hello.1", '0', 0644, "", "manual".getBytes(StandardCharsets.UTF_8));
        tar(t1, "./data/data/com.termux/files/home/.hello", '0', 0644, "", "x".getBytes(StandardCharsets.UTF_8));
        t1.write(new byte[1024]);
        byte[] hello = deb(t1.toByteArray());
        ByteArrayOutputStream t2 = new ByteArrayOutputStream();
        tar(t2, p + "lib/libfoo.so", '0', 0644, "", elfBytes());
        t2.write(new byte[1024]);
        byte[] libfoo = deb(t2.toByteArray());
        ByteArrayOutputStream t3 = new ByteArrayOutputStream();
        tar(t3, p + "bin/extra", '0', 0755, "", "#!/bin/sh\necho extra\n".getBytes(StandardCharsets.UTF_8));
        t3.write(new byte[1024]);
        byte[] extra = deb(t3.toByteArray());
        ByteArrayOutputStream t4 = new ByteArrayOutputStream();
        tar(t4, p + "share/skipme/readme", '0', 0644, "", "s".getBytes(StandardCharsets.UTF_8));
        t4.write(new byte[1024]);
        byte[] skipme = deb(t4.toByteArray());
        Files.write(new File(pool, "hello.deb").toPath(), hello);
        Files.write(new File(pool, "libfoo.deb").toPath(), libfoo);
        Files.write(new File(pool, "extra.deb").toPath(), extra);
        Files.write(new File(pool, "skipme.deb").toPath(), skipme);
        StringBuilder pk = new StringBuilder();
        stanza(pk, "hello", "1.0", "pool/hello.deb", hello, "libfoo (>= 1), termux-tools", "Greeting tool");
        stanza(pk, "libfoo", "1.1", "pool/libfoo.deb", libfoo, null, "Foo library");
        stanza(pk, "extra", "2.0", "pool/extra.deb", extra, "hello | nothing", "Extra things");
        stanza(pk, "skipme", "0.1", "pool/skipme.deb", skipme, "missing-dep", "Depends on something not in the repo");
        stanza(pk, "termux-tools", "1", "pool/none.deb", new byte[1], null, "Should be skipped");
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(gz)) {
            z.write(pk.toString().getBytes(StandardCharsets.UTF_8));
        }
        File dist = new File(repoDir, "apt/dists/stable/main/binary-x86_64");
        dist.mkdirs();
        Files.write(new File(dist, "Packages.gz").toPath(), gz.toByteArray());
        String release = "Origin: test\nLabel: test\nSuite: stable\nDate: Mon, 2 Nov 2026 16:52:37 UTC\nSHA256:\n "
                + hex(sha256(gz.toByteArray())) + " " + gz.size() + " main/binary-x86_64/Packages.gz\n";
        String signed = clearsign(release, badSignature);
        Files.write(new File(repoDir, "apt/dists/stable/InRelease").toPath(), signed.getBytes(StandardCharsets.UTF_8));
    }

    private static void stanza(StringBuilder sb, String name, String ver, String file, byte[] deb, String deps, String desc) throws Exception {
        sb.append("Package: ").append(name).append('\n').append("Version: ").append(ver).append('\n').append("Architecture: x86_64\n");
        if (deps != null) sb.append("Depends: ").append(deps).append('\n');
        sb.append("Filename: ").append(file).append('\n').append("Size: ").append(deb.length).append('\n')
                .append("Installed-Size: 4\n").append("SHA256: ").append(hex(sha256(deb))).append('\n')
                .append("Description: ").append(desc).append('\n').append(" Long description line.\n\n");
    }

    private String clearsign(String text, boolean bad) throws Exception {
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        byte[] fp = keys.get(0).fingerprint;
        ByteArrayOutputStream hashed = new ByteArrayOutputStream();
        hashed.write(new byte[]{5, 2, 0x6a, (byte) 0xe8, (byte) 0xbf, (byte) 0xd5}); // 创建时间
        hashed.write(22);
        hashed.write(33);
        hashed.write(4);
        hashed.write(fp);
        byte[] h = hashed.toByteArray();
        ByteArrayOutputStream pre = new ByteArrayOutputStream();
        pre.write(new byte[]{4, 1, 1, 8, (byte) (h.length >> 8), (byte) h.length});
        pre.write(h);
        byte[] prefix = pre.toByteArray();
        String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
        String canon = String.join("\r\n", lines);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(canon.getBytes(StandardCharsets.UTF_8));
        md.update(prefix);
        md.update(new byte[]{4, (byte) 0xFF, 0, 0, (byte) (prefix.length >> 8), (byte) prefix.length});
        byte[] digest = md.digest();
        if (bad) digest[5] ^= 1;
        Signature s = Signature.getInstance("NONEwithRSA");
        s.initSign(kp.getPrivate());
        ByteArrayOutputStream di = new ByteArrayOutputStream();
        di.write(new BigInteger("3031300d060960864801650304020105000420", 16).toByteArray());
        di.write(digest);
        s.update(di.toByteArray());
        byte[] sig = s.sign();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(prefix);
        body.write(new byte[]{0, 0, digest[0], digest[1]});
        body.write(mpi(new BigInteger(1, sig)));
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(packet(2, body.toByteArray()));
        return "-----BEGIN PGP SIGNED MESSAGE-----\nHash: SHA256\n\n" + text.substring(0, text.length() - 1)
                + "\n-----BEGIN PGP SIGNATURE-----\n\n" + b64 + "\n=AAAA\n-----END PGP SIGNATURE-----\n";
    }

    private static byte[] publicKeyBody(RSAPublicKey k) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(new byte[]{4, 0x60, 0, 0, 0, 1});
        b.write(mpi(k.getModulus()));
        b.write(mpi(k.getPublicExponent()));
        return b.toByteArray();
    }

    private static byte[] mpi(BigInteger v) {
        byte[] raw = v.toByteArray();
        int off = raw[0] == 0 ? 1 : 0;
        int bits = v.bitLength();
        byte[] out = new byte[2 + raw.length - off];
        out[0] = (byte) (bits >> 8);
        out[1] = (byte) bits;
        System.arraycopy(raw, off, out, 2, raw.length - off);
        return out;
    }

    /** 旧格式包头（2 字节长度）。 */
    private static byte[] packet(int tag, byte[] body) {
        byte[] out = new byte[3 + body.length];
        out[0] = (byte) (0x80 | (tag << 2) | 1);
        out[1] = (byte) (body.length >> 8);
        out[2] = (byte) body.length;
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }

    private static byte[] deb(byte[] dataTar) throws Exception {
        ByteArrayOutputStream xz = new ByteArrayOutputStream();
        try (XZOutputStream z = new XZOutputStream(xz, new LZMA2Options(1))) {
            z.write(dataTar);
        }
        ByteArrayOutputStream ctl = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(ctl)) {
            z.write(new byte[1024]);
        }
        ByteArrayOutputStream ar = new ByteArrayOutputStream();
        ar.write("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        arMember(ar, "debian-binary", "2.0\n".getBytes(StandardCharsets.US_ASCII));
        arMember(ar, "control.tar.gz", ctl.toByteArray());
        arMember(ar, "data.tar.xz", xz.toByteArray());
        return ar.toByteArray();
    }

    private static void arMember(ByteArrayOutputStream ar, String name, byte[] data) throws Exception {
        String h = String.format("%-16s%-12s%-6s%-6s%-8s%-10s`\n", name, "0", "0", "0", "100644", data.length);
        ar.write(h.getBytes(StandardCharsets.US_ASCII));
        ar.write(data);
        if ((data.length & 1) != 0) ar.write('\n');
    }

    private static void tar(ByteArrayOutputStream out, String name, char type, int mode, String link, byte[] data) throws Exception {
        byte[] h = new byte[512];
        put(h, 0, name.getBytes(StandardCharsets.UTF_8), 100);
        put(h, 100, String.format("%07o", mode).getBytes(StandardCharsets.US_ASCII), 8);
        put(h, 108, "0000000".getBytes(StandardCharsets.US_ASCII), 8);
        put(h, 116, "0000000".getBytes(StandardCharsets.US_ASCII), 8);
        put(h, 124, String.format("%011o", data.length).getBytes(StandardCharsets.US_ASCII), 12);
        put(h, 136, "00000000000".getBytes(StandardCharsets.US_ASCII), 12);
        h[156] = (byte) type;
        put(h, 157, link.getBytes(StandardCharsets.UTF_8), 100);
        put(h, 257, "ustar\0".getBytes(StandardCharsets.US_ASCII), 6);
        put(h, 263, "00".getBytes(StandardCharsets.US_ASCII), 2);
        Arrays.fill(h, 148, 156, (byte) ' ');
        int sum = 0;
        for (byte b : h) sum += b & 0xFF;
        put(h, 148, String.format("%06o\0 ", sum).getBytes(StandardCharsets.US_ASCII), 8);
        out.write(h);
        out.write(data);
        int pad = (512 - data.length % 512) % 512;
        out.write(new byte[pad]);
    }

    private static void put(byte[] h, int off, byte[] v, int max) {
        System.arraycopy(v, 0, h, off, Math.min(v.length, max));
    }

    /** 最小的 64 位 ELF：PT_LOAD + PT_DYNAMIC（STRTAB、RUNPATH），数据里有几处旧前缀的 /bin/sh。 */
    static byte[] elfBytes() {
        byte[] strtab = ("\0" + OLD + "/lib\0").getBytes(StandardCharsets.US_ASCII);
        byte[] data = (OLD + "/bin/sh\0" + OLD + "/bin/sha256sum\0'" + OLD + "/bin/sh'\0").getBytes(StandardCharsets.US_ASCII);
        int dynOff = 64 + 56 * 2, strOff = dynOff + 48, dataOff = strOff + strtab.length;
        int total = dataOff + data.length;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(total).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        b.putShort((short) 3).putShort((short) 0x3E).putInt(1).putLong(0).putLong(64).putLong(0).putInt(0);
        b.putShort((short) 64).putShort((short) 56).putShort((short) 2).putShort((short) 0).putShort((short) 0).putShort((short) 0);
        b.putInt(1).putInt(5).putLong(0).putLong(0).putLong(0).putLong(total).putLong(total).putLong(0x1000);
        b.putInt(2).putInt(6).putLong(dynOff).putLong(dynOff).putLong(dynOff).putLong(48).putLong(48).putLong(8);
        b.putLong(5).putLong(strOff);
        b.putLong(29).putLong(1);
        b.putLong(0).putLong(0);
        b.put(strtab);
        b.put(data);
        return b.array();
    }

    private static Toolkit.Progress progress(List<String> steps) {
        return new Toolkit.Progress() {
            @Override
            public void step(String text, long done, long total) {
                steps.add(text);
            }

            @Override
            public boolean cancelled() {
                return false;
            }
        };
    }

    private static void assertArrayEquals(File a, File b) throws Exception {
        org.junit.Assert.assertArrayEquals(Files.readAllBytes(a.toPath()), Files.readAllBytes(b.toPath()));
    }

    private static byte[] sha256(byte[] b) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(b);
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private String resource(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, in);
            return new String(Installer.readAll(in, 1 << 20), StandardCharsets.UTF_8);
        }
    }
}
