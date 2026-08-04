using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Text;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// Builds rich 2D character "cards" from layered Android views — no 3D model needed.
/// Each card = rarity frame + element gradient + glow + glyph portrait + stars + name.
/// </summary>
public static class CharacterCard
{
    // Rarity palette — 统一取自 AppTheme，避免多处配色漂移
    private static Color RarityColor(int r) => AppTheme.RarityColor(r);

    private static string RarityName(int r) => r switch
    {
        4 => "UR", 3 => "SSR", 2 => "SR", _ => "R"
    };

    private static int RaritySegments(int r) => r switch { 4 => 10, 3 => 8, 2 => 6, _ => 4 };

    // ------------------------------------------------------------------ list card (compact)

    public static View ListCard(Context context, OwnedCharacterView ch, System.Action? onClick = null)
    {
        var density = context.Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var (from, to, glow, glyph) = ElementTheme.For(ch.Element);
        var rarityCol = RarityColor(ch.Rarity);

        // Outer container with rarity border
        var card = new FrameLayout(context);
        var cardLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        cardLp.SetMargins(Dp(5), Dp(5), Dp(5), Dp(5));
        card.LayoutParameters = cardLp;
        card.SetPadding(Dp(3), Dp(3), Dp(3), Dp(3));
        card.Background = UI.RoundRect(SetAlpha(rarityCol, 110), 18, 2, rarityCol);
        if (onClick != null) { card.Clickable = true; card.Focusable = true; card.Click += (_, _) => onClick(); }

        // Inner content
        var inner = new LinearLayout(context) { Orientation = Orientation.Vertical };
        inner.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        inner.SetPadding(Dp(12), Dp(12), Dp(12), Dp(12));
        inner.Background = UI.RoundRect(AppTheme.Surface, 16);

        // Portrait box with element gradient + glow + glyph
        var portrait = Portrait(context, ch, 58);
        portrait.SetPadding(0, 0, 0, Dp(8));

        // Name + title
        var name = UI.Text(ch.Name, 15, AppTheme.Text1, bold: true);
        name.SetMaxLines(1); name.Ellipsize = TextUtils.TruncateAt.End;
        var title = UI.Text(ch.Title, 11, AppTheme.Text2);
        title.SetMaxLines(1); title.Ellipsize = TextUtils.TruncateAt.End;

        // Rarity + element row
        var metaRow = new LinearLayout(context) { Orientation = Orientation.Horizontal };
        var rarityTag = UI.Text(RarityName(ch.Rarity), 11, rarityCol, bold: true);
        rarityTag.SetPadding(0, 0, Dp(8), 0);
        var elemTag = UI.Text(glyph + " " + ch.Element, 11, from);
        metaRow.AddView(rarityTag);
        metaRow.AddView(elemTag);

        // Stars
        var stars = Stars(context, ch.Save.Stars, 11);
        stars.SetPadding(0, Dp(3), 0, 0);

        inner.AddView(portrait);
        inner.AddView(name);
        inner.AddView(title);
        inner.AddView(metaRow);
        inner.AddView(stars);

        card.AddView(inner);
        return CardEffects.Apply(context, card, ch.Rarity, rarityCol);
    }

    // ------------------------------------------------------------------ gacha result chip

    public static View GachaChip(Context context, PullResult r, System.Action? onClick = null)
    {
        var density = context.Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var def = GameState.Service.Characters.FirstOrDefault(c => c.CharacterId == r.CharacterId);
        var element = def?.Element ?? "Flame";
        var (from, to, glow, glyph) = ElementTheme.For(element);
        var rarityCol = RarityColor(r.Rarity);

        var chip = new LinearLayout(context) { Orientation = Orientation.Vertical };
        var lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        lp.SetMargins(Dp(3), Dp(3), Dp(3), Dp(3));
        chip.LayoutParameters = lp;
        chip.SetPadding(Dp(8), Dp(12), Dp(8), Dp(12));

        // Glowing background for high rarity
        if (r.Rarity >= 3)
            chip.Background = GlowBackground(context, rarityCol, from);
        else
            chip.Background = UI.RoundRect(AppTheme.Surface, 12, 1, SetAlpha(rarityCol, 140));

        var portrait = Portrait(context, element, glyph, r.Rarity, 44, r.CharacterId ?? "");
        portrait.SetPadding(0, 0, 0, Dp(6));
        var tag = UI.Text(RarityName(r.Rarity), 12, rarityCol, bold: true);
        tag.Gravity = GravityFlags.CenterHorizontal;
        var nm = UI.Text(r.CharacterName, 11, AppTheme.Text1);
        nm.Gravity = GravityFlags.CenterHorizontal;
        nm.SetMaxLines(1); nm.Ellipsize = TextUtils.TruncateAt.End;

        chip.AddView(portrait);
        chip.AddView(tag);
        chip.AddView(nm);
        if (onClick != null)
        {
            chip.Clickable = true; chip.Focusable = true;
            UI.TapFeedback(chip, onClick);
        }
        return CardEffects.Apply(context, chip, r.Rarity, rarityCol);
    }

