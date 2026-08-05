using Android.Content;
using Android.Graphics;
using Android.Views;
using Milan.Maui;

namespace Milan.Maui;

/// <summary>
/// 一次性爆发粒子（卡牌溶解 / 出牌命中）。继承 <see cref="AnimatedEffectView"/>：
/// 粒子消亡后自动停止重绘循环并自移除，无内存泄漏。
/// 坐标基于父容器（通常是一个 MatchParent 的 HandCardLayout）。
/// </summary>
public sealed class BurstView : AnimatedEffectView
{
    private readonly Paint _paint = new() { AntiAlias = true };
    private readonly Color _tint;
    private readonly int _cx, _cy;
    private readonly Particle[] _ps;
    private int _frame;
    private const int MaxFrame = 34;

    public BurstView(Context context, Color tint, int cx, int cy, int count = 30) : base(context)
    {
        _tint = tint; _cx = cx; _cy = cy;
        var rnd = new System.Random();
        _ps = new Particle[count];
        for (int i = 0; i < count; i++)
        {
            double a = rnd.NextDouble() * System.Math.PI * 2;
            float sp = 3f + (float)rnd.NextDouble() * 12f;
            _ps[i] = new Particle((float)System.Math.Cos(a) * sp, (float)System.Math.Sin(a) * sp);
        }
    }

    protected override void OnDraw(Canvas canvas)
    {
        if (Width == 0 || Height == 0) { if (Animating) Invalidate(); return; }
        _frame++;
        float k = (float)_frame / MaxFrame;
        for (int i = 0; i < _ps.Length; i++)
        {
            var p = _ps[i];
            float x = _cx + p.Vx * _frame;
            float y = _cy + p.Vy * _frame + 0.18f * _frame * _frame; // 轻微重力下坠
            int alpha = (int)(240 * (1 - k));
            if (alpha <= 0) continue;
            int rad = (int)(4f * (1 - k) + 1f);
            _paint.Color = Color.Argb(alpha, _tint.R, _tint.G, _tint.B);
            canvas.DrawCircle(x, y, rad, _paint);
        }
        if (_frame < MaxFrame && Animating) Invalidate();
        else if (_frame >= MaxFrame) Post(() => (Parent as ViewGroup)?.RemoveView(this));
    }

    private sealed class Particle
    {
        public float Vx, Vy;
        public Particle(float vx, float vy) { Vx = vx; Vy = vy; }
    }
}
