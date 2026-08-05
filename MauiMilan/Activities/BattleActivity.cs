using Android.App;
using Android.Content.PM;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.Text;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Domain.Battle;
using Milan.Infrastructure.Save;
using Milan.Maui;
using Milan.Maui.Services;
using System;
using System.Collections.Generic;
using System.Linq;

namespace Milan.Maui.Activities;

[Activity(Label = "战斗", ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize)]
public class BattleActivity : Activity
{
    // 出战单位：携带实时血量，区别于 UnitStats 的满血快照。
    sealed class Combatant
    {
        public UnitStats Stats;
        public int Hp;
        public string Name = "";
        public int Rarity;
        public string Element = "Flame";
        public bool Dead => Hp <= 0;
    }

    private HandCardLayout _hand = null!;
    private FrameLayout _enemyArea = null!;
    private View _enemyFill = null!;
    private TextView _enemyHpText = null!;
    private readonly Dictionary<View, Combatant?> _cardUnit = new();   // 卡牌 -> 出战单位（空位 null）
    private readonly Dictionary<Combatant, View> _unitCard = new();
    private readonly Dictionary<Combatant, TextView> _unitHp = new();
    private readonly List<Combatant> _team = new();
    private UnitStats _enemyStats;
    private int _enemyHp;
    private int _enemyMaxHp = 220;
    private int _enemyDef = 50;
    private int _teamPower;
    private int _turns;
    private string _enemyElement = "Flame";
    private string _enemyName = "虚空守卫";
    private bool _stateInit;   // #26: 防横竖屏重建时重复初始化战斗状态
    private bool _over;

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

    // 重建布局。横竖屏切换时 _stateInit 已为 true，保留当前血量/回合；首次构建才随机敌人元素并组队。
    private View Build()
    {
        _cardUnit.Clear();
        _unitCard.Clear();
        _unitHp.Clear();
        if (!_stateInit)
        {
            _enemyMaxHp = 220;
            _enemyDef = 50;
            _enemyHp = _enemyMaxHp;
            _enemyStats = new UnitStats { Atk = 64, Def = _enemyDef, Hp = _enemyMaxHp, Spd = 12, CharacterId = "enemy" };
            _turns = 0;
            _over = false;
            BuildTeam();
            var elems = new[] { "Flame", "Frost", "Thunder", "Earth" };
            _enemyElement = elems[new System.Random().Next(elems.Length)];
            _stateInit = true;
        }

        var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.Background = TwilightBg();

        root.AddView(AppChrome.AppTopBar(this, "战 斗", () => Finish()));

        _enemyArea = new FrameLayout(this);
        _enemyArea.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0) { Weight = 1f };
        _enemyArea.SetPadding(Dp(16), Dp(12), Dp(16), Dp(12));
        _enemyArea.AddView(EnemyPanel());
        root.AddView(_enemyArea);

        var tip = UI.Text("拖动手牌至敌人发动攻击；敌人会反击", 12, AppTheme.Text2);
        tip.Gravity = GravityFlags.CenterHorizontal;
        tip.SetPadding(0, Dp(6), 0, Dp(6));
        root.AddView(tip);

