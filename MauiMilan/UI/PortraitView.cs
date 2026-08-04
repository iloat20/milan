using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui.Services;
using System.Collections.Generic;

namespace Milan.Maui;

/// <summary>
/// Loads and caches AI-generated character portrait bitmaps.
/// </summary>
public static class PortraitLoader
{
    private const int MaxCacheEntries = 24; // 上限防止大图无界累积占用 native 内存
    private static readonly Dictionary<string, Bitmap> _cache = new();
    private static readonly LinkedList<string> _lru = new();
    private static readonly object _lock = new();

    public static Bitmap? Get(string characterId)
    {
        lock (_lock)
        {
            if (_cache.TryGetValue(characterId, out var cached))
            {
                _lru.Remove(characterId);
                _lru.AddFirst(characterId);
                return cached;
            }
        }
        var bmp = LoadFromResources(characterId);
        if (bmp != null)
        {
            lock (_lock)
            {
                _cache[characterId] = bmp;
                _lru.AddFirst(characterId);
                // LRU 淘汰。不主动 Recycle：视图可能仍持有引用，交给 GC 回收更安全。
                while (_lru.Count > MaxCacheEntries)
                {
                    var oldest = _lru.Last!.Value;
                    _lru.RemoveLast();
                    _cache.Remove(oldest);
                }
            }
        }
        return bmp;
    }

    private static Bitmap? LoadFromResources(string characterId)
    {
        try
        {
            var ctx = MauiApp.Context;
            var res = ctx.Resources;
            var id = res.GetIdentifier(characterId, "drawable", ctx.PackageName);
            if (id == 0) return null;
            using var stream = res.OpenRawResource(id);
            return BitmapFactory.DecodeStream(stream, null, new BitmapFactory.Options { InSampleSize = 1 });
        }
        catch { return null; }
    }

    public static void ClearCache()
    {
        lock (_lock)
        {
            foreach (var bmp in _cache.Values) bmp.Recycle();
            _cache.Clear();
            _lru.Clear();
            _glowCache.Clear();
        }
    }

    // ── 剪影辉光遮罩 ────────────────────────────────────────────────
    // 硬件加速画布上 BlurMaskFilter 不可靠（API 21~27 直接被忽略），
    // 因此改用 Bitmap.ExtractAlpha(paint, offset) 一次性离屏烘焙出模糊后的
    // ALPHA_8 剪影，之后每帧只是普通 drawBitmap + 着色，既贴合角色轮廓又便宜。
    private const int MaxGlowEntries = 8;
    private static readonly Dictionary<string, GlowMask?> _glowCache = new();

    public sealed class GlowMask
    {
        public Bitmap Bmp = null!;
        public int OffX, OffY;
    }

    public static GlowMask? GetGlow(string characterId)
    {
        if (string.IsNullOrEmpty(characterId)) return null;
        lock (_lock)
        {
            if (_glowCache.TryGetValue(characterId, out var hit)) return hit;
        }

        GlowMask? mask = null;
        try
        {
            var src = Get(characterId);
            if (src != null && !src.IsRecycled)
            {
                var radius = Math.Clamp(Math.Max(src.Width, src.Height) * 0.03f, 4f, 26f);
                using var p = new Paint();
                p.SetMaskFilter(new BlurMaskFilter(radius, BlurMaskFilter.Blur.Normal!));
                var offset = new int[2];
                var blurred = src.ExtractAlpha(p, offset);
                if (blurred != null)
                    mask = new GlowMask { Bmp = blurred, OffX = offset[0], OffY = offset[1] };
            }
        }
        catch { mask = null; }

        lock (_lock)
        {
            if (_glowCache.Count >= MaxGlowEntries) _glowCache.Clear();
            _glowCache[characterId] = mask;
        }
        return mask;
    }
}

/// <summary>
/// Character portrait with effects that cling to the character silhouette:
/// - Glow hugs the character body, not a circle behind it
/// - Particles emerge from the character and rise upward
/// - Rim light outlines the character edges
/// </summary>
public class PortraitView : View
{
    private Bitmap? _bitmap;
    private string _characterId = "";
    private int _rarity = 1;
    private string _element = "Flame";
    private string _glyph = "炎";
    private float _phase;
    private Paint? _paint;
    private int _w, _h;
    private bool _bursting;
    private float _burstPhase;

