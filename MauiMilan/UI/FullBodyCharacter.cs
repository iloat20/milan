using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Util;
using Android.Views;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// Rich character portrait: draws a full humanoid figure (head, face, hair,
//  body, outfit, weapon) with element-themed colors, glowing aura, and
//  floating particle effects. All procedural — no image assets.
/// </summary>
public class FullBodyCharacter : View
{
    // Visual DNA derived from character id
    private enum FaceShape { Round, Oval, Sharp }
    private enum HairStyle { Long, Short, TwinTails, Spiky, Ponytail, Bun, Mohawk }
    private enum EyeStyle { Round, Almond, Sharp, Cat }
    private enum Outfit { Robe, Armor, Dress, Cloak, Gi }

    private FaceShape _face; private HairStyle _hair; private EyeStyle _eyes; private Outfit _outfit;
    private Color _hairColor, _skinColor, _eyeColor, _from, _to, _glow;
    private string _glyph = "", _name = "";
    private int _rarity;
    private float _phase; private Paint? _paint; private Random? _rng;
    private int _w, _h;

    // Burst state
    private bool _bursting; private float _burstPhase;

    public FullBodyCharacter(Context context, CharacterDataEntry def) : base(context)
    {
        SetWillNotDraw(false);
        SetMinimumWidth(Dp(140));
        SetMinimumHeight(Dp(180));
        _glyph = ElementTheme.For(def.Element).glyph;
        _name = def.DisplayName;
        _rarity = def.BaseRarity;
        (_from, _to, _glow, _) = ElementTheme.For(def.Element);

        var h = Math.Abs(def.CharacterId.GetHashCode());
        _rng = new Random(h);
        _face = (FaceShape)(h % 3);
        _hair = (HairStyle)((h / 3) % 7);
        _eyes = (EyeStyle)((h / 21) % 4);
        _outfit = (Outfit)((h / 84) % 5);
        _hairColor = Color.Argb(255, 40 + (h % 120), 30 + ((h / 7) % 100), 50 + ((h / 13) % 120));
        _skinColor = Color.Argb(255, 230, 190 + (h % 40), 160 + ((h / 3) % 50));
        _eyeColor = Color.Argb(255, 50 + (h % 150), 80 + ((h / 5) % 120), 60 + ((h / 9) % 150));
    }

    public void TriggerBurst() { _bursting = true; _burstPhase = 0; Invalidate(); }

    private int Dp(int v) => (int)(v * Resources.DisplayMetrics.Density);

    protected override void OnSizeChanged(int w, int h, int oldw, int oldh)
    {
        base.OnSizeChanged(w, h, oldw, oldh);
        _w = w; _h = h;
    }

    protected override void OnDraw(Canvas canvas)
    {
        if (_paint == null) _paint = new Paint { AntiAlias = true, FilterBitmap = true };
        _phase += 0.03f;
        if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
        if (_w == 0 || _h == 0) return;

        var cx = _w / 2f;
        var headR = Math.Min(_w, _h) * 0.14f;
        var headCY = _h * 0.28f;

        // 1. Aura glow behind character
        DrawAura(canvas, cx, headCY, headR);

        // 2. Body (torso + legs + arms)
        DrawBody(canvas, cx, headCY, headR);

        // 3. Head + face + hair
        DrawHead(canvas, cx, headCY, headR);

        // 4. Floating particles + element glyph
        DrawEffects(canvas, cx, headCY, headR);

        // 5. Burst flash
        if (_bursting) DrawBurst(canvas);

        Invalidate(); // keep animating
    }

    // ------------------------------------------------------------------ layers

    private void DrawAura(Canvas canvas, float cx, float cy, float headR)
    {
        // Radial glow behind
        var glowR = headR * 3.5f;
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.Color = Color.Argb(60, _glow.R, _glow.G, _glow.B);
        canvas.DrawCircle(cx, cy, glowR, _paint);

        // Ground shadow
        _paint.SetStyle(Paint.Style.Fill);
        _paint.Color = Color.Argb(80, 0, 0, 0);
        canvas.DrawOval(cx - headR * 1.5f, _h * 0.85f, cx + headR * 1.5f, _h * 0.92f, _paint);
    }

