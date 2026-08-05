using Android.Content;
using Android.Graphics;
using Android.Views;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// 扇形手牌布局（Obsidian &amp; Gold 动效规范 §5）：将卡牌按圆弧排列（中间低、两侧高且外倾），
/// 每张卡可拖拽出牌（命中 dropTarget 触发 <see cref="CardPlayed"/> 并溶解粒子，否则 Spring 回弹）。
/// 卡牌载体建议用 <see cref="FlipCardView"/>，未出牌前点一下可翻面查看。
/// </summary>
public sealed class HandCardLayout : FrameLayout
{
    private readonly List<View> _cards = new();
    private readonly Dictionary<View, DragController> _ctrl = new();
    private View? _dropTarget;
    private float _fanDeg = 32f;
    public event System.Action<View>? CardPlayed;

    public HandCardLayout(Context context) : base(context) { }

    public void SetDropTarget(View v) => _dropTarget = v;

    public void SetCards(List<View> cards)
    {
        RemoveAllViews();
        _cards.Clear();
        _ctrl.Clear();
        foreach (var c in cards) { AddView(c); _cards.Add(c); }
        Arrange();
    }

    /// <summary>按当前尺寸把卡牌排成扇形。尺寸确定前（Width==0）跳过，OnSizeChanged 会再触发。</summary>
    public void Arrange()
    {
        int n = _cards.Count;
        if (n == 0 || Width == 0) return;
        float R = System.Math.Max(Width * 0.95f, Height * 1.25f);
        for (int i = 0; i < n; i++)
        {
            var card = _cards[i];
            float t = n == 1 ? 0.5f : (float)i / (n - 1);
            float ang = (t - 0.5f) * _fanDeg * (float)System.Math.PI / 180f;
            float tx = (float)System.Math.Sin(ang) * R * 0.40f;
            float ty = -(float)(1 - System.Math.Cos(ang)) * R * 0.40f;
            float rot = (t - 0.5f) * -18f;
            card.TranslationX = tx;
            card.TranslationY = ty;
            card.Rotation = rot;
            int z = n - (int)System.Math.Abs(i - (n - 1) / 2f);
            card.Elevation = 6f + z * 2f;
            if (!_ctrl.ContainsKey(card))
                _ctrl[card] = new DragController(card, tx, ty, rot,
                    () => DropRect(), v => OnPlay(v), v => OnTap(v));
            else
                _ctrl[card].SetHome(tx, ty, rot);
        }
    }

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        if (_cards.Count > 0) Arrange();
    }

    private Rect DropRect()
    {
        if (_dropTarget == null) return new Rect();
        int[] loc = new int[2];
        _dropTarget.GetLocationOnScreen(loc);
        return new Rect(loc[0], loc[1], loc[0] + _dropTarget.Width, loc[1] + _dropTarget.Height);
    }

    private void OnPlay(View card)
    {
        int[] loc = new int[2];
        card.GetLocationInWindow(loc);
        int[] my = new int[2];
        GetLocationInWindow(my);
        float cx = loc[0] - my[0] + card.Width / 2f;
        float cy = loc[1] - my[1] + card.Height / 2f;

        _cards.Remove(card);
        _ctrl.Remove(card);

        SpawnBurst(cx, cy);
        card.Animate().ScaleX(0f).ScaleY(0f).Alpha(0f).SetDuration(220)
            .WithEndAction(new Java.Lang.Runnable(() => RemoveView(card))).Start();

        Arrange();
        CardPlayed?.Invoke(card);
    }

    private void SpawnBurst(float cx, float cy)
    {
        var burst = new BurstView(Context, AppTheme.Gold, (int)cx, (int)cy);
        burst.LayoutParameters = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        AddView(burst);
    }

    private void OnTap(View card)
    {
        if (card is FlipCardView fv) fv.Flip();
    }
}
