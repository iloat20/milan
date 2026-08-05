using Android.Content;
using Android.Graphics;
using Android.Util;
using Android.Views;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// 统一风格头像（诸神黄昏·东方）：圆形裁切立绘 + 稀有度渐变光环（SweepGradient 缓慢旋转）+ 内描边。
/// 全游戏复用 —— 首页「诸神名录」横滑、角色列表、抽卡结果、图鉴、详情页头像。
/// 动画生命周期与 PortraitView 一致：脱离窗口/不可见时停止重绘，避免后台耗电。
/// </summary>
public class UnifiedAvatarView : View
{
    private Bitmap? _bitmap;
    private int _rarity = 1;
    private Color _core, _edge;
    private float _angle;
    private Paint? _paint;
    private int _sizePx;

    public UnifiedAvatarView(Context context) : base(context)
    {
        SetWillNotDraw(false);
        _core = Color.Argb(255, 255, 200, 87);
        _edge = Color.Argb(255, 232, 184, 75);
    }

    public UnifiedAvatarView Bind(CharacterDataEntry def, int sizeDp = 56)
    {
        _rarity = def.BaseRarity;
        (_core, _edge) = def.BaseRarity switch
        {
            4 => (Color.Argb(255, 255, 200, 87), Color.Argb(255, 232, 184, 75)),   // 熔金
            3 => (Color.Argb(255, 199, 155, 255), Color.Argb(255, 154, 107, 255)),  // 暮紫
            2 => (Color.Argb(255, 127, 196, 255), Color.Argb(255, 74, 144, 217)),   // 霜蓝
            _ => (Color.Argb(255, 232, 226, 242), Color.Argb(255, 200, 200, 220))   // 苍白
        };
        _bitmap = PortraitLoader.Get(def.CharacterId);
        Invalidate();
        return this;
    }

    protected override void OnMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        // 正方形，由布局参数决定尺寸
        var w = MeasureSpec.GetSize(widthMeasureSpec);
        var h = MeasureSpec.GetSize(heightMeasureSpec);
        var s = Math.Min(w, h);
        if (s <= 0) s = UI.Dp(56);
        _sizePx = s;
        SetMeasuredDimension(s, s);
    }

    protected override void OnDraw(Canvas canvas)
    {
        if (_paint == null) _paint = new Paint { AntiAlias = true, FilterBitmap = true };
        if (_sizePx <= 0) _sizePx = Width;
        var cx = _sizePx / 2f;
        var cy = _sizePx / 2f;
        var ringW = _sizePx * 0.06f;
        var outerR = _sizePx / 2f - ringW / 2f;
        var innerR = outerR - ringW;

        // 1. 稀有度渐变光环（旋转）
        canvas.Save();
        canvas.Rotate(_angle, cx, cy);
        _paint.SetStyle(Paint.Style.Stroke);
        _paint.StrokeWidth = ringW;
        _paint.SetShader(new SweepGradient(cx, cy,
            UI.ColorLongs(_core, _edge, _core),
            new[] { 0f, 0.5f, 1f }));
        canvas.DrawCircle(cx, cy, outerR, _paint);
        _paint.SetShader(null);
        canvas.Restore();

        // 2. 内底（透明 PNG 处显示为暗色，避免穿帮）
        _paint.SetStyle(Paint.Style.Fill);
        _paint.Color = Color.Argb(255, 0x0C, 0x07, 0x16);
        canvas.DrawCircle(cx, cy, innerR, _paint);

        // 3. 圆形裁切立绘（cover 适配）
        if (_bitmap != null && !_bitmap.IsRecycled)
        {
            canvas.Save();
            var clip = new Android.Graphics.Path();
            clip.AddCircle(cx, cy, innerR, Android.Graphics.Path.Direction.Cw);
            canvas.ClipPath(clip);
            var scale = Math.Max(innerR * 2f / _bitmap.Width, innerR * 2f / _bitmap.Height);
            var dw = _bitmap.Width * scale;
            var dh = _bitmap.Height * scale;
            _paint.SetShader(null);
            _paint.Color = Color.White;
            canvas.DrawBitmap(_bitmap,
                new Rect(0, 0, _bitmap.Width, _bitmap.Height),
                new RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f), _paint);
            canvas.Restore();
        }

        // 4. 内描边（暗色，分隔立绘与光环）
        _paint.SetStyle(Paint.Style.Stroke);
        _paint.StrokeWidth = UI.Dp(1.2f);
        _paint.Color = Color.Argb(180, 0, 0, 0);
        canvas.DrawCircle(cx, cy, innerR - UI.Dp(0.6f), _paint);

        if (_animating)
        {
            _angle = (_angle + 1.1f) % 360f;
            PostInvalidateDelayed(33);
        }
    }

    private bool _animating = true;
    protected override void OnAttachedToWindow() { base.OnAttachedToWindow(); _animating = true; Invalidate(); }
    protected override void OnDetachedFromWindow() { base.OnDetachedFromWindow(); _animating = false; }
    protected override void OnWindowVisibilityChanged(ViewStates visibility)
    {
        base.OnWindowVisibilityChanged(visibility);
        _animating = visibility == ViewStates.Visible;
        if (_animating) Invalidate();
    }
}
