# 立绘 · 头像 · 卡牌设计说明（2026-09-09）

> 规范源头：`docs/superpowers/art-direction/portraits-v2/`（v2.2/v2.3）
> 本文只记录工程侧资源命名与加载约定，不重复美术规范正文。

## 1. 立绘（全身 / 卡面）

| 项 | 约定 |
|---|---|
| 资源名 | `drawable-nodpi/char_<rarity>_<pinyin>.webp` |
| 数量 | **31 / 31**（与 data.json 角色一一对应） |
| 用途 | 详情 Hero、养成、抽卡演出、**卡牌画心**、列表网格 |
| 加载档 | `PortraitTarget.Full`（2x 采样） |
| 规范 | portraits-v2：透明底、叙事优先构图、卡面 75–85% 占幅、脸不裁切 |

批量重生成：`docs/superpowers/art-direction/portraits-v2/prompts-export.json` + `extract_prompts.py`。

## 2. 头像（裁脸）

| 项 | 约定 |
|---|---|
| 资源名 | `drawable-nodpi/avatar_<rarity>_<pinyin>.webp` |
| 数量 | **31 / 31** |
| 尺寸 | 256×256 正方形 |
| 裁切带 | 画布高 8%–32%（脸带）水平居中 |
| 生成脚本 | `tools/gen_avatars.py`（Pillow，改立绘后重跑） |
| 加载档 | `PortraitTarget.Avatar`（1x，直接解码 256 图） |

**接线点**（圆形/小尺寸展示）：
- 主页丹青名录 `HomeScreen.AvatarCircle`
- 抽卡池预览 / 结果 chip `GachaScreen`
- 编队槽 `FormationUi`
- 剧情对白说话者 `DialogueScreen`

缺失回退：探测不到 `avatar_*` 时自动回落 `char_*` 全立绘（`PortraitImageContent`）。

## 3. 卡牌框（CodexCard v3「丹青典藏」）

实现：`ui/components/CodexCard.kt`；组合：`CharacterCard`。

四档稀有度 = 四档装裱工艺（色+纹+光+动）：

| tier | 工艺 | 框 | 光 |
|---|---|---|---|
| R | 素面印刷 | 1dp 素线 | 无 |
| SR | 上釉 | 1.5dp 双线 | 无 |
| SSR | 鎏金边 | 2dp + 四角回纹 | 呼吸光晕 1.5s |
| UR | 全息箔 | 2.5dp 流光边 | 4s 扫光 + 底光 |

卡面结构：
```
CodexCard(tier)
└─ CharacterCard
   ├─ 画心 aspect 0.82：元素渐变底 + PortraitImage(Full) + 稀有度印章 + 元素徽章
   └─ 铭牌：名字 / 称号 / rarity 标签 / footer
```

抽卡结果格 `CyberCards` 仍用自有卡框（演出专用），画心同为全立绘。

## 4. R8 / Release

`res/raw/keep.xml` 必须同时保留：
```xml
tools:keep="@drawable/char_*,@drawable/avatar_*"
```
否则 Release 资源收缩会删光动态加载图。

## 5. 更新流程

1. 改立绘 → 覆盖 `char_*.webp`
2. 跑 `python tools/gen_avatars.py` 重生头像
3. 确认 keep.xml 通配仍覆盖新资源名
4. `assembleRelease` + 冷启动目检立绘/头像
