using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Views;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// Procedurally draws a unique anime-style character portrait per character.
/// Each character id maps to distinct face shape, hair, eyes, colors, and
/// an element-themed aura. Drawn entirely on Canvas — no image assets.
/// </summary>
public class CharacterPortrait : View
{
    // Per-character visual DNA
    private enum FaceRound { A, B, C, D, E }   // face shape
    private enum HairStyle { Long, Short, Twin, Spiky, Bun, Ponytail, Mohawk, Bob }
    private enum EyeStyle { Round, Almond, Sharp, Cat, Closed, Narrow }

    private FaceRound _face;
    private HairStyle _hair;
    private EyeStyle _eyes;
    private Color _hairColor;
    private Color _skinColor;
    private Color _eyeColor;
    private Color _from;
    private Color _to;
    private Color _glow;
    private string _glyph = "";
    private int _rarity;
    private float _phase;
    private Paint? _paint;
    private Random? _rng;

    // 仅依赖尺寸/稀有度的渐变与 Drawable 按尺寸缓存，避免每帧分配（P4）。
    private RadialGradient? _inkGrad;
    private int _inkW, _inkH;
    private GradientDrawable? _auraGrad;
    private Paint? _auraPaint;

    public CharacterPortrait(Context context) : base(context) { SetWillNotDraw(false); }

    /// <summary>Bind a character by id — derives a stable visual identity.</summary>
    public CharacterPortrait Bind(CharacterDataEntry ch)
    {
        _glyph = ElementTheme.For(ch.Element).glyph;
        (_from, _to, _glow, _) = ElementTheme.For(ch.Element);
        _rarity = ch.BaseRarity;
        // Stable hash from character id
        var h = Math.Abs(ch.CharacterId.GetHashCode());
        _rng = new Random(h);
        _face = (FaceRound)(h % 5);
        _hair = (HairStyle)((h / 5) % 8);
        _eyes = (EyeStyle)((h / 40) % 6);
        _hairColor = Color.Argb(255,
            60 + (h % 140), 50 + ((h / 7) % 150), 60 + ((h / 13) % 150));
        _skinColor = Color.Argb(255,
            200 + (h % 50), 170 + ((h / 3) % 60), 150 + ((h / 11) % 70));
        _eyeColor = Color.Argb(255,
            40 + (h % 160), 80 + ((h / 5) % 120), 60 + ((h / 9) % 160));
        return this;
    }

    protected override void OnDraw(Canvas canvas)
    {
        if (_paint == null) _paint = new Paint { AntiAlias = true, FilterBitmap = true };
        _phase += 0.02f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;

        var w = Width; var h = Height;
        if (w == 0 || h == 0) return;
        var cx = w / 2f; var cy = h * 0.46f;
        var r = Math.Min(w, h) * 0.40f;
        var breathe = 1f + 0.015f * MathF.Sin(_phase);

        // 水墨晕影 + 祥云光环（中西融合头像底）
        DrawInkVignette(canvas, w, h);
        DrawCloudHalo(canvas, cx, cy - r * 0.1f, r * 1.5f);

        // Background element aura
        DrawAura(canvas, w, h);

        // Hair (back layer)
        DrawHairBack(canvas, cx, cy, r);

        // Face
        DrawFace(canvas, cx, cy, r);

        // Hair (front layer)
        DrawHairFront(canvas, cx, cy, r);

        // Eyes
        DrawEyes(canvas, cx, cy, r);

        // Mouth
        DrawMouth(canvas, cx, cy, r);

        // Rarity jewel on corner
        if (_rarity >= 3) DrawJewel(canvas, w, h);

        // 印章式描边框（东方头像框）
        var fp = _paint!;
        fp.SetStyle(Paint.Style.Stroke);
        fp.StrokeWidth = UI.Dp(1.5f);
        fp.Color = Color.Argb(150, 255, 215, 90);
        canvas.DrawRoundRect(new RectF(UI.Dp(3), UI.Dp(3), w - UI.Dp(3), h - UI.Dp(3)), UI.Dp(12), UI.Dp(12), fp);
        fp.SetStyle(Paint.Style.Fill);

        if (_animating) Invalidate();
    }

    // ---- 动画生命周期：脱离窗口/不可见时停止重绘 ----
    private bool _animating = true;

    protected override void OnAttachedToWindow()
    {
        base.OnAttachedToWindow();
        _animating = true;
        Invalidate();
    }

    protected override void OnDetachedFromWindow()
    {
        base.OnDetachedFromWindow();
        _animating = false;
        _inkGrad?.Dispose(); _inkGrad = null;
        _auraGrad?.Dispose(); _auraGrad = null;
    }

    protected override void OnWindowVisibilityChanged(ViewStates visibility)
    {
        base.OnWindowVisibilityChanged(visibility);
        _animating = visibility == ViewStates.Visible;
        if (_animating) Invalidate();
    }

