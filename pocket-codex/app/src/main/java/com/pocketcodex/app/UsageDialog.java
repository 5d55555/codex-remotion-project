package com.pocketcodex.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.pocketcodex.core.Conversation;
import com.pocketcodex.core.Json;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/** 用量与限额：底部面板（ChatGPT 风格）——账号的 5 小时 / 每周窗口用进度条显示，本会话的令牌数（服务端累计值）。 */
final class UsageDialog {
    private UsageDialog() {
    }

    static void show(Context c, CodexRuntimeManager rt, Conversation conv) {
        if (!(c instanceof AppCompatActivity)) {
            new AlertDialog.Builder(c).setTitle("用量").setMessage(text(rt, conv)).setPositiveButton("关闭", null).show();
            return;
        }
        AppCompatActivity a = (AppCompatActivity) c;
        Overlay ov = new Overlay(a, true);
        LinearLayout sheet = Ui.vbox(a);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.color(a, R.color.card));
        float r = Ui.dp(a, 32);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(Ui.dp(a, 24), Ui.dp(a, 12), Ui.dp(a, 24), Ui.dp(a, 24));
        View handle = new View(a);
        handle.setBackground(Ui.round(a, R.color.border, 0, 2));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, hl);
        TextView title = Overlay.strong(a, "用量与限额", 21);
        title.setPadding(0, Ui.dp(a, 18), 0, Ui.dp(a, 4));
        sheet.addView(title);
        Map<String, Object> rl = rt.lastRateLimits == null ? null : Json.obj(rt.lastRateLimits, "rateLimits");
        LinearLayout box = Ui.group(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 8));
        if (rl == null || (Json.obj(rl, "primary").isEmpty() && Json.obj(rl, "secondary").isEmpty())) {
            box.addView(Ui.text(a, rt.serverAlive() ? "服务没有提供额度信息" : "Codex 启动后才能读取额度", 15, R.color.text2), Ui.matchWrap(a, 10));
        } else {
            window(a, box, Json.obj(rl, "primary"));
            window(a, box, Json.obj(rl, "secondary"));
            String reached = Json.str(rl, "rateLimitReachedType");
            if (reached != null) box.addView(Ui.text(a, "已达到限额，等重置后再用", 14, R.color.error), Ui.matchWrap(a, 8));
        }
        sheet.addView(box, Ui.matchWrap(a, 14));
        if (conv != null) {
            LinearLayout cb = Ui.group(a);
            cb.setPadding(Ui.dp(a, 20), Ui.dp(a, 14), Ui.dp(a, 20), Ui.dp(a, 14));
            cb.addView(Overlay.strong(a, "本会话", 16));
            cb.addView(Ui.text(a, conv.tokensUsed >= 0 ? "累计 " + String.format(Locale.ROOT, "%,d", conv.tokensUsed) + " 个令牌" : "还没有用量数据", 15, R.color.text2), Ui.matchWrap(a, 6));
            if (conv.lastInputTokens >= 0 && conv.contextWindow > 0) {
                cb.addView(Ui.text(a, "上下文 " + String.format(Locale.ROOT, "%,d / %,d", conv.lastInputTokens, conv.contextWindow), 14, R.color.text3), Ui.matchWrap(a, 2));
                LinearProgressIndicator p = bar(a, (int) Math.min(100, conv.lastInputTokens * 100 / Math.max(1, conv.contextWindow)));
                cb.addView(p, Ui.matchWrap(a, 8));
            }
            sheet.addView(cb, Ui.matchWrap(a, 12));
        }
        MaterialButton done = Ui.primary(a, "完成", v -> ov.close());
        done.setMinHeight(Ui.dp(a, 56));
        sheet.addView(done, Ui.matchWrap(a, 22));
        ov.addSheet(sheet);
        ov.show();
    }

    private static void window(AppCompatActivity a, LinearLayout box, Map<String, Object> w) {
        if (w.isEmpty()) return;
        long mins = Json.num(w, "windowDurationMins", -1);
        String name = mins == 300 ? "5 小时" : mins == 10080 ? "每周" : mins > 0 ? (mins >= 1440 ? mins / 1440 + " 天" : mins / 60 + " 小时") : "额度";
        long used = Json.num(w, "usedPercent", 0);
        LinearLayout row = Ui.hbox(a);
        row.setPadding(0, Ui.dp(a, 12), 0, 0);
        row.addView(Ui.text(a, name, 17, R.color.text), Ui.weight1());
        row.addView(Ui.text(a, "已用 " + used + "%", 16, used >= 90 ? R.color.error : R.color.text2));
        box.addView(row);
        box.addView(bar(a, (int) used), Ui.matchWrap(a, 8));
        long reset = Json.num(w, "resetsAt", -1);
        if (reset > 0) {
            box.addView(Ui.text(a, "预计 " + new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(reset * 1000)) + " 重置", 13, R.color.text3), Ui.matchWrap(a, 6));
        }
        View sp = new View(a);
        box.addView(sp, new LinearLayout.LayoutParams(1, Ui.dp(a, 6)));
    }

    private static LinearProgressIndicator bar(AppCompatActivity a, int pct) {
        LinearProgressIndicator p = new LinearProgressIndicator(a);
        p.setTrackStopIndicatorSize(0);
        p.setTrackThickness(Ui.dp(a, 6));
        p.setTrackCornerRadius(Ui.dp(a, 3));
        p.setIndicatorColor(pct >= 90 ? Ui.color(a, R.color.error) : Ui.accent(a));
        p.setTrackColor(Ui.color(a, R.color.surface2));
        p.setMax(100);
        p.setProgressCompat(Math.max(0, Math.min(100, pct)), false);
        return p;
    }

    private static String text(CodexRuntimeManager rt, Conversation conv) {
        StringBuilder b = new StringBuilder();
        if (conv != null) b.append(conv.tokensUsed >= 0 ? "本会话累计：" + String.format(Locale.ROOT, "%,d", conv.tokensUsed) + " 个令牌\n\n" : "本会话还没有用量数据\n\n");
        Map<String, Object> rl = rt.lastRateLimits == null ? null : Json.obj(rt.lastRateLimits, "rateLimits");
        if (rl == null) return b.append("服务未提供额度信息").toString();
        for (String k : new String[]{"primary", "secondary"}) {
            Map<String, Object> w = Json.obj(rl, k);
            if (w.isEmpty()) continue;
            long mins = Json.num(w, "windowDurationMins", -1);
            b.append("• ").append(mins == 300 ? "5 小时" : mins == 10080 ? "每周" : "额度").append(" · 已用 ").append(Json.num(w, "usedPercent", 0)).append("%\n");
        }
        return b.toString();
    }
}
