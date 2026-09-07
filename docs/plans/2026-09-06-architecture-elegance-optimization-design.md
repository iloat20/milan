# Milan 架构优雅性优化设计稿

> **日期**：2026-09-06
> **范围**：MilanKotlin 工程（`com.milan.game`）
> **目标**：识别冗余、耦合、职责不清、扩展性不足；提出并实现提升架构优雅性的优化方案，**确保现有功能与行为不受影响**。
> **方法**：四路并行 Explore 子代理静态审计 → 主代理逐条取证复核 → 设计方案 → 用户对齐范围 → 全自主执行。
> **基线**：R5 全 Bug 审查完成（2026-09-03），单测 292/292 全绿。

---

## 0. 摘要

四路审计识别出 **0 个 P0**（无崩溃级）、**11 个 P1**（显著可维护性/扩展性问题）、**8 个 P2**（改进建议）。

**推荐方案 A：纯架构清理**——只动"修一发不动行为"的纯架构债，不接线死功能、不重构 UI、不破坏序列化兼容。共 8 个原子步骤，每步可独立验证。

**不在本次范围**（独立工作流）：
- 死功能接线（属 `2026-09-04-dead-feature-wiring-design.md`，产品决策）
- UI 层 ViewModel 抽象（独立 /brainstorming）
- 巨型屏幕拆分（独立 /brainstorming）
- 充值合规（待产品裁定）

---

## 1. 审计方法

四个 Explore 子代理并行扫描四个维度：

| 子代理 | 范围 | 主要工具 |
|---|---|---|
| UI 层 | `app/src/main/java/com/milan/game/ui/` | wc/grep/import 关系 |
| services 层 | `app/src/main/java/com/milan/game/services/` | 门面扇入、互调验证、事务范式 |
| data/domain/infra 层 | `data/` + `infrastructure/` + `shared/commonMain/` | 领域纯净、序列化、容错链 |
| 跨切面 | 全工程 | 依赖图、魔法数字、命名、测试盲区 |

主代理对每条 P0/P1 用 grep/sed/read 二次实证（避免子代理偏差），关键事实全部复核。

---

## 2. 问题优先级矩阵

### 2.1 P0（动则破坏运行/构建）— 无

四路审计均无 P0 崩溃级问题。AGENTS.md 红线（领域纯净性、序列化结构、SaveManager 容错、CrashReporter 链路）整体合规。

### 2.2 P1（显著可维护性/扩展性问题）

