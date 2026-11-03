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

    /** v0.2 原样生成的文件（和当时的 writeConfig 一字不差）。 */
    static final String V02 = "# 由 Pocket Codex 生成，每次启动会覆盖。\ncli_auth_credentials_store = \"file\"\nsandbox_mode = \"danger-full-access\"\n"
            + "check_for_update_on_startup = false\nmodel = \"gpt-6-luna\"\nmodel_reasoning_effort = \"high\"\nweb_search = \"live\"\n"
            + "\n[features]\nshell_snapshot = false\n\n[shell_environment_policy]\ninherit = \"all\"\n";

    @Test
    public void migratesV02File() {
        assertEquals(CodexRuntime.HEADER, CodexRuntime.migrateConfig(V02));
    }

    /** v0.2 文件后面用户（或 REA 向导）又加了 MCP，然后直接升级：MCP 要留下。 */
    @Test
    public void migratesV02FileKeepingAppendedMcp() {
        String mcp = "\n[mcp_servers.rea]\ncommand = \"npx\"\nargs = [\n  \"-y\",\n  \"rea-agents@6.8.0\",\n]\n";
        String out = CodexRuntime.migrateConfig(V02 + mcp);
        assertTrue(out, out.startsWith(CodexRuntime.HEADER));
        assertTrue(out, out.contains("[mcp_servers.rea]\ncommand = \"npx\"\nargs = [\n  \"-y\",\n  \"rea-agents@6.8.0\",\n]\n"));
        assertFalse(out, out.contains("gpt-6-luna"));
        assertFalse(out, out.contains("[features]"));
        assertFalse(out, out.contains("[shell_environment_policy]"));
        assertEquals(out, CodexRuntime.migrateConfig(out));
        // 用户往 [features] 里加了自己的开关：表和用户的行留下，只去掉我们那一行
        String withFeature = V02.replace("shell_snapshot = false\n", "shell_snapshot = false\nmulti_agent = true\n");
        String o2 = CodexRuntime.migrateConfig(withFeature);
        assertTrue(o2, o2.contains("[features]\nmulti_agent = true\n"));
        assertFalse(o2, o2.contains("shell_snapshot"));
    }

    /** v0.3 文件里顶层多行字符串的内容像“model = ...”：不能被当成我们的键删掉。 */
    @Test
    public void migrationLeavesMultilineStringsAlone() {
        String v03 = "# 由口袋工坊生成：下面几行每次启动会重写；\n# 之后的内容（你或 Agent 加的配置，例如 [mcp_servers.*]）会原样保留。\n"
                + "cli_auth_credentials_store = \"file\"\nmodel = \"gpt-6-luna\"\n"
                + "developer_instructions = \"\"\"\n# 必须保留\nmodel = example\nweb_search = \"live\"\n\n\"\"\"\n"
                + "notes = '''\nsandbox_mode = x'''\n";
        String out = CodexRuntime.migrateConfig(v03);
        assertTrue(out, out.contains("developer_instructions = \"\"\"\n# 必须保留\nmodel = example\nweb_search = \"live\"\n\n\"\"\"\n"));
        assertTrue(out, out.contains("notes = '''\nsandbox_mode = x'''\n"));
        assertFalse(out, out.contains("gpt-6-luna"));
        assertFalse(out, out.contains("cli_auth_credentials_store"));
    }

    /** Astra 第三轮：多行基本字符串里转义的三引号不是结束符；跨行数组、内联表里的行也不能当成顶层键。 */
    @Test
    public void migrationUnderstandsEscapesArraysAndInlineTables() {
        String v03 = "# 由口袋工坊生成：下面几行每次启动会重写；\n# 之后的内容（你或 Agent 加的配置，例如 [mcp_servers.*]）会原样保留。\n"
                + "model = \"gpt-6-luna\"\n"
                + "developer_instructions = \"\"\"\n示例中的三引号：\\\"\"\"\nmodel = keep_this_instruction\n\"\"\"\n"
                + "notes = [\n  \"\"\"\n  sandbox_mode = keep_in_array\n  \"\"\",\n]\n"
                + "profile = {\n  web_search = \"keep_in_inline\" }\n"
                + "quoted = \"has \\\" quote and \\\"\\\"\\\" inside\"\nweb_search = \"live\"\n";
        String out = CodexRuntime.migrateConfig(v03);
        assertTrue(out, out.contains("示例中的三引号：\\\"\"\"\nmodel = keep_this_instruction\n\"\"\"\n"));
        assertTrue(out, out.contains("  sandbox_mode = keep_in_array\n"));
        assertTrue(out, out.contains("  web_search = \"keep_in_inline\" }\n"));
        assertTrue(out, out.contains("quoted = "));
        assertFalse(out, out.contains("gpt-6-luna"));
        assertFalse("单行字符串里的转义引号之后的顶层键照样迁移", out.contains("web_search = \"live\""));
        assertEquals(out, CodexRuntime.migrateConfig(out));
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
