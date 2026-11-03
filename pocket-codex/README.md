# 口袋工坊（Android）

在手机上直接用 Codex：App 先把官方 Codex CLI 安装到手机里，用设备码登录 ChatGPT 账号，然后由本机的 Codex Agent 修改手机上的文件、运行命令。不经过 ChatGPT 网页/App 里的云端 Codex。

独立开发的客户端，使用官方发布的 Codex CLI，与 OpenAI 无官方隶属关系。

## v0.2 更新（2026-11-03）

- **界面按 ChatGPT 手机端重做**：白底、近黑主色、圆形顶栏按钮；新对话顶部“聊天 / 工作”分段；底部大圆角输入卡片——“＋”、模型标签（如“6.1 Sol 中”）、麦克风、蓝色圆形发送键（空输入时是“语音输入并发送”，运行中变成黑色停止键）。
- **“＋”菜单**：拍照、照片（可多选）、文件、插件。照片缩到长边 2048、按 EXIF 转正后作为 `localImage` 发给 Codex；文件复制到应用私有的附件目录，工作模式让 Codex 按路径读，聊天模式附带 64 KB 以内的文本内容。
- **输入“@”**：在输入框上方列出技能和插件（`skills/list`、`plugin/list`：创建图像、代码审查、OpenAI 文档、GitHub……）和工作区里的文件（`fuzzyFileSearch`），选中后变成输入框里的标签。
- **模型面板**（点模型标签，替换掉原来的单选对话框）：模型 / 推理 / 速度 / 权限四行，点一行在上方弹出下拉卡片，底部黑色“完成”。列表来自账号的 `model/list`；速度档用模型的 `serviceTiers`（快速 = 用量更高），模型不支持时自动回到标准。
- **聊天模式**：会话放在空目录，命令和改文件请求自动拒绝；**工作模式**：在工作区里读写文件、跑命令。
- **开发工具（Node.js · Git · Python）**：设置 → 开发工具 显示安装进度、各工具版本，可安装 / 重装 / 取消 / 换软件源。来自 Termux 官方仓库（含国内镜像），InRelease 签名校验；Codex 的 PATH 里有 node、npm、npx、git、python、pip、bash、rg、curl、jq 和 `pkg install`。
- **终端**：直接在手机上运行命令，环境和 Codex 一样，**不经过模型、不消耗额度**；记住 cd 后的目录，停止时连子进程一起结束。**常用命令**面板列出 Node.js、npm、Git、Python、pkg 的常用写法，点一条放进输入框并选中要改的部分。

## 怎么用

1. 安装 APK（`口袋工坊-v0.2.apk`）。安卓可能提示“此应用专为旧版 Android 打造”或 Play Protect 警告：这是 targetSdk 28 的缘故（见下面“为什么是 targetSdk 28”），选“仍然安装”。
2. **安装 Codex CLI**：点“下载并安装”。从 npm 官方仓库（或国内镜像 npmmirror，安装包会和官方摘要比对）下载 `@openai/codex` 的 Linux 平台包（arm64 约 164 MB），断点续传、sha512 校验，只解出 `codex` 和 `codex-code-mode-host` 两个程序（约 350 MB），先在手机上自检 `codex --version`，通过才启用。
3. **登录**：App 在后台运行 `codex login --device-auth`，输出重定向到文件（`<应用目录>/runtime/logs/device-auth.log`），从里面解析出网址和一次性代码显示出来。点“复制代码并打开浏览器”，在 `auth.openai.com/codex/device` 输入代码、登录 ChatGPT。代码有效期 15 分钟，**过期前 20 秒自动换新代码**，页面倒计时环会提示；网络错误自动退避重试。也可以改用 API 密钥登录。
   - 如果网页提示设备码授权没开启：ChatGPT 网页版 → 设置 → 安全，开启 Codex 的设备码授权（团队账号可能要管理员允许）。
