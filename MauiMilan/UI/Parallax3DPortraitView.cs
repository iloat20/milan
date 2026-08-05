using Android.Content;
using Android.Graphics;
using Android.Hardware;
using Android.Util;
using Android.Views;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// 3D 沉浸式立绘展示：单张静态 PNG 也能产生景深、多层视差、随鼠标/陀螺仪联动的立体效果。
/// 分层结构（从远到近）：
///   1. 景深背景层：模糊放大版立绘 + 世界主题暗角，低系数视差移动；
///   2. 中景角色层：清晰立绘 + Camera 透视倾斜（±8°）+ 呼吸光；
///   3. 前景粒子层：元素主题粒子 + 稀有度辉光，高系数视差移动。
/// 输入：陀螺仪（首选）或手指拖拽。30fps 节流，遵守动画生命周期。
/// </summary>
public class Parallax3DPortraitView : View
{
    private Bitmap? _bitmap;
    private Bitmap? _blurredBg;
    private string _characterId = "";
    private int _rarity = 1;
    private string _element = "Flame";
    private string _glyph = "炎";
    private string _ambientVfx = "";
    private Color _rarityCore, _rarityEdge;
    private Color _elementGlow;

    private int _w, _h;
    private float _cx, _cy;
    private float _charLeft, _charTop, _charWidth, _charHeight;

    // 视差与透视
    private float _tiltX, _tiltY;      // -1..1，来自陀螺仪积分或触摸
    private float _targetTiltX, _targetTiltY;
    private readonly float _maxRotateY = 20f;  // 水平透视倾斜角度（加强以凸显 3D）
    private readonly float _maxRotateX = 14f;  // 垂直透视倾斜角度（加强）
    private readonly float _bgParallax = 60f;  // dp，背景层最大位移（反向，强化景深）
    private readonly float _fgParallax = 74f;  // dp，前景层最大位移
    private readonly float _charParallax = 32f; // dp，角色层位移（加强）
    private readonly float _cameraZ = -5f;     // 相机距离（更近=透视更强）
    private readonly float _touchSensitivity = 1.8f;

    // 呼吸与动画
    private float _phase;
    private float _autoPhase;   // idle 自动 3D 摆动相位
    private bool _animating = true;

    // 粒子
    private readonly List<Particle> _particles = new();
    private Random _rng = new();

    // 传感器
    private SensorManager? _sensorManager;
    private Sensor? _gyroSensor;
    private GyroListener? _gyroListener;
    private bool _hasGyro;

    private Paint? _paint;
    // 立绘融合滤镜：冷调去饱和（PortraitGrade ToningMatrix），让 AI 立绘原图融入
    // 「暗夜神性·诸神黄昏」世界，不再"跳"出框外。仅作用于立绘本体绘制，绘制后立即撤销。
    private readonly ColorMatrixColorFilter? _unityFilter =
        new(PortraitGrade.ToningMatrix(PortraitGrade.Grade.Standard));
    private Android.Graphics.Camera? _camera;
    private bool _cameraInit;
    private readonly Matrix _matrix = new();

    public Parallax3DPortraitView(Context context) : base(context)
    {
        SetWillNotDraw(false);
        _rarityCore = Color.Argb(255, 255, 200, 87);
        _rarityEdge = Color.Argb(255, 232, 184, 75);
        _elementGlow = Color.ParseColor("#FF5252");
    }

    public Parallax3DPortraitView Bind(CharacterDataEntry def)
    {
        _characterId = def.CharacterId;
        _rarity = def.BaseRarity;
        _element = def.Element;
        _glyph = ElementTheme.For(def.Element).glyph;
        _ambientVfx = def.AmbientVfx ?? "";
        _elementGlow = ElementTheme.For(def.Element).glow;
        ApplyRarityColors();
        _bitmap = PortraitLoader.Get(def.CharacterId);
        _blurredBg = null; // 尺寸变化后懒加载
        InitParticles();
        CalcCharacterBounds();
        Invalidate();
        return this;
    }

