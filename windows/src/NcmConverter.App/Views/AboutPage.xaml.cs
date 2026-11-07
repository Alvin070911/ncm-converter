using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace NcmConverter.App.Views;

public sealed partial class AboutPage : Page
{
    public AboutPage()
    {
        InitializeComponent();
    }

    private async void OpenRepository_Click(object sender, RoutedEventArgs e)
    {
        await Windows.System.Launcher.LaunchUriAsync(
            new Uri("https://github.com/Alvin070911/ncm-converter"));
    }
}
