package com.pocketcodex.core;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 只监听 127.0.0.1 的 HTTP 代理（CONNECT 隧道为主，也转发普通 http 请求）。
 * <p>
 * 为什么要它：官方 codex 是 musl 静态程序，安卓上没有 /etc/resolv.conf，它自己解析不了域名。让 codex 走
 * HTTPS_PROXY=http://token@127.0.0.1:端口，codex 只需要连数字 IP；真正的域名解析和连接由这里（安卓的 Java 网络栈）做，
 * 还能顺带遵守系统的 Wi-Fi 代理设置（{@link Upstream}）。TLS 仍是 codex 和服务器端到端的，这里只搬字节。
 * <p>
 * 本机的其他 App 也能连 127.0.0.1，所以要求 Proxy-Authorization（随机令牌），不对就 407。
 */
public final class LocalProxy implements Closeable {

    /** 选上游：返回 Proxy.NO_PROXY 直连，或一个 HTTP 代理。安卓上用 ProxySelector.getDefault()。 */
    public interface Upstream {
        Proxy select(String host, int port);
    }

    public static final Upstream DIRECT = new Upstream() {
        @Override
        public Proxy select(String host, int port) {
            return Proxy.NO_PROXY;
        }
    };

    /** 用 java.net.ProxySelector 的默认实现选上游（安卓上它读系统的 Wi-Fi/全局代理设置）。 */
    public static final Upstream SYSTEM = new Upstream() {
        @Override
        public Proxy select(String host, int port) {
            try {
                java.net.ProxySelector ps = java.net.ProxySelector.getDefault();
                if (ps == null) return Proxy.NO_PROXY;
                List<Proxy> l = ps.select(new URI("https://" + host + ":" + port + "/"));
                for (Proxy p : l) if (p.type() == Proxy.Type.HTTP) return p;
            } catch (Exception ignored) {
                // 选不出来就直连
            }
            return Proxy.NO_PROXY;
        }
    };

    /**
     * 本机命令通道：经代理请求 http://pocket-codex.invalid/… 不转发，交给这里（例如 pkg 命令让 App 装软件包）。
     * 同样要代理口令，所以只有拿到环境变量的 Agent 进程能用。
     */
    public interface LocalHandler {
        /** path 形如 /install；args 是查询串里的 a=…（按顺序）。往 out 写纯文本，返回退出码。 */
        int handle(String path, List<String> args, java.io.PrintStream out) throws Exception;
    }

    public static final String LOCAL_HOST = "pocket-codex.invalid";
    private volatile LocalHandler localHandler;

