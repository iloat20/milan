package com.milan.game.ui.characters

import com.milan.game.OwnedCharacterView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.services.CharacterDataEntry
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import com.milan.game.ui.theme.WorldPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── 武器面板（C# BuildWeaponPanel）──
// P4-1（2026-08-27）：从 CharacterDetailScreen.kt 拆出，武器族独立成文件。

@Composable
internal fun WeaponPanel(
    def: CharacterDataEntry,
    view: OwnedCharacterView,
    owned: Boolean,
    rarityCol: Color,
    worldColor: WorldPalette,
) {
    val (eFrom, _, _, eGlyph) = ElementTheme.forElement(view.element)

    GlassPanel(modifier = Modifier.fillMaxWidth(), highlighted = owned) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 武器舞台：全稀有度展示专属武器（SR/R 武器现已补齐 lore）
            if (def.weaponVfx.isNotBlank()) {
                WeaponStage(weaponVfx = def.weaponVfx, rarityCol = rarityCol, eFrom = eFrom, weaponName = def.weapon)
                Spacer(Modifier.height(10.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    eGlyph,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = eFrom,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(eFrom.copy(alpha = 45f / 255f)),
                )
                Text(
                    def.weapon,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 120f / 255f),
                            offset = Offset(0f, 1f),
                            blurRadius = 4f,
                        ),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
                // 专属标签（按稀有度动态：UR/SSR/SR/R 专属）
                val ownTag = when (view.rarity) {
                    4 -> "UR 专属"
                    3 -> "SSR 专属"
                    2 -> "SR 专属"
                    else -> "R 专属"
                }
                Text(
                    ownTag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = rarityCol,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                        .background(rarityCol.copy(alpha = 45f / 255f))
                        .border(1.dp, rarityCol.copy(alpha = 150f / 255f), RoundedCornerShape(AppTheme.Roundness.sm))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            if (def.weaponVfx.isNotBlank()) {
                Text(
                    "专属武器特效",
                    fontSize = 12.sp,
                    color = worldColor.glow,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Text(
                def.weaponDesc,
                fontSize = 14.sp,
                color = worldColor.textSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * 武器概念图内存缓存：详情页左右切换角色会高频重进 [WeaponStage]，
 * 无缓存时每次都重复 IO 解码 512² WebP（约 1MB/张）。LRU 上限 8 张 ≈ 8MB，
 * 超出自动驱逐最旧；键为武器 vfx 名。
 */
private val WeaponArtCache = LruCache<String, Bitmap>(8)

/**
 * 武器舞台：圆角暗底 + 元素径向晕染 + 稀有度描边光环（C# WeaponPreviewView 静态帧）。
 * 优先显示 AI 概念图 PNG（assets/weapons/{vfx}.png），缺失回退武器名（C# 的 Canvas 几何回退为 P2）。
 */
@Composable
private fun WeaponStage(
    weaponVfx: String,
    rarityCol: Color,
    eFrom: Color,
    weaponName: String,
) {
    val context = LocalContext.current
    // 武器图已转 WebP（assets/weapons/{vfx}.webp）；IO 线程按 2x 采样解码
    // （1024×1024 原图、显示仅 160.dp 高，解码内存降 4 倍），首帧不卡主线程。
    val weaponBmp by produceState<Bitmap?>(initialValue = null, weaponVfx) {
        // P1-2：key（weaponVfx）变化时先清空旧值，否则切角色瞬间会闪一瞬上一角色的武器图
        value = null
        value = withContext(Dispatchers.IO) {
            WeaponArtCache.get(weaponVfx) ?: runCatching {
                context.assets.open("weapons/$weaponVfx.webp").use { ins ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(ins, null, opts)
                }
            }.getOrNull()?.also { WeaponArtCache.put(weaponVfx, it) }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(AppTheme.Roundness.lg))
            .background(AppTheme.WeaponStageBg)
            .border(2.dp, rarityCol.copy(alpha = 200f / 255f), RoundedCornerShape(AppTheme.Roundness.lg)),
    ) {
        // 元素晕染（C# RadialGradient α75→0）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(eFrom.copy(alpha = 75f / 255f), Color.Transparent),
                        center = Offset.Unspecified,
                        radius = 0.72f,
                    ),
                ),
        )
        val bmp = weaponBmp
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = weaponName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
            )
        } else {
            // 回退：居中武器名（C# Canvas 几何绘制为 P2）
            Text(
                weaponName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
