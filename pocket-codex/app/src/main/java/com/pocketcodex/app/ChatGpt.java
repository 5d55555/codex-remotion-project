package com.pocketcodex.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * 聊天模式交给官方 ChatGPT：两条路都是 ChatGPT 自己的聊天，不经过 Codex，不占 Codex/Work 额度
 * （官方帮助中心：“Work and Codex share the usage allowance…”；12 月起 “Instant chat will not draw from your usage.”）。
 * 1. 网页版：口袋工坊里的 WebView 打开官方 chatgpt.com，用户自己登录（{@link ChatGptWebActivity}）。
 * 2. 官方 App：把写好的文字交给手机上的 ChatGPT App。
 * 不调用任何私有接口，不模拟客户端，也不改 User-Agent。
 */
final class ChatGpt {
    private ChatGpt() {
    }

    static final String PKG = "com.openai.chatgpt";
    static final String HOME = "https://chatgpt.com/";

    /** 官方网页的地址；q 是要发的话，search 时带上网页搜索提示。 */
    static String url(String text, boolean search) {
        if (text == null || text.trim().isEmpty()) return HOME + (search ? "?hints=search" : "");
        try {
            return HOME + "?" + (search ? "hints=search&" : "") + "q=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return HOME;
        }
    }

    static boolean appInstalled(Context c) {
        return c.getPackageManager().getLaunchIntentForPackage(PKG) != null;
    }

    static void openWeb(Activity a, String text, boolean search) {
        a.startActivity(new Intent(a, ChatGptWebActivity.class).putExtra(ChatGptWebActivity.EXTRA_URL, url(text, search)));
    }

    /**
     * 打开官方 App：能用“分享给 ChatGPT”就把文字带过去；不能的话只打开 App，文字放进剪贴板。
     * 没装 App 返回 false。
     */
    static boolean openApp(Activity a, String text) {
        PackageManager pm = a.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(PKG);
        if (launch == null) return false;
        if (text != null && !text.isEmpty()) {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(PKG);
            if (send.resolveActivity(pm) != null) {
                a.startActivity(send);
                return true;
            }
            ClipboardManager cm = a.getSystemService(ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ChatGPT", text));
            Toast.makeText(a, "已复制，在 ChatGPT 里粘贴就行", Toast.LENGTH_LONG).show();
        }
        a.startActivity(launch);
        return true;
    }

    /** 应用商店里的 ChatGPT（没装 App 时）。 */
    static void openStore(Activity a) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PKG)));
        } catch (Exception e) {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + PKG)));
        }
    }
}
