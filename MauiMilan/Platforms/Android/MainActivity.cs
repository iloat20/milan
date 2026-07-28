using Android.App;
using Android.Content;
using Android.OS;
using Android.Views;
using Android.Widget;
using Milan.Maui.Services;

namespace Milan.Maui;

[Activity(Label = "Milan", MainLauncher = true, Theme = "@android:style/Theme.Material.NoActionBar")]
public class MainActivity : Activity
{
    private GameService _game = null!;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);

        try
        {
            _game = new GameService();
            _game.InitializeAsync(this).GetAwaiter().GetResult();

            var layout = new LinearLayout(this)
            {
                Orientation = Orientation.Vertical,
                LayoutParameters = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MatchParent,
                    ViewGroup.LayoutParams.MatchParent)
            };
            layout.SetBackgroundColor(Android.Graphics.Color.ParseColor("#1a1a2e"));
            layout.SetPadding(50, 150, 50, 50);

            var title = new TextView(this)
            {
                Text = "MILAN",
                Gravity = GravityFlags.Center
            };
            title.SetTextSize(Android.Util.ComplexUnitType.Sp, 40);
            title.SetTextColor(Android.Graphics.Color.ParseColor("#e94560"));
            layout.AddView(title);

            var subtitle = new TextView(this)
            {
                Text = "次元裂缝 · 抽卡",
                Gravity = GravityFlags.Center
            };
            subtitle.SetTextSize(Android.Util.ComplexUnitType.Sp, 16);
            subtitle.SetTextColor(Android.Graphics.Color.ParseColor("#00d2ff"));
            layout.AddView(subtitle);

            var currency = new TextView(this)
            {
                Text = $"星尘: {_game.SaveData.SoftCurrency}",
                Gravity = GravityFlags.Center
            };
            currency.SetTextSize(Android.Util.ComplexUnitType.Sp, 18);
            currency.SetTextColor(Android.Graphics.Color.White);
            layout.AddView(currency);

            var pullBtn = new Android.Widget.Button(this)
            {
                Text = "单 抽"
            };
            pullBtn.SetBackgroundColor(Android.Graphics.Color.ParseColor("#e94560"));
            pullBtn.SetTextColor(Android.Graphics.Color.White);
            pullBtn.Click += (s, e) =>
            {
                var results = _game.Pull("pool_main", false);
                if (results.Count > 0)
                {
                    var r = results[0];
                    var dialog = new AlertDialog.Builder(this)
                        .SetTitle("获得!")
                        .SetMessage($"{r.CharacterName} ({RarityName(r.Rarity)})")
                        .SetPositiveButton("确定", (sender, args) => { })
                        .Create();
                    dialog.Show();
                    currency.Text = $"星尘: {_game.SaveData.SoftCurrency}";
                }
                else
                {
                    Toast.MakeText(this, "货币不足", ToastLength.Short)?.Show();
                }
            };
            layout.AddView(pullBtn);

            var tenBtn = new Android.Widget.Button(this)
            {
                Text = "十 连"
            };
            tenBtn.SetBackgroundColor(Android.Graphics.Color.ParseColor("#00d2ff"));
            tenBtn.SetTextColor(Android.Graphics.Color.Black);
            tenBtn.Click += (s, e) =>
            {
                var results = _game.Pull("pool_main", true);
                if (results.Count > 0)
                {
                    var last = results.Last();
                    var dialog = new AlertDialog.Builder(this)
                        .SetTitle("十连结果")
                        .SetMessage($"最后获得: {last.CharacterName} ({RarityName(last.Rarity)})\n共 {results.Count} 个角色")
                        .SetPositiveButton("确定", (sender, args) => { })
                        .Create();
                    dialog.Show();
                    currency.Text = $"星尘: {_game.SaveData.SoftCurrency}";
                }
                else
                {
                    Toast.MakeText(this, "货币不足", ToastLength.Short)?.Show();
                }
            };
            layout.AddView(tenBtn);

            SetContentView(layout);
        }
        catch (Exception ex)
        {
            var errorLayout = new LinearLayout(this)
            {
                Orientation = Orientation.Vertical,
                LayoutParameters = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MatchParent,
                    ViewGroup.LayoutParams.MatchParent)
            };
            errorLayout.SetBackgroundColor(Android.Graphics.Color.Black);
            var errorText = new TextView(this)
            {
                Text = $"错误:\n{ex.Message}\n\n{ex.StackTrace}"
            };
            errorText.SetTextColor(Android.Graphics.Color.Red);
            errorLayout.AddView(errorText);
            SetContentView(errorLayout);
        }
    }

    private static string RarityName(int r) => r switch { 3 => "SSR", 2 => "SR", 4 => "UR", _ => "R" };
}
