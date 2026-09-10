# Battle SFX assets（第 8 节 P0）

`MilanAudio` 按名加载：`assets/audio/sfx/<名称>.ogg`。
资源缺失时静默跳过，不影响可玩性。

| 文件 | 时机 |
|------|------|
| `battle_strike.ogg` | 普攻命中 |
| `battle_skill.ogg` | 主动技能 |
| `battle_ultimate.ogg` | 大招（能量 ≥ 80） |
| `battle_hurt.ogg` | 我方受击 |
| `battle_death.ogg` | 单位阵亡 |
| `battle_victory.ogg` | 战斗胜利（结算层） |
| `battle_defeat.ogg` | 战斗失败（结算层） |
| `gacha_ritual.ogg` | 抽卡落印 / 光柱爆发（设计语言 P3） |

背景乐：`assets/audio/bgm/battle.ogg`（策略战斗进入时 `playBgm("battle")`，
退出恢复 `theme`）。

## 资产形态

- battle_* 与 `gacha_ritual`、`bgm/battle.ogg` 为 **程序合成 + libvorbis 编码的真 Ogg Vorbis**（`OggS` magic）。
- 时长：SFX 0.14–0.55s（≤ 0.8s）；战斗 BGM 约 24s 可循环。
- 响度对齐 `gacha_*.ogg`。

## 替换正式音效

同名覆盖即可，无需改代码。保持 ≤0.8s，响度对齐 `gacha_pull.ogg`。
