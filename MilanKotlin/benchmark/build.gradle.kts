// 试验田·微创新：Macrobenchmark 模块（2026-08-13 修复并接入 settings：补 ext:junit、
// compileSdk 对齐 app 的 37——此前 compileSdk 36 且缺 AndroidJUnit4 依赖，模块一旦 include 即编译失败）。
// 注意：本项目已升级 AGP 9（内置 Kotlin），故不重复应用 kotlin-android 插件，
// 与 app 模块保持一致；若你的 AGP 版本不同导致 Kotlin 源无法编译，可在此补 kotlin("android")。
plugins {
    id("com.android.test")
}

android {
    namespace = "com.milan.game.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        // 被基准化的目标 App
        targetProjectPath = ":app"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // 自插桩，无需额外 test runner 进程
    experimentalProperties["android.experimental.selfInstrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.junit)
}
