package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** 用一个假的 codex（shell 脚本，按真实格式打印）测登录流程：自动刷新、成功、失败重试。 */
public class DeviceLoginTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** 每次运行打印一个新代码（CODE-1、CODE-2……用计数文件），然后按 mode 行事。 */
    private File fakeCodex(String mode) throws Exception {
        File dir = tmp.newFolder();
        File script = new File(dir, "codex");
        String s = "#!/bin/sh\n"
                + "n=$(cat \"$COUNTER\" 2>/dev/null || echo 0); n=$((n+1)); echo $n > \"$COUNTER\"\n"
                + "printf '\\033[90mWelcome\\033[0m\\n1. Open this link\\n   \\033[94mhttps://auth.openai.com/codex/device\\033[0m\\n"
                + "2. Enter this one-time code \\033[90m(expires in 15 minutes)\\033[0m\\n   \\033[94mAB%02d-Z9Q7X\\033[0m\\n' $n\n"
                + "case \"" + mode + "\" in\n"
                + "  hang) sleep 60 ;;\n"
                + "  success) sleep 0.5; echo '{\"x\":1}' > \"$AUTH\"; echo 'Successfully logged in'; exit 0 ;;\n"
                + "  network) echo 'Error: error sending request for url (https://auth.openai.com/api/accounts/deviceauth/usercode)'; exit 1 ;;\n"
                + "  expire) sleep 0.3; echo 'Error: device auth timed out after 15 minutes'; exit 1 ;;\n"
                + "esac\n";
        try (FileOutputStream o = new FileOutputStream(script)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(script.setExecutable(true));
        return script;
    }

    private DeviceLogin login(File script, List<DeviceLogin.State> states, File auth, long lifetimeMs) throws Exception {
        File counter = new File(tmp.getRoot(), "counter");
        Map<String, String> env = new HashMap<>();
        env.put("PATH", "/usr/bin:/bin");
        env.put("COUNTER", counter.getAbsolutePath());
        env.put("AUTH", auth.getAbsolutePath());
        List<String> cmd = new ArrayList<>(Arrays.asList(script.getAbsolutePath(), "login", "--device-auth"));
        return new DeviceLogin(cmd, env, new File(tmp.getRoot(), "login.log"), auth, states::add, 200, lifetimeMs);
    }

    private static DeviceLogin.State waitFor(List<DeviceLogin.State> states, DeviceLogin.Phase phase, int count, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            int n = 0;
            DeviceLogin.State last = null;
            for (DeviceLogin.State s : states) {
                if (s.phase == phase) {
                    n++;
                    last = s;
                }
            }
            if (n >= count) return last;
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError("没等到第 " + count + " 个 " + phase + "，实际：" + phases(states));
    }

    private static String phases(List<DeviceLogin.State> states) {
        StringBuilder b = new StringBuilder();
        for (DeviceLogin.State s : states) b.append(s.phase).append(s.code == null ? "" : "(" + s.code + ")").append(' ');
        return b.toString();
    }

    /** 代码到期（这里把有效期压到 1.2 秒）就自动换新代码，不用用户操作。 */
    @Test
    public void refreshesExpiredCodeAutomatically() throws Exception {
        List<DeviceLogin.State> states = new CopyOnWriteArrayList<>();
        DeviceLogin l = login(fakeCodex("hang"), states, new File(tmp.getRoot(), "auth.json"), 1200);
        l.start();
        DeviceLogin.State first = waitFor(states, DeviceLogin.Phase.WAITING, 1, 10_000);
        assertEquals("https://auth.openai.com/codex/device", first.url);
        assertEquals("AB01-Z9Q7X", first.code);
        assertTrue(first.expiresAtMs > System.nanoTime() / 1_000_000L - 2000);
        DeviceLogin.State second = waitFor(states, DeviceLogin.Phase.WAITING, 2, 10_000);
        assertEquals("AB02-Z9Q7X", second.code);
        assertEquals(2, second.codeNumber);
        assertTrue(phases(states).contains("REFRESHING"));
        l.stop();
        waitFor(states, DeviceLogin.Phase.STOPPED, 1, 5000);
    }

    /** 用户手动“换一个代码”。 */
    @Test
    public void manualRefresh() throws Exception {
        List<DeviceLogin.State> states = new CopyOnWriteArrayList<>();
        DeviceLogin l = login(fakeCodex("hang"), states, new File(tmp.getRoot(), "auth.json"), 600_000);
        l.start();
        DeviceLogin.State a = waitFor(states, DeviceLogin.Phase.WAITING, 1, 10_000);
        l.refreshNow();
        DeviceLogin.State b = waitFor(states, DeviceLogin.Phase.WAITING, 2, 10_000);
        assertNotEquals(a.code, b.code);
        l.stop();
    }

    /** 进程因过期退出（codex 自己的 15 分钟到了；这里有效期压到 0.4 秒）：同样自动换新的。 */
    @Test
    public void processExpiryRestarts() throws Exception {
        List<DeviceLogin.State> states = new CopyOnWriteArrayList<>();
        DeviceLogin l = login(fakeCodex("expire"), states, new File(tmp.getRoot(), "auth.json"), 400);
        l.start();
        waitFor(states, DeviceLogin.Phase.WAITING, 3, 15_000);
        l.stop();
    }

    /** 代码刚出来进程就说“过期”退出（异常情况）：不能死循环，要退避重试。 */
    @Test
    public void earlyExpiryBacksOff() throws Exception {
        List<DeviceLogin.State> states = new CopyOnWriteArrayList<>();
        DeviceLogin l = login(fakeCodex("expire"), states, new File(tmp.getRoot(), "auth.json"), 600_000);
        l.start();
        DeviceLogin.State r = waitFor(states, DeviceLogin.Phase.RETRYING, 1, 10_000);
        assertTrue(r.retryAtMs - System.nanoTime() / 1_000_000L > 500);
        Thread.sleep(800);
        int waiting = 0;
        for (DeviceLogin.State s : states) if (s.phase == DeviceLogin.Phase.WAITING) waiting++;
        assertTrue("没有疯狂重启：" + waiting, waiting <= 2);
        l.stop();
    }

    @Test
    public void successEndsTheFlow() throws Exception {
        List<DeviceLogin.State> states = new CopyOnWriteArrayList<>();
        File auth = new File(tmp.getRoot(), "auth.json");
        DeviceLogin l = login(fakeCodex("success"), states, auth, 600_000);
        l.start();
        waitFor(states, DeviceLogin.Phase.SUCCESS, 1, 10_000);
        assertTrue(auth.isFile());
        // 输出确实写进了文件（按用户要求重定向）
        String log = new String(Files.readAllBytes(new File(tmp.getRoot(), "login.log").toPath()), StandardCharsets.UTF_8);
        assertTrue(log.contains("AB01-Z9Q7X"));
    }

    /** 网络错误：显示原因并自动重试（退避），不会卡死。 */
    @Test
    public void networkErrorRetries() throws Exception {
        List<DeviceLogin.State> states = new CopyOnWriteArrayList<>();
        DeviceLogin l = login(fakeCodex("network"), states, new File(tmp.getRoot(), "auth.json"), 600_000);
        l.start();
        DeviceLogin.State r = waitFor(states, DeviceLogin.Phase.RETRYING, 1, 10_000);
        assertEquals(DeviceAuth.Outcome.NETWORK, r.outcome);
        assertTrue(r.message.contains("网络"));
        assertTrue(r.retryAtMs > System.nanoTime() / 1_000_000L - 1000);
        l.stop();
        waitFor(states, DeviceLogin.Phase.STOPPED, 1, 5000);
    }
}
