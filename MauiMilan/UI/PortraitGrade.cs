using Android.Graphics;

namespace Milan.Maui;

/// <summary>
/// 立绘显示层统一色调处理：让 AI 立绘原图融入「暗夜神性·诸神黄昏」世界，不去重绘原图。
/// 三件套：
///   1) 冷调去饱和（ColorMatrix）—— 降饱和 ~15% + 轻微偏冷（蓝 + / 红 −）；
///   2) 暗角（vignette）—— 边缘沉入夜色（#0B0612）；
///   3) 边缘光（rim light）—— 左上主光源方向叠熔金/霜蓝低透明高光，呼应"背光"母题。
/// 用法：① ToningMatrix 作为 Paint 滤镜（绘制立绘本体前 SetColorFilter）；
///       ② DrawOverlay 在立绘绘制后于屏幕空间叠加暗角 + 边缘光；
///       ③ Tone 生成已调色副本（调用方负责缓存与回收）。
/// 强度档位：Light / Standard / Heavy(破壁)。
/// </summary>
public static class PortraitGrade
{
    public enum Grade { Light, Standard, Heavy }

    /// <summary>冷调去饱和矩阵：每通道 = lum*(1-desat) + channel*desat，再整体偏冷。</summary>
    public static ColorMatrix ToningMatrix(Grade grade = Grade.Standard)
    {
        float desat = grade switch { Grade.Light => 0.06f, Grade.Standard => 0.15f, Grade.Heavy => 0.26f, _ => 0.15f };
        float cool = grade switch { Grade.Light => 0.01f, Grade.Standard => 0.03f, Grade.Heavy => 0.05f, _ => 0.03f };
        const float lr = 0.299f, lg = 0.587f, lb = 0.114f;
        float k = 1f - desat;
        return new ColorMatrix(new float[]
        {
            lr * k + desat, lg * k,         lb * k,         0, 0,
            lr * k,         lg * k + desat, lb * k,         0, cool * 255f,
            lr * k,         lg * k,         lb * k + desat, 0, cool * 255f,
            0,              0,              0,             1, 0
        });
    }

    /// <summary>在立绘区域叠加暗角 + 边缘光（屏幕空间，已是绘制好的立绘之上）。会重置 paint 的 shader。</summary>
    public static void DrawOverlay(Canvas canvas, RectF rect, Grade grade, Paint paint)
    {
        if (rect.Width() <= 0 || rect.Height() <= 0) return;
        float cx = rect.CenterX(), cy = rect.CenterY();
        float radius = Math.Max(rect.Width(), rect.Height()) * 0.72f;
        if (radius <= 0) return;

        // 暗角：边缘沉入夜色
        float vig = grade switch { Grade.Light => 90f, Grade.Standard => 150f, Grade.Heavy => 205f, _ => 150f };
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new RadialGradient(cx, cy, radius,
            UI.ColorLongs(
                Color.Argb(0, 0, 0, 0),
                Color.Argb(0, 0, 0, 0),
                Color.Argb((int)vig, 0x0B, 0x06, 0x12)),
            new[] { 0f, 0.55f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawRect(rect, paint);
        paint.SetShader(null);

        // 边缘光：左上主光源方向叠一层熔金/霜蓝低透明高光
        float rim = grade switch { Grade.Light => 34f, Grade.Standard => 58f, Grade.Heavy => 96f, _ => 58f };
        var rg = new RadialGradient(rect.Left, rect.Top, rect.Width() * 0.62f,
            UI.ColorLongs(
                Color.Argb((int)rim, 0xE8, 0xB8, 0x4B),
                Color.Argb(0, 0xE8, 0xB8, 0x4B)),
            new[] { 0f, 1f }, Shader.TileMode.Clamp);
        paint.SetShader(rg);
        canvas.DrawRect(rect, paint);
        paint.SetShader(null);
    }

    /// <summary>生成已调色立绘副本（调用方负责缓存与回收）。失败返回 null。</summary>
    public static Bitmap? Tone(Bitmap src, Grade grade = Grade.Standard)
    {
        if (src == null || src.IsRecycled) return null;
        try
        {
            var outp = Bitmap.CreateBitmap(src.Width, src.Height, Bitmap.Config.Argb8888!);
            using var c = new Canvas(outp);
            using var p = new Paint { AntiAlias = true, FilterBitmap = true };
            p.SetColorFilter(new ColorMatrixColorFilter(ToningMatrix(grade)));
            c.DrawBitmap(src, 0, 0, p);
            return outp;
        }
        catch { return null; }
    }
}
