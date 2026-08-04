using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Views;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// 统一 twilight 神性卡背（2D）：暮紫夜底 + 熔金线框 + 霜蓝内框 + 中央徽记。
/// 抽卡翻转卡与 Battle 手牌卡背共用，彻底取代原先各写各的硬码卡背。
/// 设计语言：暗夜神性·诸神黄昏（克制的金——金只做线/点/徽记，面用深紫黑玻璃）。
/// </summary>
public static class CardBack
{
    public static FrameLayout Build(Context ctx, int w, int h)
    {
        int dp(int v) => UI.Dp(v);
        var fr = new FrameLayout(ctx)
        {
            LayoutParameters = w > 0 && h > 0
                ? new FrameLayout.LayoutParams(w, h)
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent)
        };

        // 背景：深紫黑玻璃 + 熔金外框（克制的金，仅做线） + 霜蓝内框
        var outer = new GradientDrawable();
        outer.SetColor(AppTheme.BgDeepest.ToArgb());
        outer.SetCornerRadius(dp(14));
        outer.SetStroke(dp(2), AppTheme.Gold);

        var inner = new GradientDrawable();
        inner.SetColor(Color.Argb(0, 0, 0, 0).ToArgb());
        inner.SetCornerRadius(dp(10));
        inner.SetStroke(dp(1), Color.Argb(90, AppTheme.Frost.R, AppTheme.Frost.G, AppTheme.Frost.B));

        var bg = new LayerDrawable(new Drawable[] { outer, inner });
        bg.SetLayerInset(1, dp(6), dp(6), dp(6), dp(6));
        fr.Background = bg;

        // 中央径向辉光（暮紫 + 熔金核心）
        var glow = new RadialGlowView(ctx)
        {
            LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent)
        };
        fr.AddView(glow);

        // 顶部熔金高光线（一条发丝线，克制的金）
        var topLine = new View(ctx)
        {
            LayoutParameters = new FrameLayout.LayoutParams(dp(44), dp(2))
            {
                Gravity = GravityFlags.Top | GravityFlags.CenterHorizontal,
                TopMargin = dp(16)
            }
        };
        topLine.SetBackgroundColor(AppTheme.Gold);
        fr.AddView(topLine);

        // 中央徽记：✦ + 竖排 MILAN
        var emblem = new LinearLayout(ctx) { Orientation = Orientation.Vertical };
        emblem.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Center
        };
        emblem.SetGravity(GravityFlags.Center);

        var star = UI.Text("✦", dp(22), AppTheme.Gold, bold: true);
        star.Gravity = GravityFlags.Center;
        emblem.AddView(star);

        var milan = UI.Text("MILAN", dp(15), AppTheme.Frost, bold: true);
        milan.LetterSpacing = 0.35f;
        milan.Gravity = GravityFlags.Center;
        emblem.AddView(milan);

        fr.AddView(emblem);

        // 底部副标
        var sub = UI.Text("诸神黄昏", dp(9), Color.Argb(150, AppTheme.Frost.R, AppTheme.Frost.G, AppTheme.Frost.B));
        sub.Gravity = GravityFlags.CenterHorizontal;
        sub.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Bottom,
            BottomMargin = dp(12)
        };
        fr.AddView(sub);

        return fr;
    }
}

/// <summary>卡背中央径向辉光（暮紫 + 熔金核心）。遵循 ColorLong 铁律：渐变颜色用 long[]。</summary>
internal sealed class RadialGlowView : View
{
    private readonly Paint _p = new() { AntiAlias = true };
    private RadialGradient? _g;

    public RadialGlowView(Context ctx) : base(ctx) => SetWillNotDraw(false);

    protected override void OnDraw(Canvas canvas)
    {
        int w = Width, h = Height;
        if (w <= 0 || h <= 0) return;
        if (_g == null)
        {
            float cx = w / 2f, cy = h * 0.42f;
            float r = Math.Min(w, h) * 0.66f;
            if (r <= 0) return;
            var core = Color.Argb(130, 70, 45, 120);   // 暮紫核心
            var edge = Color.Argb(38, 45, 25, 85);      // 暮紫边
            var transparent = Color.Argb(0, AppTheme.BgDeepest.R, AppTheme.BgDeepest.G, AppTheme.BgDeepest.B);
            _g = new RadialGradient(cx, cy, r,
                new long[] { UI.ColorLong(core), UI.ColorLong(edge), UI.ColorLong(transparent) },
                null, Shader.TileMode.Clamp);
        }
        _p.SetShader(_g);
        canvas.DrawRect(0, 0, w, h, _p);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing) _g?.Dispose();
        base.Dispose(disposing);
    }
}
