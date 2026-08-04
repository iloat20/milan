using Android.Content;
using Android.Graphics;
using Android.Views;
using Android.Widget;
using Path = Android.Graphics.Path;

namespace Milan.Maui;

/// <summary>
/// 卡面特效装配器：把任意卡片视图包进「底层稀有度光晕 + 表层箔面流光」的三明治里。
/// 表层特效全部合并在单个 <see cref="CardSheenView"/> 中绘制，避免十连同屏时
/// 出现十几个各自跑 60fps 的重绘循环。
/// </summary>
public class CardEffects
{
    /// <summary>
    /// 用特效层包裹卡片。<paramref name="radiusDp"/> 需与卡片自身圆角一致，
    /// 否则光效会溢出圆角之外。
    /// </summary>
    public static FrameLayout Apply(Context context, View card, int rarity, Color rarityColor, float radiusDp = 12f)
    {
        var wrapper = new FrameLayout(context);

        // ★ 关键：wrapper 顶替了 card 在父容器中的位置，必须继承 card 原有的布局参数。
        //   否则 weight / 宽度约束全部丢失，wrapper 退化成 WrapContent —— 水平排布的
        //   十连结果会按内容宽度撑开、被挤出屏幕，肉眼只看得到前两张卡。
        if (card.LayoutParameters != null) wrapper.LayoutParameters = card.LayoutParameters;
        card.LayoutParameters = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        wrapper.AddView(card);

        // 底层：稀有度呼吸光晕（仅 SSR+，贴着卡框外沿）
        if (rarity >= 3)
        {
            var aura = new RarityAuraView(context, rarityColor, radiusDp);
            aura.LayoutParameters = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
            wrapper.AddView(aura, 0);
        }

        // 表层：斜向流光 + 全息箔 + 星芒 + 立体高光（R 卡只保留静态高光，省电）
        var sheen = new CardSheenView(context, rarity, rarityColor, radiusDp);
        sheen.LayoutParameters = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        wrapper.AddView(sheen);

        return wrapper;
    }

    public static void Reveal(View card)
    {
        card.Alpha = 0f;
        card.ScaleX = 0.6f;
        card.ScaleY = 0.6f;
        card.Animate().Alpha(1f).ScaleX(1f).ScaleY(1f).SetDuration(450)
            .SetInterpolator(Motion.Ease).Start();
    }
}

/// <summary>
/// 自绘动画 View 基类：脱离窗口 / 不可见时自动停止重绘循环。
/// 统一走 <see cref="NextFrame"/> 节流到 ~30fps —— 同屏十几张卡时这是能否流畅的关键。
/// </summary>
public abstract class AnimatedEffectView : View
{
    protected bool Animating { get; private set; } = true;

    /// <summary>目标帧间隔（毫秒）。33ms ≈ 30fps，足够顺滑且省一半功耗。</summary>
    protected int FrameIntervalMs { get; set; } = 33;

    protected AnimatedEffectView(Context context) : base(context) { }

    /// <summary>请求下一帧（带节流）。替代直接在 OnDraw 里调 Invalidate()。</summary>
    protected void NextFrame()
    {
        if (Animating) PostInvalidateDelayed(FrameIntervalMs);
    }

    protected override void OnAttachedToWindow()
    {
        base.OnAttachedToWindow();
        Animating = true;
        Invalidate();
    }

    protected override void OnDetachedFromWindow()
    {
        base.OnDetachedFromWindow();
        Animating = false;
    }

    protected override void OnWindowVisibilityChanged(ViewStates visibility)
    {
        base.OnWindowVisibilityChanged(visibility);
        Animating = visibility == ViewStates.Visible;
        if (Animating) Invalidate();
    }
}

/// <summary>可被倾斜驱动的图层（全息箔随视角流动），由实现了 <see cref="ITiltAware"/> 的视图消费。</summary>
public interface ITiltAware
{
    /// <summary>nx / ny 为 -1..1 的归一化倾斜量。</summary>
    void OnTilt(float nx, float ny);
}

/// <summary>
/// 卡面表层复合特效：一次 OnDraw 画完立体高光、斜向流光、全息箔与星芒。
/// 按稀有度分级开销：R=静态、SR=流光、SSR/UR=流光+箔+星芒。
/// </summary>
public class CardSheenView : AnimatedEffectView, ITiltAware
{
    private readonly int _rarity;
    private readonly Color _rarity_col;
    private readonly float _radius;
    private readonly Paint _paint = new() { AntiAlias = true };
    private readonly Sparkle[] _sparkles;
    private readonly System.Random _rng = new();
    private float _phase;
    private float _tiltX, _tiltY;