    private void DrawBody(Canvas canvas, float cx, float headCY, float headR)
    {
        var sway = MathF.Sin(_phase) * 1.5f;
        var shoulderY = headCY + headR * 1.1f;
        var hipY = shoulderY + headR * 1.6f;
        var bodyBottom = hipY + headR * 1.2f;

        _paint!.SetStyle(Paint.Style.Fill);

        // Legs
        var legColor = Color.Argb(255, 35, 35, 45);
        _paint.Color = legColor;
        canvas.DrawRect(cx - headR * 0.6f + sway, hipY, cx - headR * 0.15f + sway, bodyBottom, _paint);
        canvas.DrawRect(cx + headR * 0.15f + sway, hipY, cx + headR * 0.6f + sway, bodyBottom, _paint);

        // Torso / outfit
        var mainCol = Color.Argb(255, _from.R, _from.G, _from.B);
        var darkCol = Color.Argb(255, _from.R * 6 / 10, _from.G * 6 / 10, _from.B * 6 / 10);
        var path = new Android.Graphics.Path();

        if (_outfit == Outfit.Robe || _outfit == Outfit.Cloak || _outfit == Outfit.Dress)
        {
            // Flowing robe / dress
            path.MoveTo(cx - headR * 0.9f + sway, shoulderY);
            path.LineTo(cx + headR * 0.9f + sway, shoulderY);
            path.LineTo(cx + headR * 1.4f + sway, bodyBottom);
            path.LineTo(cx - headR * 1.4f + sway, bodyBottom);
            path.Close();
            _paint.Color = mainCol;
            canvas.DrawPath(path, _paint);
            // Overlay gradient
            _paint.Color = Color.Argb(70, 255, 255, 255);
            canvas.DrawPath(path, _paint);
        }
        else
        {
            // Armor / gi / suit
            _paint.Color = mainCol;
            canvas.DrawRect(cx - headR * 0.85f + sway, shoulderY, cx + headR * 0.85f + sway, hipY + headR * 0.3f, _paint);
            _paint.Color = darkCol;
            canvas.DrawRect(cx - headR * 0.85f + sway, hipY + headR * 0.3f, cx + headR * 0.85f + sway, bodyBottom, _paint);
            // Belt
            _paint.Color = _glow;
            canvas.DrawRect(cx - headR * 0.85f + sway, hipY + headR * 0.2f, cx + headR * 0.85f + sway, hipY + headR * 0.35f, _paint);
        }

        // Arms
        _paint.Color = _skinColor;
        canvas.DrawRect(cx - headR * 1.2f + sway, shoulderY + headR * 0.1f, cx - headR * 0.85f + sway, hipY * 0.8f, _paint);
        canvas.DrawRect(cx + headR * 0.85f + sway, shoulderY + headR * 0.1f, cx + headR * 1.2f + sway, hipY * 0.8f, _paint);

        // Cloak flutter
        if (_outfit == Outfit.Cloak)
        {
            _paint.Color = Color.Argb(160, _to.R, _to.G, _to.B);
            var cp = new Android.Graphics.Path();
            var flutter = MathF.Sin(_phase * 2f) * headR * 0.4f;
            cp.MoveTo(cx - headR * 0.8f, shoulderY);
            cp.QuadTo(cx - headR * 2f + flutter, (shoulderY + bodyBottom) / 2, cx - headR * 1f + flutter, bodyBottom);
            cp.LineTo(cx - headR * 0.8f, bodyBottom);
            cp.Close();
            canvas.DrawPath(cp, _paint);
        }
    }

