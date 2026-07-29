using Android.App;
using Android.Content;
using Android.Graphics;
using Android.OS;
using Android.Text;
using Android.Views;
using Android.Widget;
using Milan.Maui;
using Milan.Maui.Services;

namespace Milan.Maui.Activities;

[Activity(Label = "抽卡")]
public class GachaActivity : Activity
{
    LinearLayout _resultsRoot = null!;
    TextView _currency = null!;
    TextView _summary = null!;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        SetContentView(Build());
    }

    LinearLayout Build()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.SetPadding(Dp(20), Dp(44), Dp(20), Dp(24));
        root.SetBackgroundDrawable(UI.Gradient(AppTheme.Background, Color.ParseColor("#10182e")));

        // ---- Top bar: back + title ----
        var top = UI.HBox();
        var back = UI.Text("‹ 返回", 16, AppTheme.AccentAlt);
        back.SetPadding(0, 0, Dp(12), 0);
        back.Clickable = true; back.Focusable = true;
        back.Click += (s, e) => Finish();
        var headTitle = UI.Text("次 元 裂 缝", 24, AppTheme.TextPrimary, bold: true);
        headTitle.LetterSpacing = 0.1f;
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        _currency = UI.Text(GameState.CurrencyLabel, 15, AppTheme.Gold);
        top.AddView(back);
        top.AddView(headTitle);
        top.AddView(spacer);
        top.AddView(_currency);
        root.AddView(top);
        root.AddView(Spacer(18));

        // pool banner
        var banner = UI.VBox();
        banner.Background = UI.RoundRect(AppTheme.Surface, 16);
        banner.SetPadding(Dp(18), Dp(16), Dp(18), Dp(16));
        var poolName = UI.Text("常驻卡池 · 次元裂缝", 18, AppTheme.TextPrimary, bold: true);
        var poolHint = UI.Text("SSR 3%  ·   SR 15%  ·  90 抽硬保底", 12, AppTheme.TextSecondary);
        poolHint.SetPadding(0, Dp(4), 0, 0);
        banner.AddView(poolName);
        banner.AddView(poolHint);
        root.AddView(banner);
        root.AddView(Spacer(18));

        // ---- Pull buttons ----
        var buttons = UI.HBox();
        var singleBtn = UI.Button("单 抽", AppTheme.Accent, Color.White, 14);
        singleBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        singleBtn.Click += (s, e) => DoPull(false);
        var tenBtn = UI.Button("十 连", Color.ParseColor("#7b2ff7"), Color.White, 14);
        tenBtn.LayoutParameters = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        tenBtn.Click += (s, e) => DoPull(true);

        buttons.AddView(singleBtn);
        buttons.AddView(new View(this) { LayoutParameters = new LinearLayout.LayoutParams(Dp(14), 0) });
        buttons.AddView(tenBtn);
        root.AddView(buttons);
        root.AddView(Spacer(18));

        // ---- Results area ----
        _summary = UI.Text("", 13, AppTheme.TextSecondary);
        root.AddView(_summary);
        root.AddView(Spacer(8));

        var scroll = new ScrollView(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f) };
        _resultsRoot = UI.VBox();
        scroll.AddView(_resultsRoot);
        root.AddView(scroll);

        return root;
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }

    void DoPull(bool tenPull)
    {
        var pool = GameState.Service.Pools.FirstOrDefault();
        if (pool == null) { Toast.MakeText(this, "无可用卡池", ToastLength.Short)?.Show(); return; }

        var results = GameState.Service.Pull(pool.PoolId, tenPull);
        _currency.Text = GameState.CurrencyLabel;

        if (results.Count == 0)
        {
            Toast.MakeText(this, "星尘不足", ToastLength.Short)?.Show();
            return;
        }

        _resultsRoot.RemoveAllViews();

        // Group into rows of 5 result chips.
        int perRow = 5;
        LinearLayout? row = null;
        for (int i = 0; i < results.Count; i++)
        {
            if (i % perRow == 0)
            {
                row = UI.HBox();
                row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
                _resultsRoot.AddView(row);
            }
            row!.AddView(CharacterCard.GachaChip(this, results[i]));
        }

        // summary line
        int ssr = results.Count(r => r.Rarity >= 3);
        _summary.Text = $"共 {results.Count} 抽  ·  SSR+ {ssr}  ✦  最新: {results.Last().CharacterName}";
    }
}
