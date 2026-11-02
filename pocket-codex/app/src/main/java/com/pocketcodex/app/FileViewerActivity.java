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
    private boolean diff, preview;
    private FrameLayout body;
    private TextView info;

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
        if (file == null) {
            text = getIntent().getStringExtra(EXTRA_TEXT);
            if (text == null) text = "";
            info.setText(diff ? "改动记录（不是文件的当前内容）" : "");
            show();
            return;
        }
        if (!file.isFile()) {
            info.setText("");
            message("文件已不存在");
            return;
        }
        if (sensitive(this, file)) {
            message("这个文件属于应用的运行目录（凭据、日志），不在这里显示。");
            return;
        }
        byte[] data;
        try (InputStream in = new FileInputStream(file)) {
            int n = (int) Math.min(file.length(), LIMIT);
            data = new byte[n];
            int off = 0, r;
            while (off < n && (r = in.read(data, off, n - off)) > 0) off += r;
        } catch (IOException e) {
            message("无法读取：" + e.getMessage());
            return;
        }
        boolean truncated = file.length() > LIMIT;
        for (int i = 0; i < Math.min(data.length, 8192); i++) {
            if (data[i] == 0) {
                info.setText(Format.mb(file.length()));
                message("暂不支持预览此类文件（" + Format.mb(file.length()) + "）。可以用右上角“分享”用其他应用打开。");
                return;
            }
        }
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(truncated ? CodingErrorAction.REPLACE : CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            message("无法按 UTF-8 解码这个文件。可以用“分享”交给其他应用打开。");
            return;
        }
        info.setText("UTF-8 · " + (file.length() < 1024 ? file.length() + " B" : Format.mb(file.length())) + (truncated ? " · 只显示前 2 MB" : ""));
        show();
    }

    private void message(String m) {
        body.removeAllViews();
        TextView t = Ui.caption(this, m);
        t.setPadding(Ui.dp(this, 24), Ui.dp(this, 32), Ui.dp(this, 24), 0);
        body.addView(t);
    }

    private void show() {
        body.removeAllViews();
        if (preview) {
            ScrollView sv = new ScrollView(this);
            TextView tv = Ui.body(this, "");
            tv.setTextIsSelectable(true);
            tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 32));
            Markdown.get(this).setMarkdown(tv, text);
            sv.addView(tv);
            body.addView(sv);
            return;
        }
        final List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (start < text.length() || lines.isEmpty()) lines.add(text.substring(start));
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
        if (sensitive(this, file)) {
            Toast.makeText(this, "这个文件属于应用的运行目录，不能分享", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            // 复制一份到分享专用的缓存目录再分享：FileProvider 只开放这个目录
            File dir = new File(getCacheDir(), "share");
            Installer_deleteTree(dir);
            dir.mkdirs();
            File copy = new File(dir, file.getName());
            try (InputStream in = new FileInputStream(file); java.io.OutputStream out = new java.io.FileOutputStream(copy)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            }
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", copy);
            String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
            String mime = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
            Intent i = new Intent(Intent.ACTION_SEND).setType(mime == null ? "text/plain" : mime).putExtra(Intent.EXTRA_STREAM, u)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "分享 " + file.getName()));
        } catch (Exception e) {
            Toast.makeText(this, "无法分享：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void Installer_deleteTree(File f) {
        com.pocketcodex.core.Installer.deleteTree(f);
    }
}
