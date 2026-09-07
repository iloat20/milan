plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    // 试验田起步：先开 jvm target 验证领域层纯净性（可后续追加 android / ios / js）
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            }
        }
    }
}
