package com.pocketcodex.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 只读的 tar 流解析（ustar + GNU 长文件名/长链接 'L'/'K' + pax 'x' 的 path/linkpath），解 npm 包和 .deb 用。 */
final class TarReader {
    static final class Entry {
        final String name;
        final long size;
        final char type;
        /** 符号链接 / 硬链接的目标（其他类型为空串）。 */
        final String link;
        final int mode;

        Entry(String name, long size, char type, String link, int mode) {
            this.name = name;
            this.size = size;
            this.type = type;
            this.link = link;
            this.mode = mode;
        }

        boolean isFile() {
            return type == '0' || type == '\0' || type == '7';
        }

        boolean isDir() {
            return type == '5';
        }

        boolean isSymlink() {
            return type == '2';
        }

        boolean isHardlink() {
            return type == '1';
        }
    }

    /** 跳过大条目时也能取消。 */
    java.util.function.BooleanSupplier cancel;
    private final InputStream in;
    private final byte[] header = new byte[512];
    private long remaining;   // 当前条目还没读的数据
    private long padding;     // 当前条目数据后面的填充

    TarReader(InputStream in) {
        this.in = in;
    }

    /** 下一个条目；结束返回 null。会跳过上一个条目没读完的数据。 */
    Entry next() throws IOException {
        skipFully(remaining + padding);
        remaining = padding = 0;
        String longName = null, longLink = null;
        while (true) {
            if (!readBlock(header)) return null;
            if (allZero(header)) return null; // 结束标记
            if (!checksumOk(header)) throw new IOException("tar 头校验不对");
            String name = str(header, 0, 100);
            long size = octal(header, 124, 12);
            if (size < 0 || size > (8L << 30)) throw new IOException("tar 条目大小不对：" + size);
            char type = (char) header[156];
            String magic = str(header, 257, 6);
            if (magic.startsWith("ustar")) {
                String prefix = str(header, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
            }
            if (type == 'L' || type == 'K' || type == 'x' || type == 'g') { // 元数据条目：读出来用于下一个条目
                if (size > (1 << 20)) throw new IOException("tar 元数据太大");
                byte[] data = new byte[(int) size];
                readFully(data);
                skipFully(pad(size));
                if (type == 'L') {
                    longName = cstr(data);
                } else if (type == 'K') {
                    longLink = cstr(data);
                } else if (type == 'x') {
                    String p = pax(data, "path");
                    if (p != null) longName = p;
                    String l = pax(data, "linkpath");
                    if (l != null) longLink = l;
                }
                continue;
            }
            if (longName != null) name = longName;
            String link = longLink != null ? longLink : str(header, 157, 100);
            int mode = (int) octal(header, 100, 8);
            if (type == '1' || type == '2' || type == '5') size = 0; // 链接和目录没有数据
            remaining = size;
            padding = pad(size);
            return new Entry(name, size, type, link, mode);
        }
    }

    /** 读当前条目的数据。 */
    int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) return -1;
        int n = in.read(b, off, (int) Math.min(len, remaining));
        if (n < 0) throw new EOFException("tar 数据被截断");
        remaining -= n;
        return n;
    }

    private static long pad(long size) {
        long r = size % 512;
        return r == 0 ? 0 : 512 - r;
    }

    private boolean readBlock(byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) {
                if (off == 0) return false;
                throw new EOFException("tar 头被截断");
            }
            off += n;
        }
        return true;
    }

    private void readFully(byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) throw new EOFException("tar 被截断");
            off += n;
        }
    }

    private final byte[] skipBuf = new byte[64 * 1024];

    private void skipFully(long n) throws IOException {
        long sinceCheck = 0;
        while (n > 0) {
            if ((sinceCheck += skipBuf.length) >= (4 << 20)) {
                sinceCheck = 0;
                if (cancel != null && cancel.getAsBoolean()) throw new java.io.InterruptedIOException("已取消");
            }
            int r = in.read(skipBuf, 0, (int) Math.min(skipBuf.length, n));
            if (r < 0) throw new EOFException("tar 被截断");
            n -= r;
        }
    }

    private static boolean allZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static boolean checksumOk(byte[] h) {
        long want = octal(h, 148, 8);
        long sum = 0;
        for (int i = 0; i < 512; i++) sum += (i >= 148 && i < 156) ? ' ' : (h[i] & 0xFF);
        return sum == want;
    }

    private static String str(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static String cstr(byte[] b) {
        int end = 0;
        while (end < b.length && b[end] != 0) end++;
        return new String(b, 0, end, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] b, int off, int len) {
        if ((b[off] & 0x80) != 0) { // GNU 的二进制大小（超过 8GB 才用到）
            long v = b[off] & 0x7F;
            for (int i = 1; i < len; i++) v = (v << 8) | (b[off + i] & 0xFF);
            return v;
        }
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte c = b[i];
            if (c == 0 || c == ' ') {
                if (v != 0) break;
                continue;
            }
            if (c < '0' || c > '7') break;
            v = (v << 3) + (c - '0');
        }
        return v;
    }

    /** pax 扩展头里的 key=…（格式 “长度 key=value\n”，长度按字节算）。 */
    private static String pax(byte[] data, String key) {
        int i = 0;
        String path = null;
        while (i < data.length) {
            int sp = i;
            while (sp < data.length && data[sp] != ' ') sp++;
            if (sp >= data.length || sp - i > 9) break;
            int len;
            try {
                len = Integer.parseInt(new String(data, i, sp - i, StandardCharsets.US_ASCII));
            } catch (NumberFormatException e) {
                break;
            }
            if (len <= sp - i + 1 || i + len > data.length) break;
            int end = i + len; // 记录以 \n 结尾
            String rec = new String(data, sp + 1, end - sp - 1, StandardCharsets.UTF_8);
            if (rec.endsWith("\n")) rec = rec.substring(0, rec.length() - 1);
            if (rec.startsWith(key + "=")) path = rec.substring(key.length() + 1);
            i = end;
        }
        return path;
    }
}
