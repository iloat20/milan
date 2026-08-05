using Android.Content;
using Android.Graphics;
using Path = Android.Graphics.Path;

namespace Milan.Maui;

/// <summary>
/// 角色专属特效渲染库。
/// 根据角色数据中的 WeaponVfx / AmbientVfx 字段，在详情页与抽卡演出中绘制武器特效与背景氛围。
/// 武器段按「魔兽世界武器 icon」思路：清晰武器轮廓 + 金属三段渐变 + 华丽护手/握柄/宝石 + 元素附魔光边。
/// 并在 DrawWeapon 末尾统一叠加 AddAura 炫酷能量层（悬浮光台 + 外发光晕 + 环绕粒子 + 旋转流动光弧）。
/// 武器仅绘制在独立展示区（详情页 WeaponPreviewView / 抽卡 WeaponFxView），不再叠加到角色立绘上。
/// </summary>
public static class VfxRenderer
{
    // 金属 / 黄金调色板（WoW 风格冷钢 + 暖金）
    private static readonly Color SteelHi = Color.Rgb(233, 239, 247);
    private static readonly Color SteelMid = Color.Rgb(150, 166, 190);
    private static readonly Color SteelLo = Color.Rgb(84, 100, 126);
    private static readonly Color BronzeHi = Color.Rgb(214, 168, 110);
    private static readonly Color BronzeLo = Color.Rgb(120, 84, 44);
    private static readonly Color GoldHi = Color.Rgb(246, 216, 138);
    private static readonly Color GoldLo = Color.Rgb(150, 116, 46);
    private static readonly Color GripHi = Color.Rgb(110, 84, 64);
    private static readonly Color GripLo = Color.Rgb(54, 40, 30);
    private static readonly Color DarkHi = Color.Rgb(122, 130, 142);
    private static readonly Color DarkLo = Color.Rgb(50, 56, 68);
    private static readonly Color VoidHi = Color.Rgb(165, 95, 235);
    private static readonly Color VoidLo = Color.Rgb(40, 18, 70);

