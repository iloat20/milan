package com.milan.game.ui.battle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.milan.game.domain.battle.StrikeEvent
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.ui.theme.ElementTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 战斗打击反馈（第 8 节 P0）。
 *
 * 将领域 [StrikeEvent] 时间线映射为：
 * - 音效（strike / skill / ultimate / hurt / death）
 * - 出招 punch-in、受击 shake、死亡下沉淡出、技能 vignette
 *
 * 领域日志结构不变；本组件只读事件、只做演出。资源缺失时 MilanAudio 静默。
 */
enum class StrikeFxKind { NORMAL, SKILL, ULTIMATE }

/** 单次打击演出脉冲（UI 层生成，Screen 观察后驱动 UnitTile / 全屏层）。 */
data class StrikeFxPulse(
    val id: Long,
    val attackerId: String,
    val targetId: String,
    val attackerIsPlayer: Boolean,
    val targetIsPlayer: Boolean,
    val kind: StrikeFxKind,
    val targetDefeated: Boolean,
    val damage: Int,
    val attackerElement: String = "",
)

/** 由技能能量档位推断演出档：0=普攻，≥80 视为大招。 */
fun strikeFxKindOf(energyCost: Int): StrikeFxKind = when {
    energyCost >= 80 -> StrikeFxKind.ULTIMATE
    energyCost > 0 -> StrikeFxKind.SKILL
    else -> StrikeFxKind.NORMAL
}

/** 单位打击演出状态：graphicsLayer 直接读 Animatable（禁止在 draw 期写 mutableState）。 */
class UnitStrikeFx {
    private val punchAnim = Animatable(0f)
    private val shakeAnim = Animatable(0f)
    private val deathAnim = Animatable(0f)

    /** graphicsLayer 内只读，无副作用（R6-P1：原先 syncFromAnims 在 draw 期写 state）。 */
    val punch: Float get() = punchAnim.value
    val shake: Float get() = shakeAnim.value
    val death: Float get() = deathAnim.value

    suspend fun playAttacker() {
        punchAnim.snapTo(0f)
        punchAnim.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
    }

    suspend fun playTarget(defeated: Boolean) {
        shakeAnim.snapTo(0f)
        shakeAnim.animateTo(1f, tween(200))
        if (defeated) {
            deathAnim.snapTo(0f)
            deathAnim.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }

    /** 新开战局时复位，防死亡残留半沉/幽灵态（R6-P1）。 */
    suspend fun reset() {
        punchAnim.snapTo(0f)
        shakeAnim.snapTo(0f)
        deathAnim.snapTo(0f)
    }
}

@Composable
fun rememberUnitStrikeFx(): UnitStrikeFx = remember { UnitStrikeFx() }

/**
 * 战场打击编排：按 pulse 顺序播放音效与单位动画。
 * 播完一批后调用 [onConsumed] 清空，避免重组重播。
 */
@Composable
fun BattleStrikeOrchestrator(
    pulses: List<StrikeFxPulse>,
    playerFx: Map<String, UnitStrikeFx>,
    enemyFx: Map<String, UnitStrikeFx>,
    onConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    eventDelayMs: Long = 220L,
) {
    LaunchedEffect(pulses) {
        if (pulses.isEmpty()) return@LaunchedEffect
        pulses.forEach { p ->
            val sfx = when {
                p.targetDefeated -> "battle_death"
                p.kind == StrikeFxKind.ULTIMATE -> "battle_ultimate"
                p.kind == StrikeFxKind.SKILL -> "battle_skill"
                else -> "battle_strike"
            }
            MilanAudio.playSfx(sfx)
            if (p.targetIsPlayer && p.damage > 0 && !p.targetDefeated) {
                MilanAudio.playSfx("battle_hurt")
            }
            playerFx[p.attackerId]?.let { fx -> launch { fx.playAttacker() } }
            enemyFx[p.attackerId]?.let { fx -> launch { fx.playAttacker() } }
            playerFx[p.targetId]?.let { fx -> launch { fx.playTarget(p.targetDefeated) } }
            enemyFx[p.targetId]?.let { fx -> launch { fx.playTarget(p.targetDefeated) } }
            delay(eventDelayMs)
        }
        onConsumed()
    }
}

/** 技能/大招全屏 vignette：攻击者元素色扫过 + 上下暗角。 */
@Composable
fun BattleSkillVignette(
    pulse: StrikeFxPulse?,
    modifier: Modifier = Modifier,
    durationMs: Int = 360,
) {
    // 动效减弱：不闪全屏 vignette
    if (com.milan.game.ui.effects.LocalReduceMotion.current) return

    val alpha = remember { Animatable(0f) }
    val color = remember(pulse?.id) {
        if (pulse == null) Color.Transparent
        else ElementTheme.forElement(pulse.attackerElement).glow
    }
    LaunchedEffect(pulse?.id) {
        if (pulse == null || pulse.kind == StrikeFxKind.NORMAL) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        alpha.snapTo(0.7f)
        alpha.animateTo(0f, tween(durationMs))
    }
    if (alpha.value <= 0.01f) return
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = 1f }) {
        val a = alpha.value
        val w = size.width
        val h = size.height
        drawRect(color = color.copy(alpha = 0.28f * a))
        drawRect(color = Color.Black.copy(alpha = 0.3f * a), size = Size(w, h * 0.2f))
        drawRect(
            color = Color.Black.copy(alpha = 0.3f * a),
            topLeft = Offset(0f, h * 0.8f),
            size = Size(w, h * 0.2f),
        )
    }
}

