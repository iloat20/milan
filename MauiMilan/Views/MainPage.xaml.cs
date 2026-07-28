using Milan.Maui.Services;

namespace Milan.Maui.Views;

public partial class MainPage : ContentPage
{
    private readonly GameService _game;

    public MainPage(GameService game)
    {
        InitializeComponent();
        _game = game;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        CurrencyLabel.Text = $"星尘: {_game.SaveData.SoftCurrency}";
        OwnedLabel.Text = $"角色: {_game.SaveData.OwnedCharacters.Count}";
    }

    private async void OnGachaClicked(object s, EventArgs e)
        => await Navigation.PushAsync(new GachaPage(_game));

    private async void OnCharactersClicked(object s, EventArgs e)
        => await Navigation.PushAsync(new CharacterListPage(_game));

    private async void OnBattleClicked(object s, EventArgs e)
        => await Navigation.PushAsync(new BattlePage(_game));
}
