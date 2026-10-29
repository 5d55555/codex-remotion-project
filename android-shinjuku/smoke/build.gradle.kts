// 冒烟测试：用 Robolectric（真实的 Android 框架代码）把各个界面真的跑一遍，抓启动、跳转、生命周期里的崩溃。
// 容器里没有模拟器，这是出包前确认“点了不会闪退”的办法。
// 和 Paparazzi 不能放在同一个模块，所以单独一个模块，直接编译 app 的源码、资源和清单（不产出要发布的包）。
plugins {
    id("com.android.application")
}

android {
    namespace = "com.shinjuku.showdown"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.shinjuku.showdown.smoke"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "smoke"
    }

    sourceSets["main"].apply {
        java.srcDirs("../app/src/main/java")
        res.srcDirs("../app/src/main/res")
        manifest.srcFile("../app/src/main/AndroidManifest.xml")
    }

    androidResources {
        noCompress += "mp3"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.maxHeapSize = "2g" }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
}

// Robolectric 运行时要的 Android 框架 jar：交给 Gradle 下载（走得通代理），Robolectric 用离线模式读本地文件。
// 同一个构件的两个版本放在一个配置里会被 Gradle 当成冲突只留一个，所以每个 SDK 一个配置。
val robolectricSdks = mapOf(
    "Sdk28" to "org.robolectric:android-all-instrumented:9-robolectric-4913185-2-i7",
    "Sdk36" to "org.robolectric:android-all-instrumented:16-robolectric-13921718-i7",
)
val robolectricConfigs = robolectricSdks.map { (name, coords) ->
    configurations.create("robolectric$name") { isTransitive = false }.also { dependencies.add(it.name, coords) }
}
val robolectricDir = layout.buildDirectory.dir("robolectric-jars")
val copyRobolectricJars by tasks.registering(Copy::class) {
    robolectricConfigs.forEach { from(it) }
    into(robolectricDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(copyRobolectricJars)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDir.get().asFile.absolutePath)
}
