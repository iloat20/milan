using Android.App;
using Android.Content;
using Milan.Maui.Activities;

namespace Milan.Maui;

/// <summary>
/// 底栏导航统一入口。
/// #25: 原来 5 个 Activity 各自 StartActivity(type)，五个页面来回点会在返回栈里堆出
/// 几十个实例（内存与返回体验都糟糕）。这里统一加 ClearTop|SingleTop，
/// 已在栈中的目标页会被复用并把它上面的页面清掉。
/// </summary>
public static class Nav
{
    public static void To(Activity self, Type? target)
    {
        if (self == null || target == null) return;
        if (self.GetType() == target) return;
        var intent = new Intent(self, target);
        intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.SingleTop);
        self.StartActivity(intent);
    }

    /// <summary>已经构造好 Intent 的场景（例如带 extra）。</summary>
    public static void To(Activity self, Intent? intent)
    {
        if (self == null || intent == null) return;
        intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.SingleTop);
        self.StartActivity(intent);
    }

    /// <summary>
    /// 底栏导航项 → 目标页的唯一映射。
    /// 此前 5 个 Activity 各抄了一份完全相同的 switch：新增一个底栏入口要改 5 处，
    /// 漏改的表现是"某个页面点某个 tab 没反应"，且不会有任何编译错误。
    /// 放在 Nav 而非 GameNavBar，是为了保留导航栏组件不依赖具体 Activity 的既有约束。
    /// </summary>
    public static Type? TargetOf(GameNavBar.NavItem item) => item switch
    {
        GameNavBar.NavItem.Home => typeof(HomeActivity),
        GameNavBar.NavItem.Gacha => typeof(GachaActivity),
        GameNavBar.NavItem.Deck => typeof(DeckActivity),
        GameNavBar.NavItem.Shop => typeof(ShopActivity),
        GameNavBar.NavItem.Settings => typeof(SettingsActivity),
        _ => null
    };

    /// <summary>底栏点击的标准处理：解析目标并跳转。停留在当前页由 <see cref="To(Activity, Type)"/> 自身拦截。</summary>
    public static void Go(Activity self, GameNavBar.NavItem item) => To(self, TargetOf(item));
}
