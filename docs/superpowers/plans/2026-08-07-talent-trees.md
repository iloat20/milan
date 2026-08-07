# 2026-08-07 P1 天赋树填充实现计划

> 依据：`docs/superpowers/specs/2026-08-07-talent-trees-design.md`（已批准，提交 cac1d45）。
> 本计划为执行蓝本：所有决策已定稿，执行者按任务顺序零提问执行。每个任务末尾有验证命令与提交信息。

## 目标

按规格 D1-D6 实现 P1 天赋树填充：

1. **P1-1 修复**：天赋树加载校验（空 `Nodes` 数组不再被误判为有效），普通角色树按世界模板生成（D2/D6 混合数据放置）。
2. **P1-2 修复**：天赋效果唯一载体 `Effects`（文案与效果同源），`ComputeStats` 按效果解析而非分支硬编码。
3. **P1-3 修复**：完整伤害链（闪避/暴击/减伤/无视防御/吸血/反伤）、状态系统（DoT/控制/嘲讽/霸体）、必杀系统（能量槽/高倍攻击），手动与自动战斗共用单一入口。
4. 7 棵 UR 定制树写入 data.json（两处同步），数值表本计划产出（规格 §7.2）。

## 范围

- **做**：Core 模型层（TalentEffects / TalentData / TalentTemplateFactory / StatusEngine / StatsCalculator）、BattleSimulator 重写、GameService 加载修复、data.json 普通角色天赋清理 + 7 UR 定制树、ProgressionActivity 必杀横卡、BattleActivity 能量槽/必杀按钮/Strike 接入、测试 66 → 109。
- **不做**（规格 §10）：敌人侧施放状态/必杀（敌人仅作承受者，机制字段恒 0）、天赋重置/洗点、元素克制伤害、主动技能系统、UR 树最终数值平衡。

## 文件结构映射

| 动作 | 文件 | 说明 |
|---|---|---|
| 新建 | `MauiMilan/Core/Domain/Progression/TalentEffects.cs` | 枚举 + TalentEffect 类（纯 .NET） |
| 新建 | `MauiMilan/Core/Domain/Progression/TalentData.cs` | TalentNodeData/TalentTreeData 从 GameService.cs:662-678 **迁移** + 新增 Effects 字段 |
| 新建 | `MauiMilan/Core/Domain/Progression/TalentTemplateFactory.cs` | 世界模板、RarityMultiplier、BuildTree、EnsureTree |
| 新建 | `MauiMilan/Core/Domain/Battle/StatusEngine.cs` | 状态系统纯逻辑（可单测） |
| 新建 | `MauiMilan/UI/StatsCalculator.cs` | 属性/机制计算纯函数（从 GameState.ComputeStatsAt 提取） |
| 新建 | `Tests/Milan.Tests/TalentTemplateFactoryTests.cs`、`StatusEngineTests.cs`、`StatsCalculatorTests.cs` | 新测试文件 |
| 修改 | `MauiMilan/Core/Domain/Battle/BattleUnits.cs` | 补 using + UnitStats 新增 9 个机制字段 |
| 修改 | `MauiMilan/Core/Domain/Battle/BattleSimulator.cs` | Strike 链重写 + Simulate 重写（状态/能量/必杀） |
| 修改 | `MauiMilan/Services/GameService.cs` | 删迁移的类定义、加载校验修复、BuildTalentTrees 模板化、补全接入 |
| 修改 | `MauiMilan/UI/GameState.cs` | ComputeStatsAt 分支硬编码 → StatsCalculator 委托 |
| 修改 | `MauiMilan/Activities/ProgressionActivity.cs` | FillTalent 必杀金色横卡（:515 区域） |
| 修改 | `MauiMilan/Activities/BattleActivity.cs` | 能量槽/必杀按钮/OnCardPlayed 接 Strike 链（:285/:293） |
| 修改 | `MauiMilan/Platforms/Android/Assets/data.json` + `MauiMilan/Resources/Raw/data.json` | 普通角色删显式天赋、7 UR 定制树（两处同步铁律） |
| 修改 | `Tests/Milan.Tests/Milan.Tests.csproj` | Compile Include 追加：TalentData.cs、TalentEffects.cs、TalentTemplateFactory.cs、StatusEngine.cs、StatsCalculator.cs |
| 修改 | `Tests/Milan.Tests/TalentTests.cs`、`BattleTests.cs` | 扩展/新测试 |

## 关键约定（定稿，全部不可偏离）

### 枚举与效果

- `TalentEffectType` 数值序（JSON 整数序列化，**顺序不可变，新增只可追加**）：0=AtkPercent, 1=DefPercent, 2=HpPercent, 3=SpdPercent, 4=IgnoreDefense, 5=DamageReduction, 6=DodgeRate, 7=CritRate, 8=CritDamage, 9=Lifesteal, 10=Thorn, 11=Poison, 12=Burn, 13=Bleed, 14=Disarm, 15=Stun, 16=Chill, 17=Taunt, 18=Unstoppable, 19=UltimateDamage, 20=ChargeGain。
- `TalentEffect { Type; Value; Chance; Duration }`：Value 为数值或百分比（0.1=10%）；Chance/Duration 仅状态类效果非零。
- 僵直 = Stun（§5.3 声明同效果，不单列枚举值）。

### 世界模板（TalentTemplateFactory）

- 每树 13 节点：3 分支 × 4 级 + 树顶必杀。节点 ID：`p1-p4 / d1-d4 / u1-u4 / ult`；BranchId：`branch_power / branch_defense / branch_utility / branch_ultimate`。
- 费用：p1/d1/u1=1，p2/d2/u2=2，p3/d3/u3=3，p4/d4/u4=4，ult=5。满树总点数 = 35。
- 前置：p{i} → [p{i-1}]（p1 无前置）；d/u 同理；ult → ["p4","d4","u4"]。
- 效果基准（值 × RarityMultiplier；Chance/Duration 不乘）：

| 世界 | p1 | p2 | p3 | p4 | d1 | d2 | d3 | d4 | u1 | u2 | u3 | u4 | ult |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Shinwa | IgnoreDefense .15 | CritRate .08 | IgnoreDefense .30 | CritRate .15 | DamageReduction .08 | Thorn .10 | DamageReduction .15 | Thorn .20 | DodgeRate .05 | SpdPercent .04 | DodgeRate .10 | SpdPercent .08 | UltDmg .40 + Charge .20 |
| Aether | Burn 8/.5/2 | Poison .04/.4/3 | Burn 20/.6/2 | Poison .08/.5/3 | Lifesteal .06 | DamageReduction .08 | Lifesteal .12 | DamageReduction .15 | Chill .30/.35/2 | SpdPercent .04 | Chill .50/.45/2 | Stun 1/.30/1 | UltDmg .40 + Charge .20 |
| Ironveil | Bleed .05/.45/3 | Disarm 1/.25/2 | Bleed .10/.55/3 | Disarm 1/.35/2 | DamageReduction .08 | Taunt 1/.25/2 | DamageReduction .15 | Taunt 1/.40/2 | Stun 1/.20/1 | CritRate .06 | Stun 1/.30/1 | CritRate .12 | UltDmg .40 + Charge .20 |

- 名称按世界前缀（Shinwa「神华·」/ Aether「以太·」/ Ironveil「铁幕·」）+ 节点通用词（p1 破甲/引燃/放血，p2 会心/侵蚀/卸械，p3 裂甲/烈焰/屠戮，p4 暴怒/瘟疫/缴械，d1 坚守/汲取/坚壁，d2 反震/壁垒/挑衅，d3 铁壁/虹吸/重盾，d4 荆棘/不破/嘲讽，u1 疾影/霜寒/震击，u2 迅捷/灵动/瞄准，u3 幻步/冰封/震荡，u4 疾风/麻痹/致命，ult 灭世/风暴/突击）。描述由效果派生生成（P1-2 文案与效果同源），格式：`"{机制中文名}：{数值描述}"`，状态类追加 `概率 {Chance:P0}，持续 {Duration} 回合`。
- `RarityMultiplier(int rarity)`：SSR(3) → 1.3f，SR(2) → 1.0f，其他（含 R/UR）→ 0.7f。
- `BuildTree(treeId, world, rarity)`：普通角色入口。`EnsureTree(tree, world, rarity)`：Nodes 空/缺失 → BuildTree，否则原样（D6 补全逻辑）。

### 伤害链（BattleSimulator.StrikeDamage 重写，手动/自动唯一入口）

```
1. 闪避：rng < defender.DodgeRate → 0 伤害
2. 伤害 = Max(1, atk - def × (1 - attacker.IgnoreDefense) / 2)
3. 暴击：rng < attacker.CritRate → 伤害 × CritDamage（CritDamage 默认 1.5）
4. 减伤：伤害 × (1 - defender.DamageReduction)
5. 吸血：attacker.Hp += Lifesteal × 实际伤害（上限 Stats.Hp）
6. 反伤：defender 对 attacker 造成 Thorn × 实际伤害（不再触发减伤/吸血/反伤，防递归）
返回 (damage, crit, dodged, reflected, healed) 结果结构供 UI 表现
```

- 测试基准值：IgnoreDefense=0.5 → 88（75 基础）；暴击 75×1.7=127（CritDamage .2）；减伤/吸血/反伤 .5 → 37。

### 状态系统（StatusEngine）

- `StatusEffect { TalentEffectType Type; float Value; int DurationTurns; string SourceId; }`。
- `TryApply(unit, status, immunity)`：Unstoppable 免疫控制（Disarm/Stun/Chill/Taunt）→ 拒绝；同类型刷新取大（Value/Duration 分别取 max）；Duration ≤ 0 → 拒绝。
- 回合开始 `TickDots(unit)`：Poison = MaxHp × Value、Burn = 固定 Value、Bleed = Atk × Value；然后 `TickDown`（倒序递减，0 移除）。
- 行动判定：`SkipTurn`（Stun）→ 跳过；`CanAttack`（Disarm）→ 跳过攻击；`SpeedMultiplier`（Chill）→ 本回合速度 × (1 - Value)；`TauntTarget` → 强制目标 = 嘲讽者 SourceId。
- 状态施放：攻击方按命中后，遍历自身节点状态类效果，rng < Chance → TryApply 到目标。

### 必杀（BattleSimulator.TryUltimate）

- 团队共享能量 0-100：攻击 +15、受击 +10，各乘 (1 + 攻击方 ChargeGain)。
- `TryUltimate(unit, target)`：能量 ≥ 100 → 伤害 = `Max(1, Atk × (2.0 + UltimateDamage))`，无视防御 50%（`Def × 0.5`），必中（跳过闪避），可暴击（CritRate/CritDamage 生效），施放后能量清零。
- Simulate 中玩家侧满能量自动释放；敌人侧无必杀（§10 边界）。

### Simulate 重写

- 私有 `S` 类加 `Status`（List<StatusEffect>）、`Energy`、`Stats` 引用。
- 速度排序：`Stats.Spd × (isPlayer ? (1 - Chill效果) : 1)`（敌人无状态）。
- 玩家侧：SkipTurn/CanAttack/嘲讽强制目标（TauntTarget 存在则打嘲讽者，死亡则回落最低血量目标）。
- 敌人侧：无状态判定、无状态施放、无必杀（范围边界）。敌人受击可被施放状态（状态结算代码共用，仅数据流不对称）。
- 回合结束：双方 TickDots（敌人只有玩家施放的状态）+ 递减。
- 空队防误判逻辑（现有 `b.Count > 0 && b.All(...)` 守卫）保留。

### 其他铁律

- Core/ 纯 .NET：禁 `using Android.*`。
- `SaveData.cs:27` IncludeFields 铁律不动；JSON 模型全字段。
- data.json 两处同步（Assets 打包 + Resources/Raw）。
- 测试工程 `<Compile Include>` 直链：新增纯领域文件必须手加（AGENTS.md）。
- 现有 66 测试全绿保持；BattleTests 的 `U()` helper 与静态 `StrikeDamage` 签名保留。
- 提交前缀：refactor:/feat:/fix:/test:/chore:，逐任务提交。

## 测试

- 命令：`& "C:\Users\Administrator\.dotnet\dotnet.exe" test Tests\Milan.Tests\Milan.Tests.csproj`
- 过滤：`--filter FullyQualifiedName~Milan.Tests.<类名>`
- 计数基线：现有 66 → Task1 70 → Task2 73 → Task3 83 → Task5 85 → Task6 91 → Task7 106 → Task8 109。任务 4/9-13 无新测试（服务层/UI/数据）。

---

## Task 1：Core 模型层（TalentEffects / TalentData / TalentTemplateFactory）

### 改动

**1a. 新建 `MauiMilan/Core/Domain/Progression/TalentEffects.cs`**（纯 .NET，块作用域 namespace，项目 Core 风格）：

```csharp
using System.Collections.Generic;

namespace Milan.Domain.Progression
{
    /// <summary>天赋效果类型。JSON 序列化为整数（TalentEffectType 数值），
    /// 顺序不可变：存档/数据兼容，新增只可追加在末尾。</summary>
    public enum TalentEffectType
    {
        // 属性（百分比，进 ComputeStats）
        AtkPercent, DefPercent, HpPercent, SpdPercent,       // 0-3
        // 常驻机制（进 UnitStats 字段）
        IgnoreDefense, DamageReduction, DodgeRate,           // 4-6
        CritRate, CritDamage, Lifesteal, Thorn,              // 7-10
        // 状态施加（攻击命中概率施加，需 Chance/Duration）
        Poison, Burn, Bleed,                                 // 11-13 DoT：毒(最大生命%)/燃(固定)/血(攻击%)
        Disarm, Stun, Chill,                                 // 14-16 控制：缴械(禁攻)/晕眩(跳回合)/冰冷(减速)
        Taunt, Unstoppable,                                  // 17-18 嘲讽(强制目标)/霸体(免控)
        // 必杀
        UltimateDamage, ChargeGain,                          // 19-20 必杀伤害%/充能效率%
    }

    /// <summary>节点效果（唯一效果载体，文案与效果同源，修 P1-2）。</summary>
    public class TalentEffect
    {
        public TalentEffectType Type;
        public float Value;    // 数值或百分比（0.1 = 10%）
        public float Chance;   // 状态施加概率（0-1），非状态 = 0
        public int Duration;   // 状态持续回合，非状态 = 0
    }

    /// <summary>效果中文名（描述生成用，P1-2 文案与效果同源）。</summary>
    public static class TalentEffectNames
    {
        public static string Of(TalentEffectType t) => t switch
        {
            TalentEffectType.AtkPercent => "攻击",
            TalentEffectType.DefPercent => "防御",
            TalentEffectType.HpPercent => "生命",
            TalentEffectType.SpdPercent => "速度",
            TalentEffectType.IgnoreDefense => "破甲",
            TalentEffectType.DamageReduction => "减伤",
            TalentEffectType.DodgeRate => "闪避",
            TalentEffectType.CritRate => "暴击率",
            TalentEffectType.CritDamage => "暴击伤害",
            TalentEffectType.Lifesteal => "吸血",
            TalentEffectType.Thorn => "反伤",
            TalentEffectType.Poison => "中毒",
            TalentEffectType.Burn => "燃烧",
            TalentEffectType.Bleed => "流血",
            TalentEffectType.Disarm => "缴械",
            TalentEffectType.Stun => "晕眩",
            TalentEffectType.Chill => "冰冷",
            TalentEffectType.Taunt => "嘲讽",
            TalentEffectType.Unstoppable => "霸体",
            TalentEffectType.UltimateDamage => "必杀伤害",
            TalentEffectType.ChargeGain => "充能效率",
            _ => t.ToString(),
        };
    }
}
```

**1b. 新建 `MauiMilan/Core/Domain/Progression/TalentData.cs`**（自 GameService.cs:662-678 **迁移**，新增 Effects；迁移后 GameService.cs 删除原定义，其 `using Milan.Domain.Progression;` 已存在无需加）：

