# Milan 天赋树填充设计规格（P1）

> 日期：2026-08-07 · 状态：已批准（设计草案经用户逐项确认）

让天赋系统从「正式数据路径下不可用」升级为**数据驱动、效果真实、战斗可见**的完整养成系统。本规格是 P1 子项目（天赋树填充）的设计蓝本，后续 P2 角色扩充 / P3 深化系统 / P4 新增玩法各自独立走「设计 → 计划 → 实现」流程。

---

## 1. 现状问题（本设计的动机）

| # | 问题 | 位置 |
|---|---|---|
| P1-1 | **正式路径天赋树为空**：data.json 的 `Nodes: []`（空数组非 null）通过 `t.Nodes != null` 过滤，`TalentTrees.Count == 28 ≠ 0` 使 `BuildTalentTrees()` 不执行 → 真机天赋面板只剩 3 个空分支标题，节点完全不可分配。6 节点模板仅在 data.json 加载失败走 `LoadFallback()` 时出现 | GameService.cs:95-106、159-194 |
| P1-2 | **文案与效果脱节**：模板文案「攻击力+10%」「无视敌方15%防御」，实际 `ComputeStatsAt` 按分支硬编码每节点 +3%（power→攻、defense→防+血、utility→速）。`TalentNodeData` 无效果字段，全魔法数字 | GameState.cs:96-108 |
| P1-3 | **战斗引擎无机制概念**：`UnitStats` 仅 Atk/Def/Hp/Spd；`StrikeDamage = Max(1, Atk - Def/2)` 纯减法；无暴击/闪避/减伤/状态/必杀任何概念。手动出牌与自动战斗共用此单一伤害入口 | BattleSimulator.cs:12-61 |

---

## 2. 决策记录（用户已确认）

| # | 决策 | 内容 |
|---|---|---|
| D1 | 效果深度 | 三层全做：纯属性加成 + 战斗机制（15 种）+ 必杀强化 |
| D2 | 内容形态 | 普通角色（R/SR/SSR，21 个）按世界模板；UR 角色（7 个）按背景故事单独定制 |
| D3 | 节点规模 | 3 分支 × 4 级 + 1 必杀 = **13 节点/树**（基础 1 费 → 进阶 2 费 → 大师 3 费 → 宗师 4 费，必杀 5 费） |
| D4 | 机制清单 | 6 基础：无视防御、伤害减免、闪避、暴击/暴击伤害、吸血、反伤；9 状态：中毒、燃烧、流血、缴械、霸体、嘲讽、僵直、冰冷、晕眩 |
| D5 | 必杀落地 | 手动必杀按钮 + 能量槽（攻击/受击积攒能量，满后点亮可释放） |
| D6 | 数据放置 | 混合：世界模板代码参数化生成 + UR 定制树写入 data.json（Assets 与 Resources/Raw 两处同步）；加载逻辑改为「data.json 节点缺失时按世界模板补全」 |

---

## 3. 架构分层

```
数据模型  Core/Domain/Progression  TalentEffectType 枚举 + TalentEffect 类 + TalentNodeData.Effects
效果引擎  Core/Domain/Progression  TalentEffectResolver（节点效果 → 数值/机制字段）纯 .NET 可单测
战斗引擎  Core/Domain/Battle       UnitStats 扩展 + StatusEffect 状态系统 + StrikeDamage 重写 + Ultimate 必杀
属性计算  UI/GameState             ComputeStatsAt 改为读节点效果（不再分支硬编码）
内容数据  BuildTalentTrees 重构（世界模板参数化）+ data.json 写入 UR 定制树（两处同步）
UI        ProgressionActivity（13 节点渲染）+ BattleActivity（必杀按钮 + 能量槽）
```

约束：`Core/` 为纯 .NET，禁止 `using Android.*`；数值公式单一事实来源为 `EconomyFormulas`（新增公式必须进该文件）；`GameService.cs` 的 `IncludeFields = true` 不可删。

---

## 4. 数据模型（Core/Domain/Progression）

```csharp
public enum TalentEffectType
{
    // 属性（百分比，进 ComputeStats）
    AtkPercent, DefPercent, HpPercent, SpdPercent,
    // 常驻机制（进 UnitStats 字段）
    IgnoreDefense, DamageReduction, DodgeRate, CritRate, CritDamage, Lifesteal, Thorn,
    // 状态施加（攻击概率施加，需 Duration）
    Poison, Burn, Bleed,           // DoT：毒(最大生命%)/燃(固定)/血(攻击%)
    Disarm, Stun, Chill,           // 控制：缴械(禁攻)/晕眩(跳回合)/冰冷(减速)
    Taunt, Unstoppable,            // 嘲讽(强制目标)/霸体(免控)
    // 必杀
    UltimateDamage, ChargeGain,    // 必杀伤害%/充能效率%
}

public class TalentEffect
{
    public TalentEffectType Type;
    public float Value;            // 数值或百分比（0.1 = 10%）
    public float Chance;           // 状态施加概率（0-1），非状态 = 0
    public int Duration;           // 状态持续回合，非状态 = 0
}
```

