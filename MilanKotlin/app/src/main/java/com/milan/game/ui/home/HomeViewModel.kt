package com.milan.game.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GameService
import com.milan.game.ownedView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 名录条目（VM 派生态）：解析后的内容定义 + 展示文案。 */
data class HomeAvatarEntry(
    val def: CharacterDataEntry,
    val name: String,
    val source: String,
)

/** 主页派生态：主视觉角色 + 丹青名录（随快照 revision 重算）。 */
data class HomeUiState(
    /** 主视觉：已拥有最高稀有度（内容表可解析）优先，否则内容表首位。 */
    val featured: CharacterDataEntry,
    /** 丹青名录六宫格（PickIds 精选 + 稀有度补足）。 */
    val avatarEntries: List<HomeAvatarEntry>,
)

/** 名录精选 id（原 Screen 内私有常量，搬移时保留语义）。 */
private val PickIds = listOf(
    "char_ur_zhulong", "char_ur_xingtian", "char_ssr_fenghuang",
    "char_sr_bifang", "char_sr_jingwei", "char_ssr_leishen",
)

/**
 * 主页 ViewModel（2026-09-09 P1-6 F 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - 原 Hero/名录 各自 `remember(snap.revision) { featuredCharacter() }` /
 *   `remember(snap.revision) { ... }` 收敛为订阅 `service.snapshot` 重算；
 * - featured 口径与 ownedView 完全一致（存档 × 内容定义合并，
 *   最高稀有度优先，内容表空缺时才回退占位——宁可难看不能崩）。
 */
class HomeViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // P0 fan-out：主页名录/主视觉只随持有变化刷新（roster 指纹）。
            service.roster.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): HomeUiState {
        // featured：已拥有最高稀有度优先（与 ownedView 同口径）
        val owned = service.saveData.ownedCharacters.mapNotNull { ch ->
            ch ?: return@mapNotNull null
            service.ownedView(ch)
        }
        val featured = owned.maxByOrNull { it.rarity }?.def
            ?: service.characters.firstOrNull()
            ?: CharacterDataEntry(
                characterId = "placeholder",
                displayName = "未知存在",
                title = "数据缺失",
                world = "Shinwa",
                element = "Flame",
                baseRarity = 1,
                baseStats = listOf(10, 10, 100, 10),
            )

        // 丹青名录：精选 id 优先 + 稀有度补足，取 6
        val all = service.characters
        val byId = all.associateBy { it.characterId }
        val picked = PickIds.mapNotNull { byId[it] }
        val fill = all.filter { it.characterId !in PickIds.toSet() }
            .sortedByDescending { it.baseRarity }
        val entries = (picked + fill).take(6).map { def ->
            HomeAvatarEntry(
                def = def,
                name = def.displayName.substringBefore(' '),
                source = rarityNameFor(def.baseRarity),
            )
        }
        return HomeUiState(featured = featured, avatarEntries = entries)
    }

    /** 稀有度展示名（Home 名录专用；避免 VM 依赖 AppTheme 的 Compose 类型）。 */
    private fun rarityNameFor(rarity: Int): String = when (rarity) {
        4 -> "UR"
        3 -> "SSR"
        2 -> "SR"
        else -> "R"
    }
}