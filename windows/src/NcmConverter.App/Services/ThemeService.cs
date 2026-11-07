using Microsoft.UI.Xaml;

namespace NcmConverter.App.Services;

public static class ThemeService
{
    public static void Apply(string theme, FrameworkElement root)
    {
        root.RequestedTheme = theme switch
        {
            "Light" => ElementTheme.Light,
            "Dark" => ElementTheme.Dark,
            _ => ElementTheme.Default
        };
    }
}
