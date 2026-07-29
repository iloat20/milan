using Android.App;
using Android.Content;
using Android.Graphics;
using Android.OS;
using Android.Text;
using Android.Views;
using Android.Widget;
using Milan.Maui;

namespace Milan.Maui.Activities;

[Activity(Label = "图鉴")]
public class CollectionActivity : Activity
{
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
        root.SetPadding(Dp(20), Dp(40), Dp(20), Dp(24));
        root.SetBackgroundDrawable(UI.Gradient(AppTheme.Background, Color.ParseColor("#12112a")));

        var top = UI.HBox();
        var back = UI.Text("‹ 返回", 16, AppTheme.AccentAlt);
        back.Clickable = true; back.Focusable = true;
        back.Click += (s, e) => Finish();
        var title = UI.Text("图 鉴", 24, AppTheme.TextPrimary, bold: true);
        title.LetterSpacing = 0.1f;
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        top.AddView(back); top.AddView(title); top.AddView(spacer);
        root.AddView(top);
        root.AddView(Spacer(16));

        var all = GameState.Service.Characters;
        var owned = GameState.Owned();
        var ownedIds = new System.Collections.Generic.HashSet<string>(owned.Select(c => c.Save.CharacterId));

        // progress card
        var progress = UI.VBox();
        progress.Background = UI.RoundRect(AppTheme.Surface, 16);
        progress.SetPadding(Dp(18), Dp(16), Dp(18), Dp(16));
        int have = ownedIds.Count, total = all.Count;
        int pct = total > 0 ? (have * 100 / total) : 0;
        var pTitle = UI.Text("收 集 度", 15, AppTheme.TextSecondary, bold: true);
        var pVal = UI.Text($"{have} / {total}   ({pct}%)", 26, AppTheme.Gold, bold: true);
        pVal.SetPadding(0, Dp(4), 0, Dp(10));
        // progress bar (fill over a track, inside a frame)
        var barFrame = new FrameLayout(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(12)) };
        barFrame.Background = UI.RoundRect(AppTheme.SurfaceRaised, 6);
        var fill = new View(this) { LayoutParameters = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(12)) };
        var fillGd = new Android.Graphics.Drawables.GradientDrawable();
        fillGd.SetCornerRadius(Dp(6));
        fillGd.SetColors(new[] { AppTheme.Accent.ToArgb(), Color.ParseColor("#7b2ff7").ToArgb() });
        fill.Background = fillGd;
        barFrame.AddView(fill);
        progress.AddView(pTitle); progress.AddView(pVal); progress.AddView(barFrame);
        root.AddView(progress);
        root.AddView(Spacer(16));

        // full roster (owned shown bright, missing as silhouettes)
        var scroll = new ScrollView(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f) };
        var grid = UI.VBox();
        LinearLayout? row = null;
        for (int i = 0; i < all.Count; i++)
        {
            if (i % 2 == 0) { row = UI.HBox(); row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent); grid.AddView(row); }
            bool isOwned = ownedIds.Contains(all[i].CharacterId);
            row!.AddView(RosterCell(all[i].DisplayName, all[i].BaseRarity, isOwned));
        }
        scroll.AddView(grid);
        root.AddView(scroll);

        return root;
    }

    View RosterCell(string name, int rarity, bool owned)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var color = AppTheme.RarityColor(rarity);

        var cell = UI.VBox();
        var lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f);
        lp.SetMargins(Dp(5), Dp(5), Dp(5), Dp(5));
        cell.LayoutParameters = lp;
        cell.Background = UI.RoundRect(owned ? AppTheme.Surface : AppTheme.SurfaceRaised, 14, 1, owned ? color : AppTheme.Stroke);
        cell.SetPadding(Dp(10), Dp(12), Dp(10), Dp(12));

        var initial = name.Length > 0 ? name.Trim()[0].ToString() : "?";
        var av = UI.Avatar(initial, rarity, 36);
        av.SetPadding(0, 0, 0, Dp(6));
        var alpha = owned ? 1f : 0.35f;
        av.Alpha = alpha;

        var n = UI.Text(owned ? name : "???", owned ? 13 : 12, owned ? AppTheme.TextPrimary : AppTheme.TextMuted);
        n.Gravity = GravityFlags.CenterHorizontal;
        n.Alpha = alpha;
        n.SetMaxLines(1); n.Ellipsize = TextUtils.TruncateAt.End;

        cell.AddView(av);
        cell.AddView(n);
        return cell;
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }
}
