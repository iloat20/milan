package com.milan.game.data

/**
 * 稀有度（与 C# Milan.Data.Rarity 对齐）。
 * R=1, SR=2, SSR=3, UR=4 —— 值语义与旧档/旧逻辑一致，禁止修改。
 *
 * 2026-08 KMP 下沉：从 app 的 data/Enums.kt 迁入 shared commonMain，
 * 与 domain 引擎（抽卡/养成/战斗）同处跨平台层，桌面/Android/将来 iOS 共用。
 */
enum class Rarity(val value: Int) {
    R(1),
    SR(2),
    SSR(3),
    UR(4),
    ;

    companion object {
        fun fromValue(v: Int): Rarity? = entries.firstOrNull { it.value == v }
    }
}
