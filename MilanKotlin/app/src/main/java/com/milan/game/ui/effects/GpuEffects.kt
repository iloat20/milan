package com.milan.game.ui.effects

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * AGSL / RuntimeShader 视觉层 — 水墨国风版。
 *
 * 全部入口都先过 [supportsRuntimeShader]；低于 API 33 直接走 Compose 静态兜底，绝不抛异常。
 * 着色器源以 AGSL(SkSL) 字符串内联，避免 res 资源路径与 R8 收缩的坑。
 */

/** RuntimeShader 仅 Android 13(API 33)+ 可用；旧设备必须有降级路径。 */
fun supportsRuntimeShader(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// ── AGSL 源：水墨国风流体背景 v2 — 宣纸肌理 + 墨迹扩散 + 石青淡彩 + 金箔微光 + 朱印暗纹 ──
private const val FLUID_AGSL = """
uniform float2 u_resolution;
uniform float u_time;
// ── 基础噪声工具 ──
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
    for (int i = 0; i < 5; i++) { v += a * noise(p); p *= 2.0; a *= 0.5; }
    return v;
}
// ── 宣纸肌理：细密高频噪点模拟纤维 ──
float paperGrain(vec2 uv) {
    float g = hash(uv * 800.0) * 0.08 + hash(uv * 400.0) * 0.04;
    return g;
}
// ── 墨迹扩散：低频大块墨团 ──
float inkBleed(vec2 uv, float t) {
    float b = fbm(uv * 1.5 + vec2(t * 0.03, t * 0.02));
    b = smoothstep(0.30, 0.70, b);
    return b;
}
// ── 笔触纹理：定向拉伸噪声模拟毛笔笔锋 ──
float brushStroke(vec2 uv, float t) {
    vec2 dir = vec2(cos(0.8), sin(0.8));
    float along = dot(uv, dir);
    float across = dot(uv, vec2(-sin(0.8), cos(0.8)));
    float s = fbm(vec2(along * 6.0, across * 20.0) + vec2(t * 0.05, 0.0));
    return smoothstep(0.35, 0.65, s);
}
// ── 朱印暗纹：右下角方形印章剪影 ──
float sealStamp(vec2 uv) {
    vec2 center = vec2(0.88, 0.92);
    vec2 d = abs(uv - center);
    float box = max(d.x * 1.6, d.y * 1.6);
    return 1.0 - smoothstep(0.035, 0.045, box);
}
half4 main(float2 fragCoord){
    float2 uv = fragCoord / u_resolution;
    float t = u_time * 0.04;
    // ── 层1：宣纸底色（暖灰偏白）──
    half3 paper = half3(0.14, 0.13, 0.12);
    half3 warmWhite = half3(0.22, 0.20, 0.18);
    half3 col = mix(paper, warmWhite, 0.3 + paperGrain(uv) * 0.7);
    // ── 层2：墨迹扩散（大块浓淡变化）──
    float ink = inkBleed(uv, t);
    half3 inkDark = half3(0.04, 0.04, 0.06);
    half3 inkMid = half3(0.08, 0.08, 0.10);
    col = mix(col, mix(inkDark, inkMid, ink), 0.55);
    // ── 层3：笔触纹理（定向毛笔笔锋）──
    float brush = brushStroke(uv, t);
    col = mix(col, col * 0.75, brush * 0.35);
    // ── 层4：石青淡彩（矿物颜料渗透）──
    float n2 = fbm(uv * 4.0 - vec2(t * 0.06, t * 0.04));
    half3 teal = half3(0.25, 0.50, 0.46);
    col += teal * smoothstep(0.50, 0.85, n2) * 0.22;
    // ── 层5：金箔微光（散点金粉）──
    float goldNoise = fbm(uv * 8.0 + vec2(t * 0.08, t * 0.03));
    half3 gold = half3(0.82, 0.65, 0.30);
    col += gold * smoothstep(0.78, 0.98, goldNoise) * 0.18;
    // ── 层6：朱印暗纹（右下角印章）──
    float seal = sealStamp(uv);
    half3 vermillion = half3(0.55, 0.12, 0.10);
    col = mix(col, vermillion, seal * 0.35);
    // ── 暗角：墨色晕染四角 ──
    float vig = smoothstep(1.2, 0.15, distance(uv, vec2(0.5)));
    col *= mix(0.35, 1.0, vig);
    // ── 宣纸纤维高光 ──
    col += half3(1.0, 0.98, 0.95) * paperGrain(uv) * 0.15;
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
 * 主页程序化水墨流体背景（墨色 + 石青 + 金箔微光）。
 * 低于 API 33 退化为静态墨色渐变。
 */
@Composable
fun FluidBackground(modifier: Modifier = Modifier, active: Boolean = true) {
    if (!supportsRuntimeShader()) {
        Box(
            modifier.background(
                Brush.verticalGradient(
                    listOf(AppTheme.BgMid, AppTheme.BgDeepest),
                ),
            ),
        )
        return
    }
    val shader = remember { RuntimeShader(FLUID_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    val time = rememberShaderTime(active, minFrameIntervalMs = 45L)

    Canvas(modifier) {
        shader.setFloatUniform("u_time", time.value)
        shader.setFloatUniform("u_resolution", size.width, size.height)
        drawRect(brush)
    }
}
