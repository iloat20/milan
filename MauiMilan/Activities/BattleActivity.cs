using Android.App;
using Android.Content.PM;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Text;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui;
using Milan.Maui.Services;

namespace Milan.Maui.Activities;

[Activity(Label = "战斗", ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize)]
public class BattleActivity : Activity
{
    private HandCardLayout _hand = null!;
    private FrameLayout _enemyArea = null!;
    private View _enemyFill = null!;
    private TextView _enemyHpText = null!;
    private readonly Dictionary<View, string> _owner = new();
    private int _enemyHp = 100;
    private string _enemyElement = "Flame";
    private bool _stateInit; // #26: 防止横竖屏重建时重复初始化战斗状态

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        SetContentView(Build());
    }

    public override void OnConfigurationChanged(Android.Content.Res.Configuration newConfig)
    {
        base.OnConfigurationChanged(newConfig);
        SetContentView(Build()); // 横竖屏切换重建布局（HandCardLayout 自适应扇形）
    }

    private View Build()
    {
        // #26: Build() 会在横竖屏切换时被重复调用。战斗状态（敌方血量、敌方属性）
        // 只能在首次构建时初始化，否则一转屏敌人就满血复活、属性还会重掷。
        _owner.Clear(); // _owner 存的是旧视图对象，重建时必须清掉
        if (!_stateInit)
        {
            _enemyHp = 100;
            // #27: 元素键必须是 ElementTheme 支持的名字，"Aqua"/"Volt"/"Terra" 会全部落到
            // default 分支变成炎属性，敌人配色永远是红的。
            var elems = new[] { "Flame", "Frost", "Thunder", "Earth" };
            _enemyElement = elems[new System.Random().Next(elems.Length)];
            _stateInit = true;
        }

        var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.Background = TwilightBg();

        root.AddView(AppChrome.AppTopBar(this, "战 斗", () => Finish()));

        // ── 敌方区（拖拽 drop target）──
        _enemyArea = new FrameLayout(this);
        _enemyArea.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0) { Weight = 1f };
        _enemyArea.SetPadding(Dp(16), Dp(12), Dp(16), Dp(12));
        _enemyArea.AddView(EnemyPanel());
        root.AddView(_enemyArea);

        // ── 提示条 ──
        var tip = UI.Text("拖动手牌至敌人，发动攻击", 12, AppTheme.Text2);
        tip.Gravity = GravityFlags.CenterHorizontal;
        tip.SetPadding(0, Dp(6), 0, Dp(6));
        root.AddView(tip);

        // ── 扇形手牌 ──
        _hand = new HandCardLayout(this);
        _hand.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(210));
        _hand.SetDropTarget(_enemyArea);
        _hand.CardPlayed += OnCardPlayed;
        _hand.SetCards(BuildHand());
        root.AddView(_hand);

        // ── 操作条 ──
        var bar = UI.HBox();
        bar.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        bar.SetPadding(Dp(16), Dp(10), Dp(16), Dp(16));

        var retreat = ThemeButtons.Danger(this, "撤 退");
        retreat.Click += (_, _) => Finish();
        var retreatLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        retreatLp.SetMargins(0, 0, Dp(8), 0);
        retreat.LayoutParameters = retreatLp;

        var end = ThemeButtons.Neon(this, "结束回合");
        end.Click += (_, _) => Toast("（演示）回合已结束");
        var endLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        endLp.SetMargins(Dp(8), 0, 0, 0);
        end.LayoutParameters = endLp;

        bar.AddView(retreat);
        bar.AddView(end);
        root.AddView(bar);

        Motion.Fade(root, Motion.Trans);
        return root;
    }

    private View EnemyPanel()
    {
        var panel = new FrameLayout(this);
        panel.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        panel.Background = UI.GlassPanel(20, gold: false);

        var inner = new LinearLayout(this) { Orientation = Orientation.Vertical };
        inner.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        inner.SetGravity(GravityFlags.CenterHorizontal);
        inner.SetPadding(Dp(16), Dp(14), Dp(16), Dp(14));

        // 敌人立绘占位（元素渐变 + 大字形 + 名称）
        int psize = Dp(110);
        var portrait = new FrameLayout(this);
        portrait.LayoutParameters = new LinearLayout.LayoutParams(psize, psize) { Gravity = GravityFlags.CenterHorizontal };
        portrait.Background = ElementTheme.Gradient(_enemyElement);
        ((GradientDrawable)portrait.Background).SetCornerRadius(Dp(16));
        var (_, _, _, eglyph) = ElementTheme.For(_enemyElement);
        var g = new TextView(this) { Text = eglyph, Gravity = GravityFlags.Center };
        g.SetTextColor(Color.White);
        g.SetTextSize(ComplexUnitType.Sp, 52);
        g.SetTypeface(null, TypefaceStyle.Bold);
        g.SetShadowLayer(6, 0, 2, Color.Argb(160, 0, 0, 0));
        g.LayoutParameters = new FrameLayout.LayoutParams(psize, psize);
        portrait.AddView(g);
        inner.AddView(portrait);

        var name = UI.Text("虚空守卫", 18, AppTheme.Text1, bold: true);
        name.Gravity = GravityFlags.CenterHorizontal;
        name.SetPadding(0, Dp(10), 0, 0);
        inner.AddView(name);

        // 血条（深色空槽 + 金色填充，靠 weight 控制比例）
        var hpBar = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        hpBar.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(14)) { TopMargin = Dp(10) };
        hpBar.Background = UI.RoundRect(SetA(AppTheme.Surface, 220), 7);
        _enemyFill = new View(this);
        _enemyFill.LayoutParameters = new LinearLayout.LayoutParams(0, Dp(14), 1f);
        _enemyFill.Background = GoldFill();
        ((GradientDrawable)_enemyFill.Background).SetCornerRadius(Dp(7));
        hpBar.AddView(_enemyFill);
        inner.AddView(hpBar);

        _enemyHpText = UI.Text("HP 100 / 100", 11, AppTheme.Text2);
        _enemyHpText.Gravity = GravityFlags.CenterHorizontal;
        _enemyHpText.SetPadding(0, Dp(5), 0, 0);
        inner.AddView(_enemyHpText);

        panel.AddView(inner);
        return panel;
    }

    private List<View> BuildHand()
    {
        var owned = GameState.Owned();
        var picks = owned.Take(5).ToList();
        while (picks.Count < 5) picks.Add(null);
        var cards = new List<View>();
        int w = Dp(92), h = Dp(128);
        foreach (var ch in picks)
        {
            var flip = new FlipCardView(this);
            flip.SetFaces(BattleCardFace(ch, w, h), CardBack(w, h));
            flip.LayoutParameters = new FrameLayout.LayoutParams(w, h)
            {
                Gravity = GravityFlags.CenterHorizontal | GravityFlags.Bottom,
                BottomMargin = Dp(10)
            };
            _owner[flip] = ch?.Name ?? "";
            cards.Add(flip);
        }
        return cards;
    }

    private View BattleCardFace(OwnedCharacterView? ch, int w, int h)
    {
        int rarity = ch?.Rarity ?? 1;
        var rc = AppTheme.RarityColor(rarity);
        var elem = ch?.Element ?? "Flame";

        var box = new FrameLayout(this);
        box.LayoutParameters = new FrameLayout.LayoutParams(w, h);
        box.SetPadding(Dp(3), Dp(3), Dp(3), Dp(3));
        box.Background = UI.RoundRect(SetA(rc, 130), 12, 2, rc);

        var inner = new LinearLayout(this) { Orientation = Orientation.Vertical };
        inner.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        inner.SetPadding(Dp(8), Dp(8), Dp(8), Dp(8));
        inner.Background = UI.RoundRect(AppTheme.Surface, 10);

        var head = new View(this);
        head.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(40));
        head.Background = ElementTheme.Gradient(elem);
        ((GradientDrawable)head.Background).SetCornerRadius(Dp(8));
        inner.AddView(head);

        var (_, _, _, glyph) = ElementTheme.For(elem);
        var g = UI.Text(glyph, 34, Color.White, bold: true);
        g.Gravity = GravityFlags.CenterHorizontal;
        g.SetPadding(0, Dp(6), 0, Dp(2));
        inner.AddView(g);

        var tag = UI.Text(RarityName(rarity), 11, rc, bold: true);
        tag.Gravity = GravityFlags.CenterHorizontal;
        inner.AddView(tag);

        var name = UI.Text(ch?.Name ?? "空位", 12, AppTheme.Text1, bold: true);
        name.Gravity = GravityFlags.CenterHorizontal;
        name.SetMaxLines(1);
        name.Ellipsize = TextUtils.TruncateAt.End;
        name.SetPadding(0, Dp(2), 0, 0);
        inner.AddView(name);

        int atk = ch == null ? 0 : GameState.ComputeStats(ch).Atk;
        var atkRow = UI.Text(ch == null ? "—" : $"ATK {atk}", 11, AppTheme.Gold, bold: true);
        atkRow.Gravity = GravityFlags.CenterHorizontal;
        atkRow.SetPadding(0, Dp(3), 0, 0);
        inner.AddView(atkRow);

        box.AddView(inner);
        return box;
    }

    private View CardBack(int w, int h)
    {
        var back = new FrameLayout(this);
        back.LayoutParameters = new FrameLayout.LayoutParams(w, h);
        back.Background = UI.RoundRect(AppTheme.BgDeepest, 12, 2, SetA(AppTheme.Gold, 180));

        var dia = UI.Text("◆", 32, AppTheme.Gold, bold: true);
        dia.Gravity = GravityFlags.Center;
        dia.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        back.AddView(dia);

        var title = UI.Text("MILAN", 10, SetA(AppTheme.Gold, 170));
        title.Gravity = GravityFlags.CenterHorizontal;
        var tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Bottom
        };
        tl.SetMargins(0, 0, 0, Dp(10));
        title.LayoutParameters = tl;
        back.AddView(title);
        return back;
    }

    private void OnCardPlayed(View card)
    {
        _owner.TryGetValue(card, out var nm);
        _enemyHp = System.Math.Max(0, _enemyHp - 18);
        UpdateEnemyBar();
        Toast($"发动：{nm}");
        if (_enemyHp <= 0) Toast("胜利！（演示）");
    }

    private void UpdateEnemyBar()
    {
        float ratio = _enemyHp / 100f;
        _enemyFill.LayoutParameters = new LinearLayout.LayoutParams(0, Dp(14), ratio);
        _enemyFill.RequestLayout();
        _enemyHpText.Text = $"HP {_enemyHp} / 100";
    }

    // ── helpers ──
    private int Dp(int v) => UI.Dp(v);
    private static Color SetA(Color c, int a) => Color.Argb(a, c.R, c.G, c.B);
    private static string RarityName(int r) => r switch { 4 => "UR", 3 => "SSR", 2 => "SR", _ => "R" };

    private static Drawable TwilightBg()
    {
        var gd = new GradientDrawable();
        gd.SetColors(new[] { AppTheme.BgDeepest.ToArgb(), AppTheme.BgMid.ToArgb(), AppTheme.BgDeepest.ToArgb() });
        gd.SetOrientation(GradientDrawable.Orientation.TlBr);
        return gd;
    }

    private static Drawable GoldFill()
    {
        var gd = new GradientDrawable();
        gd.SetColors(new[] { AppTheme.GoldHi.ToArgb(), AppTheme.Gold.ToArgb() });
        gd.SetOrientation(GradientDrawable.Orientation.LeftRight);
        return gd;
    }

    private void Toast(string m) => Android.Widget.Toast.MakeText(this, m, ToastLength.Short).Show();
}
