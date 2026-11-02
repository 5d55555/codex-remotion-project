plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.pocketcodex.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.pocketcodex.app"
        minSdk = 26
        // targetSdk 28 是有意的：安卓 10 起 targetSdk ≥ 29 的应用不能执行自己下载到私有目录里的程序（W^X），
        // 而“先安装 Codex CLI 再运行”正需要这一点（Termux 也是这样做的）。见 README 和 notes/。
        @Suppress("ExpiredTargetSdkVersion")
        targetSdk = 28
        versionCode = 1
        versionName = "0.1"
        // 官方 Codex CLI 只有 arm64 和 x86_64 的 Linux 构建
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        create("test") {
            storeFile = rootProject.file("signing/test.jks")
            storePassword = "pocket-codex-test"
            keyAlias = "test"
            keyPassword = "pocket-codex-test"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("test")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += listOf("kotlin-tooling-metadata.json", "META-INF/*.version", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    lint {
        // targetSdk 28 是设计决定（见上），不让 lint 因为它失败
        disable += listOf("ExpiredTargetSdkVersion", "OldTargetApi")
        abortOnError = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")
    // Markdown：原生 Spannable 渲染，不用 WebView
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
}
