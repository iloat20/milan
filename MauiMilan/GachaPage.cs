using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// 抽卡页面：使用 MAUI 内置控件 + 动画（无需 SkiaSharp）。
/// </summary>
public class GachaPage : ContentPage
{
    private readonly GameService _game;
    private Label _resultLabel;
    private Label _currencyLabel;
    private Button _singleBtn;
    private Button _tenBtn;
    private Frame _cardFrame;
    private Label _cardNameLabel;
    private Label _cardRarityLabel;
    private AbsoluteLayout _animLayer;

    private bool _isAnimating;
    private double _animProgress;
    private IDispatcherTimer? _animTimer;
    private string _cardName = "";
    private int _rarity;
    private bool _showCard;

    public GachaPage(GameService game)
    {
        _game = game;
        BackgroundColor = Color.FromArgb("#1a1a2e");

        _currencyLabel = new Label { TextColor = Colors.White, FontSize = 16 };
        _resultLabel = new Label
        {
            Text = "", FontSize = 20,
            TextColor = Color.FromArgb("#00d2ff"),
            HorizontalOptions = LayoutOptions.Center
        };

        _singleBtn = new Button
        {
            Text = "单 抽",
            BackgroundColor = Color.FromArgb("#e94560"),
            TextColor = Colors.White,
            FontSize = 18, WidthRequest = 140, HeightRequest = 50, CornerRadius = 10
        };
        _singleBtn.Clicked += OnSinglePull;

        _tenBtn = new Button
        {
            Text = "十 连",
            BackgroundColor = Color.FromArgb("#00d2ff"),
            TextColor = Colors.Black,
            FontSize = 18, WidthRequest = 140, HeightRequest = 50, CornerRadius = 10
        };
        _tenBtn.Clicked += OnTenPull;

        var backBtn = new Button { Text = "返回", BackgroundColor = Color.FromArgb("#302b63"), TextColor = Colors.White };
        backBtn.Clicked += async (s, e) => await Navigation.PopAsync();

        // 卡牌展示
        _cardNameLabel = new Label { FontSize = 24, FontAttributes = FontAttributes.Bold, TextColor = Colors.White, HorizontalOptions = LayoutOptions.Center };
        _cardRarityLabel = new Label { FontSize = 18, HorizontalOptions = LayoutOptions.Center };
        _cardFrame = new Frame
        {
            IsVisible = false,
            WidthRequest = 200, HeightRequest = 280,
            CornerRadius = 16, Padding = 20,
            BackgroundColor = Color.FromArgb("#302b63"),
            Content = new VerticalStackLayout
            {
                VerticalOptions = LayoutOptions.Center, HorizontalOptions = LayoutOptions.Center,
                Children = { _cardNameLabel, _cardRarityLabel }
            }
        };

        // 动画层（漩涡效果用旋转的 Frame 模拟）
        _animLayer = new AbsoluteLayout { IsVisible = false };
        for (int i = 0; i < 5; i++)
        {
            var ring = new Frame
            {
                IsVisible = false,
                BackgroundColor = Colors.Transparent,
                BorderColor = Color.FromArgb("#00d2ff"),
                CornerRadius = 100,
                Opacity = 0.6
            };
            _animLayer.Children.Add(ring);
        }

        var topBar = new HorizontalStackLayout
        {
            Padding = new Thickness(15), Spacing = 20,
            HorizontalOptions = LayoutOptions.Center,
            Children = { _currencyLabel, backBtn }
        };

        var buttons = new HorizontalStackLayout
        {
            Padding = new Thickness(20), Spacing = 15,
            HorizontalOptions = LayoutOptions.Center,
            Children = { _singleBtn, _tenBtn }
        };

        Content = new Grid
        {
            RowDefinitions = new RowDefinitionCollection { new(GridLength.Auto), new(GridLength.Star), new(GridLength.Auto) }
        };
        Grid.SetRow(topBar, 0);
        Grid.SetRow(buttons, 2);

        ((Grid)Content).Children.Add(topBar);
        ((Grid)Content).Children.Add(_cardFrame);
        ((Grid)Content).Children.Add(_animLayer);
        ((Grid)Content).Children.Add(buttons);
        _resultLabel.VerticalOptions = LayoutOptions.End;
        _resultLabel.Margin = new Thickness(0, 0, 0, 80);
        ((Grid)Content).Children.Add(_resultLabel);
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _currencyLabel.Text = $"星尘: {_game.SaveData.SoftCurrency}";
    }

