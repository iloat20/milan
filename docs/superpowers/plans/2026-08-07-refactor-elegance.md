# Milan 架构优雅性改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理 Kotlin/Compose 版（`MilanKotlin/`）架构债务：A1 天赋加成数值下沉 domain 层、A2-A4 组件收敛去重、A5 死事件清理、B 类顺手修（B1 文档 / B2 校验复用 / B3 缓存 / B5 死代码删除）。

**Architecture:** `TalentEngine`（domain/progression，纯 Kotlin 禁止 android import）新增 `TalentMultipliers` + `talentMultipliers()` 作为 0.03 系数与分支映射的单一事实来源；UI 组件向 `ui/components/` 收敛（仅逐字节相同者提取）；`GameService` 提取 `talentCheck` 校验复用 + `prereqMap` 缓存。不改序列化结构、不改公共 API、既有测试零修改。

**Tech Stack:** Kotlin 2.1.20 / Jetpack Compose / Gradle 8 wrapper（`.\gradlew.bat`，无需本地 Gradle）/ JUnit4 + kotlinx-coroutines-test。构建与测试命令：
- `.\gradlew.bat :app:assembleDebug`（构建，需 exit 0）
- `.\gradlew.bat :app:testDebugUnitTest`（单测全绿）

**前置条件：** 用户已确认 D5 两项视觉变化（默认接受；若拒绝，见 Task 4 Step 6 / Task 8 Step 4 的保留路径）。规格文档：[2026-08-07-refactor-elegance-design.md](../specs/2026-08-07-refactor-elegance-design.md)。工作目录恒为 `MilanKotlin/`（所有相对路径相对于它）。

---

## 提交 ① A1 天赋加成数值下沉（Task 1-3）

### Task 1: TalentEngine 新增 TalentMultipliers + talentMultipliers（TDD）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/domain/progression/TalentEngine.kt`
- Modify: `MilanKotlin/app/src/test/java/com/milan/game/domain/progression/TalentEngineTest.kt`（**已存在**，53 行——扩展，不新建）

- [ ] **Step 1: 追加 6 个失败测试**（`TalentEngineTest.kt` 末尾、最后一个 `}` 之前插入）

```kotlin
    // ── talentMultipliers：分支属性加成（每节点 +3%）──

    @Test
    fun talentMultipliers_powerBranch_onlyAtk() {
        val m = engine.talentMultipliers(listOf(TalentEngine.BRANCH_POWER))
        assertEquals(0.03f, m.atk, 1e-6f)
        assertEquals(0f, m.def, 1e-6f)
        assertEquals(0f, m.hp, 1e-6f)
        assertEquals(0f, m.spd, 1e-6f)
    }

    @Test
    fun talentMultipliers_twoPowerNodes_accumulates() {
        val m = engine.talentMultipliers(listOf(TalentEngine.BRANCH_POWER, TalentEngine.BRANCH_POWER))
        assertEquals(0.06f, m.atk, 1e-6f)
    }

    @Test
    fun talentMultipliers_defenseBranch_boostsDefAndHp() {
        val m = engine.talentMultipliers(listOf(TalentEngine.BRANCH_DEFENSE))
        assertEquals(0.03f, m.def, 1e-6f)
        assertEquals(0.03f, m.hp, 1e-6f)
    }

    @Test
    fun talentMultipliers_utilityBranch_boostsSpd() {
        val m = engine.talentMultipliers(listOf(TalentEngine.BRANCH_UTILITY))
        assertEquals(0.03f, m.spd, 1e-6f)
    }

    @Test
    fun talentMultipliers_unknownBranch_ignored() {
        val m = engine.talentMultipliers(listOf("branch_xxx"))
        assertEquals(0f, m.atk, 1e-6f)
        assertEquals(0f, m.def, 1e-6f)
        assertEquals(0f, m.hp, 1e-6f)
        assertEquals(0f, m.spd, 1e-6f)
    }

    @Test
    fun talentMultipliers_emptyList_allZero() {
        val m = engine.talentMultipliers(emptyList())
        assertEquals(0f, m.atk, 1e-6f)
        assertEquals(0f, m.def, 1e-6f)
        assertEquals(0f, m.hp, 1e-6f)
        assertEquals(0f, m.spd, 1e-6f)
    }
```

