package com.pocketcodex.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.pocketcodex.core.Installer;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 主界面：底部导航“会话 / 文件 / 设置”。对话在 {@link ChatActivity} 里。 */
public final class MainActivity extends AppCompatActivity implements CodexRuntimeManager.Listener {
    static final int TAB_SESSIONS = 1, TAB_FILES = 2, TAB_SETTINGS = 3;
    static final String EXTRA_TAB = "tab";

    private CodexRuntimeManager rt;
    private FrameLayout pageHost;
    private BottomNavigationView nav;
    private int tab = TAB_SESSIONS;
    // 会话页
    private RowAdapter sessionsAdapter;
    private LinearLayout sessionsBanner, sessionsEmpty;
    private TextView wsName, wsKind;
    // 文件页
    private File filesDir;
    private RowAdapter filesAdapter;
    private LinearLayout fileCrumbs;
    private TextView filesEmpty;
    private boolean showHidden;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rt = CodexRuntimeManager.get(this);
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        root.setFitsSystemWindows(true);
        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        nav = new BottomNavigationView(this);
        nav.setBackgroundColor(Ui.color(this, R.color.card));
        Menu m = nav.getMenu();
        m.add(Menu.NONE, TAB_SESSIONS, 0, "会话").setIcon(R.drawable.ic_chat);
        m.add(Menu.NONE, TAB_FILES, 1, "文件").setIcon(R.drawable.ic_folder);
        m.add(Menu.NONE, TAB_SETTINGS, 2, "设置").setIcon(R.drawable.ic_settings);
        nav.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
        nav.setOnItemSelectedListener(it -> {
            showTab(it.getItemId());
            return true;
        });
        root.addView(nav, Ui.matchWrap());
        setContentView(root);
        if (b != null) tab = b.getInt("tab", TAB_SESSIONS);
        else tab = getIntent().getIntExtra(EXTRA_TAB, TAB_SESSIONS);
        nav.setSelectedItemId(tab);
        showTab(tab);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        int t = i.getIntExtra(EXTRA_TAB, 0);
        if (t != 0 && t != tab) nav.setSelectedItemId(t); // 会触发 showTab
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
        else if (rt.stage == CodexRuntimeManager.Stage.READY) {
            rt.refreshThreads();
            rt.refreshRateLimits();
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
        CodexRuntimeManager.Stage s = rt.stage;
        if (s == CodexRuntimeManager.Stage.NOT_INSTALLED || s == CodexRuntimeManager.Stage.NEED_LOGIN
                || s == CodexRuntimeManager.Stage.INSTALL_FAILED || rt.prefs.workspace() == null) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        if (tab == TAB_SESSIONS) bindSessions();
        else if (tab == TAB_SETTINGS) {
            // 设置页显示的状态（账号、版本）变了才整页重建；工具包安装进度只刷新那张卡片
            if (settingsSignature().equals(settingsSig)) bindToolkitCard();
            else buildSettings();
        }
    }

    private void showTab(int t) {
        tab = t;
        pageHost.removeAllViews();
        if (t == TAB_SESSIONS) buildSessions();
        else if (t == TAB_FILES) buildFiles();
        else buildSettings();
    }

    /* ---------------- 共用 ---------------- */