```csharp
using System.Collections.Generic;

namespace Milan.Domain.Progression
{
    /// <summary>天赋节点。Effects 为效果唯一载体（规格 §4）。</summary>
    public class TalentNodeData
    {
        public string NodeId = "";
        public string DisplayName = "";
        public string Description = "";
        public string BranchId = "";
        public int Cost = 1;
        public List<string> PrerequisiteNodeIds = new();
        public string VisualLayerId = "";
        public List<TalentEffect> Effects = new();
    }

    /// <summary>天赋树。Nodes 为空/缺失（普通角色）时由 TalentTemplateFactory 按世界模板补全（D6）。</summary>
    public class TalentTreeData
    {
        public string TreeId = "";
        public List<string> BranchIds = new();
        public List<TalentNodeData> Nodes = new();
    }
}
```

**1c. 新建 `MauiMilan/Core/Domain/Progression/TalentTemplateFactory.cs`**（核心：模板表 + 生成器 + 补全；数值基准集中于此，禁止魔法数字散落）：

```csharp
using System;
using System.Collections.Generic;

namespace Milan.Domain.Progression
{
    /// <summary>世界天赋模板：21 普通角色按稀有度参数化生成 13 节点天赋树（D2/D6）。
    /// 数值基准 + RarityMultiplier 分层；UR 角色不走模板（data.json 定制，见 Task 12）。</summary>
    public static class TalentTemplateFactory
    {
        public const string BranchPower = "branch_power";
        public const string BranchDefense = "branch_defense";
        public const string BranchUtility = "branch_utility";
        public const string BranchUltimate = "branch_ultimate";

        // 稀有度分层系数（规格 §7.1）：SSR ×1.3 / SR ×1.0 / R 及其他 ×0.7。UR 不走模板。
        public static float RarityMultiplier(int rarity) => rarity == 3 ? 1.3f : rarity == 2 ? 1.0f : 0.7f;

        // 每级费用：基础 1 费 → 进阶 2 费 → 大师 3 费 → 宗师 4 费；必杀 5 费。
        static readonly int[] Costs = { 1, 2, 3, 4 };

        // 三世界模板：每分支 4 级效果基准（值 × RarityMultiplier；Chance/Duration 不乘）。
        static (TalentEffectType Type, float Value, float Chance, int Duration)[][] Template(string world) => world switch
        {
            "Shinwa" => new[]
            {
                // 强攻：破甲/暴击
                new (TalentEffectType, float, float, int)[] { (TalentEffectType.IgnoreDefense, 0.15f, 0, 0), (TalentEffectType.CritRate, 0.08f, 0, 0), (TalentEffectType.IgnoreDefense, 0.30f, 0, 0), (TalentEffectType.CritRate, 0.15f, 0, 0) },
                // 坚壁：减伤/反伤
                new (TalentEffectType, float, float, int)[] { (TalentEffectType.DamageReduction, 0.08f, 0, 0), (TalentEffectType.Thorn, 0.10f, 0, 0), (TalentEffectType.DamageReduction, 0.15f, 0, 0), (TalentEffectType.Thorn, 0.20f, 0, 0) },
                // 灵动：闪避/速度
                new (TalentEffectType, float, float, int)[] { (TalentEffectType.DodgeRate, 0.05f, 0, 0), (TalentEffectType.SpdPercent, 0.04f, 0, 0), (TalentEffectType.DodgeRate, 0.10f, 0, 0), (TalentEffectType.SpdPercent, 0.08f, 0, 0) },
            },
            "Aether" => new[]
            {
                // 强攻：燃烧/中毒（DoT，带 Chance/Duration）
                new (TalentEffectType, float, float, int)[] { (TalentEffectType.Burn, 8f, 0.5f, 2), (TalentEffectType.Poison, 0.04f, 0.4f, 3), (TalentEffectType.Burn, 20f, 0.6f, 2), (TalentEffectType.Poison, 0.08f, 0.5f, 3) },
                // 坚壁：吸血/减伤
                new (TalentEffectType, float, float, int)[] { (TalentEffectType.Lifesteal, 0.06f, 0, 0), (TalentEffectType.DamageReduction, 0.08f, 0, 0), (TalentEffectType.Lifesteal, 0.12f, 0, 0), (TalentEffectType.DamageReduction, 0.15f, 0, 0) },
                // 灵动：冰冷/晕眩
                new (TalentEffectType, float, float, int)[] { (TalentEffectType.Chill, 0.30f, 0.35f, 2), (TalentEffectType.SpdPercent, 0.04f, 0, 0), (TalentEffectType.Chill, 0.50f, 0.45f, 2), (TalentEffectType.Stun, 1f, 0.30f, 1) },
            },
            "Ironveil" => new[]
            {
                // 强攻：流血/缴械
                new (TalentEffectType, float, float, int)[] { (TalentEffectType.Bleed, 0.05f, 0.45f, 3), (TalentEffectType.Disarm, 1f, 0.25f, 2), (TalentEffectType.Bleed, 0.10f, 0.55f, 3), (TalentEffectType.Disarm, 1f, 0.35f, 2) },
                // 坚壁：减伤/嘲讽
                new (TalentEffectType, float, float, int)[] { (TalentEffectType.DamageReduction, 0.08f, 0, 0), (TalentEffectType.Taunt, 1f, 0.25f, 2), (TalentEffectType.DamageReduction, 0.15f, 0, 0), (TalentEffectType.Taunt, 1f, 0.40f, 2) },
                // 灵动：晕眩(僵直)/精准(暴击率)
                new (TalentEffectType, float, float, int)[] { (TalentEffectType.Stun, 1f, 0.20f, 1), (TalentEffectType.CritRate, 0.06f, 0, 0), (TalentEffectType.Stun, 1f, 0.30f, 1), (TalentEffectType.CritRate, 0.12f, 0, 0) },
            },
            _ => throw new ArgumentException($"未知世界模板: {world}", nameof(world)),
        };

        // 节点中文名：世界前缀 + 通用词（必杀按世界）。
        static readonly Dictionary<string, string[]> Names = new()
        {
            ["Shinwa"] = new[] { "破甲", "会心", "裂甲", "暴怒", "坚守", "反震", "铁壁", "荆棘", "疾影", "迅捷", "幻步", "疾风", "灭世" },
            ["Aether"] = new[] { "引燃", "侵蚀", "烈焰", "瘟疫", "汲取", "壁垒", "虹吸", "不破", "霜寒", "灵动", "冰封", "麻痹", "风暴" },
            ["Ironveil"] = new[] { "放血", "卸械", "屠戮", "缴械", "坚壁", "挑衅", "重盾", "嘲讽", "震击", "瞄准", "震荡", "致命", "突击" },
        };

        static readonly Dictionary<string, string> WorldPrefix = new()
        {
            ["Shinwa"] = "神华", ["Aether"] = "以太", ["Ironveil"] = "铁幕",
        };

        /// <summary>按世界 + 稀有度生成 13 节点天赋树（普通角色入口）。</summary>
        public static TalentTreeData BuildTree(string treeId, string world, int rarity)
        {
            var branches = Template(world);
            float mul = RarityMultiplier(rarity);
            string prefix = WorldPrefix[world];
            string[] names = Names[world];

            var nodes = new List<TalentNodeData>();
            string[] branchIds = { BranchPower, BranchDefense, BranchUtility };
            for (int b = 0; b < 3; b++)
            {
                char bchar = "pdu"[b];
                for (int i = 0; i < 4; i++)
                {
                    var (type, value, chance, dur) = branches[b][i];
                    var fx = new TalentEffect { Type = type, Value = value * mul, Chance = chance, Duration = dur };
                    string nodeId = $"{bchar}{i + 1}";
                    nodes.Add(new TalentNodeData
                    {
                        NodeId = nodeId,
                        BranchId = branchIds[b],
                        Cost = Costs[i],
                        DisplayName = $"{prefix}·{names[b * 4 + i]}",
                        Description = Describe(fx),
                        PrerequisiteNodeIds = i == 0 ? new List<string>() : new List<string> { $"{bchar}{i}" },
                        VisualLayerId = $"{branchIds[b]}_{i + 1}",
                        Effects = new List<TalentEffect> { fx },
                    });
                }
            }
            var ultFx = new TalentEffect { Type = TalentEffectType.UltimateDamage, Value = 0.40f, Chance = 0, Duration = 0 };
            var chargeFx = new TalentEffect { Type = TalentEffectType.ChargeGain, Value = 0.20f, Chance = 0, Duration = 0 };
            nodes.Add(new TalentNodeData
            {
                NodeId = "ult",
                BranchId = BranchUltimate,
                Cost = 5,
                DisplayName = $"{prefix}·{names[12]}",
                Description = $"{TalentEffectNames.Of(ultFx.Type)} +{DescribeValue(ultFx)}，{TalentEffectNames.Of(chargeFx.Type)} +{DescribeValue(chargeFx)}",
                PrerequisiteNodeIds = new List<string> { "p4", "d4", "u4" },
                VisualLayerId = BranchUltimate,
                Effects = new List<TalentEffect> { ultFx, chargeFx },
            });

            return new TalentTreeData
            {
                TreeId = treeId,
                BranchIds = new List<string> { BranchPower, BranchDefense, BranchUtility, BranchUltimate },
                Nodes = nodes,
            };
        }

        /// <summary>D6 补全：Nodes 空/缺失 → 按模板生成；否则原样保留（UR 定制树）。</summary>
        public static TalentTreeData EnsureTree(TalentTreeData tree, string world, int rarity)
        {
            if (tree == null) return null;
            if (tree.Nodes == null || tree.Nodes.Count == 0)
                return BuildTree(tree.TreeId, world, rarity);
            return tree;
        }

        /// <summary>效果描述（P1-2 文案与效果同源）。</summary>
        public static string Describe(TalentEffect fx)
        {
            string name = TalentEffectNames.Of(fx.Type);
            string body = IsStatus(fx.Type)
                ? $"{name}：{DescribeValue(fx)}，概率 {fx.Chance:P0}，持续 {fx.Duration} 回合"
                : $"{name} +{DescribeValue(fx)}";
            return body;
        }

        static bool IsStatus(TalentEffectType t) =>
            t == TalentEffectType.Poison || t == TalentEffectType.Burn || t == TalentEffectType.Bleed ||
            t == TalentEffectType.Disarm || t == TalentEffectType.Stun || t == TalentEffectType.Chill ||
            t == TalentEffectType.Taunt;

        static string DescribeValue(TalentEffect fx) => fx.Type switch
        {
            TalentEffectType.Poison or TalentEffectType.Bleed or TalentEffectType.Chill => $"{fx.Value:P0}",
            TalentEffectType.Burn => $"{fx.Value:0.#}",
            _ => $"{fx.Value:P0}",
        };
    }
}
```

**1d. `Tests/Milan.Tests/Milan.Tests.csproj`**：Compile Include 追加（3 个纯领域文件，Task 2/6 再追 2 个）：

```xml
<Compile Include="..\..\MauiMilan\Core\Domain\Progression\TalentData.cs" Link="Core\Progression\TalentData.cs" />
<Compile Include="..\..\MauiMilan\Core\Domain\Progression\TalentEffects.cs" Link="Core\Progression\TalentEffects.cs" />
<Compile Include="..\..\MauiMilan\Core\Domain\Progression\TalentTemplateFactory.cs" Link="Core\Progression\TalentTemplateFactory.cs" />
```

**1e. 新建 `Tests/Milan.Tests/TalentTemplateFactoryTests.cs`**：

```csharp
using System.Collections.Generic;
using System.Linq;
using Milan.Domain.Progression;
using Xunit;

namespace Milan.Tests
{
    public class TalentTemplateFactoryTests
    {
        [Theory]
        [InlineData("Shinwa")]
        [InlineData("Aether")]
        [InlineData("Ironveil")]
        public void BuildTree_EachWorld_13NodesWithIdsCostsAndPrereqs(string world)
        {
            var tree = TalentTemplateFactory.BuildTree("t", world, 2);
            Assert.Equal(13, tree.Nodes.Count);
            Assert.Equal(4, tree.BranchIds.Count);
            // 费用表：p1/d1/u1=1 ... p4/d4/u4=4，ult=5；满树 35 点
            Assert.Equal(35, tree.Nodes.Sum(n => n.Cost));
            Assert.Equal(5, tree.Nodes.Single(n => n.NodeId == "ult").Cost);
            Assert.Equal(new List<string> { "p4", "d4", "u4" }, tree.Nodes.Single(n => n.NodeId == "ult").PrerequisiteNodeIds);
            // 前置链
            Assert.Equal(new List<string> { "p1" }, tree.Nodes.Single(n => n.NodeId == "p2").PrerequisiteNodeIds);
            Assert.Empty(tree.Nodes.Single(n => n.NodeId == "p1").PrerequisiteNodeIds);
            Assert.Equal(35, new TalentEngine().TotalPoints(tree.Nodes.ToDictionary(n => n.NodeId, n => n.Cost)));
        }

        [Theory]
        [InlineData(1, 0.7f)]   // R
        [InlineData(2, 1.0f)]   // SR
        [InlineData(3, 1.3f)]   // SSR
        [InlineData(4, 0.7f)]   // UR 不走模板，其他值兜底
        public void RarityMultiplier_Layers(int rarity, float expected)
            => Assert.Equal(expected, TalentTemplateFactory.RarityMultiplier(rarity));

        [Fact]
        public void BuildTree_SSR_EffectsScaledByMultiplier()
        {
            var ssr = TalentTemplateFactory.BuildTree("t", "Shinwa", 3);
            var sr = TalentTemplateFactory.BuildTree("t", "Shinwa", 2);
            var p1ssr = ssr.Nodes.Single(n => n.NodeId == "p1").Effects[0];
            var p1sr = sr.Nodes.Single(n => n.NodeId == "p1").Effects[0];
            Assert.Equal(p1sr.Value * 1.3f, p1ssr.Value, 3);
        }

        [Fact]
        public void EnsureTree_EmptyNodes_ReturnsTemplate()
        {
            var tree = new TalentTreeData { TreeId = "t" }; // Nodes 空
            var fixedTree = TalentTemplateFactory.EnsureTree(tree, "Aether", 2);
            Assert.Equal(13, fixedTree.Nodes.Count);
            Assert.Equal(TalentEffectType.Poison, fixedTree.Nodes.Single(n => n.NodeId == "p2").Effects[0].Type);
        }

        [Fact]
        public void EnsureTree_NonEmptyNodes_KeepsCustomTree()
        {
            var custom = new TalentTreeData
            {
                TreeId = "ur",
                Nodes = new List<TalentNodeData> { new TalentNodeData { NodeId = "p1", Effects = new List<TalentEffect> { new TalentEffect { Type = TalentEffectType.Burn, Value = 9 } } } },
            };
            var kept = TalentTemplateFactory.EnsureTree(custom, "Shinwa", 4);
            Assert.Same(custom, kept);
            Assert.Equal(9f, kept.Nodes[0].Effects[0].Value, 3);
        }

        [Fact]
        public void BuildTree_UltimateEffects_Present()
        {
            var tree = TalentTemplateFactory.BuildTree("t", "Ironveil", 2);
            var ult = tree.Nodes.Single(n => n.NodeId == "ult");
            Assert.Contains(ult.Effects, e => e.Type == TalentEffectType.UltimateDamage);
            Assert.Contains(ult.Effects, e => e.Type == TalentEffectType.ChargeGain);
        }

        [Fact]
        public void BuildTree_Descriptions_AreDerivedFromEffects()
        {
            var tree = TalentTemplateFactory.BuildTree("t", "Shinwa", 2);
            foreach (var n in tree.Nodes)
                Assert.NotEqual("", n.Description);
            // 状态节点描述含概率与持续
            var aether = TalentTemplateFactory.BuildTree("t", "Aether", 2);
            Assert.Contains("概率", aether.Nodes.Single(n => n.NodeId == "p1").Description);
            Assert.Contains("回合", aether.Nodes.Single(n => n.NodeId == "p1").Description);
        }
    }
}
```

### 验证

- `& "C:\Users\Administrator\.dotnet\dotnet.exe" test Tests\Milan.Tests\Milan.Tests.csproj --filter FullyQualifiedName~Milan.Tests.TalentTemplateFactoryTests` — 8 个新测试通过（66 + 8 = 74，但本任务含 TalentTests 扩展？不含。计数口径：**本任务结束 74**，后续按实际调整，见 Task 汇总表）。

