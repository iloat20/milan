using Android.Content;
using Android.Graphics;
using Android.Views;

namespace Milan.Maui;

/// <summary>
/// 暗夜神性·诸神黄昏 全屏背景：深紫夜底 + 顶部熔金背光辉光 + 缓慢漂移星云 + 偶发星点。
/// 复用既有常驻动画渐变缓存与生命周期暂停（optimization-review P1），
/// 并严格遵守 ColorLong 渐变铁律（Shader 只用 long / long[] 重载）。
/// </summary>
public class TwilightBackground : AnimatedEffectView
{
    private float _phase;
    private Paint? _paint;
    private readonly Random _rng = new();
    /// <summary>调用方是否要求播放动画（与"窗口是否可见"正交，后者由基类的 Animating 负责）。</summary>
    private bool _running;
    // 主背景常驻动画：渐变只依赖高度，缓存避免每帧分配 + GPU 重传（P1）。
    private LinearGradient? _gradient;
    private int _gradH;
    // 顶部熔金背光辉光：依赖尺寸，缓存。
    private RadialGradient? _glow;
    private int _glowW, _glowH;

    private struct Star
    {
        public float X, Y, Speed, Size, Alpha;
    }
    private Star[] _stars = null!;

    public TwilightBackground(Context context) : base(context) { SetWillNotDraw(false); }

    private void InitStars()
    {
        if (_stars != null) return;
        _stars = new Star[20];
        for (int i = 0; i < _stars.Length; i++)
            _stars[i] = new Star
            {
                X = _rng.Next(Math.Max(1, Width)),
                Y = _rng.Next(Math.Max(1, Height)),
                Speed = 0.2f + _rng.Next(3) * 0.3f,
                Size = 1 + _rng.Next(3),
                Alpha = 60 + _rng.Next(90)
            };
    }

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _gradient?.Dispose(); _gradient = null; _gradH = 0;
        _glow?.Dispose(); _glow = null; _glowW = _glowH = 0;
        _stars = new Star[20];
        for (int i = 0; i < _stars.Length; i++)
            _stars[i] = new Star
            {
                X = _rng.Next(w), Y = _rng.Next(h),
                Speed = 0.2f + _rng.Next(3) * 0.3f,
                Size = 1 + _rng.Next(3),
                Alpha = 60 + _rng.Next(90)
            };
    }

    public void Start() { _running = true; Invalidate(); }
    public void Stop() { _running = false; }

    // 离屏/不可见的暂停与恢复由 AnimatedEffectView 统一处理（Animating + 单帧去重），
    // 这里只需在脱离窗口时释放 native Shader。
    protected override void OnDetachedFromWindow()
    {
        base.OnDetachedFromWindow();
        _gradient?.Dispose(); _gradient = null; _gradH = 0;
        _glow?.Dispose(); _glow = null; _glowW = _glowH = 0;
    }

    protected override void OnDraw(Canvas canvas)
    {
        if (_paint == null) _paint = new Paint { AntiAlias = true };
        var w = Width; var h = Height;
        if (w == 0 || h == 0) return;
        InitStars();

        // 深紫夜垂直渐变（按高度缓存）
        if (_gradient == null || _gradH != h)
        {
            _gradient?.Dispose();
            _gradient = new LinearGradient(0, 0, 0, h,
                UI.ColorLong(AppTheme.BgDeepest), UI.ColorLong(AppTheme.BgMid), Shader.TileMode.Clamp);
            _gradH = h;
        }
        _paint!.SetShader(_gradient);
        canvas.DrawRect(0, 0, w, h, _paint);
        _paint.SetShader(null);

        // 顶部熔金背光辉光（单一背光源，电影纵深母题；按尺寸缓存）
        if (_glow == null || _glowW != w || _glowH != h)
        {
            _glow?.Dispose();
            _glow = new RadialGradient(w / 2f, -h * 0.05f, h * 0.9f,
                UI.ColorLongs(
                    Color.Argb(72, 0xE8, 0xB8, 0x4B),   // 熔金核心
                    Color.Argb(22, 0xE8, 0xB8, 0x4B),   // 熔金晕
                    Color.Argb(0, 0, 0, 0)),
                new[] { 0f, 0.45f, 1f }, Shader.TileMode.Clamp);
            _glowW = w; _glowH = h;
        }
        _paint.SetShader(_glow);
        canvas.DrawRect(0, 0, w, h, _paint);
        _paint.SetShader(null);

        // 缓慢漂移星云（暮紫低透明，叠一层纵深）
        _paint.Color = Color.Argb(26, 154, 107, 255);
        canvas.DrawCircle(w * 0.72f, h * 0.74f, w * 0.55f, _paint);

        // 星点
        if (_running) _phase += 0.015f;
        for (int i = 0; i < _stars.Length; i++)
        {
            var s = _stars[i];
            if (_running)
            {
                s.Y -= s.Speed;
                if (s.Y < -10) { s.Y = h + 10; s.X = _rng.Next(w); }
            }
            var a = (int)(s.Alpha * (0.5f + 0.5f * MathF.Sin(_phase + i)));
            _paint.Color = Color.Argb(a, 232, 226, 242);
            canvas.DrawCircle(s.X, s.Y, s.Size, _paint);
            _stars[i] = s;
        }

        if (_running) NextFrame(); // 节流到 ~30fps，且保证至多一条待执行帧
    }
}
