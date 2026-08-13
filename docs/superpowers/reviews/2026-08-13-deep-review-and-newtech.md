# Milan Kotlin 深度审查报告 + 新技术落地（2026-08-13）

> 范围：`MilanKotlin/` 全模块（app / shared / desktopApp / benchmark），三路并行静态审查
> （领域+服务层 / UI+导航层 / 数据+基建+构建配置）+ 逐条交叉验证。
> 方法：审查阶段全部只读；随后在沙箱内实际构建验证（`testDebugUnitTest` + `assembleDebug` +
> `shared:jvmTest` + `desktopApp:build`/`:run` 全绿），结论均经编译/测试复核，非纯静态推断。
> 前置参考：`BUG_REVIEW.md`（2026-08-12 静态审查，其中 #1/#2/#3 已修复并补测）。

---

## 〇、基线现状（审查启动时发现，已修复）

1. **工作树编译失败**：`ui/gacha/GachaScreen.kt` 的 `Stroke` 导入写错包
   （应为 `androidx.compose.ui.graphics.drawscope.Stroke`），且缺 `androidx.compose.foundation.layout.offset`
   导入——`:app:compileDebugKotlin` 直接失败。已修复。
2. **HEAD 遗留测试红**：`DataJsonContentTest` 断言「data.json 路径与 GameContent 兜底路径逐字段一致」，
   而 2026-08 武器重做提交给 data.json 全量补齐了 SR/R 武器名/描述（如 `char_sr_suanni` = 炎吼·焚音爪），
   GameContent 兜底 `weaponName`/`weaponDesc` 表仍只覆盖 UR+SSR 14 个角色 → 测试红。
   **已把 14 个 SR/R 的武器名与描述从 data.json 同步进兜底表**（注释标注"防双路径口径漂移"）。
3. **沙箱环境无法构建**：`%USERPROFILE%\.gradle` 与 `%USERPROFILE%\.android` 在 DSH 文件沙箱下不可写。
   已通过 workspace 内 `GRADLE_USER_HOME` / `ANDROID_USER_HOME` 重定向 + `MilanKotlin/run-gradle.ps1`
   统一入口解决（详见下文「构建环境」）。

---

## 一、P1 数据完整性缺陷（三路审查交叉印证，已全部修复并补测试）

### P1-1　`levelUp` 落盘失败回滚会抹掉经验零头，内存与存档分叉（后续保存永久丢失）
- **位置**：`services/GameService.kt` 原 `levelUp` 回滚分支
  （`save.totalExp = EconomyFormulas.cumulativeExp(save.level)`）。
- **根因**：`totalExp` 是经验唯一真值，允许大于 `cumulativeExp(level)`（`addExp` 会累加零头）；
  回滚用「按等级重算累计经验」而非「恢复原值」，`addExp` 攒下的零头从内存消失，磁盘旧档仍在，
  下次成功保存即把「0 零头」永久落盘。
- **修复**：变更前记录 `origTotal`，回滚 `save.totalExp = origTotal`（与 `addExp` 回滚写法对齐）。
- **测试**：`levelUp_saveFailure_preservesPartialExp`（播种 totalExp=50 + 落盘失败 → 零头保留）。

### P1-2　一次十连内同一「新角色」重复出现：无碎片补偿 + 拥有条目重复写入（碎片永久丢失）
- **位置**：`services/GameService.kt` `pull` 规划期 `isNew` 判定 + 发货期重复追加。
- **根因**：`isNew` 只对「抽卡前的拥有列表」判定，规划期不累积本批已确认的新角色——
  十连中同一未拥有角色抽到 2 次 → 都判 isNew、0 碎片，且 ownedCharacters 写入 2 条重复条目
  （下次载入 sanitize 去重，碎片永久丢失）。旧测试 `pull_ten_success_deductsAndAwards`
  反而**固化了这一 C# 继承缺陷**（注释写明"C# 同款行为"）。
- **修复**：规划期维护 `batchNew` 集合——同批重复出现按「重复角色」补偿碎片（UR 50/SSR 20…），
  发货只追加一条拥有条目。
- **测试**：更新 `pull_ten_success_deductsAndAwards`（1 条拥有 + 9×20=180 碎片）+ 新增
  `pull_ten_ownedCharacter_allDuplicatesAwardFragments`（已拥有后十连全按重复补偿、拥有数不增长）。