`assertEquals(float, float, float)` 是 JUnit4 带 delta 的重载，已 import（`org.junit.Assert.assertEquals`）。`TalentEngine.BRANCH_*` 引用 Step 3 才定义——此刻编译失败是预期（红灯）。

- [ ] **Step 2: 运行测试确认红灯**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.milan.game.domain.progression.TalentEngineTest"
```

Expected: FAIL——编译错误 `unresolved reference: talentMultipliers` / `unresolved reference: BRANCH_POWER`。

- [ ] **Step 3: 实现**（`TalentEngine.kt` 替换为完整内容——现有 24 行原样保留 + 新增成员进类内）

```kotlin
package com.milan.game.domain.progression

/**
 * 天赋树分配引擎（C# Milan.Domain.Progression.TalentEngine 翻译）。
 */
class TalentEngine {

    /**
     * 节点 [nodeId] 是否可分配：
     * - 已分配 → false；
     * - 无前置要求（不在 [prereqs] 中）或前置为 null → true（根节点可点；防御空引用）；
     * - 全部前置均已分配 → true。
     */
    fun canAllocate(nodeId: String, allocated: List<String>, prereqs: Map<String, List<String>?>): Boolean {
        if (nodeId in allocated) return false
        // key 不存在或值为 null 都视为「无前置」而非「锁死」
        val reqs = prereqs[nodeId] ?: return true
        return reqs.all { it in allocated }
    }

    /** 返回「满树总点数」= 所有节点 cost 之和（非已分配点数）。 */
    fun totalPoints(nodeCosts: Map<String, Int>): Int = nodeCosts.values.sum()

    // ── 天赋分支属性加成（单一事实来源：0.03 系数与分支映射只允许存在于此）──

    /** 天赋分支属性加成（每节点 +3%）。 */
    data class TalentMultipliers(
        val atk: Float = 0f,
        val def: Float = 0f,
        val hp: Float = 0f,
        val spd: Float = 0f,
    )

    /** 由已点亮节点所属分支计算加成；未知分支忽略（防御内容数据脏值）。 */
    fun talentMultipliers(branchIds: List<String>): TalentMultipliers {
        var atk = 0f; var def = 0f; var hp = 0f; var spd = 0f
        for (b in branchIds) {
            when (b) {
                BRANCH_POWER   -> atk += 0.03f
                BRANCH_DEFENSE -> { def += 0.03f; hp += 0.03f }
                BRANCH_UTILITY -> spd += 0.03f
            }
        }
        return TalentMultipliers(atk, def, hp, spd)
    }

    companion object {
        /** 分支 ID 常量：与 ContentModels.TalentNodeData.BranchId（@SerialName("BranchId")）契约对齐。 */
        const val BRANCH_POWER = "branch_power"
        const val BRANCH_DEFENSE = "branch_defense"
        const val BRANCH_UTILITY = "branch_utility"
    }
}
```

行为对照既有 `GameState.computeStatsAt`：power→攻、defense→防+血、utility→速，每节点 +0.03——逐项等同。

- [ ] **Step 4: 运行测试确认绿灯**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.milan.game.domain.progression.TalentEngineTest"
```

Expected: PASS——全部测试（原 6 个 + 新 6 个）通过。

- [ ] **Step 5: 编译检查**（确认 domain 层无 android import 泄漏）

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL（exit 0）。

---

