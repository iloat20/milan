using Android.Content;
using Android.Graphics;
using Android.Views;
using Android.Views.Animations;

namespace Milan.Maui;

/// <summary>
/// 通用拖拽控制器：挂到任意 View 上即可实现「跟随手指拖拽 → 命中目标区触发 / 否则 Spring 回弹」。
/// 基于 <see cref="View.Touch"/> 事件（符合项目约定，避免手动实现 IOnTouchListener）。
/// </summary>
public sealed class DragController
{
    private readonly View _card;
    private readonly System.Func<Rect>? _dropRect;
    private readonly System.Action<View>? _onDrop;
    private readonly System.Action<View>? _onTap;
    private float _homeX, _homeY, _homeRot;
    private float _startRawX, _startRawY;
    private bool _dragging;
    private const float Threshold = 12f;

    public DragController(View card, float homeX, float homeY, float homeRot,
        System.Func<Rect>? dropRect = null, System.Action<View>? onDrop = null, System.Action<View>? onTap = null)
    {
        _card = card;
        _homeX = homeX; _homeY = homeY; _homeRot = homeRot;
        _dropRect = dropRect; _onDrop = onDrop; _onTap = onTap;
        _card.Touch += OnTouch;
    }

    /// <summary>重排后由布局方更新卡的归位坐标与旋转。</summary>
    public void SetHome(float x, float y, float rot) { _homeX = x; _homeY = y; _homeRot = rot; }

    private void OnTouch(object? s, View.TouchEventArgs e)
    {
        var ev = e.Event;
        if (ev == null) return;
        switch (ev.Action & MotionEventActions.Mask)
        {
            case MotionEventActions.Down:
                _startRawX = ev.RawX; _startRawY = ev.RawY;
                _homeX = _card.TranslationX; _homeY = _card.TranslationY; _homeRot = _card.Rotation;
                _card.Elevation = 60f;
                _dragging = false;
                e.Handled = true;
                break;

            case MotionEventActions.Move:
                {
                    float dx = ev.RawX - _startRawX;
                    float dy = ev.RawY - _startRawY;
                    if (!_dragging && (System.Math.Abs(dx) + System.Math.Abs(dy) > Threshold)) _dragging = true;
                    if (_dragging)
                    {
                        _card.TranslationX = _homeX + dx;
                        _card.TranslationY = _homeY + dy;
                        _card.Rotation = 0f; // 拖拽时摆正，跟手更自然
                    }
                    e.Handled = true;
                }
                break;

            case MotionEventActions.Up:
                if (_dragging)
                {
                    var r = _dropRect?.Invoke();
                    if (r != null && Hit(r)) { _card.Elevation = 0f; _onDrop?.Invoke(_card); }
                    else SpringBack();
                }
                else { _card.Elevation = 0f; _onTap?.Invoke(_card); }
                e.Handled = true;
                break;

            case MotionEventActions.Cancel:
                SpringBack();
                e.Handled = true;
                break;
        }
    }

    private bool Hit(Rect r)
    {
        int[] loc = new int[2];
        _card.GetLocationOnScreen(loc);
        int cx = loc[0] + _card.Width / 2;
        int cy = loc[1] + _card.Height / 2;
        return r.Contains(cx, cy);
    }

    /// <summary>带 Overshoot 的回弹动画，落回应有的扇形位置。</summary>
    public void SpringBack()
    {
        _card.Elevation = 0f;
        _card.Animate()
            .TranslationX(_homeX).TranslationY(_homeY).Rotation(_homeRot)
            .SetDuration(320)
            .SetInterpolator(new OvershootInterpolator(1.8f))
            .Start();
    }
}
