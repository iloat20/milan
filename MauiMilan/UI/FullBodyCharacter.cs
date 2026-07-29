using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Views;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// Highly visible character portrait: bright element gradient background,
/// large glyph, character name, rarity glow border. Always visible.
/// </summary>
public class FullBodyCharacter : View
{
    private readonly string _element;
    private readonly int _rarity;
    private readonly string _glyph;
    private readonly string _name;
    private readonly Color _from;
    private readonly Color _to;
    private readonly Color _glow;
    private float _phase;
    private Paint? _paint;
    private int _w, _h;

    // Burst state
    private bool _bursting; private float _burstPhase;

    public FullBodyCharacter(Context context, CharacterDataEntry def) : base(context)
    {
        SetWillNotDraw(false);
        SetMinimumWidth(Dp(140));
        SetMinimumHeight(Dp(180));
        _element = def.Element; _rarity = def.BaseRarity;
        _glyph = ElementTheme.For(def.Element).glyph;
        _name = def.DisplayName;
        (_from, _to, _glow, _) = ElementTheme.For(def.Element);
    }

    public void TriggerBurst() { _bursting = true; _burstPhase = 0; Invalidate(); }

    private int Dp(int v) => (int)(v * Resources.DisplayMetrics.Density);

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _w = w; _h = h;
    }

    protected override void OnDraw(Canvas canvas)
    {
        if (_paint == null) _paint = new Paint { AntiAlias = true, FilterBitmap = true };
        _phase += 0.03f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        if (_w == 0 || _h == 0) return;

        var pad = Dp(8);
        var radius = Dp(16);

        // 1. Bright element gradient background
        var bg = new GradientDrawable();
        bg.SetCornerRadius(radius);
        bg.SetColors(new int[] {
            Color.Argb(255, Math.Min(255, _from.R + 40), Math.Min(255, _from.G + 40), Math.Min(255, _from.B + 40)),
            Color.Argb(255, _to.R, _to.G, _to.B)
        });
        bg.SetBounds(pad, pad, _w - pad, _h - pad);
        bg.Draw(canvas);

        // 2. Rarity glow border (thick, bright)
        var borderCol = AppTheme.RarityColor(_rarity);
        _paint!.SetStyle(Paint.Style.Stroke);
        _paint.StrokeWidth = _rarity >= 3 ? Dp(4) : Dp(2);
        _paint.Color = borderCol;
        if (_rarity >= 3) _paint.SetShadowLayer(12, 0, 0, borderCol);
        canvas.DrawRoundRect(pad + Dp(2), pad + Dp(2), _w - pad - Dp(2), _h - pad - Dp(2), radius, radius, _paint);
        _paint.ClearShadowLayer();

        // 3. Large glyph centered
        var glyphSize = Math.Min(_w, _h) * 0.38f;
        _paint.SetStyle(Paint.Style.Fill);
        _paint.Color = Color.White;
        _paint.TextSize = glyphSize;
        _paint.SetTypeface(Typeface.DefaultBold);
        _paint.SetShadowLayer(6, 0, 2, Color.Argb(100, 0, 0, 0));
        var fm = _paint.GetFontMetrics();
        canvas.DrawText(_glyph, _w / 2f - _paint.MeasureText(_glyph) / 2f,
            _h * 0.45f - (fm.Ascent + fm.Descent) / 2f, _paint);
        _paint.SetShadowLayer(0, 0, 0, Color.Transparent);

        // 4. Character name below glyph
        _paint.TextSize = Dp(12);
        _paint.Color = Color.White;
        _paint.SetTypeface(Typeface.DefaultBold);
        var nameWidth = _paint.MeasureText(_name);
        if (nameWidth > _w - Dp(16))
        {
            // shrink to fit
            _paint.TextSize = Dp(10);
            nameWidth = _paint.MeasureText(_name);
        }
        canvas.DrawText(_name, _w / 2f - nameWidth / 2f, _h * 0.78f, _paint);

        // 5. Rarity stars at bottom
        _paint.TextSize = Dp(10);
        _paint.Color = borderCol;
        var stars = new string('★', _rarity);
        var starW = _paint.MeasureText(stars);
        canvas.DrawText(stars, _w / 2f - starW / 2f, _h * 0.92f, _paint);

        // 6. Floating particles (subtle)
        for (int i = 0; i < 6; i++)
        {
            var px = ((_phase * 15 + i * 53) % 1f) * (_w - Dp(16)) + Dp(8);
            var py = _h - ((_phase * 20 + i * 41) % 1f) * (_h * 0.3f) - Dp(8);
            _paint.Color = Color.Argb(80, 255, 255, 255);
            canvas.DrawCircle(px, py, 1.5f, _paint);
        }

        // 7. Burst flash
        if (_bursting)
        {
            _burstPhase += 0.06f;
            if (_burstPhase < 1f)
            {
                var flashA = (int)(160 * (1 - _burstPhase));
                _paint.Color = Color.Argb(flashA, 255, 255, 255);
                canvas.DrawRoundRect(pad, pad, _w - pad, _h - pad, radius, radius, _paint);
            }
            else _bursting = false;
        }

        Invalidate(); // keep animating
    }
}
