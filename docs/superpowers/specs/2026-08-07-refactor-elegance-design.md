# Milan 重构 · 架构优雅性改造设计规格

> 日期：2026-08-07 · 状态：草案（待用户确认 D5 视觉变化项）

针对 Kotlin/Compose 版（`MilanKotlin/`）的架构债务清理：数值下沉、组件收敛、死代码清理。
范围：**A1-A5 + B 类顺手修（B1/B2/B3 部分/B5）**；A6（data.json 接线）暂缓。
与 `2026-08-07-talent-trees-design.md`（.NET 版遗留 P1 规格）在天赋效果方向上一致，但本规格只做**既有实现的美化**，不引入新玩法、不改序列化结构。

---

## 1. 现状问题（动机）

| # | 问题 | 位置 |
|---|---|---|
| A1 | 天赋加成魔法数字在 UI 层：`computeStatsAt` 硬编码「分支→属性」映射与 0.03 系数 | [GameState.kt:104-127](MilanKotlin/app/src/main/java/com/milan/game/ui/GameState.kt:104) |
| A2 | `GlassPanel` 三份定义：UIComponents 正式版 + 详情/养成页私有版（两私有版逐字节相同） | [UIComponents.kt:39](MilanKotlin/app/src/main/java/com/milan/game/ui/components/UIComponents.kt:39)、CharacterDetailScreen.kt、ProgressionScreen.kt |
| A3 | 8 个组件在详情/养成两页重复定义：`GlassPanel`（已由 A2 处理）、`HeroRegion`、`HeroNameplate`、`WoWDivider`、`SectionTitle`、`GlassArrow`、`switch`、`MissingCharacter`（`StatsPanel` 两版结构不同，除外；`WoWStatRow`/`StatBadge`/`WoWSectionHeader`/`LineGold`/`WoWGroupDivider` 为详情页独有，不构成重复） | CharacterDetailScreen.kt、ProgressionScreen.kt |
| A4 | HomeScreen 私有 `GoldButton`/`NeonButton` 与 ThemeButtons 正式版重复（14dp 圆角 vs 斜切角） | [HomeScreen.kt](MilanKotlin/app/src/main/java/com/milan/game/ui/home/HomeScreen.kt)、[ThemeButtons.kt](MilanKotlin/app/src/main/java/com/milan/game/ui/components/ThemeButtons.kt) |
| A5 | Events.kt 6 个零引用事件 data class | [Events.kt:16-50](MilanKotlin/app/src/main/java/com/milan/game/infrastructure/eventbus/Events.kt:16) |
| B1 | AGENTS.md 声称「Service 层目前无测试」，实际已有 `GameServiceTest` 覆盖服务层（抽卡/货币/养成/战绩） | AGENTS.md、services/GameServiceTest.kt |
| B2 | `canAllocateTalent`/`allocateTalent` 重复 4 行前置校验 | [GameService.kt](MilanKotlin/app/src/main/java/com/milan/game/services/GameService.kt) |
| B3 | `computeStatsAt` 每次调用 `new ProgressionEngine()`；`prereqMap` 每次调用重建 | GameState.kt、GameService.kt |
| B5 | `GameState.stat(s, idx)` 魔法索引（0=atk/1=def/2=hp/3=spd），**全仓库零调用点**（grep 已验证） | GameState.kt |

---

## 2. 决策记录

| # | 决策 | 内容 | 状态 |
|---|---|---|---|
| D1 | 范围 | A1-A5 + B1/B2/B3（部分）/B5；A6 暂缓 | ✅ 已确认 |
| D2 | 组织 | 单 spec 一次实施 | ✅ 已确认 |
| D3 | A1 落点 | `TalentEngine`（domain/progression）新增 `TalentMultipliers` + `talentMultipliers()`；不新建引擎（避免 YAGNI） | 本规格裁定 |
| D4 | 组件收敛原则 | 仅「逐字节相同」或「差异可参数化」才合并；仅同名不同构者保留私有并加注释 | ✅ 已确认 |
| D5 | 视觉变化 | ① 首页按钮 14dp 圆角 → 斜切角 ② GlassPanel 统一为正式版（新增内高光） | ⏳ **待确认** |
| D6 | B3 边界 | 只做零风险两项：GameState 引擎实例缓存、GameService prereqMap 缓存。**PityCounter 构造注入 GachaEngine 不做**——收益近零（每抽一次轻构造），代价是动公共 API + 改 5 个测试用例签名 | 本规格裁定 |
| D7 | B5 | `stat()` 直接删除（实施时用 codegraph_callers 复核零调用者） | ✅ 已确认 |

