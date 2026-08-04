using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Views;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// 统一科幻风卡背（2D）：暮紫夜底 + 霜蓝霓虹主框 + HUD 几何（等距网格 / 旋转六边形雷达环 /
/// 扫描线 / 四角角标）+ 克制熔金中心高光。抽卡翻转卡与 Battle 手牌卡背共用。
/// 视觉语言仍属暗夜神性·诸神黄昏调色板：金只做中心高光点（克的金），面用深紫黑玻璃 + 霜蓝科技光。
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

        // 背景：深紫黑玻璃 + 霜蓝霓虹外框（科幻主调，替代神性金框）+ 霜蓝内框
        var outer = new GradientDrawable();
        outer.SetColor(AppTheme.BgDeepest.ToArgb());
        outer.SetCornerRadius(dp(14));
        outer.SetStroke(dp(2), AppTheme.Frost);            // 霜蓝霓虹主框

        var inner = new GradientDrawable();
        inner.SetColor(Color.Argb(0, 0, 0, 0).ToArgb());
        inner.SetCornerRadius(dp(10));
        inner.SetStroke(dp(1), Color.Argb(80, AppTheme.Frost.R, AppTheme.Frost.G, AppTheme.Frost.B));

        var bg = new LayerDrawable(new Drawable[] { outer, inner });
        bg.SetLayerInset(1, dp(6), dp(6), dp(6), dp(6));
        fr.Background = bg;

        // 科幻几何层（网格 + 六边形雷达 + 扫描线 + 角标，带动画）
        var sci = new SciFiBackView(ctx)
        {
            LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent)
        };
        fr.AddView(sci);

        // 顶部数据条：TACTICAL UNIT
        var topTag = UI.Text("TACTICAL UNIT", dp(8), Color.Argb(190, AppTheme.Frost.R, AppTheme.Frost.G, AppTheme.Frost.B));
        topTag.LetterSpacing = 0.28f;
        topTag.Gravity = GravityFlags.CenterHorizontal;
        topTag.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Top,
            TopMargin = dp(15)
        };
        fr.AddView(topTag);

        // 中央徽记：MILAN（等宽霜蓝 + 发光）
        var milan = UI.Text("MILAN", dp(18), AppTheme.Frost, bold: true);
        UI.Tabular(milan);
        milan.SetShadowLayer(dp(8), 0, 0, AppTheme.Frost);
        milan.Gravity = GravityFlags.Center;
        milan.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Center
        };
        fr.AddView(milan);

        // 底部状态码：STANDBY · 0x4F2A
        var sub = UI.Text("STANDBY · 0x4F2A", dp(8), Color.Argb(140, AppTheme.Frost.R, AppTheme.Frost.G, AppTheme.Frost.B));
        UI.Tabular(sub);
        sub.LetterSpacing = 0.18f;
        sub.Gravity = GravityFlags.CenterHorizontal;
        sub.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Bottom,
            BottomMargin = dp(13)
        };
        fr.AddView(sub);

        return fr;
    }
}

/// <summary>
/// 卡背科幻几何层（自绘 + 动画）。继承 AnimatedEffectView：不可见/脱离窗口自动停帧。
/// 绘制：暮紫底辉光 → 霜蓝等距网格 → 旋转六边形雷达双环 → 自上而下扫描线（中段熔金高光）
/// → 四角 HUD 角标 → 中心克制金高光点。遵循 ColorLong 铁律，复用 Paint/Path/Gradient 降低 GC。
/// </summary>
internal sealed class SciFiBackView : AnimatedEffectView
{
    private readonly Paint _p = new() { AntiAlias = true };
    private Android.Graphics.Path? _grid;
    private Android.Graphics.Path? _hex;
    private Android.Graphics.Path? _hex2;
    private RadialGradient? _glow;
    private int _gw, _gh;

    // 颜色（遵循 ColorLong 铁律：渐变用 long[]）
    private static readonly Color CGrid = Color.Argb(26, 0x7F, 0xC4, 0xFF);   // 霜蓝网格
    private static readonly Color CHex = Color.Argb(150, 0x7F, 0xC4, 0xFF);   // 霜蓝雷达环
    private static readonly Color CFrame = Color.Argb(190, 0x7F, 0xC4, 0xFF); // 霜蓝角标
    private static readonly Color CScan = Color.Argb(180, 0x9F, 0xD8, 0xFF);  // 霜蓝扫描线
    private static readonly Color CGold = AppTheme.GoldHi;                    // 克制金高光

    public SciFiBackView(Context ctx) : base(ctx) => SetWillNotDraw(false);