    // 复用几何对象，避免每帧分配（P3）
    private readonly RectF _clip = new();
    private readonly Path _clipPath = new();
    private readonly RectF _border = new();
    private readonly Path _sparkPath = new();
    // 仅依赖尺寸/稀有度的渐变按尺寸缓存（holo/sweep 因随相位移动不缓存）
    private RadialGradient? _inkGrad;
    private LinearGradient? _topGrad, _botGrad;
    private int _gw, _gh;

    public CardSheenView(Context context, int rarity, Color rarityColor, float radiusDp = 12f) : base(context)
    {
        _rarity = rarity;
        _rarity_col = rarityColor;
        _radius = UI.Dp(radiusDp);

        int n = rarity >= 4 ? 7 : rarity == 3 ? 5 : 0;
        _sparkles = new Sparkle[n];
        for (int i = 0; i < n; i++)
            _sparkles[i] = new Sparkle
            {
                X = (float)_rng.NextDouble(),
                Y = (float)_rng.NextDouble(),
                Phase = (float)(_rng.NextDouble() * MathF.PI * 2),
                Size = 0.5f + (float)_rng.NextDouble()
            };
    }

    public void OnTilt(float nx, float ny)
    {
        _tiltX = nx; _tiltY = ny;
        Invalidate();
    }

    protected override void OnDraw(Canvas canvas)
    {
        float w = Width, h = Height;
        if (w == 0 || h == 0) { NextFrame(); return; }

        _phase += 0.045f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;

        _clip.Set(0, 0, w, h);
        canvas.Save();
        _clipPath.Reset();
        _clipPath.AddRoundRect(_clip, _radius, _radius, Path.Direction.Cw);
        canvas.ClipPath(_clipPath);

        // 水墨晕染底纹（东方质感：低 alpha 暖墨从中心向四周褪去）
        EnsureGradients((int)w, (int)h);
        _paint.SetShader(_inkGrad);
        canvas.DrawRect(0, 0, w, h, _paint);
        _paint.SetShader(null);

        DrawTopGloss(canvas, w, h);
        if (_rarity >= 3) DrawHoloFoil(canvas, w, h);
        if (_rarity >= 2) DrawSweep(canvas, w, h);
        DrawBottomBounce(canvas, w, h);
        if (_sparkles.Length > 0) DrawSparkles(canvas, w, h);

        canvas.Restore();

        // 卡框内壁高光描边（立体边缘），不裁剪以便压住圆角
        _paint.SetShader(null);
        _paint.SetStyle(Paint.Style.Stroke);
        _paint.StrokeWidth = UI.Dp(1);
        _paint.Color = Color.Argb(55, 255, 255, 255);
        _border.Set(1, 1, w - 1, h - 1);
        canvas.DrawRoundRect(_border, _radius, _radius, _paint);
        _paint.SetStyle(Paint.Style.Fill);

        // 朱印落款（右下角，稀有度越高用"珍"字）
        UI.SealStamp(canvas, _paint, w - UI.Dp(20), h - UI.Dp(20), UI.Dp(14), _rarity >= 3 ? "珍" : "录");

        if (_rarity >= 2) NextFrame();
    }

    /// <summary>按尺寸缓存仅依赖尺寸/稀有度的渐变（P3）。</summary>
    private void EnsureGradients(int w, int h)
    {
        if (_inkGrad != null && _gw == w && _gh == h) return;
        _gw = w; _gh = h;
        _inkGrad?.Dispose(); _topGrad?.Dispose(); _botGrad?.Dispose();
        float fw = w, fh = h;
        _inkGrad = new RadialGradient(fw * 0.5f, fh * 0.42f, Math.Max(fw, fh) * 0.7f,
            UI.ColorLong(Color.Argb(20, 22, 18, 34)), UI.ColorLong(Color.Argb(0, 22, 18, 34)), Shader.TileMode.Clamp);
        _topGrad = new LinearGradient(0, 0, 0, fh * 0.42f,
            UI.ColorLong(Color.Argb(48, 255, 255, 255)), UI.ColorLong(Color.Argb(0, 255, 255, 255)), Shader.TileMode.Clamp);
        _botGrad = new LinearGradient(0, fh, 0, fh * 0.78f,
            UI.ColorLong(Color.Argb(34, _rarity_col.R, _rarity_col.G, _rarity_col.B)),
            UI.ColorLong(Color.Argb(0, _rarity_col.R, _rarity_col.G, _rarity_col.B)), Shader.TileMode.Clamp);
    }