### Task 2: GameState 接入 talentMultipliers

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/GameState.kt`

- [ ] **Step 1: 加 import**（第 6 行 `import com.milan.game.domain.progression.ProgressionEngine` 之后插入）

```kotlin
import com.milan.game.domain.progression.TalentEngine
```

- [ ] **Step 2: 加引擎字段**（第 25 行 `private val gate = Any()` 之前插入——object 字段，进程级单例复用）

```kotlin
    private val talentEngine = TalentEngine()
```

- [ ] **Step 3: 替换 computeStatsAt 天赋加成块**（现状第 104-119 行）

将：
```kotlin
        // 已点亮天赋的分支加成：power→攻，defense→防+生命，utility→速度（每节点 +3%）。
        var atkB = 0f
        var defB = 0f
        var hpB = 0f
        var spdB = 0f
        val tree = ch.talent
        if (tree != null) {
            for (n in tree.nodes) {
                if (!save.talentPoints.contains(n.nodeId)) continue
                when (n.branchId) {
                    "branch_power" -> atkB += 0.03f
                    "branch_defense" -> { defB += 0.03f; hpB += 0.03f }
                    "branch_utility" -> spdB += 0.03f
                }
            }
        }
```
替换为：
```kotlin
        // 天赋加成数值下沉 domain 层（TalentEngine.talentMultipliers，单一事实来源）。
        // 树为 null → 空分支列表 → 全 0，与旧实现行为一致。
        val branchIds = ch.talent?.nodes.orEmpty()
            .filter { save.talentPoints.contains(it.nodeId) }
            .map { it.branchId }
        val m = talentEngine.talentMultipliers(branchIds)
```

- [ ] **Step 4: 替换返回值引用**（现状第 121-127 行）

将：
```kotlin
        return UnitStats(
            atk = (engine.statAtLevel(base(0, 100), lv, stg, starMul) * (1 + atkB)).toInt(),
            def = (engine.statAtLevel(base(1, 80), lv, stg, starMul) * (1 + defB)).toInt(),
            hp = (engine.statAtLevel(base(2, 1000), lv, stg, starMul) * (1 + hpB)).toInt(),
            spd = (engine.statAtLevel(base(3, 12), lv, stg, starMul) * (1 + spdB)).toInt(),
            characterId = save.characterId,
        )
```
替换为：
```kotlin
        return UnitStats(
            atk = (engine.statAtLevel(base(0, 100), lv, stg, starMul) * (1 + m.atk)).toInt(),
            def = (engine.statAtLevel(base(1, 80), lv, stg, starMul) * (1 + m.def)).toInt(),
            hp = (engine.statAtLevel(base(2, 1000), lv, stg, starMul) * (1 + m.hp)).toInt(),
            spd = (engine.statAtLevel(base(3, 12), lv, stg, starMul) * (1 + m.spd)).toInt(),
            characterId = save.characterId,
        )
```

- [ ] **Step 5: 验证**——UI 层不再出现分支字面量 `"branch_power"` 与 `0.03f`

```powershell
# 从 ui/ 全量搜索，应只剩注释或零结果
Select-String -Path "MilanKotlin\app\src\main\java\com\milan\game\ui\*.kt" -Pattern "branch_power|0\.03"
```

Expected: 零匹配（或仅注释提及）。

- [ ] **Step 6: 编译 + 全量测试**（Task 3 一起做也行，此处先快速验证本文件）

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。

---

### Task 3: 全量验证 + 提交①

**Files:** 无改动。

- [ ] **Step 1: 全量单测**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: 全部测试 PASS（含 SaveDataTest / SaveManagerTest / BattleSimulatorTest / GachaEngineTest / PityCounterTest / EconomyFormulasTest / ProgressionEngineTest / TalentEngineTest / GameServiceTest）。

- [ ] **Step 2: 构建 APK**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL（exit 0），产物 `MilanKotlin/app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 3: 提交①**（A1 数值下沉——只有 domain + 接入层两个文件 + 测试）

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/domain/progression/TalentEngine.kt MilanKotlin/app/src/test/java/com/milan/game/domain/progression/TalentEngineTest.kt MilanKotlin/app/src/main/java/com/milan/game/ui/GameState.kt
git commit -m "refactor(domain): 天赋加成数值下沉 TalentEngine（A1）