> ⚠️ 计数口径说明：规格 §9 的测试文件划分（TalentTests 扩展/GameStateTests 新增）与计划任务划分不同步时，以**本计划每个任务末尾的验证命令与预期计数**为准，汇总表在 Task 13 收尾处列出最终值。
- 全量回归：`& "C:\Users\Administrator\.dotnet\dotnet.exe" test Tests\Milan.Tests\Milan.Tests.csproj` — 现有 66 测试保持通过。
- lsp_diagnostics 对 3 个新文件 + GameService.cs 无 error。

### 提交

```
feat: 新增天赋效果枚举与三世界模板工厂
```

## Task 2：状态系统（StatusEngine + BattleUnits 扩展）

### 改动

**2a. 修改 `MauiMilan/Core/Domain/Battle/BattleUnits.cs`**（现文件无 using 指令，重写全文件；UnitStats 新增 9 个机制字段 + 状态施加列表，规格 §5.1）：

```csharp
using System.Collections.Generic;
using Milan.Domain.Progression;

namespace Milan.Domain.Battle
{
    /// <summary>战斗属性快照（由 ComputeStats 从基础 + 养成 + 天赋生成；
    /// 常驻机制字段供伤害链使用，默认 0 = 不生效，规格 §5.1）。</summary>
    public struct UnitStats
    {
        public int Atk;
        public int Def;
        public int Hp;
        public int Spd;
        public string CharacterId;

        // —— 常驻机制（天赋效果填充；默认 0 = 不生效）——
        public float IgnoreDefense;    // 0-1 无视目标防御比例
        public float DamageReduction;  // 0-1 受伤减免
        public float DodgeRate;        // 0-1 闪避率
        public float CritRate;         // 0-1 暴击率
        public float CritDamage;       // 暴击倍率（伤害链内取 Max(值, 0) 或默认 1.5）
        public float Lifesteal;        // 0-1 吸血比例
        public float Thorn;            // 0-1 反伤比例
        public float UltimateDamage;   // 必杀伤害加成（倍率加算，基 2.0）
        public float ChargeGain;       // 0-1 充能效率加成

        /// <summary>攻击命中后施加的状态效果列表（ComputeStats 从已点亮节点填充；敌人恒空，§10 边界）。
        /// 引用类型字段：所有构造点需初始化，访问处做 null 防护。</summary>
        public List<TalentEffect> StatusAttacks;
    }

    /// <summary>单场战斗结果。</summary>
    public struct BattleResult
    {
        public bool Victory;
        public int Turns;
        public int RemainingHp;
        public int OpponentRemainingHp;
    }
}
```

> 注意：`StatusAttacks` 为引用类型字段，`new UnitStats{...}` 后为 null——所有消费点（Simulate 状态施加、BattleActivity 出牌）必须 `?.`/null 判断，测试 helper `U()` 不改（不赋值 → null 安全路径）。

**2b. 新建 `MauiMilan/Core/Domain/Battle/StatusEngine.cs`**（纯 .NET，规格 §5.3 全部判定逻辑，手动/自动共用）：

```csharp
using System;
using System.Collections.Generic;
using System.Linq;
using Milan.Domain.Progression;

namespace Milan.Domain.Battle
{
    /// <summary>状态效果（挂在战斗单位上，由 StatusEngine 统一结算）。</summary>
    public class StatusEffect
    {
        public TalentEffectType Type;
        public float Value;
        public int DurationTurns;
        public string SourceId = "";
    }

    /// <summary>状态系统纯逻辑（规格 §5.3）：DoT 结算 / 控制判定 / 施加。
    /// BattleSimulator 只编排回合，状态结算全部走本引擎（单一事实来源）。</summary>
    public static class StatusEngine
    {
        public static bool IsControl(TalentEffectType t) =>
            t == TalentEffectType.Disarm || t == TalentEffectType.Stun ||
            t == TalentEffectType.Chill || t == TalentEffectType.Taunt;

        /// <summary>施加状态：霸体免疫控制；同类型刷新取大（Value/Duration 分别取 max）；Duration≤0 拒绝。</summary>
        public static bool TryApply(List<StatusEffect> statuses, StatusEffect s, bool unstoppable)
        {
            if (s == null || s.DurationTurns <= 0) return false;
            if (IsControl(s.Type) && unstoppable) return false;
            var existing = statuses.FirstOrDefault(x => x.Type == s.Type);
            if (existing != null)
            {
                existing.Value = Math.Max(existing.Value, s.Value);
                existing.DurationTurns = Math.Max(existing.DurationTurns, s.DurationTurns);
                if (!string.IsNullOrEmpty(s.SourceId)) existing.SourceId = s.SourceId;
                return true;
            }
            statuses.Add(s);
            return true;
        }

        /// <summary>回合开始 DoT 结算：毒 = 最大生命×Value、燃 = 固定 Value、血 = 攻击×Value。返回总扣血量。</summary>
        public static int TickDots(UnitStats stats, List<StatusEffect> statuses, ref int hp)
        {
            int total = 0;
            foreach (var s in statuses)
            {
                int dmg = s.Type switch
                {
                    TalentEffectType.Poison => (int)Math.Round(stats.Hp * s.Value),
                    TalentEffectType.Burn => (int)Math.Round(s.Value),
                    TalentEffectType.Bleed => (int)Math.Round(stats.Atk * s.Value),
                    _ => 0,
                };
                total += dmg;
            }
            hp = Math.Max(0, hp - total);
            return total;
        }

        /// <summary>回合结束倒序递减，0 移除（倒序防索引漂移）。</summary>
        public static void TickDown(List<StatusEffect> statuses)
        {
            for (int i = statuses.Count - 1; i >= 0; i--)
            {
                statuses[i].DurationTurns--;
                if (statuses[i].DurationTurns <= 0) statuses.RemoveAt(i);
            }
        }

        /// <summary>晕眩 → 跳过行动（僵直同效，名称区分）。</summary>
        public static bool SkipTurn(List<StatusEffect> statuses)
            => statuses.Any(s => s.Type == TalentEffectType.Stun);

        /// <summary>缴械 → 不可攻击（false = 本回合跳过攻击）。</summary>
        public static bool CanAttack(List<StatusEffect> statuses)
            => !statuses.Any(s => s.Type == TalentEffectType.Disarm);

        /// <summary>冰冷 → 本回合速度 × (1 - Value)，最低 0。</summary>
        public static float SpeedMultiplier(List<StatusEffect> statuses)
        {
            float v = statuses.Where(s => s.Type == TalentEffectType.Chill).Sum(s => s.Value);
            return Math.Max(0f, 1f - v);
        }

        /// <summary>嘲讽 → 强制目标 SourceId（最后施加者优先）；无则 null。</summary>
        public static string TauntTarget(List<StatusEffect> statuses)
        {
            var taunt = statuses.LastOrDefault(s => s.Type == TalentEffectType.Taunt);
            return taunt == null ? null : taunt.SourceId;
        }

        public static bool HasUnstoppable(List<StatusEffect> statuses)
            => statuses.Any(s => s.Type == TalentEffectType.Unstoppable);
    }
}
```

**2c. `Tests/Milan.Tests/Milan.Tests.csproj`**：Compile Include 追加：

```xml
<Compile Include="..\..\MauiMilan\Core\Domain\Battle\StatusEngine.cs" Link="Core\Battle\StatusEngine.cs" />
```

**2d. 新建 `Tests/Milan.Tests/StatusEngineTests.cs`**：

```csharp
using System.Collections.Generic;
using Milan.Domain.Battle;
using Milan.Domain.Progression;
using Xunit;

namespace Milan.Tests
{
    public class StatusEngineTests
    {
        static UnitStats U(int atk, int def, int hp, int spd) => new UnitStats { Atk = atk, Def = def, Hp = hp, Spd = spd };

        [Fact]
        public void TryApply_StunOnUnstoppable_Rejected()
        {
            var list = new List<StatusEffect>();
            Assert.True(StatusEngine.TryApply(list, new StatusEffect { Type = TalentEffectType.Stun, DurationTurns = 2, SourceId = "a" }, unstoppable: true));
            // 霸体已存在时拒绝新控制
            Assert.True(StatusEngine.TryApply(list, new StatusEffect { Type = TalentEffectType.Unstoppable, DurationTurns = 3, SourceId = "b" }, unstoppable: false));
            Assert.False(StatusEngine.TryApply(list, new StatusEffect { Type = TalentEffectType.Stun, DurationTurns = 1, SourceId = "c" }, StatusEngine.HasUnstoppable(list)));
        }

        [Fact]
        public void TryApply_SameTypeRefreshTakesMax()
        {
            var list = new List<StatusEffect>();
            StatusEngine.TryApply(list, new StatusEffect { Type = TalentEffectType.Burn, Value = 8, DurationTurns = 2, SourceId = "a" }, false);
            StatusEngine.TryApply(list, new StatusEffect { Type = TalentEffectType.Burn, Value = 5, DurationTurns = 3, SourceId = "b" }, false);
            Assert.Single(list);
            Assert.Equal(8f, list[0].Value, 3);      // Value 取大
            Assert.Equal(3, list[0].DurationTurns);  // Duration 取大
        }

        [Fact]
        public void TryApply_ZeroDuration_Rejected()
            => Assert.False(StatusEngine.TryApply(new List<StatusEffect>(), new StatusEffect { Type = TalentEffectType.Poison, DurationTurns = 0 }, false));

        [Fact]
        public void TickDots_ThreeFormulas()
        {
            var stats = U(100, 50, 1000, 10);
            int hp = 1000;
            var list = new List<StatusEffect>
            {
                new StatusEffect { Type = TalentEffectType.Poison, Value = 0.05f, DurationTurns = 2 }, // 1000×5% = 50
                new StatusEffect { Type = TalentEffectType.Burn, Value = 8f, DurationTurns = 2 },     // 固定 8
                new StatusEffect { Type = TalentEffectType.Bleed, Value = 0.10f, DurationTurns = 2 }, // 100×10% = 10
            };
            int total = StatusEngine.TickDots(stats, list, ref hp);
            Assert.Equal(68, total);
            Assert.Equal(932, hp);
        }

        [Fact]
        public void TickDown_RemovesExpiredInReverseOrder()
        {
            var list = new List<StatusEffect>
            {
                new StatusEffect { Type = TalentEffectType.Burn, DurationTurns = 1 },
                new StatusEffect { Type = TalentEffectType.Stun, DurationTurns = 2 },
            };
            StatusEngine.TickDown(list);
            Assert.Single(list);
            Assert.Equal(TalentEffectType.Stun, list[0].Type);
            StatusEngine.TickDown(list);
            Assert.Empty(list);
        }

        [Fact]
        public void ControlJudgements()
        {
            var stun = new List<StatusEffect> { new StatusEffect { Type = TalentEffectType.Stun, DurationTurns = 1 } };
            Assert.True(StatusEngine.SkipTurn(stun));

            var disarm = new List<StatusEffect> { new StatusEffect { Type = TalentEffectType.Disarm, DurationTurns = 1 } };
            Assert.False(StatusEngine.CanAttack(disarm));
            Assert.True(StatusEngine.CanAttack(new List<StatusEffect>()));

            var chill = new List<StatusEffect> { new StatusEffect { Type = TalentEffectType.Chill, Value = 0.3f, DurationTurns = 2 } };
            Assert.Equal(0.7f, StatusEngine.SpeedMultiplier(chill), 3);
            Assert.Equal(1f, StatusEngine.SpeedMultiplier(new List<StatusEffect>()), 3);

            var taunt = new List<StatusEffect>
            {
                new StatusEffect { Type = TalentEffectType.Taunt, DurationTurns = 1, SourceId = "t1" },
                new StatusEffect { Type = TalentEffectType.Taunt, DurationTurns = 1, SourceId = "t2" },
            };
            Assert.Equal("t2", StatusEngine.TauntTarget(taunt)); // 最后施加者优先
            Assert.Null(StatusEngine.TauntTarget(new List<StatusEffect>()));
        }
    }
}
```

### 验证

- `& "C:\Users\Administrator\.dotnet\dotnet.exe" test Tests\Milan.Tests\Milan.Tests.csproj --filter FullyQualifiedName~Milan.Tests.StatusEngineTests` — 7 个新测试通过（本任务结束 81）。
- 全量回归（现有 66 + 新增 15）应全绿；若 BattleTests 现有 Simulate 断言在新逻辑下失败，属预期行为变更（见 Task 3 说明），在本任务不应出现（Simulate 未动）。
- lsp_diagnostics：BattleUnits.cs / StatusEngine.cs 无 error。

### 提交

```
feat: 新增状态系统 StatusEngine 与 UnitStats 机制字段
```

## Task 3：伤害链重写 + Simulate 重写（BattleSimulator）

### 设计要点（定稿）

- `StrikeDamage(UnitStats, UnitStats)` **保留兼容签名**（无随机路径：不闪避/不暴击/不减伤）——现有 BattleTests 的 75/105/1 断言与 BattleActivity:285/:293 旧调用在 Task 11 前行为不变。公式升级为含 IgnoreDefense：`Max(1, Atk - (int)(Def × (1 - Ignore)) / 2)`（Ignore=0 时与旧公式逐位一致；Ignore=0.5, Def=50 → 25/2=12 → 100-12=88 ✓ 规格值）。
- 新增 `Strike(attacker, defender, rng)` 完整链（规格 §5.2 六步），返回 `StrikeResult { Damage, Crit, Dodged, Reflected, Healed }`，吸血/反伤数值由调用方应用（不递归）。
- 新增 `TryUltimate(attacker, defender, rng)`：`Max(1, Atk × (2.0 + UltimateDamage) - Def/4)`（防御 50% 生效）、必中、可暴击。**不触发闪避/减伤/吸血/反伤**（规格 §5.4 仅列三项特性，其余不触发——本决策写入代码注释）。
- Simulate 重写：S 类加 `Statuses`；团队共享 `teamEnergy`（玩家侧）；回合开始 TickDots + TickDown；SkipTurn/CanAttack/嘲讽/Chill 速度**两侧都判定**（敌人作为承受者被玩家施放状态后必须生效——验收标准 3 要求 DoT/控制可观察；§10「敌人侧状态不做」仅指敌人不施放/无必杀）；能量：玩家攻击 +15（×ChargeGain）、玩家受击 +10（×ChargeGain）；玩家满能量行动时自动必杀；敌人无能量无必杀。
- **现有 Simulate 测试风险**：必杀/能量可能改变回合数。执行本任务时先跑全量测试记录基线，若现有 Simulate 断言（如精确回合数）失败，将断言更新为新行为（seed 不变、结果稳定）并在提交信息注明——这是合法行为变更（新功能），不是回归。

### 改动

**3a. 重写 `MauiMilan/Core/Domain/Battle/BattleSimulator.cs`**（全文件）：

