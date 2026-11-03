package com.pocketcodex.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import com.pocketcodex.core.Conversation;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 对话里的卡片（按 Astra 的规格）：用户消息气泡、Agent 正式回答（Markdown）、执行说明、命令、文件改动、推理摘要、工具、提示。
 * 数据直接读 {@link Conversation#items}（主线程）；展开/折叠是本地状态。
 */
final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
    private static final int T_USER = 1, T_AGENT = 2, T_CARD = 3, T_NOTICE = 4, T_COMMENTARY = 5;

    private final Context ctx;
    private Conversation conv;
    private String cwd;
    private final Set<String> expanded = new HashSet<>(), collapsed = new HashSet<>();
    /** Markdown 缓存：item id → (版本, 渲染结果)。流式时限频 4 次/秒。 */
    private final Map<String, Object[]> mdCache = new HashMap<>();
    private final Map<String, Long> mdTime = new HashMap<>();

    ChatAdapter(Context ctx) {
        this.ctx = ctx;
        setHasStableIds(true);
    }

    void setConversation(Conversation c, String cwd) {
        this.conv = c;
        this.cwd = cwd;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return conv == null ? 0 : conv.items.size();
    }

    @Override
    public long getItemId(int i) {
        return conv.items.get(i).id.hashCode();
    }

    @Override
    public int getItemViewType(int i) {
        Conversation.Item it = conv.items.get(i);
        switch (it.kind) {
            case USER:
                return T_USER;
            case AGENT:
                return "commentary".equals(it.phase) ? T_COMMENTARY : T_AGENT;
            case NOTICE:
                return T_NOTICE;
            default:
                return T_CARD;
        }
    }

    static final class VH extends RecyclerView.ViewHolder {
        final LinearLayout box;

        VH(View v, LinearLayout box) {
            super(v);
            this.box = box;
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int type) {
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout box = Ui.vbox(ctx);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int side = Ui.dp(ctx, 16);
        if (type == T_USER) {
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END);
            lp.leftMargin = Ui.dp(ctx, 56);
        }
        lp.leftMargin += side;
        lp.rightMargin = side;
        lp.topMargin = Ui.dp(ctx, type == T_USER ? 16 : 6);
        lp.bottomMargin = Ui.dp(ctx, type == T_USER ? 8 : 2);
        wrap.addView(box, lp);
        return new VH(wrap, box);
    }

    @Override
    public void onBindViewHolder(VH h, int pos) {
        Conversation.Item it = conv.items.get(pos);
        h.box.removeAllViews();
        h.box.setBackground(null);
        h.box.setPadding(0, 0, 0, 0);
        h.box.setOnClickListener(null);
        h.box.setOnLongClickListener(null);
        h.box.setGravity(Gravity.NO_GRAVITY);
        switch (it.kind) {
            case USER:
                bindUser(h.box, it);
                break;
            case AGENT:
                if ("commentary".equals(it.phase)) bindCommentary(h.box, it);
                else bindAgent(h.box, it);
                break;
            case COMMAND:
                bindCommand(h.box, it);
                break;
            case FILE_CHANGE:
                bindFiles(h.box, it);
                break;
            case REASONING:
                bindReasoning(h.box, it);
                break;
            case NOTICE:
                bindNotice(h.box, it);
                break;
            default:
                bindTool(h.box, it);
        }
    }

    /* ---------------- 各种卡片 ---------------- */

    /** 用户消息：右侧浅灰圆角气泡（ChatGPT 样式）；带的图片显示在气泡上方，提及的技能/插件/文件是气泡里的小标签。 */
    private void bindUser(LinearLayout box, Conversation.Item it) {
        box.setGravity(Gravity.END);
        if (!it.images.isEmpty()) {
            LinearLayout imgs = Ui.hbox(ctx);
            imgs.setGravity(Gravity.END);
            for (String p : it.images) {
                ImageView iv = new ImageView(ctx);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                android.graphics.Bitmap bm = Attach.thumb(p, Ui.dp(ctx, 120));
                if (bm != null) iv.setImageBitmap(bm);
                else iv.setImageResource(R.drawable.ic_image);
                iv.setBackground(Ui.round(ctx, R.color.surface2, 0, 18));
                iv.setClipToOutline(true);
                iv.setContentDescription("图片");
                final String path = p;
                iv.setOnClickListener(v -> openImage(path));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(ctx, 120), Ui.dp(ctx, 120));
                lp.leftMargin = Ui.dp(ctx, 6);
                imgs.addView(iv, lp);
            }
            HorizontalScrollView hs = new HorizontalScrollView(ctx);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(imgs);
            LinearLayout.LayoutParams hl = Ui.wrap();
            hl.bottomMargin = Ui.dp(ctx, 6);
            box.addView(hs, hl);
        }
        String text = it.text.toString();
        if (text.trim().isEmpty() && it.mentions.isEmpty()) return;
        LinearLayout bubble = Ui.vbox(ctx);
        bubble.setBackground(Ui.round(ctx, R.color.bubble, 0, 22));
        bubble.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 10), Ui.dp(ctx, 16), Ui.dp(ctx, 11));
        if (!it.mentions.isEmpty()) {
            LinearLayout tags = Ui.hbox(ctx);
            for (String m : it.mentions) {
                TextView t = Ui.text(ctx, "@" + m, 13, R.color.text2);
                t.setBackground(Ui.round(ctx, R.color.card, 0, 10));
                t.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 2));
                t.setSingleLine();
                t.setMaxWidth(Ui.dp(ctx, 200));
                t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                LinearLayout.LayoutParams lp = Ui.wrap();
                lp.rightMargin = Ui.dp(ctx, 6);
                tags.addView(t, lp);
            }
            HorizontalScrollView hs = new HorizontalScrollView(ctx);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(tags);
            LinearLayout.LayoutParams tl = Ui.wrap();
            tl.bottomMargin = Ui.dp(ctx, text.trim().isEmpty() ? 0 : 6);
            bubble.addView(hs, tl);
        }
        if (!text.trim().isEmpty()) {
            TextView t = Ui.body(ctx, text);
            t.setTextIsSelectable(true);
            bubble.addView(t, Ui.wrap()); // 不能用默认的 MATCH_PARENT：气泡是 WRAP_CONTENT，会被压成标签那么窄
        }
        bubble.setOnLongClickListener(v -> {
            copy(text);
            return true;
        });
        box.addView(bubble, Ui.wrap());
    }

    private void openImage(String path) {
        try {
            File src = new File(path);
            File dir = new File(ctx.getCacheDir(), "share");
            dir.mkdirs();
            File dst = new File(dir, src.getName());
            java.nio.file.Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            android.net.Uri u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".files", dst);
            ctx.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception e) {
            Toast.makeText(ctx, "无法打开图片", Toast.LENGTH_SHORT).show();
        }
    }

    private void bindAgent(LinearLayout box, Conversation.Item it) {
        TextView t = Ui.body(ctx, "");
        t.setTextIsSelectable(true);
        t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        String md = it.text.toString();
        if (md.isEmpty() && it.running()) {
            t.setText("…");
            t.setTextColor(Ui.color(ctx, R.color.text2));
        } else if (it.running() && md.length() > 20_000) {
            t.setText(md); // 很长的流式消息先按纯文本显示，完成后再排版
        } else {
            Markdown.setTo(t, markdown(it, md));
        }
        box.addView(t);
    }

    /** 带缓存和限频的 Markdown（流式时最多每 250ms 重新排版一次）。 */
    private Spanned markdown(Conversation.Item it, String md) {
        Object[] c = mdCache.get(it.id);
        long now = SystemClock.uptimeMillis();
        if (c != null && (Integer) c[0] == it.version) return (Spanned) c[1];
        Long last = mdTime.get(it.id);
        if (c != null && it.running() && last != null && now - last < 250) return (Spanned) c[1];
        Spanned s = Markdown.render(ctx, md);
        mdCache.put(it.id, new Object[]{it.version, s});
        mdTime.put(it.id, now);
        return s;
    }

    /** 执行说明：小图标 + 14sp 次文字色。 */
    private void bindCommentary(LinearLayout box, Conversation.Item it) {
        LinearLayout row = Ui.hbox(ctx);
        row.setGravity(Gravity.TOP);
        ImageView ic = Ui.icon(ctx, R.drawable.ic_plan, R.color.text2, 16);
        LinearLayout.LayoutParams il = (LinearLayout.LayoutParams) ic.getLayoutParams();
        il.topMargin = Ui.dp(ctx, 3);
        row.addView(ic, il);
        TextView t = Ui.caption(ctx, "");
        t.setTextIsSelectable(true);
        String md = it.text.toString();
        if (md.isEmpty()) t.setText("…");
        else Markdown.setTo(t, markdown(it, md));
        t.setTextColor(Ui.color(ctx, R.color.text2));
        LinearLayout.LayoutParams lp = Ui.weight1();
        lp.leftMargin = Ui.dp(ctx, 8);
        row.addView(t, lp);
        box.addView(row);
    }

    private LinearLayout cardShell(LinearLayout box, int icon, int iconTint, String title, String status, int statusColor, final String key, boolean expandable) {
        box.setBackground(Ui.round(ctx, R.color.card, R.color.border, 18));
        int p = Ui.dp(ctx, 14);
        box.setPadding(p, Ui.dp(ctx, 8), p, Ui.dp(ctx, 12));
        LinearLayout head = Ui.hbox(ctx);
        head.setMinimumHeight(Ui.dp(ctx, 36));
        head.addView(Ui.icon(ctx, icon, iconTint, 20));
        TextView t = Ui.text(ctx, title, 14, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = Ui.weight1();
        lp.leftMargin = Ui.dp(ctx, 8);
        head.addView(t, lp);
        if (status != null) {
            TextView s = Ui.text(ctx, status, 12, statusColor);
            head.addView(s);
        }
        if (expandable) {
            ImageView chev = Ui.icon(ctx, R.drawable.ic_expand, R.color.text2, 20);
            Boolean d = defaults.get(key);
            boolean open = isExpanded(key, d != null && d);
            chev.setRotation(open ? 180 : 0);
            LinearLayout.LayoutParams cl = (LinearLayout.LayoutParams) chev.getLayoutParams();
            cl.leftMargin = Ui.dp(ctx, 4);
            head.addView(chev, cl);
            head.setOnClickListener(v -> toggle(key));
            head.setContentDescription(title + "，点按" + (open ? "收起" : "展开"));
        }
        box.addView(head);
        return head;
    }

    private boolean isExpanded(String key, boolean def) {
        if (expanded.contains(key)) return true;
        if (collapsed.contains(key)) return false;
        return def;
    }

    /** 展开/收起：以当前显示的状态（含各卡片的默认值）为准取反。 */
    private void toggle(String key) {
        Boolean d = defaults.get(key);
        boolean cur = isExpanded(key, d != null && d);
        expanded.remove(key);
        collapsed.remove(key);
        if (cur) collapsed.add(key);
        else expanded.add(key);
        notifyDataSetChanged();
    }

    private final Map<String, Boolean> defaults = new HashMap<>();

    private boolean expandedWithDefault(String key, boolean def) {
        defaults.put(key, def);
        return isExpanded(key, def);
    }

    private void bindCommand(LinearLayout box, Conversation.Item it) {
        String st = it.status;
        boolean running = it.running();
        boolean declined = "declined".equals(st);
        boolean failed = "failed".equals(st) || (it.exitCode != null && it.exitCode != 0 && !running);
        boolean waiting = running && hasPendingApproval(it.id);
        String title;
        int icon = R.drawable.ic_terminal, tint;
        if (waiting) {
            title = "等待确认";
            tint = R.color.warning;
        } else if (running) {
            title = "正在运行命令";
            tint = R.color.primary;
        } else if (declined) {
            title = "已拒绝此操作";
            tint = R.color.text2;
        } else if (failed) {
            title = "命令执行失败";
            tint = R.color.error;
            icon = R.drawable.ic_warning;
        } else if ("interrupted".equals(st)) {
            title = "已停止";
            tint = R.color.text2;
        } else {
            title = "命令已完成";
            tint = R.color.success;
            icon = R.drawable.ic_check;
        }
        String status = null;
        if (!running && !declined) {
            StringBuilder s = new StringBuilder();
            if (it.exitCode != null) s.append("退出码 ").append(it.exitCode);
            else if ("completed".equals(st)) s.append("未提供退出码");
            if (it.durationMs != null) s.append(s.length() > 0 ? " · " : "").append(Format.duration(it.durationMs));
            status = s.toString();
        }
        String key = "cmd:" + it.id;
        boolean open = expandedWithDefault(key, running || failed);
        cardShell(box, icon, tint, title, status, failed ? R.color.error : R.color.text2, key, true);
        String cmd = shortCommand(it.command);
        TextView c = Ui.mono(ctx, "$ " + cmd);
        c.setMaxLines(open ? 12 : 3);
        c.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(c, Ui.matchWrap(ctx, 4));
        if (open) {
            String out = it.output.toString();
            if (!declined) {
                String shown = running || failed ? lastLines(out, 8) : lastLines(out, 40);
                TextView o = Ui.mono(ctx, shown.isEmpty() ? (running ? "尚无输出" : "未产生输出") : shown);
                o.setTextColor(Ui.color(ctx, shown.isEmpty() ? R.color.text2 : R.color.text));
                o.setTextSize(12);
                o.setTextIsSelectable(true);
                HorizontalScrollView hs = new HorizontalScrollView(ctx);
                o.setHorizontallyScrolling(true);
                hs.addView(o);
                hs.setBackground(Ui.round(ctx, R.color.code_bg, 0, 12));
                hs.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6));
                box.addView(hs, Ui.matchWrap(ctx, 8));
                if (it.outputTruncated) box.addView(Ui.small(ctx, "较早的输出未保留"), Ui.matchWrap(ctx, 4));
                if (it.cwd != null && cwd != null && !it.cwd.equals(cwd)) box.addView(Ui.small(ctx, "目录：" + it.cwd), Ui.matchWrap(ctx, 4));
            }
            if (!out.isEmpty()) {
                TextView cp = Ui.text(ctx, "复制输出", 13, R.color.primary);
                cp.setPadding(0, Ui.dp(ctx, 8), 0, 0);
                cp.setOnClickListener(v -> copy(out));
                box.addView(cp);
            }
        }
    }

    /** 去掉 codex 加的 “/…/bash -lc '…'” 外壳，显示真正的命令。 */
    static String shortCommand(String cmd) {
        if (cmd == null) return "";
        String c = cmd.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\S*(?:bash|sh|zsh)\\s+-l?c\\s+(.*)$", java.util.regex.Pattern.DOTALL).matcher(c);
        if (m.find()) {
            String inner = m.group(1).trim();
            if (inner.length() >= 2 && ((inner.startsWith("'") && inner.endsWith("'")) || (inner.startsWith("\"") && inner.endsWith("\"")))) {
                inner = inner.substring(1, inner.length() - 1);
            }
            return inner.replace("'\"'\"'", "'");
        }
        return c;
    }

    private static String lastLines(String s, int n) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') end--;
        int i = end, count = 0;
        while (i > 0) {
            if (s.charAt(i - 1) == '\n' && ++count >= n) break;
            i--;
        }
        return s.substring(i, end);
    }

    private boolean hasPendingApproval(String itemId) {
        for (Conversation.Approval a : conv.pendingApprovals()) if (itemId.equals(a.itemId)) return true;
        return false;
    }

    private String rel(String path) {
        if (path == null) return "?";
        if (cwd != null && path.startsWith(cwd + "/")) return path.substring(cwd.length() + 1);
        return path;
    }

    private void bindFiles(LinearLayout box, Conversation.Item it) {
        String st = it.status;
        boolean running = it.running();
        boolean waiting = hasPendingApproval(it.id);
        String title;
        int tint = R.color.primary;
        if (waiting) {
            title = "等待确认文件改动";
            tint = R.color.warning;
        } else if (running) title = "正在修改文件";
        else if ("declined".equals(st)) {
            title = "已拒绝文件改动";
            tint = R.color.text2;
        } else if ("failed".equals(st)) {
            title = "文件改动未完成";
            tint = R.color.error;
        } else if ("completed".equals(st)) {
            title = "文件改动已应用";
            tint = R.color.success;
        }
        else {
            title = "文件改动（状态待确认）"; // 被中断或未知状态：不能报成功
            tint = R.color.text2;
        }
        int n = it.changes.size();
        String key = "file:" + it.id;
        boolean open = expandedWithDefault(key, n <= 2);
        cardShell(box, R.drawable.ic_file, tint, title, n == 0 ? null : "修改 " + n + " 个文件", R.color.text2, key, true);
        if ("failed".equals(st)) box.addView(Ui.small(ctx, "部分改动可能已经写入，请查看文件。"), Ui.matchWrap(ctx, 2));
        int shown = 0;
        for (Conversation.Change ch : it.changes) {
            if (!open && shown >= 3) {
                box.addView(Ui.small(ctx, "另有 " + (n - shown) + " 个文件"), Ui.matchWrap(ctx, 4));
                break;
            }
            shown++;
            LinearLayout row = Ui.hbox(ctx);
            String kind = "add".equals(ch.kind) ? "新增" : "delete".equals(ch.kind) ? "删除" : ch.movePath != null ? "移动" : "修改";
            TextView k = Ui.chip(ctx, kind, "delete".equals(ch.kind) ? R.color.diff_del : "add".equals(ch.kind) ? R.color.diff_add : R.color.surface2, R.color.text);
            row.addView(k);
            String name = ch.movePath != null ? rel(ch.path) + " → " + rel(ch.movePath) : rel(ch.path);
            TextView p = Ui.mono(ctx, name);
            p.setSingleLine();
            p.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams lp = Ui.weight1();
            lp.leftMargin = Ui.dp(ctx, 8);
            row.addView(p, lp);
            SpannableStringBuilder stat = new SpannableStringBuilder();
            stat.append("+").append(String.valueOf(ch.added));
            stat.setSpan(new ForegroundColorSpan(Ui.color(ctx, R.color.diff_add_text)), 0, stat.length(), 0);
            int s0 = stat.length();
            stat.append("  -").append(String.valueOf(ch.removed));
            stat.setSpan(new ForegroundColorSpan(Ui.color(ctx, R.color.diff_del_text)), s0, stat.length(), 0);
            TextView sv = Ui.text(ctx, stat, 12, R.color.text2);
            sv.setTypeface(Typeface.MONOSPACE);
            row.addView(sv);
            row.setPadding(0, Ui.dp(ctx, 6), 0, 0);
            final String path = ch.path;
            row.setOnClickListener(v -> {
                if (new File(path).isFile()) ctx.startActivity(new Intent(ctx, FileViewerActivity.class).putExtra(FileViewerActivity.EXTRA_PATH, path));
                else Toast.makeText(ctx, "文件不存在或无法访问", Toast.LENGTH_SHORT).show();
            });
            box.addView(row);
            if (open && !ch.diff.isEmpty()) {
                box.addView(diffPreview(ch), Ui.matchWrap(ctx, 6));
                if (lineCount(ch.diff) > 12) {
                    TextView more = Ui.text(ctx, "查看完整差异", 13, R.color.primary);
                    more.setPadding(0, Ui.dp(ctx, 6), 0, 0);
                    more.setOnClickListener(v -> ctx.startActivity(new Intent(ctx, FileViewerActivity.class)
                            .putExtra(FileViewerActivity.EXTRA_TEXT, ch.diff).putExtra(FileViewerActivity.EXTRA_DIFF, true)
                            .putExtra(FileViewerActivity.EXTRA_TITLE, rel(ch.path))));
                    box.addView(more);
                }
            } else if (open && ch.diff.isEmpty()) {
                box.addView(Ui.small(ctx, "未提供文本差异"), Ui.matchWrap(ctx, 4));
            }
        }
    }

    private static int lineCount(String s) {
        int n = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    /** diff 前 12 行：新增绿底、删除红底，保留 +/- 前缀。新增文件（不是 unified diff）整段按新增显示。 */
    private View diffPreview(Conversation.Change ch) {
        boolean unified = ch.diff.startsWith("@@") || ch.diff.contains("\n@@") || ch.diff.startsWith("---");
        String[] lines = ch.diff.split("\n", -1);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int n = 0;
        for (String l : lines) {
            if (n >= 12) break;
            if (l.isEmpty() && n == lines.length - 1) break;
            String shown = unified ? l : ("add".equals(ch.kind) ? "+" : "delete".equals(ch.kind) ? "-" : " ") + l;
            int st = sb.length();
            sb.append(shown.isEmpty() ? " " : shown).append('\n');
            int bg = 0;
            if (shown.startsWith("@@")) bg = R.color.surface2;
            else if (shown.startsWith("+") && !shown.startsWith("+++")) bg = R.color.diff_add;
            else if (shown.startsWith("-") && !shown.startsWith("---")) bg = R.color.diff_del;
            if (bg != 0) sb.setSpan(new BackgroundColorSpan(Ui.color(ctx, bg)), st, sb.length() - 1, 0);
            n++;
        }
        TextView t = Ui.mono(ctx, sb);
        t.setTextSize(12);
        t.setHorizontallyScrolling(true);
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.addView(t);
        hs.setBackground(Ui.round(ctx, R.color.code_bg, 0, 12));
        hs.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6));
        return hs;
    }

    private void bindReasoning(LinearLayout box, Conversation.Item it) {
        String key = "rs:" + it.id;
        boolean open = expandedWithDefault(key, false);
        String txt = it.text.toString().trim();
        cardShell(box, R.drawable.ic_bulb, R.color.text2, "推理摘要", it.running() ? "思考中…" : null, R.color.text2, key, !txt.isEmpty());
        if (open && !txt.isEmpty()) {
            TextView t = Ui.caption(ctx, "");
            Markdown.setTo(t, markdown(it, txt));
            box.addView(t, Ui.matchWrap(ctx, 4));
        } else if (txt.isEmpty() && !it.running()) {
            box.addView(Ui.small(ctx, "未提供摘要"), Ui.matchWrap(ctx, 2));
        }
    }

    private void bindTool(LinearLayout box, Conversation.Item it) {
        int icon = it.kind == Conversation.Kind.WEB_SEARCH ? R.drawable.ic_globe : R.drawable.ic_tool;
        String title;
        if (it.kind == Conversation.Kind.WEB_SEARCH) title = "搜索网页";
        else if (it.kind == Conversation.Kind.TOOL) title = it.title == null ? "调用工具" : it.title;
        else if ("plan".equals(it.type)) title = "方案";
        else title = "暂不支持显示此记录（" + it.type + "）";
        String status = it.running() ? "进行中" : "failed".equals(it.status) ? "失败" : null;
        cardShell(box, icon, R.color.text2, title, status, "failed".equals(it.status) ? R.color.error : R.color.text2, "tool:" + it.id, false);
        if (it.kind == Conversation.Kind.WEB_SEARCH && it.title != null) box.addView(Ui.caption(ctx, it.title), Ui.matchWrap(ctx, 2));
        String txt = it.text.toString().trim();
        if (!txt.isEmpty()) {
            TextView t = Ui.caption(ctx, "");
            if ("plan".equals(it.type)) Markdown.setTo(t, markdown(it, txt));
            else t.setText(txt.length() > 600 ? txt.substring(0, 600) + "…" : txt);
            box.addView(t, Ui.matchWrap(ctx, 4));
        }
    }

    private void bindNotice(LinearLayout box, Conversation.Item it) {
        boolean error = "error".equals(it.level);
        boolean retry = "retry".equals(it.level);
        boolean info = "info".equals(it.level);
        int fill = error ? R.color.error_soft : info ? R.color.surface2 : R.color.warning_soft;
        box.setBackground(Ui.round(ctx, fill, 0, 18));
        int p = Ui.dp(ctx, 12);
        box.setPadding(p, Ui.dp(ctx, 10), p, Ui.dp(ctx, 10));
        LinearLayout row = Ui.hbox(ctx);
        row.addView(Ui.icon(ctx, retry ? R.drawable.ic_refresh : error ? R.drawable.ic_warning : R.drawable.ic_info,
                error ? R.color.error : info ? R.color.text2 : R.color.warning, 18));
        TextView t = Ui.text(ctx, it.title == null ? "提示" : it.title, 14, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = Ui.weight1();
        lp.leftMargin = Ui.dp(ctx, 8);
        row.addView(t, lp);
        box.addView(row);
        String msg = it.text.toString().trim();
        if (!msg.isEmpty()) {
            TextView m = Ui.caption(ctx, msg.length() > 1000 ? msg.substring(0, 1000) + "…" : msg);
            m.setTextIsSelectable(true);
            box.addView(m, Ui.matchWrap(ctx, 4));
        }
    }

    private void copy(String s) {
        ctx.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("内容", s));
        Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("unused")
    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unused")
    private static List<Object> none() {
        return java.util.Collections.emptyList();
    }
}