    // 立绘统一色调：冷调去饱和矩阵（PortraitGrade），让 AI 立绘融入暮紫夜色。
    // 只作用于立绘本体绘制，不参与辉光/粒子，避免整体染色（沿用旧约束）。
    private ColorMatrixColorFilter? _toneFilter;

    // Rarity-based colors
    private Color _rarityCoreColor;
    private Color _rarityEdgeColor;

    // Particle list - each particle has position relative to character
    private List<Particle> _particles = new();
    private Random _rng = new();

    // Character bounds (where the bitmap is drawn)
    private float _charLeft, _charTop, _charRight, _charBottom;
    private float _charWidth, _charHeight;
    private PortraitLoader.GlowMask? _glow;

    // #37: 每帧复用的几何对象，避免 OnDraw 内反复 new 造成 GC 抖动。
    private readonly RectF _tmpRect = new();
    private readonly RectF _tmpRect2 = new();
    private readonly Rect _tmpSrc = new();
    private bool _framePending;

    // 暗角 vignette 缓存（绑定尺寸，避免每帧 new 渐变，守优化铁律）
    private RadialGradient? _vignette;
    private int _vigW, _vigH;

    /// <summary>立绘在容器内的占比（contain 适配后再乘该系数）。</summary>
    public float FitRatio { get; set; } = 0.98f;
    /// <summary>垂直锚点：0=贴顶，0.5=居中，1=贴底。立绘一般略微偏下更像站姿。</summary>
    public float VerticalBias { get; set; } = 0.52f;
    /// <summary>是否以 cover 方式填满容器（放大裁剪，立绘更大气）。Hero 等需要立绘顶满的区域使用。</summary>
    public bool Cover { get; set; } = false;
    /// <summary>是否绘制脚下的接地投影。</summary>
    public bool GroundShadow { get; set; } = true;

    public PortraitView(Context context) : base(context)
    {
        SetWillNotDraw(false);
        _rarityCoreColor = Color.Argb(255, 255, 215, 0);
        _rarityEdgeColor = Color.Argb(255, 255, 140, 0);
        // 冷调去饱和（Standard 档）：降饱和 ~15% + 轻微偏冷，呼应暗夜神性·诸神黄昏。
        _toneFilter = new ColorMatrixColorFilter(PortraitGrade.ToningMatrix(PortraitGrade.Grade.Standard));
    }

    public PortraitView Bind(CharacterDataEntry def)
    {
        _characterId = def.CharacterId;
        _rarity = def.BaseRarity;
        _element = def.Element;
        var (_, _, glow, glyph) = ElementTheme.For(def.Element);
        _glyph = glyph;
        _bitmap = PortraitLoader.Get(def.CharacterId);
        _glow = PortraitLoader.GetGlow(def.CharacterId);
        ApplyRarityColors();
        InitParticles();
        CalcCharacterBounds();
        Invalidate();
        return this;
    }

    public PortraitView Bind(OwnedCharacterView ch)
    {
        _characterId = ch.Save.CharacterId;
        _rarity = ch.Rarity;
        _element = ch.Element;
        var (_, _, glow, glyph) = ElementTheme.For(ch.Element);
        _glyph = glyph;
        _bitmap = PortraitLoader.Get(ch.Save.CharacterId);
        _glow = PortraitLoader.GetGlow(ch.Save.CharacterId);
        ApplyRarityColors();
        InitParticles();
        CalcCharacterBounds();
        Invalidate();
        return this;
    }

    private void ApplyRarityColors()
    {
        // 与 AppTheme.RarityColor 保持一致：UR 熔金 / SSR 暮紫 / SR 霜蓝 / R 苍白
        (_rarityCoreColor, _rarityEdgeColor) = _rarity switch
        {
            4 => (Color.Argb(255, 255, 200, 87), Color.Argb(255, 232, 184, 75)),   // 熔金
            3 => (Color.Argb(255, 199, 155, 255), Color.Argb(255, 154, 107, 255)),  // 暮紫
            2 => (Color.Argb(255, 127, 196, 255), Color.Argb(255, 74, 144, 217)),   // 霜蓝
            _ => (Color.Argb(255, 232, 226, 242), Color.Argb(255, 200, 200, 220))   // 苍白
        };
    }

