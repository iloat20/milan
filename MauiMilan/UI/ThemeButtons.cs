using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Util;
using Android.Views;
using Android.Widget;
using Path = Android.Graphics.Path;

namespace Milan.Maui;

/// <summary>
/// Obsidian &amp; Gold 按钮体系（ui-redesign-plan.md §2.1）：
/// - Gold(): 金色主按钮（斜切角 + 垂直渐变 + 双描边 + 周期扫光）
/// - Neon(): 霓虹青描边次按钮
/// - Danger(): 危险按钮（红渐变斜切角）
/// - IconCircle(): 44dp 玻璃图标圆钮
/// </summary>
public static class ThemeButtons
{
    /// <summary>金色主按钮：召唤 / 出战 / 购买确认（熔金渐变 + 发丝高光 + 周期扫光）。</summary>
    public static CutCornerButton Gold(Context context, string text, float textSp = 16) =>
        new(context, text,
            AppTheme.GoldHi, AppTheme.Gold, AppTheme.GoldDeep,
            AppTheme.GoldTextOn, textSp, shine: true);

    /// <summary>危险按钮：分解 / 放弃战斗（红渐变，三档由语义色 Danger 派生）。</summary>
    public static CutCornerButton Danger(Context context, string text, float textSp = 16) =>
        new(context, text,
            Mix(AppTheme.Danger, Color.White, 0.28f),
            AppTheme.Danger,
            Mix(AppTheme.Danger, Color.Black, 0.35f),
            Color.White, textSp, shine: false);

    /// <summary>霜蓝次按钮：详情 / 筛选 / 取消。</summary>
    public static Button Neon(Context context, string text, float textSp = 14)
    {
        var b = new NeonButton(context, text, AppTheme.Frost);
        b.SetTextSize(ComplexUnitType.Sp, textSp);
        return b;
    }

    /// <summary>44dp 圆形玻璃图标钮：返回 / 设置 / 关闭。</summary>
    public static TextView IconCircle(Context context, string glyph, Action onClick, int sizeDp = 44)
    {
        var t = new TextView(context)
        {
            Text = glyph,
            Gravity = GravityFlags.Center
        };
        t.SetTextColor(AppTheme.Gold);
        t.SetTextSize(ComplexUnitType.Sp, sizeDp * 0.4f);
        t.Background = UI.GlassPanel(sizeDp / 2f);
        t.LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(sizeDp), UI.Dp(sizeDp));
        t.Click += (_, _) => onClick();
        t.Touch += (s, e) =>
        {
            if (e.Event == null) { e.Handled = false; return; }
            switch (e.Event.Action)
            {
                case MotionEventActions.Down:
                    t.Animate()?.ScaleX(0.9f)?.ScaleY(0.9f)?.SetDuration(80)?.Start();
                    break;
                case MotionEventActions.Up:
                case MotionEventActions.Cancel:
                    t.Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(120)?.Start();
                    break;
            }
            e.Handled = false; // 让 Click 正常触发
        };
        return t;
    }

    /// <summary>在 a→b 间按 t∈[0,1] 线性混合颜色，保留 a 的 alpha。
    /// 用于从单一语义色（如 AppTheme.Danger）派生高光/暗部三档渐变，避免散落硬编码色。</summary>
    private static Color Mix(Color a, Color b, float t)
    {
        int L(int x, int y) => (int)(x + (y - x) * t);
        return Color.Argb(a.A, L(a.R, b.R), L(a.G, b.G), L(a.B, b.B));
    }
}

/// <summary>
    /// 斜切角立体按钮（左上/右下 6dp 切角）：垂直三段渐变填充 + 外深内亮双描边，
    /// 可选周期扫光（约 4s 一次）。继承 AnimatedEffectView，不可见时自动停帧。
    /// </summary>
public class CutCornerButton : AnimatedEffectView
{
    private const float CutDp = 6f;
    private const int ShinePeriodFrames = 240;   // ~4s @60fps

    private readonly Color _top, _mid, _bottom, _textColor;
    private readonly bool _shine;
    private readonly string _text;
    private readonly float _textSp;
    private readonly Paint _paint = new() { AntiAlias = true };
    private readonly Paint _textPaint;
    private int _frame;
    private bool _pressed;

    public int PadHDp { get; set; } = 32;
    public int PadVDp { get; set; } = 13;

    public CutCornerButton(Context context, string text,
        Color top, Color mid, Color bottom, Color textColor,
        float textSp = 15, bool shine = true) : base(context)
    {
        _text = text; _top = top; _mid = mid; _bottom = bottom;
        _textColor = textColor; _textSp = textSp; _shine = shine;
        _textPaint = new Paint { AntiAlias = true, TextAlign = Paint.Align.Center };
        _textPaint.SetTypeface(Typeface.DefaultBold);
        Clickable = true;
    }

