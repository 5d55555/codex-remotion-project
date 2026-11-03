package com.pocketcodex.app;

/** 输入框里的附件：图片（localImage）、文件（以路径引用）、技能（skill）、插件（mention）、网页搜索（只是一个开关，没有路径）。 */
final class Attachment {
    enum Kind { IMAGE, FILE, SKILL, PLUGIN, WEB_SEARCH }

    /** 网页搜索标签的文字，也是消息里显示的名字。 */
    static final String WEB_NAME = "网页搜索";
    /** 带了“网页搜索”标签时，发给 Codex 的这一轮前面加的提示（用户看不到）。 */
    static final String WEB_HINT = "请先用网页搜索查找最新、可靠的资料再回答，回答末尾列出用到的来源链接。";

    final Kind kind;
    final String name;   // 显示名（技能/插件的显示名，文件名）
    final String id;     // 技能/插件的名字（发送用）
    final String path;   // 本地路径；技能是 SKILL.md 路径；插件是 plugin://…
    /** 聊天模式（不能跑命令）里的小文本文件：直接把内容附在消息里。 */
    String inline;

    Attachment(Kind kind, String name, String id, String path) {
        this.kind = kind;
        this.name = name;
        this.id = id;
        this.path = path;
    }
}
