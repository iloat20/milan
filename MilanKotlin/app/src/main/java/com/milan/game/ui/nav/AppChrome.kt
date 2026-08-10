package com.milan.game.ui.nav

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.GameState
import com.milan.game.ui.theme.AppTheme
import java.text.NumberFormat

/**
 * 统一顶栏（子页面用）：返回箭头 + 标题 + 资源胶囊（C# AppChrome.AppTopBar 翻译）。
 * 主页不需要。返回动作由宿主决定（切回主页 / 收起子页）。
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showResource: Boolean = true,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 返回箭头（P2-7 无障碍：48dp 尺寸保证触控热区 ≥48dp，等效 minimumInteractiveComponentSize）
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
            )
        }
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Text1,
            letterSpacing = 0.5.sp,
            style = TextStyle(
                shadow = Shadow(
                    color = AppTheme.Gold.copy(alpha = 0.6f),
                    blurRadius = 8f,
                    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                ),
            ),
        )
        if (showResource) {
            Spacer(Modifier.weight(1f))
            ResourceBar()
        }
    }
}

/**
 * 资源胶囊：星尘（金 ✦）+ 钻石（青 ❖）两项（C# AppChrome.ResourceBar 翻译）。
 * 主页与子页面复用；数值在宿主重组时自动刷新。
 */
@Composable
fun ResourceBar(modifier: Modifier = Modifier) {
    val dust = GameState.currency
    val gems = GameState.service.saveData.hardCurrency

    Row(
        modifier = modifier
            .background(AppTheme.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Chip("✦", dust, AppTheme.Gold)
        Spacer(Modifier.width(10.dp))
        Chip("❖", gems, AppTheme.Frost)
    }
}

/** 单资源条目：字形 + 数值（C# Chip）。数值变化时 400ms 滚动动画（P1-4 数值反馈）。 */
@Composable
private fun Chip(glyph: String, value: Int, color: Color) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(400),
        label = "chipValue",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = glyph,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = NumberFormat.getIntegerInstance().format(animated.toLong()),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Text1,
        )
    }
}
