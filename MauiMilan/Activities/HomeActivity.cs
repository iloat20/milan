using Android.App;
using Android.Graphics;
using Android.OS;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui;
using Milan.Maui.Services;

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
        try { SetContentView(BuildLayout()); }
        catch (System.Exception ex) { Android.Util.Log.Error("[Milan]", $"Home: {ex}"); }
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

        var bg = new CosmicBackground(this);
        bg.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.AddView(bg);
        bg.Start();

        var scroll = new ScrollView(this) { LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent) };
        var content = new LinearLayout(this) { Orientation = Orientation.Vertical };
        content.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        content.SetPadding(Dp(24), Dp(44), Dp(24), Dp(32));

        // Header
        var header = new LinearLayout(this) { Orientation = Orientation.Vertical };
        header.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);

        var title = new TextView(this) { Text = "MILAN" };
        title.SetTypeface(null, TypefaceStyle.Bold);
        title.SetTextSize(ComplexUnitType.Sp, 42);
        title.SetTextColor(Color.White);
        title.LetterSpacing = 0.2f;
        title.SetShadowLayer(16, 0, 4, Color.Argb(180, 124, 77, 255));
        title.Gravity = GravityFlags.CenterHorizontal;
        header.AddView(title);

        var subtitle = new TextView(this) { Text = "次 元 裂 缝" };
        subtitle.SetTextColor(AppTheme.CosmicPrimarySoft);
        subtitle.SetTextSize(ComplexUnitType.Sp, 15);
        subtitle.LetterSpacing = 0.4f;
        subtitle.Gravity = GravityFlags.CenterHorizontal;
        subtitle.SetPadding(0, Dp(4), 0, 0);
        header.AddView(subtitle);
        content.AddView(header);
        content.AddView(Spacer(28));

        // Featured character portrait
        var featured = new FullBodyCharacter(this, FeaturedCharacter());
        var featLp = new LinearLayout.LayoutParams(Dp(160), Dp(210));
        featLp.Gravity = GravityFlags.CenterHorizontal;
        featured.LayoutParameters = featLp;
        content.AddView(featured);
        content.AddView(Spacer(24));

        // Stats row
        var statsRow = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        statsRow.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        
        _currency = StatChip("星尘", GameState.CurrencyLabel.Replace("星尘: ", ""), AppTheme.CosmicGold);
        _owned = StatChip("角色", GameState.OwnedCount.ToString(), AppTheme.CosmicPrimarySoft);
        statsRow.AddView(_currency);
        statsRow.AddView(SpacerH(Dp(12)));
        statsRow.AddView(_owned);
        content.AddView(statsRow);
        content.AddView(Spacer(28));

        // Navigation cards
        content.AddView(NavCard("✦", "抽 卡", "单抽 / 十连 · 跨越次元的召唤", AppTheme.CosmicPrimary, Start<GachaActivity>()));
        content.AddView(Spacer(14));
        content.AddView(NavCard("⚔", "角 色", "检视 · 养成 · 天赋加点", Color.ParseColor("#3aa0ff"), Start<CharacterListActivity>()));
        content.AddView(Spacer(14));
        content.AddView(NavCard("☲", "图 鉴", "收集度 · 世界档案 · 羁绊", AppTheme.CosmicGold, Start<CollectionActivity>()));

        scroll.AddView(content);
        root.AddView(scroll);
        return root;
    }

    CharacterDataEntry FeaturedCharacter()
    {
        var owned = GameState.Owned();
        if (owned.Count > 0) return owned.OrderByDescending(c => c.Rarity).First().Def!;
        return GameState.Service.Characters.First();
    }

    TextView StatChip(string label, string value, Color color)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var box = new TextView(this);
        box.SetTextColor(color);
        box.SetTextSize(ComplexUnitType.Sp, 13);
        box.Text = value + "\n" + label;
        box.Gravity = GravityFlags.Center;
        box.SetPadding(Dp(16), Dp(8), Dp(16), Dp(8));
        var bg = new Android.Graphics.Drawables.GradientDrawable();
        bg.SetCornerRadius(Dp(12));
        bg.SetColor(Color.Argb(30, color.R, color.G, color.B));
        bg.SetStroke(1, Color.Argb(80, color.R, color.G, color.B));
        box.Background = bg;
        return box;
    }

    LinearLayout NavCard(string glyph, string title, string desc, Color accent, System.Action onTap)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var card = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        card.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        card.SetPadding(Dp(18), Dp(18), Dp(18), Dp(18));
        
        card.Clickable = true;
        card.Focusable = true;
        var bg = new Android.Graphics.Drawables.GradientDrawable();
        bg.SetCornerRadius(Dp(16));
        bg.SetColor(Color.Argb(20, 255, 255, 255));
        bg.SetStroke(1, Color.Argb(70, accent.R, accent.G, accent.B));
        card.Background = bg;

        var circle = new TextView(this) { Text = glyph };
        circle.SetTextColor(Color.White);
        circle.SetTextSize(ComplexUnitType.Sp, 22);
        circle.Gravity = GravityFlags.Center;
        var size = Dp(48);
        circle.LayoutParameters = new LinearLayout.LayoutParams(size, size);
        var circleBg = new Android.Graphics.Drawables.GradientDrawable();
        circleBg.SetShape(Android.Graphics.Drawables.ShapeType.Oval);
        circleBg.SetColor(Color.Argb(60, accent.R, accent.G, accent.B));
        circleBg.SetStroke(2, Color.Argb(160, accent.R, accent.G, accent.B));
        circle.Background = circleBg;
        card.AddView(circle);

        var textCol = new LinearLayout(this) { Orientation = Orientation.Vertical };
        textCol.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        textCol.SetPadding(Dp(14), 0, 0, 0);
        var titleV = new TextView(this) { Text = title };
        titleV.SetTextColor(Color.White);
        titleV.SetTextSize(ComplexUnitType.Sp, 18);
        titleV.SetTypeface(null, TypefaceStyle.Bold);
        textCol.AddView(titleV);
        var descV = new TextView(this) { Text = desc };
        descV.SetTextColor(AppTheme.CosmicTextSecondary);
        descV.SetTextSize(ComplexUnitType.Sp, 12);
        descV.SetPadding(0, Dp(2), 0, 0);
        textCol.AddView(descV);
        card.AddView(textCol);

        var chevron = new TextView(this) { Text = "›" };
        chevron.SetTextColor(Color.Argb(120, 255, 255, 255));
        chevron.SetTextSize(ComplexUnitType.Sp, 28);
        card.AddView(chevron);

        card.Click += (s, e) =>
        {
            card.Animate().ScaleX(0.97f).ScaleY(0.97f).SetDuration(80).Start();
            onTap();
        };
        return card;
    }

    View Spacer(int h) => new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * Resources.DisplayMetrics.Density)) };
    View SpacerH(int w) => new View(this) { LayoutParameters = new LinearLayout.LayoutParams((int)(w * Resources.DisplayMetrics.Density), 0) };
    System.Action Start<T>() where T : Activity => () => StartActivity(typeof(T));
}
