using Android.Content;
using Android.Content.Res;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Runtime;
using Android.Util;
using Android.Views;
using Android.Views.Animations;
using Android.Widget;

namespace Milan.Maui;

/// <summary>
/// Shared design system for the Milan gacha app: 暗夜神性·诸神黄昏 (Twilight of Gods)
/// 单一调色板、稀有度体系、密度无关尺寸，以及圆角按钮 / 卡片 / 背景的助手。
/// AppTheme 为唯一颜色来源；UI 提供玻璃面板 / 饰线标题 / 按钮 / 立绘色调 / 背景等组件。
/// </summary>
public static class AppTheme
{
    // ═══════════════════════════════════════════════════════════════
    // 暗夜神性·诸神黄昏 (Twilight of Gods) — 唯一主调色板（单一来源）
    // cosmic dark / 中西融合 / Obsidian&Gold / Cosmic Nebula 四套重叠
    // 定义已收口到此；旧名一律指向此处或移除（见 docs/plans/2026-08-04）。
    // ═══════════════════════════════════════════════════════════════
    /// <summary>全局最底背景（暗紫夜·最深）。</summary>
    public static Color BgDeepest => Color.ParseColor("#0B0612");
    /// <summary>次级背景 / 分区（暗紫夜·中）。</summary>
    public static Color BgMid => Color.ParseColor("#160A26");
    /// <summary>玻璃面底色（#1E0E33 @ α≈0.55）。</summary>
    public static Color Surface => Color.Argb(140, 0x1E, 0x0E, 0x33);
    /// <summary>熔金主色（线 / 点 / 字 / 当期强调）。</summary>
    public static Color Gold => Color.ParseColor("#E8B84B");
    /// <summary>熔金高光（渐变起笔 / 高光点）。</summary>
    public static Color GoldHi => Color.ParseColor("#FFC857");
    /// <summary>熔金收尾 / 按钮底边。</summary>
    public static Color GoldDeep => Color.ParseColor("#C9962E");
    /// <summary>霜蓝（次级操作 / 信息 / 导航图标）。</summary>
    public static Color Frost => Color.ParseColor("#7FC4FF");
    /// <summary>霜蓝深。</summary>
    public static Color FrostDeep => Color.ParseColor("#4A90D9");
    /// <summary>暮紫（点缀 / 分隔 / 天赋节点 / 裂隙）。</summary>
    public static Color Violet => Color.ParseColor("#9A6BFF");
    /// <summary>主文字。</summary>
    public static Color Text1 => Color.ParseColor("#F3ECFF");
    /// <summary>次文字。</summary>
    public static Color Text2 => Color.ParseColor("#B7A6CF");
    /// <summary>弱化 / 占位。</summary>
    public static Color Text3 => Color.ParseColor("#6E5C8A");
    /// <summary>语义色：成功。</summary>
    public static Color Success => Color.ParseColor("#35D07F");
    /// <summary>语义色：警告。</summary>
    public static Color Warning => Color.ParseColor("#FFB020");
    /// <summary>语义色：危险 / 强调红。</summary>
    public static Color Danger => Color.ParseColor("#FF4D5E");
    /// <summary>发丝描边（白 α08）：替代旧金线作为默认面板边。</summary>
    public static Color Stroke => Color.Argb(20, 255, 255, 255);
    /// <summary>金底上的深色文字。</summary>
    public static Color GoldTextOn => Color.ParseColor("#3A2800");
    /// <summary>印章点缀红（中西融合母题保留项）。</summary>
    public static Color SealRed => Color.ParseColor("#C8252A");

    // ── Twilight* 兼容别名（旧名 → 新语义主名）──
    public static Color TwilightBgDeepest => BgDeepest;
    public static Color TwilightBgMid => BgMid;
    public static Color TwilightGold => Gold;
    public static Color TwilightGoldBright => GoldHi;
    public static Color TwilightFrost => Frost;
    public static Color TwilightFrostDeep => FrostDeep;
    public static Color TwilightViolet => Violet;
    public static Color TwilightTextPrimary => Text1;
    public static Color TwilightTextSecondary => Text2;

