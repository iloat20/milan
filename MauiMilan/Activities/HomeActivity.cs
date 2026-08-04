using Android.App;
using Android.Animation;
using Android.Content;
using Android.Content.PM;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Views;
using Android.Widget;
using Milan.Infrastructure.EventBus;
using Milan.Maui;
using Milan.Maui.Services;

namespace Milan.Maui.Activities;

[Activity(Label = "Milan", MainLauncher = true, Theme = "@android:style/Theme.Material.NoActionBar",
          ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize)]
public class HomeActivity : Activity
{
    private TextView _soft = null!;
    private TextView _hard = null!;
    private PortraitView _featured = null!;
    // #24: Hero 立绘的无限漂浮动画，需在页面销毁时取消
    private ObjectAnimator? _floatAnim;
    // 入场编排的目标容器（竖屏 inner / 横屏 rInner）
    private ViewGroup? _entranceRoot;

    /// <summary>取消并释放 Hero 漂浮动画。重建布局或销毁页面前必须调用。</summary>
    void StopFloatAnim()
    {
        if (_floatAnim == null) return;
        try { _floatAnim.Cancel(); } catch { }
        try { _floatAnim.Dispose(); } catch { }
        _floatAnim = null;
    }

    protected override void OnDestroy()
    {
        StopFloatAnim();
        base.OnDestroy();
    }

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        CrashReporter.Boot("home.oncreate.begin");

        // 防黑屏：先放一个带深色背景的占位。这一步本身也要保护——它早于 try 执行，
        // 之前一旦在这里抛异常就是「无提示闪退」，正是最难查的那种。
        try { SetContentView(BuildFallback("Milan · 加载中…")); }
        catch (System.Exception ex) { CrashReporter.Write("HomeActivity.BuildFallback", ex); }

