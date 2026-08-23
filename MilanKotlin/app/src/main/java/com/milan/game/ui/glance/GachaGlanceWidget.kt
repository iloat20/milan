package com.milan.game.ui.glance

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.milan.game.MainActivity
import com.milan.game.data.AndroidSaveProvider
import com.milan.game.data.SaveData
import com.milan.game.data.SaveManager
import com.milan.game.ui.formatCount
import java.util.Calendar

/**
 * Glance 桌面小组件（试验田·微创新）：展示「今日运势 / 前往召唤」入口。
 * I9 修复：不再静态占位——[provideGlance] 直接读 App 真实存档（SaveManager 载入永不抛异常），
 * 展示实际星尘/钻石/已拥有角色/主池保底进度，并按日给出确定性签文；
 * 点按经 actionStartActivity 打开 App，MainActivity 按 EXTRA_NAVIGATE 直达抽卡页。
 * provideGlance 运行在 Glance worker 后台线程，文件 IO 不阻塞主线程。
 */
class GachaGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val save = SaveManager(AndroidSaveProvider(context)).load()
        val pity = save.getGachaCounter(POOL_ID_MAIN)
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clickable(
                        // 参数以 intent extra 形式注入（key = EXTRA_NAVIGATE），MainActivity 读后直达抽卡页
                        actionStartActivity<MainActivity>(
                            actionParametersOf(
                                ActionParameters.Key<String>(MainActivity.EXTRA_NAVIGATE) to MainActivity.NAV_GACHA,
                            ),
                        ),
                    ),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text(
                    text = "MILAN · 今日运势",
                    style = TextStyle(color = ColorProvider(GOLD), fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = fortuneLine(save),
                    style = TextStyle(color = ColorProvider(TEXT_MAIN)),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "✦ 星尘 ${formatCount(save.softCurrency)} · ◆ 钻石 ${formatCount(save.hardCurrency)}",
                    style = TextStyle(color = ColorProvider(TEXT_SUB)),
                )
                Text(
                    text = "已拥有 ${save.ownedCharacters.size} 位角色 · 主池保底 $pity",
                    style = TextStyle(color = ColorProvider(TEXT_SUB)),
                )
            }
        }
    }

    private companion object {
        /** 主卡池 id（对齐 data.json PoolId，widget 不加载内容也拿得到保底计数）。 */
        const val POOL_ID_MAIN = "pool_main"
        val GOLD = 0xFFE8B84B.toInt()
        val TEXT_MAIN = 0xFFF3ECFF.toInt()
        val TEXT_SUB = 0xFFB7A6CF.toInt()
    }
}

/** 今日签文：保底/新手态优先，其余按日稳定（同一天内不跳变）。 */
private fun fortuneLine(save: SaveData): String = when {
    save.ownedCharacters.isEmpty() -> "✦ 召唤第一位伙伴吧"
    save.getGachaCounter("pool_main") >= 80 -> "保底将至，时机已成熟"
    save.getGachaCounter("pool_main") >= 50 -> "距保底 ${save.getGachaCounter("pool_main")} 抽，坚持"
    else -> {
        val seeds = listOf("星河低语，宜十连", "神谕示警，宜单抽", "诸神侧目，稳扎稳打", "命运之轮轻转", "天道昭昭，势如破竹")
        seeds[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % seeds.size]
    }
}

class GachaGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GachaGlanceWidget()
}