    /// <summary>顶部弧形玻璃高光：让卡面看起来是有厚度的亮面材质。</summary>
    private void DrawTopGloss(Canvas canvas, float w, float h)
    {
        _paint.SetShader(_topGrad);
        canvas.DrawRect(0, 0, w, h * 0.42f, _paint);
        _paint.SetShader(null);
    }

    /// <summary>底部反光：模拟卡面下缘接收环境光的回弹，增加厚度感。</summary>
    private void DrawBottomBounce(Canvas canvas, float w, float h)
    {
        _paint.SetShader(_botGrad);
        canvas.DrawRect(0, h * 0.78f, w, h, _paint);
        _paint.SetShader(null);
    }

    /// <summary>全息箔：随倾斜流动的彩虹干涉条纹（SSR/UR 专属质感）。</summary>
    private void DrawHoloFoil(Canvas canvas, float w, float h)
    {
        var drift = MathF.Sin(_phase * 0.5f) * 0.25f + _tiltX * 0.5f;
        var x0 = -w * 0.5f + drift * w;
        var colors = UI.ColorLongs(
            Color.Argb(0, 255, 255, 255),
            Color.Argb(30, 0xFF, 0x3D, 0xAE),
            Color.Argb(34, 0x00, 0xE5, 0xFF),
            Color.Argb(30, 0xFF, 0xD7, 0x5A),
            Color.Argb(26, 0x7C, 0x4D, 0xFF),
            Color.Argb(0, 255, 255, 255));
        var stops = new[] { 0f, 0.22f, 0.44f, 0.66f, 0.84f, 1f };
        _paint.SetShader(new LinearGradient(x0, 0, x0 + w * 1.6f, h, colors, stops, Shader.TileMode.Clamp));
        canvas.DrawRect(0, 0, w, h, _paint);
        _paint.SetShader(null);
    }

