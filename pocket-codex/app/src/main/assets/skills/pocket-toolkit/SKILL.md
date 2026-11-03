---
name: pocket-toolkit
description: 口袋工坊（安卓手机上的 Codex）里的命令行环境说明：有哪些工具、缺工具怎么装（pkg install、工具集）、共享存储的限制、MCP 服务和第三方技能怎么加。用户问“能不能用某个命令/软件”、命令找不到、要装新工具或配置 MCP 时使用。
metadata:
  short-description: 手机上的开发环境与装工具
---

# 口袋工坊的开发环境

你运行在安卓手机上的口袋工坊 App 里（无 root、无 sudo）。工具来自 Termux 官方仓库，装在 App 私有目录的 `$PREFIX`（不是 `/usr`）。

## 先确认有什么

```bash
command -v node npm git python3 java ffmpeg adb apktool jadx r2 gh 2>/dev/null
pkg list-installed
```

## 缺工具：用 pkg 装，不要手动下载二进制

```bash
pkg search <关键词>
pkg install <包名>        # 例：pkg install ffmpeg apktool jadx gh
pkg uninstall <包名>
```

`apt` / `apt-get` 是 `pkg` 的别名。普通 Linux（glibc）二进制、manylinux 的 pip wheel、带原生模块的 npm 包在安卓 bionic 上常常跑不了：优先 Termux 包、纯 Python / 纯 JS 的替代，或者在本机编译（需要“C/C++ 编译”工具集：clang、cmake）。

用户可以在 设置 → 开发工具 里一键装“工具集”：代码与 Git 增强、APK 构建与测试、逆向分析、剪辑与程序化动画、中文字体、C/C++ 编译、Gradle 与 Kotlin、更多编程语言。缺的整组工具可以建议用户去那里装。

## 位置和限制

- 工作区可能在 App 私有目录（能执行、能建符号链接），也可能在手机共享存储 `/storage/emulated/0/...`（**不能执行文件、不能建符号链接**）。在共享存储里：`npm install --no-bin-links`，用 `npx`/`node` 跑工具，git 设 `core.filemode=false`；重活放到私有工作区做。
- `$HOME` 是 App 私有目录；`~/.agents/skills/` 放技能，`$CODEX_HOME/config.toml` 是 Codex 配置。
- 长时间运行的服务用 `nohup … > log 2>&1 &`，告诉用户在手机浏览器打开 `http://127.0.0.1:端口`。
- 安卓会限制后台子进程（Android 12+ 的 phantom process）；别同时开太多进程。

## MCP 服务

在 `$CODEX_HOME/config.toml` 末尾加 `[mcp_servers.<名字>]`（`command`、`args`），口袋工坊每次启动只重写文件开头那几行，**你加的表会保留**。改完要重启 Codex 才生效：请用户在 设置 → Codex → 重启 Codex（或退出 App 再进）。例：

```toml
[mcp_servers.r2]
command = "r2pm"
args = ["-r", "r2mcp"]
```

## 第三方技能

技能放 `~/.agents/skills/<名字>/SKILL.md`。从 GitHub 装（先征得用户同意，注意许可证）：

```bash
git clone --depth 1 https://github.com/aicodeking/leanrea /tmp/leanrea && cp -r /tmp/leanrea/leanrea ~/.agents/skills/
```

也可以用内置的 `$skill-installer`。装好后开一个新对话才会加载。
