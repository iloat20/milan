package com.milan.game.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.milan.game.ui.theme.AppTheme

/**
 * 统一空状态：印记/图标 + 标题 + 可选操作。
 * [glyph] 优先（印章字形，对齐全站印记语言）；无 glyph 时用 [icon]。
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glyph: String? = null,
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
        when {
            glyph != null -> {
                GlyphBadge(
                    glyph = glyph,
                    from = AppTheme.Text3,
                    to = AppTheme.Stroke,
                    glyphColor = AppTheme.Text2,
                )
                Spacer(Modifier.height(AppTheme.Spacing.lg))
            }
            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = AppTheme.Text3,
                )
                Spacer(Modifier.height(AppTheme.Spacing.lg))
            }
        }

        Text(
            text = title,
            color = AppTheme.Text2,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        if (subtitle != null) {
            Spacer(Modifier.height(AppTheme.Spacing.sm))
            Text(
                text = subtitle,
                color = AppTheme.Text3,
                textAlign = TextAlign.Center,
            )
        }

        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(AppTheme.Spacing.lg))
            InkButton(
                text = actionText,
                onClick = onAction,
                color = AppTheme.Gold,
            )
        }
    }
}
