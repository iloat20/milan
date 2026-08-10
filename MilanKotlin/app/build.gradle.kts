import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 built-in Kotlin：不再应用 kotlin-android 插件
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 本地签名凭据（keystore.properties 不入库）；文件缺失时 release 退化为未签名，保证他人克隆/CI 可构建
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.milan.game"
    // compileSdk 37：Compose BOM 2026.06.01 的 ui 1.12.0-alpha03 强制要求（AGP 9.1.0+）
    compileSdk = 37

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.milan.game"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // R8 代码收缩 + 资源收缩；kotlinx.serialization keep 规则见 proguard-rules.pro
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // 底部导航等处的 Material 图标（P2-6：NavCell 字符字形 → 标准图标）
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // 安装时注入 Baseline Profile（src/main/baseline-prof.txt），冷启动预编译热点
    implementation(libs.androidx.profileinstaller)
    // 音频：背景乐 ExoPlayer（SFX 走 SoundPool，见 infrastructure/MilanAudio.kt）
    implementation(libs.androidx.media3.exoplayer)
    // 导航：Navigation Compose 2.9 类型安全路由（@Serializable），替代自研状态路由
    implementation(libs.androidx.navigation.compose)
    // 桌面小组件（Glance）：GachaGlanceWidget 的今日运势入口（ui/glance/）
    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
}
