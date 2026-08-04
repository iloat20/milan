using Android.App;
using Android.Content;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Views;
using Android.Widget;
using Milan.Infrastructure.EventBus;
using Milan.Maui;
using Milan.Maui.Services;

namespace Milan.Maui.Activities;

[Activity(Label = "抽卡")]
public class GachaActivity : Activity
{
    private LinearLayout _resultsRoot = null!;
    private TextView _summary = null!;
    private TextView _dust = null!;
    private TextView _gems = null!;
    private FrameLayout _flash = null!;
    private RiftPortal _rift = null!;
    private FrameLayout _revealContainer = null!;
    private WeaponFxView? _revealWeapon;
    private PortraitView? _revealPortrait;
    // #9: 持有当前 Click 委托，保证重复演出时先解绑再绑定
    private EventHandler? _revealClick;
    private bool _busy;
    // U2：每次抽卡演出递增 token；跳过或新演出会据此作废挂起的旧回调，避免重复展示/状态错乱。
    private int _revealToken;
    private List<PullResult>? _currentResults;
    // 所有延迟回调统一走这个 Handler，OnDestroy 时一次性取消，
    // 避免动画途中退出后挂起回调仍持有已销毁的视图树。
    private readonly Handler _handler = new(Looper.MainLooper!);

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        SetContentView(BuildLayout());
    }

    protected override void OnDestroy()
    {
        _handler.RemoveCallbacksAndMessages(null);
        base.OnDestroy();
    }

    /// <summary>PostDelayed 包装：执行前检查 Activity 是否还活着。</summary>
    void PostSafe(Action action, long delayMs)
    {
        _handler.PostDelayed(() =>
        {
            if (IsFinishing || IsDestroyed) return;
            action();
        }, delayMs);
    }

    // 事件驱动刷新资源栏（替代 OnResume 整页重建）。订阅幂等 + OnPause 退订，保证同页至多一个有效订阅。
    void OnCurrencyChanged(CurrencyChanged _) => RefreshCurrency();

    void RefreshCurrency()
    {
        if (_dust != null) _dust.Text = GameState.Currency.ToString("N0");
        if (_gems != null) _gems.Text = GameState.Service.SaveData.HardCurrency.ToString("N0");
    }

    protected override void OnResume()
    {
        base.OnResume();
        // 先订阅再直刷：本页回到前台时立即兜底显示最新值（订阅端也会在事件到达时刷新）。
        EventBus.Subscribe<CurrencyChanged>(OnCurrencyChanged);
        RefreshCurrency();
    }

    protected override void OnPause()
    {
        // 页面不可见即摘掉处理器，防止泄漏；重新可见时 OnResume 会重新订阅。
        EventBus.UnsubscribeAll(this);
        base.OnPause();
    }

    // 系统内存吃紧时释放武器图 native 缓存（VfxRenderer 内部 LRU，最多 16 张）。
    public override void OnTrimMemory(TrimMemory level)
    {
        base.OnTrimMemory(level);
        if (level >= TrimMemory.Moderate) VfxRenderer.TrimWeaponCache();
    }

    FrameLayout BuildLayout()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var root = new FrameLayout(this);
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.SetBackgroundColor(AppTheme.BgDeepest);

        var content = new LinearLayout(this) { Orientation = Orientation.Vertical };
        content.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        content.SetPadding(Dp(20), Dp(36), Dp(20), Dp(10));

        // ═══ Header: 标题 + 资源胶囊（无返回，导航由底栏承担）═══
        var header = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        header.SetGravity(GravityFlags.CenterVertical);
        var title = UI.Text("次 元 裂 缝", 24, AppTheme.Text1, bold: true);
        title.LetterSpacing = 0.12f;
        title.SetShadowLayer(8, 0, 2, AppTheme.Gold);
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        header.AddView(title);
        header.AddView(spacer);
        header.AddView(AppChrome.ResourceBar(this, out _dust, out _gems));
        content.AddView(header);
        content.AddView(Spacer(14));

        // ═══ 卡池信息（玻璃面板）═══
        var poolInfo = UI.VBox();
        poolInfo.Background = UI.GlassPanel(14);
        poolInfo.SetPadding(Dp(16), Dp(12), Dp(16), Dp(12));
        var activePool = GameState.Service.Pools.FirstOrDefault();
        poolInfo.AddView(UI.Text(activePool?.DisplayName ?? "常驻卡池", 15, AppTheme.Gold, bold: true));
        var rates = UI.Text(BuildRatesLabel(activePool), 11, AppTheme.Text2);
        rates.SetPadding(0, Dp(4), 0, 0);
        poolInfo.AddView(rates);
        // 保底进度（霜蓝，呼应暗夜神性·诸神黄昏）
        var pity = activePool != null ? GameState.Service.SaveData.GetGachaCounter(activePool.PoolId) : 0;
        var pityMax = activePool?.HardPity ?? 0;
        if (pityMax > 0)
        {
            var pityLine = UI.Text($"保底进度  {pity} / {pityMax}", 11, AppTheme.Frost);
            pityLine.SetPadding(0, Dp(4), 0, 0);
            UI.Tabular(pityLine);
            poolInfo.AddView(pityLine);
        }
        content.AddView(poolInfo);
        content.AddView(Spacer(12));

        // ═══ 卡池角色预览 ═══
        content.AddView(BuildPoolPreview());
        content.AddView(Spacer(12));

        // ═══ 召唤法阵（金/霓虹光效）═══
        var riftFrame = new FrameLayout(this);
        var rfLp = new LinearLayout.LayoutParams(Dp(190), Dp(190));
        rfLp.Gravity = GravityFlags.CenterHorizontal;
        riftFrame.LayoutParameters = rfLp;

        var riftGlow = new View(this);
        var rgLp = new FrameLayout.LayoutParams(Dp(170), Dp(170));
        rgLp.Gravity = GravityFlags.Center;
        riftGlow.LayoutParameters = rgLp;
        var glowBg = new GradientDrawable();
        glowBg.SetShape(ShapeType.Oval);
        glowBg.SetColors(new[] { Color.Argb(150, AppTheme.Violet.R, AppTheme.Violet.G, AppTheme.Violet.B).ToArgb(), Color.Argb(0, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B).ToArgb() });
        glowBg.SetGradientType(GradientType.RadialGradient);
        glowBg.SetGradientRadius(Dp(85));
        riftGlow.Background = glowBg;
        riftFrame.AddView(riftGlow);

        _rift = new RiftPortal(this);
        _rift.LayoutParameters = new FrameLayout.LayoutParams(Dp(170), Dp(170));
        ((FrameLayout.LayoutParams)_rift.LayoutParameters).Gravity = GravityFlags.Center;
        riftFrame.AddView(_rift);
        content.AddView(riftFrame);
        content.AddView(Spacer(16));

        // ═══ 召唤按钮（金色主 / 霓虹次）═══
        var buttons = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        var singleBtn = ThemeButtons.Neon(this, "单 抽");
        singleBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        singleBtn.Click += (s, e) => DoPull(false);
        var tenBtn = ThemeButtons.Gold(this, "十 连");
        tenBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        tenBtn.Click += (s, e) => DoPull(true);
        buttons.AddView(singleBtn);
        buttons.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(Dp(12), 0) });
        buttons.AddView(tenBtn);
        content.AddView(buttons);
        content.AddView(Spacer(10));

        _summary = UI.Text("", 11, AppTheme.Text2);
        content.AddView(_summary);
        content.AddView(Spacer(6));

        // ═══ 结果列表 ═══
        var scroll = new ScrollView(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f) };
        _resultsRoot = new LinearLayout(this) { Orientation = Orientation.Vertical };
        scroll.AddView(_resultsRoot);
        content.AddView(scroll);

        // ═══ 全局底部导航 ═══
        var nav = new GameNavBar(this, GameNavBar.NavItem.Gacha, OnNav);
        nav.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        content.AddView(nav);

        root.AddView(content);
        root.Post(() => PlayEntrance(content, riftFrame, buttons));

        // 白闪覆盖层（稀有度色）
        _flash = new FrameLayout(this);
        _flash.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        _flash.SetBackgroundColor(Color.White);
        _flash.Alpha = 0f;
        _flash.Clickable = false;
        _flash.Enabled = false;
        root.AddView(_flash);

        // 角色翻转演出容器
        _revealContainer = new FrameLayout(this);
        _revealContainer.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        _revealContainer.Alpha = 0f;
        _revealContainer.Clickable = false;
        root.AddView(_revealContainer);

        return root;
    }

    View BuildPoolPreview()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var scroll = new HorizontalScrollView(this)
        {
            HorizontalScrollBarEnabled = false
        };
        scroll.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        scroll.SetPadding(Dp(4), Dp(6), Dp(4), Dp(6));

        var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        row.SetGravity(GravityFlags.CenterVertical);

        var pool = GameState.Service.Pools.FirstOrDefault();
        var ids = pool?.Entries?.Select(e => e.CharacterId).Distinct().ToList() ?? new List<string>();
        foreach (var id in ids)
        {
            var def = GameState.Service.Characters.FirstOrDefault(c => c.CharacterId == id);
            if (def == null) continue;

            var frame = new FrameLayout(this);
            var flp = new LinearLayout.LayoutParams(Dp(64), Dp(64));
            flp.SetMargins(Dp(6), 0, Dp(6), 0);
            frame.LayoutParameters = flp;

            var avatar = new UnifiedAvatarView(this).Bind(def, 56);
            var alp = new FrameLayout.LayoutParams(Dp(56), Dp(56));
            alp.Gravity = GravityFlags.Center;
            avatar.LayoutParameters = alp;

            frame.AddView(avatar);
            UI.TapFeedback(frame, () => OpenCharacterDetail(def.CharacterId));
            row.AddView(frame);
        }

        scroll.AddView(row);
        return scroll;
    }

    void OpenCharacterDetail(string characterId)
    {
        var intent = new Intent(this, typeof(CharacterDetailActivity));
        intent.PutExtra("characterId", characterId);
        StartActivity(intent);
    }

    void OnNav(GameNavBar.NavItem item)
    {
        if (item == GameNavBar.NavItem.Gacha) return;
        var target = item switch
        {
            GameNavBar.NavItem.Home => typeof(HomeActivity),
            GameNavBar.NavItem.Deck => typeof(DeckActivity),
            GameNavBar.NavItem.Shop => typeof(ShopActivity),
            GameNavBar.NavItem.Settings => typeof(SettingsActivity),
            _ => null
        };
        Nav.To(this, target);
    }

    /// <summary>
    /// 入场编排（暗夜神性·诸神黄昏）：内容淡入 → 法阵缩放弹入 → 召唤按钮错落上浮（stagger 60ms，ease-out）。
    /// 仅做变换/透明动画，不动布局，安全可重入。
    /// </summary>
    void PlayEntrance(ViewGroup content, View riftFrame, LinearLayout buttons)
    {
        try
        {
            Motion.Fade(content, Motion.Trans);
            // 法阵落位：缩放弹入 + 淡入
            Motion.Pop(riftFrame, 460, 0, 0.85f);
            // 召唤按钮错落上浮
            for (int i = 0; i < buttons.ChildCount; i++)
                Motion.Rise(buttons.GetChildAt(i), 360, 160 + i * 60, 12);
        }
        catch (System.Exception ex) { CrashReporter.Write("GachaActivity.PlayEntrance", ex); }
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }

    static string BuildRatesLabel(GachaPoolDataEntry? pool)
    {
        if (pool == null || pool.RarityWeights.Length < 4) return "";
        float total = pool.RarityWeights.Sum();
        if (total <= 0) return "";
        string Pct(int i) => $"{pool.RarityWeights[i] / total * 100:0.#}%";
        return $"UR {Pct(3)}  ·  SSR {Pct(2)}  ·  SR {Pct(1)}  ·  R {Pct(0)}";
    }

    void DoPull(bool tenPull)
    {
        if (_busy) return;
        var pool = GameState.Service.Pools.FirstOrDefault();
        if (pool == null) return;

        int count = tenPull ? 10 : 1;
        int cost = tenPull ? pool.TenCost : pool.SingleCost;
        if (GameState.Service.SaveData.SoftCurrency < cost)
        {
            Toast.MakeText(this, "星尘不足", ToastLength.Short)?.Show();
            return;
        }

        _busy = true;
        var results = GameState.Service.Pull(pool.PoolId, tenPull);
        _dust.Text = GameState.Currency.ToString("N0");
        _gems.Text = GameState.Service.SaveData.HardCurrency.ToString("N0");

        // Pull 在卡池数据异常时会返回空列表。这里过去直接 .First()，
        // 抛出的 InvalidOperationException 会从 Click 处理器一路冒到 UI 线程，
        // 无人捕获 -> 进程被杀 -> 点抽卡必闪退。务必守住，并复位 _busy 避免按钮卡死。
        var bestResult = results.OrderByDescending(r => r.Rarity).FirstOrDefault();
        if (bestResult == null)
        {
            _busy = false;
            Toast.MakeText(this, "卡池数据异常，抽卡失败", ToastLength.Short)?.Show();
            CrashReporter.Boot("gacha.pull.empty " +
                $"poolId={pool.PoolId} entries={pool.Entries.Count} chars={GameState.Service.Characters.Count}");
            return;
        }

        var bestDef = GameState.Service.Characters.FirstOrDefault(c => c.CharacterId == bestResult.CharacterId);

        // U2：本次演出 token，跳过时递增作废所有挂起回调
        var token = ++_revealToken;
        _currentResults = results;

        // 阶段一：法阵扩张 + 旋转
        _rift.Animate()?.ScaleX(1.5f)?.ScaleY(1.5f)?.SetDuration(400)?.Start();
        _rift.Rotation = 0;
        _rift.Animate()?.RotationBy(360)?.SetDuration(800)?.Start();

        // 阶段二：稀有度色白闪
        _flash.SetBackgroundColor(AppTheme.RarityColor(bestResult.Rarity));
        PostSafe(() =>
        {
            if (token != _revealToken) return;
            _flash.Clickable = true;
            _flash.Enabled = true;
            _flash.Alpha = 1f;
            _flash.Animate()?.Alpha(0f)?.SetDuration(500)?.Start();
            PostSafe(() => { if (token != _revealToken) return; _flash.Clickable = false; _flash.Enabled = false; }, 550);
        }, 400);

        // 阶段三：翻牌演出
        PostSafe(() =>
        {
            if (token != _revealToken) return;
            ShowCharacterReveal(bestDef, bestResult.Rarity, token);
        }, 600);

        // 阶段四：展示结果
        PostSafe(() =>
        {
            if (token != _revealToken) return;
            ShowResults(results);
            HideReveal();
            _rift.Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(200)?.Start();
            _busy = false;
        }, 1900);
    }

    void ShowCharacterReveal(CharacterDataEntry? def, int rarity, int token)
    {
        if (def == null) return;
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var rc = AppTheme.RarityColor(rarity);

        _revealContainer.RemoveAllViews();
        _revealContainer.Alpha = 1f;
        _revealContainer.Clickable = true;
        // #9: 每次演出都会重新订阅 Click，十连时会叠加多个 handler 导致点一次打开多个详情页。
        // 先摘掉上一次的委托再挂新的。
        if (_revealClick != null) _revealContainer.Click -= _revealClick;
        var cid = def.CharacterId;
        _revealClick = (s, e) => OpenCharacterDetail(cid);
        _revealContainer.Click += _revealClick;

        // 稀有度放射光晕（外层柔光 + 内层亮芯，三色更有层次）
        var glow = new View(this);
        glow.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        var gd = new GradientDrawable();
        gd.SetColors(new[] {
            Color.Argb(200, rc.R, rc.G, rc.B).ToArgb(),
            Color.Argb(45, rc.R, rc.G, rc.B).ToArgb(),
            Color.Argb(0, 0, 0, 0).ToArgb() });
        gd.SetGradientType(GradientType.RadialGradient);
        gd.SetGradientRadius(Dp(260));
        glow.Background = gd;
        _revealContainer.AddView(glow);

        // 居中翻转卡牌
        var flip = new FlipCardView(this);
        flip.SetFaces(BuildFaceFront(def, rarity), BuildFaceBack(rarity));
        var flipLp = new FrameLayout.LayoutParams(Dp(220), Dp(312));
        flipLp.Gravity = GravityFlags.Center;
        flip.LayoutParameters = flipLp;
        _revealContainer.AddView(flip);

        // 专属武器特效：出货时从卡牌「后方」升起、定格炫光、再淡出
        // （插在卡牌下层，不长期压住角色本体；翻牌完成后才起，节奏不打架）
        // 仅 SSR(3)/UR(4) 角色展示专属武器；SR/R 不设计武器
        if (!string.IsNullOrEmpty(def.WeaponVfx) && def.BaseRarity >= 3)
        {
            var (_, _, elemGlow, _) = ElementTheme.For(def.Element);
            _revealWeapon = new WeaponFxView(this, def.WeaponVfx, rc, elemGlow, def.Element, rarity.ToString())
            {
                LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent),
                Alpha = 0f,
                TranslationY = Dp(150),
                ScaleX = 0.86f,
                ScaleY = 0.86f
            };
            // 插在稀有度光晕(0)与翻转卡牌(原1)之间 → 武器处于卡牌后方
            _revealContainer.AddView(_revealWeapon, 1);
            // 翻牌露出完成后：武器从牌后升起 + 淡入 + 轻微放大定格（640ms 起，420ms 完成）
            PostSafe(() =>
            {
                if (token != _revealToken) return;
                _revealWeapon?.Animate()
                    ?.TranslationY(0)?.Alpha(1f)?.ScaleX(1f)?.ScaleY(1f)?
                    .SetDuration(420)?.Start();
            }, 640);
            // 定格炫光后淡出（1400ms 起，520ms 完成）
            PostSafe(() =>
            {
                if (token != _revealToken) return;
                _revealWeapon?.Animate()?.Alpha(0f)?.SetDuration(520)?.Start();
            }, 1400);
            // 移除，避免持续压在结果上
            PostSafe(() =>
            {
                if (token != _revealToken) return;
                DisposeRevealWeapon();
            }, 2000);
        }

        PostSafe(() => { if (token != _revealToken) return; flip.ShowFront(true); }, 250);
        PostSafe(() => { if (token != _revealToken) return; _revealPortrait?.TriggerBurst(); }, 650);

        // U2：演出期间显示「跳过」按钮，点击立即进入结果展示（不触发角色详情）。
        var skipBtn = new FrameLayout(this)
        {
            LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent)
            {
                Gravity = GravityFlags.Top | GravityFlags.Right
            }
        };
        var skipLp = (FrameLayout.LayoutParams)skipBtn.LayoutParameters;
        skipLp.SetMargins(0, Dp(16), Dp(16), 0);
        skipBtn.Background = UI.GlassPanel(20, gold: true);
        var skipText = UI.Text("跳过 ▶", 12, AppTheme.GoldHi, bold: true);
        skipText.SetPadding(Dp(12), Dp(6), Dp(12), Dp(6));
        skipBtn.AddView(skipText);
        skipBtn.Clickable = true;
        skipBtn.Click += (_, _) => SkipReveal(token);
        _revealContainer.AddView(skipBtn);
    }

    View BuildFaceFront(CharacterDataEntry def, int rarity)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var rc = AppTheme.RarityColor(rarity);

        var frame = new FrameLayout(this);
        frame.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        var bg = new GradientDrawable();
        bg.SetCornerRadius(Dp(18));
        bg.SetColors(new[] {
            Color.Argb(255, (int)(rc.R * 0.42f), (int)(rc.G * 0.42f), (int)(rc.B * 0.42f)).ToArgb(),
            AppTheme.BgDeepest.ToArgb()
        });
        bg.SetStroke(UI.Dp(2), rc);
        frame.Background = bg;

        // 顶部内高光
        var sheen = new View(this);
        sheen.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(60));
        var sh = new GradientDrawable();
        sh.SetCornerRadius(Dp(18));
        sh.SetColors(new[] { Color.Argb(70, 255, 255, 255).ToArgb(), Color.Argb(0, 255, 255, 255).ToArgb() });
        sheen.Background = sh;
        frame.AddView(sheen);

        // 立绘
        var portrait = new PortraitView(this).Bind(def);
        portrait.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(230));
        portrait.SetPadding(0, Dp(8), 0, 0);
        frame.AddView(portrait);
        _revealPortrait = portrait;

        // 底部铭牌
        var bar = new LinearLayout(this) { Orientation = Orientation.Vertical };
        var barLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        barLp.Gravity = GravityFlags.Bottom;
        bar.LayoutParameters = barLp;
        bar.SetPadding(Dp(12), Dp(8), Dp(12), Dp(10));
        bar.SetBackgroundColor(Color.Argb(150, 4, 4, 12));

        var name = UI.Text(def.DisplayName, 17, AppTheme.Text1, bold: true);
        name.SetShadowLayer(10, 0, 2, Color.Argb(210, rc.R, rc.G, rc.B));
        var stars = UI.Text(new string('★', rarity), 12, rc);
        stars.SetPadding(0, Dp(2), 0, 0);
        bar.AddView(name);
        bar.AddView(stars);
        frame.AddView(bar);

        return frame;
    }

    View BuildFaceBack(int rarity)
    {
        // 统一 twilight 神性卡背（2D）。FlipCardView 会把它撑满 MatchParent。
        return CardBack.Build(this, 0, 0);
    }

    void HideReveal()
    {
        // #44: 先立刻摘掉点击能力，否则淡出的这 300ms 里全屏透明层仍会吞掉点击。
        _revealContainer.Clickable = false;
        if (_revealClick != null)
        {
            _revealContainer.Click -= _revealClick;
            _revealClick = null;
        }
        _revealContainer.Animate()?.Alpha(0f)?.SetDuration(300)?.Start();
        PostSafe(() =>
        {
            _revealContainer.RemoveAllViews();
            _revealContainer.Alpha = 0f;
        }, 350);
    }

    /// <summary>释放专属武器特效层（native 位图），避免泄漏。跳过与普通移除共用。</summary>
    void DisposeRevealWeapon()
    {
        if (_revealWeapon == null) return;
        _revealContainer.RemoveView(_revealWeapon);
        _revealWeapon.Dispose();
        _revealWeapon = null;
    }

    /// <summary>U2：跳过抽卡演出。立即展示结果并复位状态；递增 _revealToken 使所有挂起演出回调变为 no-op。</summary>
    void SkipReveal(int token)
    {
        if (token != _revealToken || !_busy) return;
        _revealToken++; // 作废后续全部挂起演出回调（含 1900ms 的 ShowResults/HideReveal）
        DisposeRevealWeapon();
        if (_currentResults != null) ShowResults(_currentResults);
        HideReveal();
        _rift.Animate()?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(200)?.Start();
        _busy = false;
    }

    void ShowResults(List<PullResult> results)
    {
        _resultsRoot.RemoveAllViews();
        if (results.Count == 0) return;

        int perRow = 5;
        LinearLayout? row = null;
        for (int i = 0; i < results.Count; i++)
        {
            if (i % perRow == 0)
            {
                if (i > 0) _resultsRoot.AddView(Spacer(10));
                row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
                row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
                row.SetGravity(GravityFlags.CenterHorizontal);
                _resultsRoot.AddView(row);
            }
            var result = results[i];
            var chip = CharacterCard.GachaChip(this, result, () => OpenCharacterDetail(result.CharacterId ?? ""));
            row!.AddView(chip);
            chip.Alpha = 0f;
            chip.ScaleX = 0.5f; chip.ScaleY = 0.5f;
            chip.Animate()?.Alpha(1f)?.ScaleX(1f)?.ScaleY(1f)?.SetDuration(250)?.SetStartDelay(i * 50)?.Start();
        }

        int ssr = results.Count(r => r.Rarity >= 3);
        int fragments = results.Sum(r => r.FragmentsAwarded);
        var fragText = fragments > 0 ? $"  ·  星魂碎片 +{fragments}" : "";
        _summary.Text = $"共 {results.Count} 抽  ·  SSR+ {ssr}{fragText}  ✦  最新: {results.Last().CharacterName}";
    }
}
