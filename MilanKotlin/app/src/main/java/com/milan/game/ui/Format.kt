package com.milan.game.ui

import java.text.NumberFormat
import java.util.Locale

/**
 * 数值显示统一口径（M7）：AppChrome 资源胶囊与 ShopScreen 价签此前两套格式化
 * （NumberFormat.getIntegerInstance 默认 Locale vs String.format(Locale.US, "%,d")），
 * 现在统一为千分位分组，避免同一数值在不同页出现两种分隔符。
 */
fun formatCount(value: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value.toLong())
