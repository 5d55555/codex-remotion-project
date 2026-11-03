package com.pocketcodex.app;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模型、推理、速度、权限的选择：ChatGPT 那样的底部面板——一张描边圆角卡片（每行“名称 …… 当前值 ⌄”），
 * 点一行在它上方弹出下拉卡片，底部黑色“完成”。选了立即生效（下一轮起用）。列表来自 model/list，不硬编码。
 */
final class ModelPicker {
    private ModelPicker() {
    }

    /* ---------------- 名称 ---------------- */

    static String effortName(String e) {
        if (e == null) return "自动";
        switch (e) {
            case "minimal":
                return "最低";
            case "low":
                return "低";
            case "medium":
                return "中";
            case "high":
                return "高";
            case "xhigh":
                return "很高";
            case "max":
                return "极高";
            case "ultra":
                return "超高";
            case "none":
                return "关闭";
            default:
                return e;
        }
    }

    static String effortHint(String e, String serverDesc) {
        switch (e) {
            case "minimal":
                return "几乎不思考，最快";
            case "low":
                return "更快，适合简单任务";
            case "medium":
                return "速度和质量均衡";
            case "high":
                return "想得更深，稍慢";
            case "xhigh":
                return "非常深入，耗时更长";
            case "max":
                return "全力思考，用量很大";
            case "ultra":
                return "最长的思考，用量最大";
            case "none":
                return "不做推理";
            default:
                return serverDesc == null || serverDesc.isEmpty() ? null : serverDesc;
        }
    }

