// 战斗逻辑：纯 Java，不依赖 Android，可以在 JVM 上做确定性单元测试。
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
