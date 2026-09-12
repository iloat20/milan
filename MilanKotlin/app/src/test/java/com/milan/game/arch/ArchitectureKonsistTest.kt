package com.milan.game.arch

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Konsist 架构门禁（2026-09-12 B 批）。
 *
 * 与根工程 `checkArchitecture` 正则任务互补：用编译期 API 锁层规则，
 * 随 `:app:testDebugUnitTest` 进入 CI。
 *
 * 注意：Konsist 以工程根（settings.gradle.kts / MilanKotlin/）拼接路径，
 * 入参应为 `shared/src` 这类工程根相对路径，而非测试 cwd 相对路径。
 */
class ArchitectureKonsistTest {

    private fun moduleSrc(module: String): String = "$module/src"

    @Test
    fun sharedDomainHasNoAndroidImports() {
        Konsist.scopeFromDirectory(moduleSrc("shared"))
            .files
            .filter { it.packagee?.name?.startsWith("com.milan.game.domain") == true }
            .assertTrue { file ->
                file.imports.none { imp ->
                    imp.name.startsWith("android.") || imp.name.startsWith("androidx.")
                }
            }
    }

    @Test
    fun dataSaveModelsHaveNoAndroidImports() {
        Konsist.scopeFromDirectory(moduleSrc("data"))
            .files
            .filter { it.name != "AndroidSaveProvider.kt" }
            .assertTrue { file ->
                file.imports.none { it.name.startsWith("android.") }
            }
    }

    @Test
    fun coreServicesHaveNoAndroidImports() {
        Konsist.scopeFromDirectory(moduleSrc("core"))
            .files
            .filter { it.packagee?.name?.startsWith("com.milan.game.services") == true }
            .assertTrue { file ->
                file.imports.none { it.name.startsWith("android.") }
            }
    }

    @Test
    fun viewModelsNeverDefaultInjectGameStateService() {
        Konsist.scopeFromDirectory(moduleSrc("app"))
            .classes()
            .filter { it.name.endsWith("ViewModel") }
            .assertTrue { vm ->
                val params = vm.primaryConstructor?.parameters ?: emptyList()
                params.none { param ->
                    param.hasDefaultValue() && param.text.contains("GameState.service")
                }
            }
    }

    @Test
    fun uiScreensOutsideNavDoNotTouchAppGraphService() {
        Konsist.scopeFromDirectory(moduleSrc("app"))
            .files
            .filter { it.packagee?.name?.startsWith("com.milan.game.ui") == true }
            .filter { it.packagee?.name?.endsWith(".nav") != true }
            .assertTrue { file ->
                file.text.lineSequence()
                    .map { it.trimStart() }
                    .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
                    .none { it.contains("AppGraph.service") }
            }
    }
}
