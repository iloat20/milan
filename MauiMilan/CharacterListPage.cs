using Milan.Maui.Services;

namespace Milan.Maui;

public class CharacterListPage : ContentPage
{
    private readonly GameService _game;
    private Label _countLabel;
    private VerticalStackLayout _listLayout;

    public CharacterListPage(GameService game)
    {
        _game = game;
        BackgroundColor = Color.FromArgb("#1a1a2e");

        _countLabel = new Label { TextColor = Colors.White, FontSize = 16 };
        _listLayout = new VerticalStackLayout { Spacing = 8 };

        var backBtn = new Button { Text = "返回", BackgroundColor = Color.FromArgb("#302b63"), TextColor = Colors.White };
        backBtn.Clicked += async (s, e) => await Navigation.PopAsync();

        var topBar = new HorizontalStackLayout
        {
            Padding = new Thickness(15), Spacing = 20,
            HorizontalOptions = LayoutOptions.Center,
            Children = { _countLabel, backBtn }
        };

        Content = new Grid
        {
            RowDefinitions = new RowDefinitionCollection { new(GridLength.Auto), new(GridLength.Star) }
        };
        Grid.SetRow(topBar, 0);
        Grid.SetRow(_listLayout, 1);
        _listLayout.Margin = new Thickness(15);

        ((Grid)Content).Children.Add(topBar);
        ((Grid)Content).Children.Add(new ScrollView { Content = _listLayout });
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        Refresh();
    }

    private void Refresh()
    {
        _listLayout.Children.Clear();
        foreach (var ch in _game.SaveData.OwnedCharacters)
        {
            var data = _game.Data.GetCharacter(ch.CharacterId);
            var name = data?.DisplayName ?? ch.CharacterId;
            var rarity = data?.BaseRarity ?? 1;

            var trainBtn = new Button
            {
                Text = "+",
                BackgroundColor = Color.FromArgb("#e94560"),
                TextColor = Colors.White,
                WidthRequest = 44, HeightRequest = 44, CornerRadius = 22
            };
            var id = ch.CharacterId;
            trainBtn.Clicked += (s, e) => { _game.AddExp(id, 100); Refresh(); };

            var frame = new Frame
            {
                BackgroundColor = Color.FromArgb("#2d1b69"),
                Padding = 12, CornerRadius = 12
            };

            // 用水平布局：头像框 + 信息 + 培养按钮
            var info = new VerticalStackLayout { Padding = new Thickness(10, 0), VerticalOptions = LayoutOptions.Center };
            info.Children.Add(new Label { Text = name, TextColor = Colors.White, FontSize = 16, FontAttributes = FontAttributes.Bold });
            info.Children.Add(new Label { Text = $"Lv.{ch.Level}  阶段 {ch.Stage}  天赋 {ch.UnspentPoints}", TextColor = Color.FromArgb("#aaaaaa"), FontSize = 12 });

            var avatar = new Frame
            {
                WidthRequest = 50, HeightRequest = 50,
                BackgroundColor = RarityColor(rarity),
                CornerRadius = 10, Padding = 0
            };

            var row = new HorizontalStackLayout { Spacing = 10, VerticalOptions = LayoutOptions.Center };
            row.Children.Add(avatar);
            row.Children.Add(info);
            row.Children.Add(trainBtn);

            frame.Content = row;
            _listLayout.Children.Add(frame);
        }
        _countLabel.Text = $"角色: {_game.SaveData.OwnedCharacters.Count}";
    }

    private Color RarityColor(int r) => r switch
    {
        3 => Color.FromArgb("#e94560"),
        4 => Color.FromArgb("#9b59b6"),
        2 => Color.FromArgb("#00d2ff"),
        _ => Color.FromArgb("#7f8c8d")
    };
}
