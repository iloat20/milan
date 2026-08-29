package com.milan.game.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.data.SaveData
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.CharacterCard
import com.milan.game.ui.components.FormationBar
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.theme.AppTheme

/**
 * 卡组屏（水墨国风版）：
 * 已拥有角色 2 列网格，点卡片弹出立绘大图预览，
 * 预览层提供「查看详情」入口；空态引导前往寻访。底部导航常驻。
 */
@Composable
fun DeckScreen(
    onNav: (NavItem) -> Unit,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val snapshot by GameState.service.snapshot.collectAsStateWithLifecycle()
    val owned = remember(snapshot.revision) { GameState.owned() }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val preview = owned.firstOrNull { it.save.characterId == previewId }

    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val members = remember(snapshot.revision) {
        val formed = snapshot.formation.toSet()
        owned.filter { it.save.characterId in formed }
    }
    // U2（2026-08-28 审查修复）：读-改-写整体下沉到服务层（在 writeMutex 临界区内串行）。
    // 原实现在 UI 侧读 formation → 计算 next → 调 setFormation，跨锁执行存在竞态：
    // 快速连点时两次都基于同一份过期快照计算，后提交者覆盖前者，前一次点击被静默丢弃。
    val toggleFormation: (String) -> Unit = { id ->
        scope.launch {
            when (GameState.service.toggleFormation(id)) {
                WriteOutcome.Success -> Unit
                // 列表内的角色必定已拥有，Rejected 只剩「编队已满」一种语义
                WriteOutcome.Rejected -> feedback.show("编队已满（${GameState.maxFormationSize} 人），请先移出一名角色")
                WriteOutcome.SaveFailed -> feedback.show("保存失败，请重试")
            }
        }
    }
    val previewInFormation = preview != null && preview.save.characterId in snapshot.formation
    val previewToggle: (() -> Unit)? =
        preview?.save?.characterId?.let { id -> ({ toggleFormation(id) }) }

    PageBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "卡 组", onBack = { onNav(NavItem.Home) })
            Spacer(Modifier.height(14.dp))
            Text(
                text = "已拥有  ${owned.size}  位角色",
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.Text2,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(12.dp))

            if (owned.isNotEmpty()) {
                FormationBar(
                    members = members,
                    maxSlots = GameState.maxFormationSize,
                    onSlotClick = { onOpenDeckSlot ->
                        if (onOpenDeckSlot != null) previewId = onOpenDeckSlot
                    },
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            if (owned.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "✦",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.Gold.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "还没有角色，去寻访吧",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.Text3,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        NeonButton(
                            text = "前往寻访",
                            onClick = { onNav(NavItem.Gacha) },
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                }
            } else {
                val deckGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = deckGridState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 13.dp, end = 13.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(owned, key = { _, ch -> ch.save.characterId }, contentType = { _, _ -> "characterCard" }) { index, ch ->
                        val parallax by remember {
                            derivedStateOf {
                                val first = deckGridState.layoutInfo.visibleItemsInfo.firstOrNull()
                                    ?: return@derivedStateOf 0f
                                val last = deckGridState.layoutInfo.visibleItemsInfo.lastOrNull()
                                    ?: return@derivedStateOf 0f
                                val total = last.index - first.index + 1
                                val pos = (index - first.index).toFloat() / total.coerceAtLeast(1)
                                pos.coerceIn(-0.5f, 0.5f) * 2f
                            }
                        }
                        CharacterCard(
                            characterId = ch.save.characterId,
                            name = ch.name,
                            title = ch.title,
                            rarity = ch.rarity,
                            element = ch.element,
                            onClick = { previewId = ch.save.characterId },
                            animatedVisibilityScope = animatedVisibilityScope,
                            footer = {
                                Text(
                                    text = "★".repeat(ch.save.stars.coerceAtLeast(1)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppTheme.Gold,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            },
                            modifier = Modifier
                                .animateItem()
                                .graphicsLayer { translationY = parallax * 8f },
                        )
                    }
                }
            }

            GameNavBar(
                active = NavItem.Deck,
                onSelect = onNav,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        DeckPreviewOverlay(
            preview = preview,
            onClose = { previewId = null },
            onOpenCharacter = onOpenCharacter,
            inFormation = previewInFormation,
            onToggleFormation = previewToggle,
        )
    }
}

/**
 * 立绘大图预览层（水墨国风版）：
 * 全屏遮罩 + 稀有度光晕，点空白关闭；「查看详情」进详情页。
 */
@Composable
private fun DeckPreviewOverlay(
    preview: OwnedCharacterView?,
    onClose: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    inFormation: Boolean = false,
    onToggleFormation: (() -> Unit)? = null,
) {
    BackHandler(enabled = preview != null) { onClose() }
    if (preview != null) {
        val rc = AppTheme.rarityColor(preview.rarity)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.80f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            // 稀有度光晕
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(rc.copy(alpha = 0.45f), Color.Transparent))),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    modifier = Modifier
                        .size(width = 220.dp, height = 312.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(rc.copy(alpha = 0.34f), AppTheme.BgDeepest, AppTheme.BgDeepest),
                            ),
                        )
                        .border(2.dp, rc.copy(alpha = 0.85f), RoundedCornerShape(18.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PortraitImage(
                        characterId = preview.save.characterId,
                        rarity = preview.rarity,
                        name = preview.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        aura = true,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(rc.copy(alpha = 0.16f))
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = preview.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.Text1,
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = AppTheme.rarityName(preview.rarity),
                            style = MaterialTheme.typography.labelLarge,
                            color = rc,
                        )
                        Spacer(Modifier.width(8.dp))
                        val ei = com.milan.game.ui.theme.ElementTheme.forElement(preview.element)
                        Text(
                            text = "${ei.glyph} ${preview.element}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ei.glow,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .border(1.dp, ei.glow.copy(alpha = 0.9f), MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                if (onToggleFormation != null) {
                    NeonButton(
                        text = if (inFormation) "移出编队" else "加入编队",
                        onClick = onToggleFormation,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                NeonButton(
                    text = "查看详情",
                    onClick = {
                        val id = preview.save.characterId
                        onClose()
                        onOpenCharacter(id)
                    },
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = "点击空白处关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.Text3,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
