# Research Brief — Milan App 所有页面 UI 调研

## Refined question
系统梳理 Milan（Kotlin/Compose 抽卡游戏）Android App 的**全部页面 UI**：导航架构、每个 Screen 的布局结构/交互/视觉、共享组件与设计系统，并给出可导航的完整清单。

## Scope
- In: `MilanKotlin/app/src/main/java/com/milan/game/ui/**` 全部 Screen、导航、主题、组件、效果层；Glance 桌面小部件；与 UI 相关的 ViewModel 状态暴露面。
- Out: 领域层公式、存档格式、构建脚本、非 UI 的 service 实现细节（仅在影响 UI 状态时提及）。

## Assumptions
- 「所有页面」= Navigation 图上可达的 Screen + 底部 5 tab + 子路由覆盖层，不包括测试源码里的 mock。
- 以源码为准；不跑模拟器截图（环境无设备时），结构与交互从 Compose 源码读出。
- 深度：deep（全面清单 + 设计系统）。

## Depth mode
deep

## Angles
1. 导航架构与完整路由图
2. 主 Tab 页（Home / Gacha / Deck / Shop / Settings）
3. 角色链路（列表 / 详情 / 养成 / 面板组件）
4. 系统与活动页（Tower / Arena / Story / Achievement / Affinity / BattlePass / Event / Collection / PullHistory）
5. 战斗与结算 UI
6. 设计系统（主题 / 元素色 / 组件库 / 特效 / 动效）
7. 覆盖层与共享反馈（Dialog / Overlay / Feedback / 粒子 / 小部件）

## Date
2026-09-10