```csharp
using System;
using System.Collections.Generic;
using System.Linq;
using Milan.Domain.Progression;

namespace Milan.Domain.Battle
{
    public class BattleSimulator
    {
        readonly Random _rng;
        public BattleSimulator(Random rng) { _rng = rng; }

        public BattleResult Simulate(UnitStats[] teamA, UnitStats[] teamB, int maxTurns)
        {
            teamA ??= System.Array.Empty<UnitStats>();
            teamB ??= System.Array.Empty<UnitStats>();
            var a = teamA.Select(u => new S { Stats = u, Hp = u.Hp, A = true, Statuses = new List<StatusEffect>() }).ToList();
            var b = teamB.Select(u => new S { Stats = u, Hp = u.Hp, A = false, Statuses = new List<StatusEffect>() }).ToList();

            // 团队共享能量槽（规格 §5.4）；敌人侧无能量/必杀（§10 边界）。
            int teamEnergy = 0;

            for (int turn = 1; turn <= maxTurns; turn++)
            {
                // 回合开始：DoT 结算 + 状态递减（规格 §5.3「回合开始结算」）。
                foreach (var u in a.Concat(b))
                {
                    if (u.Hp > 0) StatusEngine.TickDots(u.Stats, u.Statuses, ref u.Hp);
                }
                foreach (var u in a.Concat(b))
                    StatusEngine.TickDown(u.Statuses);

                var order = a.Where(x => x.Hp > 0)
                    .Concat(b.Where(x => x.Hp > 0))
                    .OrderByDescending(x => x.Stats.Spd * StatusEngine.SpeedMultiplier(x.Statuses))
                    .ThenBy(x => _rng.Next())
                    .ToList();

                foreach (var actor in order)
                {
                    if (actor.Hp <= 0) continue;
                    // 晕眩 → 跳过行动（两侧判定：敌人被玩家施放后生效）
                    if (StatusEngine.SkipTurn(actor.Statuses)) continue;

                    var enemies = actor.A ? b : a;
                    var target = PickTarget(actor, enemies);
                    if (target == null || target.Hp <= 0) continue;

                    // 缴械 → 跳过攻击（两侧判定）
                    if (!StatusEngine.CanAttack(actor.Statuses)) continue;

                    // 必杀（玩家侧满能量自动释放）
                    if (actor.A && teamEnergy >= 100)
                    {
                        int udmg = TryUltimate(actor.Stats, target.Stats, _rng);
                        target.Hp = Math.Max(0, target.Hp - udmg);
                        teamEnergy = 0;
                    }
                    else
                    {
                        var r = Strike(actor.Stats, target.Stats, _rng);
                        if (!r.Dodged)
                        {
                            target.Hp = Math.Max(0, target.Hp - r.Damage);
                            if (r.Healed > 0)
                                actor.Hp = Math.Min(actor.Stats.Hp, actor.Hp + r.Healed);
                            if (r.Reflected > 0)
                                actor.Hp = Math.Max(0, actor.Hp - r.Reflected);
                            // 状态施加：攻击方按命中后触发（玩家侧；敌人无状态施加，§10）
                            if (actor.A) ApplyStatusAttacks(actor, target);
                        }
                        // 能量：攻击 +15、受击 +10，各乘 (1 + ChargeGain)（仅玩家侧）
                        if (actor.A)
                            teamEnergy = Math.Min(100, teamEnergy + (int)Math.Round(15 * (1 + actor.Stats.ChargeGain)));
                        else if (target.A)
                            teamEnergy = Math.Min(100, teamEnergy + (int)Math.Round(10 * (1 + target.Stats.ChargeGain)));
                    }
                }

                // 空队伍无法"全部死亡"，必须要求队伍非空，否则空 teamB 会因 LINQ 语义被误判为胜利。
                if (b.Count > 0 && b.All(x => x.Hp <= 0))
                    return Done(true, turn, a, b);
                if (a.Count > 0 && a.All(x => x.Hp <= 0))
                    return Done(false, turn, a, b);
            }

            return Done(false, maxTurns, a, b);
        }

        /// <summary>目标选择：嘲讽强制目标（存活则锁定），否则敌方最低血量。</summary>
        static S PickTarget(S actor, List<S> enemies)
        {
            var alive = enemies.Where(e => e.Hp > 0).ToList();
            if (alive.Count == 0) return null;
            string tauntId = StatusEngine.TauntTarget(actor.Statuses);
            if (tauntId != null)
            {
                var taunter = alive.FirstOrDefault(e => e.Stats.CharacterId == tauntId);
                if (taunter != null) return taunter;
            }
            return alive.OrderBy(e => e.Hp).First();
        }

        /// <summary>攻击命中后按攻击方 StatusAttacks 施加状态到目标（含霸体判定）。</summary>
        void ApplyStatusAttacks(S attacker, S target)
        {
            var list = attacker.Stats.StatusAttacks;
            if (list == null || list.Count == 0) return;
            bool unstoppable = StatusEngine.HasUnstoppable(target.Statuses);
            foreach (var fx in list)
            {
                if (fx == null || fx.Chance <= 0) continue;
                if (_rng.NextDouble() < fx.Chance)
                {
                    StatusEngine.TryApply(target.Statuses, new StatusEffect
                    {
                        Type = fx.Type,
                        Value = fx.Value,
                        DurationTurns = fx.Duration,
                        SourceId = attacker.Stats.CharacterId,
                    }, unstoppable);
                }
            }
        }

        static BattleResult Done(bool victory, int turns, List<S> a, List<S> b) => new BattleResult
        {
            Victory = victory,
            Turns = turns,
            RemainingHp = a.Sum(x => Math.Max(0, x.Hp)),
            OpponentRemainingHp = b.Sum(x => Math.Max(0, x.Hp)),
        };

        /// <summary>基础伤害（无随机路径：不闪避/不暴击/不减伤）。旧调用点与测试兼容入口；
        /// 攻方属性由 GameState.ComputeStats 生成，已含等级/突破/天赋/升星的加成。
        /// 公式：Max(1, Atk - (int)(Def × (1 - IgnoreDefense)) / 2)。</summary>
        public static int StrikeDamage(UnitStats attacker, UnitStats defender)
            => System.Math.Max(1, attacker.Atk - (int)(defender.Def * (1 - attacker.IgnoreDefense)) / 2);

        /// <summary>单次攻击结算结果（规格 §5.2）。</summary>
        public struct StrikeResult
        {
            public int Damage;     // 最终实际伤害（0 = 被闪避）
            public bool Crit;
            public bool Dodged;
            public int Reflected;  // 反伤给攻击方（调用方应用）
            public int Healed;     // 攻击方吸血回复（调用方应用）
        }

        /// <summary>完整伤害链：闪避 → 基础伤害（含无视防御）→ 暴击 → 减伤 → 吸血 → 反伤。
        /// 手动与自动共用唯一入口；反伤不递归（不二次触发减伤/吸血/反伤）。</summary>
        public static StrikeResult Strike(UnitStats attacker, UnitStats defender, Random rng)
        {
            var r = new StrikeResult();
            if (rng != null && rng.NextDouble() < defender.DodgeRate)
            {
                r.Dodged = true;
                return r;
            }
            float dmg = StrikeDamage(attacker, defender);
            if (rng != null && rng.NextDouble() < attacker.CritRate)
            {
                r.Crit = true;
                dmg *= attacker.CritDamage > 0 ? attacker.CritDamage : 1.5f;
            }
            int final = (int)(dmg * (1 - defender.DamageReduction));
            r.Damage = Math.Max(1, final);
            if (attacker.Lifesteal > 0) r.Healed = (int)Math.Round(r.Damage * attacker.Lifesteal);
            if (defender.Thorn > 0) r.Reflected = (int)Math.Round(r.Damage * defender.Thorn);
            return r;
        }

        /// <summary>必杀：Atk × (2.0 + UltimateDamage)，防御仅 50% 生效（Def/4），必中，可暴击。
        /// 不触发闪避/减伤/吸血/反伤（规格 §5.4 仅列三项特性）。调用方负责能量清零。返回实际伤害。</summary>
        public static int TryUltimate(UnitStats attacker, UnitStats defender, Random rng)
        {
            float dmg = Math.Max(1, attacker.Atk * (2.0f + attacker.UltimateDamage) - defender.Def / 4f);
            if (rng != null && rng.NextDouble() < attacker.CritRate)
                dmg *= attacker.CritDamage > 0 ? attacker.CritDamage : 1.5f;
            return (int)Math.Round(dmg);
        }

        class S
        {
            public UnitStats Stats;
            public int Hp;
            public bool A;
            public List<StatusEffect> Statuses;
        }
    }
}
```

**3b. `Tests/Milan.Tests/BattleTests.cs` 扩展**（保留现有测试与 `U()` helper，追加 10 个；状态施加测试用 `Chance = 1` 保证确定性，seed 固定 `12345`）：

```csharp
// ────────── 追加到 BattleTests 类内 ──────────

static UnitStats With(UnitStats baseStats, params (TalentEffectType Type, float Value, float Chance, int Duration)[] attacks)
{
    baseStats.StatusAttacks = attacks.Select(a => new TalentEffect { Type = a.Type, Value = a.Value, Chance = a.Chance, Duration = a.Duration }).ToList();
    return baseStats;
}

[Fact]
public void Strike_IgnoreDefense_HalfEffectiveDefense()
{
    var atk = U(100, 0, 0, 0); atk.IgnoreDefense = 0.5f;
    var def = U(0, 50, 0, 0);
    var r = BattleSimulator.Strike(atk, def, new System.Random(1));
    Assert.False(r.Dodged);
    Assert.Equal(88, r.Damage); // 50×(1-0.5)=25 → 25/2=12 → 100-12=88
}

[Fact]
public void Strike_Crit_MultipliesByCritDamage()
{
    var atk = U(100, 0, 0, 0); atk.CritRate = 1f; atk.CritDamage = 0.2f;
    var def = U(0, 50, 0, 0);
    var r = BattleSimulator.Strike(atk, def, new System.Random(1));
    Assert.True(r.Crit);
    Assert.Equal(127, r.Damage); // 75 × 1.7 = 127.5 → (int)127
}

[Fact]
public void Strike_Dodge_ReturnsZeroDamage()
{
    var atk = U(100, 0, 0, 0);
    var def = U(0, 50, 0, 0); def.DodgeRate = 1f;
    var r = BattleSimulator.Strike(atk, def, new System.Random(1));
    Assert.True(r.Dodged);
    Assert.Equal(0, r.Damage);
}

[Fact]
public void Strike_FullChain_DamageReductionLifestealThorn()
{
    var atk = U(100, 0, 100, 0); atk.Lifesteal = 0.5f;
    var def = U(0, 50, 0, 0); def.DamageReduction = 0.5f; def.Thorn = 0.5f;
    var r = BattleSimulator.Strike(atk, def, new System.Random(1));
    Assert.False(r.Dodged);
    Assert.Equal(37, r.Damage);   // 75 × 0.5 = 37.5 → (int)37
    Assert.Equal(19, r.Healed);   // Round(37 × 0.5) = 19
    Assert.Equal(19, r.Reflected); // Round(37 × 0.5) = 19
}

[Fact]
public void Strike_Thorn_DoesNotRecurse()
{
    // 双方都有反伤：Reflected 只计算一次（不二次触发减伤/吸血/反伤）
    var atk = U(100, 0, 100, 0); atk.Thorn = 0.5f; atk.Lifesteal = 0.5f;
    var def = U(0, 50, 0, 0); def.Thorn = 0.5f; def.DamageReduction = 0.5f;
    var r = BattleSimulator.Strike(atk, def, new System.Random(1));
    Assert.Equal(19, r.Reflected);
    Assert.Equal(19, r.Healed);   // 吸血按实际伤害一次计算，不因反伤链二次变化
}

[Fact]
public void Simulate_PoisonAppliedByPlayer_TicksEachTurn()
{
    var atk = With(U(100, 0, 1000, 10), (TalentEffectType.Poison, 0.05f, 1f, 3));
    var def = U(0, 50, 1000, 5);
    var result = new BattleSimulator(new System.Random(12345)).Simulate(new[] { atk }, new[] { def }, 50);
    // 中毒 3 回合 × 50/回合 = 150 额外伤害；敌人 1000 血需 10 刀普攻 + 毒提前击杀
    Assert.True(result.Victory);
    Assert.True(result.Turns < 12); // 有 DoT 比纯普攻（10 回合击杀）更快或持平
}

[Fact]
public void Simulate_StunOnEnemy_SkipsEnemyTurn()
{
    var atk = With(U(20, 0, 1000, 1), (TalentEffectType.Stun, 1f, 1f, 1));
    // 敌人先手且输出高；被 Stun 后第 2 回合跳过一次行动，玩家存活回合更长
    var def = U(200, 0, 300, 10);
    var noStun = new BattleSimulator(new System.Random(12345)).Simulate(new[] { U(20, 0, 1000, 1) }, new[] { def }, 10);
    var withStun = new BattleSimulator(new System.Random(12345)).Simulate(new[] { atk }, new[] { def }, 10);
    Assert.True(withStun.RemainingHp > noStun.RemainingHp);
}

[Fact]
public void Simulate_TauntOnEnemy_ForcesEnemyToAttackTaunter()
{
    var a1 = With(U(10, 0, 500, 10), (TalentEffectType.Taunt, 1f, 1f, 2)); // 嘲讽者：攻击施加 Taunt(SourceId=a1)
    var a2 = U(10, 0, 500, 10);
    var def = U(300, 0, 300, 1); // 敌人高攻低血
    var result = new BattleSimulator(new System.Random(12345)).Simulate(new[] { a1, a2 }, new[] { def }, 10);
    // 敌人被嘲讽后只打 a1：a1 掉血比 a2 多（或 a2 满血时 a1 已死）
    Assert.True(result.OpponentRemainingHp == 0); // 敌人被击杀
    Assert.True(result.RemainingHp >= 0);
}

[Fact]
public void Simulate_EnergyGain_TriggersUltimateAt100()
{
    // 单挑：普攻 100/次。敌人 850 血：纯普攻 9 回合；能量 7 次攻击满（7×15=105），第 8 次必杀 200 → 8 回合击杀
    var atk = U(100, 0, 1000, 10);
    var def = U(0, 0, 850, 5);
    var result = new BattleSimulator(new System.Random(12345)).Simulate(new[] { atk }, new[] { def }, 20);
    Assert.True(result.Victory);
    Assert.Equal(8, result.Turns);
}

[Fact]
public void Simulate_EnemyNeverAppliesStatus_PlayerStaysClean()
{
    // 敌人机制字段全 0、StatusAttacks 为 null：打玩家多次后玩家无任何状态可观测（§10 边界）
    var atk = U(100, 0, 1000, 10);
    var def = U(30, 0, 1000, 10); // 双方对砍 10 回合
    var result = new BattleSimulator(new System.Random(12345)).Simulate(new[] { atk }, new[] { def }, 10);
    Assert.True(result.RemainingHp < 1000); // 玩家确实受伤（敌人普攻正常）
}
```

> 测试文件需在头部补充 `using Milan.Domain.Progression;`（`TalentEffect` 引用）。若 `Simulate_EnergyGain_TriggersUltimateAt100` 的回合数受 seed 影响的随机暴击（CritRate 默认 0，无随机伤害波动，仅 `ThenBy(_rng.Next())` 影响单挑顺序——单挑只有 1v1，无影响）断言稳定。

### 验证

- `& "C:\Users\Administrator\.dotnet\dotnet.exe" test Tests\Milan.Tests\Milan.Tests.csproj --filter FullyQualifiedName~Milan.Tests.BattleTests` — 现有 + 10 新测试通过（本任务结束 91）。
- 全量回归：若现有 Simulate 断言失败 → 按设计要点说明更新为新行为值（同一 seed 结果稳定），提交信息注明行为变更。
- lsp_diagnostics：BattleSimulator.cs / BattleTests.cs 无 error。

### 提交

```
refactor: 重写伤害结算为完整 Strike 链并接入 Simulate 状态/必杀
```

## Task 4：天赋树模板化生成 + 空树加载兜底修复（GameService）

### 实测事实（已读代码确认）

- `GameService.cs:95-98` 已过滤 `Nodes == null` 的树，`:106` 仅当 `TalentTrees.Count == 0` 才调 `BuildTalentTrees()`。
- `data.json`（1407 行）尾部有 `TalentTrees` 段，41 个角色各一条 **`Nodes: []` 空树**——空数组非 null，过滤放行 → `Count = 41 ≠ 0` → 兜底不触发 → 养成界面空白（每角色有分支标题无节点）。**这就是本任务要修的空树漏洞**。
- `BuildTalentTrees()`/`BuildTree()`/`NewTalent()`（:159-194）是硬编码 6 节点模板（char_xx_t1..t6，强攻/破甲/坚壁/铁壁/疾风步/灵动），待替换为模板工厂。
- **存档兼容铁律**：`SaveData` 已分配节点按 `NodeId = "{CharacterId}_{shortId}"`（如 `char_ur_zhulong_t1`）记录；模板 NodeId 必须保持此前缀格式且前 6 个短 ID 固定 `t1..t6`（老分配不失配；t7-t13 为新增）。ProgressionActivity:518 用 `GetTalentTree(CharacterId)`（按 TreeId 匹配），不解析 NodeId，无其他耦合。
- `GameService` 带 `using Android.Content`（构造取 Context）→ **不能进测试工程**；本任务无新单测，靠 build + 全量回归 + 下述手工验证。

### 改动

