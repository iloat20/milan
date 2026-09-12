import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 built-in Kotlin：不再应用 kotlin-android 插件
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 本地签名凭据（keystore.properties 不入库）；文件缺失时 release 退化为未签名，保证他人克隆/CI 可构建。
// 注（P3-11）：配置期读取 + configuration-cache 会把密码序列化进缓存条目——仅限本地机器使用，
// CI 应改走环境变量注入，避免凭据落进共享缓存。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.milan.game"
    // compileSdk 37：Compose BOM 2026.09.00 (Compose 1.12) 要求；AGP 9.1.0+
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
        targetSdk = 37
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
        // Macrobenchmark 专用构建类型（:benchmark 模块的 targetProjectPath 按此 variant 消费 :app；
        // 对齐官方模板：非 debuggable + debug 签名 + 与 release 相同的 R8 路径——
        // 此前未开 minify，StartupBenchmark 测到的不是生产收缩结果，2026-09-11 性能报告 P2）。
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    // Robolectric 跑 Compose UI 测试需要访问 Android 资源（读取 assets/drawable 等）。
    // 不开此项会在 setContent 时因找不到资源而失败。
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    // 架构红线（checkArchitecture）：见文件末尾 registerArchitectureCheck
}

// 打包守卫：禁止 .bak / 带日期后缀的备份文件进入 source set（会原样打进 APK/AAB）。
// 备份请放 tools/backup/，不要放 src/main/assets 或 res。
// configuration cache 友好：配置期捕获路径字符串，不在 doLast 闭包捕获 project。
tasks.register("checkNoPackagedBackups") {
    group = "verification"
    description = "拒绝 src/main 中的 .bak / *.webp.YYYYMMDD 等备份残留"
    val appDirPath = projectDir.absolutePath
    val assetsDir = file("src/main/assets")
    val resDir = file("src/main/res")
    inputs.dir(assetsDir).optional()
    inputs.dir(resDir).optional()
    doLast {
        val banned = Regex("""\.bak$|\.webp\.\d{8}$|\.png\.\d{8}$|\.json\.\d{8}$""", RegexOption.IGNORE_CASE)
        val appRoot = File(appDirPath)
        val offenders = listOf(assetsDir, resDir)
            .filter { it.exists() }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile } }
            .filter { banned.containsMatchIn(it.name) }
            .map { it.relativeTo(appRoot).path }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "发现会打进包的备份残留（请移到 tools/backup/）：\n" + offenders.joinToString("\n")
            )
        }
        logger.lifecycle("checkNoPackagedBackups: 通过")
    }
}

tasks.named("preBuild") {
    dependsOn("checkNoPackagedBackups")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // collectAsStateWithLifecycle：StateFlow 状态快照的 UI 订阅（2026-08 现代化）
    implementation(libs.androidx.lifecycle.runtime.compose)
    // P1-6（2026-09-08 ViewModel 化）：ViewModel + viewModelScope + compose viewModel()
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
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
    // WorkManager（2026-08 每日补给本地提醒）：此前仅经 Glance 传递携带，无法直接引用 API
    implementation(libs.androidx.work.runtime.ktx)
    // P2-9 Phase 2：数据模型模块（存档/枚举/序列化类），零 Android 依赖
    implementation(project(":data"))
    // P2-9 Phase 3：服务+基础设施模块（EventBus/CrashReporter/Audio/Services）
    implementation(project(":core"))
    // KMP 共享领域层：抽卡/养成/战斗引擎与跨平台模型（commonMain，见 :shared 模块）。
    // 领域逻辑自此与桌面/将来 iOS 共用同一份实现（2026-08 KMP 下沉）。
    implementation(project(":shared"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Konsist 架构测试（与 checkArchitecture 互补：层/包依赖用编译期 API 表达）
    testImplementation(libs.konsist)
    // 经济/保底不变量属性测试（Kotest property，可独立使用）
    testImplementation(libs.kotest.property)
    // ── Compose UI 测试（2026-08-28 P0）──
    // 项目此前 9624 行 UI 代码零测试覆盖，UI 缺陷只能靠静态审查发现（第三轮审查 6/14 个 bug 在 UI 层）。
    // 采用 Robolectric 在 JVM 上跑 Compose：无需模拟器/真机，可进 CI。
    // ui-test-manifest 必须走 debugImplementation（提供测试用的 AndroidManifest 合并项）。
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
}

// ── GameContent 自动生成（P1-7: data.json 单一 SoT）──
// 用法: ./gradlew :app:generateGameContent
// 修改 data.json 后运行此 task 重新生成 GameContent.kt
tasks.register<Exec>("generateGameContent") {
    description = "从 data.json 生成 GameContent.kt 兜底内容"
    group = "content"
    commandLine("python", "tools/generate_gamecontent.py")
    workingDir(rootProject.projectDir)
}
