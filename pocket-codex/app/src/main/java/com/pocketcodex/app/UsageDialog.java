package com.pocketcodex.app;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.pocketcodex.core.Conversation;
import com.pocketcodex.core.Json;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/** 用量：本会话的令牌（服务端累计值，不自己相加）和账号额度窗口。 */
final class UsageDialog {
    private UsageDialog() {
    }

    static void show(Context c, CodexRuntimeManager rt, Conversation conv) {
        StringBuilder b = new StringBuilder();
        if (conv != null) {
            b.append(conv.tokensUsed >= 0 ? "本会话累计：" + String.format(Locale.ROOT, "%,d", conv.tokensUsed) + " 个令牌" : "本会话还没有用量数据");
            if (conv.lastInputTokens >= 0) b.append("\n最近一次模型调用输入：").append(String.format(Locale.ROOT, "%,d", conv.lastInputTokens)).append(" 个令牌");
            if (conv.contextWindow > 0) b.append("\n模型上下文窗口：").append(String.format(Locale.ROOT, "%,d", conv.contextWindow));
            b.append("\n\n");
        }
        b.append("账号额度\n");
        Map<String, Object> r = rt.lastRateLimits;
        Map<String, Object> rl = r == null ? null : Json.obj(r, "rateLimits");
        if (rl == null || (Json.obj(rl, "primary").isEmpty() && Json.obj(rl, "secondary").isEmpty())) {
            b.append("服务未提供额度信息");
        } else {
            window(b, Json.obj(rl, "primary"));
            window(b, Json.obj(rl, "secondary"));
        }
        new AlertDialog.Builder(c).setTitle("用量").setMessage(b.toString()).setPositiveButton("关闭", null).show();
    }

    private static void window(StringBuilder b, Map<String, Object> w) {
        if (w.isEmpty()) return;
        long mins = Json.num(w, "windowDurationMins", -1);
        String name = mins == 300 ? "5 小时" : mins == 10080 ? "每周" : mins > 0 ? (mins >= 1440 ? mins / 1440 + " 天" : mins / 60 + " 小时") : "额度";
        b.append("• ").append(name).append(" · 已用 ").append(Json.num(w, "usedPercent", 0)).append("%");
        long reset = Json.num(w, "resetsAt", -1);
        if (reset > 0) b.append("，预计 ").append(new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(reset * 1000))).append(" 重置");
        b.append('\n');
    }
}