    private void DrawHead(Canvas canvas, float cx, float headCY, float headR)
    {
        // Hair back
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.Color = Color.Argb(255, _hairColor.R * 7 / 10, _hairColor.G * 7 / 10, _hairColor.B * 7 / 10);
        if (_hair == HairStyle.Long || _hair == HairStyle.Ponytail)
            canvas.DrawRect(cx - headR * 1.1f, headCY - headR * 0.4f, cx + headR * 1.1f, headCY + headR * 1.8f, _paint);
        if (_hair == HairStyle.TwinTails)
        {
            canvas.DrawOval(cx - headR * 1.2f, headCY, cx - headR * 0.2f, headCY + headR * 2f, _paint);
            canvas.DrawOval(cx + headR * 0.2f, headCY, cx + headR * 1.2f, headCY + headR * 2f, _paint);
        }

        // Face
        _paint.Color = _skinColor;
        var fw = headR * (_face == FaceShape.Round ? 0.95f : _face == FaceShape.Sharp ? 0.75f : 0.85f);
        canvas.DrawOval(cx - fw, headCY - headR, cx + fw, headCY + headR, _paint);
        // Cheek blush
        _paint.Color = Color.Argb(60, 255, 120, 120);
        canvas.DrawCircle(cx - fw * 0.55f, headCY + headR * 0.3f, fw * 0.18f, _paint);
        canvas.DrawCircle(cx + fw * 0.55f, headCY + headR * 0.3f, fw * 0.18f, _paint);

        // Eyes (with blink)
        var blink = (_phase * 0.7f) % MathF.PI > MathF.PI - 0.12f ? 0.08f : 1f;
        var eyeY = headCY - headR * 0.05f;
        var eyeDX = headR * 0.38f;
        var eyeW = headR * (_eyes == EyeStyle.Round ? 0.26f : 0.28f);
        var eyeH = headR * (_eyes == EyeStyle.Round ? 0.26f : 0.18f) * blink;
        foreach (var dir in new[] { -1f, 1f })
        {
            var ex = cx + dir * eyeDX;
            _paint.Color = Color.Argb(245, 250, 250, 250);
            canvas.DrawOval(ex - eyeW, eyeY - Math.Max(eyeH, 0.5f), ex + eyeW, eyeY + Math.Max(eyeH, 0.5f), _paint);
            if (blink > 0.5f)
            {
                _paint.Color = _eyeColor;
                canvas.DrawCircle(ex, eyeY, eyeH * 0.7f, _paint);
                _paint.Color = Color.Black;
                canvas.DrawCircle(ex, eyeY, eyeH * 0.35f, _paint);
                _paint.Color = Color.Argb(200, 255, 255, 255);
                canvas.DrawCircle(ex - eyeW * 0.3f, eyeY - eyeH * 0.3f, eyeH * 0.2f, _paint);
            }
        }

        // Mouth
        _paint.Color = Color.Argb(180, 180, 80, 80);
        _paint.SetStyle(Paint.Style.Stroke);
        _paint.StrokeWidth = headR * 0.05f;
        var my = headCY + headR * 0.5f;
        var mp = new Android.Graphics.Path();
        mp.MoveTo(cx - headR * 0.18f, my);
        mp.QuadTo(cx, my + headR * 0.1f, cx + headR * 0.18f, my);
        canvas.DrawPath(mp, _paint);
        _paint.SetStyle(Paint.Style.Fill);

        // Hair front (bangs)
        _paint.Color = _hairColor;
        switch (_hair)
        {
            case HairStyle.Short:
                canvas.DrawOval(cx - headR * 0.95f, headCY - headR * 0.95f, cx + headR * 0.95f, headCY + headR * 0.15f, _paint);
                break;
            case HairStyle.Long:
            case HairStyle.Ponytail:
                canvas.DrawOval(cx - headR * 0.9f, headCY - headR * 0.9f, cx + headR * 0.9f, headCY + headR * 0.1f, _paint);
                break;
            case HairStyle.TwinTails:
                canvas.DrawOval(cx - headR * 0.85f, headCY - headR * 0.85f, cx + headR * 0.85f, headCY + headR * 0.1f, _paint);
                break;
            case HairStyle.Bun:
                canvas.DrawOval(cx - headR * 0.85f, headCY - headR * 0.85f, cx + headR * 0.85f, headCY + headR * 0.1f, _paint);
                canvas.DrawCircle(cx, headCY - headR * 1.05f, headR * 0.35f, _paint);
                break;
            case HairStyle.Mohawk:
                canvas.DrawOval(cx - headR * 0.8f, headCY - headR * 0.8f, cx + headR * 0.8f, headCY, _paint);
                canvas.DrawRect(cx - headR * 0.2f, headCY - headR * 1.4f, cx + headR * 0.2f, headCY, _paint);
                break;
            case HairStyle.Spiky:
                canvas.DrawOval(cx - headR * 0.8f, headCY - headR * 0.8f, cx + headR * 0.8f, headCY, _paint);
                var sp = new Android.Graphics.Path();
                for (int i = -3; i <= 3; i++)
                {
                    var sx = cx + i * headR * 0.25f;
                    if (i == -3) sp.MoveTo(sx, headCY);
                    else sp.LineTo(sx, headCY - headR * (0.6f + 0.25f * MathF.Abs(i % 2)));
                }
                sp.LineTo(cx + headR, headCY + headR * 0.3f);
                sp.LineTo(cx - headR, headCY + headR * 0.3f);
                sp.Close();
                canvas.DrawPath(sp, _paint);
                break;
        }
    }