    private void InitParticles()
    {
        _particles.Clear();
        _rng = new Random(_characterId.GetHashCode());
        var count = _rarity switch { 4 => 20, 3 => 14, 2 => 8, _ => 0 };
        for (int i = 0; i < count; i++)
        {
            _particles.Add(new Particle
            {
                OffsetX = (float)(_rng.NextDouble() * 2 - 1), // -1 to 1 across character width
                OffsetY = (float)(_rng.NextDouble() * 2 - 1), // -1 to 1 across character height
                Speed = 0.3f + (float)_rng.NextDouble() * 0.7f,
                Size = 3 + _rng.Next(5),
                Phase = (float)(_rng.NextDouble() * MathF.PI * 2),
                Life = (float)_rng.NextDouble()
            });
        }
    }

    public void TriggerBurst()
    {
        _bursting = true;
        _burstPhase = 0;
        // Add burst particles
        for (int i = 0; i < 12; i++)
        {
            _particles.Add(new Particle
            {
                OffsetX = (float)(_rng.NextDouble() * 1.6 - 0.8),
                OffsetY = (float)(_rng.NextDouble() * 1.6 - 0.8),
                Speed = 1.5f + (float)_rng.NextDouble() * 2f,
                Size = 4 + _rng.Next(6),
                Phase = (float)(_rng.NextDouble() * MathF.PI * 2),
                Life = 0,
                IsBurst = true
            });
        }
        Invalidate();
    }

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _w = w; _h = h;
        CalcCharacterBounds();
    }

    private void CalcCharacterBounds()
    {
        if (_w <= 0 || _h <= 0) return;

        if (_bitmap == null || _bitmap.IsRecycled)
        {
            _charWidth = _w * 0.5f;
            _charHeight = _h * 0.6f;
            _charLeft = (_w - _charWidth) / 2;
            _charTop = (_h - _charHeight) / 2;
        }
        else
        {
            // contain 适配：始终完整装进容器；cover 适配：放大填满容器（多余裁切，立绘更大气）
            var bmpW = _bitmap.Width;
            var bmpH = _bitmap.Height;
            var contain = Math.Min((float)_w / bmpW, (float)_h / bmpH);
            var cover = Math.Max((float)_w / bmpW, (float)_h / bmpH);
            var scale = (Cover ? cover : contain) * (Cover ? 1f : FitRatio);
            _charWidth = bmpW * scale;
            _charHeight = bmpH * scale;
            _charLeft = (_w - _charWidth) / 2f;
            _charTop = (_h - _charHeight) * Math.Clamp(VerticalBias, 0f, 1f);
        }
        _charRight = _charLeft + _charWidth;
        _charBottom = _charTop + _charHeight;
        // 尺寸变了，缓存的台座渐变（绑定了绝对坐标）必须失效重建
        _ground?.Dispose();
        _ground = null;
        _groundRx = -1f;
    }

    protected override void OnDraw(Canvas canvas)
    {
        _framePending = false;
        if (_w <= 0 || _h <= 0) return;
        if (_paint == null) _paint = new Paint { AntiAlias = true, FilterBitmap = true };
        _phase += 0.025f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;

        // Clip to view bounds so effects don't bleed into adjacent views
        canvas.Save();
        canvas.ClipRect(0, 0, _w, _h);

        var breathe = 0.5f + 0.5f * MathF.Sin(_phase * 0.8f);

        // 1. 接地投影（先画，压在角色脚下）
        if (GroundShadow) DrawGroundShadow(canvas);

        // 2. 剪影辉光 —— 只沿角色轮廓外扩，不再是覆盖整块画面的色板
        DrawCharacterGlow(canvas, breathe);

        // 3. 轮廓光（同样基于剪影）
        DrawRimLight(canvas, breathe);

        // 4. The portrait bitmap
        if (_bitmap != null && !_bitmap.IsRecycled)
            DrawBitmap(canvas);
        else
            DrawFallback(canvas);
        DrawGrade(canvas);

        // 5. Particles emerging from character body
        DrawParticles(canvas);

        // 5. Burst effect
        if (_bursting) DrawBurst(canvas);

        canvas.Restore(); // restore clip
        // 节流到 ~30fps：十连结果页可能同时存在十几个立绘视图，60fps 会明显掉帧
        ScheduleFrame();
    }

    // ---- 动画生命周期：脱离窗口/不可见时停止重绘，避免后台耗电 ----
    private bool _animating = true;

    /// <summary>预约下一帧；重复调用不会叠加出多条并行动画链。</summary>
    private void ScheduleFrame()
    {
        if (!_animating || _framePending) return;
        _framePending = true;
        PostInvalidateDelayed(33);
    }

    protected override void OnAttachedToWindow()
    {
        base.OnAttachedToWindow();
        _animating = true;
        _framePending = false;
        ScheduleFrame();
    }

    protected override void OnDetachedFromWindow()
    {
        base.OnDetachedFromWindow();
        _animating = false;
        _framePending = false;
        _ground?.Dispose();
        _ground = null;
        _groundRx = -1f;
        _vignette?.Dispose();
        _vignette = null;
        _vigW = 0; _vigH = 0;
    }

    protected override void OnWindowVisibilityChanged(ViewStates visibility)
    {
        base.OnWindowVisibilityChanged(visibility);
        _animating = visibility == ViewStates.Visible;
        _framePending = false;
        if (_animating) ScheduleFrame();
    }

    /// <summary>
    /// 剪影辉光：用离屏烘焙的模糊 alpha 图沿角色轮廓着色。
    /// 之前这里画的是一整块稀有度色圆角矩形 —— 立绘 PNG 的透明区域会把它整片透出来，
    /// 看上去就像角色被蒙了一层蓝紫色遮罩，这正是要根除的问题。
    /// </summary>
    private void DrawCharacterGlow(Canvas canvas, float breathe)
    {
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.SetShader(null);

        if (_glow != null && !_glow.Bmp.IsRecycled && _bitmap != null && !_bitmap.IsRecycled)
        {
            var scale = _charWidth / _bitmap.Width;
            var gw = _glow.Bmp.Width * scale;
            var gh = _glow.Bmp.Height * scale;
            var gl = _charLeft + _glow.OffX * scale;
            var gt = _charTop + _glow.OffY * scale;
            var cx = gl + gw / 2f;
            var cy = gt + gh / 2f;

            // 外层：大范围柔光，随呼吸轻微胀缩
            var spread = 1.06f + 0.05f * breathe;
            var w2 = gw * spread / 2f;
            var h2 = gh * spread / 2f;
            _paint.Color = Color.Argb((int)(48 + 34 * breathe),
                _rarityCoreColor.R, _rarityCoreColor.G, _rarityCoreColor.B);
            _tmpRect.Set(cx - w2, cy - h2, cx + w2, cy + h2);
            canvas.DrawBitmap(_glow.Bmp, null, _tmpRect, _paint);

            // 内层：贴身收紧，颜色更浓，形成边缘炽光
            _paint.Color = Color.Argb((int)(60 + 40 * breathe),
                _rarityEdgeColor.R, _rarityEdgeColor.G, _rarityEdgeColor.B);
            _tmpRect.Set(gl, gt, gl + gw, gt + gh);
            canvas.DrawBitmap(_glow.Bmp, null, _tmpRect, _paint);
            return;
        }

        // 无立绘位图时的兜底：柔和径向光晕（不是硬边色块）
        var rcx = (_charLeft + _charRight) / 2f;
        var rcy = (_charTop + _charBottom) / 2f;
        var radius = Math.Max(_charWidth, _charHeight) * 0.62f * (1f + 0.05f * breathe);
        if (radius <= 0) return;
        using (var sh = new RadialGradient(rcx, rcy, radius,
            UI.ColorLongs(
                Color.Argb((int)(70 + 40 * breathe), _rarityCoreColor.R, _rarityCoreColor.G, _rarityCoreColor.B),
                Color.Argb((int)(28 + 18 * breathe), _rarityEdgeColor.R, _rarityEdgeColor.G, _rarityEdgeColor.B),
                Color.Argb(0, 0, 0, 0)),
            new[] { 0f, 0.55f, 1f }, Shader.TileMode.Clamp))
        {
            _paint.SetShader(sh);
            canvas.DrawCircle(rcx, rcy, radius, _paint);
            _paint.SetShader(null);
        }
    }

    /// <summary>脚下环境光台：先暗投影压住脚，再叠一层稀有度辉光台座，让角色"站"在发光台上而非飘着。</summary>
    private void DrawGroundShadow(Canvas canvas)
    {
        if (_charWidth <= 0) return;
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.SetShader(null);
        _paint.SetColorFilter(null);
        var cx = (_charLeft + _charRight) / 2f;
        var cy = _charBottom - _charHeight * 0.01f;
        var rx = _charWidth * 0.42f;
        var ry = _charHeight * 0.05f;
        if (rx <= 0 || ry <= 0) return;
        // 暗投影
        for (int i = 3; i >= 1; i--)
        {
            var k = i / 3f;
            _paint.Color = Color.Argb((int)(58 * (1.05f - k)), 0, 0, 0);
            _tmpRect.Set(cx - rx * (0.7f + k * 0.5f), cy - ry * (0.7f + k * 0.6f),
                cx + rx * (0.7f + k * 0.5f), cy + ry * (0.7f + k * 0.6f));
            canvas.DrawOval(_tmpRect, _paint);
        }
        // 稀有度辉光台座（径向渐变椭圆，中心亮→透明）
        if (_ground == null || Math.Abs(_groundRx - rx) > 0.5f || _groundColor != _rarityCoreColor.ToArgb())
        {
            _ground?.Dispose();
            _ground = new RadialGradient(cx, cy, rx,
                UI.ColorLong(Color.Argb(64, _rarityCoreColor.R, _rarityCoreColor.G, _rarityCoreColor.B)),
                UI.ColorLong(Color.Argb(0, _rarityCoreColor.R, _rarityCoreColor.G, _rarityCoreColor.B)),
                Shader.TileMode.Clamp);
            _groundRx = rx;
            _groundColor = _rarityCoreColor.ToArgb();
        }
        _paint.SetShader(_ground);
        _tmpRect.Set(cx - rx, cy - ry, cx + rx, cy + ry);
        canvas.DrawOval(_tmpRect, _paint);
        _paint.SetShader(null);
    }

    private RadialGradient? _ground;
    private float _groundRx = -1f;
    private int _groundColor;

    /// <summary>暗角：立绘边缘沉入暮紫夜色，呼应电影纵深（缓存渐变，绑定尺寸）。</summary>
    private void DrawGrade(Canvas canvas)
    {
        var radius = Math.Max(_w, _h) * 0.78f;
        if (radius <= 0) return;
        if (_vigW != _w || _vigH != _h || _vignette == null)
        {
            _vignette?.Dispose();
            _vignette = new RadialGradient(_w / 2f, _h / 2f, radius,
                UI.ColorLongs(
                    Color.Argb(0, 0, 0, 0),
                    Color.Argb(0, 0, 0, 0),
                    Color.Argb(150, 0x0B, 0x06, 0x12)),
                new[] { 0f, 0.55f, 1f }, Shader.TileMode.Clamp);
            _vigW = _w; _vigH = _h;
        }
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.SetShader(_vignette);
        canvas.DrawRect(0, 0, _w, _h, _paint);
        _paint.SetShader(null);
    }

    private void DrawBitmap(Canvas canvas)
    {
        if (_bitmap == null || _bitmap.IsRecycled) return;
        // 清掉上一步辉光留下的着色/shader，否则立绘会被整体染色
        _paint!.SetShader(null);
        _paint.Color = Color.White;
        _paint.SetStyle(Paint.Style.Fill);
        // 通透化调校：仅作用于立绘本体，绘制后立即撤销，不影响后续粒子/辉光
        if (_toneFilter != null) _paint.SetColorFilter(_toneFilter);
        _tmpSrc.Set(0, 0, _bitmap.Width, _bitmap.Height);
        _tmpRect2.Set(_charLeft, _charTop, _charRight, _charBottom);
        canvas.DrawBitmap(_bitmap, _tmpSrc, _tmpRect2, _paint);
        _paint.SetColorFilter(null);
    }

    private void DrawFallback(Canvas canvas)
    {
        var cx = _w / 2f;
        var cy = _h / 2f;
        _paint!.SetShader(null);
        _paint.SetStyle(Paint.Style.Fill);
        _paint.TextSize = Math.Min(_w, _h) * 0.3f;
        _paint.Color = Color.Argb(200, 255, 255, 255);
        _paint.SetTypeface(Typeface.DefaultBold);
        _paint.TextAlign = Paint.Align.Center;
        canvas.DrawText(_glyph, cx, cy + _paint.TextSize * 0.35f, _paint);
    }

    /// <summary>
    /// 轮廓光：把剪影稍稍放大后用冷白着色，形成贴着角色边缘的一圈逆光。
    /// 不再用矩形描边 —— 那看起来像给角色套了个相框。
    /// </summary>
    private void DrawRimLight(Canvas canvas, float breathe)
    {
        if (_glow == null || _glow.Bmp.IsRecycled || _bitmap == null || _bitmap.IsRecycled) return;
        if (_rarity < 2) return;

        var scale = _charWidth / _bitmap.Width;
        var gw = _glow.Bmp.Width * scale;
        var gh = _glow.Bmp.Height * scale;
        var cx = _charLeft + _glow.OffX * scale + gw / 2f;
        var cy = _charTop + _glow.OffY * scale + gh / 2f;

        // 略大于本体，只露出边缘一圈
        var k = 0.985f;
        var w2 = gw * k / 2f;
        var h2 = gh * k / 2f;
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.SetShader(null);
        _paint.Color = Color.Argb((int)(34 + 30 * breathe), 235, 245, 255);
        _tmpRect.Set(cx - w2, cy - h2, cx + w2, cy + h2);
        canvas.DrawBitmap(_glow.Bmp, null, _tmpRect, _paint);
    }

    private void DrawParticles(Canvas canvas)
    {
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.TextAlign = Paint.Align.Center;

        // 倒序遍历以便安全移除过期的 burst 粒子（否则列表随每次 TriggerBurst 无界增长）
        for (int i = _particles.Count - 1; i >= 0; i--)
        {
            var p = _particles[i];
            // Update particle life
            p.Life += p.Speed * 0.015f;
            if (p.Life > 1f)
            {
                if (p.IsBurst)
                {
                    _particles.RemoveAt(i); // burst particles die and are removed
                    continue;
                }
                p.Life = 0; // respawn normal particles
                p.OffsetX = (float)(_rng.NextDouble() * 2 - 1);
                p.OffsetY = (float)(_rng.NextDouble() * 2 - 1);
            }

            // Position relative to character body
            var px = (_charLeft + _charRight) / 2 + p.OffsetX * _charWidth * 0.45f;
            var baseY = _charTop + (1 - p.OffsetY) * 0.5f * _charHeight;
            var py = baseY - p.Life * _charHeight * 1.2f; // rise upward

            // Fade in and out
            var lifeAlpha = p.Life < 0.2f ? p.Life / 0.2f : p.Life > 0.7f ? (1 - p.Life) / 0.3f : 1f;
            var alpha = (int)(lifeAlpha * 180 * (0.5f + 0.5f * MathF.Sin(_phase * 2f + p.Phase)));

            if (alpha <= 0) continue;

            var size = p.Size * (0.7f + 0.3f * MathF.Sin(_phase * 3f + p.Phase));
            _paint.Color = Color.Argb(alpha, _rarityCoreColor.R, _rarityCoreColor.G, _rarityCoreColor.B);
            _paint.TextSize = size;
            canvas.DrawText(p.IsBurst ? "✦" : "•", px, py, _paint);
        }
    }

    private void DrawBurst(Canvas canvas)
    {
        _burstPhase += 0.04f;
        if (_burstPhase < 1f)
        {
            var a = (int)(180 * (1 - _burstPhase));
            _paint!.SetStyle(Paint.Style.Fill);
            _paint.Color = Color.Argb(a, 255, 255, 255);
            canvas.DrawRect(0, 0, _w, _h, _paint);

            // Radial lines from character center
            var cx = (_charLeft + _charRight) / 2;
            var cy = (_charTop + _charBottom) / 2;
            _paint.SetStyle(Paint.Style.Stroke);
            _paint.StrokeWidth = 2;
            _paint.Color = Color.Argb(a, _rarityCoreColor.R, _rarityCoreColor.G, _rarityCoreColor.B);
            for (int i = 0; i < 16; i++)
            {
                var angle = i * MathF.PI / 8;
                var len = _charWidth * _burstPhase * 1.2f;
                canvas.DrawLine(cx, cy,
                    cx + MathF.Cos(angle) * len,
                    cy + MathF.Sin(angle) * len, _paint);
            }
        }
        else _bursting = false;
    }

    private class Particle
    {
        public float OffsetX { get; set; }
        public float OffsetY { get; set; }
        public float Speed { get; set; }
        public float Size { get; set; }
        public float Phase { get; set; }
        public float Life { get; set; }
        public bool IsBurst { get; set; }
    }
}
