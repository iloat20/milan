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

/// <summary>卡组编队页（占位 / 品牌骨架）。后续阶段接入 ProgressionEngine 阵容数据。</summary>
[Activity(Label = "卡组", ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize)]
public class DeckActivity : Activity
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
            new[] { AppTheme.BgDeepest.ToArgb(), AppTheme.BgMid.ToArgb(), AppTheme.BgDeepest.ToArgb() }));

        var main = UI.VBox();
        main.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        main.SetPadding(UI.Dp(0), UI.Dp(38), UI.Dp(0), 0);
        main.AddView(AppChrome.AppTopBar(this, "卡组编队", () => Finish()));

        var scroll = new ScrollView(this)
        {
            LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f)
        };
        var inner = UI.VBox();
        inner.SetPadding(UI.Dp(16), UI.Dp(10), UI.Dp(16), UI.Dp(10));

        inner.AddView(UI.TitleWithOrnament("出战阵容", 16));
        inner.AddView(Spacer(12));

        // 5 个空槽位（2 列）
        for (int i = 0; i < 5; i += 2)
        {
            var row = UI.HBox();
            var rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
            rowLp.SetMargins(0, 0, 0, UI.Dp(12));
            row.LayoutParameters = rowLp;
            row.AddView(Slot(i + 1));
            var g = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(12), 0) };
            row.AddView(g);
            if (i + 1 < 5) row.AddView(Slot(i + 2));
            inner.AddView(row);
        }

        inner.AddView(Spacer(16));
        var note = UI.Text("编队与养成系统即将开放。当前展示为界面骨架。", 12, AppTheme.Text2);
        note.Gravity = GravityFlags.Center;
        note.SetPadding(0, UI.Dp(8), 0, UI.Dp(8));
        inner.AddView(note);

        scroll.AddView(inner);
        main.AddView(scroll);

        var nav = new GameNavBar(this, GameNavBar.NavItem.Deck, OnNav);
        nav.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        main.AddView(nav);

        root.AddView(main);
        return root;
    }

    FrameLayout Slot(int idx)
    {
        var slot = new FrameLayout(this);
        slot.LayoutParameters = new LinearLayout.LayoutParams(0, UI.Dp(120), 1f);
        slot.Background = UI.GlassPanel(14, gold: false);
        slot.SetPadding(UI.Dp(10), UI.Dp(10), UI.Dp(10), UI.Dp(10));
        slot.Clickable = true; slot.Focusable = true;

        var col = UI.VBox();
        col.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        col.SetGravity(GravityFlags.Center);

        var plus = UI.Text("＋", 30, Color.Argb(120, 255, 255, 255), bold: true);
        var cap = UI.Text($"空位 {idx}", 12, AppTheme.Text2);
        cap.SetPadding(0, UI.Dp(6), 0, 0);
        col.AddView(plus); col.AddView(cap);

        slot.AddView(col);
        UI.TapFeedback(slot, () => Toast($"槽位 {idx} 编队开发中"));
        return slot;
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