- **`TalentNodeData` 新增 `Effects: List<TalentEffect>`**——节点效果唯一载体，文案与效果同源（修 P1-2）。
- `TalentEngine.CanAllocate / TotalPoints` 的前置校验与点数逻辑**保持不变**（回归测试锁定）。
- 新增文件：`Core/Domain/Progression/TalentEffects.cs`（枚举 + 效果类 + 解析器）。

---

## 5. 战斗引擎扩展（Core/Domain/Battle）

### 5.1 UnitStats 新字段（常驻，由 ComputeStats 从天赋效果填充）

```csharp
public float IgnoreDefense;    // 0-1，无视目标防御比例
public float DamageReduction;  // 0-1，受伤减免
public float DodgeRate;        // 0-1，闪避率
public float CritRate;         // 0-1，暴击率
public float CritDamage;       // 倍率，基础 1.5
public float Lifesteal;        // 0-1，吸血比例
public float Thorn;            // 0-1，反伤比例
public float UltimateDamage;   // 必杀伤害加成（倍率加算，基 2.0）
public float ChargeGain;       // 0-1，充能效率加成
```

### 5.2 StrikeDamage 结算链（重写，手动/自动仍共用单一入口）

```
1. 闪避判定：随机 < DodgeRate → 返回 0 伤害（闪避）
2. 伤害 = Max(1, Atk - Def × (1 - IgnoreDefense) / 2)
3. 暴击判定：随机 < CritRate → 伤害 × CritDamage
4. 防御方减伤：伤害 × (1 - DamageReduction)
5. 攻击方吸血：攻击方回复 Lifesteal × 实际伤害（上限当前最大生命）
6. 防御方反伤：防御方对攻击方造成 Thorn × 实际伤害（反伤不再二次触发减伤/吸血/反伤，防止无限递归）
```

### 5.3 状态系统（新）

- `StatusEffect { TalentEffectType Type; float Value; int DurationTurns; string SourceId; }`，挂在战斗单位。
- **回合开始**结算：DoT 扣血（毒 = 最大生命 × Value、燃 = 固定 Value、血 = 攻击 × Value），然后 Duration 递减，0 移除。
- **行动前**控制判定：晕眩 → 跳过行动；僵直 → 跳过行动（与晕眩同效果，名称区分）；缴械 → 跳过攻击；冰冷 → 本回合速度 × (1 - Value)；嘲讽 → 目标选择强制锁定嘲讽者；霸体 → 免疫上述全部控制（施加时即被拒绝）。
- 状态施加入口：攻击方按命中后触发（节点 Chance/Duration），**统一走新 `StatusEngine`（纯 .NET，可单测）**，`BattleSimulator` 只编排回合。
- `Simulate` 的自动战斗与手动 `StrikeDamage` 路径共用同一状态结算逻辑（单一事实来源，避免双套实现漂移）。

### 5.4 必杀系统（新）

- 能量槽 0-100：攻击 +15、受击 +10（受 `ChargeGain` 加成）。
- 必杀 = 单体高倍攻击：`Atk × (2.0 + UltimateDamage)`，**无视防御 50%、必中**（不可闪避），可暴击。
- 释放后能量清零；无冷却，满即可放。
- 新文件：`Core/Domain/Battle/StatusEngine.cs`、必杀结算并入 `BattleSimulator`（`TryUltimate` 方法）。

---

## 6. 属性计算改造（UI/GameState.cs）

- `ComputeStatsAt`（GameState.cs:78-118）**删除分支硬编码 +3% 逻辑**，改为：遍历已点亮节点 → `TalentEffectResolver` 解析 `Effects` → 累计属性类效果（AtkPercent 等）与常驻机制字段（IgnoreDefense 等）→ 生成 `UnitStats`。
- 属性类效果公式：`最终 = 基础 × (1 + Σ 属性百分比)`，与现有 `starMul`/等级倍率链兼容。
- 战斗页/详情页/养成页预览共用此单一入口（注释声明不变）。

---

## 7. 内容设计

### 7.1 世界模板（代码参数化，21 普通角色套用）

每套 13 节点：3 分支 × 4 级（基础 1 费 → 进阶 2 费 → 大师 3 费 → 宗师 4 费）+ 树顶必杀 5 费。**分支主题按世界差异化**：

| 世界 | 强攻分支 | 坚壁分支 | 灵动分支 | 必杀倾向 |
|---|---|---|---|---|
| Shinwa 神华 | 破甲/暴击（物理爆发） | 减伤/反伤 | 闪避/速度 | 单体毁灭 |
| Aether 以太 | 燃烧/中毒（元素 DoT） | 吸血/减伤 | 冰冷/控制 | 元素风暴 |
| Ironveil 铁幕 | 流血/缴械（压制） | 霸体/嘲讽（坦克） | 僵直/精准 | 装甲突击 |

- 每节点：名称/描述/效果（1-2 个 TalentEffect）/费用/前置，全部参数化生成。
- 数值随稀有度分层：**R = 基准 ×0.7、SR = ×1.0、SSR = ×1.3**（UR 不走模板）。基准数值定义在 `EconomyFormulas` 或模板常量表（禁止魔法数字散落）。
- 必杀节点名按世界前缀 + 通用词（如「神华·灭世」），UR 必杀名按背景定制。

