using Android.App;
using Android.Content;

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
}