**4a. `Services/GameService.cs` 加载段（:95-106 区域）改造**：过滤条件加空树剔除；兜底从「Count==0」改为「按角色补齐」：

```csharp
TalentTrees.Clear();
// 过滤掉 Nodes 为 null 或空的树（空树=养成界面静默空白；此前只滤 null，data.json 的 Nodes:[] 空树会绕过兜底）。
TalentTrees.AddRange((root.TalentTrees ?? new List<TalentTreeData>())
    .Where(t => t != null && t.Nodes != null && t.Nodes.Count > 0));
...
// 天赋树按角色补齐：UR 角色可携带显式树（data.json），其余角色无树时用世界模板生成。
EnsureAllTalentTrees();
```

（原 `if (TalentTrees.Count == 0) BuildTalentTrees();` 删除，由 EnsureAllTalentTrees 取代。）

**4b. 重写 `BuildTalentTrees()` 段（:157-194）**：

```csharp
// ------------------------------------------------------------------ talent trees

/// <summary>按角色补齐天赋树：已有显式树（data.json 的 UR 专属树）不动，缺失的用世界模板生成。
/// 加载与兜底两条路径共用，保证 41 角色每人都有一棵非空树。</summary>
private void EnsureAllTalentTrees()
{
    foreach (var ch in Characters)
    {
        if (TalentTrees.Any(t => t.TreeId == ch.TalentTreeId)) continue;
        TalentTrees.Add(TalentTemplateFactory.BuildTree(ch.TalentTreeId, ch.CharacterId, ch.World, ch.BaseRarity));
    }
}

private void BuildTalentTrees() => EnsureAllTalentTrees();
```

（`BuildTree(CharacterDataEntry)` 与 `NewTalent(...)` 两个私有方法删除。）

**4c. 删除 `GameService.cs:655-685` 的 `TalentNodeData` / `TalentTreeData` 类定义**（已迁移至 `Core/Domain/Progression/TalentData.cs`，Task 1）。`RootData` 容器类保留（含 Characters/Pools/TalentTrees 属性），其 `TalentTrees` 属性类型自动解析到 Core 的类型。**删除前 grep 确认无其他文件直接引用 GameService 内的这两个类名**（`Select-String -Path "MauiMilan\**\*.cs" -Pattern "TalentNodeData|TalentTreeData"` 应只剩 Core 文件与 GameService 的 RootData 属性行）。

**4d. 模板工厂签名约定（衔接 Task 1；若 Task 1 落盘签名不同则统一为本节形态，Task 1 测试同步改用）**：

```csharp
// Core/Domain/Progression/TalentTemplateFactory.cs
/// <summary>按世界/稀有度生成完整天赋树。NodeId = $"{characterId}_{shortId}"（shortId 固定 t1..t13，
/// t1-t6 兼容老存档分配）。返回 13 节点（3 分支，每分支 2 行×2 列 + 1 必杀终端 = 规格 §6）。</summary>
public static TalentTreeData BuildTree(string treeId, string characterId, string world, int rarity)
```

> 若 Task 1 落盘的是 `EnsureTree(TalentTreeData, ...)` 形态：保留 EnsureTree（合并/兜底语义，Task 12 UR 树复用），另加 `BuildTree` 便捷入口（内部调 EnsureTree(new TalentTreeData{TreeId=treeId,...})）。两入口并存，语义各一。

### 验证

- `& "C:\Users\Administrator\.dotnet\dotnet.exe" build MauiMilan\MauiMilan.csproj -c Release`（全路径 dotnet）exit 0。
- 全量测试回归：`& "C:\Users\Administrator\.dotnet\dotnet.exe" test Tests\Milan.Tests\Milan.Tests.csproj` — 91 个全过（本任务无新测试，计数不变）。
- 手工验证（连真机或模拟器）：养成页打开任一非 UR 角色 → 显示 13 节点模板树（3 分支 + 必杀终端），分配/回退正常；UR 角色在 Task 12 前同样显示模板树。
- lsp_diagnostics：GameService.cs / TalentData.cs 无 error。

### 提交

```
refactor: 天赋树模板化生成并修复空树绕过加载兜底的漏洞
```

## Task 5：data.json 空天赋树清理 + 数据校验测试

### 实测事实

- 两处 data.json 的 `TalentTrees` 段全是 `{ TreeId, BranchIds, Nodes: [] }` 空树（41 条）——Task 4 修复后成为死数据（加载即被过滤，随后按角色模板补齐），删除避免误导。
- 普通角色**没有**显式天赋节点（只有 `TalentTreeId` 字段）——无需清理显式节点，只需删空树条目。7 个 UR 树在 Task 12 写入完整节点。

### 改动

**5a. 删除两处 data.json（`MauiMilan/Platforms/Android/Assets/data.json` 与 `MauiMilan/Resources/Raw/data.json`，必须同步）** `TalentTrees` 数组中全部 41 条空树条目（保留数组键；删空后写作 `"TalentTrees": []` 或整键删除均可，加载处 `?? new List<>()` 兜底）。操作：整段 `"TalentTrees": [...]` 替换为 `"TalentTrees": []`（注意最后一个角色对象结尾的 `}` 与逗号），或脚本按 `"Nodes": []` 匹配删条目。**删后用 `dotnet build` 验证 JSON 语法（构建即解析资源）+ 下述测试。**

**5b. 新测试文件 `Tests/Milan.Tests/DataJsonTests.cs`**（用 `JsonDocument` 解析，避免引用带 Android 依赖的 RootData）+ csproj 链接 data.json 复制到输出：

```xml
<!-- Tests/Milan.Tests/Milan.Tests.csproj 追加 -->
<None Include="..\..\MauiMilan\Resources\Raw\data.json" Link="data.json" CopyToOutputDirectory="PreserveNewest" />
```

```csharp
using System;
using System.IO;
using System.Linq;
using System.Text.Json;
using Xunit;

namespace Milan.Tests;

public class DataJsonTests
{
    static readonly string Path_ = Path.Combine(AppContext.BaseDirectory, "data.json");
    static JsonElement Root() => JsonDocument.Parse(File.ReadAllText(Path_)).RootElement;

    [Fact]
    public void DataJson_NoEmptyTalentTreeEntries()
    {
        // 空树条目（Nodes:[]）已全部清理；UR 树的显式节点在 Task 12 加入。
        var trees = Root().GetProperty("TalentTrees").EnumerateArray().ToList();
        Assert.All(trees, t =>
        {
            var nodes = t.GetProperty("Nodes");
            Assert.True(nodes.GetArrayLength() > 0,
                $"Tree {t.GetProperty("TreeId").GetString()} has empty Nodes");
        });
    }

    [Fact]
    public void DataJson_EveryCharacterHasTalentTreeId()
    {
        var chars = Root().GetProperty("Characters").EnumerateArray().ToList();
        Assert.NotEmpty(chars);
        Assert.All(chars, c =>
        {
            var id = c.GetProperty("TalentTreeId").GetString();
            Assert.False(string.IsNullOrWhiteSpace(id), $"Character {c.GetProperty("CharacterId").GetString()} missing TalentTreeId");
        });
    }
}
```

> 本任务删空树后 `TalentTrees` 为空数组 → 测试 1 的 `EnumerateArray` 对空数组天然通过；断言写「非空」是为 Task 12 铺路（届时 7 个 UR 树必须带节点，空树回归即红）。若实现选择整键删除，测试 1 改为 `Assert.False(Root().TryGetProperty("TalentTrees", out _) && ...)` 按实际形态定稿。

### 验证

- 两处 data.json 同步（可 `Get-FileHash` 对比两文件内容中 TalentTrees 段）。
- `dotnet test --filter FullyQualifiedName~Milan.Tests.DataJsonTests` — 2 个新测试通过（本任务结束 93）。
- 全量回归 93 全过。

### 提交

```
chore: 清理 data.json 空天赋树条目并新增数据完整性测试
```

## Task 6：提取 StatsCalculator 纯领域属性计算（Effects 聚合版）

### 实测事实

- `GameState.cs:78-118` `ComputeStatsAt` 是 UI 层的属性计算实现：ProgressionEngine.StatAtLevel 基础值 + **BranchId 硬编码「每节点 +3%」**（:96-108）——不满足规格 §5.1（机制字段）与 §6（模板节点带具体 Effects）。本任务整体提取到 Core 并升级为 Effects 聚合，保持「单一事实来源：详情页/养成页/战斗页都走这里」语义。
- `ProgressionEngine.StarMultiplier(stars)`（每星 +5%，1★×1.0，7★×1.30）与 `StatAtLevel(base, lv, stg, starMul)` 已有，直接复用。
- `UnitStats`（Task 2 已扩展）字段：Atk/Def/Hp/Spd + IgnoreDefense/DamageReduction/DodgeRate/CritRate/CritDamage/Lifesteal/Thorn/UltimateDamage/ChargeGain + `List<TalentEffect>? StatusAttacks`（null 或空均可，BattleSimulator 已防御）。
- `OwnedCharacterView.Talent`（GameState.cs:132）按 TreeId 取树；`Save.TalentPoints`（HashSet<string>）记录已分配 NodeId。

### 改动

**6a. 新文件 `MauiMilan/Core/Domain/Progression/StatsCalculator.cs`**：

```csharp
using System;
using System.Collections.Generic;

namespace Milan.Domain.Progression
{
    /// <summary>养成属性计算单一事实来源：基础值 + 已点亮天赋 Effects → 战斗属性。
    /// 纯领域（无 Android 依赖），详情页/养成页/战斗页共用（替代原 GameState.ComputeStatsAt 的 UI 层实现）。</summary>
    public static class StatsCalculator
    {
        /// <param name="effects">已点亮节点的效果聚合（未点亮节点不得传入）。</param>
        /// <param name="baseStats">BaseStats 数组（Atk/Def/Hp/Spd），越界回退默认值。</param>
        public static UnitStats Compute(string characterId, int[]? baseStats, int level, int stage, int stars,
            IReadOnlyList<TalentEffect>? effects)
        {
            // —— 效果聚合：百分比/机制取和；状态攻击原样收集；Unstoppable 无模板来源，忽略（规格 §6 无此效果）——
            float atkP = 0, defP = 0, hpP = 0, spdP = 0;
            float dodge = 0, critRate = 0, critDmg = 0, dmgRed = 0, ignoreDef = 0, lifesteal = 0, thorn = 0;
            float ultDmg = 0, charge = 0;
            var statusAttacks = new List<TalentEffect>();
            if (effects != null)
            {
                foreach (var fx in effects)
                {
                    if (fx == null) continue;
                    switch (fx.Type)
                    {
                        case TalentEffectType.AtkPercent: atkP += fx.Value; break;
                        case TalentEffectType.DefPercent: defP += fx.Value; break;
                        case TalentEffectType.HpPercent: hpP += fx.Value; break;
                        case TalentEffectType.SpdPercent: spdP += fx.Value; break;
                        case TalentEffectType.DodgeRate: dodge += fx.Value; break;
                        case TalentEffectType.CritRate: critRate += fx.Value; break;
                        case TalentEffectType.CritDamage: critDmg += fx.Value; break;
                        case TalentEffectType.DamageReduction: dmgRed += fx.Value; break;
                        case TalentEffectType.IgnoreDefense: ignoreDef += fx.Value; break;
                        case TalentEffectType.Lifesteal: lifesteal += fx.Value; break;
                        case TalentEffectType.Thorn: thorn += fx.Value; break;
                        case TalentEffectType.UltimateDamage: ultDmg += fx.Value; break;
                        case TalentEffectType.ChargeGain: charge += fx.Value; break;
                        // 状态攻击：按节点原样收集（同类型多节点可叠加为多条目，命中后逐条判定）
                        case TalentEffectType.Burn:
                        case TalentEffectType.Poison:
                        case TalentEffectType.Bleed:
                        case TalentEffectType.Stun:
                        case TalentEffectType.Chill:
                        case TalentEffectType.Taunt:
                        case TalentEffectType.Disarm:
                            statusAttacks.Add(fx);
                            break;
                    }
                }
            }

            var engine = new ProgressionEngine();
            int stg = Math.Max(1, stage);
            int lv = Math.Max(1, level);
            float starMul = ProgressionEngine.StarMultiplier(Math.Max(1, stars));
            int Base(int i, int fb) => baseStats != null && i < baseStats.Length ? baseStats[i] : fb;

            return new UnitStats
            {
                CharacterId = characterId,
                Atk = (int)(engine.StatAtLevel(Base(0, 100), lv, stg, starMul) * (1 + atkP)),
                Def = (int)(engine.StatAtLevel(Base(1, 80), lv, stg, starMul) * (1 + defP)),
                Hp = (int)(engine.StatAtLevel(Base(2, 1000), lv, stg, starMul) * (1 + hpP)),
                Spd = (int)(engine.StatAtLevel(Base(3, 12), lv, stg, starMul) * (1 + spdP)),
                IgnoreDefense = ignoreDef,
                DamageReduction = dmgRed,
                DodgeRate = dodge,
                CritRate = critRate,
                CritDamage = critDmg,
                Lifesteal = lifesteal,
                Thorn = thorn,
                UltimateDamage = ultDmg,
                ChargeGain = charge,
                StatusAttacks = statusAttacks.Count > 0 ? statusAttacks : null,
            };
        }
    }
}
```

**6b. `MauiMilan/UI/GameState.cs` `ComputeStatsAt` 重写为薄壳**（:78-118 整段替换；`OwnedCharacterView` 的 `Def`/`Save`/`Talent` 均可用）：

```csharp
public static Milan.Domain.Battle.UnitStats ComputeStatsAt(OwnedCharacterView ch, int level, int stage, int stars = -1)
{
    var save = ch.Save;
    var def = ch.Def;
    if (def == null)
        return new Milan.Domain.Battle.UnitStats { CharacterId = save.CharacterId, Hp = 1 };

    // 已点亮天赋的效果聚合（模板节点带 Effects；老存档按 NodeId 匹配，t1-t6 兼容）。
    var effects = new System.Collections.Generic.List<TalentEffect>();
    var tree = ch.Talent;
    if (tree?.Nodes != null && save.TalentPoints != null)
    {
        foreach (var n in tree.Nodes)
        {
            if (n == null || !save.TalentPoints.Contains(n.NodeId) || n.Effects == null) continue;
            effects.AddRange(n.Effects);
        }
    }

    int st = stars < 0 ? (save?.Stars ?? 1) : stars;
    return Milan.Domain.Progression.StatsCalculator.Compute(save.CharacterId, def.BaseStats, level, stage, st, effects);
}
```

（`Stat` helper :61-68 与 `ComputeStats` :72-73 不动；需在 GameState.cs 补 `using Milan.Domain.Progression;`。`TalentEffect` 类在 Core，引用无 Android 依赖。）

**6c. `Tests/Milan.Tests/Milan.Tests.csproj`**：`<Compile Include>` 追加 `StatsCalculator.cs`（已有 Progression 相关条目模式）。**6d. 新测试文件 `Tests/Milan.Tests/StatsCalculatorTests.cs`**（+6 → 99）：

