using Android.Graphics;
using Milan.Data;

namespace Milan.Maui;

/// <summary>
/// Three-world visual language system. Each world has its own color palette,
/// decoration style, and atmosphere. Implements the "mixed rendering" promise:
/// Shinwa = cel-shaded ink-wash, Aether = semi-realistic dark, Ironveil = hard-surface mechanical.
/// </summary>
public static class WorldTheme
{
    /// <summary>
    /// Get the theme for a world type.
    /// </summary>
    public static WorldPalette For(string world) => world switch
    {
        "Shinwa" => Shinwa,
        "Aether" => Aether,
        "Ironveil" => Ironveil,
        _ => Shinwa
    };

    public static WorldPalette For(WorldType world) => world switch
    {
        WorldType.Shinwa => Shinwa,
        WorldType.Aether => Aether,
        WorldType.Ironveil => Ironveil,
        _ => Shinwa
    };

    // ── Shinwa 神话界: Cel-shaded ink-wash ──
    public static readonly WorldPalette Shinwa = new()
    {
        Primary = Color.ParseColor("#C41E3A"),      // 朱砂红
        Secondary = Color.ParseColor("#D4AF37"),    // 金箔
        Accent = Color.ParseColor("#1E90FF"),       // 石青
        Background = Color.ParseColor("#0d0d1a"),
        Surface = Color.ParseColor("#1a1520"),
        TextPrimary = Color.ParseColor("#F5F0E8"),  // 玉白
        TextSecondary = Color.ParseColor("#b8a080"),
        Glow = Color.ParseColor("#FFD700"),
        ParticleColor = Color.ParseColor("#FFD700"),
        Stroke = Color.ParseColor("#3a2a1a"),
        DecorationStyle = WorldDecorationStyle.InkBrush,
        BackgroundStyle = WorldBackground.Mythical
    };

    // ── Aether 虚空界: Semi-realistic dark cosmic ──
    public static readonly WorldPalette Aether = new()
    {
        Primary = Color.ParseColor("#2D1B69"),      // 虚空紫
        Secondary = Color.ParseColor("#4A90D9"),    // 星云蓝
        Accent = Color.ParseColor("#8B0000"),       // 暗红
        Background = Color.ParseColor("#0A0A14"),
        Surface = Color.ParseColor("#12122a"),
        TextPrimary = Color.ParseColor("#E8E0F0"),  // 幽灵白
        TextSecondary = Color.ParseColor("#9090c0"),
        Glow = Color.ParseColor("#6A0DAD"),
        ParticleColor = Color.ParseColor("#4A90D9"),
        Stroke = Color.ParseColor("#2D1B69"),
        DecorationStyle = WorldDecorationStyle.EnergyLines,
        BackgroundStyle = WorldBackground.Cosmic
    };

    // ── Ironveil 铁幕界: Hard-surface mechanical ──
    public static readonly WorldPalette Ironveil = new()
    {
        Primary = Color.ParseColor("#4A4A5A"),      // 钢铁灰
        Secondary = Color.ParseColor("#B87333"),    // 铜
        Accent = Color.ParseColor("#00BFFF"),       // 电光蓝
        Background = Color.ParseColor("#1a1a20"),
        Surface = Color.ParseColor("#252530"),
        TextPrimary = Color.ParseColor("#E0E0E0"),
        TextSecondary = Color.ParseColor("#8888a0"),
        Glow = Color.ParseColor("#FF6B00"),
        ParticleColor = Color.ParseColor("#FF6B00"),
        Stroke = Color.ParseColor("#4A4A5A"),
        DecorationStyle = WorldDecorationStyle.Rivets,
        BackgroundStyle = WorldBackground.Industrial
    };
}

/// <summary>
/// Color palette for a world.
/// </summary>
public class WorldPalette
{
    public Color Primary { get; set; }
    public Color Secondary { get; set; }
    public Color Accent { get; set; }
    public Color Background { get; set; }
    public Color Surface { get; set; }
    public Color TextPrimary { get; set; }
    public Color TextSecondary { get; set; }
    public Color Glow { get; set; }
    public Color ParticleColor { get; set; }
    public Color Stroke { get; set; }
    public WorldDecorationStyle DecorationStyle { get; set; }
    public WorldBackground BackgroundStyle { get; set; }
}

public enum WorldDecorationStyle
{
    InkBrush,       // Shinwa: flowing brush strokes
    EnergyLines,    // Aether: sharp glowing energy lines
    Rivets          // Ironveil: mechanical rivets and panels
}

public enum WorldBackground
{
    Mythical,       // Shinwa: clouds, mountains, lanterns
    Cosmic,         // Aether: nebula, stars, void
    Industrial      // Ironveil: gears, pipes, metal
}
