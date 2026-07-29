using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// Gacha result card — shows character portrait, name, rarity immediately.
/// High rarity cards have a glowing border.
/// </summary>
public class ResultCard : FrameLayout
{
    private readonly PullResult _result;
    private readonly CharacterDataEntry? _def;

    public ResultCard(Context context, PullResult result, CharacterDataEntry? def) : base(context)
    {
        _result = result; _def = def;

        var density = context.Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var minW = (int)(Resources.DisplayMetrics.WidthPixels / density / 5.5f);
        var lp = new LinearLayout.LayoutParams(Math.Max(minW, Dp(64)), ViewGroup.LayoutParams.WrapContent, 0f);
        lp.SetMargins(Dp(3), Dp(3), Dp(3), Dp(3));
        LayoutParameters = lp;

        // Card background with rarity border
        var borderCol = AppTheme.RarityColor(result.Rarity);
        var bg = new GradientDrawable();
        bg.SetCornerRadius(Dp(10));
        bg.SetColor(Color.Argb(255, 20, 10, 30));
        bg.SetStroke(result.Rarity >= 3 ? Dp(3) : Dp(1), borderCol);
        Background = bg;
        SetPadding(Dp(6), Dp(6), Dp(6), Dp(6));

        // Content
        var col = new LinearLayout(context) { Orientation = Orientation.Vertical };
        col.LayoutParameters = new LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);

        // Portrait (mini FullBodyCharacter)
        var portrait = new FullBodyCharacter(context, def ?? GameState.Service.Characters.First());
        var portLp = new LinearLayout.LayoutParams(Dp(56), Dp(70));
        portLp.Gravity = GravityFlags.CenterHorizontal;
        portrait.LayoutParameters = portLp;
        col.AddView(portrait);

        // Rarity tag
        var tag = new TextView(context) { Text = AppTheme.RarityName(result.Rarity) };
        tag.SetTextColor(borderCol);
        tag.SetTextSize(ComplexUnitType.Sp, 9);
        tag.SetTypeface(null, TypefaceStyle.Bold);
        tag.Gravity = GravityFlags.CenterHorizontal;
        tag.SetPadding(0, Dp(2), 0, 0);
        col.AddView(tag);

        // Name
        var name = new TextView(context) { Text = result.CharacterName };
        name.SetTextColor(Color.White);
        name.SetTextSize(ComplexUnitType.Sp, 9);
        name.SetMaxLines(1);
        name.Gravity = GravityFlags.CenterHorizontal;
        name.SetPadding(0, 0, 0, Dp(2));
        col.AddView(name);

        // New indicator
        if (result.IsNew)
        {
            var nw = new TextView(context) { Text = "NEW" };
            nw.SetTextColor(Color.Argb(255, 255, 215, 0));
            nw.SetTextSize(ComplexUnitType.Sp, 7);
            nw.SetTypeface(null, TypefaceStyle.Bold);
            nw.Gravity = GravityFlags.CenterHorizontal;
            col.AddView(nw);
        }

        AddView(col);

        // Glow for high rarity via border thickness (SetShadowLayer unreliable in containers)
        if (result.Rarity >= 3)
        {
            bg.SetStroke(Dp(3), borderCol);
        }
    }
}