| # | 维度 | 问题 | 影响 | 取证 |
|---|---|---|---|---|
| P1-1 | services | **GameService 门面上帝化**：854 行，聚合 14 个 internal 服务（KDoc 仍写"五个业务域"，过期） | 新增系统必须改门面，扇入过高 | `GameService.kt:854`；KDoc `:82-110`；字段 `:132-145` |
| P1-2 | services | **GameService 内联业务逻辑**：`addCharacterAffinity`(`:794`)、`giftAffinity`(`:822`)、`getOwnedEquipments`(`:415`)、`getAllOwnedEquipments`(`:425`) 直接写业务规则，违反自述"门面不含领域规则" | 职责泄漏，门面难以演进 | `GameService.kt` 同上 |
| P1-3 | services | **ServiceCore 越界业务规则**：`calculateEquipmentStats`(`:353`)、`calculateSetBonuses`(`:411`)、`addAffinityDelta`(`:278`)、`unitStatsFor`(`:305`) 实现业务逻辑，违反 KDoc "core 不含业务规则"(`:36-45`) | core 难以注替身，可测性受限 | `ServiceCore.kt:353-470` |
| P1-4 | services | **死功能层（真零 UI 引用）**：`PvEService`、`SocialService`、`EquipmentService` 在 ui/ 下零调用 | 死代码增加维护负担、误导扩展判断 | MEMORY 称"6 系统全死"已过期；实际 Arena/EventRhythm/Monetization 已接线 |
| P1-5 | services | **魔法数字散落**：R5 服务多处就地硬编码违反 AGENTS.md 红线"禁止就地写数字" | 养成数值失衡、难调参 | `EquipmentService.kt:235(*10)/:278(100+(lv-1)*50)`、`SocialService.kt:162(*10)`、`MonetizationService.kt:192(lv*2000)/:195(lv*5)`、`PvEService.kt:166(500+floor*100+stage*50)`、`DailyMissionService.kt:148(coerceAtMost 100)` |
| P1-6 | 跨切面 | **命名分裂**："发放"动词混用 `grant`/`add` 同义异名 | 扩展时易选错范式 | `grantEquipment` vs `addSoft/addHard/addExp/addFriend/addBattlePassExp/addCharacterAffinity` |
| P1-7 | data | **SaveData 上帝对象**：548 行单 data class 承载 40+ 字段（货币/编队/抽卡/装备/竞技场/社交/变现/活动/剧情/好感…） | 字段无子结构封装，难维护 | `SaveData.kt:548`；sanitize/createDefault 已集中收口 |
| P1-8 | data | **EventBus.dispatch 在 writeMutex 临界区**：`ServiceCore` 在 `transaction()`(`:189`) 持锁临界区内直接 `dispatch()`(`:202/:209`)，无任何注释/标记 | 订阅者 handler 内若再调 service 写操作即死锁（Mutex 不可重入），零订阅者场景巧合安全 | `ServiceCore.kt:189/202/209` |
| P1-9 | UI | **GameState 单例散落 14+ 文件硬编码**：无 DI 容器，所有屏幕直接 `GameState.service.xxx` | 依赖无法替换、测试只能 `resetForTest()` 重置全局 | 14+ 文件取证：deck/collection/achievement/characters/progression/home/tower/gacha/MilanNavHost 等 |
| P1-10 | UI | **巨型屏幕 + 业务逻辑混 UI**：15 个文件 >300 行（GachaScreen 812、HomeScreen 698、ProgressionPanels 574、StoryScreen 506 等）；写操作包进 composable 内部局部函数（doPull/levelUp/runTowerFloor） | 业务逻辑无法单元测试、无法后台预取 | `GachaScreen.kt:161`、`ProgressionScreen.kt:108`、`TowerScreen.kt:183` |
| P1-11 | 跨切面 | **测试盲区**：`ArenaService`/`PvEService`/`SocialService` 零专用测试 | R5 核心经济路径无断言保护 | `app/src/test/...` 仅 EventEquipmentWiringTest、EconomyGuardRegressionTest 间接覆盖 |

### 2.3 P2（改进建议）

| # | 维度 | 问题 |
|---|---|---|
| P2-1 | UI | 四态骨架（加载/错误/空/刷新）未抽公共组件，写结果 `when(outcome)` 在多屏幕复制 |
| P2-2 | UI | 业务枚举字符串硬编码（稀有度 4/3/2/1、世界名 Shinwa、货币名"星尘"等） |
| P2-3 | UI | 导航回调面过宽（HomeScreen 12 个 `onOpenXxx` lambda） |
| P2-4 | services | 两套事务模板并存（`transaction` 自动加锁 vs `transactionLocked` + `withLock` 手动） |
| P2-5 | data | `SaveManager.migrate()` 为 stub；`version` 默认 1，升版本无真实迁移 |
| P2-6 | data | `sync_gamecontent.py` 无 CI 校验任务，未匹配项仅 print 不 fail |
| P2-7 | services | 5 个老服务构造签名无 `rng` 参数（Gacha/Economy/Progression/Meta/Tower），seed 仅经 core 间接注入 |
| P2-8 | docs | AGENTS.md 未登记 R5 六大系统、未登记 `AffinityFormulas` 位置（其 KDoc 已说明"好感数据存 app 存档，公式留 app"为有意设计） |

---

## 3. 推荐方案 A：纯架构清理（不接线、不重构 UI、不破坏序列化）

### 设计原则

