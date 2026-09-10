package com.milan.game.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.OwnedCharacterView
import com.milan.game.ownedView
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GameService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 角色列表页 UI 状态（拥有视图/拥有 id 集随快照 revision 重算，roster 为静态内容）。 */
data class CharacterListUiState(
    val owned: List<OwnedCharacterView>,
    /** 已拥有数量（标题「已拥有 N 位角色」，快照 ownedCount 同口径）。 */
    val ownedCount: Int,
    /** 全量图鉴视角的已拥有 id 集（CompletionPanel 用，未拥有也计入分母）。 */
    val ownedIds: Set<String>,
)

/**
 * 我的角色列表页 ViewModel（2026-09-09 P1-6 D 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - `roster` 为内容定义（进程内不变），构造时读一次；
 * - `owned`（ownedView 同口径）与 `ownedIds` 派生随快照刷新。
 * - 筛选/排序/滚动视差为纯 UI 状态，留在 Screen。
 * - [OwnedCharacterView] 经 [ownedView] 构造时注入天赋树，视图不再回查进程单例。
 */
class CharacterListViewModel(
    private val service: GameService,
) : ViewModel() {

    /** 内容定义全表（CompletionPanel 分母 + 元素筛选候选）。 */
    val roster: List<CharacterDataEntry> = service.characters

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<CharacterListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // P0 fan-out：列表只关心持有/养成指纹（roster），无关写不重建。
            service.roster.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): CharacterListUiState {
        // 与 GameService.ownedView 同口径：存档 × 内容定义合并 + 天赋注入。
        val owned = service.saveData.ownedCharacters.mapNotNull { ch ->
            ch ?: return@mapNotNull null
            service.ownedView(ch)
        }
        return CharacterListUiState(
            owned = owned,
            ownedCount = service.roster.value.ownedCount,
            ownedIds = service.saveData.ownedCharacters.filterNotNull()
                .map { it.characterId }.toSet(),
        )
    }
}
