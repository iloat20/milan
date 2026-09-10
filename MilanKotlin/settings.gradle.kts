pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MilanKotlin"
include(":app")
include(":data")
include(":core")
// 试验田 Track C：Compose Multiplatform 复用演示（shared 领域数学 + 桌面 JVM 模拟器）
include(":shared", ":desktopApp")
// 试验田·微创新：Macrobenchmark（2026-08-13 修复依赖后接入；benchmarkRelease 需真机/模拟器执行）
include(":benchmark")
