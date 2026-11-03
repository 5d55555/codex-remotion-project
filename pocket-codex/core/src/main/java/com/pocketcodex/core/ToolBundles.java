package com.pocketcodex.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 开发工具的“工具集”：在基础工具包（{@link Toolkit#BASE}）之外按用途一组一组装。
 * 包名都在 Termux 官方仓库 termux-main（aarch64）的 Packages 索引里核对过（2026-11-03，补充项由 GPT-6.1-Sol 网页检索、再逐个核对）；
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
            new Bundle("recommended", "常用全家桶", "下面前五组一起装：代码与 Git、APK 构建与测试、逆向、剪辑与动画、中文字体", 602, 2253,
                    "gh", "git-lfs", "git-delta", "lazygit", "neovim", "vim", "tmux", "fd", "fzf", "bat", "eza", "shellcheck",
                    "shfmt", "yq", "httpie", "rsync", "7zip", "unrar", "tokei", "hyperfine", "entr", "watchexec", "uv", "pnpm",
                    "git-absorb", "git-sizer", "git-cliff", "git-extras", "tig", "gitui", "jujutsu", "difftastic", "ctags", "just",
                    "parallel", "moreutils", "sd", "miller", "dasel", "jo", "pup", "dos2unix", "inotify-tools", "pv", "ncdu",
                    "taplo", "openjdk-21", "aapt", "aapt2", "apksigner", "d8", "ecj", "android-tools", "xmlstarlet",
                    "libxml2-utils", "apktool", "jadx", "dex2jar", "radare2", "rizin", "binutils", "gdb", "lldb", "strace",
                    "ltrace", "xxd", "hexedit", "hexyl", "bvi", "binwalk", "yara", "capstone", "upx", "patchelf",
                    "termux-elf-cleaner", "protobuf", "flatbuffers", "dtc", "rhash", "hashdeep", "python-lxml",
                    "python-cryptography", "ffmpeg", "imagemagick", "graphicsmagick", "sox", "mediainfo", "gifsicle", "gifski",
                    "librsvg", "python-pillow", "python-numpy", "pycairo", "pango", "exiftool", "jpegoptim", "optipng", "pngquant",
                    "pngcrush", "libwebp", "libavif", "libheif-progs", "libvips", "potrace", "libjpeg-turbo-progs", "chafa",
                    "fontconfig-utils", "ttf-dejavu", "otf-noto-sans-cjk"),
            new Bundle("code", "代码与 Git 增强", "gh、lazygit、tig、delta、difftastic、jj、neovim、tmux、fd、fzf、bat、shellcheck、yq、miller、just、uv、pnpm……", 131, 598,
                    "gh", "git-lfs", "git-delta", "lazygit", "neovim", "vim", "tmux", "fd", "fzf", "bat", "eza", "shellcheck",
                    "shfmt", "yq", "httpie", "rsync", "7zip", "unrar", "tokei", "hyperfine", "entr", "watchexec", "uv", "pnpm",
                    "git-absorb", "git-sizer", "git-cliff", "git-extras", "tig", "gitui", "jujutsu", "difftastic", "ctags", "just",
                    "parallel", "moreutils", "sd", "miller", "dasel", "jo", "pup", "dos2unix", "inotify-tools", "pv", "ncdu",
                    "taplo"),
            new Bundle("apk", "APK 构建与测试", "OpenJDK 21、aapt/aapt2、d8、ecj、apksigner、adb（无线调试连本机）、xmlstarlet", 139, 370,
                    "openjdk-21", "aapt", "aapt2", "apksigner", "d8", "ecj", "android-tools", "xmlstarlet", "libxml2-utils"),
            new Bundle("reverse", "逆向分析", "apktool、jadx、dex2jar、radare2、rizin、binutils、gdb、lldb、strace、binwalk、yara、capstone、upx、patchelf、protoc", 335, 1192,
                    "apktool", "jadx", "dex2jar", "radare2", "rizin", "binutils", "gdb", "lldb", "strace", "ltrace", "xxd",
                    "hexedit", "hexyl", "bvi", "binwalk", "yara", "capstone", "upx", "patchelf", "termux-elf-cleaner", "protobuf",
                    "flatbuffers", "dtc", "rhash", "hashdeep", "xmlstarlet", "libxml2-utils", "python-lxml", "python-cryptography"),
            new Bundle("media", "剪辑与程序化动画", "ffmpeg、ImageMagick、GraphicsMagick、sox、gifski、rsvg、vips、exiftool、Pillow、pycairo、NumPy、WebP/AVIF/HEIF", 152, 699,
                    "ffmpeg", "imagemagick", "graphicsmagick", "sox", "mediainfo", "gifsicle", "gifski", "librsvg",
                    "python-pillow", "python-numpy", "pycairo", "pango", "exiftool", "jpegoptim", "optipng", "pngquant",
                    "pngcrush", "libwebp", "libavif", "libheif-progs", "libvips", "potrace", "libjpeg-turbo-progs", "chafa",
                    "fontconfig-utils", "ttf-dejavu"),
            new Bundle("cjk", "中文字体", "Noto Sans CJK：字幕、图片和动画里的中文", 26, 52,
                    "otf-noto-sans-cjk", "fontconfig-utils"),
            new Bundle("net", "网络调试与压测", "socat、nmap/ncat、wrk、vegeta、testssl.sh", 11, 49,
                    "socat", "nmap", "vegeta", "wrk", "testssl.sh"),
            new Bundle("native", "C/C++ 编译", "clang、cmake、ninja、pkg-config、ccache、bear、binutils（编译原生库、Node 原生模块）", 99, 606,
                    "clang", "cmake", "ninja", "binutils", "pkg-config", "ccache", "bear", "cscope", "patchelf"),
            new Bundle("gradle", "Gradle、Maven 与 Kotlin", "完整的 JVM 构建工具（很大；简单 APK 用“APK 构建与测试”就够）", 348, 575,
                    "gradle", "kotlin", "maven", "ant", "openjdk-21"),
            new Bundle("lang", "更多编程语言", "Rust、Go、PHP、Ruby、Perl、Lua、Bun、Deno（很大，按需装）", 334, 1707,
                    "rust", "golang", "php", "ruby", "perl", "lua54", "bun", "deno")));

    /** 装完以后必须能用的命令（自检：少一个就算没装好）。 */
    public static List<String> commands(String id) {
        switch (id) {
            case "recommended":
                return Arrays.asList("gh", "nvim", "java", "javac", "aapt2", "d8", "apksigner", "adb", "apktool", "jadx", "r2", "ffmpeg", "magick", "fc-list");
            case "code":
                return Arrays.asList("gh", "git-lfs", "delta", "nvim", "tmux", "fd", "fzf", "shellcheck", "yq", "uv", "pnpm");
            case "apk":
                return Arrays.asList("java", "javac", "keytool", "aapt2", "d8", "apksigner", "adb");
            case "reverse":
                return Arrays.asList("java", "apktool", "jadx", "r2", "objdump", "gdb", "strace", "binwalk", "yara");
            case "media":
                return Arrays.asList("ffmpeg", "ffprobe", "magick", "gm", "sox", "gifski", "rsvg-convert");
            case "cjk":
                return Arrays.asList("fc-list");
            case "native":
                return Arrays.asList("clang", "cmake", "ninja");
            case "gradle":
                return Arrays.asList("java", "gradle", "kotlinc");
            case "lang":
                return Arrays.asList("rustc", "cargo", "go", "php", "ruby", "perl", "bun", "deno");
            case "net":
                return Arrays.asList("socat", "nmap", "wrk");
            default:
                return Collections.emptyList();
        }
    }

    public static Bundle find(String id) {
        for (Bundle b : ALL) if (b.id.equals(id)) return b;
        return null;
    }
}
