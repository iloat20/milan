package com.milan.game.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.services.DailyOffer
import com.milan.game.services.DailyOfferKind
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 商店页 UI 状态（经济四资源 + 每日特惠与已购槽位，随快照 revision 重算）。 */
data class ShopUiState(
    val softCurrency: Int,
    val hardCurrency: Int,
    val starFragments: Int,
    val battleTickets: Int,
    val dailyOffers: List<DailyOffer>,
    val dailyBought: List<Int>,
)

/**
 * 商店页 ViewModel（2026-09-08 P1-6 C 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - 经济四资源原订阅 `service.economy` 切片、每日特惠原 `remember(snap.revision)` 重算——
 *   现统一为订阅 `service.snapshot` 每次写提交重算（语义等价：任何写提交都会推进 revision，
 *   且 MutableStateFlow 值相等时跳过发射，比旧 remember 更省重组）。
 * - 四类写动作（每日特惠 / 碎片包 / 碎片兑换 / 钻石兑换）共用同一 busy 防重入，
 *   三态反馈走 [toasts]（Composable 转发 LocalFeedback）。
 */
class ShopViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): ShopUiState {
        val eco = service.economy.value
        return ShopUiState(
            softCurrency = eco.softCurrency,
            hardCurrency = eco.hardCurrency,
            starFragments = eco.starFragments,
            battleTickets = eco.battleTickets,
            dailyOffers = service.dailyOffers(),
            dailyBought = service.dailyBoughtToday(),
        )
    }

    /** 购买互斥（防连点/防重入），四类购买/兑换共用（与原实现同一 busy 语义）。 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    /** 每日特惠购买/领取（成功文案按特惠类型区分，照搬原 Screen 逻辑）。 */
    fun buyDailyOffer(offer: DailyOffer) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val msg = when (service.buyDailyOffer(offer.index)) {
                    WriteOutcome.Success -> when (offer.kind) {
                        DailyOfferKind.FREE_SUPPLY -> "每日补给已领取"
                        DailyOfferKind.DISCOUNT_PACK ->
                            "已获得 ${EconomyFormulas.fragmentPackSize(offer.pack)} 片星魂碎片"
                        DailyOfferKind.TICKET_BUNDLE ->
                            "已获得 ${EconomyFormulas.dailyTicketBundleSize()} 张战票"
                    }
                    WriteOutcome.Rejected -> "星尘不足或今日已购"
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                _toasts.send(msg)
            } finally {
                _busy.value = false
            }
        }
    }

    /** 碎片包购买（pack = 1 小包 / 2 大包）。 */
    fun buyFragmentPack(pack: Int) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val msg = when (service.buyFragmentPack(pack)) {
                    WriteOutcome.Success -> "已获得 ${EconomyFormulas.fragmentPackSize(pack)} 片星魂碎片"
                    WriteOutcome.Rejected -> "星尘不足"
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                _toasts.send(msg)
            } finally {
                _busy.value = false
            }
        }
    }

    /** 碎片兑换星尘（批量回收，回收价低于购入价）。 */
    fun exchangeFragmentsForSoft() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val msg = when (service.exchangeFragmentsForSoft()) {
                    WriteOutcome.Success -> "已兑换 ${EconomyFormulas.fragmentExchangeYield()} 星尘"
                    WriteOutcome.Rejected ->
                        "碎片不足（需 ${EconomyFormulas.fragmentExchangeBatch()} 片）"
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                _toasts.send(msg)
            } finally {
                _busy.value = false
            }
        }
    }

    /** 钻石兑换星尘。 */
    fun buyDiamondExchange() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val msg = when (service.buyDiamondExchange()) {
                    WriteOutcome.Success -> "已兑换 ${EconomyFormulas.diamondExchangeYield()} 星尘"
                    WriteOutcome.Rejected -> "钻石不足"
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                _toasts.send(msg)
            } finally {
                _busy.value = false
            }
        }
    }
}
