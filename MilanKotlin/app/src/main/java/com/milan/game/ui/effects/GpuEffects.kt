package com.milan.game.ui.effects

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.LocalWorldPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * AGSL / RuntimeShader 视觉层 — 织环 v3.1。
 *
 * 全部入口都先过 [supportsRuntimeShader]；低于 API 33 直接走 Compose 静态兜底，绝不抛异常。
 * 着色器源以 AGSL(SkSL) 字符串内联，避免 res 资源路径与 R8 收缩的坑。
 */

/** RuntimeShader 仅 Android 13(API 33)+ 可用；旧设备必须有降级路径。 */
fun supportsRuntimeShader(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// ── AGSL 源：织环流体背景 v3.1 — 玄墨基底 + 同心环痕 + 纬线织纹 + 金箔丝光 ──
// 概念：界面是织环台。背景不是水墨画，是缓慢归位的环与纬。
private const val FLUID_AGSL = """
uniform float2 u_resolution;
uniform float u_time;
float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p){
    vec2 i = floor(p); vec2 f = fract(p);
    float a = hash(i); float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0)); float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float fbm(vec2 p){
    float v = 0.0; float a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.0; a *= 0.5; }
    return v;
}
// 同心环：以屏幕中心为环心，缓慢外扩的细环
float ringBands(float rad, float t) {
    float phase = fract(rad * 7.0 - t * 0.12);
    float band = smoothstep(0.0, 0.08, phase) * (1.0 - smoothstep(0.10, 0.22, phase));
    // 第二组错相环，形成纬线叠压
    float phase2 = fract(rad * 11.0 + t * 0.07 + 0.35);
    float band2 = smoothstep(0.0, 0.05, phase2) * (1.0 - smoothstep(0.06, 0.14, phase2)) * 0.55;
    return band + band2;
}
// 纬线织纹：斜向细线
float weft(vec2 uv, float t) {
    float s = uv.x * 18.0 + uv.y * 11.0 + t * 0.15;
    float line = abs(fract(s) - 0.5);
    return smoothstep(0.42, 0.5, line) * 0.35;
}
half4 main(float2 fragCoord){
    float2 uv = fragCoord / u_resolution;
    float2 c = uv - vec2(0.5, 0.42);
    // 轻微各向异性：更像椭圆环台
    c.x *= u_resolution.x / max(u_resolution.y, 1.0);
    float rad = length(c);
    float t = u_time * 0.35;
    // 基底：中性玄墨
    half3 ink0 = half3(0.040, 0.050, 0.070);
    half3 ink1 = half3(0.065, 0.075, 0.095);
    float n = fbm(uv * 2.2 + vec2(t * 0.02, -t * 0.015));
    half3 col = mix(ink0, ink1, 0.35 + n * 0.4);
    // 同心环痕
    float rings = ringBands(rad, t);
    half3 gold = half3(0.88, 0.72, 0.38);
    col += gold * rings * 0.16;
    // 纬线
    float w = weft(uv, t);
    col += half3(0.55, 0.72, 0.70) * w * 0.08;
    // 中心环心微光
    float core = exp(-rad * rad * 6.0) * 0.20;
    col += gold * core;
    // 边缘收束
    float vig = smoothstep(1.15, 0.12, rad);
    col *= mix(0.40, 1.0, vig);
    // 细噪点（替代宣纸纤维）
    col += half3(1.0) * hash(uv * 720.0 + t) * 0.025;
    return half4(col, 1.0);
}
"""

/**
 * 着色器时间驱动：仅在生命周期 RESUMED 期间自增 [time]，
 * App 退后台自动暂停，避免逐帧无限动画常驻满帧。
 */
@Composable
private fun rememberShaderTime(active: Boolean, minFrameIntervalMs: Long = 0L): MutableFloatState {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(active, minFrameIntervalMs) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastNanos = withFrameNanos { it }
            try {
                while (true) {
                    if (minFrameIntervalMs > 0L) delay(minFrameIntervalMs)
                    val now = withFrameNanos { it }
                    time.value += (now - lastNanos) / 1_000_000_000f
                    lastNanos = now
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
    return time
}

/**
 * 主页程序化织环背景（玄墨 + 同心环痕 + 纬线丝光）。
 * 读 [LocalWorldPalette] 叠一层世界氛围 glow（v3.1 §5.1：只作用于背景氛围）。
 * 低于 API 33 退化为静态环向渐变。
 */
@Composable
fun FluidBackground(modifier: Modifier = Modifier, active: Boolean = true) {
    val world = LocalWorldPalette.current
    if (!supportsRuntimeShader()) {
        Box(
            modifier
                .background(Brush.radialGradient(listOf(AppTheme.BgMid, AppTheme.BgDeepest)))
                .background(
                    Brush.verticalGradient(
                        listOf(world.glow.copy(alpha = 0.06f), Color.Transparent, world.glow.copy(alpha = 0.03f)),
                    ),
                ),
        )
        return
    }
    val shader = remember { RuntimeShader(FLUID_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    val time = rememberShaderTime(active, minFrameIntervalMs = 45L)

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            shader.setFloatUniform("u_time", time.value)
            shader.setFloatUniform("u_resolution", size.width, size.height)
            drawRect(brush)
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(world.glow.copy(alpha = 0.05f), Color.Transparent, world.glow.copy(alpha = 0.025f)),
                    ),
                ),
        )
    }
}

// ── UR 全息箔（v3 §5.2 / §5.6）──

/**
 * 全息箔 AGSL：虹彩扫光 + 金箔偏色。仅 API 33+；旧设备由 [HolographicFoilOverlay] 自动 Compose 兜底。
 * 性能纪律：常驻流光仅限 UR，同屏由调用方控制实例数（设计语言性能预算）。
 */
private const val FOIL_AGSL = """
uniform float2 u_resolution;
uniform float u_time;
uniform float2 u_touch; // 0..1，触摸点（默认中心）
half4 main(float2 fragCoord){
    float2 uv = fragCoord / u_resolution;
    float2 t = u_touch;
    // 以触摸点为极心的斜向扫光
    float2 d = uv - t;
    float ang = atan(d.y, d.x);
    float rad = length(d);
    float band = fract(ang / 6.2831853 + u_time * 0.07 + rad * 0.35);
    // 窄高光带
    float hi = smoothstep(0.0, 0.08, band) * (1.0 - smoothstep(0.18, 0.38, band));
    // 虹彩：金箔主调 + 轻微色相偏移
    half3 gold = half3(0.94, 0.82, 0.48);
    half3 teal = half3(0.55, 0.85, 0.82);
    half3 mag = half3(0.92, 0.55, 0.72);
    half3 tint = mix(gold, teal, sin(band * 6.283) * 0.5 + 0.5);
    tint = mix(tint, mag, sin(band * 3.14 + 1.2) * 0.25 + 0.25);
    float a = hi * 0.28;
    return half4(tint, a);
}
"""

/**
 * UR 全息箔叠层：API33+ 走 AGSL，否则 Compose sweep 兜底。
 * [touchX]/[touchY] 为 0..1 相对坐标；默认中心。
 */
@Composable
fun HolographicFoilOverlay(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    touchX: Float = 0.5f,
    touchY: Float = 0.42f,
) {
    if (!supportsRuntimeShader() || !active) {
        // Compose 兜底：静态虹彩扫光（不跟帧）
        Box(
            modifier.background(
                Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.15f to AppTheme.GoldHi.copy(alpha = 0.10f),
                    0.28f to AppTheme.Frost.copy(alpha = 0.06f),
                    0.42f to Color.Transparent,
                    0.62f to AppTheme.GoldHi.copy(alpha = 0.08f),
                    0.78f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            ),
        )
        return
    }
    val shader = remember { RuntimeShader(FOIL_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    val time = rememberShaderTime(active, minFrameIntervalMs = 48L)
    Canvas(modifier) {
        shader.setFloatUniform("u_time", time.value)
        shader.setFloatUniform("u_resolution", size.width, size.height)
        shader.setFloatUniform("u_touch", touchX, touchY)
        drawRect(brush)
    }
}
