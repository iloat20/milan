using Milan.Maui.Services;

namespace Milan.Maui.Views;

public partial class CharacterListPage : ContentPage
{
    private readonly GameService _game;

    public CharacterListPage(GameService game)
    {
        InitializeComponent();
        _game = game;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        RefreshList();
    }

    private void RefreshList()
    {
        var items = new List<CharacterItem>();
        foreach (var ch in _game.SaveData.OwnedCharacters)
        {
            var data = _game.Data.GetCharacter(ch.CharacterId);
            items.Add(new CharacterItem
            {
                Id = ch.CharacterId,
                Name = data?.DisplayName ?? ch.CharacterId,
                Info = $"Lv.{ch.Level} · 阶段 {ch.Stage} · 天赋点 {ch.UnspentPoints}",
                RarityColor = RarityColor(data?.BaseRarity ?? 1)
            });
        }
        CharacterList.ItemsSource = items;
        CountLabel.Text = $"角色: {items.Count}";
    }

    private Color RarityColor(int rarity) => rarity switch
    {
        3 => Color.FromArgb("#e94560"),
        4 => Color.FromArgb("#9b59b6"),
        2 => Color.FromArgb("#00d2ff"),
        _ => Color.FromArgb("#7f8c8d")
    };

    private void OnTrainClicked(object s, EventArgs e)
    {
        if (s is Button btn && btn.CommandParameter is string id)
        {
            _game.AddExp(id, 100);
            RefreshList();
        }
    }

    private void OnBackClicked(object s, EventArgs e) => Navigation.PopAsync();

    private class CharacterItem
    {
        public string Id = "";
        public string Name = "";
        public string Info = "";
        public Color RarityColor = Colors.Gray;
    }
}
