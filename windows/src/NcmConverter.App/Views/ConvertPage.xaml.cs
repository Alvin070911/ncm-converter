using System.Diagnostics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using NcmConverter.App.Models;
using NcmConverter.App.Services;
using Windows.ApplicationModel.DataTransfer;
using Windows.Storage;

namespace NcmConverter.App.Views;

public sealed partial class ConvertPage : Page
{
    public ConvertPage()
    {
        InitializeComponent();
    }

    private void Page_Loaded(object sender, RoutedEventArgs e)
    {
        QueueList.ItemsSource = AppServices.Queue.Items;
        AppServices.Queue.Changed -= Queue_Changed;
        AppServices.Queue.Changed += Queue_Changed;
        UpdateChrome();
    }

    private void Page_Unloaded(object sender, RoutedEventArgs e)
    {
        AppServices.Queue.Changed -= Queue_Changed;
    }

    private void Queue_Changed(object? sender, EventArgs e)
    {
        UpdateChrome();
    }

    private async void SelectFiles_Click(object sender, RoutedEventArgs e)
    {
        if (App.MainWindow is null)
        {
            return;
        }

        var files = await FilePickerService.PickFilesAsync(App.MainWindow);
        await AddPathsAsync(files);
    }

    private async void SelectFolder_Click(object sender, RoutedEventArgs e)
    {
        if (App.MainWindow is null)
        {
            return;
        }

        var folder = await FilePickerService.PickFolderAsync(App.MainWindow);
        if (folder is not null)
        {
            await AddPathsAsync(new[] { folder });
        }
    }

    private void DropZone_DragOver(object sender, DragEventArgs e)
    {
        if (e.DataView.Contains(StandardDataFormats.StorageItems))
        {
            e.AcceptedOperation = DataPackageOperation.Copy;
            e.DragUIOverride.Caption = "添加到队列";
        }
    }

    private async void DropZone_Drop(object sender, DragEventArgs e)
    {
        if (!e.DataView.Contains(StandardDataFormats.StorageItems))
        {
            return;
        }

        var items = await e.DataView.GetStorageItemsAsync();
        var paths = items
            .Select(item => item.Path)
            .Where(path => !string.IsNullOrWhiteSpace(path))
            .ToList();

        await AddPathsAsync(paths);
    }

    private async Task AddPathsAsync(IEnumerable<string> paths)
    {
        var expanded = FilePickerService.ExpandNcmFiles(paths);
        if (expanded.Count == 0)
        {
            ShowNotice(InfoBarSeverity.Warning, "没有找到 .ncm 文件");
            return;
        }

        var added = await AppServices.Queue.AddFilesAsync(expanded);
        if (added == 0)
        {
            ShowNotice(InfoBarSeverity.Informational, "这些文件已经在队列中");
        }
        else
        {
            ShowNotice(InfoBarSeverity.Success, $"已添加 {added} 个文件");
        }

        UpdateChrome();
    }

    private async void Start_Click(object sender, RoutedEventArgs e)
    {
        await StartAndReportAsync();
    }

    private async Task StartAndReportAsync()
    {
        await AppServices.Queue.StartAsync();

        var success = AppServices.Queue.Items.Count(item => item.Status == ConversionStatus.Succeeded);
        var failed = AppServices.Queue.Items.Count(item =>
            item.Status is ConversionStatus.Failed or ConversionStatus.Skipped);

        if (success > 0 || failed > 0)
        {
            ShowNotice(
                failed > 0 ? InfoBarSeverity.Warning : InfoBarSeverity.Success,
                $"转换完成：成功 {success} 个，失败 {failed} 个");
        }

        UpdateChrome();
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        AppServices.Queue.Cancel();
        ShowNotice(InfoBarSeverity.Informational, "正在取消转换…");
    }

    private async void Retry_Click(object sender, RoutedEventArgs e)
    {
        AppServices.Queue.RetryFailed();
        await StartAndReportAsync();
    }

    private void Clear_Click(object sender, RoutedEventArgs e)
    {
        AppServices.Queue.ClearFinished();
        UpdateChrome();
    }

    private async void RetryItem_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is not ConversionItem item)
        {
            return;
        }

        AppServices.Queue.RetryItem(item);
        await StartAndReportAsync();
    }

    private void RemoveItem_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is not ConversionItem item)
        {
            return;
        }

        if (!AppServices.Queue.Remove(item))
        {
            ShowNotice(InfoBarSeverity.Warning, "正在转换的文件不能移除");
        }
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
        ShowNotice(InfoBarSeverity.Success, "输出位置已更新");
        UpdateChrome();
    }

    private void OpenOutput_Click(object sender, RoutedEventArgs e)
    {
        var outputDirectory = AppServices.Settings.Current.OutputDirectory;
        if (string.IsNullOrWhiteSpace(outputDirectory))
        {
            outputDirectory = AppServices.Queue.Items
                .Select(item => Path.GetDirectoryName(item.SourcePath))
                .FirstOrDefault(path => !string.IsNullOrWhiteSpace(path));
        }

        if (string.IsNullOrWhiteSpace(outputDirectory) || !Directory.Exists(outputDirectory))
        {
            ShowNotice(InfoBarSeverity.Warning, "输出目录还不存在");
            return;
        }

        try
        {
            Process.Start(new ProcessStartInfo("explorer.exe", $"\"{outputDirectory}\"")
            {
                UseShellExecute = true
            });
        }
        catch
        {
            ShowNotice(InfoBarSeverity.Error, "无法打开输出目录");
        }
    }

    private void UpdateChrome()
    {
        var items = AppServices.Queue.Items;
        var success = items.Count(item => item.Status == ConversionStatus.Succeeded);
        var failed = items.Count(item =>
            item.Status is ConversionStatus.Failed or ConversionStatus.Skipped);
        var queued = AppServices.Queue.QueuedCount;

        SummaryText.Text = items.Count == 0
            ? "队列为空"
            : $"队列 {items.Count} 个 · 完成 {success} · 失败 {failed}"
                + (AppServices.Queue.IsRunning ? $" · 待处理 {queued}" : string.Empty);

        BusyRing.IsActive = AppServices.Queue.IsRunning;
        StartButton.IsEnabled = !AppServices.Queue.IsRunning && queued > 0;
        StartButton.Content = AppServices.Queue.IsRunning ? "转换中…" : "开始转换";
        CancelButton.IsEnabled = AppServices.Queue.IsRunning;
        EmptyState.Visibility = items.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

        var outputDirectory = AppServices.Settings.Current.OutputDirectory;
        OutputText.Text = string.IsNullOrWhiteSpace(outputDirectory)
            ? "与源文件同目录"
            : outputDirectory;
    }

    private void ShowNotice(InfoBarSeverity severity, string message)
    {
        NoticeBar.Severity = severity;
        NoticeBar.Message = message;
        NoticeBar.IsOpen = true;
    }
}
