using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Views;
using Android.Widget;
using System.Collections.Generic;

namespace Milan.Maui;

/// <summary>
/// 统一卡背（2D）— 黑客帝国（The Matrix）数字雨风格：纯黑底 + 铺满整屏的绿色下落码字
/// （片假名 / 数字 / 符号）+ 雨滴尖端亮白 + 长拖尾，MILAN 仅作半透明幽灵水印。
/// 抽卡翻转卡与 Battle 手牌卡背共用。此元素刻意脱离暗夜神性 twilight 调色板，走 Matrix 绿光语汇。
/// </summary>
public static class CardBack
{
    // Matrix 绿光语汇（internal：供并列的 MatrixBackView 复用）
    internal static readonly Color Green = Color.Rgb(0, 255, 65);   // 码字主绿
    internal static readonly Color GreenDim = Color.Argb(170, 0, 255, 90);
    internal static readonly Color GreenFaint = Color.Argb(120, 0, 255, 90);
    internal static readonly Color Tip = Color.Rgb(225, 255, 230);  // 雨滴尖端：亮白绿

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
        outer.SetColor(Color.Rgb(2, 8, 4).ToArgb());
        outer.SetCornerRadius(dp(14));
        fr.Background = outer;

        // 数字雨层（带离屏缓冲拖尾，动画由 AnimatedEffectView 管理）
        var rain = new MatrixBackView(ctx)
        {
            LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent)
        };
        fr.AddView(rain);

        // 幽灵水印：MILAN（半透明绿光，无药丸遮挡，让雨幕透出）
        var milan = UI.Text("MILAN", dp(18), Color.Argb(150, 0, 255, 90));
        UI.Tabular(milan);
        milan.SetShadowLayer(dp(10), 0, 0, Green);
        milan.Gravity = GravityFlags.Center;
        milan.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Center
        };
        fr.AddView(milan);

        return fr;
    }
}

/// <summary>
/// 卡背数字雨层（自绘 + 动画）。继承 AnimatedEffectView：不可见/脱离窗口自动停帧。
/// 离屏 Bitmap 缓冲做拖尾：每帧半透明黑覆盖整图（长拖尾渐隐），再在每列头部位置画一颗亮绿码字、
/// 叠一颗亮白头（雨滴尖端）；旧码字留在缓冲里被逐帧压暗 → 形成绿色码字长拖尾。
/// 复用单一 Paint 与 Bitmap，OnDraw 空尺寸保护，Dispose 释放 native 资源（遵守项目铁律）。
/// </summary>
internal sealed class MatrixBackView : AnimatedEffectView
{
    private readonly Paint _p = new() { AntiAlias = true };
    private readonly Paint _fade = new() { Color = Color.Black, Alpha = 22 }; // 拖尾渐隐强度（低=长尾）
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
        for (int c = 0x30A0; c <= 0x30FF; c++) list.Add((char)c);          // 半角片假名
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
            _speed[i] = (2.4f + (float)_rnd.NextDouble() * 4f) * density;
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

        // 1) 整图压暗 → 旧码字渐隐成绿色长拖尾
        _bufCanvas.DrawRect(0, 0, w, h, _fade);

        // 2) 推进每列：先画亮绿码字（拖尾主体），再叠亮白头（雨滴尖端）
        _p.SetStyle(Paint.Style.Fill);
        _p.TextSize = UI.Dp(13);
        _p.TextAlign = Paint.Align.Center;
        for (int i = 0; i < _head.Length; i++)
        {
            _head[i] += _speed[i];
            if (_head[i] > h + UI.Dp(40))
            {
                _head[i] = -_rnd.Next(0, (int)(h * 0.4f));
                _speed[i] = (2.4f + (float)_rnd.NextDouble() * 4f) * density;
            }
            float y = _head[i];
            if (y < -UI.Dp(20)) continue;
            char g = Glyphs[_rnd.Next(Glyphs.Length)];
            _p.Color = CardBack.Green;
            _bufCanvas.DrawText(g.ToString(), _colX[i], y, _p);
            _p.Color = CardBack.Tip;
            _bufCanvas.DrawText(g.ToString(), _colX[i], y, _p);
        }

        // 3) 合成雨幕
        canvas.DrawBitmap(_buf, 0, 0, null);

        // 4) 绿光圆角边框（仅细框，去除一切 HUD / 扫描线装饰）
        DrawFrame(canvas, w, h);

        NextFrame();
    }

    private void DrawFrame(Canvas canvas, int w, int h)
    {
        float inset = UI.Dp(4);
        float r = UI.Dp(12);
        var rect = new RectF(inset, inset, w - inset, h - inset);
        _p.SetStyle(Paint.Style.Stroke);
        _p.StrokeWidth = UI.Dp(4);
        _p.Color = Color.Argb(55, 0, 255, 90);
        canvas.DrawRoundRect(rect, r, r, _p);
        _p.StrokeWidth = UI.Dp(1.5f);
        _p.Color = Color.Argb(230, 0, 255, 90);
        canvas.DrawRoundRect(rect, r, r, _p);
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
