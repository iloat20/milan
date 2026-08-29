# 抽卡演出增强设计稿（P4-3）

> **日期**：2026-08-29  
> **关联**：`2026-08-28-ink-wash-animations-design.md`（P4-2 基础）  
> **状态**：待审批

---

## 1. 目标

在现有水墨演出框架（CyberStage + RevealStage 状态机）上增加两层体验：

| # | 特性 | 核心价值 |
|---|---|---|
| A | **稀有度分级演出** | 不同稀有度触发不同视觉强度，让玩家「感受到」金光的价值 |
| B | **抽卡结果复盘** | 抽完后提供数据汇总入口，满足收集欲和社交分享需求 |

---

## 2. 特性 A：稀有度分级演出

### 2.1 现状

当前 `RevealStage` 对所有稀有度一视同仁：Charge(420ms) → Beam(480ms) → Single/Ten 揭晓 → 自动收起。唯一区别是 R/SR 不触发 CONFIRM 震感。

### 2.2 设计

按稀有度分4档演出强度，**不改变状态机枚举**，仅调整 `doPull()` 中 delay 时长和视觉参数：

| 稀有度 | Charge 时长 | Beam 颜色 | 揭晓停留 | 震感 | 额外效果 |
|---|---|---|---|---|---|
| R (1) | 300ms | CyberPalette.Grid（暗淡） | 1000ms | VIRTUAL_KEY | 无 |
| SR (2) | 420ms（当前默认） | CyberPalette.Cyan 50% | 1500ms | VIRTUAL_KEY | 墨粒加速 |
| SSR (3) | 600ms | CyberPalette.Magenta 80% | 2400ms | CONFIRM | 金箔粒子爆发 |
| UR (4) | 900ms | CyberPalette.BeamCore 全亮 | 3600ms | CONFIRM + LONG_PRESS | 全屏水墨山水闪现 |

### 2.3 实现路径

**修改文件**：仅 `GachaScreen.kt` 的 `doPull()` 函数

```kotlin
// 现有（line 199）：
delay(420); if (token != reveal.token) return@launch

// 改为：
val chargeMs = when (best.rarity) {
    4 -> 900L   // UR
    3 -> 600L   // SSR
    2 -> 420L   // SR
    else -> 300L // R
}
delay(chargeMs); if (token != reveal.token) return@launch
```

同理调整 Beam delay（当前固定 480ms）和揭晓停留（当前固定 1500ms/3600ms）。

**不改动**：RevealStage 枚举、CyberStage 演出组件、CyberHerald。演出强度差异完全由时长控制，视觉组件内部已是动态参数。

### 2.4 视觉增强（可选）

在 `CyberRevealLayer` 中为 SSR/UR 增加：
- **SSR**：Beam 阶段额外叠加 24 颗金箔粒子从中心爆发（复用 InkParticles 模式，颜色改为 Magenta，生命周期 800ms）
- **UR**：揭晓瞬间插入一帧全屏水墨山水 Canvas（150ms 渐显+渐隐），致敬传统卷轴画

---

## 3. 特性 B：抽卡结果复盘

### 3.1 现状

抽卡结果以 `GachaChip` 网格展示（line 441-457），上方有摘要文字行（line 404-437）和「分享」「历史」按钮。无统计汇总。

### 3.2 设计

在摘要行下方增加**统计卡片**（GlassPanel），展示本次抽卡数据：

```
┌─────────────────────────────────┐
│  本次统计                        │
│  总计 10抽  ·  最高 SSR          │
│  ★×2  ★★×5  ★★★×2  ★★★★×1      │
│  保底进度  42/90                 │
│  距下次软保底  8抽               │
└─────────────────────────────────┘
```

### 3.3 实现路径

**新增 Composable**：`PullStatsPanel`（在 GachaScreen.kt 内部）

```kotlin
@Composable
private fun PullStatsPanel(
    results: List<PullResult>,
    pity: Int,
    hardPity: Int,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) return
    val counts = results.groupBy { it.rarity }.mapValues { it.value.size }
    val best = results.maxOfOrNull { it.rarity } ?: 1
    val softStart = EconomyFormulas.softPityStart(hardPity)
    val softRemaining = if (softStart > 0) (softStart - pity).coerceAtLeast(0) else 0

    GlassPanel(modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("本次统计", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.Gold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("总计 ${results.size}抽", fontSize = 11.sp, color = AppTheme.Text2)
                Text("最高 ${AppTheme.rarityName(best)}", fontSize = 11.sp, color = AppTheme.rarityColor(best))
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 1..4) {
                    val c = counts[r] ?: 0
                    if (c > 0) {
                        Text(
                            "${"★".repeat(r)}×$c",
                            fontSize = 11.sp,
                            color = AppTheme.rarityColor(r),
                        )
                    }
                }
            }
            if (hardPity > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { pity.toFloat() / hardPity },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = AppTheme.Gold,
                    trackColor = AppTheme.Surface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "保底进度  $pity / $hardPity" +
                        if (softRemaining > 0) "  · 软保底还差 $softRemaining 抽" else "",
                    fontSize = 10.sp,
                    color = AppTheme.Text3,
                )
            }
        }
    }
}
```

**插入位置**：GachaScreen.kt line 438（摘要行与结果网格之间）

```kotlin
Spacer(Modifier.height(6.dp))
// 新增：
PullStatsPanel(
    results = results,
    pity = pity,
    hardPity = pool?.hardPity ?: 0,
    modifier = Modifier.fillMaxWidth(),
)
Spacer(Modifier.height(4.dp))
// 结果网格...
```

### 3.4 交互

- 统计卡片随 `results` 变化自动更新（每次抽卡后刷新）
- 保底进度条复用现有 `LinearProgressIndicator` 样式
- 点击「历史」按钮跳转已有历史页（`onOpenHistory`）

---

## 4. 不改动的边界

| 文件 | 原因 |
|---|---|
| CyberStage.kt | 演出组件已是参数化设计，时长差异由调用方控制 |
| CharacterCard.kt | 卡片展示不变 |
| GameService.kt / GachaEngine.kt | 数据层不变，仅 UI 层展示调整 |
| SharedTransitionLocals.kt | 转场不变 |

---

## 5. 验收标准

- [ ] R 抽快速过场（300ms charge），UR 抽仪式感拉满（900ms charge + 全屏闪）
- [ ] 抽卡后统计卡片正确显示数量/稀有度分布/保底进度
- [ ] 统计卡片在空结果时不渲染
- [ ] `assembleDebug` 通过
- [ ] `testDebugUnitTest` 通过
- [ ] 所有注释中文