    // Rarity palette — 诸神黄昏·东方 调性（UR 熔金 / SSR 暮紫 / SR 霜蓝 / R 苍白）
    public static Color RarityColor(int rarity) => rarity switch
    {
        1 => Color.ParseColor("#E8E2F2"),   // R  - 苍白
        2 => Color.ParseColor("#7FC4FF"),   // SR - 霜蓝
        3 => Color.ParseColor("#C79BFF"),   // SSR - 暮紫
        4 => Color.ParseColor("#FFC857"),   // UR - 熔金
        _ => Text3
    };

    public static string RarityName(int rarity) => rarity switch
    {
        3 => "SSR",
        2 => "SR",
        4 => "UR",
        _ => "R"
    };

    /// <summary>
    /// Get the world theme for a character world.
    /// </summary>
    public static WorldPalette World(string world) => WorldTheme.For(world);

    /// <summary>
    /// Create a world-styled card background.
    /// </summary>
    public static GradientDrawable WorldCard(Color worldPrimary, Color worldSurface, float radiusDp = 16)
    {
        var gd = new GradientDrawable();
        gd.SetColor(worldSurface.ToArgb());
        gd.SetCornerRadius(UI.Dp(radiusDp));
        gd.SetStroke(UI.Dp(2), Color.Argb(120, worldPrimary.R, worldPrimary.G, worldPrimary.B));
        return gd;
    }
}

public static class UI
{
    public static float Density { get; set; } = 1f;

    public static int Dp(this int v) => (int)(v * Density);
    public static int Dp(float v) => (int)(v * Density);

    /// <summary>
    /// 把 Color 转成 sRGB ColorLong（long），用于 Shader 的 long / long[] 颜色重载。
    /// ⚠️ 绝不能改动编码方式，这是本项目历史上最难查的闪退根因：
    /// .NET for Android 的 RadialGradient/LinearGradient/SweepGradient 没有 Color[] 重载，
    /// 只有 int[]/long[]；而 Android.Graphics.Color 存在到 int 的隐式转换，int 又能隐式转 long，
    /// 于是 `new LinearGradient(..., someColor, ...)` 会被编译器悄悄绑到 long 重载，
    /// 把裸 ARGB 直接塞进 long 的低 32 位。Android 的 Color.colorSpace(long) 取 `color &amp; 0x3F`
    /// 作为 ColorSpace id，也就是把蓝色分量的低 6 位当成色彩空间编号 →
    /// ColorSpace.get(id) 抛 IllegalArgumentException: Invalid ID: 23/33/40（= 蓝色分量低 6 位）。
    /// OnDraw 抛异常无法被 Activity 的 try/catch 兜住 → 渲染线程静默杀进程（无 UI、无弹窗）。
    /// 正确编码与 AOSP Color.pack(int) 完全一致：ARGB 放高 32 位，低位全 0 → colorSpace id = 0 = sRGB。
    /// </summary>
    public static long ColorLong(Color c) => (c.ToArgb() & 0xFFFFFFFFL) << 32;

    /// <summary>批量转换，便于直接喂给 RadialGradient/LinearGradient/SweepGradient 的 long[] 重载。</summary>
    public static long[] ColorLongs(params Color[] colors) => System.Array.ConvertAll(colors, ColorLong);

    public static void Init(Context context)
    {
        Density = context.Resources.DisplayMetrics.Density;
    }

    /// <summary>Solid rounded surface (card / panel).</summary>
    public static GradientDrawable RoundRect(int fill, float radiusDp, int? strokePx = null, Color? strokeColor = null)
    {
        var gd = new GradientDrawable();
        gd.SetColor(fill);
        gd.SetCornerRadius(Dp(radiusDp));
        if (strokePx.HasValue) gd.SetStroke(Dp(strokePx.Value), strokeColor ?? AppTheme.Stroke);
        return gd;
    }

