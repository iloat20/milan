package com.milan.game.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
// NeonButton replaces TextButton for consistent neon-gold styling
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme

/**
 * 统一空状态占位组件（水墨国风）。
 *
 * - [icon]：顶部线性图标（可选，Material outlined；v3 §5.5 禁用 emoji 作图标）
 * - [title]：主标题（必填）
 * - [subtitle]：副标题（可选，Text2 色弱化）
 * - [actionText] / [onAction]：底部操作按钮文字与回调（可选）
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.Spacing.xl, vertical = AppTheme.Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AppTheme.Text3,
            )
            Spacer(Modifier.height(AppTheme.Spacing.lg))
        }

        Text(
            text = title,
            color = AppTheme.Text2,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        if (subtitle != null) {
            Spacer(Modifier.height(AppTheme.Spacing.sm))
            Text(
                text = subtitle,
                color = AppTheme.Text3,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(AppTheme.Spacing.lg))
            NeonButton(
                text = actionText,
                onClick = onAction,
                color = AppTheme.Gold,
            )
        }
    }
}
