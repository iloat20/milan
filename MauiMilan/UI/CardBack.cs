using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Views;
using Android.Widget;
using System.Collections.Generic;

namespace Milan.Maui;

/// <summary>
/// 统一卡背（2D）— 黑客帝国（The Matrix）数字雨风格：纯黑底 + 下落的绿色片假名/数字符文 +
/// 亮白头部与渐隐拖尾 + 绿光圆角边框 + 四角 HUD 角标 + 终端文字层。抽卡翻转卡与 Battle 手牌卡背共用。
/// 此元素刻意脱离暗夜神性 twilight 调色板，走 Matrix 绿光语汇（用户明确指定）。
/// </summary>
public static class CardBack
{
    // Matrix 绿光语汇（internal：供并列的 MatrixBackView 复用）
    internal static readonly Color Green = Color.Rgb(0, 255, 90);
    internal static readonly Color GreenDim = Color.Argb(180, 0, 255, 90);
    internal static readonly Color GreenFaint = Color.Argb(140, 0, 255, 90);
    internal static readonly Color Head = Color.Rgb(205, 255, 215); // 雨滴头部：亮白绿

    public static FrameLayout Build(Context ctx, int w, int h)
    {
        int dp(int v) => UI.Dp(v);
        var fr = new FrameLayout(ctx)
        {
            LayoutParameters = w > 0 && h > 0
                ? new FrameLayout.LayoutParams(w, h)
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent)
        };

        // 背景：近黑底（数字雨会覆盖，此处仅防首帧闪烁）+ 圆角
        var outer = new GradientDrawable();
        outer.SetColor(Color.Rgb(2, 10, 5).ToArgb());
        outer.SetCornerRadius(dp(14));
        fr.Background = outer;

        // 数字雨层（带离屏缓冲拖尾，动画由 AnimatedEffectView 管理）
        var rain = new MatrixBackView(ctx)
        {
            LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent)
        };
        fr.AddView(rain);

        // 顶部终端条：SYSTEM ONLINE
        var topTag = UI.Text("SYSTEM ONLINE", dp(8), GreenDim);
        topTag.LetterSpacing = 0.30f;
        topTag.Gravity = GravityFlags.CenterHorizontal;
        topTag.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Top,
            TopMargin = dp(16)
        };
        fr.AddView(topTag);

        // 中央徽记：MILAN（等宽绿光，深底药丸保证雨幕上可读）
        var milan = UI.Text("MILAN", dp(20), Green, bold: true);
        UI.Tabular(milan);
        milan.SetShadowLayer(dp(10), 0, 0, Green);
        milan.Gravity = GravityFlags.Center;
        var pill = new GradientDrawable();
        pill.SetColor(Color.Argb(160, 0, 10, 4).ToArgb());
        pill.SetCornerRadius(dp(10));
        milan.Background = pill;
        milan.SetPadding(dp(20), dp(8), dp(20), dp(8));
        milan.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Center
        };
        fr.AddView(milan);

        // 底部状态码：WAKE UP · 0101
        var sub = UI.Text("WAKE UP · 0101", dp(8), GreenFaint);
        UI.Tabular(sub);
        sub.LetterSpacing = 0.18f;
        sub.Gravity = GravityFlags.CenterHorizontal;
        sub.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Bottom,
            BottomMargin = dp(14)
        };
        fr.AddView(sub);

        return fr;
    }
}

/// <summary>
/// 卡背数字雨层（自绘 + 动画）。继承 AnimatedEffectView：不可见/脱离窗口自动停帧。
/// 离屏 Bitmap 缓冲做拖尾：每帧半透明黑覆盖整图（渐隐），再在每列头部位置画一颗亮白绿字符；
/// 旧头部留在缓冲里被逐帧压暗 → 形成下落拖尾。复用单一 Paint 与 Bitmap，OnDraw 空尺寸保护，
/// Dispose 释放 native 资源（遵守项目铁律）。
/// </summary>
internal sealed class MatrixBackView : AnimatedEffectView
{
    private readonly Paint _p = new() { AntiAlias = true };
    private readonly Paint _fade = new() { Color = Color.Black, Alpha = 42 }; // 拖尾渐隐强度
    private readonly System.Random _rnd = new();

    private Bitmap? _buf;
    private Canvas? _bufCanvas;
    private float[]? _colX;     // 每列 x 中心（px）
    private float[]? _head;     // 每列头部 y（px）
    private float[]? _speed;    // 每列下落速度（px/帧）
    private int _gw, _gh;

