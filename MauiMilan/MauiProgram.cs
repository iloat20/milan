using Milan.Maui.Services;

namespace Milan.Maui;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .ConfigureFonts(fonts =>
            {
                fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
            });

        // 注册全局游戏服务（单例）
        builder.Services.AddSingleton<GameService>();

        return builder.Build();
    }
}
