package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class DeviceAuthTest {
    static String fixture() throws Exception {
        try (InputStream in = DeviceAuthTest.class.getResourceAsStream("/device_auth_0164.txt")) {
            return new String(Installer.readAll(in, 1 << 20), StandardCharsets.UTF_8);
        }
    }

    /** 真实的 codex 0.164 输出（带 ANSI 颜色）。 */
    @Test
    public void parsesRealOutput() throws Exception {
        DeviceAuth.Info i = DeviceAuth.parse(fixture());
        assertNotNull(i);
        assertEquals("https://auth.openai.com/codex/device", i.url);
        assertEquals("VR0G-2QTVX", i.code);
        assertEquals(15 * 60, i.expiresInSec);
    }

    /** 还没写完（只有网址、没有代码）时不算拿到。 */
    @Test
    public void partialOutputIsNotReady() throws Exception {
        String f = fixture();
        assertNull(DeviceAuth.parse(f.substring(0, f.indexOf("2. Enter"))));
        assertNull(DeviceAuth.parse(""));
    }

    /** 不被网址里的连字符、UTF-8 之类的词骗到；有效期按输出里写的。 */
    @Test
    public void ignoresLookalikes() {
        String s = "Locale UTF-8\nOpen https://auth.openai.com/codex/device?x=AB12-CD34.\nEnter this one-time code (expires in 10 minutes)\n  X-RAY\n  K7QP-M2ZZ1\n";
        DeviceAuth.Info i = DeviceAuth.parse(s);
        assertNotNull(i);
        assertEquals("https://auth.openai.com/codex/device?x=AB12-CD34", i.url);
        assertEquals("K7QP-M2ZZ1", i.code);
        assertEquals(600, i.expiresInSec);
    }

    /** 只把 OpenAI 官方授权页交给浏览器。 */
    @Test
    public void onlyOfficialUrls() {
        assertNull(DeviceAuth.parse("Open https://evil.example/codex/device\nEnter this one-time code\n  AB12-CD345\n"));
        assertNotNull(DeviceAuth.parse("Open https://auth.openai.com/codex/device\nEnter this one-time code\n  AB12-CD345\n"));
        assertNull("http 不行", DeviceAuth.parse("Open http://auth.openai.com/codex/device\nEnter this one-time code\n  AB12-CD345\n"));
    }

    @Test
    public void classifiesOutcomes() {
        assertEquals(DeviceAuth.Outcome.SUCCESS, DeviceAuth.classify(0, "Successfully logged in", true));
        assertEquals(DeviceAuth.Outcome.EXPIRED, DeviceAuth.classify(1, "Error: device auth timed out after 15 minutes", false));
        assertEquals("一般的超时是网络问题，不是过期", DeviceAuth.Outcome.NETWORK, DeviceAuth.classify(1, "Error: operation timed out", false));
        assertEquals(DeviceAuth.Outcome.NETWORK, DeviceAuth.classify(1, "error sending request for url (https://auth.openai.com/...)", false));
        assertEquals(DeviceAuth.Outcome.DEVICE_AUTH_DISABLED, DeviceAuth.classify(1, "Device code login is not enabled for this workspace", false));
        assertEquals(DeviceAuth.Outcome.FAILED, DeviceAuth.classify(1, "something odd", false));
        assertEquals(DeviceAuth.Outcome.FAILED, DeviceAuth.classify(0, "not logged in", false));
        // 代码说明里的 “expires in 15 minutes” 不算过期
        assertEquals(DeviceAuth.Outcome.NETWORK, DeviceAuth.classify(1, "Enter this one-time code (expires in 15 minutes)\nAB12-CD345\nError: error sending request", false));
    }

    @Test
    public void tailDropsColorsAndBlankLines() {
        assertEquals("b\nc", DeviceAuth.tail("\u001B[31ma\u001B[0m\n\nb\n\n c \n", 2));
    }
}
