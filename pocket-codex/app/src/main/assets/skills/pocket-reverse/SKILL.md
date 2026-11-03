---
name: pocket-reverse
description: 在手机上做软件逆向分析：APK（apktool 解包/回编、jadx 反编译、dex2jar）、ELF/.so（radare2、binutils、strings）、固件和未知文件（file、binwalk、yara、xxd），以及接入 leanrea 技能、REA、r2mcp、jadx-headless-mcp。用户要分析 APK/二进制、看某个 App 怎么实现、改 smali 重打包时使用。
metadata:
  short-description: APK 与二进制逆向
---

# 逆向分析（口袋工坊）

只分析用户有权分析的软件（自己的 App、开源/授权的样本、CTF 题目）。不要帮助破解付费验证、绕过 DRM、窃取凭据或制作恶意软件。

需要“逆向分析”工具集（apktool、jadx、dex2jar、radare2、binutils、gdb、strace、binwalk、yara、capstone），没有就请用户在 设置 → 开发工具 安装，或 `pkg install apktool jadx radare2 binutils`。jadx/apktool 依赖 OpenJDK，手机内存小：大 APK 给 jadx 设 `JAVA_OPTS=-Xmx1536m`，用 `-j 2` 限制线程。

## 先识别

```bash
file target; ls -la target; xxd target | head; strings -n 6 target | head -50
unzip -l app.apk | head -40           # APK 结构、有没有 lib/*.so
aapt2 dump badging app.apk 2>/dev/null | head   # 包名、版本、权限
```

## APK

```bash
apktool d -f app.apk -o app_src            # 资源、AndroidManifest.xml、smali
jadx -d app_java -j 2 --show-bad-code app.apk   # Java 源码（尽力反编译）
d2j-dex2jar app.apk -o app.jar             # 需要 jar 时
rg -n "http|https|api|key|token" app_java | head -100
apktool b app_src -o rebuilt.apk           # 回编（Termux 的 apktool 用手机版 aapt2）
```

回编后要 zipalign + apksigner 重新签名（见 `pocket-apk-build`），签名不同不能覆盖安装原 App。

## 原生库 / ELF

```bash
readelf -h -d lib.so; nm -D --defined-only lib.so | head
r2 -A -q -c 'afl~sym.;pdf @ sym.Java_com_example_Foo_bar' lib.so
rabin2 -I -i -E -z lib.so | head -100
```

## 固件 / 未知数据

`binwalk -e firmware.bin`（提取），`yara rules.yar target`，`xxd`/`hexedit` 看字节。
`rizin`（radare2 的分支，命令相近）、`lldb`/`gdb` 调试自己启动的程序、`upx -d` 解 UPX 壳、`patchelf --print-needed`、`protoc --decode_raw < blob` 看未知 protobuf、`dtc -I dtb -O dts` 转设备树。

写脚本分析时可以用纯 Python 的库（`pip install` 一般能直接装）：`pyelftools`（ELF/DWARF）、`pefile`（PE）、`construct` / `kaitaistruct`（声明式二进制解析）、`hachoir`。`androguard`、`lief` 有原生依赖，先确认 Termux 的 `python-lxml`、`python-cryptography` 已装，再按版本装，装不上就换上面的纯 Python 方案。

## 更强的工具（可选，先问用户）

- **leanrea**（技能，用本地命令行工具做多类型逆向）：`git clone --depth 1 https://github.com/aicodeking/leanrea /tmp/leanrea && cp -r /tmp/leanrea/leanrea ~/.agents/skills/`。它是按 macOS 写的：`brew`、`otool`、`xcrun`、`codesign` 在这里没有，换成 `pkg install`、`readelf`/`r2`。
- **REA（rea-agents 6.8.0，229 个 MCP 工具）**：请用户在 口袋工坊 → 设置 → 开发工具 → **REA 逆向助手** 一键安装（装在 `~/.npm-global`，`[mcp_servers.rea]` 直接用 node 运行 `scripts/rea.mjs mcp`，技能复制到 `~/.agents/skills/reverse-engineer-anything`）。**不要**用 `npx rea-agents setup`：它在安卓上装技能会因硬链接失败，而且 npx 版本的配置在手机上启动慢。
  - 工具名形如 `mcp__rea__binary_session`、`mcp__rea__xrefs`。工具很多时它们不会直接列出来：**先在工具目录里搜 “rea” 或具体工具名**，搜不到再说没有。
  - 真的没接上（新开对话也搜不到）：让用户到 开发工具 → REA 逆向助手 → 检查连接，会显示 Codex 报的原因；或者重新安装。
  - 在安卓上属实验性：Ghidra、IDA、Hopper、Kuna 这类反编译引擎手机上没有；APK 后端要完整 JDK 和 JADX headless；PTY、Frida 这些原生功能可能用不了。
- **r2mcp**（radare2 的 MCP）：`pkg install radare2 clang make pkg-config git && r2pm -Uci r2mcp`，在 `$CODEX_HOME/config.toml` 加 `[mcp_servers.r2]` `command="r2pm"` `args=["-r","r2mcp"]`，重启 Codex。
- **jadx-headless-mcp**：下载 release 的 `jadx-headless-mcp-0.8.0-all.jar`，配置 `command="java"`、`args=["-Xmx2g","-jar","/绝对路径/jadx-headless-mcp-0.8.0-all.jar","--threads","2"]`。
- Ghidra、Frida 在手机本机很难跑，建议用户在电脑上运行后端。

分析结论要附证据：文件路径、函数名、偏移、命令输出片段。
