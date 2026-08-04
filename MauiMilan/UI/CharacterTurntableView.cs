using Android.Content;
using Android.Graphics;
using Android.Views;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// 360° 沉浸式检视：把角色当成「有厚度的 3D 卡片」渲染，而非平面图片旋转。
/// 关键厚度逻辑：
///   - 正面随旋转角 θ 做 cos 透视收缩（faceW = W·|cosθ|）；
///   - 侧面厚度边条宽度 = T·|sinθ|，转到 ±90° 时恰好呈现一条「厚度边」；
///   - |cosθ|&lt;0（背面朝向）显示设计好的卡背，而非镜像正面。
/// 自动旋转（转台）+ 手指拖拽控制角度 + 缩放 + 技能迸发粒子。
/// </summary>
public class CharacterTurntableView : View
{
    private Bitmap? _bmp;
    private string _id = "";
    private int _rarity = 1;
    private string _element = "Flame";
    private string _glyph = "炎";
    private string _name = "";
    private Color _rarityCore, _rarityEdge, _glow;
    private WorldPalette _worldPal = WorldTheme.Shinwa;

    private float _w, _h, _cx, _cy;
    private float _angle = -22f;       // 当前旋转角（度）
    private bool _autoSpin = true;
    private float _zoom = 1f, _targetZoom = 1f;
    private float _phase;

    private readonly List<Spark> _sparks = new();
    private Random _rng = new();

    private Paint? _p;
    // 立绘融合滤镜：冷调去饱和（与 PortraitGrade.Standard 一致），让 AI 立绘统一融入暗夜背光。
    private readonly ColorMatrixColorFilter? _unityFilter =
        new ColorMatrixColorFilter(PortraitGrade.ToningMatrix(PortraitGrade.Grade.Standard));

    // 自动旋转常驻：地面阴影 + 背后光晕的渐变只依赖尺寸/稀有度，缓存避免每帧分配（P2）。
    private RadialGradient? _groundGrad;
    private RadialGradient? _backGrad;
    private float _gradW = -1;
    private int _gradRarity = -1;
    private float _groundBaseY;

    public CharacterTurntableView(Context context) : base(context)
    {
        SetWillNotDraw(false);
        _rarityCore = Color.Argb(255, 255, 200, 87);
        _rarityEdge = Color.Argb(255, 232, 184, 75);
        _glow = Color.ParseColor("#FF5252");
    }

    public CharacterTurntableView Bind(OwnedCharacterView ch)
    {
        _id = ch.Save.CharacterId;
        _worldPal = WorldTheme.For(ch.World);
        _rarity = ch.Rarity;
        _element = ch.Element;
        _glyph = ElementTheme.For(ch.Element).glyph;
        _name = ch.Name;
        _glow = ElementTheme.For(ch.Element).glow;
        (_rarityCore, _rarityEdge) = _rarity switch
        {
            4 => (Color.Argb(255, 255, 200, 87), Color.Argb(255, 232, 184, 75)),
            3 => (Color.Argb(255, 199, 155, 255), Color.Argb(255, 154, 107, 255)),
            2 => (Color.Argb(255, 127, 196, 255), Color.Argb(255, 74, 144, 217)),
            _ => (Color.Argb(255, 232, 226, 242), Color.Argb(255, 200, 200, 220))
        };
        _bmp = PortraitLoader.Get(ch.Save.CharacterId);
        return this;
    }

