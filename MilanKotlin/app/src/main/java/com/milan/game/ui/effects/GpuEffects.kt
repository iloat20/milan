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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

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
    // 3 octave（原 5）：环境背景为缓慢漂移的低频图案，高频细节贡献极小；
    // 每像素 2 次 fbm 共 10→6 次 noise 采样，片段着色器 ALU 直降 ~40%（低端机 GPU/耗电）。
    for (int i = 0; i < 3; i++) { v += a * noise(p); p *= 2.0; a *= 0.5; }
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
 * 着色器时间驱动（C1 功耗/发热修复）：仅在生命周期 RESUMED 期间自增 [time]，
 * App 退后台（< RESUMED）自动暂停，避免逐帧无限动画常驻满帧。
 * [active] 为 false（如演出未激活）时完全不启动。
 *
 * 时间按「增量累加」推进：回前台从暂停处继续，不再重锚定为 0——
 * 此前每次 repeatOnLifecycle 重启都重新取 start，退后台再回来背景会跳回初始相位。
 *
 * @param minFrameIntervalMs 帧间隔下限：>0 时以约 1000/该值 的频率更新时间
 *   （供慢速环境背景限帧省电；演出层保持默认 0 = 每帧满帧率）。
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
                    // withFrameNanos 对齐到帧边界，保证状态写入落在渲染帧上
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
    val time = rememberShaderTime(active)

    Box(
        modifier.graphicsLayer {
        shader.setFloatUniform("u_time", time.value)
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
    active: Boolean = true,
) {
    if (!supportsRuntimeShader()) return
    val shader = remember { RuntimeShader(HOLOGRAPHIC_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    val tilt = rememberDeviceTilt()
    val time = rememberShaderTime(active)

    Canvas(modifier.graphicsLayer { this.alpha = alpha }) {
        shader.setFloatUniform("u_time", time.value)
        shader.setFloatUniform("u_resolution", size.width, size.height)
        // I11：draw 阶段直读 pitch/roll（draw-phase 失效，姿态事件不触发重组）
        shader.setFloatUniform("u_tilt", tilt.pitch.value, tilt.roll.value)
        drawRect(brush)
    }
}

/**
 * 主页程序化熔金流体背景（暮紫夜 + 熔金）。低于 API 33 退化为静态双色渐变。
 *
 * 限帧（2026-08-22）：图案时间系数仅 0.05（极慢漂移），满帧率重绘纯属浪费——
 * 以 ~45ms 间隔（≈22fps）更新 uniform，肉眼无感知差异，GPU/耗电显著下降。
 */
@Composable
fun FluidBackground(modifier: Modifier = Modifier, active: Boolean = true) {
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
    val time = rememberShaderTime(active, minFrameIntervalMs = 45L)

    Canvas(modifier) {
        shader.setFloatUniform("u_time", time.value)
        shader.setFloatUniform("u_resolution", size.width, size.height)
        drawRect(brush)
    }
}

/**
 * 设备姿态快照：pitch/roll 两个独立浮点 State。
 * I11：替代 `State<Pair<Float, Float>>`——传感器事件写入不再装箱 Pair。
 */
class DeviceTilt internal constructor(
    val pitch: MutableFloatState,
    val roll: MutableFloatState,
)

/**
 * 设备姿态（pitch/roll）状态，用于驱动全息箔视角变色。
 * I11 修复：
 *  - 生命周期感知——仅 RESUMED 期间注册 TYPE_ROTATION_VECTOR，退后台立即注销省电，
 *    回前台自动重挂（repeatOnLifecycle 内注册/注销成对出现）；
 *  - 独立 mutableFloatStateOf——每次姿态事件直接写浮点，不再 `o[1] to o[2]` 装箱；
 *    消费方在 draw 阶段直读 .value（draw-phase 失效），不触发重组。
 * 全程 try/catch；不支持或无传感器时恒为 (0,0)。
 */
@Composable
fun rememberDeviceTilt(): DeviceTilt {
    val pitch = remember { mutableFloatStateOf(0f) }
    val roll = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sm == null || sensor == null) return@LaunchedEffect
        val listener = object : SensorEventListener {
            val r = FloatArray(9)
            val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent?) {
                e ?: return
                try {
                    SensorManager.getRotationMatrixFromVector(r, e.values)
                    SensorManager.getOrientation(r, o)
                    pitch.value = o[1] // pitch
                    roll.value = o[2] // roll
                } catch (_: Exception) { }
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) { }
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            } catch (_: Exception) { }
            try {
                // 挂起直到生命周期跌出 RESUMED；repeatOnLifecycle 会取消本块并重跑
                awaitCancellation()
            } finally {
                try { sm.unregisterListener(listener) } catch (_: Exception) { }
            }
        }
    }
    return remember { DeviceTilt(pitch, roll) }
}
