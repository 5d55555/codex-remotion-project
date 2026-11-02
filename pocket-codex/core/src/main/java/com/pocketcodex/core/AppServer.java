package com.pocketcodex.core;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * `codex app-server` 的客户端：stdin/stdout 上一行一个 JSON-RPC 消息（不带 "jsonrpc" 字段）。
 * 读线程收到的回复、通知、服务端请求都在读线程上回调；界面层自己切到主线程。
 */
public final class AppServer implements Closeable {

    public interface Listener {
        /** 服务端通知（turn/started、item/agentMessage/delta……）。 */
        void onNotification(String method, Map<String, Object> params);

        /** 服务端请求（审批等），必须用 {@link #respond} 或 {@link #respondError} 回复。 */
        void onServerRequest(Object id, String method, Map<String, Object> params);

        /** 进程退出（正常关闭也会调）。stderrTail 是最后几十行日志。 */
        void onExit(int code, String stderrTail);
    }

    public interface Callback {
        void onResult(Map<String, Object> result);

        void onError(RpcError error);
    }

    /** JSON-RPC 错误，或者进程没了。 */
    public static final class RpcError extends Exception {
        public final long code;
        /** 服务端 error.data（可能为 null），用于分类和诊断。 */
        public final Object data;

        public RpcError(long code, String message) {
            this(code, message, null);
        }

        public RpcError(long code, String message, Object data) {
            super(message);
            this.code = code;
            this.data = data;
        }
    }

    /** 单条协议消息的上限（超出说明出了问题，按协议错误处理，不无限占内存）。 */
    public static final int MAX_LINE = 32 << 20;

    public static final long ERR_PROCESS_GONE = -1, ERR_TIMEOUT = -2;

    private final Process process;
    private final OutputStream stdin;
    private final Listener listener;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, Callback> pending = new ConcurrentHashMap<>();
    private final ArrayDeque<String> stderrTail = new ArrayDeque<>();
    private volatile boolean closed;
    private volatile int exitCode = Integer.MIN_VALUE;
    /** 写 stdin 放在独立的串行线程：调用方（安卓主线程）只入队，codex 不读 stdin 时也不会卡住界面。 */
    private final java.util.concurrent.LinkedBlockingQueue<byte[]> outQ = new java.util.concurrent.LinkedBlockingQueue<>();
    private static final byte[] EOF_MARK = new byte[0];
    private volatile boolean writeFailed;

    private AppServer(Process process, Listener listener) {
        this.process = process;
        this.stdin = process.getOutputStream();
        this.listener = listener;
    }

