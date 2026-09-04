package com.milan.game.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
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
            // ── 题记（lore）：金箔竖条 + 淡墨引言 ──
            if (view.lore.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(AppTheme.Gold.copy(alpha = 0.7f), RoundedCornerShape(1.5.dp)),
                    )
                    Text(
                        view.lore,
                        fontSize = 14.sp,
                        color = worldColor.textSecondary,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            // ── 三段式故事（本源 / 执念 / 羁绊）──
            val story = view.def?.story.orEmpty()
            if (story.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                val sections = parseStorySections(story)
                sections.forEachIndexed { index, (header, body) ->
                    if (header.isNotBlank()) {
                        StorySectionHeader(header, sectionColor(header))
                    }
                    Text(
                        body,
                        fontSize = 14.sp,
                        color = worldColor.textPrimary,
                        lineHeight = 20.sp,
                    )
                    if (index != sections.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            // ── 所属势力徽标 ──
            if (!view.def?.faction.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "所属势力",
                        fontSize = 11.sp,
                        color = worldColor.textSecondary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        view.def.faction,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = worldColor.glow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(worldColor.glow.copy(alpha = 0.12f))
                            .border(1.dp, worldColor.glow.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** 三段式小标题（本源/执念/羁绊）：彩色竖条 + 加粗节名 + 细分隔线。 */
@Composable
private fun StorySectionHeader(name: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(15.dp)
                .background(accent, RoundedCornerShape(1.5.dp)),
        )
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(accent.copy(alpha = 0.22f)),
        )
    }
}

/** 三段式节名 → 强调色（金箔 / 朱砂 / 石青）。 */
private fun sectionColor(header: String): Color = when (header) {
    "本源" -> AppTheme.Gold
    "执念" -> AppTheme.SealRed
    "羁绊" -> AppTheme.Frost
    else -> AppTheme.Text2
}

/** 解析「本源：…\n\n执念：…\n\n羁绊：…」为 (节名, 正文) 列表；无标题块以空节名兜底。 */
private fun parseStorySections(story: String): List<Pair<String, String>> =
    story.split("\n\n").mapNotNull { block ->
        val trimmed = block.trim()
        if (trimmed.isEmpty()) {
            null
        } else {
            val sep = trimmed.indexOf("：")
            if (sep in 1 until trimmed.length) {
                trimmed.substring(0, sep).trim() to trimmed.substring(sep + 1).trim()
            } else {
                "" to trimmed
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