    /// <summary>
    /// 玻璃拟态面板（暗夜神性·诸神黄昏）：半透明深紫黑底 + 顶部内高光 + 发丝边。
    /// 默认描边改为发丝白线（金不再默认铺满）；gold=true 仅用于选中 / 当期 UP 面板，
    /// 用熔金发丝线（α45%）点睛。
    /// </summary>
    public static Drawable GlassPanel(float radiusDp = 14, bool gold = false, bool nested = false)
    {
        // 底色：Surface 半透明（默认 α≈0.55）；嵌套态略深以拉开层次。
        var fill = nested ? Color.Argb(150, 0x25, 0x12, 0x42) : AppTheme.Surface;
        var body = new GradientDrawable();
        body.SetColor(fill.ToArgb());
        body.SetCornerRadius(Dp(radiusDp));
        body.SetStroke(Dp(gold ? 1.5f : 1f), gold ? AppTheme.Gold : AppTheme.Stroke);

        // 顶部内高光：白 α10% → 透明，覆盖上半部
        var highlight = new GradientDrawable(
            GradientDrawable.Orientation.TopBottom,
            new[] { Color.Argb(26, 255, 255, 255).ToArgb(), Color.Argb(0, 255, 255, 255).ToArgb() });
        highlight.SetCornerRadius(Dp(radiusDp));
        highlight.SetGradientCenter(0.5f, 0.2f);

        var layers = new LayerDrawable(new Drawable[] { body, highlight });
        return layers;
    }

    /// <summary>
    /// 双描边浮雕卡面：外圈深色 + 内圈亮色，带垂直渐变填充，用于立体感组件。
    /// </summary>
    public static Drawable EmbossPanel(Color topFill, Color bottomFill, Color innerStroke, float radiusDp = 14)
    {
        var outer = new GradientDrawable();
        outer.SetColor(Color.Argb(200, 0, 0, 0).ToArgb());
        outer.SetCornerRadius(Dp(radiusDp));

        var inner = new GradientDrawable(
            GradientDrawable.Orientation.TopBottom,
            new[] { topFill.ToArgb(), bottomFill.ToArgb() });
        inner.SetCornerRadius(Dp(radiusDp - 1));
        inner.SetStroke(Dp(1), innerStroke);

        var layers = new LayerDrawable(new Drawable[] { outer, inner });
        layers.SetLayerInset(1, Dp(1), Dp(1), Dp(1), Dp(1));
        return layers;
    }

    /// <summary>标题饰线行：左右 1px 金线（或霜蓝）渐隐 + 中心 ◆ + 标题文字，字距收紧。</summary>
    public static LinearLayout TitleWithOrnament(string title, float sp = 20, bool frost = false)
    {
        var row = HBox();
        row.SetGravity(GravityFlags.CenterVertical);

        View Line(bool leftToRight)
        {
            var v = new View(MauiApp.Context);
            var c = frost ? AppTheme.Frost : AppTheme.Gold;
            var gd = new GradientDrawable(
                leftToRight ? GradientDrawable.Orientation.LeftRight : GradientDrawable.Orientation.RightLeft,
                new[] { Color.Argb(0, c.R, c.G, c.B).ToArgb(), Color.Argb(140, c.R, c.G, c.B).ToArgb() });
            v.Background = gd;
            v.LayoutParameters = new LinearLayout.LayoutParams(0, Dp(1), 1f)
            { Gravity = GravityFlags.CenterVertical };
            return v;
        }

        var t = Text($"◆ {title}", sp, AppTheme.Text1, bold: true);
        t.SetPadding(Dp(12), 0, Dp(12), 0);
        t.LetterSpacing = 0.08f; // 字距收紧，更克制

        row.AddView(Line(true));
        row.AddView(t);
        row.AddView(Line(false));
        return row;
    }

    /// <summary>Vertical gradient background.</summary>
    public static GradientDrawable Gradient(int top, int bottom)
    {
        var gd = new GradientDrawable();
        gd.SetColors(new[] { top, bottom });
        gd.SetCornerRadius(0);
        return gd;
    }

