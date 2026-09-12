package com.milan.game.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.milan.game.GameState
import com.milan.game.services.GameService
import com.milan.game.ui.achievement.AchievementViewModel
import com.milan.game.ui.affinity.AffinityViewModel
import com.milan.game.ui.arena.ArenaViewModel
import com.milan.game.ui.battle.StrategicBattleViewModel
import com.milan.game.ui.battlepass.BattlePassViewModel
import com.milan.game.ui.characters.CharacterDetailViewModel
import com.milan.game.ui.characters.CharacterListViewModel
import com.milan.game.ui.collection.CollectionViewModel
import com.milan.game.ui.deck.DeckViewModel
import com.milan.game.ui.event.EventViewModel
import com.milan.game.ui.gacha.GachaViewModel
import com.milan.game.ui.gacha.PullHistoryViewModel
import com.milan.game.ui.home.HomeViewModel
import com.milan.game.ui.inspection.InspectionViewModel
import com.milan.game.ui.missions.DailyMissionViewModel
import com.milan.game.ui.progression.ProgressionViewModel
import com.milan.game.ui.settings.SettingsViewModel
import com.milan.game.ui.shop.ShopViewModel
import com.milan.game.ui.story.StoryViewModel
import com.milan.game.ui.tower.TowerViewModel
import com.milan.game.ui.tutorial.TutorialViewModel

/**
 * 组合根（Composition Root）：进程内 [GameService] 的唯一装配点。
 *
 * ## 为什么需要它
 * 此前所有 ViewModel 以 `service: GameService = GameState.service` 默认参数接线，
 * Screen 再直呼 `GameState.service.xxx`——这是典型的服务定位器：依赖在构造点隐式解析、
 * 无法从签名看出真实耦合、测试只能靠进程单例 `resetForTest()`。
 *
 * ## 约定
 * - [GameState.ensureInitialized] 成功后调用 [install]；[resetForTest] 链路调用 [clear]；
 * - Screen 一律经 `viewModel(factory = AppGraph.xxxFactory())` 构造 VM，**禁止**再摸 `GameState.service`；
 * - ViewModel 构造函数**必填** [GameService]（无默认参数），依赖关系在类型上可见；
 * - 需要路由参数的 VM（按角色 id 建实例）用对应的 `xxxFactory(id)`。
 */
object AppGraph {

    @Volatile
    private var serviceRef: GameService? = null

    /** 已装配的服务。未 install 时抛出——比静默 GameState 更早暴露装配错误。 */
    val service: GameService
        get() = checkNotNull(serviceRef) { "AppGraph 未装配：GameState.ensureInitialized 成功后才会 install" }

    val isInstalled: Boolean get() = serviceRef != null

    /** GameState 初始化成功后装配（进程内一次）。 */
    fun install(service: GameService) {
        serviceRef = service
    }

    /** 测试重置 / 存档重置后清空，与 GameState 生命周期对齐。 */
    fun clear() {
        serviceRef = null
    }

    // ── 无路由参数的 VM ──

    val factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(service) }
        initializer { GachaViewModel(service) }
        initializer { PullHistoryViewModel(service) }
        initializer { DeckViewModel(service) }
        initializer { ShopViewModel(service) }
        initializer { SettingsViewModel(service) }
        initializer { StoryViewModel(service) }
        initializer { CharacterListViewModel(service) }
        initializer { CollectionViewModel(service) }
        initializer { TowerViewModel(service) }
        initializer { ArenaViewModel(service) }
        initializer { AffinityViewModel(service) }
        initializer { AchievementViewModel(service) }
        initializer { BattlePassViewModel(service) }
        initializer { DailyMissionViewModel(service) }
        initializer { EventViewModel(service) }
        initializer { StrategicBattleViewModel(service) }
        initializer { TutorialViewModel(service) }
    }

    /** 角色详情：按 characterId 建实例（切角色 = 换 key = 新 VM）。 */
    fun characterDetailFactory(characterId: String): ViewModelProvider.Factory = viewModelFactory {
        initializer { CharacterDetailViewModel(characterId, service) }
    }

    /** 角色养成：同上。 */
    fun progressionFactory(characterId: String): ViewModelProvider.Factory = viewModelFactory {
        initializer { ProgressionViewModel(characterId, service) }
    }

    /** 角色检视：按 characterId 建实例（2026-09-11 死功能接线）。 */
    fun inspectionFactory(characterId: String): ViewModelProvider.Factory = viewModelFactory {
        initializer { InspectionViewModel(characterId, service) }
    }
}
