package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** config.toml 合并：我们管的键每次重写，Agent / 安装向导加的 [mcp_servers.*] 等保留，不出现重复键。 */
public class ConfigMergeTest {
    private static CodexRuntime.Config cfg(String model) {
        CodexRuntime.Config c = new CodexRuntime.Config();
        c.model = model;
        return c;
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) n++;
        return n;
    }

    /** v0.2 写的整份文件：合并后只有一份我们的键。 */
    static final String V02 = "# 由 Pocket Codex 生成，每次启动会覆盖。\n"
            + "cli_auth_credentials_store = \"file\"\nsandbox_mode = \"danger-full-access\"\ncheck_for_update_on_startup = false\n"
            + "model = \"gpt-6-luna\"\nweb_search = \"live\"\n\n[features]\nshell_snapshot = false\n\n[shell_environment_policy]\ninherit = \"all\"\n";

    @Test
    public void oldFileHasNoDuplicates() {
        String out = CodexRuntime.mergeConfig(V02, cfg("gpt-6-astra"));
        assertEquals(1, count(out, "sandbox_mode"));
        assertEquals(1, count(out, "model = "));
        assertTrue(out.contains("model = \"gpt-6-astra\""));
        assertEquals(1, count(out, "shell_snapshot = false"));
        assertEquals(1, count(out, "inherit = \"all\""));
        assertFalse(out.contains("gpt-6-luna"));
    }

    /** REA 之类的安装向导在文件末尾加的 MCP 服务：保留，而且下次启动还在。 */
    @Test
    public void keepsMcpServers() {
        String withRea = CodexRuntime.mergeConfig(V02, cfg(null)) + "\n[mcp_servers.rea]\ncommand = \"npx\"\nargs = [\"-y\", \"rea-agents@6.8.0\", \"mcp\"]\nstartup_timeout_sec = 60\n";
        String once = CodexRuntime.mergeConfig(withRea, cfg("gpt-6.1-sol"));
        String twice = CodexRuntime.mergeConfig(once, cfg("gpt-6.1-sol"));
        assertTrue(once.contains("[mcp_servers.rea]"));
        assertTrue(once.contains("args = [\"-y\", \"rea-agents@6.8.0\", \"mcp\"]"));
        assertEquals(once, twice); // 幂等
        assertFalse(once.contains("model = \"gpt-6-luna\""));
        assertFalse(once.contains("\nmodel = ") && cfg(null).model == null && once.indexOf("model = ") != once.lastIndexOf("model = "));
    }

    /** 用户自己有 [features] 表：把我们的那一项放进去，不再写点号键（避免重复定义表）。 */
    @Test
    public void mergesIntoUserFeaturesTable() {
        String user = "[features]\nweb_search_request = true\nshell_snapshot = true\n";
        String out = CodexRuntime.mergeConfig(user, cfg(null));
        assertFalse(out.contains("features.shell_snapshot"));
        assertEquals(1, count(out, "shell_snapshot"));
        assertTrue(out.contains("shell_snapshot = false"));
        assertTrue(out.contains("web_search_request = true"));
        assertTrue(out.contains("shell_environment_policy.inherit = \"all\""));
    }

    /** 用户写在顶层的其他键保留；我们管的键用我们的值。 */
    @Test
    public void keepsUserTopLevelKeys() {
        String user = "model = \"x\"\nmodel_verbosity = \"low\"\nsandbox_mode = \"read-only\"\n[mcp_servers.r2]\ncommand = \"r2pm\"\nargs = [\"-r\", \"r2mcp\"]\n";
        String out = CodexRuntime.mergeConfig(user, cfg(null));
        assertTrue(out.contains("model_verbosity = \"low\""));
        assertEquals(1, count(out, "sandbox_mode"));
        assertTrue(out.contains("sandbox_mode = \"danger-full-access\""));
        assertFalse(out.contains("model = \"x\""));
        assertTrue(out.indexOf("model_verbosity") < out.indexOf("[mcp_servers.r2]"));
    }
}