    private static readonly char[] Glyphs = BuildGlyphs();
    private static char[] BuildGlyphs()
    {
        var list = new List<char>();
        for (int c = 0x30A0; c <= 0x30FF; c++) list.Add((char)c);          // 片假名
        foreach (var ch in "0123456789ABCDEFZ:.=*+<>|") list.Add(ch);      // 数字 / 拉丁 / 符号
        return list.ToArray();
    }

    public MatrixBackView(Context ctx) : base(ctx) => SetWillNotDraw(false);

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        Rebuild(w, h);
    }

    private void Rebuild(int w, int h)
    {
        if (w <= 0 || h <= 0) return;
        _buf?.Dispose();
        _buf = Bitmap.CreateBitmap(w, h, Bitmap.Config.Argb8888);
        _buf.EraseColor(0xFF000000);                 // 纯黑底
        _bufCanvas = new Canvas(_buf);

        int colW = UI.Dp(13);
        int n = (int)System.Math.Ceiling((float)w / colW) + 1;
        float density = Resources?.DisplayMetrics?.Density ?? 1f;
        _colX = new float[n];
        _head = new float[n];
        _speed = new float[n];
        for (int i = 0; i < n; i++)
        {
            _colX[i] = i * colW + colW / 2f;
            _head[i] = -_rnd.Next(0, h);
            _speed[i] = (1.4f + (float)_rnd.NextDouble() * 3.2f) * density;
        }
        _gw = w; _gh = h;
    }

    protected override void OnDraw(Canvas canvas)
    {
        int w = Width, h = Height;
        if (w <= 0 || h <= 0) return;
        if (_buf == null || _gw != w || _gh != h) Rebuild(w, h);
        if (_buf == null || _bufCanvas == null || _head == null || _speed == null || _colX == null) return;

        float density = Resources?.DisplayMetrics?.Density ?? 1f;
        int colH = UI.Dp(13);

        // 1) 整图压暗 → 旧头部渐隐成拖尾
        _bufCanvas.DrawRect(0, 0, w, h, _fade);

        // 2) 推进每列并在头部画亮白绿字符
        _p.SetStyle(Paint.Style.Fill);
        _p.TextSize = UI.Dp(13);
        _p.TextAlign = Paint.Align.Center;
        _p.Color = CardBack.Head;
        for (int i = 0; i < _head.Length; i++)
        {
            _head[i] += _speed[i];
            if (_head[i] > h + UI.Dp(40))
            {
                _head[i] = -_rnd.Next(0, (int)(h * 0.4f));
                _speed[i] = (1.4f + (float)_rnd.NextDouble() * 3.2f) * density;
            }
            float y = _head[i];
            if (y < -UI.Dp(20)) continue;
            char g = Glyphs[_rnd.Next(Glyphs.Length)];
            _bufCanvas.DrawText(g.ToString(), _colX[i], y, _p);
        }

        // 3) 合成雨幕
        canvas.DrawBitmap(_buf, 0, 0, null);

        // 4) CRT 扫描线（极淡绿）
        _p.SetStyle(Paint.Style.Stroke);
        _p.StrokeWidth = UI.Dp(1);
        _p.Color = Color.Argb(16, 0, 255, 90);
        for (int y2 = 0; y2 < h; y2 += UI.Dp(3))
            canvas.DrawLine(0, y2, w, y2, _p);

        // 5) 绿光圆角边框 + 四角 HUD 角标
        DrawFrame(canvas, w, h);

        NextFrame();
    }

    private void DrawFrame(Canvas canvas, int w, int h)
    {
        float inset = UI.Dp(4);
        float r = UI.Dp(12);
        var rect = new RectF(inset, inset, w - inset, h - inset);

        _p.SetStyle(Paint.Style.Stroke);
        // 外发光
        _p.StrokeWidth = UI.Dp(4);
        _p.Color = Color.Argb(60, 0, 255, 90);
        canvas.DrawRoundRect(rect, r, r, _p);
        // 内清晰线
        _p.StrokeWidth = UI.Dp(1.5f);
        _p.Color = Color.Argb(230, 0, 255, 90);
        canvas.DrawRoundRect(rect, r, r, _p);

        // 四角 HUD 角标
        _p.StrokeWidth = UI.Dp(2);
        _p.Color = Color.Argb(240, 0, 255, 90);
        float m = UI.Dp(9), len = UI.Dp(15);
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

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _p?.Dispose();
            _fade?.Dispose();
            _buf?.Dispose();
            _bufCanvas = null;
        }
        base.Dispose(disposing);
    }
}
