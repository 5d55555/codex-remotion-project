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
        versionCode = 1
        versionName = "0.1"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
}