    private void OnSinglePull(object? s, EventArgs e)
    {
        if (_isAnimating) return;
        var results = _game.Pull("pool_main", false);
        if (results.Count == 0) { _resultLabel.Text = "货币不足"; return; }
        StartReveal(results[0]);
    }

    private void OnTenPull(object? s, EventArgs e)
    {
        if (_isAnimating) return;
        var results = _game.Pull("pool_main", true);
        if (results.Count == 0) { _resultLabel.Text = "货币不足"; return; }
        StartReveal(results.Last());
    }

    private void StartReveal(GameService.PullResult r)
    {
        _isAnimating = true;
        _singleBtn.IsEnabled = false;
        _tenBtn.IsEnabled = false;
        _cardName = r.CharacterName;
        _rarity = r.Rarity;
        _showCard = false;
        _animProgress = 0;
        _cardFrame.IsVisible = false;
        _animLayer.IsVisible = true;

        // 初始化漩涡环
        for (int i = 0; i < _animLayer.Children.Count; i++)
        {
            if (_animLayer.Children[i] is View view)
            {
                view.IsVisible = true;
                view.Opacity = 0.6;
                AbsoluteLayout.SetLayoutBounds(view, new Rect(0.5, 0.5, 60, 60));
                AbsoluteLayout.SetLayoutFlags(view, Microsoft.Maui.Layouts.AbsoluteLayoutFlags.PositionProportional);
            }
        }

        _animTimer = Dispatcher.CreateTimer();
        _animTimer.Interval = TimeSpan.FromMilliseconds(16);
        _animTimer.Tick += (s, e) =>
        {
            _animProgress += 0.02f;

            // 漩涡扩散动画
            for (int i = 0; i < _animLayer.Children.Count; i++)
            {
                if (!(_animLayer.Children[i] is View ring)) continue;
                float delay = i * 0.1f;
                float t = Math.Max(0, (float)_animProgress - delay);
                float size = 60 + t * 300;
                ring.WidthRequest = size;
                ring.HeightRequest = size;
                ring.Opacity = Math.Max(0.0, 0.6 - t * 0.6);
                ring.Rotation = (double)(t * 360);
            }

            if (_animProgress >= 0.7 && !_showCard)
            {
                _showCard = true;
                _animLayer.IsVisible = false;
                ShowCard();
            }
            if (_animProgress >= 1.0f)
            {
                _animTimer?.Stop();
                _isAnimating = false;
                _singleBtn.IsEnabled = true;
                _tenBtn.IsEnabled = true;
                _resultLabel.Text = $"获得: {_cardName} ({RarityName(_rarity)})";
                _currencyLabel.Text = $"星尘: {_game.SaveData.SoftCurrency}";
            }
        };
        _animTimer.Start();
    }

    private void ShowCard()
    {
        _cardNameLabel.Text = _cardName;
        _cardRarityLabel.Text = RarityName(_rarity);
        _cardRarityLabel.TextColor = _rarity switch
        {
            3 => Color.FromArgb("#ffd700"),
            4 => Color.FromArgb("#ff00ff"),
            2 => Color.FromArgb("#00ffff"),
            _ => Colors.LightGray
        };
        _cardFrame.BackgroundColor = _rarity switch
        {
            3 => Color.FromArgb("#e94560"),
            4 => Color.FromArgb("#9b59b6"),
            2 => Color.FromArgb("#00d2ff"),
            _ => Color.FromArgb("#7f8c8d")
        };
        _cardFrame.IsVisible = true;
        _cardFrame.Scale = 0.5;
        _cardFrame.FadeTo(1, 200);
        _cardFrame.ScaleTo(1, 300, Easing.SpringOut);
    }

    private string RarityName(int r) => r switch { 3 => "SSR", 2 => "SR", 4 => "UR", _ => "R" };
}
