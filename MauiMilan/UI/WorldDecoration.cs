using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// World-themed decoration components: ornate borders, corner pieces,
/// and background effects for each of the three worlds.
/// </summary>
public static class WorldDecoration
{
    /// <summary>
    /// Create a world-themed ornate border drawable.
    /// Shinwa = ink-brush gold border, Aether = glowing energy border, Ironveil = riveted steel border.
    /// </summary>
    public static GradientDrawable OrnateBorder(string world, int strokeWidthDp = 2)
    {
        var density = UI.Density;
        int Dp(int v) => (int)(v * density);
        var theme = AppTheme.World(world);

        var gd = new GradientDrawable();
        gd.SetColor(Color.Argb(0, 0, 0, 0)); // transparent fill
        gd.SetCornerRadius(Dp(16));
        gd.SetStroke(Dp(strokeWidthDp), Color.Argb(160, theme.Primary.R, theme.Primary.G, theme.Primary.B));

        return gd;
    }

    /// <summary>
    /// Create a world-themed card background with subtle gradient.
    /// </summary>
    public static GradientDrawable ThemedCard(string world)
    {
        var theme = AppTheme.World(world);
        var gd = new GradientDrawable();
        gd.SetColors(new[] {
            Color.Argb(255, theme.Surface.R, theme.Surface.G, theme.Surface.B).ToArgb(),
            Color.Argb(255, theme.Background.R, theme.Background.G, theme.Background.B).ToArgb()
        });
        gd.SetCornerRadius(UI.Dp(16));
        gd.SetStroke(UI.Dp(1), Color.Argb(80, theme.Primary.R, theme.Primary.G, theme.Primary.B));
        return gd;
    }

    /// <summary>
    /// Create a glow effect behind a character portrait, tinted by world.
    /// </summary>
    public static View PortraitGlow(string world, int widthDp, int heightDp)
    {
        var density = UI.Density;
        var theme = AppTheme.World(world);

        var view = new View(MauiApp.Context);
        view.LayoutParameters = new FrameLayout.LayoutParams(
            (int)(widthDp * density), (int)(heightDp * density));

        var gd = new GradientDrawable();
        gd.SetShape(ShapeType.Oval);
        gd.SetColors(new[] {
            Color.Argb(80, theme.Glow.R, theme.Glow.G, theme.Glow.B).ToArgb(),
            Color.Argb(0, theme.Glow.R, theme.Glow.G, theme.Glow.B).ToArgb()
        });
        gd.SetGradientType(GradientType.RadialGradient);
        gd.SetGradientRadius(density * widthDp * 0.6f);
        view.Background = gd;
        return view;
    }

    /// <summary>
    /// Create a world-themed section divider.
    /// </summary>
    public static View SectionDivider(string world)
    {
        var density = UI.Density;
        var theme = AppTheme.World(world);

        var divider = new View(MauiApp.Context);
        divider.LayoutParameters = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, (int)(1 * density));
        divider.SetBackgroundColor(Color.Argb(60, theme.Primary.R, theme.Primary.G, theme.Primary.B));
        return divider;
    }

    /// <summary>
    /// Create a world-themed header bar with gradient.
    /// </summary>
    public static GradientDrawable HeaderBackground(string world)
    {
        var theme = AppTheme.World(world);
        var gd = new GradientDrawable();
        gd.SetColors(new[] {
            Color.Argb(200, theme.Primary.R, theme.Primary.G, theme.Primary.B).ToArgb(),
            Color.Argb(100, theme.Secondary.R, theme.Secondary.G, theme.Secondary.B).ToArgb()
        });
        gd.SetCornerRadius(0);
        return gd;
    }
}

/// <summary>
/// Enhanced gacha result card with world-themed styling and better visual impact.
/// </summary>
public class EnhancedResultCard : FrameLayout
{
    public EnhancedResultCard(Context context, PullResult result, CharacterDataEntry? def) : base(context)
    {
        var density = context.Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var world = def?.World ?? "Shinwa";
        var theme = AppTheme.World(world);

        var minW = (int)(Resources.DisplayMetrics.WidthPixels / density / 5.5f);
        var lp = new LinearLayout.LayoutParams(Math.Max(minW, Dp(64)), ViewGroup.LayoutParams.WrapContent, 0f);
        lp.SetMargins(Dp(3), Dp(3), Dp(3), Dp(3));
        LayoutParameters = lp;

        // World-themed card background
        var borderCol = AppTheme.RarityColor(result.Rarity);
        var bg = new GradientDrawable();
        bg.SetCornerRadius(Dp(12));
        bg.SetColors(new[] {
            Color.Argb(255, theme.Background.R, theme.Background.G, theme.Background.B).ToArgb(),
            Color.Argb(255, theme.Surface.R, theme.Surface.G, theme.Surface.B).ToArgb()
        });
        bg.SetStroke(result.Rarity >= 3 ? Dp(3) : Dp(1), borderCol);
        Background = bg;
        SetPadding(Dp(6), Dp(6), Dp(6), Dp(6));

        var col = new LinearLayout(context) { Orientation = Orientation.Vertical };
        col.LayoutParameters = new LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);

        // AI Portrait
        var portrait = new PortraitView(context).Bind(def ?? GameState.Service.Characters.First());
        var portLp = new LinearLayout.LayoutParams(Dp(56), Dp(70));
        portLp.Gravity = GravityFlags.CenterHorizontal;
        portrait.LayoutParameters = portLp;
        col.AddView(portrait);

        // Rarity tag with world color accent
        var tagRow = new LinearLayout(context) { Orientation = Orientation.Horizontal };
        tagRow.SetGravity(GravityFlags.CenterHorizontal);
        var rarityTag = new TextView(context) { Text = AppTheme.RarityName(result.Rarity) };
        rarityTag.SetTextColor(borderCol);
        rarityTag.SetTextSize(ComplexUnitType.Sp, 9);
        rarityTag.SetTypeface(null, TypefaceStyle.Bold);

        // World indicator dot
        var worldDot = new TextView(context) { Text = " ●" };
        worldDot.SetTextColor(theme.Primary);
        worldDot.SetTextSize(ComplexUnitType.Sp, 6);
        tagRow.AddView(rarityTag);
        if (result.Rarity >= 3) tagRow.AddView(worldDot);
        col.AddView(tagRow);

        // Name
        var name = new TextView(context) { Text = result.CharacterName };
        name.SetTextColor(Color.White);
        name.SetTextSize(ComplexUnitType.Sp, 9);
        name.SetMaxLines(1);
        name.Gravity = GravityFlags.CenterHorizontal;
        name.SetPadding(0, Dp(1), 0, Dp(2));
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
    }
}