---

## 3. 架构分层（改动落点）

| 改动 | 文件 | 层 |
|---|---|---|
| A1 数值下沉 | [TalentEngine.kt](MilanKotlin/app/src/main/java/com/milan/game/domain/progression/TalentEngine.kt)（domain）+ GameState.kt（接入） | 领域 / 接入 |
| A2-A4 组件收敛 | `ui/components/`（新 `WoWComponents.kt`）+ 详情/养成两页 + HomeScreen | UI |
| A5 死事件 | [Events.kt](MilanKotlin/app/src/main/java/com/milan/game/infrastructure/eventbus/Events.kt) | 基础设施 |
| B1 文档 | AGENTS.md | 文档 |
| B2/B3 复用与缓存 | GameService.kt、GameState.kt | 服务 / 接入 |

**约束（红线重申）**：`domain/` 禁止 `import android.*`；数值公式单一事实来源——0.03 系数与分支映射只允许存在于 `domain/progression/TalentEngine.kt`；不动 `@Serializable` 结构；货币/养成写操作事务范式不变。

---

## 4. A1 详细设计 · 天赋加成数值下沉

### 4.1 domain 层（TalentEngine.kt 新增）

> 以下成员均加入既有 `class TalentEngine`：`data class TalentMultipliers` 为嵌套成员类、`talentMultipliers` 为实例方法、`companion object` 进类（**不得悬空在类外**——顶层函数无法直接引用 companion 常量，会编译失败）。

```kotlin
/** 天赋分支属性加成（每节点 +3%，单一事实来源）。 */
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
```

行为等价性核对（对照现有 GameState.computeStatsAt）：power→攻、defense→防+血、utility→速，每节点 +0.03，**逐项等同，无行为变化**。

### 4.2 接入层（GameState.kt）

`computeStatsAt` 改为：收集 `allocatedNodes.map { it.branchId }` → 调 `talentEngine.talentMultipliers(...)` → 应用到四维属性。
UI 层从此**不再出现分支字面量与 0.03**（违反时视为 A1 未完成）。

### 4.3 单测（TalentEngineTest.kt 新增）

| 用例 | 断言 |
|---|---|
| power 分支累积 | `["branch_power"]` → atk=0.03，其余 0 |
| 多节点累积 | `["branch_power","branch_power"]` → atk=0.06 |
| defense 双属性 | `["branch_defense"]` → def=hp=0.03 |
| utility | `["branch_utility"]` → spd=0.03 |
| 未知分支忽略 | `["branch_xxx"]` → 全 0 |
| 空列表 | `[]` → 全 0 |

---

## 5. A2-A4 详细设计 · 组件收敛

### 5.1 提取判定流程（实施第一步）

对每个候选组件做两页逐字节 diff，按 D4 原则分流，**产出对比表附在 PR 说明**：

- **已确认逐字节相同**：`GlassPanel`（详情/养成私有版，已复核）→ 删私有，调用点改用 UIComponents 正式版
- **待 diff 判定**（仅两页共有的 7 个组件；相同→提取，不同→保留私有加注释）：`HeroRegion`、`HeroNameplate`、`WoWDivider`、`SectionTitle`、`GlassArrow`、`switch`、`MissingCharacter`
- **已确认不同构**：`StatsPanel`（详情页 WoW 风格面板 vs 养成页 StatRow 列表）→ 两版均保留私有
- **非重复（详情页独有，保持私有）**：`WoWStatRow`、`StatBadge`、`WoWSectionHeader`、`LineGold`、`WoWGroupDivider`——养成页无对应组件，不构成重复，不迁移（最小改动）

### 5.2 落点

