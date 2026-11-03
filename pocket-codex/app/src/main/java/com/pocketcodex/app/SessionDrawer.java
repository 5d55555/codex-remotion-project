package com.pocketcodex.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ChatGPT 那样从左边拉出来的抽屉：顶部品牌名 + 圆形搜索键；几个入口（文件、终端、工作区、开发工具、用量）；
 * “置顶”和“聊天”两组会话；底部左边蓝色“新对话”胶囊、右边圆形设置键。长按会话：置顶 / 重命名 / 归档。
 */
final class SessionDrawer {
    interface Host {
        void openThread(CodexRuntimeManager.ThreadSummary t);

        void newChat();

        void closeDrawer();

        String currentThreadId();

        void chooseWorkspace();
    }

    private final AppCompatActivity a;
    private final CodexRuntimeManager rt;
    private final Host host;
    final FrameLayout root;
    private final LinearLayout list;
    private final EditText search;
    private final TextView wsLabel;
    private String query = "";

    SessionDrawer(AppCompatActivity a, CodexRuntimeManager rt, Host host) {
        this.a = a;
        this.rt = rt;
        this.host = host;
        root = new FrameLayout(a);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Ui.color(a, R.color.bg), Ui.color(a, R.color.bg_low)}));
        root.setClickable(true);
        root.setFitsSystemWindows(true);
        LinearLayout col = Ui.vbox(a);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部：品牌名 + 搜索
        LinearLayout head = Ui.hbox(a);
        head.setPadding(dp(28), dp(14), dp(18), dp(6));
        TextView brand = Ui.text(a, "口袋工坊", 28, R.color.text);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        head.addView(brand, Ui.weight1());
        ImageView sb = Ui.roundButton(a, R.drawable.ic_search_line, "搜索对话", null);
        head.addView(sb, new LinearLayout.LayoutParams(dp(50), dp(50)));
        col.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        search = new EditText(a);
        search.setHint("搜索对话");
        search.setSingleLine();
        search.setTextSize(16);
        search.setTextColor(Ui.color(a, R.color.text));
        search.setHintTextColor(Ui.color(a, R.color.text3));
        search.setBackground(Ui.round(a, R.color.surface2, 0, 24));
        search.setPadding(dp(18), dp(10), dp(18), dp(10));
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                bindList();
            }
        });
        LinearLayout.LayoutParams sl = Ui.matchWrap();
        sl.leftMargin = sl.rightMargin = dp(20);
        sl.bottomMargin = dp(6);
        col.addView(search, sl);
        sb.setOnClickListener(v -> {
            boolean show = search.getVisibility() != View.VISIBLE;
            search.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) search.requestFocus();
            else search.setText("");
        });

        ScrollView sv = new ScrollView(a);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setPadding(0, dp(6), 0, dp(110));
        LinearLayout content = Ui.vbox(a);
        sv.addView(content);
        // 列表滚到顶部标题下面时淡出，不是一刀切
        FrameLayout svHost = new FrameLayout(a);
        svHost.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View listTop = Ui.fade(a, R.color.bg, true);
        svHost.addView(listTop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22), Gravity.TOP));
        Ui.followScroll(listTop, sv, true);
        col.addView(svHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(navRow(R.drawable.ic_folder_line, "文件", v -> open(new Intent(a, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_FILES))));
        content.addView(navRow(R.drawable.ic_terminal_line, "终端", v -> open(new Intent(a, TerminalActivity.class))));
        LinearLayout ws = navRow(R.drawable.ic_box_line, "工作区", v -> {
            host.closeDrawer();
            host.chooseWorkspace();
        });
        wsLabel = Ui.text(a, "", 15, R.color.text3);
        wsLabel.setSingleLine();
        wsLabel.setEllipsize(TextUtils.TruncateAt.END);
        wsLabel.setMaxWidth(dp(130));
        ws.addView(wsLabel);
        content.addView(ws);
        content.addView(navRow(R.drawable.ic_code_line, "开发工具", v -> open(new Intent(a, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS)
                .putExtra(MainActivity.EXTRA_SECTION, "devtools"))));
        content.addView(navRow(R.drawable.ic_chart_line, "用量", v -> {
            host.closeDrawer();
            UsageDialog.show(a, rt, null);
        }));
        list = Ui.vbox(a);
        content.addView(list, Ui.matchWrap(a, 12));

        // 底部：新对话（强调色胶囊）+ 设置（圆形），后面的列表淡出
        View listBottom = Ui.fade(a, R.color.bg_low, false);
        root.addView(listBottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130), Gravity.BOTTOM));
        Ui.followScroll(listBottom, sv, false);
        FrameLayout bottom = new FrameLayout(a);
        LinearLayout pill = Ui.hbox(a);
        pill.setBackground(roundColor(Ui.accent(a), dp(30)));
        Ui.elevate(pill, 6);
        pill.setPadding(dp(22), 0, dp(28), 0);
        ImageView pi = new ImageView(a);
        pi.setImageResource(R.drawable.ic_compose);
        pi.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.onAccent(a)));
        pill.addView(pi, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView pt = Ui.text(a, "新对话", 18, R.color.text);
        pt.setTextColor(Ui.onAccent(a));
        pt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams ptl = Ui.wrap();
        ptl.leftMargin = dp(12);
        pill.addView(pt, ptl);
        Ui.ripple(pill, false);
        pill.setContentDescription("新对话");
        pill.setOnClickListener(v -> {
            host.closeDrawer();
            host.newChat();
        });
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(60), Gravity.START | Gravity.CENTER_VERTICAL);
        pl.leftMargin = dp(20);
        bottom.addView(pill, pl);
        ImageView gear = Ui.roundButton(a, R.drawable.ic_gear_line, "设置", v -> open(new Intent(a, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS)));
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.END | Gravity.CENTER_VERTICAL);
        gl.rightMargin = dp(20);
        bottom.addView(gear, gl);
        root.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92), Gravity.BOTTOM));
        bind();
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    private static GradientDrawable roundColor(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    private void open(Intent i) {
        host.closeDrawer();
        a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private LinearLayout navRow(int icon, String label, View.OnClickListener l) {
        LinearLayout row = Ui.hbox(a);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(28), 0, dp(24), 0);
        row.addView(Ui.icon(a, icon, R.color.text, 26));
        TextView t = Ui.text(a, label, 18, R.color.text);
        LinearLayout.LayoutParams tl = Ui.weight1();
        tl.leftMargin = dp(20);
        row.addView(t, tl);
        Ui.ripple(row, false);
        row.setOnClickListener(l);
        row.setContentDescription(label);
        return row;
    }

    /** 刷新（打开抽屉时、会话列表变了时）。 */
    void bind() {
        wsLabel.setText(Workspaces.name(rt.prefs.workspace()));
        bindList();
    }

    private void bindList() {
        list.removeAllViews();
        List<String> pins = rt.prefs.pinned();
        List<CodexRuntimeManager.ThreadSummary> pinned = new ArrayList<>(), rest = new ArrayList<>();
        for (CodexRuntimeManager.ThreadSummary t : rt.threads) {
            if (!query.isEmpty() && !t.title().toLowerCase(Locale.ROOT).contains(query)
                    && (t.preview == null || !t.preview.toLowerCase(Locale.ROOT).contains(query))) continue;
            if (pins.contains(t.id)) pinned.add(t);
            else rest.add(t);
        }
        if (!pinned.isEmpty()) {
            list.addView(header("置顶"));
            for (CodexRuntimeManager.ThreadSummary t : pinned) list.addView(threadRow(t, true));
        }
        list.addView(header("聊天"));
        if (rest.isEmpty()) {
            TextView e = Ui.text(a, !query.isEmpty() ? "没有找到相关对话" : rt.threadsLoaded ? "还没有对话" : "正在载入…", 15, R.color.text3);
            e.setPadding(dp(28), dp(8), dp(28), dp(8));
            list.addView(e);
        }
        for (CodexRuntimeManager.ThreadSummary t : rest) list.addView(threadRow(t, false));
    }

    private TextView header(String s) {
        TextView t = Ui.text(a, s, 17, R.color.text);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(28), dp(22), dp(28), dp(8));
        return t;
    }

    private View threadRow(CodexRuntimeManager.ThreadSummary t, boolean pinned) {
        LinearLayout outer = Ui.vbox(a);
        outer.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout row = Ui.hbox(a);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(16), 0, dp(14), 0);
        boolean current = t.id.equals(host.currentThreadId());
        if (current) row.setBackground(Ui.round(a, R.color.surface2, 0, 18));
        if (pinned) {
            ImageView ic = Ui.icon(a, R.drawable.ic_bubble_line, R.color.text, 22);
            LinearLayout.LayoutParams il = (LinearLayout.LayoutParams) ic.getLayoutParams();
            il.rightMargin = dp(16);
            row.addView(ic, il);
        }
        TextView tv = Ui.text(a, t.title(), 17, R.color.text);
        tv.setSingleLine();
        tv.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tv, Ui.weight1());
        if (t.id.equals(rt.runningThreadId)) {
            View dot = new View(a);
            dot.setBackground(Ui.circle(a, rt.pendingApprovalCount() > 0 ? R.color.warning : R.color.success));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(8), dp(8));
            dl.leftMargin = dp(10);
            row.addView(dot, dl);
            row.setContentDescription(t.title() + (rt.pendingApprovalCount() > 0 ? "，等待确认" : "，正在执行"));
        } else {
            row.setContentDescription(t.title());
        }
        Ui.ripple(row, false);
        row.setOnClickListener(v -> {
            host.closeDrawer();
            if (!current) host.openThread(t);
        });
        row.setOnLongClickListener(v -> {
            threadMenu(row, t);
            return true;
        });
        outer.addView(row, Ui.matchWrap());
        return outer;
    }

    private void threadMenu(View anchor, CodexRuntimeManager.ThreadSummary t) {
        List<String> pins = rt.prefs.pinned();
        boolean pinned = pins.contains(t.id);
        List<Overlay.Opt> l = new ArrayList<>();
        l.add(new Overlay.Opt(pinned ? "取消置顶" : "置顶", null, false, () -> {
            List<String> p = rt.prefs.pinned();
            if (p.contains(t.id)) p.remove(t.id);
            else p.add(0, t.id);
            rt.prefs.setPinned(p);
            bindList();
        }));
        l.add(new Overlay.Opt("重命名", null, false, () -> rename(t)));
        l.add(new Overlay.Opt("归档", "从列表移除，不删除文件", false, () -> {
            List<String> p = rt.prefs.pinned();
            p.remove(t.id);
            rt.prefs.setPinned(p);
            rt.archiveThread(t.id);
        }));
        Overlay.dropdown(a, anchor, l, true, null, null);
    }

    private void rename(CodexRuntimeManager.ThreadSummary t) {
        final EditText et = new EditText(a);
        et.setText(t.title());
        et.setSingleLine();
        LinearLayout box = Ui.vbox(a);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(et);
        new AlertDialog.Builder(a).setTitle("重命名").setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) rt.renameThread(t.id, n);
                }).setNegativeButton("取消", null).show();
    }
}
