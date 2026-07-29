using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Views;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// Full-body procedural character portrait with idle + burst animations.
/// Draws: aura → body (legs/torso/arms) → head (hair/face/eyes) → weapon.
/// Animates: breathing sway, hair/clothing drift, eye blink, floating motes.
/// Burst mode: full-screen flash + particle explosion + screen shake.
/// </summary>
public class FullBodyCharacter : View
{
    private enum FaceRound { A, B, C }
    private enum HairStyle { Long, Twin, Spiky, Bun, Ponytail, Bob, Short, Mohawk }
    private enum EyeStyle { Round, Almond, Sharp, Cat, Narrow }
    private enum Outfit { Robe, Armor, Dress, Cloak, Gi, Suit }

    private FaceRound _face; private HairStyle _hair; private EyeStyle _eyes; private Outfit _outfit;
    private Color _hairColor, _skinColor, _eyeColor, _from, _to, _glow;
    private string _glyph = ""; private int _rarity;
    private float _phase; private Paint? _paint; private Random? _rng;
    private int _w, _h;

    // Burst state
    private bool _bursting; private float _burstPhase; private float _shakeX, _shakeY;

    public FullBodyCharacter(Context context) : base(context) { SetWillNotDraw(false); }

    public FullBodyCharacter Bind(CharacterDataEntry ch)
    {
        _glyph = ElementTheme.For(ch.Element).glyph;
        (_from, _to, _glow, _) = ElementTheme.For(ch.Element);
        _rarity = ch.BaseRarity;
        var h = Math.Abs(ch.CharacterId.GetHashCode());
        _rng = new Random(h);
        _face = (FaceRound)(h % 3);
        _hair = (HairStyle)((h / 3) % 8);
        _eyes = (EyeStyle)((h / 24) % 5);
        _outfit = (Outfit)((h / 120) % 6);
        _hairColor = Color.Argb(255, 50 + (h % 150), 40 + ((h / 7) % 160), 50 + ((h / 13) % 160));
        _skinColor = Color.Argb(255, 210 + (h % 40), 175 + ((h / 3) % 50), 160 + ((h / 11) % 50));
        _eyeColor = Color.Argb(255, 40 + (h % 160), 80 + ((h / 5) % 120), 60 + ((h / 9) % 160));
        return this;
    }

