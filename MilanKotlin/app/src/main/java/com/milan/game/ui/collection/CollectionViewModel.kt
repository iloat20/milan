package com.milan.game.ui.collection

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

/** 图鉴页 UI 状态（拥有视图随快照 revision 重算）。 */
data class CollectionUiState(
    val owned: List<OwnedCharacterView>,
    val ownedById: Map<String, OwnedCharacterView>,
)

/**
 * 神谱图鉴页 ViewModel（2026-09-09 P1-6 D 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移，范式对齐 Detail/Progression 页）：
 * - `all` 为内容定义（进程内不变），构造时读一次；
 * - `owned`（ownedView 同口径：saveData.ownedCharacters × 内容定义合并）与
 *   `ownedById` 派生随快照刷新——抽卡后图鉴进度随重组更新。
 * - 搜索/稀有度/元素/排序为纯 UI 状态（rememberSaveable），留在 Screen。
 * - [OwnedCharacterView] 经 [ownedView] 构造时注入天赋树，视图不再回查进程单例。
 */
class CollectionViewModel(
    private val service: GameService,
) : ViewModel() {

    /** 全量角色内容定义（进程内不变）。 */
    val all: List<CharacterDataEntry> = service.characters

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): CollectionUiState {
        // 与 GameService.ownedView 同口径：存档 × 内容定义合并 + 天赋注入。
        val owned = service.saveData.ownedCharacters.mapNotNull { ch ->
            ch ?: return@mapNotNull null
            service.ownedView(ch)
        }
        return CollectionUiState(
            owned = owned,
            ownedById = owned.associateBy { it.save.characterId },
        )
    }
}
