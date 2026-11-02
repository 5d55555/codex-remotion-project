package com.pocketcodex.app;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;

/** 模型和推理强度的选择（列表来自 model/list，不硬编码）。 */
final class ModelPicker {
    private ModelPicker() {
    }

    static String effortName(String e) {
        if (e == null) return "由模型决定";
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
            case "none":
                return "关闭";
            default:
                return e;
        }
    }

    static void show(Context c, CodexRuntimeManager rt, Runnable done) {
        if (rt.models.isEmpty()) {
            new AlertDialog.Builder(c).setTitle("选择模型").setMessage(rt.serverAlive() ? "当前没有可用模型。" : "Codex 还没启动，稍后再试。").setPositiveButton("好", null).show();
            return;
        }
        final List<CodexRuntimeManager.ModelInfo> ms = rt.models;
        List<String> labels = new ArrayList<>();
        labels.add("默认（由 Codex 决定）");
        int checked = 0;
        for (int i = 0; i < ms.size(); i++) {
            CodexRuntimeManager.ModelInfo m = ms.get(i);
            labels.add(m.displayName + (m.isDefault ? " · 默认" : "") + (m.description.isEmpty() ? "" : "\n" + m.description));
            if (m.id.equals(rt.prefs.model())) checked = i + 1;
        }
        new AlertDialog.Builder(c).setTitle("选择模型").setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, w) -> {
            rt.prefs.setModel(w == 0 ? null : ms.get(w - 1).id);
            // 强度不在新模型支持列表里就清掉
            CodexRuntimeManager.ModelInfo cur = rt.currentModel();
            if (rt.prefs.effort() != null && cur != null && !cur.efforts.isEmpty() && !cur.efforts.contains(rt.prefs.effort())) rt.prefs.setEffort(null);
            d.dismiss();
            if (done != null) done.run();
        }).setNegativeButton("取消", null).show();
    }

    static void showEffort(Context c, CodexRuntimeManager rt, Runnable done) {
        CodexRuntimeManager.ModelInfo m = rt.currentModel();
        final List<String> efforts = m == null ? new ArrayList<String>() : m.efforts;
        if (efforts.isEmpty()) {
            new AlertDialog.Builder(c).setTitle("推理强度").setMessage("由模型决定。").setPositiveButton("好", null).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        labels.add("由模型决定" + (m.defaultEffort == null ? "" : "（" + effortName(m.defaultEffort) + "）"));
        int checked = 0;
        for (int i = 0; i < efforts.size(); i++) {
            labels.add(effortName(efforts.get(i)));
            if (efforts.get(i).equals(rt.prefs.effort())) checked = i + 1;
        }
        new AlertDialog.Builder(c).setTitle("推理强度").setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, w) -> {
            rt.prefs.setEffort(w == 0 ? null : efforts.get(w - 1));
            d.dismiss();
            if (done != null) done.run();
        }).setNegativeButton("取消", null).show();
    }
}