    private LinearLayout topBar(String title, View... actions) {
        LinearLayout bar = Ui.hbox(this);
        bar.setMinimumHeight(Ui.dp(this, 56));
        bar.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 4), 0);
        TextView t = Ui.text(this, title, 22, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(t, Ui.weight1());
        for (View a : actions) bar.addView(a);
        return bar;
    }

    ImageButton iconButton(int icon, String desc, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackgroundResource(rippleRes());
        b.setContentDescription(desc);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.text)));
        b.setMinimumWidth(Ui.dp(this, 48));
        b.setMinimumHeight(Ui.dp(this, 48));
        b.setOnClickListener(l);
        return b;
    }

    int rippleRes() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        return tv.resourceId;
    }

    private LinearLayout workspaceRow() {
        LinearLayout row = Ui.hbox(this);
        row.setMinimumHeight(Ui.dp(this, 56));
        row.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        row.addView(Ui.icon(this, R.drawable.ic_folder, R.color.primary, 24));
        LinearLayout tx = Ui.vbox(this);
        wsName = Ui.body(this, "");
        wsName.setSingleLine();
        wsKind = Ui.small(this, "");
        tx.addView(wsName);
        tx.addView(wsKind);
        LinearLayout.LayoutParams lp = Ui.weight1();
        lp.leftMargin = Ui.dp(this, 12);
        row.addView(tx, lp);
        row.addView(Ui.icon(this, R.drawable.ic_expand, R.color.text2, 24));
        row.setOnClickListener(v -> chooseWorkspace());
        row.setContentDescription("切换工作区");
        bindWorkspaceRow();
        return row;
    }

    private void bindWorkspaceRow() {
        if (wsName == null) return;
        String w = rt.prefs.workspace();
        wsName.setText(Workspaces.name(w));
        wsKind.setText(Workspaces.isShared(rt, w) ? "手机存储 · " + Format.prettyPath(rt, w) : "应用内");
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
                    showTab(tab);
                }).show();
    }

    /* ---------------- 会话 ---------------- */

    private void buildSessions() {
        LinearLayout page = Ui.vbox(this);
        page.addView(topBar("会话", iconButton(R.drawable.ic_refresh, "刷新", v -> rt.refreshThreads())));
        page.addView(workspaceRow());
        page.addView(Ui.divider(this));
        sessionsBanner = Ui.vbox(this);
        page.addView(sessionsBanner, Ui.matchWrap());
        FrameLayout listHost = new FrameLayout(this);
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        sessionsAdapter = new RowAdapter((r, v, longPress) -> {
            CodexRuntimeManager.ThreadSummary t = (CodexRuntimeManager.ThreadSummary) r.key;
            if (longPress) sessionMenu(t);
            else openChat(t.id, t.cwd);
        });
        rv.setAdapter(sessionsAdapter);
        listHost.addView(rv);
        sessionsEmpty = Ui.vbox(this);
        sessionsEmpty.setPadding(Ui.dp(this, 32), Ui.dp(this, 48), Ui.dp(this, 32), 0);
        sessionsEmpty.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        TextView et = Ui.text(this, "从一个小任务开始", 18, R.color.text);
        et.setTypeface(Typeface.DEFAULT_BOLD);
        sessionsEmpty.addView(Ui.icon(this, R.drawable.ic_chat, R.color.border, 48));
        sessionsEmpty.addView(et, Ui.wrap());
        sessionsEmpty.addView(Ui.caption(this, "例如：帮我创建一份旅行清单。"), Ui.wrap());
        listHost.addView(sessionsEmpty);
        page.addView(listHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout foot = Ui.vbox(this);
        foot.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        foot.addView(Ui.primary(this, "新对话", v -> openChat(null, rt.prefs.workspace())), Ui.matchWrap());
        page.addView(foot);
        pageHost.addView(page);
        bindSessions();
    }

    private void bindSessions() {
        if (sessionsAdapter == null || tab != TAB_SESSIONS) return;
        bindWorkspaceRow();
        sessionsBanner.removeAllViews();
        if (rt.stage == CodexRuntimeManager.Stage.STARTING || rt.stage == CodexRuntimeManager.Stage.CHECKING) {
            sessionsBanner.addView(banner(R.drawable.ic_refresh, "正在启动 Codex…", null, null));
        } else if (rt.stage == CodexRuntimeManager.Stage.ERROR) {
            sessionsBanner.addView(banner(R.drawable.ic_warning, rt.errorMessage == null ? "Codex 没有运行" : rt.errorMessage, "重试", v -> rt.startServer()));
        } else if (rt.runningThreadId != null) {
            int ap = rt.pendingApprovalCount();
            String name = "对话";
            for (CodexRuntimeManager.ThreadSummary t : rt.threads) if (t.id.equals(rt.runningThreadId)) name = t.title();
            final String id = rt.runningThreadId;
            sessionsBanner.addView(banner(ap > 0 ? R.drawable.ic_warning : R.drawable.ic_terminal, name + " · " + (ap > 0 ? "等待确认（" + ap + "）" : "正在执行"), "查看",
                    v -> openChat(id, null)));
        }
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (CodexRuntimeManager.ThreadSummary t : rt.threads) {
            String status = null;
            if (t.id.equals(rt.runningThreadId)) status = rt.pendingApprovalCount() > 0 ? "等待确认" : "正在执行";
            boolean named = t.name != null && !t.name.trim().isEmpty();
            String sub = named && t.preview != null ? t.preview.trim().replace('\n', ' ') : ""; // 没起名时标题就是预览，不重复
            String ws = t.cwd == null ? "" : rt.isChatCwd(t.cwd) ? "聊天" : Workspaces.name(t.cwd);
            String line = ws.isEmpty() ? sub : sub.isEmpty() ? ws : ws + " · " + sub;
            rows.add(new RowAdapter.Row(t, R.drawable.ic_chat, t.id.equals(rt.runningThreadId) ? R.color.primary : R.color.text2,
                    t.title(), line, Format.ago(t.updatedAt), status, 2));
        }
        sessionsAdapter.set(rows);
        boolean empty = rows.isEmpty() && rt.threadsLoaded;
        sessionsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private View banner(int icon, String text, String action, View.OnClickListener l) {
        LinearLayout b = Ui.hbox(this);
        b.setBackgroundColor(Ui.color(this, R.color.primary_soft));
        b.setMinimumHeight(Ui.dp(this, 48));
        b.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        b.addView(Ui.icon(this, icon, R.color.primary, 20));
        TextView t = Ui.text(this, text, 14, R.color.text);
        LinearLayout.LayoutParams lp = Ui.weight1();
        lp.leftMargin = Ui.dp(this, 12);
        b.addView(t, lp);
        if (action != null) b.addView(Ui.textButton(this, action, l));
        return b;
    }

    private void sessionMenu(CodexRuntimeManager.ThreadSummary t) {
        new AlertDialog.Builder(this).setTitle(t.title())
                .setItems(new String[]{"重命名", "归档（从列表移除，不删除文件）"}, (d, w) -> {
                    if (w == 0) {
                        final EditText et = new EditText(this);
                        et.setText(t.title());
                        et.setSingleLine();
                        LinearLayout box = Ui.vbox(this);
                        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
                        box.addView(et);
                        new AlertDialog.Builder(this).setTitle("重命名").setView(box)
                                .setPositiveButton("保存", (d2, w2) -> rt.renameThread(t.id, et.getText().toString().trim()))
                                .setNegativeButton("取消", null).show();
                    } else {
                        rt.archiveThread(t.id);
                    }
                }).show();
    }

    private void openChat(String threadId, String cwd) {
        Intent i = new Intent(this, ChatActivity.class);
        if (threadId != null) i.putExtra(ChatActivity.EXTRA_THREAD, threadId);
        if (cwd != null) i.putExtra(ChatActivity.EXTRA_CWD, cwd);
        startActivity(i);
    }

    /* ---------------- 文件 ---------------- */

    private void buildFiles() {
        String ws = rt.prefs.workspace();
        File root = ws == null ? null : new File(ws);
        if (filesDir == null || root == null || !filesDir.getAbsolutePath().startsWith(root.getAbsolutePath())) filesDir = root;
        LinearLayout page = Ui.vbox(this);
        page.addView(topBar("文件", iconButton(R.drawable.ic_refresh, "刷新", v -> bindFiles()),
                iconButton(R.drawable.ic_more, "更多", v -> filesMenu())));
        page.addView(workspaceRow());
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        fileCrumbs = Ui.hbox(this);
        fileCrumbs.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        fileCrumbs.setMinimumHeight(Ui.dp(this, 44));
        hs.addView(fileCrumbs);
        page.addView(hs);
        page.addView(Ui.divider(this));
        FrameLayout host = new FrameLayout(this);
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        filesAdapter = new RowAdapter((r, v, longPress) -> {
            File f = (File) r.key;
            if (FileViewerActivity.sensitive(this, f)) { // 指向运行时目录（凭据）的链接：不进
                Toast.makeText(this, "这个位置属于应用的运行目录，不能在这里打开", Toast.LENGTH_LONG).show();
                return;
            }
            if (longPress) fileMenu(f);
            else if (f.isDirectory()) {
                filesDir = f;
                bindFiles();
            } else {
                startActivity(new Intent(this, FileViewerActivity.class).putExtra(FileViewerActivity.EXTRA_PATH, f.getAbsolutePath()));
            }
        });
        rv.setAdapter(filesAdapter);
        host.addView(rv);
        filesEmpty = Ui.caption(this, "");
        filesEmpty.setPadding(Ui.dp(this, 32), Ui.dp(this, 48), Ui.dp(this, 32), 0);
        filesEmpty.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        host.addView(filesEmpty);
        page.addView(host, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        pageHost.addView(page);
        bindFiles();
    }

    private void bindFiles() {
        if (filesAdapter == null) return;
        String ws = rt.prefs.workspace();
        File root = ws == null ? null : new File(ws);
        fileCrumbs.removeAllViews();
        if (root == null || filesDir == null) return;
        List<File> chain = new ArrayList<>();
        for (File f = filesDir; f != null; f = f.getParentFile()) {
            chain.add(0, f);
            if (f.equals(root)) break;
        }
        for (int i = 0; i < chain.size(); i++) {
            final File f = chain.get(i);
            TextView c = Ui.text(this, i == 0 ? Workspaces.name(ws) : f.getName(), 14, i == chain.size() - 1 ? R.color.text : R.color.primary);
            c.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
            c.setOnClickListener(v -> {
                filesDir = f;
                bindFiles();
            });
            if (i > 0) fileCrumbs.addView(Ui.small(this, "›"));
            fileCrumbs.addView(c);
        }
        File[] fs = filesDir.listFiles();
        List<RowAdapter.Row> rows = new ArrayList<>();
        if (fs == null) {
            filesAdapter.set(rows);
            filesEmpty.setText(filesDir.exists() ? "无法读取这个文件夹（文件访问权限可能已关闭）" : "这个文件夹已移动或删除");
            filesEmpty.setVisibility(View.VISIBLE);
            return;
        }
        Arrays.sort(fs, (a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName()));
        for (File f : fs) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            String meta = Format.ago(f.lastModified() / 1000);
            String sub = f.isDirectory() ? countLabel(f) : Format.mb(f.length()).replace(" MB", "") .equals("0.0") ? f.length() + " B" : Format.mb(f.length());
            rows.add(new RowAdapter.Row(f, f.isDirectory() ? R.drawable.ic_folder_filled : R.drawable.ic_file, f.isDirectory() ? R.color.primary : R.color.text2,
                    f.getName(), sub, meta, null, 1));
        }
        filesAdapter.set(rows);
        filesEmpty.setText("这个文件夹还是空的。去对话里让 Codex 创建文件吧。");
        filesEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String countLabel(File d) {
        String[] n = d.list();
        return n == null ? "文件夹" : n.length + " 项";
    }

    private void filesMenu() {
        new AlertDialog.Builder(this).setItems(new String[]{showHidden ? "隐藏隐藏文件" : "显示隐藏文件", "复制当前路径", "新建文件夹"}, (d, w) -> {
            if (w == 0) {
                showHidden = !showHidden;
                bindFiles();
            } else if (w == 1) {
                copy("路径", filesDir.getAbsolutePath());
            } else {
                final EditText et = new EditText(this);
                et.setSingleLine();
                LinearLayout box = Ui.vbox(this);
                box.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
                box.addView(et);
                new AlertDialog.Builder(this).setTitle("新建文件夹").setView(box).setPositiveButton("创建", (d2, w2) -> {
                    String n = et.getText().toString().trim().replaceAll("[/\\\\]", "_");
                    if (!n.isEmpty() && new File(filesDir, n).mkdirs()) bindFiles();
                }).setNegativeButton("取消", null).show();
            }
        }).show();
    }

    private void fileMenu(File f) {
        new AlertDialog.Builder(this).setTitle(f.getName()).setItems(new String[]{"复制路径", "引用到新对话"}, (d, w) -> {
            if (w == 0) copy("路径", f.getAbsolutePath());
            else {
                Intent i = new Intent(this, ChatActivity.class).putExtra(ChatActivity.EXTRA_CWD, rt.prefs.workspace())
                        .putExtra(ChatActivity.EXTRA_DRAFT, "请看一下这个文件：" + f.getAbsolutePath() + "\n");
                startActivity(i);
            }
        }).show();
    }

    private void copy(String label, String s) {
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(label, s));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    /* ---------------- 设置 ---------------- */

    private String settingsSig = "";
    private ScrollView settingsScroll;
    private LinearLayout toolkitHost;

    private String settingsSignature() {
        return rt.stage + "|" + rt.accountEmail + "|" + rt.accountPlan + "|" + rt.installedVersion + "|" + rt.models.size() + "|" + ModelPicker.summary(rt)
                + "|" + rt.prefs.mode() + "|" + rt.prefs.workspace() + "|" + (rt.lastRateLimits == null ? 0 : rt.lastRateLimits.hashCode());
    }

    private void buildSettings() {
        if (tab != TAB_SETTINGS) return;
        final int oldY = settingsScroll == null ? 0 : settingsScroll.getScrollY();
        settingsSig = settingsSignature();
        pageHost.removeAllViews();
        LinearLayout page = Ui.vbox(this);
        page.addView(topBar("设置"));
        ScrollView sv = new ScrollView(this);
        settingsScroll = sv;
        if (oldY > 0) sv.post(() -> sv.scrollTo(0, oldY));
        LinearLayout list = Ui.vbox(this);
        list.setPadding(0, 0, 0, Ui.dp(this, 24));

        list.addView(Ui.section(this, "账号"));
        String who = rt.accountEmail != null ? rt.accountEmail : rt.accountType != null ? ("apiKey".equals(rt.accountType) ? "API 密钥" : rt.accountType) : "正在确认…";
        list.addView(item("当前账号", who + (rt.accountPlan == null ? "" : " · " + rt.accountPlan), null));
        list.addView(item("账号额度", usageSummary(), v -> showUsage()));
        list.addView(item("退出登录", "退出后保留本地文件和会话记录", v -> new AlertDialog.Builder(this).setTitle("退出登录？")
                .setMessage("退出后保留本地文件和会话记录，下次需要重新用设备码登录。")
                .setPositiveButton("退出登录", (d, w) -> rt.logout()).setNegativeButton("取消", null).show()));

        list.addView(Ui.section(this, "运行模式"));
        boolean auto = Prefs.MODE_AUTO.equals(rt.prefs.mode());
        list.addView(item((auto ? "○ " : "● ") + "每步确认", "运行命令和应用文件改动前逐项确认（默认）", v -> {
            rt.prefs.setMode(Prefs.MODE_CONFIRM);
            buildSettings();
        }));
        list.addView(item((auto ? "● " : "○ ") + "完全自动", "运行命令和修改文件时不再请求确认", v -> {
            if (Prefs.MODE_AUTO.equals(rt.prefs.mode())) return;
            new AlertDialog.Builder(this).setTitle("开启完全自动？")
                    .setMessage("助手将直接运行命令和修改文件，不再逐项确认。它能访问本应用有权限访问的位置；工作区不是独立隔离区。\n你仍可随时停止，但已经发生的改动不会自动撤销。")
                    .setPositiveButton("开启完全自动", (d, w) -> {
                        rt.prefs.setMode(Prefs.MODE_AUTO);
                        buildSettings();
                    }).setNegativeButton("保持每步确认", null).show();
        }));
        list.addView(item("模型、推理与速度", rt.models.isEmpty() ? "Codex 启动后可选" : ModelPicker.summary(rt), v -> ModelPicker.show(this, rt, false, this::buildSettings)));

        list.addView(Ui.section(this, "开发工具（Node.js · Git · Python）"));
        toolkitHost = Ui.vbox(this);
        list.addView(toolkitHost, Ui.matchWrap());
        bindToolkitCard();
        list.addView(item("终端", "直接运行 node、npm、git、pkg 等命令；不经过模型，不消耗额度", v -> startActivity(new Intent(this, TerminalActivity.class))));
        list.addView(item("常用命令", "Node.js、npm、Git、Python、pkg 的常用写法，点一下就能用", v -> startActivity(new Intent(this, TerminalActivity.class).putExtra(TerminalActivity.EXTRA_COMMANDS, true))));
        list.addView(item("软件源", toolkitRepoLabel(), v -> chooseToolkitRepo()));

        list.addView(Ui.section(this, "工作区"));
        String ws = rt.prefs.workspace();
        list.addView(item("当前工作区", Workspaces.name(ws) + " · " + (Workspaces.isShared(rt, ws) ? Format.prettyPath(rt, ws) : "应用内"), v -> chooseWorkspace()));
        list.addView(item("系统存储权限", "权限对整个应用生效；在系统设置里管理", v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))));

        list.addView(Ui.section(this, "Codex CLI"));
        list.addView(item("当前版本", (rt.installedVersion == null ? "未安装" : rt.installedVersion) + (Installer.TESTED_VERSION.equals(rt.installedVersion) ? " · 已验证版本" : " · 未验证版本"), null));
        list.addView(item("下载来源", "npmmirror".equals(rt.prefs.source()) ? "国内镜像（npmmirror）" : "npm 官方", v -> {
            String[] ids = {"npm", "npmmirror"};
            new AlertDialog.Builder(this).setTitle("下载来源").setItems(new String[]{"npm 官方（registry.npmjs.org）", "国内镜像（npmmirror · 第三方镜像）"}, (d, w) -> {
                rt.prefs.setSource(ids[w]);
                buildSettings();
            }).show();
        }));
        list.addView(item("检查更新", "查询 npm 上的最新版本，不会自动启用", v -> checkUpdate()));
        list.addView(item("重新安装", "重新下载并校验当前版本，不删除账号、历史和工作区", v -> new AlertDialog.Builder(this).setTitle("重新安装 Codex CLI？")
                .setMessage("会重新下载约 164 MB。").setPositiveButton("重新安装", (d, w) -> {
                    rt.prefs.setTargetVersion(rt.installedVersion == null ? Installer.TESTED_VERSION : rt.installedVersion);
                    rt.reinstall();
                    startActivity(new Intent(this, SetupActivity.class));
                }).setNegativeButton("取消", null).show()));

        list.addView(Ui.section(this, "网络与证书"));
        list.addView(item("连接方式", "跟随系统（Wi-Fi 代理设置会自动生效）", null));
        list.addView(item((rt.prefs.userCa() ? "● " : "○ ") + "信任用户安装的证书", "默认关闭。公司网络或抓包工具需要时再打开", v -> {
            rt.prefs.setUserCa(!rt.prefs.userCa());
            rt.invalidateCerts();
            Toast.makeText(this, "下次启动 Codex 时生效", Toast.LENGTH_SHORT).show();
            buildSettings();
        }));
        list.addView(item("测试连接", "运行自检，检查网络、证书和本机执行", v -> startActivity(new Intent(this, DiagnosticsActivity.class))));

        list.addView(Ui.section(this, "通知与后台"));
        list.addView(item("通知设置", "审批提醒、任务完成与失败提醒", v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        }));
        list.addView(item("电池限制", "任务期间保留运行通知，系统仍可能中断进程。被打断时可以在这里放宽限制", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
        }));

        list.addView(Ui.section(this, "诊断"));
        list.addView(item("自检与诊断", "检查本机能否运行 Codex", v -> startActivity(new Intent(this, DiagnosticsActivity.class))));

        list.addView(Ui.section(this, "关于"));
        String[] themes = {"跟随系统", "浅色", "深色"};
        list.addView(item("外观", themes[rt.prefs.theme()], v -> new AlertDialog.Builder(this).setTitle("外观").setItems(themes, (d, w) -> {
            rt.prefs.setTheme(w);
            App.applyTheme(w);
        }).show()));
        list.addView(item("本地执行与隐私", "文件和命令在本机运行；对话和相关文件内容会发送给模型服务", v -> new AlertDialog.Builder(this).setTitle("本地执行与隐私")
                .setMessage("• Codex CLI 安装在本应用的私有目录，命令和文件改动都在这台手机上执行。\n"
                        + "• 模型请求需要联网，你的消息和 Agent 读取的文件内容会发送给 OpenAI 的模型服务。\n"
                        + "• Android 的应用沙箱限制本应用（和它运行的命令）只能访问自己的目录和你授权的存储。工作区只是默认位置，不是独立隔离区。\n"
                        + "• 登录凭据保存在本应用私有目录，不会写进日志或诊断结果。")
                .setPositiveButton("知道了", null).show()));
        list.addView(item("应用版本", "口袋工坊 " + BuildConfig.VERSION_NAME + " · 独立开发的客户端，与 OpenAI 无官方隶属关系", null));
        list.addView(item("开源许可", "Codex CLI（Apache-2.0）、AndroidX、Material Components、Markwon、commonmark-java", null));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        pageHost.addView(page);
    }

    /** 开发工具卡片：未安装 / 安装中（进度）/ 已就绪（版本）/ 自检失败 / 安装失败。 */
    private void bindToolkitCard() {
        if (toolkitHost == null) return;
        toolkitHost.removeAllViews();
        LinearLayout card = Ui.vbox(this);
        card.setBackground(Ui.round(this, R.color.surface2, 0, 22));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        LinearLayout.LayoutParams cl = Ui.matchWrap();
        cl.leftMargin = cl.rightMargin = Ui.dp(this, 16);
        cl.topMargin = Ui.dp(this, 4);
        cl.bottomMargin = Ui.dp(this, 6);
        CodexRuntimeManager.ToolkitState st = rt.toolkitState;
        if (rt.paths.toolkit() == null) st = CodexRuntimeManager.ToolkitState.FAILED;
        String head, body = null;
        int icon, tint;
        switch (st) {
            case INSTALLING:
                head = "正在安装开发工具";
                body = rt.toolkitStep == null ? "准备中…" : rt.toolkitStep;
                icon = R.drawable.ic_download;
                tint = R.color.text;
                break;
            case READY:
                head = "开发工具已就绪";
                StringBuilder v = new StringBuilder();
                String[][] names = {{"node", "Node.js"}, {"npm", "npm"}, {"git", "Git"}, {"python3", "Python"}, {"pip", "pip"}, {"bash", "bash"}, {"rg", "ripgrep"}, {"curl", "curl"}};
                for (String[] n : names) {
                    String ver = rt.toolkitVersions.get(n[0]);
                    if (ver == null) continue;
                    if (v.length() > 0) v.append("　");
                    v.append(n[1]).append(' ').append(ver);
                }
                body = (v.length() == 0 ? "" : v + "\n") + "Codex 和终端都能用。缺的软件让 Codex 用 pkg install 安装，或在终端里自己装。";
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
                body = "装好后 Codex 就能用 Node.js、npm/npx、Git、Python/pip、bash、rg、curl、jq 等命令，缺的用 pkg install 补。下载约 57 MB，占用约 230 MB，来自 Termux 官方仓库（签名校验）。";
                icon = R.drawable.ic_download;
                tint = R.color.text;
        }
        LinearLayout row = Ui.hbox(this);
        row.addView(Ui.icon(this, icon, tint, 22));
        TextView t = Overlay.strong(this, head, 16);
        LinearLayout.LayoutParams tl = Ui.weight1();
        tl.leftMargin = Ui.dp(this, 12);
        row.addView(t, tl);
        card.addView(row);
        if (body != null) {
            TextView b = Ui.caption(this, body);
            card.addView(b, Ui.matchWrap(this, 8));
        }
        if (st == CodexRuntimeManager.ToolkitState.INSTALLING) {
            com.google.android.material.progressindicator.LinearProgressIndicator pi = new com.google.android.material.progressindicator.LinearProgressIndicator(this);
            pi.setTrackStopIndicatorSize(0);
            pi.setIndicatorColor(Ui.color(this, R.color.text));
            pi.setTrackColor(Ui.color(this, R.color.border));
            pi.setTrackCornerRadius(Ui.dp(this, 2));
            if (rt.toolkitTotal > 0) {
                pi.setMax(1000);
                pi.setProgressCompat((int) (rt.toolkitDone * 1000 / rt.toolkitTotal), false);
            } else {
                pi.setIndeterminate(true);
            }
            card.addView(pi, Ui.matchWrap(this, 12));
            com.google.android.material.button.MaterialButton c = Ui.outlined(this, "取消", v -> rt.cancelToolkitInstall());
            c.setTextColor(Ui.color(this, R.color.text));
            LinearLayout.LayoutParams bl = Ui.wrap();
            bl.topMargin = Ui.dp(this, 12);
            card.addView(c, bl);
        } else if (rt.paths.toolkit() != null) {
            String label = st == CodexRuntimeManager.ToolkitState.NONE ? "安装开发工具" : st == CodexRuntimeManager.ToolkitState.READY ? "重新安装" : "重试";
            com.google.android.material.button.MaterialButton go = st == CodexRuntimeManager.ToolkitState.NONE || st == CodexRuntimeManager.ToolkitState.FAILED
                    ? Ui.primary(this, label, null) : Ui.outlined(this, label, null);
            go.setOnClickListener(v -> {
                if (rt.toolkitState == CodexRuntimeManager.ToolkitState.READY) {
                    new AlertDialog.Builder(this).setTitle("重新安装开发工具？").setMessage("会重新下载并校验全部软件包（约 57 MB），用 pkg 装的额外软件包需要重新安装。")
                            .setPositiveButton("重新安装", (d, w) -> rt.startToolkitInstall()).setNegativeButton("取消", null).show();
                } else {
                    rt.startToolkitInstall();
                }
            });
            LinearLayout.LayoutParams bl = Ui.wrap();
            bl.topMargin = Ui.dp(this, 12);
            card.addView(go, bl);
        }
        toolkitHost.addView(card, cl);
    }

    private String toolkitRepoLabel() {
        String id = rt.prefs.toolkitRepo();
        if (id != null) for (com.pocketcodex.core.Toolkit.Repo r : com.pocketcodex.core.Toolkit.REPOS) if (r.id.equals(id)) return r.label + "（上次成功）";
        return "自动（官方仓库和国内镜像，哪个能连上用哪个）";
    }

    private void chooseToolkitRepo() {
        final List<com.pocketcodex.core.Toolkit.Repo> repos = com.pocketcodex.core.Toolkit.REPOS;
        List<String> labels = new ArrayList<>();
        labels.add("自动");
        for (com.pocketcodex.core.Toolkit.Repo r : repos) labels.add(r.label);
        new AlertDialog.Builder(this).setTitle("开发工具的软件源").setItems(labels.toArray(new String[0]), (d, w) -> {
            rt.prefs.setToolkitRepo(w == 0 ? null : repos.get(w - 1).id);
            buildSettings();
        }).setNegativeButton("取消", null).show();
    }

    private View item(String title, String summary, View.OnClickListener l) {
        LinearLayout row = Ui.vbox(this);
        row.setMinimumHeight(Ui.dp(this, 64));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(Ui.body(this, title));
        if (summary != null) row.addView(Ui.caption(this, summary), Ui.matchWrap(this, 2));
        if (l != null) {
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
            row.setOnClickListener(l);
        }
        return row;
    }

    private String usageSummary() {
        java.util.Map<String, Object> r = rt.lastRateLimits;
        if (r == null) return "点开查看";
        java.util.Map<String, Object> rl = com.pocketcodex.core.Json.obj(r, "rateLimits");
        java.util.Map<String, Object> p = com.pocketcodex.core.Json.obj(rl, "primary");
        if (p.isEmpty()) return "服务未提供额度信息";
        return "5 小时窗口已用 " + com.pocketcodex.core.Json.num(p, "usedPercent", 0) + "%";
    }

    private void showUsage() {
        rt.refreshRateLimits();
        UsageDialog.show(this, rt, null);
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
}
