using System.Diagnostics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using NcmConverter.App.Models;
using NcmConverter.App.Services;

namespace NcmConverter.App.Views;

public sealed partial class HistoryPage : Page
{
    public HistoryPage()
    {
        InitializeComponent();
    }

    private void Page_Loaded(object sender, RoutedEventArgs e)
    {
        AppServices.History.Changed -= History_Changed;
        AppServices.History.Changed += History_Changed;
        Refresh();
    }

    private void Page_Unloaded(object sender, RoutedEventArgs e)
    {
        AppServices.History.Changed -= History_Changed;
    }

    private void History_Changed(object? sender, EventArgs e)
    {
        Refresh();
    }

    private void Search_Changed(object sender, TextChangedEventArgs e)
    {
        Refresh();
    }

    private void Filter_Changed(object sender, SelectionChangedEventArgs e)
    {
        Refresh();
    }

    private void Refresh()
    {
        if (SearchBox is null || StatusFilter is null)
        {
            return;
        }

        var keyword = SearchBox.Text;
        var filter = (StatusFilter.SelectedItem as ComboBoxItem)?.Tag as string ?? "all";
        var entries = AppServices.History.Entries
            .Where(entry => entry.MatchesSearch(keyword) && entry.MatchesStatus(filter))
            .ToList();

        HistoryList.ItemsSource = entries;
        EmptyState.Visibility = entries.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private void OpenHistoryItem_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is not HistoryEntry entry ||
            string.IsNullOrWhiteSpace(entry.OutputPath))
        {
            return;
        }

        var path = entry.OutputPath;
        try
        {
            if (File.Exists(path))
            {
                Process.Start(new ProcessStartInfo("explorer.exe", $"/select,\"{path}\"")
                {
                    UseShellExecute = true
                });
            }
            else if (Directory.Exists(Path.GetDirectoryName(path) ?? string.Empty))
            {
                Process.Start(new ProcessStartInfo("explorer.exe", $"\"{Path.GetDirectoryName(path)}\"")
                {
                    UseShellExecute = true
                });
            }
        }
        catch
        {
            // 打开失败时保持列表可用
        }
    }

    private async void DeleteHistoryItem_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is not HistoryEntry entry)
        {
            return;
        }

        var dialog = new ContentDialog
        {
            Title = "删除记录",
            Content = $"确定删除「{entry.DisplayTitle}」这条历史记录吗？音频文件不会被删除。",
            PrimaryButtonText = "删除",
            CloseButtonText = "取消",
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = XamlRoot
        };

        if (await dialog.ShowAsync() == ContentDialogResult.Primary)
        {
            AppServices.History.Remove(entry.Id);
        }
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
}
