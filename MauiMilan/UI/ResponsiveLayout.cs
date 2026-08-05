using Android.Content;
using Android.Content.Res;

namespace Milan.Maui;

/// <summary>
/// 横竖屏与尺寸类基建（ui-redesign-plan.md §4）。
/// 使用方式：Activity 声明
///   [Activity(ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize)]
/// 并在 OnConfigurationChanged 中调用 SetContentView(BuildLayout()) 重建布局。
/// </summary>
public static class Responsive
{
    public enum Orientation { Portrait, Landscape }
    public enum WidthClass { Compact, Regular }

    public static Orientation GetOrientation(Context ctx)
    {
        var o = ctx.Resources?.Configuration?.Orientation;
        return o == Android.Content.Res.Orientation.Landscape ? Orientation.Landscape : Orientation.Portrait;
    }

    public static bool IsLandscape(Context ctx) => GetOrientation(ctx) == Orientation.Landscape;

    /// <summary>compact &lt; 600dp 宽（手机竖屏），regular &gt;= 600dp（平板 / 横屏手机）。</summary>
    public static WidthClass GetWidthClass(Context ctx)
    {
        var dm = ctx.Resources?.DisplayMetrics;
        if (dm == null) return WidthClass.Compact;
        float dpW = dm.WidthPixels / dm.Density;
        return dpW >= 600 ? WidthClass.Regular : WidthClass.Compact;
    }

    public static bool IsWide(Context ctx) => GetWidthClass(ctx) == WidthClass.Regular;
}
