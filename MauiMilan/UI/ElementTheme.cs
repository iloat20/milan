using Android.Graphics;
using Android.Graphics.Drawables;

namespace Milan.Maui;

/// <summary>
/// Element visual identity: a signature gradient (from -> to), a glow color, and a glyph.
/// Drives every character card's color theme.
/// </summary>
public static class ElementTheme
{
    public static (Color from, Color to, Color glow, string glyph) For(string element) => element switch
    {
        "Flame"   => (Color.ParseColor("#FF4D00"), Color.ParseColor("#FFB300"), Color.ParseColor("#FF5252"), "炎"),
        "Frost"   => (Color.ParseColor("#0097A7"), Color.ParseColor("#B2EBF2"), Color.ParseColor("#4DD0E1"), "冰"),
        "Thunder" => (Color.ParseColor("#FFD600"), Color.ParseColor("#7E57C2"), Color.ParseColor("#E040FB"), "雷"),
        "Wind"    => (Color.ParseColor("#00C853"), Color.ParseColor("#B9F6CA"), Color.ParseColor("#69F0AE"), "风"),
        "Shadow"  => (Color.ParseColor("#1A0033"), Color.ParseColor("#6A1B9A"), Color.ParseColor("#9C27B0"), "暗"),
        "Light"   => (Color.ParseColor("#FFD600"), Color.ParseColor("#FFFDE7"), Color.Argb(255, 253, 231, 255), "光"),
        "Earth"   => (Color.ParseColor("#795548"), Color.ParseColor("#D7CCC8"), Color.ParseColor("#A1887F"), "土"),
        "Metal"   => (Color.ParseColor("#455A64"), Color.ParseColor("#B0BEC5"), Color.ParseColor("#78909C"), "钢"),
        "Void"    => (Color.ParseColor("#0D0221"), Color.ParseColor("#6A0DAD"), Color.ParseColor("#CE93D8"), "虚"),
        "Star"    => (Color.ParseColor("#1A237E"), Color.ParseColor("#7C4DFF"), Color.ParseColor("#B388FF"), "星"),
        _         => (Color.ParseColor("#FF4D00"), Color.ParseColor("#FFB300"), Color.ParseColor("#FF5252"), "炎")
    };

    public static GradientDrawable Gradient(string element)
    {
        var (from, to, _, _) = For(element);
        var gd = new GradientDrawable();
        gd.SetColors(new[] { from.ToArgb(), to.ToArgb() });
        gd.SetCornerRadius(0);
        return gd;
    }

    public static GradientDrawable Glow(string element)
    {
        var (_, _, glow, _) = For(element);
        var transparent = Color.Argb(0, glow.R, glow.G, glow.B);
        var medium = Color.Argb(90, glow.R, glow.G, glow.B);
        var gd = new GradientDrawable();
        gd.SetColors(new[] { medium.ToArgb(), transparent.ToArgb() });
        gd.SetCornerRadius(0);
        return gd;
    }
}
