package com.pocketcodex.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        // 创建并进入 → 主界面
        findText(root, "创建并进入").performClick();
        Intent main = shadowOf(a).getNextStartedActivity();
        assertNotNull(main);
        assertEquals(MainActivity.class.getName(), main.getComponent().getClassName());
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
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        assertFalse("主界面不应跳回设置", a.isFinishing());
        assertTrue(hasText(root, "整理旅行清单"));
        assertTrue(hasText(root, "新对话"));
        shot("10_sessions", a);
        // 文件页
        com.google.android.material.bottomnavigation.BottomNavigationView nav = findNav(root);
        nav.setSelectedItemId(MainActivity.TAB_FILES);
        idle();
        assertTrue(hasText(root, "trip.md"));
        shot("11_files", a);
        // 设置页
        nav.setSelectedItemId(MainActivity.TAB_SETTINGS);
        idle();
        assertTrue(hasText(root, "每步确认"));
        shot("12_settings", a);
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
        assertTrue(hasText(empty.getWindow().getDecorView(), "想先改点什么？"));
        shot("22_chat_empty", empty);
    }

    /** 文案集中一处，测试也用。 */
    static final class DeviceLoginMessages {
        static final String NETWORK = "连不上 OpenAI 的登录服务。请检查网络（需要能访问 auth.openai.com），稍后自动重试。";
    }
}