    public static TextView Text(string s, float sp, Color color, bool bold = false)
    {
        var t = new TextView(MauiApp.Context)
        {
            Text = s
        };
        t.SetTextColor(color);
        t.SetTextSize(ComplexUnitType.Sp, sp);
        if (bold) t.SetTypeface(null, TypefaceStyle.Bold);
        return t;
    }

    /// <summary>把数值 TextView 设为等宽字体（DIN / Roboto Mono 风格），
    /// 用于货币 / 计数 / 数值面板，避免数字跳动、强化「节律化」的字阶秩序（设计文档 §5）。</summary>
    public static void Tabular(TextView tv)
    {
        if (tv == null) return;
        tv.SetTypeface(Typeface.Monospace, tv.Typeface?.IsBold == true ? TypefaceStyle.Bold : TypefaceStyle.Normal);
        tv.LetterSpacing = 0.02f;
    }

    /// <summary>Big rounded button with solid fill and white text.</summary>
    public static Button Button(string label, Color fill, Color textColor, float radiusDp = 16)
    {
        var b = new Button(MauiApp.Context)
        {
            Text = label
        };
        b.SetTextColor(textColor);
        b.SetTextSize(ComplexUnitType.Sp, 17);
        b.SetTypeface(null, TypefaceStyle.Bold);
        b.Background = RoundRect(fill, radiusDp);
        b.SetPadding(Dp(24), Dp(14), Dp(24), Dp(14));
        // remove default insets so padding feels even
        b.SetMinimumHeight(0);
        b.SetMinHeight(0);
        return b;
    }

    /// <summary>Circular / rounded avatar box showing a character initial on a rarity-colored ground.</summary>
    public static TextView Avatar(string initial, int rarity, int sizeDp)
    {
        var t = new TextView(MauiApp.Context)
        {
            Text = initial,
            Gravity = GravityFlags.Center
        };
        t.SetTextColor(Color.White);
        t.SetTextSize(ComplexUnitType.Sp, sizeDp * 0.42f);
        t.SetTypeface(null, TypefaceStyle.Bold);
        t.SetTextColor(Color.White);
        t.SetShadowLayer(6, 0, 2, Color.Argb(120, 0, 0, 0));
        t.Background = RoundRect(AppTheme.RarityColor(rarity), sizeDp / 2f);
        var lp = new LinearLayout.LayoutParams(Dp(sizeDp), Dp(sizeDp));
        t.LayoutParameters = lp;
        return t;
    }

    public static LinearLayout VBox()
    {
        var l = new LinearLayout(MauiApp.Context) { Orientation = Android.Widget.Orientation.Vertical };
        return l;
    }

    public static LinearLayout HBox()
    {
        var l = new LinearLayout(MauiApp.Context) { Orientation = Android.Widget.Orientation.Horizontal };
        return l;
    }

