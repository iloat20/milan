package com.milan.game.ui.components

// 从 CharacterDetailScreen.kt / ProgressionScreen.kt 提取的角色页共享组件。
// 提取条件：两页定义逐字节相同（diff 判定，见 2026-08-07-refactor-elegance 提交② PR 对比表）。
// 判定为「不同」而保留私有的：HeroRegion（detail 版多 owned/onOpenProgression/未拥有遮罩/养成入口，
// 渐隐 150 vs 140dp）；switch（两页均为页面主函数内部局部函数，不可提取）。

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            .background(Brush.verticalGradient(listOf(AppTheme.BgMid, AppTheme.BgDeepest))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("未找到该角色", fontSize = 16.sp, color = AppTheme.Text2)
            Text(
                "‹ 返 回",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppTheme.Surface)
                    .border(1.dp, AppTheme.Gold.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .clickable(onClick = onBack),
            )
        }
    }
}

/** 左右切换箭头（C# BuildArrow：44dp 圆角玻璃按钮 + 金色高光阴影）。 */
@Composable
fun GlassArrow(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            .clickable(onClick = onClick),
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
            .background(Brush.verticalGradient(listOf(Color(0x000B0612), Color(0xBE07040F))))
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
