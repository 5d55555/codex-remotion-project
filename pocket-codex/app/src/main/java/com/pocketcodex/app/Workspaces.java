package com.pocketcodex.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 用过的工作区列表（最近的在前），只记录路径，“移除”不删文件。 */
final class Workspaces {
    private Workspaces() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("workspaces", Context.MODE_PRIVATE);
    }

    static void remember(Context c, String path) {
        List<String> l = list(c);
        l.remove(path);
        l.add(0, path);
        save(c, l);
    }

    static void forget(Context c, String path) {
        List<String> l = list(c);
        l.remove(path);
        save(c, l);
    }

    static List<String> list(Context c) {
        String raw = sp(c).getString("list", "");
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private static void save(Context c, List<String> l) {
        Set<String> uniq = new LinkedHashSet<>(l);
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String s : uniq) {
            if (n++ >= 20) break;
            b.append(s).append('\n');
        }
        sp(c).edit().putString("list", b.toString()).apply();
    }

    static boolean isShared(CodexRuntimeManager rt, String path) {
        return path != null && !path.startsWith(rt.paths.root.getAbsolutePath());
    }

    /** 显示名：应用内工作区的目录是 UUID，名字另存；手机存储的用文件夹名。 */
    static String name(String path) {
        if (path == null) return "工作区";
        String n = names.get(path);
        return n != null ? n : new File(path).getName();
    }

    private static final java.util.Map<String, String> names = new java.util.HashMap<>();

    static void loadNames(Context c) {
        names.clear();
        for (java.util.Map.Entry<String, ?> e : c.getSharedPreferences("workspace_names", Context.MODE_PRIVATE).getAll().entrySet()) {
            if (e.getValue() instanceof String) names.put(e.getKey(), (String) e.getValue());
        }
    }

    static void setName(Context c, String path, String name) {
        names.put(path, name);
        c.getSharedPreferences("workspace_names", Context.MODE_PRIVATE).edit().putString(path, name).apply();
    }
}
