package com.pocketcodex.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文本查看：行号 + 等宽字体 + 可选中；Markdown 文件可切换预览。也用来显示完整 diff（EXTRA_TEXT）。
 * 大文件只读前 2 MB。二进制和非 UTF-8 文件不硬显示。
 */
public final class FileViewerActivity extends AppCompatActivity {
    static final String EXTRA_PATH = "path", EXTRA_TEXT = "text", EXTRA_TITLE = "title", EXTRA_DIFF = "diff";
    private static final int LIMIT = 2 << 20;

    private File file;
    private String text;
    /** 拆好的行（后台算好）。 */
    private List<String> lines;
    /** Markdown 预览（后台解析好的结果）。 */
    private android.text.Spanned rendered;
    /** 这个页面自己的 Markwon：要在后台线程解析，不能和聊天共用全局实例（表格插件有内部状态）。 */
    private io.noties.markwon.Markwon md;
    /** 正在准备分享：不接受重复点击（否则后一次会删掉前一次正在分享的文件）。 */
    private boolean sharing;
    private boolean diff, preview;
    private FrameLayout body;
    private TextView info;
    /** 读文件、解码、拆行、解析 Markdown、分享前复制都在这里做，主线程只放结果。 */
    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "file-viewer");
        t.setDaemon(true);
        return t;
    });

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    /** 回到主线程；页面已经关了就丢掉。 */
    private void post(Runnable r) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) r.run();
        });
    }

    private static List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                out.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (start < text.length() || out.isEmpty()) out.add(text.substring(start));
        return out;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        file = path == null ? null : new File(path);
        diff = getIntent().getBooleanExtra(EXTRA_DIFF, false);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        LinearLayout page = Ui.vbox(this);
        page.setBackgroundColor(Ui.color(this, R.color.bg));
        page.setFitsSystemWindows(true);
        LinearLayout bar = Ui.hbox(this);
        bar.setMinimumHeight(Ui.dp(this, 56));
        bar.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        bar.addView(iconButton(R.drawable.ic_back, "返回", v -> finish()));
        TextView t = Ui.text(this, title != null ? title : file != null ? file.getName() : "", 18, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setSingleLine();
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        bar.addView(t, Ui.weight1());
        if (file != null && file.getName().toLowerCase(Locale.ROOT).endsWith(".md")) {
            final com.google.android.material.button.MaterialButton pv = Ui.textButton(this, "预览", null);
            pv.setOnClickListener(v -> {
                preview = !preview;
                pv.setText(preview ? "原文" : "预览");
                show();
            });
            bar.addView(pv);
        }
        bar.addView(iconButton(R.drawable.ic_copy, "复制", v -> {
            if (text == null) return;
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("文本", text.length() > 512 * 1024 ? text.substring(0, 512 * 1024) : text));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        }));
        if (file != null) bar.addView(iconButton(R.drawable.ic_share, "分享", v -> share()));
        page.addView(bar);
        info = Ui.small(this, "");
        info.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));
        page.addView(info);
        body = new FrameLayout(this);
        body.setBackgroundColor(Ui.color(this, R.color.code_bg));
        page.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        load();
    }

    private ImageButton iconButton(int icon, String desc, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackground(null);
        b.setContentDescription(desc);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.text)));
        b.setMinimumWidth(Ui.dp(this, 48));
        b.setMinimumHeight(Ui.dp(this, 48));
        b.setOnClickListener(l);
        return b;
    }

    private void load() {
        message("正在读取…");
        final File f = file;
        final String extra = f == null ? getIntent().getStringExtra(EXTRA_TEXT) : null;
        io.execute(() -> {
            if (f == null) {
                final String t = extra == null ? "" : extra;
                final List<String> ls = splitLines(t);
                post(() -> {
                    text = t;
                    lines = ls;
                    info.setText(diff ? "改动记录（不是文件的当前内容）" : "");
                    show();
                });
                return;
            }
            if (!f.isFile()) {
                post(() -> {
                    info.setText("");
                    message("文件已不存在");
                });
                return;
            }
            if (sensitive(this, f)) {
                post(() -> message("这个文件属于应用的运行目录（凭据、日志），不在这里显示。"));
                return;
            }
            final long len = f.length();
            byte[] data;
            try (InputStream in = new FileInputStream(f)) {
                int n = (int) Math.min(len, LIMIT);
                data = new byte[n];
                int off = 0, r;
                while (off < n && (r = in.read(data, off, n - off)) > 0) off += r;
            } catch (IOException e) {
                final String m = e.getMessage();
                post(() -> message("无法读取：" + m));
                return;
            }
            final boolean truncated = len > LIMIT;
            for (int i = 0; i < Math.min(data.length, 8192); i++) {
                if (data[i] == 0) {
                    post(() -> {
                        info.setText(Format.mb(len));
                        message("暂不支持预览此类文件（" + Format.mb(len) + "）。可以用右上角“分享”用其他应用打开。");
                    });
                    return;
                }
            }
            final String t;
            try {
                t = StandardCharsets.UTF_8.newDecoder().onMalformedInput(truncated ? CodingErrorAction.REPLACE : CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
            } catch (CharacterCodingException e) {
                post(() -> message("无法按 UTF-8 解码这个文件。可以用“分享”交给其他应用打开。"));
                return;
            }
            final List<String> ls = splitLines(t);
            final String inf = "UTF-8 · " + (len < 1024 ? len + " B" : Format.mb(len)) + (truncated ? " · 只显示前 2 MB" : "");
            post(() -> {
                text = t;
                lines = ls;
                info.setText(inf);
                show();
            });
        });
    }

    private void message(String m) {
        body.removeAllViews();
        TextView t = Ui.caption(this, m);
        t.setPadding(Ui.dp(this, 24), Ui.dp(this, 32), Ui.dp(this, 24), 0);
        body.addView(t);
    }

    private void show() {
        if (text == null) return; // 还在读
        body.removeAllViews();
        if (preview) {
            ScrollView sv = new ScrollView(this);
            TextView tv = Ui.body(this, "");
            tv.setTextIsSelectable(true);
            tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 32));
            sv.addView(tv);
            body.addView(sv);
            if (md == null) md = Markdown.create(this);
            final io.noties.markwon.Markwon mw = md;
            if (rendered != null) {
                mw.setParsedMarkdown(tv, rendered);
                return;
            }
            tv.setText("正在排版…");
            final String src = text;
            io.execute(() -> { // 解析放后台，主线程只把结果放上去
                final android.text.Spanned sp = mw.toMarkdown(src);
                post(() -> {
                    rendered = sp;
                    if (preview) show();
                });
            });
            return;
        }
        final List<String> lines = this.lines != null ? this.lines : splitLines(text);
        final int digits = String.valueOf(lines.size()).length();
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 32));
        rv.setClipToPadding(false);
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int type) {
                TextView tv = Ui.mono(FileViewerActivity.this, "");
                tv.setTextIsSelectable(true);
                tv.setPadding(Ui.dp(FileViewerActivity.this, 8), 0, Ui.dp(FileViewerActivity.this, 12), 0);
                tv.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerView.ViewHolder(tv) {
                };
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder h, int i) {
                TextView tv = (TextView) h.itemView;
                String l = lines.get(i);
                SpannableStringBuilder sb = new SpannableStringBuilder();
                String no = String.format(Locale.ROOT, "%" + digits + "d  ", i + 1);
                sb.append(no);
                sb.setSpan(new ForegroundColorSpan(Ui.color(FileViewerActivity.this, R.color.text2)), 0, no.length(), 0);
                sb.append(l.isEmpty() ? " " : l);
                tv.setText(sb);
                int bg = 0;
                if (diff) {
                    if (l.startsWith("+") && !l.startsWith("+++")) bg = R.color.diff_add;
                    else if (l.startsWith("-") && !l.startsWith("---")) bg = R.color.diff_del;
                    else if (l.startsWith("@@")) bg = R.color.surface2;
                }
                tv.setBackgroundColor(bg == 0 ? 0 : Ui.color(FileViewerActivity.this, bg));
            }

            @Override
            public int getItemCount() {
                return lines.size();
            }
        });
        body.addView(rv);
    }

    /** 真实路径（跟随符号链接后）在不在运行时目录（凭据、日志）里：在的话不显示、不分享。 */
    static boolean sensitive(android.content.Context c, File f) {
        try {
            String canon = f.getCanonicalPath();
            Paths p = CodexRuntimeManager.get(c).paths;
            String root = p.root.getCanonicalPath(), ws = p.workspaces.getCanonicalPath();
            return (canon.equals(root) || canon.startsWith(root + "/")) && !(canon.equals(ws) || canon.startsWith(ws + "/"));
        } catch (IOException e) {
            return true;
        }
    }

    private void share() {
        if (file == null || sharing) return;
        sharing = true;
        final File src = file;
        Toast.makeText(this, "正在准备分享…", Toast.LENGTH_SHORT).show();
        io.execute(() -> { // 判断路径、复制整个文件都在后台
            try {
                shareInBackground(src);
            } finally {
                post(() -> sharing = false);
            }
        });
    }

    private void shareInBackground(File src) {
        {
            if (sensitive(this, src)) {
                post(() -> Toast.makeText(this, "这个文件属于应用的运行目录，不能分享", Toast.LENGTH_LONG).show());
                return;
            }
            try {
                // 复制一份到分享专用的缓存目录再分享：FileProvider 只开放这个目录。
                // 每次一个新子目录；只清一小时前的旧副本——刚发出去的分享，接收的应用可能还没读完
                File root = new File(getCacheDir(), "share");
                File[] old = root.listFiles();
                long cutoff = System.currentTimeMillis() - 3600_000L;
                if (old != null) for (File o : old) if (o.lastModified() < cutoff) Installer_deleteTree(o);
                File dir = new File(root, String.valueOf(System.currentTimeMillis()));
                dir.mkdirs();
                File copy = new File(dir, src.getName());
                try (InputStream in = new FileInputStream(src); java.io.OutputStream out = new java.io.FileOutputStream(copy)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("已取消");
                        out.write(buf, 0, n);
                    }
                }
                final Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", copy);
                String ext = MimeTypeMap.getFileExtensionFromUrl(src.getName());
                String mime = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
                final Intent i = new Intent(Intent.ACTION_SEND).setType(mime == null ? "text/plain" : mime).putExtra(Intent.EXTRA_STREAM, u)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                post(() -> startActivity(Intent.createChooser(i, "分享 " + src.getName())));
            } catch (Exception e) {
                final String m = e.getMessage();
                post(() -> Toast.makeText(this, "无法分享：" + m, Toast.LENGTH_LONG).show());
            }
        }
    }

    private static void Installer_deleteTree(File f) {
        com.pocketcodex.core.Installer.deleteTree(f);
    }
}
