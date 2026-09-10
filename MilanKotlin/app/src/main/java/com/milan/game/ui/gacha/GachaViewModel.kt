package com.milan.game.ui.gacha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.EconomySlice
import com.milan.game.services.GachaSlice
import com.milan.game.services.GameService
import com.milan.game.services.MetaSlice
import com.milan.game.services.PullOutcome
import com.milan.game.services.PullResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 抽卡页 ViewModel（2026-09-08 P1-6）。
 *
 * 职责：
 * - 持有演出状态（busy / reveal / results / summary / batch）
 * - 抽卡业务逻辑（余额校验 → pull → 演出编排）
 * - 演出结束/跳过
 * - 经济/保底/振动切片透传（Screen 不订 GameState.snapshot）
 *
 * 依赖由 [com.milan.game.di.AppGraph] 组合根注入（构造函数必填，无服务定位器默认参数）。
 */
class GachaViewModel(
    private val service: GameService,
) : ViewModel() {

    // ── 内容表与切片透传（消除 Screen 对进程单例的直接访问）──

    /** 全部卡池（内容定义，进程内不变）。 */
    val pools: List<com.milan.game.services.GachaPoolDataEntry> get() = service.pools

    /** 经济切片（余额）。 */
    val economy: StateFlow<EconomySlice> = service.economy

    /** 保底切片（pityByPool / UP 定轨）。 */
    val gachaSlice: StateFlow<GachaSlice> = service.gachaSlice

    /** 设置切片（振动开关）。 */
    val meta: StateFlow<MetaSlice> = service.meta

    /** 按 id 查角色内容（缺失返回 null）。 */
    fun character(characterId: String?): CharacterDataEntry? =
        characterId?.let { service.character(it) }

    // ── 演出状态 ──

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _reveal = MutableStateFlow(RevealUiState())
    internal val reveal: StateFlow<RevealUiState> = _reveal.asStateFlow()

    private val _results = MutableStateFlow<List<PullResult>>(emptyList())
    val results: StateFlow<List<PullResult>> = _results.asStateFlow()

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    private val _batch = MutableStateFlow(0)
    val batch: StateFlow<Int> = _batch.asStateFlow()

    /** 演出结束：展示结果、复位状态。 */
    fun finishReveal() {
        val list = _reveal.value.staged
        if (list != null) {
            _results.value = list
            _summary.value = buildSummary(list)
            _batch.value = _batch.value + 1
        }
        _reveal.value = RevealUiState(token = _reveal.value.token)
        _busy.value = false
    }

    /** 跳过演出：仅递增 token 作废挂起编排。 */
    fun skipReveal() {
        if (!_busy.value || !_reveal.value.visible) return
        _reveal.value = _reveal.value.copy(token = _reveal.value.token + 1)
    }

    /**
     * 抽卡入口：余额检查 → pull → 兜底 → 演出编排。
     *
     * [onBuzz] 由 Screen 注入触觉反馈（需要 Android Context）。
     * [onSfx] 由 Screen 注入音效播放。
     */
    fun doPull(
        tenPull: Boolean,
        poolId: String,
        singleCost: Int,
        tenCost: Int,
        softCurrency: Int,
        onBuzz: (bestRarity: Int) -> Unit,
        onSfx: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (_busy.value) return
        val cost = if (tenPull) tenCost else singleCost
        if (softCurrency < cost) {
            onError("星尘不足")
            return
        }
        _busy.value = true
        viewModelScope.launch {
            try {
                val outcome = service.pull(poolId, tenPull)
                val pulled = when (outcome) {
                    is PullOutcome.Success -> outcome.results
                    is PullOutcome.Rejected -> {
                        _busy.value = false
                        onError("抽卡失败，请重试")
                        return@launch
                    }
                    is PullOutcome.SaveFailed -> {
                        _busy.value = false
                        onError("保存失败，请重试")
                        return@launch
                    }
                }
                val best = pulled.maxByOrNull { it.rarity }
                if (best == null || best.characterId == null) {
                    _busy.value = false
                    onError("抽卡失败，请重试")
                    return@launch
                }
                val charId = best.characterId ?: return@launch.also { _busy.value = false; onError("抽卡失败，请重试") }
                val bestDef = service.character(charId)
                val token = _reveal.value.token + 1
                // 触觉反馈
                onBuzz(best.rarity)
                onSfx("gacha_pull")
                // 阶段一：蓄能
                val chargeMs = when (best.rarity) {
                    4 -> 900L; 3 -> 600L; 2 -> 420L; else -> 300L
                }
                val beamMs = when (best.rarity) {
                    4 -> 640L; 3 -> 520L; else -> 480L
                }
                val revealMs = when {
                    tenPull -> when (best.rarity) {
                        4 -> 4800L; 3 -> 4000L; else -> 3600L
                    }
                    else -> when (best.rarity) {
                        4 -> 3000L; 3 -> 2400L; 2 -> 1500L; else -> 1000L
                    }
                }
                _reveal.value = RevealUiState(
                    token = token,
                    staged = pulled,
                    def = bestDef,
                    rarity = best.rarity,
                    stage = RevealStage.Charge,
                )
                kotlinx.coroutines.delay(chargeMs)
                if (token != _reveal.value.token) { finishReveal(); return@launch }
                // 阶段二：光柱爆发
                _reveal.value = _reveal.value.copy(stage = RevealStage.Beam, flashVisible = true)
                kotlinx.coroutines.delay(beamMs)
                if (token != _reveal.value.token) { finishReveal(); return@launch }
                _reveal.value = _reveal.value.copy(flashVisible = false)
                kotlinx.coroutines.delay(120)
                if (token != _reveal.value.token) { finishReveal(); return@launch }
                // 阶段三：揭晓
                _reveal.value = _reveal.value.copy(
                    cardIn = true,
                    visible = true,
                    stage = if (tenPull) RevealStage.Ten else RevealStage.Single,
                )
                onSfx("gacha_reveal")
                kotlinx.coroutines.delay(revealMs)
                if (token != _reveal.value.token) { finishReveal(); return@launch }
                // 阶段四：结果
                finishReveal()
            } catch (e: Exception) {
                _busy.value = false
                onError("抽卡异常，请重试")
            }
        }
    }

    /** 摘要行文案。 */
    private fun buildSummary(list: List<PullResult>): String {
        if (list.isEmpty()) return ""
        val ssr = list.count { it.rarity >= 3 }
        val frags = list.sumOf { it.fragmentsAwarded }
        val latest = list.maxByOrNull { it.rarity }?.characterName ?: ""
        val fragPart = if (frags > 0) " · 星魂碎片 +$frags" else ""
        return "共 ${list.size} 抽 · SSR+ $ssr$fragPart ✦ 最新: $latest"
    }
}
