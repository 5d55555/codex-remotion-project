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
import androidx.activity.OnBackPressedCallback;
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
 * 设置页、文件页（按 ChatGPT 手机端的设置页）：
 * <ul>
 * <li>顶栏浮在内容上面，内容滚上去是渐隐的（底部搜索框下面也是），不是一刀切；</li>
 * <li>设置首页只有几个入口（头像、账户、外观、我的口袋工坊、通用……），具体选项在点进去的子页面里；</li>
 * <li>首页的“搜索设置”能搜到所有子页面里的项。</li>
 * </ul>
 * 会话列表不在这里：在对话页左边拉出的抽屉里。
 */
public final class MainActivity extends AppCompatActivity implements CodexRuntimeManager.Listener {
    static final int TAB_SESSIONS = 1, TAB_FILES = 2, TAB_SETTINGS = 3;
    static final String EXTRA_TAB = "tab", EXTRA_SECTION = "section";
    static final String P_ROOT = "root", P_DEV = "devtools", P_SKILLS = "skills", P_GENERAL = "general", P_CLI = "cli", P_ABOUT = "about";

    private CodexRuntimeManager rt;
    private int tab = TAB_SETTINGS;
    private String page = P_ROOT;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView titleView, subtitleView;
    private ImageView actionButton;
    private View searchBar, bottomFade;
    private EditText searchBox;
    private String query = "";
    private int rootScrollY;
    private OnBackPressedCallback pageBack;
    // 设置
    private String settingsSig = "";
    private LinearLayout toolkitHost;
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
        if (b == null && "devtools".equals(getIntent().getStringExtra(EXTRA_SECTION))) page = P_DEV;
        String savedQuery = "";
        int savedY = 0;
        if (b != null) { // 旋转、切换深色模式后回到原来的位置
            page = b.getString("page", P_ROOT);
            savedQuery = b.getString("query", "");
            savedY = b.getInt("scrollY", 0);
            rootScrollY = b.getInt("rootScrollY", 0);
            showHidden = b.getBoolean("hidden", false);
            String fd = b.getString("filesDir");
            if (fd != null) filesDir = new File(fd);
        }
        pageBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                openPage(P_ROOT);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, pageBack);
        setContentView(buildShell());
        query = savedQuery;
        if (!query.isEmpty()) searchBox.setText(query); // 触发重建
        showTab();
        final int y = savedY;
        if (y > 0) scroll.post(() -> scroll.scrollTo(0, y));
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        int t = i.getIntExtra(EXTRA_TAB, 0);
        if (t == TAB_FILES || t == TAB_SETTINGS) {
            tab = t;
            page = "devtools".equals(i.getStringExtra(EXTRA_SECTION)) ? P_DEV : P_ROOT;
            query = "";
            searchBox.setText("");
            showTab();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putString("page", page);
        out.putString("query", query);
        if (scroll != null) out.putInt("scrollY", scroll.getScrollY());
        out.putInt("rootScrollY", rootScrollY);
        out.putBoolean("hidden", showHidden);
        if (filesDir != null) out.putString("filesDir", filesDir.getAbsolutePath());
    }

    @Override
    protected void onStart() {
        super.onStart();
        rt.addListener(this);
        if (rt.stage == CodexRuntimeManager.Stage.CHECKING || rt.stage == CodexRuntimeManager.Stage.ERROR && !rt.serverAlive()) rt.check();
        else if (rt.stage == CodexRuntimeManager.Stage.READY) {
            rt.refreshRateLimits();
            rt.loadCatalog();
        }
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
            // 账号、版本等变了才整页重建；工具包安装进度只刷新那张卡片和工具集那几行
            if (settingsSignature().equals(settingsSig)) bindToolkitCard();
            else buildSettings();
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    /* ---------------- 页面外壳 ---------------- */

    private View buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Ui.color(this, R.color.bg_low));
        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(0, dp(84), 0, dp(120));
        content = Ui.vbox(this);
        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // 顶部：内容滚到顶栏下面时淡出（ChatGPT 的头像就是这样滑走的）
        View topFade = Ui.fade(this, R.color.bg_low, true);
        root.addView(topFade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104), Gravity.TOP));
        Ui.followScroll(topFade, scroll, true);
        FrameLayout bar = new FrameLayout(this);
        ImageView back = Ui.roundButton(this, R.drawable.ic_back_line, "返回", v -> {
            if (!P_ROOT.equals(page) && tab == TAB_SETTINGS) openPage(P_ROOT);
            else finish();
        });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.START | Gravity.CENTER_VERTICAL);
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
        tl.leftMargin = tl.rightMargin = dp(84);
        bar.addView(tb, tl);
        actionButton = Ui.roundButton(this, R.drawable.ic_dots, "更多", null);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.END | Gravity.CENTER_VERTICAL);
        al.rightMargin = dp(16);
        bar.addView(actionButton, al);
        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80), Gravity.TOP));
        // 底部：搜索框下面也淡出
        bottomFade = Ui.fade(this, R.color.bg_low, false);
        root.addView(bottomFade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120), Gravity.BOTTOM));
        Ui.followScroll(bottomFade, scroll, false);
        LinearLayout sb = Ui.hbox(this);
        GradientDrawable sbg = Ui.round(this, R.color.card, 0, 30);
        sbg.setStroke(Math.max(1, dp(1) / 2 + 1), Ui.color(this, R.color.border));
        sb.setBackground(sbg);
        Ui.elevate(sb, 8);
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
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                if (q.equals(query)) return;
                query = q;
                if (tab == TAB_SETTINGS) buildSettings();
                else bindFiles();
            }
        });
        sb.addView(searchBox, Ui.weight1());
        searchBar = sb;
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60), Gravity.BOTTOM);
        sl.leftMargin = sl.rightMargin = dp(16);
        sl.bottomMargin = dp(18);
        root.addView(sb, sl);
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        return root;
    }

    private void showTab() {
        if (tab == TAB_FILES) {
            searchBox.setHint("搜索这个文件夹");
            pageBack.setEnabled(false);
            buildFiles();
        } else {
            searchBox.setHint("搜索设置");
            buildSettings();
        }
    }

    /** 设置的子页面（开发工具、技能与插件、通用……）。返回首页时回到原来滚动的位置。 */
    private void openPage(String p) {
        if (P_ROOT.equals(page)) rootScrollY = scroll.getScrollY();
        page = p;
        query = "";
        if (searchBox.getText().length() > 0) searchBox.setText("");
        buildSettings();
        final int y = P_ROOT.equals(p) ? rootScrollY : 0;
        scroll.scrollTo(0, 0);
        scroll.post(() -> scroll.scrollTo(0, y));
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
        add(g, r, title + " " + (sub == null ? "" : sub) + " " + (value == null ? "" : value));
        return r;
    }

    private void applySearch() {
        boolean searching = !query.isEmpty();
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
        return page + "|" + rt.stage + "|" + rt.accountEmail + "|" + rt.accountPlan + "|" + rt.installedVersion + "|" + rt.models.size() + "|" + ModelPicker.summary(rt)
                + "|" + rt.prefs.mode() + "|" + rt.prefs.workspace() + "|" + (rt.lastRateLimits == null ? 0 : rt.lastRateLimits.hashCode())
                + "|" + rt.prefs.theme() + "|" + rt.prefs.accent() + "|" + rt.prefs.userCa() + "|" + rt.prefs.toolkitRepo() + "|" + rt.catalog.size()
                + "|" + rt.prefs.nickname() + "|" + rt.toolkitState;
    }

    private static String pageTitle(String p) {
        switch (p) {
            case P_DEV:
                return "开发工具";
            case P_SKILLS:
                return "技能与插件";
            case P_GENERAL:
                return "通用";
            case P_CLI:
                return "Codex CLI";
            case P_ABOUT:
                return "关于";
            default:
                return "";
        }
    }

    private void buildSettings() {
        if (tab != TAB_SETTINGS) return;
        boolean root = P_ROOT.equals(page);
        boolean searching = !query.isEmpty();
        titleView.setText(searching ? "搜索设置" : pageTitle(page));
        subtitleView.setVisibility(View.GONE);
        actionButton.setVisibility(View.GONE);
        searchBar.setVisibility(root ? View.VISIBLE : View.GONE);
        bottomFade.setVisibility(root ? View.VISIBLE : View.GONE);
        scroll.setPadding(0, dp(84), 0, dp(root ? 120 : 36));
        pageBack.setEnabled(!root);
        final int oldY = scroll.getScrollY();
        settingsSig = settingsSignature();
        content.removeAllViews();
        groups.clear();
        toolkitHost = null;
        if (searching) { // 搜索：所有页面的项都放出来再过滤
            buildRootPage(false);
            buildDevtoolsPage();
            buildSkillsPage();
            buildGeneralPage();
            buildCliPage();
            buildAboutPage();
        } else {
            switch (page) {
                case P_DEV:
                    buildDevtoolsPage();
                    break;
                case P_SKILLS:
                    buildSkillsPage();
                    break;
                case P_GENERAL:
                    buildGeneralPage();
                    break;
                case P_CLI:
                    buildCliPage();
                    break;
                case P_ABOUT:
                    buildAboutPage();
                    break;
                default:
                    buildRootPage(true);
            }
        }
        applySearch();
        if (searching && content.getChildCount() > 0) {
            boolean any = false;
            for (Group g : groups) if (g.card.getVisibility() == View.VISIBLE) any = true;
            if (!any) {
                TextView none = Ui.text(this, "没有找到“" + query + "”", 15, R.color.text3);
                none.setGravity(Gravity.CENTER);
                none.setPadding(dp(24), dp(40), dp(24), dp(40));
                content.addView(none, Ui.matchWrap());
            }
        }
        if (oldY > 0) scroll.post(() -> scroll.scrollTo(0, oldY));
    }

    /** 首页：头像、账户、外观、我的口袋工坊（几个入口）、通用 / Codex CLI / 关于。 */
    private void buildRootPage(boolean header) {
        String email = rt.accountEmail;
        String name = rt.prefs.nickname() != null ? rt.prefs.nickname()
                : email == null ? (rt.accountType == null ? "正在确认…" : "apiKey".equals(rt.accountType) ? "API 密钥" : rt.accountType) : email.replaceAll("@.*", "");
        if (header) {
            LinearLayout profile = Ui.vbox(this);
            profile.setGravity(Gravity.CENTER_HORIZONTAL);
            profile.setPadding(0, 0, 0, dp(10));
            FrameLayout av = new FrameLayout(this);
            TextView avatar = Ui.text(this, initials(name), 34, R.color.text);
            avatar.setTextColor(0xFFFFFFFF);
            avatar.setGravity(Gravity.CENTER);
            GradientDrawable ag = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFE6D8F2, 0xFFC3A5DF});
            ag.setShape(GradientDrawable.OVAL);
            avatar.setBackground(ag);
            av.addView(avatar, new FrameLayout.LayoutParams(dp(104), dp(104)));
            ImageView pen = new ImageView(this);
            pen.setImageResource(R.drawable.ic_pen);
            pen.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.text2)));
            GradientDrawable pb = Ui.circle(this, R.color.card);
            pb.setStroke(Math.max(1, dp(1)), Ui.color(this, R.color.bg_low));
            pen.setBackground(pb);
            pen.setPadding(dp(8), dp(8), dp(8), dp(8));
            pen.setContentDescription("改名字");
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.END | Gravity.BOTTOM);
            av.addView(pen, pl);
            Ui.ripple(av, true);
            av.setOnClickListener(v -> editNickname());
            profile.addView(av, new LinearLayout.LayoutParams(dp(110), dp(108)));
            TextView nm = Overlay.strong(this, name, 21);
            nm.setPadding(0, dp(12), 0, 0);
            profile.addView(nm, Ui.wrap());
            content.addView(profile, Ui.matchWrap());
        }

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

        Group mine = newGroup("我的口袋工坊");
        boolean auto = Prefs.MODE_AUTO.equals(rt.prefs.mode());
        CodexRuntimeManager.ModelInfo mi = rt.currentModel();
        String modelShort = mi == null ? "默认" : ModelPicker.shortModel(mi.displayName);
        row(mine, R.drawable.ic_codex_line, "模型与权限", null, modelShort + " · " + (auto ? "完全自动" : "每步确认"), 1,
                v -> ModelPicker.show(this, rt, false, this::buildSettings));
        row(mine, R.drawable.ic_code_line, "开发工具", null, devtoolsSummary(), 1, v -> openPage(P_DEV));
        int skills = 0, plugins = 0;
        for (Catalog c : rt.catalog) if (c.skill) skills++;
        else plugins++;
        row(mine, R.drawable.ic_plugin, "技能与插件", null, rt.catalog.isEmpty() ? null : skills + " 个技能" + (plugins > 0 ? " · " + plugins + " 个插件" : ""), 1,
                v -> openPage(P_SKILLS));
        String ws = rt.prefs.workspace();
        row(mine, R.drawable.ic_box_line, "工作区", null, Workspaces.name(ws), 1, v -> chooseWorkspace());

        Group more = newGroup(null);
        row(more, R.drawable.ic_gear_line, "通用", null, null, 1, v -> openPage(P_GENERAL));
        row(more, R.drawable.ic_terminal_line, "Codex CLI", null, rt.installedVersion, 1, v -> openPage(P_CLI));
        row(more, R.drawable.ic_info, "关于", null, BuildConfig.VERSION_NAME, 1, v -> openPage(P_ABOUT));
    }

    private String devtoolsSummary() {
        switch (rt.toolkitState) {
            case INSTALLING:
                return "正在安装";
            case READY:
                int n = 0;
                for (ToolBundles.Bundle b : ToolBundles.ALL) if (!b.id.equals("recommended") && rt.bundleInstalled(b.id)) n++;
                return n == 0 ? "已就绪" : "已就绪 · " + n + " 组工具";
            case BROKEN:
                return "自检没通过";
            case FAILED:
                return "安装没完成";
            default:
                return "未安装";
        }
    }

    /** 开发工具：状态卡片、工具集、终端和常用命令、软件源。 */
    private void buildDevtoolsPage() {
        Group dev = newGroup(query.isEmpty() ? null : "开发工具");
        toolkitHost = Ui.vbox(this);
        dev.card.addView(toolkitHost);
        dev.rows.add(toolkitHost);
        dev.keys.add("开发工具 node git python 工具包 安装 基础包");
        bindToolkitCard();

        Group sets = newGroup("工具集（一组一组装）");
        for (ToolBundles.Bundle tb : ToolBundles.ALL) {
            final ToolBundles.Bundle fb = tb;
            LinearLayout br = row(sets, bundleIcon(tb.id), tb.title, tb.summary + " · 下载约 " + tb.downloadMb + " MB", bundleState(tb), 0, v -> installBundle(fb));
            br.setTag(R.id.tag_bundle, tb.id);
        }
        bindBundleRows();

        Group tools = newGroup("命令行");
        row(tools, R.drawable.ic_terminal_line, "终端", "直接运行 node、npm、git、pkg；不经过模型，不消耗额度", null, 1, v -> startActivity(new Intent(this, TerminalActivity.class)));
        row(tools, R.drawable.ic_code_line, "常用命令", "Node.js、npm、Git、Python、pkg 的常用写法", null, 1,
                v -> startActivity(new Intent(this, TerminalActivity.class).putExtra(TerminalActivity.EXTRA_COMMANDS, true)));
        LinearLayout repo = row(tools, R.drawable.ic_globe_line, "软件源", null, toolkitRepoLabel(), 2, null);
        repo.setOnClickListener(v -> {
            List<Overlay.Opt> o = new ArrayList<>();
            o.add(new Overlay.Opt("自动", "官方和国内镜像，哪个能连上用哪个", rt.prefs.toolkitRepo() == null, () -> rt.prefs.setToolkitRepo(null)));
            for (com.pocketcodex.core.Toolkit.Repo r : com.pocketcodex.core.Toolkit.REPOS) {
                o.add(new Overlay.Opt(r.label, null, r.id.equals(rt.prefs.toolkitRepo()), () -> rt.prefs.setToolkitRepo(r.id)));
            }
            Overlay.dropdown(this, repo, o, true, (ImageView) repo.findViewWithTag("chevron"), this::buildSettings);
        });
    }

    /** 技能与插件：Codex 能用的技能（内置的 pocket-* 在前）、已装的插件、MCP 怎么加、重启 Codex。 */
    private void buildSkillsPage() {
        Group sk = newGroup("技能（输入 @ 可以直接选）");
        int n = 0;
        List<Catalog> ordered = new ArrayList<>();
        for (Catalog c : rt.catalog) if (c.skill && c.id.startsWith("pocket-")) ordered.add(c);
        for (Catalog c : rt.catalog) if (c.skill && !c.id.startsWith("pocket-")) ordered.add(c);
        for (Catalog c : ordered) {
            LinearLayout r = row(sk, c.iconRes(), c.label(), c.description == null || c.description.isEmpty() ? null : c.description,
                    c.id.startsWith("pocket-") ? "内置" : null, 0, null);
            if (c.colored()) {
                View ic = r.getChildAt(0);
                if (ic instanceof ImageView) ((ImageView) ic).setImageTintList(null);
            }
            n++;
        }
        if (n == 0) row(sk, R.drawable.ic_info, rt.serverAlive() ? "还没有读到技能" : "Codex 启动后会列出技能", null, null, 0, null);
        Group pl = newGroup("插件");
        int p = 0;
        for (Catalog c : rt.catalog) {
            if (c.skill) continue;
            row(pl, c.iconRes(), c.label(), c.description == null || c.description.isEmpty() ? null : c.description, "已启用", 0, null);
            p++;
        }
        if (p == 0) row(pl, R.drawable.ic_plugin, "没有已启用的插件", "在 ChatGPT 账号里启用的 Codex 插件会出现在这里", null, 0, null);
        Group mcp = newGroup("MCP 服务与第三方技能");
        row(mcp, R.drawable.ic_book, "怎么加 MCP 服务", "例如 radare2 的 r2mcp、jadx-headless-mcp、REA", null, 1, v -> new AlertDialog.Builder(this).setTitle("加 MCP 服务")
                .setMessage("在 Codex 的 config.toml（$CODEX_HOME/config.toml）末尾加一段，例如：\n\n[mcp_servers.r2]\ncommand = \"r2pm\"\nargs = [\"-r\", \"r2mcp\"]\n\n"
                        + "口袋工坊不会改写这个文件。也可以直接让 Codex 帮你加（它知道路径，见内置技能“手机开发环境”）。加完点“重启 Codex”。\n\n"
                        + "第三方技能放在 ~/.agents/skills/<名字>/SKILL.md，例如让 Codex 执行：git clone https://github.com/aicodeking/leanrea 后复制 leanrea 目录过去。")
                .setPositiveButton("复制示例", (d, w) -> copy("MCP", "[mcp_servers.r2]\ncommand = \"r2pm\"\nargs = [\"-r\", \"r2mcp\"]\n"))
                .setNegativeButton("关闭", null).show());
        row(mcp, R.drawable.ic_book, "安装 REA（逆向，实验性）", "在终端里运行安装命令（已填好，按发送即可）；装完回来点“重启 Codex”", null, 1, v ->
                startActivity(new Intent(this, TerminalActivity.class).putExtra(TerminalActivity.EXTRA_PREFILL, REA_SETUP)));
        row(mcp, R.drawable.ic_refresh_line, "重启 Codex", "加了 MCP 服务或技能后用", null, 1, v -> restartCodex());
    }

    /** REA 注册到 Codex 的命令（版本固定，和内置技能里写的一致）。 */
    static final String REA_SETUP = "npx --yes rea-agents@6.8.0 setup --client codex";

    private void restartCodex() {
        if (rt.restartCodex()) Toast.makeText(this, "正在重启 Codex…", Toast.LENGTH_SHORT).show();
        else Toast.makeText(this, "有任务正在运行，结束后再重启", Toast.LENGTH_LONG).show();
    }

    /** 通用：通知、电池、存储权限、证书、自检。 */
    private void buildGeneralPage() {
        Group gen = newGroup(query.isEmpty() ? null : "通用");
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
        Group net = newGroup("网络");
        LinearLayout ca = row(net, R.drawable.ic_lock_line, "信任用户安装的证书", "公司网络或抓包工具需要时再打开", null, 0, null);
        com.google.android.material.materialswitch.MaterialSwitch sw = Ui.toggle(this, rt.prefs.userCa(), (b, on) -> {
            if (on == rt.prefs.userCa()) return;
            rt.prefs.setUserCa(on);
            rt.invalidateCerts();
            Toast.makeText(this, "下次启动 Codex 时生效", Toast.LENGTH_SHORT).show();
        });
        ca.addView(sw);
        ca.setOnClickListener(v -> sw.toggle());
        Ui.ripple(ca, false);
        row(net, R.drawable.ic_pulse_line, "自检与诊断", "检查网络、证书、本机执行、开发工具和技能", null, 1, v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
    }

    /** Codex CLI：版本、下载来源、检查更新、重新安装、重启。 */
    private void buildCliPage() {
        Group cli = newGroup(query.isEmpty() ? null : "Codex CLI");
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
        row(cli, R.drawable.ic_refresh_line, "重启 Codex", "重新读取配置、技能和 MCP 服务", null, 1, v -> restartCodex());
    }

    private void buildAboutPage() {
        Group about = newGroup(query.isEmpty() ? null : "关于");
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
    }

    private void editNickname() {
        final EditText et = new EditText(this);
        et.setSingleLine();
        et.setText(rt.prefs.nickname() == null ? "" : rt.prefs.nickname());
        et.setHint(rt.accountEmail == null ? "名字" : rt.accountEmail.replaceAll("@.*", ""));
        LinearLayout box = Ui.vbox(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(et);
        new AlertDialog.Builder(this).setTitle("显示的名字").setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    rt.prefs.setNickname(et.getText().toString());
                    buildSettings();
                }).setNegativeButton("取消", null).show();
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
            case "net":
                return R.drawable.ic_pulse_line;
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

    /** 工具集那几行右边的状态（安装中 / 已安装 / 安装），搜索用的关键字也一起更新。 */
    private void bindBundleRows() {
        boolean changed = false;
        for (Group g : groups) {
            for (int i = 0; i < g.rows.size(); i++) {
                View row = g.rows.get(i);
                Object id = row.getTag(R.id.tag_bundle);
                if (!(id instanceof String)) continue;
                TextView val = row.findViewWithTag("value");
                ToolBundles.Bundle b = ToolBundles.find((String) id);
                if (val == null || b == null) continue;
                String st = bundleState(b);
                if (!st.contentEquals(val.getText())) changed = true;
                val.setText(st);
                val.setTextColor(st.equals("安装") ? Ui.accent(this) : Ui.color(this, R.color.text3));
                g.keys.set(i, (b.title + " " + b.summary + " " + st).toLowerCase(Locale.ROOT));
            }
        }
        if (changed && !query.isEmpty()) applySearch();
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
        bindBundleRows();
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
                body = (rt.toolkitError == null ? "" : rt.toolkitError + "\n") + "可以重新安装，或到 通用 → 自检与诊断 里查看详情。";
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
        searchBar.setVisibility(View.VISIBLE);
        bottomFade.setVisibility(View.VISIBLE);
        content.removeAllViews();
        groups.clear();
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
