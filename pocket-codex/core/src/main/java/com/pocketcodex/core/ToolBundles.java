package com.pocketcodex.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 开发工具的“工具集”：在基础工具包（{@link Toolkit#BASE}）之外按用途一组一组装。
 * 包名都在 Termux 官方仓库 termux-main（aarch64）的 Packages 索引里核对过（2026-11-03）；
 * 大小是当时按依赖闭包算出的下载量（不含基础包已有的部分），只用于提示。
 * 不包括 AI 训练、科学计算、生物、医疗方向的工具。
 */
public final class ToolBundles {
    private ToolBundles() {
    }

    public static final class Bundle {
        public final String id, title, summary;
        public final List<String> packages;
        /** 下载量（MB，约数）、安装后占用（MB，约数）。 */
        public final int downloadMb, installedMb;

        Bundle(String id, String title, String summary, int downloadMb, int installedMb, String... packages) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.downloadMb = downloadMb;
            this.installedMb = installedMb;
            this.packages = Collections.unmodifiableList(Arrays.asList(packages));
        }
    }

    public static final List<Bundle> ALL = Collections.unmodifiableList(Arrays.asList(
            new Bundle("recommended", "常用全家桶", "下面前五组一起装：代码与 Git、APK 构建与测试、逆向、剪辑与动画、中文字体", 469, 1509,
                    "gh", "git-lfs", "git-delta", "lazygit", "neovim", "vim", "tmux", "fd", "fzf", "bat", "eza", "shellcheck", "shfmt", "yq",
                    "httpie", "rsync", "7zip", "unrar", "tokei", "hyperfine", "entr", "watchexec", "uv", "pnpm",
                    "openjdk-21", "aapt", "aapt2", "apksigner", "d8", "ecj", "android-tools",
                    "apktool", "jadx", "dex2jar", "radare2", "binutils", "gdb", "strace", "ltrace", "xxd", "hexedit", "binwalk", "yara", "capstone",
                    "python-cryptography", "ffmpeg", "imagemagick", "graphicsmagick", "sox", "mediainfo", "gifsicle", "gifski", "librsvg",
                    "python-pillow", "python-numpy", "fontconfig-utils", "ttf-dejavu", "otf-noto-sans-cjk"),
            new Bundle("code", "代码与 Git 增强", "gh、git-lfs、delta、lazygit、neovim、tmux、fd、fzf、bat、shellcheck、yq、7zip、uv、pnpm……", 79, 328,
                    "gh", "git-lfs", "git-delta", "lazygit", "neovim", "vim", "tmux", "fd", "fzf", "bat", "eza", "shellcheck", "shfmt", "yq",
                    "httpie", "rsync", "7zip", "unrar", "tokei", "hyperfine", "entr", "watchexec", "uv", "pnpm"),
            new Bundle("apk", "APK 构建与测试", "OpenJDK 21、aapt/aapt2、d8、ecj、apksigner、adb（无线调试连本机）", 137, 363,
                    "openjdk-21", "aapt", "aapt2", "apksigner", "d8", "ecj", "android-tools"),
            new Bundle("reverse", "逆向分析", "apktool、jadx、dex2jar、radare2、binutils、gdb、strace、binwalk、yara、capstone", 234, 603,
                    "apktool", "jadx", "dex2jar", "radare2", "binutils", "gdb", "strace", "ltrace", "xxd", "hexedit", "binwalk", "yara", "capstone",
                    "python-cryptography"),
            new Bundle("media", "剪辑与程序化动画", "ffmpeg、ImageMagick、GraphicsMagick、sox、gifski、rsvg-convert、Pillow、NumPy、字体", 122, 565,
                    "ffmpeg", "imagemagick", "graphicsmagick", "sox", "mediainfo", "gifsicle", "gifski", "librsvg", "python-pillow", "python-numpy",
                    "fontconfig-utils", "ttf-dejavu"),
            new Bundle("cjk", "中文字体", "Noto Sans CJK：字幕、图片和动画里的中文", 23, 38, "otf-noto-sans-cjk", "fontconfig-utils"),
            new Bundle("native", "C/C++ 编译", "clang、cmake、ninja、binutils（编译原生库、Node 原生模块）", 94, 576,
                    "clang", "cmake", "ninja", "binutils"),
            new Bundle("gradle", "Gradle 与 Kotlin", "完整的 Gradle 构建（很大；简单 APK 用上面的“APK 构建与测试”就够）", 335, 559,
                    "gradle", "kotlin", "openjdk-21"),
            new Bundle("lang", "更多编程语言", "Rust、Go、PHP、Ruby、Perl、Lua、Bun、Deno（很大，按需装）", 334, 1706,
                    "rust", "golang", "php", "ruby", "perl", "lua54", "bun", "deno")));

    public static Bundle find(String id) {
        for (Bundle b : ALL) if (b.id.equals(id)) return b;
        return null;
    }
}
