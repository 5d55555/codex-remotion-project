package com.pocketcodex.core;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import org.junit.Test;

/**
 * 真实 Termux 仓库：TOOLKIT_REAL_PREFIX=…/usr TOOLKIT_REAL_ABI=x86_64 时才跑，装完整的基础工具包
 * （配合安卓 bionic 的 chroot 手工验证 node/npm/git/python）。
 */
public class ToolkitRealTest {
    @Test
    public void installRealBase() throws Exception {
        String p = System.getenv("TOOLKIT_REAL_PREFIX");
        assumeTrue(p != null);
        String abi = System.getenv("TOOLKIT_REAL_ABI") != null ? System.getenv("TOOLKIT_REAL_ABI") : "x86_64";
        File prefix = new File(p);
        Toolkit t = new Toolkit(prefix, new File(prefix.getParentFile(), "pkg"), abi);
        long t0 = System.currentTimeMillis();
        t.installBase(System.getenv("TOOLKIT_REAL_REPO"), new Toolkit.Progress() {
            String last = "";

            @Override
            public void step(String text, long done, long total) {
                if (!text.equals(last)) System.out.println(text + (total > 0 ? " " + Toolkit.mb(done) + "/" + Toolkit.mb(total) : ""));
                last = text;
            }

            @Override
            public boolean cancelled() {
                return false;
            }
        });
        System.out.println("装好 " + t.status().size() + " 个包，用时 " + (System.currentTimeMillis() - t0) / 1000 + " 秒");
        assertTrue(t.installed());
        assertTrue(new File(prefix, "bin/node").isFile());
    }

    /** TOOLKIT_CHROOT=<安卓 chroot 根目录> 时：在 chroot 里跑 pkg 命令，经 LocalProxy 的本机通道让这里的 Toolkit 安装。 */
    @Test
    public void pkgThroughProxyInChroot() throws Exception {
        String p = System.getenv("TOOLKIT_REAL_PREFIX"), root = System.getenv("TOOLKIT_CHROOT");
        assumeTrue(p != null && root != null);
        File prefix = new File(p);
        Toolkit t = new Toolkit(prefix, new File(prefix.getParentFile(), "pkg"), "x86_64");
        assertTrue(t.installed());
        LocalProxy proxy = new LocalProxy(LocalProxy.DIRECT);
        proxy.start();
        proxy.setLocalHandler((path, args, out) -> path.equals("/pkg") ? t.command(args, null, out, () -> false) : 2);
        File shim = new File(prefix.getParentFile(), "shim/bin");
        shim.mkdirs();
        for (String n : new String[]{"pkg", "apt", "apt-get"}) CodexRuntime.writeIfChanged(new File(shim, n), Toolkit.PKG_SCRIPT, true);
        String script = "pkg install fd 2>&1 | tail -3; fd --version; apt install -y fd; apt list --installed | grep -c . ; "
                + "pkg search ripgrep | head -2; pkg show fd | head -2; pkg install no-such-thing-xyz; echo rc=$?; "
                + "pkg uninstall fd; fd --version; echo rc=$?; pkg remove git; echo rc=$?";
        ProcessBuilder pb = new ProcessBuilder("/usr/sbin/chroot", root, "/system/bin/sh", "-c",
                "export PREFIX=" + p + " PATH=" + shim + ":" + p + "/bin:/system/bin http_proxy=" + proxy.url()
                        + " LD_PRELOAD=" + p + "/lib/libtermux-exec-direct-ld-preload.so TERMUX__PREFIX=" + p + "; exec " + p + "/bin/bash -c '" + script + "'");
        pb.environment().clear();
        pb.environment().put("ANDROID_ROOT", "/system");
        pb.environment().put("ANDROID_DATA", "/data");
        pb.redirectErrorStream(true);
        Process pr = pb.start();
        String out = new String(Installer.readAll(pr.getInputStream(), 1 << 20), java.nio.charset.StandardCharsets.UTF_8);
        pr.waitFor();
        proxy.close();
        System.out.println(out);
        assertTrue(out, out.contains("fd ") || out.contains("fdfind"));
        assertTrue(out, out.contains("rc=3"));
        assertTrue(out, out.contains("属于基础工具包"));
    }
}
