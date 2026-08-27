package com.milan.game.ui.characters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.infrastructure.SpeechPlayer
import com.milan.game.services.SkillData
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import com.milan.game.ui.theme.WorldPalette

// ── 技能 / 故事 / 语音面板 ──
// P4-1（2026-08-27）：从 CharacterDetailScreen.kt 拆出，信息面板族独立成文件。

@Composable
internal fun SkillPanel(
    skills: List<SkillData>,
    worldColor: WorldPalette,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (skills.isEmpty()) {
                Text("暂无技能", fontSize = 14.sp, color = worldColor.textSecondary)
                return@Column
            }
            skills.forEach { sk ->
                val (from, _, _, _) = ElementTheme.forElement(sk.element)
                val typeColor = when (sk.type) {
                    "Ultimate" -> AppTheme.Warning
                    "Active" -> from
                    else -> worldColor.textSecondary
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                ) {
                    Text(
                        "[${sk.type}]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = typeColor,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        sk.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = worldColor.textPrimary,
                    )
                }
                Text(
                    sk.description,
                    fontSize = 12.sp,
                    color = worldColor.textSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun StoryPanel(
    view: OwnedCharacterView,
    worldColor: WorldPalette,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                view.lore,
                fontSize = 14.sp,
                color = worldColor.textPrimary,
                lineHeight = 18.sp,
            )
            if (view.def?.story.isNullOrBlank().not()) {
                Text(
                    view.def.story.orEmpty(),
                    fontSize = 14.sp,
                    color = worldColor.textSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (!view.def?.faction.isNullOrBlank()) {
                Text(
                    "所属势力：${view.def.faction}",
                    fontSize = 12.sp,
                    color = worldColor.glow,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
internal fun VoicePanel(
    voices: List<String>,
    worldColor: WorldPalette,
) {
    val context = LocalContext.current
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (voices.isEmpty()) {
                Text("暂无语音", fontSize = 14.sp, color = worldColor.textSecondary)
                return@Column
            }
            voices.forEach { v ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Text(
                        "▸",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = worldColor.glow,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        v,
                        fontSize = 14.sp,
                        color = worldColor.textSecondary,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                    // TTS 播报（2026-08：面板从纯文本升级为可播；引擎惰性初始化，失败静默）
                    Text(
                        "▶",
                        fontSize = 13.sp,
                        color = AppTheme.Gold,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clip(CircleShape)
                            .clickable { SpeechPlayer.speak(context, v) }
                            .padding(4.dp),
                    )
                }
            }
        }
    }
}