    protected override void OnMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        _textPaint.TextSize = TypedValue.ApplyDimension(ComplexUnitType.Sp, _textSp, Resources?.DisplayMetrics);
        var tw = _textPaint.MeasureText(_text);
        var fm = _textPaint.GetFontMetrics()!;
        var desiredW = (int)(tw + UI.Dp(PadHDp) * 2);
        var desiredH = (int)((fm.Descent - fm.Ascent) + UI.Dp(PadVDp) * 2);
        SetMeasuredDimension(
            ResolveSize(desiredW, widthMeasureSpec),
            ResolveSize(desiredH, heightMeasureSpec));
    }

    private Path BuildPath(float w, float h, float inset)
    {
        var cut = UI.Dp(CutDp);
        var p = new Path();
        p.MoveTo(inset + cut, inset);
        p.LineTo(w - inset, inset);
        p.LineTo(w - inset, h - inset - cut);
        p.LineTo(w - inset - cut, h - inset);
        p.LineTo(inset, h - inset);
        p.LineTo(inset, inset + cut);
        p.Close();
        return p;
    }

    protected override void OnDraw(Canvas canvas)
    {
        float w = Width, h = Height;
        if (w == 0 || h == 0) return;

        // 外圈深色描边（画大一号的底）
        _paint.SetShader(null);
        _paint.SetStyle(Paint.Style.Fill);
        _paint.Color = Color.Argb(200, 0, 0, 0);
        canvas.DrawPath(BuildPath(w, h, 0), _paint);

        // 渐变主体
        var body = BuildPath(w, h, UI.Dp(1));
        _paint.SetShader(new LinearGradient(0, 0, 0, h,
            UI.ColorLongs(_top, _mid, _bottom),
            new[] { 0f, 0.35f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(body, _paint);
        _paint.SetShader(null);

        // 内圈亮描边
        _paint.SetStyle(Paint.Style.Stroke);
        _paint.StrokeWidth = UI.Dp(1);
        _paint.Color = Color.Argb(120, 255, 255, 255);
        canvas.DrawPath(body, _paint);
        _paint.SetStyle(Paint.Style.Fill);

        // 顶部内高光（上半 40%）
        _paint.SetShader(new LinearGradient(0, 0, 0, h * 0.4f,
            UI.ColorLong(Color.Argb(46, 255, 255, 255)), UI.ColorLong(Color.Argb(0, 255, 255, 255)), Shader.TileMode.Clamp));
        canvas.Save();
        canvas.ClipPath(body);
        canvas.DrawRect(0, 0, w, h * 0.4f, _paint);
        _paint.SetShader(null);

        // 周期扫光
        if (_shine)
        {
            _frame++;
            if (_frame >= ShinePeriodFrames) _frame = 0;
            var sweepFrames = 40f;
            if (_frame < sweepFrames)
            {
                var t = _frame / sweepFrames;
                var cx = -w * 0.4f + t * (w * 1.8f);
                _paint.SetShader(new LinearGradient(cx - UI.Dp(30), 0, cx + UI.Dp(30), h,
                    UI.ColorLong(Color.Argb(0, 255, 255, 255)), UI.ColorLong(Color.Argb(140, 255, 255, 255)), Shader.TileMode.Clamp));
                canvas.DrawRect(0, 0, w, h, _paint);
                _paint.SetShader(null);
            }
        }

        // 按压提亮
        if (_pressed)
        {
            _paint.Color = Color.Argb(26, 255, 255, 255);
            canvas.DrawRect(0, 0, w, h, _paint);
        }
        canvas.Restore();

        // 文字
        _textPaint.Color = _textColor;
        var fm = _textPaint.GetFontMetrics()!;
        canvas.DrawText(_text, w / 2f, h / 2f - (fm.Ascent + fm.Descent) / 2f, _textPaint);

        if (Animating && _shine) Invalidate();
    }

    public override bool OnTouchEvent(MotionEvent? e)
    {
        if (e == null) return false;
        switch (e.Action)
        {
            case MotionEventActions.Down:
                _pressed = true;
                Animate()?.ScaleX(0.96f)?.ScaleY(0.96f)?.SetDuration(80)?.Start();
                Invalidate();
                return true;
            case MotionEventActions.Up:
                _pressed = false;
                Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(120)?.Start();
                Invalidate();
                PerformClick();
                return true;
            case MotionEventActions.Cancel:
                _pressed = false;
                Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(120)?.Start();
                Invalidate();
                return true;
        }
        return base.OnTouchEvent(e);
    }

    public override bool PerformClick() => base.PerformClick();
}

/// <summary>霓虹描边次按钮：透明底 + 描边 + 内发光，按压填充提亮。</summary>
public class NeonButton : Button
{
    private readonly Color _neon;

    public NeonButton(Context context, string text, Color neon) : base(context)
    {
        _neon = neon;
        Text = text;
        SetTextColor(neon);
        SetTypeface(null, TypefaceStyle.Bold);
        SetPadding(UI.Dp(28), UI.Dp(12), UI.Dp(28), UI.Dp(12));
        SetMinimumHeight(0);
        SetMinHeight(0);
        UpdateBackground(false);
    }

    private void UpdateBackground(bool pressed)
    {
        var gd = new GradientDrawable();
        gd.SetColor(Color.Argb(pressed ? 46 : 15, _neon.R, _neon.G, _neon.B).ToArgb());
        gd.SetCornerRadius(UI.Dp(10));
        gd.SetStroke(UI.Dp(1.5f), _neon);
        Background = gd;
    }

    public override bool OnTouchEvent(MotionEvent? e)
    {
        if (e == null) return false;
        switch (e.Action)
        {
            case MotionEventActions.Down:
                UpdateBackground(true);
                Animate()?.ScaleX(0.96f)?.ScaleY(0.96f)?.SetDuration(80)?.Start();
                return true;
            case MotionEventActions.Up:
                UpdateBackground(false);
                Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(120)?.Start();
                PerformClick();
                return true;
            case MotionEventActions.Cancel:
                UpdateBackground(false);
                Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(120)?.Start();
                return true;
        }
        return base.OnTouchEvent(e);
    }

    public override bool PerformClick() => base.PerformClick();
}
