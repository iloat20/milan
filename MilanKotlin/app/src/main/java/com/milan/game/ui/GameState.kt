package com.milan.game.ui

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.domain.battle.UnitStats
import com.milan.game.domain.progression.ProgressionEngine
import com.milan.game.domain.progression.TalentEngine
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.eventbus.EventBus
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GameService
import com.milan.game.services.TalentTreeData
import kotlin.math.max

/**
 * 进程级游戏状态单例（C# GameState.cs 翻译）。
 *
 * 所有界面共享同一 [GameService]（从而同一存档 / 同一内容）：抽卡结果在列表页立即可见，
 * 存档只加载一次。Activity 在 OnResume 重建视图时从这里取最新状态。
 *
 * 初始化：[ensureInitialized] 幂等（双检锁），Android 接入层（MainActivity / Application）
 * 注入存档提供者与内容 JSON；访问 [service] 前必须已初始化。
 */
object GameState {

    private val gate = Any()
    private var initialized = false
    private var serviceRef: GameService? = null
    private val talentEngine = TalentEngine()

    /** 共享服务实例（唯一 GameService，进程内只创建一次）。 */
    val service: GameService
        get() = checkNotNull(serviceRef) { "GameState 未初始化：先调用 ensureInitialized(...)" }

    /** 幂等初始化（C# EnsureInitialized 双检锁）。内容加载失败/缺失走 [GameService] 兜底。 */
    fun ensureInitialized(saveProvider: SaveProvider, contentJson: String?, onTrace: (String) -> Unit) {
        if (initialized) return
        synchronized(gate) {
            if (initialized) return
            serviceRef = GameService(saveProvider, contentJson, onTrace)
            initialized = true
            // 宿主装配 EventBus 留痕钩子 → CrashReporter（Core 层保持纯逻辑，不依赖 Android）。
            if (EventBus.handlerException == null) {
                EventBus.handlerException = { type, ex ->
                    try { CrashReporter.write("EventBus.${type.name}", ex) } catch (_: Exception) { }
                }
            }
        }
    }

    // ---- 便捷访问器（UI 用，对齐 C# 同名成员）----

    val currency: Int
        get() = service.saveData.softCurrency

    val currencyLabel: String
        get() = "星尘: $currency"

    val ownedCount: Int
        get() = service.saveData.ownedCharacters.size

    /** 已拥有角色视图列表（存档 × 内容定义合并）。 */
    fun owned(): List<OwnedCharacterView> = service.saveData.ownedCharacters.mapNotNull { ch ->
        ch ?: return@mapNotNull null
        val def = service.characters.firstOrNull { it.characterId == ch.characterId }
        OwnedCharacterView(ch, def)
    }

    /** 按索引取战斗属性（C# Stat）：0=攻 1=防 2=命 3=速。 */
    fun stat(s: UnitStats, idx: Int): Int = when (idx) {
        0 -> s.atk
        1 -> s.def
        2 -> s.hp
        3 -> s.spd
        else -> 0
    }

    /**
     * 由基础值 + 养成推导实时战斗属性（单一事实来源：详情页/养成页/战斗页都走这里）。
     */
    fun computeStats(ch: OwnedCharacterView): UnitStats =
        computeStatsAt(ch, ch.save.level, max(1, ch.save.stage))

    /**
     * 在指定等级/阶段/星级下计算属性（用于养成页「下一级 / 下一阶 / 升星」预测值）。
     * 天赋加成按当前已点亮节点计算，不随等级/阶段/星级假设改变。
     * [stars] 缺省（<0）时取 [ch.save.stars] 的实时值。
     */
    fun computeStatsAt(ch: OwnedCharacterView, level: Int, stage: Int, stars: Int = -1): UnitStats {
        val def = ch.def
        val save = ch.save
        val engine = ProgressionEngine()
        val stg = max(1, stage)
        val lv = max(1, level)
        val st = if (stars < 0) save.stars.coerceAtLeast(1) else stars
        // 星级小幅加成：每星 +5%（1★→×1.0，满 7★→×1.30）。并入 StatAtLevel 的倍率槽。
        val starMul = ProgressionEngine.starMultiplier(st)
        if (def == null)
            return UnitStats(atk = 0, def = 0, hp = 1, spd = 0, characterId = save.characterId)

        // BaseStats 来自外部 data.json，长度不可信。越界会直接抛异常，
        // 而本方法在详情页/检视页/战斗页的构建路径上被调用 —— 抛了就是闪退，一律兜底。
        val bs = def.baseStats
        fun base(i: Int, fallback: Int): Int = if (i < bs.size) bs[i] else fallback

        // 天赋加成数值下沉 domain 层（TalentEngine.talentMultipliers，单一事实来源）。
        // 树为 null → 空分支列表 → 全 0，与旧实现行为一致。
        val branchIds = ch.talent?.nodes.orEmpty()
            .filter { save.talentPoints.contains(it.nodeId) }
            .map { it.branchId }
        val m = talentEngine.talentMultipliers(branchIds)

        return UnitStats(
            atk = (engine.statAtLevel(base(0, 100), lv, stg, starMul) * (1 + m.atk)).toInt(),
            def = (engine.statAtLevel(base(1, 80), lv, stg, starMul) * (1 + m.def)).toInt(),
            hp = (engine.statAtLevel(base(2, 1000), lv, stg, starMul) * (1 + m.hp)).toInt(),
            spd = (engine.statAtLevel(base(3, 12), lv, stg, starMul) * (1 + m.spd)).toInt(),
            characterId = save.characterId,
        )
    }
}

/** 角色视图：存档 + 内容定义合并（C# OwnedCharacterView）。 */
class OwnedCharacterView(
    val save: CharacterSaveState,
    val def: CharacterDataEntry?,
) {
    val name: String get() = def?.displayName ?: save.characterId
    val title: String get() = def?.title ?: ""
    val rarity: Int get() = def?.baseRarity ?: 1
    val world: String get() = def?.world ?: "Shinwa"
    val element: String get() = def?.element ?: "Flame"
    val lore: String get() = def?.lore ?: ""
    val canBreakthrough: Boolean get() = def?.canBreakthrough ?: false

    /** 角色天赋树（按 TalentTreeId 从服务内容中查；无定义返回 null）。 */
    val talent: TalentTreeData?
        get() = GameState.service.talentTrees.firstOrNull { it.treeId == def?.talentTreeId }
}
