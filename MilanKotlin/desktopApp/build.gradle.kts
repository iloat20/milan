// 桌面抽卡模拟器（KMP 复用演示）：复用 :shared 的真实领域引擎。
// 运行：./gradlew :desktopApp:run
plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.milan.game.desktop.MainKt")
}

dependencies {
    implementation(project(":shared"))
}
