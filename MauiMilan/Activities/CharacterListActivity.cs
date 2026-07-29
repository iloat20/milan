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
            row!.AddView(CharacterCard(owned[i]));
        }
    }

    View CharacterCard(OwnedCharacterView ch)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var color = AppTheme.RarityColor(ch.Rarity);

        var card = UI.VBox();
        var lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        lp.SetMargins(Dp(5), Dp(5), Dp(5), Dp(5));
        card.LayoutParameters = lp;
        card.Background = UI.RoundRect(AppTheme.Surface, 16, 2, color);
        card.SetPadding(Dp(14), Dp(14), Dp(14), Dp(14));
        card.Focusable = true; card.Clickable = true;
        card.Click += (s, e) => OpenDetail(ch);

        // avatar row
        var avatarRow = UI.HBox();
        var initial = ch.Name.Length > 0 ? ch.Name.Trim()[0].ToString() : "?";
        var av = UI.Avatar(initial, ch.Rarity, 40);
        av.SetPadding(0, 0, Dp(10), 0);
        var nameCol = UI.VBox();
        var name = UI.Text(ch.Name, 16, AppTheme.TextPrimary, bold: true);
        name.SetMaxLines(1); name.Ellipsize = TextUtils.TruncateAt.End;
        var rarity = UI.Text(AppTheme.RarityName(ch.Rarity) + " · " + ch.World, 11, color);
        nameCol.AddView(name); nameCol.AddView(rarity);
        avatarRow.AddView(av);
        avatarRow.AddView(nameCol);

        // level + stars
        var lvl = UI.Text($"Lv.{ch.Save.Level}    {new string('★', ch.Save.Stars)}", 12, AppTheme.TextSecondary);
        lvl.SetPadding(0, Dp(8), 0, 0);

        card.AddView(avatarRow);
        card.AddView(lvl);
        return card;
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
