using Android.App;
using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Text;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui;
using Milan.Maui.Services;

namespace Milan.Maui.Activities;

[Activity(Label = "角色详情")]
public class CharacterDetailActivity : Activity
{
    private CharacterDataEntry _def = null!;
    private OwnedCharacterView _view = null!;
    private bool _owned;
    private ScrollView? _page;
    private View? _heroView;
    private View[]? _staged;
    private readonly Handler _handler = new(Looper.MainLooper!);

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);

        var id = Intent.GetStringExtra("characterId");
        if (string.IsNullOrEmpty(id) || !ResolveCharacter(id)) { Finish(); return; }

        var page = BuildPage();
        SetContentView(page);
        page.Post(() => PlayEntrance());
    }

    void PlayEntrance()
    {
        // 错落入场：根淡入 → hero 淡入（不位移，避免与立绘漂浮冲突）→ 各内容区块上浮。
        if (_page != null && _heroView != null && _staged != null)
            Motion.PlayEntrance(_page, _heroView, 70, _staged);
    }

    protected override void OnDestroy()
    {
        _handler.RemoveCallbacksAndMessages(null);
        base.OnDestroy();
    }

    // 系统内存吃紧时释放武器图 native 缓存（VfxRenderer 内部 LRU，最多 16 张）。
    public override void OnTrimMemory(TrimMemory level)
    {
        base.OnTrimMemory(level);
        if (level >= TrimMemory.Moderate) VfxRenderer.TrimWeaponCache();
    }

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

    ScrollView BuildPage()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var screenH = Resources.DisplayMetrics.HeightPixels;

        var root = new ScrollView(this)
        {
            LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent),
            VerticalScrollBarEnabled = false
        };
        // 顶部略亮（BgMid）→ 底部深（BgDeepest），单一背光源的纵深感。
        root.Background = new GradientDrawable(
            GradientDrawable.Orientation.TopBottom,
            new[] { AppTheme.BgMid.ToArgb(), AppTheme.BgDeepest.ToArgb() });

        var content = new LinearLayout(this) { Orientation = Orientation.Vertical };
        content.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        content.SetPadding(0, 0, 0, 0);

        // ═══ HERO（立绘全屏 + 浮层铭牌）═══
        var hero = BuildHeroRegion((int)(screenH * 0.56));
        content.AddView(hero);
        content.AddView(Spacer(14));

        // ═══ 内容区（玻璃面板卡，左右留白）═══
        var inner = new LinearLayout(this) { Orientation = Orientation.Vertical };
        inner.SetPadding(Dp(16), 0, Dp(16), Dp(8));

        var staged = new System.Collections.Generic.List<View>();

        if (!string.IsNullOrEmpty(_def.Weapon))
        {
            var s = BuildSection("专 属 武 器", BuildWeaponPanel());
            inner.AddView(s); inner.AddView(Spacer(16));
            staged.Add(s);
        }

        var sStats = BuildSection("基 本 属 性", BuildStatsPanel());
        inner.AddView(sStats); inner.AddView(Spacer(16)); staged.Add(sStats);

        var sSkill = BuildSection("技 能", BuildSkillPanel());
        inner.AddView(sSkill); inner.AddView(Spacer(16)); staged.Add(sSkill);

        var sStory = BuildSection("背 景 故 事", BuildStoryPanel());
        inner.AddView(sStory); inner.AddView(Spacer(16)); staged.Add(sStory);

        var sVoice = BuildSection("语 音 / 台 词", BuildVoicePanel());
        inner.AddView(sVoice); staged.Add(sVoice);

        inner.AddView(Spacer(24));
        content.AddView(inner);

        _page = root;
        _heroView = hero;
        _staged = staged.ToArray();
        root.AddView(content);
        return root;
    }

    // ── HERO 区：立绘铺满 + 渐隐融入 + 浮层铭牌 + 悬浮操作 ──
    View BuildHeroRegion(int heightPx)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var rarityCol = AppTheme.RarityColor(_view.Rarity);

        var frame = new FrameLayout(this);
        frame.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, heightPx);

        // 立绘（2D 视差，暗夜神性·诸神黄昏）
        var portrait = new Parallax3DPortraitView(this).Bind(_view);
        portrait.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        frame.AddView(portrait);

        // 底部渐隐遮罩：立绘下缘柔和融入背景
        var fade = new View(this);
        fade.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(150))
        {
            Gravity = GravityFlags.Bottom
        };
        fade.Background = new GradientDrawable(
            GradientDrawable.Orientation.TopBottom,
            new[] { Color.Argb(0, 11, 6, 18).ToArgb(), AppTheme.BgDeepest.ToArgb() });
        frame.AddView(fade);

        // 未拥有遮罩
        if (!_owned)
        {
            var lockOverlay = new View(this);
            lockOverlay.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
            lockOverlay.SetBackgroundColor(Color.Argb(150, 0, 0, 0));
            var lockIcon = UI.Text("🔒 未获得", 18, AppTheme.Text2, bold: true);
            lockIcon.Gravity = GravityFlags.Center;
            lockIcon.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
            {
                Gravity = GravityFlags.Center
            };
            frame.AddView(lockOverlay);
            frame.AddView(lockIcon);
        }

        // 底部浮层铭牌（名字 / 称号 / 稀有度 / 元素）
        frame.AddView(BuildHeroNameplate());

        // 悬浮操作：返回（左上）+ 左右切换（两侧）
        var back = BuildBackButton();
        frame.AddView(back);
        var prevBtn = BuildArrow("‹", true);
        var nextBtn = BuildArrow("›", false);
        frame.AddView(prevBtn);
        frame.AddView(nextBtn);

        return frame;
    }

    View BuildHeroNameplate()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var rarityCol = AppTheme.RarityColor(_view.Rarity);
        var (eFrom, _, _, eGlyph) = ElementTheme.For(_view.Element);

        var plate = new LinearLayout(this) { Orientation = Orientation.Vertical };
        plate.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(156))
        {
            Gravity = GravityFlags.Bottom
        };
        // 上透明 → 下微暗，保证文字可读且压在立绘下缘
        plate.Background = new GradientDrawable(
            GradientDrawable.Orientation.TopBottom,
            new[] { Color.Argb(0, 11, 6, 18).ToArgb(), Color.Argb(190, 7, 4, 15).ToArgb() });
        plate.SetPadding(Dp(20), 0, Dp(20), Dp(18));
        plate.SetGravity(GravityFlags.Bottom);

        // 第一行：稀有度徽章 + 名字（权重1）+ 元素图标
        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetGravity(GravityFlags.CenterVertical);
        row.SetPadding(0, 0, 0, Dp(2));

        var rarityBadge = UI.Text(AppTheme.RarityName(_view.Rarity), 13, rarityCol, bold: true);
        var rBg = new GradientDrawable();
        rBg.SetCornerRadius(Dp(7));
        rBg.SetColor(Color.Argb(45, rarityCol.R, rarityCol.G, rarityCol.B));
        rBg.SetStroke(Dp(1), Color.Argb(150, rarityCol.R, rarityCol.G, rarityCol.B));
        rarityBadge.Background = rBg;
        rarityBadge.SetPadding(Dp(10), Dp(4), Dp(10), Dp(4));
        row.AddView(rarityBadge);

        var name = UI.Text(_view.Name, 27, AppTheme.Text1, bold: true);
        name.SetShadowLayer(8, 0, 2, Color.Argb(160, 0, 0, 0));
        name.SetPadding(Dp(14), 0, Dp(14), 0);
        name.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        row.AddView(name);

        var elemIcon = UI.Text(eGlyph, 15, eFrom, bold: true);
        elemIcon.Gravity = GravityFlags.Center;
        elemIcon.Background = UI.RoundRect(Color.Argb(50, eFrom.R, eFrom.G, eFrom.B), 18);
        elemIcon.LayoutParameters = new LinearLayout.LayoutParams(Dp(34), Dp(34));
        row.AddView(elemIcon);

        plate.AddView(row);

        // 称号
        if (!string.IsNullOrEmpty(_view.Title))
        {
            var title = UI.Text(_view.Title, 14, rarityCol);
            title.SetPadding(Dp(2), Dp(6), 0, 0);
            plate.AddView(title);
        }

        return plate;
    }

    View BuildBackButton()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var btn = UI.Text("‹ 返 回", 16, AppTheme.Gold, bold: true);
        btn.SetPadding(Dp(12), Dp(8), Dp(12), Dp(8));
        btn.Background = UI.GlassPanel(20, gold: true);
        btn.Clickable = true; btn.Focusable = true;
        UI.TapFeedback(btn, Finish);
        btn.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Top | GravityFlags.Left,
            TopMargin = Dp(40),
            LeftMargin = Dp(14)
        };
        return btn;
    }

    View BuildArrow(string text, bool left)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var btn = new TextView(this)
        {
            Text = text,
            Gravity = GravityFlags.Center
        };
        btn.SetTextColor(AppTheme.Gold);
        btn.SetTextSize(ComplexUnitType.Sp, 32);
        btn.SetShadowLayer(12, 0, 0, Color.Argb(200, AppTheme.GoldHi.R, AppTheme.GoldHi.G, AppTheme.GoldHi.B));
        var lp = new FrameLayout.LayoutParams(Dp(44), Dp(44));
        lp.Gravity = left ? GravityFlags.CenterVertical | GravityFlags.Left : GravityFlags.CenterVertical | GravityFlags.Right;
        btn.LayoutParameters = lp;
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
        var intent = new Intent(this, typeof(CharacterDetailActivity));
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

    // 武器舞台：圆角暗底 + 元素径向晕染 + 稀有度描边光环，居中绘制干净武器剪影
    private class WeaponPreviewView : View
    {
        private readonly string _wv;
        private readonly Color _rc, _eg;
        private readonly string _el;
        private readonly string _rarity;
        private float _phase;
        private bool _running;
        private bool _framePending;
        // #37/#38: Paint / Path / RectF 全部复用，避免每帧新建对象造成 GC 抖动。
        private readonly Paint _pBase = new() { AntiAlias = true };
        private readonly Paint _pArt = new() { AntiAlias = true, FilterBitmap = true };
        private readonly Paint _pRing = new() { AntiAlias = true };
        private readonly Android.Graphics.Path _clip = new();
        private readonly RectF _rect = new();
        private readonly RectF _wb = new();
        private readonly RectF _dst = new();
        private int _clipW, _clipH;

        public WeaponPreviewView(Context ctx, string wv, Color rc, Color eg, string element = "", string rarity = "") : base(ctx)
        {
            _wv = wv; _rc = rc; _eg = eg; _el = element; _rarity = rarity;
        }

        protected override void OnDraw(Canvas canvas)
        {
            base.OnDraw(canvas);
            if (Width <= 0 || Height <= 0 || string.IsNullOrEmpty(_wv)) return;
            var rad = UI.Dp(14);
            _rect.Set(0, 0, Width, Height);
            if (_clipW != Width || _clipH != Height)
            {
                _clip.Reset();
                _clip.AddRoundRect(_rect, rad, rad, Android.Graphics.Path.Direction.Cw);
                _clipW = Width; _clipH = Height;
            }
            var cx = Width / 2f;
            var cy = Height / 2f;

            // 暗底 + 元素晕染（裁切到圆角内）
            canvas.Save();
            canvas.ClipPath(_clip);
            _pBase.SetStyle(Paint.Style.Fill);
            _pBase.Color = Color.Argb(255, 0x10, 0x10, 0x18);
            canvas.DrawPath(_clip, _pBase);
            var R = Math.Max(Width, Height) * 0.72f;
            if (R > 0)
            {
                using (var sh = new RadialGradient(cx, cy, R,
                    UI.ColorLongs(Color.Argb(75, _eg.R, _eg.G, _eg.B), Color.Argb(0, 0, 0, 0)),
                    new[] { 0f, 1f }, Shader.TileMode.Clamp))
                {
                    _pBase.SetShader(sh);
                    canvas.DrawPath(_clip, _pBase);
                    _pBase.SetShader(null);
                }
            }
            canvas.Restore();

            // 居中武器：优先显示 AI 概念图 PNG，找不到则回退 Canvas 几何
            var pad = Math.Min(Width, Height) * 0.06f;
            _wb.Set(pad, pad, Width - pad, Height - pad);
            var bmp = VfxRenderer.GetWeaponBitmap(Context, _wv);
            if (bmp != null && !bmp.IsRecycled)
            {
                VfxRenderer.DrawWeaponBackdrop(canvas, _pArt, _wb, _phase, _eg, _el, _rarity);
                float side = Math.Min(Width, Height) * 0.94f;
                _dst.Set((Width - side) / 2f, (Height - side) / 2f, (Width + side) / 2f, (Height + side) / 2f);
                canvas.DrawBitmap(bmp, null, _dst, _pArt);
            }
            else
            {
                VfxRenderer.DrawWeapon(canvas, _wv, _wb, _phase, _pBase, _rc, _eg, _el, _rarity);
                _pBase.SetShader(null);
            }

            // 稀有度描边光环
            _pRing.SetStyle(Paint.Style.Stroke);
            _pRing.StrokeWidth = UI.Dp(2);
            _pRing.Color = Color.Argb(200, _rc.R, _rc.G, _rc.B);
            canvas.DrawRoundRect(_rect, rad, rad, _pRing);

            // #29: 帧驱动必须在绘制后推进，否则动画停在第一帧。
            Tick();
        }

        private void Tick()
        {
            _framePending = false;
            if (!_running) return;
            _phase += 0.045f;
            if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
            ScheduleFrame();
        }

        private void ScheduleFrame()
        {
            if (!_running || _framePending) return;
            _framePending = true;
            PostInvalidateDelayed(33);
        }

        protected override void OnAttachedToWindow()
        {
            base.OnAttachedToWindow();
            if (!_running) { _running = true; _framePending = false; ScheduleFrame(); }
        }
        protected override void OnDetachedFromWindow()
        {
            _running = false; _framePending = false;
            base.OnDetachedFromWindow();
        }
        protected override void OnWindowVisibilityChanged(ViewStates visibility)
        {
            base.OnWindowVisibilityChanged(visibility);
            if (visibility == ViewStates.Visible) { if (!_running) { _running = true; _framePending = false; ScheduleFrame(); } }
            else { _running = false; _framePending = false; }
        }
    }

    View BuildWeaponPanel()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var world = AppTheme.World(_view.World);
        var rarityCol = AppTheme.RarityColor(_view.Rarity);

        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14, gold: _owned);
        box.SetPadding(Dp(16), Dp(14), Dp(16), Dp(14));

        // 武器舞台（独立展示卡：圆角暗底 + 元素晕染 + 稀有度描边，干净呈现成形武器）
        // 仅 SSR(3)/UR(4) 角色展示专属武器；SR/R 不设计武器
        if (!string.IsNullOrEmpty(_def.WeaponVfx) && _def.BaseRarity >= 3)
        {
            var stage = new FrameLayout(this);
            stage.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(160));
            var preview = new WeaponPreviewView(this, _def.WeaponVfx, rarityCol, world.Glow, _view.Element, _view.Rarity.ToString());
            preview.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
            stage.AddView(preview);
            box.AddView(stage);
            box.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, Dp(10)) });
        }

        // 武器名 + UR 专属标签
        var head = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        head.SetGravity(GravityFlags.CenterVertical);

        // 元素字形圆形图标
        var (eFrom, _, _, eGlyph) = ElementTheme.For(_view.Element);
        var icon = UI.Text(eGlyph, 16, eFrom, bold: true);
        icon.Gravity = GravityFlags.Center;
        icon.Background = UI.RoundRect(Color.Argb(45, eFrom.R, eFrom.G, eFrom.B), 18);
        icon.LayoutParameters = new LinearLayout.LayoutParams(Dp(32), Dp(32));
        head.AddView(icon);

        var wname = UI.Text(_def.Weapon, 18, AppTheme.Gold, bold: true);
        wname.SetShadowLayer(4, 0, 1, Color.Argb(120, 0, 0, 0));

        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };

        var tag = UI.Text(_view.Rarity >= 4 ? "UR 专属" : "SSR 专属", 10, rarityCol, bold: true);
        var tBg = new GradientDrawable();
        tBg.SetCornerRadius(Dp(6));
        tBg.SetColor(Color.Argb(45, rarityCol.R, rarityCol.G, rarityCol.B));
        tBg.SetStroke(1, Color.Argb(150, rarityCol.R, rarityCol.G, rarityCol.B));
        tag.Background = tBg;
        tag.SetPadding(Dp(8), Dp(2), Dp(8), Dp(2));

        head.AddView(wname);
        head.AddView(spacer);
        head.AddView(tag);
        box.AddView(head);

        // 武器特效标签
        if (!string.IsNullOrEmpty(_def.WeaponVfx))
        {
            var vfx = UI.Text($"武器特效 · {_def.WeaponVfx}", 12, world.Glow);
            vfx.SetPadding(0, Dp(6), 0, 0);
            box.AddView(vfx);
        }

        // 背景故事式描述
        var desc = UI.Text(_def.WeaponDesc, 14, world.TextSecondary);
        desc.SetPadding(0, Dp(10), 0, 0);
        desc.SetLineSpacing(Dp(4), 1f);
        box.AddView(desc);

        return box;
    }

    View BuildStatsPanel()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var stats = GameState.ComputeStats(_view);
        var world = AppTheme.World(_view.World);

        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14);
        box.SetPadding(Dp(16), Dp(12), Dp(16), Dp(12));

        // 克制数据行：左标签 + 右等宽数值，行间细分隔线
        var items = new (string label, int val, Color col)[]
        {
            ("ATK", stats.Atk, AppTheme.Danger),
            ("DEF", stats.Def, AppTheme.Frost),
            ("HP",  stats.Hp,  AppTheme.Success),
            ("SPD", stats.Spd, AppTheme.Gold),
        };
        for (int i = 0; i < items.Length; i++)
        {
            box.AddView(StatRow(items[i].label, items[i].val, items[i].col));
            if (i < items.Length - 1) box.AddView(ThinDivider());
        }

        var extra = UI.Text($"等级 Lv.{_view.Save.Level}  ·  星级 {new string('★', _view.Save.Stars)}  ·  天赋点 {_view.Save.UnspentPoints}" + (_owned ? "" : "  ·  未拥有"), 12, world.TextSecondary);
        extra.SetPadding(0, Dp(12), 0, 0);
        extra.Gravity = GravityFlags.CenterHorizontal;
        box.AddView(extra);

        return box;
    }

    View BuildSkillPanel()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var world = AppTheme.World(_view.World);

        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14);
        box.SetPadding(Dp(16), Dp(14), Dp(16), Dp(14));

        if (_def.Skills == null || _def.Skills.Count == 0)
        {
            box.AddView(UI.Text("暂无技能", 14, world.TextSecondary));
            return box;
        }

        foreach (var sk in _def.Skills)
        {
            var (from, _, _, _) = ElementTheme.For(sk.Element);
            var typeColor = sk.Type == "Ultimate" ? AppTheme.Warning
                : sk.Type == "Active" ? from : world.TextSecondary;

            var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
            row.SetPadding(0, Dp(6), 0, Dp(2));
            var tag = UI.Text("[" + sk.Type + "]", 10, typeColor, bold: true);
            tag.SetPadding(0, 0, Dp(8), 0);
            var nm = UI.Text(sk.DisplayName, 14, world.TextPrimary, bold: true);
            row.AddView(tag);
            row.AddView(nm);
            box.AddView(row);

            var desc = UI.Text(sk.Description, 12, world.TextSecondary);
            desc.SetPadding(Dp(16), Dp(2), 0, Dp(6));
            box.AddView(desc);
        }

        return box;
    }

    View BuildStoryPanel()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var world = AppTheme.World(_view.World);

        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14);
        box.SetPadding(Dp(16), Dp(14), Dp(16), Dp(14));

        var lore = UI.Text(_def.Lore, 14, world.TextPrimary);
        lore.SetLineSpacing(Dp(4), 1f);
        box.AddView(lore);

        if (!string.IsNullOrEmpty(_def.Story))
        {
            var story = UI.Text(_def.Story, 14, world.TextSecondary);
            story.SetPadding(0, Dp(10), 0, 0);
            story.SetLineSpacing(Dp(4), 1f);
            box.AddView(story);
        }

        if (!string.IsNullOrEmpty(_def.Faction))
        {
            var faction = UI.Text($"所属势力：{_def.Faction}", 12, world.Glow);
            faction.SetPadding(0, Dp(10), 0, 0);
            box.AddView(faction);
        }

        return box;
    }

    View BuildVoicePanel()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var world = AppTheme.World(_view.World);

        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.Background = UI.GlassPanel(14);
        box.SetPadding(Dp(16), Dp(14), Dp(16), Dp(14));

        var voices = _def.Voices;
        if (voices == null || voices.Count == 0)
        {
            box.AddView(UI.Text("暂无语音", 14, world.TextSecondary));
            return box;
        }

        for (int i = 0; i < voices.Count; i++)
        {
            var v = voices[i];
            var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
            row.SetGravity(GravityFlags.CenterVertical);
            row.SetPadding(0, Dp(6), 0, Dp(6));

            var bullet = UI.Text("▸", 14, world.Glow, bold: true);
            bullet.SetPadding(0, 0, Dp(10), 0);

            var text = UI.Text(v, 14, world.TextSecondary);
            text.SetLineSpacing(Dp(3), 1f);

            row.AddView(bullet);
            row.AddView(text);
            box.AddView(row);
        }

        return box;
    }

    View SectionTitle(string s)
    {
        // 左金线 + 标题：统一卡牌铭牌风格（全局规范）
        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetGravity(GravityFlags.CenterVertical);
        row.SetPadding(0, 0, 0, UI.Dp(10));
        var bar = new View(this);
        bar.LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(3), UI.Dp(16));
        bar.Background = UI.RoundRect(AppTheme.Gold, 2);
        row.AddView(bar);
        var t = UI.Text(s, 14, AppTheme.Text1, bold: true);
        t.LetterSpacing = 0.18f;
        t.SetPadding(UI.Dp(10), 0, 0, 0);
        row.AddView(t);
        return row;
    }

    View StatRow(string label, int value, Color color)
    {
        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetPadding(0, UI.Dp(7), 0, UI.Dp(7));

        var name = UI.Text(label, 15, AppTheme.Text1);
        name.SetPadding(0, 0, UI.Dp(10), 0);
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        var val = UI.Text(value.ToString("N0"), 16, color, bold: true);
        UI.Tabular(val);

        row.AddView(name); row.AddView(spacer); row.AddView(val);
        return row;
    }

    View ThinDivider()
    {
        var v = new View(this);
        v.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, UI.Dp(1));
        v.SetBackgroundColor(Color.Argb(22, 255, 255, 255));
        return v;
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }
}
