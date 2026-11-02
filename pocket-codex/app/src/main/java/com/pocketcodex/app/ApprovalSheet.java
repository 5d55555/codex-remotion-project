package com.pocketcodex.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.pocketcodex.core.Conversation;
import java.util.List;

/**
 * 审批底部弹窗（Astra：首版只给一次性决定）：允许这一次 → accept；拒绝此操作 → decline；拒绝并停止本轮 → cancel。
 * 关掉弹窗只是收起，审批仍然待处理。按请求到达顺序一项一项处理。
 */
final class ApprovalSheet {
    private final Context ctx;
    private final CodexRuntimeManager rt;
    private BottomSheetDialog dialog;
    private Conversation conv;
    private String threadTitle;
    private Object showingId;
    private String signature;

    ApprovalSheet(Context ctx, CodexRuntimeManager rt) {
        this.ctx = ctx;
        this.rt = rt;
    }

    boolean showing() {
        return dialog != null && dialog.isShowing();
    }

    /** 显示（或刷新）当前会话的第一个待处理审批；没有了就关掉。 */
    void update(Conversation c, String title, boolean open) {
        conv = c;
        threadTitle = title;
        List<Conversation.Approval> l = c.pendingApprovals();
        if (l.isEmpty()) {
            dismiss();
            return;
        }
        Conversation.Approval a = l.get(0);
        Conversation.Item item = a.itemId == null ? null : c.item(a.itemId);
        // 同一个请求：只有内容（改动详情到了、进入提交中）变了才重画，不重复弹
        String sig = a.key() + "|" + a.submitting + "|" + (item == null ? -1 : item.version) + "|" + l.size();
        if (showing() && String.valueOf(a.requestId).equals(String.valueOf(showingId)) && sig.equals(signature)) return;
        if (!open && !showing()) return;
        dismiss();
        showingId = a.requestId;
        signature = sig;
        dialog = new BottomSheetDialog(ctx);
        dialog.setContentView(build(a, l.size()));
        dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        dialog.setOnDismissListener(d -> showingId = null);
        dialog.show();
    }

    void dismiss() {
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
        dialog = null;
        showingId = null;
    }

