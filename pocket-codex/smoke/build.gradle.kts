// 冒烟测试：用 Robolectric（真实的 Android 框架代码）把各个界面真的跑一遍，并用原生图形模式截图。
// 和 Paparazzi 不能放在同一个模块，所以单独一个模块，直接编译 app 的源码、资源和清单（不产出要发布的包）。
plugins {
    id("com.android.application")
}

android {
    namespace = "com.pocketcodex.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.pocketcodex.app.smoke"
        minSdk = 26
        @Suppress("ExpiredTargetSdkVersion")
        targetSdk = 28
        versionCode = 1
        versionName = "smoke"
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets["main"].apply {
        java.srcDirs("../app/src/main/java")
        res.srcDirs("../app/src/main/res")
        manifest.srcFile("../app/src/main/AndroidManifest.xml")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                it.systemProperty("shots.dir", rootProject.file("smoke/shots").absolutePath)
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
}

// Robolectric 运行时要的 Android 框架 jar：交给 Gradle 下载（走得通代理），Robolectric 用离线模式读本地文件。
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
