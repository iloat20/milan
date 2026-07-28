using Milan.Maui.Services;

namespace Milan.Maui;

public class App : Application
{
    private readonly GameService _game;

    public App(GameService game)
    {
        _game = game;
        try
        {
            MainPage = new NavigationPage(new MainPage(game));
        }
        catch (Exception ex)
        {
            MainPage = new ContentPage
            {
                Content = new Label { Text = $"启动错误: {ex.Message}", TextColor = Colors.Red }
            };
        }
    }

    protected override async void OnStart()
    {
        base.OnStart();
        try
        {
            await _game.InitializeAsync();
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[Milan] Init error: {ex.Message}");
        }
    }
}
