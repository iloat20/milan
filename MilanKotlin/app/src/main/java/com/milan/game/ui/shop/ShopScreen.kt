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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.services.DailyOffer
import com.milan.game.services.DailyOfferKind
import com.milan.game.ui.components.EntranceItem
import com.milan.game.ui.components.ArtifactPanel
import com.milan.game.ui.components.GlyphBadge
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.formatCount
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.CurrencyNames

/**
 * 商店（v4）：资源一览 → 每日特惠 → 补给/兑换。分区间距加大。
 */
@Composable
fun ShopScreen(
    onNav: (NavItem) -> Unit,
) {
    val feedback = LocalFeedback.current
    val vm: ShopViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }
    val soft = ui.softCurrency
    val hard = ui.hardCurrency
    val frags = ui.starFragments
    val tickets = ui.battleTickets
    val dailyOffers = ui.dailyOffers
    val dailyBought = ui.dailyBought

    PageBackground {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "商店", onBack = { onNav(NavItem.Home) })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                EntranceItem(index = 0) {
                    ResourcePanel(soft = soft, hard = hard, frags = frags, tickets = tickets)
                }

                SectionTitle("每日特惠")
                dailyOffers.forEachIndexed { i, offer ->
                    val bought = offer.index in dailyBought
                    EntranceItem(index = i + 1) {
                        DailyOfferCard(
                            offer = offer,
                            bought = bought,
                            affordable = offer.costSoft == 0 || soft >= offer.costSoft,
                            enabled = !busy,
                            onBuy = { vm.buyDailyOffer(offer) },
                        )
                    }
                }

                SectionTitle("残玦补给")
                for (pack in 1..2) {
                    EntranceItem(index = pack + 2) {
                        FragmentPackCard(
                            pack = pack,
                            name = if (pack == 1) "小包 · 残玦" else "大包 · 残玦",
                            size = EconomyFormulas.fragmentPackSize(pack),
                            cost = EconomyFormulas.fragmentPackCost(pack),
                            affordable = soft >= EconomyFormulas.fragmentPackCost(pack),
                            enabled = !busy,
                            onBuy = { vm.buyFragmentPack(pack) },
                        )
                    }
                }

                SectionTitle("残玦兑换")
                EntranceItem(index = 5) {
                    FragmentExchangeCard(
                        batch = EconomyFormulas.fragmentExchangeBatch(),
                        yield = EconomyFormulas.fragmentExchangeYield(),
                        frags = frags,
                        enabled = !busy,
                        onExchange = { vm.exchangeFragmentsForSoft() },
                    )
                }

                SectionTitle("纯环商城")
                EntranceItem(index = 6) {
                    DiamondCard(
                        cost = EconomyFormulas.diamondExchangeCost(),
                        gain = EconomyFormulas.diamondExchangeYield(),
                        affordable = hard >= EconomyFormulas.diamondExchangeCost(),
                        enabled = !busy,
                        onExchange = { vm.buyDiamondExchange() },
                    )
                }
            }
            GameNavBar(
                active = NavItem.Shop,
                onSelect = onNav,
            )
        }
    }
}

/** 资源一览：环痕 / 纯环 / 残玦 / 战票。 */
@Composable
private fun ResourcePanel(soft: Int, hard: Int, frags: Int, tickets: Int) {
    ArtifactPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ResourceRow("环痕", com.milan.game.ui.theme.CurrencyNames.SOFT_GLYPH, formatCount(soft), AppTheme.Gold, AppTheme.GoldDeep)
            ResourceRow("纯环", com.milan.game.ui.theme.CurrencyNames.HARD_GLYPH, formatCount(hard), AppTheme.GoldHi, AppTheme.Violet)
            ResourceRow("残玦", com.milan.game.ui.theme.CurrencyNames.FRAG_GLYPH, formatCount(frags), AppTheme.Frost, AppTheme.Violet)
            ResourceRow("战票", "⚔", formatCount(tickets), AppTheme.Text1, AppTheme.Text3)
        }
    }
}

@Composable
private fun ResourceRow(label: String, symbol: String, value: String, from: Color, to: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphBadge(glyph = symbol, from = from, to = to)
            Spacer(Modifier.width(10.dp))
            Text(label, color = AppTheme.Text2, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, color = from, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

/** 每日特惠卡片（织环 v3.1）：免费补给金边高亮；已购态禁用按钮并打标。 */
@Composable
private fun DailyOfferCard(
    offer: DailyOffer,
    bought: Boolean,
    affordable: Boolean,
    enabled: Boolean = true,
    onBuy: () -> Unit,
) {
    ArtifactPanel(highlighted = offer.kind == DailyOfferKind.FREE_SUPPLY) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(offer.title, color = AppTheme.Text1, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    if (bought) {
                        Text(
                            text = if (offer.kind == DailyOfferKind.FREE_SUPPLY) "已领取" else "已购",
                            color = AppTheme.Text3,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .border(1.dp, AppTheme.Stroke, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(offer.detail, color = AppTheme.Text2, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (offer.costSoft == 0) "免费" else "${formatCount(offer.costSoft)} ${CurrencyNames.SOFT_GLYPH}",
                    color = AppTheme.Gold,
                    style = MaterialTheme.typography.bodyLarge,
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

/** 碎片包卡片：织环台玻璃底，大包金边高亮。 */
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
    ArtifactPanel(highlighted = pack == 2) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(name, color = AppTheme.Text1, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("获得 $size 片残玦", color = AppTheme.Text2, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatCount(cost)} ${CurrencyNames.SOFT_GLYPH}", color = AppTheme.Gold, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                GoldButton(text = "购 买", onClick = onBuy, enabled = affordable && enabled)
            }
        }
    }
}

/** 纯环兑换环痕卡片。 */
@Composable
private fun DiamondCard(
    cost: Int,
    gain: Int,
    affordable: Boolean,
    enabled: Boolean = true,
    onExchange: () -> Unit,
) {
    ArtifactPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("纯环兑换环痕", color = AppTheme.Text1, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatCount(cost)} ${CurrencyNames.HARD_GLYPH} → ${formatCount(gain)} ${CurrencyNames.SOFT_GLYPH}",
                    color = AppTheme.Text2,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("纯环暂无获取途径", color = AppTheme.Text2.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            }
            GoldButton(text = "兑 换", onClick = onExchange, enabled = affordable && enabled)
        }
    }
}

/** 碎片兑换环痕卡片（织环 v3.1）。 */
@Composable
private fun FragmentExchangeCard(
    batch: Int,
    yield: Int,
    frags: Int,
    enabled: Boolean = true,
    onExchange: () -> Unit,
) {
    ArtifactPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("残玦兑换环痕", color = AppTheme.Text1, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatCount(batch)} ${CurrencyNames.FRAG_GLYPH} → ${formatCount(yield)} ${CurrencyNames.SOFT_GLYPH}",
                    color = AppTheme.Text2,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "回收价低于购入价（80/片 < 100/片），持有 ${formatCount(frags)} ${CurrencyNames.FRAG_GLYPH}",
                    color = AppTheme.Text2.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            GoldButton(text = "兑 换", onClick = onExchange, enabled = frags >= batch && enabled)
        }
    }
}