    /** “GPT-6.1-Sol” → “6.1 Sol”（ChatGPT 里的叫法）。 */
    static String shortModel(String displayName) {
        if (displayName == null) return "默认";
        String s = displayName.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("gpt-")) s = s.substring(4);
        s = s.replace('-', ' ').replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? displayName : s;
    }

    /** 速度档的中文名和说明。 */
    static String[] tierLabel(String id, String name, String desc) {
        String k = (id + " " + (name == null ? "" : name)).toLowerCase(Locale.ROOT);
        if (k.contains("priority") || k.contains("fast")) return new String[]{"快速", "用量更高"};
        if (k.contains("flex")) return new String[]{"省用量", "更慢，用量更低"};
        return new String[]{name == null || name.isEmpty() ? id : name, desc == null || desc.isEmpty() ? null : desc};
    }

    /** 实际生效的推理强度（选的不支持就用模型默认）。 */
    static String effectiveEffort(CodexRuntimeManager rt) {
        CodexRuntimeManager.ModelInfo m = rt.currentModel();
        String e = rt.prefs.effort();
        if (e != null && (m == null || m.efforts.isEmpty() || m.efforts.contains(e))) return e;
        return m == null ? null : m.defaultEffort;
    }

    static boolean fast(CodexRuntimeManager rt) {
        CodexRuntimeManager.ModelInfo m = rt.currentModel();
        return rt.prefs.speed() != null && m != null && m.serviceTiers.contains(rt.prefs.speed());
    }

    /** 输入框里那颗“6.1 Sol 中”。 */
    static String chipLabel(CodexRuntimeManager rt) {
        CodexRuntimeManager.ModelInfo m = rt.currentModel();
        if (m == null) return "默认";
        String e = effectiveEffort(rt);
        return shortModel(m.displayName) + (e == null ? "" : " " + effortName(e));
    }

    /** 选了新模型以后：它不支持的推理强度、速度档清掉。 */
    static void normalize(CodexRuntimeManager rt) {
        CodexRuntimeManager.ModelInfo cur = rt.currentModel();
        if (cur == null) return;
        if (rt.prefs.effort() != null && !cur.efforts.isEmpty() && !cur.efforts.contains(rt.prefs.effort())) rt.prefs.setEffort(null);
        if (rt.prefs.speed() != null && !cur.serviceTiers.contains(rt.prefs.speed())) rt.prefs.setSpeed(null);
    }

    /** 设置页那一行的摘要。 */
    static String summary(CodexRuntimeManager rt) {
        CodexRuntimeManager.ModelInfo m = rt.currentModel();
        String model = rt.prefs.model() == null || m == null ? "默认" + (m == null ? "" : "（" + shortModel(m.displayName) + "）") : shortModel(m.displayName);
        String e = rt.prefs.effort();
        return model + " · 推理" + (e == null ? "自动" : effortName(e)) + " · " + (fast(rt) ? "快速" : "标准");
    }

    /* ---------------- 面板 ---------------- */

    private static final class Option {
        final String title, sub;
        final boolean checked, enabled;
        final Runnable pick;

        Option(String title, String sub, boolean checked, boolean enabled, Runnable pick) {
            this.title = title;
            this.sub = sub;
            this.checked = checked;
            this.enabled = enabled;
            this.pick = pick;
        }
    }

    private interface Options {
        List<Option> get();
    }

    private interface Value {
        String get();
    }

    /** 打开面板。chatMode 为 true 时不显示“权限”（聊天模式不运行命令）。changed 在每次改动后调用。 */
    static Overlay show(AppCompatActivity a, CodexRuntimeManager rt, boolean chatMode, Runnable changed) {
        final Overlay ov = new Overlay(a, true);
        LinearLayout sheet = Ui.vbox(a);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.color(a, R.color.card));
        float r = Ui.dp(a, 32);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(Ui.dp(a, 20), Ui.dp(a, 12), Ui.dp(a, 20), Ui.dp(a, 24));
        // 拖动条（视觉提示：这是可以收起的面板）
        View handle = new View(a);
        handle.setBackground(Ui.round(a, R.color.border, 0, 2));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = Ui.dp(a, 22);
        sheet.addView(handle, hl);

        LinearLayout box = Ui.vbox(a);
        box.setBackground(Ui.round(a, R.color.card, R.color.border, 28));
        sheet.addView(box, Ui.matchWrap());

        if (rt.models.isEmpty()) {
            TextView t = Ui.caption(a, rt.serverAlive() ? "正在读取账号可用的模型…" : "Codex 还没启动，启动后才能选择模型。");
            t.setPadding(Ui.dp(a, 22), Ui.dp(a, 18), Ui.dp(a, 22), Ui.dp(a, 18));
            box.addView(t);
        } else {
            addRow(ov, box, "模型", () -> {
                CodexRuntimeManager.ModelInfo m = rt.currentModel();
                return rt.prefs.model() == null || m == null ? "默认" : shortModel(m.displayName);
            }, () -> {
                List<Option> l = new ArrayList<>();
                l.add(new Option("默认", "推荐的前沿模型组合", rt.prefs.model() == null, true, () -> rt.prefs.setModel(null)));
                for (CodexRuntimeManager.ModelInfo m : rt.models) {
                    l.add(new Option(shortModel(m.displayName), null, m.id.equals(rt.prefs.model()), true, () -> rt.prefs.setModel(m.id)));
                }
                return l;
            }, () -> {
                normalize(rt);
                changed.run();
            }, true);
            addRow(ov, box, "推理", () -> {
                String e = rt.prefs.effort();
                String eff = effectiveEffort(rt);
                return e == null ? "自动" + (eff == null ? "" : "（" + effortName(eff) + "）") : effortName(e);
            }, () -> {
                CodexRuntimeManager.ModelInfo m = rt.currentModel();
                List<Option> l = new ArrayList<>();
                l.add(new Option("自动", "由模型决定" + (m == null || m.defaultEffort == null ? "" : "，当前是“" + effortName(m.defaultEffort) + "”"),
                        rt.prefs.effort() == null, true, () -> rt.prefs.setEffort(null)));
                if (m != null) {
                    for (int i = 0; i < m.efforts.size(); i++) {
                        String e = m.efforts.get(i);
                        String d = i < m.effortDescriptions.size() ? m.effortDescriptions.get(i) : null;
                        l.add(new Option(effortName(e), effortHint(e, d), e.equals(rt.prefs.effort()), true, () -> rt.prefs.setEffort(e)));
                    }
                }
                return l;
            }, changed, true);
            addRow(ov, box, "速度", () -> fast(rt) ? "快速" : "标准", () -> {
                CodexRuntimeManager.ModelInfo m = rt.currentModel();
                List<Option> l = new ArrayList<>();
                l.add(new Option("标准", "默认用量", !fast(rt), true, () -> rt.prefs.setSpeed(null)));
                boolean any = false;
                if (m != null) {
                    for (int i = 0; i < m.serviceTiers.size(); i++) {
                        String id = m.serviceTiers.get(i);
                        String[] lab = tierLabel(id, i < m.serviceTierNames.size() ? m.serviceTierNames.get(i) : null,
                                i < m.serviceTierDescriptions.size() ? m.serviceTierDescriptions.get(i) : null);
                        l.add(new Option(lab[0], lab[1], id.equals(rt.prefs.speed()) && fast(rt), true, () -> rt.prefs.setSpeed(id)));
                        any = true;
                    }
                }
                if (!any) l.add(new Option("快速", "当前模型不提供快速档", false, false, null));
                return l;
            }, changed, !chatMode);
            if (!chatMode) {
                addRow(ov, box, "权限", () -> Prefs.MODE_AUTO.equals(rt.prefs.mode()) ? "完全自动" : "每步确认", () -> {
                    boolean auto = Prefs.MODE_AUTO.equals(rt.prefs.mode());
                    List<Option> l = new ArrayList<>();
                    l.add(new Option("每步确认", "运行命令、修改文件前先问你", !auto, true, () -> rt.prefs.setMode(Prefs.MODE_CONFIRM)));
                    l.add(new Option("完全自动", "不再逐项确认，已发生的改动不会自动撤销", auto, true, null));
                    return l;
                }, changed, false);
            }
        }

        MaterialButton done = Ui.primary(a, "完成", v -> ov.close());
        done.setMinHeight(Ui.dp(a, 56));
        sheet.addView(done, Ui.matchWrap(a, 24));
        ov.addSheet(sheet);
        ov.show();
        return ov;
    }

    /** 卡片里的一行；点开弹下拉。 */
    private static void addRow(Overlay ov, LinearLayout box, String label, Value value, Options options, Runnable changed, boolean divider) {
        AppCompatActivity a = ov.a;
        LinearLayout row = Ui.hbox(a);
        row.setMinimumHeight(Ui.dp(a, 66));
        row.setPadding(Ui.dp(a, 22), 0, Ui.dp(a, 18), 0);
        TextView l = Ui.text(a, label, 17, R.color.text);
        row.addView(l, Ui.weight1());
        TextView v = Ui.text(a, value.get(), 17, R.color.text3);
        row.addView(v);
        ImageView chev = Ui.icon(a, R.drawable.ic_chev_down, R.color.text2, 22);
        LinearLayout.LayoutParams cl = (LinearLayout.LayoutParams) chev.getLayoutParams();
        cl.leftMargin = Ui.dp(a, 10);
        row.addView(chev, cl);
        Ui.ripple(row, false);
        row.setContentDescription(label + "：" + value.get());
        row.setOnClickListener(x -> openDropdown(ov, row, chev, options.get(), () -> {
            changed.run();
            // 换了模型，其他行的值可能变（推理、速度被清掉）
            refreshValues(box);
        }, label));
        row.setTag(new Object[]{v, value, label});
        box.addView(row, Ui.matchWrap());
        if (divider) {
            View d = new View(a);
            d.setBackgroundColor(Ui.color(a, R.color.border));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(a, 1) / 2));
            dl.leftMargin = Ui.dp(a, 22);
            dl.rightMargin = Ui.dp(a, 22);
            box.addView(d, dl);
        }
    }

    private static void refreshValues(LinearLayout box) {
        for (int i = 0; i < box.getChildCount(); i++) {
            View row = box.getChildAt(i);
            Object t = row.getTag();
            if (t instanceof Object[]) {
                Object[] o = (Object[]) t;
                String val = ((Value) o[1]).get();
                ((TextView) o[0]).setText(val);
                row.setContentDescription(o[2] + "：" + val);
            }
        }
    }

    /** 下拉卡片：右对齐、底边贴着这一行的上沿；太高就滚动。 */
    private static void openDropdown(Overlay sheetOv, View row, ImageView chev, List<Option> opts, Runnable picked, String label) {
        AppCompatActivity a = sheetOv.a;
        Overlay dd = new Overlay(a, false);
        LinearLayout card = Overlay.card(a, 28);
        card.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        ScrollView sv = new ScrollView(a);
        LinearLayout list = Ui.vbox(a);
        for (Option o : opts) {
            list.addView(Overlay.optionRow(a, o.title, o.sub, o.checked, o.enabled, x -> {
                dd.close();
                if (o.pick == null && "权限".equals(label)) { // 完全自动：先确认
                    confirmAuto(a, picked);
                    return;
                }
                if (o.pick != null) o.pick.run();
                picked.run();
            }), Ui.matchWrap());
        }
        sv.addView(list);
        card.addView(sv);
        int[] b = dd.boundsOf(row);
        int w = Math.max(Ui.dp(a, 230), Math.round(dd.layerWidth() * 0.62f));
        int maxH = Math.max(Ui.dp(a, 160), b[1] - Ui.dp(a, 24));
        card.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int h = Math.min(card.getMeasuredHeight(), maxH);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.START);
        lp.leftMargin = Math.max(Ui.dp(a, 12), b[2] - w);
        lp.topMargin = Math.max(Ui.dp(a, 12), b[1] - h + Ui.dp(a, 6));
        card.setPivotX(w);
        card.setPivotY(h);
        dd.add(card, lp);
        chev.setRotation(180);
        dd.onClose(() -> chev.setRotation(0));
        dd.show();
    }

    private static void confirmAuto(AppCompatActivity a, Runnable picked) {
        CodexRuntimeManager rt = CodexRuntimeManager.get(a);
        new AlertDialog.Builder(a).setTitle("开启完全自动？")
                .setMessage("助手将直接运行命令和修改文件，不再逐项确认。它能访问本应用有权限访问的位置；工作区不是独立隔离区。\n你仍可随时停止，但已经发生的改动不会自动撤销。")
                .setPositiveButton("开启完全自动", (d, w) -> {
                    rt.prefs.setMode(Prefs.MODE_AUTO);
                    picked.run();
                }).setNegativeButton("保持每步确认", null).show();
    }
}