computeStatsAt 的分支映射与 0.03 系数移入 domain/progression/TalentEngine
（TalentMultipliers + talentMultipliers），单一事实来源；GameState 只做接入。
行为逐项等价：power→攻、defense→防+血、utility→速，每节点 +3%。
TalentEngineTest 扩展 6 个用例（含未知分支忽略、空列表兜底）。"
```

---

## 提交 ② A2/A3 组件收敛（Task 4-7）

### Task 4: GlassPanel 三份统一（D5-② 视觉变化）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/characters/CharacterDetailScreen.kt`
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/progression/ProgressionScreen.kt`
- 使用既有: `MilanKotlin/app/src/main/java/com/milan/game/ui/components/UIComponents.kt` 的 `GlassPanel`（正式版）

- [ ] **Step 1: 目检两页私有版确认逐字节相同**（已核实，执行时仍各读一遍确认无漂移）

- detail 私有版：`CharacterDetailScreen.kt:411-430`
- progression 私有版：`ProgressionScreen.kt:438-457`

两者签名均为 `(modifier: Modifier = Modifier, gold: Boolean = false, radius: Int = 14, content: @Composable () -> Unit)`，实现为 clip + background(AppTheme.Surface) + border。

- [ ] **Step 2: 删除两页私有定义**

- `CharacterDetailScreen.kt`：删 411-430 行（`@Composable private fun GlassPanel(...) { ... }` 整块，含上方 doc 注释）
- `ProgressionScreen.kt`：删 438-457 行（同上）

- [ ] **Step 3: 两页加 import**

```kotlin
import com.milan.game.ui.components.GlassPanel
```

- [ ] **Step 4: 适配 9 个调用点**（私有版 `radius: Int` → 正式版 `radius: Dp`；`content` lambda 从 `() -> Unit` 升级为 `BoxScope.() -> Unit`，现有 lambda 自动适配，无需改 lambda 体）

逐一查看并修改以下调用点，若传 `radius = <Int>` 参数则改为 `radius = <Int>.dp`；未传 radius 的调用点直接可用：

| 文件 | 调用点（所在函数） |
|---|---|
| CharacterDetailScreen.kt | WeaponPanel :434、SkillPanel :813、StoryPanel :861、VoicePanel :895 |
| ProgressionScreen.kt | LevelPanel :505、AscendPanel :610、StarPanel :660、StatsPanel :719、TalentPanel :793 |

示例：`GlassPanel(radius = 12) { ... }` → `GlassPanel(radius = 12.dp) { ... }`。

- [ ] **Step 5: 编译验证**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。若某调用点报 BoxScope/参数类型错误，回到 Step 4 修正该点。

- [ ] **Step 6: D5-② 视觉变化记录**（合并到提交②的 PR 说明）

统一到正式版后，两页 GlassPanel 新增**顶部内高光**（白 α10% 渐变，UIComponents.kt:61-71）。若用户拒绝此视觉变化（D5-② 不通过），替代路径：在 UIComponents 正式版加 `highlight: Boolean = true` 参数，两页调用点传 `highlight = false`——**默认走 Step 5 的接受路径，仅在用户明确拒绝时改走此路径**。

---

### Task 5: 7 个候选组件 diff 判定（产出对比表）

**Files:** 只读。

- [ ] **Step 1: 逐组件读取两页定义并逐字节比较**

| # | 组件 | detail 行 | progression 行 |
|---|---|---|---|
| 1 | switch | CharacterDetailScreen.kt:103-174 | ProgressionScreen.kt:115-121 |
| 2 | MissingCharacter | :175-203 | :236-264 |
| 3 | HeroRegion | :204-296 | :265-323 |
| 4 | GlassArrow | :297-314 | :324-341 |
| 5 | HeroNameplate | :315-386 | :342-413 |
| 6 | SectionTitle | :387-410 | :414-437 |
| 7 | WoWDivider | :786-796 | :781-792 |

判定规则（D4）：**逐字节相同**（含注释与签名）→ 候选提取；**仅注释差异** → 候选提取（统一注释）；**签名或实现有结构差异** → 保留两页私有，不加注释（差异即设计意图）。

- [ ] **Step 2: 记录判定结果表**（提交② PR 必须附）

```
| 组件 | 判定 | 差异说明 |
|---|---|---|
| switch | 相同/不同 | ... |
| ... | | |
```
（`SectionTitle` 在 HomeScreen.kt:429-458 还有第三份**不同签名**的私有版 `(title: String, en: String)`——不在本次提取范围，保持私有。）

---

### Task 6: 提取判定为「相同」的组件

**Files:**
- Create: `MilanKotlin/app/src/main/java/com/milan/game/ui/components/PageComponents.kt`（仅当 Task 5 至少有一个组件判定为相同；全部不同则跳过本任务并在提交② PR 注明「7 组件均保留私有」）
- Modify: CharacterDetailScreen.kt / ProgressionScreen.kt（删已提取组件的私有定义）

- [ ] **Step 1: 建文件骨架**

```kotlin
package com.milan.game.ui.components

