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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * AGSL / RuntimeShader 视觉层（C# 无等价；Android 13+ 的 GPU 着色器演出）。
 *
 * 全部入口都先过 [supportsRuntimeShader]；低于 API 33 直接走 Compose 静态兜底，绝不抛异常。
 * 着色器源以 AGSL(SkSL) 字符串内联，避免 res 资源路径与 R8 收缩的坑。
 *
 * （2026-08 UI 现代化清理：GpuRevealLayer/HolographicFoilOverlay/rememberDeviceTilt
 * 自 CyberStage 改用纯 Canvas 自绘路线后一直零调用，连同 REVEAL_AGSL/HOLOGRAPHIC_AGSL
 * 与传感器依赖一并删除；如需恢复查 git 历史。）
 */

/** RuntimeShader 仅 Android 13(API 33)+ 可用；旧设备必须有降级路径。 */
fun supportsRuntimeShader(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
