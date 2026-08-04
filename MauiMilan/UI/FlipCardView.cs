using Android.Content;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// 通用可翻转卡牌（Obsidian &amp; Gold 动效规范 §5）：背面 → 正面（或反之）带
/// 90° 换面的缩放翻转动效。用于抽卡演出、图鉴、角色卡等一切需要"翻牌"交互的地方。
/// 用法：先 <see cref="SetFaces"/> 注入正反两面，再 <see cref="ShowFront"/> /
/// <see cref="ShowBack"/> / <see cref="Flip"/> 控制显隐与动画。
/// </summary>
public sealed class FlipCardView : FrameLayout
{
    private View? _front;
    private View? _back;
    private bool _showingFront;
    private readonly Handler _handler = new(Looper.MainLooper!);

    public FlipCardView(Context context) : base(context) { }

    /// <summary>注入正反两面。初始显示背面。</summary>
    public void SetFaces(View front, View back)
    {
        _front = front;
        _back = back;
        RemoveAllViews();
        back.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        front.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        front.Visibility = ViewStates.Gone;
        AddView(back);
        AddView(front);
        _showingFront = false;
    }

    public bool IsFront => _showingFront;
    public View? FrontView => _front;
    public View? BackView => _back;

    public void ShowFront(bool animate) => FlipTo(true, animate);
    public void ShowBack(bool animate) => FlipTo(false, animate);

    public void Flip()
    {
        if (_showingFront) FlipTo(false, true);
        else FlipTo(true, true);
    }

    private void FlipTo(bool toFront, bool animate)
    {
        if (_front == null || _back == null) return;
        var cur = toFront ? _back! : _front!;
        var nxt = toFront ? _front! : _back!;
        _showingFront = toFront;

        if (!animate)
        {
            cur.Visibility = ViewStates.Gone;
            nxt.Visibility = ViewStates.Visible;
            nxt.ScaleX = 1f; nxt.ScaleY = 1f;
            return;
        }

        // 以卡牌中心为轴做 90° 换面：当前面收缩到 0 → 换面 → 新面展开
        cur.PivotX = cur.Width > 0 ? cur.Width / 2f : Width / 2f;
        cur.PivotY = cur.Height > 0 ? cur.Height / 2f : Height / 2f;
        nxt.PivotX = nxt.Width > 0 ? nxt.Width / 2f : Width / 2f;
        nxt.PivotY = nxt.Height > 0 ? nxt.Height / 2f : Height / 2f;

        cur.Animate()?.ScaleX(0f)?.SetDuration(180)?.SetInterpolator(Motion.Ease)?.Start();
        _handler.PostDelayed(() =>
        {
            if (cur.Handle == IntPtr.Zero) return;
            cur.Visibility = ViewStates.Gone;
            nxt.Visibility = ViewStates.Visible;
            nxt.ScaleX = 0f;
            nxt.Animate()?.ScaleX(1f)?.SetDuration(180)?.SetInterpolator(Motion.Ease)?.Start();
        }, 180);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing) _handler.RemoveCallbacksAndMessages(null);
        base.Dispose(disposing);
    }
}
