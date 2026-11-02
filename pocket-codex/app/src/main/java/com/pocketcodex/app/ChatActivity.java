package com.pocketcodex.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.pocketcodex.core.Conversation;
import java.io.File;

/**
 * 对话页：消息列表 + 输入区。新对话第一次发送时才创建 thread。
 * 回合运行中：“停止”可用；输入内容后发送按钮变成“追加”（turn/steer）。审批用底部弹窗。
 */
public final class ChatActivity extends AppCompatActivity implements CodexRuntimeManager.Listener, CodexRuntimeManager.ConversationListener {
    static final String EXTRA_THREAD = "thread", EXTRA_CWD = "cwd", EXTRA_DRAFT = "draft";

    private CodexRuntimeManager rt;
    private Conversation conv;
    private String threadId, cwd;
    private ChatAdapter adapter;
    private RecyclerView list;
    private LinearLayoutManager lm;
    private TextView title, subtitle, modeStrip, statusText, planText, pendingText, emptyHint, configText;
    private LinearLayout planBar, planSteps, pendingBar, emptyBox;
    private LinearProgressIndicator planProgress;
    private EditText input;
    private MaterialButton send, stop, toLatest, olderBar;
    private final java.util.Set<Integer> changedRows = new java.util.HashSet<>();
    private InputDialog inputDialog;
    private ApprovalSheet sheet;
    private boolean sending, stopping, loading;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean refreshPending;
    private boolean structurePending;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rt = CodexRuntimeManager.get(this);
        threadId = getIntent().getStringExtra(EXTRA_THREAD);
        cwd = getIntent().getStringExtra(EXTRA_CWD);
        if (b != null) { // 旋转/重建：新对话发送后已经有 thread 了
            if (b.getString(EXTRA_THREAD) != null) threadId = b.getString(EXTRA_THREAD);
            if (b.getString(EXTRA_CWD) != null) cwd = b.getString(EXTRA_CWD);
        }
        if (cwd == null) cwd = rt.prefs.workspace();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        sheet = new ApprovalSheet(this, rt);
        setContentView(build());
        String draft = getIntent().getStringExtra(EXTRA_DRAFT);
        if (draft != null) input.setText(draft);
        if (threadId != null) {
            String saved = getSharedPreferences("drafts", MODE_PRIVATE).getString(threadId, null);
            if (saved != null && draft == null) input.setText(saved);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(EXTRA_THREAD, threadId);
        out.putString(EXTRA_CWD, cwd);
    }

    /* ---------------- 布局 ---------------- */

