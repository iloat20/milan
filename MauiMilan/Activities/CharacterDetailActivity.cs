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
using Milan.Infrastructure.EventBus;

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
    private View? _statsSection;
    private LinearLayout? _contentInner;
    private readonly Handler _handler = new(Looper.MainLooper!);
    // 魔兽世界风格数值增长的绿色（属性加成 +N）
    private static readonly Color WoWGreen = Color.Rgb(70, 255, 130);

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
        _contentInner = inner;

        var staged = new System.Collections.Generic.List<View>();

        if (!string.IsNullOrEmpty(_def.Weapon))
        {
            var s = BuildSection("专 属 武 器", BuildWeaponPanel());
            inner.AddView(s); inner.AddView(Spacer(16));
            staged.Add(s);
        }

        var sStats = BuildSection("基 本 属 性", BuildStatsPanel());
        inner.AddView(sStats); inner.AddView(Spacer(16)); staged.Add(sStats);
        _statsSection = sStats;

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

        // 悬浮操作：返回（左上）+ 左右切换（两侧）+ 养成入口（右上，仅已拥有）
        var back = BuildBackButton();
        frame.AddView(back);
        var prevBtn = BuildArrow("‹", true);
        var nextBtn = BuildArrow("›", false);
        frame.AddView(prevBtn);
        frame.AddView(nextBtn);
        if (_owned) frame.AddView(BuildProgressionButton());

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

    View BuildProgressionButton()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var btn = UI.Text("养 成 ▲", 16, AppTheme.Gold, bold: true);
        btn.SetPadding(Dp(16), Dp(9), Dp(16), Dp(9));
        btn.Background = UI.GlassPanel(20, gold: true);
        btn.Clickable = true; btn.Focusable = true;
        UI.TapFeedback(btn, () =>
        {
            var intent = new Intent(this, typeof(ProgressionActivity));
            intent.PutExtra("characterId", _def.CharacterId);
            StartActivity(intent);
        });
        btn.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent)
        {
            Gravity = GravityFlags.Top | GravityFlags.Right,
            TopMargin = Dp(40),
            RightMargin = Dp(14)
        };
        return btn;
    }

    /// <summary>从养成页返回时，订阅养成变动并在事件/Resume 时就地刷新属性面板，
    /// 使等级/突破/天赋带来的属性变化即时可见（内存与存档同源，直接重建该区块即可）。</summary>
    protected override void OnResume()
    {
        base.OnResume();
        EventBus.Subscribe<ProgressionChanged>(OnProgChanged);
        RefreshStats();
    }

    protected override void OnPause()
    {
        base.OnPause();
        EventBus.UnsubscribeAll(this);
    }

    void OnProgChanged(ProgressionChanged _) => RefreshStats();

    void RefreshStats()
    {
        if (_contentInner == null || _statsSection == null) return;
        int idx = _contentInner.IndexOfChild(_statsSection);
        if (idx < 0) return;
        _contentInner.RemoveViewAt(idx);
        var fresh = BuildSection("基 本 属 性", BuildStatsPanel());
        _contentInner.AddView(fresh, idx);
        _statsSection = fresh;
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
        var baseStats = ComputeBaseStats(_view);

        // 魔兽世界风格角色面板：暗色底 + 金色双描边 + 四角菱形饰钉；主/次级属性分组。
        var frame = new WoWStatsFrame(this);
        frame.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        frame.Background = new GradientDrawable(
            GradientDrawable.Orientation.TopBottom,
            new[] { Color.Argb(255, 0x16, 0x10, 0x1C).ToArgb(), Color.Argb(255, 0x0B, 0x07, 0x12).ToArgb() });
        frame.SetPadding(Dp(16), Dp(16), Dp(16), Dp(14));

        var inner = new LinearLayout(this) { Orientation = Orientation.Vertical };
        inner.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);

        // ── 主属性（对应魔兽 力量/敏捷/智力/耐力 四项基础属性）──
        inner.AddView(WoWSectionHeader("主 属 性"));
        inner.AddView(Spacer(4));

        var primaries = new (string glyph, Color col, string cn, string en, int val, int bonus)[]
        {
            ("攻", AppTheme.Danger,  "攻击", "Attack",  stats.Atk,  stats.Atk  - baseStats.Atk),
            ("防", AppTheme.Frost,   "防御", "Defense", stats.Def,  stats.Def  - baseStats.Def),
            ("命", AppTheme.Success, "生命", "Health",  stats.Hp,   stats.Hp   - baseStats.Hp),
            ("速", AppTheme.Gold,    "速度", "Speed",   stats.Spd,  stats.Spd  - baseStats.Spd),
        };
        for (int i = 0; i < primaries.Length; i++)
        {
            inner.AddView(WoWStatRow(primaries[i].glyph, primaries[i].col, primaries[i].cn, primaries[i].en, primaries[i].val.ToString("N0"), primaries[i].bonus));
            if (i < primaries.Length - 1) inner.AddView(WoWDivider());
        }

        // ── 次级属性（派生战斗属性，对应魔兽 暴击/急速/护甲/格挡）──
        inner.AddView(Spacer(6));
        inner.AddView(WoWGroupDivider());
        inner.AddView(Spacer(6));
        inner.AddView(WoWSectionHeader("次 级 属 性"));
        inner.AddView(Spacer(4));

        var secCur = DeriveSecondary(stats);
        var secBase = DeriveSecondary(baseStats);
        var secondaries = new (string glyph, Color col, string cn, string en, string val, int bonus)[]
        {
            ("暴", AppTheme.Warning,   "暴击", "Critical", secCur.crit + "%",  secCur.crit  - secBase.crit),
            ("急", AppTheme.Violet,    "急速", "Haste",    secCur.haste + "%", secCur.haste - secBase.haste),
            ("甲", AppTheme.FrostDeep, "护甲", "Armor",    secCur.armor.ToString("N0"), secCur.armor - secBase.armor),
            ("挡", AppTheme.GoldDeep,  "格挡", "Block",    secCur.block + "%", secCur.block - secBase.block),
        };
        for (int i = 0; i < secondaries.Length; i++)
        {
            inner.AddView(WoWStatRow(secondaries[i].glyph, secondaries[i].col, secondaries[i].cn, secondaries[i].en, secondaries[i].val, secondaries[i].bonus));
            if (i < secondaries.Length - 1) inner.AddView(WoWDivider());
        }

        // 页脚：等级 / 星级 / 天赋点
        inner.AddView(WoWDivider());
        inner.AddView(Spacer(4));
        inner.AddView(WoWFooter());

        frame.AddView(inner);
        return frame;
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

    // 魔兽世界风格属性行：圆形角色徽章 + 中英名称 + 等宽数值 + 绿色加成。
    View WoWStatRow(string glyph, Color col, string cn, string en, string valueText, int bonus)
    {
        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetGravity(GravityFlags.CenterVertical);
        row.SetPadding(0, UI.Dp(9), 0, UI.Dp(9));

        row.AddView(StatBadge(glyph, col));

        var nameBlock = new LinearLayout(this) { Orientation = Orientation.Vertical };
        nameBlock.SetPadding(UI.Dp(12), 0, 0, 0);
        nameBlock.AddView(UI.Text(cn, 15, AppTheme.Text1, bold: true));
        var enTv = UI.Text(en, 10, AppTheme.Text3);
        enTv.LetterSpacing = 0.08f;
        nameBlock.AddView(enTv);
        row.AddView(nameBlock);

        row.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) });

        var valBlock = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        valBlock.SetGravity(GravityFlags.CenterVertical);
        var valTv = UI.Text(valueText, 18, AppTheme.Text1, bold: true);
        UI.Tabular(valTv);
        valBlock.AddView(valTv);
        if (bonus > 0)
        {
            var bTv = UI.Text(" +" + bonus, 13, WoWGreen, bold: true);
            UI.Tabular(bTv);
            bTv.SetPadding(UI.Dp(6), 0, 0, 0);
            valBlock.AddView(bTv);
        }
        row.AddView(valBlock);
        return row;
    }

    // 圆形角色徽章：暗底 + 角色色描边 + 中文单字字形
    View StatBadge(string glyph, Color col)
    {
        var badge = new TextView(this) { Text = glyph, Gravity = GravityFlags.Center };
        badge.SetTextColor(Color.Argb(255, col.R, col.G, col.B));
        badge.SetTextSize(ComplexUnitType.Sp, 16);
        badge.SetTypeface(Typeface.DefaultBold, TypefaceStyle.Bold);
        var gd = new GradientDrawable();
        gd.SetShape(Android.Graphics.Drawables.ShapeType.Oval);
        gd.SetColor(Color.Argb(45, col.R, col.G, col.B).ToArgb());
        gd.SetStroke(UI.Dp(1.5f), Color.Argb(195, col.R, col.G, col.B));
        badge.Background = gd;
        badge.LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(34), UI.Dp(34));
        return badge;
    }

    // 居中分组标题：两侧金色渐隐线 + ◆ + 标题
    View WoWSectionHeader(string title)
    {
        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetGravity(GravityFlags.CenterVertical);
        var left = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, UI.Dp(1), 1f) };
        left.Background = LineGold();
        var d1 = UI.Text("◆", 10, AppTheme.Gold);
        d1.SetPadding(UI.Dp(10), 0, UI.Dp(6), 0);
        var t = UI.Text(title, 13, AppTheme.Gold, bold: true);
        t.LetterSpacing = 0.2f;
        var d2 = UI.Text("◆", 10, AppTheme.Gold);
        d2.SetPadding(UI.Dp(6), 0, UI.Dp(10), 0);
        var right = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, UI.Dp(1), 1f) };
        right.Background = LineGold();
        row.AddView(left); row.AddView(d1); row.AddView(t); row.AddView(d2); row.AddView(right);
        return row;
    }

    // 行间细分隔（金 α 发丝线）
    View WoWDivider()
    {
        var v = new View(this);
        v.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, UI.Dp(1));
        v.SetBackgroundColor(Color.Argb(24, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B));
        return v;
    }

    // 组间分隔：线 + 中心 ◆ + 线
    View WoWGroupDivider()
    {
        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetGravity(GravityFlags.CenterVertical);
        var left = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, UI.Dp(1), 1f) };
        left.Background = LineGold();
        var d = UI.Text("◆", 11, AppTheme.Gold);
        d.SetPadding(UI.Dp(12), 0, UI.Dp(12), 0);
        var right = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, UI.Dp(1), 1f) };
        right.Background = LineGold();
        row.AddView(left); row.AddView(d); row.AddView(right);
        return row;
    }

    View WoWFooter()
    {
        var wrap = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        wrap.SetGravity(GravityFlags.Center);
        var txt = UI.Text(
            $"等级 Lv.{_view.Save.Level}   ·   星级 {new string('★', _view.Save.Stars)}   ·   天赋点 {_view.Save.UnspentPoints}" + (_owned ? "" : "   ·   未拥有"),
            12, AppTheme.Text2);
        txt.Gravity = GravityFlags.CenterHorizontal;
        wrap.AddView(txt);
        return wrap;
    }

    Drawable LineGold()
    {
        return new GradientDrawable(
            GradientDrawable.Orientation.LeftRight,
            new[] {
                Color.Argb(0, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B).ToArgb(),
                Color.Argb(130, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B).ToArgb(),
                Color.Argb(0, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B).ToArgb()
            });
    }

    // 由主属性派生次级战斗属性（透明公式，仅用于面板展示）
    (int crit, int haste, int armor, int block) DeriveSecondary(Milan.Domain.Battle.UnitStats s)
    {
        int Clamp(int v, int lo, int hi) => System.Math.Max(lo, System.Math.Min(hi, v));
        return (
            Clamp(8 + s.Atk / 120, 8, 60),
            Clamp(5 + s.Spd * 2, 5, 50),
            (int)(s.Def * 1.6 + s.Hp * 0.05),
            Clamp(3 + s.Def / 200, 3, 30)
        );
    }

    // 1 级基准属性（用于绿色加成 = 当前 - 基准）
    Milan.Domain.Battle.UnitStats ComputeBaseStats(OwnedCharacterView ch)
    {
        var engine = new Milan.Domain.Progression.ProgressionEngine();
        int stg = System.Math.Max(1, ch.Save.Stage);
        var bs = ch.Def?.BaseStats;
        int Base(int i, int fb) => bs != null && i < bs.Length ? bs[i] : fb;
        return new Milan.Domain.Battle.UnitStats
        {
            CharacterId = ch.Save.CharacterId,
            Atk = engine.StatAtLevel(Base(0, 100), 1, stg, 1f),
            Def = engine.StatAtLevel(Base(1, 80), 1, stg, 1f),
            Hp = engine.StatAtLevel(Base(2, 1000), 1, stg, 1f),
            Spd = engine.StatAtLevel(Base(3, 12), 1, stg, 1f),
        };
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }

    // 魔兽世界风格面板外框：暗底 + 金色双描边 + 四角菱形饰钉。
    // 复用 Paint/Path/RectF，OnDraw 空尺寸保护。
    private sealed class WoWStatsFrame : FrameLayout
    {
        private readonly Paint _p = new() { AntiAlias = true };
        private readonly Android.Graphics.Path _diamond = new();
        private readonly RectF _r = new();

        public WoWStatsFrame(Context ctx) : base(ctx) => SetWillNotDraw(false);

        protected override void OnDraw(Canvas canvas)
        {
            base.OnDraw(canvas);
            int w = Width, h = Height;
            if (w == 0 || h == 0) return;
            var density = Resources.DisplayMetrics.Density;
            float Dp(float v) => v * density;
            float rad = Dp(14);
            _p.Reset(); _p.AntiAlias = true;

            // 外描边（金）
            _p.SetStyle(Paint.Style.Stroke);
            _p.StrokeWidth = Dp(2);
            _p.Color = AppTheme.Gold;
            _r.Set(0, 0, w, h);
            canvas.DrawRoundRect(_r, rad, rad, _p);

            // 内描边（细金，低透明）
            float inset = Dp(5);
            _p.StrokeWidth = Dp(1);
            _p.Color = Color.Argb(130, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B);
            _r.Set(inset, inset, w - inset, h - inset);
            canvas.DrawRoundRect(_r, rad - Dp(2), rad - Dp(2), _p);

            // 四角菱形饰钉
            float d = Dp(4.5f);
            DrawDiamond(canvas, inset, inset, d);
            DrawDiamond(canvas, w - inset, inset, d);
            DrawDiamond(canvas, inset, h - inset, d);
            DrawDiamond(canvas, w - inset, h - inset, d);
        }

        private void DrawDiamond(Canvas c, float x, float y, float r)
        {
            _p.Reset(); _p.AntiAlias = true;
            _p.SetStyle(Paint.Style.Fill);
            _p.Color = AppTheme.GoldHi;
            _diamond.Reset();
            _diamond.MoveTo(x, y - r); _diamond.LineTo(x + r, y); _diamond.LineTo(x, y + r); _diamond.LineTo(x - r, y); _diamond.Close();
            c.DrawPath(_diamond, _p);
        }
    }
}