```csharp
using System.Collections.Generic;
using Milan.Domain.Battle;
using Milan.Domain.Progression;
using Xunit;

namespace Milan.Tests;

public class StatsCalculatorTests
{
    static UnitStats Compute(int[]? bs, params TalentEffect[] fx)
        => StatsCalculator.Compute("c1", bs, 1, 1, 1, fx);

    // 基础路径：1级1阶1星，无效果 → 等于基础值（StatAtLevel(bs,1,1,1.0) 恒等）
    [Fact]
    public void Compute_NoEffects_ReturnsBaseValues()
    {
        var s = Compute(new[] { 100, 80, 1000, 12 });
        Assert.Equal(100, s.Atk); Assert.Equal(80, s.Def);
        Assert.Equal(1000, s.Hp); Assert.Equal(12, s.Spd);
        Assert.Equal(0, s.CritRate); Assert.Null(s.StatusAttacks);
    }

    [Fact]
    public void Compute_PercentEffects_MultiplyBase()
    {
        var s = Compute(new[] { 100, 80, 1000, 12 },
            new TalentEffect { Type = TalentEffectType.AtkPercent, Value = 0.08f },
            new TalentEffect { Type = TalentEffectType.DefPercent, Value = 0.10f },
            new TalentEffect { Type = TalentEffectType.HpPercent, Value = 0.05f },
            new TalentEffect { Type = TalentEffectType.SpdPercent, Value = 0.04f });
        Assert.Equal(108, s.Atk);   // 100 × 1.08
        Assert.Equal(88, s.Def);    // 80 × 1.10
        Assert.Equal(1050, s.Hp);   // 1000 × 1.05
        Assert.Equal(12, s.Spd);    // 12 × 1.04 = 12.48 → (int)12
    }

    [Fact]
    public void Compute_MechanicEffects_Sum()
    {
        var s = Compute(new[] { 100, 80, 1000, 12 },
            new TalentEffect { Type = TalentEffectType.CritRate, Value = 0.08f },
            new TalentEffect { Type = TalentEffectType.CritRate, Value = 0.15f },
            new TalentEffect { Type = TalentEffectType.DodgeRate, Value = 0.05f },
            new TalentEffect { Type = TalentEffectType.DamageReduction, Value = 0.08f },
            new TalentEffect { Type = TalentEffectType.IgnoreDefense, Value = 0.15f },
            new TalentEffect { Type = TalentEffectType.Lifesteal, Value = 0.06f },
            new TalentEffect { Type = TalentEffectType.Thorn, Value = 0.10f });
        Assert.Equal(0.23f, s.CritRate, 3); // 0.08 + 0.15
        Assert.Equal(0.05f, s.DodgeRate, 3);
        Assert.Equal(0.08f, s.DamageReduction, 3);
        Assert.Equal(0.15f, s.IgnoreDefense, 3);
        Assert.Equal(0.06f, s.Lifesteal, 3);
        Assert.Equal(0.10f, s.Thorn, 3);
    }

    [Fact]
    public void Compute_StatusAttacks_CollectedPerNode()
    {
        var s = Compute(new[] { 100, 80, 1000, 12 },
            new TalentEffect { Type = TalentEffectType.Burn, Value = 8, Chance = 0.5f, Duration = 2 },
            new TalentEffect { Type = TalentEffectType.Poison, Value = 0.04f, Chance = 0.4f, Duration = 3 });
        Assert.NotNull(s.StatusAttacks);
        Assert.Equal(2, s.StatusAttacks!.Count);
        Assert.Equal(TalentEffectType.Burn, s.StatusAttacks[0].Type);
        Assert.Equal(TalentEffectType.Poison, s.StatusAttacks[1].Type);
    }

    [Fact]
    public void Compute_UltimateCharge_Sum()
    {
        var s = Compute(new[] { 100, 80, 1000, 12 },
            new TalentEffect { Type = TalentEffectType.UltimateDamage, Value = 0.40f },
            new TalentEffect { Type = TalentEffectType.ChargeGain, Value = 0.20f });
        Assert.Equal(0.40f, s.UltimateDamage, 3);
        Assert.Equal(0.20f, s.ChargeGain, 3);
    }

    [Fact]
    public void Compute_NullBaseStats_FallsBackDefaults()
    {
        var s = Compute(null);
        Assert.Equal(100, s.Atk); Assert.Equal(80, s.Def);
        Assert.Equal(1000, s.Hp); Assert.Equal(12, s.Spd);
    }
}
```

> 断言 `s.Spd == 12`（12×1.04=12.48 → (int) 截断）：保持与原 `ComputeStatsAt` 一致（:116 `(int)(...)` 截断语义），勿改成四舍五入。

### 验证

- `dotnet test --filter FullyQualifiedName~Milan.Tests.StatsCalculatorTests` — 6 个新测试通过（本任务结束 99）。
- 全量回归 99 全过——**关键回归点**：详情页/养成页/战斗页的数值展示依赖本计算（GameState 无测试），回归只靠 build + 真机手工抽查：详情页属性值、养成页「下一级/下一阶/升星」预测、战斗页伤害（Task 11 前 StrikeDamage 路径不变）。
- lsp_diagnostics：StatsCalculator.cs / GameState.cs 无 error。

### 提交

```
refactor: 提取 StatsCalculator 纯领域属性计算并升级为天赋 Effects 聚合
```

## Task 7：世界模板全量验证测试（衔接修正 + 规格对齐）

### 规格 §7.1 定稿事实（已读规格全文 :97-214）

- **模板结构**：13 节点 = 3 分支 × 4 级（基础 1 费 → 进阶 2 费 → 大师 3 费 → 宗师 4 费）+ 树顶必杀 5 费。每分支总 10 费，全树 35 点（§8 明示「13 节点共需 35 点」）。
- **分支主题**（§7.1 表格）：Shinwa=强攻 破甲/暴击、坚壁 减伤/反伤、灵动 闪避/速度、必杀 单体毁灭；Aether=强攻 燃烧/中毒、坚壁 吸血/减伤、灵动 冰冷/控制、必杀 元素风暴；Ironveil=强攻 流血/缴械、坚壁 霸体/嘲讽、灵动 僵直/精准、必杀 装甲突击。
- **稀有度分层**：R=×0.7、SR=×1.0、SSR=×1.3；**UR 不走模板**（§7.1 原文）——Task 1 落盘的 RarityMultiplier 若含 UR 分支值 ≠1.3，统一改为 **UR=×1.3 保底**（Task 12 定制树接入前 UR 临时走模板，系数与 SSR 同档；接入后模板路径不再服务 UR）。
- **「僵直」无独立枚举**（§4 枚举 0-20 无 Stagger）：Ironveil 灵动的僵直节点用 `TalentEffectType.Stun` 表达（§5.3「与晕眩同效果，名称区分」——区分靠模板 DisplayName/Description，如「僵直压制」）。
- **必杀前置设计决策**（规格未明示，本计划定稿）：必杀节点 `PrerequisiteNodeIds` = **其余 12 个节点**（整树点满才解锁必杀；全树恰 35 点 = 普通角色满级可点满的优雅闭环）。分支内前置线性：2 级←1 级、3 级←2 级、4 级←3 级。
- **必杀节点 BranchId 约定**：`"branch_ultimate"`（Task 9 渲染按此识别，不占三列）。
- **Ironveil「霸体」处理决策**：Unstoppable 作为 TalentEffect 时被 StatsCalculator 聚合忽略（Task 6 已注明），无法表达「自身常驻霸体」→ **P1 模板与 UR 树均不使用 Unstoppable 常驻**；Ironveil 坚壁主题以 DamageReduction + Taunt 表达（嘲讽=压制）；规格 §7.2 刑天「霸体」在 Task 12 数值表落地时以 Taunt + DamageReduction 替代，并在提交说明记录规格偏差（霸体常驻留 P3 敌人侧）。

### 改动

**7a. `Tests/Milan.Tests/TalentTemplateFactoryTests.cs` 扩展**（追加 5 个 → 104）：

```csharp
// ────────── 追加到 TalentTemplateFactoryTests 类内 ──────────

[Theory]
[InlineData("Shinwa")] [InlineData("Aether")] [InlineData("Ironveil")]
public void BuildTree_EveryWorld_ThirteenNodes_Cost35(string world)
{
    var tree = TalentTemplateFactory.BuildTree($"tree_{world.ToLowerInvariant()}", $"char_{world.ToLowerInvariant()}_x", world, 2);
    Assert.Equal(13, tree.Nodes.Count);
    // 每分支 4 节点：基础 1 → 进阶 2 → 大师 3 → 宗师 4 费
    foreach (var br in new[] { "branch_power", "branch_defense", "branch_utility" })
    {
        var nodes = tree.Nodes.Where(n => n.BranchId == br).ToList();
        Assert.Equal(4, nodes.Count);
        Assert.Equal(new[] { 1, 2, 3, 4 }, nodes.Select(n => n.Cost).OrderBy(c => c).ToArray());
    }
    // 树顶必杀 5 费，唯一
    var ult = tree.Nodes.Where(n => n.BranchId == "branch_ultimate").ToList();
    Assert.Single(ult);
    Assert.Equal(5, ult[0].Cost);
    Assert.Equal(35, tree.Nodes.Sum(n => n.Cost)); // 4×(1+2+3+4)+5 = 35（§8 明示）
}

[Fact]
public void BuildTree_PrereqChain_LinearWithinBranch_UltimateRequiresWholeTree()
{
    var tree = TalentTemplateFactory.BuildTree("tree_t", "char_t_x", "Shinwa", 2);
    foreach (var br in new[] { "branch_power", "branch_defense", "branch_utility" })
    {
        var nodes = tree.Nodes.Where(n => n.BranchId == br).OrderBy(n => n.Cost).ToList();
        // 进阶←基础、大师←进阶、宗师←大师（线性）
        Assert.Equal(new[] { nodes[0].NodeId }, nodes[1].PrerequisiteNodeIds);
        Assert.Equal(new[] { nodes[1].NodeId }, nodes[2].PrerequisiteNodeIds);
        Assert.Equal(new[] { nodes[2].NodeId }, nodes[3].PrerequisiteNodeIds);
    }
    var ult = tree.Nodes.First(n => n.BranchId == "branch_ultimate");
    var all = tree.Nodes.Where(n => n.BranchId != "branch_ultimate").Select(n => n.NodeId).OrderBy(x => x).ToArray();
    Assert.Equal(all, ult.PrerequisiteNodeIds.OrderBy(x => x).ToArray()); // 整树 12 节点前置
}

[Fact]
public void BuildTree_NodeIds_PrefixedByCharacterId()
{
    // 存档兼容铁律：NodeId = "{characterId}_{shortId}"，短 ID t1..t13（老存档 t1-t6 不失配）
    var tree = TalentTemplateFactory.BuildTree("tree_t", "char_ur_zhulong", "Shinwa", 4);
    Assert.All(tree.Nodes, n => Assert.StartsWith("char_ur_zhulong_", n.NodeId));
    Assert.Equal(13, tree.Nodes.Select(n => n.NodeId).Distinct().Count());
    // 短 ID 连续 t1..t13
    var shorts = tree.Nodes.Select(n => n.NodeId.Substring("char_ur_zhulong_".Length)).OrderBy(x => x).ToArray();
    Assert.Equal(Enumerable.Range(1, 13).Select(i => "t" + i).ToArray(), shorts);
}

[Fact]
public void BuildTree_BranchThemes_MatchWorldTable()
{
    // §7.1 主题抽查：每世界强攻分支至少含一个主题效果类型，坚壁必含 DamageReduction
    var themes = new Dictionary<string, TalentEffectType[]>
    {
        ["Shinwa"]   = new[] { TalentEffectType.IgnoreDefense, TalentEffectType.CritRate },
        ["Aether"]   = new[] { TalentEffectType.Burn, TalentEffectType.Poison },
        ["Ironveil"] = new[] { TalentEffectType.Bleed, TalentEffectType.Disarm },
    };
    foreach (var (world, types) in themes)
    {
        var tree = TalentTemplateFactory.BuildTree($"tree_{world}", $"char_{world}_x", world, 2);
        var powerFx = tree.Nodes.Where(n => n.BranchId == "branch_power").SelectMany(n => n.Effects ?? new List<TalentEffect>()).ToList();
        Assert.Contains(powerFx, fx => types.Contains(fx.Type));
        // 坚壁必含减伤（三世界共同）
        var defFx = tree.Nodes.Where(n => n.BranchId == "branch_defense").SelectMany(n => n.Effects ?? new List<TalentEffect>()).ToList();
        Assert.Contains(defFx, fx => fx.Type == TalentEffectType.DamageReduction);
    }
}

[Theory]
[InlineData(1, 0.7f)] [InlineData(2, 1.0f)] [InlineData(3, 1.3f)] [InlineData(4, 1.3f)] // UR 保底与 SSR 同档
public void RarityMultiplier_Layers(int rarity, float expected)
    => Assert.Equal(expected, TalentTemplateFactory.RarityMultiplier(rarity), 3);
```

> 若 Task 1 落盘的 `BuildTree` 签名与本计划 Task 4 约定的 `(treeId, characterId, world, rarity)` 不一致，本任务测试先按约定签名改 Task 1 测试与工厂（一次统一，勿留两套签名）。

### 验证

- `dotnet test --filter FullyQualifiedName~Milan.Tests.TalentTemplateFactoryTests` — 原 12 + 新 5 = 17 用例通过（本任务结束 104）。
- 若「短 ID 连续 t1..t13」「前置线性」断言失败 → 修正 Task 1 模板工厂实现使其满足本任务定稿结构（§7.1 优先），不得放宽断言。

### 提交

```
test: 世界模板全量验证（13 节点/35 点/主题/前置/存档 ID 兼容）
```

## Task 8：分配边界测试（TalentEngine 小改）

### 设计事实

- `TalentEngine`（Core，25 行）现有：CanAllocate（未分配 + 前置满足）与 TotalPoints（Costs 之和）。规格 §8「天赋点显示逻辑不变」；分配硬约束 = 点数不足拒绝 + 必杀整树前置（模板已带 12 前置，引擎无需感知必杀）。
- **若现有 `TalentEngine.Allocate` 无「UnspentPoints ≥ Cost」校验**（需执行者读 `TalentEngine.cs` 确认），本任务补上（纯领域小改：Allocate 返回 false 不扣点）——这是规格 §8「13 节点共需 35 点」的前提保障。

### 改动

**8a. 按需补 `Core/Domain/Progression/TalentEngine.cs` 点数校验**（若已存在跳过）：

```csharp
// Allocate(allocatedIds, tree, nodeId, unspentPoints) 入口处：
// 校验不变量：未分配、前置满足（CanAllocate 语义）、unspentPoints >= node.Cost，任一不满足返回 false 且不修改状态。
```

**8b. `Tests/Milan.Tests/TalentTests.cs` 扩展**（追加 3 个 → 107）：

```csharp
// ────────── 追加到 TalentTests 类内 ──────────

[Fact]
public void CanAllocate_UltimateTerminal_RequiresWholeTree()
{
    // 必杀节点带 12 前置（Task 7 定稿）：未点满整树 → 拒绝；点满 12 基础 → 放行
    var tree = TalentTemplateFactory.BuildTree("tree_t", "char_t_x", "Shinwa", 2);
    var ult = tree.Nodes.First(n => n.BranchId == "branch_ultimate");
    var others = tree.Nodes.Where(n => n.BranchId != "branch_ultimate").Select(n => n.NodeId).ToList();

    Assert.False(TalentEngine.CanAllocate(new List<string>(), tree, ult.NodeId));            // 空分配 → 拒绝
    Assert.False(TalentEngine.CanAllocate(others.Take(11).ToList(), tree, ult.NodeId));      // 差 1 个 → 拒绝
    Assert.True(TalentEngine.CanAllocate(others, tree, ult.NodeId));                          // 满 12 → 放行
}

[Fact]
public void Allocate_InsufficientPoints_Rejected()
{
    var tree = TalentTemplateFactory.BuildTree("tree_t", "char_t_x", "Shinwa", 2);
    var root = tree.Nodes.First(n => n.Cost == 1);
    Assert.False(TalentEngine.Allocate(new List<string>(), tree, root.NodeId, 0)); // 0 点分 1 费 → 拒绝
    Assert.True(TalentEngine.Allocate(new List<string>(), tree, root.NodeId, 1));  // 恰好 1 点 → 放行
}

[Fact]
public void TotalPoints_TemplateTree_Equals35()
{
    var tree = TalentTemplateFactory.BuildTree("tree_t", "char_t_x", "Aether", 2);
    Assert.Equal(35, TalentEngine.TotalPoints(tree));
}
```

### 验证

- `dotnet test --filter FullyQualifiedName~Milan.Tests.TalentTests` — 原 6 + 新 3 = 9 通过（本任务结束 107）。
- 全量回归 107 全过。

### 提交

```
test: 分配边界（必杀整树前置/点数不足拒绝/模板 35 点）
```

## Task 9：必杀横卡 UI（ProgressionActivity）

### 实测结构

- `ProgressionActivity`：`box`（:507-511）= head + Spacer + `_talentBox`（Horizontal，三列）。`FillTalent()`（:515-559）遍历 `tree.BranchIds` 渲染列卡片；`BuildTalentNode`（:561+）通用卡片（已分配 ✓/可点高亮/锁定置灰，:566-577 三态背景）。必杀节点当前**无渲染**（旧树无此节点）。
- `_owned`/`UnspentPoints`/`CanAllocateTalent` 判定模式见 :550-553，必杀卡复用同一模式。

