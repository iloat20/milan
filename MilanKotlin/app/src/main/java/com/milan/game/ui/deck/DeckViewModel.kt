package com.milan.game.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.OwnedCharacterView
import com.milan.game.data.SaveData
import com.milan.game.ownedView
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 卡组页 UI 状态（拥有列表 + 编队成员 + 编队 id 集，随快照 revision 重算）。 */
data class DeckUiState(
    val owned: List<OwnedCharacterView>,
    /** 编队成员（保持拥有顺序过滤，与原实现一致；爬塔页才按槽位序）。 */
    val members: List<OwnedCharacterView>,
    /** 当前编队 id 集（预览层「已入队」判定用）。 */
    val formation: List<String>,
    val maxSlots: Int = SaveData.MAX_FORMATION_SIZE,
)

/**
 * 卡组页 ViewModel（2026-09-09 P1-6 E 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - 原 `remember(snapshot.revision) { GameState.owned() }` / members 收敛为订阅快照重算；
 * - U2 语义保留：toggleFormation 是服务层读-改-写事务（临界区内串行），VM 只透传结果；
 * - 预览 id、空槽引导等纯 UI 状态留在 Screen。
 */
class DeckViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<DeckUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // P0 fan-out：订 roster 切片（编队 + 养成指纹）。仅货币/开关变化不发射，
            // 卡组不再因商店买碎片或设置开关而全量重建 ownedView。
            service.roster.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): DeckUiState {
        // 与 GameService.ownedView 同口径：存档 × 内容定义合并 + 天赋注入。
        val owned = service.saveData.ownedCharacters.mapNotNull { ch ->
            ch ?: return@mapNotNull null
            service.ownedView(ch)
        }
        val formation = service.roster.value.formation
        val formed = formation.toSet()
        return DeckUiState(
            owned = owned,
            members = owned.filter { it.save.characterId in formed },
            formation = formation,
        )
    }

    /** 编队切换互斥（防连点；U2 修复后服务层已临界区串行，此处再挡 UI 抖动）。 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    /** 加入/移出编队。Success 静默（编队条即时刷新即反馈）。 */
    fun toggleFormation(id: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                when (service.toggleFormation(id)) {
                    WriteOutcome.Success -> Unit
                    // 列表内的角色必定已拥有，Rejected 只剩「编队已满」一种语义
                    WriteOutcome.Rejected ->
                        _toasts.send("编队已满（${SaveData.MAX_FORMATION_SIZE} 人），请先移出一名角色")
                    WriteOutcome.SaveFailed -> _toasts.send("保存失败，请重试")
                }
            } catch (_: Exception) {
                _toasts.send("操作异常，请重试")
            } finally {
                _busy.value = false
            }
        }
    }
}
