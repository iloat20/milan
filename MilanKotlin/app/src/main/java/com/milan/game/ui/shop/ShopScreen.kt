package com.milan.game.ui.shop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.services.DailyOffer
import com.milan.game.services.DailyOfferKind
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.formatCount
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 商店页（C# ShopPage 翻译）。
 *
 * 结构：资源一览（星尘/钻石/星魂碎片）→ 碎片补给（星尘兑换碎片，小包/大包）→ 钻石商城（钻石兑换星尘）。
 * 定价一律走 [EconomyFormulas]（单一事实来源）；购买走 GameService 事务方法（预算校验 → 落盘 → 失败回滚）。
 * 资源数字订阅 [GameState.snapshot]（StateFlow，2026-08 现代化）自动刷新，
 * 替代此前「EventBus 订阅 + 手动重读」的轻标记模式。
 */
@Composable
fun ShopScreen(
    onNav: (NavItem) -> Unit,
) {
    val service = GameState.service
    // R3/I4：反馈统一走 LocalFeedback（由 MainActivity 提供的 Snackbar 宿主）。
    val feedback = LocalFeedback.current
    // I13：in-flight 防重入——购买/兑换落盘期间禁用按钮，避免快速双击重复扣费。
    var busy by remember { mutableStateOf(false) }
    // 资源快照：任何成功写操作后自动刷新（含本页购买及其它页面的经济变动）
    val snap by service.snapshot.collectAsStateWithLifecycle()
    val soft = snap.softCurrency
    val hard = snap.hardCurrency
    val frags = snap.starFragments
    val tickets = snap.battleTickets

    // 每日特惠（2026-08 二期）：offers 由日期种子确定性生成；bought 随快照 revision 刷新
    val dailyOffers = remember(snap.revision) { service.dailyOffers() }
    val dailyBought = remember(snap.revision) { service.dailyBoughtToday() }

    // 2026-08 主线程 IO 异步化：购买为 suspend（落盘在 IO 线程），用页面协程调用
    val scope = rememberCoroutineScope()

    /** 每日特惠购买通用流程（busy 防重入 + Snackbar 反馈）。 */
    fun buyDaily(index: Int, successMsg: String) {
        scope.launch {
            if (busy) return@launch
            busy = true
            try {
                val msg = when (service.buyDailyOffer(index)) {
                    WriteOutcome.Success -> successMsg
                    WriteOutcome.Rejected -> "星尘不足或今日已购"
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                feedback.show(msg)
            } finally {
                busy = false
            }
        }
    }

    PageBackground {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "商 店", onBack = { onNav(NavItem.Home) })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ResourcePanel(soft = soft, hard = hard, frags = frags, tickets = tickets)

                SectionTitle("每 日 特 惠")
                for (offer in dailyOffers) {
                    val bought = offer.index in dailyBought
                    DailyOfferCard(
                        offer = offer,
                        bought = bought,
                        affordable = offer.costSoft == 0 || soft >= offer.costSoft,
                        enabled = !busy,
                        onBuy = {
                            buyDaily(
                                index = offer.index,
                                successMsg = when (offer.kind) {
                                    DailyOfferKind.FREE_SUPPLY -> "每日补给已领取"
                                    DailyOfferKind.DISCOUNT_PACK ->
                                        "已获得 ${EconomyFormulas.fragmentPackSize(offer.pack)} 片星魂碎片"
                                    DailyOfferKind.TICKET_BUNDLE ->
                                        "已获得 ${EconomyFormulas.dailyTicketBundleSize()} 张战票"
                                },
                            )
                        },
                    )
                }

                SectionTitle("碎 片 补 给")
                for (pack in 1..2) {
                    FragmentPackCard(
                        pack = pack,
                        name = if (pack == 1) "小包 · 星魂碎片" else "大包 · 星魂碎片",
                        size = EconomyFormulas.fragmentPackSize(pack),
                        cost = EconomyFormulas.fragmentPackCost(pack),
                        affordable = soft >= EconomyFormulas.fragmentPackCost(pack),
                        enabled = !busy,
                        onBuy = {
                            scope.launch {
                                if (busy) return@launch
                                busy = true
                                try {
                                    val msg = when (service.buyFragmentPack(pack)) {
                                        WriteOutcome.Success -> "已获得 ${EconomyFormulas.fragmentPackSize(pack)} 片星魂碎片"
                                        WriteOutcome.Rejected -> "星尘不足"
                                        WriteOutcome.SaveFailed -> "保存失败，请重试"
                                    }
                                    feedback.show(msg)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                }

                SectionTitle("钻 石 商 城")
                DiamondCard(
                    cost = EconomyFormulas.diamondExchangeCost(),
                    gain = EconomyFormulas.diamondExchangeYield(),
                    affordable = hard >= EconomyFormulas.diamondExchangeCost(),
                    enabled = !busy,
                    onExchange = {
                        scope.launch {
                            if (busy) return@launch
                            busy = true
                            try {
                                val msg = when (service.buyDiamondExchange()) {
                                    WriteOutcome.Success -> "已兑换 ${EconomyFormulas.diamondExchangeYield()} 星尘"
                                    WriteOutcome.Rejected -> "钻石不足"
                                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                                }
                                feedback.show(msg)
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }
            GameNavBar(
                active = NavItem.Shop,
                onSelect = onNav,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** 资源一览：星尘 / 钻石 / 星魂碎片 / 战票四行（符号约定：✦ 星尘、❖ 碎片、◆ 钻石、⚔ 战票）。 */
@Composable
private fun ResourcePanel(soft: Int, hard: Int, frags: Int, tickets: Int) {
    GlassPanel {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResourceRow("星尘", "✦", formatCount(soft), AppTheme.Gold)
            ResourceRow("钻石", "◆", formatCount(hard), AppTheme.GoldHi)
            ResourceRow("星魂碎片", "❖", formatCount(frags), AppTheme.Frost)
            ResourceRow("战票", "⚔", formatCount(tickets), AppTheme.Text1)
        }
    }
}

@Composable
private fun ResourceRow(label: String, symbol: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppTheme.Text2, fontSize = 13.sp)
        Text("$symbol $value", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 每日特惠卡片（2026-08 二期）：免费补给金边高亮；已购态禁用按钮并打标。 */
@Composable
private fun DailyOfferCard(
    offer: DailyOffer,
    bought: Boolean,
    affordable: Boolean,
    enabled: Boolean = true,
    onBuy: () -> Unit,
) {
    GlassPanel(highlighted = offer.kind == DailyOfferKind.FREE_SUPPLY) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(offer.title, color = AppTheme.Text1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (bought) {
                        Text(
                            text = if (offer.kind == DailyOfferKind.FREE_SUPPLY) "已领取" else "已购",
                            color = AppTheme.Text3,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .border(1.dp, AppTheme.Stroke, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(offer.detail, color = AppTheme.Text2, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (offer.costSoft == 0) "免费" else "${formatCount(offer.costSoft)} ✦",
                    color = AppTheme.Gold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                GoldButton(
                    text = when {
                        bought -> if (offer.kind == DailyOfferKind.FREE_SUPPLY) "已 领" else "已 购"
                        offer.kind == DailyOfferKind.FREE_SUPPLY -> "领 取"
                        else -> "购 买"
                    },
                    onClick = onBuy,
                    enabled = !bought && affordable && enabled,
                )
            }
        }
    }
}

/** 碎片包卡片：小包普通玻璃底，大包金边（批量优惠由 [EconomyFormulas] 定价保证）。 */
@Composable
private fun FragmentPackCard(
    pack: Int,
    name: String,
    size: Int,
    cost: Int,
    affordable: Boolean,
    enabled: Boolean = true,
    onBuy: () -> Unit,
) {
    GlassPanel(highlighted = pack == 2) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(name, color = AppTheme.Text1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("获得 $size 片星魂碎片", color = AppTheme.Text2, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatCount(cost)} ✦", color = AppTheme.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                GoldButton(text = "购 买", onClick = onBuy, enabled = affordable && enabled)
            }
        }
    }
}

/** 钻石兑换星尘卡片：钻石暂无获取途径，余额不足时按钮禁用并提示。 */
@Composable
private fun DiamondCard(
    cost: Int,
    gain: Int,
    affordable: Boolean,
    enabled: Boolean = true,
    onExchange: () -> Unit,
) {
    GlassPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("钻石兑换星尘", color = AppTheme.Text1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${formatCount(cost)} ◆ → ${formatCount(gain)} ✦", color = AppTheme.Text2, fontSize = 12.sp)
                Text("钻石暂无获取途径", color = AppTheme.Text2.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            GoldButton(text = "兑 换", onClick = onExchange, enabled = affordable && enabled)
        }
    }
}