// 从 CharacterDetailScreen.kt / ProgressionScreen.kt 提取的角色页共享组件。
// 提取条件：两页定义逐字节相同（diff 判定，见提交② PR 对比表）。
// 新增 import 一律从源文件迁移（仅该组件使用的 import）。

// TODO(提取): 粘贴每个判定为「相同」的组件函数体，去掉 private 修饰符
```

- [ ] **Step 2: 逐个提取**（对 Task 5 判定为相同的每个组件）

1. 从源文件复制完整函数体（含 `@Composable` 注解与 doc 注释）到 PageComponents.kt，**去掉 `private`**
2. 检查源文件的 import 块，把仅被该组件使用的 import 一并迁移到 PageComponents.kt（无法确定时保留在源文件——多余的未用 import 由编译器告警提示，删除之）
3. 删除两页的私有定义
4. 两页调用点无需改名（同名函数，包 import 后解析到 components 版）

- [ ] **Step 3: 不同组件处理**——Task 5 判定为「不同」的组件：两页私有定义均保留，无其他动作。

- [ ] **Step 4: 编译验证**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。重点检查：提取后两页无 `unresolved reference`（遗漏 import 迁移）与重复定义（漏删私有）。

---

### Task 7: 构建 + 测试 + 提交②

- [ ] **Step 1: 全量单测 + 构建**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Expected: 全 PASS + BUILD SUCCESSFUL。

- [ ] **Step 2: 提交②**（组件收敛 + D5-② 视觉变化）

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/
git commit -m "refactor(ui): GlassPanel 统一正式版 + 提取页共享组件（A2/A3）

- GlassPanel 三份归一：删详情/养成私有版，9 个调用点改 radius: Dp 并复用
  UIComponents 正式版（新增顶部内高光，D5-② 已确认）
- 两页逐字节相同的组件提取到 ui/components/PageComponents.kt（对比表见 PR）
- StatsPanel 两版不同构、WoW 系列为详情页独有：均保留私有"
```

---

## 提交 ③ A4 按钮统一（Task 8-9）

### Task 8: HomeScreen 换正式版按钮（D5-① 视觉变化）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/home/HomeScreen.kt`

- [ ] **Step 1: 加 import**（`import com.milan.game.ui.nav.ResourceBar` 之后插入）

```kotlin
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.NeonButton
```

- [ ] **Step 2: 删除两个私有按钮定义**

- `HomeScreen.kt:319-341`：私有 `GoldButton`（14dp 圆角、两档渐变 GoldHi→GoldDeep、GoldHi α0.6 描边、padding vertical 14dp、textSize 15sp）
- `HomeScreen.kt:343-361`：私有 `NeonButton`（14dp 圆角、Frost α0.6 描边、padding vertical 14dp、textSize 15sp）

