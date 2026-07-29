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
        if (_paint == null) _paint = new Paint { AntiAlias = true };
        _phase += 0.03f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;

        var w = Width; var h = Height;
        if (w == 0 || h == 0) { Invalidate(); return; }
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
        var gd = new GradientDrawable();
        gd.SetColors(new[] { _from.ToArgb(), _to.ToArgb() });
        gd.SetShape(ShapeType.Oval);
        gd.SetBounds(0, 0, w, h);
        gd.Draw(canvas);

        // Glyph
        var tp = new Paint { AntiAlias = true, Color = Color.White, TextAlign = Paint.Align.Center };
        tp.TextSize = w * 0.40f;
        tp.SetTypeface(Typeface.DefaultBold);
        tp.SetShadowLayer(6, 0, 2, Color.Argb(130, 0, 0, 0));
        var fm = tp.GetFontMetrics();
        canvas.DrawText(_glyph, cx, cy - (fm.Ascent + fm.Descent) / 2f, tp);

        Invalidate(); // keep animating
    }
}
