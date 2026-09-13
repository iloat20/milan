package com.milan.game.ui.theme

/**
 * 织环 v3.1 货币显示名与 glyph（仅 UI 文案，不改存档字段名）。
 *
 * | 存档字段 | 旧显示 | 新显示 | glyph |
 * |---|---|---|---|
 * | softCurrency | 星尘 | 环痕 | ◎ 环心 |
 * | hardCurrency | 钻石 | 纯环 | ◉ 完环 |
 * | starFragments | 星魂碎片 | 残玦 | ◇ 断玦 |
 */
object CurrencyNames {
    const val SOFT = "环痕"
    const val HARD = "纯环"
    const val FRAG = "残玦"
    const val TICKET = "战票"
    const val SOFT_GLYPH = "◎"
    const val HARD_GLYPH = "◉"
    const val FRAG_GLYPH = "◇"
    /** 战票：角形印记，替代 emoji ⚔。 */
    const val TICKET_GLYPH = "▣"
}
