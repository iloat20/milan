using Android.Content;
using Android.Content.PM;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Runtime;
using Android.Util;
using Android.Views;
using Android.Widget;
using Java.Lang;

namespace Milan.Maui;

/// <summary>
/// Obsidian &amp; Gold 全局底部导航栏（5 项）。玻璃底座 + 选中项金色高亮面板 +
/// 顶部金色指示线 + 按压缩放反馈。导航目标由宿主 Activity 通过 onSelect 决定，
/// 组件自身不依赖任何具体 Activity，避免目标页未建时编译失败。
/// </summary>
public sealed class GameNavBar : LinearLayout
{
    public enum NavItem { Home, Gacha, Deck, Shop, Settings }

    private static readonly (NavItem item, string glyph, string label)[] Items =
    {
        (NavItem.Home, "◈", "主页"),
        (NavItem.Gacha, "✦", "抽卡"),
        (NavItem.Deck, "❖", "卡组"),
        (NavItem.Shop, "⬢", "商店"),
        (NavItem.Settings, "⚙", "设置"),
    };

    private readonly NavItem _active;
    private readonly Action<NavItem> _onSelect;

    /// <summary>导航按钮固定高度：保证每格是等大的长方形，文字有稳定的居中基准。</summary>
    private const int CellHeightDp = 56;

    public GameNavBar(Context context, NavItem active, Action<NavItem> onSelect) : base(context)
    {
        _active = active;
        _onSelect = onSelect;
        Orientation = Orientation.Horizontal;
        Background = UI.GlassPanel(0, gold: false);
        SetGravity(GravityFlags.CenterVertical);
        SetPadding(UI.Dp(6), UI.Dp(6), UI.Dp(6), UI.Dp(8));
        foreach (var (item, glyph, label) in Items) AddItem(item, glyph, label);
    }

    private void AddItem(NavItem item, string glyph, string label)
    {
        bool sel = item == _active;

        // 按钮本体：等宽（weight=1）+ 固定高，是一个规整长方形
        var cell = new FrameLayout(Context);
        var cellLp = new LinearLayout.LayoutParams(0, UI.Dp(CellHeightDp), 1f);
        cellLp.SetMargins(UI.Dp(3), 0, UI.Dp(3), 0);
        cell.LayoutParameters = cellLp;
        cell.Clickable = true;
        cell.Focusable = true;
        if (sel) cell.Background = UI.GlassPanel(14, gold: true, nested: true);

        // 图标 + 文字整体在长方形内水平垂直居中。
        // 关键：TextView 自身必须 MatchParent + Gravity.Center，
        // 只靠父容器的 SetGravity 在 WrapContent 子项上并不总能对齐。
        var stack = new LinearLayout(Context) { Orientation = Orientation.Vertical };
        stack.LayoutParameters = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
        { Gravity = GravityFlags.Center };
        stack.SetGravity(GravityFlags.Center);

        var icon = UI.Text(glyph, 20, sel ? AppTheme.Gold : Color.Argb(180, AppTheme.Frost.R, AppTheme.Frost.G, AppTheme.Frost.B), bold: true);
        icon.Gravity = GravityFlags.Center;
        icon.LayoutParameters = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        icon.SetIncludeFontPadding(false);
        stack.AddView(icon);

        var lab = UI.Text(label, 10,
            sel ? AppTheme.Gold : AppTheme.Text2, bold: sel);
        lab.Gravity = GravityFlags.Center;
        lab.LayoutParameters = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        lab.SetIncludeFontPadding(false);
        lab.SetPadding(0, UI.Dp(3), 0, 0);
        stack.AddView(lab);

        cell.AddView(stack);

        // 顶部金色指示线：绝对定位到长方形顶边，不参与内容流，
        // 因此不会把图标/文字挤下去破坏居中。
        if (sel)
        {
            var ind = new View(Context);
            ind.LayoutParameters = new FrameLayout.LayoutParams(UI.Dp(24), UI.Dp(2))
            { Gravity = GravityFlags.Top | GravityFlags.CenterHorizontal, TopMargin = UI.Dp(4) };
            ind.Background = (GlassLine());
            cell.AddView(ind);
        }

        UI.TapFeedback(cell, () => _onSelect(item));
        AddView(cell);
    }

