package com.pocketcodex.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 `codex login --device-auth` 写到文件里的输出（带 ANSI 颜色）。实测 0.164–0.166 的格式：
 * <pre>
 * 1. Open this link in your browser and sign in to your account
 *    https://auth.openai.com/codex/device
 * 2. Enter this one-time code (expires in 15 minutes)
 *    VR0G-2QTVX
 * </pre>
 * 只认“像网址”和“像一次性代码”的片段，不依赖英文句子，CLI 改措辞也能用。
 */
public final class DeviceAuth {
    private DeviceAuth() {
    }

    /** 解析出来的结果；url、code 都有才算拿到。 */
    public static final class Info {
        public final String url;
        public final String code;
        /** 输出里写的有效期（秒）；没写就按 15 分钟。 */
        public final int expiresInSec;

        Info(String url, String code, int expiresInSec) {
            this.url = url;
            this.code = code;
            this.expiresInSec = expiresInSec;
        }
    }

    /** 进程结束后的判断。 */
    public enum Outcome { SUCCESS, EXPIRED, DEVICE_AUTH_DISABLED, NETWORK, CANCELLED, FAILED }

    private static final Pattern ANSI = Pattern.compile("\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[@-Z\\\\-_])");
    private static final Pattern URL = Pattern.compile("https://[^\\s\"'<>()]+");
    // 一次性代码：两段大写字母数字，中间一个连字符（实测 4-5 位，比如 VR0G-2QTVX）
    private static final Pattern CODE = Pattern.compile("(?<![A-Za-z0-9-])([A-Z0-9]{3,8}-[A-Z0-9]{3,8})(?![A-Za-z0-9-])");
    private static final Pattern EXPIRES = Pattern.compile("expires? in (\\d{1,4}) (second|minute|hour)s?", Pattern.CASE_INSENSITIVE);

    public static String stripAnsi(String s) {
        return ANSI.matcher(s).replaceAll("");
    }

    /** 从（可能还没写完的）输出里找网址和代码；还没出来就返回 null。 */
    public static Info parse(String raw) {
        String s = stripAnsi(raw).replace("\r", "");
        String url = null;
        Matcher um = URL.matcher(s);
        while (um.find()) {
            String u = trimPunct(um.group());
            if (!officialHost(u)) continue; // 只接受 OpenAI 官方授权页，别的链接不交给浏览器
            // 优先设备授权页（不是“设置页面”之类的帮助链接）
            if (u.contains("/device")) {
                url = u;
                break;
            }
            if (url == null) url = u;
        }
        if (url == null) return null;
        // 代码在网址之后出现；网址里本身可能也有连字符，所以从网址后面开始找
        int from = s.indexOf(url) + url.length();
        Matcher cm = CODE.matcher(s);
        String code = null;
        while (cm.find(from)) {
            String c = cm.group(1);
            from = cm.end();
            if (hasLetterAndDigitOrLong(c)) {
                code = c;
                break;
            }
        }
        if (code == null) return null;
        int secs = 15 * 60;
        Matcher em = EXPIRES.matcher(s);
        if (em.find()) {
            int n = Integer.parseInt(em.group(1));
            String unit = em.group(2).toLowerCase(Locale.ROOT);
            secs = unit.startsWith("sec") ? n : unit.startsWith("min") ? n * 60 : n * 3600;
            if (secs < 30 || secs > 24 * 3600) secs = 15 * 60;
        }
        return new Info(url, code, secs);
    }

    /** https 且主机是 auth.openai.com / *.openai.com / chatgpt.com。 */
    static boolean officialHost(String u) {
        try {
            java.net.URI uri = new java.net.URI(u);
            String h = uri.getHost();
            if (!"https".equals(uri.getScheme()) || h == null) return false;
            h = h.toLowerCase(Locale.ROOT);
            return h.equals("auth.openai.com") || h.endsWith(".openai.com") || h.equals("openai.com") || h.equals("chatgpt.com") || h.endsWith(".chatgpt.com");
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    private static boolean hasLetterAndDigitOrLong(String c) {
        // 排除 “UTF-8”“X-RAY” 这种普通单词：代码通常字母数字混排；全字母时至少 8 个字符
        boolean letter = false, digit = false;
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            if (ch >= '0' && ch <= '9') digit = true;
            else if (ch >= 'A' && ch <= 'Z') letter = true;
        }
        return (letter && digit) || c.length() >= 9;
    }

    private static String trimPunct(String u) {
        while (!u.isEmpty() && ".,;:!?)".indexOf(u.charAt(u.length() - 1)) >= 0) u = u.substring(0, u.length() - 1);
        return u;
    }

    /** 进程退出后，根据退出码和输出判断结果。 */
    public static Outcome classify(int exitCode, String raw, boolean authFileExists) {
        // 打印代码时那句“(expires in 15 minutes)”是说明，不是错误：先去掉，免得每次都被当成“过期”
        String s = EXPIRES.matcher(stripAnsi(raw)).replaceAll("").toLowerCase(Locale.ROOT);
        if (exitCode == 0 && (authFileExists || s.contains("successfully logged in"))) return Outcome.SUCCESS;
        if (s.contains("device code") && (s.contains("not enabled") || s.contains("disabled") || s.contains("enable device")
                || s.contains("not allowed") || s.contains("security settings"))) return Outcome.DEVICE_AUTH_DISABLED;
        if (s.contains("device auth timed out") || s.contains("code expired") || s.contains("code has expired")
                || s.contains("expired_token") || s.contains("device code expired") || s.contains("authorization expired")) return Outcome.EXPIRED;
        if (s.contains("error sending request") || s.contains("dns") || s.contains("connection refused") || s.contains("connect error")
                || s.contains("network") || s.contains("tls") || s.contains("certificate") || s.contains("proxy")
                || s.contains("timed out") || s.contains("timeout")) return Outcome.NETWORK;
        if (s.contains("expired")) return Outcome.EXPIRED;
        if (s.contains("cancel") || s.contains("denied") || s.contains("declined")) return Outcome.CANCELLED;
        return Outcome.FAILED;
    }

    /** 给用户看的最后几行（去掉颜色和空行），出错时显示。 */
    public static String tail(String raw, int maxLines) {
        String[] lines = stripAnsi(raw).replace("\r", "").split("\n");
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (int i = lines.length - 1; i >= 0 && n < maxLines; i--) {
            String l = lines[i].trim();
            if (l.isEmpty()) continue;
            b.insert(0, l + (b.length() > 0 ? "\n" : ""));
            n++;
        }
        return b.toString();
    }
}
