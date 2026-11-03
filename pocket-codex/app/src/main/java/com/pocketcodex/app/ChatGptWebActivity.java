package com.pocketcodex.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

/**
 * 聊天模式的“网页版”：WebView 里打开官方 chatgpt.com，用户自己登录、自己聊。口袋工坊只是一个浏览器外壳：
 * 不注入脚本、不读页面内容、不改 User-Agent。用的是 ChatGPT 自己的聊天，不经过 Codex，不占 Codex/Work 额度。
 * 登录状态（Cookie）留在 WebView 里，下次不用再登。
 */
public final class ChatGptWebActivity extends AppCompatActivity {
    static final String EXTRA_URL = "url";
    private static final int REQ_UPLOAD = 61;

    private WebView web;
    private ProgressBar bar;
    private ValueCallback<Uri[]> uploadCb;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);        // chatgpt.com 是单页应用，没有 JS 打不开
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(false);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true); // 登录在 auth.openai.com，回到 chatgpt.com 要带上
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                Uri u = r.getUrl();
                String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
                if (host.equals("accounts.google.com")) { // Google 不允许在内嵌网页里登录：不硬闯，给出办法
                    googleBlocked();
                    return true;
                }
                if (staysInside(host)) return false;
                try { // 回答里的其他链接：交给系统浏览器
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                    // 没有浏览器就算了
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                CookieManager.getInstance().flush(); // 登录状态及时落盘
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? android.view.View.GONE : android.view.View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (uploadCb != null) uploadCb.onReceiveValue(null);
                uploadCb = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_UPLOAD);
                    return true;
                } catch (Exception e) {
                    uploadCb = null;
                    return false;
                }
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.accent(this)));
        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3), Gravity.TOP));
        setContentView(root);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });
        if (b != null) web.restoreState(b);
        else {
            String url = getIntent().getStringExtra(EXTRA_URL);
            web.loadUrl(url != null && url.startsWith(ChatGpt.HOME) ? url : ChatGpt.HOME);
        }
    }

    /** 留在 WebView 里的网站：OpenAI 自己的，加上登录会用到的微软、苹果账号页和 Cloudflare 验证页。 */
    static boolean staysInside(String host) {
        String[] own = {"chatgpt.com", "openai.com", "oaistatic.com", "oaiusercontent.com",
                "login.microsoftonline.com", "login.live.com", "account.live.com", "appleid.apple.com", "idmsa.apple.com", "challenges.cloudflare.com"};
        for (String d : own) if (host.equals(d) || host.endsWith("." + d)) return true;
        return false;
    }

    private void googleBlocked() {
        if (isFinishing()) return;
        boolean app = ChatGpt.appInstalled(this);
        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle("Google 账号不能在这里登录")
                .setMessage("Google 不允许在 App 内嵌的网页里登录。可以在登录页改用邮箱登录" + (app ? "，或者转到官方 ChatGPT App 继续。" : "。"))
                .setNegativeButton("知道了", null);
        if (app) d.setPositiveButton("打开 ChatGPT App", (x, w) -> {
            ChatGpt.openApp(this, null);
            finish();
        });
        d.show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_UPLOAD || uploadCb == null) return;
        // 选择器返回的地址不能直接信：只放行别的应用提供的 content:// 文件，挡住 file:// 和本应用自己的 Provider
        //（否则一个不怀好意的文件选择器可以让网页上传口袋工坊的私有文件，例如登录凭据）
        Uri[] picked = WebChromeClient.FileChooserParams.parseResult(res, data);
        java.util.List<Uri> ok = new java.util.ArrayList<>();
        if (picked != null) for (Uri u : picked) if (safeUpload(u, getPackageName())) ok.add(u);
        if (picked != null && ok.size() < picked.length) {
            android.widget.Toast.makeText(this, "有文件不能从这里上传，已跳过", android.widget.Toast.LENGTH_LONG).show();
        }
        uploadCb.onReceiveValue(ok.isEmpty() ? null : ok.toArray(new Uri[0]));
        uploadCb = null;
    }

    /** 能交给网页上传的文件：别的应用通过 content:// 提供的；不能是 file://，也不能是本应用自己的 Provider。 */
    static boolean safeUpload(Uri u, String pkg) {
        if (u == null || !"content".equalsIgnoreCase(u.getScheme())) return false;
        String auth = u.getAuthority();
        return auth != null && !auth.equals(pkg) && !auth.startsWith(pkg + ".");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (uploadCb != null) uploadCb.onReceiveValue(null);
        web.destroy();
        super.onDestroy();
    }
}
