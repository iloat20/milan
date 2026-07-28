using Milan.Maui.Services;

namespace Milan.Maui;

public class App : Application
{
    private readonly GameService _game;

    public App(GameService game)
    {
        _game = game;
        MainPage = new MainPage(game);
    }
}