        try
        {
            CrashReporter.Boot("home.gamestate.init");
            GameState.EnsureInitialized(this);
            CrashReporter.Boot("home.buildlayout");
            var layout = BuildLayout();
            SetContentView(layout);
            CrashReporter.Boot("home.oncreate.done");
            ShowPreviousCrashIfAny();
        }
        catch (System.Exception ex)
        {
            CrashReporter.Write("HomeActivity.OnCreate", ex);
            // 关键：不再静默吞异常——把真实错误暴露给用户，便于定位根因。
            var detail = $"[{ex.GetType().Name}] {ex.Message}\n\n{ex.StackTrace}";
            Android.Util.Log.Error("[Milan]", detail);
            RunOnUiThread(() =>
            {
                // 整段包住：报错对话框自己再抛异常的话，进程会静默死掉，
                // 反而把真正的错误吃掉——这是排查期最坑的一种连锁失败。
                try
                {
                    try { SetContentView(BuildFallback($"启动失败：{ex.GetType().Name}\n{ex.Message}")); } catch { }
                    var shown = detail.Length > 1400 ? detail.Substring(0, 1400) : detail;
                    new AlertDialog.Builder(this)
                        .SetTitle("Milan 启动错误")
                        .SetMessage(shown)
                        .SetPositiveButton("退出", (_, _) => Finish())
                        .SetCancelable(false)
                        .Show();
                }
                catch (System.Exception dlgEx) { CrashReporter.Write("HomeActivity.ErrorDialog", dlgEx); }
            });
        }
    }

    /// <summary>上一次是崩溃退出的话，把落盘的现场回显出来（设备连不上 adb 时的取证手段）。</summary>
    void ShowPreviousCrashIfAny()
    {
        try
        {
            var report = CrashReporter.ReadAndClear();
            if (string.IsNullOrEmpty(report))
            {
                // 没有托管异常报告，但上轮启动没走完 —— 说明是 native 层崩溃。
                if (!CrashReporter.PreviousBootIncomplete()) return;
                report = "未捕获到托管异常，但上次启动未走完流程 —— 疑似 native 层崩溃。\n\n"
                       + "上次启动面包屑：\n" + (CrashReporter.PreviousBootTrace() ?? "(无)");
            }

            var shown = report.Length > 3000 ? report.Substring(0, 3000) : report;
            new AlertDialog.Builder(this)
                .SetTitle("上次异常退出的现场")
                .SetMessage(shown)
                .SetPositiveButton("复制", (_, _) =>
                {
                    try
                    {
                        var cm = (Android.Content.ClipboardManager?)GetSystemService(ClipboardService);
                        cm?.PrimaryClip?.Dispose();
                        cm!.PrimaryClip = ClipData.NewPlainText("milan-crash", report);
                        Toast.MakeText(this, "已复制到剪贴板", ToastLength.Short)?.Show();
                    }
                    catch { }
                })
                .SetNegativeButton("关闭", (_, _) => { })
                .Show();
        }
        catch (System.Exception ex) { CrashReporter.Write("ShowPreviousCrashIfAny", ex); }
    }

    /// <summary>深色背景占位（加载中 / 出错兜底），避免异常时纯黑窗。</summary>
    FrameLayout BuildFallback(string msg)
    {
        var root = new FrameLayout(this);
        root.SetBackgroundColor(AppTheme.BgDeepest);
        var t = UI.Text(msg, 15, AppTheme.Text2);
        t.SetPadding(UI.Dp(24), UI.Dp(24), UI.Dp(24), UI.Dp(24));
        t.LayoutParameters = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent)
        { Gravity = GravityFlags.Center };
        root.AddView(t);
        return root;
    }

    public override void OnConfigurationChanged(Android.Content.Res.Configuration newConfig)
    {
        base.OnConfigurationChanged(newConfig);
        // 横竖屏切换：重建布局（响应式）。这里抛异常同样是无提示闪退，必须兜住。
        try { SetContentView(BuildLayout()); }
        catch (System.Exception ex)
        {
            CrashReporter.Write("HomeActivity.OnConfigurationChanged", ex);
            try { SetContentView(BuildFallback($"旋转屏幕时出错：{ex.GetType().Name}\n{ex.Message}")); } catch { }
        }
    }

    // 事件驱动刷新资源栏（替代 OnResume 整页重建）。订阅幂等 + OnPause 退订，保证同页至多一个有效订阅。
    void OnCurrencyChanged(CurrencyChanged _) => RefreshCurrency();

    void RefreshCurrency()
    {
        if (_soft != null) _soft.Text = GameState.Currency.ToString("N0");
        if (_hard != null) _hard.Text = GameState.Service.SaveData.HardCurrency.ToString("N0");
    }

    protected override void OnResume()
    {
        base.OnResume();
        // OnResume 在 OnCreate 的 try 之外执行，这里抛异常就是「启动后立刻闪退且无对话框」。
        try
        {
            // 先订阅再直刷：回到前台立即兜底显示最新值（订阅端也会在事件到达时刷新）。
            EventBus.Subscribe<CurrencyChanged>(OnCurrencyChanged);
            RefreshCurrency();
        }
        catch (System.Exception ex) { CrashReporter.Write("HomeActivity.OnResume", ex); }
    }

    protected override void OnPause()
    {
        // 页面不可见即摘掉处理器，防止泄漏；重新可见时 OnResume 会重新订阅。
        EventBus.UnsubscribeAll(this);
        base.OnPause();
    }

    FrameLayout BuildLayout()
    {
        var land = Responsive.IsLandscape(this);

        var root = new FrameLayout(this);
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);

        // 深黑底 + 粒子
        var bgGrad = new GradientDrawable(
            GradientDrawable.Orientation.TlBr,
            new[] { AppTheme.BgDeepest.ToArgb(), Color.ParseColor("#0E0A1E").ToArgb(), AppTheme.BgDeepest.ToArgb() });
        root.Background = (bgGrad);

        var bg = new TwilightBackground(this);
        bg.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        bg.Alpha = 0.22f;
        root.AddView(bg);
        bg.Start();

        // 主列：资源栏 / 主体 / 导航
        var main = UI.VBox();
        main.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        main.SetPadding(UI.Dp(0), UI.Dp(12), UI.Dp(0), 0);

        main.AddView(BuildResourceBar());

        // 主体
        var body = new FrameLayout(this);
        body.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f);
        if (land) body.AddView(BuildLandscapeBody());
        else body.AddView(BuildPortraitBody());
        main.AddView(body);

        // 底部导航
        var nav = new GameNavBar(this, GameNavBar.NavItem.Home, OnNav);
        nav.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        main.AddView(nav);

        root.AddView(main);
        root.Post(() => PlayEntrance(main, _entranceRoot));
        return root;
    }

    // ── 资源栏（持引用，OnResume 刷新）──
    LinearLayout BuildResourceBar()
    {
        var bar = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        bar.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        bar.SetPadding(UI.Dp(16), UI.Dp(6), UI.Dp(16), UI.Dp(6));
        bar.SetGravity(GravityFlags.CenterVertical);

        var logo = UI.Text("MILAN", 22, AppTheme.Gold, bold: true);
        logo.LetterSpacing = 0.12f;
        logo.SetShadowLayer(10, 0, 2, AppTheme.Gold);
        var sub = UI.Text("诸神黄昏·东方", 10, AppTheme.Text2);
        sub.SetPadding(0, UI.Dp(1), 0, 0);
        var logoCol = UI.VBox();
        logoCol.AddView(logo); logoCol.AddView(sub);
        bar.AddView(logoCol);

        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        bar.AddView(spacer);

        // 资源胶囊
        var pill = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        pill.Background = UI.GlassPanel(16, gold: false);
        pill.SetPadding(UI.Dp(12), UI.Dp(7), UI.Dp(12), UI.Dp(7));
        pill.SetGravity(GravityFlags.CenterVertical);

        var sGlyph = UI.Text("✦", 14, AppTheme.Gold, bold: true);
        sGlyph.SetPadding(0, 0, UI.Dp(4), 0);
        _soft = UI.Text(GameState.Currency.ToString("N0"), 14, AppTheme.Text1, bold: true);
        UI.Tabular(_soft);
        pill.AddView(sGlyph); pill.AddView(_soft);

        var gap = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(10), 0) };
        pill.AddView(gap);

        var hGlyph = UI.Text("❖", 14, AppTheme.Frost, bold: true);
        hGlyph.SetPadding(0, 0, UI.Dp(4), 0);
        _hard = UI.Text(GameState.Service.SaveData.HardCurrency.ToString("N0"), 14, AppTheme.Text1, bold: true);
        UI.Tabular(_hard);
        pill.AddView(hGlyph); pill.AddView(_hard);

        bar.AddView(pill);
        return bar;
    }

    // ── 竖屏主体：滚动（主视觉 + 按钮 + 入口网格）──
    ScrollView BuildPortraitBody()
    {
        var scroll = new ScrollView(this)
        {
            LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent)
        };
        var inner = UI.VBox();
        inner.SetPadding(UI.Dp(14), UI.Dp(6), UI.Dp(14), UI.Dp(8));

        inner.AddView(BuildHero(430));
        inner.AddView(Spacer(10));
        inner.AddView(BuildHeroButtons());
        inner.AddView(Spacer(14));
        inner.AddView(SectionTitle("诸神名录", "UNIFIED AVATARS"));
        inner.AddView(Spacer(8));
        inner.AddView(BuildAvatarStrip());
        inner.AddView(Spacer(6));
        var loreNote = UI.Text("✦ 立绘皆源自山海经与中国上古神话，依各自背景故事创作", 10, AppTheme.TwilightTextSecondary);
        loreNote.SetPadding(UI.Dp(14), 0, UI.Dp(14), 0);
        inner.AddView(loreNote);
        _entranceRoot = inner;
        scroll.AddView(inner);
        return scroll;
    }

    // ── 横屏主体：左主视觉 + 右入口网格 ──
    LinearLayout BuildLandscapeBody()
    {
        var hbox = UI.HBox();
        hbox.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);

        var left = UI.VBox();
        var leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MatchParent, 1.05f);
        leftLp.SetMargins(UI.Dp(16), UI.Dp(10), UI.Dp(8), UI.Dp(10));
        left.LayoutParameters = leftLp;
        left.AddView(BuildHero(400));
        left.AddView(Spacer(12));
        left.AddView(BuildHeroButtons());
        hbox.AddView(left);

        var right = new ScrollView(this)
        {
            LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MatchParent, 1f)
        };
        var rInner = UI.VBox();
        rInner.SetPadding(UI.Dp(8), UI.Dp(10), UI.Dp(16), UI.Dp(10));
        rInner.AddView(SectionTitle("诸神名录", "UNIFIED AVATARS"));
        rInner.AddView(Spacer(10));
        rInner.AddView(BuildAvatarStrip());
        _entranceRoot = rInner;
        right.AddView(rInner);
        hbox.AddView(right);
        return hbox;
    }

    // ── 主视觉：角色大图 + 底部铭牌 ──
    // fixedH 单位是 dp（不是 px）。调用方以前传的是 UI.Dp(430)，这里又 UI.Dp 一次，
    // 在 3x 屏上高度被放大成 430*3*3 = 3870px，主视觉把整屏顶掉。
    FrameLayout BuildHero(int fixedH)
    {
        var hero = new FrameLayout(this);
        if (fixedH > 0)
            hero.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, UI.Dp(fixedH));
        else
            hero.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        hero.SetClipChildren(true);

        var def = FeaturedCharacter();

        // 稀有度光晕（脉动，位于立绘之后）
        var haloColor = AppTheme.RarityColor(def.BaseRarity);
        var halo = new HaloView(this, haloColor);
        halo.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        hero.AddView(halo);

        _featured = new PortraitView(this).Bind(def);
        _featured.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        _featured.Cover = true;            // 立绘顶满 Hero，更大气
        _featured.VerticalBias = 0.5f;    // cover 模式下居中裁切
        hero.AddView(_featured);

        // 立绘漂浮（±10dp 缓动，伪 3D 呼吸感）
        // #24: 无限循环动画必须持有引用并在 OnDestroy 取消，否则每次 OnResume 重建布局
        // 都会新起一条永不停止的动画，持有旧视图引用 → 泄漏 + 掉帧。
        StopFloatAnim();
        _floatAnim = ObjectAnimator.OfFloat(_featured, "translationY", 0f, -UI.Dp(10), 0f);
        _floatAnim.SetDuration(6000);
        _floatAnim.RepeatCount = ValueAnimator.Infinite;
        _floatAnim.RepeatMode = ValueAnimatorRepeatMode.Restart;
        _floatAnim.SetInterpolator(new Android.Views.Animations.AccelerateDecelerateInterpolator());
        _floatAnim.Start();

        // 底部暗化渐变 + 铭牌
        var overlay = new LinearLayout(this) { Orientation = Orientation.Vertical };
        var oLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        oLp.Gravity = GravityFlags.Bottom | GravityFlags.CenterHorizontal;
        overlay.LayoutParameters = oLp;
        overlay.SetPadding(UI.Dp(18), UI.Dp(48), UI.Dp(18), UI.Dp(16));
        var shade = new GradientDrawable(
            GradientDrawable.Orientation.BottomTop,
            new[] { Color.Argb(228, 7, 7, 15).ToArgb(), Color.Argb(120, 7, 7, 15).ToArgb(), Color.Argb(0, 7, 7, 15).ToArgb() });
        overlay.Background = (shade);

        // 顶部金色渐隐分隔线
        var sep = new View(this);
        sep.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, UI.Dp(1));
        sep.Background = new GradientDrawable(GradientDrawable.Orientation.LeftRight,
            new[] { Color.Argb(0, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B).ToArgb(), Color.Argb(160, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B).ToArgb(), Color.Argb(0, AppTheme.Gold.R, AppTheme.Gold.G, AppTheme.Gold.B).ToArgb() });
        overlay.AddView(sep);
        overlay.AddView(Spacer(10));

        var name = UI.Text(def.DisplayName, 22, AppTheme.Text1, bold: true);
        name.SetShadowLayer(10, 0, 2, Color.Argb(180, 0, 0, 0));
        var title = UI.Text(def.Title, 12, AppTheme.Text2);
        title.SetPadding(0, UI.Dp(3), 0, 0);

        // 标签行：稀有度 + 世界
        var tagRow = UI.HBox();
        tagRow.SetPadding(0, UI.Dp(8), 0, 0);
        var rarityCol = AppTheme.RarityColor(def.BaseRarity);
        var rtag = UI.Text(AppTheme.RarityName(def.BaseRarity), 11, rarityCol, bold: true);
        var rbg = new GradientDrawable();
        rbg.SetCornerRadius(UI.Dp(8));
        rbg.SetColor(Color.Argb(70, rarityCol.R, rarityCol.G, rarityCol.B));
        rbg.SetStroke(UI.Dp(1), rarityCol);
        rtag.Background = rbg;
        rtag.SetPadding(UI.Dp(10), UI.Dp(3), UI.Dp(10), UI.Dp(3));
        tagRow.AddView(rtag);

        var wtag = UI.Text(def.World, 11, AppTheme.TwilightTextSecondary);
        wtag.SetPadding(UI.Dp(10), UI.Dp(3), UI.Dp(10), UI.Dp(3));
        wtag.Background = UI.GlassPanel(8);
        tagRow.AddView(wtag);

        overlay.AddView(name); overlay.AddView(title); overlay.AddView(tagRow);
        hero.AddView(overlay);

        // 外框玻璃描边
        hero.Background = UI.GlassPanel(18, gold: true);
        return hero;
    }

    // ── 主视觉下方动作钮 ──
    LinearLayout BuildHeroButtons()
    {
        var row = UI.HBox();
        row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        row.SetGravity(GravityFlags.CenterVertical);

        var summon = ThemeButtons.Gold(this, "✦ 前往召唤", 15);
        summon.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        summon.Click += (_, _) => StartActivity(typeof(GachaActivity));
        row.AddView(summon);

        var gap = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(12), 0) };
        row.AddView(gap);

        var chars = ThemeButtons.Neon(this, "神谱图鉴", 15);
        chars.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        chars.Click += (_, _) => StartActivity(typeof(CollectionActivity));
        row.AddView(chars);
        return row;
    }

    // ── 诸神名录：统一头像横滑（替代原功能入口网格）──
    HorizontalScrollView BuildAvatarStrip()
    {
        var scroll = new HorizontalScrollView(this)
        {
            LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent)
        };
        scroll.FillViewport = false;
        scroll.HorizontalScrollBarEnabled = false;

        var row = UI.HBox();
        row.SetPadding(UI.Dp(14), UI.Dp(2), UI.Dp(14), UI.Dp(2));

        var picks = new (string id, string name, string src)[]
        {
            ("char_ur_zhulong", "烛龙", "山海经"),
            ("char_ur_xingtian", "刑天", "中国神话"),
            ("char_ssr_fenghuang", "凤凰", "山海经"),
            ("char_sr_bifang", "毕方", "山海经"),
            ("char_sr_jingwei", "精卫", "中国神话"),
            ("char_ssr_leishen", "雷神", "山海经"),
        };

        foreach (var p in picks)
        {
            var def = GameState.Service.Characters.FirstOrDefault(c => c.CharacterId == p.id);
            if (def == null) continue;

            var col = UI.VBox();
            col.SetGravity(GravityFlags.CenterHorizontal);
            var colLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WrapContent, ViewGroup.LayoutParams.WrapContent);
            colLp.SetMargins(0, 0, UI.Dp(12), 0);
            col.LayoutParameters = colLp;

            var av = new UnifiedAvatarView(this).Bind(def, 58);
            av.LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(58), UI.Dp(58));
            col.AddView(av);

            var nm = UI.Text(p.name, 10, AppTheme.TwilightTextPrimary, bold: true);
            nm.Gravity = GravityFlags.Center;
            nm.SetPadding(0, UI.Dp(3), 0, 0);
            var src = UI.Text(p.src, 8, AppTheme.TwilightTextSecondary);
            src.Gravity = GravityFlags.Center;
            col.AddView(nm); col.AddView(src);

            col.Clickable = true; col.Focusable = true;
            var pid = p.id;
            UI.TapFeedback(col, () =>
            {
                var intent = new Intent(this, typeof(CharacterDetailActivity));
                intent.PutExtra("characterId", pid);
                StartActivity(intent);
            });
            row.AddView(col);
        }

        scroll.AddView(row);
        return scroll;
    }

    // ── 小标题：左金线 + 标题 + 英文副标 ──
    LinearLayout SectionTitle(string title, string en)
    {
        var row = UI.HBox();
        row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        row.SetGravity(GravityFlags.CenterVertical);
        row.SetPadding(UI.Dp(14), 0, UI.Dp(14), 0);

        var bar = new View(this)
        {
            LayoutParameters = new LinearLayout.LayoutParams(UI.Dp(3), UI.Dp(15))
        };
        bar.Background = UI.RoundRect(AppTheme.TwilightGoldBright, 2);
        row.AddView(bar);

        var t = UI.Text(title, 13, AppTheme.Text1, bold: true);
        t.SetPadding(UI.Dp(8), 0, UI.Dp(6), 0);
        row.AddView(t);

        var sub = UI.Text(en, 9, AppTheme.TwilightTextSecondary);
        sub.LetterSpacing = 0.1f;
        row.AddView(sub);
        return row;
    }

    CharacterDataEntry FeaturedCharacter()
    {
        // 存档里的角色可能在当前内容表里查不到（换过数据源 / 走了兜底数据），
        // 这时 Def 为 null，原来直接 `.Def!` 返回 null，下游 def.BaseRarity 必然 NRE。
        var owned = GameState.Owned();
        var best = owned.Where(c => c.Def != null)
                        .OrderByDescending(c => c.Rarity)
                        .FirstOrDefault();
        if (best?.Def != null) return best.Def;

        var any = GameState.Service.Characters.FirstOrDefault();
        if (any != null) return any;

        // 内容表彻底为空时的最后防线：造一个占位角色，宁可难看也不能崩。
        return new CharacterDataEntry
        {
            CharacterId = "placeholder",
            DisplayName = "未知存在",
            Title = "数据缺失",
            World = "Shinwa",
            Element = "Flame",
            BaseRarity = 1,
            BaseStats = new[] { 10, 10, 100, 10 },
        };
    }

    void OnNav(GameNavBar.NavItem item)
    {
        var target = item switch
        {
            GameNavBar.NavItem.Home => typeof(HomeActivity),
            GameNavBar.NavItem.Gacha => typeof(GachaActivity),
            GameNavBar.NavItem.Deck => typeof(DeckActivity),
            GameNavBar.NavItem.Shop => typeof(ShopActivity),
            GameNavBar.NavItem.Settings => typeof(SettingsActivity),
            _ => null
        };
        Nav.To(this, target);
    }

    /// <summary>
    /// 入场编排（暗夜神性·诸神黄昏）：背景淡入 → 立绘浮入 → 主体各区块错落上浮（stagger 60ms，ease-out）。
    /// 仅做变换/透明动画，不动布局，安全可重入（横竖屏切换重建后再次播放）。
    /// </summary>
    void PlayEntrance(ViewGroup main, ViewGroup? entranceRoot)
    {
        try
        {
            var decel = new Android.Views.Animations.DecelerateInterpolator();
            main.Alpha = 0f;
            main.Animate()?.Alpha(1f)?.SetDuration(320)?.SetInterpolator(decel)?.Start();

            // 立绘浮入（不动 translationY，避免与 Hero 漂浮动画冲突）
            if (_featured != null)
            {
                _featured.Alpha = 0f;
                _featured.Animate()?.Alpha(1f)?.SetDuration(440)?.SetInterpolator(decel)?.Start();
            }

            // 主体各区块错落上浮
            if (entranceRoot != null)
            {
                for (int i = 0; i < entranceRoot.ChildCount; i++)
                {
                    var c = entranceRoot.GetChildAt(i);
                    if (c == null) continue;
                    c.Alpha = 0f;
                    c.TranslationY = UI.Dp(14);
                    c.Animate()?.Alpha(1f)?.TranslationY(0)?.SetDuration(380)
                        ?.SetStartDelay(140 + i * 60)?.SetInterpolator(decel)?.Start();
                }
            }
        }
        catch (System.Exception ex) { CrashReporter.Write("HomeActivity.PlayEntrance", ex); }
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }

    /// <summary>稀有度径向光晕：RadialGradient 圆 + 呼吸脉动，位于立绘之后。</summary>
    private class HaloView : View
    {
        private readonly Color _color;
        private Paint? _paint;
        private float _phase;
        private bool _animating = true;

        public HaloView(Context context, Color color) : base(context)
        {
            _color = color;
            SetWillNotDraw(false);
        }

        protected override void OnDraw(Canvas canvas)
        {
            try
            {
                if (_paint == null) _paint = new Paint { AntiAlias = true };
                _paint.SetStyle(Paint.Style.Fill);
                var cx = Width / 2f;
                var cy = Height / 2f;
                var r = Math.Min(Width, Height) * 0.5f;
                // RadialGradient 半径 <= 0 会抛 IllegalArgumentException("radius must be > 0")。
                // OnDraw 在 Activity 的 try/catch 之外执行，抛出即进程静默死亡、无任何对话框。
                if (r <= 0f)
                {
                    if (_animating) PostInvalidateDelayed(33);
                    return;
                }
                _phase += 0.02f;
                if (_phase > MathF.PI * 2) _phase -= MathF.PI * 2;
                var breathe = 0.5f + 0.5f * MathF.Sin(_phase);
                // 关键修复：本 ROM（ColorOS/Android15）上 RadialGradient 接收 int 颜色时，内部
                // Color.valueOf(int) 会返回非法 ColorSpace(id 23)，detectColorSpace 抛
                // IllegalArgumentException，渲染线程直接杀进程、无对话框。改为传 Color 对象（走 sRGB
                // ColorLong 重载）即可彻底避开；外层 try/catch 兜底，即使极端情况建不出渐变也不杀进程。
                var centerColor = Color.Argb((int)(150 + 60 * breathe), _color.R, _color.G, _color.B);
                var edgeColor = Color.Argb(0, _color.R, _color.G, _color.B);
                _paint.SetShader(new RadialGradient(cx, cy, r, UI.ColorLong(centerColor), UI.ColorLong(edgeColor), Shader.TileMode.Clamp));
                canvas.DrawCircle(cx, cy, r, _paint);
                _paint.SetShader(null);
            }
            catch (Exception)
            {
                // OnDraw 抛异常会静默杀进程；兜底跳过本次绘制以保活（光晕可能不显示）。
            }
            if (_animating) PostInvalidateDelayed(33);
        }

        protected override void OnAttachedToWindow() { base.OnAttachedToWindow(); _animating = true; Invalidate(); }
        protected override void OnDetachedFromWindow() { base.OnDetachedFromWindow(); _animating = false; }
        protected override void OnWindowVisibilityChanged(ViewStates visibility)
        {
            base.OnWindowVisibilityChanged(visibility);
            _animating = visibility == ViewStates.Visible;
            if (_animating) Invalidate();
        }
    }

}
