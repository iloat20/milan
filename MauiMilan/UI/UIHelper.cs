using Android.Content;
using Android.Content.Res;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Runtime;
using Android.Util;
using Android.Views;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// Shared design system for the Milan gacha app: cosmic dark theme, rarity palette,
/// density-independent sizing, and helpers for rounded buttons / cards / backgrounds.
/// Kept stateless so every Activity draws a consistent look.
/// </summary>
public static class AppTheme
{
    // Core palette (cosmic dark)
    public static Color Background => Color.ParseColor("#0d0d1a");
    public static Color Surface => Color.ParseColor("#161628");
    public static Color SurfaceRaised => Color.ParseColor("#1f1f38");
    public static Color Accent => Color.ParseColor("#e94560");     // cosmic red
    public static Color AccentAlt => Color.ParseColor("#00d2ff");   // cyan
    public static Color Gold => Color.ParseColor("#ffd75a");
    public static Color TextPrimary => Color.ParseColor("#ffffff");
    public static Color TextSecondary => Color.ParseColor("#b8b8d0");
    public static Color TextMuted => Color.ParseColor("#7a7a99");
    public static Color Stroke => Color.ParseColor("#2e2e52");

    // ── Cosmic Nebula palette (宇宙星穹) ──
    public static Color CosmicBgDeep => Color.ParseColor("#0d0221");
    public static Color CosmicBgLight => Color.ParseColor("#1a0533");
    public static Color CosmicBgMid => Color.ParseColor("#2a0a4a");
    public static Color CosmicPrimary => Color.ParseColor("#7c4dff");
    public static Color CosmicPrimarySoft => Color.ParseColor("#b388ff");
    public static Color CosmicGold => Color.ParseColor("#ffd75a");
    public static Color CosmicTextPrimary => Color.ParseColor("#ffffff");
    public static Color CosmicTextSecondary => Color.ParseColor("#b8b8d0");
    public static Color CosmicTextMuted => Color.ParseColor("#7a7a99");
    public static Color CosmicSurface => Color.ParseColor("#1a0533");

    // Rarity palette (index matches Rarity enum: 1=R .. 4=UR)
    public static Color RarityColor(int rarity) => rarity switch
    {
        1 => Color.ParseColor("#8e8e93"),   // R  - gray
        2 => Color.ParseColor("#3aa0ff"),   // SR - blue
        3 => Color.ParseColor("#d070ff"),   // SSR - purple (cosmic)
        4 => Color.ParseColor("#ff6b00"),   // UR - orange-gold (cosmic)
        _ => TextMuted
    };

    public static string RarityName(int rarity) => rarity switch
    {
        3 => "SSR",
        2 => "SR",
        4 => "UR",
        _ => "R"
    };
}

public static class UI
{
    public static float Density { get; set; } = 1f;

    public static int Dp(this int v) => (int)(v * Density);
    public static int Dp(float v) => (int)(v * Density);

    public static void Init(Context context)
    {
        Density = context.Resources.DisplayMetrics.Density;
    }

    /// <summary>Solid rounded surface (card / panel).</summary>
    public static GradientDrawable RoundRect(int fill, float radiusDp, int? strokePx = null, Color? strokeColor = null)
    {
        var gd = new GradientDrawable();
        gd.SetColor(fill);
        gd.SetCornerRadius(Dp(radiusDp));
        if (strokePx.HasValue) gd.SetStroke(Dp(strokePx.Value), strokeColor ?? AppTheme.Stroke);
        return gd;
    }

    /// <summary>Vertical gradient background.</summary>
    public static GradientDrawable Gradient(int top, int bottom)
    {
        var gd = new GradientDrawable();
        gd.SetColors(new[] { top, bottom });
        gd.SetCornerRadius(0);
        return gd;
    }

    public static TextView Text(string s, float sp, Color color, bool bold = false)
    {
        var t = new TextView(MauiApp.Context)
        {
            Text = s
        };
        t.SetTextColor(color);
        t.SetTextSize(ComplexUnitType.Sp, sp);
        if (bold) t.SetTypeface(null, TypefaceStyle.Bold);
        return t;
    }

    /// <summary>Big rounded button with solid fill and white text.</summary>
    public static Button Button(string label, Color fill, Color textColor, float radiusDp = 16)
    {
        var b = new Button(MauiApp.Context)
        {
            Text = label
        };
        b.SetTextColor(textColor);
        b.SetTextSize(ComplexUnitType.Sp, 17);
        b.SetTypeface(null, TypefaceStyle.Bold);
        b.Background = RoundRect(fill, radiusDp);
        b.SetPadding(Dp(24), Dp(14), Dp(24), Dp(14));
        // remove default insets so padding feels even
        b.SetMinimumHeight(0);
        b.SetMinHeight(0);
        return b;
    }

    /// <summary>Circular / rounded avatar box showing a character initial on a rarity-colored ground.</summary>
    public static TextView Avatar(string initial, int rarity, int sizeDp)
    {
        var t = new TextView(MauiApp.Context)
        {
            Text = initial,
            Gravity = GravityFlags.Center
        };
        t.SetTextColor(Color.White);
        t.SetTextSize(ComplexUnitType.Sp, sizeDp * 0.42f);
        t.SetTypeface(null, TypefaceStyle.Bold);
        t.SetTextColor(Color.White);
        t.SetShadowLayer(6, 0, 2, Color.Argb(120, 0, 0, 0));
        t.Background = RoundRect(AppTheme.RarityColor(rarity), sizeDp / 2f);
        var lp = new LinearLayout.LayoutParams(Dp(sizeDp), Dp(sizeDp));
        t.LayoutParameters = lp;
        return t;
    }

    public static LinearLayout VBox()
    {
        var l = new LinearLayout(MauiApp.Context) { Orientation = Android.Widget.Orientation.Vertical };
        return l;
    }

    public static LinearLayout HBox()
    {
        var l = new LinearLayout(MauiApp.Context) { Orientation = Android.Widget.Orientation.Horizontal };
        return l;
    }
}

/// <summary>
/// Minimal Application subclass that gives every Activity a global Context
/// (handy for stateless UI helpers) and owns the shared GameState.
/// </summary>
[Android.App.Application]
public class MauiApp : Application
{
    public static Context Context = null!;

    public MauiApp(IntPtr handle, JniHandleOwnership transfer) : base(handle, transfer) { }

    public override void OnCreate()
    {
        base.OnCreate();
        Context = this;
        UI.Init(this);
    }
}