连同上方 doc 注释一起删除（`// ── 主视觉下方动作钮` 区块的按钮定义部分）。

- [ ] **Step 3: 调用点**（:313-315）——签名兼容，无需改动

```kotlin
        GoldButton("✦ 前往召唤", Modifier.weight(1f), onOpenGacha)
        Spacer(Modifier.width(12.dp))
        NeonButton("神谱图鉴", Modifier.weight(1f), onOpenCollection)
```

正式版签名 `GoldButton(text, modifier, onClick, textSize = 16.sp, enabled = true)` / `NeonButton(text, modifier, onClick, textSize = 14.sp, color = AppTheme.Frost, enabled = true)`——位置参数兼容。

- [ ] **Step 4: 编译验证 + D5-① 视觉变化记录**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。HomeScreen 从此不再定义任何按钮组件。

视觉变化（并入提交③ PR 说明）：
- GoldButton：14dp 圆角 → **6dp 斜切角**（CutShape）+ 渐变两档→三档 + 描边 GoldHi→White α0.47
- NeonButton：14dp 圆角 → **10dp 圆角** + 新增 α0.06 底色填充 + 描边 1dp→1.5dp

若用户拒绝 D5-①：替代路径——保留私有按钮定义，仅删注释标注「与 ThemeButtons 正式版的差异」，**默认走接受路径**。

- [ ] **Step 5: 编译 + 测试 + 提交③**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/home/HomeScreen.kt
git commit -m "refactor(ui): HomeScreen 按钮统一 ThemeButtons 正式版（A4）

删私有 GoldButton/NeonButton（14dp 圆角），改用正式版：Gold 斜切角 + 三档
渐变，Neon 10dp 圆角 + 底填充（D5-① 已确认）。HomeScreen 不再定义按钮组件。"
```

---

## 提交 ④ A5+B 清理（Task 10-15）

### Task 9: A5 删除 6 个死事件

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/infrastructure/eventbus/Events.kt`

- [ ] **Step 1: 删除 6 个零引用事件**（`:16-50` 行的 `GachaResultEvent`、`CharacterLevelUpEvent`、`CharacterStageUpEvent`、`TalentAllocatedEvent`、`CharacterBreakthroughEvent`、`BattleCompletedEvent`）

替换后完整文件：

```kotlin
package com.milan.game.infrastructure.eventbus

/**
 * 事件类型定义（翻译 C# Events.cs + GameEvents.cs）。
 */

/** 经济变动（星尘 / 钻石）标记事件。
 * 作为"轻标记"使用：handler 收到后直接重读 GameState 当前值，不依赖负载里的旧数值，
 * 避免队列中连续多次扣费时负载过期导致显示陈旧。 */
object CurrencyChanged

/** 养成变动（升级 / 突破 / 天赋加点）标记事件。
 * 作为"轻标记"使用（无负载）：handler 收到后直接重读当前角色存档的实时值。 */
object ProgressionChanged
```

- [ ] **Step 2: 引用复核**（确认零残留）

```powershell
Select-String -Path "MilanKotlin\app\src\main\**\*.kt","MilanKotlin\app\src\test\**\*.kt" -Pattern "GachaResultEvent|CharacterLevelUpEvent|CharacterStageUpEvent|TalentAllocatedEvent|CharacterBreakthroughEvent|BattleCompletedEvent"
```

Expected: 零匹配（Events.kt 自身除外，因已删除）。若主代码有残留引用 → 编译错误会暴露，修复残留调用点。

---

### Task 10: B1 更新 AGENTS.md

**Files:**
- Modify: `AGENTS.md`（仓库根）

- [ ] **Step 1: 修正测试段**（「领域引擎都支持注入 seed……新增领域逻辑请配套单测。Service 层与 UI 层目前无测试。」）

