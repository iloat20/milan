package com.milan.game.domain.battle

import org.junit.Assert.assertEquals
import org.junit.Test

/** 队伍同源共鸣测试：双星/同调触发边界、空元素豁免、apply 不改入参。 */
class TeamResonanceTest {

    private fun stats(atk: Int = 100, spd: Int = 10, element: String = "", id: String = "c") =
        UnitStats(atk = atk, def = 50, hp = 1000, spd = spd, characterId = id, element = element)

    @Test
    fun `两人同元素即同调共鸣`() {
        // 规则语义：全队一致即「同调」（含两人队）；「双星」只在混编队中生效。
        val b = TeamResonance.buffFor("Flame", listOf("Flame", "Flame"))
        assertEquals(TeamResonance.UNISON_ATK_BONUS, b.atkBonus, 0.0)
        assertEquals(TeamResonance.UNISON_SPD_BONUS, b.spdBonus, 0.0)
    }

    @Test
    fun `同调共鸣_全队同元素加攻20速10`() {
        val team = listOf("Water", "Water", "Water")
        val b = TeamResonance.buffFor("Water", team)
        assertEquals(TeamResonance.UNISON_ATK_BONUS, b.atkBonus, 0.0)
        assertEquals(TeamResonance.UNISON_SPD_BONUS, b.spdBonus, 0.0)
    }

    @Test
    fun `单人无共鸣`() {
        val b = TeamResonance.buffFor("Flame", listOf("Flame"))
        assertEquals(0.0, b.atkBonus, 0.0)
        assertEquals(0.0, b.spdBonus, 0.0)
    }

    @Test
    fun `空元素不参与判定`() {
        // 全队空元素：element 为空的角色直接豁免
        assertEquals(0.0, TeamResonance.buffFor("", listOf("", "", "")).atkBonus, 0.0)
        // 混编空元素不构成同调
        assertEquals(0.0, TeamResonance.buffFor("", listOf("", "Flame")).atkBonus, 0.0)
    }

    @Test
    fun `混编队只有凑满2人的元素吃到双星`() {
        val team = listOf("Flame", "Flame", "Water")
        val fire = TeamResonance.buffFor("Flame", team)
        val water = TeamResonance.buffFor("Water", team)
        assertEquals(TeamResonance.PAIR_ATK_BONUS, fire.atkBonus, 0.0)
        assertEquals(0.0, water.atkBonus, 0.0)
    }

    @Test
    fun `apply套用加成且不改入参`() {
        val a = stats(element = "Flame")
        val b = stats(id = "b", element = "Flame")
        val original = listOf(a, b)
        val boosted = TeamResonance.apply(original)

        // 两人同元素 = 同调：atk×1.20 → 120；spd×1.10 → 11
        assertEquals(120, boosted[0].atk)
        assertEquals(120, boosted[1].atk)
        assertEquals(11, boosted[0].spd)
        // 入参未被修改
        assertEquals(100, a.atk)
        assert(original !== boosted)

        // 同调三人：atk×1.20=120，spd×1.10=11
        val trio = listOf(stats(), stats(id = "x", element = "Light"), stats(id = "y", element = "Light"))
        val unison = TeamResonance.apply(trio.map { it.copy(element = "Light") })
        assertEquals(120, unison[0].atk)
        assertEquals(11, unison[0].spd)
    }

    @Test
    fun `apply单人与零共鸣原样返回`() {
        val solo = listOf(stats())
        assertEquals(solo, TeamResonance.apply(solo))
        val mixed = listOf(stats(element = "Flame"), stats(id = "b", element = "Water"))
        assertEquals(mixed, TeamResonance.apply(mixed))
    }
}
