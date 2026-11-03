package com.pocketcodex.app;

/**
 * 常用命令速查（终端里点一下放进输入框；设置页里点一下复制）。
 * 这些命令由开发工具包提供（Termux 仓库的 Node.js、Git、Python……），没装时只有系统自带的 toybox 命令。
 * 尖括号里的内容要换成自己的。
 */
final class Commands {
    private Commands() {
    }

    static final class Group {
        final String title;
        final String[][] items; // {命令, 说明}

        Group(String title, String[][] items) {
            this.title = title;
            this.items = items;
        }
    }

    static final Group[] GROUPS = {
            new Group("先看看环境", new String[][]{
                    {"node -v && npm -v && git --version && python -V", "看看工具都在不在、各是什么版本"},
                    {"pwd && ls -la", "当前目录和里面的文件"},
                    {"tree -L 2", "目录结构（两层）"},
                    {"du -sh * | sort -h", "每个文件/文件夹占多大"},
                    {"df -h .", "剩余空间"},
            }),
            new Group("Node.js / npm", new String[][]{
                    {"npm init -y", "在当前目录新建 package.json"},
                    {"npm install <包名>", "安装依赖（写进 package.json）"},
                    {"npm install", "按 package.json 装全部依赖"},
                    {"npm run dev", "运行 package.json 里的 dev 脚本"},
                    {"npx <工具> --help", "不安装直接运行一个命令行工具"},
                    {"npm install -g <包名>", "全局安装（装到 ~/.npm-global/bin）"},
                    {"npm config set registry https://registry.npmmirror.com", "改用国内镜像（下载慢时）"},
                    {"node -e \"console.log(process.version, process.platform)\"", "直接运行一行 JavaScript"},
                    {"nohup npx http-server -p 8080 > server.log 2>&1 &", "后台开一个网页服务器，手机浏览器打开 127.0.0.1:8080"},
            }),
            new Group("Git", new String[][]{
                    {"git clone <仓库网址>", "下载一个仓库"},
                    {"git status", "看看改了什么"},
                    {"git add -A && git commit -m \"<说明>\"", "提交全部改动"},
                    {"git log --oneline -10", "最近 10 次提交"},
                    {"git diff", "还没提交的具体改动"},
                    {"git pull", "拉取远端更新"},
                    {"git push", "推送（需要你自己的访问令牌）"},
                    {"git checkout -b <分支名>", "新建并切换分支"},
                    {"git config --global user.name \"<名字>\" && git config --global user.email \"<邮箱>\"", "设置提交用的名字和邮箱"},
                    {"git config core.filemode false", "手机存储里的仓库：忽略文件权限变化"},
            }),
            new Group("Python", new String[][]{
                    {"python -m venv .venv && . .venv/bin/activate", "建一个虚拟环境并启用"},
                    {"pip install <包名>", "安装 Python 包"},
                    {"pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple", "改用清华镜像（下载慢时）"},
                    {"python -c \"print('hello')\"", "直接运行一行 Python"},
            }),
            new Group("装更多软件（pkg）", new String[][]{
                    {"pkg search <关键词>", "在 Termux 仓库里搜软件包"},
                    {"pkg install <包名>", "安装，例如 clang、cmake、golang、rust、ffmpeg、imagemagick"},
                    {"pkg list-installed", "已经装了哪些"},
                    {"pkg uninstall <包名>", "卸载"},
            }),
            new Group("查找与文本", new String[][]{
                    {"rg \"<关键词>\"", "在所有文件里搜文字（很快）"},
                    {"find . -name \"*.js\"", "按文件名找"},
                    {"jq . <文件>.json", "格式化查看 JSON"},
                    {"curl -I https://www.baidu.com", "测试网络能不能连上"},
                    {"tar -czf backup.tar.gz <文件夹>", "打包压缩"},
                    {"unzip <文件>.zip", "解压 zip"},
            }),
            new Group("Codex", new String[][]{
                    {"codex --version", "当前安装的 Codex CLI 版本"},
            }),
    };

    /** 终端顶部一排的快捷命令。 */
    static final String[] QUICK = {"ls -la", "git status", "node -v", "npm install", "git log --oneline -10", "pkg install ", "python -V", "clear"};
}
