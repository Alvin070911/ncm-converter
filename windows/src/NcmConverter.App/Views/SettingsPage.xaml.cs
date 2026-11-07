using System.Diagnostics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using NcmConverter.App.Services;

namespace NcmConverter.App.Views;

public sealed partial class SettingsPage : Page
{
    private bool _loading;

    public SettingsPage()
    {
        InitializeComponent();
    }

    private void Page_Loaded(object sender, RoutedEventArgs e)
    {
        _loading = true;
        var settings = AppServices.Settings.Current;

        SelectByTag(ThemeBox, settings.Theme);
        SelectByTag(NamingBox, settings.NamingMode);
        SelectByTag(ConflictBox, settings.ConflictPolicy);
        SelectByTag(ConcurrencyBox, settings.Concurrency.ToString());
        CoverToggle.IsOn = settings.SaveCoverSeparately;
        HistoryToggle.IsOn = settings.RecordHistory;
        UpdateOutputText();

        _loading = false;
    }

    private void Theme_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading)
        {
            return;
        }

        var theme = GetSelectedTag(ThemeBox);
        AppServices.Settings.Current.Theme = theme;
        AppServices.Settings.Save();
        App.MainWindow?.ApplyTheme(theme);
    }

    private void Naming_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading)
        {
            return;
        }

        AppServices.Settings.Current.NamingMode = GetSelectedTag(NamingBox);
        AppServices.Settings.Save();
    }

    private void Conflict_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading)
        {
            return;
        }

        AppServices.Settings.Current.ConflictPolicy = GetSelectedTag(ConflictBox);
        AppServices.Settings.Save();
    }

    private void Concurrency_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading)
        {
            return;
        }

        if (int.TryParse(GetSelectedTag(ConcurrencyBox), out var concurrency))
        {
            AppServices.Settings.Current.Concurrency = concurrency;
            AppServices.Settings.Save();
        }
    }

    private void Cover_Toggled(object sender, RoutedEventArgs e)
    {
        if (_loading)
        {
            return;
        }

        AppServices.Settings.Current.SaveCoverSeparately = CoverToggle.IsOn;
        AppServices.Settings.Save();
    }

    private void History_Toggled(object sender, RoutedEventArgs e)
    {
        if (_loading)
        {
            return;
        }

        AppServices.Settings.Current.RecordHistory = HistoryToggle.IsOn;
        AppServices.Settings.Save();
    }

    private async void ChangeOutput_Click(object sender, RoutedEventArgs e)
    {
        if (App.MainWindow is null)
        {
            return;
        }

        var folder = await FilePickerService.PickFolderAsync(App.MainWindow);
        if (folder is null)
        {
            return;
        }

        AppServices.Settings.Current.OutputDirectory = folder;
        AppServices.Settings.Save();
        UpdateOutputText();
    }

    private void ResetOutput_Click(object sender, RoutedEventArgs e)
    {
        AppServices.Settings.Current.OutputDirectory = null;
        AppServices.Settings.Save();
        UpdateOutputText();
    }

    private async void ClearHistory_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new ContentDialog
        {
            Title = "清空历史",
            Content = "将删除全部转换记录，但不会删除任何音频文件。",
            PrimaryButtonText = "清空",
            CloseButtonText = "取消",
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = XamlRoot
        };

        if (await dialog.ShowAsync() == ContentDialogResult.Primary)
        {
            AppServices.History.Clear();
        }
    }

    private void OpenDataDirectory_Click(object sender, RoutedEventArgs e)
    {
        var directory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "NcmConverter");
        if (!Directory.Exists(directory))
        {
            return;
        }

        try
        {
            Process.Start(new ProcessStartInfo("explorer.exe", $"\"{directory}\"")
            {
                UseShellExecute = true
            });
        }
        catch
        {
            // 忽略打开失败
        }
    }

    private void UpdateOutputText()
    {
        OutputDirectoryText.Text = string.IsNullOrWhiteSpace(AppServices.Settings.Current.OutputDirectory)
            ? "与源文件同目录"
            : AppServices.Settings.Current.OutputDirectory!;
    }

    private static string GetSelectedTag(ComboBox box) =>
        (box.SelectedItem as ComboBoxItem)?.Tag as string ?? string.Empty;

    private static void SelectByTag(ComboBox box, string tag)
    {
        foreach (var item in box.Items)
        {
            if (item is ComboBoxItem comboBoxItem && comboBoxItem.Tag as string == tag)
            {
                box.SelectedItem = comboBoxItem;
                return;
            }
        }
    }
}
