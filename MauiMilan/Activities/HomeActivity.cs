using Android.App;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Widget;
using Milan.Maui;

namespace Milan.Maui.Activities;

[Activity(Label = "Milan", MainLauncher = true, Theme = "@android:style/Theme.Material.NoActionBar")]
public class HomeActivity : Activity
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        SetContentView(Build());
    }

    protected override void OnResume()
    {
        base.OnResume();
        // Currency / owned count may have changed while in another Activity.
        SetContentView(Build());
    }

    LinearLayout Build()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.SetPadding(Dp(24), Dp(56), Dp(24), Dp(32));
        root.SetBackgroundDrawable(UI.Gradient(AppTheme.Background, Color.ParseColor("#1a1330")));

        // ---- Header: title + subtitle + currency ----
        var title = UI.Text("MILAN", 40, AppTheme.Accent);
        title.SetTypeface(null, TypefaceStyle.Bold);
        title.LetterSpacing = 0.15f;
        title.SetShadowLayer(12, 0, 4, Color.Argb(160, 233, 69, 96));

        var subtitle = UI.Text("次 元 裂 缝 · 抽 卡", 14, AppTheme.AccentAlt);
        subtitle.LetterSpacing = 0.3f;

        var currency = UI.Text(GameState.CurrencyLabel, 16, AppTheme.Gold);
        currency.SetPadding(0, Dp(8), 0, 0);

        root.AddView(title);
        root.AddView(subtitle);
        root.AddView(currency);
        root.AddView(Spacer(28));

        // ---- Navigation cards ----
        root.AddView(NavCard("抽卡", "单抽 / 十连 · 跨越次元的召唤", AppTheme.Accent, "✦", Start<GachaActivity>()));
        root.AddView(Spacer(16));
        root.AddView(NavCard("角色", "检视 · 养成 · 天赋", AppTheme.AccentAlt, "⚔", Start<CharacterListActivity>()));
        root.AddView(Spacer(16));
        root.AddView(NavCard("图鉴", "收集度 · 世界档案", AppTheme.Gold, "☲", Start<CollectionActivity>()));

        root.AddView(Spacer(28));
        var footer = UI.Text($"已拥有 {GameState.OwnedCount} 个角色  ·  测试版", 12, AppTheme.TextMuted);
        footer.Gravity = GravityFlags.Center;
        root.AddView(footer);

        return root;
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }

    /// <summary>Returns an Action that starts T, for wiring into cards/buttons.</summary>
    System.Action Start<T>() where T : Activity => () => StartActivity(typeof(T));

    /// <summary>Big rounded nav card: icon glyph + title + description + chevron, tap to navigate.</summary>
    LinearLayout NavCard(string title, string desc, Color accent, string glyph, System.Action onTap)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var card = UI.VBox();
        card.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        card.Background = UI.RoundRect(AppTheme.Surface, 20, 1, accent);
        card.SetPadding(Dp(22), Dp(22), Dp(22), Dp(22));
        card.Focusable = true;
        card.Clickable = true;
        card.Click += (s, e) => onTap();

        var top = UI.HBox();

        var glyphView = UI.Text(glyph, 26, AppTheme.TextPrimary);
        glyphView.SetPadding(0, 0, Dp(14), 0);

        var titleView = UI.Text(title, 22, AppTheme.TextPrimary, bold: true);

        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        var chevron = UI.Text("›", 26, AppTheme.TextMuted);

        top.AddView(glyphView);
        top.AddView(titleView);
        top.AddView(spacer);
        top.AddView(chevron);

        var descView = UI.Text(desc, 13, AppTheme.TextSecondary);
        descView.SetPadding(0, Dp(8), 0, 0);

        card.AddView(top);
        card.AddView(descView);
        return card;
    }
}
