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
        versionCode = 3
        versionName = "0.3"
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

    // 背景音乐用 openRawResourceFd 直接从 APK 里读，必须不压缩
    androidResources {
        noCompress += "mp3"
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
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
}
