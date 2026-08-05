using Android.Content;
using Android.Graphics;
using Android.Views;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// 轻量武器特效叠层：把 <see cref="VfxRenderer.DrawWeapon"/> 的成形武器演出
/// 叠加在任意容器之上（抽卡出货、详情页等），自带 30fps 动画循环与可见性生命周期防护。
/// </summary>
public sealed class WeaponFxView : View
{
    private readonly string _weaponVfx;
    private readonly Color _rarityCore;
    private readonly Color _elementGlow;
    private readonly string _element;
    private readonly string _rarity;
    private readonly Paint _paint = new() { AntiAlias = true, FilterBitmap = true };
    // #37: 复用 RectF，避免每帧新建对象触发 GC 抖动。
    private readonly RectF _box = new();
    private readonly RectF _dst = new();
    private float _phase;
    private bool _running;

    public WeaponFxView(Context context, string weaponVfx, Color rarityCore, Color elementGlow, string element = "", string rarity = "")
        : base(context)
    {
        _weaponVfx = weaponVfx ?? "";
        _rarityCore = rarityCore;
        _elementGlow = elementGlow;
        _element = element;
        _rarity = rarity;
    }

    public override void Draw(Canvas? canvas)
    {
        base.Draw(canvas);
        if (canvas == null || _w == 0 || _h == 0 || string.IsNullOrEmpty(_weaponVfx)) return;

        // 居中武器盒（约卡牌大小），让成形武器正好覆在主体上
        var boxW = Math.Min(_w * 0.72f, UI.Dp(240));
        var boxH = boxW * 1.4f;
        if (boxW <= 0 || boxH <= 0) return;
        _box.Set((_w - boxW) / 2f, (_h - boxH) / 2f, (_w + boxW) / 2f, (_h + boxH) / 2f);

        var bmp = VfxRenderer.GetWeaponBitmap(Context, _weaponVfx);
        if (bmp != null && !bmp.IsRecycled)
        {
            VfxRenderer.DrawWeaponBackdrop(canvas, _paint, _box, _phase, _elementGlow, _element, _rarity);
            float side = Math.Min(_box.Width(), _box.Height());
            _dst.Set(_box.CenterX() - side / 2f, _box.CenterY() - side / 2f, _box.CenterX() + side / 2f, _box.CenterY() + side / 2f);
            canvas.DrawBitmap(bmp, null, _dst, _paint);
        }
        else
        {
            VfxRenderer.DrawWeapon(canvas, _weaponVfx, _box, _phase, _paint, _rarityCore, _elementGlow, _element, _rarity);
        }

        // #10: 帧驱动必须在绘制后继续推进，否则 Tick() 只在 attach 时跑一次，动画停在第一帧。
        Tick();
    }

    private int _w, _h;
    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _w = w; _h = h;
    }

    private bool _framePending;

    /// <summary>推进一帧并预约下一帧。仅在绘制完成后调用。</summary>
    private void Tick()
    {
        _framePending = false;
        if (!_running) return;
        _phase += 0.045f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        ScheduleFrame();
    }

    /// <summary>预约下一帧，重复调用不会叠加多条动画链。</summary>
    private void ScheduleFrame()
    {
        if (!_running || _framePending) return;
        _framePending = true;
        PostInvalidateDelayed(33);
    }

    private void StopLoop()
    {
        _running = false;
        _framePending = false;
    }

    protected override void OnAttachedToWindow()
    {
        base.OnAttachedToWindow();
        if (!_running)
        {
            _running = true;
            _framePending = false;
            ScheduleFrame();
        }
    }

    protected override void OnDetachedFromWindow()
    {
        StopLoop();
        base.OnDetachedFromWindow();
    }

    protected override void OnWindowVisibilityChanged(ViewStates visibility)
    {
        base.OnWindowVisibilityChanged(visibility);
        if (visibility == ViewStates.Visible)
        {
            if (!_running) { _running = true; _framePending = false; ScheduleFrame(); }
        }
        else
        {
            StopLoop();
        }
    }
}