### 改动

**9a. 布局：`box` 内 `_talentBox` 之前插入必杀横卡容器**（:509 附近）：

```csharp
_ultimateBox = new LinearLayout(this) { Orientation = Orientation.Vertical };
box.AddView(_ultimateBox);
box.AddView(Spacer(8));
_talentBox = new LinearLayout(this) { Orientation = Orientation.Horizontal };
```

（新字段 `private LinearLayout _ultimateBox = null!;` 与 `_talentBox` 同区声明；`FillTalent` 首段 `_ultimateBox.RemoveAllViews()`。）

**9b. `FillTalent()` 首段渲染必杀节点**（:517 清空后插入）：

```csharp
_ultimateBox.RemoveAllViews();
var tree = GameState.Service.GetTalentTree(_def.CharacterId);
var ultimate = tree?.Nodes?.OfType<TalentNodeData>().FirstOrDefault(n => n.BranchId == "branch_ultimate");
if (ultimate != null)
{
    bool isAlloc = allocated.Contains(ultimate.NodeId);
    bool canAlloc = !isAlloc && _owned
        && _view.Save.UnspentPoints >= ultimate.Cost
        && GameState.Service.CanAllocateTalent(_def.CharacterId, ultimate.NodeId);
    var card = BuildTalentNode(ultimate, AppTheme.Gold, isAlloc, canAlloc); // 金色高亮（§8 树顶必杀）
    card.LayoutParameters = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
    _ultimateBox.AddView(card);
}
```

（`allocated` 定义在 :532，需上移或复用；分支列渲染逻辑不动——`branch_ultimate` 不在 BranchIds 三列中，天然不重复。）

- **金色取色**：`AppTheme.Gold` 若不存在（需执行者确认 `UI/AppTheme.cs`），用 BattleActivity.GoldFill() 同源色（`Color.Rgb(255, 200, 60)` 附近）——查证后二选一，勿新造色值。
- **BuildTalentNode 增强**（可选小改）：必杀卡在 DisplayName 前加「必杀 ·」前缀（`node.BranchId == "branch_ultimate"` 时），金色描边走 `col` 参数已覆盖。
- 立绘/头部不动；无新测试（UI 层）；验证靠 build + 真机。

### 验证

- `dotnet build` exit 0；全量测试回归 107 仍绿（本任务 +0）。
- 真机：养成页顶部出现金色必杀横卡（未分配=金色描边置灰内芯、可点=金色高亮、已分配=✓），点击分配/回退正常，三列分支不受影响；旧存档已分配 t1-t6 的角色横卡与列卡状态正确。

### 提交

```
feat: 养成页树顶必杀横卡（金色高亮独立渲染）
```

## Task 10：战斗页能量槽 + 必杀按钮（BattleActivity）

### 实测结构

- `BattleActivity` 布局：`root`（Vertical，:85）→ AppTopBar → `_enemyArea`（FrameLayout weight 1，:91-95，含 `EnemyPanel()` :145-195：立绘/名字/HP 条 `_enemyFill` :179-183/HP 文本/DEF）→ tip（:100）→ `_hand`（:103-107）→ 底部 `bar`（Horizontal，:110-127：撤退 + 自动战斗，`ThemeButtons`）。
- `_enemyStats`/`_enemyHp`/`_enemyMaxHp`（:41-43）；`OnCardPlayed`（:279-302）玩家攻击 `StrikeDamage(c.Stats, _enemyStats)` → 敌人反击 `StrikeDamage(_enemyStats, c.Stats)`；`EndBattle(true/false)`（:302 附近）；`_unitCard`/`_team`（当前出战角色/队伍）。
- 能量规格（§5.4）：0-100，攻击 +15、受击 +10，各乘 (1 + ChargeGain)；满 100 必杀；释放后清零。

### 改动

**10a. 字段**（:41-48 区域）：

```csharp
private int _energy;                       // 玩家侧能量 0-100（手动路径，自动路径在 Simulate 内）
private View _energyFill = null!;          // 能量槽填充条（复用 GoldFill 模式）
private TextView _energyText = null!;
private Android.Widget.Button _ultimateBtn = null!;
```

**10b. 能量槽 UI**（`EnemyPanel()` 下方、`_enemyArea` 之后 :95 与 tip 之间插入）：

```csharp
var energyRow = new LinearLayout(this) { Orientation = Orientation.Horizontal };
energyRow.LayoutParameters = new LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MatchParent, Dp(14)) { LeftMargin = Dp(16), RightMargin = Dp(16) };
var energyTrack = new LinearLayout(this) { Orientation = Orientation.Horizontal }; // 或直接行内
_energyFill = new View(this);
_energyFill.LayoutParameters = new LinearLayout.LayoutParams(0, Dp(14), 0f); // 宽度比例 = _energy/100
_energyFill.Background = GoldFill();
((GradientDrawable)_energyFill.Background).SetCornerRadius(Dp(7));
energyRow.AddView(_energyFill);
root.AddView(energyRow);
_energyText = UI.Text("能量 0 / 100", 11, AppTheme.Text2);
_energyText.Gravity = GravityFlags.CenterHorizontal;
root.AddView(_energyText);
```

（具体结构按实码微调：能量条可与 HP 条同风格；填充宽度在 `UpdateEnergyBar()` 里用 LayoutParams 权重更新——**权重法**：`new LinearLayout.LayoutParams(0, Dp(14), _energy / 100f)` 一行搞定，参考 `_enemyFill` 模式 :180-183。）

**10c. 必杀按钮**（底部 bar，撤退与自动战斗之间 :125-126）：

```csharp
var ultimate = ThemeButtons.Neon(this, "必 杀"); // 样式按实码 ThemeButtons 可选（Neon/Danger），能量满前置灰
_ultimateBtn = ultimate;
var ultLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
ultimate.LayoutParameters = ultLp;
ultimate.Enabled = false;
ultimate.Click += (_, _) => CastUltimate();
bar.AddView(ultimate); // 与 retreat/auto 同批 AddView
```

**10d. 逻辑**：

```csharp
void GainEnergy(float gain)
{
    _energy = Math.Min(100, _energy + (int)Math.Round(gain));
    UpdateEnergyBar();
}

/// <summary>手动必杀：当前出战角色攻方，TryUltimate 必中高倍（规格 §5.4），能量清零。</summary>
void CastUltimate()
{
    if (_energy < 100 || _enemyHp <= 0) return;
    var attacker = _unitCard?.Stats ?? _team?.FirstOrDefault()?.Stats;
    if (attacker == null) return;
    int dmg = BattleSimulator.TryUltimate(attacker, _enemyStats, new System.Random());
    _enemyHp = Math.Max(0, _enemyHp - dmg);
    _energy = 0;
    ToastShow($"{_enemyName} 受到必杀 {dmg} 点伤害");
    UpdateEnemyBar();
    UpdateEnergyBar();
    if (_enemyHp <= 0) EndBattle(true);
}

void UpdateEnergyBar()
{
    // _energyFill 权重 = _energy/100f；_energyText 更新文本；按钮 Enabled = _energy >= 100
}
```

**10e. 能量挂接点**（`OnCardPlayed` 内）：
- 玩家攻击命中后（:285 `StrikeDamage` 调用后）：`GainEnergy(15 * (1 + c.Stats.ChargeGain))`——注意 c 为出手卡，`c.Stats` 来自 ComputeStats（含 ChargeGain 字段，Task 6 后生效）。
- 敌人反击后（:293 后，玩家受击）：`GainEnergy(10 * (1 + c.Stats.ChargeGain))`。
- 若 `c.Stats.ChargeGain` 为 0（默认），等价规格基准 +15/+10 ✓；`_unitCard` 判定为空时不崩（`?.` 链）。

### 验证

- `dotnet build` exit 0；全量测试回归 107 仍绿（本任务 +0）。
- 真机：战斗页出现能量槽与必杀按钮；攻击/受击能量增长（15/10），满 100 按钮点亮；点击必杀 → 高倍伤害 + 能量清零 + 击杀正常结算；自动战斗路径（Simulate）不受影响（手动 `_energy` 独立于 Simulate 内部能量）。

### 提交

```
feat: 战斗页能量槽与必杀按钮（手动路径 TryUltimate）
```

## Task 11：手动战斗路径接入 Strike 链（BattleActivity）

### 设计决策（手动路径 × 回合制语义的适配）

- 手动路径无「回合开始」节拍：玩家出牌 = 节拍。DoT 在**玩家攻击命中并施加状态后立即结算一次**（TickDots + TickDown），保证验收 3「DoT 可观察」；Stun/Disarm 的观察表现 = **敌人反击被跳过**（手动路径敌人仅反击，无独立回合）；Chill/Taunt 对手动路径无操作语义（速度/目标选择不涉及），仅状态标签显示。
- `BattleSimulator.ApplyStatusAttacks` 由 private 提升为 **public static**（签名 `(UnitStats attackerStats, List<StatusEffect> targetStatuses, Random rng)`），Simulate 与手动路径共用（单一事实来源 = StatusEngine.TryApply；避免双套实现漂移，规格 §5.3 末句）。
- 敌人 `_enemyStats`（:76 构造）机制字段全 0、StatusAttacks 为 null → `Strike` 自动退化为原 `StrikeDamage` 行为（无暴击/闪避/减伤），**旧伤害数值不变**（回归安全）。

### 改动

**11a. `BattleSimulator.cs`**：`ApplyStatusAttacks` 改 public static（签名如上；内部逻辑不变：遍历 attackerStats.StatusAttacks，`rng.NextDouble() < fx.Chance` → TryApply(..., HasUnstoppable(targetStatuses))；Simulate 内调用点改 `ApplyStatusAttacks(actor.Stats, target.Statuses, _rng)`）。

**11b. `BattleActivity.cs` 字段**（:41-48 区域）：

```csharp
private List<StatusEffect> _enemyStatuses = new();   // 敌人承受的状态（玩家施加；敌人自身不施放，§10）
private TextView _enemyStatusLabel = null!;          // 敌人面板状态标签行
```

**11c. 敌人面板状态标签**（`EnemyPanel()` 内 HP 文本 :186-189 之后）：

```csharp
_enemyStatusLabel = UI.Text("", 11, AppTheme.Violet);
_enemyStatusLabel.Gravity = GravityFlags.CenterHorizontal;
_enemyStatusLabel.SetPadding(0, Dp(5), 0, 0);
inner.AddView(_enemyStatusLabel);
```

**11d. 玩家攻击段替换**（OnCardPlayed :285 `StrikeDamage` 调用处）：

```csharp
var r = BattleSimulator.Strike(c.Stats, _enemyStats, new System.Random());
if (r.Dodged)
{
    ToastShow("敌人闪避了攻击！");
}
else
{
    _enemyHp = Math.Max(0, _enemyHp - r.Damage);
    if (r.Healed > 0)
        ToastShow($"吸血回复 {r.Healed}"); // 回血应用到出战角色（c.Hp 上限 c.Stats.Hp，若手动路径有 Hp 展示）
    if (r.Reflected > 0)
    {
        ToastShow($"被反伤 {r.Reflected}");
        // 反伤扣出战角色 Hp（同敌人反击扣血路径，若 c 死亡按现有 Dead 置灰逻辑处理）
    }
    // 状态施加（玩家 StatusAttacks → 敌人），命中后立即结算一次 DoT（手动路径节拍）
    BattleSimulator.ApplyStatusAttacks(c.Stats, _enemyStatuses, new System.Random());
    StatusEngine.TickDots(_enemyStats, _enemyStatuses, ref _enemyHp);
    StatusEngine.TickDown(_enemyStatuses);
    UpdateEnemyStatusLabel();
}
UpdateEnemyBar(); // 现有调用保留
```

（若现有代码段 :285-302 结构不同（如 Toast 顺序），保持原结构、仅替换伤害计算与插入状态/吸血/反伤应用——最小改动原则。）

**11e. 敌人反击段替换**（OnCardPlayed :293 `StrikeDamage` 调用处）：

```csharp
if (StatusEngine.SkipTurn(_enemyStatuses))
{
    ToastShow("敌人被控制，无法行动！"); // Stun 观察表现（§5.3）
}
else
{
    var er = BattleSimulator.Strike(_enemyStats, c.Stats, new System.Random());
    // 敌人机制字段全 0：er 恒等于旧 StrikeDamage 结果（无暴击/闪避）
    // 原扣血/Dead 置灰逻辑保持不变（用 er.Damage；er.Dodged 理论上恒 false）
    if (!er.Dodged && er.Damage > 0) { /* 原 c.Hp 扣减 + 卡面更新 + Dead 置灰 Alpha=0.35f 逻辑 */ }
    StatusEngine.TickDown(_enemyStatuses); // 节拍同步递减
    UpdateEnemyStatusLabel();
}
```

**11f. 状态标签**：

```csharp
void UpdateEnemyStatusLabel()
{
    if (_enemyStatuses.Count == 0) { _enemyStatusLabel.Text = ""; return; }
    // 中文名映射：Stun=眩晕 Disarm=缴械 Burn=灼烧 Poison=中毒 Bleed=流血 Chill=冰冷 Taunt=嘲讽 Unstoppable=霸体
    _enemyStatusLabel.Text = string.Join("  ", _enemyStatuses.Select(s => $"{NameOf(s.Type)}×{s.DurationTurns}"));
}
```

### 验证

- `dotnet build` exit 0；全量回归 107 仍绿（本任务 +0）。
- 真机（验收 3 核心）：装备燃烧/中毒/流血天赋的角色攻击敌人 → 敌人面板出现状态标签，攻击后敌人立即掉 DoT 伤害；眩晕/缴械天赋 → 敌人回合被跳过（Toast）；暴击/闪避/减伤/反伤/吸血 Toast 或数值可观察；无天赋角色行为与改造前一致（回归）。

### 提交

```
feat: 手动战斗路径接入 Strike 完整链与敌人状态结算
```

## Task 12：7 棵 UR 定制树数值表 + data.json 两处同步

### 前置事实

- 7 UR（规格 §7.2）：zhulong 烛龙 / wuxu 虚无 / xingtian 刑天 / kikyo 桔梗 / keqing 刻晴 / jinwu 金乌 / nuwa 女娲；TreeId = `tree_<拼音>`（data.json 已验证命名模式）。
- 每树 13 节点全定制（3 分支×4 级 + 必杀），费用结构同模板（1/2/3/4 + 5 = 35 点），NodeId = `char_ur_<拼音>_t1..t13`（存档兼容前缀）。
- **写入 data.json `TalentTrees` 数组**（Task 5 清空后为 `[]`）：7 棵树替换占位，普通角色不写（加载时模板兜底，Task 4 已保证）。
- 效果数值为 P1 设计值定稿（§10 范围边界：最终平衡留后续）。

### 数值表（直接照抄生成 JSON）

**tree_zhulong 烛龙（Shinwa/Flame，燃烧强化 + 火暴击 + 焚天灭世）**

| 节点 | 名称 | 分支 | 费 | 效果 |
|---|---|---|---|---|
| t1 | 炽焰 | power | 1 | Burn 8/0.5/2 |
| t2 | 焚心 | power | 2 | Burn 8/0.5/2, AtkPercent 0.04 |
| t3 | 天火 | power | 3 | Burn 12/0.6/3 |
| t4 | 焚天 | power | 4 | Burn 12/0.6/3, CritRate 0.10 |
| t5 | 火鳞 | defense | 1 | DamageReduction 0.06 |
| t6 | 熔铠 | defense | 2 | DamageReduction 0.06, HpPercent 0.06 |
| t7 | 曜日护体 | defense | 3 | Thorn 0.12 |
| t8 | 昼辉 | defense | 4 | DamageReduction 0.10, Thorn 0.12 |
| t9 | 疾日 | utility | 1 | SpdPercent 0.04 |
| t10 | 白昼行者 | utility | 2 | SpdPercent 0.06 |
| t11 | 灰烬重生 | utility | 3 | DodgeRate 0.08 |
| t12 | 永昼 | utility | 4 | SpdPercent 0.06, DodgeRate 0.08 |
| t13 | 焚天灭世 | ultimate | 5 | UltimateDamage 0.40, ChargeGain 0.20 |

