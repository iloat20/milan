package com.milan.game.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.CharacterAction
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 检视页 UI 状态（按 characterId 派生；次数/动作随写操作刷新）。 */
data class InspectionUiState(
    val characterId: String,
    val displayName: String,
    val rarity: Int,
    val inspectionCount: Int,
    val actions: List<CharacterAction>,
    val hiddenUnlocked: Boolean,
    val owned: Boolean,
)

/**
 * 角色检视 ViewModel（2026-09-11 死功能接线骨架）。
 *
 * 只接 InspectionApi：记录检视次数 + 可用互动动作列表。拍照/视差立绘为后续批次。
 * 写操作经 [toasts] 三态反馈；状态订阅 snapshot revision 重算。
 */
class InspectionViewModel(
    private val characterId: String,
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): InspectionUiState {
        val def = service.characters.firstOrNull { it.characterId == characterId }
        val owned = service.saveData.ownedCharacters.any { it?.characterId == characterId }
        @Suppress("DEPRECATION")
        val inspection = service.getInspectionData()
        @Suppress("DEPRECATION")
        val actions = service.getAvailableActions(characterId)
        @Suppress("DEPRECATION")
        val hidden = service.checkHiddenInteraction(characterId)
        return InspectionUiState(
            characterId = characterId,
            displayName = def?.displayName ?: characterId,
            rarity = def?.baseRarity ?: 1,
            inspectionCount = inspection.inspectionCounts[characterId] ?: 0,
            actions = actions,
            hiddenUnlocked = hidden,
            owned = owned,
        )
    }

    /** 记录一次检视（推进解锁进度）。 */
    fun recordInspection() {
        viewModelScope.launch {
            @Suppress("DEPRECATION")
            when (service.recordInspection(characterId)) {
                WriteOutcome.Success -> {
                    _uiState.value = buildState()
                    _toasts.send("检视完成 · 累计 ${_uiState.value.inspectionCount} 次")
                }
                WriteOutcome.Rejected -> _toasts.send("今日检视次数已达上限")
                WriteOutcome.SaveFailed -> _toasts.send("存档失败，检视未计入")
            }
        }
    }
}
