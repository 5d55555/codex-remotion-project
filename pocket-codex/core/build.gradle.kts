// 纯 Java：安装、登录、本地代理、app-server 协议和会话状态。不依赖 Android，可以在 JVM 上直接测试
// （集成测试会在这台机器上启动真实的 codex app-server）。
plugins {
    `java-library`
}

// 按 Java 8 的类库编译：安卓 8.0（API 26）上没有的 JDK 9+ 方法（List.of、readAllBytes、isBlank……）在这里就编不过
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

dependencies {
    // 解 Termux 的 .deb（data.tar.xz）
    api("org.tukaani:xz:1.10")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    // 需要真实 codex 的集成测试：CODEX_BIN 指向 x64 版 codex（同目录要有 codex-code-mode-host），CODEX_AUTH 指向已登录的 auth.json
    System.getenv("CODEX_BIN")?.let { environment("CODEX_BIN", it) }
    System.getenv("CODEX_AUTH")?.let { environment("CODEX_AUTH", it) }
    for (k in listOf("TOOLKIT_REAL_PREFIX", "TOOLKIT_REAL_ABI", "TOOLKIT_REAL_REPO")) System.getenv(k)?.let { environment(k, it) }
    testLogging { showStandardStreams = System.getenv("TOOLKIT_REAL_PREFIX") != null }
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
