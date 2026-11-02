package com.pocketcodex.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

/** 自检与诊断：逐项检查本机运行环境，结果可以复制给开发者（不含凭据、代码、邮箱）。 */
public final class DiagnosticsActivity extends AppCompatActivity {
    private TextView result;
    private MaterialButton run, copy;
    private String report;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CodexRuntimeManager rt = CodexRuntimeManager.get(this);
        LinearLayout page = Ui.vbox(this);
        page.setBackgroundColor(Ui.color(this, R.color.bg));
        page.setFitsSystemWindows(true);
        LinearLayout bar = Ui.hbox(this);
        bar.setMinimumHeight(Ui.dp(this, 56));
        bar.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 16), 0);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackground(null);
        back.setContentDescription("返回");
        back.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.text)));
        back.setMinimumWidth(Ui.dp(this, 48));
        back.setMinimumHeight(Ui.dp(this, 48));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView t = Ui.text(this, "自检与诊断", 20, R.color.text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(t, Ui.weight1());
        page.addView(bar);
        ScrollView sv = new ScrollView(this);
        LinearLayout c = Ui.vbox(this);
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        LinearLayout dev = Ui.card(this);
        dev.addView(Ui.caption(this, Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " · " + Paths.abi()));
        dev.addView(Ui.caption(this, "应用 " + BuildConfig.VERSION_NAME + " · Codex " + (rt.installedVersion == null ? "未安装" : rt.installedVersion)));
        c.addView(dev, Ui.matchWrap());
        c.addView(Ui.caption(this, "检查本机运行环境和网络连接，不读取你的项目内容。"), Ui.matchWrap(this, 12));
        run = Ui.primary(this, "开始自检", v -> start());
        c.addView(run, Ui.matchWrap(this, 12));
        result = Ui.mono(this, "");
        result.setTextIsSelectable(true);
        LinearLayout box = Ui.card(this);
        box.setBackground(Ui.round(this, R.color.code_bg, 0, 12));
        box.addView(result);
        c.addView(box, Ui.matchWrap(this, 16));
        copy = Ui.outlined(this, "复制诊断结果", v -> {
            if (report == null) return;
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("诊断", report));
            Toast.makeText(this, "诊断结果已复制（已隐藏凭据和项目内容）", Toast.LENGTH_LONG).show();
        });
        copy.setEnabled(false);
        c.addView(copy, Ui.matchWrap(this, 12));
        sv.addView(c, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        result.setText("等待检查");
    }

    private void start() {
        CodexRuntimeManager rt = CodexRuntimeManager.get(this);
        if (rt.runningThreadId != null) {
            result.setText("任务运行中，自检将在停止后可用。");
            return;
        }
        run.setEnabled(false);
        run.setText("正在检查…");
        result.setText("正在检查…");
        rt.runDiagnostics(r -> {
            String root = rt.paths.root.getAbsolutePath();
            report = r.replace(root, "<应用目录>");
            result.setText(report);
            run.setEnabled(true);
            run.setText("重新检查");
            copy.setEnabled(true);
        });
    }
}
