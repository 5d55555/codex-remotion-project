package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * MCP 服务接入对话：Codex 启动 stdio MCP 服务时只传很少的环境变量（codex 0.166 实测），口袋工坊用 -c 给每个用命令启动的服务
 * 补上 env_vars 和启动超时；REA 由口袋工坊写成直接用 node 运行的配置。
 */
public class McpConfigTest {
    static final String SAMPLE = "# 用户自己的配置\n"
            + "model_verbosity = \"low\"\n\n"
            + "[mcp_servers.rea]\n"
            + "command = \"npx\"\n"
            + "args = [\"-y\", \"rea-agents@6.8.0\", \"mcp\"]\n\n"
            + "[mcp_servers.rea.env]\n"
            + "REA_TELEMETRY = \"0\"\n\n"
            + "[mcp_servers.r2]\n"
            + "command = \"r2pm\"\n"
            + "args = [\n  \"-r\",\n  \"r2mcp\",\n]\n"
            + "env_vars = [\n  \"R2_HOME\",\n  'MY_TOKEN',\n]\n"
            + "startup_timeout_sec = 30\n\n"
            + "[mcp_servers.docs]\n"
            + "url = \"https://example.com/mcp\"\n\n"
            + "[mcp_servers.\"has space\"]\n"
            + "command = \"node\"\n\n"
            + "[profiles.fast]\n"
            + "command = \"not-a-server\"\n";

    @Test
    public void findsServers() {
        Map<String, CodexRuntime.McpServer> m = CodexRuntime.mcpServers(SAMPLE);
        assertEquals(Arrays.asList("rea", "r2", "docs", "has space"), new java.util.ArrayList<>(m.keySet()));
        assertTrue(m.get("rea").stdio);
        assertFalse(m.get("rea").hasTimeout);
        assertTrue("[mcp_servers.rea.env] 里的键不算服务本身的", m.get("rea").envVars.isEmpty());
        assertTrue(m.get("r2").stdio);
        assertTrue(m.get("r2").hasTimeout);
        assertEquals(Arrays.asList("R2_HOME", "MY_TOKEN"), m.get("r2").envVars);
        assertFalse("用 url 的是 HTTP 服务", m.get("docs").stdio);
    }

    @Test
    public void overridesOnlyStdioServers() {
        List<String> o = CodexRuntime.mcpOverrides(SAMPLE, Arrays.asList("LD_PRELOAD", "PREFIX", "TMPDIR", "HTTPS_PROXY"));
        String all = String.join(" ", o);
        // REA：补齐环境变量 + 启动超时
        assertTrue(all, o.contains("mcp_servers.rea.env_vars=[\"LD_PRELOAD\",\"PREFIX\",\"TMPDIR\",\"HTTPS_PROXY\"]"));
        assertTrue(all, o.contains("mcp_servers.rea.startup_timeout_sec=" + CodexRuntime.MCP_STARTUP_TIMEOUT_SEC));
        // r2：用户原有的 env_vars 保留在前面；自己设了超时就不动
        assertTrue(all, o.contains("mcp_servers.r2.env_vars=[\"R2_HOME\",\"MY_TOKEN\",\"LD_PRELOAD\",\"PREFIX\",\"TMPDIR\",\"HTTPS_PROXY\"]"));
        assertFalse(all, all.contains("mcp_servers.r2.startup_timeout_sec"));
        // HTTP 服务、名字写不进键路径的、不是 MCP 的表：都不碰
        assertFalse(all, all.contains("docs"));
        assertFalse(all, all.contains("space"));
        assertFalse(all, all.contains("profiles"));
        for (int i = 0; i < o.size(); i += 2) assertEquals("-c", o.get(i));
        assertTrue(CodexRuntime.mcpOverrides(null, Arrays.asList("A")).isEmpty());
    }

    @Test
    public void inlineServersAndMultilineStrings() {
        String t = "developer_instructions = \"\"\"\n[mcp_servers.fake]\ncommand = \"x\"\n\"\"\"\n"
                + "[mcp_servers]\n"
                + "a = { command = \"node\", args = [\"a.js\"], env_vars = [\"KEEP\"] }\n"
                + "b = { url = \"https://example.com\" }\n"
                + "c = { command = \"node\", startup_timeout_sec = 9 }\n";
        Map<String, CodexRuntime.McpServer> m = CodexRuntime.mcpServers(t);
        assertFalse("多行字符串里的表头不算", m.containsKey("fake"));
        assertTrue(m.get("a").stdio);
        assertEquals(Arrays.asList("KEEP"), m.get("a").envVars);
        assertFalse(m.get("b").stdio);
        assertTrue(m.get("c").hasTimeout);
        List<String> o = CodexRuntime.mcpOverrides(t, Arrays.asList("PREFIX"));
        assertTrue(o.toString(), o.contains("mcp_servers.a.env_vars=[\"KEEP\",\"PREFIX\"]"));
        assertFalse(o.toString(), o.toString().contains("mcp_servers.c.startup_timeout_sec"));
    }

    @Test
    public void writesReaConfig() {
        String node = "/data/user/0/com.pocketcodex.app/files/usr/bin/node";
        String js = "/data/user/0/com.pocketcodex.app/files/home/.npm-global/lib/node_modules/rea-agents/scripts/rea.mjs";
        String out = CodexRuntime.withRea(SAMPLE, node, js);
        // npx 版本换成直接用 node；子表、别的服务、用户的顶层键都留着
        assertFalse(out, out.contains("\"npx\""));
        assertTrue(out, out.contains("[mcp_servers.rea]\n# 口袋工坊装的 REA"));
        assertTrue(out, out.contains("command = \"" + node + "\"\nargs = [\"" + js + "\", \"mcp\"]\nstartup_timeout_sec = 120\n\n[mcp_servers.rea.env]\nREA_TELEMETRY = \"0\""));
        assertTrue(out, out.contains("[mcp_servers.r2]\ncommand = \"r2pm\""));
        assertTrue(out, out.contains("model_verbosity = \"low\""));
        assertEquals("再写一次不变", out, CodexRuntime.withRea(out, node, js));
        Map<String, CodexRuntime.McpServer> m = CodexRuntime.mcpServers(out);
        assertTrue(m.get("rea").stdio);
        assertTrue(m.get("rea").hasTimeout);
        // 没有 REA：加在末尾
        String added = CodexRuntime.withRea("model_verbosity = \"low\"\n", node, js);
        assertTrue(added, added.startsWith("model_verbosity = \"low\"\n\n[mcp_servers.rea]\n"));
        assertTrue(CodexRuntime.withRea(null, node, js).startsWith(CodexRuntime.HEADER));
    }
}
