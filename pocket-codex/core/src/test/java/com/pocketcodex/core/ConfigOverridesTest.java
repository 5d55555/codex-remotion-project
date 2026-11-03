package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * 口袋工坊的设置用 codex -c 传，config.toml 留给用户和 Agent：
 * 用户写的内容（MCP、内联表、带引号的键、多行字符串）原样不动；只有旧版口袋工坊生成的文件做一次迁移。
 */
public class ConfigOverridesTest {
    @Test
    public void overridesCarryManagedSettings() {
        CodexRuntime.Config c = new CodexRuntime.Config();
        c.model = "gpt-6.1-sol";
        c.reasoningEffort = "medium";
        List<String> o = CodexRuntime.overrides(c);
        String all = String.join(" ", o);
        assertTrue(all.contains("cli_auth_credentials_store=\"file\""));
        assertTrue(all.contains("sandbox_mode=\"danger-full-access\""));
        assertTrue(all.contains("model=\"gpt-6.1-sol\""));
        assertTrue(all.contains("model_reasoning_effort=\"medium\""));
        assertTrue(all.contains("features.shell_snapshot=false"));
        assertFalse("approval_policy 不能写进配置（0.166 会拒绝启动）", all.contains("approval_policy"));
        for (int i = 0; i < o.size(); i += 2) assertEquals("-c", o.get(i));
        assertFalse(String.join(" ", CodexRuntime.overrides(new CodexRuntime.Config())).contains("model="));
    }

    @Test
    public void newFileIsJustAComment() {
        String s = CodexRuntime.migrateConfig(null);
        for (String line : s.split("\n")) assertTrue(line.isEmpty() || line.startsWith("#"));
    }

    @Test
    public void userFilesAreUntouched() {
        String[] samples = {
                "[features] # 自定义功能\nmulti_agent = true\n",
                "[\"features\"]\nshell_snapshot = true\n",
                "'model' = 'old' # 注释\n",
                "features = { shell_snapshot = true, multi_agent = true }\n",
                "developer_instructions = \"\"\"\n# 必须保留\nmodel = example\n\nhello\n\"\"\"\n",
                "\"features.shell_snapshot\" = true\n[mcp_servers.rea]\ncommand = \"npx\"\nargs = [\n  \"-y\",\n  \"rea-agents@6.8.0\",\n  \"mcp\",\n]\n",
        };
        for (String s : samples) assertEquals(s, CodexRuntime.migrateConfig(s));
    }

    @Test
    public void migratesV02File() {
        String v02 = "# 由 Pocket Codex 生成，每次启动会覆盖。\ncli_auth_credentials_store = \"file\"\nsandbox_mode = \"danger-full-access\"\nmodel = \"gpt-6-luna\"\n\n[features]\nshell_snapshot = false\n";
        assertEquals(CodexRuntime.HEADER, CodexRuntime.migrateConfig(v02));
    }

    /** v0.3 写的文件（开头是我们的键，后面是用户/REA 加的 MCP）：去掉我们的键，MCP 留下，再迁移一次不变。 */
    @Test
    public void migratesV03FileKeepingMcp() {
        String v03 = "# 由口袋工坊生成：下面几行每次启动会重写；\n# 之后的内容（你或 Agent 加的配置，例如 [mcp_servers.*]）会原样保留。\n"
                + "cli_auth_credentials_store = \"file\"\nsandbox_mode = \"danger-full-access\"\ncheck_for_update_on_startup = false\nmodel = \"gpt-6-luna\"\n"
                + "web_search = \"live\"\nfeatures.shell_snapshot = false\nshell_environment_policy.inherit = \"all\"\nmodel_verbosity = \"low\"\n\n"
                + "[mcp_servers.rea]\ncommand = \"npx\"\nargs = [\"-y\", \"rea-agents@6.8.0\", \"mcp\"]\n";
        String out = CodexRuntime.migrateConfig(v03);
        assertTrue(out.startsWith(CodexRuntime.HEADER));
        assertFalse(out.contains("gpt-6-luna"));
        assertFalse(out.contains("sandbox_mode"));
        assertTrue(out.contains("model_verbosity = \"low\""));
        assertTrue(out.contains("[mcp_servers.rea]\ncommand = \"npx\"\nargs = [\"-y\", \"rea-agents@6.8.0\", \"mcp\"]"));
        assertEquals(out, CodexRuntime.migrateConfig(out));
    }
}
