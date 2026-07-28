using CommunityToolkit.Maui;
using Milan.Maui.Views;

namespace Milan.Maui;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .UseMauiCommunityToolkit()
            .ConfigureFonts(fonts =>
            {
                fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
            });

        // 注册全局游戏服务（单例）
        builder.Services.AddSingleton<Services.GameService>();
        builder.Services.AddSingleton<MainPage>();
        builder.Services.AddSingleton<GachaPage>();
        builder.Services.AddSingleton<CharacterListPage>();
        builder.Services.AddSingleton<BattlePage>();

        return builder.Build();
    }
}
