package com.milan.game.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.services.CharacterDataEntry
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.nav.ResourceBar
import com.milan.game.ui.theme.AppTheme

/**
 * 主页（C# HomeActivity 翻译）。
 * 资源栏 / 主视觉（Hero 光晕 + 立绘漂浮 + 铭牌）/ 动作按钮 / 诸神名录横滑条 / 底部导航。
 * 立绘经 PortraitImage 按角色 CharacterId 动态加载（drawable/char_<rarity>_<pinyin>.png），
 * 缺图自动回退首字占位；R8 保留规则见 res/raw/keep.xml。
 */
@Composable
fun HomeScreen(
    onNav: (NavItem) -> Unit,
    onOpenGacha: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(AppTheme.BgDeepest, AppTheme.BgMid))
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 资源栏：logo + 资源胶囊
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "MILAN",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.Gold,
                        letterSpacing = 2.6.sp,
                    )
                    Text(
                        text = "诸神黄昏·东方",
                        fontSize = 10.sp,
                        color = AppTheme.Text2,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                ResourceBar()
            }

            // 主体：滚动
            Box(Modifier.weight(1f)) {
                val config = LocalConfiguration.current
                val heroHeight =
                    if (config.screenWidthDp > config.screenHeightDp) 300.dp else 430.dp
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, top = 6.dp, end = 14.dp, bottom = 8.dp
                    ),
                ) {
                    item { Hero(heroHeight) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { HeroButtons(onOpenGacha, onOpenCollection) }
                    item { Spacer(Modifier.height(14.dp)) }
                    item { SectionTitle("诸神名录", "UNIFIED AVATARS") }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { AvatarStrip(onOpenCharacter) }
                    item {
                        Text(
                            text = "✦ 立绘皆源自山海经与中国上古神话，依各自背景故事创作",
                            fontSize = 10.sp,
                            color = AppTheme.Text2,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }
            }

            // 底部导航
            GameNavBar(
                active = NavItem.Home,
                onSelect = onNav,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // 上次异常退出现场回显
        CrashDialogIfAny()
    }
}

// ── 主视觉：角色大图 + 底部铭牌 ──

@Composable
private fun Hero(fixedH: androidx.compose.ui.unit.Dp) {
    val def = remember { featuredCharacter() }
    val rarityColor = AppTheme.rarityColor(def.baseRarity)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(fixedH)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(18.dp))
            .background(AppTheme.Surface),
    ) {
        // 稀有度光晕（脉动，位于立绘之后）
        Halo(rarityColor, Modifier.fillMaxSize())

        // 主视觉立绘：稀有度光晕之上叠真实立绘（缺图自动回退首字占位）
        HeroPortrait(def, rarityColor, Modifier.fillMaxSize())

        // 底部暗化渐变 + 铭牌
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF07070F).copy(alpha = 0f),
                            Color(0xFF07070F).copy(alpha = 0.5f),
                            Color(0xFF07070F).copy(alpha = 0.9f),
                        )
                    )
                )
                .padding(start = 18.dp, top = 48.dp, end = 18.dp, bottom = 16.dp),
        ) {
            // 顶部金色渐隐分隔线
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, AppTheme.Gold.copy(alpha = 0.63f), Color.Transparent)
                        )
                    ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = def.displayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
            )
            Text(
                text = def.title,
                fontSize = 12.sp,
                color = AppTheme.Text2,
                modifier = Modifier.padding(top = 3.dp),
            )
            // 标签行：稀有度 + 世界
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AppTheme.rarityName(def.baseRarity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = rarityColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(rarityColor.copy(alpha = 0.27f))
                        .border(1.dp, rarityColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = def.world,
                    fontSize = 11.sp,
                    color = AppTheme.Text2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppTheme.Surface)
                        .border(1.dp, AppTheme.Stroke, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** 稀有度径向光晕：呼吸脉动（C# HaloView）。 */
@Composable
private fun Halo(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "halo")
    val scale by t.animateFloat(
        initialValue = 1f, targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "haloScale",
    )
    val alpha by t.animateFloat(
        initialValue = 0.30f, targetValue = 0.62f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "haloAlpha",
    )
    Box(
        modifier = modifier
            .alpha(alpha)
            .scale(scale)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.45f), Color.Transparent),
                    radius = 900f,
                )
            ),
    )
}

/** 主视觉立绘：稀有度渐变底 + 真实立绘（浮动缓动；C# ObjectAnimator 6s 循环）。
 * 立绘资源名 = 角色 CharacterId（drawable/char_<rarity>_<pinyin>.png），
 * 经 PortraitImage 动态加载，缺图自动回退首字占位。 */
