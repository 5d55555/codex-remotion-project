package com.pocketcodex.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.pocketcodex.core.Installer;
import com.pocketcodex.core.Json;
import com.pocketcodex.core.ToolBundles;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 设置页、文件页（v0.3 按 ChatGPT 手机端的设置页重做）：左上圆形返回键、居中标题；
 * 头像和账号；一组一组的大圆角卡片（图标 + 名称 + 右边的值 + “›”或“⌄”），下拉卡片、黑色开关；底部浮着“搜索设置”。
 * 会话列表不在这里了：在对话页左边拉出的抽屉里。
 */
public final class MainActivity extends AppCompatActivity implements CodexRuntimeManager.Listener {
    static final int TAB_SESSIONS = 1, TAB_FILES = 2, TAB_SETTINGS = 3;
    static final String EXTRA_TAB = "tab", EXTRA_SECTION = "section";

    private CodexRuntimeManager rt;
    private int tab = TAB_SETTINGS;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView titleView, subtitleView;
    private ImageView actionButton;
    private EditText searchBox;
    private String query = "";
    // 设置
    private String settingsSig = "";
    private LinearLayout toolkitHost, profileBox;
    private final List<Group> groups = new ArrayList<>();
    // 文件
    private File filesDir;
    private boolean showHidden;
    private LinearLayout crumbs;
    private Group filesGroup;
    private TextView filesEmpty;

