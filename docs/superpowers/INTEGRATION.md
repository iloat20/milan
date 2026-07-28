# Milan MVP 集成指南

代码实现已完成（任务 1-12）。下列步骤需要在 Unity Editor 中手动完成。

## 在 Unity 中打开项目

1. 打开 Unity Hub → 用 Unity 2022.3 LTS 打开项目
2. 等待包导入和脚本编译完成
3. 确认控制台无编译错误

## 创建内容资产

1. 菜单栏选择 **Milan → Create MVP Content**（ContentSetup.cs）
2. 确认 `Assets/_Project/Scripts/Content/` 下生成了 SO 资产

## 创建场景

创建以下场景（Assets/_Project/Scenes/）：

### Boot.unity
- 创建空场景，保存为 Boot.unity
- 创建空 GameObject "Bootstrap"，附加 Bootstrap 脚本

### Main.unity
- Canvas → MainScreen 脚本
- 4 个 Button：抽卡 / 角色 / 战斗 / 收集
- 将 Button 引用拖到 MainScreen 的 SerializeField

### Gacha.unity
- Canvas → GachaScreen 脚本
- 3 个 Button：单抽 / 十连 / 返回
- 1 个 Text：结果显示
- 拖拽引用

### CharacterList.unity
- Canvas → CharacterListScreen 脚本
- Text + 返回 Button

### Battle.unity
- Canvas → BattleScreen 脚本
- 开始 Button + 结果 Text + 返回 Button

### Inspection.unity
- 3D 场景
- 创建 Camera + InspectionCamera 脚本（Target 指向角色模型占位）
- 创建角色模型占位（Cube 即可）+ Animator（含 TapReact trigger）
- TapInteraction 脚本 + Collider
- Canvas → InspectionScreen + 返回 Button

### Collection.unity
- Canvas → CollectionScreen 脚本
- Text + 返回 Button

## 构建设置

1. File → Build Settings → Add Open Scenes（按顺序）：Boot, Main, Gacha, CharacterList, Battle, Inspection, Collection

## Android 设置

Edit → Project Settings → Player：
- Company Name: Milan
- Package Name: com.milan.game
- Target API Level: Min 24, Target 33
- Architecture: ARM64
- Graphics APIs: Vulkan + OpenGLES3
- Scripting Backend: IL2CPP

## 运行测试

Window → General → Test Runner：
- EditMode → Run All（应全部通过）

## PlayMode 冒烟测试

1. 从 Boot 场景进入 Play Mode
2. 验证 Bootstrap 运行、存档加载
3. 导航各场景，确认按钮响应
4. 抽卡 → 货币扣除、结果展示
5. 战斗 → 胜利奖励

## 构建 APK

File → Build Settings → Build → Milan.apk

## 后续任务

- 填充更多角色/天赋树/皮肤内容
- 完善 3D 模型和检视动画
- 联机功能（排行榜/聊天/交易）— 架构已预留
