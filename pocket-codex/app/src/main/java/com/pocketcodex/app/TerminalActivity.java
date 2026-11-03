package com.pocketcodex.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 终端：直接在手机上运行命令，用的和 Codex Agent 同一套环境（开发工具包的 Node.js、npm、Git、Python、pkg，
 * 代理和证书也一样）。不经过模型，不消耗额度——额度用完时也能自己 git pull、npm install、看日志。
 * <p>
 * 每条命令用 bash -c 单独运行（没有伪终端）：会记住 cd 后的目录；不支持 vim、python 交互模式这类需要键盘交互的程序。
 */
public final class TerminalActivity extends AppCompatActivity implements CodexRuntimeManager.Listener {
    static final String EXTRA_CWD = "cwd", EXTRA_COMMANDS = "commands";
    private static final int MAX_CHARS = 300_000;
    private static final char MARK = '\u001e';

    private CodexRuntimeManager rt;
    private String cwd;
    private TextView out, subtitle;
    private ScrollView scroll;
    private EditText input;
    private FrameLayout runBtn;
    private ImageView runIcon;
    private final SpannableStringBuilder buf = new SpannableStringBuilder();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private volatile Process proc;
    private volatile int procPid = -1;
    private volatile boolean running, stopped;
    private final List<String> history = new ArrayList<>();
    private int histPos;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rt = CodexRuntimeManager.get(this);
        cwd = getIntent().getStringExtra(EXTRA_CWD);
        if (cwd == null || !new File(cwd).isDirectory()) cwd = rt.prefs.workspace();
        if (cwd == null || !new File(cwd).isDirectory()) cwd = rt.paths.home.getAbsolutePath();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(build());
        String saved = getSharedPreferences("terminal", MODE_PRIVATE).getString("history", "");
        for (String s : saved.split("\n")) if (!s.trim().isEmpty()) history.add(s);
        histPos = history.size();
        intro();
        if (getIntent().getBooleanExtra(EXTRA_COMMANDS, false)) h.post(this::commandsSheet);
    }

    @Override
    protected void onStart() {
        super.onStart();
        rt.addListener(this);
        bind();
    }

    @Override
    protected void onStop() {
        rt.removeListener(this);
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, history.size() - 50); i < history.size(); i++) sb.append(history.get(i).replace('\n', ' ')).append('\n');
        getSharedPreferences("terminal", MODE_PRIVATE).edit().putString("history", sb.toString()).apply();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (running) stop();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRuntimeChanged() {
        bind();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    /* ---------------- 布局 ---------------- */

    private View build() {
        LinearLayout root = Ui.vbox(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        FrameLayout bar = new FrameLayout(this);
        ImageView back = Ui.roundButton(this, R.drawable.ic_back, "返回", v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.START | Gravity.CENTER_VERTICAL);
        bl.leftMargin = dp(14);
        bar.addView(back, bl);
        ImageView more = Ui.roundButton(this, R.drawable.ic_dots, "更多", this::menu);
        FrameLayout.LayoutParams mr = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.END | Gravity.CENTER_VERTICAL);
        mr.rightMargin = dp(14);
        bar.addView(more, mr);
        LinearLayout tb = Ui.vbox(this);
        tb.setGravity(Gravity.CENTER);
        TextView t = Overlay.strong(this, "终端", 16);
        t.setGravity(Gravity.CENTER);
        tb.addView(t, Ui.matchWrap());
        subtitle = Ui.text(this, "", 12, R.color.text3);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setSingleLine();
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        tb.addView(subtitle, Ui.matchWrap());
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tl.leftMargin = tl.rightMargin = dp(72);
        bar.addView(tb, tl);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        // 快捷命令
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setPadding(dp(14), 0, dp(14), 0);
        hs.setClipToPadding(false);
        LinearLayout chips = Ui.hbox(this);
        TextView all = chip("常用命令", true);
        all.setOnClickListener(v -> commandsSheet());
        chips.addView(all);
        for (String q : Commands.QUICK) {
            TextView c = chip(q.trim(), false);
            c.setOnClickListener(v -> {
                if (q.endsWith(" ")) {
                    input.setText(q);
                    input.setSelection(q.length());
                    input.requestFocus();
                } else if (!running) {
                    run(q);
                }
            });
            chips.addView(c);
        }
        hs.addView(chips);
        LinearLayout.LayoutParams hl = Ui.matchWrap();
        hl.bottomMargin = dp(8);
        root.addView(hs, hl);

        // 输出
        scroll = new ScrollView(this);
        scroll.setBackground(Ui.round(this, R.color.code_bg, 0, 22));
        scroll.setClipToPadding(false);
        out = Ui.mono(this, "");
        out.setTextSize(13);
        out.setTextIsSelectable(true);
        out.setPadding(dp(16), dp(14), dp(16), dp(14));
        out.setLineSpacing(0, 1.12f);
        scroll.addView(out);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sl.leftMargin = sl.rightMargin = dp(12);
        root.addView(scroll, sl);

        // 输入卡片
        LinearLayout card = Ui.hbox(this);
        GradientDrawable bg = Ui.round(this, R.color.card, 0, 28);
        bg.setStroke(Math.max(1, dp(1) / 2), Ui.color(this, R.color.border));
        card.setBackground(bg);
        Ui.elevate(card, 6);
        card.setPadding(dp(18), dp(6), dp(8), dp(6));
        TextView prompt = Ui.mono(this, "$");
        prompt.setTextColor(Ui.color(this, R.color.text3));
        prompt.setTextSize(16);
        card.addView(prompt);
        input = new EditText(this);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(15);
        input.setTextColor(Ui.color(this, R.color.text));
        input.setHintTextColor(Ui.color(this, R.color.text3));
        input.setHint("输入命令，例如 git status");
        input.setBackground(null);
        input.setSingleLine();
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(10), dp(12), dp(8), dp(12));
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN)) {
                submit();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_UP) return recall(-1);
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) return recall(1);
            return false;
        });
        card.addView(input, Ui.weight1());
        ImageView up = Ui.plainIcon(this, R.drawable.ic_up, R.color.text2, 40, "上一条命令", v -> recall(-1));
        up.setPadding(dp(9), dp(9), dp(9), dp(9));
        card.addView(up);
        runBtn = new FrameLayout(this);
        runIcon = new ImageView(this);
        runBtn.addView(runIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        Ui.ripple(runBtn, true);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(42), dp(42));
        rl.leftMargin = dp(4);
        card.addView(runBtn, rl);
        LinearLayout.LayoutParams cl = Ui.matchWrap();
        cl.leftMargin = cl.rightMargin = dp(12);
        cl.topMargin = dp(10);
        cl.bottomMargin = dp(10);
        root.addView(card, cl);
        return root;
    }

    private TextView chip(String s, boolean strong) {
        TextView t = strong ? Overlay.strong(this, s, 14) : Ui.mono(this, s);
        if (!strong) t.setTextSize(13);
        t.setBackground(Ui.round(this, strong ? R.color.surface2 : R.color.card, strong ? 0 : R.color.border, 18));
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        Ui.ripple(t, false);
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void bind() {
        if (subtitle == null) return;
        String tk;
        if (rt.toolkitUsable()) tk = "Node.js · Git · Python 已就绪";
        else if (rt.toolkitState == CodexRuntimeManager.ToolkitState.INSTALLING) tk = "开发工具正在安装";
        else tk = "只有系统命令";
        subtitle.setText(Workspaces.name(cwd) + " · " + tk);
        if (running) {
            runBtn.setBackground(Ui.circle(this, R.color.primary));
            runIcon.setImageResource(R.drawable.ic_stop_square);
            runIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.on_primary)));
            runBtn.setContentDescription("停止");
            runBtn.setOnClickListener(v -> stop());
        } else {
            runBtn.setBackground(Ui.circle(this, R.color.accent));
            runIcon.setImageResource(R.drawable.ic_arrow_up);
            runIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.on_accent)));
            runBtn.setContentDescription("运行");
            runBtn.setOnClickListener(v -> submit());
        }
    }

    private void intro() {
        append("在手机上直接运行命令，环境和 Codex 一样，不消耗模型额度。\n", R.color.text2, false);
        if (rt.toolkitUsable()) {
            append("可用：node / npm / npx、git、python / pip、bash、rg、curl、jq…；缺什么用 pkg install <包名>。\n", R.color.text2, false);
        } else {
            append("开发工具包还没装好，现在只有系统自带的命令（ls、cat、grep…）。到 设置 → 开发工具 安装 Node.js、Git、Python（约 57 MB）。\n", R.color.warning, false);
        }
        append("不支持 vim、python 交互模式这类需要键盘交互的程序。\n", R.color.text3, false);
    }

    /* ---------------- 输出 ---------------- */

    private void append(CharSequence s, int colorRes, boolean bold) {
        int st = buf.length();
        buf.append(s);
        if (colorRes != 0) buf.setSpan(new ForegroundColorSpan(Ui.color(this, colorRes)), st, buf.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) buf.setSpan(new StyleSpan(Typeface.BOLD), st, buf.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (buf.length() > MAX_CHARS) buf.delete(0, buf.length() - MAX_CHARS + 50_000);
        out.setText(buf);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    /* ---------------- 运行 ---------------- */

    private void submit() {
        if (running) return;
        String c = input.getText().toString().trim();
        if (c.isEmpty()) return;
        input.setText("");
        run(c);
    }

    private boolean recall(int dir) {
        if (history.isEmpty()) return false;
        histPos = Math.max(0, Math.min(history.size(), histPos + dir));
        String s = histPos >= history.size() ? "" : history.get(histPos);
        input.setText(s);
        input.setSelection(s.length());
        return true;
    }

    void run(String cmd) {
        if (running) return;
        if (history.isEmpty() || !history.get(history.size() - 1).equals(cmd)) history.add(cmd);
        histPos = history.size();
        if (cmd.equals("clear")) {
            buf.clear();
            out.setText("");
            return;
        }
        append("\n" + Workspaces.name(cwd) + " $ ", R.color.text3, false);
        append(cmd + "\n", R.color.text, true);
        running = true;
        stopped = false;
        bind();
        final String dir = cwd;
        final long t0 = SystemClock.elapsedRealtime();
        io.execute(() -> {
            int rc = -1;
            String err = null;
            StringBuilder pwd = new StringBuilder();
            try {
                Object[] es = rt.terminalEnv();
                @SuppressWarnings("unchecked")
                Map<String, String> env = (Map<String, String>) es[0];
                String shell = (String) es[1];
                String script = "cd " + quote(dir) + " 2>/dev/null || cd \"$HOME\"\n" + cmd + "\n__rc=$?\nprintf '\\n\\036%s\\n' \"$PWD\"\nexit $__rc\n";
                ProcessBuilder pb = new ProcessBuilder(shell, "-c", script);
                pb.environment().clear();
                pb.environment().putAll(env);
                pb.directory(new File(dir).isDirectory() ? new File(dir) : rt.paths.home);
                pb.redirectErrorStream(true);
                pb.redirectInput(new File("/dev/null"));
                Process p = pb.start();
                proc = p;
                procPid = pidOf(p);
                try (InputStream in = p.getInputStream(); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    char[] cb = new char[4096];
                    StringBuilder pending = new StringBuilder();
                    long last = 0;
                    boolean marked = false;
                    int n;
                    while ((n = r.read(cb)) > 0) {
                        for (int i = 0; i < n; i++) {
                            char ch = cb[i];
                            if (marked) pwd.append(ch);
                            else if (ch == MARK) marked = true;
                            else pending.append(ch);
                        }
                        long now = SystemClock.elapsedRealtime();
                        if (now - last > 80 && pending.length() > 0) {
                            last = now;
                            final String chunk = pending.toString();
                            pending.setLength(0);
                            h.post(() -> append(chunk, 0, false));
                        }
                    }
                    String rest = pending.toString();
                    if (rest.endsWith("\n") && marked) rest = rest.substring(0, rest.length() - 1); // 去掉标记前补的换行
                    final String fr = rest;
                    if (!fr.isEmpty()) h.post(() -> append(fr, 0, false));
                }
                rc = p.waitFor();
            } catch (IOException e) {
                err = e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                err = "已中断";
            }
            final int frc = rc;
            final String ferr = err;
            final String newDir = pwd.toString().trim();
            final long ms = SystemClock.elapsedRealtime() - t0;
            h.post(() -> {
                proc = null;
                procPid = -1;
                running = false;
                if (buf.length() > 0 && buf.charAt(buf.length() - 1) != '\n') append("\n", 0, false);
                if (ferr != null && !stopped) append("无法运行：" + ferr + "\n", R.color.error, false);
                else append(stopped ? "已停止\n" : "退出码 " + frc + " · " + Format.duration(ms) + "\n", stopped || frc != 0 ? R.color.error : R.color.text3, false);
                if (!newDir.isEmpty() && new File(newDir).isDirectory()) cwd = newDir;
                bind();
            });
        });
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private void stop() {
        Process p = proc;
        if (p == null) return;
        stopped = true;
        int pid = procPid;
        if (pid > 0) killTree(pid);
        p.destroy();
    }

    /** 子进程的 pid（安卓的 Process 没有公开 pid()）。 */
    private static int pidOf(Process p) {
        try {
            java.lang.reflect.Method m = p.getClass().getMethod("pid");
            Object v = m.invoke(p);
            if (v instanceof Number) return ((Number) v).intValue();
        } catch (Exception ignored) {
            // 老版本没有 pid()
        }
        for (Class<?> c = p.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("pid");
                f.setAccessible(true);
                return f.getInt(p);
            } catch (Exception ignored) {
                // 下一个父类
            }
        }
        return -1;
    }

    /** 停止：shell 和它启动的所有子进程（npm、node……）都结束掉。读 /proc 找子孙进程（同一用户的才杀得动）。 */
    private static void killTree(int root) {
        Map<Integer, List<Integer>> kids = new HashMap<>();
        File[] ps = new File("/proc").listFiles();
        if (ps != null) {
            for (File d : ps) {
                String n = d.getName();
                if (!n.matches("\\d+")) continue;
                try {
                    String stat = new String(java.nio.file.Files.readAllBytes(new File(d, "stat").toPath()), StandardCharsets.UTF_8);
                    String[] f = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
                    int ppid = Integer.parseInt(f[1]);
                    List<Integer> l = kids.get(ppid);
                    if (l == null) kids.put(ppid, l = new ArrayList<>());
                    l.add(Integer.parseInt(n));
                } catch (Exception ignored) {
                    // 进程已经没了或读不了
                }
            }
        }
        List<Integer> all = new ArrayList<>();
        List<Integer> q = new ArrayList<>();
        q.add(root);
        while (!q.isEmpty()) {
            int x = q.remove(0);
            all.add(x);
            List<Integer> l = kids.get(x);
            if (l != null) q.addAll(l);
        }
        for (int i = all.size() - 1; i >= 0; i--) {
            try {
                android.os.Process.sendSignal(all.get(i), 9);
            } catch (Exception ignored) {
                // 不是自己的进程
            }
        }
    }

    /* ---------------- 菜单、常用命令 ---------------- */

    private void menu(View anchor) {
        Overlay ov = new Overlay(this, false);
        LinearLayout card = Overlay.card(this, 24);
        card.setPadding(0, dp(8), 0, dp(8));
        card.addView(Overlay.iconRow(this, R.drawable.ic_terminal_line, "常用命令", R.color.text, v -> {
            ov.close();
            commandsSheet();
        }));
        card.addView(Overlay.iconRow(this, R.drawable.ic_erase, "清屏", R.color.text, v -> {
            ov.close();
            buf.clear();
            out.setText("");
        }));
        card.addView(Overlay.iconRow(this, R.drawable.ic_copy_line, "复制全部输出", R.color.text, v -> {
            ov.close();
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("终端输出", buf.toString()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        }));
        String ws = rt.prefs.workspace();
        if (ws != null && new File(ws).isDirectory()) card.addView(Overlay.iconRow(this, R.drawable.ic_folder_line, "回到工作区", R.color.text, v -> {
            ov.close();
            cwd = ws;
            bind();
            append("\n已切换到 " + ws + "\n", R.color.text3, false);
        }));
        card.addView(Overlay.iconRow(this, R.drawable.ic_gear_line, "开发工具设置", R.color.text, v -> {
            ov.close();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS));
        }));
        int[] b = ov.boundsOf(anchor);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = b[3] + dp(6);
        lp.rightMargin = Math.max(dp(8), ov.layerWidth() - b[2]);
        card.setMinimumWidth(dp(200));
        ov.add(card, lp);
        ov.show();
    }

    /** 常用命令面板：点一条放进输入框（尖括号的部分自己改）。 */
    void commandsSheet() {
        Overlay ov = new Overlay(this, true);
        LinearLayout sheet = Ui.vbox(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.color(this, R.color.card));
        float r = dp(32);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(0, dp(12), 0, dp(12));
        View handle = new View(this);
        handle.setBackground(Ui.round(this, R.color.border, 0, 2));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(36), dp(4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, hl);
        TextView title = Overlay.strong(this, "常用命令", 20);
        title.setPadding(dp(24), dp(14), dp(24), dp(2));
        sheet.addView(title);
        TextView sub = Ui.text(this, "点一条放进输入框；尖括号里的内容换成你自己的。", 13, R.color.text3);
        sub.setPadding(dp(24), 0, dp(24), dp(6));
        sheet.addView(sub);
        ScrollView sv = new ScrollView(this);
        LinearLayout list = Ui.vbox(this);
        for (Commands.Group g : Commands.GROUPS) {
            list.addView(Overlay.header(this, g.title));
            for (String[] it : g.items) {
                LinearLayout row = Ui.vbox(this);
                row.setPadding(dp(24), dp(9), dp(24), dp(9));
                TextView c = Ui.mono(this, it[0]);
                c.setTextSize(14);
                row.addView(c);
                TextView d = Ui.text(this, it[1], 13, R.color.text3);
                row.addView(d, Ui.matchWrap(this, 2));
                Ui.ripple(row, false);
                row.setOnClickListener(v -> {
                    ov.close();
                    input.setText(it[0]);
                    int lt = it[0].indexOf('<');
                    int gt = it[0].indexOf('>', Math.max(lt, 0));
                    if (lt >= 0 && gt > lt) input.setSelection(lt, gt + 1); // 选中要改的部分
                    else input.setSelection(it[0].length());
                    input.requestFocus();
                });
                list.addView(row);
            }
        }
        sv.addView(list);
        int maxH = Math.round(ov.layerHeight() * 0.72f);
        sheet.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH));
        ov.addSheet(sheet);
        ov.show();
    }
}
