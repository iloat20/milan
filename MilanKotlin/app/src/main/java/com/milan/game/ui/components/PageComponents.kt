package com.milan.game.ui.components

// 从 CharacterDetailScreen.kt / ProgressionScreen.kt 提取的角色页共享组件。
// 提取条件：两页定义逐字节相同（diff 判定，见 2026-08-07-refactor-elegance 提交② PR 对比表）。
// 2026-08-27 前端统一（docs/plans/2026-08-27-frontend-unification-design.md）：
// HeroRegion 已参数化合并为 SubPageHero（fadeHeight/owned/onOpenProgression/portraitModifier 可配），
// 「‹ 返 回」胶囊统一为 BackCapsule；仅 switch 仍保留页面私有（主函数内部局部函数，不可提取）。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.theme.AppTheme

/** 角色不存在（C# Finish 的等价安全态）。 */
@Composable
fun MissingCharacter(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppTheme.BgDeepest, AppTheme.BgMid, AppTheme.BgDeepest))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("未找到该角色", fontSize = 16.sp, color = AppTheme.Text2)
            BackCapsule(onClick = onBack, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** 「‹ 返 回」金色胶囊按钮（详情/养成/空态三处原逐字复制，2026-08-27 统一）。 */
@Composable
fun BackCapsule(onClick: () -> Unit, modifier: Modifier = Modifier, text: String = "‹ 返 回") {
    Text(
        text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = AppTheme.Gold,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppTheme.Surface)
            .border(1.dp, AppTheme.Gold.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    )
}

/**
 * 角色子页 Hero 区：立绘铺满 + 底部渐隐融入 + 浮层铭牌 + 悬浮操作（返回 / 左右切换 / 养成入口）。
 * 由 CharacterDetailScreen 与 ProgressionScreen 的两份近重复 HeroRegion 合并而来
 * （差异全部参数化：[fadeHeight] 渐隐高度 150/140dp、[owned] 未拥有遮罩、
 * [onOpenProgression] 养成入口、[portraitModifier] 共享元素过渡 sharedBounds，缺省安全降级）。
 */
@Composable
fun SubPageHero(
    view: OwnedCharacterView,
    rarityCol: Color,
    eFrom: Color,
    eGlyph: String,
    heroHeight: Dp,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    fadeHeight: Dp = 140.dp,
    owned: Boolean = true,
    onOpenProgression: ((String) -> Unit)? = null,
    portraitModifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(heroHeight)) {
        // 立绘（C# Parallax3DPortraitView；P2 视差，先用静态铺满）
        PortraitImage(
            characterId = view.save.characterId,
            rarity = view.rarity,
            name = view.name,
            modifier = Modifier.fillMaxSize().then(portraitModifier),
            contentScale = ContentScale.Crop,
            aura = true, // v2：稀有度脚下光环（详情页主立绘）
        )

        // 底部渐隐遮罩：立绘下缘柔和融入背景
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(fadeHeight)
                .background(Brush.verticalGradient(listOf(Color.Transparent, AppTheme.BgDeepest))),
        )

        // 未拥有遮罩（详情页图鉴剪影浏览场景）
        if (!owned) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 150f / 255f)),
            )
            Text(
                "🔒 未获得",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text2,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        HeroNameplate(
            view = view,
            rarityCol = rarityCol,
            eFrom = eFrom,
            eGlyph = eGlyph,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 悬浮操作：返回（左上）+ 左右切换（两侧）+ 养成入口（右上，仅已拥有且提供回调）
        BackCapsule(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 40.dp),
        )
        GlassArrow("‹", Modifier.align(Alignment.CenterStart), onPrev, contentDescription = "上一个")
        GlassArrow("›", Modifier.align(Alignment.CenterEnd), onNext, contentDescription = "下一个")
        if (owned && onOpenProgression != null) {
            Text(
                "养 成 ▲",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppTheme.Surface)
                    .border(1.dp, AppTheme.Gold.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .clickable(onClick = { onOpenProgression(view.save.characterId) }),
            )
        }
    }
}

/** 左右切换箭头（C# BuildArrow：44dp 圆角玻璃按钮 + 金色高光阴影）。
 *  P2-6：补方向语义；M14：语义文案显式入参（默认按字形反推兜底）。 */
@Composable
fun GlassArrow(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
    // 捕获到独立名，避免 semantics 块内 receiver.contentDescription 与参数同名遮蔽（见 GameNavBar 同款坑）
    val desc = contentDescription ?: if (text == "‹") "上一个" else "下一个"
    Text(
        text,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = AppTheme.Gold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(AppTheme.Surface)
            .border(1.dp, AppTheme.Gold.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = desc },
    )
}

/** 底部浮层铭牌：稀有度徽章 + 名字 + 元素图标 + 称号（C# BuildHeroNameplate）。 */
@Composable
fun HeroNameplate(
    view: OwnedCharacterView,
    rarityCol: Color,
    eFrom: Color,
    eGlyph: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(156.dp)
            .background(Brush.verticalGradient(listOf(AppTheme.ScrimTop, AppTheme.ScrimBottom)))
            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 稀有度徽章：圆角 7 底色 α45 + 描边 α150
            Text(
                AppTheme.rarityName(view.rarity),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = rarityCol,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(rarityCol.copy(alpha = 45f / 255f))
                    .border(1.dp, rarityCol.copy(alpha = 150f / 255f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(
                view.name,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 160f / 255f),
                        offset = Offset(0f, 2f),
                        blurRadius = 8f,
                    ),
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            )
            // 元素字形圆形图标：圆 18 底色 α50
            Text(
                eGlyph,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = eFrom,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(eFrom.copy(alpha = 50f / 255f)),
            )
        }
        if (view.title.isNotBlank()) {
            Text(
                view.title,
                fontSize = 14.sp,
                color = rarityCol,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            )
        }
    }
}

/** 面板标题：左金线 + 标题（C# SectionTitle）。 */
@Composable
fun SectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppTheme.Gold),
        )
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Text1,
            letterSpacing = 0.18.em,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** 行间细分隔（金 α24 发丝线，C# WoWDivider）。 */
@Composable
fun WoWDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppTheme.Gold.copy(alpha = 24f / 255f)),
    )
}
