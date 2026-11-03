package com.pocketcodex.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;
import org.robolectric.RuntimeEnvironment;
import com.pocketcodex.core.Conversation;
import com.pocketcodex.core.DeviceAuth;
import com.pocketcodex.core.DeviceLogin;
import com.pocketcodex.core.Installer;
import com.pocketcodex.core.Json;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

/**
 * 把每个界面在各种状态下真的跑起来（Robolectric，真实的 Android 框架），并用原生图形模式截图到 smoke/shots/。
 * 容器里没有模拟器，这是检查“点了不会闪退、画面对不对”的办法。状态直接塞进 CodexRuntimeManager（不启动真的 codex）。
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 36, qualifiers = "w400dp-h860dp-xxhdpi")
public class ScreensTest {
    private CodexRuntimeManager rt;
    private String theme = "light";

    @Before
    public void setUp() {
        Paths.abiOverride = "arm64-v8a";
        Overlay.animate = false;
        Thumbs.synchronous = true; // 截图里要看到缩略图
        CodexRuntimeManager.resetForTest();
        rt = CodexRuntimeManager.get(RuntimeEnvironment.getApplication());
    }

    /* ---------------- 工具 ---------------- */

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** 等 worker 线程（check 之类）跑完再把主线程的消息处理掉。 */
    private static void settle() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            Thread.sleep(30);
            idle();
        }
    }

    private void shot(String name, View root) throws Exception {
        DisplayMetrics dm = root.getResources().getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        File d = new File(System.getProperty("shots.dir", "build/shots"));
        d.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(d, name + "_" + theme + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    private void shot(String name, Activity a) throws Exception {
        idle();
        shot(name, a.getWindow().getDecorView());
    }

    private static boolean hasText(View v, String s) {
        if (v instanceof TextView && ((TextView) v).getText().toString().contains(s) && v.isShown()) return true;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (hasText(g.getChildAt(i), s)) return true;
        }
        return false;
    }

    private static View findText(View v, String s) {
        if (v instanceof TextView && ((TextView) v).getText().toString().equals(s)) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = findText(g.getChildAt(i), s);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void readyWithWorkspace() {
        File ws = new File(rt.paths.workspaces, "我的工作区");
        ws.mkdirs();
        rt.prefs.setWorkspace(ws.getAbsolutePath());
        rt.prefs.setIntroSeen();
        rt.prefs.setSandboxNoticeSeen();
        rt.installedVersion = Installer.TESTED_VERSION;
        rt.accountEmail = "me@example.com";
        rt.accountPlan = "plus";
        rt.accountType = "chatgpt";
        rt.stage = CodexRuntimeManager.Stage.READY;
    }

    /* ---------------- 首次设置 ---------------- */

    @Test
    public void setupFlowLight() throws Exception {
        setupFlow();
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-night-xxhdpi")
    public void setupFlowDark() throws Exception {
        theme = "dark";
        setupFlow();
    }

    private void setupFlow() throws Exception {
        ActivityController<SetupActivity> c = Robolectric.buildActivity(SetupActivity.class).setup();
        settle();
        SetupActivity a = c.get();
        View root = a.getWindow().getDecorView();
        assertTrue(hasText(root, "开始设置"));
        shot("01_welcome", a);
        // 点“开始设置” → 安装页（没装：NOT_INSTALLED）
        findText(root, "开始设置").performClick();
        settle();
        assertEquals(CodexRuntimeManager.Stage.NOT_INSTALLED, rt.stage);
        assertTrue(hasText(root, "下载并安装"));
        assertTrue(hasText(root, "npm 官方"));
        shot("02_install", a);
        // 不支持的处理器：一开始就说清楚，不给下载按钮
        Paths.abiOverride = "armeabi-v7a";
        rt.setStageForTest(CodexRuntimeManager.Stage.NOT_INSTALLED);
        assertTrue(hasText(root, "这台设备暂不支持"));
        assertFalse(hasText(root, "下载并安装"));
        shot("02b_unsupported", a);
        Paths.abiOverride = "arm64-v8a";
        rt.setStageForTest(CodexRuntimeManager.Stage.NOT_INSTALLED);
        assertTrue(hasText(root, "下载并安装"));
        // 暂停下载后：保留已下载部分，按钮是“继续下载”
        rt.installErrorKind = Installer.Kind.CANCELLED;
        rt.setStageForTest(CodexRuntimeManager.Stage.NOT_INSTALLED);
        assertTrue(hasText(root, "继续下载"));
        rt.installErrorKind = null;
        // 下载中
        rt.install.stage = Installer.Stage.DOWNLOAD;
        rt.install.done = 82_400_000L;
        rt.install.total = 164_402_913L;
        rt.install.bytesPerSec = 3.2 * 1048576;
        rt.install.sourceLabel = Installer.NPM.label;
        rt.setStageForTest(CodexRuntimeManager.Stage.INSTALLING);
        assertTrue(hasText(root, "正在下载 Codex"));
        shot("03_downloading", a);
        // 失败
        rt.installErrorKind = Installer.Kind.NETWORK;
        rt.errorMessage = "连不上 registry.npmjs.org：connect timed out";
        rt.setStageForTest(CodexRuntimeManager.Stage.INSTALL_FAILED);
        assertTrue(hasText(root, "暂时无法下载"));
        shot("04_install_failed", a);
        // 登录：拿到代码
        rt.errorMessage = null;
        rt.login = new DeviceLogin.State(DeviceLogin.Phase.WAITING, "https://auth.openai.com/codex/device", "VR0G-2QTVX",
                SystemClock.elapsedRealtime() + 14 * 60_000 + 32_000, 15 * 60_000, null, null, null, 1, 0);
        rt.setStageForTest(CodexRuntimeManager.Stage.LOGGING_IN);
        idle();
        assertTrue(hasText(root, "VR0G-2QTVX"));
        assertTrue(hasText(root, "复制代码并打开浏览器"));
        shot("05_login_code", a);
        // 点“复制代码并打开浏览器”：复制到剪贴板、打开官方授权页
        findText(root, "复制代码并打开浏览器").performClick();
        Intent opened = shadowOf(a).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals("https://auth.openai.com/codex/device", opened.getDataString());
        // 网络重试
        rt.login = new DeviceLogin.State(DeviceLogin.Phase.RETRYING, null, null, 0, 0, DeviceLoginMessages.NETWORK, "Error: error sending request for url (https://auth.openai.com/...)",
                DeviceAuth.Outcome.NETWORK, 1, SystemClock.elapsedRealtime() + 8000);
        rt.setStageForTest(CodexRuntimeManager.Stage.LOGGING_IN);
        idle();
        assertTrue(hasText(root, "暂时无法连接登录服务"));
        shot("06_login_retry", a);
        // 设备码授权没开
        rt.login = new DeviceLogin.State(DeviceLogin.Phase.FAILED, null, null, 0, 0, "x", "device code login is not enabled", DeviceAuth.Outcome.DEVICE_AUTH_DISABLED, 1, 0);
        rt.setStageForTest(CodexRuntimeManager.Stage.LOGGING_IN);
        idle();
        assertTrue(hasText(root, "需要开启设备码授权"));
        shot("07_login_disabled", a);
        // 登录成功 → 工作区
        rt.accountEmail = "me@example.com";
        rt.accountPlan = "plus";
        rt.setStageForTest(CodexRuntimeManager.Stage.READY);
        idle();
        assertTrue(hasText(root, "文件放在哪里？"));
        shot("08_workspace", a);
        // 创建并进入 → 直接进新对话（和 ChatGPT 一样，会话列表在抽屉里）
        findText(root, "创建并进入").performClick();
        Intent main = shadowOf(a).getNextStartedActivity();
        assertNotNull(main);
        assertEquals(ChatActivity.class.getName(), main.getComponent().getClassName());
        assertTrue(new File(rt.prefs.workspace()).isDirectory());
        assertTrue(a.isFinishing());
    }

    /* ---------------- 主界面 ---------------- */

    @Test
    public void mainScreensLight() throws Exception {
        mainScreens();
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-night-xxhdpi")
    public void mainScreensDark() throws Exception {
        theme = "dark";
        mainScreens();
    }

    private void mainScreens() throws Exception {
        readyWithWorkspace();
        long now = System.currentTimeMillis() / 1000;
        rt.threads.add(newThread("t1", "整理旅行清单", "帮我把 trip.md 里的行程按日期排好，再加一列预算", now - 120));
        rt.threads.add(newThread("t2", null, "看看这个文件夹里有什么，写一份 README", now - 3 * 3600));
        rt.threads.add(newThread("t3", "改简历", "把 resume.md 的工作经历精简到一页", now - 2 * 86400));
        rt.threadsLoaded = true;
        rt.runningThreadId = "t1";
        File ws = new File(rt.prefs.workspace());
        new File(ws, "notes").mkdirs();
        write(new File(ws, "trip.md"), "# 行程\n\n- 11月3日 东京\n- 11月5日 京都\n");
        write(new File(ws, "budget.csv"), "日期,项目,金额\n11-03,酒店,800\n");
        rt.prefs.setPinned(java.util.Collections.singletonList("t3"));
        // 对话页左边拉出的抽屉：置顶、聊天、底部“新对话”和设置
        ChatActivity chat = Robolectric.buildActivity(ChatActivity.class, new Intent(RuntimeEnvironment.getApplication(), ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_CWD, rt.prefs.workspace())).setup().get();
        settle();
        layout(chat);
        chat.openDrawer();
        idle();
        layout(chat);
        View cr = chat.getWindow().getDecorView();
        assertTrue(hasText(cr, "整理旅行清单"));
        assertTrue(hasText(cr, "置顶"));
        assertTrue(hasText(cr, "新对话"));
        assertTrue(hasText(cr, "开发工具"));
        shot("10_drawer", chat);
        // 文件页
        MainActivity fa = Robolectric.buildActivity(MainActivity.class, new Intent(RuntimeEnvironment.getApplication(), MainActivity.class)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_FILES)).setup().get();
        settle();
        View root = fa.getWindow().getDecorView();
        assertFalse("文件页不应跳回设置", fa.isFinishing());
        assertTrue(hasText(root, "trip.md"));
        assertTrue(hasText(root, "notes"));
        layout(fa);
        shot("11_files", fa);
        // 设置页（首页只有几个入口）
        rt.catalog.add(new Catalog(true, "pocket-apk-build", "构建 APK", "aapt2 + d8 + apksigner，或 Gradle 覆盖 aapt2", null, null, "/x/SKILL.md"));
        rt.catalog.add(new Catalog(true, "pocket-reverse", "逆向分析", "apktool、jadx、radare2，及 leanrea / REA / r2mcp", null, null, "/x/SKILL.md"));
        rt.catalog.add(new Catalog(true, "imagegen", "Image Gen", "Generate or edit images", null, null, "/x/SKILL.md"));
        MainActivity a = Robolectric.buildActivity(MainActivity.class, new Intent(RuntimeEnvironment.getApplication(), MainActivity.class)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS)).setup().get();
        settle();
        root = a.getWindow().getDecorView();
        assertTrue(hasText(root, "账户"));
        assertTrue(hasText(root, "我的口袋工坊"));
        assertTrue(hasText(root, "技能与插件"));
        assertTrue(hasText(root, "用量与限额"));
        assertFalse("具体选项在子页面里", hasText(root, "信任用户安装的证书"));
        layout(a);
        shot("12_settings", a);
        // 外观下拉
        tap(root, "外观");
        assertTrue(hasText(root, "深色"));
        shot("12b_settings_dropdown", a);
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        // 搜索设置：能搜到子页面里的项
        android.widget.EditText search = (android.widget.EditText) field(a, "searchBox");
        search.setText("证书");
        idle();
        assertTrue(hasText(root, "信任用户安装的证书"));
        assertFalse(findText(root, "强调色") != null && findText(root, "强调色").isShown());
        layout(a);
        shot("12c_settings_search", a);
        search.setText("");
        idle();
        // 开发工具子页
        tap(root, "开发工具");
        assertTrue(hasText(root, "安装开发工具"));
        assertTrue(hasText(root, "网络调试与压测"));
        assertTrue(hasText(root, "终端"));
        layout(a);
        shot("13_settings_devtools", a);
        // 开发工具：装好以后显示各工具版本；进度变化只刷新那张卡片
        rt.toolkitState = CodexRuntimeManager.ToolkitState.READY;
        rt.toolkitVersions.put("node", "24.22.2");
        rt.toolkitVersions.put("npm", "11.6.2");
        rt.toolkitVersions.put("git", "2.51.2");
        rt.toolkitVersions.put("python3", "3.14.8");
        rt.toolkitVersions.put("pip", "26.3");
        rt.toolkitVersions.put("bash", "5.3.3");
        rt.toolkitVersions.put("rg", "15.2.0");
        rt.toolkitVersions.put("curl", "8.23.0");
        rt.toolkitVersions.put("ffmpeg", "9.0.2");
        rt.toolkitPackages.addAll(com.pocketcodex.core.ToolBundles.find("media").packages);
        rt.toolkitPackages.add("bash");
        rt.setStageForTest(CodexRuntimeManager.Stage.READY);
        idle();
        assertTrue(hasText(root, "开发工具已就绪"));
        assertTrue(hasText(root, "Node.js 24.22.2"));
        assertTrue(hasText(root, "已安装")); // 剪辑与程序化动画
        layout(a);
        shot("13b_settings_devtools_ready", a);
        // 返回首页，再进技能与插件
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertTrue(hasText(root, "我的口袋工坊"));
        tap(root, "技能与插件");
        assertTrue(hasText(root, "构建 APK"));
        assertTrue(hasText(root, "怎么加 MCP 服务"));
        layout(a);
        shot("13c_settings_skills", a);
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        // 用量与限额（底部面板）
        rt.lastRateLimits = Json.parseObject("{\"rateLimits\":{\"primary\":{\"usedPercent\":37,\"windowDurationMins\":300,\"resetsAt\":1793733725},"
                + "\"secondary\":{\"usedPercent\":76,\"windowDurationMins\":10080,\"resetsAt\":1793785825}}}");
        UsageDialog.show(a, rt, null);
        idle();
        assertTrue(hasText(root, "每周"));
        layout(a);
        shot("19_usage", a);
        // 打开文本查看
        Intent i = new Intent(a, FileViewerActivity.class).putExtra(FileViewerActivity.EXTRA_PATH, new File(ws, "trip.md").getAbsolutePath());
        FileViewerActivity fv = Robolectric.buildActivity(FileViewerActivity.class, i).setup().get();
        idle();
        assertTrue(hasText(fv.getWindow().getDecorView(), "UTF-8"));
        shot("14_viewer", fv);
        // 诊断页（不点开始，codex 不存在）
        DiagnosticsActivity da = Robolectric.buildActivity(DiagnosticsActivity.class).setup().get();
        shot("15_diagnostics", da);
        // 文件夹选择
        FolderPickerActivity fp = Robolectric.buildActivity(FolderPickerActivity.class).setup().get();
        shot("16_folder_picker", fp);
    }

    /** 把包含这段文字的 ScrollView 滚到它那里。 */
    private static void scrollTo(View root, String text) {
        View t = findTextContains(root, text);
        assertNotNull(t);
        int y = 0;
        View v = t;
        while (v.getParent() instanceof View && !(v.getParent() instanceof android.widget.ScrollView)) {
            y += v.getTop();
            v = (View) v.getParent();
        }
        y += v.getTop();
        if (v.getParent() instanceof android.widget.ScrollView) ((android.widget.ScrollView) v.getParent()).scrollTo(0, Math.max(0, y - 400));
    }

    private static View findTextContains(View v, String s) {
        if (v instanceof TextView && ((TextView) v).getText().toString().contains(s)) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = findTextContains(g.getChildAt(i), s);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static com.google.android.material.bottomnavigation.BottomNavigationView findNav(View v) {
        if (v instanceof com.google.android.material.bottomnavigation.BottomNavigationView) return (com.google.android.material.bottomnavigation.BottomNavigationView) v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                com.google.android.material.bottomnavigation.BottomNavigationView n = findNav(g.getChildAt(i));
                if (n != null) return n;
            }
        }
        return null;
    }

    private static CodexRuntimeManager.ThreadSummary newThread(String id, String name, String preview, long t) throws Exception {
        java.lang.reflect.Constructor<CodexRuntimeManager.ThreadSummary> k = CodexRuntimeManager.ThreadSummary.class
                .getDeclaredConstructor(String.class, String.class, String.class, String.class, long.class);
        k.setAccessible(true);
        return k.newInstance(id, name, preview, null, t);
    }

    private static void write(File f, String s) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    /* ---------------- 对话 ---------------- */

    @Test
    public void chatLight() throws Exception {
        chat();
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-night-xxhdpi")
    public void chatDark() throws Exception {
        theme = "dark";
        chat();
    }

    private void chat() throws Exception {
        readyWithWorkspace();
        // 真实录下的一轮（codex 0.166，五步：命令、apply_patch、删除、ls）
        List<Map<String, Object>> msgs = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/replay_approvals.jsonl"), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) if (!l.trim().isEmpty()) msgs.add(Json.parseObject(l));
        }
        String tid = null;
        for (Map<String, Object> m : msgs) if ("thread/started".equals(m.get("method"))) tid = Json.str(Json.obj(Json.obj(m, "params"), "thread"), "id");
        Conversation conv = new Conversation(tid);
        for (Map<String, Object> m : msgs) {
            if (m.containsKey("id")) conv.onServerRequest(m.get("id"), Json.str(m, "method"), Json.obj(m, "params"));
            else conv.apply(Json.str(m, "method"), Json.obj(m, "params"));
        }
        conv.threadName = "清理测试文件";
        // 再开一轮：有计划、一条正在跑的命令、一个等待确认的审批
        conv.apply("turn/started", Json.parseObject("{\"threadId\":\"" + tid + "\",\"turn\":{\"id\":\"r2\",\"status\":\"inProgress\",\"items\":[]}}"));
        conv.apply("turn/plan/updated", Json.parseObject("{\"threadId\":\"" + tid + "\",\"turnId\":\"r2\",\"plan\":[{\"step\":\"读取 trip.md\",\"status\":\"completed\"},{\"step\":\"按日期排序\",\"status\":\"inProgress\"},{\"step\":\"加预算列\",\"status\":\"pending\"}]}"));
        conv.apply("item/started", Json.parseObject("{\"threadId\":\"" + tid + "\",\"turnId\":\"r2\",\"item\":{\"type\":\"commandExecution\",\"id\":\"c9\",\"command\":\"/data/x/bash -lc 'sort -t, -k1 budget.csv > budget.sorted.csv'\",\"cwd\":\"/data/ws\",\"status\":\"inProgress\"}}"));
        conv.onServerRequest(99L, "item/commandExecution/requestApproval", Json.parseObject("{\"threadId\":\"" + tid + "\",\"turnId\":\"r2\",\"itemId\":\"c9\",\"command\":\"/data/x/bash -lc 'sort -t, -k1 budget.csv > budget.sorted.csv'\",\"cwd\":\"/data/ws\",\"startedAtMs\":1}"));
        rt.putConversationForTest(conv);
        rt.runningThreadId = tid;
        Intent i = new Intent(RuntimeEnvironment.getApplication(), ChatActivity.class).putExtra(ChatActivity.EXTRA_THREAD, tid)
                .putExtra(ChatActivity.EXTRA_CWD, "/data/data/app/files/ws");
        ActivityController<ChatActivity> c = Robolectric.buildActivity(ChatActivity.class, i).setup();
        settle();
        ChatActivity a = c.get();
        View root = a.getWindow().getDecorView();
        assertTrue(hasText(root, "有 1 项操作等待确认"));
        assertTrue(hasText(root, "计划 · 已完成 1/3"));
        // 审批弹窗自动弹出
        Dialog d = ShadowDialog.getLatestDialog();
        assertNotNull("应该自动弹出审批", d);
        assertTrue(d.isShowing());
        idle();
        shot("21_approval_sheet", d.getWindow().getDecorView());
        assertTrue(hasText(d.getWindow().getDecorView(), "允许运行这条命令？"));
        // 收起弹窗后的对话页
        d.dismiss();
        idle();
        shot("20_chat", a);
        // 没有 app-server：回复写不出去 → 返回 false，审批保留，不能假装已经处理
        assertFalse(rt.answerApproval(conv, conv.pendingApprovals().get(0), "accept"));
        idle();
        assertEquals(1, conv.pendingApprovals().size());
        // 服务端确认（serverRequest/resolved）后才移除，提示条消失
        rt.handleNotificationForTest("serverRequest/resolved", Json.parseObject("{\"threadId\":\"" + tid + "\",\"requestId\":99}"));
        idle();
        assertTrue(conv.pendingApprovals().isEmpty());
        assertFalse(hasText(root, "有 1 项操作等待确认"));
        // 新对话（空）
        Intent i2 = new Intent(RuntimeEnvironment.getApplication(), ChatActivity.class).putExtra(ChatActivity.EXTRA_CWD, rt.prefs.workspace());
        rt.runningThreadId = null;
        ChatActivity empty = Robolectric.buildActivity(ChatActivity.class, i2).setup().get();
        settle();
        assertTrue(hasText(empty.getWindow().getDecorView(), "今天要做点什么？"));
        shot("22_chat_empty", empty);
    }

    /* ---------------- v0.2：ChatGPT 风格的输入卡片、＋菜单、@、模型面板、终端 ---------------- */

    @Test
    public void chatgptStyleLight() throws Exception {
        chatgptStyle();
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-night-xxhdpi")
    public void chatgptStyleDark() throws Exception {
        theme = "dark";
        chatgptStyle();
    }

    /** 和用户截图里账号的模型列表一致（model/list 的形状）。 */
    private void fakeModels() {
        String[][] ms = {{"gpt-6.1-sol", "GPT-6.1-Sol"}, {"gpt-6.1-terra", "GPT-6.1 Terra"}, {"gpt-6-astra", "GPT-6-Astra"}, {"gpt-6-sol", "GPT-6-Sol"},
                {"gpt-6-luna", "GPT-6-Luna"}, {"gpt-5.6-sol", "GPT-5.6-Sol"}, {"gpt-5.6-terra", "GPT-5.6-Terra"}, {"gpt-5.6-luna", "GPT-5.6-Luna"}};
        for (int i = 0; i < ms.length; i++) {
            List<String> eff = new ArrayList<>(java.util.Arrays.asList("low", "medium", "high", "xhigh", "max", "ultra"));
            CodexRuntimeManager.ModelInfo m = new CodexRuntimeManager.ModelInfo(ms[i][0], ms[i][1], "", "medium", eff, i == 0);
            for (String e : eff) m.effortDescriptions.add("");
            if (i < 2) {
                m.serviceTiers.add("priority");
                m.serviceTierNames.add("Fast");
                m.serviceTierDescriptions.add("Faster responses, higher usage");
            }
            rt.models.add(m);
        }
        rt.catalog.add(new Catalog(true, "imagegen", "Image Gen", "Generate or edit images", null, null, "/x/imagegen/SKILL.md"));
        rt.catalog.add(new Catalog(false, "github@openai-curated", "GitHub", "Repos, issues, PRs", null, null, "plugin://github@openai-curated"));
        rt.catalog.add(new Catalog(true, "review-agent", "Review Agent", "Find bugs", null, null, "/x/review-agent/SKILL.md"));
        rt.catalog.add(new Catalog(true, "openai-docs", "OpenAI Docs", "Docs", null, null, "/x/openai-docs/SKILL.md"));
        rt.catalog.add(new Catalog(true, "skill-creator", "Skill Creator", "Create a skill", null, null, "/x/skill-creator/SKILL.md"));
    }

    private static void layout(Activity a) {
        View root = a.getWindow().getDecorView();
        DisplayMetrics dm = root.getResources().getDisplayMetrics();
        root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, dm.widthPixels, dm.heightPixels);
    }

    /** 点文字所在的可点击行（文字本身或往上找第一个能点的父视图）。 */
    private static void tap(View root, String text) {
        View v = findText(root, text);
        assertNotNull("找不到：" + text, v);
        while (v != null && !v.hasOnClickListeners()) v = v.getParent() instanceof View ? (View) v.getParent() : null;
        assertNotNull("不能点：" + text, v);
        v.performClick();
        idle();
    }

    private static Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private void chatgptStyle() throws Exception {
        readyWithWorkspace();
        fakeModels();
        rt.prefs.setWorkMode(true);
        Intent i = new Intent(RuntimeEnvironment.getApplication(), ChatActivity.class).putExtra(ChatActivity.EXTRA_CWD, rt.prefs.workspace());
        ChatActivity a = Robolectric.buildActivity(ChatActivity.class, i).setup().get();
        settle();
        layout(a);
        View root = a.getWindow().getDecorView();
        // 新对话：顶部“聊天 / 工作”，输入卡片里“6.1 Sol 中”
        assertTrue(hasText(root, "聊天"));
        assertTrue(hasText(root, "工作"));
        // 收起时是一行（＋ 输入框 麦克风 语音），模型标签藏起来；点输入框展开
        assertFalse(hasText(root, "6.1 Sol 中"));
        assertTrue(hasText(root, "语音"));
        assertTrue(hasText(root, "今天要做点什么？"));
        shot("23_chat_new_work", a);
        android.widget.EditText in0 = (android.widget.EditText) field(a, "input");
        in0.requestFocus();
        idle();
        layout(a);
        assertTrue(hasText(root, "6.1 Sol 中"));
        shot("23b_chat_focused", a);
        ((View) field(a, "frame")).requestFocus();
        idle();
        // 切到聊天
        tap(root, "聊天");
        assertFalse(rt.prefs.workMode());
        assertTrue(hasText(root, "有什么可以帮忙的？"));
        layout(a);
        shot("24_chat_new_chat", a);
        // 聊天模式点发送：不走 Codex，二选一（网页版 / 官方 App）；网页版打开官方 chatgpt.com 并带上这句话
        android.widget.EditText inChat = (android.widget.EditText) field(a, "input");
        inChat.setText("你好");
        idle();
        java.lang.reflect.Method send = ChatActivity.class.getDeclaredMethod("onSend");
        send.setAccessible(true);
        send.invoke(a);
        idle();
        layout(a);
        assertTrue(hasText(root, "ChatGPT 网页版"));
        assertTrue(hasText(root, "ChatGPT App"));
        shot("24b_chat_send_choice", a);
        tap(root, "ChatGPT 网页版");
        Intent web = org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
        assertEquals(ChatGptWebActivity.class.getName(), web.getComponent().getClassName());
        assertEquals("https://chatgpt.com/?q=%E4%BD%A0%E5%A5%BD", web.getStringExtra(ChatGptWebActivity.EXTRA_URL));
        assertEquals("", inChat.getText().toString());
        assertNull("聊天模式不应该创建 Codex 会话", field(a, "conv"));
        tap(root, "工作");
        assertTrue(rt.prefs.workMode());
        layout(a);
        // “＋”菜单
        android.widget.ImageView plusBtn = (android.widget.ImageView) field(a, "plusBtn");
        plusBtn.performClick();
        idle();
        assertTrue(hasText(root, "拍照"));
        assertTrue(hasText(root, "照片"));
        assertTrue(hasText(root, "文件"));
        assertTrue(hasText(root, "插件"));
        shot("25_plus_menu", a);
        a.getOnBackPressedDispatcher().onBackPressed(); // 返回键关掉菜单
        idle();
        assertFalse(hasText(root, "拍照"));
        // 输入“@”：上方列出技能和插件
        android.widget.EditText input = (android.widget.EditText) field(a, "input");
        input.requestFocus();
        input.setSelection(0);
        input.getText().insert(0, "@");
        idle();
        assertTrue(hasText(root, "插件与技能"));
        assertTrue(hasText(root, "创建图像"));
        assertTrue(hasText(root, "GitHub"));
        shot("26_mention", a);
        // 选“创建图像” → 变成输入框上方的标签，“@”被去掉
        tap(root, "创建图像");
        assertEquals("", input.getText().toString());
        assertFalse(hasText(root, "插件与技能"));
        // 再加一张图片附件、写点字
        File img = new File(rt.paths.attachments, "cat.png");
        img.getParentFile().mkdirs();
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(120, 90, android.graphics.Bitmap.Config.ARGB_8888);
        new Canvas(bm).drawColor(0xFF8EC5FC);
        try (FileOutputStream o = new FileOutputStream(img)) {
            bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
        }
        a.addAttachment(new Attachment(Attachment.Kind.IMAGE, "cat.png", null, img.getAbsolutePath()));
        a.addAttachment(new Attachment(Attachment.Kind.WEB_SEARCH, Attachment.WEB_NAME, "web_search", null));
        input.setText("画一只橘猫，放在这张图的风格里");
        idle();
        layout(a);
        assertTrue(hasText(root, Attachment.WEB_NAME));
        assertTrue(hasText(root, "创建图像"));
        assertEquals("发送", ((View) field(a, "sendBtn")).getContentDescription());
        shot("27_composer_attachments", a);
        // 模型面板
        tap(root, "6.1 Sol 中");
        assertTrue(hasText(root, "模型"));
        assertTrue(hasText(root, "推理"));
        assertTrue(hasText(root, "速度"));
        assertTrue(hasText(root, "权限"));
        assertTrue(hasText(root, "完成"));
        layout(a);
        shot("28_model_sheet", a);
        tap(root, "模型");
        assertTrue(hasText(root, "推荐的前沿模型组合"));
        assertTrue(hasText(root, "5.6 Luna"));
        shot("29_model_dropdown", a);
        tap(root, "6.1 Terra");
        assertEquals("gpt-6.1-terra", rt.prefs.model());
        layout(a);
        tap(root, "推理");
        assertTrue(hasText(root, "全力思考，用量很大"));
        shot("30_effort_dropdown", a);
        tap(root, "高");
        assertEquals("high", rt.prefs.effort());
        layout(a);
        tap(root, "速度");
        assertTrue(hasText(root, "用量更高"));
        shot("31_speed_dropdown", a);
        tap(root, "快速");
        assertEquals("priority", rt.prefs.speed());
        tap(root, "完成");
        assertFalse(hasText(root, "推荐的前沿模型组合"));
        assertTrue(hasText(root, "6.1 Terra 高"));
        // 换成不支持快速档的模型：速度自动回到标准
        layout(a);
        tap(root, "6.1 Terra 高");
        tap(root, "模型");
        tap(root, "6 Luna");
        assertEquals(null, rt.prefs.speed());
        tap(root, "完成");

        // 对话里：用户消息带图片和提及；Agent 的回答；顶部是标题
        String tid = "th-style";
        Conversation conv = new Conversation(tid);
        conv.addLocalUserMessage("u1", "画一只橘猫，放在这张图的风格里", java.util.Collections.singletonList(img.getAbsolutePath()), java.util.Collections.singletonList("创建图像"));
        conv.apply("turn/started", Json.parseObject("{\"threadId\":\"" + tid + "\",\"turn\":{\"id\":\"t1\",\"status\":\"inProgress\",\"items\":[]}}"));
        conv.apply("item/completed", Json.parseObject("{\"threadId\":\"" + tid + "\",\"turnId\":\"t1\",\"item\":{\"type\":\"agentMessage\",\"id\":\"a1\",\"phase\":\"final_answer\","
                + "\"text\":\"好的，已经按参考图的**柔和蓝色调**生成了一只橘猫，保存为 `orange-cat.png`。\\n\\n- 尺寸：1024×1024\\n- 风格：扁平插画\"}}"));
        conv.apply("turn/completed", Json.parseObject("{\"threadId\":\"" + tid + "\",\"turn\":{\"id\":\"t1\",\"status\":\"completed\",\"items\":[]}}"));
        conv.threadName = "画一只橘猫";
        rt.putConversationForTest(conv);
        Intent i3 = new Intent(RuntimeEnvironment.getApplication(), ChatActivity.class).putExtra(ChatActivity.EXTRA_THREAD, tid).putExtra(ChatActivity.EXTRA_CWD, rt.prefs.workspace());
        ChatActivity c3 = Robolectric.buildActivity(ChatActivity.class, i3).setup().get();
        settle();
        layout(c3);
        idle();
        layout(c3);
        View r3 = c3.getWindow().getDecorView();
        assertTrue(hasText(r3, "画一只橘猫"));
        assertTrue(hasText(r3, "@创建图像"));
        assertFalse("对话里不显示“聊天 / 工作”切换", findText(r3, "工作").isShown());
        layout(c3);
        shot("32_chat_conversation", c3);
        // “⋯”菜单
        java.util.ArrayList<View> found = new java.util.ArrayList<>();
        r3.findViewsWithText(found, "更多", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        assertFalse(found.isEmpty());
        found.get(0).performClick();
        idle();
        assertTrue(hasText(r3, "终端"));
        assertTrue(hasText(r3, "重命名"));
        shot("33_more_menu", c3);
        // 终端（从菜单进）
        tap(r3, "终端");
        Intent ti = shadowOf(c3).getNextStartedActivity();
        assertNotNull(ti);
        assertEquals(TerminalActivity.class.getName(), ti.getComponent().getClassName());
        rt.toolkitState = CodexRuntimeManager.ToolkitState.NONE;
        TerminalActivity term = Robolectric.buildActivity(TerminalActivity.class, ti).setup().get();
        settle();
        View tr = term.getWindow().getDecorView();
        assertTrue(hasText(tr, "不消耗模型额度"));
        assertTrue(hasText(tr, "git status"));
        layout(term);
        shot("17_terminal", term);
        tap(tr, "常用命令");
        assertTrue(hasText(tr, "npm install <包名>"));
        assertTrue(hasText(tr, "git clone <仓库网址>"));
        shot("18_commands", term);
        tap(tr, "git clone <仓库网址>");
        android.widget.EditText tin = (android.widget.EditText) field(term, "input");
        assertEquals("git clone <仓库网址>", tin.getText().toString());
        assertEquals("<仓库网址>", tin.getText().subSequence(tin.getSelectionStart(), tin.getSelectionEnd()).toString());
    }

    /** 文案集中一处，测试也用。 */
    static final class DeviceLoginMessages {
        static final String NETWORK = "连不上 OpenAI 的登录服务。请检查网络（需要能访问 auth.openai.com），稍后自动重试。";
    }
}