@Composable
private fun HeroPortrait(def: CharacterDataEntry, rarityColor: Color, modifier: Modifier = Modifier) {
    // 立绘漂浮（±10dp 缓动，伪 3D 呼吸感；C# ObjectAnimator 6s 循环）
    val t = rememberInfiniteTransition(label = "float")
    val offsetY by t.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "floatY",
    )
    Box(
        modifier = modifier.offset(y = offsetY.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 稀有度渐变底（立绘为透明底 PNG 时提供视觉支撑）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(rarityColor.copy(alpha = 0.28f), Color.Transparent),
                        radius = 1400f,
                    )
                ),
        )
        PortraitImage(
            characterId = def.characterId,
            rarity = def.baseRarity,
            name = def.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/** 主视觉下方动作钮：金色召唤 + 霓虹图鉴。 */
@Composable
private fun HeroButtons(onOpenGacha: () -> Unit, onOpenCollection: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoldButton("✦ 前往召唤", Modifier.weight(1f), onOpenGacha)
        Spacer(Modifier.width(12.dp))
        NeonButton("神谱图鉴", Modifier.weight(1f), onOpenCollection)
    }
}

// ── 诸神名录：统一头像横滑 ──

/** 名录精选（C# HomeActivity 同款：id / 名称 / 出处）。 */
private val Picks = listOf(
    Triple("char_ur_zhulong", "烛龙", "山海经"),
    Triple("char_ur_xingtian", "刑天", "中国神话"),
    Triple("char_ssr_fenghuang", "凤凰", "山海经"),
    Triple("char_sr_bifang", "毕方", "山海经"),
    Triple("char_sr_jingwei", "精卫", "中国神话"),
    Triple("char_ssr_leishen", "雷神", "山海经"),
)

@Composable
private fun AvatarStrip(onOpenCharacter: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((id, name, src) in Picks) {
            val def = GameState.service.characters.firstOrNull { it.characterId == id }
                ?: continue
            Column(
                modifier = Modifier
                    .clickable { onOpenCharacter(id) }
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarCircle(def, Modifier.size(58.dp))
                Text(
                    text = name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Text1,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(text = src, fontSize = 8.sp, color = AppTheme.Text2)
            }
        }
    }
}

/** 名录头像：稀有度色圆环 + 真实立绘（C# UnifiedAvatarView；缺图自动回退首字）。 */
@Composable
private fun AvatarCircle(def: CharacterDataEntry, modifier: Modifier = Modifier) {
    val c = AppTheme.rarityColor(def.baseRarity)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(c.copy(alpha = 0.20f))
            .border(1.5.dp, c.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        PortraitImage(
            characterId = def.characterId,
            rarity = def.baseRarity,
            name = def.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

// ── 小标题：左金线 + 标题 + 英文副标 ──

@Composable
private fun SectionTitle(title: String, en: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppTheme.GoldHi),
        )
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Text1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
        )
        Text(
            text = en,
            fontSize = 9.sp,
            color = AppTheme.Text2,
            letterSpacing = 1.sp,
        )
    }
}

// ── 主视觉角色选取（C# FeaturedCharacter）──

private fun featuredCharacter(): CharacterDataEntry {
    // 存档角色可能在内容表查不到：先取已拥有最高稀有度（Def 非空）
    val best = GameState.owned()
        .filter { it.def != null }
        .maxByOrNull { it.rarity }
        ?.def
    if (best != null) return best

    val any = GameState.service.characters.firstOrNull()
    if (any != null) return any

    // 内容表彻底为空时的最后防线：占位角色，宁可难看也不能崩。
    return CharacterDataEntry(
        characterId = "placeholder",
        displayName = "未知存在",
        title = "数据缺失",
        world = "Shinwa",
        element = "Flame",
        baseRarity = 1,
        baseStats = listOf(10, 10, 100, 10),
    )
}

// ── 上次异常退出现场回显（C# ShowPreviousCrashIfAny）──

@Composable
private fun CrashDialogIfAny() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        var r = CrashReporter.readAndClear() ?: ""
        if (r.isEmpty() && CrashReporter.previousBootIncomplete()) {
            r = "未捕获到托管异常，但上次启动未走完流程 —— 疑似 native 层崩溃。\n\n" +
                "上次启动面包屑：\n" + (CrashReporter.previousBootTrace() ?: "(无)")
        }
        if (r.isNotEmpty()) {
            report = r
            show = true
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("上次异常退出的现场") },
            text = { Text(report.take(3000)) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("milan-crash", report))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                    }
                    show = false
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text("关闭") }
            },
        )
    }
}