    /** 启动 `codex app-server`。env 会完整替换子进程的环境变量（安卓上不继承 App 进程的那些）。 */
    public static AppServer start(List<String> command, Map<String, String> env, File cwd, Listener listener) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().clear();
        pb.environment().putAll(env);
        if (cwd != null) pb.directory(cwd);
        Process p = pb.start();
        final AppServer s = new AppServer(p, listener);
        Thread out = new Thread(s::readLoop, "app-server-out");
        out.setDaemon(true);
        out.start();
        Thread err = new Thread(s::errLoop, "app-server-err");
        err.setDaemon(true);
        err.start();
        Thread w = new Thread(s::writeLoop, "app-server-in");
        w.setDaemon(true);
        w.start();
        return s;
    }

    public boolean alive() {
        return !closed && !writeFailed && exitCode == Integer.MIN_VALUE;
    }

    private void writeLoop() {
        try {
            while (true) {
                byte[] line = outQ.take();
                if (line == EOF_MARK) break;
                stdin.write(line);
                if (outQ.isEmpty()) stdin.flush();
            }
        } catch (InterruptedException | IOException e) {
            writeFailed = true;
        } finally {
            try {
                stdin.close();
            } catch (IOException ignored) {
                // 已关
            }
        }
    }

    /* ---------------- 发送 ---------------- */

    public long request(String method, Object params, Callback cb) {
        long id = nextId.getAndIncrement();
        if (cb != null) pending.put(id, cb);
        Map<String, Object> m = Json.map("id", id, "method", method);
        if (params != null) m.put("params", params);
        if (!send(m)) {
            Callback c = pending.remove(id);
            if (c != null) c.onError(new RpcError(ERR_PROCESS_GONE, "codex 进程已经退出"));
        }
        return id;
    }

    /** 同步请求（不要在主线程上调）。 */
    public Map<String, Object> call(String method, Object params, long timeoutMs) throws RpcError {
        final Object[] box = new Object[1];
        final CountDownLatch done = new CountDownLatch(1);
        long id = request(method, params, new Callback() {
            @Override
            public void onResult(Map<String, Object> result) {
                box[0] = result;
                done.countDown();
            }

            @Override
            public void onError(RpcError error) {
                box[0] = error;
                done.countDown();
            }
        });
        try {
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                pending.remove(id);
                throw new RpcError(ERR_TIMEOUT, method + " 超时");
            }
        } catch (InterruptedException e) {
            pending.remove(id);
            Thread.currentThread().interrupt();
            throw new RpcError(ERR_TIMEOUT, "被中断");
        }
        if (box[0] instanceof RpcError) throw (RpcError) box[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) box[0];
        return r;
    }

    public void notify(String method, Object params) {
        Map<String, Object> m = Json.map("method", method);
        if (params != null) m.put("params", params);
        send(m);
    }

    /** 回复服务端请求；返回是否写进了 stdin（false：进程已经没了）。 */
    public boolean respond(Object id, Object result) {
        return send(Json.map("id", id, "result", result));
    }

    public boolean respondError(Object id, long code, String message) {
        return send(Json.map("id", id, "error", Json.map("code", code, "message", message)));
    }

    /** 入队发送；返回 false 表示进程已经没了（不会阻塞）。 */
    private boolean send(Map<String, Object> m) {
        if (!alive()) return false;
        return outQ.offer((Json.write(m) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /* ---------------- 接收 ---------------- */

    /** 读一行（\n 结尾），超过 MAX_LINE 抛异常。返回 null 表示流结束。 */
    static String readLine(java.io.InputStream in, java.io.ByteArrayOutputStream buf) throws IOException {
        buf.reset();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') return buf.toString("UTF-8");
            buf.write(c);
            if (buf.size() > MAX_LINE) throw new IOException("协议消息超过 " + (MAX_LINE >> 20) + " MB");
        }
        return buf.size() > 0 ? buf.toString("UTF-8") : null;
    }

    @SuppressWarnings("unchecked")
    private void readLoop() {
        try (java.io.InputStream in = new java.io.BufferedInputStream(process.getInputStream(), 64 * 1024)) {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(8192);
            String line;
            while ((line = readLine(in, buf)) != null) {
                if (line.isEmpty()) continue;
                Map<String, Object> m;
                try {
                    m = Json.parseObject(line);
                } catch (IllegalArgumentException e) {
                    addErr("非 JSON 输出：" + (line.length() > 200 ? line.substring(0, 200) : line));
                    continue;
                }
                Object id = m.get("id");
                String method = Json.str(m, "method");
                try {
                    if (method == null && id != null) { // 回复
                        Callback cb = pending.remove(id instanceof Number ? ((Number) id).longValue() : -999L);
                        if (cb == null) continue;
                        if (m.containsKey("error")) {
                            Map<String, Object> e = Json.obj(m, "error");
                            cb.onError(new RpcError(Json.num(e, "code", 0), Json.str(e, "message", "未知错误"), e.get("data")));
                        } else {
                            Object res = m.get("result");
                            cb.onResult(res instanceof Map ? (Map<String, Object>) res : Json.map("value", res));
                        }
                    } else if (method != null && id != null) {
                        listener.onServerRequest(id, method, Json.obj(m, "params"));
                    } else if (method != null) {
                        listener.onNotification(method, Json.obj(m, "params"));
                    }
                } catch (RuntimeException e) { // 回调里的错不能弄死读线程
                    addErr("处理 " + method + " 出错：" + e);
                }
            }
        } catch (IOException e) {
            addErr("读取 codex 输出出错：" + e.getMessage());
        }
        // stdout 关了：进程多半在退出；等一会儿，还活着就杀掉（不能卡在这里）
        int code;
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            }
            code = process.waitFor(2, TimeUnit.SECONDS) ? process.exitValue() : -1;
        } catch (InterruptedException e) {
            code = -1;
        }
        exitCode = code;
        for (Long pid : pending.keySet()) {
            Callback cb = pending.remove(pid);
            if (cb == null) continue;
            try {
                cb.onError(new RpcError(ERR_PROCESS_GONE, "codex 进程已经退出（" + code + "）"));
            } catch (RuntimeException ignored) {
                // 一个回调出错不影响其他的清理
            }
        }
        try {
            listener.onExit(code, stderrTail());
        } catch (RuntimeException ignored) {
            // 同上
        }
    }

    private void errLoop() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) addErr(DeviceAuth.stripAnsi(line));
        } catch (IOException ignored) {
            // 进程关了
        }
    }

    private void addErr(String line) {
        synchronized (stderrTail) {
            stderrTail.addLast(line.length() > 500 ? line.substring(0, 500) : line);
            while (stderrTail.size() > 80) stderrTail.removeFirst();
        }
    }

    public String stderrTail() {
        synchronized (stderrTail) {
            StringBuilder b = new StringBuilder();
            for (String l : stderrTail) b.append(l).append('\n');
            return b.toString();
        }
    }

    /** 关掉：先关 stdin 让它自己退出，1.5 秒不退就杀。 */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        outQ.offer(EOF_MARK); // 写线程把排队的消息写完后关 stdin，codex 读到 EOF 自己退出
        Thread killer = new Thread(() -> {
            try {
                if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                    process.destroy();
                    if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                process.destroyForcibly();
            }
        }, "app-server-close");
        killer.setDaemon(true);
        killer.start();
    }
}
