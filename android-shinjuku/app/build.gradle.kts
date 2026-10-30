plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.shinjuku.showdown"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.shinjuku.showdown"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.11"
    }

    // 测试签名：放在仓库里，保证每次构建的签名一致，新包能直接覆盖安装旧包。只用于测试，不用于上架。
    signingConfigs {
        create("test") {
            storeFile = rootProject.file("signing/test.jks")
            storePassword = "shinjuku-test"
            keyAlias = "test"
            keyPassword = "shinjuku-test"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        // 发布版：R8 精简 + 资源压缩，不带 debuggable，测性能请装这个
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("test")
        }
    }

    // 背景音乐和领域展开的过场视频用 openRawResourceFd 直接从 APK 里读，必须不压缩
    androidResources {
        noCompress += listOf("mp3", "mp4")
    }

    // Kotlin 只用在截图测试里；它的插件生成的元数据文件不需要进 APK
    packaging {
        resources.excludes += "kotlin-tooling-metadata.json"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    // GL 画面的对照测试：用 LWJGL + 系统的 Mesa 软件 GLES（libEGL / libGLESv2）在 JVM 上跑真正的 GlTarget
    testImplementation("org.lwjgl:lwjgl:3.3.6")
    testImplementation("org.lwjgl:lwjgl-egl:3.3.6")
    testImplementation("org.lwjgl:lwjgl-opengles:3.3.6")
    testRuntimeOnly("org.lwjgl:lwjgl:3.3.6:natives-linux")
    testRuntimeOnly("org.lwjgl:lwjgl-opengles:3.3.6:natives-linux")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
}

// GL 对照测试用 Mesa 的软件实现，不需要显示器
tasks.withType<Test>().configureEach {
    environment("EGL_PLATFORM", "surfaceless")
    environment("LIBGL_ALWAYS_SOFTWARE", "1")
}
