package com.milan.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.ui.nav.MilanNavHost
import com.milan.game.ui.theme.MilanTheme

/**
 * 单一宿主的游戏入口（C# 多 Activity 结构的 Compose 单 Activity 等价物）。
 * 路由模型：Navigation Compose 2.9 类型安全路由——5 个主 tab（各页内自行渲染 GameNavBar）
 * + 4 层子页（神谱图鉴 → 我的角色 → 角色详情 → 角色养成），子页压栈盖住 tab，
 * 顶栏返回 / 系统返回（Predictive Back）逐层退出。路由定义见 ui/nav/Routes.kt。
 * 立绘共享元素过渡：SharedTransitionLayout 包 NavHost，作用域经自建
 * LocalSharedTransitionScope 注入；composable 的 AnimatedContentScope receiver
 * 直接作为各 Screen 的 animatedVisibilityScope 参数（见 CharacterListScreen/DetailScreen）。
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Glance 小组件深链参数（I9）：值为 [NAV_GACHA] 时启动直达抽卡页。 */
        const val EXTRA_NAVIGATE = "milan.navigate"

        /** [EXTRA_NAVIGATE] 取值：直达抽卡页。 */
        const val NAV_GACHA = "gacha"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.boot("main.onCreate")
        // targetSdk≥35 强制 edge-to-edge：内容延伸到系统栏区域，由各组件用 insets 内边距避让
        enableEdgeToEdge()
        MilanAudio.playBgm("theme") // 启动 BGM（无资源静默，见 MilanAudio）
        val openGacha = intent?.getStringExtra(EXTRA_NAVIGATE) == NAV_GACHA
        setContent {
            MilanTheme {
                MilanNavHost(openGachaOnStart = openGacha)
            }
        }
    }

    /**
     * BGM 生命周期接线：App 退后台（锁屏/切走）暂停 BGM 并释放 ExoPlayer 省资源，
     * 回前台自动恢复上次曲目（保留 bgmTarget 意图，见 MilanAudio.pauseBackground/resumeForeground）。
     * 之前缺失此接线，BGM 在后台持续播放。
     */
    override fun onStop() {
        super.onStop()
        MilanAudio.pauseBackground()
    }

    override fun onStart() {
        super.onStart()
        MilanAudio.resumeForeground()
    }
}
