using Android.Content;
using Android.Graphics;
using Android.Views;
using Path = Android.Graphics.Path;

namespace Milan.Maui;

/// <summary>
/// 诸神黄昏传送门（Twilight gateway）科幻传送门：六边形栅格 + 旋转霓虹线框 + 扫描线 + 故障切片。
/// 旋转中呈现"折叠空间"的未来感，替代原朴素粒子盘，放在召唤法阵中央。
/// 复用 <see cref="AnimatedEffectView"/> 的 30fps 节流，避免十连演出掉帧。
/// </summary>
public class RiftPortal : AnimatedEffectView
{
    private float _phase;
    private readonly Paint _paint = new() { AntiAlias = true };
    private readonly System.Random _rng = new();
    // #37/#38: Path 与核心辉光 Shader 复用，避免每帧新建 native 对象。
    private readonly Path _hexPath = new();
    private RadialGradient? _core;
    private float _coreRad = -1f;

    public RiftPortal(Context context) : base(context) { }

    protected override void OnDraw(Canvas canvas)
    {
        float w = Width, h = Height;
        if (w <= 0 || h <= 0) { NextFrame(); return; }

        _phase += 0.03f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        float cx = w / 2f, cy = h / 2f;
        float rad = Math.Min(w, h) * 0.42f;
        // RadialGradient 半径必须为正，否则 IllegalArgumentException 直接杀进程。
        if (rad <= 0f) { NextFrame(); return; }

        // 核心辉光（青色等离子）
        _paint.SetStyle(Paint.Style.Fill);
        if (_core == null || Math.Abs(_coreRad - rad) > 0.5f)
        {
            _core?.Dispose();
            _core = new RadialGradient(cx, cy, rad * 0.95f,
                UI.ColorLong(Color.Argb(175, AppTheme.Violet.R, AppTheme.Violet.G, AppTheme.Violet.B)),
                UI.ColorLong(Color.Argb(0, 0x3A, 0x22, 0x66)), Shader.TileMode.Clamp);
            _coreRad = rad;
        }
        _paint.SetShader(_core);
        canvas.DrawCircle(cx, cy, rad, _paint);
        _paint.SetShader(null);

        // 双层反向旋转六边形线框
        DrawHexRing(canvas, cx, cy, rad, _phase, Color.Argb(225, AppTheme.Violet.R, AppTheme.Violet.G, AppTheme.Violet.B), 6);
        DrawHexRing(canvas, cx, cy, rad * 0.68f, -_phase * 1.4f, Color.Argb(205, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B), 6);

        // 时空栅格 + 扫描线
        _paint.SetStyle(Paint.Style.Stroke);
        _paint.StrokeWidth = UI.Dp(0.4f);
        UI.RiftGrid(canvas, _paint, w, h, _phase);

        var scanY = cy + MathF.Sin(_phase * 0.8f) * rad;
        _paint.Color = Color.Argb(95, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B);
        canvas.DrawRect(0, scanY - UI.Dp(2), w, scanY + UI.Dp(2), _paint);

        // 故障切片（青/品红错位）
        for (int i = 0; i < 3; i++)
        {
            float gy = ((float)(_rng.NextDouble()) + i * 0.33f) % 1f * h;
            _paint.Color = Color.Argb(48, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B);
            canvas.DrawRect(0, gy, w * 0.55f, gy + UI.Dp(1.5f), _paint);
        }

        NextFrame();
    }

    private void DrawHexRing(Canvas canvas, float cx, float cy, float r, float rot, Color col, int sides)
    {
        _paint.SetStyle(Paint.Style.Stroke);
        _paint.StrokeWidth = UI.Dp(1.4f);
        _paint.Color = col;
        _hexPath.Reset();
        for (int i = 0; i <= sides; i++)
        {
            var a = rot + MathF.PI * 2f * i / sides;
            float x = cx + r * MathF.Cos(a);
            float y = cy + r * MathF.Sin(a);
            if (i == 0) _hexPath.MoveTo(x, y); else _hexPath.LineTo(x, y);
        }
        canvas.DrawPath(_hexPath, _paint);
    }

    protected override void OnDetachedFromWindow()
    {
        base.OnDetachedFromWindow();
        _core?.Dispose();
        _core = null;
        _coreRad = -1f;
    }
}