/** 我方受伤：屏幕边缘红闪。 */
@Composable
fun BattleHurtFlash(
    pulse: StrikeFxPulse?,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(pulse?.id) {
        if (pulse == null || !pulse.targetIsPlayer || pulse.damage <= 0 || pulse.targetDefeated) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        alpha.snapTo(0.2f)
        alpha.animateTo(0f, tween(160))
    }
    if (alpha.value <= 0.01f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val a = alpha.value
        val w = size.width
        val h = size.height
        val stroke = h * 0.08f
        drawRect(Color(0xFFE34234).copy(alpha = a), size = Size(w, stroke))
        drawRect(
            Color(0xFFE34234).copy(alpha = a),
            topLeft = Offset(0f, h - stroke),
            size = Size(w, stroke),
        )
        drawRect(Color(0xFFE34234).copy(alpha = a), size = Size(stroke, h))
        drawRect(
            Color(0xFFE34234).copy(alpha = a),
            topLeft = Offset(w - stroke, 0f),
            size = Size(stroke, h),
        )
    }
}

/**
 * UnitTile 动画修饰符：punch 放大 + 水平 shake + 死亡下沉/去饱和（用 alpha 近似）。
 * 注意 GraphicsLayerScope 的 scaleX/alpha 会遮蔽外部同名变量——内部统一用 animX 等局部名。
 */
fun Modifier.unitStrikeLayer(fx: UnitStrikeFx): Modifier = this.graphicsLayer {
    val p = fx.punch
    val s = fx.shake
    val d = fx.death
    // kotlin.math.sin 只接受 Double，结果再收回 Float（GraphicsLayerScope 要求 Float）
    val punchScale = 1f + 0.12f * sin(p * PI).toFloat()
    scaleX = punchScale
    scaleY = punchScale
    translationX = if (s > 0f) (sin(s * 6.0 * PI).toFloat() * 6f) else 0f
    translationY = d * 14f
    alpha = 1f - 0.55f * d
}

/** 结算层胜利/失败音效（BattleResultOverlay 内调用）。 */
fun playBattleResultSfx(victory: Boolean) {
    MilanAudio.playSfx(if (victory) "battle_victory" else "battle_defeat")
}
