package com.pocketcodex.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** 本地 HTTP 服务器扮演 npm 仓库：元数据、Range 断点续传、第一次连接中途断开、校验失败。 */
public class InstallerTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private HttpServer server;
    private byte[] tgz;
    private byte[] codexBytes, hostBytes;
    private String integrity;
    private final AtomicInteger tarballHits = new AtomicInteger();
    private volatile boolean dropFirst = true;
    private volatile boolean corrupt = false;

    @Before
    public void setUp() throws Exception {
        Random r = new Random(1);
        codexBytes = new byte[300_000];
        r.nextBytes(codexBytes);
        hostBytes = new byte[70_001];
        r.nextBytes(hostBytes);
        byte[] voice = new byte[50_000];
        r.nextBytes(voice);
        String p = "package/vendor/x86_64-unknown-linux-musl/";
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        tarEntry(tar, p + "codex-resources/bwrap", new byte[1234]);
        tarEntry(tar, p + "bin/codex", codexBytes);
        tarEntry(tar, p + "bin/codex-code-mode-host", hostBytes);
        tarEntry(tar, p + "codex-resources/voice/bin/codex-voice-host", voice);
        tarEntry(tar, p + "codex-package.json", "{\"version\":\"9.9.9\"}".getBytes(StandardCharsets.UTF_8));
        // 长文件名（超过 100 字节，走 ustar prefix）
        tarEntry(tar, p + "codex-resources/voice/licenses/" + repeat("very-long-name-", 8) + ".txt", "x".getBytes(StandardCharsets.UTF_8));
        tar.write(new byte[1024]);
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(gz)) {
            g.write(tar.toByteArray());
        }
        tgz = gz.toByteArray();
        integrity = "sha512-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(tgz));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final String base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/@openai/codex/latest", ex -> send(ex, 200, "{\"version\":\"9.9.9\"}".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/@openai/codex/9.9.9-linux-x64", ex -> send(ex, 200, ("{\"dist\":{\"tarball\":\"" + base
                + "/@openai/codex/-/codex-9.9.9-linux-x64.tgz\",\"integrity\":\"" + integrity + "\",\"unpackedSize\":420}}").getBytes(StandardCharsets.UTF_8)));
        server.createContext("/@openai/codex/-/codex-9.9.9-linux-x64.tgz", ex -> {
            int hit = tarballHits.incrementAndGet();
            byte[] body = tgz.clone();
            if (corrupt) body[body.length / 2] ^= 1;
            String range = ex.getRequestHeaders().getFirst("Range");
            int from = 0;
            if (range != null && range.startsWith("bytes=")) from = Integer.parseInt(range.substring(6, range.indexOf('-')));
            if (from > 0) {
                ex.getResponseHeaders().add("Content-Range", "bytes " + from + "-" + (body.length - 1) + "/" + body.length);
                ex.sendResponseHeaders(206, body.length - from);
            } else {
                ex.sendResponseHeaders(200, body.length);
            }
            try (OutputStream o = ex.getResponseBody()) {
                int len = body.length - from;
                if (hit == 1 && dropFirst) len = len / 3; // 第一次只给三分之一就断
                o.write(body, from, len);
                o.flush();
                if (hit == 1 && dropFirst) {
                    ex.close();
                    return;
                }
            }
        });
        server.start();
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    private Installer.Source src() {
        return new Installer.Source("test", "test", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static final Installer.Progress NOOP = new Installer.Progress() {
        @Override
        public void onProgress(Installer.Stage stage, long done, long total) {
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    };

    @Test
    public void installsResumesAndVerifies() throws Exception {
        File root = tmp.newFolder("codex");
        Installer ins = new Installer(root, "x86_64");
        ins.setTimeouts(3000, 3000);
        assertNull(ins.installedVersion());
        assertEquals("9.9.9", ins.latestVersion(src()));
        Installer.Package p = ins.resolve(src(), "9.9.9");
        File dir = ins.install(p, NOOP);
        assertTrue("第一次断开后续传", tarballHits.get() >= 2);
        assertEquals("9.9.9", ins.installedVersion());
        File codex = new File(dir, "bin/codex");
        assertArrayEquals(codexBytes, Files.readAllBytes(codex.toPath()));
        assertArrayEquals(hostBytes, Files.readAllBytes(new File(dir, "bin/codex-code-mode-host").toPath()));
        assertTrue(codex.canExecute());
        assertTrue(new File(dir, "codex-package.json").isFile());
        assertFalse("语音组件不解", new File(dir, "codex-resources").exists());
        assertFalse("压缩包装完就删", new File(root, "downloads/codex-9.9.9-linux-x64.tgz").exists());
        assertEquals(codex, ins.codexBinary());
        // 再装一次同版本：什么都不做
        int hits = tarballHits.get();
        ins.install(p, NOOP);
        assertEquals(hits, tarballHits.get());
    }

    @Test
    public void corruptDownloadIsRejectedAndDeleted() throws Exception {
        dropFirst = false;
        corrupt = true;
        File root = tmp.newFolder("codex");
        Installer ins = new Installer(root, "x86_64");
        Installer.Package p = ins.resolve(src(), "9.9.9");
        try {
            ins.install(p, NOOP);
            fail("应该校验失败");
        } catch (Installer.InstallException e) {
            assertEquals(Installer.Kind.CORRUPT, e.kind);
        }
        assertNull(ins.installedVersion());
        assertFalse(new File(root, "downloads/codex-9.9.9-linux-x64.tgz").exists());
        // 修好以后重装成功
        corrupt = false;
        ins.install(p, NOOP);
        assertEquals("9.9.9", ins.installedVersion());
    }

    @Test
    public void missingVersionAndAbi() throws Exception {
        Installer ins = new Installer(tmp.newFolder(), "x86_64");
        try {
            ins.resolve(src(), "1.0.0");
            fail();
        } catch (Installer.InstallException e) {
            assertEquals(Installer.Kind.NOT_FOUND, e.kind);
        }
        try {
            new Installer(tmp.newFolder(), "armeabi-v7a");
            fail();
        } catch (Installer.InstallException e) {
            assertEquals(Installer.Kind.UNSUPPORTED_ABI, e.kind);
        }
    }

    @Test
    public void cancelStopsDownload() throws Exception {
        dropFirst = false;
        File root = tmp.newFolder("codex");
        Installer ins = new Installer(root, "x86_64");
        Installer.Package p = ins.resolve(src(), "9.9.9");
        try {
            ins.install(p, new Installer.Progress() {
                @Override
                public void onProgress(Installer.Stage stage, long done, long total) {
                }

                @Override
                public boolean cancelled() {
                    return true;
                }
            });
            fail();
        } catch (Installer.InstallException e) {
            assertEquals(Installer.Kind.CANCELLED, e.kind);
        }
        assertNull(ins.installedVersion());
    }

    /* ---------------- 造 tar ---------------- */

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    private static void tarEntry(ByteArrayOutputStream out, String name, byte[] data) throws Exception {
        byte[] h = new byte[512];
        String prefix = "", nm = name;
        if (name.length() > 100) {
            int cut = name.lastIndexOf('/', 154);
            prefix = name.substring(0, cut);
            nm = name.substring(cut + 1);
        }
        put(h, 0, nm, 100);
        put(h, 100, "0000755", 8);
        put(h, 108, "0000000", 8);
        put(h, 116, "0000000", 8);
        put(h, 124, String.format("%011o", data.length), 12);
        put(h, 136, "00000000000", 12);
        for (int i = 148; i < 156; i++) h[i] = ' ';
        h[156] = '0';
        put(h, 257, "ustar", 6);
        h[263] = '0';
        h[264] = '0';
        put(h, 345, prefix, 155);
        long sum = 0;
        for (byte b : h) sum += b & 0xFF;
        put(h, 148, String.format("%06o", sum), 7);
        h[154] = 0;
        h[155] = ' ';
        out.write(h);
        out.write(data);
        int pad = (512 - data.length % 512) % 512;
        out.write(new byte[pad]);
    }

    private static void put(byte[] h, int off, String s, int len) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(b, 0, h, off, Math.min(b.length, len));
    }

    private static void send(com.sun.net.httpserver.HttpExchange ex, int code, byte[] body) throws java.io.IOException {
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(body);
        }
    }
}
