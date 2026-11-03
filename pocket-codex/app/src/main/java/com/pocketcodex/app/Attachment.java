package com.pocketcodex.app;

/** 输入框里的附件：图片（localImage）、文件（以路径引用）、技能（skill）、插件（mention）。 */
final class Attachment {
    enum Kind { IMAGE, FILE, SKILL, PLUGIN }

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