    private View build(Conversation.Approval a, int total) {
        LinearLayout root = Ui.vbox(ctx);
        root.setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 8), Ui.dp(ctx, 20), Ui.dp(ctx, 16));
        LinearLayout head = Ui.hbox(ctx);
        head.addView(Ui.icon(ctx, a.command ? R.drawable.ic_terminal : R.drawable.ic_file, R.color.warning, 24));
        boolean stdin = a.command && "writeStdin".equals(kindOf(a));
        TextView t = Ui.text(ctx, a.command ? (stdin ? "允许向运行中的命令发送输入？" : "允许运行这条命令？") : "允许修改这些文件？", 20, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = Ui.weight1();
        lp.leftMargin = Ui.dp(ctx, 12);
        head.addView(t, lp);
        root.addView(head, Ui.matchWrap(ctx, 8));
        root.addView(Ui.small(ctx, threadTitle + " · 第 1 项，共 " + total + " 项"), Ui.matchWrap(ctx, 4));

        ScrollView sv = new ScrollView(ctx);
        LinearLayout body = Ui.vbox(ctx);
        Conversation.Item item = a.itemId == null ? null : conv.item(a.itemId);
        boolean canAllow = true;
        if (a.command) {
            String cmd = a.commandText != null ? a.commandText : item != null ? item.command : null;
            TextView c = Ui.mono(ctx, "$ " + ChatAdapter.shortCommand(cmd == null ? "（命令没有提供）" : cmd));
            c.setTextIsSelectable(true);
            c.setHorizontallyScrolling(true);
            HorizontalScrollView hs = new HorizontalScrollView(ctx);
            hs.addView(c);
            hs.setBackground(Ui.round(ctx, R.color.code_bg, 0, 8));
            hs.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8));
            body.addView(hs, Ui.matchWrap(ctx, 12));
            if (cmd != null && !cmd.equals(ChatAdapter.shortCommand(cmd))) {
                body.addView(Ui.small(ctx, "完整命令"), Ui.matchWrap(ctx, 8));
                TextView full = Ui.mono(ctx, cmd);
                full.setTextIsSelectable(true);
                full.setTextColor(Ui.color(ctx, R.color.text2));
                body.addView(full, Ui.matchWrap(ctx, 2));
            }
            body.addView(Ui.small(ctx, "这条命令可能修改文件，未提供改动预览。"), Ui.matchWrap(ctx, 6));
        } else {
            if (item == null || item.changes.isEmpty()) {
                body.addView(Ui.caption(ctx, "正在获取改动详情…（可以先拒绝）"), Ui.matchWrap(ctx, 12));
                canAllow = false;
            } else {
                for (Conversation.Change ch : item.changes) {
                    String kind = "add".equals(ch.kind) ? "新增" : "delete".equals(ch.kind) ? "删除" : ch.movePath != null ? "移动" : "修改";
                    TextView r = Ui.mono(ctx, kind + "  " + ch.path + (ch.movePath != null ? " → " + ch.movePath : "") + "   +" + ch.added + " -" + ch.removed);
                    body.addView(r, Ui.matchWrap(ctx, 8));
                    if (!ch.diff.isEmpty()) {
                        String preview = ch.diff.length() > 2000 ? ch.diff.substring(0, 2000) + "\n…" : ch.diff;
                        if (ch.diff.length() > 2000) {
                            TextView all = Ui.text(ctx, "查看完整差异", 14, R.color.primary);
                            all.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));
                            all.setOnClickListener(v -> ctx.startActivity(new android.content.Intent(ctx, FileViewerActivity.class)
                                    .putExtra(FileViewerActivity.EXTRA_TEXT, ch.diff).putExtra(FileViewerActivity.EXTRA_DIFF, true)
                                    .putExtra(FileViewerActivity.EXTRA_TITLE, ch.path)));
                            body.addView(all);
                        }
                        TextView d = Ui.mono(ctx, preview);
                        d.setTextSize(12);
                        d.setHorizontallyScrolling(true);
                        HorizontalScrollView hs = new HorizontalScrollView(ctx);
                        hs.addView(d);
                        hs.setBackground(Ui.round(ctx, R.color.code_bg, 0, 8));
                        hs.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6));
                        body.addView(hs, Ui.matchWrap(ctx, 4));
                    }
                }
            }
            if (a.grantRoot != null) body.addView(Ui.small(ctx, "请求涉及的目录：" + a.grantRoot), Ui.matchWrap(ctx, 6));
        }
        if (a.cwd != null) body.addView(Ui.caption(ctx, "工作目录：" + a.cwd), Ui.matchWrap(ctx, 8));
        if (a.reason != null && !a.reason.isEmpty()) body.addView(Ui.caption(ctx, "原因：" + a.reason), Ui.matchWrap(ctx, 6));
        sv.addView(body);
        int maxH = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.5f);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sv.post(() -> {
            if (sv.getHeight() > maxH) {
                sv.getLayoutParams().height = maxH;
                sv.requestLayout();
            }
        });

        MaterialButton allow = Ui.primary(ctx, "允许这一次", null);
        MaterialButton decline = Ui.outlined(ctx, "拒绝此操作", null);
        MaterialButton cancel = Ui.textButton(ctx, "拒绝并停止本轮", null);
        cancel.setTextColor(Ui.color(ctx, R.color.error));
        allow.setEnabled(canAllow && !a.submitting);
        decline.setEnabled(!a.submitting);
        cancel.setEnabled(!a.submitting);
        if (a.submitting) {
            MaterialButton chosen = "accept".equals(a.decision) ? allow : "decline".equals(a.decision) ? decline : cancel;
            chosen.setText("正在提交决定…");
        }
        View.OnClickListener answer = v -> {
            String decision = v == allow ? "accept" : v == decline ? "decline" : "cancel";
            allow.setEnabled(false);
            decline.setEnabled(false);
            cancel.setEnabled(false);
            ((MaterialButton) v).setText("正在提交决定…");
            // 弹窗保留到服务端确认（serverRequest/resolved）才关
            if (!rt.answerApproval(conv, a, decision)) {
                android.widget.Toast.makeText(ctx, "提交失败：Codex 进程已经不在了。请回到会话页重试。", android.widget.Toast.LENGTH_LONG).show();
                allow.setEnabled(true);
                decline.setEnabled(true);
                cancel.setEnabled(true);
                ((MaterialButton) v).setText(v == allow ? "允许这一次" : v == decline ? "拒绝此操作" : "拒绝并停止本轮");
            }
        };
        allow.setOnClickListener(answer);
        decline.setOnClickListener(answer);
        cancel.setOnClickListener(answer);
        root.addView(allow, Ui.matchWrap(ctx, 16));
        root.addView(decline, Ui.matchWrap(ctx, 8));
        root.addView(cancel, Ui.matchWrap(ctx, 4));
        return root;
    }

    private static String kindOf(Conversation.Approval a) {
        return a.kind;
    }
}
