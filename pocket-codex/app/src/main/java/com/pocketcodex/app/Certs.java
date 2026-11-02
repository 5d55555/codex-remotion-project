package com.pocketcodex.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * 把安卓信任的 CA 证书导出成 PEM（给 codex 的 SSL_CERT_FILE / CODEX_CA_CERTIFICATE）。
 * AndroidCAStore 里别名以 “system:” 开头的是系统证书，“user:” 是用户装的；用户证书默认不导出（设置里可以打开）。
 */
public final class Certs {
    private Certs() {
    }

    /** 返回导出的证书个数。 */
    public static int export(File out, boolean includeUser) throws IOException {
        StringBuilder pem = new StringBuilder(256 * 1024);
        Set<String> seen = new HashSet<>();
        int n = 0;
        try {
            KeyStore ks = KeyStore.getInstance("AndroidCAStore");
            ks.load(null, null);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            Base64.Encoder enc = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
            for (Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {
                String alias = e.nextElement();
                boolean user = alias.startsWith("user:");
                if (user && !includeUser) continue;
                Certificate c = ks.getCertificate(alias);
                if (c == null) continue;
                byte[] der = c.getEncoded();
                String fp = Base64.getEncoder().encodeToString(sha.digest(der));
                if (!seen.add(fp)) continue;
                pem.append("-----BEGIN CERTIFICATE-----\n").append(enc.encodeToString(der)).append("\n-----END CERTIFICATE-----\n");
                n++;
            }
        } catch (Exception e) {
            throw new IOException("读取系统证书失败：" + e, e);
        }
        if (n == 0) throw new IOException("系统里没有可用的 CA 证书");
        File dir = out.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("建目录失败：" + dir);
        File tmp = new File(out.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(pem.toString().getBytes(StandardCharsets.US_ASCII));
            o.getFD().sync();
        }
        if (!tmp.renameTo(out)) throw new IOException("写证书失败");
        return n;
    }
}
