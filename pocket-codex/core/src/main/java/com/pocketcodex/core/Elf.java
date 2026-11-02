package com.pocketcodex.core;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * 原地修补 Termux 的 ELF（64 位小端：aarch64 / x86_64）。
 * <ul>
 * <li>DT_RUNPATH / DT_RPATH：/data/data/com.termux/files/usr/lib 改成 $ORIGIN 相对路径（更短，原地写，尾部补 0）。
 * bionic 从 Android 7 起支持 DT_RUNPATH 里的 $ORIGIN，这样不用 LD_LIBRARY_PATH，也不会影响系统程序。</li>
 * <li>编译进去的 “前缀/bin/sh”（Node 的 child_process、make、gawk 的 system() 等用来跑 shell 命令）
 * 改成同样长度的 “////…/system/bin/sh”：多个斜杠在路径里等于一个。</li>
 * </ul>
 */
public final class Elf {
    private Elf() {
    }

    static boolean isElf(byte[] head, int len) {
        return len >= 4 && head[0] == 0x7f && head[1] == 'E' && head[2] == 'L' && head[3] == 'F';
    }

    /** RUNPATH/RPATH 字符串在文件里的偏移；不是 64 位小端 ELF 或没有返回空数组。 */
    static long[] runpathOffsets(RandomAccessFile f) throws IOException {
        long[] none = new long[0];
        byte[] eh = new byte[64];
        if (f.length() < 64) return none;
        f.seek(0);
        f.readFully(eh);
        if (!isElf(eh, 64) || eh[4] != 2 /* ELFCLASS64 */ || eh[5] != 1 /* 小端 */) return none;
        long phoff = le64(eh, 0x20);
        int phentsize = le16(eh, 0x36), phnum = le16(eh, 0x38);
        if (phentsize < 56 || phnum <= 0 || phnum > 4096 || phoff <= 0 || phoff + (long) phentsize * phnum > f.length()) return none;
        byte[] ph = new byte[phentsize * phnum];
        f.seek(phoff);
        f.readFully(ph);
        long dynOff = -1, dynSize = 0;
        long[][] loads = new long[phnum][];
        int nLoads = 0;
        for (int i = 0; i < phnum; i++) {
            int o = i * phentsize;
            int type = le32(ph, o);
            long off = le64(ph, o + 8), vaddr = le64(ph, o + 16), filesz = le64(ph, o + 32);
            if (type == 1) loads[nLoads++] = new long[]{vaddr, off, filesz};
            else if (type == 2) {
                dynOff = off;
                dynSize = filesz;
            }
        }
        if (dynOff < 0 || dynSize <= 0 || dynSize > (16 << 20) || dynOff + dynSize > f.length()) return none;
        byte[] dyn = new byte[(int) dynSize];
        f.seek(dynOff);
        f.readFully(dyn);
        long strtab = -1;
        long[] paths = new long[8];
        int nPaths = 0;
        for (int o = 0; o + 16 <= dyn.length; o += 16) {
            long tag = le64(dyn, o), val = le64(dyn, o + 8);
            if (tag == 0) break;
            if (tag == 5) strtab = val;
            else if ((tag == 15 || tag == 29) && nPaths < paths.length) paths[nPaths++] = val;
        }
        if (strtab < 0 || nPaths == 0) return none;
        long strOff = -1;
        for (int i = 0; i < nLoads; i++) {
            long[] l = loads[i];
            if (strtab >= l[0] && strtab < l[0] + l[2]) strOff = strtab - l[0] + l[1];
        }
        if (strOff < 0) return none;
        long[] out = new long[nPaths];
        int k = 0;
        for (int i = 0; i < nPaths; i++) {
            long p = strOff + paths[i];
            if (p >= 0 && p < f.length()) out[k++] = p;
        }
        return java.util.Arrays.copyOf(out, k);
    }

    /** 读 RUNPATH/RPATH（诊断和测试用）。 */
    public static java.util.List<String> readRunpaths(RandomAccessFile f) throws IOException {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (long p : runpathOffsets(f)) {
            byte[] s = cstrAt(f, p);
            if (s != null) out.add(new String(s, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static byte[] cstrAt(RandomAccessFile f, long p) throws IOException {
        f.seek(p);
        byte[] buf = new byte[4096];
        int n = f.read(buf, 0, (int) Math.min(buf.length, f.length() - p));
        int len = 0;
        while (len < n && buf[len] != 0) len++;
        return len >= n ? null : java.util.Arrays.copyOf(buf, len);
    }

    /** 把 RUNPATH/RPATH 改成 newPath。返回改了几处；新路径比原来长的那一处不改。不是 64 位小端 ELF 返回 0。 */
    public static int patchRunpath(RandomAccessFile f, String newPath) throws IOException {
        byte[] np = newPath.getBytes(StandardCharsets.UTF_8);
        int patched = 0;
        for (long p : runpathOffsets(f)) {
            byte[] old = cstrAt(f, p);
            if (old == null || np.length > old.length) continue;
            byte[] out = new byte[old.length];
            System.arraycopy(np, 0, out, 0, np.length);
            f.seek(p);
            f.write(out);
            patched++;
        }
        return patched;
    }

    /** RUNPATH 应该写什么：从文件所在目录到 prefix/lib 的 $ORIGIN 相对路径。 */
    public static String originPath(String fileRel) {
        int depth = 0;
        for (int i = 0; i < fileRel.length(); i++) if (fileRel.charAt(i) == '/') depth++;
        // fileRel 形如 bin/node（depth 1）、lib/libz.so（depth 1，就在 lib 里）、libexec/git-core/git（depth 2）
        if (fileRel.startsWith("lib/") && fileRel.indexOf('/', 4) < 0) return "$ORIGIN";
        StringBuilder sb = new StringBuilder("$ORIGIN");
        for (int i = 0; i < depth; i++) sb.append("/..");
        return sb.append("/lib").toString();
    }

    static int le16(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8;
    }

    static int le32(byte[] b, int o) {
        return le16(b, o) | le16(b, o + 2) << 16;
    }

    static long le64(byte[] b, int o) {
        return (le32(b, o) & 0xFFFFFFFFL) | ((long) le32(b, o + 4)) << 32;
    }
}
