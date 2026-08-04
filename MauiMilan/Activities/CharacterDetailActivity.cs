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
    private CharacterTurntableView? _portrait;
    private LinearLayout? _detailContent;
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
        if (_detailContent == null) return;
        try { Motion.Rise(_detailContent, Motion.Trans, 0, 12); }
        catch (System.Exception) { }
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

        var root = new ScrollView(this)
        {
            LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent),
            VerticalScrollBarEnabled = false
        };
        root.SetBackgroundColor(AppTheme.BgDeepest);

        var content = new LinearLayout(this) { Orientation = Orientation.Vertical };
        content.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        content.SetPadding(Dp(16), Dp(28), Dp(16), Dp(24));

        // ═══ TOP BAR ═══
        content.AddView(BuildTopBar());
        content.AddView(Spacer(12));

        // ═══ 3D 立绘区 ═══
        var portraitFrame = new FrameLayout(this);
        portraitFrame.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(420));
        portraitFrame.Background = (UI.GlassPanel(18, gold: _owned));
        portraitFrame.SetPadding(Dp(6), Dp(6), Dp(6), Dp(6));

        // 真 3D：用 CharacterTurntableView（有厚度的 3D 卡片：绕轴旋转 + 厚度边条 + 卡背），
        // 取代原来的 Parallax3D 视差（仅 2.5D）。自动旋转 + 手指拖拽控制角度。
        _portrait = new CharacterTurntableView(this).Bind(_view);
        _portrait.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        portraitFrame.AddView(_portrait);

        // 未拥有遮罩
        if (!_owned)
        {
            var lockOverlay = new View(this);
            lockOverlay.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
            lockOverlay.SetBackgroundColor(Color.Argb(130, 0, 0, 0));
            var lockIcon = UI.Text("🔒 未获得", 18, AppTheme.Text2, bold: true);
            lockIcon.Gravity = GravityFlags.Center;
            lockIcon.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
            ((FrameLayout.LayoutParams)lockIcon.LayoutParameters).Gravity = GravityFlags.Center;
            portraitFrame.AddView(lockOverlay);
            portraitFrame.AddView(lockIcon);
        }

        // 左右切换按钮悬浮在立绘两侧
        var prevBtn = BuildArrow("‹", true);
        var nextBtn = BuildArrow("›", false);
        portraitFrame.AddView(prevBtn);
        portraitFrame.AddView(nextBtn);

        content.AddView(portraitFrame);
        content.AddView(Spacer(16));

        // ═══ 名字与称号 ═══
        content.AddView(BuildNamePlate());
        content.AddView(Spacer(18));

        // ═══ 专属武器（UR 特色武器）═══
        if (!string.IsNullOrEmpty(_def.Weapon))
        {
            content.AddView(SectionTitle("专 属 武 器"));
            content.AddView(BuildWeaponPanel());
            content.AddView(Spacer(16));
        }

        // ═══ 属性 ═══
        content.AddView(SectionTitle("基 本 属 性"));
        content.AddView(BuildStatsPanel());
        content.AddView(Spacer(16));

        // ═══ 技能 ═══
        content.AddView(SectionTitle("技 能"));
        content.AddView(BuildSkillPanel());
        content.AddView(Spacer(16));

        // ═══ 背景故事 ═══
        content.AddView(SectionTitle("背 景 故 事"));
        content.AddView(BuildStoryPanel());
        content.AddView(Spacer(16));

        // ═══ 语音 ═══
        content.AddView(SectionTitle("语 音 / 台 词"));
        content.AddView(BuildVoicePanel());
        content.AddView(Spacer(24));

        // ═══ 底部 360° 检视按钮 ═══
        if (_owned)
        {
            var inspect = ThemeButtons.Neon(this, "360° 检 视");
            inspect.Click += (s, e) =>
            {
                var intent = new Intent(this, typeof(InspectionActivity));
                intent.PutExtra("characterId", _def.CharacterId);
                StartActivity(intent);
            };
            content.AddView(inspect);
        }

        _detailContent = content;
        root.AddView(content);
        return root;
    }

    View BuildTopBar()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var top = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        top.SetGravity(GravityFlags.CenterVertical);

        var back = UI.Text("‹ 返 回", 16, AppTheme.Gold);
        back.Clickable = true; back.Focusable = true;
        back.SetPadding(Dp(4), Dp(4), Dp(4), Dp(4));
        UI.TapFeedback(back, Finish);

        var title = UI.Text("角 色 档 案", 20, AppTheme.Text1, bold: true);
        title.LetterSpacing = 0.12f;
        title.SetPadding(Dp(12), 0, 0, 0);

        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };

        top.AddView(back);
        top.AddView(title);
        top.AddView(spacer);
        return top;
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

    View BuildNamePlate()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var rarityCol = AppTheme.RarityColor(_view.Rarity);

        var box = new LinearLayout(this) { Orientation = Orientation.Vertical };
        box.SetGravity(GravityFlags.CenterHorizontal);

        var name = UI.Text(_view.Name, 24, AppTheme.Text1, bold: true);
        name.SetShadowLayer(6, 0, 2, Color.Argb(120, 0, 0, 0));
        name.Gravity = GravityFlags.CenterHorizontal;

        var title = UI.Text(_view.Title, 14, rarityCol);
        title.SetPadding(0, Dp(4), 0, 0);
        title.Gravity = GravityFlags.CenterHorizontal;

        var meta = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        meta.SetGravity(GravityFlags.CenterHorizontal);
        meta.SetPadding(0, Dp(8), 0, 0);

        var rarityBadge = UI.Text(AppTheme.RarityName(_view.Rarity), 12, rarityCol, bold: true);
        var rBg = new GradientDrawable();
        rBg.SetCornerRadius(Dp(6));
        rBg.SetColor(Color.Argb(40, rarityCol.R, rarityCol.G, rarityCol.B));
        rBg.SetStroke(1, Color.Argb(120, rarityCol.R, rarityCol.G, rarityCol.B));
        rarityBadge.Background = rBg;
        rarityBadge.SetPadding(Dp(8), Dp(3), Dp(8), Dp(3));

        var (from, _, _, glyph) = ElementTheme.For(_view.Element);
        var elemBadge = UI.Text($"{glyph} {_view.Element}", 12, from);
        elemBadge.SetPadding(Dp(12), Dp(3), 0, Dp(3));

        meta.AddView(rarityBadge);
        meta.AddView(elemBadge);

        box.AddView(name);
        box.AddView(title);
        box.AddView(meta);
        return box;
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
        box.SetPadding(Dp(16), Dp(14), Dp(16), Dp(14));

        box.AddView(StatRow("ATK", stats.Atk, AppTheme.Danger));
        box.AddView(StatRow("DEF", stats.Def, AppTheme.Frost));
        box.AddView(StatRow("HP", stats.Hp, Color.ParseColor("#7ee787")));
        box.AddView(StatRow("SPD", stats.Spd, AppTheme.Gold));

        var extra = UI.Text($"等级 Lv.{_view.Save.Level}  ·  星级 {new string('★', _view.Save.Stars)}  ·  天赋点 {_view.Save.UnspentPoints}" + (_owned ? "" : "  ·  未拥有"), 12, world.TextSecondary);
        extra.SetPadding(0, Dp(10), 0, 0);
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
            var typeColor = sk.Type == "Ultimate" ? Color.ParseColor("#FF6B00")
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
        row.SetPadding(0, UI.Dp(5), 0, UI.Dp(5));

        var dot = UI.Text("●", 12, color);
        dot.SetPadding(0, 0, UI.Dp(8), 0);
        var name = UI.Text(label, 16, AppTheme.Text1);
        name.SetPadding(0, 0, UI.Dp(10), 0);
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        var val = UI.Text(value.ToString("N0"), 16, color, bold: true);
        UI.Tabular(val);

        row.AddView(dot); row.AddView(name); row.AddView(spacer); row.AddView(val);
        return row;
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }
}
