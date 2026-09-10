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

## 当前资产形态

上述 7 个文件为**程序合成占位音效**（`tools/generate_battle_sfx.py`，
numpy 合成 → 22.05kHz mono 16-bit PCM）。

- 容器实为 RIFF/WAVE，扩展名按约定写成 `.ogg`。
- Android `SoundPool` 经 `MediaExtractor` **按数据嗅探格式**（非扩展名），
  WAVE/PCM 可加载播放；与现有 `gacha_*.ogg`（真 Ogg Vorbis）并存无冲突。
- 时长 0.14–0.55s，均 ≤ 0.8s；响度统一 gain≈0.38。

## 替换为正式音效

美术/音频侧直接**同名覆盖**为真 Ogg Vorbis 即可（保持 ≤0.8s，
响度对齐 `gacha_pull.ogg` / `gacha_reveal.ogg`）。无需改代码。

重新生成占位音：

```powershell
$env:MIMO_PYTHON tools/generate_battle_sfx.py
```
