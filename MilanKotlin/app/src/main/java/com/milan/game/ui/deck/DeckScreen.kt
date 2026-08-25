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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.data.SaveData
import com.milan.game.ui.GameState
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.CharacterCard
import com.milan.game.ui.components.FormationBar
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.theme.AppTheme

/**
 * 卡组屏（P1-3 新建，替换原「建设中」占位页）：
 * 已拥有角色 2 列网格，点卡片弹出立绘大图预览（对标 Forge 悬浮大卡预览），
 * 预览层提供「查看详情」入口；空态引导前往寻访。底部导航常驻（主 tab 页）。
 */
@Composable
fun DeckScreen(
    onNav: (NavItem) -> Unit,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val owned = remember { GameState.owned() }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val preview = owned.firstOrNull { it.save.characterId == previewId }

    // ── 编队状态（2026-08 编队系统）：快照驱动，setFormation 成功后经 refreshSnapshot 回流重组 ──
    val snapshot by GameState.service.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val members = remember(snapshot.revision) {
        val formed = snapshot.formation.toSet()
        owned.filter { it.save.characterId in formed }
    }
    val toggleFormation: (String) -> Unit = { id ->
        val current = GameState.service.getFormation()
        val next = when (id) {
            in current -> current - id
            else -> if (current.size < SaveData.MAX_FORMATION_SIZE) current + id else current
        }
        if (next != current) scope.launch { GameState.service.setFormation(next) }
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
                fontSize = 14.sp,
                color = AppTheme.Text2,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(12.dp))

            // 出战编队（2026-08 编队系统）：点预览层「加入/移出编队」维护；槽位条只读展示
            if (owned.isNotEmpty()) {
                FormationBar(
                    members = members,
                    maxSlots = SaveData.MAX_FORMATION_SIZE,
                    onSlotClick = { onOpenDeckSlot ->
                        // 空槽点击不动作；有角色槽点击进预览（与网格卡片同语义）
                        if (onOpenDeckSlot != null) previewId = onOpenDeckSlot
                    },
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            if (owned.isEmpty()) {
                // 空态：无角色时引导前往寻访（对标 CharacterListScreen 空态语义）
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
                            fontSize = 14.sp,
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 13.dp, end = 13.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // P3-3：Lazy 容器补稳定 key（角色 Id），避免槽位复用导致筛选/状态错乱
                    itemsIndexed(owned, key = { _, ch -> ch.save.characterId }) { _, ch ->
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
                                    fontSize = 11.sp,
                                    color = AppTheme.Gold,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            },
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

        // 立绘大图预览层（I12：抽出为 DeckPreviewOverlay，收窄主函数职责）
        // P-编队：预览层提供「加入/移出编队」快捷入口（2026-08 编队系统）
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
 * 立绘大图预览层（I12：从 DeckScreen 主函数抽出）：
 * 全屏遮罩 + 稀有度光晕，点空白关闭；「查看详情」进详情页。
 * P0-C6：预览态拦截系统返回（物理/手势），与「点空白关闭」同源退出，避免返回键穿透到列表。
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
            // 稀有度光晕（径向渐变全屏，与 Gacha reveal 同语言）
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(rc.copy(alpha = 0.45f), Color.Transparent))),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 大立绘卡 220x312（与 Gacha reveal 卡片同尺寸语言）
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
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.Text1,
                        )
                    }
                    // 稀有度 + 元素徽章行（与大卡卡面同语言）
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = AppTheme.rarityName(preview.rarity),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = rc,
                        )
                        Spacer(Modifier.width(8.dp))
                        val ei = com.milan.game.ui.theme.ElementTheme.forElement(preview.element)
                        Text(
                            text = "${ei.glyph} ${preview.element}",
                            fontSize = 10.sp,
                            color = ei.glow,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .border(1.dp, ei.glow.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                // 编队快捷入口（2026-08）：加入/移出编队；成功后经快照回流刷新按钮态
                if (onToggleFormation != null) {
                    NeonButton(
                        text = if (inFormation) "移出编队" else "加入编队",
                        onClick = onToggleFormation,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                // 查看详情：先关预览层再进详情页（预览层与详情页不同屏，无 sharedBounds 配对）
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
                    fontSize = 12.sp,
                    color = AppTheme.Text3,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/**
 * 卡组卡片（已收敛至共享组件 CharacterCard，R1/I1）：星级 footer 走参数插槽；
 * 共享元素过渡（animatedVisibilityScope 由 MainActivity 传入）随统一实现一并补回。
 */
