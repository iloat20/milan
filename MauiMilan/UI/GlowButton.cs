using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Util;
using Android.Views;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// Button with a glowing cosmic border and press feedback.
/// Uses shadow layer for the glow effect.
/// </summary>
public class GlowButton : Button
{
    private readonly Color _glowColor;
    private readonly float _cornerDp;
    private bool _pressed;

    public GlowButton(Context context, string text, Color fillColor, Color textColor, float cornerDp = 14) : base(context)
    {
        _cornerDp = cornerDp;
        _glowColor = fillColor;
        Text = text;
        SetTextColor(textColor);
        SetTextSize(ComplexUnitType.Sp, 15);
        SetTypeface(null, TypefaceStyle.Bold);
        UpdateBackground(false);
        SetPadding(Dp(20), Dp(12), Dp(20), Dp(12));
        SetMinimumHeight(0);
        SetMinHeight(0);
    }

    private void UpdateBackground(bool pressed)
    {
        var density = Resources.DisplayMetrics.Density;
        var radius = (int)(_cornerDp * density);
        var bg = new GradientDrawable();
        bg.SetCornerRadius(radius);
        bg.SetColor(_glowColor.ToArgb());
        bg.SetStroke((int)(2 * density), Color.Argb(pressed ? 200 : 100, _glowColor.R, _glowColor.G, _glowColor.B));
        Background = bg;
    }

    public override bool OnTouchEvent(MotionEvent e)
    {
        switch (e.Action)
        {
            case MotionEventActions.Down:
                _pressed = true;
                UpdateBackground(true);
                Animate().ScaleX(0.96f).ScaleY(0.96f).SetDuration(80).Start();
                return true;
            case MotionEventActions.Up:
                _pressed = false;
                UpdateBackground(false);
                Animate().ScaleX(1f).ScaleY(1f).SetDuration(80).Start();
                PerformClick();
                return true;
            case MotionEventActions.Cancel:
                _pressed = false;
                UpdateBackground(false);
                Animate().ScaleX(1f).ScaleY(1f).SetDuration(80).Start();
                return true;
        }
        return base.OnTouchEvent(e);
    }

    public override bool PerformClick()
    {
        return base.PerformClick();
    }

    private int Dp(int v) => (int)(v * Resources.DisplayMetrics.Density);
}
