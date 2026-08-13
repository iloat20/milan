package com.milan.game.ui.shop

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.theme.AppTheme
import java.util.Locale
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
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }
    // 资源快照：任何成功写操作后自动刷新（含本页购买与其它页面的经济变动）
    val snap by service.snapshot.collectAsStateWithLifecycle()
    val soft = snap.softCurrency
    val hard = snap.hardCurrency
    val frags = snap.starFragments

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }
    // 2026-08 主线程 IO 异步化：购买为 suspend（落盘在 IO 线程），用页面协程调用
    val scope = rememberCoroutineScope()

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
                ResourcePanel(soft = soft, hard = hard, frags = frags)

                SectionTitle("碎 片 补 给")
                FragmentPackCard(
                    pack = 1,
                    name = "小包 · 星魂碎片",
                    size = EconomyFormulas.fragmentPackSize(1),
                    cost = EconomyFormulas.fragmentPackCost(1),
                    affordable = soft >= EconomyFormulas.fragmentPackCost(1),
                    onBuy = {
                        scope.launch {
                            toast = when (service.buyFragmentPack(1)) {
                                WriteOutcome.Success -> "已获得 ${EconomyFormulas.fragmentPackSize(1)} 片星魂碎片"
                                WriteOutcome.Rejected -> "星尘不足"
                                WriteOutcome.SaveFailed -> "保存失败，请重试"
                            }
                        }
                    },
                )
                FragmentPackCard(
                    pack = 2,
                    name = "大包 · 星魂碎片",
                    size = EconomyFormulas.fragmentPackSize(2),
                    cost = EconomyFormulas.fragmentPackCost(2),
                    affordable = soft >= EconomyFormulas.fragmentPackCost(2),
                    onBuy = {
                        scope.launch {
                            toast = when (service.buyFragmentPack(2)) {
                                WriteOutcome.Success -> "已获得 ${EconomyFormulas.fragmentPackSize(2)} 片星魂碎片"
                                WriteOutcome.Rejected -> "星尘不足"
                                WriteOutcome.SaveFailed -> "保存失败，请重试"
                            }
                        }
                    },
                )

                SectionTitle("钻 石 商 城")
                DiamondCard(
                    cost = EconomyFormulas.diamondExchangeCost(),
                    yield = EconomyFormulas.diamondExchangeYield(),
                    affordable = hard >= EconomyFormulas.diamondExchangeCost(),
                    onExchange = {
                        scope.launch {
                            toast = when (service.buyDiamondExchange()) {
                                WriteOutcome.Success -> "已兑换 ${EconomyFormulas.diamondExchangeYield()} 星尘"
                                WriteOutcome.Rejected -> "钻石不足"
                                WriteOutcome.SaveFailed -> "保存失败，请重试"
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

/** 资源一览：星尘 / 钻石 / 星魂碎片三行（符号约定与养成页一致：✦ 星尘、❖ 碎片、◆ 钻石）。 */
@Composable
private fun ResourcePanel(soft: Int, hard: Int, frags: Int) {
    GlassPanel {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResourceRow("星尘", "✦", n0(soft), AppTheme.Gold)
            ResourceRow("钻石", "◆", n0(hard), AppTheme.GoldHi)
            ResourceRow("星魂碎片", "❖", n0(frags), AppTheme.Frost)
        }
    }
}

@Composable
private fun ResourceRow(label: String, symbol: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppTheme.Text2, fontSize = 13.sp)
        Text("$symbol $value", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    onBuy: () -> Unit,
) {
    GlassPanel(gold = pack == 2) {
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
                Text("${n0(cost)} ✦", color = AppTheme.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                GoldButton(text = "购 买", onClick = onBuy, enabled = affordable)
            }
        }
    }
}

/** 钻石兑换星尘卡片：钻石暂无获取途径，余额不足时按钮禁用并提示。 */
@Composable
private fun DiamondCard(
    cost: Int,
    yield: Int,
    affordable: Boolean,
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
                Text("${n0(cost)} ◆ → ${n0(yield)} ✦", color = AppTheme.Text2, fontSize = 12.sp)
                Text("钻石暂无获取途径", color = AppTheme.Text2.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            GoldButton(text = "兑 换", onClick = onExchange, enabled = affordable)
        }
    }
}

/** 千分位格式化（与养成页 n0 同一口径）。 */
private fun n0(v: Int): String = String.format(Locale.US, "%,d", v)