1. **行为不变**：所有改动通过现有 292 单测 + assembleDebug 验证，无语义变化。
2. **可分阶段**：8 个原子步骤，每步独立验证、独立 commit，回滚成本低。
3. **不夹带产品决策**：死功能接线（I10/I12）、UI 重构、充值合规均不在本次。
4. **不破坏序列化**：不动 `@SerialName`、不动 SaveData 顶层字段；SaveData 子结构封装作为可选高阶项单独评估。

### 执行计划（按依赖顺序，自底向上）

| 阶段 | 改动 | 影响文件 | 风险 | 验证 |
|---|---|---|---|---|
| **S1** ServiceCore 业务规则归位 | `calculateEquipmentStats`/`calculateSetBonuses` 移至 EquipmentService；`addAffinityDelta` 移至 ProgressionService（或下沉 shared/domain/affinity）；core 保留事务模板/EventBus 通道/转发层 | ServiceCore.kt(-150 行)、EquipmentService.kt、ProgressionService.kt | 中 | 292 单测全绿 |
| **S2** GameService 门面瘦化 | 内联方法转委托：`addCharacterAffinity`/`giftAffinity`/`getOwnedEquipments`/`getAllOwnedEquipments` 改为转发到对应服务；更新 KDoc：14 个服务（不是 5 个）+ 服务清单表格 | GameService.kt | 低 | 292 单测全绿 |
| **S3** 魔法数字收口 | 新建 `shared/.../domain/equipment/EquipmentFormulas.kt`、`shared/.../domain/social/SocialFormulas.kt`、`shared/.../domain/monetization/MonetizationFormulas.kt`、`shared/.../domain/pve/PvEFormulas.kt`、`shared/.../domain/mission/DailyMissionFormulas.kt`；服务引用更新 | shared 5 个新文件 + 5 个服务 | 中 | 新增 5 套公式单测 |
| **S4** 命名收口（渐进式） | "发放"动词统一为 `grant`：`addSoft→grantSoft`、`addHard→grantHard`、`addExp→grantExp`、`addFriend→addFriendSocial`（保留，社交语境合理）、`addBattlePassExp→grantBattlePassExp`、`addCharacterAffinity→grantAffinity`；ServiceCore 底层 delta 原语（`addItemDelta`/`addCurrencyDelta`/`addAffinityDelta`）保留 `add` 名（语境为"原子增量"非"业务发放"）；保留 deprecated typealias 兼容期 | 全工程 rename + UI 调用点同步 | 中 | grep + 单测 |
| **S5** EventBus 隐患标记 + 设计文档化 | 在 `ServiceCore.transaction` 的 `dispatch()` 调用处加 KDoc 警告："订阅者 handler 内禁止调用任意 service 写操作（writeMutex 不可重入）"；评估是否将 `withWriteLock`（已存在方案）接线替代——若接线风险高则仅加注释，留作 M2 根治 backlog | ServiceCore.kt 注释 | 极低 | 静态 |
| **S6** Arena/PvE/Social 冒烟单测 | 为 `ArenaService`、`PvEService`、`SocialService` 各加最小冒烟测试（构造 + 主要写操作不崩 + WriteOutcome 三态分发），不要求全覆盖；标注"非业务正确性断言，仅作回归网" | `app/src/test/...` 新增 3 测试文件 | 低 | 292→295+ 全绿 |
| **S7** SaveData 子结构封装（可选高阶） | 将 40+ 字段按业务域分组为内嵌 data class（如 `SaveData.Gacha`、`SaveData.Equipment`、`SaveData.Social`…）；@Serializable 内嵌结构对 JSON 透明（保持 PascalCase 键名不变）；sanitize/createDefault 同步拆分 | SaveData.kt 大重构 | 高 | 需先 PoC 验证 kotlinx.serialization 内嵌 data class 对 PascalCase JSON 兼容性 |
| **S8** AGENTS.md 文档同步 | 登记 R5 六大系统（Arena/PvE/Social/Monetization/Equipment/EventRhythm/DailyMission/Inspection）；更新 GameService 服务数 5→14；登记新增 5 个 Formulas；登记 S4 命名约定；登记 S5 EventBus 隐患注释 | AGENTS.md | 极低 | 静态 |

