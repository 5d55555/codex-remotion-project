package com.pocketcodex.app;

/** “@”和“插件”里能选的东西：来自 skills/list（启用的技能）和 plugin/list（已安装且启用的插件）。 */
final class Catalog {
    final String id, displayName, description, iconUrl, brandColor, path;
    final boolean skill;

    Catalog(boolean skill, String id, String displayName, String description, String iconUrl, String brandColor, String path) {
        this.skill = skill;
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.iconUrl = iconUrl;
        this.brandColor = brandColor;
        this.path = path;
    }

    /** 常见技能的中文名（对应 ChatGPT 里的叫法），其他用服务端给的显示名。 */
    String label() {
        if (skill) {
            switch (id) {
                case "imagegen":
                    return "创建图像";
                case "openai-docs":
                    return "查 OpenAI 文档";
                case "review-agent":
                    return "代码审查";
                case "skill-creator":
                    return "创建技能";
                case "skill-installer":
                    return "安装技能";
                default:
                    break;
            }
        }
        return displayName == null || displayName.isEmpty() ? id : displayName;
    }

    Attachment toAttachment() {
        return new Attachment(skill ? Attachment.Kind.SKILL : Attachment.Kind.PLUGIN, label(), id, path);
    }
}
