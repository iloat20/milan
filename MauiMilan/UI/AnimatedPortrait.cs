using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Views;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// Animated character portrait: breathing aura ring + floating particles + glyph.
/// Used in detail view and as the centerpiece of gacha reveals.
/// </summary>
public class AnimatedPortrait : FrameLayout
{
    private readonly string _element;
    private readonly int _rarity;
    private readonly string _glyph;
    private readonly Color _from;
    private readonly Color _to;
    private readonly Color _glow;
    private float _phase;
    private Paint? _paint;
    private ParticleView? _particles;
    // #37: GradientDrawable / 文字 Paint 复用，避免每帧新建。
    private GradientDrawable? _disc;
    private Paint? _textPaint;
    private int _discW, _discH;
    private bool _framePending;

    public AnimatedPortrait(Context context, string element, int rarity, string glyph, int sizeDp) : base(context)
    {
        var density = context.Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        _element = element; _rarity = rarity; _glyph = glyph;
        (_from, _to, _glow, _) = ElementTheme.For(element);

        LayoutParameters = new LinearLayout.LayoutParams(Dp(sizeDp), Dp(sizeDp));

        // Particle layer (behind)
        if (rarity >= 3)
        {
            _particles = new ParticleView(context);
            var pp = new LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
            _particles.Configure("glow", _glow, _rarity == 4 ? 3 : 2);
            AddView(_particles, pp);
        }

        SetWillNotDraw(false);
    }

    protected override void OnLayout(bool changed, int l, int t, int r, int b)
    {
        base.OnLayout(changed, l, t, r, b);
        if (_particles != null) _particles.Start();
    }

    protected override void OnDraw(Canvas canvas)
    {
        _framePending = false;
        var w = Width; var h = Height;
        // 尺寸未就绪时直接返回：此处再 Invalidate 会形成不停重绘的空转死循环。
        if (w <= 0 || h <= 0) return;

        if (_paint == null) _paint = new Paint { AntiAlias = true };
        _phase += 0.03f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;

        var cx = w / 2f; var cy = h / 2f;
        var baseR = Math.Min(w, h) * 0.42f;
        var breathe = 1f + 0.04f * MathF.Sin(_phase);
        var ringR = baseR * breathe;

        // Outer breathing aura ring
        if (_rarity >= 2)
        {
            var auraAlpha = (int)(50 + 30 * MathF.Sin(_phase));
            _paint!.Color = Color.Argb(auraAlpha, _glow.R, _glow.G, _glow.B);
            _paint.SetShadowLayer(15, 0, 0, _paint.Color);
            canvas.DrawCircle(cx, cy, ringR + 8, _paint);
            _paint.ClearShadowLayer();
        }

        // Inner gradient disc
        if (_disc == null || _discW != w || _discH != h)
        {
            _disc ??= new GradientDrawable();
            _disc.SetColors(new[] { _from.ToArgb(), _to.ToArgb() });
            _disc.SetShape(ShapeType.Oval);
            _disc.SetBounds(0, 0, w, h);
            _discW = w; _discH = h;
        }
        _disc.Draw(canvas);

        // Glyph
        if (_textPaint == null)
        {
            _textPaint = new Paint { AntiAlias = true, Color = Color.White, TextAlign = Paint.Align.Center };
            _textPaint.SetTypeface(Typeface.DefaultBold);
            _textPaint.SetShadowLayer(6, 0, 2, Color.Argb(130, 0, 0, 0));
        }
        _textPaint.TextSize = w * 0.40f;
        var fm = _textPaint.GetFontMetrics();
        if (fm != null && !string.IsNullOrEmpty(_glyph))
            canvas.DrawText(_glyph, cx, cy - (fm.Ascent + fm.Descent) / 2f, _textPaint);

        ScheduleFrame(); // keep animating while visible
    }

    // ---- 动画生命周期：脱离窗口/不可见时停止重绘 ----
    private bool _animating = true;

    /// <summary>30fps 节流预约下一帧，重复调用不会叠加多条动画链。</summary>
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
    }

    protected override void OnWindowVisibilityChanged(ViewStates visibility)
    {
        base.OnWindowVisibilityChanged(visibility);
        _animating = visibility == ViewStates.Visible;
        _framePending = false;
        if (_animating) ScheduleFrame();
    }
}