    /// <summary>选中指示线：熔金中心高亮、两端渐隐（金仅作选中态点睛）。</summary>
    private static Drawable GlassLine()
    {
        var c = AppTheme.Gold;
        var gd = new GradientDrawable(
            GradientDrawable.Orientation.LeftRight,
            new[] {
                Color.Argb(0, c.R, c.G, c.B).ToArgb(),
                Color.Argb(255, c.R, c.G, c.B).ToArgb(),
                Color.Argb(0, c.R, c.G, c.B).ToArgb()
            });
        return gd;
    }

}

/// <summary>统一顶栏（子页面用）：返回箭头 + 标题 + 资源胶囊。主页不需要。</summary>
public static class AppChrome
{
    public static LinearLayout AppTopBar(Context context, string title, Action onBack, bool showResource = true)
    {
        var bar = new LinearLayout(context) { Orientation = Orientation.Horizontal };
        bar.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        bar.SetPadding(UI.Dp(14), UI.Dp(10), UI.Dp(14), UI.Dp(6));
        bar.SetGravity(GravityFlags.CenterVertical);

        var back = UI.Text("‹", 24, AppTheme.Gold, bold: true);
        back.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent);
        back.SetPadding(0, 0, UI.Dp(8), 0);
        back.Clickable = true; back.Focusable = true;
        back.Click += (_, _) => onBack();
        bar.AddView(back);

        var t = UI.Text(title, 20, AppTheme.Text1, bold: true);
        t.LetterSpacing = 0.04f;
        t.SetShadowLayer(8, 0, 2, Color.Argb(150, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B));
        bar.AddView(t);

        if (showResource)
        {
            var spacer = new View(context) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
            bar.AddView(spacer);
            bar.AddView(ResourceBar(context));
        }
        return bar;
    }

    /// <summary>资源胶囊：星尘（金）+ 钻石（青）两项。供主页与子页面复用。</summary>
    public static LinearLayout ResourceBar(Context context) => ResourceBar(context, out _, out _);

    /// <summary>同 <see cref="ResourceBar(Context)"/>，额外回传两个数值 TextView 供 OnResume 刷新。</summary>
    public static LinearLayout ResourceBar(Context context, out TextView dust, out TextView gems)
    {
        var pill = new LinearLayout(context) { Orientation = Orientation.Horizontal };
        pill.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent);
        pill.Background = UI.GlassPanel(16, gold: false);
        pill.SetPadding(UI.Dp(12), UI.Dp(7), UI.Dp(12), UI.Dp(7));
        pill.SetGravity(GravityFlags.CenterVertical);

        pill.AddView(Chip(context, "✦", GameState.Currency.ToString("N0"), AppTheme.Gold, out dust));
        var gap = new View(context) { LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(10), 0) };
        pill.AddView(gap);
        pill.AddView(Chip(context, "❖", GameState.Service.SaveData.HardCurrency.ToString("N0"), AppTheme.Frost, out gems));
        return pill;
    }

    private static LinearLayout Chip(Context context, string glyph, string value, Color color, out TextView valueTv)
    {
        var row = new LinearLayout(context) { Orientation = Orientation.Horizontal };
        row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent);
        row.SetGravity(GravityFlags.CenterVertical);
        var g = UI.Text(glyph, 14, color, bold: true);
        g.SetPadding(0, 0, UI.Dp(4), 0);
        var v = UI.Text(value, 14, AppTheme.Text1, bold: true);
        UI.Tabular(v);
        row.AddView(g);
        row.AddView(v);
        valueTv = v;
        return row;
    }
}
