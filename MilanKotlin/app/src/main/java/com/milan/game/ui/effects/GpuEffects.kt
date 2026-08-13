package com.milan.game.ui.effects

import android.content.Context
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max

/**
 * AGSL / RuntimeShader 视觉层（C# 无等价；Android 13+ 的 GPU 着色器演出）。
 *
 * 全部入口都先过 [supportsRuntimeShader]；低于 API 33 直接走 Compose 静态兜底，绝不抛异常。
 * 着色器源以 AGSL(SkSL) 字符串内联，避免 res 资源路径与 R8 收缩的坑。
 */

/** RuntimeShader 仅 Android 13(API 33)+ 可用；旧设备必须有降级路径。 */
fun supportsRuntimeShader(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// ── AGSL 源：开包演出后处理（content 输入 + 进度/时间 uniform）──
private const val REVEAL_AGSL = """
uniform shader content;
uniform float2 u_resolution;
uniform float u_time;
uniform float u_progress;
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / u_resolution;
    float ca = 0.006 * u_progress;
    half4 c;
    c.r = content.eval(fragCoord + vec2(ca * u_resolution.x, 0.0)).r;
    c.g = content.eval(fragCoord).g;
    c.b = content.eval(fragCoord - vec2(ca * u_resolution.x, 0.0)).b;
    float2 center = u_resolution * 0.5;
    float d = distance(fragCoord, center);
    float maxR = max(u_resolution.x, u_resolution.y);
    float ring = smoothstep(10.0, 0.0, abs(d - u_progress * 0.62 * maxR));
    c.rgb += vec3(0.95, 0.78, 0.40) * ring * 0.55;
    c.rgb += 0.035 * sin(fragCoord.y * 1.4 + u_time * 9.0) * u_progress;
    c.a = 1.0;
    return c;
}
"""

// ── AGSL 源：卡牌全息箔（程序化彩虹干涉，随设备姿态变色）──
private const val HOLOGRAPHIC_AGSL = """
uniform float2 u_resolution;
uniform float u_time;
uniform float2 u_tilt;
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / u_resolution;
    float ang = u_tilt.x * 3.0 + u_tilt.y * 2.0;
    float h = 0.5 + 0.5 * sin((uv.x + uv.y) * 7.0 + u_time * 1.4 + ang * 6.0);
    float g = 0.5 + 0.5 * sin((uv.x - uv.y) * 7.0 + u_time * 1.1 + ang * 6.0 + 2.094);
    float b = 0.5 + 0.5 * sin((uv.x * 2.0) * 6.0 + u_time * 0.9 + ang * 6.0 + 4.188);
    half3 iri = half3(h, g, b);
    float vig = smoothstep(0.95, 0.2, distance(uv, vec2(0.5)));
    return half4(iri * vig, 1.0);
}
"""

// ── AGSL 源：主页程序化熔金流体背景（暮紫夜 + 熔金，FBM 噪声驱动）──
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
    for (int i = 0; i < 5; i++) { v += a * noise(p); p *= 2.0; a *= 0.5; }
    return v;
}
half4 main(float2 fragCoord){
    float2 uv = fragCoord / u_resolution;
    float t = u_time * 0.05;
    float n = fbm(uv * 3.0 + vec2(t, t * 0.5));
    float n2 = fbm(uv * 5.0 - vec2(t * 0.7, t));
    half3 violet = half3(0.10, 0.07, 0.20);
    half3 gold = half3(0.85, 0.62, 0.28);
    half3 col = mix(violet, gold, smoothstep(0.45, 0.78, n) * 0.6);
    col += gold * smoothstep(0.72, 0.96, n2) * 0.5;
    float vig = smoothstep(1.15, 0.2, distance(uv, vec2(0.5)));
    col *= mix(0.55, 1.0, vig);
    return half4(col, 1.0);
}
"""

/**
 * 开包演出 GPU 后处理层：把子内容作为 content 输入，叠加色散 + 能量环 + 扫描线。
 * [progress] 0→1 驱动能量环扩张与强度；[active] 为 true 时持续驱动 u_time。
 * 低于 API 33 直接透传内容（无特效）。
 */
@Composable
fun GpuRevealLayer(
    active: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!supportsRuntimeShader()) {
        Box(modifier) { content() }
        return
    }
    val shader = remember { RuntimeShader(REVEAL_AGSL) }
    val effect = remember { AndroidRenderEffect.createRuntimeShaderEffect(shader, "content") }
    var time by remember { mutableFloatStateOf(0f) }

    if (active) {
        LaunchedEffect(Unit) {
            val start = System.nanoTime()
            try {
                while (true) {
                    withFrameNanos { t -> time = (t - start) / 1_000_000_000f }
                }
            } catch (_: Exception) { /* 协程被取消即停止 */ }
        }
    }

    Box(
        modifier.graphicsLayer {
            shader.setFloatUniform("u_time", time)
            shader.setFloatUniform("u_progress", progress)
            shader.setFloatUniform("u_resolution", size.width, size.height)
            renderEffect = effect.asComposeRenderEffect()
        },
    ) { content() }
}

/**
 * 卡牌全息箔叠层：程序化彩虹干涉箔，随设备姿态（pitch/roll）变色。
 * 用 [alpha] 控制叠加强度（建议 0.25~0.35）。低于 API 33 不绘制。
 */
@Composable
fun HolographicFoilOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = 0.32f,
) {
    if (!supportsRuntimeShader()) return
    val shader = remember { RuntimeShader(HOLOGRAPHIC_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    val tilt = rememberDeviceTilt()
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        try {
            while (true) {
                withFrameNanos { t -> time = (t - start) / 1_000_000_000f }
            }
        } catch (_: Exception) { }
    }

    val t = tilt.value
    Canvas(modifier.graphicsLayer { this.alpha = alpha }) {
        shader.setFloatUniform("u_time", time)
        shader.setFloatUniform("u_resolution", size.width, size.height)
        shader.setFloatUniform("u_tilt", t.first, t.second)
        drawRect(brush)
    }
}

/**
 * 主页程序化熔金流体背景（暮紫夜 + 熔金）。低于 API 33 退化为静态双色渐变。
 */
@Composable
fun FluidBackground(modifier: Modifier = Modifier) {
    if (!supportsRuntimeShader()) {
        Box(
            modifier.background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1030), Color(0xFF0B0A16)),
                ),
            ),
        )
        return
    }
    val shader = remember { RuntimeShader(FLUID_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        try {
            while (true) {
                withFrameNanos { t -> time = (t - start) / 1_000_000_000f }
            }
        } catch (_: Exception) { }
    }

    Canvas(modifier) {
        shader.setFloatUniform("u_time", time)
        shader.setFloatUniform("u_resolution", size.width, size.height)
        drawRect(brush)
    }
}

/**
 * 设备姿态（pitch/roll）状态，用于驱动全息箔视角变色。
 * 注册 TYPE_ROTATION_VECTOR，全程 try/catch；不支持或无传感器时恒为 (0,0)。
 */
@Composable
fun rememberDeviceTilt(): State<Pair<Float, Float>> {
    val tilt = remember { mutableStateOf(0f to 0f) }
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            val r = FloatArray(9)
            val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent?) {
                e ?: return
                try {
                    SensorManager.getRotationMatrixFromVector(r, e.values)
                    SensorManager.getOrientation(r, o)
                    tilt.value = o[1] to o[2] // pitch, roll
                } catch (_: Exception) { }
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) { }
        }
        try {
            sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        } catch (_: Exception) { }
        onDispose {
            try { sm?.unregisterListener(listener) } catch (_: Exception) { }
        }
    }
    return tilt
}