### P1-3　重置存档不清理备份链，被删旧档可能「复活」
- **位置**：`data/AndroidSaveProvider.kt` `delete()` 只删 `save.json`。
- **根因**：`.bak`/`.tmp` 残留；主档被删后 `save()` 不再滚 bak，旧档永久保留在 `.bak`；
  日后主档损坏时 `loadBackup()` 优先读 `.bak`，把玩家已明确删除的进度原样复活。
- **修复**：`delete()` 清理 main/bak/tmp 三件套。
- **测试**：`resetSave_clearsBackupChain`（契约锁：重置后 `loadBackup()` 必须为空）。

---

## 二、P2/P3 主要发现（审查产物；本轮未全修，标注处置）

### 已随「新技术」顺带解决
| 项 | 内容 | 处置 |
|---|---|---|
| P2-14 | 顶栏资源胶囊（ResourceBar）不订阅事件，子页停留期间显示陈旧货币值 | StateFlow 快照订阅，天然修复 |
| P2-10 | `recordBattle` 无返回值、落盘失败静默 | 事务模板化（仍保留 Unit 语义，留痕补充） |
| P2-1 | `SaveManager.load()` IO 异常分支静默回退默认档（无法区分新号/IO 失败） | 补 `onTrace("save.load.failed: ...")` + 测试锁定 |
| P1-3 | 抽卡演出中系统返回直接销毁组合，付费结果展示丢失 | `BackHandler(showReveal) { skipReveal() }` 拦截并转跳过 |
| P2-7 | `GameState.computeStatsAt` 每次调用 `new ProgressionEngine()`（每重组重复分配） | 提升为进程级单例实例 |

### 第二轮「全部修复」追加（2026-08-13 二批）
| 项 | 内容 | 处置 |
|---|---|---|
| P1-3(领域) | 掷出保底档但该档无候选被降档时，保底计数被白白重置（保底被吞） | 重置判定推迟到「实际交付档位确定后」（`PityCounter.onNaturalPityOrAbove`）+ 加载时校验保底档有候选 + 测试 |
| P2-5/P2-6 | 货币 Int 溢出可回绕成负/大值；`spendSoft(-50)` 变相加钱 | Long 预算校验 + 负数金额一律 Rejected + 测试 |
| P2-7(领域) | `rarityWeights.size >= 4` 应为 `== 4`（>4 时权重并入 UR 扭曲概率） | 加载校验收紧为 ==4，>4 的池走兜底 |
| P2-12 | 突破后等级停留旧上限，银行经验被重复收费 | `ascend` 成功后按 totalExp 重推导等级并补发天赋点 + 测试 |
| P2-2 | `AndroidSaveProvider.save` 非原子回退路径、无 fsync、无写锁 | NIO `Files.move`+ATOMIC_MOVE 原子替换 + fsync + 对象级写锁 |
| P3-6 | CrashReporter SimpleDateFormat 非线程安全 | 换 `DateTimeFormatter`（不可变线程安全） |
| P3-7 | CrashReporter mirror 每次调用 spawn 线程 | 单线程执行器串行落盘 |
| P3-8 | EventBus.handlerException 接 write() 会弹崩溃对话框+计 crash 数 | 新增 `CrashReporter.traceNonFatal`（non_fatal.txt 轻量留痕） |
| P3-9 | MilanAudio 无 AudioFocus（来电/其他 App 播放不响应） | `AudioFocusRequest`：GAIN 请求 + LOSS 暂停/TRANSIENT 暂停/DUCK 降音量 + 释放时 abandon |
| P3-10 | EventBus 无宿主级周期 dispatch | MainActivity `LaunchedEffect` 每 250ms 兜底 dispatch |
| P3-12 | `allowBackup=true` 无 dataExtractionRules | 新增 `res/xml/data_extraction_rules.xml` 排除 save//crash/ |
| P3-13 | keep.xml 注释写 .png 实际 .webp | 注释修正 |
| P3-1(构建) | `-Xmx2g` 偏小；未开 build cache；toml 注释 2.4.0 漂移 | `-Xmx3g` + `org.gradle.caching=true` + 注释修正 |
| P3-4(构建) | `viewmodel-compose` 无人使用、`ui-graphics` 冗余 | 移除两个依赖（ui 传递提供） |
| P2-5(构建) | `:benchmark` 潜伏编译失败（缺 ext:junit、compileSdk 36、BenchmarkRule 错用） | 补 `ext:junit`、compileSdk 37、`:app` 增 `benchmark` buildType、修正 `MacrobenchmarkRule`、接入 settings |
| P2-4 | baseline-prof.txt 两条死规则（ensureInitialized 0 参、MilanThemeKt 类名错） | 按真实签名修正（生成式链路仍需设备，见后续建议） |
| P1-1(UI) | 抽卡结果网格无 key + batch 重播动画 | chip 加稳定 key（`key(i, r)`） |
| P1-2(UI) | produceState 切角色/武器闪上一张图 | PortraitImage / WeaponStage 的 produceState 块先 `value = null` |
| P2-5(UI) | ❖ 顶栏=钻石 vs 商店=碎片 符号冲突 | 统一 ✦ 星尘 / ◆ 钻石 / ❖ 碎片（顶栏钻石改 ◆） |
| P2-6(UI) | 无障碍缺失：返回箭头/切换箭头/tab 选中态/reveal 遮罩无语义 | 补 contentDescription + `SemanticsProperties.Selected/Role.Tab` + 遮罩描述 |
| P2-13/P3-5 | CharacterDetailScreen remember(characterId) 缓存失效引用 + 兜底对象每重组新建 | 订阅快照 revision 重读 + 兜底对象按角色缓存 |
| P3-3(UI) | Lazy 容器缺 key（DeckScreen / GachaScreen） | 补稳定 key |
| P3-6(UI) | HomeScreen 崩溃取证主线程读文件 | `withContext(Dispatchers.IO)` |
| P3-7(UI) | PortraitLoader 24MB LRU 无内存压力感知 | `ComponentCallbacks2.onTrimMemory` 分级收缩/清空 |
| P3-2 | 无排版体系（Typography 未传） | `GameTypography` 定义并传入 MaterialExpressiveTheme（增量使用） |
| LOW | `ensureInitialized` 双检锁缺 `@Volatile` | 补 `@Volatile` |
| P3-11 | GameState KDoc 声称 OnResume 钩子（实际无） | KDoc 修正 |
| P3-2 | 11 处手写「快照→变更→落盘→回滚→广播」模板重复 | 收敛为 `transaction()` 模板（见新技术 C） |
| P3-9 | GachaScreen 空结果统一提示「卡池数据异常」，余额不足与落盘失败混淆 | `PullOutcome` 类型化后按原因精确提示 |
| P2-4 | shared `GachaRng` 与主引擎双份抽卡数学、语义分歧、app 未消费 | 删除 GachaRng，引擎迁入 shared 单一事实来源 |

