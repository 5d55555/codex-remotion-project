package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 真实 codex（x64 版，和安卓上的 arm64 版同一版本）经过 {@link LocalProxy}（带认证）跑通：
 * 设备码登录拿到代码、app-server 初始化、账号、开会话、让 Agent 写文件和执行命令、审批请求。
 * <p>
 * 需要 CODEX_BIN（codex 程序，同目录要有 codex-code-mode-host）和 CODEX_AUTH（已登录的 auth.json）；没有就跳过。
 * 本机的上游代理从 JVM 的 https.proxyHost 读（{@link LocalProxy#SYSTEM}），CA 从 SSL_CERT_FILE 读。
 */
public class CodexIntegrationTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static String env(String k) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? null : v;
    }

    private CodexRuntime.Layout layout(File root) throws Exception {
        String bin = env("CODEX_BIN");
        assumeTrue("没有 CODEX_BIN，跳过真实 codex 测试", bin != null && new File(bin).canExecute());
        File ca = env("SSL_CERT_FILE") != null ? new File(env("SSL_CERT_FILE")) : null;
        return new CodexRuntime.Layout(new File(bin), new File(root, "codex-home"), new File(root, "home"), new File(root, "bin"), new File(root, "tmp"),
                ca, new File(root, "logs"), "/bin/sh");
    }

    /** 设备码登录：经代理拿到网址和代码（不真的去授权，拿到就停）。 */
    @Test
    public void deviceAuthThroughProxy() throws Exception {
        File root = tmp.newFolder();
        CodexRuntime.Layout l = layout(root);
        CodexRuntime.prepare(l, new CodexRuntime.Config());
        try (LocalProxy proxy = new LocalProxy(LocalProxy.SYSTEM)) {
            proxy.start();
            Map<String, String> e = CodexRuntime.env(l, proxy.url(), "/usr/bin:/bin", "UTC");
            final List<DeviceLogin.State> states = new CopyOnWriteArrayList<>();
            final CountDownLatch got = new CountDownLatch(1);
            DeviceLogin login = new DeviceLogin(CodexRuntime.command(l, "login", "--device-auth"), e, new File(l.logDir, "login.log"),
                    new File(l.codexHome, "auth.json"), s -> {
                        states.add(s);
                        if (s.phase == DeviceLogin.Phase.WAITING) got.countDown();
                    });
            login.start();
            boolean ok = got.await(60, TimeUnit.SECONDS);
            StringBuilder seen = new StringBuilder();
            for (DeviceLogin.State st : states) seen.append(st.phase).append(st.detail == null ? "" : "(" + st.detail + ")").append(' ');
            assertTrue("60 秒内应该拿到设备码，状态：" + seen, ok);
            DeviceLogin.State s = login.lastState();
            assertEquals("https://auth.openai.com/codex/device", s.url);
            assertTrue(s.code, s.code.matches("[A-Z0-9]{4,5}-[A-Z0-9]{4,6}"));
            login.stop();
            assertTrue("流量确实走了本地代理", proxy.bytesDown.get() > 0);
        }
    }

    /** app-server：初始化 → 账号 → 开会话（每步确认）→ Agent 写文件、执行命令 → 我们批准 → 文件真的写了。 */
    @Test
    public void appServerTurnWithApprovals() throws Exception {
        String auth = env("CODEX_AUTH");
        File root = tmp.newFolder();
        CodexRuntime.Layout l = layout(root);
        assumeTrue("没有 CODEX_AUTH，跳过", auth != null && new File(auth).isFile());
        CodexRuntime.prepare(l, new CodexRuntime.Config());
        Files.copy(new File(auth).toPath(), new File(l.codexHome, "auth.json").toPath());
        File ws = tmp.newFolder("workspace");
        try (LocalProxy proxy = new LocalProxy(LocalProxy.SYSTEM)) {
            proxy.start();
            Map<String, String> e = CodexRuntime.env(l, proxy.url(), "/usr/bin:/bin", "UTC");
            final List<String> requests = new CopyOnWriteArrayList<>();
            final List<String> notes = new CopyOnWriteArrayList<>();
            final CountDownLatch turnDone = new CountDownLatch(1);
            final AppServer[] holder = new AppServer[1];
            AppServer s = AppServer.start(CodexRuntime.command(l, "app-server"), e, ws, new AppServer.Listener() {
                @Override
                public void onNotification(String method, Map<String, Object> params) {
                    notes.add(method);
                    if (method.equals("turn/completed")) turnDone.countDown();
                }

                @Override
                public void onServerRequest(Object id, String method, Map<String, Object> params) {
                    requests.add(method);
                    holder[0].respond(id, Json.map("decision", "accept"));
                }

                @Override
                public void onExit(int code, String stderrTail) {
                    turnDone.countDown();
                }
            });
            holder[0] = s;
            try {
                long t0 = System.currentTimeMillis();
                Map<String, Object> init = s.call("initialize", Json.map("clientInfo", Json.map("name", "pocket-codex-test", "version", "0.1")), 30_000);
                assertEquals("0.166.0", Json.str(init, "userAgent", "").replaceAll(".*?/([0-9.]+) .*", "$1"));
                s.notify("initialized", null);
                Map<String, Object> acct = s.call("account/read", Json.map(), 30_000);
                assertNotNull(Json.obj(acct, "account").get("type"));
                Map<String, Object> th = s.call("thread/start", Json.map("cwd", ws.getAbsolutePath(), "sandbox", "danger-full-access",
                        "approvalPolicy", "untrusted", "approvalsReviewer", "user"), 30_000);
                String tid = Json.str(Json.obj(th, "thread"), "id");
                assertNotNull(tid);
                List<Object> input = new ArrayList<>();
                input.add(Json.map("type", "text", "text", "Run exactly one shell command: printf hello > note.txt — then reply with the single word: done"));
                s.call("turn/start", Json.map("threadId", tid, "input", input, "effort", "low"), 30_000);
                assertTrue("回合应在 4 分钟内结束；stderr：" + s.stderrTail(), turnDone.await(240, TimeUnit.SECONDS));
                System.out.println("initialize+turn 用时 " + (System.currentTimeMillis() - t0) + " ms；通知 " + notes.size() + " 条；审批 " + requests);
                assertTrue("每步确认模式下应该收到命令审批：" + requests, requests.contains("item/commandExecution/requestApproval"));
                assertEquals("hello", new String(Files.readAllBytes(new File(ws, "note.txt").toPath()), StandardCharsets.UTF_8));
                assertTrue(notes.contains("item/agentMessage/delta") || notes.contains("item/completed"));
                assertTrue("流量确实走了本地代理", proxy.bytesDown.get() > 0);
            } finally {
                s.close();
            }
        }
    }
}
