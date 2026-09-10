package com.milan.game.ui.affinity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.services.AffinityFormulas
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 已拥有角色的好感度行（UI 只渲染此列表，不再自行过滤 owned）。 */
data class AffinityRow(
    val characterId: String,
    val displayName: String,
    val rarity: Int,
    val affinity: Int,
)

/** 好感度页 UI 状态（派生自角色定义 + owned + 好感数据 + 星尘，随快照 revision 重算）。 */
data class AffinityUiState(
    val rows: List<AffinityRow>,
    val softCurrency: Int,
)

/**
 * 好感度页 ViewModel（2026-09-08 P1-6 B 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：好感数据不在 snapshot 字段内，派生读订阅
 * `service.snapshot` 随每次写提交（赠送/战斗/剧情落盘后的 refreshSnapshot）重算；
 * 角色列表只含已拥有者（owned 过滤下沉到 VM）。赠送动作走 [toasts] 三态反馈。
 */
class AffinityViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<AffinityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): AffinityUiState {
        val ownedIds = service.saveData.ownedCharacters
            .mapNotNull { it?.characterId }
            .toSet()
        val affinity = service.getCharacterAffinityData()
        val defs: List<CharacterDataEntry> = service.characters
        val rows = defs.filter { it.characterId in ownedIds }.map { def ->
            AffinityRow(
                characterId = def.characterId,
                displayName = def.displayName,
                rarity = def.baseRarity,
                affinity = affinity[def.characterId] ?: 0,
            )
        }
        return AffinityUiState(
            rows = rows,
            softCurrency = service.saveData.softCurrency,
        )
    }

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    /** 赠送礼物（100 星尘 → +200 好感，数值见 [AffinityFormulas]）。拒绝时给针对性原因。 */
    fun gift(characterId: String) {
        viewModelScope.launch {
            try {
                val msg = when (service.giftAffinity(characterId)) {
                    WriteOutcome.Success -> "好感 +${AffinityFormulas.GIFT_AFFINITY_AMOUNT}（扣除 ${AffinityFormulas.GIFT_COST_SOFT} 星尘）"
                    WriteOutcome.Rejected -> {
                        val current = service.getCharacterAffinityData()[characterId] ?: 0
                        if (current >= AffinityFormulas.MAX_AFFINITY) {
                            "该角色好感已满级"
                        } else {
                            "星尘不足（赠送需 ${AffinityFormulas.GIFT_COST_SOFT}）"
                        }
                    }
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                _toasts.send(msg)
            } catch (_: Exception) {
                _toasts.send("操作异常，请重试")
            }
        }
    }
}
