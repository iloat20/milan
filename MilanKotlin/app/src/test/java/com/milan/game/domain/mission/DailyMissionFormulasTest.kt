package com.milan.game.domain.mission

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [DailyMissionFormulas] 单元测试（2026-09-06 S5 配套）。
 *
 * 验证活跃度累加 + 上限钳位语义：100 是封顶，超出不再累计。
 */
class DailyMissionFormulasTest {

    @Test
    fun `活跃度上限为 100`() {
        assertEquals(100, DailyMissionFormulas.MAX_ACTIVITY_POINTS)
    }

    @Test
    fun `累加未超上限时正常返回和`() {
        assertEquals(30, DailyMissionFormulas.activityAfterReward(20, 10))
        assertEquals(80, DailyMissionFormulas.activityAfterReward(60, 20))
    }

    @Test
    fun `累加超上限时钳位到 100 不溢出`() {
        assertEquals(100, DailyMissionFormulas.activityAfterReward(80, 50))
        assertEquals(100, DailyMissionFormulas.activityAfterReward(100, 100))
    }

    @Test
    fun `已封顶再加奖励保持 100 不变`() {
        assertEquals(100, DailyMissionFormulas.activityAfterReward(100, 30))
    }
}
