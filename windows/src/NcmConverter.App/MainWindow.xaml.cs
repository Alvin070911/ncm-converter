using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using NcmConverter.App.Services;
using NcmConverter.App.Views;
using Windows.Graphics;

namespace NcmConverter.App;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        Title = "NCM 转 FLAC";

        try
        {
            AppWindow.Resize(new SizeInt32(1180, 780));
        }
        catch
        {
            // 部分环境不支持调整窗口尺寸
        }

        ThemeService.Apply(AppServices.Settings.Current.Theme, RootGrid);

        if (Nav.MenuItems.Count > 0)
        {
            Nav.SelectedItem = Nav.MenuItems[0];
        }

        ContentFrame.Navigate(typeof(ConvertPage));
    }

    public FrameworkElement ThemeRoot => RootGrid;

    public void ApplyTheme(string theme)
    {
        ThemeService.Apply(theme, RootGrid);
    }

    private void Nav_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.SelectedItem is not NavigationViewItem item)
        {
            return;
        }

        switch (item.Tag)
        {
            case "history":
                ContentFrame.Navigate(typeof(HistoryPage));
                break;
            case "settings":
                ContentFrame.Navigate(typeof(SettingsPage));
                break;
            case "about":
                ContentFrame.Navigate(typeof(AboutPage));
                break;
            default:
                ContentFrame.Navigate(typeof(ConvertPage));
                break;
        }
    }
}
