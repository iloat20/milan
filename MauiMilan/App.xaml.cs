using Microsoft.Maui;

namespace Milan.Maui;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
        MainPage = new AppShell();
    }
}