4. **选择工作区**：
   - 应用内工作区（默认，不需要权限）；
   - 或“手机存储里的文件夹”：授予存储权限后，在 App 自带的文件夹浏览器里选一个项目文件夹（会先做写入—读回—删除的探测）。Codex 直接改原文件，文件管理器、编辑器里马上能看到。
5. **对话**：输入要做的事。Agent 的回答流式显示（Markdown），执行的每条命令、每次改文件都是一张卡片（命令、输出、退出码、耗时；改动的 diff）。运行中可以“停止”，也可以输入内容“追加”到正在进行的这一轮。

### “沙箱”与运行模式

- Codex 自己的 Linux 沙箱（bubblewrap/landlock）在安卓应用里用不了，所以 Codex 以 `danger-full-access` 运行，靠**安卓的应用沙箱**兜底：独立 UID + SELinux，只能访问本应用的私有目录和你授权的共享存储。工作区只是默认目录，不是独立隔离区。
- **每步确认**（默认，`approvalPolicy = untrusted`）：每条命令、每次改文件都先弹出底部弹窗：允许这一次 / 拒绝此操作 / 拒绝并停止本轮。实测 0.166 在这个模式下连 `ls` 都会请求确认。
- **完全自动**（`never`）：不再逐项确认（设置里开启，有确认弹窗）。
- Agent 在后台工作时有常驻通知（可以直接“停止”）；App 在后台时来了审批请求会发提醒通知，但不会在通知里直接批准。

## 架构

```
App 进程（Java）
 ├─ CodexRuntimeManager      运行时的唯一所有者（主线程状态）
 │   ├─ Installer            下载/校验/解压/自检/切换版本
 │   ├─ DeviceLogin          codex login --device-auth → 文件 → 解析 → 自动换码
 │   ├─ LocalProxy           127.0.0.1 上的 HTTP CONNECT 代理（带随机口令）
 │   └─ AppServer            codex app-server 的 JSON-RPC 客户端
 ├─ RuntimeService           前台服务：安装、登录、Agent 工作、等审批时保活 + 通知 + 唤醒锁
 └─ 界面：SetupActivity（安装→登录→工作区）、MainActivity（会话/文件/设置）、ChatActivity、FileViewer、FolderPicker、Diagnostics

codex app-server（musl 静态程序，子进程）
 ├─ codex-code-mode-host     0.166 的工具调用都要经过它（缺了模型就执行不了命令）
 └─ /system/bin/sh（mksh）  经 PATH 里的 bash 链接调用
```

在安卓上跑官方 musl 静态版 codex 要解决的问题（都在 chroot 模拟的安卓文件系统里实测过，见 `notes/`）：

| 问题 | 原因 | 办法 |
|---|---|---|
| 执行不了下载的程序 | 安卓 10 起 targetSdk ≥ 29 不能 execve 私有目录里的文件（W^X） | targetSdk 28（Termux 也是这样） |
| 域名解析失败 | musl 读 `/etc/resolv.conf`，安卓没有 | Codex 走 `HTTPS_PROXY=http://codex:<口令>@127.0.0.1:<端口>`（数字 IP，不需要 DNS），由 Java 的 LocalProxy 用安卓系统解析和连接，顺便跟随系统 Wi-Fi 代理 |
| TLS 没有根证书 | 安卓没有 `/etc/ssl` | 从 `AndroidCAStore` 导出 PEM，设 `SSL_CERT_FILE` / `CODEX_CA_CERTIFICATE`（用户安装的证书默认不导出，设置里可开） |
| 命令全失败 | 没有 `/etc/passwd`、`/bin`，codex 忽略 `$SHELL`，最后回退到不存在的 `/bin/sh` | codex 会先在 PATH 里找 `bash`：在 PATH 最前面放 `bash → /system/bin/sh` 的链接 |
| 工具调用失败 | 0.166 需要 `codex-code-mode-host` | 安装时一起解出 |

`config.toml`（每次启动重写）：`cli_auth_credentials_store = "file"`、`sandbox_mode = "danger-full-access"`、`check_for_update_on_startup = false`、`[features] shell_snapshot = false`。**不写 `approval_policy`**：0.166 起 config 里写 `"untrusted"` 会直接拒绝启动（集成测试抓到的），审批模式在每次 `thread/start`、`thread/resume`、`turn/start` 里显式传。

