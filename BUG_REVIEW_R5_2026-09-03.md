# MilanKotlin 第五轮 Bug 审查报告（R5）

> 日期：2026-09-03
> 性质：只读审查（未修改任何生产代码）
> 范围：2026-08-29 之后**未提交、从未被审查**的新代码——12 个新聚合服务、8 个新存档模型、4 个新 UI 包、战斗域扩展、门面转发层。
> 方法：编译验证 + 四路并行 Explore（服务层/UI 层/领域数据层/接线横切）+ 主代理逐条取证复核。
> 编译基线：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` BUILD SUCCESSFUL（无阻断项，审查为静态+接线取证）。

## 结论摘要

| 严重度 | 数量 | 说明 |
|---|---|---|
| Critical（经济崩溃/存档损坏/可刷） | 8 | 仅 1 条有生产调用点，其余为已暴露于门面的未接线接口 |
| Important（功能错误/死功能/数值错误） | 12 | 含系统性「跨日重置缺失」「锁外写」「回滚遗漏」 |
| Minor（一致性/API 污染） | 2 | — |
| 待复核 | 4 | 需内容数据接入后验证 |

**最重要结论（一条顶十条）**：2026-09 新增的 8 大系统（竞技场 / PvE 深渊 / 活动运营 / 月卡充值 / 装备 / 社交公会 / 360° 检视 / 策略战斗）中，**除「剧情」「每日任务」「通行证」「好感度」外，其余几乎全部没有 UI 调用点**——它们构成了一个「已暴露门面、写满业务逻辑、但玩家完全碰不到」的**死功能层**。更糟的是，这层里的经济写路径普遍缺校验（负数穿透、无余额预检、无领取门控、跨日重置缺失、回滚遗漏），一旦未来接线即成为经济永动机或刷币口。**当前玩家可触达的活路径中，仅通行证（S-A4 回滚遗漏）与剧情（S-A14 双份奖励）存在实质缺陷。**

---

## 一、Critical（8 条）

### R5-C1｜活动任务奖励可无限重复领取（经济永动机）
- **位置**：`EventRhythmService.kt:83-119`（`claimTaskReward`），已暴露于 `GameService.claimEventTaskReward:647`
- **机理**：唯一领取门槛是 `currentProgress >= task.target`（:90-91）；mutate（:106-109）只是把 progress 设回 target（本就 ≥ target），**全程无「已领取」标记**。连续 N 次调用净增 N×rewardAmount。
- **触发**：未来接入活动任务后，点击「领取」按钮重复点击即可刷星尘/钻石。
- **修复**：加独立 `claimedTask` 集合，校验与回滚都覆盖它。

### R5-C2｜活动商店兑换无余额校验 + 负数/溢出穿透
- **位置**：`EventRhythmService.kt:125-160`（`redeemShopItem`），门面 `GameService.redeemEventShopItem:651`
- **机理**：三条路径白拿星尘——①余额不足照扣（软货币可负，:145 无预检）；②`amount=-1` → `totalCost=-price` → `softCurrency -= (-price)` 加钱；③`price*amount` Int 溢出翻负后同②。
- **修复**：入口校验 `amount>0`、Long 算 totalCost 防溢出、锁内余额预检。

### R5-C3｜签到落盘失败钻石凭空增加（回滚遗漏）
- **位置**：`EventRhythmService.kt:172-198`（`signIn`），门面 `GameService.signIn:659`
- **机理**：mutate（:188-189）加 `hardCurrency += rewardAmount`，rollback（:191-193）只恢复 `signInProgress`，**不恢复 hardCurrency**。每次落盘失败重试都再白拿一次。
- **修复**：rollback 补 `hardCurrency = origHC`。

### R5-C4｜通行证奖励落盘失败不回滚货币（有生产调用点）
- **位置**：`MonetizationService.kt:164-189`（`claimBattlePassReward`），UI 调用点 `BattlePassScreen.kt:59`
- **机理**：mutate 加 `softCurrency += level*2000`（:176）与 premium 时 `hardCurrency += level*5`（:178-179）；rollback（:182-184）只恢复 `claimedBPRewards`。落盘失败时 UI 提示「保存失败」，但货币已到账。
- **附**：`level` 无下界校验（:166 `level > battlePassLevel` 对负数恒 false → 负 level 通过后 `+= 负数` 扣钱）。
- **修复**：rollback 恢复 origSoft/origHard；校验 `level >= 1`。

### R5-C5｜充值里程碑回滚遗漏 + 可按精确值无限刷
- **位置**：`MonetizationService.kt:241-262`（`claimChargeMilestone`），门面 `GameService.claimChargeMilestone:616`
- **机理**：①rollback（:255-257）只恢复 claimedChargeMilestones，不恢复 :253 加的 hardCurrency → 落盘失败钻石凭空增；②claimed 按精确值去重，`totalChargeAmount=600` 时可依次 claim 600,599,…,1，`Σ(k/100)≈1800` 星琼（6 元应得 60，刷 30 倍）。
- **修复**：里程碑改为固定档位白名单（复用 getChargeMilestones 的 6 档）+ 回滚补货币。

### R5-C6｜公会捐献负数金额无限加星尘
- **位置**：`SocialService.kt:272-296`（`donateToGuild`），门面 `GameService.donateToGuild:568`
- **机理**：`donateToGuild(-1000)` → `softCurrency < -1000` 恒 false → `softCurrency -= (-1000)` 加 1000（:284）。无 `amount>0` 校验。
- **修复**：入口拒绝 `amount<=0`。

### R5-C7｜装备强化负数经验刷星尘 + 升级经验不随级重算
- **位置**：`EquipmentService.kt:219-267`（`enhanceEquipment`），门面 `GameService.enhanceEquipment:358`
- **机理**：①`expPoints=-1` → `goldCost=-10` → `softCurrency < -10` 恒 false → `softCurrency -= (-10)` 加钱（:238-246）；②`expPoints≈2.1e8` 时 `*10` Int 溢出翻负同①；③:252 `expPerLevel` 在 while 外取一次，连升多级时每级扣同一值，升级经验不随级递增。
- **附**：`goldCost = expPoints * 10`（:238）就地写死，违反「EconomyFormulas 单一事实来源」红线。
- **修复**：入口校验 expPoints>0 + Long 算 goldCost；expPerLevel 移入循环内按当前 level 重取。

### R5-C8｜装备/其它服务用「mutate 内抛异常」表达业务拒绝 → 异常穿透闪退
- **位置**：`EquipmentService.kt:231,234,242,296,300,304,307,313,346,387`
- **机理**：余额不足/装备不存在/槽位不匹配等**正常业务拒绝**全在 mutate lambda 内 throw。`ServiceCore.transactionLocked`（ServiceCore.kt:167-177）对 mutate 异常语义是「回滚→留痕→**原样上抛**」，`core.transaction` 不再捕获 → 异常穿透到 UI 协程（调用点无 try-catch 惯例）→ 闪退。违反「先校验后改内存」范式。
- **修复**：校验全部移到事务前预算阶段返回 Rejected；mutate 内零 throw。

---

## 二、Important（12 条）

### R5-I1｜策略战斗结算无 floor 上界/编队/胜利校验（接线后升 Critical）
- **位置**：`TowerService.kt:385-490`（`settleStrategicBattle`）
- **机理**：对比 `runTowerFloor`（:108 有 `floor in 1..towerMaxFloor()`、:114 空编队拒绝），此方法三者皆无：①`floor=1000` 即 `towerBestFloor=1000`，此后 1..999 层「刷新纪录」恒 false → 爬塔奖励/返票永久锁死（存档级损坏）；②巨值 floor 触发 `(oldBest+1..newBest).sumOf{...}` 巨量循环 ANR 且 sumOf(Int) 溢出；③`victory` 由调用方断言、不重放战斗 → 接线后可无限「胜利」推进刷奖励；④胜利不发好感（`runTowerFloor:220-224` 有 BATTLE_WIN_AFFINITY），两结算路径口径分裂。
- **修复**：复用 runTowerFloor 三道校验 + 好感发放；victory 改服务端重放判定。

### R5-I2｜竞技场/PvE/社交的「每日次数」无跨日重置 → 永久锁死
- **位置**：`ArenaService.kt:121`（attackCount≥5 永久拒绝）、`PvEService.kt:48`（challengeCount≥3）、`PvEService.kt:235-238`（challengeCounts≥3）、`SocialService.kt:105`（giftedFriends≥20）
- **机理**：`lastRefreshTime`/`lastResetTime` 字段定义后全项目零读写（grep 验证）；`SaveData.sanitize` 只 `take(20)` 不清空。正确范式见 `DailyMissionService.getData():44-54`（currentDay 跨日重置）。
- **后果**：竞技场 5 次、深渊 3 次、日常副本各 3 次、赠体力 20 次后**对玩家永久关闭**。
- **修复**：仿 DailyMissionService 用 `core.today()` 跨日重置。

### R5-I3｜每日任务 5/15 模板无生产上报点 → 死任务 + 活跃度封顶被压低
- **位置**：`DailyMissionService.kt:20-37`（模板池）
- **机理**：模板池 15 个里 `SPEND_SOFT_CURRENCY`(2)、`CHECK_IN`、`FRIEND_GIFT`、`CLAIM_AFFINITY` 共 5 类全项目无 reportProgress 生产点（grep 验证：仅 6 类有生产者）。每日随机 6 个抽中即永不完成。极端日 6 个全抽死任务 → 活跃度恒 0，30000 星尘宝箱不可达。
- **修复**：补 5 类生产点，或从模板池剔除。

### R5-I4｜每日任务 getData/reportProgress 锁外写存档 + 跨日重置不落盘
- **位置**：`DailyMissionService.kt:40-56, 78-96`
- **机理**：①getData 的跨日重置（改 currentDay/activityPoints/missionProgress/completedMissions + 重新生成）全在 writeMutex 外且不落盘，`getTodayMissions`/`getDailyMissionData` 等只读 API 也触发；②`generateDailyMissions` 用 `missionTemplates.shuffled(rng)`（:60）消耗共享主 rng → 跨日首次打开每日任务页会**打乱抽卡随机序列**；③reportProgress 在聚合服务**返回后（锁外）**调用（GameService.kt:218/258/301/361/454/698），聚合服务 SaveFailed 回滚后每日任务进度已推进且永不回滚。
- **修复**：重置/上报纳入事务或持锁+落盘；每日任务用独立 rng。

### R5-I5｜新服务群「锁外快照 + 锁内写」竞态 + 只读 API 无锁写存档
- **位置**（模式）：`ArenaService.kt:31-35/117-145`、`PvEService.kt:31-35/219-262`、`SocialService.kt:22-26/40-55`、`MonetizationService.kt:20-24/29-37`、`EventRhythmService.kt:22-26/53-80`、`InspectionService.kt:23-27`、`StoryService.kt:21-25`、`DailyMissionService.kt:40-43`
- **机理**：①`getXxxData()` 首次调用在锁外写 `core.saveData.xxx = it`（与临界区写并发，同 R4 修掉的 prereqCache 类缺陷，但这是**写路径**，比已知 P2「只读撕裂读」更重）；②两次并发挑战都在锁外读 attackCount=4 双双放行 → 次数超限；③A 事务 SaveFailed 的 rollback 用旧快照，会抹掉并发成功的 B 事务已落盘改动。
- **另**：`ArenaService.kt:169-183` 战报追加写在 **onCommit**（save 成功后）→ 本事务落盘不含该记录、列表无上限、rollback 不可达。
- **修复**：getXxxData 创建写入移入事务内；快照读取移入 withLock；战报追加移入 mutate。

### R5-I6｜检视计数/动作解锁零持久化
- **位置**：`InspectionService.kt:32-36`（recordInspection）、`195-200`（unlockAction）
- **机理**：直接改 `data.inspectionCounts/unlockedActions`，无事务无落盘——除非之后恰好有其它成功保存，否则重启即丢；且锁外写共享存档。对照同文件 `savePhoto:118-139` 走 core.transaction 正常。
- **修复**：改造为 suspend + transaction。

### R5-I7｜剧情关卡双份奖励 + 完成路径不校验前置
- **位置**：`StoryService.kt:72-123`（completeStage 内 :92-98 直接发奖）、`:128-159`（claimReward 再发一次）；`:72-76` 无 canEnterStage 校验（canEnterStage :52-56 定义了却无人调用）
- **机理**：completeStoryStage 已接线（MilanNavHost.kt:287,293）；claimStoryReward 无 UI 调用点，一旦接线同一关两份奖。completeStage 可对任意 stageId 直接调用（DialogueRoute 参数）跳过前置直领奖励。
- **修复**：completeStage 只标记不发奖（领奖统一走 claimReward）；complete 前校验 canEnterStage。

### R5-I8｜充值里程碑奖励类型被无视（一律发硬通货）
- **位置**：`MonetizationService.kt:247-253`（claimChargeMilestone）
- **机理**：`getChargeMilestones` 定义了 6 档含 `EQUIPMENT`/`SKIN`/`CHARACTER` 类型（:275-283），但 claim 逻辑 `reward = amountCents / 100` 一律发硬通货，里程碑奖励类型完全没实现。
- **修复**：按 milestone 定义分发实际奖励类型。

### R5-I9｜通行证经验零生产调用点 → 通行证永远 0 级（付费黑洞）
- **位置**：`MonetizationService.kt:134`（addBattlePassExp），门面 `GameService.addBattlePassExp:600`
- **机理**：grep 全项目 addBattlePassExp 仅 GameService 转发 + MonetizationService 定义，**零 UI/服务调用点**。通行证等级永远 0，买豪华版（680💎）后永远无法领取任何等级奖励。
- **修复**：接线到每日任务/爬塔/剧情等完成事件。

### R5-I10｜月卡/充值/竞技场/PvE/装备/社交 六大系统零 UI 调用（死功能层）
- **位置**：门面 GameService 竞技场(:440-463)/PvE(:467-491)/活动(:629-666)/月卡充值(:581-625)/装备写操作(:350-376)/社交(:530-577)
- **机理**：grep 验证 UI 层仅调用 `purchaseBattlePass`/`claimBattlePassReward`（通行证）、`giftAffinity`/`addCharacterAffinity`（好感）、`completeStoryStage`/`getStoryChapters`（剧情）、`getTodayMissions`/`claimActivityChest`（每日任务）。竞技场/PvE/活动/月卡/充值/装备强化穿戴/社交公会全部无调用点。
- **后果**：大量已写好的业务逻辑（含 C1-C7 的经济漏洞）玩家无法触发，但也意味着「功能宣称存在实则不可用」。
- **修复**：逐系统补齐导航入口 + 屏幕。

### R5-I11｜货币加法普遍缺 Int 溢出拦截 + 零散回滚遗漏
- **位置**：`MonetizationService.kt:76-77/176/179/189/222/253`、`SocialService.kt:151`、`PvEService.kt:270`、`StoryService.kt:94-95/145-146`、`EventRhythmService.kt:101-103/145/189`、`DailyMissionService.kt:114-117`
- **机理**：裸 `+=` 无 Long 预检（对照正确实现 `EconomyService.applyCurrencyDelta`、`TowerService:166-171`）。`SocialService.createGuild:256` 置 `guildContributions=0` 但 rollback 不恢复（SaveFailed 后贡献值永久清零）；`activateMonthlyCard`/`purchaseBattlePass` 的 cost 无 `>0` 校验。
- **修复**：统一抽 core 层 `addCurrencyDelta`（带 Long 预检）供所有服务复用。

### R5-I12｜活动系统整体无激活入口（死功能）
- **位置**：`EventRhythmSaveData.activeEvents`（`EventRhythmSaveData.kt:17`）
- **机理**：grep 全项目 `activeEvents =` 仅 `SaveData.kt:382`（迁移过滤），**无任何创建/激活活动的代码**。`getDefaultEventDefinitions`（EventRhythmService.kt:209）定义了模板但从不实例化为 activeEvents。
- **后果**：签到/活动任务/活动商店全部 Rejected，玩家看不到任何活动。同时使 C1-C3 漏洞处于休眠状态。
- **修复**：实现活动实例化/激活/过期逻辑。

---

## 三、Minor（2 条）

### R5-M1｜门面公开方法使用中文命名
- **位置**：`GameService.kt:451,471,486`（`挑战对手`/`挑战深渊关卡`/`挑战日常副本`）
- **机理**：公开 API 用中文方法名，破坏代码规范，未来接线/重构易错。
- **修复**：改英文命名（如 `challengeOpponent`/`challengeAbyssStage`/`challengeDailyDungeon`）。

### R5-M2｜门面在自身层写业务规则（违反 KDoc 契约）
- **位置**：`GameService.kt:215-221`（pull）、`:255-261`（levelUp）、`:298-304`（runTowerFloor）、`:358-364`（enhanceEquipment）、`:451-457`（挑战对手）、`:695-701`（completeStoryStage）
- **机理**：GameService 的 KDoc 明确「本类不含业务规则，只转发」，但上述方法在转发后追加 `dailyMissionService.reportProgress(...)` 判断逻辑（`if (result is Success)`）。聚合服务层（dailyMissionService）被门面反向调用，破坏了「门面→服务」单向依赖。
- **修复**：把 reportProgress 上移为服务内部职责，或在门面用更中立的方式转发。

---

## 四、待复核（4 条，需内容数据接入后验证）

- **T1**：`EquipmentService.generateSubStats:105-106` `rng.nextInt(totalWeight)` 若 subStatPool 全 weight=0 → IllegalArgumentException。当前 5 个兜底模板权重均>0，现网不可触发。
- **T2**：`ServiceCore.kt:497-498` JSON 路径 pools 全被过滤时用 `GameContent.buildPools(characters)` 重建，`pool_flame` 条目硬编码 `char_sr_taotie`——若 JSON 角色集不含 taotie 则与 data.json 逐条一致性断言失配。
- **T3**：`EquipmentService.generateEquipmentId:132-134` 同毫秒批量发放 1/10000 碰撞率（装备 ID 重复让 firstOrNull 定位错对象）。
- **T4**：`ArenaService.buildOpponentTeam:195-197` `(teamPower*0.3)+rng.nextInt(-50,51)` 无 coerceAtLeast(0)，低 teamPower 可产负属性喂给 BattleSimulator（未验证模拟器对负属性容错）。

---

## 五、与历史轮次的关系

- **R3/R4 已修项全部复核有效**：爬塔星尘门控、addExp 接线、prereqCache 竞态、transactionLocked try 包裹、兜底池概率塌缩、立绘 scale——均未回退，未重复报告。
- **本轮新代码是主战场**：R3/R4 审的是老 5 服务（Gacha/Progression/Tower/Economy/Meta），其写路径范式已成熟。本轮 12 个新服务里，**只有 StoryService 的 completeStage 走完整事务范式**，其余新服务普遍存在「锁外快照」「回滚遗漏」「负数穿透」「跨日重置缺失」四大类问题，说明新服务的写路径是**未按 AGENTS.md 红线审查过的低质量代码**。

## 六、修复优先级建议

1. **P0（立即，活路径实弹）**：C4（通行证回滚）、I7（剧情双份奖励）、I9（通行证经验接线）——玩家当前即可触达。
2. **P1（接线前必改，否则未来引爆）**：C1-C3/C5-C8（活动/充值/公会/装备经济漏洞）、I2/I3/I4（跨日重置与每日任务）。
3. **P2（架构债）**：I1/I5/I11（并发与溢出收口）、I10/I12（死功能层补接线）、M1/M2（API 规范）。
4. **P3（待验证）**：T1-T4。
