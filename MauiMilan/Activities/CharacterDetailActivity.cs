using Android.App;
using Android.Content;
using Android.Graphics;
using Android.OS;
using Android.Views;
using Android.Widget;
using Milan.Domain.Battle;
using Milan.Maui;

namespace Milan.Maui.Activities;

[Activity(Label = "角色详情")]
public class CharacterDetailActivity : Activity
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        GameState.EnsureInitialized(this);

        var id = Intent.GetStringExtra("characterId");
        var ch = GameState.Owned().FirstOrDefault(c => c.Save.CharacterId == id);
        if (ch == null) { Finish(); return; }

        SetContentView(Build(ch));
    }

    LinearLayout Build(OwnedCharacterView ch)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var color = AppTheme.RarityColor(ch.Rarity);
        var stats = GameState.ComputeStats(ch);

        var root = new LinearLayout(this) { Orientation = Orientation.Vertical };
        root.LayoutParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent);
        root.SetPadding(Dp(20), Dp(40), Dp(20), Dp(24));
        root.SetBackgroundDrawable(UI.Gradient(AppTheme.Background, Color.ParseColor("#1a1330")));

        // top bar
        var top = UI.HBox();
        var back = UI.Text("‹ 返回", 16, AppTheme.AccentAlt);
        back.Clickable = true; back.Focusable = true;
        back.Click += (s, e) => Finish();
        var head = UI.Text("角 色 详 情", 22, AppTheme.TextPrimary, bold: true);
        head.LetterSpacing = 0.1f;
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        top.AddView(back); top.AddView(head); top.AddView(spacer);
        root.AddView(top);
        root.AddView(Spacer(16));

        // hero banner: big avatar + name + rarity
        var banner = UI.HBox();
        banner.Background = UI.RoundRect(AppTheme.Surface, 18, 2, color);
        banner.SetPadding(Dp(18), Dp(18), Dp(18), Dp(18));

        var av = CharacterCard.DetailPortrait(this, ch, 80);
        av.SetPadding(0, 0, Dp(16), 0);

        var info = UI.VBox();
        var name = UI.Text(ch.Name, 22, AppTheme.TextPrimary, bold: true);
        var title = UI.Text(ch.Title, 13, color);
        title.SetPadding(0, Dp(2), 0, 0);
        var rarity = UI.Text($"{AppTheme.RarityName(ch.Rarity)}  ·  {ch.World}  ·  {ch.Element}", 12, AppTheme.TextSecondary, bold: true);
        rarity.SetPadding(0, Dp(4), 0, 0);
        var level = UI.Text($"Lv.{ch.Save.Level}   {new string('★', ch.Save.Stars)}   天赋点 {ch.Save.UnspentPoints}", 13, AppTheme.TextSecondary);
        level.SetPadding(0, Dp(6), 0, 0);
        info.AddView(name); info.AddView(title); info.AddView(rarity); info.AddView(level);

        banner.AddView(av);
        banner.AddView(info);
        root.AddView(banner);
        root.AddView(Spacer(12));

        // Lore
        root.AddView(SectionTitle("背 景 故 事"));
        var loreBox = UI.VBox();
        loreBox.Background = UI.RoundRect(AppTheme.Surface, 14);
        loreBox.SetPadding(Dp(16), Dp(14), Dp(16), Dp(14));
        var lore = UI.Text(ch.Lore, 14, AppTheme.TextSecondary);
        lore.SetLineSpacing(Dp(4), 1f);
        loreBox.AddView(lore);
        root.AddView(loreBox);
        root.AddView(Spacer(16));

        // stats
        root.AddView(SectionTitle("属 性"));
        var statsBox = UI.VBox();
        statsBox.Background = UI.RoundRect(AppTheme.Surface, 14);
        statsBox.SetPadding(Dp(16), Dp(14), Dp(16), Dp(14));
        statsBox.AddView(StatRow("ATK", stats.Atk, AppTheme.Accent));
        statsBox.AddView(StatRow("DEF", stats.Def, AppTheme.AccentAlt));
        statsBox.AddView(StatRow("HP", stats.Hp, Color.ParseColor("#7ee787")));
        statsBox.AddView(StatRow("SPD", stats.Spd, AppTheme.Gold));
        root.AddView(statsBox);
        root.AddView(Spacer(16));

        // talents
        root.AddView(SectionTitle("天 赋"));
        root.AddView(TalentPreview(this, ch));
        root.AddView(Spacer(16));

        // inspect button
        var inspect = UI.Button("360° 检 视", Color.ParseColor("#7b2ff7"), Color.White, 14);
        inspect.Click += (s, e) => Toast.MakeText(this, "3D 检视（占位）", ToastLength.Short)?.Show();
        root.AddView(inspect);

        return root;
    }

    View TalentPreview(Activity activity, OwnedCharacterView ch)
    {
        var density = activity.Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var tree = ch.Talent;
        var box = UI.VBox();
        box.Background = UI.RoundRect(AppTheme.Surface, 14);
        box.SetPadding(Dp(16), Dp(14), Dp(16), Dp(14));
        if (tree == null) { box.AddView(UI.Text("暂无天赋", 14, AppTheme.TextMuted)); return box; }

        foreach (var branch in tree.BranchIds)
        {
            var nodes = tree.Nodes.Where(n => n.BranchId == branch).ToList();
            if (nodes.Count == 0) continue;
            var branchName = branch switch { "branch_power" => "强攻", "branch_defense" => "防御", "branch_utility" => "通用", _ => branch };
            var bh = UI.Text("■ " + branchName, 13, AppTheme.Gold, bold: true);
            bh.SetPadding(0, Dp(6), 0, Dp(2));
            box.AddView(bh);
            foreach (var n in nodes)
            {
                var allocated = ch.Save.TalentPoints.Contains(n.NodeId);
                var nc = allocated ? AppTheme.TextPrimary : AppTheme.TextMuted;
                var nt = UI.Text($"{(allocated ? "●" : "○")} {n.DisplayName}  —  {n.Description}", 12, nc);
                nt.SetPadding(Dp(8), Dp(2), 0, Dp(2));
                box.AddView(nt);
            }
        }
        return box;
    }

    TextView SectionTitle(string s)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);
        var t = UI.Text(s, 15, AppTheme.TextSecondary, bold: true);
        t.LetterSpacing = 0.15f;
        t.SetPadding(0, 0, 0, Dp(8));
        return t;
    }

    View StatRow(string label, int value, Color color)
    {
        var density = Resources.DisplayMetrics.Density;
        int Dp(int v) => (int)(v * density);

        var row = UI.HBox();
        row.LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent);
        row.SetPadding(0, Dp(5), 0, Dp(5));

        var dot = UI.Text("●", 12, color);
        dot.SetPadding(0, 0, Dp(8), 0);
        var name = UI.Text(label, 15, AppTheme.TextPrimary);
        name.SetPadding(0, 0, Dp(10), 0);
        var spacer = new View(this) { LayoutParameters = new LinearLayout.LayoutParams(0, 0, 1f) };
        var val = UI.Text(value.ToString("N0"), 16, color, bold: true);

        row.AddView(dot); row.AddView(name); row.AddView(spacer); row.AddView(val);
        return row;
    }

    View Spacer(int h)
    {
        var density = Resources.DisplayMetrics.Density;
        return new View(this) { LayoutParameters = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, (int)(h * density)) };
    }
}
