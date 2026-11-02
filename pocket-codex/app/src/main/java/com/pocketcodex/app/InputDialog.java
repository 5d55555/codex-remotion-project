package com.pocketcodex.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.pocketcodex.core.Conversation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent 向用户提问（item/tool/requestUserInput）：每个问题选一项或填文字，提交后按 question id 回复。不预选、不自动提交。 */
final class InputDialog {
    private final Context ctx;
    private final CodexRuntimeManager rt;
    private final Conversation conv;
    private final Conversation.InputRequest req;
    private AlertDialog dialog;

    InputDialog(Context ctx, CodexRuntimeManager rt, Conversation conv, Conversation.InputRequest req) {
        this.ctx = ctx;
        this.rt = rt;
        this.conv = conv;
        this.req = req;
    }

    boolean isFor(Conversation.InputRequest r) {
        return r == req && dialog != null && dialog.isShowing();
    }

    void dismiss() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    void show() {
        LinearLayout box = Ui.vbox(ctx);
        box.setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 8), Ui.dp(ctx, 24), 0);
        final List<RadioGroup> groups = new ArrayList<>();
        final List<EditText> texts = new ArrayList<>();
        for (Conversation.Question q : req.questions) {
            if (!q.header.isEmpty()) {
                TextView h = Ui.text(ctx, q.header, 14, R.color.primary);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                box.addView(h, Ui.matchWrap(ctx, 12));
            }
            box.addView(Ui.body(ctx, q.question), Ui.matchWrap(ctx, 4));
            RadioGroup g = new RadioGroup(ctx);
            for (int i = 0; i < q.options.size(); i++) {
                RadioButton rb = new RadioButton(ctx);
                String[] o = q.options.get(i);
                rb.setText(o[1].isEmpty() ? o[0] : o[0] + "\n" + o[1]);
                rb.setId(i + 1);
                rb.setTag(o[0]);
                g.addView(rb);
            }
            box.addView(g, Ui.matchWrap(ctx, 4));
            EditText et = null;
            if (q.options.isEmpty() || q.allowOther) {
                et = new EditText(ctx);
                et.setHint(q.options.isEmpty() ? "输入回答" : "或者自己填");
                if (q.secret) et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                box.addView(et, Ui.matchWrap(ctx, 4));
            }
            groups.add(g);
            texts.add(et);
        }
        ScrollView sv = new ScrollView(ctx);
        sv.addView(box);
        dialog = new AlertDialog.Builder(ctx).setTitle("Codex 在问你").setView(sv)
                .setPositiveButton("提交回答", null)
                .setNegativeButton("稍后回答", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Map<String, List<String>> answers = new LinkedHashMap<>();
            for (int i = 0; i < req.questions.size(); i++) {
                Conversation.Question q = req.questions.get(i);
                List<String> a = new ArrayList<>();
                RadioGroup g = groups.get(i);
                EditText et = texts.get(i);
                String typed = et == null ? "" : et.getText().toString().trim();
                if (!typed.isEmpty()) a.add(typed);
                else if (g.getCheckedRadioButtonId() > 0) a.add((String) g.findViewById(g.getCheckedRadioButtonId()).getTag());
                if (a.isEmpty()) {
                    Toast.makeText(ctx, "还有问题没回答：" + (q.header.isEmpty() ? q.question : q.header), Toast.LENGTH_SHORT).show();
                    return;
                }
                answers.put(q.id, a);
            }
            if (rt.answerInput(conv, req, answers)) dismiss();
            else Toast.makeText(ctx, "提交失败：Codex 进程已经不在了", Toast.LENGTH_LONG).show();
        }));
        dialog.show();
    }
}
