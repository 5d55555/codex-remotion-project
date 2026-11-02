package com.pocketcodex.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
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
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 原生的目录浏览器（不用 SAF：SAF 给的是 content:// 而不是 codex 能用的路径）。
 * 只浏览共享存储根目录之内；选定后做一次写入—读回—删除的探测，成功才返回。
 */
public final class FolderPickerActivity extends AppCompatActivity {
    static final String EXTRA_PATH = "path";
    private File root, cur;
    private LinearLayout list, crumbs;
    private TextView curText;
    private boolean showHidden;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = Environment.getExternalStorageDirectory();
        cur = root;
        LinearLayout page = Ui.vbox(this);
        page.setBackgroundColor(Ui.color(this, R.color.bg));
        page.setFitsSystemWindows(true);
        LinearLayout bar = Ui.hbox(this);
        bar.setMinimumHeight(Ui.dp(this, 56));
        bar.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        bar.addView(iconButton(R.drawable.ic_back, "返回", v -> finish()));
        TextView t = Ui.text(this, "选择文件夹", 20, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(t, Ui.weight1());
        bar.addView(iconButton(R.drawable.ic_add, "新建文件夹", v -> newFolder()));
        bar.addView(iconButton(R.drawable.ic_more, "显示隐藏文件夹", v -> {
            showHidden = !showHidden;
            refresh();
        }));
        page.addView(bar);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        crumbs = Ui.hbox(this);
        crumbs.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        crumbs.setMinimumHeight(Ui.dp(this, 48));
        hs.addView(crumbs);
        page.addView(hs);
        TextView note = Ui.caption(this, "选择后会直接修改这里的文件");
        note.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));
        page.addView(note);
        ScrollView sv = new ScrollView(this);
        list = Ui.vbox(this);
        sv.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout foot = Ui.vbox(this);
        foot.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 12));
        curText = Ui.small(this, "");
        foot.addView(curText);
        foot.addView(Ui.primary(this, "使用此文件夹", v -> choose()), Ui.matchWrap(this, 8));
        page.addView(foot);
        setContentView(page);
        refresh();
    }

    private View iconButton(int icon, String desc, View.OnClickListener l) {
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

    private void refresh() {
        crumbs.removeAllViews();
        List<File> chain = new ArrayList<>();
        for (File f = cur; f != null; f = f.getParentFile()) {
            chain.add(0, f);
            if (f.equals(root)) break;
        }
        for (int i = 0; i < chain.size(); i++) {
            final File f = chain.get(i);
            TextView c = Ui.text(this, i == 0 ? "手机存储" : f.getName(), 14, i == chain.size() - 1 ? R.color.text : R.color.primary);
            c.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
            c.setOnClickListener(v -> {
                cur = f;
                refresh();
            });
            if (i > 0) crumbs.addView(Ui.small(this, "›"));
            crumbs.addView(c);
        }
        curText.setText(Format.prettyPath(CodexRuntimeManager.get(this), cur.getAbsolutePath()));
        list.removeAllViews();
        File[] fs = cur.listFiles(File::isDirectory);
        if (fs == null) {
            list.addView(pad(Ui.caption(this, "无法读取这个文件夹（可能没有存储权限）")));
            return;
        }
        Arrays.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        int n = 0;
        for (final File f : fs) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            if (f.getName().equals("Android") && cur.equals(root)) continue; // 其他 App 的私有目录，不可用
            LinearLayout row = Ui.hbox(this);
            row.setMinimumHeight(Ui.dp(this, 56));
            row.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
            row.setBackgroundResource(rippleRes());
            row.addView(Ui.icon(this, R.drawable.ic_folder_filled, R.color.primary, 24));
            TextView name = Ui.body(this, f.getName());
            LinearLayout.LayoutParams lp = Ui.weight1();
            lp.leftMargin = Ui.dp(this, 16);
            row.addView(name, lp);
            row.addView(Ui.icon(this, R.drawable.ic_chevron_right, R.color.text2, 20));
            row.setOnClickListener(v -> {
                cur = f;
                refresh();
            });
            list.addView(row, Ui.matchWrap());
            n++;
        }
        if (n == 0) list.addView(pad(Ui.caption(this, "这里没有子文件夹。可以点右上角“＋”新建一个项目文件夹。")));
    }

    private int rippleRes() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }

    private View pad(View v) {
        v.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        return v;
    }

    private void newFolder() {
        final EditText et = new EditText(this);
        et.setSingleLine();
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(cur.equals(root) ? "口袋工坊" : "新项目");
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
        box.addView(et);
        new AlertDialog.Builder(this).setTitle("新建文件夹").setView(box)
                .setPositiveButton("创建", (d, w) -> {
                    String n = et.getText().toString().trim().replaceAll("[/\\\\:*?\"<>|]", "_");
                    if (n.isEmpty()) return;
                    File f = new File(cur, n);
                    if (f.isDirectory() || f.mkdirs()) {
                        cur = f;
                        refresh();
                    } else {
                        Toast.makeText(this, "无法在这里创建文件夹", Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void choose() {
        if (cur.equals(root)) {
            Toast.makeText(this, "请选择或新建一个项目文件夹，不要直接用存储根目录", Toast.LENGTH_LONG).show();
            return;
        }
        // 探测：写入、读回、删除
        File probe = new File(cur, ".pocket-codex-probe-" + UUID.randomUUID());
        String err = null;
        try {
            try (FileOutputStream o = new FileOutputStream(probe)) {
                o.write("pocket-codex-ok".getBytes());
            }
            byte[] back = new byte[64];
            int n;
            try (java.io.FileInputStream in = new java.io.FileInputStream(probe)) {
                n = in.read(back);
            }
            if (n != 15 || !new String(back, 0, n).equals("pocket-codex-ok")) err = "读回的内容不对";
        } catch (Exception e) {
            err = e.getMessage();
        } finally {
            if (probe.exists() && !probe.delete() && err == null) err = "探测文件删不掉：" + probe.getName();
        }
        if (err != null) {
            new AlertDialog.Builder(this).setTitle("这个文件夹无法读写").setMessage("请选择其他位置。\n\n" + err).setPositiveButton("好", null).show();
            return;
        }
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_PATH, cur.getAbsolutePath()));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (!cur.equals(root) && cur.getParentFile() != null) {
            cur = cur.getParentFile();
            refresh();
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("unused")
    private static final int GRAVITY = Gravity.CENTER;
}
