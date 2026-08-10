// 试验田·微创新：Macrobenchmark 模块（独立，不污染 :app）。
// 注意：本项目已升级 AGP 9（内置 Kotlin），故不重复应用 kotlin-android 插件，
// 与 app 模块保持一致；若你的 AGP 版本不同导致 Kotlin 源无法编译，可在此补 kotlin("android")。
plugins {
    id("com.android.test")
}

android {
    namespace = "com.milan.game.benchmark"
    compileSdk = 36

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
    implementation(libs.androidx.uiautomator)
    implementation(libs.junit)
}