将 `Service 层与 UI 层目前无测试。` 改为：

```markdown
- `GameServiceTest`（app/src/test/java/com/milan/game/services/GameServiceTest.kt）覆盖服务层（抽卡/货币/养成/战绩）；UI 层目前无测试。
```

- [ ] **Step 2: 检查 AGENTS.md 是否有其他过时断言**——浏览全文件，若发现与现状不符的描述（如组件/文件清单），一并修正（**仅限明确过时的陈述**，不做无关编辑）。

---

### Task 11: B2 提取 talentCheck 校验复用

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/services/GameService.kt`

- [ ] **Step 1: 新增私有辅助**（插在 `canAllocateTalent` 定义之前）

```kotlin
    /** 天赋加点前置校验：角色存在 + 树存在 + 节点存在 + 未点亮 + 点数足够；任一失败返回 null。 */
    private fun talentCheck(charId: String, nodeId: String): Triple<CharacterSaveState, TalentNodeData, TalentTreeData>? {
        val save = getSave(charId) ?: return null
        val tree = getTalentTree(charId) ?: return null
        val node = tree.nodes.firstOrNull { it.nodeId == nodeId } ?: return null
        if (save.talentPoints.contains(nodeId)) return null
        if (save.unspentPoints < node.cost) return null
        return Triple(save, node, tree)
    }
```

`CharacterSaveState` 已 import（data 包）；`TalentNodeData` / `TalentTreeData` 同包（services）无需 import。`save.talentPoints` 在 GameServiceTest 既有测试中由 `coerceInputValues` 保证非空。

- [ ] **Step 2: 重写 canAllocateTalent**（替换 :437-446 现状）

```kotlin
    /** 不落盘地预判某天赋节点当前是否可点亮（用于 UI 三态与按钮可用性）。 */
    fun canAllocateTalent(charId: String, nodeId: String): Boolean {
        // C# 在渲染路径上曾有 "TalentPoints": null 覆盖字段初始化器的 NRE（逐节点调用直接闪退），
        // 用 ??= 兜底；Kotlin 类型系统 + coerceInputValues 保证 talentPoints 非空，天然免疫。
        val (save, _, tree) = talentCheck(charId, nodeId) ?: return false
        return talent.canAllocate(nodeId, save.talentPoints.filterNotNull(), prereqMap(tree))
    }
```

- [ ] **Step 3: 重写 allocateTalent 前置段**（替换 :450-456 现状；:458 之后的扣点/落盘逻辑原样保留）

```kotlin
    /** 点亮天赋节点：校验前置（TalentEngine）与天赋点余额，扣点并落盘。
     * 已点过 / 点不够 / 前置未满足 / 落盘失败均返回 false。 */
    fun allocateTalent(charId: String, nodeId: String): Boolean {
        val (save, node, tree) = talentCheck(charId, nodeId) ?: return false
        if (!talent.canAllocate(nodeId, save.talentPoints.filterNotNull(), prereqMap(tree))) return false

        val origPoints = save.unspentPoints
        save.unspentPoints -= node.cost
        save.talentPoints = save.talentPoints + nodeId
        if (!saveManager.save()) {
            save.unspentPoints = origPoints
            save.talentPoints = save.talentPoints.filterNot { it == nodeId }
            return false
        }
        publishProgressionChanged()
        return true
    }
```

- [ ] **Step 4: 运行 GameServiceTest 验证行为不变**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.milan.game.services.GameServiceTest"
```

Expected: 全部 PASS（`allocateTalent_requiresPrereqAndPoints` :364、`allocateTalent_noPoints_returnsFalse` :387 必须通过）。

---

### Task 12: B3 prereqMap 缓存

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/services/GameService.kt`

- [ ] **Step 1: 加缓存字段**（`private val talent = TalentEngine()` 之后插入）

```kotlin
    /** prereqMap 结果缓存（treeId → map）；loadContent 时失效重建。 */
    private var prereqCache: MutableMap<String, Map<String, List<String>>>? = null
