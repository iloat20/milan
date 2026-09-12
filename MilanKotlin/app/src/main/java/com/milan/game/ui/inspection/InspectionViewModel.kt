package com.milan.game.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.CharacterAction
import com.milan.game.data.PhotoBackground
import com.milan.game.data.PhotoFilter
import com.milan.game.data.PhotoPose
import com.milan.game.data.PhotoRecord
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
    val poses: List<PhotoPose> = emptyList(),
    val backgrounds: List<PhotoBackground> = emptyList(),
    val filters: List<PhotoFilter> = emptyList(),
    val photoCount: Int = 0,
    val selectedPoseId: String = "pose_standing",
    val selectedBackgroundId: String = "bg_default",
    val selectedFilterId: String = "filter_none",
)

/**
 * 角色检视 ViewModel（2026-09-11 骨架；2026-09-12 拍照模式接线）。
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
        val inspection = service.getInspectionData()
        val actions = service.getAvailableActions(characterId)
        val hidden = service.checkHiddenInteraction(characterId)
        val prev = _uiState.value
        return InspectionUiState(
            characterId = characterId,
            displayName = def?.displayName ?: characterId,
            rarity = def?.baseRarity ?: 1,
            inspectionCount = inspection.inspectionCounts[characterId] ?: 0,
            actions = actions,
            hiddenUnlocked = hidden,
            owned = owned,
            poses = service.getPoses(),
            backgrounds = service.getBackgrounds(),
            filters = service.getFilters(),
            photoCount = inspection.photoCollection.size,
            selectedPoseId = prev.selectedPoseId,
            selectedBackgroundId = prev.selectedBackgroundId,
            selectedFilterId = prev.selectedFilterId,
        )
    }

    fun selectPose(id: String) {
        _uiState.value = _uiState.value.copy(selectedPoseId = id)
    }

    fun selectBackground(id: String) {
        _uiState.value = _uiState.value.copy(selectedBackgroundId = id)
    }

    fun selectFilter(id: String) {
        _uiState.value = _uiState.value.copy(selectedFilterId = id)
    }

    /** 记录一次检视（推进解锁进度）。 */
    fun recordInspection() {
        viewModelScope.launch {
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

    /** 保存当前姿势/背景/滤镜组合为照片记录。 */
    fun savePhoto() {
        viewModelScope.launch {
            val rec = PhotoRecord(
                photoId = "photo_${characterId}_${System.currentTimeMillis()}",
                characterId = characterId,
                pose = _uiState.value.selectedPoseId,
                background = _uiState.value.selectedBackgroundId,
                filter = _uiState.value.selectedFilterId,
                timestamp = System.currentTimeMillis(),
            )
            when (service.savePhoto(rec)) {
                WriteOutcome.Success -> {
                    _uiState.value = buildState()
                    val max = com.milan.game.data.InspectionSaveData.MAX_PHOTOS
                    _toasts.send("照片已收藏（${_uiState.value.photoCount}/$max）")
                }
                WriteOutcome.Rejected -> _toasts.send(
                    "相册已满（上限 ${com.milan.game.data.InspectionSaveData.MAX_PHOTOS}）",
                )
                WriteOutcome.SaveFailed -> _toasts.send("存档失败，照片未保存")
            }
        }
    }
}
