package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 新手引导步骤 id（纯字符串常量，跨层稳定）。 */
object TutorialSteps {
    const val INTRO = "intro"
    const val FIRST_PULL = "first_pull"
    const val FORM_TEAM = "form_team"
    const val FIRST_BATTLE = "first_battle"
    const val FIRST_LEVEL = "first_level"

    /** 引导主线顺序（完成判定与「当前步」推导共用）。 */
    val ORDER: List<String> = listOf(INTRO, FIRST_PULL, FORM_TEAM, FIRST_BATTLE, FIRST_LEVEL)
}

/**
 * 新手引导存档（第 8 节）。字段全部带默认值——旧档无该键时反序列化为干净初值。
 * 完成/跳过皆幂等；重进不再强制。
 */
@Serializable
data class TutorialSaveData(
    /** 是否整段完成（全部步骤走完）。 */
    @SerialName("Completed") var completed: Boolean = false,
    /** 用户是否主动跳过。 */
    @SerialName("Skipped") var skipped: Boolean = false,
    /** 已完成步骤 id 列表（滤空）。 */
    @SerialName("Done") var done: List<String?> = emptyList(),
) {
    fun doneSteps(): Set<String> = done.filterNotNull().toSet()

    fun hasDone(step: String): Boolean = step in doneSteps()

    /** 标记一步完成；若齐则 completed=true。返回是否发生变更。 */
    fun markDone(step: String): Boolean {
        if (completed || skipped || hasDone(step)) return false
        done = (doneSteps() + step).toList()
        if (TutorialSteps.ORDER.all { it in doneSteps() }) completed = true
        return true
    }

    fun markSkipped(): Boolean {
        if (completed || skipped) return false
        skipped = true
        return true
    }
}