### 7.2 UR 定制树（7 棵，写入 data.json 两处同步）

| 角色 | 世界/元素 | 定制方向（按 Lore） |
|---|---|---|
| char_ur_zhulong 烛龙 | Shinwa/Flame | 燃烧强化（灼烧伤害+50%）、火暴击、必杀「焚天灭世」（UltimateDamage+40%、ChargeGain+20%） |
| char_ur_wuxu 虚无 | Aether/Void | 中毒/虚无 DoT、减伤、必杀吞噬流 |
| char_ur_xingtian 刑天 | Ironveil/Metal | 流血/缴械压制、霸体、必杀破军 |
| char_ur_kikyo 桔梗 | Shinwa/Shadow | 闪避/暗袭、暴击、必杀幽冥 |
| char_ur_keqing 刻晴 | Aether/Thunder | 速度/雷暴连击、ChargeGain、必杀雷鸣 |
| char_ur_jinwu 金乌 | Shinwa/Flame | 燃烧+速度、必杀烈日 |
| char_ur_nuwa 女娲 | Aether/Earth | 减伤/反伤/吸血（造物守护）、必杀补天 |

- 每 UR 树 13 节点全定制：名称/描述/效果/必杀独立，效果数值由设计值给出（P1 计划阶段落到具体数值表）。
- **两处同步**：`MauiMilan/Platforms/Android/Assets/data.json` 与 `MauiMilan/Resources/Raw/data.json`（AGENTS.md 铁律）。

### 7.3 加载逻辑修复（修 P1-1）

`GameService.Initialize`（95-106 行）talent 加载改为：

```
遍历 root.TalentTrees：
  Nodes 非空（UR 定制树）→ 保留
  Nodes 为空/缺失（普通角色）→ 按该角色 World 套世界模板生成（BuildTree(ch)）
```

`LoadFallback`（123-128）同步受益（现有 BuildTalentTrees 升级为模板版本）。`EnrichCharacters` 之后执行，保证 def 可用。

---

## 8. UI 变更

- **ProgressionActivity.FillTalent**（515-559）：每列 4 节点（基础→进阶→大师→宗师）+ 树顶横跨的必杀节点（金色高亮、独立渲染、可点击分配）。3 列布局不变。
- **BattleActivity**：操作栏新增「必杀」按钮（能量满点亮，点击对当前敌人释放）+ 敌人面板下方能量槽（0-100 进度条，攻击/受击时更新）。手动攻击路径与自动战斗路径共用 `TryUltimate`。
- 天赋点显示逻辑不变（升级每级 +1 点）；13 节点共需 35 点，UR 满级 80 点富余，R/SR 角色点数不足部分通过突破/升星补足（数值在 P1 计划阶段经 `EconomyFormulas` 定，若需调整升级点数产出则单独提方案）。

---

## 9. 测试计划（Tests/Milan.Tests，纯 .NET 可测）

| 测试文件 | 覆盖 |
|---|---|
| TalentTests 扩展 | 效果解析（节点 → 数值/机制字段）、世界模板生成（3 世界 × 13 节点、费用/前置正确）、稀有度分层系数、UR 定制树反序列化 |
| BattleTests 扩展 | 15 机制逐个单测：闪避/暴击/减伤/无视防御/吸血/反伤 + 5 DoT/控制 + 嘲讽/霸体 + 必杀充能/释放/倍率；反伤不递归；DoT 回合递减 |
| GameStateTests 新增 | ComputeStatsAt 节点效果 → 属性/机制字段正确（替换魔法数字断言） |
| 回归 | 现有 66 测试保持通过 |

测试工程注意：`Tests/Milan.Tests/Milan.Tests.csproj` 用 `<Compile Include>` 直接链接 Core 文件，**新增纯领域文件必须手动加入 Compile 列表**（AGENTS.md）。

---

## 10. 范围边界（本 P1 不做）

- ❌ 敌人侧状态/必杀（敌人保持脚本模板，留待 P3 深化）
- ❌ 天赋重置/洗点功能
- ❌ 元素克制伤害倍率（元素仅显示，机制扩展留待 P3）
- ❌ 角色主动技能系统（必杀之外的技能，P4 玩法范畴）
- ❌ UR 定制树的最终数值平衡（P1 计划阶段产出数值表，本规格定框架）

---

## 11. 验收标准

1. 真机（或加载 data.json 路径）天赋面板显示 13 节点/树，可分配、前置正确、落盘回滚生效（P1-1 修复）。
2. 点亮节点后，详情页/养成页/战斗页属性与机制字段同步变化，文案与效果一致（P1-2 修复）。
3. 战斗中出现暴击/闪避/减伤/DoT/控制/嘲讽/霸体/必杀等可观察表现（P1-3 修复），手动与自动战斗行为一致。
4. 必杀能量槽随攻击/受击增长，满能量按钮点亮，释放高倍伤害。
5. 21 普通角色树按世界模板生成，7 UR 树定制生效且 data.json 两处一致。
6. 新增测试全绿，现有 66 测试回归通过。
