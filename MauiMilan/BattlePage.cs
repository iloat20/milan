using Milan.Maui.Services;

namespace Milan.Maui;

public class BattlePage : ContentPage
{
    private readonly GameService _game;
    private Label _teamLabel;
    private Label _statsLabel;
    private Label _resultLabel;

    public BattlePage(GameService game)
    {
        _game = game;
        BackgroundColor = Color.FromArgb("#1a1a2e");

        _teamLabel = new Label { TextColor = Colors.White, FontSize = 16 };
        _statsLabel = new Label { TextColor = Colors.White, FontSize = 18, HorizontalOptions = LayoutOptions.Center };
        _resultLabel = new Label { Text = "点击开始战斗", TextColor = Color.FromArgb("#00d2ff"), FontSize = 22, HorizontalOptions = LayoutOptions.Center, FontAttributes = FontAttributes.Bold };

        var backBtn = new Button { Text = "返回", BackgroundColor = Color.FromArgb("#302b63"), TextColor = Colors.White };
        backBtn.Clicked += async (s, e) => await Navigation.PopAsync();

        var startBtn = new Button
        {
            Text = "开 始 战 斗",
            BackgroundColor = Color.FromArgb("#e94560"),
            TextColor = Colors.White,
            FontSize = 20,
            HeightRequest = 56,
            CornerRadius = 12
        };
        startBtn.Clicked += OnStart;

        var topBar = new HorizontalStackLayout
        {
            Padding = new Thickness(15), Spacing = 20,
            HorizontalOptions = LayoutOptions.Center,
            Children = { _teamLabel, backBtn }
        };

        var battleFrame = new Frame
        {
            BackgroundColor = Color.FromArgb("#2d1b69"),
            Padding = 20, CornerRadius = 15,
            Content = new VerticalStackLayout
            {
                Spacing = 12, VerticalOptions = LayoutOptions.Center,
                Children = { _statsLabel, _resultLabel }
            }
        };

        Content = new Grid
        {
            RowDefinitions = new RowDefinitionCollection { new(GridLength.Auto), new(GridLength.Star), new(GridLength.Auto) },
            Padding = new Thickness(20), RowSpacing = 20
        };
        Grid.SetRow(topBar, 0);
        Grid.SetRow(battleFrame, 1);
        Grid.SetRow(startBtn, 2);

        ((Grid)Content).Children.Add(topBar);
        ((Grid)Content).Children.Add(battleFrame);
        ((Grid)Content).Children.Add(startBtn);
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _teamLabel.Text = $"队伍: {_game.SaveData.OwnedCharacters.Count} 角色";
        _statsLabel.Text = "点击开始战斗";
    }

    private void OnStart(object s, EventArgs e)
    {
        var result = _game.RunStage("stage_1");
        _resultLabel.Text = result.Victory ? "胜 利! +100 星尘" : "失 败";
        _statsLabel.Text = $"总攻击: {result.TotalAtk}\n总生命: {result.TotalHp}";
        _teamLabel.Text = $"队伍: {result.OwnedCount} 角色";
    }
}
