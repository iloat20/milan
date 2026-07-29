using Android.App;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Widget;
using Milan.Maui;
using Milan.Maui.Services;

namespace Milan.Maui.Activities;

[Activity(Label = "抽卡")]
public class GachaActivity : Activity
{
    private LinearLayout _resultsRoot = null!;
    private TextView _currency = null!;
    private TextView _summary = null!;
    private FrameLayout _flash = null!;
    private View _rift = null!;
    private bool _busy;

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

        var content = new LinearLayout(this) { Orientation = Orientation.Vertical };
        content.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        content.SetPadding(Dp(20), Dp(40), Dp(20), Dp(16));

        // Top bar
        var top = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        var back = UI.Text("‹ 返回", 16, AppTheme.CosmicPrimarySoft);
        back.Clickable = true; back.Focusable = true;
        back.Click += (s, e) => Finish();
        var head = UI.Text("次 元 裂 缝", 22, AppTheme.CosmicTextPrimary, bold: true);
        head.LetterSpacing = 0.1f;
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        _currency = UI.Text(GameState.CurrencyLabel, 13, AppTheme.CosmicGold);
        top.AddView(back); top.AddView(head); top.AddView(spacer); top.AddView(_currency);
        content.AddView(top);
        content.AddView(Spacer(12));

        // Pool info
        var info = new LinearLayout(this) { Orientation = Orientation.Vertical };
        info.SetPadding(Dp(14), Dp(10), Dp(14), Dp(10));
        var infoBg = new Android.Graphics.Drawables.GradientDrawable();
        infoBg.SetCornerRadius(Dp(12));
        infoBg.SetColor(Color.Argb(30, 124, 77, 255));
        infoBg.SetStroke(1, Color.Argb(80, 124, 77, 255));
        info.Background = infoBg;
        info.AddView(UI.Text("常驻卡池 · 次元裂缝", 14, AppTheme.CosmicTextPrimary, bold: true));
        info.AddView(UI.Text("UR 10%  ·  SSR 20%  ·  SR 30%  ·  R 40%", 10, AppTheme.CosmicTextMuted));
        content.AddView(info);
        content.AddView(Spacer(16));

        // Summoning rift
        var rift = new ParticleView(this);
        rift.Configure("glow", AppTheme.CosmicPrimarySoft, 3);
        var riftLp = new LinearLayout.LayoutParams(Dp(160), Dp(160));
        
        rift.LayoutParameters = riftLp;
        _rift = rift;
        content.AddView(rift);
        ((ParticleView)_rift).Start();

        var hint = UI.Text("✦ 点击下方按钮召唤 ✦", 10, AppTheme.CosmicTextMuted);
        
        hint.SetPadding(0, Dp(8), 0, 0);
        content.AddView(hint);
        content.AddView(Spacer(16));

        // Buttons
        var buttons = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        var singleBtn = new GlowButton(this, "单 抽", AppTheme.CosmicPrimary, Color.White, 12);
        singleBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        singleBtn.Click += (s, e) => DoPull(false);
        var tenBtn = new GlowButton(this, "十 连", Color.ParseColor("#ff6b00"), Color.White, 12);
        tenBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        tenBtn.Click += (s, e) => DoPull(true);
        buttons.AddView(singleBtn);
        buttons.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(Dp(12), 0) });
        buttons.AddView(tenBtn);
        content.AddView(buttons);
        content.AddView(Spacer(12));

        _summary = UI.Text("", 11, AppTheme.CosmicTextSecondary);
        content.AddView(_summary);
        content.AddView(Spacer(6));

        var scroll = new ScrollView(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f) };
        _resultsRoot = new LinearLayout(this) { Orientation = Orientation.Vertical };
        scroll.AddView(_resultsRoot);
        content.AddView(scroll);

        root.AddView(content);

        // Flash overlay (hidden) — enabled only during flash
        _flash = new FrameLayout(this);
        _flash.LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        _flash.SetBackgroundColor(Color.White);
        _flash.Alpha = 0f;
        _flash.Clickable = false; // don't block touches when hidden
        _flash.Enabled = false;
        root.AddView(_flash);

        return root;
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
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
        _rift.Animate().ScaleX(1.3f).ScaleY(1.3f).SetDuration(200).Start();

        // Flash
        _flash.Clickable = true;
        _flash.Enabled = true;
        _flash.Alpha = 1f;
        _flash.Animate().Alpha(0f).SetDuration(400).Start();
        new Handler(Looper.MainLooper).PostDelayed(() => { _flash.Clickable = false; _flash.Enabled = false; }, 450);

        var results = GameState.Service.Pull(pool.PoolId, tenPull);
        _currency.Text = GameState.CurrencyLabel;

        new Handler(Looper.MainLooper).PostDelayed(() =>
        {
            ShowResults(results);
            _rift.Animate().ScaleX(1f).ScaleY(1f).SetDuration(200).Start();
            _busy = false;
        }, 350);
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
                row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
                row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
                _resultsRoot.AddView(row);
            }
            var def = GameState.Service.Characters.FirstOrDefault(c => c.CharacterId == results[i].CharacterId);
            var card = new ResultCard(this, results[i], def);
            row!.AddView(card);
            card.Alpha = 0f;
            card.ScaleX = 0.5f; card.ScaleY = 0.5f;
            card.Animate().Alpha(1f).ScaleX(1f).ScaleY(1f).SetDuration(250).SetStartDelay(i * 50).Start();
        }

        int ssr = results.Count(r => r.Rarity >= 3);
        _summary.Text = $"共 {results.Count} 抽  ·  SSR+ {ssr}  ✦  最新: {results.Last().CharacterName}";
    }
}
