using Android.App;
using Android.Content;
using Android.Graphics;
using Android.OS;
using Android.Text;
using Android.Util;
using Android.Views;
using Android.Widget;
using Milan.Maui.Services;
using Milan.Domain.Battle;

namespace Milan.Maui.Activities;

[Activity(Label = "图鉴")]
public class CollectionActivity : Activity
{
    LinearLayout _gridRoot = null!;

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
        root.SetPadding(Dp(16), Dp(40), Dp(16), Dp(16));
        root.SetBackgroundDrawable(UI.Gradient(AppTheme.Background, Color.ParseColor("#12112a")));

        // top bar
        var top = new LinearLayout(this) { Orientation = Orientation.Horizontal };
        
        var back = new TextView(this) { Text = "‹ 返回" };
        back.SetTextColor(AppTheme.AccentAlt);
        back.SetTextSize(ComplexUnitType.Sp, 16);
        back.Clickable = true; back.Focusable = true;
        back.Click += (_, _) => Finish();
        var title = new TextView(this) { Text = "角 色 图 鉴" };
        title.SetTextColor(AppTheme.TextPrimary);
        title.SetTextSize(ComplexUnitType.Sp, 24);
        title.SetTypeface(null, TypefaceStyle.Bold);
        title.LetterSpacing = 0.1f;
        var spacer = new View(this);
        spacer.LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f);
        top.AddView(back); top.AddView(title); top.AddView(spacer);
        root.AddView(top);
        root.AddView(Spacer(14));

        var hint = new TextView(this) { Text = "共 20 位角色  ·  点击立绘查看详情" };
        hint.SetTextColor(AppTheme.TextSecondary);
        hint.SetTextSize(ComplexUnitType.Sp, 12);
        root.AddView(hint);
        root.AddView(Spacer(10));

        // grid of portraits
        var scroll = new ScrollView(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f) };
        _gridRoot = new LinearLayout(this) { Orientation = Orientation.Vertical };
        BuildGrid(_gridRoot);
        scroll.AddView(_gridRoot);
        root.AddView(scroll);

        return root;
    }

    void BuildGrid(LinearLayout grid)
    {
        grid.RemoveAllViews();
        var chars = GameState.Service.Characters;
        int perRow = 3;
        LinearLayout? row = null;
        for (int i = 0; i < chars.Count; i++)
        {
            if (i % perRow == 0)
            {
                row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
                row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
                grid.AddView(row);
            }
            row!.AddView(PortraitCell(chars[i]));
        }
    }

    View PortraitCell(CharacterDataEntry ch)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var (from, to, glow, _) = ElementTheme.For(ch.Element);
        var rarityCol = ch.BaseRarity == 4 ? Color.ParseColor("#FF6B00")
            : ch.BaseRarity == 3 ? Color.ParseColor("#D070FF")
            : ch.BaseRarity == 2 ? Color.ParseColor("#3AA0FF") : Color.ParseColor("#9E9E9E");

        var cell = new LinearLayout(this) { Orientation = Orientation.Vertical };
        var lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        lp.SetMargins(Dp(5), Dp(5), Dp(5), Dp(5));
        cell.LayoutParameters = lp;
        
        cell.SetPadding(Dp(6), Dp(6), Dp(6), Dp(6));
        cell.Background = UI.RoundRect(AppTheme.Surface, 14, 1, rarityCol);
        cell.Focusable = true; cell.Clickable = true;
        cell.Click += (_, _) => OpenDetail(ch);

        // Portrait
        var portrait = new FullBodyCharacter(this, ch);
        var portraitLp = new LinearLayout.LayoutParams(Dp(100), Dp(140));
        portrait.LayoutParameters = portraitLp;
        cell.AddView(portrait);

        // Name
        var name = new TextView(this) { Text = ch.DisplayName };
        name.SetTextColor(AppTheme.TextPrimary);
        name.SetTextSize(ComplexUnitType.Sp, 11);
        name.SetTypeface(null, TypefaceStyle.Bold);
        name.Gravity = GravityFlags.CenterHorizontal;
        name.SetMaxLines(1); name.Ellipsize = TextUtils.TruncateAt.End;
        name.SetPadding(0, Dp(4), 0, 0);
        cell.AddView(name);

        // Rarity stars
        var stars = new TextView(this) { Text = new string('★', ch.BaseRarity) };
        stars.SetTextColor(rarityCol);
        stars.SetTextSize(ComplexUnitType.Sp, 10);
        stars.Gravity = GravityFlags.CenterHorizontal;
        cell.AddView(stars);

        return cell;
    }

    void OpenDetail(CharacterDataEntry ch)
    {
        var intent = new Intent(this, typeof(CharacterDetailActivity));
        intent.PutExtra("characterId", ch.CharacterId);
        StartActivity(intent);
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }
}