    public void ToggleAutoSpin() { _autoSpin = !_autoSpin; Invalidate(); }
    public void ToggleZoom() { _targetZoom = _targetZoom > 1.01f ? 1f : 1.15f; Invalidate(); }
    public void TriggerBurst()
    {
        _rng = new Random(_id.GetHashCode() ^ (int)(_phase * 1000));
        for (int i = 0; i < 28; i++)
        {
            float ang = (float)(_rng.NextDouble() * Math.PI * 2);
            float sp = UI.Dp(2) + (float)_rng.NextDouble() * UI.Dp(7);
            _sparks.Add(new Spark
            {
                X = _cx,
                Y = _cy,
                Vx = (float)Math.Cos(ang) * sp,
                Vy = (float)Math.Sin(ang) * sp - UI.Dp(2),
                Life = 1f,
                Size = UI.Dp(2) + (float)_rng.NextDouble() * UI.Dp(4)
            });
        }
        Invalidate();
    }

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _w = w; _h = h; _cx = w / 2f; _cy = h / 2f;
        // 尺寸变化使缓存渐变失效（下次绘制重建）
        _groundGrad?.Dispose(); _groundGrad = null;
        _backGrad?.Dispose(); _backGrad = null;
        _gradW = -1; _gradRarity = -1;
    }

    protected override void OnDetachedFromWindow()
    {
        base.OnDetachedFromWindow();
        _groundGrad?.Dispose(); _groundGrad = null;
        _backGrad?.Dispose(); _backGrad = null;
        _gradW = -1; _gradRarity = -1;
    }

    public override bool OnTouchEvent(MotionEvent? e)
    {
        if (e == null) return base.OnTouchEvent(e);
        switch (e.Action)
        {
            case MotionEventActions.Down:
                _lastX = e.GetX();
                _autoSpin = false;
                return true;
            case MotionEventActions.Move:
            {
                float dx = e.GetX() - _lastX;
                _lastX = e.GetX();
                _angle += dx * 0.7f;
                Invalidate();
                return true;
            }
        }
        return base.OnTouchEvent(e);
    }
    private float _lastX;

    protected override void OnDraw(Canvas canvas)
    {
        if (_w == 0 || _h == 0) return;
        _p ??= new Paint { AntiAlias = true, FilterBitmap = true };

        _phase += 0.03f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        if (_autoSpin) { _angle += 0.7f; if (_angle > 360) _angle -= 360; }
        _zoom += (_targetZoom - _zoom) * 0.15f;

        canvas.Save();
        if (Math.Abs(_zoom - 1f) > 0.002f) canvas.Scale(_zoom, _zoom, _cx, _cy);
        DrawFigure(canvas);
        canvas.Restore();

        bool keep = _autoSpin || _sparks.Count > 0 || Math.Abs(_targetZoom - _zoom) > 0.005f;
        if (keep) PostInvalidateDelayed(16);
    }

    private void DrawFigure(Canvas canvas)
    {
        float aspect = _bmp != null ? (float)_bmp.Width / _bmp.Height : 0.714f;
        float H = _h * 0.82f;
        float W = H * aspect;
        W = Math.Min(W, _w * 0.82f);
        H = W / aspect;

        float bob = (float)Math.Sin(_phase * 1.2f) * UI.Dp(6f);
        float cx = _cx;
        float cy = _cy + bob;

        float rad = _angle * MathF.PI / 180f;
        float c = (float)Math.Cos(rad);
        float s = (float)Math.Sin(rad);
        float faceW = Math.Abs(c) * W;
        float T = Math.Min(W * 0.24f, UI.Dp(46f));   // 卡片厚度（加强，立体边条更明显）
        bool leftSide = s >= 0;
        float sideW = Math.Abs(s) * T;

        DrawGroundShadow(canvas, cx, W);
        DrawBackGlow(canvas, cx, W);

        // 侧面厚度边条（贴在正面一侧，转到 ±90° 时呈现整条厚度）
        if (sideW > 0.5f)
        {
            float faceL = cx - faceW / 2f;
            float faceR = cx + faceW / 2f;
            float bx0 = leftSide ? faceL - sideW : faceR;
            float bx1 = leftSide ? faceL : faceR + sideW;
            _p!.SetShader(new LinearGradient(bx0, 0, bx1, 0,
                UI.ColorLongs(_rarityEdge, Color.Argb(255, 14, 12, 22)),
                new[] { 0f, 1f }, Shader.TileMode.Clamp));
            _p.SetStyle(Paint.Style.Fill);
            canvas.DrawRect(Math.Min(bx0, bx1), cy - H / 2f, Math.Max(bx0, bx1), cy + H / 2f, _p);
            _p.SetShader(null);
        }

        // 正面 / 背面
        float fx = cx - faceW / 2f;
        float fy = cy - H / 2f;
        if (c >= 0)
        {
            if (_bmp != null && !_bmp.IsRecycled)
            {
                _p!.SetShader(null);
                _p.SetStyle(Paint.Style.Fill);
                _p.Color = Color.White;
                if (_unityFilter != null) _p.SetColorFilter(_unityFilter);
                canvas.DrawBitmap(_bmp, new Rect(0, 0, _bmp.Width, _bmp.Height),
                    new RectF(fx, fy, fx + faceW, fy + H), _p);
                _p.SetColorFilter(null);
            }
            else
            {
                DrawGlyphPlaceholder(canvas, fx, fy, faceW, H);
            }
        }
        else
        {
            DrawCardBack(canvas, new RectF(fx, fy, fx + faceW, fy + H));
        }

        DrawSparks(canvas);
    }

    private void DrawGroundShadow(Canvas canvas, float cx, float W)
    {
        if (W <= 0) return;
        _p!.SetShader(null);
        _p.SetStyle(Paint.Style.Fill);
        if (_groundGrad == null || Math.Abs(_gradW - W) > 0.5f || _gradRarity != _rarity)
        {
            _groundGrad?.Dispose();
            float aspect = _bmp != null ? (float)_bmp.Width / _bmp.Height : 0.714f;
            float hh = W / aspect;
            _groundBaseY = _cy + hh / 2f;
            _groundGrad = new RadialGradient(cx, _groundBaseY, W * 0.5f,
                UI.ColorLongs(Color.Argb(120, 0, 0, 0), Color.Argb(0, 0, 0, 0)),
                new[] { 0f, 1f }, Shader.TileMode.Clamp);
            _gradW = W; _gradRarity = _rarity;
        }
        _p.SetShader(_groundGrad);
        canvas.DrawOval(cx - W * 0.5f, _groundBaseY - UI.Dp(8), cx + W * 0.5f, _groundBaseY + UI.Dp(10), _p);
        _p.SetShader(null);
    }

    private void DrawBackGlow(Canvas canvas, float cx, float W)
    {
        if (W <= 0) return;
        _p!.SetShader(null);
        _p.SetStyle(Paint.Style.Fill);
        if (_backGrad == null || Math.Abs(_gradW - W) > 0.5f || _gradRarity != _rarity)
        {
            _backGrad?.Dispose();
            _backGrad = new RadialGradient(cx, _cy, W * 0.75f,
                UI.ColorLongs(Color.Argb(70, _rarityCore.R, _rarityCore.G, _rarityCore.B), Color.Argb(0, 0, 0, 0)),
                new[] { 0f, 1f }, Shader.TileMode.Clamp);
            _gradW = W; _gradRarity = _rarity;
        }
        _p.SetShader(_backGrad);
        canvas.DrawCircle(cx, _cy, W * 0.85f, _p);
        _p.SetShader(null);
    }

    private void DrawGlyphPlaceholder(Canvas canvas, float x, float y, float w, float h)
    {
        _p!.SetShader(null);
        _p.SetStyle(Paint.Style.Fill);
        _p.Color = Color.Argb(40, _rarityCore.R, _rarityCore.G, _rarityCore.B);
        canvas.DrawRoundRect(x, y, x + w, y + h, UI.Dp(16), UI.Dp(16), _p);
        _p.SetTypeface(Typeface.DefaultBold);
        _p.Color = _glow;
        _p.TextSize = Math.Min(w, h) * 0.4f;
        _p.TextAlign = Paint.Align.Center;
        canvas.DrawText(_glyph, x + w / 2f, y + h / 2f + _p.TextSize * 0.34f, _p);
        _p.SetTypeface(null);
        _p.TextAlign = Paint.Align.Left;
    }

    private void DrawCardBack(Canvas canvas, RectF r)
    {
        var pal = _worldPal;
        // 深底：世界色暗渐变（世界 Secondary + 稀有度暗底），取代原来单调的稀有度直染
        _p!.SetShader(new LinearGradient(r.Left, r.Top, r.Right, r.Bottom,
            UI.ColorLongs(
                Color.Argb(255, 18, 14, 32),
                Color.Argb(255, pal.Secondary.R, pal.Secondary.G, pal.Secondary.B),
                Color.Argb(255, 10, 8, 18)),
            new[] { 0f, 0.5f, 1f }, Shader.TileMode.Clamp));
        _p.SetStyle(Paint.Style.Fill);
        canvas.DrawRoundRect(r, UI.Dp(16), UI.Dp(16), _p);
        _p.SetShader(null);

        // 世界主题纹饰：神话界祥云印章 / 虚空界能量星云 / 铁幕界铆钉机械
        switch (pal.DecorationStyle)
        {
            case WorldDecorationStyle.InkBrush: DrawCardBackShinwa(canvas, r, pal); break;
            case WorldDecorationStyle.EnergyLines: DrawCardBackAether(canvas, r, pal); break;
            case WorldDecorationStyle.Rivets: DrawCardBackIronveil(canvas, r, pal); break;
        }

        // 中央元素字形（核心，覆盖在纹饰之上）
        _p.SetStyle(Paint.Style.Fill);
        _p.SetTypeface(Typeface.DefaultBold);
        _p.Color = pal.Glow;
        _p.TextSize = Math.Min(r.Width(), r.Height()) * 0.40f;
        _p.TextAlign = Paint.Align.Center;
        canvas.DrawText(_glyph, r.CenterX(), r.CenterY() + _p.TextSize * 0.34f, _p);
        _p.SetTypeface(null);

        // 稀有度/世界描边
        _p.SetStyle(Paint.Style.Stroke);
        _p.StrokeWidth = UI.Dp(3);
        _p.Color = Color.Argb(220, pal.Secondary.R, pal.Secondary.G, pal.Secondary.B);
        canvas.DrawRoundRect(r, UI.Dp(16), UI.Dp(16), _p);

        // 名字
        _p.SetStyle(Paint.Style.Fill);
        _p.Color = Color.White;
        _p.TextSize = UI.Dp(15);
        canvas.DrawText(_name, r.CenterX(), r.Bottom - UI.Dp(18), _p);
        _p.TextAlign = Paint.Align.Left;
    }

    // ── 神话界：四角祥云弧纹（印章风）──
    private void DrawCardBackShinwa(Canvas canvas, RectF r, WorldPalette pal)
    {
        _p!.SetStyle(Paint.Style.Stroke);
        _p.StrokeWidth = UI.Dp(2.5f);
        _p.Color = Color.Argb(130, pal.Secondary.R, pal.Secondary.G, pal.Secondary.B);
        float m = r.Width() * 0.12f;
        float rad = r.Width() * 0.15f;
        var arc = new RectF();
        arc.Set(r.Left + m, r.Top + m, r.Left + m + rad * 2, r.Top + m + rad * 2);
        canvas.DrawArc(arc, 180, 90, false, _p);
        arc.Set(r.Right - m - rad * 2, r.Top + m, r.Right - m, r.Top + m + rad * 2);
        canvas.DrawArc(arc, 270, 90, false, _p);
        arc.Set(r.Left + m, r.Bottom - m - rad * 2, r.Left + m + rad * 2, r.Bottom - m);
        canvas.DrawArc(arc, 90, 90, false, _p);
        arc.Set(r.Right - m - rad * 2, r.Bottom - m - rad * 2, r.Right - m, r.Bottom - m);
        canvas.DrawArc(arc, 0, 90, false, _p);
    }

    // ── 虚空界：能量线 + 星点（星云风，随相位缓动）──
    private void DrawCardBackAether(Canvas canvas, RectF r, WorldPalette pal)
    {
        var cx = r.CenterX(); var cy = r.CenterY();
        _p!.SetStyle(Paint.Style.Stroke);
        _p.StrokeWidth = UI.Dp(1.5f);
        const int lines = 12;
        for (int i = 0; i < lines; i++)
        {
            float a = i * MathF.PI * 2f / lines + _phase * 0.3f;
            float r0 = r.Width() * 0.20f;
            float r1 = r.Width() * 0.46f;
            _p.Color = Color.Argb(85, pal.Glow.R, pal.Glow.G, pal.Glow.B);
            canvas.DrawLine(cx + MathF.Cos(a) * r0, cy + MathF.Sin(a) * r0,
                            cx + MathF.Cos(a) * r1, cy + MathF.Sin(a) * r1, _p);
        }
        _p.SetStyle(Paint.Style.Fill);
        for (int i = 0; i < 10; i++)
        {
            float a = i * 2.39996f + _phase * 0.2f;
            float rr = r.Width() * (0.22f + 0.24f * ((i * 0.17f) % 1f));
            _p.Color = Color.Argb(170, pal.Glow.R, pal.Glow.G, pal.Glow.B);
            canvas.DrawCircle(cx + MathF.Cos(a) * rr, cy + MathF.Sin(a) * rr, UI.Dp(1.6f), _p);
        }
    }

    // ── 铁幕界：面板分割 + 四角铆钉（机械风）──
    private void DrawCardBackIronveil(Canvas canvas, RectF r, WorldPalette pal)
    {
        float inset = r.Width() * 0.10f;
        var inner = new RectF(r.Left + inset, r.Top + inset, r.Right - inset, r.Bottom - inset);
        _p!.SetStyle(Paint.Style.Stroke);
        _p.StrokeWidth = UI.Dp(1.5f);
        _p.Color = Color.Argb(110, pal.Secondary.R, pal.Secondary.G, pal.Secondary.B);
        canvas.DrawRoundRect(inner, UI.Dp(10), UI.Dp(10), _p);
        _p.SetStyle(Paint.Style.Fill);
        float dot = UI.Dp(4);
        var rivets = new[] { (inner.Left, inner.Top), (inner.Right, inner.Top),
                             (inner.Left, inner.Bottom), (inner.Right, inner.Bottom) };
        foreach (var (dx, dy) in rivets)
        {
            _p.Color = Color.Argb(210, pal.Secondary.R, pal.Secondary.G, pal.Secondary.B);
            canvas.DrawCircle(dx, dy, dot, _p);
            _p.Color = Color.Argb(130, 255, 255, 255);
            canvas.DrawCircle(dx - dot * 0.3f, dy - dot * 0.3f, dot * 0.4f, _p);
        }
    }

    private void DrawSparks(Canvas canvas)
    {
        _p!.SetStyle(Paint.Style.Fill);
        for (int i = _sparks.Count - 1; i >= 0; i--)
        {
            var sp = _sparks[i];
            sp.X += sp.Vx;
            sp.Y += sp.Vy;
            sp.Vy += UI.Dp(0.3f);
            sp.Life -= 0.02f;
            if (sp.Life <= 0) { _sparks.RemoveAt(i); continue; }
            _p.Color = Color.Argb((int)(sp.Life * 220), _glow.R, _glow.G, _glow.B);
            canvas.DrawCircle(sp.X, sp.Y, sp.Size * sp.Life, _p);
        }
    }

    private class Spark
    {
        public float X, Y, Vx, Vy, Life, Size;
    }
}
