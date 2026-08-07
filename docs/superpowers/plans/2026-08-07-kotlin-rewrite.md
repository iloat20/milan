# Milan Kotlin 全量重写计划（2026-08-07）

> **用户决策（2026-08-07 确认）**：动机 = 现代 Kotlin 生态（Compose/协程/AndroidX）；范围 = 一次性全量重写；现有 C# 代码 = 重写完成后直接废弃。
> 重写期间 C# 代码保留（git 历史 + 工作树）作为翻译基准；Kotlin 交付后由用户确认再删。

## 目标

Kotlin + Compose 重写 Milan，功能等价于 C# 版：

- 领域逻辑行为等价（113 个 xunit 测试翻译为 JUnit 对照）
- 七屏 UI 重建（Home / Gacha / 角色列表 / 角色详情 / 图鉴 / 养成 / 战斗）
- 存档兼容（C# 老存档 JSON 可读）

## 环境事实（2026-08-07 验证）

- JDK 17.0.19 Temurin ✓（JAVA_HOME 已设）
- Android SDK：`C:\Users\Administrator\AppData\Local\Android\Sdk`（platforms/build-tools 34/35/36、licenses 已接受、cmdline-tools 12.0/latest）
- Gradle CLI 无 → 首次构建下载 Gradle 发行版（wrapper 指向本地 zip 可加速，收尾回写 https URL）
- 无连接设备/模拟器 → UI 验证 = 构建 + 静态走查；真机验收留给用户
- dotnet 链（AGENTS.md 红线）仅约束 C# 工程，与 Kotlin 工程互不影响

## 架构决策

| 项 | 决策 |
|---|---|
| 工程 | 单 Gradle 工程 `MilanKotlin/`（主仓根，与 MauiMilan 并存过渡） |
| 包名 | `com.milan.game`（与 C# 版一致） |
| SDK | minSdk 29 / compileSdk 36 / targetSdk 36（对齐 C# 版 minSdk 29） |
| UI | Compose（Material3）+ Activity Compose + Navigation Compose，单 Activity |
| 异步 | 协程 + StateFlow；领域层纯 Kotlin（无 Android 依赖，可 JVM 单测） |
| 序列化 | kotlinx.serialization；SaveData JSON 字段名对齐 C# 版（老存档可读） |
| 测试 | JUnit4 翻译领域测试（113 个对照）；Robolectric UI 冒烟收尾评估 |
| 版本 | Gradle 8.13 / AGP 8.10.1 / Kotlin 2.1.20 / compose BOM 2025.05.00 / serialization 1.8.1 / coroutines 1.10.1 / activity-compose 1.10.0 / navigation-compose 2.8.9 / core-ktx 1.15.0 / lifecycle 2.8.7 |

## 任务

### Task 1：Gradle 工程骨架

**改动**：`MilanKotlin/` 下 settings.gradle.kts、根 build.gradle.kts、gradle.properties、gradle/libs.versions.toml、Gradle wrapper；`app/` 模块（AndroidManifest、最小 Compose MainActivity 空壳、主题最小集、图标复用 C# `MauiMilan/Resources` 的 mipmap）。

**验证**：`gradlew :app:assembleRelease` 成功出 APK；lint 无 error。

**提交**：`chore: Kotlin/Compose 工程骨架`

### Task 2：Enums + 数据模型 + 存档系统

**改动**：翻译 `Core/Data/Enums.cs`（Rarity/World/TalentEffectType 等）；SaveData/SaveManager/ISaveProvider → kotlinx.serialization，字段名对齐 C#（老存档兼容）；损坏 JSON 兜底、.tmp/.bak 原子写语义保持。

**验证**：存档相关测试翻译全绿。

**提交**：`feat: 数据模型与存档系统（kotlinx.serialization 兼容）`

### Task 3：抽卡引擎