    /// <summary>
    /// 垂直留白。此前 9 个 Activity 各自复制了一份等价实现（还有两种 density 取法），
    /// 收口到这里作为唯一实现。<paramref name="heightDp"/> 为 dp，内部统一换算。
    /// </summary>
    public static View Spacer(Context ctx, int heightDp)
        => new View(ctx)
        {
            LayoutParameters = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MatchParent, Dp(heightDp))
        };

    /// <summary>
    /// Twilight 页面底色：暮紫夜对角三段渐变。原先 7 个页面各写一遍字面量，
    /// 一旦调色板改动就会漏改其中几处 —— 收口为唯一来源。
    /// 每次返回新实例：Drawable 有绑定状态，多个 View 共用同一实例会互相干扰。
    /// </summary>
    public static GradientDrawable PageBackground()
        => new GradientDrawable(
            GradientDrawable.Orientation.TlBr,
            new[] { AppTheme.BgDeepest.ToArgb(), AppTheme.BgMid.ToArgb(), AppTheme.BgDeepest.ToArgb() });

    /// <summary>通用触摸反馈：按下缩放、抬起恢复并触发 onTap。基于 Touch 事件，
    /// 避免手动实现 IOnTouchListener（net10 Android 绑定要求较多接口成员）。</summary>
    public static void TapFeedback(View v, Action onTap)
    {
        if (v == null || onTap == null) return;
        // #28: 手指按下后滑出控件再抬起不应该算点击；用 pressed 状态 + 边界判定过滤。
        bool pressed = false;
        float slop = Dp(16);
        v.Touch += (s, e) =>
        {
            var ev = e?.Event;
            if (ev == null) { if (e != null) e.Handled = false; return; }
            bool Inside()
            {
                float x = ev.GetX(), y = ev.GetY();
                return x >= -slop && y >= -slop && x <= v.Width + slop && y <= v.Height + slop;
            }
            switch (ev.Action)
            {
                case MotionEventActions.Down:
                    pressed = true;
                    v.Animate()?.ScaleX(0.95f)?.ScaleY(0.95f)?.SetDuration(120)?.SetInterpolator(Motion.Ease)?.Start();
                    e.Handled = true;
                    break;
                case MotionEventActions.Move:
                    if (pressed && !Inside())
                    {
                        pressed = false;
                        v.Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(Motion.Micro)?.SetInterpolator(Motion.Ease)?.Start();
                    }
                    e.Handled = true;
                    break;
                case MotionEventActions.Up:
                    v.Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(Motion.Micro)?.SetInterpolator(Motion.Ease)?.Start();
                    if (pressed && Inside()) onTap();
                    pressed = false;
                    e.Handled = true;
                    break;
                case MotionEventActions.Cancel:
                    pressed = false;
                    v.Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(Motion.Micro)?.SetInterpolator(Motion.Ease)?.Start();
                    e.Handled = true;
                    break;
            }
        };
    }

    // ── 中西融合母题绘制助手 ─────────────────────────────
    public static void LatticeFrame(Canvas canvas, Paint paint, float w, float h, float inset = 8f, int divisions = 5)
    {
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(0.5f);
        float stepX = (w - inset * 2f) / divisions;
        float stepY = (h - inset * 2f) / divisions;
        for (int i = 1; i < divisions; i++)
        {
            float x = inset + stepX * i;
            float y = inset + stepY * i;
            canvas.DrawLine(x, inset, x, h - inset, paint);
            canvas.DrawLine(inset, y, w - inset, y, paint);
        }
    }

    public static void CloudCorner(Canvas canvas, Paint paint, float cx, float cy, float r)
    {
        paint.SetStyle(Paint.Style.Fill);
        var p = new Android.Graphics.Path();
        p.MoveTo(cx, cy);
        for (int a = 0; a < 360; a += 14)
        {
            double rad = Math.PI * a / 180.0;
            float rr = r * (0.4f + 0.6f * (a / 360f));
            float px = cx + rr * (float)Math.Cos(rad);
            float py = cy + rr * (float)Math.Sin(rad);
            if (a == 0) p.MoveTo(px, py); else p.LineTo(px, py);
        }
        p.Close();
        canvas.DrawPath(p, paint);
    }

    public static void SealStamp(Canvas canvas, Paint paint, float x, float y, float s, string glyph)
    {
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.Argb(225, 200, 37, 42);
        var rr = new RectF(x, y, x + s, y + s);
        canvas.DrawRoundRect(rr, UI.Dp(3), UI.Dp(3), paint);
        paint.Color = Color.White;
        paint.TextSize = s * 0.5f;
        paint.TextAlign = Paint.Align.Center;
        canvas.DrawText(glyph, x + s / 2f, y + s * 0.5f + s * 0.16f, paint);
    }

    public static void MeanderBorder(Canvas canvas, Paint paint, float w, float h, float inset = 6f)
    {
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.2f);
        float pad = UI.Dp(6f) + inset;
        void Unit(float ex, float ey)
        {
            var p = new Android.Graphics.Path();
            p.MoveTo(ex, ey);
            p.LineTo(ex + UI.Dp(7), ey);
            p.LineTo(ex + UI.Dp(7), ey + UI.Dp(7));
            p.LineTo(ex + UI.Dp(3.5f), ey + UI.Dp(7));
            p.LineTo(ex + UI.Dp(3.5f), ey + UI.Dp(3.5f));
            p.LineTo(ex, ey + UI.Dp(3.5f));
            p.Close();
            canvas.DrawPath(p, paint);
        }
        float x = pad;
        while (x < w - pad)
        {
            Unit(x, pad);
            x += UI.Dp(13);
        }
        float xb = pad;
        while (xb < w - pad)
        {
            Unit(xb, h - pad);
            xb += UI.Dp(13);
        }
    }

    public static void RiftGrid(Canvas canvas, Paint paint, float w, float h, float t)
    {
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(0.4f);
        int div = 9;
        float stx = w / div, sty = h / div;
        for (int i = 0; i <= div; i++)
        {
            float xx = stx * i;
            float yy = sty * i;
            canvas.DrawLine(xx, 0, xx, h, paint);
            canvas.DrawLine(0, yy, w, yy, paint);
        }
        float gy = h * (0.5f + 0.12f * MathF.Sin(t));
        paint.Color = Color.Argb(40, 0, 229, 255);
        canvas.DrawRect(0, gy - UI.Dp(2), w, gy + UI.Dp(2), paint);
    }

    }

