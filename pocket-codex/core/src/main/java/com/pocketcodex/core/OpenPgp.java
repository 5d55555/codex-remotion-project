package com.pocketcodex.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 只做一件事：用内置的 Termux 公钥（termux-keyring 里的 RSA 钥匙）验证 apt 仓库的 InRelease（OpenPGP 明文签名）。
 * 这样索引从哪个镜像下载都可以信任：InRelease 签名 → 里面的 Packages 摘要 → Packages 里每个 .deb 的 SHA256。
 * 只支持 v4 签名、RSA、SHA-256/384/512（Termux 仓库现在用 RSA 4096 + SHA-256）。
 */
public final class OpenPgp {
    private OpenPgp() {
    }

    static final String[] KEYRING = {"termux-autobuilds", "grimler", "agnostic-apollo", "mradityaalok", "kcubeterm", "landfillbaby", "2096779623"};

    static final class Key {
        final byte[] fingerprint; // 20 字节（v4）
        final BigInteger n, e;

        Key(byte[] fingerprint, BigInteger n, BigInteger e) {
            this.fingerprint = fingerprint;
            this.n = n;
            this.e = e;
        }

        long keyId() {
            long id = 0;
            for (int i = 12; i < 20; i++) id = (id << 8) | (fingerprint[i] & 0xFF);
            return id;
        }
    }

    public static final class Verified {
        /** 签过名的正文（已去掉转义，行尾是 \n）。 */
        public final String text;
        public final String signer;
        public final long createdSeconds;

        Verified(String text, String signer, long createdSeconds) {
            this.text = text;
            this.signer = signer;
            this.createdSeconds = createdSeconds;
        }
    }

    private static List<Key> builtin;

    static synchronized List<Key> builtinKeys() throws IOException {
        if (builtin == null) {
            List<Key> keys = new ArrayList<>();
            for (String name : KEYRING) {
                try (InputStream in = OpenPgp.class.getResourceAsStream("keyring/" + name + ".gpg")) {
                    if (in == null) throw new IOException("缺少内置公钥 " + name);
                    keys.addAll(parseKeys(Installer.readAll(in, 1 << 20)));
                }
            }
            if (keys.isEmpty()) throw new IOException("内置公钥是空的");
            builtin = keys;
        }
        return builtin;
    }

    /** 解析二进制公钥环（public key / public subkey 包）里的 RSA 钥匙。 */
    static List<Key> parseKeys(byte[] data) throws IOException {
        List<Key> out = new ArrayList<>();
        int[] pos = {0};
        while (pos[0] < data.length) {
            int[] hdr = packetHeader(data, pos);
            int tag = hdr[0], len = hdr[1], start = pos[0];
            if (start + len > data.length) throw new IOException("公钥包被截断");
            if ((tag == 6 || tag == 14) && len > 6 && data[start] == 4) {
                int algo = data[start + 5] & 0xFF;
                if (algo == 1 || algo == 3) {
                    int[] p = {start + 6};
                    BigInteger n = mpi(data, p), e = mpi(data, p);
                    byte[] fp = fingerprint(data, start, len);
                    out.add(new Key(fp, n, e));
                }
            }
            pos[0] = start + len;
        }
        return out;
    }

    private static byte[] fingerprint(byte[] data, int start, int len) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update((byte) 0x99);
            sha1.update((byte) (len >> 8));
            sha1.update((byte) len);
            sha1.update(data, start, len);
            return sha1.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 验证 InRelease；成功返回签过名的正文，失败抛 IOException。 */
    public static Verified verifyClearsigned(String armored) throws IOException {
        return verifyClearsigned(armored, builtinKeys());
    }

