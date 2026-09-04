package com.milan.game.ui.affinity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.services.AffinityFormulas
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 角色好感度界面。
 *
 * 对标原神/崩铁的好感度系统：
 * - 角色列表 + 好感度等级
 * - 好感度等级奖励预览
 * - 累计好感度经验
 *
 * 2026-09-02 完整闭环：
 * - 「赠送礼物」入口（100 星尘 → +200 好感，数值见 [AffinityFormulas]）——
 *   此前信息卡宣称「通过赠送礼物、出战战斗提升」但页面无任何入口，好感度恒为 0
 *   （服务层 addCharacterAffinity 零生产调用点）。
 * - 好感数据随 [GameState.snapshot] revision 重算——此前 `remember { }` 一次性取值，
 *   赠送后界面永不刷新（与 C2 同模式：无 key remember + 不订阅快照）。
 */
@Composable
fun AffinityScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
) {
    val service = GameState.service
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val snapshot by service.snapshot.collectAsState()
    val characters = remember { service.characters }
    // 好感数据不在 snapshot 字段内：随 revision 重算，任何写操作（赠送/战斗/剧情）落盘后
    // refreshSnapshot 的 revision+1 都会触发本页重组刷新。
    val affinityData = remember(snapshot.revision) { service.getCharacterAffinityData() }
    val softCurrency = snapshot.softCurrency

    // 赠送处理：同一协程内调 suspend 写操作并给 Snackbar 反馈。
    val onGift: (String) -> Unit = { characterId ->
        scope.launch {
            try {
                when (service.giftAffinity(characterId)) {
                    WriteOutcome.Success -> feedback.show(
                        "好感 +${AffinityFormulas.GIFT_AFFINITY_AMOUNT}（扣除 ${AffinityFormulas.GIFT_COST_SOFT} 星尘）",
                    )
                    WriteOutcome.Rejected -> {
                        val current = service.getCharacterAffinityData()[characterId] ?: 0
                        val reason = if (current >= AffinityFormulas.MAX_AFFINITY) {
                            "该角色好感已满级"
                        } else {
                            "星尘不足（赠送需 ${AffinityFormulas.GIFT_COST_SOFT}）"
                        }
                        feedback.show(reason)
                    }
                    WriteOutcome.SaveFailed -> feedback.show("保存失败，请重试")
                }
            } catch (_: Exception) {
                feedback.show("操作异常，请重试")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BgDeepest)
    ) {
        PageBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(title = "好感度", onBack = onBack)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    // 说明
                    item {
                        AffinityInfoCard()
                    }

                    // 角色好感度列表
                    items(characters.filter { service.getSave(it.characterId) != null }) { char ->
                        val affinity = affinityData[char.characterId] ?: 0
                        AffinityCard(
                            characterId = char.characterId,
                            name = char.displayName,
                            rarity = char.baseRarity,
                            affinity = affinity,
                            giftEnabled = affinity < AffinityFormulas.MAX_AFFINITY &&
                                softCurrency >= AffinityFormulas.GIFT_COST_SOFT,
                            onGift = { onGift(char.characterId) },
                            onClick = { onOpenCharacter(char.characterId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 好感度说明卡片。
 */
@Composable
private fun AffinityInfoCard() {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "好感度系统",
                color = AppTheme.Text1,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "通过赠送礼物、出战战斗提升角色好感度，解锁专属剧情和奖励。",
                color = AppTheme.Text2,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            // 等级奖励预览
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AffinityLevelReward(level = 1, reward = "角色语音")
                AffinityLevelReward(level = 3, reward = "专属剧情")
                AffinityLevelReward(level = 5, reward = "头像框")
                AffinityLevelReward(level = 8, reward = "限定皮肤")
                AffinityLevelReward(level = 10, reward = "专属称号")
            }
        }
    }
}

/**
 * 好感度等级奖励预览。
 */
@Composable
private fun AffinityLevelReward(level: Int, reward: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AppTheme.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$level",
                color = AppTheme.Gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = reward,
            color = AppTheme.Text3,
            fontSize = 8.sp,
        )
    }
}

/**
 * 单个角色好感度卡片。
 *
 * 右侧操作区（2026-09-02）：已满级 → 徽章；未满级 → 「赠送」按钮（100 星尘 +200 好感）。
 * [giftEnabled] 由外部按「未满级 && 星尘足够」计算；禁用时按钮置灰仍可点
 * （点击给出针对性 Rejected 文案，如余额不足提示）。
 */
@Composable
private fun AffinityCard(
    characterId: String,
    name: String,
    rarity: Int,
    affinity: Int,
    giftEnabled: Boolean,
    onGift: () -> Unit,
    onClick: () -> Unit,
) {
    val level = AffinityFormulas.levelOf(affinity)
    val expInLevel = AffinityFormulas.expInLevel(affinity)

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 角色立绘
            PortraitImage(
                characterId = characterId,
                rarity = rarity,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                name = name,
            )

            Spacer(Modifier.width(12.dp))

            // 角色信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        color = AppTheme.Text1,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "好感 Lv.$level",
                        color = AppTheme.Frost,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 好感度进度条
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { expInLevel / AffinityFormulas.EXP_PER_LEVEL.toFloat() },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AppTheme.Frost,
                        trackColor = AppTheme.Surface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$expInLevel / ${AffinityFormulas.EXP_PER_LEVEL}",
                        color = AppTheme.Text3,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // 右侧操作区：满级徽章 or 赠送按钮
            if (level >= AffinityFormulas.MAX_LEVEL) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = "❤️", fontSize = 16.sp)
                    Text(
                        text = "已满级",
                        color = AppTheme.Text3,
                        fontSize = 9.sp,
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (giftEnabled) AppTheme.Gold.copy(alpha = 0.18f) else AppTheme.Surface
                            )
                            .clickable(
                                enabled = true, // 禁用态仍可点，走 Rejected 提示（余额/满级原因）
                                onClick = onGift,
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "🎁 赠送",
                            color = if (giftEnabled) AppTheme.Gold else AppTheme.Text3,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "${AffinityFormulas.GIFT_COST_SOFT} 星尘",
                        color = AppTheme.Text3,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}
