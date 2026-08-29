package com.milan.game.ui.components

// 从 CharacterDetailScreen.kt / ProgressionScreen.kt 提取的角色页共享组件。
// 2026-08 水墨国风重构：视觉风格从暗紫+熔金切换到墨色+金箔+朱砂。

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

/** 「‹ 返 回」金箔胶囊按钮（水墨国风版）。 */
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
 * 角色子页 Hero 区：立绘铺满 + 底部渐隐融入 + 浮层铭牌 + 悬浮操作。
 * 水墨国风版：底部渐隐用墨色，铭牌用宣纸白文字。
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
        PortraitImage(
            characterId = view.save.characterId,
            rarity = view.rarity,
            name = view.name,
            modifier = Modifier.fillMaxSize().then(portraitModifier),
            contentScale = ContentScale.Crop,
            aura = true,
        )

        // 底部渐隐遮罩：立绘下缘柔和融入墨色背景
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(fadeHeight)
                .background(Brush.verticalGradient(listOf(Color.Transparent, AppTheme.BgDeepest))),
        )

        // 未拥有遮罩
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

        // 悬浮操作：返回（左上）+ 左右切换（两侧）+ 养成入口（右上）
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

/** 左右切换箭头（水墨国风版：金箔箭头 + 墨色玻璃底）。 */
@Composable
fun GlassArrow(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
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

/** 底部浮层铭牌：稀有度徽章 + 名字 + 元素图标 + 称号。 */
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
            // 元素字形圆形图标
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

/** 面板标题：左金线 + 标题（水墨国风版）。 */
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

/** 行间细分隔（金箔 α24 发丝线）。 */
@Composable
fun WoWDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppTheme.Gold.copy(alpha = 24f / 255f)),
    )
}