    protected override void OnDraw(Canvas canvas)
    {
        int w = Width, h = Height;
        if (w <= 0 || h <= 0) return;
        if (_grid == null || _gw != w || _gh != h) BuildCache(w, h);

        long now = SystemClock.ElapsedRealtime();
        float scanT = (now % 5200) / 5200f;       // 扫描线周期 5.2s
        float spin = (now % 14000) / 14000f;      // 主环 14s 一圈
        float spin2 = (now % 9000) / 9000f;       // 副环 9s 反向

        // 暮紫底辉光
        if (_glow != null)
        {
            _p.SetShader(_glow);
            _p.Alpha = 255;
            canvas.DrawRect(0, 0, w, h, _p);
            _p.SetShader(null);
        }

        // 等距网格
        _p.SetStyle(Paint.Style.Stroke);
        _p.StrokeWidth = UI.Dp(1);
        _p.Color = CGrid;
        canvas.DrawPath(_grid!, _p);

        float cx = w / 2f, cy = h * 0.40f;

        // 旋转六边形雷达双环
        _p.Color = CHex;
        _p.StrokeWidth = UI.Dp(1.5f);
        DrawHex(canvas, cx, cy, spin * 360f);
        DrawHex(canvas, cx, cy, -(spin2 * 360f), _hex2!);

        // 中心同心圆（随主环呼吸）
        float breathe = 0.5f + 0.5f * (float)System.Math.Sin(now / 900.0);
        float ir = (_hexR * 0.46f) * (0.9f + 0.1f * breathe);
        canvas.DrawCircle(cx, cy, ir, _p);

        // 扫描线（自上而下，中段叠熔金高光——克制的金）
        float sy = scanT * h;
        float soft = UI.Dp(7);
        _p.Color = CScan;
        _p.Alpha = 60;
        canvas.DrawRect(0, sy - soft, w, sy - soft + UI.Dp(1), _p);
        canvas.DrawRect(0, sy + soft, w, sy + soft + UI.Dp(1), _p);
        _p.Alpha = 255;
        canvas.DrawRect(0, sy, w, sy + UI.Dp(1.5f), _p);
        // 中段熔金高光点
        _p.Color = CGold;
        float gw = w * 0.32f;
        canvas.DrawRect(cx - gw / 2, sy, cx + gw / 2, sy + UI.Dp(1.5f), _p);

        // 四角 HUD 角标
        DrawCorners(canvas, w, h);

        // 中心克制金高光点
        _p.Color = CGold;
        _p.SetStyle(Paint.Style.Fill);
        canvas.DrawCircle(cx, cy, UI.Dp(2.5f), _p);

        NextFrame();
    }

    private float _hexR, _hexR2;

    private void DrawHex(Canvas canvas, float cx, float cy, float deg, Android.Graphics.Path? hexOverride = null)
    {
        var hex = hexOverride ?? _hex!;
        canvas.Save();
        canvas.Translate(cx, cy);
        canvas.Rotate(deg);
        canvas.DrawPath(hex, _p);
        canvas.Restore();
    }

    private void DrawCorners(Canvas canvas, int w, int h)
    {
        _p.SetStyle(Paint.Style.Stroke);
        _p.StrokeWidth = UI.Dp(2);
        _p.Color = CFrame;
        float m = UI.Dp(10), len = UI.Dp(12);
        // 左上
        canvas.DrawLine(m, m + len, m, m, _p);
        canvas.DrawLine(m, m, m + len, m, _p);
        // 右上
        canvas.DrawLine(w - m - len, m, w - m, m, _p);
        canvas.DrawLine(w - m, m, w - m, m + len, _p);
        // 左下
        canvas.DrawLine(m, h - m - len, m, h - m, _p);
        canvas.DrawLine(m, h - m, m + len, h - m, _p);
        // 右下
        canvas.DrawLine(w - m - len, h - m, w - m, h - m, _p);
        canvas.DrawLine(w - m, h - m - len, w - m, h - m, _p);
    }

    private void BuildCache(int w, int h)
    {
        _gw = w; _gh = h;

        // 暮紫底辉光（缓存 RadialGradient）
        _glow?.Dispose();
        float gcx = w / 2f, gcy = h * 0.40f;
        float gr = System.Math.Min(w, h) * 0.75f;
        if (gr > 0)
        {
            var core = Color.Argb(70, 90, 55, 150);     // 暮紫核心
            var mid = Color.Argb(28, 58, 37, 96);
            var edge = Color.Argb(0, AppTheme.BgDeepest.R, AppTheme.BgDeepest.G, AppTheme.BgDeepest.B);
            _glow = new RadialGradient(gcx, gcy, gr,
                new long[] { UI.ColorLong(core), UI.ColorLong(mid), UI.ColorLong(edge) },
                null, Shader.TileMode.Clamp);
        }

        // 等距网格（缓存 Path）
        _grid?.Dispose();
        _grid = new Android.Graphics.Path();
        int step = UI.Dp(18);
        for (int x = step; x < w; x += step)
        {
            _grid.MoveTo(x, 0); _grid.LineTo(x, h);
        }
        for (int y = step; y < h; y += step)
        {
            _grid.MoveTo(0, y); _grid.LineTo(w, y);
        }

        // 六边形（缓存 Path，随尺寸）
        _hexR = System.Math.Min(w, h) * 0.32f;
        _hexR2 = _hexR * 0.6f;
        _hex?.Dispose();
        _hex = MakeHex(_hexR);
        _hex2?.Dispose();
        _hex2 = MakeHex(_hexR2);
    }

    private static Android.Graphics.Path MakeHex(float r)
    {
        var p = new Android.Graphics.Path();
        for (int i = 0; i < 6; i++)
        {
            double a = System.Math.PI / 6 + i * System.Math.PI / 3;  // 顶点朝上
            float x = (float)(r * System.Math.Cos(a));
            float y = (float)(r * System.Math.Sin(a));
            if (i == 0) p.MoveTo(x, y); else p.LineTo(x, y);
        }
        p.Close();
        return p;
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _p?.Dispose();
            _grid?.Dispose();
            _hex?.Dispose();
            _hex2?.Dispose();
            _glow?.Dispose();
        }
        base.Dispose(disposing);
    }
}
