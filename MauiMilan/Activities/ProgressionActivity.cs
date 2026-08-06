using Android.App;
using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Text;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Domain.Battle;
using Milan.Infrastructure.EventBus;
using Milan.Maui;
using Milan.Maui.Services;
using System;
using System.Collections.Generic;
using System.Linq;

namespace Milan.Maui.Activities;

/// <summary>
/// 养成系统全屏页（暗夜神性·诸神黄昏）。
/// Hero 立绘 + 玻璃面板四块：等级/经验、突破、属性、天赋树。
/// 所有消费操作走 GameService（先校验后扣、落盘失败回滚），完成后 Refresh 面板并广播事件。
/// 订阅 CurrencyChanged / ProgressionChanged 保持跨页一致。
/// </summary>
[Activity(Label = "养成", ConfigurationChanges = Android.Content.PM.ConfigChanges.Orientation | Android.Content.PM.ConfigChanges.ScreenSize)]
public class ProgressionActivity : Activity
{
    private CharacterDataEntry _def = null!;
    private OwnedCharacterView _view = null!;
    private bool _owned;
    private ScrollView _scroll = null!;
    private View _hero = null!;
    private View[] _staged = null!;
    private readonly Handler _handler = new(Looper.MainLooper!);

    // 动态引用（Refresh 时更新）
    private TextView _levelText = null!;
    private TextView _capText = null!;
    private TextView _expText = null!;
    private View _expFill = null!;
    private View _expSpacer = null!;
    private View _btnLv1 = null!;
    private View _btnLv5 = null!;
    private View _btnLvMax = null!;
    private TextView _stageText = null!;
    private TextView _ascendCost = null!;
    private View _btnAscend = null!;
    private TextView _starText = null!;
    private TextView _starCostText = null!;
    private View _btnStarUp = null!;
    private LinearLayout _statsBox = null!;
    private TextView _talentPointText = null!;
    private LinearLayout _talentBox = null!;
    private TextView _softText = null!;
    private TextView _fragText = null!;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);

        var id = Intent.GetStringExtra("characterId");
        if (string.IsNullOrEmpty(id) || !ResolveCharacter(id)) { Finish(); return; }

        _scroll = BuildPage();
        SetContentView(_scroll);
        _scroll.Post(() => Motion.PlayEntrance(_scroll, _hero, 70, _staged));
    }

    protected override void OnResume()
    {
        base.OnResume();
        EventBus.Subscribe<CurrencyChanged>(OnCurrencyChanged);
        EventBus.Subscribe<ProgressionChanged>(OnProgressionChanged);
        Refresh();
    }

    protected override void OnPause()
    {
        base.OnPause();
        EventBus.UnsubscribeAll(this);
    }

    protected override void OnDestroy()
    {
        _handler.RemoveCallbacksAndMessages(null);
        base.OnDestroy();
    }

    // 内存压力处理已收口到 MauiApp.OnTrimMemory（进程级回调，覆盖全部页面）。

    bool ResolveCharacter(string id)
    {
        _def = GameState.Service.Characters.FirstOrDefault(c => c.CharacterId == id)!;
        if (_def == null) return false;
        var save = GameState.Service.SaveData.OwnedCharacters.FirstOrDefault(c => c.CharacterId == id);
        _owned = save != null;
        save ??= new Milan.Infrastructure.Save.CharacterSaveState { CharacterId = id, Level = 1, Stage = 1, Stars = 1 };
        _view = new OwnedCharacterView { Save = save, Def = _def };
        return true;
    }

    // ───────────────────────── 布局 ─────────────────────────

    ScrollView BuildPage()
    {
        var root = new ScrollView(this)
        {
            LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent),
            VerticalScrollBarEnabled = false
        };
        root.Background = new GradientDrawable(
            GradientDrawable.Orientation.TopBottom,
            new[] { AppTheme.BgMid.ToArgb(), AppTheme.BgDeepest.ToArgb() });

        var content = new LinearLayout(this) { Orientation = Orientation.Vertical };
        content.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);

        _hero = BuildHero((int)(Resources.DisplayMetrics.HeightPixels * 0.46));
        content.AddView(_hero);
        content.AddView(Spacer(12));

        content.AddView(BuildResourceBar());
        content.AddView(Spacer(12));

        var inner = new LinearLayout(this) { Orientation = Orientation.Vertical };
        inner.SetPadding(UI.Dp(16), 0, UI.Dp(16), UI.Dp(8));

        var staged = new System.Collections.Generic.List<View>();
        var sLv = BuildSection("等 级 与 经 验", BuildLevelPanel());
        inner.AddView(sLv); inner.AddView(Spacer(14)); staged.Add(sLv);
        var sAs = BuildSection("突 破", BuildAscendPanel());
        inner.AddView(sAs); inner.AddView(Spacer(14)); staged.Add(sAs);
        var sStar = BuildSection("升 星", BuildStarPanel());
        inner.AddView(sStar); inner.AddView(Spacer(14)); staged.Add(sStar);
        var sSt = BuildSection("属 性", BuildStatsPanel());
        inner.AddView(sSt); inner.AddView(Spacer(14)); staged.Add(sSt);
        var sTa = BuildSection("天 赋", BuildTalentPanel());
        inner.AddView(sTa); staged.Add(sTa);

        inner.AddView(Spacer(24));
        content.AddView(inner);
        root.AddView(content);

        _scroll = root;
        _staged = staged.ToArray();
        return root;
    }

    View BuildHero(int heightPx)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var frame = new FrameLayout(this);
        frame.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, heightPx);

        var portrait = new Parallax3DPortraitView(this).Bind(_view);
        portrait.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        frame.AddView(portrait);

        var fade = new View(this);
        fade.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(140)) { Gravity = GravityFlags.Bottom };
        fade.Background = new GradientDrawable(GradientDrawable.Orientation.TopBottom,
            new[] { Color.Argb(0, 11, 6, 18).ToArgb(), AppTheme.BgDeepest.ToArgb() });
        frame.AddView(fade);

        var rarityCol = AppTheme.RarityColor(_view.Rarity);
        frame.AddView(BuildNameplate(rarityCol));

        // 返回（左上）
        var back = UI.Text("‹ 返 回", 16, AppTheme.Gold, bold: true);
        back.SetPadding(Dp(12), Dp(8), Dp(12), Dp(8));
        back.Background = UI.GlassPanel(20, gold: true);
        back.Clickable = true; back.Focusable = true;
        UI.TapFeedback(back, Finish);
        back.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent)
        { Gravity = GravityFlags.Top | GravityFlags.Left, TopMargin = Dp(40), LeftMargin = Dp(14) };
        frame.AddView(back);

        // 左右切换
        var prev = BuildArrow("‹", true);
        var next = BuildArrow("›", false);
        frame.AddView(prev); frame.AddView(next);

        return frame;
    }

    View BuildNameplate(Color rarityCol)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var (eFrom, _, _, eGlyph) = ElementTheme.For(_view.Element);

        var plate = new LinearLayout(this) { Orientation = Orientation.Vertical };
        plate.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(150)) { Gravity = GravityFlags.Bottom };
        plate.Background = new GradientDrawable(GradientDrawable.Orientation.TopBottom,
            new[] { Color.Argb(0, 11, 6, 18).ToArgb(), Color.Argb(190, 7, 4, 15).ToArgb() });
        plate.SetPadding(Dp(20), 0, Dp(20), Dp(16));
        plate.SetGravity(GravityFlags.Bottom);

        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetGravity(GravityFlags.CenterVertical);

        var badge = UI.Text(AppTheme.RarityName(_view.Rarity), 13, rarityCol, bold: true);
        var rBg = new GradientDrawable();
        rBg.SetCornerRadius(Dp(7));
        rBg.SetColor(Color.Argb(45, rarityCol.R, rarityCol.G, rarityCol.B));
        rBg.SetStroke(Dp(1), Color.Argb(150, rarityCol.R, rarityCol.G, rarityCol.B));
        badge.Background = rBg;
        badge.SetPadding(Dp(10), Dp(4), Dp(10), Dp(4));
        row.AddView(badge);

        var name = UI.Text(_view.Name, 27, AppTheme.Text1, bold: true);
        name.SetShadowLayer(8, 0, 2, Color.Argb(160, 0, 0, 0));
        name.SetPadding(Dp(14), 0, Dp(14), 0);
        name.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        row.AddView(name);

        var elem = UI.Text(eGlyph, 15, eFrom, bold: true);
        elem.Gravity = GravityFlags.Center;
        elem.Background = UI.RoundRect(Color.Argb(50, eFrom.R, eFrom.G, eFrom.B), 18);
        elem.LayoutParameters = new LinearLayout.LayoutParams(Dp(34), Dp(34));
        row.AddView(elem);
        plate.AddView(row);

        if (!string.IsNullOrEmpty(_view.Title))
            plate.AddView(UI.Text(_view.Title, 14, rarityCol));
        return plate;
    }

    View BuildArrow(string text, bool left)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var btn = new TextView(this) { Text = text, Gravity = GravityFlags.Center };
        btn.SetTextColor(AppTheme.Gold);
        btn.SetTextSize(ComplexUnitType.Sp, 32);
        btn.SetShadowLayer(12, 0, 0, Color.Argb(200, AppTheme.GoldHi.R, AppTheme.GoldHi.G, AppTheme.GoldHi.B));
        btn.LayoutParameters = new FrameLayout.LayoutParams(Dp(44), Dp(44))
        { Gravity = (left ? GravityFlags.CenterVertical | GravityFlags.Left : GravityFlags.CenterVertical | GravityFlags.Right) };
        btn.Background = UI.GlassPanel(22, gold: true);
        btn.Clickable = true; btn.Focusable = true;
        UI.TapFeedback(btn, () => SwitchCharacter(left ? -1 : 1));
        return btn;
    }

    void SwitchCharacter(int delta)
    {
        var chars = GameState.Service.Characters;
        var idx = chars.FindIndex(c => c.CharacterId == _def.CharacterId);
        if (idx < 0) return;
        var next = (idx + delta + chars.Count) % chars.Count;
        // 养成页内切角色必须停留在养成页；此前误指向 CharacterDetailActivity，点箭头会被踢出养成流程。
        var intent = new Intent(this, typeof(ProgressionActivity));
        intent.PutExtra("characterId", chars[next].CharacterId);
        StartActivity(intent);
        Finish();
#pragma warning disable CA1422
        OverridePendingTransition(Android.Resource.Animation.SlideInLeft, Android.Resource.Animation.SlideOutRight);
#pragma warning restore CA1422
    }

    View BuildSection(string title, View panel)
    {
        var sec = new LinearLayout(this) { Orientation = Orientation.Vertical };
        sec.AddView(SectionTitle(title));
        sec.AddView(panel);
        return sec;
    }

    View SectionTitle(string s)
    {
        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetGravity(GravityFlags.CenterVertical);
        row.SetPadding(0, 0, 0, UI.Dp(10));
        var bar = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(3), UI.Dp(16)) };
        bar.Background = UI.RoundRect(AppTheme.Gold, 2);
        row.AddView(bar);
        var t = UI.Text(s, 14, AppTheme.Text1, bold: true);
        t.LetterSpacing = 0.18f;
        t.SetPadding(UI.Dp(10), 0, 0, 0);
        row.AddView(t);
        return row;
    }

    // ───────────────────────── 资源条 ─────────────────────────

    View BuildResourceBar()
    {
        var bar = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        bar.SetPadding(UI.Dp(16), UI.Dp(10), UI.Dp(16), UI.Dp(10));
        bar.Background = UI.GlassPanel(14, gold: false);

        _softText = UI.Text("", 15, AppTheme.Gold, bold: true);
        UI.Tabular(_softText);
        bar.AddView(Chip("✦", AppTheme.Gold, _softText));

        bar.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) });

        _fragText = UI.Text("", 15, AppTheme.Frost, bold: true);
        UI.Tabular(_fragText);
        bar.AddView(Chip("❖", AppTheme.Frost, _fragText));
        return bar;
    }

    View Chip(string glyph, Color col, TextView value)
    {
        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetGravity(GravityFlags.CenterVertical);
        var g = UI.Text(glyph, 16, col, bold: true);
        g.SetPadding(0, 0, UI.Dp(6), 0);
        row.AddView(g);
        row.AddView(value);
        return row;
    }

    // ───────────────────────── 等级 / 经验 ─────────────────────────

    View BuildLevelPanel()
    {
        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14);
        box.SetPadding(UI.Dp(16), UI.Dp(14), UI.Dp(16), UI.Dp(14));

        var head = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        head.SetGravity(GravityFlags.CenterVertical);
        _levelText = UI.Text("Lv.1", 30, AppTheme.Gold, bold: true);
        head.AddView(_levelText);
        _capText = UI.Text("/ 20", 14, AppTheme.Text2);
        _capText.SetPadding(UI.Dp(8), 0, 0, 0);
        head.AddView(_capText);
        head.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) });
        _expText = UI.Text("", 12, AppTheme.Text3);
        UI.Tabular(_expText);
        head.AddView(_expText);
        box.AddView(head);
        box.AddView(Spacer(8));

        // 经验条（暗轨 + 金填充，权重控制比例）
        var track = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        track.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, UI.Dp(12));
        track.Background = UI.RoundRect(Color.Argb(60, 0, 0, 0), 6);
        _expFill = new View(this) { Background = UI.RoundRect(AppTheme.Gold, 6) };
        _expSpacer = new View(this);
        track.AddView(_expFill);
        track.AddView(_expSpacer);
        box.AddView(track);
        box.AddView(Spacer(12));

        var btns = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        _btnLv1 = ThemeButtons.Gold(this, "升级 ×1");
        _btnLv5 = ThemeButtons.Neon(this, "升级 ×5");
        _btnLvMax = ThemeButtons.Neon(this, "升满");
        foreach (var b in new[] { _btnLv1, _btnLv5, _btnLvMax })
        {
            b.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f) { Gravity = GravityFlags.CenterVertical };
            b.SetPadding(UI.Dp(6), 0, UI.Dp(6), 0);
        }
        _btnLv1.Click += (_, _) => OnLevel(1);
        _btnLv5.Click += (_, _) => OnLevel(5);
        _btnLvMax.Click += (_, _) => OnLevel(int.MaxValue);
        btns.AddView(_btnLv1); btns.AddView(_btnLv5); btns.AddView(_btnLvMax);
        box.AddView(btns);
        return box;
    }

    // ───────────────────────── 突破 ─────────────────────────

    View BuildAscendPanel()
    {
        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14);
        box.SetPadding(UI.Dp(16), UI.Dp(14), UI.Dp(16), UI.Dp(14));

        var head = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        head.SetGravity(GravityFlags.CenterVertical);
        _stageText = UI.Text("", 18, AppTheme.Frost, bold: true);
        head.AddView(_stageText);
        head.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) });
        _ascendCost = UI.Text("", 13, AppTheme.Text2);
        UI.Tabular(_ascendCost);
        head.AddView(_ascendCost);
        box.AddView(head);
        box.AddView(Spacer(10));

        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        _btnAscend = ThemeButtons.Gold(this, "突 破");
        _btnAscend.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        _btnAscend.Click += (_, _) => OnAscend();
        row.AddView(_btnAscend);
        box.AddView(row);
        return box;
    }

    // ───────────────────────── 升星 ─────────────────────────

    View BuildStarPanel()
    {
        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14);
        box.SetPadding(UI.Dp(16), UI.Dp(14), UI.Dp(16), UI.Dp(14));

        var head = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        head.SetGravity(GravityFlags.CenterVertical);
        _starText = UI.Text("", 18, AppTheme.Frost, bold: true);
        head.AddView(_starText);
        head.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) });
        _starCostText = UI.Text("", 13, AppTheme.Text2);
        UI.Tabular(_starCostText);
        head.AddView(_starCostText);
        box.AddView(head);
        box.AddView(Spacer(10));

        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        _btnStarUp = ThemeButtons.Gold(this, "升 星");
        _btnStarUp.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        _btnStarUp.Click += (_, _) => OnStarUp();
        row.AddView(_btnStarUp);
        box.AddView(row);
        return box;
    }

    // ───────────────────────── 属性 ─────────────────────────

    View BuildStatsPanel()
    {
        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14);
        box.SetPadding(UI.Dp(16), UI.Dp(14), UI.Dp(16), UI.Dp(14));
        _statsBox = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.AddView(_statsBox);
        return box;
    }

    void FillStats()
    {
        _statsBox.RemoveAllViews();
        var cur = GameState.ComputeStats(_view);
        int cap = GameState.Service.MaxLevelForStage(_view.Save.Stage);
        int maxStg = _def.MaxStage;
        int maxStars = _def.MaxStars;
        var nextLv = _view.Save.Level < cap ? GameState.ComputeStatsAt(_view, _view.Save.Level + 1, _view.Save.Stage) : (UnitStats?)null;
        var nextStg = _view.Save.Stage < maxStg ? GameState.ComputeStatsAt(_view, _view.Save.Level, _view.Save.Stage + 1) : (UnitStats?)null;
        var nextStar = _view.Save.Stars < maxStars ? GameState.ComputeStatsAt(_view, _view.Save.Level, _view.Save.Stage, _view.Save.Stars + 1) : (UnitStats?)null;

        var rows = new (string cn, int val, int? nl, int? ns, int? nstar)[]
        {
            ("攻击", cur.Atk, nextLv?.Atk, nextStg?.Atk, nextStar?.Atk),
            ("防御", cur.Def, nextLv?.Def, nextStg?.Def, nextStar?.Def),
            ("生命", cur.Hp, nextLv?.Hp, nextStg?.Hp, nextStar?.Hp),
            ("速度", cur.Spd, nextLv?.Spd, nextStg?.Spd, nextStar?.Spd),
        };
        foreach (var r in rows)
        {
            var line = new LinearLayout(this) { Orientation = Orientation.Horizontal };
            line.SetGravity(GravityFlags.CenterVertical);
            line.SetPadding(0, UI.Dp(7), 0, UI.Dp(7));
            line.AddView(UI.Text(r.cn, 15, AppTheme.Text2, bold: true));
            line.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) });
            var val = UI.Text(r.val.ToString("N0"), 17, AppTheme.Text1, bold: true);
            UI.Tabular(val);
            line.AddView(val);
            _statsBox.AddView(line);

            if (r.nl.HasValue || r.ns.HasValue || r.nstar.HasValue)
            {
                var sub = new LinearLayout(this) { Orientation = Orientation.Horizontal };
                sub.SetGravity(GravityFlags.CenterVertical);
                var sb = new System.Text.StringBuilder();
                if (r.nl.HasValue) sb.Append($"Lv+1 → {r.nl.Value:N0}");
                if (r.ns.HasValue) sb.Append((sb.Length > 0 ? "   ·   " : "") + $"突破 → {r.ns.Value:N0}");
                if (r.nstar.HasValue) sb.Append((sb.Length > 0 ? "   ·   " : "") + $"升星 → {r.nstar.Value:N0}");
                var subTv = UI.Text(sb.ToString(), 12, AppTheme.Frost);
                UI.Tabular(subTv);
                sub.AddView(subTv);
                _statsBox.AddView(sub);
            }
            _statsBox.AddView(WoWDivider());
        }
    }

    View WoWDivider()
    {
        var v = new View(this);
        v.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, UI.Dp(1));
        v.SetBackgroundColor(Color.Argb(24, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B));
        return v;
    }

    // ───────────────────────── 天赋 ─────────────────────────

    View BuildTalentPanel()
    {
        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14);
        box.SetPadding(UI.Dp(16), UI.Dp(14), UI.Dp(16), UI.Dp(14));

        var head = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        head.SetGravity(GravityFlags.CenterVertical);
        head.AddView(UI.Text("天赋点", 13, AppTheme.Text2));
        _talentPointText = UI.Text("", 16, AppTheme.Gold, bold: true);
        _talentPointText.SetPadding(UI.Dp(8), 0, 0, 0);
        UI.Tabular(_talentPointText);
        head.AddView(_talentPointText);
        box.AddView(head);
        box.AddView(Spacer(10));

        _talentBox = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        _talentBox.SetPadding(0, 0, 0, 0);
        box.AddView(_talentBox);
        return box;
    }

    void FillTalent()
    {
        _talentBox.RemoveAllViews();
        var tree = GameState.Service.GetTalentTree(_def.CharacterId);
        var branches = tree?.BranchIds ?? new List<string> { "branch_power", "branch_defense", "branch_utility" };
        var branchColor = new Dictionary<string, Color>
        {
            ["branch_power"] = AppTheme.Danger,
            ["branch_defense"] = AppTheme.Frost,
            ["branch_utility"] = AppTheme.Violet,
        };
        var branchName = new Dictionary<string, string>
        {
            ["branch_power"] = "强攻",
            ["branch_defense"] = "坚壁",
            ["branch_utility"] = "灵动",
        };
        var allocated = _view.Save.TalentPoints ?? new List<string>();

        foreach (var br in branches)
        {
            var col = branchColor.GetValueOrDefault(br, AppTheme.Text2);
            var colBox = new LinearLayout(this) { Orientation = Orientation.Vertical };
            colBox.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
            colBox.SetPadding(UI.Dp(4), 0, UI.Dp(4), 0);

            var lab = UI.Text(branchName.GetValueOrDefault(br, br), 13, col, bold: true);
            lab.Gravity = GravityFlags.CenterHorizontal;
            colBox.AddView(lab);
            colBox.AddView(Spacer(8));

            var nodes = (tree?.Nodes ?? new List<TalentNodeData>())
                .OfType<TalentNodeData>().Where(n => n.BranchId == br).ToList();
            foreach (var node in nodes)
            {
                bool isAlloc = allocated.Contains(node.NodeId);
                bool canAlloc = !isAlloc && _owned
                    && _view.Save.UnspentPoints >= node.Cost
                    && GameState.Service.CanAllocateTalent(_def.CharacterId, node.NodeId);
                colBox.AddView(BuildTalentNode(node, col, isAlloc, canAlloc));
                colBox.AddView(Spacer(8));
            }
            _talentBox.AddView(colBox);
        }
    }

    View BuildTalentNode(TalentNodeData node, Color col, bool allocated, bool canAlloc)
    {
        var node2 = new LinearLayout(this) { Orientation = Orientation.Vertical };
        node2.SetPadding(UI.Dp(8), UI.Dp(8), UI.Dp(8), UI.Dp(8));
        int radius = UI.Dp(12);
        if (allocated)
        {
            node2.Background = UI.RoundRect(Color.Argb(70, col.R, col.G, col.B), radius, strokePx: 2, strokeColor: col);
        }
        else if (canAlloc)
        {
            node2.Background = UI.RoundRect(Color.Argb(28, col.R, col.G, col.B), radius, strokePx: 2, strokeColor: col);
        }
        else
        {
            node2.Background = UI.RoundRect(Color.Argb(30, 20, 16, 30), radius, strokePx: 1, strokeColor: AppTheme.Stroke);
        }

        var name = UI.Text(node.DisplayName + (allocated ? " ✓" : ""), 13, allocated || canAlloc ? col : AppTheme.Text3, bold: true);
        name.Gravity = GravityFlags.CenterHorizontal;
        node2.AddView(name);

        var cost = UI.Text($"耗费 {node.Cost}", 11, allocated || canAlloc ? AppTheme.Text2 : AppTheme.Text3);
        cost.Gravity = GravityFlags.CenterHorizontal;
        cost.SetPadding(0, UI.Dp(3), 0, 0);
        node2.AddView(cost);

        if (canAlloc)
        {
            node2.Clickable = true; node2.Focusable = true;
            UI.TapFeedback(node2, () => OnTalent(node.NodeId));
        }
        return node2;
    }

    // ───────────────────────── 操作 ─────────────────────────

    void OnLevel(int n)
    {
        if (!_owned) { Toast("未拥有该角色"); return; }
        if (!GameState.Service.LevelUp(_def.CharacterId, n))
            Toast(GameState.Service.MaxLevelForStage(_view.Save.Stage) <= _view.Save.Level ? "已满级" : "星尘不足");
        Refresh();
    }

    void OnAscend()
    {
        if (!_owned) { Toast("未拥有该角色"); return; }
        if (!GameState.Service.Ascend(_def.CharacterId))
        {
            int frags = GameState.Service.AscendFragments(_view.Save.Stage);
            Toast(GameState.Service.GetStarFragments() < frags ? "星魂碎片不足" : "星尘不足");
        }
        Refresh();
    }

    void OnStarUp()
    {
        if (!_owned) { Toast("未拥有该角色"); return; }
        if (!GameState.Service.StarUp(_def.CharacterId))
            Toast(_view.Save.Stars >= _def.MaxStars ? "已满星" : "星魂碎片不足");
        Refresh();
    }

    void OnTalent(string nodeId)
    {
        if (!_owned) { Toast("未拥有该角色"); return; }
        if (!GameState.Service.AllocateTalent(_def.CharacterId, nodeId))
            Toast("无法满足前置或天赋点不足");
        Refresh();
    }

    void Toast(string msg) => Android.Widget.Toast.MakeText(this, msg, Android.Widget.ToastLength.Short)?.Show();

    // ───────────────────────── 刷新 ─────────────────────────

    void Refresh()
    {
        var save = _view.Save;
        int cap = GameState.Service.MaxLevelForStage(save.Stage);
        int soft = GameState.Service.SaveData.SoftCurrency;
        int frags = GameState.Service.GetStarFragments();

        _softText.Text = $"星尘 {soft:N0}";
        _fragText.Text = $"星魂碎片 {frags:N0}";

        _levelText.Text = $"Lv.{save.Level}";
        _capText.Text = $"/ {cap}";
        var (cur, need) = GameState.Service.ExpProgress(_def.CharacterId);
        _expText.Text = $"{cur} / {need} EXP";
        _expFill.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MatchParent, System.Math.Max(0.001f, cur));
        _expSpacer.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MatchParent, System.Math.Max(0.001f, need - cur));

        bool canLevel = _owned && save.Level < cap && soft >= GameState.Service.LevelCost(save.Level);
        _btnLv1.Enabled = canLevel;
        _btnLv5.Enabled = canLevel;
        _btnLvMax.Enabled = canLevel;
        _btnLv1.Alpha = _btnLv5.Alpha = _btnLvMax.Alpha = canLevel ? 1f : 0.4f;

        // 突破
        int maxStg = _def.MaxStage;
        bool atMax = save.Stage >= maxStg;
        int aFrag = GameState.Service.AscendFragments(save.Stage);
        int aSoft = GameState.Service.AscendSoft(save.Stage);
        _stageText.Text = atMax
            ? $"突破阶段 {save.Stage} / {maxStg}（已满）"
            : $"突破阶段 {save.Stage} / {maxStg}";
        _ascendCost.Text = atMax ? "—" : $"❖ {aFrag}  +  ✦ {aSoft:N0}";
        bool canAscend = _owned && !atMax && frags >= aFrag && soft >= aSoft;
        _btnAscend.Enabled = canAscend;
        _btnAscend.Alpha = canAscend ? 1f : 0.4f;

        // 升星
        int maxStars = _def.MaxStars;
        bool starMax = save.Stars >= maxStars;
        int sFrag = GameState.Service.StarUpFragments(save.Stars);
        // 注意：不能用 "★".PadLeft(n,'★') —— PadLeft 的语义是"补齐到总长 n"，
        // n<=1 时原样返回一个字符，Stars=0 会画出一颗实心星；n 为负还会抛异常。
        string filled = new string('★', Math.Max(0, save.Stars));
        string empty = new string('☆', Math.Max(0, maxStars - save.Stars));
        _starText.Text = starMax
            ? $"{filled} 满星"
            : $"{filled}{empty}  {save.Stars}/{maxStars}";
        _starCostText.Text = starMax ? "—" : $"❖ {sFrag:N0}";
        bool canStar = _owned && !starMax && frags >= sFrag;
        _btnStarUp.Enabled = canStar;
        _btnStarUp.Alpha = canStar ? 1f : 0.4f;

        FillStats();
        _talentPointText.Text = $"× {save.UnspentPoints}";
        FillTalent();
    }

    void OnCurrencyChanged(CurrencyChanged _) => Refresh();
    void OnProgressionChanged(ProgressionChanged _) => Refresh();

    // 唯一实现在 UI.Spacer，避免 9 个页面各维护一份换算逻辑。
    View Spacer(int h) => UI.Spacer(this, h);
}