        _hand = new HandCardLayout(this);
        _hand.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(210));
        _hand.SetDropTarget(_enemyArea);
        _hand.CardPlayed += OnCardPlayed;
        _hand.SetCards(BuildHand());
        root.AddView(_hand);

        var bar = UI.HBox();
        bar.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        bar.SetPadding(Dp(16), Dp(10), Dp(16), Dp(16));

        var retreat = ThemeButtons.Danger(this, "撤 退");
        retreat.Click += (_, _) => Finish();
        var retreatLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        retreatLp.SetMargins(0, 0, Dp(8), 0);
        retreat.LayoutParameters = retreatLp;

        var auto = ThemeButtons.Neon(this, "自动战斗");
        auto.Click += (_, _) => OnAutoBattle();
        var autoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        autoLp.SetMargins(Dp(8), 0, 0, 0);
        auto.LayoutParameters = autoLp;

        bar.AddView(retreat);
        bar.AddView(auto);
        root.AddView(bar);

        Motion.Fade(root, Motion.Trans);
        return root;
    }

    private void BuildTeam()
    {
        _team.Clear();
        foreach (var ch in GameState.Owned().Take(5))
        {
            if (ch == null) continue;
            var st = GameState.ComputeStats(ch);   // 含等级/突破/天赋/升星 +5%/星 加成
            _team.Add(new Combatant { Stats = st, Hp = st.Hp, Name = ch.Name, Rarity = ch.Rarity, Element = ch.Element });
        }
        _teamPower = _team.Sum(c => c.Stats.Atk);
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

        var name = UI.Text(_enemyName, 18, AppTheme.Text1, bold: true);
        name.Gravity = GravityFlags.CenterHorizontal;
        name.SetPadding(0, Dp(10), 0, 0);
        inner.AddView(name);

        var hpBar = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        hpBar.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(14)) { TopMargin = Dp(10) };
        hpBar.Background = UI.RoundRect(SetA(AppTheme.Surface, 220), 7);
        _enemyFill = new View(this);
        _enemyFill.LayoutParameters = new LinearLayout.LayoutParams(0, Dp(14), 1f);
        _enemyFill.Background = GoldFill();
        ((GradientDrawable)_enemyFill.Background).SetCornerRadius(Dp(7));
        hpBar.AddView(_enemyFill);
        inner.AddView(hpBar);

        _enemyHpText = UI.Text($"HP {_enemyHp} / {_enemyMaxHp}", 11, AppTheme.Text2);
        _enemyHpText.Gravity = GravityFlags.CenterHorizontal;
        _enemyHpText.SetPadding(0, Dp(5), 0, 0);
        inner.AddView(_enemyHpText);

        var defText = UI.Text($"DEF {_enemyDef}", 11, AppTheme.Frost);
        defText.Gravity = GravityFlags.CenterHorizontal;
        inner.AddView(defText);

        panel.AddView(inner);
        return panel;
    }

    private List<View> BuildHand()
    {
        var cards = new List<View>();
        int w = Dp(92), h = Dp(128);
        for (int i = 0; i < 5; i++)
        {
            Combatant? c = i < _team.Count ? _team[i] : null;
            var flip = new FlipCardView(this);
            flip.SetFaces(BattleCardFace(c, w, h), BuildCardBack(w, h));
            flip.LayoutParameters = new FrameLayout.LayoutParams(w, h)
            {
                Gravity = GravityFlags.CenterHorizontal | GravityFlags.Bottom,
                BottomMargin = Dp(10)
            };
            _cardUnit[flip] = c;
            if (c != null) _unitCard[c] = flip;
            cards.Add(flip);
        }
        return cards;
    }

    private View BattleCardFace(Combatant? c, int w, int h)
    {
        int rarity = c?.Rarity ?? 1;
        var rc = AppTheme.RarityColor(rarity);
        var elem = c?.Element ?? "Flame";

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

        var name = UI.Text(c?.Name ?? "空位", 12, AppTheme.Text1, bold: true);
        name.Gravity = GravityFlags.CenterHorizontal;
        name.SetMaxLines(1);
        name.Ellipsize = TextUtils.TruncateAt.End;
        name.SetPadding(0, Dp(2), 0, 0);
        inner.AddView(name);

        int atk = c?.Stats.Atk ?? 0;
        var atkRow = UI.Text(c == null ? "—" : $"ATK {atk}", 11, AppTheme.Gold, bold: true);
        atkRow.Gravity = GravityFlags.CenterHorizontal;
        atkRow.SetPadding(0, Dp(3), 0, 0);
        inner.AddView(atkRow);

        var hpRow = UI.Text(c == null ? "" : $"HP {c.Hp}/{c.Stats.Hp}", 11, AppTheme.Frost);
        hpRow.Gravity = GravityFlags.CenterHorizontal;
        inner.AddView(hpRow);
        if (c != null) _unitHp[c] = hpRow;

        box.AddView(inner);
        return box;
    }

    private View BuildCardBack(int w, int h)
    {
        return CardBack.Build(this, w, h);
    }

    private void OnCardPlayed(View card)
    {
        if (_over) return;
        if (!_cardUnit.TryGetValue(card, out var c) || c == null || c.Dead) return;

        // 玩家攻击（攻方属性由 ComputeStats 生成，已含升星加成）
        int dmg = BattleSimulator.StrikeDamage(c.Stats, _enemyStats);
        _enemyHp = System.Math.Max(0, _enemyHp - dmg);
        _turns++;
        UpdateEnemyBar();
        Toast($"{c.Name} 造成 {dmg} 伤害");
        if (_enemyHp <= 0) { EndBattle(true); return; }

        // 敌人按攻防还手（双向）
        int cdmg = BattleSimulator.StrikeDamage(_enemyStats, c.Stats);
        c.Hp = System.Math.Max(0, c.Hp - cdmg);
        UpdateCard(c);
        Toast($"敌人反击，{c.Name} -{cdmg}");
        if (c.Dead)
        {
            if (_unitCard.TryGetValue(c, out var cv)) cv.Alpha = 0.35f;
            if (_team.All(x => x.Dead)) { EndBattle(false); return; }
        }
    }

    private void OnAutoBattle()
    {
        if (_over) return;
        if (_team.Count == 0) { Toast("无出战角色"); return; }
        // 复用 BattleSimulator.Simulate 跑整队结算（攻方属性含升星加成）
        var team = _team.Select(x => new UnitStats
        {
            Atk = x.Stats.Atk, Def = x.Stats.Def, Hp = x.Stats.Hp, Spd = x.Stats.Spd, CharacterId = x.Stats.CharacterId
        }).ToArray();
        var enemy = new UnitStats { Atk = _enemyStats.Atk, Def = _enemyStats.Def, Hp = _enemyMaxHp, Spd = _enemyStats.Spd, CharacterId = "enemy" };
        var result = new BattleSimulator(new System.Random()).Simulate(team, new[] { enemy }, 50);
        _enemyHp = result.Victory ? 0 : _enemyMaxHp;
        UpdateEnemyBar();
        EndBattle(result.Victory, result.Turns, result.RemainingHp);
    }

    private void EndBattle(bool victory) => EndBattle(victory, _turns, _team.Where(x => !x.Dead).Sum(x => x.Hp));

    private void EndBattle(bool victory, int turns, int remainingHp)
    {
        if (_over) return;
        _over = true;
        GameState.Service.RecordBattle(new BattleRecord
        {
            EnemyName = _enemyName,
            EnemyElement = _enemyElement,
            Victory = victory,
            Turns = turns,
            RemainingHp = remainingHp,
            TeamPower = _teamPower,
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
        });
        ShowResult(victory, turns, remainingHp);
    }

    private void ShowResult(bool victory, int turns, int remainingHp)
    {
        var overlay = new FrameLayout(this);
        overlay.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        overlay.SetBackgroundColor(SetA(Color.Black, 180));
        overlay.Clickable = true;

        var panel = new LinearLayout(this) { Orientation = Orientation.Vertical };
        panel.LayoutParameters = new FrameLayout.LayoutParams(Dp(300), ViewGroup.LayoutParams.WrapContent) { Gravity = GravityFlags.Center };
        panel.SetPadding(Dp(20), Dp(20), Dp(20), Dp(20));
        panel.Background = UI.GlassPanel(16, gold: true);

        var title = UI.Text(victory ? "胜 利" : "战 败", 28, victory ? AppTheme.Gold : AppTheme.Frost, bold: true);
        title.Gravity = GravityFlags.CenterHorizontal;
        panel.AddView(title);

        panel.AddView(UI.Text($"回合 {turns}   ·   剩余血量 {remainingHp}", 13, AppTheme.Text2));

        var recTitle = UI.Text("近期战绩", 12, AppTheme.Text2, bold: true);
        recTitle.SetPadding(0, Dp(10), 0, Dp(2));
        panel.AddView(recTitle);
        var recs = GameState.Service.GetBattleRecords();
        int n = 0;
        for (int i = recs.Count - 1; i >= 0 && n < 3; i--, n++)
        {
            var r = recs[i];
            panel.AddView(UI.Text($"{(r.Victory ? "胜" : "负")} {r.EnemyName} · {r.Turns}回合 · 战力{r.TeamPower}", 12, AppTheme.Text2));
        }

        var btnRow = UI.HBox();
        btnRow.SetPadding(0, Dp(12), 0, 0);
        var again = ThemeButtons.Gold(this, "再 战");
        again.Click += (_, _) => Restart();
        var back = ThemeButtons.Neon(this, "返 回");
        back.Click += (_, _) => Finish();
        btnRow.AddView(again);
        btnRow.AddView(back);
        panel.AddView(btnRow);

        overlay.AddView(panel);
        AddContentView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent));
    }

    private void Restart()
    {
        _enemyHp = _enemyMaxHp;
        _turns = 0;
        _over = false;
        foreach (var c in _team) c.Hp = c.Stats.Hp;
        SetContentView(Build()); // _stateInit 为 true，保留敌人元素与队伍，仅重置血量/回合
    }

    private void UpdateCard(Combatant c)
    {
        if (_unitHp.TryGetValue(c, out var hp)) hp.Text = $"HP {c.Hp}/{c.Stats.Hp}";
        if (c.Dead && _unitCard.TryGetValue(c, out var cv)) cv.Alpha = 0.35f;
    }

    private void UpdateEnemyBar()
    {
        float ratio = _enemyMaxHp > 0 ? (float)_enemyHp / _enemyMaxHp : 0f;
        _enemyFill.LayoutParameters = new LinearLayout.LayoutParams(0, Dp(14), ratio);
        _enemyFill.RequestLayout();
        _enemyHpText.Text = $"HP {_enemyHp} / {_enemyMaxHp}";
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