### 为什么是 targetSdk 28

“先安装 Codex CLI 再运行”需要执行下载到私有目录的程序，只有 targetSdk ≤ 28 允许。另一条路是把 350 MB 的程序塞进 APK 的 native 库目录：APK 会超过 100 MB，而且每次 Codex 更新都要重装 App。代价：不能上架 Google Play；安装时可能有旧版提示。Android 14/15/16 的最低可安装 targetSdk 是 23/24，28 可以安装（Luna 查证，`notes/安卓运行调研.md`）。

### 对话里的细节

- 审批按到达顺序一项一项弹出；点了以后显示“正在提交决定…”，等服务端确认（`serverRequest/resolved`）才关闭。Codex 进程不在了会提示提交失败，不会假装已经处理。
- Agent 向你提问（`requestUserInput`）时弹出问题框：选一项或自己填，提交后继续。
- 打开旧会话先载入最近 30 轮，顶部“加载更早的记录”往前翻。
- App 在后台空闲一分钟后关掉 Codex 进程省电；回来时自动重启，旧会话发消息前先自动恢复（`thread/resume`）。Codex 进程意外退出会自动重启（10 分钟内最多 3 次），正在进行的那一轮标成未完成，**不会自动重发**。
- 分享文件时先复制到专用缓存目录再分享；指向应用运行目录（凭据、日志）的文件和链接不显示、不分享。

### 已知风险

- 安卓应用进程有 seccomp 白名单：Android 12 以下调用 `pidfd_open` 等较新的系统调用可能被直接杀掉（SIGSYS）。这里没有真机，**首版以真机自检为准**：设置 → 自检与诊断，结果可以复制发给开发者（不含凭据、登录代码、邮箱、项目内容）。
- Android 12+ 的“幽灵进程”限制可能在后台杀掉子进程；前台服务能降低但不能杜绝。被打断时可以在设置 → 电池限制放宽。
- 只支持 64 位（arm64-v8a、x86_64）：官方 Codex CLI 只有这两种 Linux 构建。
- 开发工具包没装好之前，Agent 只有安卓自带的 toybox 命令和 apply_patch；装好后才有 node、git、python 等（设置 → 开发工具）。
- 终端没有伪终端：不支持 vim、python 交互模式这类需要键盘交互的程序；git 需要密码时不会提示（用令牌写进网址或 credential store）。
- 语音输入用系统的语音识别服务（`RecognizerIntent`）；没装的手机会提示改用输入法的语音键。
- 还没做（下一版）：发送记录持久化对账（进程中途死掉时“提交状态待确认”）、会话和文件搜索、手动 HTTP 代理设置、平板双栏、存储用量统计。

## 构建与测试

```bash
./gradlew :core:test                      # 纯 Java：JSON、设备码解析/自动换码、代理、安装器、tar、app-server 客户端、会话归并（用真实录制的协议消息回放）
CODEX_BIN=<x64 codex> CODEX_AUTH=~/.codex/auth.json ./gradlew :core:test --tests '*CodexIntegrationTest*'
                                          # 真实 codex 0.166 经 LocalProxy：设备码拿到代码、app-server 开会话、每步确认下批准命令、文件真的写了
./gradlew :smoke:testDebugUnitTest        # Robolectric 真实 Activity 跑一遍各个状态并截图（smoke/shots/，浅色和深色）
                                          # v0.2 新增：23–33 号截图（新对话、聊天模式、＋菜单、@、附件、模型/推理/速度面板、对话、⋯菜单）、13 开发工具、17 终端、18 常用命令
./gradlew :app:assembleRelease            # 发布包（R8），测试签名 signing/test.jks
```

设计来源：架构、界面规格、文案由 Astra 设计（`notes/设计稿一.md`、`notes/设计稿二.md`），安卓限制由 Luna 联网查证（`notes/安卓运行调研.md`）。
