package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** 包安装脚本里的 update-alternatives：我们不执行脚本，自己建链接（OpenJDK 的 java/javac、nano 的 editor）。 */
public class AlternativesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Termux 自动生成的 postinst（和 nano_9.3 的一样，加上 openjdk 那种 --slave 链）。 */
    static final String POSTINST = "#!/data/data/com.termux/files/usr/bin/sh\n"
            + "if [ \"$1\" = 'configure' ]; then\n"
            + "  if [ -x \"/data/data/com.termux/files/usr/bin/update-alternatives\" ]; then\n"
            + "    update-alternatives \\\n"
            + "      --install \"/data/data/com.termux/files/usr/bin/java\" \"java\" \"/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk/bin/java\" 40 \\\n"
            + "      --slave \"/data/data/com.termux/files/usr/bin/javac\" \"javac\" \"/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk/bin/javac\" \\\n"
            + "      --slave \"/data/data/com.termux/files/usr/share/man/man1/java.1.gz\" \"java.1.gz\" \"/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk/man/man1/java.1.gz\"\n"
            + "    update-alternatives \\\n"
            + "      --install \"/data/data/com.termux/files/usr/bin/editor\" \"editor\" \"/data/data/com.termux/files/usr/bin/nano\" 50\n"
            + "  fi\nfi\n";

    @Test
    public void createsRelativeLinks() throws Exception {
        File p = tmp.newFolder("usr");
        File jdk = new File(p, "lib/jvm/java-21-openjdk/bin");
        jdk.mkdirs();
        Files.write(new File(jdk, "java").toPath(), new byte[]{1});
        Files.write(new File(jdk, "javac").toPath(), new byte[]{1});
        new File(p, "bin").mkdirs();
        Files.write(new File(p, "bin/nano").toPath(), new byte[]{1});
        List<String> made = Toolkit.applyAlternatives(POSTINST, p);
        assertTrue(made.contains("bin/java"));
        assertTrue(made.contains("bin/javac"));
        assertTrue(made.contains("bin/editor"));
        assertFalse("手册页被省略了，不建链接", made.contains("share/man/man1/java.1.gz"));
        assertEquals("../lib/jvm/java-21-openjdk/bin/java", Files.readSymbolicLink(new File(p, "bin/java").toPath()).toString());
        assertTrue(new File(p, "bin/java").exists());
        // 再来一次：链接已经指向同一个目标，照样算这个包的（升级时要记进文件清单），不重复建
        List<String> again = Toolkit.applyAlternatives(POSTINST, p);
        assertEquals(made, again);
        assertEquals("../lib/jvm/java-21-openjdk/bin/java", Files.readSymbolicLink(new File(p, "bin/java").toPath()).toString());
        assertEquals(new File(p, "lib/jvm/java-21-openjdk"), Toolkit.javaHome(p));
    }

    /** 别的包的有效链接、真文件不动；这个包以前建的、指向别处的链接换掉；targets 限制只能链接到包自己的文件。 */
    @Test
    public void respectsOwnership() throws Exception {
        File p = tmp.newFolder("usr2");
        File jdk = new File(p, "lib/jvm/java-21-openjdk/bin");
        jdk.mkdirs();
        Files.write(new File(jdk, "java").toPath(), new byte[]{1});
        Files.write(new File(jdk, "javac").toPath(), new byte[]{1});
        new File(p, "bin").mkdirs();
        Files.write(new File(p, "bin/nano").toPath(), new byte[]{1});
        Files.write(new File(p, "bin/other").toPath(), new byte[]{1});
        Files.createSymbolicLink(new File(p, "bin/java").toPath(), new File("other").toPath());   // 别的包的
        Files.write(new File(p, "bin/javac").toPath(), new byte[]{2});                         // 真文件
        Files.createSymbolicLink(new File(p, "bin/editor").toPath(), new File("other").toPath()); // 这个包旧版本的
        List<String> made = Toolkit.applyAlternatives(POSTINST, p, java.util.Collections.singleton("bin/editor"), null);
        assertEquals(java.util.Collections.singletonList("bin/editor"), made);
        assertEquals("other", Files.readSymbolicLink(new File(p, "bin/java").toPath()).toString());
        assertFalse(Files.isSymbolicLink(new File(p, "bin/javac").toPath()));
        assertEquals("nano", Files.readSymbolicLink(new File(p, "bin/editor").toPath()).toString());
        // 只允许链接到包自己的文件
        Files.delete(new File(p, "bin/java").toPath());
        List<String> limited = Toolkit.applyAlternatives(POSTINST, p, java.util.Collections.<String>emptySet(),
                java.util.Collections.singleton("bin/nano"));
        assertFalse(limited.contains("bin/java"));
        assertFalse(new File(p, "bin/java").exists());
    }

    @Test
    public void readsPostinstFromDeb() throws Exception {
        // 最小的 .deb：ar 头 + debian-binary + control.tar.gz（里面一个 ./postinst）
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        byte[] body = POSTINST.getBytes(StandardCharsets.UTF_8);
        tar.write(tarHeader("./postinst", body.length));
        tar.write(body);
        tar.write(new byte[(512 - body.length % 512) % 512]);
        tar.write(new byte[1024]);
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(gz)) {
            z.write(tar.toByteArray());
        }
        ByteArrayOutputStream deb = new ByteArrayOutputStream();
        deb.write("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        member(deb, "debian-binary", "2.0\n".getBytes(StandardCharsets.US_ASCII));
        member(deb, "control.tar.gz", gz.toByteArray());
        assertEquals(POSTINST, Toolkit.Deb.controlFile(new ByteArrayInputStream(deb.toByteArray()), "postinst"));
        assertNull(Toolkit.Deb.controlFile(new ByteArrayInputStream(deb.toByteArray()), "prerm"));
        // 只下载了开头、control.tar 被截断：要报 EOF（调用方会多取一些再试），不能当成“没有 postinst”
        byte[] all = deb.toByteArray();
        byte[] cut = java.util.Arrays.copyOf(all, all.length - gz.size() / 2);
        try {
            Toolkit.Deb.controlFile(new ByteArrayInputStream(cut), "prerm");
            org.junit.Assert.fail("截断的 control.tar 应该报错");
        } catch (java.io.EOFException expected) {
            // ok：Toolkit.fetchPostinst 只在 EOF 时多取一些重试
        }
    }

    private static void member(ByteArrayOutputStream out, String name, byte[] data) throws Exception {
        String h = String.format("%-16s%-12s%-6s%-6s%-8s%-10s`\n", name + "/", "0", "0", "0", "100644", data.length);
        out.write(h.getBytes(StandardCharsets.US_ASCII));
        out.write(data);
        if (data.length % 2 == 1) out.write('\n');
    }

    private static byte[] tarHeader(String name, int size) {
        byte[] h = new byte[512];
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(n, 0, h, 0, n.length);
        put(h, 100, "0000644");
        put(h, 108, "0000000");
        put(h, 116, "0000000");
        put(h, 124, String.format("%011o", size));
        put(h, 136, "00000000000");
        h[156] = '0';
        put(h, 257, "ustar");
        h[262] = 0;
        put(h, 263, "00");
        for (int i = 148; i < 156; i++) h[i] = ' ';
        int sum = 0;
        for (byte b : h) sum += b & 0xff;
        put(h, 148, String.format("%06o", sum));
        h[154] = 0;
        h[155] = ' ';
        return h;
    }

    private static void put(byte[] h, int off, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, h, off, b.length);
    }
}