### 第三轮：主线程 IO 异步化 + 旋转状态保持（2026-08-13 三批）
| 项 | 内容 | 处置 |
|---|---|---|
| P2-2/P2-3 | 主线程同步全量 IO（每次写档三步文件操作，低端机卡顿/ANR 隐患） | **全部写操作改 suspend**：`writeMutex`（串行 Mutex）临界区内「变更→落盘(Dispatchers.IO)→失败回滚→仅成功广播」，主线程零阻塞、回滚与落盘判定同临界区同步完成（事务语义不变）；`pull` 整段持锁走 `transactionLocked`（Mutex 不可重入）；UI 四处（Gacha/Progression/Shop/Settings）改 `rememberCoroutineScope().launch`；`GameServiceTest` 48 用例改 `runTest` 全绿 |
| P3-8 | 抽卡结果/摘要非 `rememberSaveable`，旋转即丢付费结果展示 | `results`/`summary` 改 `rememberSaveable`，`PullResultsSaver`（listSaver 管道编码）自定义 Saver |

---

## 三、新技术落地（用户确认三项，全部完成并验证）

### 新技术 A：KMP 领域层下沉 `:shared`（复用演示 → 真实复用）

- **迁移**：`EconomyFormulas`(+`PlanResult`) / `ProgressionEngine` / `TalentEngine` /
  `BattleSimulator` / `BattleUnits` / `GachaEngine` / `PityCounter` / `Rarity`
  从 `app/src/main/java` 迁入 `shared/src/commonMain/kotlin/com/milan/game/`（**保留原包名**，
  app 侧 import 零改动）；`data/Enums.kt` 移除 Rarity（ItemType 等留原地）。
