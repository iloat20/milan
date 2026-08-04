using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Views;
using Android.Widget;
using Milan.Maui;

namespace Milan.Maui.Activities;

/// <summary>补给商店页（占位 / 品牌骨架）。后续阶段接入商品数据与购买逻辑。</summary>
[Activity(Label = "商店", ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize)]
public class ShopActivity : Activity
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        var root = Build();
        SetContentView(root);
        root.Post(() => Motion.Fade(root, Motion.Trans));
    }

    public override void OnConfigurationChanged(Android.Content.Res.Configuration newConfig)
    {
        base.OnConfigurationChanged(newConfig);
        var root = Build();
        SetContentView(root);
        root.Post(() => Motion.Fade(root, Motion.Trans));
    }

    FrameLayout Build()
    {
        var root = new FrameLayout(this);
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.Background = (new GradientDrawable(
            GradientDrawable.Orientation.TlBr,
            new[] { AppTheme.BgDeepest.ToArgb(), Color.ParseColor("#0E0A1E").ToArgb(), AppTheme.BgDeepest.ToArgb() }));

        var main = UI.VBox();
        main.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        main.SetPadding(UI.Dp(0), UI.Dp(38), UI.Dp(0), 0);
        main.AddView(AppChrome.AppTopBar(this, "补给商店", () => Finish()));

        var scroll = new ScrollView(this)
        {
            LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f)
        };
        var inner = UI.VBox();
        inner.SetPadding(UI.Dp(16), UI.Dp(10), UI.Dp(16), UI.Dp(10));

        inner.AddView(UI.TitleWithOrnament("精选补给", 16));
        inner.AddView(Spacer(12));

        var items = new (string name, string price, Color accent)[]
        {
            ("星尘礼包", "❖ 60", AppTheme.Gold),
            ("角色经验", "❖ 30", AppTheme.Frost),
            ("星魂碎片", "❖ 45", AppTheme.Violet),
            ("限定皮肤", "❖ 120", AppTheme.Gold),
        };
        foreach (var it in items)
        {
            inner.AddView(ProductCard(it.name, it.price, it.accent));
            inner.AddView(Spacer(12));
        }

        var note = UI.Text("商店商品与兑换逻辑即将开放。当前展示为界面骨架。", 12, AppTheme.Text2);
        note.Gravity = GravityFlags.Center;
        note.SetPadding(0, UI.Dp(8), 0, UI.Dp(8));
        inner.AddView(note);

        scroll.AddView(inner);
        main.AddView(scroll);

        var nav = new GameNavBar(this, GameNavBar.NavItem.Shop, OnNav);
        nav.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        main.AddView(nav);

        root.AddView(main);
        return root;
    }

    LinearLayout ProductCard(string name, string price, Color accent)
    {
        var card = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        card.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        card.Background = UI.GlassPanel(14, gold: false);
        card.SetPadding(UI.Dp(14), UI.Dp(14), UI.Dp(14), UI.Dp(14));
        card.SetGravity(GravityFlags.CenterVertical);
        card.Clickable = true; card.Focusable = true;

        var icon = UI.Text("⬢", 30, accent, bold: true);
        icon.SetPadding(0, 0, UI.Dp(14), 0);
        card.AddView(icon);

        var col = UI.VBox();
        col.AddView(UI.Text(name, 15, AppTheme.Text1, bold: true));
        col.AddView(UI.Text("限时补给", 11, AppTheme.Text2));
        card.AddView(col);

        card.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) });

        var buy = UI.Text(price, 14, accent, bold: true);
        buy.Background = (UI.GlassPanel(12, gold: false, nested: true));
        buy.SetPadding(UI.Dp(12), UI.Dp(7), UI.Dp(12), UI.Dp(7));
        card.AddView(buy);

        UI.TapFeedback(card, () => Toast($"{name} 即将开放"));
        return card;
    }

    void OnNav(GameNavBar.NavItem item)
    {
        var target = item switch
        {
            GameNavBar.NavItem.Home => typeof(HomeActivity),
            GameNavBar.NavItem.Gacha => typeof(GachaActivity),
            GameNavBar.NavItem.Deck => typeof(DeckActivity),
            GameNavBar.NavItem.Shop => typeof(ShopActivity),
            GameNavBar.NavItem.Settings => typeof(SettingsActivity),
            _ => null
        };
        Nav.To(this, target);
    }

    void Toast(string m) => Android.Widget.Toast.MakeText(this, m, Android.Widget.ToastLength.Short)?.Show();

    View Spacer(int h)
    {
        var d = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * d)) };
    }
}