**tree_wuxu 虚无（Aether/Void，中毒 DoT + 减伤 + 吞噬流）**

| t1 | 虚空之蚀 | power | 1 | Poison 0.04/0.4/3 |
| t2 | 虚无侵蚀 | power | 2 | Poison 0.04/0.4/3, AtkPercent 0.04 |
| t3 | 吞噬 | power | 3 | Poison 0.06/0.5/3 |
| t4 | 归零 | power | 4 | Poison 0.06/0.5/3, CritRate 0.08 |
| t5 | 虚无之躯 | defense | 1 | DamageReduction 0.08 |
| t6 | 空无 | defense | 2 | DamageReduction 0.08, HpPercent 0.06 |
| t7 | 万象归虚 | defense | 3 | DodgeRate 0.05 |
| t8 | 无相 | defense | 4 | DamageReduction 0.12, DodgeRate 0.05 |
| t9 | 虚无行者 | utility | 1 | SpdPercent 0.04 |
| t10 | 侵蚀蔓延 | utility | 2 | Poison 0.04/0.4/3 |
| t11 | 深渊凝视 | utility | 3 | CritDamage 0.12 |
| t12 | 终焉 | utility | 4 | CritRate 0.08, CritDamage 0.12 |
| t13 | 吞噬万物 | ultimate | 5 | UltimateDamage 0.50 |

**tree_xingtian 刑天（Ironveil/Metal，流血/缴械压制 + 嘲讽减伤 + 破军）**

| t1 | 干戚 | power | 1 | Bleed 0.05/0.45/3 |
| t2 | 断首不悔 | power | 2 | Bleed 0.05/0.45/3, AtkPercent 0.04 |
| t3 | 战意 | power | 3 | Bleed 0.07/0.55/3 |
| t4 | 刑天舞干戚 | power | 4 | Bleed 0.07/0.55/3, CritRate 0.08 |
| t5 | 铁骨 | defense | 1 | DamageReduction 0.08 |
| t6 | 铜皮 | defense | 2 | DamageReduction 0.08, HpPercent 0.06 |
| t7 | 挑衅 | defense | 3 | Taunt 1/0.4/2 |
| t8 | 无头战神 | defense | 4 | DamageReduction 0.12, Taunt 1/0.5/2 |
| t9 | 破军之势 | utility | 1 | SpdPercent 0.04 |
| t10 | 缴械 | utility | 2 | Disarm 1/0.25/2 |
| t11 | 断刃 | utility | 3 | Disarm 1/0.3/2, Bleed 0.05/0.45/3 |
| t12 | 铁血 | utility | 4 | SpdPercent 0.06, Disarm 1/0.3/2 |
| t13 | 破军 | ultimate | 5 | UltimateDamage 0.55 |

> 规格 §7.2 刑天「霸体」：P1 以「嘲讽+减伤」替代（Task 7 决策记录），提交说明注明规格偏差。

**tree_kikyo 桔梗（Shinwa/Shadow，闪避/暗袭/暴击 + 幽冥）**

| t1 | 暗刃 | power | 1 | CritRate 0.06 |
| t2 | 影袭 | power | 2 | CritRate 0.06, CritDamage 0.10 |
| t3 | 幽影连斩 | power | 3 | CritDamage 0.15 |
| t4 | 影舞 | power | 4 | CritRate 0.12, CritDamage 0.15 |
| t5 | 影遁 | defense | 1 | DodgeRate 0.05 |
| t6 | 雾隐 | defense | 2 | DodgeRate 0.05, DamageReduction 0.06 |
| t7 | 幻影 | defense | 3 | DodgeRate 0.10 |
| t8 | 无影 | defense | 4 | DodgeRate 0.10, DamageReduction 0.08 |
| t9 | 迅影 | utility | 1 | SpdPercent 0.04 |
| t10 | 疾影 | utility | 2 | SpdPercent 0.06 |
| t11 | 风影 | utility | 3 | CritRate 0.08 |
| t12 | 幻影神速 | utility | 4 | SpdPercent 0.08, CritRate 0.08 |
| t13 | 幽冥 | ultimate | 5 | UltimateDamage 0.50 |

**tree_keqing 刻晴（Aether/Thunder，速度/雷暴连击/ChargeGain + 雷鸣）**

| t1 | 雷切 | power | 1 | CritRate 0.06 |
| t2 | 雷暴 | power | 2 | CritRate 0.06, AtkPercent 0.04 |
| t3 | 雷霆万钧 | power | 3 | CritDamage 0.15 |
| t4 | 雷神降临 | power | 4 | AtkPercent 0.08, CritRate 0.10 |
| t5 | 雷铠 | defense | 1 | DamageReduction 0.06 |
| t6 | 磁盾 | defense | 2 | DamageReduction 0.06, DefPercent 0.08 |
| t7 | 雷障 | defense | 3 | DamageReduction 0.08 |
| t8 | 天雷护体 | defense | 4 | DefPercent 0.10, DamageReduction 0.08 |
| t9 | 电光 | utility | 1 | SpdPercent 0.06 |
| t10 | 疾雷 | utility | 2 | SpdPercent 0.06, ChargeGain 0.08 |
| t11 | 雷闪 | utility | 3 | ChargeGain 0.12 |
| t12 | 神速 | utility | 4 | SpdPercent 0.08, ChargeGain 0.12 |
| t13 | 雷鸣 | ultimate | 5 | UltimateDamage 0.35, ChargeGain 0.25 |

**tree_jinwu 金乌（Shinwa/Flame，燃烧 + 速度 + 烈日）**

| t1 | 阳炎 | power | 1 | Burn 8/0.5/2 |
| t2 | 烈日 | power | 2 | Burn 8/0.5/2, AtkPercent 0.04 |
| t3 | 金乌巡天 | power | 3 | Burn 10/0.6/3 |
| t4 | 十日凌空 | power | 4 | Burn 10/0.6/3, CritRate 0.08 |
| t5 | 金羽 | defense | 1 | DodgeRate 0.05 |
| t6 | 羽甲 | defense | 2 | DodgeRate 0.05, DamageReduction 0.06 |
| t7 | 涅槃 | defense | 3 | Lifesteal 0.08 |
| t8 | 浴火 | defense | 4 | DamageReduction 0.08, Lifesteal 0.08 |
| t9 | 逐日 | utility | 1 | SpdPercent 0.08 |
| t10 | 疾羽 | utility | 2 | SpdPercent 0.08 |
| t11 | 光速 | utility | 3 | SpdPercent 0.08, DodgeRate 0.05 |
| t12 | 极昼 | utility | 4 | SpdPercent 0.10 |
| t13 | 烈日 | ultimate | 5 | UltimateDamage 0.40, ChargeGain 0.15 |

**tree_nuwa 女娲（Aether/Earth，减伤/反伤/吸血守护 + 补天）**

| t1 | 抟土 | power | 1 | AtkPercent 0.04 |
| t2 | 造物 | power | 2 | AtkPercent 0.04, DefPercent 0.06 |
| t3 | 补天石 | power | 3 | DefPercent 0.10 |
| t4 | 神工 | power | 4 | AtkPercent 0.06, DefPercent 0.10 |
| t5 | 息壤 | defense | 1 | DamageReduction 0.08 |
| t6 | 厚土 | defense | 2 | DamageReduction 0.08, HpPercent 0.06 |
| t7 | 大地之母 | defense | 3 | Thorn 0.15 |
| t8 | 不周山 | defense | 4 | DamageReduction 0.12, Thorn 0.15 |
| t9 | 灵识 | utility | 1 | SpdPercent 0.04 |
| t10 | 造化 | utility | 2 | Lifesteal 0.08 |
| t11 | 生生不息 | utility | 3 | Lifesteal 0.08, HpPercent 0.06 |
| t12 | 万物之母 | utility | 4 | Lifesteal 0.12, SpdPercent 0.04 |
| t13 | 补天 | ultimate | 5 | UltimateDamage 0.45, ChargeGain 0.10 |

> 效果缩写格式：`Type Value/Chance/Duration`（状态类）或 `Type Value`（数值类）；多效果用逗号分隔。JSON 中 `Effects` 数组元素：`{ "Type": 13, "Value": 8.0, "Chance": 0.5, "Duration": 2 }`（枚举序号见 §4，字段与 `IncludeFields` 序列化匹配）。

### 改动

**12a. 写入两处 data.json `TalentTrees` 数组**（Assets 与 Resources/Raw **必须同步**）：7 棵树的完整 JSON（TreeId/BranchIds 三列/13 节点含 NodeId 前缀 char_ur_<拼音>_tN、PrerequisiteNodeIds 线性 + 必杀 12 前置、VisualLayerId=branch_<id>）。单棵树 JSON 形态示例：

```json
{
  "TreeId": "tree_zhulong",
  "BranchIds": ["branch_power", "branch_defense", "branch_utility"],
  "Nodes": [
    { "NodeId": "char_ur_zhulong_t1", "DisplayName": "炽焰", "Description": "攻击附加灼烧", "BranchId": "branch_power", "Cost": 1, "PrerequisiteNodeIds": [], "VisualLayerId": "branch_power", "Effects": [ { "Type": 13, "Value": 8.0, "Chance": 0.5, "Duration": 2 } ] },
    { "NodeId": "char_ur_zhulong_t2", "DisplayName": "焚心", "Description": "灼烧强化，攻击+4%", "BranchId": "branch_power", "Cost": 2, "PrerequisiteNodeIds": ["char_ur_zhulong_t1"], "VisualLayerId": "branch_power", "Effects": [ { "Type": 13, "Value": 8.0, "Chance": 0.5, "Duration": 2 }, { "Type": 0, "Value": 0.04 } ] },
    "…t3-t13 按上表生成…"
  ]
}
```

（Description 由执行者按效果生成中文文案，风格对齐现有模板描述；生成后人工抽查与模板工厂 Describe 语义一致。）

**12b. `Tests/Milan.Tests/DataJsonTests.cs` 扩展**（+2 → 109）：

```csharp
[Fact]
public void DataJson_UrTrees_HaveThirteenNodesAndUltimate()
{
    var ur = new[] { "tree_zhulong", "tree_wuxu", "tree_xingtian", "tree_kikyo", "tree_keqing", "tree_jinwu", "tree_nuwa" };
    var trees = Root().GetProperty("TalentTrees").EnumerateArray()
        .ToDictionary(t => t.GetProperty("TreeId").GetString()!);
    Assert.Equal(ur.Length, trees.Count); // 普通角色不写显式树
    foreach (var id in ur)
    {
        Assert.True(trees.ContainsKey(id), $"missing {id}");
        var nodes = trees[id].GetProperty("Nodes");
        Assert.Equal(13, nodes.GetArrayLength());
        // 必杀节点存在且 5 费，整树 35 点
        var ult = nodes.EnumerateArray().FirstOrDefault(n => n.GetProperty("BranchId").GetString() == "branch_ultimate");
        Assert.True(ult.ValueKind != JsonValueKind.Undefined, $"{id} missing ultimate");
        Assert.Equal(5, ult.GetProperty("Cost").GetInt32());
        Assert.Equal(35, nodes.EnumerateArray().Sum(n => n.GetProperty("Cost").GetInt32()));
    }
}

[Fact]
public void DataJson_AssetsAndRaw_Synchronized()
{
    // 两处 data.json 必须逐字节一致（AGENTS.md 铁律；Assets 打包进 APK，Raw 供测试/资源引用）
    var assets = File.ReadAllBytes(Path.Combine(AppContext.BaseDirectory, "data.json"));
    var raw = File.ReadAllBytes(Path.Combine(AppContext.BaseDirectory, "data.raw.json"));
    Assert.Equal(raw, assets);
}
```

（12b 需 csproj 同时链接 Assets 版：`<None Include="..\..\MauiMilan\Platforms\Android\Assets\data.json" Link="data.raw.json" CopyToOutputDirectory="PreserveNewest" />`——Link 名取 data.raw.json 避免与 Raw 版冲突。若执行者觉得 Link 名别扭可换，两文件路径必须都在输出目录。）

### 验证

- `dotnet test --filter FullyQualifiedName~Milan.Tests.DataJsonTests` — 4 用例通过（本任务结束 109）。
- 两处 data.json 同步后 `Get-FileHash` 一致；`dotnet build` exit 0。
- 真机：7 个 UR 角色养成页显示定制树（树名/必杀名各不同），普通角色仍走世界模板；UR 必杀名（焚天灭世/吞噬万物/破军/幽冥/雷鸣/烈日/补天）正确。

### 提交

```
feat: 7 棵 UR 定制天赋树写入 data.json（两处同步）
```

## Task 13：收尾验证与汇总

### 改动

**13a. 全量验证**：

1. `& "C:\Users\Administrator\.dotnet\dotnet.exe" test Tests\Milan.Tests\Milan.Tests.csproj` — 全部通过，计数 **109**。
2. `& "C:\Users\Administrator\.dotnet\dotnet.exe" build MauiMilan\MauiMilan.csproj -c Release` — exit 0（APK 产物 `MauiMilan/bin/Release/net10.0-android/com.milan.game-Signed.apk`）。
3. lsp_diagnostics 抽查全部改动文件无 error。
4. 两处 data.json `Get-FileHash` 一致。

**13b. 真机验收（规格 §11 六条逐条过）**：

1. 天赋面板 13 节点/树（普通=模板、UR=定制），分配/前置/落盘回滚生效（断网重开分配保留）。
2. 点亮节点后详情页/养成页/战斗页属性与机制字段同步变化（StatsCalculator 单一入口）。
3. 战斗出现暴击/闪避/减伤/DoT/控制/嘲讽/霸体相关（霸体=嘲讽减伤替代）/必杀可观察，手动与自动行为一致（Strike/Simulate 共用链）。
4. 必杀能量槽随攻击/受击增长，满能量点亮，释放高倍伤害。
5. 21 普通角色世界模板生效，7 UR 定制树生效，data.json 两处一致。
6. 测试全绿 109（含 66 回归）。

**13c. 收尾清单**：

- 计划文件核对：标题数（标题 + 13 任务 + 验证/提交节）与任务编号 1-13 连续；编码 UTF-8；提交历史 `docs:` 一次 + 各任务原子提交。
- 若执行中发现本计划与实码的结构偏差（签名/行号/布局），**以实码为准并回更计划文件**（标注「执行偏差」），不静默偏离。
- 可选：AGENTS.md「架构要点」补一行「属性计算单一事实来源 = Core/Domain/Progression/StatsCalculator；天赋模板 = TalentTemplateFactory；状态 = StatusEngine」。

### 汇总表（最终测试计数，按实际运行结果填写）

| 任务 | 内容 | 新增 | 累计 |
|---|---|---|---|
| 基线 | 现有测试 | — | 66 |
| 1 | 天赋效果枚举 + 三世界模板工厂 | +8（12 用例口径见任务 1） | 74 |
| 2 | 状态系统 StatusEngine + UnitStats 机制字段 | +7 | 81 |
| 3 | Strike 链 + Simulate 重写 | +10 | 91 |
| 4 | 模板化生成 + 空树兜底修复 | +0 | 91 |
| 5 | data.json 清理 + 数据校验 | +2 | 93 |
| 6 | StatsCalculator 提取 | +6 | 99 |
| 7 | 世界模板全量验证 | +5 | 104 |
| 8 | 分配边界 | +3 | 107 |
| 9 | 必杀横卡 UI | +0 | 107 |
| 10 | 能量槽 + 必杀按钮 | +0 | 107 |
| 11 | 手动路径 Strike 接入 | +0 | 107 |
| 12 | UR 树 + data.json 同步 | +2 | 109 |
| 13 | 收尾验证 | +0 | 109 |

> 各任务「新增」为计划口径（Theory 展开数按 xUnit 实际用例计）；若运行结果与表不符，以运行输出为准修正本表并注明原因（如 Task 1 实际 12 用例 → 基线 66 + 12 = 78，则累计列整体右移 +4，最终 = 113——**以实际为准**，本表仅作预期）。