- `WoWDivider` 若两页 diff 相同 → 提取到 `ui/components/`（WoWComponents.kt 当前仅此一个候选，或并入既有 components 文件）
- 其余提取组件（HeroRegion/HeroNameplate/SectionTitle/GlassArrow/switch/MissingCharacter）按现有组织并入 `ui/components/`
- 详情页独有的 WoW 系列（WoWStatRow/StatBadge/WoWSectionHeader/LineGold/WoWGroupDivider）**保持私有**——不重复、不迁移（最小改动）
- `GlassPanel` 调用点适配：正式版带 `nested: Boolean = false` 参数，调用点用默认值即可（不传 = 与私有版渲染一致 + 内高光）

### 5.3 A4 按钮统一

HomeScreen 两处按钮（`GoldButton`/`NeonButton` 私有版）替换为 ThemeButtons 正式版，删除私有定义。替换后 HomeScreen 不再定义任何按钮组件。

### 5.4 视觉变化清单（D5，实施前必须用户确认）

| 位置 | 变化 | 性质 |
|---|---|---|
| Home 首页按钮 | 14dp 圆角 → 斜切角 | 正式版样式（既有意图） |
| 角色详情 / 养成页 GlassPanel | 新增顶部内高光（白 α10% 渐变） | 向统一设计体系靠拢 |

其余全部位置**像素级不变**——由「逐组件 diff 等价性」保证。

---

## 6. A5 + B 详细设计

### 6.1 A5 死事件（Events.kt）

删除：`GachaResultEvent`、`CharacterLevelUpEvent`、`CharacterStageUpEvent`、`TalentAllocatedEvent`、`CharacterBreakthroughEvent`、`BattleCompletedEvent`。
保留：`CurrencyChanged`、`ProgressionChanged`（连同现有「轻标记」设计注释）。
依据：已 grep 验证全仓库零引用；实施时再用 LSP 引用复核。

### 6.2 B1（AGENTS.md）

测试段「Service 层与 UI 层目前无测试」→ 改为「GameServiceTest（app/src/test/java/com/milan/game/services/GameServiceTest.kt）覆盖服务层；UI 层无测试」。

### 6.3 B2（GameService）

提取私有辅助：
```kotlin
/** 天赋加点前置校验：节点/角色存在 + 可分配 + 点数足够；失败返回 null。 */
private fun talentCheck(charId: String, nodeId: String): Triple<CharacterSaveState, TalentNodeData, TalentTreeData>?
```
`canAllocateTalent` 与 `allocateTalent` 共用，删各自重复的 4 行校验块。

### 6.4 B3（缓存，零风险两项）

- `GameState`：新增 `private val talentEngine = TalentEngine()` object 字段——A1 的 `talentMultipliers` 调用点复用该实例（替代每次 new；顺带保留 `ProgressionEngine` 现状，其 new 成本等同，不扩大改动面）
- `GameService`：`prereqMap` 计算结果缓存字段，`loadContent` 时失效重建
- PityCounter 构造注入：**不做**（D6）

### 6.5 B5（stat 删除）

`GameState.stat(s: UnitStats, idx: Int)` 及其配套注释删除；实施时 `codegraph_callers` 复核确认零调用者后移除。

---

## 7. 测试与验证策略

| 项 | 手段 |
|---|---|
| 构建 | `.\gradlew.bat :app:assembleDebug`（exit 0） |
| 单测 | `.\gradlew.bat :app:testDebugUnitTest` 全绿 |
| 新增单测 | TalentEngineTest：talentMultipliers 6 用例（见 4.3） |
| 既有测试 | **零修改**（PityCounterTest 不动——D6） |
| UI 行为 | 无 UI 自动化基础设施（现状如实）；等价性 = 逐组件 diff + 构建 + 单测 |
| 视觉目验 | D5 两项变化由用户真机/模拟器确认（我无法在本环境渲染 Compose） |
| 提交粒度 | 4 个独立提交：① A1 数值下沉 ② A2/A3 组件收敛 ③ A4 按钮 ④ A5+B 清理 |

---

## 8. 待用户确认项

1. **D5-①**：首页按钮圆角 → 斜切角（换正式版）
2. **D5-②**：GlassPanel 统一正式版（新增内高光）

确认后本规格转「已批准」，即可进入实施计划。