    private void DrawEffects(Canvas canvas, float cx, float headCY, float headR)
    {
        // Element glyph (subtle, behind particles)
        _paint!.SetStyle(Paint.Style.Fill);
        _paint.TextSize = headR * 0.9f;
        _paint.Color = Color.Argb(40, 255, 255, 255);
        _paint.SetTypeface(Typeface.DefaultBold);
        var gw = _paint.MeasureText(_glyph);
        canvas.DrawText(_glyph, cx - gw / 2f, headCY + headR * 2.5f, _paint);

        // Floating particles
        if (_rng == null) return;
        for (int i = 0; i < 10; i++)
        {
            var angle = _phase * 0.5f + i * 0.6f;
            var dist = headR * (1.8f + 0.5f * MathF.Sin(_phase + i));
            var px = cx + MathF.Cos(angle) * dist;
            var py = headCY + MathF.Sin(angle) * dist * 0.6f - headR * 0.5f;
            var a = (int)(80 + 60 * MathF.Sin(_phase * 2f + i));
            _paint.Color = Color.Argb(a, _glow.R, _glow.G, _glow.B);
            _paint.TextSize = 8 + (i % 3) * 3;
            canvas.DrawText("✦", px, py, _paint);
        }

        // Rarity jewel (top-right corner)
        if (_rarity >= 3)
        {
            var jx = _w - Dp(14);
            var jy = Dp(14);
            var jr = Dp(6);
            var jcol = AppTheme.RarityColor(_rarity);
            _paint.Color = jcol;
            _paint.SetShadowLayer(6, 0, 0, jcol);
            canvas.DrawCircle(jx, jy, jr, _paint);
            _paint.ClearShadowLayer();
            _paint.Color = Color.Argb(200, 255, 255, 255);
            canvas.DrawCircle(jx - jr * 0.3f, jy - jr * 0.3f, jr * 0.3f, _paint);
        }
    }

    private void DrawBurst(Canvas canvas)
    {
        _burstPhase += 0.06f;
        if (_burstPhase < 1f)
        {
            var a = (int)(160 * (1 - _burstPhase));
            _paint!.SetStyle(Paint.Style.Fill);
            _paint.Color = Color.Argb(a, 255, 255, 255);
            canvas.DrawRect(0, 0, _w, _h, _paint);
            // Radial burst lines
            _paint.SetStyle(Paint.Style.Stroke);
            _paint.StrokeWidth = 2;
            _paint.Color = Color.Argb(a, _glow.R, _glow.G, _glow.B);
            for (int i = 0; i < 12; i++)
            {
                var angle = i * MathF.PI / 6;
                var len = _w * _burstPhase * 0.8f;
                canvas.DrawLine(_w / 2f, _h / 2f, _w / 2f + MathF.Cos(angle) * len, _h / 2f + MathF.Sin(angle) * len, _paint);
            }
        }
        else _bursting = false;
    }
}
