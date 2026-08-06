using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui;

namespace Milan.Maui;

/// <summary>列表排序方式。</summary>
public enum ListSortMode
{
    RarityDesc, // 稀有度由高到低
    NameAsc,    // 名称 A→Z
    WorldAsc,   // 阵营 A→Z
    ElementAsc  // 元素 A→Z
}

/// <summary>
/// 通用列表筛选条：搜索框 + 稀有度 / 元素 / 排序 chips。
/// 暗夜神性·诸神黄昏（twilight）视觉语言：玻璃面板 + 选中态金线 + 金字，其余发丝白线。
/// 通过 <see cref="Changed"/> 通知宿主重建列表，逻辑与视图解耦，两个列表页共用。
/// </summary>
public sealed class ListFilterBar
{
    public string SearchText = "";
    public int RarityFilter = -1;          // -1 = 全部
    public string? ElementFilter = null;   // null = 全部
    public ListSortMode Sort = ListSortMode.RarityDesc;

    /// <summary>任意筛选/排序变更时触发，宿主应重建列表。</summary>
    public event Action? Changed;

    readonly Context _ctx;
    readonly List<string> _elements;
    readonly EditText _search;
    readonly LinearLayout _rarityRow;
    readonly LinearLayout _elementRow;
    readonly LinearLayout _sortRow;

    public ListFilterBar(Context ctx, IEnumerable<string> availableElements)
    {
        _ctx = ctx;
        _elements = availableElements.Distinct().ToList();

        var root = UI.VBox();
        root.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        root.SetPadding(0, 0, 0, UI.Dp(8));

        // ── 搜索框 ──
        _search = new EditText(ctx)
        {
            Hint = "搜索角色名称…",
        };
        _search.SetTextColor(AppTheme.Text1);
        _search.SetHintTextColor(AppTheme.Text3);
        _search.TextSize = 14;
        _search.Background = UI.GlassPanel(14);
        _search.SetPadding(UI.Dp(14), UI.Dp(10), UI.Dp(14), UI.Dp(10));
        _search.SetSingleLine(true);
        _search.TextChanged += (_, _) =>
        {
            SearchText = _search.Text ?? "";
            Changed?.Invoke();
        };
        root.AddView(_search);
        root.AddView(UI.Spacer(_ctx, 10));

        // ── 稀有度 ──
        root.AddView(SectionLabel("稀有度"));
        _rarityRow = UI.HBox();
        root.AddView(Scroll(_rarityRow));
        root.AddView(UI.Spacer(_ctx, 6));

        // ── 元素 ──
        root.AddView(SectionLabel("元素"));
        _elementRow = UI.HBox();
        root.AddView(Scroll(_elementRow));
        root.AddView(UI.Spacer(_ctx, 6));

        // ── 排序 ──
        root.AddView(SectionLabel("排序"));
        _sortRow = UI.HBox();
        root.AddView(Scroll(_sortRow));

        RefreshChips();
        View = root;
    }

    public View View { get; }

    /// <summary>对源集合按当前筛选 + 排序条件投影，返回新列表（不修改入参）。</summary>
    public List<T> FilterSort<T>(
        IEnumerable<T> source,
        Func<T, string> getName,
        Func<T, int> getRarity,
        Func<T, string> getElement,
        Func<T, string> getGroupKey)
    {
        IEnumerable<T> q = source;
        if (RarityFilter >= 0)
            q = q.Where(x => getRarity(x) == RarityFilter);
        if (!string.IsNullOrEmpty(ElementFilter))
            q = q.Where(x => getElement(x) == ElementFilter);
        if (!string.IsNullOrWhiteSpace(SearchText))
        {
            var s = SearchText.Trim().ToLowerInvariant();
            q = q.Where(x => getName(x).ToLowerInvariant().Contains(s));
        }

        var list = q.ToList();
        switch (Sort)
        {
            case ListSortMode.RarityDesc:
                list.Sort((a, b) => getRarity(b).CompareTo(getRarity(a)));
                break;
            case ListSortMode.NameAsc:
                list.Sort((a, b) => string.Compare(getName(a), getName(b), StringComparison.Ordinal));
                break;
            case ListSortMode.WorldAsc:
                list.Sort((a, b) => string.Compare(getGroupKey(a), getGroupKey(b), StringComparison.Ordinal));
                break;
            case ListSortMode.ElementAsc:
                list.Sort((a, b) => string.Compare(getElement(a), getElement(b), StringComparison.Ordinal));
                break;
        }
        return list;
    }

    void RefreshChips()
    {
        // 稀有度
        _rarityRow.RemoveAllViews();
        var rarities = new[] { (-1, "全部"), (1, "R"), (2, "SR"), (3, "SSR"), (4, "UR") };
        foreach (var (r, label) in rarities)
            AddChip(_rarityRow, label, RarityFilter == r,
                () => { RarityFilter = r; RefreshChips(); Changed?.Invoke(); });

        // 元素
        _elementRow.RemoveAllViews();
        AddChip(_elementRow, "全部", ElementFilter == null,
            () => { ElementFilter = null; RefreshChips(); Changed?.Invoke(); });
        foreach (var e in _elements)
        {
            var glyph = ElementTheme.For(e).glyph;
            AddChip(_elementRow, glyph, ElementFilter == e,
                () => { ElementFilter = e; RefreshChips(); Changed?.Invoke(); });
        }

        // 排序
        _sortRow.RemoveAllViews();
        var sorts = new[]
        {
            (ListSortMode.RarityDesc, "稀有度↓"),
            (ListSortMode.NameAsc, "名称"),
            (ListSortMode.WorldAsc, "阵营"),
            (ListSortMode.ElementAsc, "元素"),
        };
        foreach (var (s, label) in sorts)
            AddChip(_sortRow, label, Sort == s,
                () => { Sort = s; RefreshChips(); Changed?.Invoke(); });
    }

    void AddChip(LinearLayout row, string label, bool active, Action onTap)
    {
        var chip = Label(label, 12, active ? AppTheme.Gold : AppTheme.Text2, bold: true);
        chip.Gravity = GravityFlags.Center;
        chip.SetPadding(UI.Dp(12), UI.Dp(6), UI.Dp(12), UI.Dp(6));
        chip.Clickable = true;
        chip.Focusable = true;
        chip.Background = active
            ? (Drawable)UI.GlassPanel(14, gold: true)
            : UI.GlassPanel(14);
        chip.Click += (_, _) => onTap();

        var lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent);
        lp.SetMargins(0, 0, UI.Dp(8), 0);
        chip.LayoutParameters = lp;
        row.AddView(chip);
    }

    TextView Label(string s, float sp, Color c, bool bold = false)
    {
        var t = new TextView(_ctx) { Text = s };
        t.SetTextColor(c);
        t.SetTextSize(ComplexUnitType.Sp, sp);
        if (bold) t.SetTypeface(null, TypefaceStyle.Bold);
        return t;
    }

    TextView SectionLabel(string s)
    {
        var t = Label(s, 12, AppTheme.Text2, bold: true);
        t.SetPadding(0, 0, 0, UI.Dp(4));
        return t;
    }

    HorizontalScrollView Scroll(View child)
    {
        var sv = new HorizontalScrollView(_ctx)
        {
            LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent),
        };
        sv.SetPadding(0, 0, 0, 0);
        child.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent);
        sv.AddView(child);
        return sv;
    }

}
