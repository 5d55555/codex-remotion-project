package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** 用真实录下来的 app-server 消息（codex 0.166，chroot 模拟安卓）回放，检查归并结果。 */
public class ConversationTest {

    private static List<Map<String, Object>> load(String res) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ConversationTest.class.getResourceAsStream(res), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) if (!l.trim().isEmpty()) out.add(Json.parseObject(l));
        }
        return out;
    }

    private static String threadOf(List<Map<String, Object>> msgs) {
        for (Map<String, Object> m : msgs) {
            if ("thread/started".equals(m.get("method"))) return Json.str(Json.obj(Json.obj(m, "params"), "thread"), "id");
        }
        throw new AssertionError("没有 thread/started");
    }

    private static Conversation replay(List<Map<String, Object>> msgs, List<Conversation.Approval> seen) {
        Conversation c = new Conversation(threadOf(msgs));
        for (Map<String, Object> m : msgs) {
            String method = Json.str(m, "method");
            if (m.containsKey("id")) {
                Conversation.Approval a = c.onServerRequest(m.get("id"), method, Json.obj(m, "params"));
                if (a != null) seen.add(a);
            } else {
                c.apply(method, Json.obj(m, "params"));
            }
        }
        return c;
    }

    @Test
    public void replaysApprovalsTurn() throws Exception {
        List<Conversation.Approval> seen = new ArrayList<>();
        Conversation c = replay(load("/replay_approvals.jsonl"), seen);
        assertEquals(5, seen.size());
        assertTrue(seen.get(0).command);
        assertEquals("/data/data/app/files/bin/bash -lc 'echo $0; echo $PATH; cat /proc/version | head -c 40'", seen.get(0).commandText);
        assertFalse("第三个是改文件", seen.get(2).command);
        assertTrue("serverRequest/resolved 之后不再挂着", c.pendingApprovals().isEmpty());
        assertEquals("completed", c.turnStatus);
        assertFalse(c.running());

        int commands = 0, files = 0, finals = 0;
        for (Conversation.Item it : c.items) {
            switch (it.kind) {
                case COMMAND:
                    commands++;
                    assertEquals("completed", it.status);
                    assertEquals(Long.valueOf(0), it.exitCode);
                    assertFalse(it.running());
                    break;
                case FILE_CHANGE:
                    files++;
                    assertEquals(1, it.changes.size());
                    assertEquals("/data/data/app/files/ws/b.txt", it.changes.get(0).path);
                    assertEquals("add", it.changes.get(0).kind);
                    assertEquals(1, it.changes.get(0).added);
                    break;
                case AGENT:
                    if ("final_answer".equals(it.phase)) {
                        finals++;
                        // delta 累积后被 item/completed 的完整文本替换，不会重复
                        assertEquals("Completed all five steps in order; `ls` shows only `b.txt`.", it.text.toString());
                    }
                    break;
                default:
                    break;
            }
        }
        assertEquals(4, commands);
        assertEquals(1, files);
        assertEquals(1, finals);
        Conversation.Item ls = null;
        for (Conversation.Item it : c.items) if (it.kind == Conversation.Kind.COMMAND && it.command.endsWith("-lc ls")) ls = it;
        assertNotNull(ls);
        assertEquals("b.txt\n", ls.output.toString());
        assertTrue(c.tokensUsed > 0);
        assertTrue(c.primaryUsedPct >= 0);
        assertNotNull(c.turnDiff);
    }

    @Test
    public void replaysBasicTurnAndKeepsUserMessage() throws Exception {
        Conversation c = replay(load("/replay_basic.jsonl"), new ArrayList<Conversation.Approval>());
        assertEquals(Conversation.Kind.USER, c.items.get(0).kind);
        assertTrue(c.items.get(0).text.toString().startsWith("Create a file hello.txt"));
        assertEquals("completed", c.turnStatus);
    }

    /** 本地先显示的用户消息，服务端带着同一个 clientId 回来时替换，不重复。 */
    @Test
    public void localUserMessageIsAdopted() {
        Conversation c = new Conversation("t1");
        c.addLocalUserMessage("u1", "hi");
        Conversation.Update u = c.apply("item/started", Json.parseObject(
                "{\"threadId\":\"t1\",\"turnId\":\"r1\",\"item\":{\"type\":\"userMessage\",\"id\":\"srv-1\",\"clientId\":\"u1\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}}"));
        assertEquals(1, c.items.size());
        assertEquals("srv-1", c.items.get(0).id);
        assertTrue(u.any());
    }

    /** delta 先到、item/started 后到也不丢；输出只保留尾部；重试提示只留一条；失败回合有提示；别的会话的通知忽略。 */
    @Test
    public void robustness() {
        Conversation c = new Conversation("t1");
        c.apply("turn/started", Json.parseObject("{\"threadId\":\"t1\",\"turn\":{\"id\":\"r1\",\"status\":\"inProgress\",\"items\":[]}}"));
        assertTrue(c.running());
        c.apply("item/agentMessage/delta", Json.parseObject("{\"threadId\":\"t1\",\"turnId\":\"r1\",\"itemId\":\"m1\",\"delta\":\"Hel\"}"));
        c.apply("item/started", Json.parseObject("{\"threadId\":\"t1\",\"turnId\":\"r1\",\"item\":{\"type\":\"agentMessage\",\"id\":\"m1\",\"text\":\"\",\"phase\":\"final_answer\"}}"));
        c.apply("item/agentMessage/delta", Json.parseObject("{\"threadId\":\"t1\",\"turnId\":\"r1\",\"itemId\":\"m1\",\"delta\":\"lo\"}"));
        assertEquals("Hello", c.item("m1").text.toString());

        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 5000; i++) big.append("line ").append(i).append('\n');
        c.apply("item/started", Json.parseObject("{\"threadId\":\"t1\",\"turnId\":\"r1\",\"item\":{\"type\":\"commandExecution\",\"id\":\"c1\",\"command\":\"seq\",\"status\":\"inProgress\"}}"));
        Map<String, Object> d = Json.map("threadId", "t1", "turnId", "r1", "itemId", "c1", "delta", big.toString());
        c.apply("item/commandExecution/outputDelta", d);
        Conversation.Item cmd = c.item("c1");
        assertTrue(cmd.output.length() <= Conversation.OUTPUT_TAIL);
        assertTrue(cmd.outputTruncated);
        assertTrue(cmd.output.toString().endsWith("line 4999\n"));
        assertTrue(cmd.output.toString().startsWith("line "));

        for (int i = 0; i < 3; i++) {
            c.apply("error", Json.parseObject("{\"threadId\":\"t1\",\"turnId\":\"r1\",\"willRetry\":true,\"error\":{\"message\":\"Reconnecting... " + i + "/5\"}}"));
        }
        int retries = 0;
        for (Conversation.Item it : c.items) if ("retry".equals(it.level)) retries++;
        assertEquals(1, retries);

        assertFalse(c.apply("item/started", Json.parseObject("{\"threadId\":\"other\",\"turnId\":\"x\",\"item\":{\"type\":\"agentMessage\",\"id\":\"zz\",\"text\":\"\"}}")).any());
        assertNull(c.item("zz"));

        c.apply("item/started", Json.parseObject("{\"threadId\":\"t1\",\"turnId\":\"r1\",\"item\":{\"type\":\"somethingNew\",\"id\":\"n1\"}}"));
        assertEquals(Conversation.Kind.OTHER, c.item("n1").kind);

        c.apply("turn/completed", Json.parseObject("{\"threadId\":\"t1\",\"turn\":{\"id\":\"r1\",\"status\":\"failed\",\"items\":[],\"error\":{\"message\":\"usage limit\"}}}"));
        assertFalse(c.running());
        assertFalse("命令卡片不再显示运行中", c.item("c1").running());
        Conversation.Item last = c.items.get(c.items.size() - 1);
        assertEquals("error", last.level);
        assertEquals("usage limit", last.text.toString());
        for (Conversation.Item it : c.items) assertFalse("回合结束后重试提示清掉", "retry".equals(it.level));
    }

    @Test
    public void diffStats() {
        Conversation.Change u = new Conversation.Change("a.txt", "update", null, "--- a/a.txt\n+++ b/a.txt\n@@ -1,2 +1,2 @@\n-old\n+new\n+more\n ctx\n");
        assertEquals(2, u.added);
        assertEquals(1, u.removed);
        Conversation.Change add = new Conversation.Change("b.txt", "add", null, "x\ny\n");
        assertEquals(2, add.added);
        Conversation.Change del = new Conversation.Change("c.txt", "delete", null, "x\ny\nz\n");
        assertEquals(3, del.removed);
    }
}
