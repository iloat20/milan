using Android.App;
using Android.Graphics;
using Android.OS;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui;

namespace Milan.Maui.Activities;

[Activity(Label = "Milan", MainLauncher = true, Theme = "@android:style/Theme.Material.NoActionBar")]
public class HomeActivity : Activity
{
    private TextView _currency = null!;
    private TextView _owned = null!;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        SetContentView(BuildLayout());
    }

    protected override void OnResume()
    {
        base.OnResume();
        if (_currency != null) _currency.Text = GameState.CurrencyLabel;
        if (_owned != null) _owned.Text = $"已拥有 {GameState.OwnedCount} 个角色";
    }

    FrameLayout BuildLayout()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var root = new FrameLayout(this);
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);

        // Cosmic background
        var bg = new CosmicBackground(this);
        bg.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.AddView(bg);
        bg.Start();

        // Content overlay
        var content = new LinearLayout(this) { Orientation = Orientation.Vertical };
        content.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        content.SetPadding(Dp(24), Dp(48), Dp(24), Dp(24));

        // Top bar: logo + currency
        var top = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        top.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        var title = new TextView(this) { Text = "MILAN" };
        title.SetTypeface(null, TypefaceStyle.Bold);
        title.SetTextSize(ComplexUnitType.Sp, 28);
        title.SetTextColor(AppTheme.CosmicPrimary);
        title.LetterSpacing = 0.15f;
        title.SetShadowLayer(12, 0, 4, Color.Argb(160, 124, 77, 255));
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        _currency = new TextView(this) { Text = GameState.CurrencyLabel };
        _currency.SetTextColor(AppTheme.CosmicGold);
        _currency.SetTextSize(ComplexUnitType.Sp, 13);
        _currency.SetPadding(Dp(10), Dp(4), Dp(10), Dp(4));
        var currencyBg = new Android.Graphics.Drawables.GradientDrawable();
        currencyBg.SetCornerRadius(Dp(12));
        currencyBg.SetColor(Color.Argb(40, 255, 215, 0));
        currencyBg.SetStroke(1, Color.Argb(100, 255, 215, 0));
        _currency.Background = currencyBg;
        top.AddView(title); top.AddView(spacer); top.AddView(_currency);
        content.AddView(top);
        content.AddView(Spacer(6));

        var subtitle = new TextView(this) { Text = "次 元 裂 缝 · 抽 卡 养 成" };
        subtitle.SetTextColor(AppTheme.CosmicTextSecondary);
        subtitle.SetTextSize(ComplexUnitType.Sp, 12);
        subtitle.LetterSpacing = 0.3f;
        content.AddView(subtitle);
        content.AddView(Spacer(24));

        // Hero summoning rift
        var rift = new ParticleView(this);
        rift.Configure("glow", AppTheme.CosmicPrimarySoft, 4);
        var riftLp = new LinearLayout.LayoutParams(Dp(200), Dp(200));
        riftLp.Gravity = GravityFlags.CenterHorizontal;
        rift.LayoutParameters = riftLp;
        content.AddView(rift);
        rift.Start();

        var riftHint = new TextView(this) { Text = "✦ 点击召唤 ✦" };
        riftHint.SetTextColor(AppTheme.CosmicPrimarySoft);
        riftHint.SetTextSize(ComplexUnitType.Sp, 11);
        riftHint.Gravity = GravityFlags.CenterHorizontal;
        riftHint.SetPadding(0, Dp(8), 0, 0);
        content.AddView(riftHint);
        content.AddView(Spacer(20));

        // Three entry buttons (circular, horizontal)
        var entries = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        
        entries.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        
        entries.AddView(EntryButton("✦", "抽卡", AppTheme.CosmicPrimary, Start<GachaActivity>()));
        entries.AddView(SpacerH(Dp(16)));
        entries.AddView(EntryButton("⚔", "角色", AppTheme.AccentAlt, Start<CharacterListActivity>()));
        entries.AddView(SpacerH(Dp(16)));
        entries.AddView(EntryButton("☲", "图鉴", AppTheme.CosmicGold, Start<CollectionActivity>()));
        content.AddView(entries);

        content.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) });

        // Footer
        _owned = new TextView(this) { Text = $"已拥有 {GameState.OwnedCount} 个角色" };
        _owned.SetTextColor(AppTheme.CosmicTextMuted);
        _owned.SetTextSize(ComplexUnitType.Sp, 11);
        _owned.Gravity = GravityFlags.CenterHorizontal;
        content.AddView(_owned);

        root.AddView(content);
        return root;
    }

    View EntryButton(string glyph, string label, Color color, System.Action onTap)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        
        box.Clickable = true;
        box.Focusable = true;

        var circle = new TextView(this) { Text = glyph };
        circle.SetTextColor(Color.White);
        circle.SetTextSize(ComplexUnitType.Sp, 24);
        circle.Gravity = GravityFlags.Center;
        var size = Dp(56);
        var lp = new LinearLayout.LayoutParams(size, size);
        circle.LayoutParameters = lp;
        var bg = new Android.Graphics.Drawables.GradientDrawable();
        bg.SetShape(Android.Graphics.Drawables.ShapeType.Oval);
        bg.SetColor(Color.Argb(40, color.R, color.G, color.B));
        bg.SetStroke(Dp(2), Color.Argb(180, color.R, color.G, color.B));
        circle.Background = bg;
        box.AddView(circle);

        var lbl = new TextView(this) { Text = label };
        lbl.SetTextColor(AppTheme.CosmicTextSecondary);
        lbl.SetTextSize(ComplexUnitType.Sp, 11);
        lbl.SetPadding(0, Dp(6), 0, 0);
        box.AddView(lbl);

        box.Click += (s, e) =>
        {
            circle.Animate().ScaleX(1.15f).ScaleY(1.15f).SetDuration(120).Start();
            onTap();
        };
        return box;
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }

    View SpacerH(int w)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams((int)(w * density), 0) };
    }

    System.Action Start<T>() where T : Activity => () => StartActivity(typeof(T));
}