**改动**：GachaEngine/PityCounter（随机种子语义对齐 C# `System.Random`）；重复补偿碎片（EconomyFormulas.FragmentsForRarity：UR 50/SR 20/SR 5/R 1——依赖 Task 4，如遇先实现最小集）。

**验证**：测试翻译全绿（硬保底/软保底/稀有度带提升）。

**提交**：`feat: 抽卡引擎与保底（行为等价）`

### Task 4：养成引擎

**改动**：ProgressionEngine/EconomyFormulas（升级/突破/升星花费单一事实来源——C# 铁律同款：禁止就地写数字）。

**验证**：测试翻译全绿。

**提交**：`feat: 养成引擎与经济公式`

### Task 5：天赋系统

**改动**：TalentEngine/TalentTemplateFactory/StatsCalculator/VisualLayerComposer 翻译；世界模板 + 7 棵 UR 定制树加载（两处 data.json 同步语义在 GameService，Task 7 收口）。

**验证**：模板全量验证测试翻译（13 节点/35 点/主题/前置/存档 ID 兼容/UR 定制树）。

**提交**：`feat: 天赋系统（模板工厂 + 属性计算单一入口）`

### Task 6：战斗系统

**改动**：StatusEngine/UnitStats/Strike 链/BattleSimulator 翻译（15 种机制、必杀、手动/自动共用链）。

**验证**：测试翻译全绿（状态结算/必杀/自动战斗/双向反击）。

**提交**：`feat: 战斗系统（StatusEngine + Strike 链）`

### Task 7：EventBus + GameService

**改动**：EventBus 队列 + 逐帧分发语义 → 协程等价；GameService（data.json 内容加载 + 兜底、抽卡/养成/战斗编排、货币事务范式：预算校验→改内存→落盘→失败回滚）。

**验证**：内容加载/事务语义测试（Service 层 C# 无测试，此处补 Kotlin 测试）。

**提交**：`feat: 服务编排（EventBus + GameService）`

### Task 8：Compose 主题

**改动**：twilight 调色板对齐（C# `UI/ElementTheme.cs` / `WorldTheme.cs` / `UIHelper.cs` 色值）；组件：按钮/卡片/导航栏/渐变（Compose 的 Color 无 C# 的 ColorOS long 重载坑）。

**验证**：构建 + 色值对照表核对。

**提交**：`feat: Compose 主题系统（twilight 对齐）`

### Task 9：Home + Gacha 屏

**改动**：标题/货币/导航卡片；抽卡（单抽/十连/稀有度网格/裂缝视觉/结果立绘）。

**提交**：`feat: 首页与抽卡屏`

### Task 10：角色列表 + 详情 + 图鉴屏

**改动**：稀有度卡片 2 列网格；详情（Hero 立绘 + 属性 + 天赋入口）；图鉴（进度条 + 未拥有剪影）。

**提交**：`feat: 角色列表/详情/图鉴屏`

### Task 11：养成屏

**改动**：升级/突破/升星 + 天赋树（13 节点、三列、必杀横卡金色高亮、分配/前置/落盘回滚）。

**提交**：`feat: 养成屏（天赋树 + 必杀横卡）`

### Task 12：战斗屏

**改动**：队伍/敌人/能量槽/必杀按钮/状态提示/手动 + 自动。

**提交**：`feat: 战斗屏（能量槽 + 必杀）`

### Task 13：收尾验证与汇总

**改动**：全量验证（`gradlew :app:testDebugUnitTest` 全绿 + `assembleRelease` + lint）；领域行为等价对照（C# 测试 vs Kotlin 测试同输入同输出）；计划回更执行偏差；C# 废弃交接说明（删除由用户确认后执行）。

**提交**：`chore: Kotlin 重写收尾验证与汇总`

## 验证

- 每任务：`gradlew :app:testDebugUnitTest` + `gradlew :app:assembleRelease` exit 0
- 领域等价：C# 113 测试与 Kotlin 翻译测试同输入断言同输出
- 真机验收（用户）：七屏 UI、养成/战斗、C# 老存档读入 Kotlin 版