/// <summary>
/// 统一动效语言（暗夜神性·诸神黄昏 设计文档 §6）：单一缓动 + 规范时长 + 入场编排。
/// 顶层静态类，命名空间 Milan.Maui —— 全仓（含 namespace Milan.Maui.UI 的 UI 组件）直接用 `Motion.X` 引用，
/// 避免与同名命名空间 Milan.Maui.UI 冲突导致嵌套类型无法解析。
/// </summary>
public static class Motion
{
    /// <summary>统一缓动 cubic-bezier(.2,.8,.2,1)（ease-out 快出）。PathInterpolator 需 API 21+，本项目达标。</summary>
    public static readonly PathInterpolator Ease =
        new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    // 时长规范（ms）：微交互 / 转场 / 强调 / 演出（设计文档 §6）
    public const int Micro = 150;
    public const int Trans = 300;
    public const int Emph = 500;
    public const int Show = 1500;

    /// <summary>淡入（根/容器/单元素）。</summary>
    public static void Fade(View? v, int dur = Trans, int delay = 0)
    {
        if (v == null) return;
        v.Alpha = 0f;
        v.Animate()?.Alpha(1f)?.SetDuration(dur)?.SetStartDelay(delay)?.SetInterpolator(Ease)?.Start();
    }

    /// <summary>淡入 + 上浮（UI 区块错落入场）。</summary>
    public static void Rise(View? v, int dur = Trans, int delay = 0, int dyDp = 12)
    {
        if (v == null) return;
        v.Alpha = 0f; v.TranslationY = UI.Dp(dyDp);
        v.Animate()?.Alpha(1f)?.TranslationY(0)?.SetDuration(dur)?.SetStartDelay(delay)?.SetInterpolator(Ease)?.Start();
    }

    /// <summary>缩放弹入（法阵 / 焦点元素）。</summary>
    public static void Pop(View? v, int dur = Emph, int delay = 0, float from = 0.85f)
    {
        if (v == null) return;
        v.Alpha = 0f; v.ScaleX = from; v.ScaleY = from;
        v.Animate()?.Alpha(1f)?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(dur)?.SetStartDelay(delay)?.SetInterpolator(Ease)?.Start();
    }

    /// <summary>
    /// 入场编排：根淡入 → hero 浮入（仅淡入，不位移以免与漂浮动画冲突）→ staged 子项错落上浮（stagger ms）。
    /// 背景先于主体 ~80ms 落位（设计文档 §6）。仅做变换/透明，安全可重入（旋转屏重建后重播）。
    /// </summary>
    public static void PlayEntrance(View? root, View? hero, int stagger, params View[] staged)
    {
        Fade(root, Trans);
        if (hero != null) Fade(hero, Emph);
        for (int i = 0; i < staged.Length; i++)
            Rise(staged[i], Trans, 140 + i * stagger);
    }

