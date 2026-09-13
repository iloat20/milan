# Research Brief — Milan 项目升级点全景调研

## Refined question

在 **2026-09-11** 时点，对 Milan（Kotlin/Compose 多模块 Android 抽卡游戏，`MilanKotlin/`）做一次「可升级点」全景扫描：技术栈、架构与模块边界、已知缺陷与经济安全、测试与质量门禁、性能与资源、玩法与产品完整度。输出一份可执行的升级路线报告（优先级 + 依据 + 风险），而不是泛泛的「建议」。

## Scope

### In
- `MilanKotlin/` 全部模块：`:app` `:core` `:data` `:shared` `:desktopApp` `:benchmark`
- `libs.versions.toml` / Gradle wrapper / AGP / Kotlin / Compose BOM 与 2026-09 官方最新稳定/推荐版本对比
- 架构：分层与模块边界、`ServiceCore.withWriteLock`、EventBus、`AppGraph`/`GameState`、ViewModel 抽取进度（`docs/plans/2026-09-08-p1-6-viewmodel-extraction.md`）
- 最近缺陷审查：`BUG_REVIEW.md` 与 R3–R7（尤其 R7 2026-09-10 未修 P0/P1）
- 测试：单测/Compose UI/Robolectric/Macrobenchmark 覆盖与 flakiness；`checkArchitecture` 红灯
- 性能：启动、立绘/武器解码、抽卡动画、伤害飘字、R8
- 产品/玩法：对照 `docs/superpowers/specs/`（story-campaign 2026-09-11、narrative-ui、gacha-ritual、experience-retention）与占位功能
- 工程体验：文档漂移（AGENTS.md/CLAUDE.md 与真实模块结构不一致）、脚本路径失效、本地无 CI

### Out
- Unity/.NET 旧版实现（仅 git 历史）
- 未上线的第三方 SDK 选型深度对比（仅点到为止）
- 具体数值平衡改动清单（只指出契约缺陷，不给数值表）
- 提交代码修复（本轮只出报告）

## Assumptions

- 调研环境为 Windows + DSH 沙箱；构建用 `pwsh -NoProfile -File .\run-gradle.ps1`。
- 「升级」同时包含：**技术栈升级**、**架构优雅化**、**质量/测试升级**、**产品完整度升级**、**工程流程升级**。
- 已有多轮 BUG_REVIEW 与架构优化 proposal；本报告应优先「去重、收敛、给出下一步优先级」，而非重做代码审查。
- 当前版本基线（读自 `libs.versions.toml`）：AGP 9.3.0 · Kotlin 2.4.10 · Compose BOM 2026.08.00 · serialization 1.11.0 · coroutines 1.11.0 · nav 2.9.8 · media3 1.11.0 · work 2.10.0 · robolectric 4.16.1 · compileSdk/targetSdk 37 · minSdk 29。

## Depth

**deep**（5–8 个并行角度，最多 2 轮补研）——项目体量大、跨架构/产品/性能多轴。

## Angles（并行研究，一轮 6 个）

| ID | 角色 | 主要问题 | 方法 |
|----|------|----------|------|
| F1 | 技术栈与工具链时效 | 当前 toml 各依赖相对 2026-09 官方最新差多少？升级收益/风险/必经路径（AGP/Kotlin/BOM/Gradle）？ | WebSearch + 本地 toml |
| F2 | 架构与模块边界 | core/data/shared/app/desktopApp 划分是否最优？ServiceCore 写锁/EventBus/AppGraph 有无结构性升级点？VM 抽取缺口？ | 本地代码 + 规划文档 |
| F3 | 缺陷债与经济安全 | R7 中未修 P0/P1 的真实状态；TOCTOU/回滚/溢出/重置锁死哪些仍是阻断升级的雷？ | 本地代码核验 R7 清单 |
| F4 | 测试与质量门禁 | 覆盖缺口、Robolectric 单例竞态、无设备 benchmark、checkArchitecture 红灯、缺 CI/lint 策略？业界 2026 Android 测试栈建议？ | 本地 + Web |
| F5 | 性能与资源 | 启动路径、资产加载、动画成本、R8/AAB、基准现状；对标 Compose 性能 2026 实践 | 本地 + Web |
| F6 | 玩法完整度与产品路线 | 占位/未接线功能；story-campaign / narrative-ui / mediapipe AI / 社交竞技场成熟度；CMP 桌面/iOS 扩展路径 | 本地 docs + 代码 |

## Open questions（写入报告而非阻塞）

- 是否有线上/分发渠道目标（AAB / Play Console）——影响 R8 与交付流水线建议优先级。
- 真机性能预算是否已有硬指标（设计文档曾有 performance targets）——影响 F5 排序。

## Date

2026-09-11（调研执行日）