    public void setLocalHandler(LocalHandler h) {
        localHandler = h;
    }

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int HEAD_LIMIT = 16 * 1024;
    /** 同时最多几条隧道（每条两个线程）；codex 平时只用几条。 */
    private static final int MAX_TUNNELS = 32;
    private final java.util.Set<Socket> live = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Socket, Boolean>());

    private final Upstream upstream;
    private final String user = "codex";
    private final String token;
    private ServerSocket server;
    private final ExecutorService pool;
    private volatile boolean closed;
    public final AtomicInteger openTunnels = new AtomicInteger();
    public final AtomicLong bytesUp = new AtomicLong(), bytesDown = new AtomicLong();
    private volatile String lastError;

    public LocalProxy(Upstream upstream) {
        this.upstream = upstream == null ? DIRECT : upstream;
        byte[] r = new byte[18];
        new SecureRandom().nextBytes(r);
        StringBuilder b = new StringBuilder();
        for (byte x : r) b.append(String.format(Locale.ROOT, "%02x", x));
        token = b.toString();
        pool = new ThreadPoolExecutor(0, MAX_TUNNELS + 4, 30, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), r2 -> {
            Thread t = new Thread(r2, "proxy");
            t.setDaemon(true);
            return t;
        });
    }

    /** 开始监听，返回端口。 */
    public synchronized int start() throws IOException {
        if (closed) throw new IOException("代理已关闭");
        if (server != null) return server.getLocalPort();
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 64);
        Thread t = new Thread(this::acceptLoop, "proxy-accept");
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    public int port() {
        return server == null ? -1 : server.getLocalPort();
    }

    /** 给 codex 的代理地址（带令牌）。 */
    public String url() {
        return "http://" + user + ":" + token + "@127.0.0.1:" + port();
    }

    public String lastError() {
        return lastError;
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                final Socket c = server.accept();
                try {
                    pool.execute(() -> handle(c));
                } catch (Exception rejected) {
                    closeQuietly(c);
                }
            } catch (IOException e) {
                if (closed) return;
                lastError = e.toString();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void handle(Socket client) {
        Socket remote = null;
        live.add(client);
        try {
            if (openTunnels.get() >= MAX_TUNNELS) {
                reply(client.getOutputStream(), "503 Service Unavailable");
                return;
            }
            client.setTcpNoDelay(true);
            client.setSoTimeout(10_000); // 读请求头最多等 10 秒
            InputStream cin = client.getInputStream();
            OutputStream cout = client.getOutputStream();
            byte[] headBytes = readHead(cin);
            if (headBytes == null) return;
            String head = new String(headBytes, StandardCharsets.ISO_8859_1);
            if (!head.endsWith("\r\n\r\n")) { // 头没读完连接就断了
                reply(cout, "400 Bad Request");
                return;
            }
            int eol = head.indexOf("\r\n");
            String requestLine = eol < 0 ? head : head.substring(0, eol);
            String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                reply(cout, "400 Bad Request");
                return;
            }
            if (!authorized(head)) {
                cout.write(("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"pocket-codex\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                cout.flush();
                return;
            }
            String method = parts[0].toUpperCase(Locale.ROOT);
            client.setSoTimeout(0); // 隧道里的流（SSE、WebSocket）可以长时间不说话
            if (method.equals("CONNECT")) {
                String hp = parts[1];
                int colon = hp.lastIndexOf(':');
                String host = colon > 0 ? hp.substring(0, colon) : hp;
                int port = colon > 0 ? Integer.parseInt(hp.substring(colon + 1)) : 443;
                if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
                if (host.equalsIgnoreCase(LOCAL_HOST)) {
                    reply(cout, "400 Bad Request");
                    return;
                }
                try {
                    remote = open(host, port);
                    live.add(remote);
                } catch (IOException e) {
                    lastError = host + ":" + port + " " + e;
                    reply(cout, "502 Bad Gateway");
                    return;
                }
                cout.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                cout.flush();
                pipe(client, remote);
            } else {
                // 普通 http：GET http://host/path HTTP/1.1 → 改成 GET /path，去掉代理头，Connection: close
                URI u = new URI(parts[1]);
                if (u.getHost() == null || !"http".equalsIgnoreCase(u.getScheme())) {
                    reply(cout, "400 Bad Request");
                    return;
                }
                if (u.getHost().equalsIgnoreCase(LOCAL_HOST)) {
                    handleLocal(u, cout);
                    return;
                }
                int port = u.getPort() > 0 ? u.getPort() : 80;
                Proxy up = upstream.select(u.getHost(), port);
                boolean viaUpstream = up != null && up.type() == Proxy.Type.HTTP;
                remote = viaUpstream ? connectTo((InetSocketAddress) up.address()) : open(u.getHost(), port);
                live.add(remote);
                // 直连：改成源站形式（/path）；经上游 HTTP 代理：保持绝对地址（正向代理请求，不用 CONNECT 到 80 端口）
                String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
                if (u.getRawQuery() != null) path += "?" + u.getRawQuery();
                String target = viaUpstream ? parts[1] : path;
                StringBuilder nh = new StringBuilder();
                nh.append(parts[0]).append(' ').append(target).append(' ').append(parts[2]).append("\r\n");
                String[] lines = head.split("\r\n");
                for (int i = 1; i < lines.length; i++) {
                    String l = lines[i];
                    if (l.isEmpty()) continue;
                    String lk = l.toLowerCase(Locale.ROOT);
                    if (lk.startsWith("proxy-") || lk.startsWith("connection:") || lk.startsWith("keep-alive:")) continue;
                    nh.append(l).append("\r\n");
                }
                nh.append("Connection: close\r\n\r\n");
                OutputStream rout = remote.getOutputStream();
                rout.write(nh.toString().getBytes(StandardCharsets.ISO_8859_1));
                rout.flush();
                pipe(client, remote);
            }
        } catch (Exception e) {
            if (!closed) lastError = e.toString();
        } finally {
            closeQuietly(client);
            closeQuietly(remote);
            live.remove(client);
            if (remote != null) live.remove(remote);
        }
    }

    private void handleLocal(URI u, OutputStream cout) throws IOException {
        LocalHandler h = localHandler;
        if (h == null) {
            reply(cout, "503 Service Unavailable");
            return;
        }
        java.util.List<String> args = new java.util.ArrayList<>();
        String q = u.getRawQuery();
        if (q != null) {
            for (String kv : q.split("&")) {
                if (kv.startsWith("a=")) args.add(java.net.URLDecoder.decode(kv.substring(2), "UTF-8"));
            }
        }
        cout.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        cout.flush();
        java.io.PrintStream out = new java.io.PrintStream(cout, true, "UTF-8");
        int code;
        try {
            code = h.handle(u.getPath() == null ? "/" : u.getPath(), args, out);
        } catch (Exception e) {
            out.println("错误：" + e.getMessage());
            code = 1;
        }
        out.println("@@exit " + code);
        out.flush();
    }

    private boolean authorized(String head) {
        for (String l : head.split("\r\n")) {
            int c = l.indexOf(':');
            if (c <= 0) continue;
            if (!l.substring(0, c).trim().equalsIgnoreCase("Proxy-Authorization")) continue;
            String v = l.substring(c + 1).trim();
            // Basic 的方案名大小写不敏感；令牌部分比较原样（Base64 区分大小写）
            int sp = v.indexOf(' ');
            if (sp < 0) return false;
            String scheme = v.substring(0, sp).toLowerCase(Locale.ROOT);
            String cred = v.substring(sp + 1).trim();
            String want = java.util.Base64.getEncoder().encodeToString((user + ":" + token).getBytes(StandardCharsets.UTF_8));
            return scheme.equals("basic") && constantTimeEquals(cred, want);
        }
        return false;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private static Socket connectTo(InetSocketAddress a) throws IOException {
        Socket s = new Socket();
        try {
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
            s.connect(a.isUnresolved() ? new InetSocketAddress(a.getHostString(), a.getPort()) : a, CONNECT_TIMEOUT_MS);
            return s;
        } catch (IOException e) {
            closeQuietly(s);
            throw e;
        }
    }

    /** 连目标：按上游设置直连（域名在这里由系统解析），或经上游 HTTP 代理打 CONNECT 隧道。 */
    private Socket open(String host, int port) throws IOException {
        Proxy p = upstream.select(host, port);
        if (p == null || p.type() != Proxy.Type.HTTP) return connectTo(new InetSocketAddress(host, port));
        Socket s = connectTo((InetSocketAddress) p.address());
        try {
            OutputStream o = s.getOutputStream();
            String hp = (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
            o.write(("CONNECT " + hp + " HTTP/1.1\r\nHost: " + hp + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            o.flush();
            s.setSoTimeout(CONNECT_TIMEOUT_MS);
            byte[] resp = readHead(s.getInputStream());
            s.setSoTimeout(0);
            String r = resp == null ? "" : new String(resp, StandardCharsets.ISO_8859_1);
            if (!r.startsWith("HTTP/1.") || r.length() < 12 || r.charAt(9) != '2') {
                throw new IOException("上游代理拒绝：" + (r.indexOf('\r') > 0 ? r.substring(0, r.indexOf('\r')) : r));
            }
            return s;
        } catch (IOException e) {
            closeQuietly(s);
            throw e;
        }
    }

    /** 读到 \r\n\r\n 为止（逐字节读，不多读，后面的数据留在流里给隧道）。 */
    private static byte[] readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(512);
        int state = 0;
        while (b.size() < HEAD_LIMIT) {
            int c = in.read();
            if (c < 0) return b.size() == 0 ? null : b.toByteArray();
            b.write(c);
            if ((state == 0 || state == 2) && c == '\r') state++;
            else if ((state == 1 || state == 3) && c == '\n') state++;
            else state = c == '\r' ? 1 : 0;
            if (state == 4) return b.toByteArray();
        }
        throw new IOException("请求头太长");
    }

    private void pipe(Socket a, Socket b) throws IOException, InterruptedException {
        openTunnels.incrementAndGet();
        try {
            final InputStream ain = a.getInputStream(), bin = b.getInputStream();
            final OutputStream aout = a.getOutputStream(), bout = b.getOutputStream();
            Thread up = new Thread(() -> copy(ain, bout, b, bytesUp), "proxy-up");
            up.setDaemon(true);
            up.start();
            copy(bin, aout, a, bytesDown);
            // 下行结束（服务器关了）：等上行一小会儿再整体关
            up.join(2000);
        } finally {
            openTunnels.decrementAndGet();
        }
    }

    private static void copy(InputStream in, OutputStream out, Socket outSock, AtomicLong counter) {
        byte[] buf = new byte[32 * 1024];
        try {
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n == 0) continue;
                out.write(buf, 0, n);
                out.flush();
                counter.addAndGet(n);
            }
            try {
                outSock.shutdownOutput(); // 半关闭：告诉对面“我这边写完了”
            } catch (IOException ignored) {
                // 对面已经关了
            }
        } catch (SocketException ignored) {
            // 连接被关：正常结束
        } catch (IOException ignored) {
            // 同上
        }
    }

    private static void reply(OutputStream out, String status) throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static void closeQuietly(Socket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (IOException ignored) {
            // 已关
        }
    }

    /** 关掉监听和所有正在用的连接（阻塞在读上的隧道也会立刻结束）。关了就不能再 start。 */
    @Override
    public synchronized void close() {
        closed = true;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
            // 已关
        }
        for (Socket s : live) closeQuietly(s);
        live.clear();
        pool.shutdownNow();
    }

    /** 当前打开的连接数（测试和诊断用）。 */
    public int liveSockets() {
        return live.size();
    }
}
