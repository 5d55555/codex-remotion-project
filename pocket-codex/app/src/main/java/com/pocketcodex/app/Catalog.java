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
                    return "OpenAI 文档";
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

    /** 内置图标：常见技能用对应的线条图标，GitHub 插件用它的标志，其他插件用 @ 图标。 */
    int iconRes() {
        String k = (id + " " + displayName).toLowerCase(java.util.Locale.ROOT);
        if (skill) {
            switch (id) {
                case "imagegen":
                    return R.drawable.ic_image_color;
                case "openai-docs":
                    return R.drawable.ic_book;
                case "review-agent":
                    return R.drawable.ic_review;
                case "skill-creator":
                    return R.drawable.ic_sparkle;
                case "skill-installer":
                    return R.drawable.ic_download;
                case "pocket-apk-build":
                    return R.drawable.ic_android_line;
                case "pocket-apk-test":
                    return R.drawable.ic_bug_line;
                case "pocket-reverse":
                    return R.drawable.ic_code_line;
                case "pocket-media":
                    return R.drawable.ic_film_line;
                case "pocket-toolkit":
                    return R.drawable.ic_box_line;
                default:
                    break;
            }
            if (k.contains("task") || k.contains("todo")) return R.drawable.ic_tasks;
            if (k.contains("draw") || k.contains("sketch")) return R.drawable.ic_scribble;
            return R.drawable.ic_sparkle;
        }
        if (k.contains("github")) return R.drawable.ic_github;
        return R.drawable.ic_plugin;
    }

    /** 彩色图标不着色。 */
    boolean colored() {
        return skill && "imagegen".equals(id);
    }

    /** 插件包里自带的位图图标（本地路径，PNG/JPG/WebP；SVG 用内置图标）。 */
    String localBitmapIcon() {
        if (iconUrl == null || iconUrl.startsWith("http")) return null;
        String l = iconUrl.toLowerCase(java.util.Locale.ROOT);
        if (!(l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".webp"))) return null;
        return new java.io.File(iconUrl).isFile() ? iconUrl : null;
    }

    Attachment toAttachment() {
        return new Attachment(skill ? Attachment.Kind.SKILL : Attachment.Kind.PLUGIN, label(), id, path);
    }
}