    private void DrawAura(Canvas canvas, int w, int h)
    {
        // 渐变与光点 Paint 仅依赖本实例配色（Bind 后恒定），缓存避免每帧重建（P4）。
        if (_auraGrad == null)
        {
            _auraGrad = new GradientDrawable();
            _auraGrad.SetColors(new[] { _from.ToArgb(), Color.Argb(30, _to.R, _to.G, _to.B) });
        }
        _auraGrad.SetBounds(0, 0, w, h);
        _auraGrad.Draw(canvas);
        // floating motes
        if (_rng == null) return;
        _auraPaint ??= new Paint { AntiAlias = true };
        for (int i = 0; i < 8; i++)
        {
            var px = ((_phase * 37 + i * 53) % 1f) * w;
            var py = ((_phase * 23 + i * 71) % 1f) * h;
            _auraPaint.Color = Color.Argb(60, _glow.R, _glow.G, _glow.B);
            canvas.DrawCircle(px, py, 2 + (i % 3), _auraPaint);
        }
    }

    private void DrawFace(Canvas canvas, float cx, float cy, float r)
    {
        var fp = _paint!;
        fp.Color = _skinColor;
        fp.SetStyle(Paint.Style.Fill);
        var fw = r * (_face == FaceRound.A ? 0.95f : _face == FaceRound.E ? 0.78f : 0.86f);
        var fh = r * (_face == FaceRound.C ? 1.1f : 1.0f);
        canvas.DrawOval(cx - fw, cy - fh, cx + fw, cy + fh, fp);
        // cheek blush
        fp.Color = Color.Argb(70, 255, 130, 130);
        canvas.DrawCircle(cx - fw * 0.5f, cy + fh * 0.25f, fw * 0.18f, fp);
        canvas.DrawCircle(cx + fw * 0.5f, cy + fh * 0.25f, fw * 0.18f, fp);
    }

    private void DrawEyes(Canvas canvas, float cx, float cy, float r)
    {
        var ep = _paint!;
        var eyeY = cy - r * 0.05f;
        var eyeDX = r * 0.38f;
        var eyeW = r * (_eyes == EyeStyle.Round ? 0.28f : 0.30f);
        var eyeH = r * (_eyes == EyeStyle.Closed ? 0.06f : _eyes == EyeStyle.Round ? 0.30f : 0.20f);

        foreach (var dir in new[] { -1f, 1f })
        {
            var ex = cx + dir * eyeDX;
            // white
            ep.Color = Color.Argb(245, 250, 250, 250);
            ep.SetStyle(Paint.Style.Fill);
            canvas.DrawOval(ex - eyeW, eyeY - eyeH, ex + eyeW, eyeY + eyeH, ep);
            // iris
            ep.Color = _eyeColor;
            canvas.DrawCircle(ex, eyeY, eyeH * 0.7f, ep);
            // pupil
            ep.Color = Color.Black;
            canvas.DrawCircle(ex, eyeY, eyeH * 0.35f, ep);
            // highlight
            ep.Color = Color.Argb(200, 255, 255, 255);
            canvas.DrawCircle(ex - eyeW * 0.3f, eyeY - eyeH * 0.3f, eyeH * 0.2f, ep);
        }
    }

    private void DrawMouth(Canvas canvas, float cx, float cy, float r)
    {
        var mp = _paint!;
        mp.Color = Color.Argb(180, 180, 90, 90);
        mp.SetStyle(Paint.Style.Stroke);
        mp.StrokeWidth = r * 0.04f;
        var my = cy + r * 0.45f;
        var path = new Android.Graphics.Path();
        path.MoveTo(cx - r * 0.2f, my);
        path.QuadTo(cx, my + r * 0.12f, cx + r * 0.2f, my);
        canvas.DrawPath(path, mp);
    }

    private void DrawHairBack(Canvas canvas, float cx, float cy, float r)
    {
        var hp = _paint!;
        hp.Color = Color.Argb(255, _hairColor.R * 7 / 10, _hairColor.G * 7 / 10, _hairColor.B * 7 / 10);
        hp.SetStyle(Paint.Style.Fill);
        var top = cy - r * 1.05f;
        switch (_hair)
        {
            case HairStyle.Long:
                canvas.DrawRect(cx - r * 1.0f, top, cx + r * 1.0f, cy + r * 1.3f, hp);
                break;
            case HairStyle.Twin:
                canvas.DrawOval(cx - r * 1.0f, cy, cx - r * 0.1f, cy + r * 1.6f, hp);
                canvas.DrawOval(cx + r * 0.1f, cy, cx + r * 1.0f, cy + r * 1.6f, hp);
                break;
            case HairStyle.Spiky:
                var path = new Android.Graphics.Path();
                for (int i = -4; i <= 4; i++)
                {
                    var sx = cx + i * r * 0.22f;
                    if (i == -4) path.MoveTo(sx, cy);
                    else path.LineTo(sx, cy - r * (0.8f + 0.3f * MathF.Abs(i % 2)));
                }
                path.LineTo(cx + r, cy + r * 0.5f);
                path.LineTo(cx - r, cy + r * 0.5f);
                path.Close();
                canvas.DrawPath(path, hp);
                break;
        }
    }