    /// <summary>周期性斜向扫光：柔边窄带，比原先的全屏渐变干净得多。</summary>
    private void DrawSweep(Canvas canvas, float w, float h)
    {
        // 4.2s 一个周期，其中约 0.9s 在扫过
        var t = (_phase / (MathF.PI * 2));
        var cycle = (t * 1.0f) % 1f;
        if (cycle > 0.28f) return;

        var k = cycle / 0.28f;
        var cx = -w * 0.6f + k * (w * 2.2f);
        var band = UI.Dp(26);
        var colors = UI.ColorLongs(
            Color.Argb(0, 255, 255, 255),
            Color.Argb(_rarity >= 3 ? 120 : 70, 255, 255, 255),
            Color.Argb(0, 255, 255, 255));
        _paint.SetShader(new LinearGradient(
            cx - band, -h * 0.2f, cx + band, h * 1.2f,
            colors, new[] { 0f, 0.5f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawRect(0, 0, w, h, _paint);
        _paint.SetShader(null);
    }

    /// <summary>四芒星闪烁：卡面上随机位置的小十字光斑，SSR+ 的精致感来源。</summary>
    private void DrawSparkles(Canvas canvas, float w, float h)
    {
        _paint.SetStyle(Paint.Style.Fill);
        foreach (var s in _sparkles)
        {
            var life = (MathF.Sin(_phase * 1.6f + s.Phase) + 1f) * 0.5f;
            if (life < 0.55f) continue;
            var a = (int)(((life - 0.55f) / 0.45f) * 235);
            var cx = s.X * w;
            var cy = s.Y * h;
            var r = UI.Dp(5f) * s.Size * ((life - 0.55f) / 0.45f);

            _paint.Color = Color.Argb(a, 255, 255, 255);
            // 四芒星：两条对穿的细长三角
            _sparkPath.Reset();
            _sparkPath.MoveTo(cx, cy - r * 2.4f);
            _sparkPath.LineTo(cx + r * 0.34f, cy);
            _sparkPath.LineTo(cx, cy + r * 2.4f);
            _sparkPath.LineTo(cx - r * 0.34f, cy);
            _sparkPath.Close();
            _sparkPath.MoveTo(cx - r * 2.4f, cy);
            _sparkPath.LineTo(cx, cy - r * 0.34f);
            _sparkPath.LineTo(cx + r * 2.4f, cy);
            _sparkPath.LineTo(cx, cy + r * 0.34f);
            _sparkPath.Close();
            canvas.DrawPath(_sparkPath, _paint);

            _paint.Color = Color.Argb((int)(a * 0.55f), _rarity_col.R, _rarity_col.G, _rarity_col.B);
            canvas.DrawCircle(cx, cy, r * 0.75f, _paint);
        }
    }

    private sealed class Sparkle
    {
        public float X, Y, Phase, Size;
    }
}

/// <summary>
/// 稀有度氛围光：贴着卡框外沿的呼吸辉光 + 四角光斑，画在卡片之下，
/// 形成"卡牌从背光中浮起"的层次。
/// </summary>
public class RarityAuraView : AnimatedEffectView
{
    private readonly Color _color;
    private readonly float _radius;
    private readonly Paint _paint = new() { AntiAlias = true };
    private readonly RectF _rect = new();
    private float _phase;

    public RarityAuraView(Context context, Color color, float radiusDp = 12f) : base(context)
    {
        _color = color;
        _radius = UI.Dp(radiusDp);
    }

    protected override void OnDraw(Canvas canvas)
    {
        float w = Width, h = Height;
        if (w == 0 || h == 0) { NextFrame(); return; }

        _phase += 0.05f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        var breathe = 0.5f + 0.5f * MathF.Sin(_phase);

        // 多层递减描边模拟外发光（硬件加速下 BlurMaskFilter 不可靠，用叠描边替代）
        _paint.SetStyle(Paint.Style.Stroke);
        for (int i = 4; i >= 1; i--)
        {
            var spread = UI.Dp(1.6f) * i;
            var a = (int)((26 - i * 4) * (0.55f + 0.45f * breathe));
            if (a <= 0) continue;
            _paint.StrokeWidth = spread;
            _paint.Color = Color.Argb(a, _color.R, _color.G, _color.B);
            _rect.Set(spread * 0.5f, spread * 0.5f, w - spread * 0.5f, h - spread * 0.5f);
            canvas.DrawRoundRect(
                _rect,
                _radius + spread * 0.5f, _radius + spread * 0.5f, _paint);
        }
        _paint.SetStyle(Paint.Style.Fill);
        NextFrame();
    }
}

// ── 兼容旧调用点：保留 ShineView / PulseView，实现同步升级 ────────────────

/// <summary>斜向柔边扫光（旧接口保留）。</summary>
public class ShineView : AnimatedEffectView
{
    private float _phase;
    private readonly Paint _paint = new() { AntiAlias = true };
    public ShineView(Context context) : base(context) { }

    protected override void OnDraw(Canvas canvas)
    {
        float w = Width, h = Height;
        if (w == 0 || h == 0) { NextFrame(); return; }
        _phase += 0.012f;
        if (_phase > 1f) _phase = -0.35f;
        if (_phase >= 0f)
        {
            var cx = _phase * w * 1.4f;
            var band = UI.Dp(24);
            _paint.SetShader(new LinearGradient(cx - band, 0, cx + band, h,
                UI.ColorLongs(
                    Color.Argb(0, 255, 255, 255),
                    Color.Argb(85, 255, 255, 255),
                    Color.Argb(0, 255, 255, 255)),
                new[] { 0f, 0.5f, 1f }, Shader.TileMode.Clamp));
            canvas.DrawRect(0, 0, w, h, _paint);
            _paint.SetShader(null);
        }
        NextFrame();
    }
}

/// <summary>稀有度呼吸辉光（旧接口保留，行为等同 <see cref="RarityAuraView"/>）。</summary>
public class PulseView : AnimatedEffectView
{
    private float _phase;
    private readonly Color _color;
    private readonly Paint _paint = new() { AntiAlias = true };
    public PulseView(Context context, Color color) : base(context) { _color = color; }

    protected override void OnDraw(Canvas canvas)
    {
        float w = Width, h = Height;
        if (w == 0 || h == 0) { NextFrame(); return; }
        _phase += 0.05f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        var breathe = 0.5f + 0.5f * MathF.Sin(_phase);
        _paint.SetStyle(Paint.Style.Stroke);
        for (int i = 3; i >= 1; i--)
        {
            var spread = UI.Dp(2f) * i;
            var a = (int)((24 - i * 5) * (0.5f + 0.5f * breathe));
            if (a <= 0) continue;
            _paint.StrokeWidth = spread;
            _paint.Color = Color.Argb(a, _color.R, _color.G, _color.B);
            canvas.DrawRoundRect(new RectF(spread / 2, spread / 2, w - spread / 2, h - spread / 2),
                UI.Dp(12), UI.Dp(12), _paint);
        }
        _paint.SetStyle(Paint.Style.Fill);
        NextFrame();
    }
}