    public Parallax3DPortraitView Bind(OwnedCharacterView ch)
    {
        _characterId = ch.Save.CharacterId;
        _rarity = ch.Rarity;
        _element = ch.Element;
        _glyph = ElementTheme.For(ch.Element).glyph;
        _ambientVfx = ch.Def?.AmbientVfx ?? "";
        _elementGlow = ElementTheme.For(ch.Element).glow;
        ApplyRarityColors();
        _bitmap = PortraitLoader.Get(ch.Save.CharacterId);
        _blurredBg = null;
        InitParticles();
        CalcCharacterBounds();
        Invalidate();
        return this;
    }

    private void ApplyRarityColors()
    {
        (_rarityCore, _rarityEdge) = _rarity switch
        {
            4 => (Color.Argb(255, 255, 200, 87), Color.Argb(255, 232, 184, 75)),
            3 => (Color.Argb(255, 199, 155, 255), Color.Argb(255, 154, 107, 255)),
            2 => (Color.Argb(255, 127, 196, 255), Color.Argb(255, 74, 144, 217)),
            _ => (Color.Argb(255, 232, 226, 242), Color.Argb(255, 200, 200, 220))
        };
    }

    private void InitParticles()
    {
        _particles.Clear();
        _rng = new Random(_characterId.GetHashCode());
        var count = _rarity switch { 4 => 22, 3 => 16, 2 => 10, _ => 6 };
        for (int i = 0; i < count; i++)
        {
            _particles.Add(new Particle
            {
                X = (float)_rng.NextDouble(),
                Y = (float)_rng.NextDouble(),
                Z = 0.3f + (float)_rng.NextDouble() * 0.7f,
                Size = 2 + _rng.Next(6),
                Speed = 0.2f + (float)_rng.NextDouble() * 0.6f,
                Phase = (float)(_rng.NextDouble() * MathF.PI * 2),
                Life = (float)_rng.NextDouble(),
                Type = _rng.Next(3)
            });
        }
    }

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _w = w; _h = h;
        _cx = _w / 2f; _cy = _h / 2f;
        _blurredBg = null; // 尺寸变了重新烘焙背景
        CalcCharacterBounds();
    }

    private void CalcCharacterBounds()
    {
        if (_w == 0 || _h == 0) return;
        if (_bitmap == null || _bitmap.IsRecycled)
        {
            _charWidth = _w * 0.55f;
            _charHeight = _h * 0.65f;
            _charLeft = (_w - _charWidth) / 2f;
            _charTop = (_h - _charHeight) * 0.45f;
        }
        else
        {
            // contain 适配，fit 比例 0.92，让立绘占据更多画面
            var scale = Math.Min((float)_w / _bitmap.Width, (float)_h / _bitmap.Height) * 0.92f;
            _charWidth = _bitmap.Width * scale;
            _charHeight = _bitmap.Height * scale;
            _charLeft = (_w - _charWidth) / 2f;
            _charTop = (_h - _charHeight) * 0.42f;
        }
    }

    protected override void OnAttachedToWindow()
    {
        base.OnAttachedToWindow();
        _animating = true;
        TryRegisterGyroscope();
        Invalidate();
    }

    protected override void OnDetachedFromWindow()
    {
        base.OnDetachedFromWindow();
        _animating = false;
        UnregisterGyroscope();
        ReleaseBlurredBg();
    }

    protected override void OnWindowVisibilityChanged(ViewStates visibility)
    {
        base.OnWindowVisibilityChanged(visibility);
        _animating = visibility == ViewStates.Visible;
        if (_animating)
        {
            TryRegisterGyroscope();
            Invalidate();
        }
        else
        {
            UnregisterGyroscope();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 输入：陀螺仪
    // ═══════════════════════════════════════════════════════════════
    private void TryRegisterGyroscope()
    {
        if (_hasGyro) return;
        try
        {
            _sensorManager ??= Context.GetSystemService(Context.SensorService) as SensorManager;
            if (_sensorManager == null) return;
            _gyroSensor ??= _sensorManager.GetDefaultSensor(SensorType.Gyroscope);
            if (_gyroSensor == null) return;
            _gyroListener ??= new GyroListener(this);
            _sensorManager.RegisterListener(_gyroListener, _gyroSensor, SensorDelay.Game);
            _hasGyro = true;
        }
        catch { _hasGyro = false; }
    }

    private void UnregisterGyroscope()
    {
        if (!_hasGyro || _sensorManager == null || _gyroListener == null) return;
        try { _sensorManager.UnregisterListener(_gyroListener); } catch { }
        _hasGyro = false;
    }

    internal void OnGyroscope(float x, float y)
    {
        // 陀螺仪给出的是角速度（rad/s）。简单积分 + 阻尼映射到目标倾斜。
        const float scale = 0.08f;
        const float decay = 0.92f;
        _targetTiltX += x * scale;
        _targetTiltY += y * scale;
        _targetTiltX *= decay;
        _targetTiltY *= decay;
        _targetTiltX = Math.Clamp(_targetTiltX, -1f, 1f);
        _targetTiltY = Math.Clamp(_targetTiltY, -1f, 1f);
    }

    // ═══════════════════════════════════════════════════════════════
    // 输入：触摸拖拽
    // ═══════════════════════════════════════════════════════════════
    public override bool OnTouchEvent(MotionEvent? e)
    {
        if (e == null) return base.OnTouchEvent(e);
        var x = e.GetX();
        var y = e.GetY();
        switch (e.Action)
        {
            case MotionEventActions.Down:
                _targetTiltX = Math.Clamp((x - _cx) / (_w * 0.5f) * _touchSensitivity, -1f, 1f);
                _targetTiltY = Math.Clamp((y - _cy) / (_h * 0.5f) * _touchSensitivity, -1f, 1f);
                return true;
            case MotionEventActions.Move:
                _targetTiltX = Math.Clamp((x - _cx) / (_w * 0.5f) * _touchSensitivity, -1f, 1f);
                _targetTiltY = Math.Clamp((y - _cy) / (_h * 0.5f) * _touchSensitivity, -1f, 1f);
                return true;
        }
        return base.OnTouchEvent(e);
    }

    // ═══════════════════════════════════════════════════════════════
    // 绘制
    // ═══════════════════════════════════════════════════════════════
    protected override void OnDraw(Canvas canvas)
    {
        if (_w == 0 || _h == 0) return;
        _paint ??= new Paint { AntiAlias = true, FilterBitmap = true };
        _camera ??= new Android.Graphics.Camera();
        if (!_cameraInit) { _camera.SetLocation(0, 0, _cameraZ); _cameraInit = true; }

        // 平滑插值
        const float lerp = 0.12f;
        _tiltX += (_targetTiltX - _tiltX) * lerp;
        _tiltY += (_targetTiltY - _tiltY) * lerp;

        // 呼吸光相位
        _phase += 0.028f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        // idle 自动 3D 摆动：即使无陀螺仪/拖拽，立绘也持续呈现景深与透视，
        // 否则静置时 _tiltX/_tiltY 为 0，立绘就是一张正面平面图，毫无立体感。
        _autoPhase += 0.011f;
        if (_autoPhase > MathF.PI * 2) _autoPhase -= MathF.PI * 2;
        var breathe = 0.5f + 0.5f * MathF.Sin(_phase * 0.85f);

        // 自动摆动叠加到交互倾斜上，得到最终绘制用的倾斜角（eff）
        var tiltX = _tiltX + MathF.Sin(_autoPhase) * 0.28f;
        var tiltY = _tiltY + MathF.Cos(_autoPhase * 0.6f) * 0.13f;

        // 层位移（dp -> px）。背景与角色/前景反向，制造清晰景深分离
        var bgDx = -tiltX * UI.Dp(_bgParallax);
        var bgDy = -tiltY * UI.Dp(_bgParallax);
        var fgDx = tiltX * UI.Dp(_fgParallax);
        var fgDy = tiltY * UI.Dp(_fgParallax);

        // 1. 背景层：模糊立绘 + 暗角 + 世界色晕染
        DrawBackgroundLayer(canvas, bgDx, bgDy, breathe);

        // 1.5 氛围特效（背景增强）
        VfxRenderer.DrawAmbient(canvas, _ambientVfx, new RectF(0, 0, _w, _h), _phase, _paint!, _elementGlow);

        // 2. 中景角色层：清晰立绘 + Camera 透视（含自动摆动）
        DrawCharacterLayer(canvas, tiltX, tiltY, breathe);

        // 2.5 立绘统一色调覆盖：暗角 + 左上边缘光（屏幕空间，呼应"背光"母题）
        PortraitGrade.DrawOverlay(canvas,
            new RectF(_charLeft, _charTop, _charLeft + _charWidth, _charTop + _charHeight),
            PortraitGrade.Grade.Standard, _paint!);

        // 注：武器不再叠加在立绘之上（会挡住角色），改由详情页「专属武器」展示卡单独呈现。

        // 3. 稀有度辉光（沿角色轮廓）
        DrawRarityGlow(canvas, breathe);

        // 4. 前景粒子层
        DrawForegroundParticles(canvas, fgDx, fgDy, breathe);

        // 5. 暗角遮罩（增强景深）
        DrawVignette(canvas);

        if (_animating) PostInvalidateDelayed(33);
    }

    private void DrawBackgroundLayer(Canvas canvas, float dx, float dy, float breathe)
    {
        // 懒加载模糊背景
        if (_blurredBg == null && _bitmap != null && !_bitmap.IsRecycled)
            BuildBlurredBackground();

        _paint!.SetStyle(Paint.Style.Fill);
        _paint.SetShader(null);
        _paint.Color = Color.Argb(255, 0x07, 0x07, 0x0F);
        canvas.DrawRect(0, 0, _w, _h, _paint);

        if (_blurredBg != null && !_blurredBg.IsRecycled)
        {
            // 模糊背景放大 12%，随倾斜反向微动，形成景深
            var scale = 1.14f + 0.02f * breathe;
            var bw = _blurredBg.Width * scale;
            var bh = _blurredBg.Height * scale;
            var bx = (_w - bw) / 2f + dx * 0.4f;
            var by = (_h - bh) / 2f + dy * 0.4f;
            // 模糊底用偏冷、更低透明度的着色，避免白色提亮导致立绘发灰发亮，让底更沉。
            _paint.Color = Color.Argb((int)(72 + 22 * breathe), 206, 202, 226);
            canvas.DrawBitmap(_blurredBg, null, new RectF(bx, by, bx + bw, by + bh), _paint);
        }

        // 世界色晕染：仅用元素 glow（设计好的主题光色），去掉刺眼的纯黄 from 层，
        // 避免雷/光系元素把立绘四周染成块黄，与 Obsidian 风格冲突。
        var (_, _, glow, _) = ElementTheme.For(_element);
        var radius = Math.Max(_w, _h) * 0.65f;
        if (radius > 0)
        {
            _paint.SetShader(new RadialGradient(_cx + dx * 0.3f, _cy + dy * 0.3f, radius,
                UI.ColorLongs(
                    Color.Argb((int)(26 + 14 * breathe), glow.R, glow.G, glow.B),
                    Color.Argb((int)(7 * breathe), glow.R, glow.G, glow.B),
                    Color.Argb(0, 0, 0, 0)),
                new[] { 0f, 0.55f, 1f }, Shader.TileMode.Clamp));
            canvas.DrawRect(0, 0, _w, _h, _paint);
            _paint.SetShader(null);
        }
    }

    private void BuildBlurredBackground()
    {
        if (_bitmap == null || _bitmap.IsRecycled) return;
        try
        {
            // 快速模糊：先缩放到 1/4，再放大绘制时自然产生双线性插值柔焦
            var smallW = Math.Max(1, _bitmap.Width / 4);
            var smallH = Math.Max(1, _bitmap.Height / 4);
            var small = Bitmap.CreateScaledBitmap(_bitmap, smallW, smallH, true);
            _blurredBg = Bitmap.CreateBitmap(_w, _h, Bitmap.Config.Argb8888!);
            using var c = new Canvas(_blurredBg);
            using var p = new Paint { AntiAlias = true, FilterBitmap = true };
            // 放大 small 到画布尺寸，产生柔焦
            c.DrawBitmap(small, null, new RectF(0, 0, _w, _h), p);
            small.Recycle();
        }
        catch { _blurredBg = null; }
    }

    private void ReleaseBlurredBg()
    {
        if (_blurredBg != null && !_blurredBg.IsRecycled) _blurredBg.Recycle();
        _blurredBg = null;
    }

    private void DrawCharacterLayer(Canvas canvas, float tiltX, float tiltY, float breathe)
    {
        if (_bitmap == null || _bitmap.IsRecycled)
        {
            DrawFallback(canvas);
            return;
        }

        var cx = _charLeft + _charWidth / 2f;
        var cy = _charTop + _charHeight / 2f;

        // 接触阴影：与地面反向偏移，强化悬浮立体感（屏幕空间，不随透视旋转）
        DrawContactShadow(canvas, tiltX, tiltY);

        canvas.Save();
        // 视差平移 + 透视旋转（加大位移与角度，3D 更明显）
        canvas.Translate(cx + tiltX * UI.Dp(_charParallax), cy + tiltY * UI.Dp(_charParallax * 0.7f));
        _camera!.Save();
        _camera.RotateY(tiltX * _maxRotateY);
        _camera.RotateX(-tiltY * _maxRotateX);
        _camera.ApplyToCanvas(canvas);
        _camera.Restore();
        canvas.Translate(-cx, -cy);

        // 呼吸光缩放（极轻微，避免穿帮）
        var s = 1f + 0.02f * breathe;
        canvas.Scale(s, s, cx, cy);

        _paint!.SetShader(null);
        _paint.Color = Color.White;
        _paint.SetStyle(Paint.Style.Fill);
        // 立绘本体融合滤镜：极轻压暗+冷化，使其融入深色背景，缓解原图直出不和谐。
        if (_unityFilter != null) _paint.SetColorFilter(_unityFilter);
        canvas.DrawBitmap(_bitmap,
            new Rect(0, 0, _bitmap.Width, _bitmap.Height),
            new RectF(_charLeft, _charTop, _charLeft + _charWidth, _charTop + _charHeight), _paint);
        _paint.SetColorFilter(null);

        // 体积光影：朝离视线一侧压暗，营造受光面/背光面
        DrawVolumeShade(canvas, tiltX);

        canvas.Restore();
    }

    private void DrawContactShadow(Canvas canvas, float tiltX, float tiltY)
    {
        _paint!.SetShader(null);
        _paint.SetStyle(Paint.Style.Fill);
        var shadowW = _charWidth * 0.72f;
        var shadowH = _charHeight * 0.10f;
        var sx = _cx - shadowW / 2f - tiltX * UI.Dp(16f);
        var sy = _charTop + _charHeight * 0.96f - tiltY * UI.Dp(6f);
        var g = new RadialGradient(sx + shadowW / 2f, sy, shadowW * 0.6f,
            UI.ColorLongs(Color.Argb(150, 0, 0, 0), Color.Argb(0, 0, 0, 0)),
            new[] { 0f, 1f }, Shader.TileMode.Clamp);
        _paint.SetShader(g);
        canvas.DrawOval(sx, sy - shadowH / 2f, sx + shadowW, sy + shadowH / 2f, _paint);
        _paint.SetShader(null);
    }

    private void DrawVolumeShade(Canvas canvas, float tiltX)
    {
        if (_bitmap == null || _bitmap.IsRecycled) return;
        var k = Math.Abs(tiltX);
        if (k < 0.04f) return;
        var rect = new RectF(_charLeft, _charTop, _charLeft + _charWidth, _charTop + _charHeight);
        var a = (int)(95 * k);
        // tiltX>0 时右侧远离观察者 -> 右侧压暗
        Color darkSide = Color.Argb(a, 0, 0, 0);
        Color clear = Color.Argb(0, 0, 0, 0);
        Color c0 = tiltX > 0 ? clear : darkSide;
        Color c1 = tiltX > 0 ? darkSide : clear;
        _paint!.SetShader(new LinearGradient(rect.Left, 0, rect.Right, 0,
            UI.ColorLongs(c0, c1), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        _paint.SetStyle(Paint.Style.Fill);
        canvas.DrawRect(rect, _paint);
        _paint.SetShader(null);
    }

    private void DrawRarityGlow(Canvas canvas, float breathe)
    {
        if (_rarity < 2) return;
        var glow = PortraitLoader.GetGlow(_characterId);
        if (glow == null || glow.Bmp.IsRecycled || _bitmap == null || _bitmap.IsRecycled) return;

        var scale = _charWidth / _bitmap.Width;
        var gw = glow.Bmp.Width * scale;
        var gh = glow.Bmp.Height * scale;
        var gl = _charLeft + glow.OffX * scale;
        var gt = _charTop + glow.OffY * scale;

        _paint!.SetStyle(Paint.Style.Fill);
        _paint.SetShader(null);
        // 外层柔光随呼吸胀缩
        var spread = 1.08f + 0.06f * breathe;
        _paint.Color = Color.Argb((int)(40 + 30 * breathe), _rarityCore.R, _rarityCore.G, _rarityCore.B);
        canvas.DrawBitmap(glow.Bmp, null,
            new RectF(gl + (gw - gw * spread) / 2f, gt + (gh - gh * spread) / 2f,
                      gl + (gw + gw * spread) / 2f, gt + (gh + gh * spread) / 2f), _paint);
        // 内层贴身炽光
        _paint.Color = Color.Argb((int)(55 + 35 * breathe), _rarityEdge.R, _rarityEdge.G, _rarityEdge.B);
        canvas.DrawBitmap(glow.Bmp, null, new RectF(gl, gt, gl + gw, gt + gh), _paint);
    }

    private void DrawForegroundParticles(Canvas canvas, float dx, float dy, float breathe)
    {
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.SetShader(null);
        _paint.TextAlign = Paint.Align.Center;

        var (from, to, glow, _) = ElementTheme.For(_element);
        for (int i = _particles.Count - 1; i >= 0; i--)
        {
            var p = _particles[i];
            p.Life += p.Speed * 0.012f;
            if (p.Life > 1f)
            {
                p.Life = 0;
                p.X = (float)_rng.NextDouble();
                p.Y = (float)_rng.NextDouble();
            }

            // 前景粒子：以角色区域为中心散开，加上高系数视差
            var px = _cx + (p.X - 0.5f) * _w * 1.2f + dx * p.Z;
            var py = _cy + (p.Y - 0.5f) * _h * 1.1f + dy * p.Z - p.Life * _h * 0.25f;

            var lifeAlpha = p.Life < 0.2f ? p.Life / 0.2f : p.Life > 0.75f ? (1 - p.Life) / 0.25f : 1f;
            var alpha = (int)(lifeAlpha * 200 * (0.5f + 0.5f * MathF.Sin(_phase * 2.5f + p.Phase)));
            if (alpha <= 0) continue;

            var size = p.Size * (0.7f + 0.3f * MathF.Sin(_phase * 3f + p.Phase));
            _paint.TextSize = size;

            // 粒子类型：0=元素字/星，1=圆点，2=菱形
            var shape = p.Type switch
            {
                0 => _rarity >= 3 ? "✦" : _glyph,
                1 => "●",
                _ => "◆"
            };
            _paint.Color = Color.Argb(alpha, glow.R, glow.G, glow.B);
            canvas.DrawText(shape, px, py, _paint);
        }
    }

    private void DrawVignette(Canvas canvas)
    {
        var radius = Math.Max(_w, _h) * 0.75f;
        if (radius <= 0) return;
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.SetShader(new RadialGradient(_cx, _cy, radius,
            UI.ColorLongs(Color.Argb(0, 0, 0, 0), Color.Argb(0, 0, 0, 0), Color.Argb(160, 0, 0, 0)),
            new[] { 0f, 0.55f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawRect(0, 0, _w, _h, _paint);
        _paint.SetShader(null);
    }

    private void DrawFallback(Canvas canvas)
    {
        _paint!.SetShader(null);
        _paint.SetStyle(Paint.Style.Fill);
        _paint.TextSize = Math.Min(_w, _h) * 0.25f;
        _paint.Color = Color.Argb(200, 255, 255, 255);
        _paint.SetTypeface(Typeface.DefaultBold);
        _paint.TextAlign = Paint.Align.Center;
        canvas.DrawText(_glyph, _cx, _cy + _paint.TextSize * 0.35f, _paint);
    }

    private class Particle
    {
        public float X, Y, Z;
        public float Size;
        public float Speed;
        public float Phase;
        public float Life;
        public int Type;
    }

    private class GyroListener : Java.Lang.Object, ISensorEventListener
    {
        private readonly Parallax3DPortraitView _view;
        public GyroListener(Parallax3DPortraitView view) => _view = view;
        public void OnAccuracyChanged(Sensor? sensor, SensorStatus accuracy) { }
        public void OnSensorChanged(SensorEvent? e)
        {
            if (e?.Values == null || e.Values.Count < 2) return;
            _view.OnGyroscope(e.Values[0], e.Values[1]);
        }
    }
}
