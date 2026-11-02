package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.Test;

public class LocalProxyTest {

    private static String head(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c, state = 0;
        while ((c = in.read()) >= 0) {
            b.write(c);
            state = (c == '\r' && (state == 0 || state == 2)) ? state + 1 : (c == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
            if (state == 4) break;
        }
        return b.toString("ISO-8859-1");
    }

    private static String auth(LocalProxy p) {
        String u = p.url();
        String cred = u.substring("http://".length(), u.indexOf('@'));
        return "Proxy-Authorization: Basic " + Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8)) + "\r\n";
    }

    /** CONNECT 隧道：域名在代理这边解析（localhost），之后双向搬字节。 */
    @Test
    public void tunnelsWithAuth() throws Exception {
        try (ServerSocket echo = new ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"));
             LocalProxy p = new LocalProxy(LocalProxy.DIRECT)) {
            Thread t = new Thread(() -> {
                try (Socket s = echo.accept()) {
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();
                    byte[] buf = new byte[1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        out.flush();
                    }
                } catch (Exception ignored) {
                    // 测试结束
                }
            });
            t.start();
            int port = p.start();
            assertTrue(p.url().endsWith("@127.0.0.1:" + port));
            try (Socket c = new Socket("127.0.0.1", port)) {
                OutputStream o = c.getOutputStream();
                o.write(("CONNECT localhost:" + echo.getLocalPort() + " HTTP/1.1\r\nHost: x\r\n" + auth(p) + "\r\nhello").getBytes(StandardCharsets.ISO_8859_1));
                o.flush();
                InputStream in = c.getInputStream();
                assertTrue(head(in).startsWith("HTTP/1.1 200"));
                byte[] got = new byte[5];
                int off = 0;
                while (off < 5) off += in.read(got, off, 5 - off);
                assertEquals("hello", new String(got, StandardCharsets.ISO_8859_1));
                o.write("world".getBytes(StandardCharsets.ISO_8859_1));
                off = 0;
                while (off < 5) off += in.read(got, off, 5 - off);
                assertEquals("world", new String(got, StandardCharsets.ISO_8859_1));
            }
        }
    }

    /** 关掉代理时，正在阻塞读的隧道也立刻断开，不留连接。 */
    @Test
    public void closeKillsLiveTunnels() throws Exception {
        try (ServerSocket sink = new ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))) {
            Thread t = new Thread(() -> {
                try (Socket s = sink.accept()) {
                    s.getInputStream().read(); // 一直挂着不说话
                } catch (Exception ignored) {
                    // 结束
                }
            });
            t.start();
            LocalProxy p = new LocalProxy(LocalProxy.DIRECT);
            int port = p.start();
            Socket c = new Socket("127.0.0.1", port);
            c.getOutputStream().write(("CONNECT 127.0.0.1:" + sink.getLocalPort() + " HTTP/1.1\r\n" + auth(p) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            assertTrue(head(c.getInputStream()).startsWith("HTTP/1.1 200"));
            Thread.sleep(100);
            assertTrue(p.liveSockets() >= 2);
            p.close();
            c.setSoTimeout(3000);
            assertEquals("代理关闭后客户端读到 EOF", -1, c.getInputStream().read());
            assertEquals(0, p.liveSockets());
            c.close();
        }
    }

    /** 不带令牌（本机别的 App）：407。 */
    @Test
    public void rejectsWithoutAuth() throws Exception {
        try (LocalProxy p = new LocalProxy(LocalProxy.DIRECT)) {
            int port = p.start();
            try (Socket c = new Socket("127.0.0.1", port)) {
                c.getOutputStream().write("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                assertTrue(head(c.getInputStream()).startsWith("HTTP/1.1 407"));
            }
            try (Socket c = new Socket("127.0.0.1", port)) {
                c.getOutputStream().write(("CONNECT example.com:443 HTTP/1.1\r\nProxy-Authorization: Basic Y29kZXg6d3Jvbmc=\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                assertTrue(head(c.getInputStream()).startsWith("HTTP/1.1 407"));
            }
        }
    }

    /** 普通 http 的绝对地址请求转发成源站形式。 */
    @Test
    public void forwardsPlainHttp() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/hi", ex -> {
            byte[] b = ("path=" + ex.getRequestURI()).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        s.start();
        try (LocalProxy p = new LocalProxy(LocalProxy.DIRECT)) {
            int port = p.start();
            try (Socket c = new Socket("127.0.0.1", port)) {
                c.getOutputStream().write(("GET http://localhost:" + s.getAddress().getPort() + "/hi?a=1 HTTP/1.1\r\nHost: localhost\r\n" + auth(p) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                InputStream in = c.getInputStream();
                assertTrue(head(in).startsWith("HTTP/1.1 200"));
                byte[] b = Installer.readAll(in, 4096);
                assertEquals("path=/hi?a=1", new String(b, StandardCharsets.UTF_8));
            }
        } finally {
            s.stop(0);
        }
    }

    /** 目标连不上：502，不挂住。 */
    @Test
    public void badGateway() throws Exception {
        try (LocalProxy p = new LocalProxy(LocalProxy.DIRECT)) {
            int port = p.start();
            int dead;
            try (ServerSocket ss = new ServerSocket(0)) {
                dead = ss.getLocalPort();
            }
            try (Socket c = new Socket("127.0.0.1", port)) {
                c.getOutputStream().write(("CONNECT 127.0.0.1:" + dead + " HTTP/1.1\r\n" + auth(p) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                assertTrue(head(c.getInputStream()).startsWith("HTTP/1.1 502"));
            }
        }
    }
}