    private void DrawHairFront(Canvas canvas, float cx, float cy, float r)
    {
        var hp = _paint!;
        hp.Color = _hairColor;
        hp.SetStyle(Paint.Style.Fill);
        var top = cy - r * 1.05f;
        // bangs across forehead
        switch (_hair)
        {
            case HairStyle.Short:
                canvas.DrawOval(cx - r * 0.95f, top, cx + r * 0.95f, cy + r * 0.3f, hp);
                break;
            case HairStyle.Bob:
                canvas.DrawOval(cx - r * 1.0f, top, cx + r * 1.0f, cy + r * 0.7f, hp);
                break;
            case HairStyle.Bun:
                canvas.DrawOval(cx - r * 0.9f, top, cx + r * 0.9f, cy + r * 0.1f, hp);
                canvas.DrawCircle(cx, top - r * 0.3f, r * 0.4f, hp);
                break;
            case HairStyle.Ponytail:
                canvas.DrawOval(cx - r * 0.9f, top, cx + r * 0.9f, cy + r * 0.2f, hp);
                var tail = new Android.Graphics.Path();
                tail.MoveTo(cx + r * 0.4f, cy - r * 0.5f);
                tail.QuadTo(cx + r * 1.3f, cy, cx + r * 0.6f, cy + r * 1.2f);
                tail.QuadTo(cx + r * 0.2f, cy + r * 0.3f, cx + r * 0.4f, cy - r * 0.5f);
                canvas.DrawPath(tail, hp);
                break;
            case HairStyle.Mohawk:
                canvas.DrawOval(cx - r * 0.85f, top, cx + r * 0.85f, cy + r * 0.1f, hp);
                canvas.DrawRect(cx - r * 0.25f, top - r * 0.7f, cx + r * 0.25f, cy, hp);
                break;
            case HairStyle.Twin:
                canvas.DrawOval(cx - r * 0.9f, top, cx + r * 0.9f, cy + r * 0.15f, hp);
                break;
            case HairStyle.Long:
                canvas.DrawOval(cx - r * 0.92f, top, cx + r * 0.92f, cy + r * 0.25f, hp);
                break;
            default:
                canvas.DrawOval(cx - r * 0.9f, top, cx + r * 0.9f, cy + r * 0.2f, hp);
                break;
        }
    }

    private void DrawJewel(Canvas canvas, int w, int h)
    {
        var jp = _paint!;
        var jx = w * 0.82f; var jy = h * 0.18f; var jr = Math.Min(w, h) * 0.07f;
        jp.Color = Color.Argb(230, _glow.R, _glow.G, _glow.B);
        jp.SetShadowLayer(8, 0, 0, jp.Color);
        canvas.DrawCircle(jx, jy, jr, jp);
        jp.ClearShadowLayer();
        jp.Color = Color.Argb(200, 255, 255, 255);
        canvas.DrawCircle(jx - jr * 0.3f, jy - jr * 0.3f, jr * 0.3f, jp);
    }

    private void DrawInkVignette(Canvas canvas, int w, int h)
    {
        var gp = _paint!;
        gp.SetStyle(Paint.Style.Fill);
        // 仅依赖尺寸的渐变按尺寸缓存（P4）
        if (_inkGrad == null || _inkW != w || _inkH != h)
        {
            _inkGrad?.Dispose();
            _inkGrad = new RadialGradient(w / 2f, h / 2f, Math.Max(w, h) * 0.6f,
                UI.ColorLong(Color.Argb(0, 0, 0, 0)), UI.ColorLong(Color.Argb(36, 8, 8, 16)), Shader.TileMode.Clamp);
            _inkW = w; _inkH = h;
        }
        gp.SetShader(_inkGrad);
        canvas.DrawRect(0, 0, w, h, gp);
        gp.SetShader(null);
    }

    private void DrawCloudHalo(Canvas canvas, float cx, float cy, float r)
    {
        var hp = _paint!;
        hp.SetStyle(Paint.Style.Fill);
        hp.Color = Color.Argb(40, 255, 215, 90);
        UI.CloudCorner(canvas, hp, cx - r * 0.5f, cy - r * 0.5f, r * 0.5f);
        hp.Color = Color.Argb(28, 124, 77, 255);
        UI.CloudCorner(canvas, hp, cx + r * 0.2f, cy + r * 0.1f, r * 0.42f);
    }
}