    // ------------------------------------------------------------------ large portrait (detail)

    public static View DetailPortrait(Context context, OwnedCharacterView ch, int sizeDp = 120)
    {
        var (from, to, glow, glyph) = ElementTheme.For(ch.Element);
        var portrait = new AnimatedPortrait(context, ch.Element, ch.Rarity, glyph, sizeDp);
        return portrait;
    }

    // ------------------------------------------------------------------ helpers

    private static View Portrait(Context context, OwnedCharacterView ch, int sizeDp)
        => Portrait(context, ch.Element, ElementTheme.For(ch.Element).glyph, ch.Rarity, sizeDp, ch.Save.CharacterId);

    private static View Portrait(Context context, string element, string glyph, int rarity, int sizeDp, string characterId = "")
    {
        var density = context.Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var (from, to, glow, _) = ElementTheme.For(element);

        var box = new FrameLayout(context);
        box.LayoutParameters = new LinearLayout.LayoutParams(Dp(sizeDp), Dp(sizeDp));

        // Try to use AI portrait if available
        var bmp = PortraitLoader.Get(characterId);
        if (bmp != null && !bmp.IsRecycled)
        {
            var iv = new ImageView(context);
            iv.LayoutParameters = new FrameLayout.LayoutParams(Dp(sizeDp), Dp(sizeDp));
            iv.SetImageBitmap(bmp);
            iv.SetScaleType(ImageView.ScaleType.FitCenter);
            // Rounded corners via clip
            var bg = new Android.Graphics.Drawables.GradientDrawable();
            bg.SetCornerRadius(Dp(12));
            bg.SetColor(Color.Argb(40, 0, 0, 0));
            box.Background = bg;
            box.AddView(iv);
        }
        else
        {
            // Fallback: element gradient + glyph
            var bg = new View(context);
            bg.LayoutParameters = new FrameLayout.LayoutParams(Dp(sizeDp), Dp(sizeDp));
            bg.Background = ElementTheme.Gradient(element);
            ((GradientDrawable)bg.Background).SetCornerRadius(Dp(12));

            var sheen = new View(context);
            var sheenLp = new FrameLayout.LayoutParams(Dp(sizeDp), Dp(sizeDp));
            sheen.LayoutParameters = sheenLp;
            sheen.Background = Sheen(rarity);
            ((GradientDrawable)sheen.Background).SetCornerRadius(Dp(12));

            var g = new TextView(context) { Text = glyph, Gravity = GravityFlags.Center };
            g.SetTextColor(Color.White);
            g.SetTextSize(ComplexUnitType.Sp, sizeDp * 0.45f);
            g.SetTypeface(null, TypefaceStyle.Bold);
            g.SetShadowLayer(4, 0, 2, Color.Argb(130, 0, 0, 0));
            g.LayoutParameters = new FrameLayout.LayoutParams(Dp(sizeDp), Dp(sizeDp));

            box.AddView(bg);
            box.AddView(sheen);
            box.AddView(g);
        }
        return box;
    }

    private static View Stars(Context context, int count, float sp)
    {
        var row = new TextView(context)
        {
            Text = new string('★', count)
        };
        row.SetTextColor(AppTheme.Gold);
        row.SetTextSize(ComplexUnitType.Sp, sp);
        return row;
    }

    private static Drawable Sheen(int rarity)
    {
        var c = rarity >= 3 ? Color.Argb(70, 255, 255, 255) : Color.Argb(40, 255, 255, 255);
        var gd = new GradientDrawable();
        gd.SetColors(new[] { c.ToArgb(), Color.Argb(0, 255, 255, 255), Color.Argb(0, 255, 255, 255), c.ToArgb() });
        return gd;
    }

    private static Drawable RarityRing(Context context, int rarity, Color col, int sizePx)
    {
        var gd = new GradientDrawable();
        gd.SetShape(ShapeType.Oval);
        gd.SetColor(Color.Argb(0, 0, 0, 0));
        gd.SetStroke((int)(sizePx * 0.06f), col);
        return gd;
    }

    private static Drawable GlowBackground(Context context, Color rarityCol, Color elementFrom)
    {
        var gd = new GradientDrawable();
        gd.SetCornerRadius(14);
        gd.SetColor(SetAlpha(rarityCol, 50));
        gd.SetStroke(2, SetAlpha(rarityCol, 200));
        return gd;
    }

    private static Color SetAlpha(Color c, int a) => Color.Argb(a, c.R, c.G, c.B);
}
