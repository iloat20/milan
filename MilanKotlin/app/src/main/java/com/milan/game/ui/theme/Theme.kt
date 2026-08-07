package com.milan.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// twilight 暗夜主题最小集（Task 8 对齐 C# ElementTheme/WorldTheme 完整调色板）
private val TwilightColors = darkColorScheme(
    primary = Color(0xFF8B7BD8),
    secondary = Color(0xFFD9A95C),
    background = Color(0xFF0F0D1A),
    surface = Color(0xFF1A1730),
    onBackground = Color(0xFFE8E4F2),
)

@Composable
fun MilanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TwilightColors,
        content = content,
    )
}
