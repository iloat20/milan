using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Views;

namespace Milan.Maui;

/// <summary>
/// Full-screen cosmic nebula background: deep purple radial gradient with
/// slowly drifting starlight particles. Drop behind any content.
/// </summary>
public class CosmicBackground : View
{
    private float _phase;
    private Paint? _paint;
    private readonly Random _rng = new();
    private bool _running;

    private struct Star
    {
        public float X, Y, Speed, Size, Alpha;
    }
    private Star[] _stars = null!;

    public CosmicBackground(Context context) : base(context) { SetWillNotDraw(false); }

    private void InitStars()
    {
        if (_stars != null) return;
        _stars = new Star[18];
        for (int i = 0; i < _stars.Length; i++)
        {
            _stars[i] = new Star
            {
                X = _rng.Next(Math.Max(1, Width)),
                Y = _rng.Next(Math.Max(1, Height)),
                Speed = 0.2f + _rng.Next(3) * 0.3f,
                Size = 1 + _rng.Next(3),
                Alpha = 80 + _rng.Next(100)
            };
        }
    }

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _stars = new Star[18];
        for (int i = 0; i < _stars.Length; i++)
        {
            _stars[i] = new Star
            {
                X = _rng.Next(w),
                Y = _rng.Next(h),
                Speed = 0.2f + _rng.Next(3) * 0.3f,
                Size = 1 + _rng.Next(3),
                Alpha = 80 + _rng.Next(100)
            };
        }
    }

    public void Start() { _running = true; Invalidate(); }
    public void Stop() { _running = false; }

    protected override void OnDraw(Canvas canvas)
    {
        if (_paint == null) _paint = new Paint { AntiAlias = true };
        var w = Width; var h = Height;
        if (w == 0 || h == 0) return;
        InitStars();

        // Deep purple vertical gradient via LinearGradient shader
        var shader = new LinearGradient(0, 0, 0, h,
            Color.Argb(255, 13, 2, 33), Color.Argb(255, 26, 5, 51), Shader.TileMode.Clamp);
        _paint!.SetShader(shader);
        canvas.DrawRect(0, 0, w, h, _paint);
        _paint.SetShader(null);

        // Central nebula glow
        _paint.Color = Color.Argb(50, 124, 77, 255);
        canvas.DrawCircle(w / 2f, h * 0.4f, w * 0.5f, _paint);

        if (_running)
        {
            _phase += 0.015f;
            for (int i = 0; i < _stars.Length; i++)
            {
                var s = _stars[i];
                s.Y -= s.Speed;
                if (s.Y < -10) { s.Y = h + 10; s.X = _rng.Next(w); }
                var a = (int)(s.Alpha * (0.5f + 0.5f * MathF.Sin(_phase + i)));
                _paint!.Color = Color.Argb(a, 180, 160, 255);
                canvas.DrawCircle(s.X, s.Y, s.Size, _paint);
                _stars[i] = s;
            }
        }
        else
        {
            foreach (var s in _stars)
            {
                _paint!.Color = Color.Argb((int)s.Alpha, 180, 160, 255);
                canvas.DrawCircle(s.X, s.Y, s.Size, _paint);
            }
        }

        if (_running) Invalidate();
    }
}