### 验证策略

每阶段执行后强制验证（按 AGENTS.md 三步法）：

```bash
# 1. 编译
./gradlew.bat :app:compileDebugKotlin

# 2. 单测全绿（基线 292 → 阶段性递增）
./gradlew.bat :app:testDebugUnitTest

# 3. assembleDebug
./gradlew.bat :app:assembleDebug
```

任一阶段验证失败立即停手、回滚、根因调查（按 systematic-debugging 流程，不以猜测为根因）。

### 关于 Edit 工具 phantom edit 铁律

MEMORY.md 已记录：「同一文件多条 Edit 偶发『报 Successfully edited 但磁盘未落盘』」。本工程改动密集，必须遵守：
- 同文件多处改动**逐个串行 Edit（勿并行）**
- 每处改后必须 Grep/Read 复核落盘
- 勿信「Successfully edited」字样

---

## 4. 不在本次范围（独立工作流）

| 项 | 原因 | 后续入口 |
|---|---|---|
| 死功能接线（PvE/Social/Equipment） | 属 I10/I12 设计文档，产品决策（活动代币体系/激活路径/装备发放来源） | `docs/plans/2026-09-04-dead-feature-wiring-design.md` |
| UI ViewModel 抽象 | 影响 14+ 文件，独立 UI 重构工作流 | 独立 /brainstorming |
| 巨型屏幕拆分 | 影响 15 个屏幕，独立 UI 重构 | 独立 /brainstorming |
| 充值合规 | 待产品裁定（单机不上架 / 上架计划） | 待用户拍板 |
| ServiceCore 替身可注入 | 涉及构造签名重构（影响全服务） | 独立工作流，可结合 P2-7 老服务 rng 注入一并做 |
| `migrate()` 实装 | 当前无版本升级需求（v1 仍是初始版本） | 触发条件：首次升 version |

---

## 5. 待用户决策点

**D1：死功能层（PvE/Social/Equipment 服务）如何处理？**
- 选项 A（推荐）：留作 backlog，本次不动（不删不接线），仅加 S6 冒烟单测防回归
- 选项 B：删除（违反 I10/I12 设计，可能误伤未来接线计划）
- 选项 C：按 I10/I12 接线（爆炸工作量，不属"架构优化"范围）

**D2：SaveData 子结构封装（S7）是否纳入本次？**
- 选项 A（推荐）：不纳入，作为独立后续工作流（先做 PoC 验证序列化兼容性）
- 选项 B：纳入本次（高风险，需先 PoC）
- 选项 C：仅做 PoC，视结果决定是否纳入

**D3：命名收口（S4）激进程度？**
- 选项 A（推荐）：渐进式（rename + 保留 deprecated typealias 兼容期，下次大版本删除）
- 选项 B：激进全量 rename（不留 alias，可能漏改 UI 调用点）
- 选项 C：不收口（命名分裂保留现状）

**D4：EventBus.dispatch 临界区隐患处理深度？**
- 选项 A（推荐）：仅加 KDoc 警告 + 留作 M2 根治 backlog（S5 现状）
- 选项 B：本次接线 `withWriteLock` 出临界区再 dispatch（影响所有 transaction 调用路径，回归风险高）
- 选项 C：什么都不做

---

## 6. 推荐执行顺序

若用户采纳推荐方案（D1=A、D2=A、D3=A、D4=A）：

**执行 S1 → S2 → S3 → S4 → S5 → S6 → S8**（S7 跳过）

每阶段独立 commit，提交信息格式：`refactor(arch): S<n> <改动摘要>`。

最终交付：
- 全 8 阶段 commit 历史
- 单测基线 292 → 295+ 全绿
- assembleDebug 成功
- 本设计文档勾选完成项
- AGENTS.md 同步更新

预计代码改动量：~600 行新增（Formulas + 单测）、~200 行删除/迁移、~150 行 KDoc 更新。