    /// <summary>Trigger the burst/ult animation (flash + particles + shake).</summary>
    public void TriggerBurst()
    {
        _bursting = true;
        _burstPhase = 0;
        Invalidate();
    }

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _w = w; _h = h;
    }

    protected override void OnDraw(Canvas canvas)
    {
        if (_paint == null) _paint = new Paint { AntiAlias = true, FilterBitmap = true };
        _phase += 0.025f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        if (_w == 0 || _h == 0) { Invalidate(); return; }

        canvas.Save();
        // Screen shake during burst
        if (_bursting)
        {
            _shakeX = (((float)_rng!.NextDouble()) - 0.5f) * 12 * (1 - _burstPhase);
            _shakeY = (((float)_rng.NextDouble()) - 0.5f) * 12 * (1 - _burstPhase);
            canvas.Translate(_shakeX, _shakeY);
        }

        var cx = _w / 2f;
        var bodyH = _h * 0.7f;
        var headR = _h * 0.16f;
        var headCY = _h * 0.28f;

        // 1. Background aura
        DrawAura(canvas);
        // 2. Floating motes
        DrawMotes(canvas);
        // 3. Legs + torso + arms (body)
        DrawBody(canvas, cx, headCY, headR, bodyH);
        // 4. Head
        DrawHead(canvas, cx, headCY, headR);
        // 5. Weapon
        DrawWeapon(canvas, cx, headCY, headR);
        // 6. Burst flash
        if (_bursting) DrawBurst(canvas);

        canvas.Restore();
        Invalidate();
    }

    // ------------------------------------------------------------------ layers

    private void DrawAura(Canvas canvas)
    {
        var grad = new GradientDrawable();
        grad.SetColors(new int[] { Color.Argb(40, _from.R, _from.G, _from.B), Color.Argb(0, _to.R, _to.G, _to.B) });
        grad.SetBounds(0, 0, _w, _h);
        grad.Draw(canvas);
        // ground glow
        var gp = _paint!;
        gp.Color = Color.Argb(80, _glow.R, _glow.G, _glow.B);
        gp.SetShadowLayer(30, 0, _h * 0.85f, gp.Color);
        canvas.DrawCircle(_w / 2f, _h * 0.88f, _w * 0.3f, gp);
        gp.ClearShadowLayer();
    }

    private void DrawMotes(Canvas canvas)
    {
        if (_rng == null) return;
        var mp = _paint!;
        for (int i = 0; i < 12; i++)
        {
            var px = ((_phase * 20 + i * 47) % 1f) * _w;
            var py = _h - ((_phase * 35 + i * 31) % 1f) * _h;
            var a = (int)(60 + 40 * MathF.Sin(_phase + i));
            mp.Color = Color.Argb(a, _glow.R, _glow.G, _glow.B);
            canvas.DrawCircle(px, py, 2 + (i % 3), mp);
        }
    }

    private void DrawBody(Canvas canvas, float cx, float headCY, float headR, float bodyH)
    {
        var bp = _paint!;
        var sway = MathF.Sin(_phase) * 2f; // breathing sway
        var hipY = headCY + headR * 1.4f;
        var shoulderY = headCY + headR * 0.6f;
        var bodyBottom = hipY + bodyH * 0.45f;

        // Legs
        bp.Color = Color.Argb(245, 50, 50, 60);
        bp.SetStyle(Paint.Style.Fill);
        canvas.DrawRect(cx - headR * 0.7f + sway, hipY, cx - headR * 0.1f + sway, bodyBottom, bp);
        canvas.DrawRect(cx + headR * 0.1f + sway, hipY, cx + headR * 0.7f + sway, bodyBottom, bp);

        // Torso / outfit
        var robe = _outfit == Outfit.Robe || _outfit == Outfit.Dress || _outfit == Outfit.Cloak;
        var mainColor = Color.Argb(255, _from.R, _from.G, _from.B);
        var darkColor = Color.Argb(255, _from.R * 6 / 10, _from.G * 6 / 10, _from.B * 6 / 10);
        bp.Color = mainColor;
        var left = cx - headR * 1.1f + sway;
        var right = cx + headR * 1.1f + sway;
        if (robe)
        {
            // flowing robe / dress
            var path = new Android.Graphics.Path();
            path.MoveTo(left, shoulderY);
            path.LineTo(right, shoulderY);
            path.LineTo(right + headR * 0.5f, bodyBottom);
            path.LineTo(left - headR * 0.5f, bodyBottom);
            path.Close();
            canvas.DrawPath(path, bp);
            // gradient overlay
            bp.Color = Color.Argb(80, 255, 255, 255);
            canvas.DrawPath(path, bp);
        }
        else
        {
            // armor / gi / suit
            canvas.DrawRect(left, shoulderY, right, hipY + bodyH * 0.15f, bp);
            bp.Color = darkColor;
            canvas.DrawRect(left, hipY + bodyH * 0.15f, right, bodyBottom, bp);
            // belt
            bp.Color = Color.Argb(255, _glow.R, _glow.G, _glow.B);
            canvas.DrawRect(left, hipY + bodyH * 0.1f, right, hipY + bodyH * 0.16f, bp);
        }

        // Arms
        bp.Color = _skinColor;
        var armSway = MathF.Sin(_phase * 1.3f) * 3f;
        canvas.DrawRect(left - headR * 0.5f, shoulderY + armSway, left, bodyBottom * 0.7f, bp);
        canvas.DrawRect(right, shoulderY - armSway, right + headR * 0.5f, bodyBottom * 0.7f, bp);

        // Cloak flutter (if cloak)
        if (_outfit == Outfit.Cloak)
        {
            bp.Color = Color.Argb(180, _to.R, _to.G, _to.B);
            var cp = new Android.Graphics.Path();
            var flutter = MathF.Sin(_phase * 2f) * 8f;
            cp.MoveTo(left, shoulderY);
            cp.QuadTo(left - headR * 1.5f + flutter, bodyBottom * 0.6f, left - headR * 0.3f, bodyBottom);
            cp.LineTo(left, bodyBottom);
            cp.Close();
            canvas.DrawPath(cp, bp);
        }
    }

    private void DrawHead(Canvas canvas, float cx, float headCY, float headR)
    {
        var hp = _paint!;
        var sway = MathF.Sin(_phase) * 1.5f;

        // Hair back
        hp.Color = Color.Argb(255, _hairColor.R * 7 / 10, _hairColor.G * 7 / 10, _hairColor.B * 7 / 10);
        hp.SetStyle(Paint.Style.Fill);
        if (_hair == HairStyle.Long || _hair == HairStyle.Ponytail)
            canvas.DrawRect(cx - headR * 1.1f, headCY - headR * 0.5f, cx + headR * 1.1f, headCY + headR * 1.8f, hp);
        if (_hair == HairStyle.Twin)
        {
            canvas.DrawOval(cx - headR * 1.1f, headCY, cx - headR * 0.2f, headCY + headR * 2f, hp);
            canvas.DrawOval(cx + headR * 0.2f, headCY, cx + headR * 1.1f, headCY + headR * 2f, hp);
        }

        // Face
        var fw = headR * (_face == FaceRound.A ? 0.95f : _face == FaceRound.C ? 0.8f : 0.88f);
        hp.Color = _skinColor;
        canvas.DrawOval(cx - fw, headCY - headR, cx + fw, headCY + headR, hp);
        // blush
        hp.Color = Color.Argb(70, 255, 130, 130);
        canvas.DrawCircle(cx - fw * 0.55f, headCY + headR * 0.3f, fw * 0.16f, hp);
        canvas.DrawCircle(cx + fw * 0.55f, headCY + headR * 0.3f, fw * 0.16f, hp);

        // Eyes (with blink)
        var blink = (_phase * 0.7f) % MathF.PI > MathF.PI - 0.12f ? 0.1f : 1f;
        DrawEyes(canvas, cx, headCY, headR, blink);

        // Mouth
        hp.Color = Color.Argb(180, 180, 90, 90);
        hp.SetStyle(Paint.Style.Stroke);
        hp.StrokeWidth = headR * 0.05f;
        var my = headCY + headR * 0.5f;
        var p = new Android.Graphics.Path();
        p.MoveTo(cx - headR * 0.2f, my);
        p.QuadTo(cx, my + headR * 0.12f, cx + headR * 0.2f, my);
        canvas.DrawPath(p, hp);

        // Hair front (bangs)
        hp.Color = _hairColor;
        hp.SetStyle(Paint.Style.Fill);
        var hairSway = MathF.Sin(_phase * 1.5f) * 2f;
        switch (_hair)
        {
            case HairStyle.Short:
            case HairStyle.Bob:
                canvas.DrawOval(cx - headR * 1.0f, headCY - headR * 1.0f + hairSway, cx + headR * 1.0f, headCY + headR * 0.2f, hp);
                break;
            case HairStyle.Long:
                canvas.DrawOval(cx - headR * 0.95f, headCY - headR * 0.95f + hairSway, cx + headR * 0.95f, headCY + headR * 0.15f, hp);
                break;
            case HairStyle.Twin:
                canvas.DrawOval(cx - headR * 0.9f, headCY - headR * 0.9f, cx + headR * 0.9f, headCY + headR * 0.1f, hp);
                break;
            case HairStyle.Bun:
                canvas.DrawOval(cx - headR * 0.9f, headCY - headR * 0.9f, cx + headR * 0.9f, headCY + headR * 0.1f, hp);
                canvas.DrawCircle(cx, headCY - headR * 1.1f, headR * 0.35f, hp);
                break;
            case HairStyle.Ponytail:
                canvas.DrawOval(cx - headR * 0.9f, headCY - headR * 0.9f, cx + headR * 0.9f, headCY + headR * 0.1f, hp);
                var tp = new Android.Graphics.Path();
                tp.MoveTo(cx + headR * 0.3f, headCY - headR * 0.6f);
                tp.QuadTo(cx + headR * 1.4f + hairSway, headCY, cx + headR * 0.6f, headCY + headR * 1.5f);
                tp.QuadTo(cx + headR * 0.2f, headCY + headR * 0.4f, cx + headR * 0.3f, headCY - headR * 0.6f);
                canvas.DrawPath(tp, hp);
                break;
            case HairStyle.Mohawk:
                canvas.DrawOval(cx - headR * 0.85f, headCY - headR * 0.85f, cx + headR * 0.85f, headCY + headR * 0.05f, hp);
                canvas.DrawRect(cx - headR * 0.25f, headCY - headR * 1.5f, cx + headR * 0.25f, headCY, hp);
                break;
            case HairStyle.Spiky:
                canvas.DrawOval(cx - headR * 0.85f, headCY - headR * 0.85f, cx + headR * 0.85f, headCY + headR * 0.05f, hp);
                var sp = new Android.Graphics.Path();
                for (int i = -3; i <= 3; i++)
                {
                    var sx = cx + i * headR * 0.28f;
                    if (i == -3) sp.MoveTo(sx, headCY);
                    else sp.LineTo(sx, headCY - headR * (0.7f + 0.3f * MathF.Abs(i % 2)));
                }
                sp.LineTo(cx + headR, headCY + headR * 0.3f);
                sp.LineTo(cx - headR, headCY + headR * 0.3f);
                sp.Close();
                canvas.DrawPath(sp, hp);
                break;
        }
    }

    private void DrawEyes(Canvas canvas, float cx, float cy, float headR, float blink)
    {
        var ep = _paint!;
        var eyeY = cy - headR * 0.05f;
        var eyeDX = headR * 0.38f;
        var eyeW = headR * (_eyes == EyeStyle.Round ? 0.26f : 0.28f);
        var eyeH = headR * (_eyes == EyeStyle.Round ? 0.28f : 0.18f) * blink;
        foreach (var dir in new[] { -1f, 1f })
        {
            var ex = cx + dir * eyeDX;
            ep.Color = Color.Argb(245, 250, 250, 250);
            ep.SetStyle(Paint.Style.Fill);
            canvas.DrawOval(ex - eyeW, eyeY - Math.Max(eyeH, 0.5f), ex + eyeW, eyeY + Math.Max(eyeH, 0.5f), ep);
            if (blink > 0.5f)
            {
                ep.Color = _eyeColor;
                canvas.DrawCircle(ex, eyeY, eyeH * 0.7f, ep);
                ep.Color = Color.Black;
                canvas.DrawCircle(ex, eyeY, eyeH * 0.35f, ep);
                ep.Color = Color.Argb(200, 255, 255, 255);
                canvas.DrawCircle(ex - eyeW * 0.3f, eyeY - eyeH * 0.3f, eyeH * 0.2f, ep);
            }
        }
    }

    private void DrawWeapon(Canvas canvas, float cx, float headCY, float headR)
    {
        var wp = _paint!;
        var sway = MathF.Sin(_phase * 0.8f) * 2f;
        wp.SetStyle(Paint.Style.Fill);
        // Weapon type by world
        var world = _glyph; // placeholder
        // Sword on right hip
        wp.Color = Color.Argb(230, 200, 200, 210);
        var sx = cx + headR * 1.2f + sway;
        canvas.DrawRect(sx - 3, headCY + headR * 0.5f, sx + 3, headCY + headR * 2.2f, wp);
        // hilt
        wp.Color = Color.Argb(240, 180, 140, 60);
        canvas.DrawRect(sx - 6, headCY + headR * 0.4f, sx + 6, headCY + headR * 0.6f, wp);
        // glow on blade for high rarity
        if (_rarity >= 3)
        {
            wp.Color = Color.Argb(120, _glow.R, _glow.G, _glow.B);
            wp.SetShadowLayer(10, 0, 0, wp.Color);
            canvas.DrawRect(sx - 2, headCY + headR * 0.6f, sx + 2, headCY + headR * 2.2f, wp);
            wp.ClearShadowLayer();
        }
    }

    private void DrawBurst(Canvas canvas)
    {
        _burstPhase += 0.05f;
        var bp = _paint!;
        // Full-screen flash
        var flashA = (int)(180 * (1 - _burstPhase));
        if (flashA > 0)
        {
            bp.Color = Color.Argb(Math.Min(flashA, 255), 255, 255, 255);
            canvas.DrawRect(0, 0, _w, _h, bp);
        }
        // Particle burst from center
        if (_rng != null)
        {
            for (int i = 0; i < 30; i++)
            {
                var a = ((float)_rng.NextDouble()) * MathF.PI * 2;
                var dist = _burstPhase * _w * 0.6f;
                var px = _w / 2f + MathF.Cos(a) * dist;
                var py = _h / 2f + MathF.Sin(a) * dist;
                var sz = 4f * (1 - _burstPhase);
                bp.Color = Color.Argb((int)(200 * (1 - _burstPhase)), _glow.R, _glow.G, _glow.B);
                canvas.DrawCircle(px, py, sz, bp);
            }
        }
        if (_burstPhase >= 1f) { _bursting = false; _burstPhase = 0; }
    }
}