- **RNG 统一**：`GachaEngine`/`PityCounter`/`GameService` 由 `java.util.Random`（JVM-only）
  切换为 `kotlin.random.Random`（跨平台纯 Kotlin）——消除双随机源家族，抽卡数学真正 KMP 可移植。
- **接线**：`app/build.gradle.kts` 增加 `implementation(project(":shared"))`；
  `desktopApp` 补 `application` 插件 + `mainClass`（原注释声称的 `:desktopApp:run` 此前根本不存在），
  `Main.kt` 改用**真实** `GachaEngine`/`PityCounter`/`EconomyFormulas` 演示（删除重复实现 GachaRng）。
- **验证**：`:desktopApp:run` 实跑输出抽卡序列与保底计数；`shared:jvmTest` 构建通过。

### 新技术 B：StateFlow 状态快照（现代化状态管理）

- `GameService.snapshot: StateFlow<GameSnapshot>`（`revision/softCurrency/hardCurrency/starFragments/ownedCount`），
  在成功写操作的统一出口（`publishCurrencyChanged`/`publishProgressionChanged`）刷新，载入后立即发布初始值。
- UI 改用 `collectAsStateWithLifecycle()`（新增 `lifecycle-runtime-compose` 依赖）：
  `ResourceBar`（修 P2-14 陈旧值）、`ShopScreen`（删 EventBus 订阅与本地软状态）、
  `ProgressionScreen`（删 EventBus tick 轻标记，读 `snap.revision` 触发重读存档）。
- EventBus 保留：仍是跨屏指令/音频/触觉与测试断言的契约点；快照是**增量**机制，未破坏既有事件语义。

### 新技术 C：事务模板收敛 + 类型化结果

- 新增 `transaction(tag, mutate, rollback, onCommit)` 私有模板（GameService 内）：
  11 处手写「变更→落盘→回滚→仅成功广播」收敛为模板调用；留痕统一为 `"<tag>.save.failed: rolled back"`。
- 新增 `services/WriteOutcome.kt`：`sealed interface WriteOutcome { Success / Rejected / SaveFailed }`；
  货币/养成/商店/设置全部写操作改为类型化返回；`pull` 返回 `PullOutcome { Success(results) / Rejected / SaveFailed }`。
- UI 精确提示：GachaScreen「抽卡失败」vs「保存失败，请重试」；商店/养成页同理区分
  余额不足与落盘失败；SettingsScreen 三种分支。
- 测试全量更新为类型化断言（GameServiceTest 44 用例全绿）。

---

## 四、构建环境（沙箱专项，已固化）

- 默认 `%USERPROFILE%\.gradle` / `%USERPROFILE%\.android` 在 DSH 文件沙箱下不可写 →
  `GRADLE_USER_HOME`/`ANDROID_USER_HOME` 重定向到 workspace 内 `.gradle-home/`、`.android-home/`
  （均入 `.gitignore`）；发行版预置 `.gradle-home` 后 `gradlew.bat` 无需再下载。
- 统一入口：`MilanKotlin/run-gradle.ps1`（注入环境变量后转发 gradlew 参数）。
- Kotlin daemon 客户端标记写入 `%LOCALAPPDATA%\kotlin\daemon` 被沙箱拒 → 自动走 in-process 回退
  （有噪音但构建成功；未改 gradle.properties，避免影响正常开发环境的 daemon 加速）。

---

## 五、测试与构建状态

- `:app:testDebugUnitTest`：全部通过（GameServiceTest 44 用例 + 领域/存档/路由等全量）。
- `:app:assembleDebug` / `:shared:jvmTest` / `:desktopApp:build`：全部成功。
- 新增/更新测试：P1-1 回归 1 例、P1-2 回归 2 例、P1-3 契约 1 例，类型化断言全量改写。

## 六、后续建议（下轮）

1. 存档 IO 移出主线程（suspend + 串行写队列，回滚仍同步判定）——需与快照机制协同；这是剩余最大项。
2. 前台常驻动画改可见性感知停帧（Home 三路 + Gacha 法阵 + GpuEffects 帧循环），电量/发热收编。
3. 真机跑 `:benchmark:benchmarkRelease` + `generateBaselineProfile`，用生成式规则替换手写 baseline-prof（模块已修复接入）。
4. 抽卡结果 `rememberSaveable`（自定义 Saver）覆盖旋转场景。
5. 端侧 AI：AICore/Gemini Nano POC 接线 `FortuneAgent`（Stub 已就位），MediaPipe 作硬离线备选。