    private View build() {
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        root.setFitsSystemWindows(true);
        // 顶栏
        LinearLayout bar = Ui.hbox(this);
        bar.setMinimumHeight(Ui.dp(this, 60));
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        bar.addView(iconButton(R.drawable.ic_back, "返回", v -> finish()));
        LinearLayout tbox = Ui.vbox(this);
        title = Ui.text(this, "新对话", 16, R.color.text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine();
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle = Ui.small(this, "");
        subtitle.setSingleLine();
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        tbox.addView(title);
        tbox.addView(subtitle);
        bar.addView(tbox, Ui.weight1());
        bar.addView(iconButton(R.drawable.ic_info, "用量", v -> UsageDialog.show(this, rt, conv)));
        bar.addView(iconButton(R.drawable.ic_more, "更多", v -> moreMenu()));
        root.addView(bar);
        modeStrip = Ui.small(this, "");
        modeStrip.setPadding(Ui.dp(this, 16), Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 6));
        root.addView(modeStrip);
        // 计划条
        planBar = Ui.vbox(this);
        planBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 8));
        planBar.setBackgroundColor(Ui.color(this, R.color.card));
        LinearLayout prow = Ui.hbox(this);
        prow.addView(Ui.icon(this, R.drawable.ic_plan, R.color.primary, 18));
        planText = Ui.text(this, "", 14, R.color.text);
        LinearLayout.LayoutParams pl = Ui.weight1();
        pl.leftMargin = Ui.dp(this, 8);
        prow.addView(planText, pl);
        prow.addView(Ui.icon(this, R.drawable.ic_expand, R.color.text2, 20));
        planBar.addView(prow);
        planProgress = new LinearProgressIndicator(this);
        planProgress.setTrackStopIndicatorSize(0);
        planProgress.setIndicatorColor(Ui.color(this, R.color.primary));
        planProgress.setTrackColor(Ui.color(this, R.color.surface2));
        planProgress.setTrackThickness(Ui.dp(this, 3));
        planBar.addView(planProgress, Ui.matchWrap(this, 6));
        planSteps = Ui.vbox(this);
        planSteps.setVisibility(View.GONE);
        planBar.addView(planSteps, Ui.matchWrap(this, 4));
        planBar.setOnClickListener(v -> planSteps.setVisibility(planSteps.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        planBar.setVisibility(View.GONE);
        root.addView(planBar, Ui.matchWrap());
        // 更早的记录（只在有更早历史时显示）
        olderBar = Ui.textButton(this, "加载更早的记录", v -> loadOlder());
        olderBar.setVisibility(View.GONE);
        root.addView(olderBar, Ui.matchWrap());
        // 消息列表
        FrameLayout listHost = new FrameLayout(this);
        list = new RecyclerView(this);
        lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        list.setLayoutManager(lm);
        list.setItemAnimator(null); // 流式更新不要闪
        list.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        list.setClipToPadding(false);
        adapter = new ChatAdapter(this);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                toLatest.setVisibility(nearBottom() ? View.GONE : View.VISIBLE);
            }
        });
        listHost.addView(list);
        emptyBox = Ui.vbox(this);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.setPadding(Ui.dp(this, 24), Ui.dp(this, 48), Ui.dp(this, 24), 0);
        TextView e1 = Ui.text(this, "想先改点什么？", 20, R.color.text);
        e1.setTypeface(Typeface.DEFAULT_BOLD);
        emptyBox.addView(e1, Ui.wrap());
        emptyHint = Ui.caption(this, "可以创建文本、整理内容，或修改已有文件。");
        emptyHint.setGravity(Gravity.CENTER);
        emptyBox.addView(emptyHint, Ui.matchWrap(this, 8));
        for (String ex : new String[]{"创建一份待办清单", "看看这个文件夹里有什么", "帮我整理这份文档"}) {
            MaterialButton chip = Ui.outlined(this, ex, v -> {
                input.setText(ex);
                input.setSelection(ex.length());
                input.requestFocus();
            });
            emptyBox.addView(chip, Ui.matchWrap(this, 8));
        }
        listHost.addView(emptyBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        toLatest = Ui.primary(this, "回到最新", v -> scrollToBottom());
        toLatest.setIconResource(R.drawable.ic_down);
        toLatest.setVisibility(View.GONE);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fl.bottomMargin = Ui.dp(this, 12);
        listHost.addView(toLatest, fl);
        root.addView(listHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        // 待处理条
        pendingBar = Ui.hbox(this);
        pendingBar.setBackgroundColor(Ui.color(this, R.color.warning_soft));
        pendingBar.setMinimumHeight(Ui.dp(this, 48));
        pendingBar.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        pendingBar.addView(Ui.icon(this, R.drawable.ic_warning, R.color.warning, 20));
        pendingText = Ui.text(this, "", 14, R.color.text);
        LinearLayout.LayoutParams ptl = Ui.weight1();
        ptl.leftMargin = Ui.dp(this, 10);
        pendingBar.addView(pendingText, ptl);
        pendingBar.addView(Ui.textButton(this, "查看", v -> showPending(true)));
        pendingBar.setVisibility(View.GONE);
        root.addView(pendingBar, Ui.matchWrap());
        // 输入区
        LinearLayout inputArea = Ui.vbox(this);
        inputArea.setBackgroundColor(Ui.color(this, R.color.card));
        inputArea.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 8));
        View top = new View(this);
        top.setBackgroundColor(Ui.color(this, R.color.border));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1) / 2)));
        configText = Ui.text(this, "", 12, R.color.text2);
        configText.setMinHeight(Ui.dp(this, 32));
        configText.setGravity(Gravity.CENTER_VERTICAL);
        configText.setOnClickListener(v -> new AlertDialog.Builder(this).setItems(new String[]{"选择模型", "推理强度"}, (d, w) -> {
            if (w == 0) ModelPicker.show(this, rt, this::bindStatus);
            else ModelPicker.showEffort(this, rt, this::bindStatus);
        }).show());
        inputArea.addView(configText);
        input = new EditText(this);
        input.setHint("描述你想修改的内容…");
        input.setTextSize(16);
        input.setMinLines(1);
        input.setMaxLines(6);
        input.setTextColor(Ui.color(this, R.color.text));
        input.setHintTextColor(Ui.color(this, R.color.text2));
        input.setBackground(Ui.round(this, R.color.surface2, 0, 20));
        input.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                bindStatus();
            }
        });
        input.setOnKeyListener((v, code, ev) -> {
            // 物理键盘 Ctrl+Enter 发送
            if (code == KeyEvent.KEYCODE_ENTER && ev.isCtrlPressed() && ev.getAction() == KeyEvent.ACTION_DOWN) {
                onSend();
                return true;
            }
            return false;
        });
        inputArea.addView(input, Ui.matchWrap());
        LinearLayout actions = Ui.hbox(this);
        actions.setMinimumHeight(Ui.dp(this, 52));
        statusText = Ui.text(this, "", 12, R.color.text2);
        actions.addView(statusText, Ui.weight1());
        stop = Ui.outlined(this, "停止", v -> stopTurn());
        stop.setIconResource(R.drawable.ic_stop);
        stop.setTextColor(Ui.color(this, R.color.error));
        stop.setIconTint(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.error)));
        LinearLayout.LayoutParams sl = Ui.wrap();
        sl.rightMargin = Ui.dp(this, 8);
        actions.addView(stop, sl);
        send = Ui.primary(this, "发送", v -> onSend());
        send.setIconResource(R.drawable.ic_send);
        actions.addView(send, Ui.wrap());
        inputArea.addView(actions, Ui.matchWrap(this, 4));
        root.addView(inputArea, Ui.matchWrap());
        return root;
    }

    private ImageButton iconButton(int icon, String desc, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        b.setContentDescription(desc);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.text)));
        b.setMinimumWidth(Ui.dp(this, 48));
        b.setMinimumHeight(Ui.dp(this, 48));
        b.setOnClickListener(l);
        return b;
    }

    /* ---------------- 生命周期 ---------------- */

    @Override
    protected void onStart() {
        super.onStart();
        rt.addListener(this);
        rt.addConversationListener(this);
        if (rt.stage == CodexRuntimeManager.Stage.CHECKING) rt.check();
        attach();
        bindAll();
        if (conv != null) { // 回到页面：还挂着的审批/提问重新弹出来
            adapter.notifyDataSetChanged();
            showPending(true);
        }
    }

    @Override
    protected void onStop() {
        rt.removeListener(this);
        rt.removeConversationListener(this);
        sheet.dismiss();
        if (inputDialog != null) inputDialog.dismiss();
        if (threadId != null) getSharedPreferences("drafts", MODE_PRIVATE).edit().putString(threadId, input.getText().toString()).apply();
        super.onStop();
    }

    /** 打开已有会话（resume + 历史）。新对话等第一次发送时再建。 */
    private void attach() {
        if (conv != null || threadId == null || loading) return;
        Conversation c = rt.conversation(threadId);
        if (c != null) {
            setConversation(c);
            return;
        }
        if (!rt.serverAlive()) return; // 等 READY 后 onRuntimeChanged 再来
        loading = true;
        statusText.setText("正在载入历史…");
        rt.openThread(threadId, cwd, (cc, err) -> {
            loading = false;
            if (cc == null) {
                statusText.setText("无法打开这个会话：" + err);
                return;
            }
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            setConversation(cc);
        });
    }

    private void setConversation(Conversation c) {
        conv = c;
        threadId = c.threadId;
        adapter.setConversation(c, cwd);
        scrollToBottom();
        bindAll();
        showPending(true);
    }

    @Override
    public void onRuntimeChanged() {
        attach();
        bindStatus();
    }

    @Override
    public void onConversationUpdate(Conversation c, Conversation.Update u) {
        if (conv == null || c != conv) return;
        if (u.approvals) {
            showPending(true);
            adapter.notifyDataSetChanged();
        }
        if (u.structure) structurePending = true;
        else changedRows.addAll(u.changed);
        // 合并刷新：流式 delta 很密，最多每 100ms 刷一次列表
        if (!refreshPending) {
            refreshPending = true;
            h.postDelayed(flush, u.structure || u.turnEnded || u.approvals ? 0 : 100);
        }
        if (u.status || u.turnEnded || u.approvals) bindStatus();
        if (u.turnEnded) stopping = false;
    }

    private final Runnable flush = new Runnable() {
        @Override
        public void run() {
            refreshPending = false;
            boolean stick = nearBottom();
            if (structurePending) {
                structurePending = false;
                changedRows.clear();
                adapter.notifyDataSetChanged();
            } else {
                // 只刷这段时间里真的变了的卡片
                int n = adapter.getItemCount();
                for (int i : changedRows) if (i >= 0 && i < n) adapter.notifyItemChanged(i);
                changedRows.clear();
            }
            olderBar.setVisibility(conv != null && conv.olderCursor != null ? View.VISIBLE : View.GONE);
            emptyBox.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            if (stick) scrollToBottom();
        }
    };

    private boolean nearBottom() {
        if (adapter.getItemCount() == 0) return true;
        return !list.canScrollVertically(1) || list.computeVerticalScrollRange() - list.computeVerticalScrollOffset() - list.computeVerticalScrollExtent() < Ui.dp(this, 80);
    }

    private void scrollToBottom() {
        int n = adapter.getItemCount();
        if (n > 0) list.scrollToPosition(n - 1);
        toLatest.setVisibility(View.GONE);
    }

    /* ---------------- 状态显示 ---------------- */

    private void bindAll() {
        emptyBox.setVisibility(adapter.getItemCount() == 0 && !loading ? View.VISIBLE : View.GONE);
        bindStatus();
    }

    private String threadTitle() {
        if (conv != null && conv.threadName != null) return conv.threadName;
        if (threadId != null) for (CodexRuntimeManager.ThreadSummary t : rt.threads) if (t.id.equals(threadId)) return t.title();
        return "新对话";
    }

    private void bindStatus() {
        if (title == null) return;
        title.setText(threadTitle());
        boolean running = conv != null && conv.running();
        int approvals = conv == null ? 0 : conv.pendingApprovals().size() + conv.pendingInputs().size();
        String state;
        if (rt.stage == CodexRuntimeManager.Stage.STARTING || rt.stage == CodexRuntimeManager.Stage.CHECKING) state = "正在启动 Codex…";
        else if (rt.stage != CodexRuntimeManager.Stage.READY) state = "Codex 没有运行";
        else if (stopping) state = "正在停止…";
        else if (approvals > 0) state = "等待你的确认";
        else if (running) state = retrying() ? "连接中断，正在重试" : "正在处理";
        else if (conv != null && "interrupted".equals(conv.turnStatus)) state = "已停止";
        else if (conv != null && "failed".equals(conv.turnStatus)) state = "本轮未完成";
        else if (conv != null && "completed".equals(conv.turnStatus)) state = "已完成";
        else state = "准备就绪";
        subtitle.setText(Workspaces.name(cwd) + " · " + state);
        boolean auto = Prefs.MODE_AUTO.equals(rt.prefs.mode());
        modeStrip.setText(auto ? "完全自动：命令和文件改动不再逐项确认" : "每步确认：运行命令、修改文件前都会问你");
        modeStrip.setTextColor(Ui.color(this, auto ? R.color.warning : R.color.text2));
        CodexRuntimeManager.ModelInfo mi = rt.currentModel();
        configText.setText("模型：" + (mi == null ? "默认" : mi.displayName) + "   ·   推理：" + ModelPicker.effortName(rt.prefs.effort())
                + (running ? "   （下一轮生效）" : ""));
        // 计划
        if (conv != null && !conv.plan.isEmpty()) {
            int done = 0;
            planSteps.removeAllViews();
            for (Conversation.PlanStep s : conv.plan) {
                if ("completed".equals(s.status)) done++;
                String mark = "completed".equals(s.status) ? "✓ " : "inProgress".equals(s.status) ? "▶ " : "○ ";
                TextView st = Ui.caption(this, mark + s.step);
                if ("inProgress".equals(s.status)) st.setTextColor(Ui.color(this, R.color.text));
                planSteps.addView(st, Ui.matchWrap(this, 4));
            }
            boolean ended = !running && done < conv.plan.size();
            planText.setText("计划 · 已完成 " + done + "/" + conv.plan.size() + (ended ? " · 本轮已结束，部分步骤未完成" : ""));
            planProgress.setMax(conv.plan.size());
            planProgress.setProgressCompat(done, true);
            planBar.setVisibility(View.VISIBLE);
        } else {
            planBar.setVisibility(View.GONE);
        }
        // 待处理
        pendingBar.setVisibility(approvals > 0 ? View.VISIBLE : View.GONE);
        boolean submitting = conv != null && !conv.pendingApprovals().isEmpty() && conv.pendingApprovals().get(0).submitting;
        boolean asking = conv != null && conv.pendingApprovals().isEmpty() && !conv.pendingInputs().isEmpty();
        pendingText.setText(submitting ? "正在提交决定…" : asking ? "Codex 在问你问题" : "有 " + approvals + " 项操作等待确认");
        if (olderBar != null) olderBar.setVisibility(conv != null && conv.olderCursor != null ? View.VISIBLE : View.GONE);
        // 按钮
        boolean otherRunning = rt.runningThreadId != null && (conv == null || !rt.runningThreadId.equals(conv.threadId));
        boolean hasText = input.getText().toString().trim().length() > 0;
        boolean ready = rt.stage == CodexRuntimeManager.Stage.READY;
        stop.setVisibility(running ? View.VISIBLE : View.GONE);
        stop.setEnabled(!stopping);
        send.setText(running ? "追加" : "发送");
        send.setEnabled(ready && hasText && !sending && !otherRunning);
        String hint;
        if (!ready) hint = rt.stage == CodexRuntimeManager.Stage.ERROR ? "Codex 没有运行，到会话页重试" : "正在启动 Codex…";
        else if (otherRunning) hint = "另一个会话正在运行，结束后可以发送";
        else if (sending) hint = "正在提交…";
        else if (running) hint = hasText ? "追加的内容会加入正在进行的这一轮" : "可以随时停止";
        else if (conv != null && "interrupted".equals(conv.turnStatus)) hint = "已发生的文件改动会保留";
        else hint = "";
        if (!loading) statusText.setText(hint);
    }

    /** 先处理审批（按到达顺序），没有审批再处理 Agent 的提问。 */
    private void showPending(boolean open) {
        if (conv == null) return;
        sheet.update(conv, title.getText().toString(), open);
        if (!conv.pendingApprovals().isEmpty() || sheet.showing()) return;
        java.util.List<Conversation.InputRequest> qs = conv.pendingInputs();
        if (qs.isEmpty()) {
            if (inputDialog != null) inputDialog.dismiss();
            return;
        }
        Conversation.InputRequest r = qs.get(0);
        if (inputDialog != null && inputDialog.isFor(r)) return;
        if (inputDialog != null) inputDialog.dismiss();
        if (!open) return;
        inputDialog = new InputDialog(this, rt, conv, r);
        inputDialog.show();
    }

    private void loadOlder() {
        if (conv == null) return;
        olderBar.setEnabled(false);
        olderBar.setText("正在加载…");
        rt.loadOlder(conv, err -> {
            olderBar.setEnabled(true);
            olderBar.setText("加载更早的记录");
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            olderBar.setVisibility(conv.olderCursor != null ? View.VISIBLE : View.GONE);
        });
    }

    private boolean retrying() {
        if (conv == null) return false;
        for (int i = conv.items.size() - 1; i >= 0 && i >= conv.items.size() - 3; i--) {
            if ("retry".equals(conv.items.get(i).level)) return true;
        }
        return false;
    }

    /* ---------------- 操作 ---------------- */

    private void onSend() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty() || sending) return;
        if (!rt.prefs.sandboxNoticeSeen()) {
            new AlertDialog.Builder(this).setTitle("在本机运行")
                    .setMessage("文件和命令在本机运行，受 Android 应用沙箱限制。Agent 可能访问本应用内的其他文件；项目目录不是独立隔离区。模型请求需要联网。")
                    .setPositiveButton("知道了", (d, w) -> {
                        rt.prefs.setSandboxNoticeSeen();
                        onSend();
                    }).setNegativeButton("取消", null).show();
            return;
        }
        if (conv != null && conv.running()) { // 追加
            sending = true;
            bindStatus();
            rt.steer(conv, text, err -> {
                sending = false;
                if (err == null) input.setText("");
                else Toast.makeText(this, err + "（内容已保留）", Toast.LENGTH_LONG).show();
                bindStatus();
            });
            return;
        }
        sending = true;
        bindStatus();
        if (conv == null) {
            File ws = new File(cwd);
            rt.newThread(ws, (c, err) -> {
                if (c == null) {
                    sending = false;
                    Toast.makeText(this, "创建对话失败：" + err, Toast.LENGTH_LONG).show();
                    bindStatus();
                    return;
                }
                setConversation(c);
                doSend(text);
            });
        } else {
            doSend(text);
        }
    }

    private void doSend(String text) {
        input.setText("");
        rt.sendTurn(conv, text, err -> {
            sending = false;
            if (err != null) {
                input.setText(text);
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            }
            bindStatus();
        });
        emptyBox.setVisibility(View.GONE);
        scrollToBottom();
    }

    private void stopTurn() {
        if (conv == null || !conv.running()) return;
        stopping = true;
        rt.interrupt(conv);
        bindStatus();
    }

    private void moreMenu() {
        new AlertDialog.Builder(this).setItems(new String[]{"本轮改动", "重命名", "打开工作区文件", "用量"}, (d, w) -> {
            if (w == 0) {
                String diff = conv == null ? null : conv.turnDiff;
                if (diff == null || diff.isEmpty()) Toast.makeText(this, "这一轮还没有改动记录", Toast.LENGTH_SHORT).show();
                else startActivity(new Intent(this, FileViewerActivity.class).putExtra(FileViewerActivity.EXTRA_TEXT, diff)
                        .putExtra(FileViewerActivity.EXTRA_DIFF, true).putExtra(FileViewerActivity.EXTRA_TITLE, "本轮改动"));
            } else if (w == 1) {
                if (threadId == null) {
                    Toast.makeText(this, "发送第一条消息后才能重命名", Toast.LENGTH_SHORT).show();
                    return;
                }
                final EditText et = new EditText(this);
                et.setText(threadTitle());
                et.setSingleLine();
                LinearLayout box = Ui.vbox(this);
                box.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
                box.addView(et);
                new AlertDialog.Builder(this).setTitle("重命名").setView(box)
                        .setPositiveButton("保存", (d2, w2) -> {
                            String n = et.getText().toString().trim();
                            if (n.isEmpty()) return;
                            rt.renameThread(threadId, n);
                            if (conv != null) conv.threadName = n;
                            bindStatus();
                        }).setNegativeButton("取消", null).show();
            } else if (w == 2) {
                Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
            } else {
                UsageDialog.show(this, rt, conv);
            }
        }).show();
    }
}