    /** 一张卡片和它的标题、行（搜索时按行过滤）。 */
    private static final class Group {
        View title;
        LinearLayout card;
        final List<View> rows = new ArrayList<>();
        final List<View> dividers = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rt = CodexRuntimeManager.get(this);
        tab = b != null ? b.getInt("tab", TAB_SETTINGS) : getIntent().getIntExtra(EXTRA_TAB, TAB_SETTINGS);
        if (tab == TAB_SESSIONS) { // 会话列表在对话页的抽屉里
            startActivity(new Intent(this, ChatActivity.class).putExtra(ChatActivity.EXTRA_OPEN_DRAWER, true));
            finish();
            return;
        }
        setContentView(buildShell());
        showTab();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        int t = i.getIntExtra(EXTRA_TAB, 0);
        if (t == TAB_FILES || t == TAB_SETTINGS) {
            tab = t;
            showTab();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onStart() {
        super.onStart();
        rt.addListener(this);
        if (rt.stage == CodexRuntimeManager.Stage.CHECKING || rt.stage == CodexRuntimeManager.Stage.ERROR && !rt.serverAlive()) rt.check();
        else if (rt.stage == CodexRuntimeManager.Stage.READY) rt.refreshRateLimits();
        onRuntimeChanged();
    }

    @Override
    protected void onStop() {
        rt.removeListener(this);
        super.onStop();
    }

    @Override
    public void onRuntimeChanged() {
        if (content == null) return;
        CodexRuntimeManager.Stage s = rt.stage;
        if (s == CodexRuntimeManager.Stage.NOT_INSTALLED || s == CodexRuntimeManager.Stage.NEED_LOGIN
                || s == CodexRuntimeManager.Stage.INSTALL_FAILED || rt.prefs.workspace() == null) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        if (tab == TAB_SETTINGS) {
            // 账号、版本等变了才整页重建；工具包安装进度只刷新那张卡片
            if (settingsSignature().equals(settingsSig)) bindToolkitCard();
            else buildSettings();
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    /* ---------------- 页面外壳 ---------------- */

    private View buildShell() {
        FrameLayout pageRoot = new FrameLayout(this);
        pageRoot.setFitsSystemWindows(true);
        pageRoot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.color(this, R.color.bg_low), Ui.color(this, R.color.bg_low), Ui.color(this, R.color.bg)}));
        LinearLayout col = Ui.vbox(this);
        pageRoot.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout bar = new FrameLayout(this);
        ImageView back = Ui.roundButton(this, R.drawable.ic_back_line, "返回", v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.START | Gravity.CENTER_VERTICAL);
        bl.leftMargin = dp(16);
        bar.addView(back, bl);
        LinearLayout tb = Ui.vbox(this);
        tb.setGravity(Gravity.CENTER);
        titleView = Overlay.strong(this, "", 17);
        titleView.setGravity(Gravity.CENTER);
        tb.addView(titleView, Ui.matchWrap());
        subtitleView = Ui.text(this, "", 12, R.color.text3);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tb.addView(subtitleView, Ui.matchWrap());
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tl.leftMargin = tl.rightMargin = dp(80);
        bar.addView(tb, tl);
        actionButton = Ui.roundButton(this, R.drawable.ic_dots, "更多", null);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.END | Gravity.CENTER_VERTICAL);
        al.rightMargin = dp(16);
        bar.addView(actionButton, al);
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));
        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(4), 0, dp(110));
        content = Ui.vbox(this);
        scroll.addView(content);
        col.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        // 底部浮着的搜索框（ChatGPT 设置页那样）
        LinearLayout sb = Ui.hbox(this);
        GradientDrawable sbg = Ui.round(this, R.color.card, 0, 30);
        sbg.setStroke(Math.max(1, dp(1) / 2 + 1), Ui.color(this, R.color.border));
        sb.setBackground(sbg);
        Ui.elevate(sb, 10);
        sb.setPadding(dp(20), 0, dp(16), 0);
        sb.addView(Ui.icon(this, R.drawable.ic_search_line, R.color.text2, 24));
        searchBox = new EditText(this);
        searchBox.setBackground(null);
        searchBox.setSingleLine();
        searchBox.setTextSize(17);
        searchBox.setTextColor(Ui.color(this, R.color.text));
        searchBox.setHintTextColor(Ui.color(this, R.color.text3));
        searchBox.setPadding(dp(14), dp(12), dp(8), dp(12));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int c, int d) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                if (tab == TAB_SETTINGS) applySearch();
                else bindFiles();
            }
        });
        sb.addView(searchBox, Ui.weight1());
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60), Gravity.BOTTOM);
        sl.leftMargin = sl.rightMargin = dp(16);
        sl.bottomMargin = dp(18);
        pageRoot.addView(sb, sl);
        pageRoot.setFocusableInTouchMode(true);
        pageRoot.requestFocus();
        return pageRoot;
    }

    private void showTab() {
        query = "";
        searchBox.setText("");
        if (tab == TAB_FILES) {
            searchBox.setHint("搜索这个文件夹");
            buildFiles();
        } else {
            searchBox.setHint("搜索设置");
            buildSettings();
            String sec = getIntent().getStringExtra(EXTRA_SECTION);
            if ("devtools".equals(sec) && toolkitHost != null) scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, topIn(toolkitHost) - dp(64))));
        }
    }

    /** 视图在 ScrollView 内容里的纵坐标。 */
    private int topIn(View v) {
        int y = 0;
        for (View x = v; x != null && x != content; x = (View) x.getParent()) y += x.getTop();
        return y;
    }

    private Group newGroup(String title) {
        Group g = new Group();
        if (title != null) {
            g.title = Ui.groupTitle(this, title);
            content.addView(g.title);
        }
        g.card = Ui.group(this);
        content.addView(g.card, Ui.groupParams(this));
        groups.add(g);
        return g;
    }

    private void add(Group g, View row, String key) {
        if (!g.rows.isEmpty()) {
            View d = Ui.groupDivider(this, true);
            g.card.addView(d);
            g.dividers.add(d);
        }
        g.card.addView(row);
        g.rows.add(row);
        g.keys.add(key.toLowerCase(Locale.ROOT));
    }

    /** trailing：0 无，1 细线“›”，2 “⌄”。 */
    private LinearLayout row(Group g, int icon, String title, String sub, String value, int trailing, View.OnClickListener l) {
        LinearLayout r = Ui.groupRow(this, icon, title, sub, value, trailing == 1 ? 0 : trailing, l);
        if (trailing == 1) {
            ImageView ch = Ui.icon(this, R.drawable.ic_chev_right, R.color.text2, 22);
            LinearLayout.LayoutParams cl = (LinearLayout.LayoutParams) ch.getLayoutParams();
            cl.leftMargin = dp(8);
            r.addView(ch, cl);
        }
        r.setTag(R.id.tag_row_title, title);
        add(g, r, title + " " + (sub == null ? "" : sub) + " " + (value == null ? "" : value));
        return r;
    }

    private void applySearch() {
        boolean searching = !query.isEmpty();
        if (profileBox != null) profileBox.setVisibility(searching ? View.GONE : View.VISIBLE);
        for (Group g : groups) {
            int shown = 0;
            for (int i = 0; i < g.rows.size(); i++) {
                boolean ok = !searching || g.keys.get(i).contains(query);
                g.rows.get(i).setVisibility(ok ? View.VISIBLE : View.GONE);
                if (ok) shown++;
            }
            for (View d : g.dividers) d.setVisibility(searching ? View.GONE : View.VISIBLE);
            g.card.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
            if (g.title != null) g.title.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        }
    }

    /* ---------------- 设置 ---------------- */

    private String settingsSignature() {
        return rt.stage + "|" + rt.accountEmail + "|" + rt.accountPlan + "|" + rt.installedVersion + "|" + rt.models.size() + "|" + ModelPicker.summary(rt)
                + "|" + rt.prefs.mode() + "|" + rt.prefs.workspace() + "|" + (rt.lastRateLimits == null ? 0 : rt.lastRateLimits.hashCode())
                + "|" + rt.prefs.theme() + "|" + rt.prefs.accent() + "|" + rt.prefs.userCa() + "|" + rt.prefs.toolkitRepo();
    }

    private void buildSettings() {
        if (tab != TAB_SETTINGS) return;
        titleView.setText("设置");
        subtitleView.setVisibility(View.GONE);
        actionButton.setVisibility(View.GONE);
        final int oldY = scroll.getScrollY();
        settingsSig = settingsSignature();
        content.removeAllViews();
        groups.clear();

        // 头像 + 账号
        profileBox = Ui.vbox(this);
        profileBox.setGravity(Gravity.CENTER_HORIZONTAL);
        profileBox.setPadding(0, dp(4), 0, dp(14));
        String email = rt.accountEmail;
        String name = email == null ? (rt.accountType == null ? "正在确认…" : "apiKey".equals(rt.accountType) ? "API 密钥" : rt.accountType) : email.replaceAll("@.*", "");
        TextView avatar = Ui.text(this, initials(name), 32, R.color.text);
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.circleColor(0xFFB08D57));
        profileBox.addView(avatar, new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView nm = Overlay.strong(this, name, 21);
        nm.setPadding(0, dp(12), 0, 0);
        profileBox.addView(nm, Ui.wrap());
        if (rt.accountPlan != null) profileBox.addView(Ui.text(this, planName(rt.accountPlan) + " · ChatGPT 账号", 14, R.color.text3), Ui.wrap());
        content.addView(profileBox, Ui.matchWrap());

        Group acct = newGroup("账户");
        row(acct, R.drawable.ic_user_line, "账号", null, email == null ? name : email, 0, null);
        row(acct, R.drawable.ic_card_line, "订阅", null, rt.accountPlan == null ? "—" : planName(rt.accountPlan), 0, null);
        row(acct, R.drawable.ic_chart_line, "用量与限额", null, usageSummary(), 1, v -> {
            rt.refreshRateLimits();
            UsageDialog.show(this, rt, null);
        });
        row(acct, R.drawable.ic_logout_line, "退出登录", null, null, 1, v -> new AlertDialog.Builder(this).setTitle("退出登录？")
                .setMessage("退出后保留本地文件和会话记录，下次需要重新用设备码登录。")
                .setPositiveButton("退出登录", (d, w) -> rt.logout()).setNegativeButton("取消", null).show());

        Group look = newGroup(null);
        String[] themes = {"系统", "浅色", "深色"};
        LinearLayout tr = row(look, R.drawable.ic_sun_line, "外观", null, themes[rt.prefs.theme()], 2, null);
        tr.setOnClickListener(v -> {
            List<Overlay.Opt> o = new ArrayList<>();
            for (int i = 0; i < themes.length; i++) {
                final int w = i;
                o.add(new Overlay.Opt(themes[i], null, rt.prefs.theme() == i, () -> {
                    rt.prefs.setTheme(w);
                    App.applyTheme(w);
                }));
            }
            Overlay.dropdown(this, tr, o, true, (ImageView) tr.findViewWithTag("chevron"), this::buildSettings);
        });
        LinearLayout ar = row(look, R.drawable.ic_brush_line, "强调色", null, Ui.ACCENT_NAMES[rt.prefs.accent()], 2, null);
        View dot = new View(this);
        dot.setBackground(Ui.circleColor(Ui.accent(this)));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(16), dp(16));
        dl.leftMargin = dp(8);
        ar.addView(dot, ar.getChildCount() - 2, dl);
        ar.setOnClickListener(v -> {
            List<Overlay.Opt> o = new ArrayList<>();
            for (int i = 0; i < Ui.ACCENT_NAMES.length; i++) {
                final int w = i;
                o.add(new Overlay.Opt(Ui.ACCENT_NAMES[i], null, rt.prefs.accent() == i, () -> rt.prefs.setAccent(w)));
            }
            Overlay.dropdown(this, ar, o, true, (ImageView) ar.findViewWithTag("chevron"), this::buildSettings);
        });

        Group codex = newGroup("Codex");
        row(codex, R.drawable.ic_codex_line, "模型、推理与速度", null, rt.models.isEmpty() ? "启动后可选" : ModelPicker.summary(rt), 1,
                v -> ModelPicker.show(this, rt, false, this::buildSettings));
        boolean auto = Prefs.MODE_AUTO.equals(rt.prefs.mode());
        LinearLayout pr = row(codex, R.drawable.ic_shield_line, "权限", auto ? "命令和改文件不再逐项确认" : "运行命令、修改文件前先问你", auto ? "完全自动" : "每步确认", 2, null);
        pr.setOnClickListener(v -> Overlay.dropdown(this, pr, Arrays.asList(
                new Overlay.Opt("每步确认", "运行命令、修改文件前先问你", !Prefs.MODE_AUTO.equals(rt.prefs.mode()), () -> {
                    rt.prefs.setMode(Prefs.MODE_CONFIRM);
                    buildSettings();
                }),
                new Overlay.Opt("完全自动", "不再逐项确认，已发生的改动不会自动撤销", Prefs.MODE_AUTO.equals(rt.prefs.mode()), this::confirmAuto)),
                true, (ImageView) pr.findViewWithTag("chevron"), null));
        String ws = rt.prefs.workspace();
        row(codex, R.drawable.ic_box_line, "工作区", Workspaces.isShared(rt, ws) ? Format.prettyPath(rt, ws) : "应用内", Workspaces.name(ws), 1, v -> chooseWorkspace());
        row(codex, R.drawable.ic_refresh_line, "重启 Codex", "加了 MCP 服务或技能后用", null, 1, v -> {
            if (rt.restartCodex()) Toast.makeText(this, "正在重启 Codex…", Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, "有任务正在运行，结束后再重启", Toast.LENGTH_LONG).show();
        });

        // 开发工具
        Group dev = newGroup("开发工具（Node.js · Git · Python）");
        toolkitHost = Ui.vbox(this);
        dev.card.addView(toolkitHost);
        dev.rows.add(toolkitHost);
        dev.keys.add("开发工具 node git python 工具包 安装");
        bindToolkitCard();
        for (ToolBundles.Bundle tb : ToolBundles.ALL) {
            final ToolBundles.Bundle fb = tb;
            LinearLayout br = row(dev, bundleIcon(tb.id), tb.title, tb.summary + " · 下载约 " + tb.downloadMb + " MB", bundleState(tb), 0, v -> installBundle(fb));
            br.setTag(R.id.tag_bundle, tb.id);
        }
        bindBundleRows();
        row(dev, R.drawable.ic_terminal_line, "终端", "直接运行 node、npm、git、pkg；不经过模型，不消耗额度", null, 1, v -> startActivity(new Intent(this, TerminalActivity.class)));
        row(dev, R.drawable.ic_code_line, "常用命令", "Node.js、npm、Git、Python、pkg 的常用写法", null, 1,
                v -> startActivity(new Intent(this, TerminalActivity.class).putExtra(TerminalActivity.EXTRA_COMMANDS, true)));
        LinearLayout repo = row(dev, R.drawable.ic_globe_line, "软件源", null, toolkitRepoLabel(), 2, null);
        repo.setOnClickListener(v -> {
            List<Overlay.Opt> o = new ArrayList<>();
            o.add(new Overlay.Opt("自动", "官方和国内镜像，哪个能连上用哪个", rt.prefs.toolkitRepo() == null, () -> rt.prefs.setToolkitRepo(null)));
            for (com.pocketcodex.core.Toolkit.Repo r : com.pocketcodex.core.Toolkit.REPOS) {
                o.add(new Overlay.Opt(r.label, null, r.id.equals(rt.prefs.toolkitRepo()), () -> rt.prefs.setToolkitRepo(r.id)));
            }
            Overlay.dropdown(this, repo, o, true, (ImageView) repo.findViewWithTag("chevron"), this::buildSettings);
        });

        Group gen = newGroup("通用");
        row(gen, R.drawable.ic_bell_line, "通知", "审批提醒、任务完成与失败提醒", null, 1, v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        });
        row(gen, R.drawable.ic_battery_line, "电池限制", "任务被系统中断时在这里放宽", null, 1, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
        });
        row(gen, R.drawable.ic_storage_line, "存储权限", "选手机存储里的文件夹做工作区时需要", null, 1,
                v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        LinearLayout ca = row(gen, R.drawable.ic_lock_line, "信任用户安装的证书", "公司网络或抓包工具需要时再打开", null, 0, null);
        com.google.android.material.materialswitch.MaterialSwitch sw = Ui.toggle(this, rt.prefs.userCa(), (b, on) -> {
            if (on == rt.prefs.userCa()) return;
            rt.prefs.setUserCa(on);
            rt.invalidateCerts();
            Toast.makeText(this, "下次启动 Codex 时生效", Toast.LENGTH_SHORT).show();
        });
        ca.addView(sw);
        ca.setOnClickListener(v -> sw.toggle());
        Ui.ripple(ca, false);
        row(gen, R.drawable.ic_pulse_line, "自检与诊断", "检查网络、证书、本机执行和开发工具", null, 1, v -> startActivity(new Intent(this, DiagnosticsActivity.class)));

        Group cli = newGroup("Codex CLI");
        row(cli, R.drawable.ic_codex_line, "当前版本", Installer.TESTED_VERSION.equals(rt.installedVersion) ? "已验证版本" : "未验证版本",
                rt.installedVersion == null ? "未安装" : rt.installedVersion, 0, null);
        LinearLayout src = row(cli, R.drawable.ic_download, "下载来源", null, "npmmirror".equals(rt.prefs.source()) ? "国内镜像" : "npm 官方", 2, null);
        src.setOnClickListener(v -> Overlay.dropdown(this, src, Arrays.asList(
                new Overlay.Opt("npm 官方", "registry.npmjs.org", !"npmmirror".equals(rt.prefs.source()), () -> rt.prefs.setSource("npm")),
                new Overlay.Opt("国内镜像", "npmmirror，第三方镜像，安装包会和官方摘要比对", "npmmirror".equals(rt.prefs.source()), () -> rt.prefs.setSource("npmmirror"))),
                true, (ImageView) src.findViewWithTag("chevron"), this::buildSettings));
        row(cli, R.drawable.ic_refresh_line, "检查更新", "查询 npm 上的最新版本，不会自动启用", null, 1, v -> checkUpdate());
        row(cli, R.drawable.ic_download, "重新安装", "重新下载并校验当前版本，不删账号、历史和工作区", null, 1, v -> new AlertDialog.Builder(this).setTitle("重新安装 Codex CLI？")
                .setMessage("会重新下载约 164 MB。").setPositiveButton("重新安装", (d, w) -> {
                    rt.prefs.setTargetVersion(rt.installedVersion == null ? Installer.TESTED_VERSION : rt.installedVersion);
                    rt.reinstall();
                    startActivity(new Intent(this, SetupActivity.class));
                }).setNegativeButton("取消", null).show());

        Group about = newGroup("关于");
        row(about, R.drawable.ic_shield_line, "本地执行与隐私", "文件和命令在本机运行；对话会发送给模型服务", null, 1, v -> new AlertDialog.Builder(this).setTitle("本地执行与隐私")
                .setMessage("• Codex CLI 安装在本应用的私有目录，命令和文件改动都在这台手机上执行。\n"
                        + "• 模型请求需要联网，你的消息和 Agent 读取的文件内容会发送给 OpenAI 的模型服务。\n"
                        + "• Android 的应用沙箱限制本应用（和它运行的命令）只能访问自己的目录和你授权的存储。工作区只是默认位置，不是独立隔离区。\n"
                        + "• 登录凭据保存在本应用私有目录，不会写进日志或诊断结果。")
                .setPositiveButton("知道了", null).show());
        row(about, R.drawable.ic_info, "应用版本", "独立开发的客户端，与 OpenAI 无官方隶属关系", "口袋工坊 " + BuildConfig.VERSION_NAME, 0, null);
        row(about, R.drawable.ic_book, "开源许可", "Codex CLI、Termux 软件包、AndroidX、Material、Markwon", null, 1, v -> new AlertDialog.Builder(this).setTitle("开源许可")
                .setMessage("Codex CLI（Apache-2.0）\nTermux 软件包（各包自己的许可证，用 pkg show 查看）\nAndroidX、Material Components（Apache-2.0）\nMarkwon、commonmark-java（Apache-2.0 / BSD）")
                .setPositiveButton("好", null).show());

        applySearch();
        if (oldY > 0) scroll.post(() -> scroll.scrollTo(0, oldY));
    }

    private static String initials(String s) {
        String t = s == null ? "" : s.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5]", "");
        if (t.isEmpty()) return "PC";
        return t.length() >= 2 ? t.substring(0, 2).toUpperCase(Locale.ROOT) : t.toUpperCase(Locale.ROOT);
    }

    private static String planName(String p) {
        if (p == null) return "—";
        switch (p.toLowerCase(Locale.ROOT)) {
            case "plus":
                return "Plus";
            case "pro":
                return "Pro";
            case "free":
                return "免费版";
            case "team":
                return "Team";
            case "business":
                return "Business";
            case "enterprise":
                return "Enterprise";
            default:
                return p;
        }
    }

    private void confirmAuto() {
        new AlertDialog.Builder(this).setTitle("开启完全自动？")
                .setMessage("助手将直接运行命令和修改文件，不再逐项确认。它能访问本应用有权限访问的位置；工作区不是独立隔离区。\n你仍可随时停止，但已经发生的改动不会自动撤销。")
                .setPositiveButton("开启完全自动", (d, w) -> {
                    rt.prefs.setMode(Prefs.MODE_AUTO);
                    buildSettings();
                }).setNegativeButton("保持每步确认", null).show();
    }

    private static int bundleIcon(String id) {
        switch (id) {
            case "recommended":
                return R.drawable.ic_sparkle;
            case "code":
                return R.drawable.ic_code_line;
            case "apk":
                return R.drawable.ic_android_line;
            case "reverse":
                return R.drawable.ic_review;
            case "media":
                return R.drawable.ic_film_line;
            case "cjk":
                return R.drawable.ic_lang_line;
            case "native":
                return R.drawable.ic_terminal_line;
            case "gradle":
                return R.drawable.ic_box_line;
            default:
                return R.drawable.ic_globe_line;
        }
    }

    private String bundleState(ToolBundles.Bundle b) {
        if (b.id.equals(rt.bundleInstalling)) return "正在安装";
        return rt.bundleInstalled(b.id) ? "已安装" : "安装";
    }

    /** 工具集那几行右边的状态（安装中 / 已安装 / 安装）。 */
    private void bindBundleRows() {
        for (Group g : groups) {
            for (View row : g.rows) {
                Object id = row.getTag(R.id.tag_bundle);
                if (!(id instanceof String)) continue;
                TextView val = row.findViewWithTag("value");
                ToolBundles.Bundle b = ToolBundles.find((String) id);
                if (val == null || b == null) continue;
                String st = bundleState(b);
                val.setText(st);
                val.setTextColor(st.equals("安装") ? Ui.accent(this) : Ui.color(this, R.color.text3));
            }
        }
    }

    private void installBundle(ToolBundles.Bundle b) {
        if (rt.paths.toolkit() == null) {
            Toast.makeText(this, "这台设备的处理器不支持开发工具包", Toast.LENGTH_LONG).show();
            return;
        }
        if (rt.toolkitState == CodexRuntimeManager.ToolkitState.INSTALLING) {
            Toast.makeText(this, "正在安装，等这一项装完再装下一组", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean have = rt.bundleInstalled(b.id);
        new AlertDialog.Builder(this).setTitle((have ? "更新“" : "安装“") + b.title + "”？")
                .setMessage(b.summary + "\n\n下载约 " + b.downloadMb + " MB，安装后占用约 " + b.installedMb + " MB（和已装的部分共用依赖，实际可能更少）。"
                        + "来自 Termux 官方仓库，签名和每个包的 SHA256 都会校验。" + (rt.toolkitUsable() ? "" : "\n基础工具包还没装好，会先装基础包（约 62 MB）。")
                        + "\n\n包：" + String.join(" ", b.packages))
                .setPositiveButton(have ? "检查并更新" : "安装", (d, w) -> {
                    rt.startBundleInstall(b.id);
                    bindToolkitCard();
                }).setNegativeButton("取消", null).show();
    }

    /** 开发工具卡片：未安装 / 安装中（进度）/ 已就绪（版本）/ 自检失败 / 安装失败。 */
    private void bindToolkitCard() {
        if (toolkitHost == null) return;
        toolkitHost.removeAllViews();
        LinearLayout card = Ui.vbox(this);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        CodexRuntimeManager.ToolkitState st = rt.toolkitState;
        if (rt.paths.toolkit() == null) st = CodexRuntimeManager.ToolkitState.FAILED;
        String head, body;
        int icon, tint;
        switch (st) {
            case INSTALLING:
                ToolBundles.Bundle ib = rt.bundleInstalling == null ? null : ToolBundles.find(rt.bundleInstalling);
                head = ib == null ? "正在安装开发工具" : "正在安装“" + ib.title + "”";
                body = rt.toolkitStep == null ? "准备中…" : rt.toolkitStep;
                icon = R.drawable.ic_download;
                tint = R.color.text;
                break;
            case READY:
                head = "开发工具已就绪";
                StringBuilder v = new StringBuilder();
                String[][] names = {{"node", "Node.js"}, {"npm", "npm"}, {"git", "Git"}, {"python3", "Python"}, {"pip", "pip"}, {"bash", "bash"}, {"rg", "ripgrep"},
                        {"curl", "curl"}, {"java", "Java"}, {"ffmpeg", "FFmpeg"}, {"adb", "adb"}, {"gh", "gh"}, {"r2", "radare2"}, {"magick", "ImageMagick"}};
                for (String[] n : names) {
                    String ver = rt.toolkitVersions.get(n[0]);
                    if (ver == null || ver.equals("不可用")) continue;
                    if (v.length() > 0) v.append("　");
                    v.append(n[1]).append(' ').append(ver);
                }
                body = (v.length() == 0 ? "" : v + "\n") + "Codex 和终端都能用；缺的软件用下面的工具集一组一组装，或让 Codex 用 pkg install 装。"
                        + (rt.toolkitError != null ? "\n上一次：" + rt.toolkitError : "");
                icon = R.drawable.ic_check;
                tint = R.color.success;
                break;
            case BROKEN:
                head = "装好了，但在这台手机上自检没通过";
                body = (rt.toolkitError == null ? "" : rt.toolkitError + "\n") + "可以重新安装，或到 自检与诊断 里查看详情。";
                icon = R.drawable.ic_warning;
                tint = R.color.warning;
                break;
            case FAILED:
                head = rt.paths.toolkit() == null ? "这台设备不支持开发工具包" : "安装没有完成";
                body = rt.toolkitError == null ? "可以换一个软件源再试。" : rt.toolkitError;
                icon = R.drawable.ic_warning;
                tint = R.color.error;
                break;
            default:
                head = "还没安装";
                body = "装好后 Codex 就能用 Node.js、npm/npx、Git、Python/pip、bash、rg、curl、jq 等命令。下载约 62 MB，来自 Termux 官方仓库（签名校验）。";
                icon = R.drawable.ic_download;
                tint = R.color.text;
        }
        LinearLayout r = Ui.hbox(this);
        r.addView(Ui.icon(this, icon, tint, 24));
        TextView t = Overlay.strong(this, head, 17);
        LinearLayout.LayoutParams tl = Ui.weight1();
        tl.leftMargin = dp(16);
        r.addView(t, tl);
        card.addView(r);
        TextView b = Ui.text(this, body, 14, R.color.text2);
        b.setLineSpacing(0, 1.2f);
        card.addView(b, Ui.matchWrap(this, 8));
        if (st == CodexRuntimeManager.ToolkitState.INSTALLING) {
            LinearProgressIndicator pi = new LinearProgressIndicator(this);
            pi.setTrackStopIndicatorSize(0);
            pi.setIndicatorColor(Ui.accent(this));
            pi.setTrackColor(Ui.color(this, R.color.border));
            pi.setTrackCornerRadius(dp(2));
            if (rt.toolkitTotal > 0) {
                pi.setMax(1000);
                pi.setProgressCompat((int) (rt.toolkitDone * 1000 / rt.toolkitTotal), false);
            } else {
                pi.setIndeterminate(true);
            }
            card.addView(pi, Ui.matchWrap(this, 14));
            MaterialButton c = Ui.outlined(this, "取消", v -> rt.cancelToolkitInstall());
            c.setTextColor(Ui.color(this, R.color.text));
            LinearLayout.LayoutParams bl = Ui.wrap();
            bl.topMargin = dp(12);
            card.addView(c, bl);
        } else if (rt.paths.toolkit() != null && st != CodexRuntimeManager.ToolkitState.READY) {
            MaterialButton go = Ui.primary(this, st == CodexRuntimeManager.ToolkitState.NONE ? "安装开发工具" : "重试", v -> rt.startToolkitInstall());
            LinearLayout.LayoutParams bl = Ui.wrap();
            bl.topMargin = dp(14);
            card.addView(go, bl);
        } else if (st == CodexRuntimeManager.ToolkitState.READY) {
            MaterialButton re = Ui.outlined(this, "重新安装基础包", v -> new AlertDialog.Builder(this).setTitle("重新安装开发工具？")
                    .setMessage("会重新下载并校验基础软件包；已经装的工具集会一起重装。").setPositiveButton("重新安装", (d, w) -> rt.startToolkitInstall())
                    .setNegativeButton("取消", null).show());
            re.setTextColor(Ui.color(this, R.color.text));
            LinearLayout.LayoutParams bl = Ui.wrap();
            bl.topMargin = dp(12);
            card.addView(re, bl);
        }
        toolkitHost.addView(card, Ui.matchWrap());
        bindBundleRows();
    }

    private String toolkitRepoLabel() {
        String id = rt.prefs.toolkitRepo();
        if (id != null) for (com.pocketcodex.core.Toolkit.Repo r : com.pocketcodex.core.Toolkit.REPOS) if (r.id.equals(id)) return r.label;
        return "自动";
    }

    private String usageSummary() {
        Map<String, Object> r = rt.lastRateLimits;
        if (r == null) return "点开查看";
        Map<String, Object> p = Json.obj(Json.obj(r, "rateLimits"), "primary");
        if (p.isEmpty()) return "没有数据";
        return "5 小时已用 " + Json.num(p, "usedPercent", 0) + "%";
    }

    private void checkUpdate() {
        Toast.makeText(this, "正在查询…", Toast.LENGTH_SHORT).show();
        rt.checkLatest((latest, err) -> {
            if (err != null) {
                Toast.makeText(this, "查询失败：" + err, Toast.LENGTH_LONG).show();
                return;
            }
            if (latest.equals(rt.installedVersion)) {
                new AlertDialog.Builder(this).setMessage("已经是最新版本 " + latest + "。").setPositiveButton("好", null).show();
                return;
            }
            new AlertDialog.Builder(this).setTitle("有新版本 " + latest)
                    .setMessage(String.format(Locale.ROOT, "当前 %s（%s）。\n\n新版本没有和本应用一起测试过，app-server 协议可能有变化。装好后会先自检，失败不会启用；上一版本会保留，可以退回。",
                            rt.installedVersion, Installer.TESTED_VERSION.equals(rt.installedVersion) ? "已验证" : "未验证"))
                    .setPositiveButton("安装 " + latest, (d, w) -> {
                        rt.prefs.setTargetVersion(latest);
                        rt.reinstall();
                        startActivity(new Intent(this, SetupActivity.class));
                    }).setNegativeButton("以后再说", null).show();
        });
    }

    private void chooseWorkspace() {
        final List<String> l = Workspaces.list(this);
        String cur = rt.prefs.workspace();
        if (cur != null && !l.contains(cur)) l.add(0, cur);
        List<String> labels = new ArrayList<>();
        for (String p : l) labels.add(Workspaces.name(p) + "\n" + (Workspaces.isShared(rt, p) ? Format.prettyPath(rt, p) : "应用内"));
        labels.add("＋ 新建或添加工作区");
        new AlertDialog.Builder(this).setTitle("切换工作区")
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    if (w == l.size()) {
                        rt.prefs.setWorkspace(null);
                        startActivity(new Intent(this, SetupActivity.class));
                        finish();
                        return;
                    }
                    String p = l.get(w);
                    if (!new File(p).isDirectory()) {
                        Toast.makeText(this, "这个工作区已移动或删除", Toast.LENGTH_LONG).show();
                        Workspaces.forget(this, p);
                        return;
                    }
                    rt.prefs.setWorkspace(p);
                    Workspaces.remember(this, p);
                    filesDir = null;
                    showTab();
                }).show();
    }

    /* ---------------- 文件 ---------------- */

    private void buildFiles() {
        String ws = rt.prefs.workspace();
        File root = ws == null ? null : new File(ws);
        if (filesDir == null || root == null || !filesDir.getAbsolutePath().startsWith(root.getAbsolutePath())) filesDir = root;
        titleView.setText("文件");
        subtitleView.setVisibility(View.VISIBLE);
        subtitleView.setText(Workspaces.name(ws) + " · " + (Workspaces.isShared(rt, ws) ? "手机存储" : "应用内"));
        actionButton.setVisibility(View.VISIBLE);
        actionButton.setOnClickListener(this::filesMenu);
        content.removeAllViews();
        groups.clear();
        profileBox = null;
        toolkitHost = null;
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(dp(16), dp(4), dp(16), dp(12));
        crumbs = Ui.hbox(this);
        hs.addView(crumbs);
        content.addView(hs, Ui.matchWrap());
        filesGroup = new Group();
        filesGroup.card = Ui.group(this);
        content.addView(filesGroup.card, Ui.groupParams(this));
        filesEmpty = Ui.text(this, "", 15, R.color.text3);
        filesEmpty.setGravity(Gravity.CENTER);
        filesEmpty.setPadding(dp(32), dp(36), dp(32), dp(36));
        content.addView(filesEmpty, Ui.matchWrap());
        bindFiles();
    }

    private void bindFiles() {
        if (filesGroup == null || tab != TAB_FILES) return;
        String ws = rt.prefs.workspace();
        File root = ws == null ? null : new File(ws);
        crumbs.removeAllViews();
        filesGroup.card.removeAllViews();
        if (root == null || filesDir == null) return;
        List<File> chain = new ArrayList<>();
        for (File f = filesDir; f != null; f = f.getParentFile()) {
            chain.add(0, f);
            if (f.equals(root)) break;
        }
        for (int i = 0; i < chain.size(); i++) {
            final File f = chain.get(i);
            boolean last = i == chain.size() - 1;
            if (i > 0) {
                TextView sep = Ui.text(this, "›", 16, R.color.text3);
                LinearLayout.LayoutParams sl = Ui.wrap();
                sl.rightMargin = dp(6);
                crumbs.addView(sep, sl);
            }
            TextView c = Ui.text(this, i == 0 ? Workspaces.name(ws) : f.getName(), 14, last ? R.color.text : R.color.text2);
            c.setBackground(Ui.round(this, last ? R.color.card : R.color.surface2, last ? R.color.border : 0, 16));
            c.setPadding(dp(14), dp(7), dp(14), dp(7));
            Ui.ripple(c, false);
            c.setOnClickListener(v -> {
                filesDir = f;
                bindFiles();
            });
            LinearLayout.LayoutParams lp = Ui.wrap();
            lp.rightMargin = dp(6);
            crumbs.addView(c, lp);
        }
        File[] list = filesDir.listFiles();
        List<File> files = new ArrayList<>();
        if (list != null) for (File f : list) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            if (!query.isEmpty() && !f.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            files.add(f);
        }
        java.util.Collections.sort(files, (a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName()));
        int shown = 0;
        for (File f : files) {
            if (shown >= 400) {
                TextView more = Ui.text(this, "还有 " + (files.size() - shown) + " 项，用下面的搜索找", 14, R.color.text3);
                more.setPadding(dp(24), dp(14), dp(24), dp(14));
                filesGroup.card.addView(more);
                break;
            }
            if (shown > 0) filesGroup.card.addView(Ui.groupDivider(this, true));
            String size = f.isDirectory() ? countLabel(f) : f.length() < 1024 * 100 ? (f.length() < 1024 ? f.length() + " B" : (f.length() / 1024) + " KB") : Format.mb(f.length());
            String sub = size + " · " + Format.ago(f.lastModified() / 1000);
            LinearLayout r = Ui.groupRow(this, f.isDirectory() ? R.drawable.ic_folder_line : R.drawable.ic_file_line, f.getName(), sub, null, 0, v -> openFile(f));
            if (f.isDirectory()) r.addView(Ui.icon(this, R.drawable.ic_chev_right, R.color.text2, 22));
            r.setOnLongClickListener(v -> {
                fileMenu(r, f);
                return true;
            });
            filesGroup.card.addView(r);
            shown++;
        }
        filesGroup.card.setVisibility(shown == 0 ? View.GONE : View.VISIBLE);
        filesEmpty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        filesEmpty.setText(!query.isEmpty() ? "这个文件夹里没有“" + query + "”" : "这个文件夹还是空的。去对话里让 Codex 创建文件吧。");
    }

    private void openFile(File f) {
        if (FileViewerActivity.sensitive(this, f)) { // 指向运行时目录（凭据）的链接：不进
            Toast.makeText(this, "这个位置属于应用的运行目录，不能在这里打开", Toast.LENGTH_LONG).show();
            return;
        }
        if (f.isDirectory()) {
            filesDir = f;
            searchBox.setText("");
            bindFiles();
            scroll.scrollTo(0, 0);
        } else {
            startActivity(new Intent(this, FileViewerActivity.class).putExtra(FileViewerActivity.EXTRA_PATH, f.getAbsolutePath()));
        }
    }

    private static String countLabel(File d) {
        String[] n = d.list();
        return n == null ? "文件夹" : n.length + " 项";
    }

    private void filesMenu(View anchor) {
        Overlay.dropdown(this, anchor, Arrays.asList(
                new Overlay.Opt(showHidden ? "不显示隐藏文件" : "显示隐藏文件", null, false, () -> {
                    showHidden = !showHidden;
                    bindFiles();
                }),
                new Overlay.Opt("新建文件夹", null, false, this::newFolder),
                new Overlay.Opt("复制当前路径", null, false, () -> copy("路径", filesDir.getAbsolutePath())),
                new Overlay.Opt("在终端打开", null, false, () -> startActivity(new Intent(this, TerminalActivity.class).putExtra(TerminalActivity.EXTRA_CWD, filesDir.getAbsolutePath()))),
                new Overlay.Opt("切换工作区", null, false, this::chooseWorkspace),
                new Overlay.Opt("刷新", null, false, this::bindFiles)), true, null, null);
    }

    private void newFolder() {
        final EditText et = new EditText(this);
        et.setSingleLine();
        LinearLayout box = Ui.vbox(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(et);
        new AlertDialog.Builder(this).setTitle("新建文件夹").setView(box).setPositiveButton("创建", (d2, w2) -> {
            String n = et.getText().toString().trim().replaceAll("[/\\\\]", "_");
            if (!n.isEmpty() && new File(filesDir, n).mkdirs()) bindFiles();
        }).setNegativeButton("取消", null).show();
    }

    private void fileMenu(View anchor, File f) {
        List<Overlay.Opt> o = new ArrayList<>();
        o.add(new Overlay.Opt("复制路径", null, false, () -> copy("路径", f.getAbsolutePath())));
        o.add(new Overlay.Opt("引用到新对话", "让 Codex 看这个" + (f.isDirectory() ? "文件夹" : "文件"), false, () -> startActivity(new Intent(this, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_CWD, rt.prefs.workspace()).putExtra(ChatActivity.EXTRA_DRAFT, "请看一下这个" + (f.isDirectory() ? "文件夹" : "文件") + "：" + f.getAbsolutePath() + "\n"))));
        if (f.isDirectory()) o.add(new Overlay.Opt("在终端打开", null, false, () -> startActivity(new Intent(this, TerminalActivity.class).putExtra(TerminalActivity.EXTRA_CWD, f.getAbsolutePath()))));
        Overlay.dropdown(this, anchor, o, true, null, null);
    }

    private void copy(String label, String s) {
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(label, s));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }
}