```

- [ ] **Step 2: loadContent 开头置空**（`fun loadContent(rawJson: String?) {` 之后第一行插入）

```kotlin
        prereqCache = null // 内容重载 → 前置映射可能变化，缓存作废
```

- [ ] **Step 3: 重写 prereqMap**（替换 :430-434 现状；doc 注释保留）

```kotlin
    /** 构建 nodeId → 前置节点列表 的映射，喂给 TalentEngine.canAllocate。 */
    fun prereqMap(tree: TalentTreeData): Map<String, List<String>> {
        val cache = prereqCache ?: mutableMapOf<String, Map<String, List<String>>>().also { prereqCache = it }
        return cache.getOrPut(tree.treeId) { buildPrereq(tree) }
    }

    private fun buildPrereq(tree: TalentTreeData): Map<String, List<String>> {
        val m = mutableMapOf<String, List<String>>()
        for (n in tree.nodes) m[n.nodeId] = n.prerequisiteNodeIds
        return m
    }
```

- [ ] **Step 4: 编译 + 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.milan.game.services.GameServiceTest"
```

Expected: 全 PASS（`TalentTreeData.treeId` 字段已存在——`getTalentTree` 用 `it.treeId == def.talentTreeId`）。

---

### Task 13: B5 删除 stat() 死代码

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/GameState.kt`

- [ ] **Step 1: 复核零调用者**

```powershell
Select-String -Path "MilanKotlin\app\src\main\**\*.kt","MilanKotlin\app\src\test\**\*.kt" -Pattern "\.stat\("
```

Expected: 零匹配（B5 已 grep 验证，此处复核）。

- [ ] **Step 2: 删除 stat()**（现状 :67-74：`/** 按索引取战斗属性（C# Stat）：0=攻 1=防 2=命 3=速。 */` 注释 + 函数体整块）

删除后 `import com.milan.game.domain.battle.UnitStats` 可能变为未使用——保留（computeStatsAt 返回类型用到 `UnitStats`）。

- [ ] **Step 3: 编译验证**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。

---

### Task 14: 全量验证 + 提交④

- [ ] **Step 1: 全量单测 + 构建**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Expected: 全 PASS + BUILD SUCCESSFUL（exit 0）。

- [ ] **Step 2: 提交④**（A5+B 清理）

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/infrastructure/eventbus/Events.kt AGENTS.md MilanKotlin/app/src/main/java/com/milan/game/services/GameService.kt MilanKotlin/app/src/main/java/com/milan/game/ui/GameState.kt
git commit -m "chore: A5 死事件清理 + B 类顺手修

- Events.kt 删 6 个零引用事件 data class（GachaResult/LevelUp/StageUp/
  TalentAllocated/Breakthrough/BattleCompleted），保留轻标记两件套
- AGENTS.md：补 GameServiceTest 覆盖说明（B1）
- GameService：提取 talentCheck 校验复用（B2）、prereqMap 缓存（B3）
- GameState：删 stat() 死代码（B5，grep 零调用者）"
```

---

## 验收清单（全部 4 个提交完成后）

- [ ] `.\gradlew.bat :app:testDebugUnitTest` 全绿（既有测试零修改、零删除）
- [ ] `.\gradlew.bat :app:assembleDebug` exit 0
- [ ] `ui/` 目录下无 `"branch_power"` 字面量与 `0.03f`（A1 完成判据）
- [ ] 提交② PR 附组件 diff 对比表（Task 5 产出）
- [ ] HomeScreen 无私有按钮定义；两页无私有 GlassPanel（A2/A4 完成判据）
- [ ] Events.kt 仅剩 `CurrencyChanged` / `ProgressionChanged`
- [ ] 4 个提交各自独立可编译（提交① 单独可跑全量测试）
- [ ] 视觉变化（D5-① 斜切角按钮 / D5-② GlassPanel 内高光）由用户真机/模拟器目验确认
