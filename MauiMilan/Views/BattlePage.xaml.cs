using Milan.Maui.Services;

namespace Milan.Maui.Views;

public partial class BattlePage : ContentPage
{
    private readonly GameService _game;

    public BattlePage(GameService game)
    {
        InitializeComponent();
        _game = game;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        TeamLabel.Text = $"队伍: {_game.SaveData.OwnedCharacters.Count} 角色";
        StatsLabel.Text = "点击开始战斗";
    }

    private void OnStartClicked(object s, EventArgs e)
    {
        var result = _game.RunStage("stage_1");
        ResultLabel.Text = result.Victory ? "胜 利! +100 星尘" : "失 败";
        StatsLabel.Text = $"总攻击: {result.TotalAtk}\n总生命: {result.TotalHp}";
        TeamLabel.Text = $"队伍: {result.OwnedCount} 角色";
    }

    private void OnBackClicked(object s, EventArgs e) => Navigation.PopAsync();
}
