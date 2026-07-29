using Android.App;
using Android.Content;
using Android.Graphics;
using Android.OS;
using Android.Text;
using Android.Views;
using Android.Widget;
using Milan.Maui;

namespace Milan.Maui.Activities;

[Activity(Label = "角色")]
public class CharacterListActivity : Activity
{
    LinearLayout _gridRoot = null!;
    TextView _currency = null!;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);
        SetContentView(Build());
    }

    protected override void OnResume()
    {
        base.OnResume();
        SetContentView(Build());
    }

    LinearLayout Build()
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.SetPadding(Dp(18), Dp(40), Dp(18), Dp(18));
        root.SetBackgroundDrawable(UI.Gradient(AppTheme.Background, Color.ParseColor("#12112a")));

        // top bar
        var top = UI.HBox();
        var back = UI.Text("‹ 返回", 16, AppTheme.AccentAlt);
        back.Clickable = true; back.Focusable = true;
        back.Click += (s, e) => Finish();
        var title = UI.Text("我 的 角 色", 24, AppTheme.TextPrimary, bold: true);
        title.LetterSpacing = 0.1f;
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        _currency = UI.Text(GameState.CurrencyLabel, 14, AppTheme.Gold);
        top.AddView(back); top.AddView(title); top.AddView(spacer); top.AddView(_currency);
        root.AddView(top);
        root.AddView(Spacer(14));

        var count = UI.Text($"已拥有  {GameState.OwnedCount}", 13, AppTheme.TextSecondary);
        root.AddView(count);
        root.AddView(Spacer(12));

        // grid inside a scroll
        var scroll = new ScrollView(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f) };
        _gridRoot = UI.VBox();
        RebuildGrid(_gridRoot);
        scroll.AddView(_gridRoot);
        root.AddView(scroll);

        return root;
    }

    void RebuildGrid(LinearLayout grid)
    {
        grid.RemoveAllViews();
        var owned = GameState.Owned();
        if (owned.Count == 0)
        {
            var empty = UI.Text("还没有角色 — 去抽卡吧 ✦", 15, AppTheme.TextMuted);
            empty.Gravity = GravityFlags.Center;
            empty.SetPadding(0, 80, 0, 0);
            grid.AddView(empty);
            return;
        }

        // 2 columns: each row holds two cards
        LinearLayout? row = null;
        for (int i = 0; i < owned.Count; i++)
        {
            if (i % 2 == 0)
            {
                row = UI.HBox();
                row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
                grid.AddView(row);
            }
            var ch = owned[i];
            row!.AddView(CharacterCard.ListCard(this, ch, () => OpenDetail(ch)));
        }
    }

    void OpenDetail(OwnedCharacterView ch)
    {
        var intent = new Intent(this, typeof(CharacterDetailActivity));
        intent.PutExtra("characterId", ch.Save.CharacterId);
        StartActivity(intent);
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }
}