    // ═══════════════════════════════════════════════════════════════
    // 氛围特效入口
    // ═══════════════════════════════════════════════════════════════
    public static void DrawAmbient(Canvas canvas, string ambientVfx, RectF bounds, float phase, Paint paint, Color elementGlow)
    {
        if (string.IsNullOrEmpty(ambientVfx)) return;
        switch (ambientVfx)
        {
            case "day_night_cycle_glow":
            case "ember_rebirth_field":
            case "broken_suns_inferno":
            case "pride_aura_flame":
            case "broken_bronze_ash":
                DrawFlameAmbient(canvas, bounds, phase, paint, elementGlow, intensity: ambientVfx.Contains("inferno") ? 1.4f : 1f);
                break;

            case "thundercloud_city":
            case "electric_coil_core":
            case "storm_feather_spark":
                DrawLightningAmbient(canvas, bounds, phase, paint, elementGlow);
                break;

            case "scrap_storm_ironveil":
            case "ruin_battlefield_banners":
            case "metal_storm_wasteland":
            case "blood_metal_smoke":
            case "ruin_jungle_parts":
            case "mechanical_bee_cloud":
                DrawMetalAmbient(canvas, bounds, phase, paint, elementGlow, heavy: ambientVfx.Contains("battlefield") || ambientVfx.Contains("scrap_storm"));
                break;

            case "wind_tunnel_speedlines":
            case "sea_stone_mist":
            case "floating_garden_petals":
            case "harvest_wind_leaves":
                DrawWindAmbient(canvas, bounds, phase, paint, elementGlow, petal: ambientVfx.Contains("petals") || ambientVfx.Contains("leaves"));
                break;

            case "soul_petal_drift":
            case "night_city_shadows":
            case "rift_shadow_motes":
                DrawShadowAmbient(canvas, bounds, phase, paint, elementGlow);
                break;

            case "void_devour_particles":
            case "poison_marsh_fog":
                DrawVoidAmbient(canvas, bounds, phase, paint, elementGlow);
                break;

            case "floating_island_aurora":
            case "jade_shield_earth":
            case "dust_earth_burst":
                DrawEarthAmbient(canvas, bounds, phase, paint, elementGlow);
                break;

            case "underwater_bubble_light":
                DrawWaterAmbient(canvas, bounds, phase, paint, elementGlow);
                break;

            case "constellation_guidance":
                DrawStarAmbient(canvas, bounds, phase, paint, elementGlow);
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器特效入口
    // ═══════════════════════════════════════════════════════════════
    /// <summary>
    /// 在以 bounds 为中心的「武器舞台」中绘制成形武器。phase 为静态值时呈现为干净的静态徽记。
    /// 任何未匹配的 weaponVfx 都会画一把默认长剑，保证展示卡永不为空。
    /// </summary>
    public static void DrawWeapon(Canvas canvas, string weaponVfx, RectF bounds, float phase, Paint paint, Color rarityCore, Color elementGlow, string element = "", string rarity = "")
    {
        if (string.IsNullOrEmpty(weaponVfx) || bounds.Width() <= 0 || bounds.Height() <= 0) return;

        // 武器悬浮微动：整体上下轻浮 + 极缓旋转，营造"活物"漂浮感（能量层保持不动）
        float u = Wu(bounds);
        float bob = MathF.Sin(phase * 1.3f) * u * 0.020f;
        float rot = MathF.Sin(phase * 0.9f) * 0.045f; // ≈2.6°
        canvas.Save();
        canvas.Translate(0, bob);
        canvas.Rotate((float)(rot * 180 / Math.PI), bounds.CenterX(), bounds.CenterY());
        switch (weaponVfx)
        {
            case "sun_orb_flame":
                DrawSunHalo(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "solar_orb_bow":
            case "shadow_bow_arrow":
                DrawBow(canvas, bounds, phase, paint, rarityCore, elementGlow, weaponVfx == "shadow_bow_arrow");
                break;

            case "five_color_stone_staff":
                DrawStaff(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "gear_axe_storm":
                DrawAxe(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "lightning_dual_swords":
                DrawTwinSwords(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "void_rift_blade":
                DrawVoidBlade(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "tiger_soul_cleaver":
                DrawGreatsword(canvas, bounds, phase, paint, rarityCore, elementGlow, rarityCore, true);
                break;

            case "bronze_greed_flame":
                DrawBronzeCauldron(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "vibranium_tiger_claw":
            case "roar_shock_claw":
                DrawFist(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "wind_blade_dash":
            case "petal_ribbon_blade":
                DrawSaber(canvas, bounds, phase, paint, rarityCore, elementGlow, false);
                break;

            case "wind_stone_projectile":
                DrawStoneBeak(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "shadow_dagger_whisper":
            case "shadow_wire_tangle":
                DrawDagger(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "mjolnir_hammer_arc":
                DrawWarhammer(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "venom_fang_whip":
                DrawWhip(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "phoenix_wing_flame":
                DrawGlaive(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "star_oracle_sigil":
                DrawAstrolabe(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "shell_barrier_earth":
            case "earth_burrow_strike":
            case "tusk_charge_wind":
                DrawShield(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "regen_blast_cannon":
                DrawCannon(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "poison_stinger_swarm":
            case "scrap_claw_mine":
                DrawSwarm(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "water_trident_surge":
                DrawTrident(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            case "thunder_feather_dive":
                DrawThunderSpear(canvas, bounds, phase, paint, rarityCore, elementGlow);
                break;

            default:
                // 兜底：任何未匹配武器画一把默认长剑，避免展示卡空白
                DrawSword(canvas, bounds, phase, paint, rarityCore, elementGlow, 0, false, 1f, 1f);
                break;
        }
        canvas.Restore();

        // 通用炫酷能量层：呼吸核心辉光 + 元素主题粒子 + 稀有度极光环 + 周期冲击波 + 流动光弧
        AddAura(canvas, paint, bounds.CenterX(), bounds.CenterY(), u, elementGlow, phase, element, rarity);
    }

    // ─────────────────────────────────────────────────────────────
    // 通用辅助
    // ─────────────────────────────────────────────────────────────
    private static float Wu(RectF b) => Math.Min(b.Width(), b.Height());

    /// <summary>冷钢三段渐变（亮-中-暗），模拟圆柱金属反光。</summary>
    private static void Steel(Canvas c, Paint p, Path path, float x0, float y0, float x1, float y1)
    {
        p.SetStyle(Paint.Style.Fill);
        p.SetShader(new LinearGradient(x0, y0, x1, y1,
            UI.ColorLongs(SteelHi, SteelMid, SteelLo), new[] { 0f, 0.5f, 1f }, Shader.TileMode.Clamp));
        c.DrawPath(path, p);
        p.SetShader(null);
    }

    /// <summary>附魔光边：先模糊光晕，再亮芯描边，得到 WoW 式发光武器边缘。</summary>
    private static void GlowStroke(Canvas c, Paint p, Path path, Color glow, float glowDp, float coreDp)
    {
        p.SetStyle(Paint.Style.Stroke);
        p.StrokeJoin = Paint.Join.Round;
        p.StrokeCap = Paint.Cap.Round;
        p.SetMaskFilter(new BlurMaskFilter(UI.Dp(glowDp), BlurMaskFilter.Blur.Solid));
        p.StrokeWidth = UI.Dp(glowDp);
        p.Color = glow;
        c.DrawPath(path, p);
        p.SetMaskFilter(null);
        p.StrokeWidth = UI.Dp(coreDp);
        p.Color = Color.Argb(235, 250, 250, 255);
        c.DrawPath(path, p);
    }

    private static void LineGlow(Canvas c, Paint p, float x0, float y0, float x1, float y1, Color glow, float glowDp, float coreDp)
    {
        var path = new Path();
        path.MoveTo(x0, y0);
        path.LineTo(x1, y1);
        GlowStroke(c, p, path, glow, glowDp, coreDp);
    }

    /// <summary>柔光圆，作为能量辉光底。</summary>
    private static void GlowDisc(Canvas c, Paint paint, float cx, float cy, float r, Color col, int a)
    {
        if (r <= 0 || a <= 0) return;
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new RadialGradient(cx, cy, r,
            UI.ColorLongs(Color.Argb(a, col.R, col.G, col.B), Color.Argb(0, 0, 0, 0)),
            new[] { 0f, 1f }, Shader.TileMode.Clamp));
        c.DrawCircle(cx, cy, r, paint);
        paint.SetShader(null);
    }

    /// <summary>竖直金属渐变填充（保留，供球/宝石等使用）。</summary>
    private static void FillGradV(Canvas c, Path p, Paint paint, float y0, float y1, Color top, Color bot)
    {
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(0, y0, 0, y1,
            UI.ColorLongs(top, bot), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        c.DrawPath(p, paint);
        paint.SetShader(null);
    }

    // ─────────────────────────────────────────────────────────────
    // 通用炫酷能量层（所有武器共享）：呼吸核心辉光 + 元素主题粒子 + 稀有度极光环 + 周期冲击波 + 流动光弧
    // ─────────────────────────────────────────────────────────────
    private static void AddAura(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase, string element, string rarity)
    {
        bool legendary = (rarity ?? "").Contains("UR") || (rarity ?? "").Contains("SSR");

        // 呼吸式核心辉光（白热核心 + 元素色外晕）
        float breathe = 0.5f + 0.5f * MathF.Sin(phase * 1.5f);
        GlowDisc(c, p, cx, cy, u * (0.30f + 0.05f * breathe), glow, (int)(22 + 12 * breathe));
        GlowDisc(c, p, cx, cy, u * (0.15f + 0.03f * breathe), Color.Argb(255, 255, 255, 255), (int)(34 * breathe));

        // 悬浮光台（底部椭圆辉光）
        float baseY = cy + u * 0.34f;
        p.SetStyle(Paint.Style.Fill);
        p.SetShader(new RadialGradient(cx, baseY, u * 0.30f,
            UI.ColorLongs(Color.Argb(70, glow.R, glow.G, glow.B), Color.Argb(0, 0, 0, 0)),
            new[] { 0f, 1f }, Shader.TileMode.Clamp));
        var pad = u * 0.30f;
        c.DrawOval(new RectF(cx - pad, baseY - u * 0.055f, cx + pad, baseY + u * 0.055f), p);
        p.SetShader(null);

        // 元素主题粒子系统（火/雷/冰/风/光/暗/水/土/通用）
        DrawElementParticles(c, p, cx, cy, u, glow, phase, element);

        // 稀有度极光环（高稀有度更强、更亮）
        DrawSpectralRing(c, p, cx, cy, u, glow, phase, legendary);

        // 周期冲击波（两道错相扩散）
        for (int k = 0; k < 2; k++)
        {
            float t = (phase * 0.5f + k * 0.5f) % 1f;
            float rr = u * (0.16f + t * 0.50f);
            p.SetStyle(Paint.Style.Stroke);
            p.StrokeWidth = UI.Dp(2f) * (1 - t);
            p.SetMaskFilter(new BlurMaskFilter(UI.Dp(3), BlurMaskFilter.Blur.Solid));
            p.Color = Color.Argb((int)(110 * (1 - t)), glow.R, glow.G, glow.B);
            c.DrawCircle(cx, cy, rr, p);
            p.SetMaskFilter(null);
        }

        // 旋转流动光弧（能量在武器周围流动）
        float arcR = u * 0.40f;
        var oval = new RectF(cx - arcR, cy - arcR * 0.94f, cx + arcR, cy + arcR * 0.94f);
        var arc = new Path();
        arc.AddArc(oval, (phase * 1.2f) * 180 / MathF.PI, (MathF.PI * 1.1f) * 180 / MathF.PI);
        p.SetStyle(Paint.Style.Stroke);
        p.StrokeWidth = UI.Dp(2.4f);
        p.StrokeCap = Paint.Cap.Round;
        p.SetPathEffect(new DashPathEffect(new float[] { UI.Dp(16), UI.Dp(22) }, (phase * UI.Dp(70)) % UI.Dp(38)));
        p.SetMaskFilter(new BlurMaskFilter(UI.Dp(4), BlurMaskFilter.Blur.Solid));
        p.Color = glow;
        c.DrawPath(arc, p);
        p.SetPathEffect(null);
        p.SetMaskFilter(null);
    }

    /// <summary>确定性伪随机（0..1），用于粒子分布，避免每帧抖动。</summary>
    private static float Hash(int i)
    {
        var x = MathF.Sin(i * 12.9898f + 78.233f);
        return x - MathF.Floor(x);
    }

    /// <summary>按角色元素属性分发不同的主题粒子系统。</summary>
    private static void DrawElementParticles(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase, string element)
    {
        var el = (element ?? "").ToLowerInvariant();
        if (el.Contains("fire") || el.Contains("flame") || el.Contains("ember") || el.Contains("inferno") || el.Contains("burn") || el.Contains("pyro") || el.Contains("sun"))
            FireParticles(c, p, cx, cy, u, glow, phase);
        else if (el.Contains("thunder") || el.Contains("lightning") || el.Contains("electric") || el.Contains("storm") || el.Contains("volt"))
            ThunderParticles(c, p, cx, cy, u, glow, phase);
        else if (el.Contains("ice") || el.Contains("frost") || el.Contains("snow") || el.Contains("cold") || el.Contains("cryo"))
            IceParticles(c, p, cx, cy, u, glow, phase);
        else if (el.Contains("wind") || el.Contains("air") || el.Contains("gale") || el.Contains("leaf") || el.Contains("petal") || el.Contains("anemo"))
            WindParticles(c, p, cx, cy, u, glow, phase);
        else if (el.Contains("light") || el.Contains("holy") || el.Contains("radiance") || el.Contains("lum") || el.Contains("day"))
            LightParticles(c, p, cx, cy, u, glow, phase);
        else if (el.Contains("void") || el.Contains("shadow") || el.Contains("dark") || el.Contains("abyss") || el.Contains("night"))
            VoidParticles(c, p, cx, cy, u, glow, phase);
        else if (el.Contains("water") || el.Contains("sea") || el.Contains("aqua") || el.Contains("ocean") || el.Contains("hydro") || el.Contains("bubble"))
            WaterParticles(c, p, cx, cy, u, glow, phase);
        else if (el.Contains("earth") || el.Contains("stone") || el.Contains("rock") || el.Contains("dust") || el.Contains("terra") || el.Contains("geo"))
            EarthParticles(c, p, cx, cy, u, glow, phase);
        else
            GenericRing(c, p, cx, cy, u, glow, phase);
    }

    private static void FireParticles(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase)
    {
        int N = 9;
        float baseY = cy + u * 0.34f, topY = cy - u * 0.40f, h = baseY - topY;
        for (int i = 0; i < N; i++)
        {
            float s = Hash(i * 7 + 3);
            float t = (phase * 0.5f + s) % 1f;
            float x = cx + (Hash(i * 13 + 1) - 0.5f) * u * 0.5f + MathF.Sin(phase * 3 + s * 6) * u * 0.03f;
            float y = baseY - t * h;
            float flick = 0.55f + 0.45f * MathF.Sin(phase * 9 + s * 12);
            float r = u * 0.014f * (1 - t * 0.4f);
            GlowDisc(c, p, x, y, r * 2.2f, Color.Rgb(255, 140, 40), (int)(120 * flick * (1 - t)));
            p.SetStyle(Paint.Style.Fill);
            p.Color = Color.Argb((int)(230 * flick * (1 - t)), 255, 200 + (int)(40 * flick), 120);
            c.DrawCircle(x, y, r, p);
        }
    }

    private static void ThunderParticles(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase)
    {
        int N = 10;
        for (int i = 0; i < N; i++)
        {
            float s = Hash(i * 5 + 2);
            float flash = MathF.Sin(phase * 10 + s * 22);
            if (flash < 0.25f) continue;
            float a = s * 6.283f;
            float r0 = u * 0.10f, len = u * (0.14f + 0.12f * Hash(i * 3 + 9));
            float x0 = cx + MathF.Cos(a) * r0, y0 = cy + MathF.Sin(a) * r0;
            float x1 = cx + MathF.Cos(a) * (r0 + len), y1 = cy + MathF.Sin(a) * (r0 + len);
            float mx = (x0 + x1) / 2f + MathF.Cos(a + 1.5f) * u * 0.04f, my = (y0 + y1) / 2f + MathF.Sin(a + 1.5f) * u * 0.04f;
            var sp = new Path(); sp.MoveTo(x0, y0); sp.LineTo(mx, my); sp.LineTo(x1, y1);
            p.SetStyle(Paint.Style.Stroke); p.StrokeWidth = UI.Dp(1.6f); p.StrokeCap = Paint.Cap.Round;
            p.SetMaskFilter(new BlurMaskFilter(UI.Dp(3), BlurMaskFilter.Blur.Solid));
            p.Color = Color.Argb((int)(230 * (flash - 0.25f) / 0.75f), glow.R, glow.G, glow.B);
            c.DrawPath(sp, p);
            p.SetMaskFilter(null);
            p.SetStyle(Paint.Style.Fill); p.Color = Color.Argb(240, 255, 255, 255);
            c.DrawCircle(x1, y1, UI.Dp(1.6f), p);
        }
    }

    private static void IceParticles(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase)
    {
        int N = 14;
        float topY = cy - u * 0.42f, botY = cy + u * 0.36f, h = botY - topY;
        for (int i = 0; i < N; i++)
        {
            float s = Hash(i * 11 + 4);
            float t = (phase * 0.22f + s) % 1f;
            float x = cx + (Hash(i * 17 + 6) - 0.5f) * u * 0.7f + MathF.Sin(phase * 2 + s * 6) * u * 0.04f;
            float y = topY + t * h;
            float r = u * 0.010f;
            float tw = 0.5f + 0.5f * MathF.Sin(phase * 5 + s * 9);
            p.SetStyle(Paint.Style.Fill);
            p.Color = Color.Argb((int)(200 * (1 - t * 0.5f)), 230, 245, 255);
            c.DrawCircle(x, y, r, p);
            p.SetStyle(Paint.Style.Stroke); p.StrokeWidth = UI.Dp(0.8f);
            p.Color = Color.Argb((int)(150 * tw), 255, 255, 255);
            c.DrawLine(x - r * 1.8f, y, x + r * 1.8f, y, p);
            c.DrawLine(x, y - r * 1.8f, x, y + r * 1.8f, p);
        }
    }

    private static void WindParticles(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase)
    {
        int N = 10;
        for (int i = 0; i < N; i++)
        {
            float s = Hash(i * 9 + 5);
            float a = s * 6.283f + phase * 1.3f;
            float x = cx + MathF.Cos(a) * u * 0.34f, y = cy + MathF.Sin(a) * u * 0.21f;
            float r = u * 0.012f;
            p.SetStyle(Paint.Style.Fill);
            p.Color = Color.Argb((int)(180 + 40 * MathF.Sin(phase * 3 + s * 6)), 220, 240, 255);
            c.DrawCircle(x, y, r, p);
            p.SetStyle(Paint.Style.Stroke); p.StrokeWidth = UI.Dp(1f);
            p.Color = Color.Argb(90, glow.R, glow.G, glow.B);
            var pp = new Path(); pp.MoveTo(x, y); pp.QuadTo(x + u * 0.03f, y - u * 0.02f, x + u * 0.05f, y); c.DrawPath(pp, p);
        }
    }

    private static void LightParticles(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase)
    {
        int N = 10;
        float baseY = cy + u * 0.34f, topY = cy - u * 0.40f, h = baseY - topY;
        for (int i = 0; i < N; i++)
        {
            float s = Hash(i * 6 + 8);
            float t = (phase * 0.4f + s) % 1f;
            float x = cx + (Hash(i * 14 + 2) - 0.5f) * u * 0.6f + MathF.Sin(phase * 2 + s * 5) * u * 0.02f;
            float y = baseY - t * h;
            float r = u * 0.010f * (1 - t * 0.3f);
            GlowDisc(c, p, x, y, r * 2.4f, Color.Rgb(255, 240, 200), (int)(120 * (1 - t)));
            p.SetStyle(Paint.Style.Fill);
            p.Color = Color.Argb((int)(230 * (1 - t)), 255, 250, 235);
            c.DrawCircle(x, y, r, p);
        }
        for (int i = 0; i < 3; i++)
        {
            float s = Hash(i * 23 + 11);
            float tw = 0.5f + 0.5f * MathF.Sin(phase * 4 + s * 15);
            if (tw < 0.5f) continue;
            float x = cx + MathF.Cos(s * 6.283f) * u * 0.30f, y = cy + MathF.Sin(s * 6.283f) * u * 0.30f;
            float g = u * 0.03f * tw;
            p.SetStyle(Paint.Style.Fill); p.Color = Color.Argb((int)(200 * tw), 255, 250, 230);
            c.DrawCircle(x, y, UI.Dp(1.4f), p);
            p.SetStyle(Paint.Style.Stroke); p.StrokeWidth = UI.Dp(0.8f);
            p.Color = Color.Argb((int)(160 * tw), 255, 250, 230);
            c.DrawLine(x - g, y, x + g, y, p); c.DrawLine(x, y - g, x, y + g, p);
        }
    }

    private static void VoidParticles(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase)
    {
        int N = 9;
        for (int i = 0; i < N; i++)
        {
            float s = Hash(i * 8 + 7);
            float a = s * 6.283f - phase * 0.9f;
            float x = cx + MathF.Cos(a) * u * 0.30f, y = cy + MathF.Sin(a) * u * 0.30f * 0.92f;
            float rs = u * (0.03f + 0.02f * Hash(i * 5 + 3));
            p.SetStyle(Paint.Style.Fill); p.Color = Color.Argb(60, 0, 0, 0);
            c.DrawCircle(x, y, rs, p);
            GlowDisc(c, p, x, y, rs * 1.6f, glow, (int)(90 + 60 * MathF.Sin(phase * 3 + s * 5)));
            p.SetStyle(Paint.Style.Fill); p.Color = Color.Argb(200, glow.R, glow.G, glow.B);
            c.DrawCircle(x, y, UI.Dp(1.2f), p);
        }
    }

    private static void WaterParticles(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase)
    {
        int N = 12;
        float baseY = cy + u * 0.34f, topY = cy - u * 0.40f, h = baseY - topY;
        for (int i = 0; i < N; i++)
        {
            float s = Hash(i * 10 + 12);
            float t = (phase * 0.35f + s) % 1f;
            float x = cx + (Hash(i * 15 + 9) - 0.5f) * u * 0.55f + MathF.Sin(phase * 3 + s * 4) * u * 0.02f;
            float y = baseY - t * h;
            float r = u * 0.012f * (1 - t * 0.3f);
            p.SetStyle(Paint.Style.Fill); p.Color = Color.Argb((int)(40 * (1 - t)), glow.R, glow.G, glow.B);
            c.DrawCircle(x, y, r * 1.4f, p);
            p.SetStyle(Paint.Style.Stroke); p.StrokeWidth = UI.Dp(1f);
            p.Color = Color.Argb((int)(170 * (1 - t)), 190, 230, 255);
            c.DrawCircle(x, y, r, p);
            p.SetStyle(Paint.Style.Fill); p.Color = Color.Argb(200, 255, 255, 255);
            c.DrawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.3f, p);
        }
    }

    private static void EarthParticles(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase)
    {
        int N = 8;
        for (int i = 0; i < N; i++)
        {
            float s = Hash(i * 7 + 15);
            float a = s * 6.283f + phase * 0.5f;
            float x = cx + MathF.Cos(a) * u * 0.28f, y = cy + MathF.Sin(a) * u * 0.28f * 0.82f + MathF.Sin(phase * 2 + s * 4) * u * 0.02f;
            float rs = u * 0.018f;
            p.SetStyle(Paint.Style.Fill); p.Color = Color.Argb(170, 120, 96, 70);
            c.DrawRoundRect(new RectF(x - rs, y - rs * 0.7f, x + rs, y + rs * 0.7f), UI.Dp(1), UI.Dp(1), p);
        }
    }

    private static void GenericRing(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase)
    {
        const int N = 9;
        float spin = phase * 0.5f;
        for (int i = 0; i < N; i++)
        {
            float a = i * 2 * MathF.PI / N + spin;
            float ringR = u * 0.33f + u * 0.025f * MathF.Sin(phase * 2 + i);
            float x = cx + MathF.Cos(a) * ringR;
            float y = cy + MathF.Sin(a) * ringR * 0.94f;
            float pr = u * 0.010f + u * 0.006f * MathF.Sin(phase * 3 + i * 1.7f);
            if (pr <= 0) continue;
            GlowDisc(c, p, x, y, UI.Dp(6) + pr * 3, glow, 130);
            p.SetStyle(Paint.Style.Fill);
            p.Color = Color.Argb(220, 255, 250, 245);
            c.DrawCircle(x, y, pr, p);
        }
    }

    /// <summary>稀有度极光环：旋转的彩虹光谱环，高稀有度（UR/SSR）更粗更亮。</summary>
    private static void DrawSpectralRing(Canvas c, Paint p, float cx, float cy, float u, Color glow, float phase, bool legendary)
    {
        float ringR = u * (legendary ? 0.48f : 0.44f);
        var oval = new RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        var spectral = new[]
        {
            Color.Rgb(255, 90, 120), Color.Rgb(255, 190, 90), Color.Rgb(120, 255, 170),
            Color.Rgb(100, 205, 255), Color.Rgb(190, 140, 255), Color.Rgb(255, 140, 220)
        };
        p.SetStyle(Paint.Style.Stroke);
        p.StrokeWidth = UI.Dp(legendary ? 3f : 1.8f);
        p.StrokeCap = Paint.Cap.Round;
        p.SetMaskFilter(new BlurMaskFilter(UI.Dp(legendary ? 6 : 3), BlurMaskFilter.Blur.Solid));
        p.SetShader(new SweepGradient(cx, cy, UI.ColorLongs(spectral), null));
        c.Save();
        c.Rotate(phase * 40f, cx, cy);
        c.DrawOval(oval, p);
        c.Restore();
        p.SetShader(null);
        p.SetMaskFilter(null);
    }

    /// <summary>华丽护手：黄金横 guard + 缠绳握柄 + 宝石柄头（柄头宝石按 phase 脉冲发光）。scale 为整体缩放。</summary>
    private static void Hilt(Canvas c, Paint p, float cx, float guardY, float gripBot, Color gem, float scale, float phase)
    {
        float gw = UI.Dp(11) * scale;     // guard 半宽
        float gt = UI.Dp(3.4f) * scale;   // guard 厚度
        // 横 guard（黄金渐变）
        var grd = new RectF(cx - gw, guardY - gt, cx + gw, guardY + gt);
        p.SetStyle(Paint.Style.Fill);
        p.SetShader(new LinearGradient(cx - gw, guardY - gt, cx + gw, guardY + gt, UI.ColorLongs(GoldHi, GoldLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        c.DrawRoundRect(grd, UI.Dp(2), UI.Dp(2), p);
        p.SetShader(null);
        // guard 两端圆头
        p.Color = GoldHi;
        c.DrawCircle(cx - gw, guardY, gt * 0.95f, p);
        c.DrawCircle(cx + gw, guardY, gt * 0.95f, p);
        // 握柄（皮革渐变）
        float gtop = guardY + gt * 0.5f;
        var gripr = new RectF(cx - UI.Dp(3.1f) * scale, gtop, cx + UI.Dp(3.1f) * scale, gripBot);
        p.SetShader(new LinearGradient(gripr.Left, 0, gripr.Right, 0, UI.ColorLongs(GripHi, GripLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        c.DrawRoundRect(gripr, UI.Dp(1.5f) * scale, UI.Dp(1.5f) * scale, p);
        p.SetShader(null);
        // 缠绳纹
        p.SetStyle(Paint.Style.Stroke);
        p.StrokeWidth = UI.Dp(1) * scale;
        p.Color = Color.Argb(120, 28, 20, 14);
        for (float yy = gtop + UI.Dp(2.5f) * scale; yy < gripBot - UI.Dp(2f) * scale; yy += UI.Dp(4f) * scale)
            c.DrawLine(gripr.Left + UI.Dp(1) * scale, yy, gripr.Right - UI.Dp(1) * scale, yy - UI.Dp(2.2f) * scale, p);
        // 柄头圆 + 宝石（脉冲发光）
        float py = gripBot + UI.Dp(3.2f) * scale;
        float pulse = 0.6f + 0.4f * MathF.Sin(phase * 3);
        GlowDisc(c, p, cx, py, UI.Dp(8) * scale * pulse, gem, 150);
        p.SetStyle(Paint.Style.Fill);
        p.Color = GoldHi;
        c.DrawCircle(cx, py, UI.Dp(4.4f) * scale, p);
        p.Color = gem;
        c.DrawCircle(cx, py, UI.Dp(2.6f) * scale, p);
        p.Color = Color.Argb(190, 255, 255, 255);
        c.DrawCircle(cx - UI.Dp(0.9f) * scale, py - UI.Dp(0.9f) * scale, UI.Dp(1f) * scale, p);
    }

    // ═══════════════════════════════════════════════════════════════
    // 通用长剑（多数武器复用）
    // angleDeg：整体旋转角；great：巨剑（更宽更长）；widMul：刃宽倍率；scale：整体缩放
    // ═══════════════════════════════════════════════════════════════
    private static void DrawSword(Canvas c, RectF b, float phase, Paint p, Color glow, Color gem, float angleDeg, bool great, float widMul, float scale)
    {
        float u = Wu(b), cx = b.CenterX(), cy = b.CenterY();
        c.Save();
        c.Rotate(angleDeg, cx, cy);
        float lenExtra = great ? u * 0.07f : 0;
        float tipY = cy - u * 0.42f * scale - lenExtra;
        float guardY = cy + u * 0.02f * scale;
        float pommelY = cy + u * 0.17f * scale;
        float bw = u * 0.052f * scale * widMul * (great ? 1.3f : 1f);
        // 剑刃（尖头 clip point）
        var blade = new Path();
        blade.MoveTo(cx, tipY);
        blade.LineTo(cx + bw, guardY - u * 0.04f * scale);
        blade.LineTo(cx + bw * 0.78f, guardY);
        blade.LineTo(cx - bw * 0.78f, guardY);
        blade.LineTo(cx - bw, guardY - u * 0.04f * scale);
        blade.Close();
        Steel(c, p, blade, cx - bw, tipY, cx + bw, guardY);
        // 中央血槽高光
        p.SetStyle(Paint.Style.Stroke);
        p.StrokeWidth = UI.Dp(1.3f) * scale;
        p.Color = Color.Argb(150, 245, 250, 255);
        c.DrawLine(cx, tipY + u * 0.03f, cx, guardY - u * 0.03f, p);
        // 刃口高光
        p.StrokeWidth = UI.Dp(1f) * scale;
        p.Color = Color.Argb(210, 255, 255, 255);
        c.DrawLine(cx - bw * 0.92f, guardY - u * 0.04f * scale, cx - bw * 0.4f, tipY + u * 0.025f, p);
        // 附魔光边
        GlowStroke(c, p, blade, glow, UI.Dp(3.2f) * scale, UI.Dp(1.1f) * scale);
        // 护手 + 握柄 + 柄头宝石
        Hilt(c, p, cx, guardY, pommelY, gem, scale, phase);
        c.Restore();
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：烛龙·阖辟神瞳·昼夜轮（半面熔金烈焰为昼、半面幽暗为夜，环心竖瞳）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawSunHalo(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        var r = u * 0.20f;
        var dayC = Color.Rgb(255, 196, 92);   // 昼焰金
        var nightC = Color.Rgb(150, 92, 220);  // 夜幽紫

        // 背后大辉光
        GlowDisc(canvas, paint, cx, cy, r * 2.4f, eg, 70);

        // 外金环
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(2.6f);
        paint.Color = GoldHi;
        canvas.DrawCircle(cx, cy, r, paint);

        // 神环：上半昼焰（暖橙发光弧）、下半夜暗（冷紫发光弧）
        var topArc = new Path(); topArc.AddArc(new RectF(cx - r * 1.06f, cy - r * 1.06f, cx + r * 1.06f, cy + r * 1.06f), 180, 180);
        GlowStroke(canvas, paint, topArc, dayC, UI.Dp(3.4f), UI.Dp(1.6f));
        var botArc = new Path(); botArc.AddArc(new RectF(cx - r * 1.06f, cy - r * 1.06f, cx + r * 1.06f, cy + r * 1.06f), 0, 180);
        GlowStroke(canvas, paint, botArc, nightC, UI.Dp(3.4f), UI.Dp(1.6f));

        // 环心竖瞳（元素色发光椭圆 + 幽暗瞳体）
        GlowDisc(canvas, paint, cx, cy, r * 0.62f, eg, 170);
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.Rgb(20, 12, 34);
        canvas.DrawOval(new RectF(cx - r * 0.16f, cy - r * 0.46f, cx + r * 0.16f, cy + r * 0.46f), paint);
        paint.Color = eg;
        canvas.DrawOval(new RectF(cx - r * 0.11f, cy - r * 0.40f, cx + r * 0.11f, cy + r * 0.40f), paint);
        paint.Color = Color.Argb(210, 255, 255, 255);
        canvas.DrawCircle(cx - r * 0.04f, cy - r * 0.16f, r * 0.05f, paint);

        // 外旋金芒
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.6f);
        paint.StrokeCap = Paint.Cap.Round;
        for (int i = 0; i < 14; i++)
        {
            var a = i * MathF.PI / 7 + phase * 0.4f;
            var r1 = r * 1.30f;
            var r2 = r * (1.5f + 0.12f * MathF.Sin(phase * 2 + i));
            LineGlow(canvas, paint, cx + MathF.Cos(a) * r1, cy + MathF.Sin(a) * r1, cx + MathF.Cos(a) * r2, cy + MathF.Sin(a) * r2, GoldHi, 1.4f, 0.7f);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：神弓（反曲弓 + 搭箭，金乌/破魔两种）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawBow(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg, bool shadow)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float hh = u * 0.40f;
        float gripX = cx + (shadow ? -u * 0.12f : u * 0.12f);
        float dir = shadow ? -1 : 1; // 弓臂开口方向
        // 反曲弓臂
        var limb = new Path();
        limb.MoveTo(gripX, cy - hh);
        limb.QuadTo(gripX + dir * hh * 0.85f, cy - hh * 0.45f, gripX + dir * hh * 0.35f, cy - hh * 0.12f);
        limb.QuadTo(gripX + dir * hh * 0.55f, cy, gripX + dir * hh * 0.35f, cy + hh * 0.12f);
        limb.QuadTo(gripX + dir * hh * 0.85f, cy + hh * 0.45f, gripX, cy + hh);
        // 弓臂描金 + 附魔光
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(5f);
        paint.StrokeJoin = Paint.Join.Round;
        paint.Color = GoldHi;
        canvas.DrawPath(limb, paint);
        GlowStroke(canvas, paint, limb, eg, UI.Dp(3f), UI.Dp(1.2f));
        // 弓臂顶端宝石
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = eg;
        canvas.DrawCircle(gripX + dir * hh * 0.35f, cy - hh * 0.12f, UI.Dp(3), paint);
        canvas.DrawCircle(gripX + dir * hh * 0.35f, cy + hh * 0.12f, UI.Dp(3), paint);
        // 弦
        float pull = (phase * 0.5f) % 1f;
        float nock = gripX - dir * pull * hh * 0.45f;
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.4f);
        paint.Color = Color.Argb(200, 235, 235, 255);
        canvas.DrawLine(gripX, cy - hh, nock, cy, paint);
        canvas.DrawLine(nock, cy, gripX, cy + hh, paint);
        // 搭箭（元素光簇）
        float aLen = hh * 1.0f;
        float tipX = gripX - dir * aLen;
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.Argb(230, 220, 220, 245);
        var shaftR = new RectF(Math.Min(nock, tipX), cy - UI.Dp(1.3f), Math.Max(nock, tipX), cy + UI.Dp(1.3f));
        canvas.DrawRect(shaftR, paint);
        float flare = 150 + 105 * MathF.Sin(phase * 3);
        GlowDisc(canvas, paint, tipX, cy, UI.Dp(11 + 5 * pull), eg, (int)(flare * 0.5f));
        paint.Color = Color.Argb((int)flare, 255, 220 + (shadow ? 0 : 40), 130 + (shadow ? 40 : 20));
        canvas.DrawCircle(tipX, cy, UI.Dp(4 + 3 * pull), paint);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：女娲·五色补天杖（长杖 + 五色石法球）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawStaff(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float topY = cy - u * 0.42f;
        float botY = cy + u * 0.42f;
        // 杖身（木纹金属）
        var shaft = new Path();
        shaft.AddRoundRect(new RectF(cx - UI.Dp(3.2f), topY, cx + UI.Dp(3.2f), botY), UI.Dp(2), UI.Dp(2), Path.Direction.Cw);
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - UI.Dp(3.2f), 0, cx + UI.Dp(3.2f), 0, UI.ColorLongs(GripHi, GripLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(shaft, paint);
        paint.SetShader(null);
        // 杖身金环
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.4f);
        paint.Color = GoldHi;
        canvas.DrawLine(cx - UI.Dp(3.2f), cy - u * 0.05f, cx + UI.Dp(3.2f), cy - u * 0.05f, paint);
        canvas.DrawLine(cx - UI.Dp(3.2f), cy + u * 0.18f, cx + UI.Dp(3.2f), cy + u * 0.18f, paint);
        // 顶端托架
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = GoldHi;
        canvas.DrawCircle(cx, topY + UI.Dp(2), UI.Dp(4.5f), paint);
        // 五色石
        float stoneY = topY - u * 0.04f;
        float stoneR = u * 0.10f;
        var cols = new[]
        {
            Color.Rgb(228, 72, 72), Color.Rgb(72, 132, 236), Color.Rgb(246, 216, 72),
            Color.Rgb(240, 240, 248), Color.Rgb(150, 110, 180)
        };
        var pulse = 0.85f + 0.15f * MathF.Sin(phase * 2);
        for (int i = 0; i < 5; i++)
        {
            var a = i * 2 * MathF.PI / 5 - MathF.PI / 2;
            var px = cx + MathF.Cos(a) * stoneR * pulse;
            var py = stoneY + MathF.Sin(a) * stoneR * pulse;
            GlowDisc(canvas, paint, px, py, stoneR * 0.8f, cols[i], 60);
            paint.SetStyle(Paint.Style.Fill);
            paint.Color = Color.Argb(225, cols[i].R, cols[i].G, cols[i].B);
            canvas.DrawCircle(px, py, stoneR * 0.52f, paint);
            paint.Color = Color.Argb(200, 255, 255, 255);
            canvas.DrawCircle(px - stoneR * 0.15f, py - stoneR * 0.15f, stoneR * 0.16f, paint);
        }
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.Argb(240, 255, 255, 255);
        canvas.DrawCircle(cx, stoneY, stoneR * 0.4f, paint);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：刑天·干戚（战斧 + 盾 + 齿轮）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawAxe(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        // 背景齿轮（刑天意象）
        for (int g = 0; g < 2; g++)
        {
            var gr = u * (0.30f + g * 0.12f);
            var spin = phase * (g == 0 ? 1f : -1.4f);
            int teeth = 10;
            paint.SetStyle(Paint.Style.Stroke);
            paint.StrokeWidth = UI.Dp(1.6f);
            paint.Color = Color.Argb(80, 150, 140, 160);
            canvas.Save();
            canvas.Rotate(spin * 30, cx, cy);
            var gp = new Path();
            for (int i = 0; i < teeth; i++)
            {
                var a0 = i * 2 * MathF.PI / teeth;
                var a1 = a0 + MathF.PI / teeth;
                var a2 = a0 + 2 * MathF.PI / teeth;
                gp.MoveTo(cx + MathF.Cos(a0) * gr, cy + MathF.Sin(a0) * gr);
                gp.LineTo(cx + MathF.Cos(a1) * (gr + UI.Dp(4)), cy + MathF.Sin(a1) * (gr + UI.Dp(4)));
                gp.LineTo(cx + MathF.Cos(a2) * gr, cy + MathF.Sin(a2) * gr);
            }
            canvas.DrawPath(gp, paint);
            canvas.DrawCircle(cx, cy, gr * 0.6f, paint);
            canvas.Restore();
        }
        // 斧柄
        float ax = cx + u * 0.16f;
        float ay = cy - u * 0.30f;
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(4f);
        paint.StrokeCap = Paint.Cap.Round;
        paint.Color = GripHi;
        canvas.DrawLine(ax, ay + u * 0.04f, cx - u * 0.04f, cy + u * 0.28f, paint);
        // 斧头（带胡刃的战斧）
        var blade = new Path();
        blade.MoveTo(ax, ay);
        blade.QuadTo(ax + u * 0.20f, ay - u * 0.12f, ax + u * 0.20f, ay + u * 0.02f); // 上尖
        blade.QuadTo(ax + u * 0.10f, ay + u * 0.10f, ax + u * 0.04f, ay + u * 0.20f);  // 胡刃
        blade.QuadTo(ax - u * 0.02f, ay + u * 0.10f, ax, ay + u * 0.04f);
        blade.Close();
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(ax, ay - u * 0.12f, ax + u * 0.20f, ay + u * 0.20f, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(blade, paint);
        paint.SetShader(null);
        GlowStroke(canvas, paint, blade, eg, UI.Dp(2.6f), UI.Dp(1f));
        // 斧头铆钉
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = GoldHi;
        canvas.DrawCircle(ax + u * 0.06f, ay + u * 0.04f, UI.Dp(2), paint);
        // 盾（干）
        float shx = cx - u * 0.18f;
        float shy = cy + u * 0.06f;
        float sr = u * 0.13f;
        var shield = new Path();
        shield.AddCircle(shx, shy, sr, Path.Direction.Cw);
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(shx - sr, shy - sr, shx + sr, shy + sr, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(shield, paint);
        paint.SetShader(null);
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(2.4f);
        paint.Color = GoldHi;
        canvas.DrawCircle(shx, shy, sr, paint);
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = eg;
        canvas.DrawCircle(shx, shy, sr * 0.42f, paint);
        paint.Color = Color.Argb(200, 255, 255, 255);
        canvas.DrawCircle(shx - sr * 0.15f, shy - sr * 0.15f, sr * 0.14f, paint);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：刻晴·雷楔双剑（交叉双剑）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawTwinSwords(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float strike = (phase * 0.6f) % 1f;
        for (int i = 0; i < 2; i++)
        {
            float ang = (i == 0 ? -24f : 24f) - strike * 16f;
            DrawSword(canvas, b, phase, paint, eg, eg, ang, false, 0.85f, 0.92f);
        }
        // 斩击雷弧
        if (strike < 0.2f)
        {
            float k = (0.2f - strike) / 0.2f;
            var slash = new Path();
            slash.MoveTo(cx - u * 0.26f, cy - u * 0.14f);
            slash.QuadTo(cx, cy + u * 0.04f, cx + u * 0.26f, cy - u * 0.14f);
            GlowStroke(canvas, paint, slash, eg, UI.Dp(3) * k, UI.Dp(1.2f) * k);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：巨剑 / 阔刃 / 蚩尤斧（复用 DrawSword 的 great 形态）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawGreatsword(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg, Color gem, bool cleaver)
    {
        DrawSword(canvas, b, phase, paint, eg, gem, 0, true, cleaver ? 1.5f : 1.1f, 1f);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：虚无·无相之刃（无柄无锷的悬浮裂隙刃，刃身即被吞噬的星空）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawVoidBlade(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        // 背后裂隙漩涡
        GlowDisc(canvas, paint, cx, cy, u * 0.30f, VoidLo, 55);
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.4f);
        for (int i = 0; i < 3; i++)
        {
            var sp = new Path();
            float rr = u * (0.10f + i * 0.07f);
            float a0 = phase * (1.2f - i * 0.3f) + i * 2f;
            sp.AddArc(new RectF(cx - rr, cy - rr, cx + rr, cy + rr), a0 * 180 / MathF.PI, 280);
            paint.Color = Color.Argb(90, VoidHi.R, VoidHi.G, VoidHi.B);
            canvas.DrawPath(sp, paint);
        }
        // 悬浮刃（无握柄，竖直叶片状）
        float tipY = cy - u * 0.40f;
        float botY = cy + u * 0.34f;
        float bw = u * 0.07f;
        var blade = new Path();
        blade.MoveTo(cx, tipY);
        blade.QuadTo(cx + bw * 1.8f, cy - u * 0.10f, cx + bw * 0.7f, botY);
        blade.LineTo(cx - bw * 0.7f, botY);
        blade.QuadTo(cx - bw * 1.8f, cy - u * 0.10f, cx, tipY);
        blade.Close();
        // 星空渐变（紫→黑→紫）
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - bw * 2, tipY, cx + bw * 2, botY,
            UI.ColorLongs(VoidHi, VoidLo, VoidHi), new[] { 0f, 0.5f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(blade, paint);
        paint.SetShader(null);
        // 刃内星点
        paint.SetStyle(Paint.Style.Fill);
        for (int i = 0; i < 6; i++)
        {
            var t = (phase * 0.3f + i * 0.17f) % 1f;
            float px = cx + (i % 2 == 0 ? 1 : -1) * bw * (0.4f + 0.4f * MathF.Sin(phase + i));
            float py = tipY + t * (botY - tipY);
            paint.Color = Color.Argb((int)(200 * (1 - t)), 230, 220, 255);
            canvas.DrawCircle(px, py, UI.Dp(1.4f), paint);
        }
        // 刃缘虚空流光
        GlowStroke(canvas, paint, blade, VoidHi, UI.Dp(3.2f), UI.Dp(1.3f));
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：虎爪 / 震爪（拳套 + 三利爪）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawFist(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        // 护腕
        float cuffY = cy + u * 0.10f;
        var cuff = new Path();
        cuff.AddRoundRect(new RectF(cx - u * 0.13f, cuffY, cx + u * 0.13f, cy + u * 0.30f), UI.Dp(4), UI.Dp(4), Path.Direction.Cw);
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - u * 0.13f, 0, cx + u * 0.13f, 0, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(cuff, paint);
        paint.SetShader(null);
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.6f);
        paint.Color = GoldHi;
        canvas.DrawRoundRect(new RectF(cx - u * 0.13f, cuffY, cx + u * 0.13f, cy + u * 0.30f), UI.Dp(4), UI.Dp(4), paint);
        // 三利爪
        for (int i = -1; i <= 1; i++)
        {
            float sx = cx + i * u * 0.09f;
            float topY = cy - u * 0.28f;
            float botY = cuffY + UI.Dp(4);
            var claw = new Path();
            claw.MoveTo(sx - UI.Dp(3), topY);
            claw.QuadTo(sx + UI.Dp(5), cy - u * 0.05f, sx - UI.Dp(2), botY);
            claw.LineTo(sx + UI.Dp(2.5f), botY);
            claw.QuadTo(sx + UI.Dp(9), cy - u * 0.05f, sx + UI.Dp(3), topY);
            claw.Close();
            paint.SetStyle(Paint.Style.Fill);
            paint.SetShader(new LinearGradient(sx - UI.Dp(3), topY, sx + UI.Dp(3), botY, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
            canvas.DrawPath(claw, paint);
            paint.SetShader(null);
            GlowStroke(canvas, paint, claw, eg, UI.Dp(2.2f), UI.Dp(0.8f));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：风刃 / 飘带（弯刀 + 风痕）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawSaber(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg, bool projectile)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        // 弯刀刃（弧刃）
        float topY = cy - u * 0.34f;
        float botY = cy + u * 0.10f;
        var blade = new Path();
        blade.MoveTo(cx - u * 0.04f, topY);
        blade.QuadTo(cx + u * 0.26f, cy - u * 0.04f, cx - u * 0.02f, botY); // 外弧
        blade.LineTo(cx - u * 0.12f, botY);
        blade.QuadTo(cx + u * 0.10f, cy - u * 0.04f, cx - u * 0.10f, topY); // 内弧
        blade.Close();
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - u * 0.10f, topY, cx + u * 0.20f, botY, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(blade, paint);
        paint.SetShader(null);
        GlowStroke(canvas, paint, blade, eg, UI.Dp(2.6f), UI.Dp(1f));
        // 护手 + 握柄
        Hilt(canvas, paint, cx - u * 0.07f, botY + UI.Dp(2), cy + u * 0.20f, eg, 0.85f, phase);
        // 风痕
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.6f);
        for (int i = 0; i < 3; i++)
        {
            float yy = cy - u * 0.10f + i * u * 0.12f;
            paint.Color = Color.Argb((int)(120 + 60 * MathF.Sin(phase * 3 + i)), eg.R, eg.G, eg.B);
            var wp = new Path();
            wp.MoveTo(cx - u * 0.30f, yy);
            wp.QuadTo(cx - u * 0.05f, yy + UI.Dp(6), cx + u * 0.20f, yy - UI.Dp(4));
            canvas.DrawPath(wp, paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：匕首（细长短剑）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawDagger(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float topY = cy - u * 0.34f;
        float guardY = cy + u * 0.02f;
        float pommelY = cy + u * 0.16f;
        float bw = u * 0.040f;
        var blade = new Path();
        blade.MoveTo(cx, topY);
        blade.LineTo(cx + bw, guardY - u * 0.02f);
        blade.LineTo(cx + bw * 0.6f, guardY);
        blade.LineTo(cx - bw * 0.6f, guardY);
        blade.LineTo(cx - bw, guardY - u * 0.02f);
        blade.Close();
        Steel(canvas, paint, blade, cx - bw, topY, cx + bw, guardY);
        GlowStroke(canvas, paint, blade, eg, UI.Dp(2.4f), UI.Dp(0.9f));
        Hilt(canvas, paint, cx, guardY, pommelY, eg, 0.9f, phase);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：雷锤（战锤 + 电弧）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawWarhammer(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float handX = cx - u * 0.04f;
        float handY = cy - u * 0.06f;
        // 柄
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(5f);
        paint.StrokeCap = Paint.Cap.Round;
        paint.Color = GripHi;
        canvas.DrawLine(handX, handY, handX + u * 0.03f, cy + u * 0.36f, paint);
        // 锤头
        float hl = u * 0.22f;
        float hw = u * 0.11f;
        float hx = handX - hw;
        float hy = handY - u * 0.12f;
        var head = new Path();
        head.AddRoundRect(new RectF(hx, hy, hx + hw * 2, hy + hl), UI.Dp(5), UI.Dp(5), Path.Direction.Cw);
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(hx, hy, hx + hw * 2, hy, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(head, paint);
        paint.SetShader(null);
        GlowStroke(canvas, paint, head, eg, UI.Dp(2.4f), UI.Dp(1f));
        // 符文
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.4f);
        paint.Color = Color.Argb(200, eg.R, eg.G, eg.B);
        canvas.DrawLine(hx + hw * 0.5f, hy + hl * 0.3f, hx + hw * 1.5f, hy + hl * 0.3f, paint);
        canvas.DrawLine(hx + hw, hy + hl * 0.2f, hx + hw, hy + hl * 0.8f, paint);
        // 电弧
        paint.StrokeWidth = UI.Dp(1.4f);
        for (int i = 0; i < 4; i++)
        {
            float ax = hx + (hw * 2) * (i / 3f);
            paint.Color = Color.Argb((int)(150 + 60 * MathF.Sin(phase * 4 + i)), eg.R, eg.G, eg.B);
            canvas.DrawLine(ax, hy + hl, ax + (i % 2 == 0 ? UI.Dp(12) : -UI.Dp(12)), hy + hl + UI.Dp(16), paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：毒牙鞭（盘绕长鞭 + 毒牙尖）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawWhip(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        // 握柄
        float gripX = cx + u * 0.16f;
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(gripX - UI.Dp(4), 0, gripX + UI.Dp(4), 0, UI.ColorLongs(GripHi, GripLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawRoundRect(new RectF(gripX - UI.Dp(4), cy - u * 0.02f, gripX + UI.Dp(4), cy + u * 0.22f), UI.Dp(2), UI.Dp(2), paint);
        paint.SetShader(null);
        // 鞭身（盘绕曲线）
        var p = new Path();
        p.MoveTo(gripX, cy);
        for (int i = 1; i <= 9; i++)
        {
            p.QuadTo(
                gripX + i * u * 0.07f + MathF.Sin(phase * 4 + i) * UI.Dp(12),
                cy + MathF.Cos(phase * 3 + i) * UI.Dp(16),
                gripX + i * u * 0.08f,
                cy + (i % 2 == 0 ? u * 0.07f : -u * 0.07f));
        }
        GlowStroke(canvas, paint, p, eg, UI.Dp(2.6f), UI.Dp(1.1f));
        // 毒牙尖
        float tipX = gripX + 9 * u * 0.08f;
        float tipY = cy + (9 % 2 == 0 ? u * 0.07f : -u * 0.07f);
        var fang = new Path();
        fang.MoveTo(tipX, tipY);
        fang.LineTo(tipX + UI.Dp(7), tipY - UI.Dp(3));
        fang.LineTo(tipX + UI.Dp(2), tipY + UI.Dp(4));
        fang.Close();
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.Argb(235, 240, 245, 255);
        canvas.DrawPath(fang, paint);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：凤凰·炎翼戟（长柄 + 弯刃 + 火焰翼）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawGlaive(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        // 长柄
        float topY = cy - u * 0.40f;
        float botY = cy + u * 0.40f;
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - UI.Dp(3), 0, cx + UI.Dp(3), 0, UI.ColorLongs(GripHi, GripLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawRoundRect(new RectF(cx - UI.Dp(3), topY, cx + UI.Dp(3), botY), UI.Dp(1.5f), UI.Dp(1.5f), paint);
        paint.SetShader(null);
        // 弯刃（凤凰之喙）
        var blade = new Path();
        blade.MoveTo(cx, topY);
        blade.QuadTo(cx + u * 0.24f, topY + u * 0.06f, cx + u * 0.16f, topY + u * 0.20f);
        blade.QuadTo(cx + u * 0.06f, topY + u * 0.12f, cx, topY + u * 0.16f);
        blade.Close();
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx, topY, cx + u * 0.24f, topY + u * 0.20f, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(blade, paint);
        paint.SetShader(null);
        GlowStroke(canvas, paint, blade, eg, UI.Dp(2.6f), UI.Dp(1f));
        // 火焰翼
        float breathe = 0.5f + 0.5f * MathF.Sin(phase * 2.5f);
        for (int side = -1; side <= 1; side += 2)
        {
            var wing = new Path();
            wing.MoveTo(cx, topY + u * 0.04f);
            for (int i = 1; i <= 5; i++)
                wing.QuadTo(cx + side * (u * 0.14f + i * u * 0.06f), topY + u * 0.02f + (i % 2) * u * 0.08f,
                            cx + side * (u * 0.12f + i * u * 0.07f), topY + i * u * 0.05f);
            wing.LineTo(cx, topY + u * 0.04f);
            paint.SetStyle(Paint.Style.Fill);
            paint.Color = Color.Argb((int)(70 + 40 * breathe), 255, 170, 80);
            canvas.DrawPath(wing, paint);
            GlowStroke(canvas, paint, wing, eg, UI.Dp(2f), UI.Dp(0.8f));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：商羊·卜天星谶盘（星轨罗盘：外环刻度 + 旋转星点 + 卜辞光核）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawAstrolabe(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float r = u * 0.30f;
        // 外环
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(2.6f);
        paint.Color = GoldHi;
        canvas.DrawCircle(cx, cy, r, paint);
        paint.StrokeWidth = UI.Dp(1.4f);
        paint.Color = Color.Argb(180, GoldHi.R, GoldHi.G, GoldHi.B);
        canvas.DrawCircle(cx, cy, r * 0.82f, paint);
        // 刻度（星轨纹）
        paint.StrokeWidth = UI.Dp(1.2f);
        for (int i = 0; i < 36; i++)
        {
            var a = i * MathF.PI / 18;
            float r0 = r * 0.90f;
            float r1 = (i % 3 == 0) ? r * 1.0f : r * 0.95f;
            paint.Color = Color.Argb(150, GoldHi.R, GoldHi.G, GoldHi.B);
            canvas.DrawLine(cx + MathF.Cos(a) * r0, cy + MathF.Sin(a) * r0, cx + MathF.Cos(a) * r1, cy + MathF.Sin(a) * r1, paint);
        }
        // 旋转星点（内环）
        for (int i = 0; i < 6; i++)
        {
            var a = i * MathF.PI / 3 + phase * 0.6f;
            float sx = cx + MathF.Cos(a) * r * 0.62f;
            float sy = cy + MathF.Sin(a) * r * 0.62f;
            GlowDisc(canvas, paint, sx, sy, UI.Dp(4), eg, 150);
            paint.SetStyle(Paint.Style.Fill);
            paint.Color = Color.Argb(230, 255, 250, 240);
            canvas.DrawCircle(sx, sy, UI.Dp(1.8f), paint);
        }
        // 卜辞光核（中心元素光核 + 竖直卜辞纹）
        GlowDisc(canvas, paint, cx, cy, r * 0.5f, eg, 170);
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.Argb(235, 255, 252, 245);
        canvas.DrawCircle(cx, cy, r * 0.20f, paint);
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.4f);
        paint.Color = Color.Argb(210, eg.R, eg.G, eg.B);
        canvas.DrawLine(cx, cy - r * 0.30f, cx, cy + r * 0.30f, paint);
        canvas.DrawLine(cx - r * 0.16f, cy - r * 0.12f, cx + r * 0.16f, cy - r * 0.12f, paint);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：盾击 / 冲撞（圆盾 + 盾钉 + 纹章）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawShield(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float sr = u * 0.30f;
        var shield = new Path();
        shield.AddCircle(cx, cy, sr, Path.Direction.Cw);
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - sr, cy - sr, cx + sr, cy + sr, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(shield, paint);
        paint.SetShader(null);
        // 盾缘
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(3f);
        paint.Color = GoldHi;
        canvas.DrawCircle(cx, cy, sr, paint);
        // 内圈
        paint.StrokeWidth = UI.Dp(1.4f);
        paint.Color = Color.Argb(180, GoldHi.R, GoldHi.G, GoldHi.B);
        canvas.DrawCircle(cx, cy, sr * 0.72f, paint);
        // 中央纹章宝石
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = eg;
        canvas.DrawCircle(cx, cy, sr * 0.30f, paint);
        paint.Color = Color.Argb(200, 255, 255, 255);
        canvas.DrawCircle(cx - sr * 0.10f, cy - sr * 0.10f, sr * 0.10f, paint);
        // 盾钉
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = GoldHi;
        for (int i = 0; i < 8; i++)
        {
            var a = i * MathF.PI / 4;
            canvas.DrawCircle(cx + MathF.Cos(a) * sr * 0.86f, cy + MathF.Sin(a) * sr * 0.86f, UI.Dp(2.2f), paint);
        }
        // 冲击环
        float expand = (phase * 2) % 1f;
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(2f);
        paint.Color = Color.Argb((int)(180 * (1 - expand)), eg.R, eg.G, eg.B);
        canvas.DrawCircle(cx, cy, sr + expand * u * 0.16f, paint);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：再生炮（手持能量炮）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawCannon(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        // 炮管
        float bx = cx - u * 0.22f;
        float by = cy - u * 0.05f;
        var barrel = new Path();
        barrel.AddRoundRect(new RectF(bx, by, cx + u * 0.16f, cy + u * 0.07f), UI.Dp(4), UI.Dp(4), Path.Direction.Cw);
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(bx, by, bx, cy + u * 0.07f, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(barrel, paint);
        paint.SetShader(null);
        // 枪身/握把
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx + u * 0.10f, 0, cx + u * 0.22f, 0, UI.ColorLongs(DarkHi, DarkLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawRoundRect(new RectF(cx + u * 0.08f, cy - u * 0.02f, cx + u * 0.22f, cy + u * 0.16f), UI.Dp(3), UI.Dp(3), paint);
        paint.SetShader(null);
        // 炮口蓄能
        float muzzleX = bx;
        float flare = 150 + 105 * MathF.Sin(phase * 3);
        GlowDisc(canvas, paint, muzzleX, cy, UI.Dp(12), eg, (int)(flare * 0.5f));
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.Argb((int)flare, eg.R, eg.G, eg.B);
        canvas.DrawCircle(muzzleX, cy, UI.Dp(5 + 2 * MathF.Sin(phase * 3)), paint);
        // 能量环
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.6f);
        paint.Color = Color.Argb(180, eg.R, eg.G, eg.B);
        canvas.DrawCircle(cx + u * 0.06f, cy + u * 0.07f, u * 0.05f, paint);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：蜂群 / 地雷（毒刺虫群）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawSwarm(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        // 中心蜂巢
        GlowDisc(canvas, paint, cx, cy, u * 0.10f, eg, 70);
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.Argb(210, 60, 70, 90);
        canvas.DrawCircle(cx, cy, u * 0.05f, paint);
        // 飞虫（带毒刺）
        for (int i = 0; i < 7; i++)
        {
            float t = (phase * 0.5f + i * 0.14f) % 1f;
            float ang = i * 1.7f + phase * 0.6f;
            float dist = u * 0.10f + t * u * 0.22f;
            float x = cx + MathF.Cos(ang) * dist;
            float y = cy + MathF.Sin(ang) * dist;
            // 虫身
            paint.Color = Color.Argb((int)(230 * (1 - t * 0.4f)), 200, 210, 230);
            canvas.DrawCircle(x, y, UI.Dp(3), paint);
            // 毒刺
            float tx = x + MathF.Cos(ang) * UI.Dp(7);
            float ty = y + MathF.Sin(ang) * UI.Dp(7);
            LineGlow(canvas, paint, x, y, tx, ty, eg, 1.6f, 0.8f);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：三叉戟（三尖长戟）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawTrident(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float topY = cy - u * 0.36f;
        float botY = cy + u * 0.38f;
        // 杆
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - UI.Dp(3), 0, cx + UI.Dp(3), 0, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawRoundRect(new RectF(cx - UI.Dp(3), topY, cx + UI.Dp(3), botY), UI.Dp(1.5f), UI.Dp(1.5f), paint);
        paint.SetShader(null);
        // 三尖
        for (int i = -1; i <= 1; i++)
        {
            float tx = cx + i * u * 0.07f;
            var t = new Path();
            t.MoveTo(tx - UI.Dp(2), topY + u * 0.10f);
            t.LineTo(tx + UI.Dp(2), topY + u * 0.10f);
            t.LineTo(tx, topY);
            t.Close();
            paint.SetStyle(Paint.Style.Fill);
            paint.SetShader(new LinearGradient(tx, topY, tx, topY + u * 0.10f, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
            canvas.DrawPath(t, paint);
            paint.SetShader(null);
            GlowStroke(canvas, paint, t, eg, UI.Dp(2), UI.Dp(0.8f));
        }
        // 横档
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - u * 0.10f, 0, cx + u * 0.10f, 0, UI.ColorLongs(GoldHi, GoldLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawRoundRect(new RectF(cx - u * 0.10f, topY + u * 0.10f, cx + u * 0.10f, topY + u * 0.15f), UI.Dp(1.5f), UI.Dp(1.5f), paint);
        paint.SetShader(null);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：雷羽俯冲（雷枪 + 羽饰）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawThunderSpear(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float topY = cy - u * 0.40f;
        float botY = cy + u * 0.36f;
        // 枪杆
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - UI.Dp(2.6f), 0, cx + UI.Dp(2.6f), 0, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawRoundRect(new RectF(cx - UI.Dp(2.6f), topY, cx + UI.Dp(2.6f), botY), UI.Dp(1.3f), UI.Dp(1.3f), paint);
        paint.SetShader(null);
        // 枪尖
        var tip = new Path();
        tip.MoveTo(cx - UI.Dp(4), topY + u * 0.14f);
        tip.LineTo(cx + UI.Dp(4), topY + u * 0.14f);
        tip.LineTo(cx, topY);
        tip.Close();
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx, topY, cx, topY + u * 0.14f, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(tip, paint);
        paint.SetShader(null);
        GlowStroke(canvas, paint, tip, eg, UI.Dp(2.6f), UI.Dp(1f));
        // 羽饰（枪尾羽）
        for (int i = -1; i <= 1; i++)
        {
            float fx = cx + i * u * 0.05f;
            var feather = new Path();
            feather.MoveTo(fx, botY);
            feather.QuadTo(fx + i * u * 0.04f, botY + u * 0.06f, fx, botY + u * 0.14f);
            feather.QuadTo(fx - i * u * 0.02f, botY + u * 0.06f, fx, botY);
            feather.Close();
            paint.SetStyle(Paint.Style.Fill);
            paint.Color = Color.Argb(200, eg.R, eg.G, eg.B);
            canvas.DrawPath(feather, paint);
        }
        // 电弧
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.4f);
        for (int i = 0; i < 3; i++)
        {
            float ax = cx + (i - 1) * u * 0.06f;
            paint.Color = Color.Argb((int)(150 + 60 * MathF.Sin(phase * 4 + i)), eg.R, eg.G, eg.B);
            canvas.DrawLine(ax, topY + u * 0.16f, ax + (i == 1 ? 0 : (i == 0 ? UI.Dp(10) : -UI.Dp(10))), topY + u * 0.16f + UI.Dp(14), paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：饕餮·青铜巨鼎（三足两耳，鼎口吞噬虚空/毒火）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawBronzeCauldron(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        float bodyR = u * 0.20f;
        float bodyTop = cy - u * 0.14f;
        float bodyBot = cy + u * 0.16f;
        // 鼎腹（圆角矩形）
        var body = new Path();
        body.AddRoundRect(new RectF(cx - bodyR, bodyTop, cx + bodyR, bodyBot), UI.Dp(10), UI.Dp(10), Path.Direction.Cw);
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(cx - bodyR, bodyTop, cx + bodyR, bodyBot, UI.ColorLongs(BronzeHi, BronzeLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(body, paint);
        paint.SetShader(null);
        // 青铜纹（饕餮纹简化：两道横曲线）
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.6f);
        paint.Color = Color.Argb(150, 60, 40, 18);
        canvas.DrawLine(cx - bodyR * 0.7f, bodyTop + (bodyBot - bodyTop) * 0.4f, cx + bodyR * 0.7f, bodyTop + (bodyBot - bodyTop) * 0.4f, paint);
        canvas.DrawLine(cx - bodyR * 0.7f, bodyTop + (bodyBot - bodyTop) * 0.62f, cx + bodyR * 0.7f, bodyTop + (bodyBot - bodyTop) * 0.62f, paint);
        // 两耳
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = BronzeHi;
        canvas.DrawCircle(cx - bodyR - UI.Dp(2), bodyTop + UI.Dp(2), UI.Dp(4), paint);
        canvas.DrawCircle(cx + bodyR + UI.Dp(2), bodyTop + UI.Dp(2), UI.Dp(4), paint);
        // 三足
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(4.5f);
        paint.StrokeCap = Paint.Cap.Round;
        paint.Color = BronzeLo;
        canvas.DrawLine(cx - bodyR * 0.6f, bodyBot, cx - bodyR * 0.7f, bodyBot + u * 0.16f, paint);
        canvas.DrawLine(cx, bodyBot, cx, bodyBot + u * 0.18f, paint);
        canvas.DrawLine(cx + bodyR * 0.6f, bodyBot, cx + bodyR * 0.7f, bodyBot + u * 0.16f, paint);
        // 鼎口吞噬：虚空/毒火辉光
        GlowDisc(canvas, paint, cx, bodyTop, bodyR * 1.1f, eg, 120);
        paint.SetStyle(Paint.Style.Fill);
        for (int i = 0; i < 5; i++)
        {
            var a = i * 2 * MathF.PI / 5 + phase * 0.8f;
            float fx = cx + MathF.Cos(a) * bodyR * 0.8f;
            float fy = bodyTop - u * 0.02f + MathF.Sin(phase * 3 + i) * UI.Dp(4);
            paint.Color = Color.Argb(180, eg.R, eg.G, eg.B);
            canvas.DrawCircle(fx, fy, UI.Dp(2.4f), paint);
        }
        paint.Color = Color.Argb(200, 30, 10, 50);
        canvas.DrawOval(new RectF(cx - bodyR * 0.8f, bodyTop - u * 0.04f, cx + bodyR * 0.8f, bodyTop + u * 0.05f), paint);
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器：精卫·衔石填海（神鸟衔发光石 + 环绕飞石）
    // ═══════════════════════════════════════════════════════════════
    private static void DrawStoneBeak(Canvas canvas, RectF b, float phase, Paint paint, Color rc, Color eg)
    {
        var u = Wu(b); var cx = b.CenterX(); var cy = b.CenterY();
        // 神鸟首剪影（侧脸）
        float headR = u * 0.13f;
        float headX = cx - u * 0.04f;
        float headY = cy - u * 0.16f;
        var head = new Path();
        head.AddCircle(headX, headY, headR, Path.Direction.Cw);
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(new LinearGradient(headX - headR, headY - headR, headX + headR, headY + headR, UI.ColorLongs(SteelHi, SteelLo), new[] { 0f, 1f }, Shader.TileMode.Clamp));
        canvas.DrawPath(head, paint);
        paint.SetShader(null);
        // 喙（三角，指向右下衔石）
        float beakTipX = headX + u * 0.20f;
        float beakTipY = headY + u * 0.04f;
        var beak = new Path();
        beak.MoveTo(headX + headR * 0.4f, headY - u * 0.03f);
        beak.LineTo(beakTipX, beakTipY);
        beak.LineTo(headX + headR * 0.5f, headY + u * 0.05f);
        beak.Close();
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = GoldHi;
        canvas.DrawPath(beak, paint);
        GlowStroke(canvas, paint, beak, eg, UI.Dp(1.8f), UI.Dp(0.8f));
        // 衔石（发光）
        GlowDisc(canvas, paint, beakTipX, beakTipY, UI.Dp(10), eg, 170);
        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.Argb(235, 200, 220, 240);
        canvas.DrawCircle(beakTipX, beakTipY, UI.Dp(4.5f), paint);
        paint.Color = Color.Argb(160, eg.R, eg.G, eg.B);
        canvas.DrawCircle(beakTipX, beakTipY, UI.Dp(2.4f), paint);
        // 环绕飞石
        for (int i = 0; i < 4; i++)
        {
            var a = i * MathF.PI / 2 + phase * 0.7f;
            float sx = cx + MathF.Cos(a) * u * 0.26f;
            float sy = cy + MathF.Sin(a) * u * 0.24f;
            GlowDisc(canvas, paint, sx, sy, UI.Dp(5), eg, 120);
            paint.SetStyle(Paint.Style.Fill);
            paint.Color = Color.Argb(210, 180, 190, 210);
            canvas.DrawCircle(sx, sy, UI.Dp(2.6f), paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 氛围：火焰
    // ═══════════════════════════════════════════════════════════════
    private static void DrawFlameAmbient(Canvas canvas, RectF bounds, float phase, Paint paint, Color elementGlow, float intensity)
    {
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(null);
        var rng = new Random(7);
        int count = (int)(18 * intensity);
        for (int i = 0; i < count; i++)
        {
            var t = (phase * (0.3f + (i % 5) * 0.1f) + i * 0.7f) % 1f;
            var x = bounds.Left + (float)(rng.NextDouble() * bounds.Width());
            var y = bounds.Bottom - t * bounds.Height() * 1.2f;
            var size = UI.Dp(2 + (i % 4));
            var alpha = (int)(220 * (1 - t) * (0.5f + 0.5f * MathF.Sin(phase * 4 + i)));
            if (alpha <= 0) continue;
            paint.Color = Color.Argb(alpha, elementGlow.R, elementGlow.G, elementGlow.B);
            canvas.DrawCircle(x, y, size, paint);
        }
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1);
        for (int i = 0; i < 5; i++)
        {
            var y = bounds.Top + bounds.Height() * (0.3f + i * 0.12f);
            var wave = MathF.Sin(phase * 2 + i) * UI.Dp(6);
            paint.Color = Color.Argb((int)(30 * intensity), elementGlow.R, elementGlow.G, elementGlow.B);
            canvas.DrawLine(bounds.Left, y + wave, bounds.Right, y - wave, paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 氛围：雷电
    // ═══════════════════════════════════════════════════════════════
    private static void DrawLightningAmbient(Canvas canvas, RectF bounds, float phase, Paint paint, Color elementGlow)
    {
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1.5f);
        paint.SetShader(null);
        var rng = new Random(11);
        for (int i = 0; i < 6; i++)
        {
            var flash = MathF.Sin(phase * 6 + i * 1.3f);
            if (flash < 0.6f) continue;
            var alpha = (int)(255 * (flash - 0.6f) / 0.4f);
            paint.Color = Color.Argb(alpha, elementGlow.R, elementGlow.G, elementGlow.B);
            var x = bounds.Left + (float)(rng.NextDouble() * bounds.Width());
            var y = bounds.Top;
            var path = new Path();
            path.MoveTo(x, y);
            while (y < bounds.Bottom)
            {
                x += (float)(rng.NextDouble() * UI.Dp(24) - UI.Dp(12));
                y += UI.Dp(18);
                path.LineTo(x, y);
            }
            canvas.DrawPath(path, paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 氛围：金属
    // ═══════════════════════════════════════════════════════════════
    private static void DrawMetalAmbient(Canvas canvas, RectF bounds, float phase, Paint paint, Color elementGlow, bool heavy)
    {
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(null);
        var rng = new Random(13);
        int count = heavy ? 22 : 12;
        for (int i = 0; i < count; i++)
        {
            var t = (phase * 0.15f + i * 0.13f) % 1f;
            var x = bounds.Left + (float)(rng.NextDouble() * bounds.Width());
            var y = bounds.Top + (float)(rng.NextDouble() * bounds.Height());
            var rot = phase * 60 + i * 30;
            var size = UI.Dp(2 + (i % 3));
            paint.Color = Color.Argb((int)(160 * (1 - t * 0.5f)), elementGlow.R, elementGlow.G, elementGlow.B);
            canvas.Save();
            canvas.Rotate(rot, x, y);
            if (i % 3 == 0)
                canvas.DrawRect(x - size, y - size / 2f, x + size, y + size / 2f, paint);
            else
                canvas.DrawCircle(x, y, size, paint);
            canvas.Restore();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 氛围：风
    // ═══════════════════════════════════════════════════════════════
    private static void DrawWindAmbient(Canvas canvas, RectF bounds, float phase, Paint paint, Color elementGlow, bool petal)
    {
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(null);
        var rng = new Random(17);
        for (int i = 0; i < 20; i++)
        {
            var t = (phase * 0.25f + i * 0.11f) % 1f;
            var y = bounds.Top + (float)(rng.NextDouble() * bounds.Height());
            var x = bounds.Left + t * bounds.Width() + MathF.Sin(phase * 3 + i) * UI.Dp(20);
            var alpha = (int)(160 * (1 - t) * (0.5f + 0.5f * MathF.Sin(phase * 2 + i)));
            if (alpha <= 0) continue;
            paint.Color = Color.Argb(alpha, elementGlow.R, elementGlow.G, elementGlow.B);
            canvas.Save();
            canvas.Rotate(phase * 90 + i * 20, x, y);
            if (petal)
                canvas.DrawOval(new RectF(x - UI.Dp(3), y - UI.Dp(1.5f), x + UI.Dp(3), y + UI.Dp(1.5f)), paint);
            else
                canvas.DrawCircle(x, y, UI.Dp(1.5f), paint);
            canvas.Restore();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 氛围：暗影
    // ═══════════════════════════════════════════════════════════════
    private static void DrawShadowAmbient(Canvas canvas, RectF bounds, float phase, Paint paint, Color elementGlow)
    {
        paint.SetStyle(Paint.Style.Fill);
        var rng = new Random(19);
        for (int i = 0; i < 16; i++)
        {
            var t = (phase * 0.2f + i * 0.14f) % 1f;
            var x = bounds.Left + (float)(rng.NextDouble() * bounds.Width());
            var y = bounds.Top + (float)(rng.NextDouble() * bounds.Height());
            var size = UI.Dp(8 + (i % 6)) * (0.5f + 0.5f * MathF.Sin(phase + i));
            if (size <= 0) continue;
            paint.Color = Color.Argb((int)(60 * (1 - t)), 0, 0, 0);
            canvas.DrawCircle(x, y, size, paint);
        }
        paint.Color = Color.Argb((int)(80 + 40 * MathF.Sin(phase * 2)), elementGlow.R, elementGlow.G, elementGlow.B);
        for (int i = 0; i < 6; i++)
        {
            var x = bounds.Left + bounds.Width() * (0.2f + (i % 5) * 0.15f);
            var y = bounds.Top + bounds.Height() * (0.3f + 0.1f * MathF.Sin(phase * 2 + i));
            canvas.DrawCircle(x, y, UI.Dp(2), paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 氛围：虚空
    // ═══════════════════════════════════════════════════════════════
    private static void DrawVoidAmbient(Canvas canvas, RectF bounds, float phase, Paint paint, Color elementGlow)
    {
        paint.SetStyle(Paint.Style.Fill);
        var cx = bounds.CenterX();
        var cy = bounds.CenterY();
        var radius = Math.Min(bounds.Width(), bounds.Height()) * 0.35f;
        if (radius <= 0) return;
        paint.SetShader(new SweepGradient(cx, cy,
            new[] { Color.Argb(0, 0, 0, 0).ToArgb(), Color.Argb((int)(60 + 30 * MathF.Sin(phase * 3)), elementGlow.R, elementGlow.G, elementGlow.B).ToArgb(), Color.Argb(0, 0, 0, 0).ToArgb() },
            new[] { 0f, 0.5f, 1f }));
        canvas.Save();
        canvas.Rotate(phase * 15, cx, cy);
        canvas.DrawCircle(cx, cy, radius, paint);
        canvas.Restore();
        paint.SetShader(null);
        var rng = new Random(23);
        for (int i = 0; i < 18; i++)
        {
            var t = (phase * 0.18f + i * 0.16f) % 1f;
            var angle = i * 0.7f + phase;
            var dist = radius * (0.3f + t * 0.8f);
            var x = cx + MathF.Cos(angle) * dist;
            var y = cy + MathF.Sin(angle) * dist;
            paint.Color = Color.Argb((int)(160 * (1 - t)), elementGlow.R, elementGlow.G, elementGlow.B);
            canvas.DrawCircle(x, y, UI.Dp(1.5f), paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 氛围：大地
    // ═══════════════════════════════════════════════════════════════
    private static void DrawEarthAmbient(Canvas canvas, RectF bounds, float phase, Paint paint, Color elementGlow)
    {
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(null);
        var rng = new Random(29);
        for (int i = 0; i < 14; i++)
        {
            var x = bounds.Left + (float)(rng.NextDouble() * bounds.Width());
            var grow = (phase * 0.3f + i * 0.2f) % 1f;
            var h = UI.Dp(4 + (i % 8)) * grow;
            paint.Color = Color.Argb((int)(120 * grow), elementGlow.R, elementGlow.G, elementGlow.B);
            canvas.DrawRect(x - UI.Dp(1), bounds.Bottom - h, x + UI.Dp(1), bounds.Bottom, paint);
        }
        for (int i = 0; i < 8; i++)
        {
            var t = (phase * 0.2f + i * 0.25f) % 1f;
            var x = bounds.Left + (float)(rng.NextDouble() * bounds.Width());
            var y = bounds.Bottom - t * bounds.Height() * 0.6f;
            paint.Color = Color.Argb((int)(100 * (1 - t)), elementGlow.R, elementGlow.G, elementGlow.B);
            canvas.DrawCircle(x, y, UI.Dp(2), paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 氛围：水
    // ═══════════════════════════════════════════════════════════════
    private static void DrawWaterAmbient(Canvas canvas, RectF bounds, float phase, Paint paint, Color elementGlow)
    {
        paint.SetStyle(Paint.Style.Fill);
        paint.SetShader(null);
        var rng = new Random(31);
        for (int i = 0; i < 22; i++)
        {
            var t = (phase * 0.22f + i * 0.13f) % 1f;
            var x = bounds.Left + (float)(rng.NextDouble() * bounds.Width());
            var y = bounds.Bottom - t * bounds.Height() * 1.1f;
            var size = UI.Dp(1.5f + (i % 3));
            var alpha = (int)(140 * (1 - t) * (0.5f + 0.5f * MathF.Sin(phase * 3 + i)));
            if (alpha <= 0) continue;
            paint.Color = Color.Argb(alpha, elementGlow.R, elementGlow.G, elementGlow.B);
            canvas.DrawCircle(x, y, size, paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 氛围：星象
    // ═══════════════════════════════════════════════════════════════
    private static void DrawStarAmbient(Canvas canvas, RectF bounds, float phase, Paint paint, Color elementGlow)
    {
        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = UI.Dp(1);
        paint.SetShader(null);
        var cx = bounds.CenterX();
        var cy = bounds.CenterY();
        var rng = new Random(37);
        for (int i = 0; i < 5; i++)
        {
            var radius = UI.Dp(40 + i * 28);
            var alpha = (int)(80 + 40 * MathF.Sin(phase * 1.5f + i));
            paint.Color = Color.Argb(alpha, elementGlow.R, elementGlow.G, elementGlow.B);
            canvas.Save();
            canvas.Rotate(-phase * 8 + i * 30, cx, cy);
            canvas.DrawCircle(cx, cy, radius, paint);
            canvas.Restore();
        }
        paint.SetStyle(Paint.Style.Fill);
        for (int i = 0; i < 24; i++)
        {
            var sx = bounds.Left + (float)(rng.NextDouble() * bounds.Width());
            var sy = bounds.Top + (float)(rng.NextDouble() * bounds.Height());
            var twinkle = 0.5f + 0.5f * MathF.Sin(phase * 4 + i);
            paint.Color = Color.Argb((int)(200 * twinkle), 255, 255, 255);
            canvas.DrawCircle(sx, sy, UI.Dp(1), paint);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 武器 PNG 资源（AI 生成的概念图，按 WeaponVfx 命名，放 assets/weapons/）
    // ═══════════════════════════════════════════════════════════════
    private const int MaxWeaponBitmaps = 16; // 目前仅 SSR/UR 共 14 张，留 2 张余量
    private static readonly Dictionary<string, Bitmap?> _weaponBitmapCache = new();
    private static readonly LinkedList<string> _weaponLru = new();

    /// <summary>按 WeaponVfx 加载武器概念图 PNG（assets/weapons/&lt;wv&gt;.png）。
    /// 结果（含 null）会缓存；找不到资源时返回 null，调用方应回退到 DrawWeapon 的 Canvas 几何绘制。</summary>
    public static Bitmap? GetWeaponBitmap(Context? ctx, string wv)
    {
        if (ctx == null || string.IsNullOrEmpty(wv)) return null;
        lock (_weaponBitmapCache)
        {
            if (_weaponBitmapCache.TryGetValue(wv, out var cached))
            {
                _weaponLru.Remove(wv);
                _weaponLru.AddFirst(wv);
                return cached != null && cached.IsRecycled ? null : cached;
            }
        }
        Bitmap? bmp = null;
        try
        {
            using var stream = ctx.Assets!.Open("weapons/" + wv + ".png");
            var opts = new BitmapFactory.Options { InSampleSize = 2 };
            bmp = BitmapFactory.DecodeStream(stream, null, opts);
        }
        catch
        {
            bmp = null;
        }
        lock (_weaponBitmapCache)
        {
            _weaponBitmapCache[wv] = bmp;
            _weaponLru.Remove(wv);
            _weaponLru.AddFirst(wv);
            // #39: LRU 淘汰，防止 native 内存无界增长。不主动 Recycle：视图可能仍在绘制。
            while (_weaponLru.Count > MaxWeaponBitmaps)
            {
                var oldest = _weaponLru.Last!.Value;
                _weaponLru.RemoveLast();
                _weaponBitmapCache.Remove(oldest);
            }
        }
        return bmp;
    }

    /// <summary>系统内存吃紧时（Activity.OnTrimMemory）释放武器图缓存。</summary>
    public static void TrimWeaponCache()
    {
        lock (_weaponBitmapCache)
        {
            _weaponBitmapCache.Clear();
            _weaponLru.Clear();
        }
    }

    /// <summary>仅绘制武器背后的炫酷能量层（粒子/极光环/光台/冲击波/流动光弧），供 PNG 武器图叠加其上。</summary>
    public static void DrawWeaponBackdrop(Canvas canvas, Paint paint, RectF bounds, float phase, Color glow, string element = "", string rarity = "")
    {
        if (bounds.Width() <= 0 || bounds.Height() <= 0) return;
        AddAura(canvas, paint, bounds.CenterX(), bounds.CenterY(), Wu(bounds), glow, phase, element, rarity);
    }
}