    static Verified verifyClearsigned(String armored, List<Key> keys) throws IOException {
        String s = armored.replace("\r\n", "\n");
        String begin = "-----BEGIN PGP SIGNED MESSAGE-----\n";
        int b = s.indexOf(begin);
        if (b < 0) throw new IOException("不是 OpenPGP 明文签名");
        int headersEnd = s.indexOf("\n\n", b + begin.length() - 1);
        if (headersEnd < 0) throw new IOException("签名格式不对");
        int sigStart = s.indexOf("\n-----BEGIN PGP SIGNATURE-----\n", headersEnd);
        if (sigStart < 0) throw new IOException("没有签名");
        String body = s.substring(headersEnd + 2, sigStart);
        // 去掉 “- ” 转义，规范化：每行去掉末尾空白，用 CRLF 连接，最后一行没有换行
        String[] lines = body.split("\n", -1);
        StringBuilder canon = new StringBuilder(body.length() + lines.length), plain = new StringBuilder(body.length());
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (l.startsWith("- ")) l = l.substring(2);
            plain.append(l).append('\n');
            int end = l.length();
            while (end > 0 && (l.charAt(end - 1) == ' ' || l.charAt(end - 1) == '\t')) end--;
            if (i > 0) canon.append("\r\n");
            canon.append(l, 0, end);
        }
        int sigEnd = s.indexOf("\n-----END PGP SIGNATURE-----", sigStart);
        if (sigEnd < 0) throw new IOException("签名没有结尾");
        String armor = s.substring(sigStart + "\n-----BEGIN PGP SIGNATURE-----\n".length(), sigEnd);
        int blank = armor.indexOf("\n\n");
        if (blank >= 0 && armor.substring(0, blank).contains(":")) armor = armor.substring(blank + 2);
        StringBuilder b64 = new StringBuilder();
        for (String l : armor.split("\n")) {
            l = l.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("=")) break; // CRC24
            b64.append(l);
        }
        byte[] sig;
        try {
            sig = Base64.getDecoder().decode(b64.toString());
        } catch (IllegalArgumentException e) {
            throw new IOException("签名不是合法的 base64", e);
        }
        byte[] text = canon.toString().getBytes(StandardCharsets.UTF_8);
        int[] pos = {0};
        IOException last = new IOException("没有可用的签名");
        while (pos[0] < sig.length) {
            int[] hdr = packetHeader(sig, pos);
            int start = pos[0];
            if (start + hdr[1] > sig.length) throw new IOException("签名包被截断");
            if (hdr[0] == 2) {
                try {
                    long[] created = new long[1];
                    String who = verifyOne(sig, start, hdr[1], text, keys, created);
                    return new Verified(plain.toString(), who, created[0]);
                } catch (IOException e) {
                    last = e;
                }
            }
            pos[0] = start + hdr[1];
        }
        throw last;
    }

    private static String verifyOne(byte[] p, int off, int len, byte[] text, List<Key> keys, long[] created) throws IOException {
        int end = off + len;
        if (len < 10 || p[off] != 4) throw new IOException("只支持 v4 签名");
        int sigType = p[off + 1] & 0xFF, pkAlgo = p[off + 2] & 0xFF, hashAlgo = p[off + 3] & 0xFF;
        if (sigType != 0x01) throw new IOException("签名类型不对：" + sigType);
        if (pkAlgo != 1 && pkAlgo != 3) throw new IOException("不支持的签名算法：" + pkAlgo);
        int hashedLen = (p[off + 4] & 0xFF) << 8 | (p[off + 5] & 0xFF);
        int hashedEnd = off + 6 + hashedLen;
        if (hashedEnd + 2 > end) throw new IOException("签名包格式不对");
        int unhashedLen = (p[hashedEnd] & 0xFF) << 8 | (p[hashedEnd + 1] & 0xFF);
        int unhashedEnd = hashedEnd + 2 + unhashedLen;
        if (unhashedEnd + 2 > end) throw new IOException("签名包格式不对");
        // 找签名者：issuer fingerprint（33）或 issuer key id（16）；hashed 和 unhashed 都看
        byte[] issuerFp = null;
        long issuerId = 0;
        boolean hasId = false;
        for (int[] range : new int[][]{{off + 6, hashedEnd}, {hashedEnd + 2, unhashedEnd}}) {
            int i = range[0];
            while (i < range[1]) {
                int l = p[i] & 0xFF, hl;
                if (l < 192) hl = 1;
                else if (l < 255) {
                    l = ((l - 192) << 8) + (p[i + 1] & 0xFF) + 192;
                    hl = 2;
                } else {
                    l = (p[i + 1] & 0xFF) << 24 | (p[i + 2] & 0xFF) << 16 | (p[i + 3] & 0xFF) << 8 | (p[i + 4] & 0xFF);
                    hl = 5;
                }
                int st = i + hl;
                if (l < 1 || st + l > range[1]) throw new IOException("签名子包格式不对");
                int type = p[st] & 0x7F;
                if (type == 33 && l == 22 && p[st + 1] == 4) issuerFp = Arrays.copyOfRange(p, st + 2, st + 22);
                else if (type == 16 && l == 9) {
                    issuerId = 0;
                    for (int k = 1; k < 9; k++) issuerId = (issuerId << 8) | (p[st + k] & 0xFF);
                    hasId = true;
                } else if (type == 2 && l == 5 && i < hashedEnd) {
                    created[0] = ((long) (p[st + 1] & 0xFF) << 24) | (p[st + 2] & 0xFF) << 16 | (p[st + 3] & 0xFF) << 8 | (p[st + 4] & 0xFF);
                }
                i = st + l;
            }
        }
        Key key = null;
        for (Key k : keys) {
            if ((issuerFp != null && Arrays.equals(issuerFp, k.fingerprint)) || (issuerFp == null && hasId && k.keyId() == issuerId)) {
                key = k;
                break;
            }
        }
        if (key == null) throw new IOException("签名者不是内置的 Termux 公钥");
        String alg;
        byte[] prefix;
        switch (hashAlgo) {
            case 8:
                alg = "SHA-256";
                prefix = hex("3031300d060960864801650304020105000420");
                break;
            case 9:
                alg = "SHA-384";
                prefix = hex("3041300d060960864801650304020205000430");
                break;
            case 10:
                alg = "SHA-512";
                prefix = hex("3051300d060960864801650304020305000440");
                break;
            default:
                throw new IOException("不支持的摘要算法：" + hashAlgo);
        }
        byte[] digest;
        try {
            MessageDigest md = MessageDigest.getInstance(alg);
            md.update(text);
            int hashedPart = 6 + hashedLen;
            md.update(p, off, hashedPart);
            md.update(new byte[]{4, (byte) 0xFF, (byte) (hashedPart >>> 24), (byte) (hashedPart >>> 16), (byte) (hashedPart >>> 8), (byte) hashedPart});
            digest = md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        if ((digest[0] & 0xFF) != (p[unhashedEnd] & 0xFF) || (digest[1] & 0xFF) != (p[unhashedEnd + 1] & 0xFF)) {
            throw new IOException("签名和内容不符（摘要前缀不对）");
        }
        int[] q = {unhashedEnd + 2};
        BigInteger s = mpi(p, q);
        if (q[0] > end) throw new IOException("签名包格式不对");
        int k = (key.n.bitLength() + 7) / 8;
        if (s.signum() <= 0 || s.compareTo(key.n) >= 0) throw new IOException("签名值不对");
        byte[] em = toFixed(s.modPow(key.e, key.n), k);
        byte[] want = new byte[k];
        int tLen = prefix.length + digest.length;
        if (k < tLen + 11) throw new IOException("公钥太短");
        want[0] = 0;
        want[1] = 1;
        for (int i = 2; i < k - tLen - 1; i++) want[i] = (byte) 0xFF;
        want[k - tLen - 1] = 0;
        System.arraycopy(prefix, 0, want, k - tLen, prefix.length);
        System.arraycopy(digest, 0, want, k - digest.length, digest.length);
        if (!MessageDigest.isEqual(em, want)) throw new IOException("签名验证失败");
        return String.format(Locale.ROOT, "%016X", key.keyId());
    }

    /** 包头：返回 {tag, 长度}，pos 移到包体开始。 */
    private static int[] packetHeader(byte[] d, int[] pos) throws IOException {
        int i = pos[0];
        if (i >= d.length) throw new IOException("包头被截断");
        int c = d[i] & 0xFF;
        if ((c & 0x80) == 0) throw new IOException("不是 OpenPGP 包");
        int tag, len;
        if ((c & 0x40) != 0) { // 新格式
            tag = c & 0x3F;
            int o = d[i + 1] & 0xFF;
            if (o < 192) {
                len = o;
                i += 2;
            } else if (o < 224) {
                len = ((o - 192) << 8) + (d[i + 2] & 0xFF) + 192;
                i += 3;
            } else if (o == 255) {
                len = (d[i + 2] & 0xFF) << 24 | (d[i + 3] & 0xFF) << 16 | (d[i + 4] & 0xFF) << 8 | (d[i + 5] & 0xFF);
                i += 6;
            } else {
                throw new IOException("不支持分段长度");
            }
        } else {
            tag = (c >> 2) & 0xF;
            int lt = c & 3;
            if (lt == 0) {
                len = d[i + 1] & 0xFF;
                i += 2;
            } else if (lt == 1) {
                len = (d[i + 1] & 0xFF) << 8 | (d[i + 2] & 0xFF);
                i += 3;
            } else if (lt == 2) {
                len = (d[i + 1] & 0xFF) << 24 | (d[i + 2] & 0xFF) << 16 | (d[i + 3] & 0xFF) << 8 | (d[i + 4] & 0xFF);
                i += 5;
            } else {
                throw new IOException("不支持不定长度的包");
            }
        }
        if (len < 0) throw new IOException("包长度不对");
        pos[0] = i;
        return new int[]{tag, len};
    }

    private static BigInteger mpi(byte[] d, int[] pos) throws IOException {
        int i = pos[0];
        if (i + 2 > d.length) throw new IOException("MPI 被截断");
        int bits = (d[i] & 0xFF) << 8 | (d[i + 1] & 0xFF);
        int bytes = (bits + 7) / 8;
        if (i + 2 + bytes > d.length) throw new IOException("MPI 被截断");
        pos[0] = i + 2 + bytes;
        return new BigInteger(1, Arrays.copyOfRange(d, i + 2, i + 2 + bytes));
    }

    private static byte[] toFixed(BigInteger v, int k) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[k];
        int copy = Math.min(raw.length, k);
        System.arraycopy(raw, raw.length - copy, out, k - copy, copy);
        return out;
    }

    private static byte[] hex(String h) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int i = 0; i < h.length(); i += 2) o.write(Integer.parseInt(h.substring(i, i + 2), 16));
        return o.toByteArray();
    }
}
