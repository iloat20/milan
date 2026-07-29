using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// Gacha result card with flip animation. Shows starfield back, flips to
/// reveal character portrait + name. High rarity cards glow.
/// </summary>
public class ResultCard : FrameLayout
{
    private readonly PullResult _result;
    private readonly CharacterDataEntry? _def;
    private readonly string _element;
    private readonly Color _rarityCol;
    private bool _flipped;
    private View _face = null!;
    private View _back = null!;

    public ResultCard(Context context, PullResult result, CharacterDataEntry? def) : base(context)
    {
        _result = result; _def = def;
        _element = def?.Element ?? "Flame";
        _rarityCol = AppTheme.RarityColor(result.Rarity);

        var lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        var density = context.Resources.DisplayMetrics.Density;
        lp.SetMargins((int)(3 * density), (int)(3 * density), (int)(3 * density), (int)(3 * density));
        LayoutParameters = lp;

        _back = BuildBack();
        _face = BuildFace();
        _face.Alpha = 0f;
        AddView(_back);
        AddView(_face);

        Clickable = true;
        Click += (_, _) => Flip();
    }

    private View BuildBack()
    {
        var v = new View(Context);
        v.LayoutParameters = new LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(80));
        var bg = new GradientDrawable();
        bg.SetCornerRadius(Dp(8));
        bg.SetColors(new int[] { AppTheme.CosmicBgDeep.ToArgb(), AppTheme.CosmicBgMid.ToArgb() });
        bg.SetStroke(Dp(1), Color.Argb(80, 124, 77, 255));
        v.Background = bg;
        return v;
    }

    private View BuildFace()
    {
        var box = new LinearLayout(Context) { Orientation = Orientation.Vertical };
        box.LayoutParameters = new LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(80));
        

        var density = Context.Resources.DisplayMetrics.Density;
        var bg = new GradientDrawable();
        bg.SetCornerRadius(Dp(8));
        var (from, to, _, glyph) = ElementTheme.For(_element);
        bg.SetColors(new int[] { from.ToArgb(), to.ToArgb() });
        var borderAlpha = _result.Rarity >= 3 ? 220 : 120;
        bg.SetStroke(Dp(2), Color.Argb(borderAlpha, _rarityCol.R, _rarityCol.G, _rarityCol.B));
        box.Background = bg;

        // glow for high rarity — use view layer shadow
        if (_result.Rarity >= 3)
        {
            box.SetLayerType(LayerType.Software, null);
            // box glow via background border
        }

        var g = new TextView(Context) { Text = glyph };
        g.SetTextColor(Color.White);
        g.SetTextSize(ComplexUnitType.Sp, 22);
        g.SetTypeface(null, TypefaceStyle.Bold);
        g.SetPadding(0, Dp(6), 0, 0);
        box.AddView(g);

        var tag = new TextView(Context) { Text = AppTheme.RarityName(_result.Rarity) };
        tag.SetTextColor(_rarityCol);
        tag.SetTextSize(ComplexUnitType.Sp, 9);
        tag.SetTypeface(null, TypefaceStyle.Bold);
        tag.SetPadding(0, 2, 0, 0);
        box.AddView(tag);

        var nm = new TextView(Context) { Text = _result.CharacterName };
        nm.SetTextColor(Color.White);
        nm.SetTextSize(ComplexUnitType.Sp, 8);
        nm.Gravity = GravityFlags.CenterHorizontal;
        nm.SetMaxLines(1);
        nm.SetPadding(0, 1, 0, 2);
        box.AddView(nm);

        return box;
    }

    public void Flip()
    {
        if (_flipped) return;
        _flipped = true;
        Animate().RotationYBy(180).SetDuration(350).Start();
        _back.Animate().Alpha(0f).SetDuration(175).Start();
        _face.Animate().Alpha(1f).SetDuration(175).SetStartDelay(175).Start();
    }

    private int Dp(int v) => (int)(v * Resources.DisplayMetrics.Density);
}
