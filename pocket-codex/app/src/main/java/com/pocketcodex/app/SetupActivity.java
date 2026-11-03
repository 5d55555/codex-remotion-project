package com.pocketcodex.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.pocketcodex.core.DeviceLogin;
import com.pocketcodex.core.Installer;
import java.io.File;
import java.util.UUID;

/**
 * 首次使用：欢迎 → 安装 Codex CLI → 登录（设备码，过期自动换码）→ 选择工作区。
 * 按运行时的阶段决定显示哪一步；全部完成后进入主界面。
 */
public final class SetupActivity extends AppCompatActivity implements CodexRuntimeManager.Listener {
    static final int REQ_STORAGE = 7, REQ_PICK_FOLDER = 8;

    private CodexRuntimeManager rt;
    private LinearLayout content, bottom;
    private TextView steps, topTitle;
    private ScrollView scroll;
    private String shown; // 当前显示的是哪一页（同一页内只更新字段，不重建）
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean welcomeDone;
    /** 工作区页：选了“手机存储”时的文件夹。 */
    private String pickedFolder;
    private boolean useShared;

    // 安装页的控件
    private TextView instStage, instBytes, instSpeed;
    private LinearProgressIndicator instBar;
    private LinearLayout instAlertBox;
    // 登录页的控件
    private TextView codeView, expiryText, loginStatus, codeUpdated;
    private CountdownRing ring;
    private LinearLayout loginAlertBox, codeCard;
    private MaterialButton copyOpen, copyOnly, newCode;
    private int lastCodeNumber;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rt = CodexRuntimeManager.get(this);
        welcomeDone = rt.prefs.introSeen();
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        root.setFitsSystemWindows(true);
        // 顶栏
        LinearLayout top = Ui.vbox(this);
        top.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 4));
        topTitle = Ui.text(this, "", 20, R.color.text);
        topTitle.setTypeface(Typeface.DEFAULT_BOLD);
        steps = Ui.small(this, "");
        top.addView(topTitle);
        top.addView(steps, Ui.matchWrap(this, 4));
        root.addView(top, Ui.matchWrap());
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = Ui.vbox(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        bottom = Ui.vbox(this);
        bottom.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        bottom.setBackgroundColor(Ui.color(this, R.color.bg));
        root.addView(bottom, Ui.matchWrap());
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        rt.addListener(this);
        rt.check();
        render();
        h.post(tick);
    }

    @Override
    protected void onStop() {
        rt.removeListener(this);
        h.removeCallbacks(tick);
        super.onStop();
    }

    @Override
    public void onRuntimeChanged() {
        render();
    }

    /** 每半秒刷新倒计时。 */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if ("login".equals(shown)) bindLogin();
            h.postDelayed(this, 500);
        }
    };

    /* ---------------- 路由 ---------------- */

    private void render() {
        CodexRuntimeManager.Stage s = rt.stage;
        if (!welcomeDone && s != CodexRuntimeManager.Stage.READY) {
            show("welcome");
            return;
        }
        switch (s) {
            case CHECKING:
            case NOT_INSTALLED:
            case INSTALLING:
            case INSTALL_FAILED:
                show("install");
                bindInstall();
                break;
            case NEED_LOGIN:
            case LOGGING_IN:
                show("login");
                bindLogin();
                break;
            case STARTING:
                if ("login".equals(shown)) bindLogin();
                else show("starting");
                break;
            case READY:
                if (rt.prefs.workspace() == null) show("workspace");
                else {
                    startActivity(new Intent(this, ChatActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                }
                break;
            case ERROR:
                show("error");
                break;
        }
    }

    private void show(String which) {
        if (which.equals(shown)) return;
        shown = which;
        content.removeAllViews();
        bottom.removeAllViews();
        scroll.scrollTo(0, 0);
        switch (which) {
            case "welcome":
                buildWelcome();
                break;
            case "install":
                buildInstall();
                break;
            case "login":
                buildLogin();
                break;
            case "workspace":
                buildWorkspace();
                break;
            case "starting":
                topTitle.setText("正在启动");
                steps.setText("");
                content.addView(Ui.caption(this, "正在启动 Codex…"), Ui.matchWrap(this, 24));
                break;
            case "error":
                buildError();
                break;
        }
    }

    /** 步骤条：安装 → 登录 → 工作区（当前步骤用主色，完成的打勾）。 */
    private void setSteps(int current) {
        String[] names = {"安装", "登录", "工作区"};
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append("  →  ");
            int st = sb.length();
            sb.append(i < current ? "✓ " + names[i] : names[i]);
            if (i == current) {
                sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.color(this, R.color.primary)), st, sb.length(), 0);
                sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), st, sb.length(), 0);
            }
        }
        sb.append("   ·   第 ").append(String.valueOf(current + 1)).append(" 步，共 3 步");
        steps.setText(sb);
    }

    /* ---------------- 欢迎 ---------------- */

    private void buildWelcome() {
        topTitle.setText("");
        steps.setText("");
        content.addView(Ui.space(this, 24));
        content.addView(Ui.icon(this, R.drawable.ic_logo, R.color.primary, 72));
        TextView t = Ui.text(this, "让手机里的文件\n按你的想法改变", 26, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(t, Ui.matchWrap(this, 24));
        content.addView(Ui.body(this, "用自然语言修改本地文件，查看执行过程，并决定是否允许操作。"), Ui.matchWrap(this, 12));
        int[] icons = {R.drawable.ic_phone, R.drawable.ic_terminal, R.drawable.ic_shield};
        String[] lines = {"文件直接保存在这台手机上", "运行 Android 自带命令", "默认每步确认，随时可以停止"};
        for (int i = 0; i < 3; i++) {
            LinearLayout row = Ui.hbox(this);
            row.addView(Ui.icon(this, icons[i], R.color.primary, 24));
            TextView l = Ui.body(this, lines[i]);
            LinearLayout.LayoutParams lp = Ui.weight1();
            lp.leftMargin = Ui.dp(this, 12);
            row.addView(l, lp);
            content.addView(row, Ui.matchWrap(this, i == 0 ? 24 : 16));
        }
        LinearLayout note = Ui.card(this);
        note.addView(Ui.caption(this, "文件和命令在本机处理。模型需要联网，相关对话和文件内容可能发送给模型服务。\n本应用是独立开发的客户端，使用官方 Codex CLI，并非 OpenAI 官方应用。"));
        content.addView(note, Ui.matchWrap(this, 24));
        bottom.addView(Ui.primary(this, "开始设置", v -> {
            rt.prefs.setIntroSeen();
            welcomeDone = true;
            shown = null;
            render();
        }), Ui.matchWrap());
        bottom.addView(Ui.textButton(this, "自检与诊断", v -> startActivity(new Intent(this, DiagnosticsActivity.class))), Ui.matchWrap());
    }

    /* ---------------- 安装 ---------------- */

    private void buildInstall() {
        topTitle.setText("安装 Codex");
        setSteps(0);
        content.addView(Ui.title(this, "安装本地运行组件"), Ui.matchWrap(this, 8));
        content.addView(Ui.caption(this, "安装完成后，文件操作将在这台手机上执行。"), Ui.matchWrap(this, 8));
        LinearLayout ver = Ui.card(this);
        LinearLayout r1 = Ui.hbox(this);
        r1.addView(Ui.body(this, "Codex CLI"));
        TextView tag = Ui.chip(this, rt.prefs.targetVersion().equals(Installer.TESTED_VERSION) ? "已验证版本" : "未验证版本", R.color.primary_soft, R.color.primary);
        LinearLayout.LayoutParams tl = Ui.wrap();
        tl.leftMargin = Ui.dp(this, 8);
        r1.addView(tag, tl);
        ver.addView(r1);
        String abi = Paths.abi();
        String abiName = abi.equals("arm64-v8a") ? "ARM 64 位" : abi.equals("x86_64") ? "x86 64 位" : abi + "（不支持）";
        ver.addView(Ui.caption(this, rt.prefs.targetVersion() + " · " + abiName), Ui.matchWrap(this, 4));
        long free = rt.paths.root.getParentFile() != null ? rt.paths.root.getParentFile().getUsableSpace() : -1;
        ver.addView(Ui.caption(this, "需要约 620 MB 空间（下载 164 MB，装好后约 350 MB），当前可用 " + Format.mb(free)), Ui.matchWrap(this, 4));
        content.addView(ver, Ui.matchWrap(this, 16));

        content.addView(Ui.caption(this, "下载来源"), Ui.matchWrap(this, 16));
        String src = rt.prefs.source();
        content.addView(sourceOption("npm", "npm 官方", "registry.npmjs.org", !src.equals("npmmirror")), Ui.matchWrap(this, 8));
        content.addView(sourceOption("npmmirror", "国内镜像", "npmmirror · 第三方镜像（安装包与官方摘要比对）", src.equals("npmmirror")), Ui.matchWrap(this, 8));

        LinearLayout stage = Ui.card(this);
        instStage = Ui.body(this, "准备安装");
        instBar = new LinearProgressIndicator(this);
        instBar.setTrackStopIndicatorSize(0);
        instBar.setTrackCornerRadius(Ui.dp(this, 2));
        instBar.setIndicatorColor(Ui.color(this, R.color.primary));
        instBar.setTrackColor(Ui.color(this, R.color.surface2));
        instBytes = Ui.caption(this, "");
        instSpeed = Ui.small(this, "");
        stage.addView(instStage);
        stage.addView(instBar, Ui.matchWrap(this, 12));
        stage.addView(instBytes, Ui.matchWrap(this, 8));
        stage.addView(instSpeed, Ui.matchWrap(this, 2));
        stage.setTag("stagecard");
        content.addView(stage, Ui.matchWrap(this, 16));
        instAlertBox = Ui.vbox(this);
        content.addView(instAlertBox, Ui.matchWrap(this, 12));
    }

    private View sourceOption(String id, String title, String sub, boolean checked) {
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(Ui.dp(this, 64));
        MaterialRadioButton rb = new MaterialRadioButton(this);
        rb.setChecked(checked);
        rb.setClickable(false);
        card.addView(rb);
        LinearLayout tx = Ui.vbox(this);
        tx.addView(Ui.body(this, title));
        tx.addView(Ui.small(this, sub));
        card.addView(tx, Ui.weight1());
        card.setOnClickListener(v -> {
            if (rt.stage == CodexRuntimeManager.Stage.INSTALLING) {
                Toast.makeText(this, "下载中不能更换来源，请先取消", Toast.LENGTH_SHORT).show();
                return;
            }
            rt.prefs.setSource(id);
            shown = null;
            render();
        });
        card.setContentDescription(title + "，" + sub + (checked ? "，已选中" : ""));
        return card;
    }

    private void bindInstall() {
        if (instStage == null) return;
        CodexRuntimeManager.InstallProgress p = rt.install;
        bottom.removeAllViews();
        instAlertBox.removeAllViews();
        String abi = Paths.abi();
        if (!abi.equals("arm64-v8a") && !abi.equals("x86_64")) {
            instStage.setText("这台设备暂不支持");
            instAlertBox.addView(Ui.alert(this, true, "这台设备暂不支持", "官方 Codex CLI 只有 64 位 ARM 和 x86 64 位的构建。这台设备是 "
                    + String.join("、", android.os.Build.SUPPORTED_ABIS) + "。"));
            bottom.addView(Ui.outlined(this, "复制设备信息", v -> {
                copy("设备信息", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE
                        + " · " + String.join(",", android.os.Build.SUPPORTED_ABIS));
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
            }), Ui.matchWrap());
            bottom.addView(Ui.textButton(this, "运行诊断", v -> startActivity(new Intent(this, DiagnosticsActivity.class))), Ui.matchWrap());
            return;
        }
        switch (rt.stage) {
            case INSTALLING: {
                String what;
                switch (p.stage) {
                    case DOWNLOAD:
                        what = "正在下载 Codex";
                        break;
                    case VERIFY:
                        what = "正在校验安装包…";
                        break;
                    case EXTRACT:
                        what = "正在解压运行组件…";
                        break;
                    default:
                        what = "正在获取下载信息…";
                }
                instStage.setText(what);
                boolean determinate = p.total > 0 && (p.stage == Installer.Stage.DOWNLOAD || p.stage == Installer.Stage.VERIFY);
                if (determinate) {
                    instBar.setIndeterminate(false);
                    instBar.setProgressCompat((int) (p.done * 1000 / Math.max(1, p.total)), true);
                    instBar.setMax(1000);
                    instBytes.setText(Format.mb(p.done) + " / " + Format.mb(p.total) + "   " + (p.done * 100 / Math.max(1, p.total)) + "%");
                } else {
                    if (!instBar.isIndeterminate()) {
                        instBar.setVisibility(View.INVISIBLE);
                        instBar.setIndeterminate(true);
                        instBar.setVisibility(View.VISIBLE);
                    }
                    instBytes.setText(p.stage == Installer.Stage.DOWNLOAD && p.done > 0 ? "已下载 " + Format.mb(p.done) : "");
                }
                String sp = Format.speed(p.bytesPerSec), eta = Format.eta(p.done, p.total, p.bytesPerSec);
                instSpeed.setText(p.stage == Installer.Stage.DOWNLOAD ? (sp + (eta.isEmpty() ? "" : " · 剩余" + eta) + (p.sourceLabel == null ? "" : "\n来源：" + p.sourceLabel)) : "");
                // 下载阶段的“暂停”会保留已下载部分，下次接着下；校验、解压阶段是取消
                bottom.addView(Ui.outlined(this, p.stage == Installer.Stage.DOWNLOAD ? "暂停下载" : p.stage == Installer.Stage.METADATA ? "取消" : "取消安装",
                        v -> rt.cancelInstall()), Ui.matchWrap());
                break;
            }
            case INSTALL_FAILED: {
                instStage.setText("安装没有完成");
                instBar.setIndeterminate(false);
                instBar.setProgressCompat(0, false);
                instBytes.setText("");
                instSpeed.setText("");
                Installer.Kind k = rt.installErrorKind;
                String title;
                if (k == Installer.Kind.NETWORK) title = "暂时无法下载，已下载内容会保留";
                else if (k == Installer.Kind.NO_SPACE) title = "存储空间不足";
                else if (k == Installer.Kind.CORRUPT) title = "安装包校验失败，未启用该版本";
                else if (k == Installer.Kind.NOT_FOUND) title = "这个来源暂时没有所选版本";
                else if (k == Installer.Kind.UNSUPPORTED_ABI) title = "这台设备暂不支持";
                else if (k == Installer.Kind.EXEC_FAILED) title = "安装包已准备好，但本机暂时无法启动 Codex";
                else title = "无法写入运行组件";
                String friendly = k == Installer.Kind.NETWORK ? "无法连接下载服务器，请检查网络后重试。" : rt.errorMessage;
                LinearLayout al = Ui.alert(this, true, title, friendly);
                if (rt.errorMessage != null && k == Installer.Kind.NETWORK) al.addView(detailsButton(rt.errorMessage));
                instAlertBox.addView(al);
                bottom.addView(Ui.primary(this, k == Installer.Kind.NO_SPACE ? "重新检查" : "重试", v -> rt.startInstall()), Ui.matchWrap());
                if (k == Installer.Kind.EXEC_FAILED || k == Installer.Kind.UNSUPPORTED_ABI) {
                    bottom.addView(Ui.textButton(this, "运行诊断", v -> startActivity(new Intent(this, DiagnosticsActivity.class))), Ui.matchWrap());
                } else if (k != Installer.Kind.NO_SPACE) {
                    String other = rt.prefs.source().equals("npmmirror") ? "npm" : "npmmirror";
                    bottom.addView(Ui.textButton(this, other.equals("npm") ? "改用 npm 官方" : "改用国内镜像", v -> {
                        rt.prefs.setSource(other);
                        shown = null;
                        render();
                    }), Ui.matchWrap());
                }
                break;
            }
            default: {
                boolean installed = rt.installedVersion != null;
                instStage.setText(rt.stage == CodexRuntimeManager.Stage.CHECKING ? "正在检查…" : installed ? "安装完成，可以连接账号了" : "准备安装");
                instBar.setIndeterminate(false);
                instBar.setProgressCompat(installed ? 1000 : 0, false);
                instBar.setMax(1000);
                instBytes.setText("");
                boolean paused = rt.stage == CodexRuntimeManager.Stage.NOT_INSTALLED && rt.installErrorKind == Installer.Kind.CANCELLED;
                if (paused) instStage.setText("下载已暂停");
                instSpeed.setText(paused ? "已下载的内容会保留，继续时接着下载。" : "");
                if (rt.stage == CodexRuntimeManager.Stage.NOT_INSTALLED) {
                    bottom.addView(Ui.primary(this, paused ? "继续下载" : "下载并安装", v -> rt.startInstall()), Ui.matchWrap());
                }
            }
        }
    }

    /* ---------------- 登录 ---------------- */

    private void buildLogin() {
        topTitle.setText("连接账号");
        setSteps(1);
        content.addView(Ui.title(this, "连接你的 Codex"), Ui.matchWrap(this, 8));
        content.addView(Ui.caption(this, "在浏览器完成授权后，回到这里即可。"), Ui.matchWrap(this, 8));
        codeCard = Ui.card(this);
        codeCard.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        LinearLayout head = Ui.hbox(this);
        head.addView(Ui.caption(this, "一次性登录代码"), Ui.weight1());
        ring = new CountdownRing(this);
        head.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        codeCard.addView(head);
        codeView = Ui.text(this, "", 32, R.color.text);
        codeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeView.setGravity(Gravity.CENTER);
        codeView.setMinHeight(Ui.dp(this, 64));
        codeView.setTextIsSelectable(true);
        codeView.setLetterSpacing(0.08f);
        codeCard.addView(codeView, Ui.matchWrap(this, 8));
        expiryText = Ui.caption(this, "");
        expiryText.setGravity(Gravity.CENTER);
        codeCard.addView(expiryText, Ui.matchWrap());
        copyOpen = Ui.primary(this, "复制代码并打开浏览器", v -> copyAndOpen(true));
        codeCard.addView(copyOpen, Ui.matchWrap(this, 16));
        LinearLayout two = Ui.hbox(this);
        copyOnly = Ui.outlined(this, "仅复制代码", v -> copyAndOpen(false));
        newCode = Ui.outlined(this, "换一个代码", v -> rt.refreshLoginCode());
        LinearLayout.LayoutParams a = Ui.weight1(), b2 = Ui.weight1();
        b2.leftMargin = Ui.dp(this, 8);
        two.addView(copyOnly, a);
        two.addView(newCode, b2);
        codeCard.addView(two, Ui.matchWrap(this, 8));
        codeUpdated = Ui.small(this, "即将过期时会自动换码，请使用页面上最新的代码。");
        codeCard.addView(codeUpdated, Ui.matchWrap(this, 8));
        content.addView(codeCard, Ui.matchWrap(this, 24));
        loginStatus = Ui.caption(this, "");
        content.addView(loginStatus, Ui.matchWrap(this, 12));
        loginAlertBox = Ui.vbox(this);
        content.addView(loginAlertBox, Ui.matchWrap(this, 8));
        TextView help = Ui.text(this, "登录遇到问题？", 14, R.color.primary);
        help.setMinHeight(Ui.dp(this, 48));
        help.setGravity(Gravity.CENTER_VERTICAL);
        help.setOnClickListener(v -> showLoginHelp());
        content.addView(help, Ui.matchWrap(this, 8));
        TextView api = Ui.text(this, "改用 API 密钥登录", 14, R.color.primary);
        api.setMinHeight(Ui.dp(this, 48));
        api.setGravity(Gravity.CENTER_VERTICAL);
        api.setOnClickListener(v -> showApiKeyDialog());
        content.addView(api, Ui.matchWrap());
        TextView foot = Ui.small(this, "请只在官方授权页面输入代码，不要把代码发给其他人。");
        foot.setGravity(Gravity.CENTER);
        bottom.addView(foot, Ui.matchWrap());
        lastCodeNumber = 0;
    }

    private void bindLogin() {
        if (codeView == null || !"login".equals(shown)) return;
        DeviceLogin.State s = rt.login;
        loginAlertBox.removeAllViews();
        boolean hasCode = s != null && s.phase == DeviceLogin.Phase.WAITING && s.code != null;
        if (rt.stage == CodexRuntimeManager.Stage.NEED_LOGIN && s == null && rt.errorMessage == null) {
            rt.startLogin(); // 进入登录页就自动开始（按用户要求：后台跑设备码登录）
            s = rt.login;
        }
        boolean busy = s == null || s.phase == DeviceLogin.Phase.STARTING || s.phase == DeviceLogin.Phase.REFRESHING
                || rt.stage == CodexRuntimeManager.Stage.STARTING;
        newCode.setEnabled(!busy);
        if (rt.stage == CodexRuntimeManager.Stage.STARTING) {
            setCode(null);
            loginStatus.setText("授权已保存，正在确认账号…");
            ring.set(1, false);
            expiryText.setText("");
            return;
        }
        if (hasCode) {
            if (s.codeNumber != lastCodeNumber) {
                if (lastCodeNumber > 0) codeUpdated.setText("登录代码已更新，请重新复制。");
                lastCodeNumber = s.codeNumber;
            }
            setCode(s.code);
            long left = s.expiresAtMs - SystemClock.elapsedRealtime();
            boolean warn = left < 60_000;
            ring.set(s.lifetimeMs > 0 ? (float) left / s.lifetimeMs : 1, warn);
            expiryText.setText(left > 0 ? "预计 " + Format.clock(left) + " 后过期" + (warn ? "，即将自动换码" : "") : "正在换新代码…");
            loginStatus.setText("等待你在浏览器完成授权");
            ring.setContentDescription("剩余 " + Format.clock(left));
        } else {
            setCode(null);
            ring.set(0, false);
            expiryText.setText("");
            if (s == null || s.phase == DeviceLogin.Phase.STARTING) loginStatus.setText("正在获取一次性代码…");
            else if (s.phase == DeviceLogin.Phase.REFRESHING) loginStatus.setText("当前代码即将过期，正在更换新代码…");
            else if (s.phase == DeviceLogin.Phase.STOPPED) {
                loginStatus.setText("");
                LinearLayout al = Ui.alert(this, false, "登录已暂停", "需要时点“继续登录”获取新的代码。");
                al.addView(Ui.textButton(this, "继续登录", v -> rt.startLogin()));
                loginAlertBox.addView(al);
            }
            else if (s.phase == DeviceLogin.Phase.RETRYING) {
                long wait = Math.max(0, s.retryAtMs - SystemClock.elapsedRealtime());
                loginStatus.setText("");
                LinearLayout al = Ui.alert(this, false, "暂时无法连接登录服务", s.message + "\n" + (wait / 1000) + " 秒后重试");
                LinearLayout row = Ui.hbox(this);
                row.addView(Ui.textButton(this, "立即重试", v -> rt.refreshLoginCode()));
                row.addView(Ui.textButton(this, "诊断", v -> startActivity(new Intent(this, DiagnosticsActivity.class))));
                al.addView(row);
                if (s.detail != null && !s.detail.isEmpty()) al.addView(detailsButton(s.detail));
                loginAlertBox.addView(al);
            } else if (s.phase == DeviceLogin.Phase.FAILED) {
                loginStatus.setText("");
                boolean disabled = s.outcome == com.pocketcodex.core.DeviceAuth.Outcome.DEVICE_AUTH_DISABLED;
                LinearLayout al = Ui.alert(this, true, disabled ? "需要开启设备码授权" : "登录未完成", disabled
                        ? "在 ChatGPT 网页版打开“设置 → 安全”，查找 Codex 设备码授权选项并开启。组织账号可能需要管理员允许。页面名称可能因账号而不同。"
                        : s.message);
                LinearLayout row = Ui.hbox(this);
                row.addView(Ui.textButton(this, disabled ? "已开启，重试" : "重新登录", v -> {
                    rt.errorMessage = null;
                    rt.startLogin();
                }));
                if (disabled) row.addView(Ui.textButton(this, "打开 ChatGPT", v -> openUrl("https://chatgpt.com/")));
                al.addView(row);
                if (disabled) al.addView(Ui.textButton(this, "改用 API 密钥", v -> showApiKeyDialog()));
                if (s.detail != null && !s.detail.isEmpty()) al.addView(detailsButton(s.detail));
                loginAlertBox.addView(al);
            } else if (rt.errorMessage != null) {
                loginStatus.setText("");
                LinearLayout al = Ui.alert(this, true, rt.errorMessage, rt.errorDetail);
                al.addView(Ui.textButton(this, "重新登录", v -> {
                    rt.errorMessage = null;
                    rt.startLogin();
                }));
                loginAlertBox.addView(al);
            } else {
                loginStatus.setText("正在连接登录服务…");
            }
        }
    }

    private void setCode(String code) {
        boolean has = code != null;
        codeView.setText(has ? code : "––––-–––––");
        codeView.setTextColor(Ui.color(this, has ? R.color.text : R.color.border));
        codeView.setContentDescription(has ? "登录代码 " + code.replace("", " ").trim() : "代码还没准备好");
        copyOpen.setEnabled(has);
        copyOnly.setEnabled(has);
    }

    private View detailsButton(String detail) {
        return Ui.textButton(this, "查看详情", v -> new AlertDialog.Builder(this).setTitle("详情").setMessage(detail)
                .setPositiveButton("复制", (d, w) -> copy("详情", detail)).setNegativeButton("关闭", null).show());
    }

    private void copyAndOpen(boolean open) {
        DeviceLogin.State s = rt.login;
        if (s == null || s.code == null) return;
        copy("登录代码", s.code);
        Toast.makeText(this, "代码已复制", Toast.LENGTH_SHORT).show();
        if (open) openUrl(s.url);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException e) {
            copy("网址", url);
            Toast.makeText(this, "未找到可以打开授权页面的浏览器，网址已复制", Toast.LENGTH_LONG).show();
        }
    }

    private void copy(String label, String text) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    private void showLoginHelp() {
        new AlertDialog.Builder(this).setTitle("登录遇到问题")
                .setMessage("• 打开的网页要求登录：用你的 ChatGPT 账号登录后再输入代码。\n"
                        + "• 网页提示设备码授权未开启：ChatGPT 网页版 → 设置 → 安全，开启 Codex 的设备码授权；团队账号可能需要管理员允许。\n"
                        + "• 一直拿不到代码：检查网络能否访问 auth.openai.com；需要代理时在系统 Wi-Fi 设置里配置，本应用会跟随系统代理。\n"
                        + "• 代码过期：页面会自动换新代码，请使用最新的。")
                .setPositiveButton("知道了", null).show();
    }

    private void showApiKeyDialog() {
        TextInputLayout til = new TextInputLayout(this);
        til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        til.setHint("粘贴 API 密钥");
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
        box.addView(Ui.caption(this, "API 使用单独计费，不使用 ChatGPT 套餐的对话额度。密钥只通过标准输入交给 Codex CLI，不写进日志。"));
        box.addView(til, Ui.matchWrap(this, 12));
        new AlertDialog.Builder(this).setTitle("使用 API 密钥").setView(box)
                .setPositiveButton("保存并登录", (d, w) -> {
                    String k = et.getText() == null ? "" : et.getText().toString().trim();
                    if (k.isEmpty()) return;
                    rt.loginWithApiKey(k, null);
                })
                .setNegativeButton("返回设备码登录", null).show();
    }

    /* ---------------- 工作区 ---------------- */

    private void buildWorkspace() {
        topTitle.setText("选择工作区");
        setSteps(2);
        content.addView(Ui.title(this, "文件放在哪里？"), Ui.matchWrap(this, 8));
        content.addView(Ui.caption(this, "工作区是对话默认操作的文件夹。"), Ui.matchWrap(this, 8));
        if (rt.accountEmail != null || rt.accountType != null) {
            String who = rt.accountEmail != null ? rt.accountEmail : "API 密钥";
            content.addView(Ui.alert(this, 0, "已连接 " + who, rt.accountPlan == null ? null : "套餐：" + rt.accountPlan), Ui.matchWrap(this, 12));
        }
        LinearLayout priv = Ui.card(this);
        LinearLayout r = Ui.hbox(this);
        MaterialRadioButton rb1 = new MaterialRadioButton(this);
        rb1.setChecked(!useShared);
        rb1.setClickable(false);
        r.addView(rb1);
        r.addView(Ui.body(this, "应用内工作区"), Ui.weight1());
        r.addView(Ui.chip(this, "推荐", R.color.primary_soft, R.color.primary));
        priv.addView(r);
        priv.addView(Ui.caption(this, "无需存储权限。卸载应用或清除数据会删除这些文件。"), Ui.matchWrap(this, 4));
        TextInputLayout nameBox = new TextInputLayout(this);
        nameBox.setHint("工作区名称");
        nameBox.setCounterEnabled(true);
        nameBox.setCounterMaxLength(40);
        final TextInputEditText name = new TextInputEditText(nameBox.getContext());
        name.setText("我的工作区");
        name.setSingleLine();
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        nameBox.addView(name);
        if (!useShared) priv.addView(nameBox, Ui.matchWrap(this, 8));
        priv.setOnClickListener(v -> {
            useShared = false;
            shown = null;
            render();
        });
        content.addView(priv, Ui.matchWrap(this, 16));

        LinearLayout shared = Ui.card(this);
        LinearLayout r2 = Ui.hbox(this);
        MaterialRadioButton rb2 = new MaterialRadioButton(this);
        rb2.setChecked(useShared);
        rb2.setClickable(false);
        r2.addView(rb2);
        r2.addView(Ui.body(this, "手机存储里的文件夹"), Ui.weight1());
        shared.addView(r2);
        shared.addView(Ui.caption(this, "直接修改原文件，其他编辑器和文件管理器可以看到改动。"), Ui.matchWrap(this, 4));
        if (useShared) {
            shared.addView(Ui.body(this, pickedFolder == null ? "还没有选择文件夹" : Format.prettyPath(rt, pickedFolder)), Ui.matchWrap(this, 8));
            shared.addView(Ui.outlined(this, pickedFolder == null ? "选择文件夹" : "换一个文件夹", v -> askStorageThenPick()), Ui.matchWrap(this, 8));
        }
        shared.setOnClickListener(v -> {
            if (!useShared) askStorageThenPick();
        });
        content.addView(shared, Ui.matchWrap(this, 12));

        LinearLayout mode = Ui.card(this);
        mode.addView(Ui.body(this, "运行模式：" + (Prefs.MODE_AUTO.equals(rt.prefs.mode()) ? "完全自动" : "每步确认")));
        mode.addView(Ui.caption(this, "运行命令和应用文件改动前逐项确认。可以在设置里改成“完全自动”。"), Ui.matchWrap(this, 4));
        content.addView(mode, Ui.matchWrap(this, 12));
        content.addView(Ui.caption(this, "文件和命令在本机运行，受 Android 应用沙箱限制。Agent 可能访问本应用内的其他文件；工作区不是独立隔离区。"), Ui.matchWrap(this, 12));

        bottom.addView(Ui.primary(this, useShared ? "使用此工作区" : "创建并进入", v -> {
            String path;
            if (useShared) {
                if (pickedFolder == null) {
                    askStorageThenPick();
                    return;
                }
                path = pickedFolder;
            } else {
                String n = name.getText() == null ? "" : name.getText().toString().trim();
                if (n.isEmpty()) {
                    nameBox.setError("请输入工作区名称");
                    return;
                }
                // 目录用 UUID，名字另存（名字里的 “..”、“/” 不会变成路径）
                File dir = new File(rt.paths.workspaces, UUID.randomUUID().toString());
                if (!dir.mkdirs()) {
                    Toast.makeText(this, "无法创建工作区，请检查可用存储空间", Toast.LENGTH_LONG).show();
                    return;
                }
                path = dir.getAbsolutePath();
                Workspaces.setName(this, path, n);
            }
            rt.prefs.setWorkspace(path);
            Workspaces.remember(this, path);
            startActivity(new Intent(this, ChatActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        }), Ui.matchWrap());
    }

    private void askStorageThenPick() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            pickFolder();
            return;
        }
        new AlertDialog.Builder(this).setTitle("允许访问手机存储")
                .setMessage("授权后，助手可以读取和修改系统允许本应用访问的共享存储，包括所选文件夹之外的文件。\n\n选择工作区只设置默认操作位置，不会把助手限制在这个文件夹内。改动会直接写入原文件。")
                .setPositiveButton("继续授权", (d, w) -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE))
                .setNegativeButton("使用应用内工作区", (d, w) -> {
                    useShared = false;
                    shown = null;
                    render();
                }).show();
    }

    private void pickFolder() {
        startActivityForResult(new Intent(this, FolderPickerActivity.class), REQ_PICK_FOLDER);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code != REQ_STORAGE) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            pickFolder();
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this).setMessage("请在系统设置中允许文件访问。")
                    .setPositiveButton("打开应用设置", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("取消", null).show();
        } else {
            Toast.makeText(this, "未获得存储权限，仍可使用应用内工作区。", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK_FOLDER && res == RESULT_OK && data != null) {
            pickedFolder = data.getStringExtra(FolderPickerActivity.EXTRA_PATH);
            useShared = pickedFolder != null;
            shown = null;
            render();
        }
    }

    /* ---------------- 错误 ---------------- */

    private void buildError() {
        topTitle.setText("出了点问题");
        steps.setText("");
        content.addView(Ui.alert(this, true, rt.errorMessage == null ? "Codex 启动失败" : rt.errorMessage, rt.errorDetail), Ui.matchWrap(this, 16));
        bottom.addView(Ui.primary(this, "重试", v -> {
            shown = null;
            rt.startServer();
        }), Ui.matchWrap());
        bottom.addView(Ui.textButton(this, "运行诊断", v -> startActivity(new Intent(this, DiagnosticsActivity.class))), Ui.matchWrap());
        bottom.addView(Ui.textButton(this, "重新安装 Codex CLI", v -> rt.startInstall()), Ui.matchWrap());
    }

    @SuppressWarnings("unused")
    private static String newId() {
        return UUID.randomUUID().toString();
    }

    @SuppressWarnings("unused")
    private static File defaultSharedRoot() {
        return Environment.getExternalStorageDirectory();
    }
}
