# 前端结构与视觉统一设计（P1+P2）

- 日期：2026-08-27
- 范围：MilanKotlin app UI 层；方案 A（结构抽取 + 视觉对齐 + 冲突点 token 收口）
- 来源：全量 UI 审查（Explore 代理）+ brainstorming 对齐（范围=方案 A；背景统一为三段渐变已确认）

## 背景

UI 层整体健康（无占位页、无 TODO 残留、颜色已 token 化、M3 Expressive 正确启用），但存在两类主要问题：

- **P1 结构重复**：详情页与养成页的 HeroRegion 近乎重复（`PageComponents.kt:5` 注释自认差异仅渐隐 150 vs 140dp）；「‹ 返 回」胶囊按钮逐字复制 3 处（CharacterDetailScreen.kt:284-297 / ProgressionScreen.kt:322 / PageComponents.kt:53-65）；页底渐变逐字重复 3 处（Detail:150 / Progression:197 / PageComponents:48），且与共享 `PageBackground`（UIComponents.kt:82，三段渐变）不一致。
- **P2 token 收口半途**：约 130 处 `fontSize=N.sp` 硬编码、散装圆角散布 16 文件；新旧页面 typography 双范式并存（如 CollectionScreen.kt:123,149 用 typography 而 :200 硬编码 22.sp）；Detail 私藏色未入 AppTheme（WoWGreen :492、武器舞台底 0xFF101018 :447、WoW 面板渐变 0xFF16101C/0xFF0B0712 :509）。

## 设计

### 1. 共享组件抽取（P1）

在 `ui/components/PageComponents.kt` 上扩展：

| 共享单元 | 收敛方式 |
|---|---|
| 页背景 | Detail/Progression 统一改用共享 `PageBackground`（三段渐变），删除全部私有渐变 |
| `BackCapsule` 返回胶囊按钮 | 保留 PageComponents 一份，Detail/Progression 两处删除并引用 |
| `SubPageHero` Hero 区 | 合并 Detail:213 与 Progression:282 为一个组件，`fadeHeight` 参数化；保留共享元素过渡（SharedTransition）的 Modifier 传递，立绘跨页动画不受影响 |

### 2. 视觉 token 收口（P2，仅冲突点）

- 同文件双范式混用处改用 `GameTypography`（预计 20–30 处区段/标题级，正文级 100 余处不动，属后续方案 B 范畴）。
- Detail 私藏色入 `AppTheme`：WoWGreen、武器舞台底色、WoW 面板渐变双色。

### 3. 边界与红线

- 不碰 `domain/`、`data/`、`infrastructure/`、`EconomyFormulas`（领域纯净红线）。
- 武器舞台 Canvas、CyberStage 演出逻辑不动。
- 立绘缺失占位（首字 + 稀有度渐变）行为不变。
- `CharacterDetailScreen` 的 SharedTransition 接口签名保持兼容。

### 4. 视觉变化（已与用户确认）

Detail/Progression 背景由两段渐变统一为三段渐变（顶部更亮、过渡更柔和），与 Shop/Deck/Collection 等页一致。这是唯一用户可感知的观感变化。

## 验收

- 每步执行 `run-gradle.ps1 :app:assembleDebug` 与 `:app:testDebugUnitTest`（DSH 沙箱须用 run-gradle.ps1 重定向 GRADLE_USER_HOME / ANDROID_USER_HOME）。
- 分批交付：①共享组件抽取 → ②token 收口 → ③回归验证，每批给出文件/修改清单。
