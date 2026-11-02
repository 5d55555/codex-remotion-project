package com.pocketcodex.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个会话（thread）的界面状态：把 app-server 的通知归并成一串卡片。只在一个线程上调用（安卓上是主线程）。
 * <ul>
 *   <li>item/started 建卡片；各种 delta 往里追加；item/completed 用完整内容<b>替换</b>累积内容（不重复追加）；</li>
 *   <li>命令输出只在内存里留尾部 {@link #OUTPUT_TAIL} 字符；</li>
 *   <li>审批请求按请求 ID 记下，回复或 serverRequest/resolved 后移除；</li>
 *   <li>不认识的 item 类型显示成“此版本暂不支持的事件”，不崩溃。</li>
 * </ul>
 * apply 返回这次改了什么，界面据此只刷新变化的卡片。
 */
public final class Conversation {

    public static final int OUTPUT_TAIL = 32 * 1024;
    public static final int TEXT_LIMIT = 512 * 1024;

    public enum Kind { USER, AGENT, REASONING, COMMAND, FILE_CHANGE, TOOL, WEB_SEARCH, NOTICE, OTHER }

    public static final class Change {
        public final String path, kind, movePath, diff;
        public final int added, removed;

        public Change(String path, String kind, String movePath, String diff) {
            this.path = path;
            this.kind = kind;
            this.movePath = movePath;
            this.diff = diff == null ? "" : diff;
            int a = 0, r = 0;
            boolean unified = this.diff.startsWith("@@") || this.diff.contains("\n@@") || this.diff.startsWith("---");
            for (String l : this.diff.split("\n", -1)) {
                if (!unified) {
                    if (!l.isEmpty()) a++;
                    continue;
                }
                if (l.startsWith("+++") || l.startsWith("---")) continue;
                if (l.startsWith("+")) a++;
                else if (l.startsWith("-")) r++;
            }
            if (!unified && "delete".equals(kind)) {
                r = a;
                a = 0;
            }
            added = a;
            removed = r;
        }
    }

    public static final class PlanStep {
        public final String step, status;

        PlanStep(String step, String status) {
            this.step = step;
            this.status = status;
        }
    }

    public static final class Item {
        public final String id;
        public final Kind kind;
        public final String type;
        public String turnId;
        public final StringBuilder text = new StringBuilder();
        public String phase;                 // agentMessage：commentary / final_answer
        public String command, cwd;          // commandExecution
        public final StringBuilder output = new StringBuilder();
        public boolean outputTruncated;
        public Long exitCode, durationMs;
        public String status;                // inProgress / completed / failed / declined
        public List<Change> changes = Collections.emptyList();
        public String title;                 // 工具名、搜索词、提示的标题
        /** 用户消息带的本地图片路径（缩略图）和提及的技能/插件/文件名。 */
        public final List<String> images = new ArrayList<>();
        public final List<String> mentions = new ArrayList<>();
        public String level;                 // NOTICE：warning / retry / error / info
        public long startedAtMs, completedAtMs;
        public boolean completed;
        /** 每次内容变化加一（界面用来判断要不要重绑）。 */
        public int version;

        Item(String id, Kind kind, String type) {
            this.id = id;
            this.kind = kind;
            this.type = type;
        }

        public boolean running() {
            return !completed && (status == null || "inProgress".equals(status));
        }
    }

    /** 等用户回答的审批。 */
    public static final class Approval {
        public final Object requestId;
        public final String method, itemId, turnId;
        public final boolean command;        // true：命令；false：改文件
        public final String commandText, cwd, reason, grantRoot;
        /** 命令审批的 kind（command / writeStdin …），可能为 null。 */
        public final String kind;
        public final long startedAtMs;
        /** 用户已经点了，正在等服务端 serverRequest/resolved。 */
        public boolean submitting;
        public String decision;

        Approval(Object requestId, String method, Map<String, Object> p) {
            this.requestId = requestId;
            this.method = method;
            this.itemId = Json.str(p, "itemId");
            this.turnId = Json.str(p, "turnId");
            this.command = method.equals("item/commandExecution/requestApproval") || method.equals("execCommandApproval");
            this.commandText = Json.str(p, "command");
            this.cwd = Json.str(p, "cwd");
            this.reason = Json.str(p, "reason");
            this.grantRoot = Json.str(p, "grantRoot");
            this.kind = Json.str(p, "kind");
            this.startedAtMs = Json.num(p, "startedAtMs", System.currentTimeMillis());
        }

        public String key() {
            return String.valueOf(requestId);
        }
    }

    /** Agent 向用户提问（item/tool/requestUserInput）：按 question id 收集答案回复。 */
    public static final class InputRequest {
        public final Object requestId;
        public final String itemId, turnId;
        public final boolean blocking;
        public final List<Question> questions = new ArrayList<>();
        public boolean submitting;

        InputRequest(Object requestId, Map<String, Object> p) {
            this.requestId = requestId;
            this.itemId = Json.str(p, "itemId");
            this.turnId = Json.str(p, "turnId");
            this.blocking = Json.bool(p, "isBlocking", true);
            for (Object q : Json.arr(p, "questions")) {
                List<String[]> opts = new ArrayList<>();
                for (Object o : Json.arr(q, "options")) opts.add(new String[]{Json.str(o, "label", ""), Json.str(o, "description", "")});
                questions.add(new Question(Json.str(q, "id", ""), Json.str(q, "header", ""), Json.str(q, "question", ""), opts,
                        Json.bool(q, "isOther", false), Json.bool(q, "isSecret", false)));
            }
        }

        public String key() {
            return String.valueOf(requestId);
        }
    }

    public static final class Question {
        public final String id, header, question;
        public final List<String[]> options;
        public final boolean allowOther, secret;

        Question(String id, String header, String question, List<String[]> options, boolean allowOther, boolean secret) {
            this.id = id;
            this.header = header;
            this.question = question;
            this.options = options;
            this.allowOther = allowOther;
            this.secret = secret;
        }
    }

    private final Map<String, InputRequest> inputs = new LinkedHashMap<>();

    public List<InputRequest> pendingInputs() {
        return new ArrayList<>(inputs.values());
    }

    /** apply 的结果。 */
    public static final class Update {
        /** 列表结构变了（增删卡片）：整表刷新。 */
        public boolean structure;
        /** 内容变了的卡片下标（结构没变时有效）。 */
        public final List<Integer> changed = new ArrayList<>();
        /** 顶部状态（运行中、计划、令牌、额度）变了。 */
        public boolean status;
        /** 审批列表变了。 */
        public boolean approvals;
        /** 回合结束（完成、中断、失败）。 */
        public boolean turnEnded;

        public boolean any() {
            return structure || !changed.isEmpty() || status || approvals || turnEnded;
        }
    }

    public final String threadId;
    public final List<Item> items = new ArrayList<>();
    private final Map<String, Item> byId = new HashMap<>();
    private final Map<String, Approval> approvals = new LinkedHashMap<>();

    public String activeTurnId;
    /** 最后一个回合的状态：inProgress / completed / interrupted / failed。 */
    public String turnStatus;
    public final List<PlanStep> plan = new ArrayList<>();
    public String planExplanation;
    public String turnDiff;
    public long tokensUsed = -1, contextWindow = -1, lastInputTokens = -1;
    /** 主额度窗口（5 小时）和次额度窗口（每周）用了百分之多少，-1 表示不知道。 */
    public int primaryUsedPct = -1, secondaryUsedPct = -1;
    public long primaryResetsAt = -1, secondaryResetsAt = -1;
    public String threadName;
    private int noticeSeq;

    public Conversation(String threadId) {
        this.threadId = threadId;
    }

    public boolean running() {
        return "inProgress".equals(turnStatus);
    }

    public List<Approval> pendingApprovals() {
        return new ArrayList<>(approvals.values());
    }

    public Item item(String id) {
        return byId.get(id);
    }

    public int indexOf(Item it) {
        // 新卡片多在末尾：从后往前找更快
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i) == it) return i;
        return -1;
    }

    /* ---------------- 通知 ---------------- */

    public Update apply(String method, Map<String, Object> p) {
        Update u = new Update();
        String tid = Json.str(p, "threadId");
        if (tid != null && !tid.equals(threadId)) return u; // 别的会话的
        switch (method) {
            case "turn/started": {
                Map<String, Object> turn = Json.obj(p, "turn");
                activeTurnId = Json.str(turn, "id");
                turnStatus = "inProgress";
                plan.clear();
                planExplanation = null;
                turnDiff = null;
                u.status = true;
                break;
            }
            case "turn/completed": {
                Map<String, Object> turn = Json.obj(p, "turn");
                turnStatus = Json.str(turn, "status", "completed");
                // 回合里没收到 item/completed 的卡片（比如被中断）：用 turn.items 补全，剩下的标成结束
                for (Object o : Json.arr(turn, "items")) {
                    if (o instanceof Map) upsert(cast(o), Json.str(turn, "id"), true, u);
                }
                for (Item it : items) {
                    if (!it.completed && Json.str(turn, "id", "").equals(it.turnId)) {
                        it.completed = true;
                        if (it.status == null || "inProgress".equals(it.status)) it.status = "interrupted".equals(turnStatus) ? "interrupted" : it.status;
                        it.version++;
                        mark(u, it);
                    }
                }
                Map<String, Object> err = Json.obj(turn, "error");
                if ("failed".equals(turnStatus) && !err.isEmpty()) {
                    addNotice("error", "本轮失败", Json.str(err, "message", "未知错误"), Json.str(turn, "id"), u);
                } else if ("interrupted".equals(turnStatus)) {
                    addNotice("info", "已停止", "这一轮被停止了。已经执行的命令和改过的文件不会自动撤销。", Json.str(turn, "id"), u);
                }
                removeRetryNotices(u);
                activeTurnId = null;
                u.status = true;
                u.turnEnded = true;
                // 回合结束时还挂着的审批、提问（服务端已作废）
                if (clearApprovals()) u.approvals = true;
                break;
            }
            case "item/started":
                upsert(Json.obj(p, "item"), Json.str(p, "turnId"), false, u);
                break;
            case "item/completed":
                upsert(Json.obj(p, "item"), Json.str(p, "turnId"), true, u);
                break;
            case "item/agentMessage/delta":
            case "item/plan/delta": {
                Item it = ensure(Json.str(p, "itemId"), method.equals("item/plan/delta") ? Kind.OTHER : Kind.AGENT,
                        method.equals("item/plan/delta") ? "plan" : "agentMessage", Json.str(p, "turnId"), u);
                if (!it.completed) appendText(it, Json.str(p, "delta", ""));
                mark(u, it);
                break;
            }
            case "item/reasoning/summaryTextDelta": {
                Item it = ensure(Json.str(p, "itemId"), Kind.REASONING, "reasoning", Json.str(p, "turnId"), u);
                if (!it.completed) appendText(it, Json.str(p, "delta", ""));
                mark(u, it);
                break;
            }
            case "item/reasoning/summaryPartAdded": {
                Item it = ensure(Json.str(p, "itemId"), Kind.REASONING, "reasoning", Json.str(p, "turnId"), u);
                if (!it.completed && it.text.length() > 0) appendText(it, "\n\n");
                mark(u, it);
                break;
            }
            case "item/commandExecution/outputDelta": {
                Item it = ensure(Json.str(p, "itemId"), Kind.COMMAND, "commandExecution", Json.str(p, "turnId"), u);
                if (!it.completed) appendOutput(it, Json.str(p, "delta", ""));
                mark(u, it);
                break;
            }
            case "turn/plan/updated": {
                plan.clear();
                for (Object o : Json.arr(p, "plan")) {
                    if (o instanceof Map) plan.add(new PlanStep(Json.str(o, "step", ""), Json.str(o, "status", "pending")));
                }
                planExplanation = Json.str(p, "explanation");
                u.status = true;
                break;
            }
            case "turn/diff/updated":
                turnDiff = Json.str(p, "diff");
                u.status = true;
                break;
            case "thread/tokenUsage/updated": {
                Map<String, Object> tu = Json.obj(p, "tokenUsage");
                tokensUsed = Json.num(Json.obj(tu, "total"), "totalTokens", tokensUsed);
                lastInputTokens = Json.num(Json.obj(tu, "last"), "inputTokens", lastInputTokens);
                contextWindow = Json.num(tu, "modelContextWindow", contextWindow);
                u.status = true;
                break;
            }
            case "account/rateLimits/updated":
                applyRateLimits(Json.obj(p, "rateLimits"));
                u.status = true;
                break;
            case "error": {
                Map<String, Object> err = Json.obj(p, "error");
                boolean retry = Json.bool(p, "willRetry", false);
                String msg = Json.str(err, "message", "出错了");
                if (retry) {
                    // 重试提示只留最新一条
                    removeRetryNotices(u);
                    addNotice("retry", "网络不稳定，正在重试", msg, Json.str(p, "turnId"), u);
                } else {
                    addNotice("error", "出错了", msg + detailSuffix(err), Json.str(p, "turnId"), u);
                }
                break;
            }
            case "warning": {
                String msg = Json.str(p, "message", "");
                for (Item it : items) if (it.kind == Kind.NOTICE && "warning".equals(it.level) && msg.contentEquals(it.text)) return u;
                addNotice("warning", "提示", msg, activeTurnId, u);
                break;
            }
            case "serverRequest/resolved": {
                Object rid = p.get("requestId");
                if (resolve(rid instanceof Number ? ((Number) rid).longValue() : rid)) u.approvals = true;
                break;
            }
            case "thread/name/updated":
                threadName = Json.str(p, "threadName");
                u.status = true;
                break;
            default:
                break;
        }
        return u;
    }

    private static String detailSuffix(Map<String, Object> err) {
        String d = Json.str(err, "additionalDetails");
        return d == null || d.isEmpty() ? "" : "（" + d + "）";
    }

    private void applyRateLimits(Map<String, Object> rl) {
        Map<String, Object> pw = Json.obj(rl, "primary"), sw = Json.obj(rl, "secondary");
        if (!pw.isEmpty()) {
            primaryUsedPct = (int) Json.num(pw, "usedPercent", -1);
            primaryResetsAt = Json.num(pw, "resetsAt", -1);
        }
        if (!sw.isEmpty()) {
            secondaryUsedPct = (int) Json.num(sw, "usedPercent", -1);
            secondaryResetsAt = Json.num(sw, "resetsAt", -1);
        }
    }

    /** account/rateLimits/read 的结果。 */
    public void setRateLimits(Map<String, Object> response) {
        applyRateLimits(Json.obj(response, "rateLimits"));
    }

    /* ---------------- 审批 ---------------- */

    /** 是不是我们会处理的请求（审批或提问）。 */
    public static boolean handles(String method) {
        return method.equals("item/commandExecution/requestApproval") || method.equals("item/fileChange/requestApproval")
                || method.equals("item/tool/requestUserInput");
    }

    /** 服务端请求：认识的审批记下来返回；提问记到 pendingInputs 里（返回 null）；不认识的也返回 null（调用方先用 handles 判断）。 */
    public Approval onServerRequest(Object id, String method, Map<String, Object> p) {
        if (method.equals("item/tool/requestUserInput")) {
            InputRequest r = new InputRequest(id, p);
            inputs.put(r.key(), r);
            return null;
        }
        if (!method.equals("item/commandExecution/requestApproval") && !method.equals("item/fileChange/requestApproval")) return null;
        Approval a = new Approval(id, method, p);
        approvals.put(a.key(), a);
        return a;
    }

    /** app-server 重启了：旧连接上的审批全部作废（不能在新连接上回复旧请求）。 */
    public boolean clearApprovals() {
        boolean had = !approvals.isEmpty() || !inputs.isEmpty();
        approvals.clear();
        inputs.clear();
        return had;
    }

    /** 用户回答了（或者回复失败也算处理掉）。 */
    public boolean resolve(Object requestId) {
        boolean a = approvals.remove(String.valueOf(requestId)) != null;
        boolean b = inputs.remove(String.valueOf(requestId)) != null;
        return a || b;
    }

    /* ---------------- 历史 ---------------- */

    /** thread/resume 或 thread/turns/list 拿到的回合（itemsView = full），按时间顺序装进来。 */
    public Update loadTurns(List<Object> turns) {
        Update u = new Update();
        for (Object t : turns) {
            if (!(t instanceof Map)) continue;
            Map<String, Object> turn = cast(t);
            String turnId = Json.str(turn, "id");
            for (Object o : Json.arr(turn, "items")) if (o instanceof Map) upsert(cast(o), turnId, true, u);
            String st = Json.str(turn, "status");
            if ("inProgress".equals(st)) {
                activeTurnId = turnId;
                turnStatus = st;
            } else if (st != null) {
                turnStatus = st;
                Map<String, Object> err = Json.obj(turn, "error");
                if ("failed".equals(st) && !err.isEmpty()) addNotice("error", "本轮失败", Json.str(err, "message", ""), turnId, u);
            }
        }
        u.structure = true;
        u.status = true;
        return u;
    }

    /** 更早的历史（thread/turns/list 的下一页）：插到最前面，已经有的不重复。 */
    public Update prependTurns(List<Object> turnsOldestFirst) {
        Conversation tmp = new Conversation(threadId);
        tmp.loadTurns(turnsOldestFirst);
        List<Item> add = new ArrayList<>();
        for (Item it : tmp.items) if (!byId.containsKey(it.id)) add.add(it);
        items.addAll(0, add);
        for (Item it : add) byId.put(it.id, it);
        Update u = new Update();
        u.structure = true;
        return u;
    }

    /** 还有更早的历史可以加载（thread/turns/list 的 nextCursor）。 */
    public String olderCursor;

    /* ---------------- 内部 ---------------- */

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    private static Kind kindOf(String type) {
        if (type == null) return Kind.OTHER;
        switch (type) {
            case "userMessage":
                return Kind.USER;
            case "agentMessage":
                return Kind.AGENT;
            case "reasoning":
                return Kind.REASONING;
            case "commandExecution":
                return Kind.COMMAND;
            case "fileChange":
                return Kind.FILE_CHANGE;
            case "mcpToolCall":
            case "dynamicToolCall":
            case "collabAgentToolCall":
            case "imageGeneration":
            case "imageView":
                return Kind.TOOL;
            case "webSearch":
                return Kind.WEB_SEARCH;
            case "contextCompaction":
            case "enteredReviewMode":
            case "exitedReviewMode":
                return Kind.NOTICE;
            default:
                return Kind.OTHER;
        }
    }

    private Item ensure(String id, Kind kind, String type, String turnId, Update u) {
        Item it = byId.get(id);
        if (it != null) return it;
        it = new Item(id == null ? "anon-" + items.size() : id, kind, type);
        it.turnId = turnId;
        it.startedAtMs = System.currentTimeMillis();
        add(it, u);
        return it;
    }

    private void add(Item it, Update u) {
        items.add(it);
        byId.put(it.id, it);
        u.structure = true;
    }

    private void mark(Update u, Item it) {
        if (u.structure) return;
        int i = indexOf(it);
        if (i >= 0 && !u.changed.contains(i)) u.changed.add(i);
    }

    private void upsert(Map<String, Object> m, String turnId, boolean completed, Update u) {
        String id = Json.str(m, "id");
        String type = Json.str(m, "type");
        Item it = byId.get(id);
        String clientId = Json.str(m, "clientId");
        if (it == null && "userMessage".equals(type) && clientId != null && byId.containsKey("local-" + clientId)) {
            adoptLocalUserMessage(clientId, id); // 本地先显示的那条换成服务端的，不重复
            it = byId.get(id);
        }
        if (it == null) {
            it = new Item(id == null ? "anon-" + items.size() : id, kindOf(type), type == null ? "unknown" : type);
            add(it, u);
        }
        it.turnId = turnId != null ? turnId : it.turnId;
        long st = Json.num(m, "startedAtMs", 0);
        if (st > 0) it.startedAtMs = st;
        long ct = Json.num(m, "completedAtMs", 0);
        if (ct > 0) it.completedAtMs = ct;
        if (completed) it.completed = true;
        switch (it.kind) {
            case USER: {
                StringBuilder b = new StringBuilder();
                it.images.clear();
                it.mentions.clear();
                for (Object c : Json.arr(m, "content")) {
                    String t = Json.str(c, "type", "");
                    if (t.equals("text")) b.append(Json.str(c, "text", ""));
                    else if (t.equals("localImage")) it.images.add(Json.str(c, "path", ""));
                    else if (t.equals("image")) it.images.add(Json.str(c, "url", ""));
                    else if (t.equals("mention") || t.equals("skill")) it.mentions.add(Json.str(c, "name", ""));
                }
                setText(it, b.toString());
                break;
            }
            case AGENT: {
                it.phase = Json.str(m, "phase", it.phase);
                String t = Json.str(m, "text");
                // 完整内容替换累积的 delta；开始时 text 是空串，不能把已经收到的 delta 清掉
                if (t != null && (completed || t.length() >= it.text.length())) setText(it, t);
                break;
            }
            case REASONING: {
                List<Object> summary = Json.arr(m, "summary");
                if (!summary.isEmpty() || completed) {
                    StringBuilder b = new StringBuilder();
                    for (Object s : summary) {
                        String piece = s instanceof String ? (String) s : Json.str(s, "text", "");
                        if (piece.isEmpty()) continue;
                        if (b.length() > 0) b.append("\n\n");
                        b.append(piece);
                    }
                    if (b.length() > 0 || completed && it.text.length() == 0) setText(it, b.toString());
                }
                break;
            }
            case COMMAND: {
                it.command = Json.str(m, "command", it.command);
                it.cwd = Json.str(m, "cwd", it.cwd);
                it.status = Json.str(m, "status", it.status);
                if (m.get("exitCode") instanceof Number) it.exitCode = Json.num(m, "exitCode", 0);
                if (m.get("durationMs") instanceof Number) it.durationMs = Json.num(m, "durationMs", 0);
                String agg = Json.str(m, "aggregatedOutput");
                if (agg != null && (completed || agg.length() >= it.output.length())) {
                    it.output.setLength(0);
                    it.outputTruncated = false;
                    appendOutput(it, agg);
                }
                break;
            }
            case FILE_CHANGE: {
                it.status = Json.str(m, "status", it.status);
                List<Change> cs = new ArrayList<>();
                for (Object c : Json.arr(m, "changes")) {
                    Map<String, Object> kind = Json.obj(c, "kind");
                    cs.add(new Change(Json.str(c, "path", "?"), Json.str(kind, "type", "update"), Json.str(kind, "move_path"), Json.str(c, "diff")));
                }
                if (!cs.isEmpty() || completed) it.changes = cs;
                break;
            }
            case TOOL: {
                it.status = Json.str(m, "status", it.status);
                String server = Json.str(m, "server"), tool = Json.str(m, "tool");
                if (tool != null) it.title = server != null ? server + " · " + tool : tool;
                else if (it.type.equals("imageGeneration")) it.title = "生成图片";
                else if (it.type.equals("imageView")) it.title = "查看图片 " + Json.str(m, "path", "");
                else if (it.type.equals("collabAgentToolCall")) it.title = "子 Agent";
                Object err = m.get("error");
                if (err instanceof Map) setText(it, Json.str(err, "message", ""));
                break;
            }
            case WEB_SEARCH: {
                it.title = Json.str(m, "query", it.title);
                it.status = completed ? "completed" : "inProgress";
                break;
            }
            case NOTICE: {
                it.level = "info";
                it.title = it.type.equals("contextCompaction") ? "上下文已压缩" : it.type.equals("enteredReviewMode") ? "进入审查模式" : "退出审查模式";
                break;
            }
            default:
                it.title = it.type;
                break;
        }
        it.version++;
        mark(u, it);
    }

    private static void setText(Item it, String s) {
        it.text.setLength(0);
        it.text.append(s.length() > TEXT_LIMIT ? s.substring(0, TEXT_LIMIT) + "\n…（太长，已截断）" : s);
        it.version++;
    }

    private static void appendText(Item it, String d) {
        if (it.text.length() + d.length() <= TEXT_LIMIT) it.text.append(d);
        it.version++;
    }

    private static void appendOutput(Item it, String d) {
        it.output.append(d);
        if (it.output.length() > OUTPUT_TAIL) {
            int cut = it.output.length() - OUTPUT_TAIL;
            // 从下一个换行开始保留，避免半行
            int nl = it.output.indexOf("\n", cut);
            it.output.delete(0, nl >= 0 && nl - cut < 512 ? nl + 1 : cut);
            it.outputTruncated = true;
        }
        it.version++;
    }

    private void addNotice(String level, String title, String text, String turnId, Update u) {
        Item it = new Item("notice-" + (++noticeSeq), Kind.NOTICE, "notice");
        it.level = level;
        it.title = title;
        it.turnId = turnId;
        it.completed = true;
        it.startedAtMs = System.currentTimeMillis();
        setText(it, text == null ? "" : text);
        add(it, u);
    }

    private void removeRetryNotices(Update u) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (it.kind == Kind.NOTICE && "retry".equals(it.level)) {
                items.remove(i);
                byId.remove(it.id);
                u.structure = true;
            }
        }
    }

    /** 用户刚发的消息先放上去（服务端的 userMessage item 到了会按 clientId 合并）。 */
    public Item addLocalUserMessage(String clientId, String text) {
        return addLocalUserMessage(clientId, text, null, null);
    }

    public Item addLocalUserMessage(String clientId, String text, List<String> images, List<String> mentions) {
        Item it = new Item("local-" + clientId, Kind.USER, "userMessage");
        it.completed = true;
        it.startedAtMs = System.currentTimeMillis();
        if (images != null) it.images.addAll(images);
        if (mentions != null) it.mentions.addAll(mentions);
        setText(it, text);
        items.add(it);
        byId.put(it.id, it);
        return it;
    }

    /** 服务端的 userMessage 到了：如果带着我们的 clientId，替换本地那条，不重复显示。 */
    public void adoptLocalUserMessage(String clientId, String serverItemId) {
        Item local = byId.remove("local-" + clientId);
        if (local == null || serverItemId == null) return;
        int i = indexOf(local);
        Item it = new Item(serverItemId, Kind.USER, "userMessage");
        it.text.append(local.text);
        it.images.addAll(local.images);
        it.mentions.addAll(local.mentions);
        it.completed = true;
        it.startedAtMs = local.startedAtMs;
        if (i >= 0) items.set(i, it);
        byId.put(serverItemId, it);
    }
}
