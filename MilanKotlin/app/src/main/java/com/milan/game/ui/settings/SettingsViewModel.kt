package com.milan.game.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.ai.AIRecommendationEngine
import com.milan.game.ai.CharacterRecommendation
import com.milan.game.ai.GachaRecommendation
import com.milan.game.services.GameService
import com.milan.game.services.MetaSlice
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel：写操作（开关/重置）与 AI 推荐派生。
 *
 * 平台副作用（音量、WorkManager、通知权限）留在 Screen，经回调注入——
 * VM 只表达「用户意图 → 服务结果」，可纯 JVM 单测。
 */
class SettingsViewModel(
    private val service: GameService,
) : ViewModel() {

    /** 设置三开关（订阅 meta 切片，无关字段变化不重组）。 */
    val meta: StateFlow<MetaSlice> = service.meta

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    /** 重置确认后由 Screen 调用；成功返回 true（UI 可提示已重置）。 */
    private val _resetDone = Channel<Boolean>(Channel.BUFFERED)
    val resetDone: Flow<Boolean> = _resetDone.receiveAsFlow()

    private fun runWrite(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun mapWrite(outcome: WriteOutcome, failHint: String = "设置失败") {
        when (outcome) {
            WriteOutcome.Success -> Unit
            WriteOutcome.Rejected -> _toasts.send(failHint)
            WriteOutcome.SaveFailed -> _toasts.send("保存失败，请重试")
        }
    }

    /** 音效开关。[onSuccess] 在落盘成功后由 Screen 应用 MilanAudio 音量。 */
    fun setSoundEnabled(enabled: Boolean, onSuccess: (Boolean) -> Unit) = runWrite {
        val outcome = service.setSoundEnabled(enabled)
        mapWrite(outcome)
        if (outcome == WriteOutcome.Success) onSuccess(enabled)
    }

    fun setVibrationEnabled(enabled: Boolean) = runWrite {
        mapWrite(service.setVibrationEnabled(enabled))
    }

    /** 动效减弱（无障碍）：true=关闭高负载演出。 */
    fun setReduceMotionEnabled(enabled: Boolean) = runWrite {
        mapWrite(service.setReduceMotionEnabled(enabled))
    }

    /**
     * 推送开关。[onSuccess] 由 Screen 排程 WorkManager / 请求通知权限
     *（权限拒绝不回滚开关，与原实现一致）。
     */
    fun setPushEnabled(enabled: Boolean, onSuccess: (Boolean) -> Unit) = runWrite {
        val outcome = service.setPushEnabled(enabled)
        mapWrite(outcome)
        if (outcome == WriteOutcome.Success) onSuccess(enabled)
    }

    fun resetSave() = runWrite {
        _resetDone.send(service.resetSave())
    }

    /** 角色培养推荐（内容+存档派生，无写路径）。 */
    fun recommendCharacters(): List<CharacterRecommendation> =
        AIRecommendationEngine.recommendCharactersToLevelUp(save = service.saveData, service = service)

    /** 抽卡策略推荐。 */
    fun recommendGacha(): GachaRecommendation? =
        AIRecommendationEngine.recommendGachaStrategy(save = service.saveData, service = service)
}
