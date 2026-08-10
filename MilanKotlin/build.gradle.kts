plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// AGP 9 内置 Kotlin（built-in Kotlin）默认携带 KGP 2.2.10；
// 显式提升 classpath 使 kotlin-compose / kotlin-serialization 插件与项目 Kotlin 2.4.0 对齐。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}
