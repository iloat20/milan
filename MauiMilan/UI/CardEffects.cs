using Android.Content;
using Android.Graphics;
using Android.Views;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// Card visual effects: a moving shine sweep across the card surface and
/// a pulsing rarity glow. Wraps any card view in a FrameLayout overlay.
/// </summary>
public class CardEffects
{
    public static FrameLayout Apply(Context context, View card, int rarity, Color rarityColor)
    {
        var wrapper = new FrameLayout(context);
        card.LayoutParameters = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        wrapper.AddView(card);

        var shine = new ShineView(context);
        shine.LayoutParameters = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        wrapper.AddView(shine);

        if (rarity >= 3)
        {
            var pulse = new PulseView(context, rarityColor);
            pulse.LayoutParameters = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
            wrapper.AddView(pulse, 0);
        }
        return wrapper;
    }

    public static void Reveal(View card)
    {
        card.Alpha = 0f;
        card.ScaleX = 0.6f;
        card.ScaleY = 0.6f;
        card.Animate().Alpha(1f).ScaleX(1f).ScaleY(1f).SetDuration(450)
            .SetInterpolator(new Android.Views.Animations.DecelerateInterpolator()).Start();
    }
}

public class ShineView : View
{
    private float _offset = -1f;
    private Paint? _paint;
    public ShineView(Context context) : base(context) { }
    protected override void OnDraw(Canvas canvas)
    {
        if (_paint == null) _paint = new Paint();
        var w = Width; var h = Height;
        if (w == 0 || h == 0) return;
        _offset += 0.03f;
        if (_offset > 1.5f) _offset = -1f;
        var cx = _offset * (w * 1.5f);
        var grad = new LinearGradient(cx - 80, 0, cx + 80, h,
            Color.Argb(0, 255, 255, 255), Color.Argb(90, 255, 255, 255), Shader.TileMode.Clamp);
        _paint!.SetShader(grad);
        canvas.DrawRect(0, 0, w, h, _paint);
        _paint.SetShader(null);
        Invalidate();
    }
}

public class PulseView : View
{
    private float _phase;
    private readonly Color _color;
    private Paint? _paint;
    public PulseView(Context context, Color color) : base(context) { _color = color; }
    protected override void OnDraw(Canvas canvas)
    {
        if (_paint == null) _paint = new Paint { AntiAlias = true };
        _phase += 0.04f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        var alpha = (int)(40 + 30 * MathF.Sin(_phase));
        var c = Color.Argb(alpha, _color.R, _color.G, _color.B);
        _paint!.Color = c;
        _paint.SetShadowLayer(20, 0, 0, c);
        var r = Math.Min(Width, Height) * 0.4f;
        canvas.DrawCircle(Width / 2f, Height / 2f, r, _paint);
        _paint.ClearShadowLayer();
        Invalidate();
    }
}
