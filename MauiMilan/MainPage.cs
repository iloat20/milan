using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// 主页面：纯 C# 构建（无需 XAML）。
/// </summary>
public class MainPage : ContentPage
{
    private readonly GameService _game;
    private Label _currencyLabel;
    private Label _ownedLabel;

    public MainPage(GameService game)
    {
        _game = game;
        BackgroundColor = Color.FromArgb("#1a1a2e");

        _currencyLabel = new Label { TextColor = Colors.White, FontSize = 18 };
        _ownedLabel = new Label { TextColor = Color.FromArgb("#00d2ff"), FontSize = 18 };

        var gachaBtn = new Button
        {
            Text = "抽 卡",
            BackgroundColor = Color.FromArgb("#e94560"),
            TextColor = Colors.White,
            FontSize = 20,
            HeightRequest = 56,
            CornerRadius = 12
        };
        gachaBtn.Clicked += async (s, e) =>
        {
            var page = new GachaPage(_game);
            await Navigation.PushAsync(page);
        };

        var charBtn = new Button
        {
            Text = "角 色",
            BackgroundColor = Color.FromArgb("#302b63"),
            TextColor = Colors.White,
            FontSize = 18,
            HeightRequest = 48,
            CornerRadius = 10
        };
        charBtn.Clicked += async (s, e) =>
        {
            var page = new CharacterListPage(_game);
            await Navigation.PushAsync(page);
        };

        var battleBtn = new Button
        {
            Text = "战 斗",
            BackgroundColor = Color.FromArgb("#302b63"),
            TextColor = Colors.White,
            FontSize = 18,
            HeightRequest = 48,
            CornerRadius = 10
        };
        battleBtn.Clicked += async (s, e) =>
        {
            var page = new BattlePage(_game);
            await Navigation.PushAsync(page);
        };

        Content = new Grid
        {
            RowDefinitions = new RowDefinitionCollection
            {
                new(GridLength.Star),
                new(GridLength.Auto),
                new(GridLength.Auto),
                new(GridLength.Star)
            },
            Padding = new Thickness(20),
            RowSpacing = 15
        };

        var title = new Label
        {
            Text = "MILAN",
            FontSize = 48,
            TextColor = Color.FromArgb("#e94560"),
            HorizontalOptions = LayoutOptions.Center,
            FontAttributes = FontAttributes.Bold
        };
        Grid.SetRow(title, 0);

        var subtitle = new Label
        {
            Text = "次 元 裂 缝 · 抽 卡",
            FontSize = 16,
            TextColor = Color.FromArgb("#00d2ff"),
            HorizontalOptions = LayoutOptions.Center
        };
        Grid.SetRow(subtitle, 0);

        var currencyFrame = new Frame
        {
            BackgroundColor = Color.FromArgb("#2d1b69"),
            Padding = 15,
            CornerRadius = 10,
            Content = new HorizontalStackLayout
            {
                Spacing = 20,
                HorizontalOptions = LayoutOptions.Center,
                Children = { _currencyLabel, _ownedLabel }
            }
        };
        Grid.SetRow(currencyFrame, 1);

        var buttons = new VerticalStackLayout { Spacing = 12 };
        buttons.Children.Add(gachaBtn);
        buttons.Children.Add(charBtn);
        buttons.Children.Add(battleBtn);
        Grid.SetRow(buttons, 2);

        ((Grid)Content).Children.Add(title);
        ((Grid)Content).Children.Add(subtitle);
        ((Grid)Content).Children.Add(currencyFrame);
        ((Grid)Content).Children.Add(buttons);
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _currencyLabel.Text = $"星尘: {_game.SaveData.SoftCurrency}";
        _ownedLabel.Text = $"角色: {_game.SaveData.OwnedCharacters.Count}";
    }
}