    public static void PlayEntrance(View root, params View[] staged)
        => PlayEntrance(root, null, 60, staged);
}

/// <summary>
/// Minimal Application subclass that gives every Activity a global Context
/// (handy for stateless UI helpers) and owns the shared GameState.
/// </summary>
[Android.App.Application]
public class MauiApp : Application
{
    public new static Context Context = null!;
    /// <summary>当前前台 Activity，供 CrashReporter 在崩溃时弹出现场对话框（免改 10 个 Activity 的 OnCreate）。</summary>
    public static Android.App.Activity? Current { get; set; }

    public MauiApp(IntPtr handle, JniHandleOwnership transfer) : base(handle, transfer) { }

    public override void OnCreate()
    {
        base.OnCreate();
        // 全局 Context 先于一切就位：CrashReporter 的外部目录镜像依赖它（取证文件要能被用户取回）。
        Context = this;
        // 自动追踪前台 Activity，无需逐个页面改代码即可在崩溃时定位现场。
        try { RegisterActivityLifecycleCallbacks(new ActivityTracker()); } catch { }
        // 取证优先：先归档上一轮面包屑并装上全局异常钩子，之后任何环节崩了都能留下现场。
        CrashReporter.BeginBootTrace();
        CrashReporter.Install();
        CrashReporter.Boot("app.oncreate.begin");

        try
        {
            UI.Init(this);
            CrashReporter.Boot("app.ui.init.ok density=" + UI.Density);
        }
        catch (Exception ex)
        {
            // Density 拿不到不该让整个进程死掉，退回默认值继续。
            CrashReporter.Write("MauiApp.OnCreate/UI.Init", ex);
            CrashReporter.Boot("app.ui.init.FAILED");
        }
        CrashReporter.Boot("app.oncreate.done");

        // 最后：若有上次崩溃现场（托管异常报告，或未走完的面包屑= native 崩溃判据），
        // 直接启动展示页。必须在 Application 阶段做 —— HomeActivity 可能因同样的崩溃
        // 永远起不来，原来的「下次启动回显」就永远看不到。
        ShowPreviousCrashScreen();
    }

    /// <summary>
    /// 全局内存压力回调。Application 本身就是 ComponentCallbacks2，系统会对整个进程回调一次，
    /// 因此缓存收缩收口在这里，无需在 11 个 Activity 里各写一份 OnTrimMemory。
    /// </summary>
    public override void OnTrimMemory(TrimMemory level)
    {
        base.OnTrimMemory(level);
        try
        {
            if (level >= TrimMemory.Complete) { VfxRenderer.TrimWeaponCache(); PortraitLoader.Trim(0); }
            else if (level >= TrimMemory.Moderate) { VfxRenderer.TrimWeaponCache(); PortraitLoader.Trim(4); }
            else if (level >= TrimMemory.UiHidden) PortraitLoader.Trim(8);
        }
        catch (Exception ex) { CrashReporter.Write("MauiApp.OnTrimMemory", ex); }
    }

    /// <summary>上次崩溃现场回显：优先托管异常报告，否则用未走完的面包屑。</summary>
    static void ShowPreviousCrashScreen()
    {
        try
        {
            string? report = CrashReporter.ReadAndClear();
            if (string.IsNullOrEmpty(report) && CrashReporter.PreviousBootIncomplete())
                report = "未捕获到托管异常，但上次启动未走完流程 —— 疑似 native 层崩溃或进程被系统杀死。\n\n"
                       + "上次启动面包屑：\n" + (CrashReporter.PreviousBootTrace() ?? "(无)");
            if (string.IsNullOrEmpty(report)) return;
            // 现场已在 Application 阶段展示，删掉归档标记，HomeActivity 不再重复弹。
            CrashReporter.ClearPreviousBootFlag();
            var i = new Android.Content.Intent(Context, typeof(Activities.CrashActivity));
            i.SetFlags(Android.Content.ActivityFlags.NewTask);
            i.PutExtra("report", report);
            Context.StartActivity(i);
        }
        catch { }
    }
}
