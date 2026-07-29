using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Util;
using Android.Views;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// Lightweight particle effect view drawn on Canvas. Attach as overlay.
/// Supports: Glow (soft floating orbs), Spark (burst of light), Stardust (drifting motes).
/// </summary>
public class ParticleView : View
{
    private enum Kind { Glow, Spark, Stardust }

    private class P
    {
        public float X, Y, Vx, Vy, Life, MaxLife, Size;
        public Color Color;
        public float Alpha;
    }

    private readonly List<P> _particles = new();
    private readonly Random _rng = new();
    private Kind _kind = Kind.Glow;
    private Color _tint = Color.ParseColor("#FFD600");
    private int _emitRate = 3;
    private bool _running;
    private long _last;
    private float _cx, _cy;
    private int _w, _h;
    private Paint? _paint;

    public ParticleView(Context context) : base(context) { }
    public ParticleView(Context context, IAttributeSet attrs) : base(context, attrs) { }

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _w = w; _h = h; _cx = w / 2f; _cy = h / 2f;
    }

    /// <summary>Configure the emitter. Call before Start().</summary>
    public ParticleView Configure(string kind, Color tint, int emitRate)
    {
        _kind = kind switch { "spark" => Kind.Spark, "stardust" => Kind.Stardust, _ => Kind.Glow };
        _tint = tint;
        _emitRate = emitRate;
        return this;
    }

    public void Start()
    {
        if (_running) return;
        _running = true;
        _last = SystemClock.UptimeMillis();
        Invalidate();
    }

    public void Stop() { _running = false; Invalidate(); }

    private void Emit()
    {
        for (int i = 0; i < _emitRate; i++)
        {
            switch (_kind)
            {
                case Kind.Glow:
                    var angle = ((float)_rng.NextDouble()) * MathF.PI * 2;
                    var dist = ((float)_rng.NextDouble()) * MathF.Min(_w, _h) * 0.4f;
                    _particles.Add(new P
                    {
                        X = _cx + MathF.Cos(angle) * dist,
                        Y = _cy + MathF.Sin(angle) * dist,
                        Vx = (((float)_rng.NextDouble()) - 0.5f) * 0.6f,
                        Vy = -((float)_rng.NextDouble()) * 0.8f - 0.2f,
                        Life = 0, MaxLife = 2.5f + ((float)_rng.NextDouble()) * 2f,
                        Size = 6f + ((float)_rng.NextDouble()) * 10f,
                        Color = _tint, Alpha = 1f
                    });
                    break;
                case Kind.Spark:
                    var a = ((float)_rng.NextDouble()) * MathF.PI * 2;
                    var spd = 2f + ((float)_rng.NextDouble()) * 4f;
                    _particles.Add(new P
                    {
                        X = _cx, Y = _cy,
                        Vx = MathF.Cos(a) * spd, Vy = MathF.Sin(a) * spd,
                        Life = 0, MaxLife = 0.6f + ((float)_rng.NextDouble()) * 0.5f,
                        Size = 2f + ((float)_rng.NextDouble()) * 4f,
                        Color = _tint, Alpha = 1f
                    });
                    break;
                case Kind.Stardust:
                    _particles.Add(new P
                    {
                        X = ((float)_rng.NextDouble()) * _w,
                        Y = _h + 10f,
                        Vx = (((float)_rng.NextDouble()) - 0.5f) * 0.4f,
                        Vy = -0.5f - ((float)_rng.NextDouble()) * 1f,
                        Life = 0, MaxLife = 4f + ((float)_rng.NextDouble()) * 3f,
                        Size = 1f + ((float)_rng.NextDouble()) * 3f,
                        Color = _tint, Alpha = 1f
                    });
                    break;
            }
        }
    }

    protected override void OnDraw(Canvas canvas)
    {
        base.OnDraw(canvas);
        if (_paint == null) _paint = new Paint { AntiAlias = true, FilterBitmap = true };

        if (_running)
        {
            var now = SystemClock.UptimeMillis();
            var dt = Math.Min((now - _last) / 1000f, 0.05f);
            _last = now;
            Emit();
            for (int i = _particles.Count - 1; i >= 0; i--)
            {
                var p = _particles[i];
                p.Life += dt;
                if (p.Life >= p.MaxLife) { _particles.RemoveAt(i); continue; }
                p.X += p.Vx; p.Y += p.Vy;
                if (_kind == Kind.Spark) { p.Vx *= 0.96f; p.Vy *= 0.96f; }
                var t = p.Life / p.MaxLife;
                p.Alpha = t < 0.2f ? t / 0.2f : 1f - (t - 0.2f) / 0.8f;
            }
        }

        foreach (var p in _particles)
        {
            var c = Color.Argb((int)(p.Alpha * 200), p.Color.R, p.Color.G, p.Color.B);
            _paint!.Color = c;
            if (_kind == Kind.Glow)
            {
                _paint.SetShadowLayer(p.Size, 0, 0, c);
                canvas.DrawCircle(p.X, p.Y, p.Size * 0.4f, _paint);
                _paint.ClearShadowLayer();
            }
            else
            {
                canvas.DrawCircle(p.X, p.Y, p.Size, _paint);
            }
        }

        if (_running) Invalidate();
    }
}
