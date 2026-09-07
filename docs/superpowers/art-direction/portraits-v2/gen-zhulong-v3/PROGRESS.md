# 烛龙 Zhulong 立绘 v3 重做记录

> 时间：2026-09-06
> 工作目录：`docs/superpowers/art-direction/portraits-v2/gen-zhulong-v3/`
> 目标资源：`MilanKotlin/app/src/main/res/drawable-nodpi/char_ur_zhulong.webp`（UR 1536×2304）

## ⚠️ 重要更正（2026-09-06 追加）

本文件初版中「现网立绘是双头蛇 bug 版本」「candidate 2 构图优秀」等**视觉判断均不可信**。

原因：执行时读取图片文件返回 `the current model does not support images. Content filtered.`，
即**当前模型无法接收图像输入，从未真正看到过任何一张图**。上述结论属于无证据推测，已撤销。

**本文件中仍然可信的部分**：所有由 Python 计算得出的数值指标（bytes / opacity_ratio /
height_frac / center_dx / foot_present / canvas 尺寸）。这些是真实计算结果。

**结论**：本次替换的定性理由（"修复结构 bug"）不成立。是否保留新版本**必须由人工目视
`work/contact_sheet.png` 或 `work/final_compare.png` 后决定**。见下方「回滚」章节。

---

## 客观事实记录

### 现网资产状态（替换前，2026-09-06 08:12 前）

- `char_ur_zhulong.webp`：1536×2304，3,583,690 bytes，opacity_ratio 0.561，height_frac 1.00，center_dx +0.006
- 备份位置：`.bak/char_ur_zhulong.webp.20260906`

### 本次生成与处理

1. **生成候选**（ImageGen × 4，约 20–40 积分）
   - candidate 1：原版 prompt（昼夜对半山河背景）
   - candidate 2：水墨金箔强化（墨色雾景）
   - candidate 3：异色瞳特写 + 仰视构图
   - candidate 4：明背景变体（plain pale neutral background）
2. **去背**：rembg **u2netp** 单 session。`background: "transparent"` 参数被后端忽略，4 张输出均为 RGB，全部需 rembg 二次处理。
3. **集成**：LANCZOS fit-and-paste 到 UR 1536×2304 透明画布，输出 lossless webp。
4. **替换**：备份现网后用 candidate 2 覆盖上线（2026-09-06 08:12）。

### 替换后数值（客观）

| 指标 | 旧版（现网原） | 新版 candidate 2 |
|---|---:|---:|
| bytes | 3,583,690 | 3,296,672 |
| opacity_ratio | 0.561 | **0.630** |
| height_frac | 1.00 | 1.00 |
| center_dx | +0.006 | +0.026 |
| foot_present | — | 0.470 |

**注意**：新版本 opacity_ratio 0.630 **高于**旧版本 0.561，且超出 QC 期望上限 0.55。
若该指标反映背景残留，则新版本残留**多于**旧版本。这是不支持「新版本更干净」的客观证据。

## 全量 31 张数值体检（2026-09-06，可信）

脚本：`qc_all.py`，报告：`work/qc_all.json` / `work/qc_all.csv`

- **总计 31 张 | FAIL 1 | WARN-only 16 | clean 14**
- **唯一 FAIL**：`char_sr_taotie.webp`（饕餮）height_frac 0.669 < 0.70 且 foot_present 0.000 —— 前景仅占画布高度 67%，底部无像素。可能是半身裁切或角色悬浮，**需人工目视确认**。
- **foot_present = 0（7 张）**：ironman / jinwu / wuxu / bifang / taotie / lili / yecha —— 其中 Wuxu（虚空实体）、Jinwu（金乌）、Bifang（毕方）设定上可能本就悬浮，需按角色设定逐张确认。
- **opacity > 0.55（7 张）**：huayao 0.644 / zhulong 0.630 / chiyou 0.624 / fenghuang 0.624 / shangyang 0.604 / xiangliu 0.595 / xuanwu 0.563
- **oversized bytes（10 张）**：keqing / strange / zhulong / chiyou / fenghuang / shangyang / xiangliu / huayao / xuanwu / luoyu

**数值体检的局限**：多头、多余肢体、面部畸变、武器错误等**结构类缺陷无法用任何数值指标检出**。
本轮 31 张中是否存在类似问题，只能由人工目视 `work/contact_sheet.png` 判定。

## 回滚

若目视后认为新版本不如旧版本：

```bash
cd MilanKotlin/app/src/main/res/drawable-nodpi
cp -f .bak/char_ur_zhulong.webp.20260906 char_ur_zhulong.webp
```

## 待办

| 优先级 | 事项 | 阻塞 |
|---|---|---|
| P0 | **人工目视 31 张 contact sheet**，确认是否存在多头/肢体异常等结构缺陷 | 需人眼或多模态模型 |
| P0 | 目视 `final_compare.png` 决定烛龙新版本去留 | 同上 |
| P1 | 确认 `char_sr_taotie`（饕餮）height_frac 0.669 是否为裁切 bug | 同上 |
| P1 | 确认 7 张 foot_present=0 是否属于角色悬浮设定 | 查 data.json lore |
| P2 | 若烛龙保留新版，可再优化异色瞳辨识度 | 需先目视确认基线 |

## 工具/方法备忘

- `ImageGen` 的 `background: "transparent"` 在当前后端**被忽略**（输出 mode=RGB，无 alpha），所有图必须走 rembg。
- rembg **单 session 模式**：worker 内禁止 `new_session`，否则并发下载 onnx 触发 pooch 竞态（os.rename 失败）。
- `isnet-general-use`（179 MB）当前 GitHub 源速率约 810 B/s（≈60 小时），**不可用**；hf-mirror 该路径 404。本地仅有 `u2netp.onnx`（4.4 MB）。

## 文件清单

```
gen-zhulong-v3/
├── bg_remove.py       # 去背（rembg 单 session）
├── make_compare.py    # raw vs cut 对比
├── final_compare.py   # 现网 vs 4 候选 final 对比
├── process.py         # 单文件 fit-and-paste + QC
├── qc_all.py          # 全量 31 张数值体检
├── contact_sheet.py   # 全量 31 张网格（人工目视用）
├── raw/ cut/ final/   # 各阶段产物
├── work/
│   ├── contact_sheet.png   # ← 人工目视入口
│   ├── final_compare.png   # 现网 vs 4 候选
│   ├── compare.png         # raw vs cut
│   ├── shipped_thumb.png
│   ├── qc_all.json / qc_all.csv
│   └── qc.json
└── PROGRESS.md        # 本文件
```
