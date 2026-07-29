# Milan UI 重新设计实现计划

> 基于规格：`docs/superpowers/specs/2026-07-29-ui-redesign-design.md`

## 任务 1：宇宙星穹视觉基础

**文件：**
- 修改：`MauiMilan/UI/AppTheme.cs`

**步骤：**
1. 在 `AppTheme` 静态类中新增宇宙星穹色板常量：
   - `CosmicBgDeep = #0d0221`
   - `CosmicBgLight = #1a0533`
   - `CosmicPrimary = #7c4dff`
   - `CosmicPrimarySoft = #b388ff`
   - `CosmicGold = #ffd75a`
   - `CosmicTextPrimary = #ffffff`
   - `CosmicTextSecondary = #b8b8d0`
   - `CosmicTextMuted = #7a7a99`
2. 保留原有 `AppTheme` 颜色作为兼容性别名（避免其他文件编译失败）

**验证：** 编译通过，无错误。

---

## 任务 2：通用背景组件 CosmicBackground

**文件：**
- 新增：`MauiMilan/UI/CosmicBackground.cs`

**步骤：**
1. 创建 `CosmicBackground : FrameLayout` 类
2. `OnDraw` 中绘制：
   - 全屏深紫径向渐变（`CosmicBgDeep` → `CosmicBgLight`）
   - 12-15 个缓慢漂移的星光粒子（`Paint` + `Canvas.DrawCircle`）
   - 粒子从底部上升，循环
3. 使用 `Invalidate()` 持续动画
4. 提供 `Start()` / `Stop()` 控制动画生命周期

**验证：** 背景显示深紫渐变 + 浮动粒子。

---

## 任务 3：发光按钮 GlowButton

**文件：**
- 新增：`MauiMilan/UI/GlowButton.cs`

**步骤：**
1. 创建 `GlowButton : Button` 类
2. 构造函数参数：`text, fillColor, textColor`
3. 背景使用 `UI.RoundRect` + `SetShadowLayer` 实现发光边框
4. 重写 `OnTouchEvent`：按下时缩放 0.95 + 增强光晕
5. 提供静态工厂 `GlowButton.Create(context, text, color)` 简化调用

**验证：** 按钮显示发光边框，按下有反馈。

---

## 任务 4：结果卡牌 ResultCard

**文件：**
- 新增：`MauiMilan/UI/ResultCard.cs`

**步骤：**
1. 创建 `ResultCard : FrameLayout` 类
2. 正面：角色立绘区域 + 名字 + 稀有度边框色
3. 背面：星空纹理（深紫 + 星点）
4. 翻转动画：`Animate().RotationYBy(180).SetDuration(400)`
5. 高稀有度（SSR/UR）添加 `SetShadowLayer` 发光边框
6. 构造函数参数：`PullResult result, CharacterDataEntry def`

**验证：** 卡牌显示背面纹理，点击翻转显示角色。

---

## 任务 5：首页重设计 — 聚焦式英雄区

**文件：**
- 修改：`MauiMilan/Activities/HomeActivity.cs`

**步骤：**
1. 根布局改为 `FrameLayout`，底层添加 `CosmicBackground`
2. 顶部栏：`MILAN` logo（星紫渐变 + 发光阴影）+ 星尘余额（金色 chip）
3. 中央：`ParticleView` glow 模式作为召唤光圈，持续旋转脉动
4. 三入口改为圆形图标按钮（抽卡/角色/图鉴），放射状排列在光圈下方
5. 底部：已拥有角色数
6. 入口按钮点击时触发扩散光环动画
7. 修复 `OnResume` 只刷新数据不重建布局（同之前 GachaActivity 的修复）

**验证：** 首页显示发光 logo + 呼吸光圈 + 三入口圆形按钮。

---

## 任务 6：抽卡重设计 — 裂缝召唤

**文件：**
- 修改：`MauiMilan/Activities/GachaActivity.cs`

**步骤：**
1. 根布局添加 `CosmicBackground`
2. 顶部：返回按钮 + 卡池名称 + 保底计数
3. 卡池信息条：UR/SSR/SR/R 概率展示
4. 中央召唤区：`ParticleView` 光圈 + 单抽/十连按钮
5. 召唤动画序列：
   - 点击按钮 → 光圈加速 + 亮度提升
   - 全屏白色 flash（`View` 覆盖，alpha 动画 0→180→0，0.3s）
   - 粒子爆发（spark 模式，30 粒子径向飞散）
   - 结果卡牌从中心弹出（scale 0→1，staggered 0.05s/张）
6. 结果展示：`ResultCard` 2×5 网格，高稀有度发光边框
7. 货币反馈：抽卡后星尘数字更新

**验证：** 点击十连 → 闪光 → 粒子爆发 → 10 张卡牌弹出。

---

## 任务 7：检视界面 — 展示厅

**文件：**
- 新增：`MauiMilan/Activities/InspectionActivity.cs`

**步骤：**
1. 根布局添加 `CosmicBackground`
2. 顶部：返回按钮 + 角色名
3. 中央展示区：`FullBodyCharacter` 角色立绘 + 呼吸式光晕
4. 手势交互：
   - `OnTouchListener` 单指拖动 → 角色旋转（rotationY）
   - 双指捏合 → 缩放（scaleX/scaleY）
5. 底部操作栏：旋转/缩放/技能/信息 四个 `GlowButton`
6. 技能按钮点击 → 触发 `FullBodyCharacter.TriggerBurst()`
7. 标签页：立绘/天赋/故事/羁绊（`HorizontalScrollView` + 内容区）
8. 从首页"角色"入口或角色详情页可跳转进入

**验证：** 角色可旋转缩放，技能按钮触发 burst 动画。

---

## 任务 8：构建验证

**步骤：**
1. 运行 `dotnet build MauiMilan/MauiMilan.csproj -c Release`
2. 确认 0 错误
3. 确认 APK 生成

**验证：** 构建成功，APK 可安装。

---

## 依赖关系

```
任务1 (色板) → 任务2,3,4 (组件) → 任务5,6,7 (界面) → 任务8 (验证)
```

任务 2/3/4 可并行，任务 5/6/7 依赖 2/3/4 完成后串行或并行。
