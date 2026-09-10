plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// AGP 9 内置 Kotlin（built-in Kotlin）默认携带 KGP 2.2.10；
// 显式提升 classpath 使 kotlin-compose / kotlin-serialization 插件与项目 Kotlin 2.4.10 对齐。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

// ── 层间依赖规则（2026-09 架构优雅化，CI fail-fast）──
// 用法: ./gradlew checkArchitecture
// 1. :shared 领域层禁止 android.*（跨端纯净性红线）
// 2. ViewModel 构造禁止 `GameService = GameState.service` 默认参数（服务定位器）
// 3. ui/ 屏幕禁止 GameState.service / GameState.snapshot / GameState.computeStats
//    （允许 ready / failure / ensureInitialized 启动门控）
// configuration cache 友好：配置期捕获路径字符串，不在 doLast 闭包捕获 project。
tasks.register("checkArchitecture") {
    group = "verification"
    description = "校验领域纯净性、组合根接线与 UI 单例泄漏（层间依赖规则）"
    val rootPath = projectDir.absolutePath
    val sharedDir = File(rootPath, "shared/src/commonMain")
    val uiDir = File(rootPath, "app/src/main/java/com/milan/game/ui")
    inputs.dir(sharedDir).optional()
    inputs.dir(uiDir).optional()
    doLast {
        val violations = mutableListOf<String>()

        fun ktFiles(dir: File): Sequence<File> =
            if (!dir.exists()) emptySequence()
            else dir.walkTopDown().filter { it.isFile && it.extension == "kt" }

        fun isCommentOrDoc(line: String): Boolean {
            val t = line.trimStart()
            return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }

        // 1) :shared 无 android.*
        ktFiles(sharedDir).forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (isCommentOrDoc(line)) return@forEachIndexed
                if (line.contains("import android.") || Regex("""\bandroid\.[a-zA-Z]""").containsMatchIn(line)) {
                    violations += "shared 禁止 android.*: ${f.relativeTo(File(rootPath))}:${i + 1}"
                }
            }
        }

        // 2) ViewModel 禁止服务定位器默认参数
        ktFiles(uiDir).forEach { f ->
            if (!f.name.endsWith("ViewModel.kt")) return@forEach
            f.readLines().forEachIndexed { i, line ->
                if (line.contains("GameService") && line.contains("= GameState.service")) {
                    violations += "VM 禁止默认注入 GameState.service: ${f.relativeTo(File(rootPath))}:${i + 1}"
                }
            }
        }

        // 3) ui/ 禁止业务侧直连 GameState（启动门控除外）
        val allowedBoot = setOf("ready", "failure", "ensureInitialized", "resetForTest")
        ktFiles(uiDir).forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (isCommentOrDoc(line)) return@forEachIndexed
                Regex("""GameState\.(\w+)""").findAll(line).forEach { m ->
                    val member = m.groupValues[1]
                    if (member !in allowedBoot) {
                        violations += "UI 禁止 GameState.$member: ${f.relativeTo(File(rootPath))}:${i + 1}"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "架构检查失败（${violations.size} 处）：\n" + violations.joinToString("\n")
            )
        }
        logger.lifecycle("checkArchitecture: 通过（领域纯净 / VM 接线 / UI 无业务单例直连）")
    }
}
