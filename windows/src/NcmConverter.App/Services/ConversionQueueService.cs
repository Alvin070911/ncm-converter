using System.Collections.ObjectModel;
using System.Text;
using Microsoft.UI.Dispatching;
using NcmConverter.App.Models;
using NcmConverter.Core.Audio;
using NcmConverter.Core.Conversion;
using NcmConverter.Core.Ncm;

namespace NcmConverter.App.Services;

public sealed class ConversionQueueService
{
    private readonly SettingsService _settings;
    private readonly HistoryService _history;
    private readonly NcmDecoder _decoder = new();
    private readonly DispatcherQueue _dispatcher;
    private CancellationTokenSource? _cts;

    public ConversionQueueService(SettingsService settings, HistoryService history)
    {
        _settings = settings;
        _history = history;
        _dispatcher = DispatcherQueue.GetForCurrentThread();
    }

    public ObservableCollection<ConversionItem> Items { get; } = new();

    public bool IsRunning { get; private set; }

    public event EventHandler? Changed;

    public async Task<int> AddFilesAsync(IEnumerable<string> paths)
    {
        var existing = Items
            .Select(item => item.SourcePath)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        var added = new List<ConversionItem>();
        foreach (var path in paths)
        {
            var fullPath = Path.GetFullPath(path);
            if (!File.Exists(fullPath) ||
                !string.Equals(Path.GetExtension(fullPath), ".ncm", StringComparison.OrdinalIgnoreCase) ||
                existing.Contains(fullPath))
            {
                continue;
            }

            var item = new ConversionItem(fullPath);
            Items.Add(item);
            added.Add(item);
            existing.Add(fullPath);
        }

        Changed?.Invoke(this, EventArgs.Empty);
        if (added.Count > 0)
        {
            await Task.WhenAll(added.Select(AnalyzeAsync));
            Changed?.Invoke(this, EventArgs.Empty);
        }

        return added.Count;
    }

    public async Task StartAsync()
    {
        if (IsRunning)
        {
            return;
        }

        var pending = Items
            .Where(item => item.Status == ConversionStatus.Queued)
            .ToList();
        if (pending.Count == 0)
        {
            return;
        }

        IsRunning = true;
        _cts = new CancellationTokenSource();
        Changed?.Invoke(this, EventArgs.Empty);

        var concurrency = Math.Clamp(_settings.Current.Concurrency, 1, 4);
        using var semaphore = new SemaphoreSlim(concurrency, concurrency);

        try
        {
            await Task.WhenAll(pending.Select(item =>
                RunItemAsync(item, semaphore, _cts.Token)));
        }
        finally
        {
            IsRunning = false;
            _cts?.Dispose();
            _cts = null;
            Changed?.Invoke(this, EventArgs.Empty);
        }
    }

    public void Cancel()
    {
        _cts?.Cancel();
    }

    public void RetryFailed()
    {
        foreach (var item in Items)
        {
            if (item.Status is ConversionStatus.Failed or ConversionStatus.Cancelled)
            {
                item.Status = ConversionStatus.Queued;
                item.ErrorMessage = null;
                item.Progress = 0;
            }
        }

        Changed?.Invoke(this, EventArgs.Empty);
    }

    public bool RetryItem(ConversionItem item)
    {
        if (item.Status is not (ConversionStatus.Failed or ConversionStatus.Cancelled))
        {
            return false;
        }

        item.Status = ConversionStatus.Queued;
        item.ErrorMessage = null;
        item.Progress = 0;
        Changed?.Invoke(this, EventArgs.Empty);
        return true;
    }

    public bool Remove(ConversionItem item)
    {
        if (item.Status is ConversionStatus.Converting or ConversionStatus.Analyzing)
        {
            return false;
        }

        var removed = Items.Remove(item);
        if (removed)
        {
            Changed?.Invoke(this, EventArgs.Empty);
        }

        return removed;
    }

    public void ClearFinished()
    {
        var removable = Items
            .Where(item => item.Status is ConversionStatus.Succeeded
                or ConversionStatus.Failed
                or ConversionStatus.Cancelled
                or ConversionStatus.Skipped)
            .ToList();

        foreach (var item in removable)
        {
            Items.Remove(item);
        }

        Changed?.Invoke(this, EventArgs.Empty);
    }

    public int QueuedCount => Items.Count(item =>
        item.Status is ConversionStatus.Pending or ConversionStatus.Analyzing or ConversionStatus.Queued);

    public int FinishedCount => Items.Count(item =>
        item.Status is ConversionStatus.Succeeded or ConversionStatus.Failed
            or ConversionStatus.Cancelled or ConversionStatus.Skipped);

    private async Task AnalyzeAsync(ConversionItem item)
    {
        item.Status = ConversionStatus.Analyzing;
        Changed?.Invoke(this, EventArgs.Empty);

        try
        {
            var info = await Task.Run(() => _decoder.Inspect(item.SourcePath));
            item.Info = info;
            item.Status = ConversionStatus.Queued;
        }
        catch (NcmException ex)
        {
            item.Status = ConversionStatus.Failed;
            item.ErrorMessage = ex.Message;
        }
        catch (Exception)
        {
            item.Status = ConversionStatus.Failed;
            item.ErrorMessage = "无法解析该文件";
        }
        finally
        {
            Changed?.Invoke(this, EventArgs.Empty);
        }
    }

    private async Task RunItemAsync(
        ConversionItem item,
        SemaphoreSlim semaphore,
        CancellationToken cancellationToken)
    {
        try
        {
            await semaphore.WaitAsync(cancellationToken);
        }
        catch (OperationCanceledException)
        {
            item.Status = ConversionStatus.Cancelled;
            item.ErrorMessage = "已取消";
            return;
        }

        var startedAt = DateTimeOffset.Now;
        try
        {
            if (item.Status != ConversionStatus.Queued)
            {
                return;
            }

            item.Status = ConversionStatus.Converting;
            item.Progress = 0;
            item.ErrorMessage = null;
            Changed?.Invoke(this, EventArgs.Empty);

            var options = BuildOptions();
            var outputDirectory = _settings.Current.OutputDirectory;
            var progress = new DispatcherProgress(_dispatcher, item);

            var result = await Task.Run(
                () => _decoder.Decode(item.SourcePath, outputDirectory, options, progress, cancellationToken),
                cancellationToken);

            item.OutputPath = result.OutputPath;
            item.Progress = 100;
            item.Status = ConversionStatus.Succeeded;
            AddHistory(item, startedAt, "success", result.Format.DisplayName(), result.OutputPath, null);
        }
        catch (NcmException ex)
        {
            if (ex.Code == NcmErrorCode.Cancelled)
            {
                item.Status = ConversionStatus.Cancelled;
                item.ErrorMessage = "已取消";
                return;
            }

            if (ex.Code == NcmErrorCode.ConflictSkipped)
            {
                item.Status = ConversionStatus.Skipped;
                item.ErrorMessage = ex.Message;
                AddHistory(item, startedAt, "skipped", GetSourceFormat(item), null, ex.Message);
            }
            else
            {
                item.Status = ConversionStatus.Failed;
                item.ErrorMessage = BuildErrorMessage(ex);
                LogConversionError(item.SourcePath, ex);
                AddHistory(item, startedAt, "failed", GetSourceFormat(item), null, item.ErrorMessage);
            }
        }
        catch (Exception ex)
        {
            item.Status = ConversionStatus.Failed;
            item.ErrorMessage = BuildErrorMessage(ex);
            LogConversionError(item.SourcePath, ex);
            AddHistory(item, startedAt, "failed", GetSourceFormat(item), null, item.ErrorMessage);
        }
        finally
        {
            semaphore.Release();
            Changed?.Invoke(this, EventArgs.Empty);
        }
    }

    private DecodeOptions BuildOptions()
    {
        var conflictPolicy = _settings.Current.ConflictPolicy switch
        {
            "Skip" => ConflictPolicy.Skip,
            "Overwrite" => ConflictPolicy.Overwrite,
            _ => ConflictPolicy.Rename
        };

        var namingMode = _settings.Current.NamingMode == "Original"
            ? NamingMode.SourceFileName
            : NamingMode.Metadata;

        return new DecodeOptions
        {
            ConflictPolicy = conflictPolicy,
            NamingMode = namingMode,
            SaveCover = _settings.Current.SaveCoverSeparately,
            WriteTags = true
        };
    }

    private void AddHistory(
        ConversionItem item,
        DateTimeOffset startedAt,
        string status,
        string outputFormat,
        string? outputPath,
        string? errorMessage)
    {
        if (!_settings.Current.RecordHistory)
        {
            return;
        }

        try
        {
            long outputSize = 0;
            if (outputPath is not null && File.Exists(outputPath))
            {
                try
                {
                    outputSize = new FileInfo(outputPath).Length;
                }
                catch
                {
                    // 忽略读取失败
                }
            }

            _history.Add(new HistoryEntry
            {
                Title = item.Info?.Metadata.Title,
                Artists = item.Info?.Metadata.ArtistText,
                Album = item.Info?.Metadata.Album,
                SourceFormat = GetSourceFormat(item),
                OutputFormat = outputFormat,
                SourceSize = item.Info?.FileSize ?? 0,
                OutputSize = outputSize,
                OutputPath = outputPath,
                FinishedAt = DateTimeOffset.Now,
                ElapsedMs = (long)(DateTimeOffset.Now - startedAt).TotalMilliseconds,
                Status = status,
                ErrorMessage = errorMessage
            });
        }
        catch (Exception ex)
        {
            // 历史记录写入失败不能把已经成功的转换改判为失败。
            LogConversionError(item.SourcePath, ex);
        }
    }

    private static string BuildErrorMessage(Exception exception)
    {
        var builder = new StringBuilder(exception.Message);
        var inner = exception.InnerException;
        var depth = 0;

        while (inner is not null && depth < 3)
        {
            var message = inner.Message;
            if (!string.IsNullOrWhiteSpace(message) &&
                !builder.ToString().Contains(message, StringComparison.Ordinal))
            {
                builder.Append("：").Append(message);
            }

            inner = inner.InnerException;
            depth++;
        }

        return builder.ToString();
    }

    private static void LogConversionError(string sourcePath, Exception exception)
    {
        try
        {
            var directory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "NcmConverter");
            Directory.CreateDirectory(directory);
            File.AppendAllText(
                Path.Combine(directory, "conversion.log"),
                $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] {sourcePath}{Environment.NewLine}{exception}{Environment.NewLine}{Environment.NewLine}",
                Encoding.UTF8);
        }
        catch
        {
            // 日志写入失败不影响转换
        }
    }

    private static string GetSourceFormat(ConversionItem item) =>
        item.Info?.ActualFormat?.DisplayName() ?? "NCM";

    private sealed class DispatcherProgress : IProgress<DecodeProgress>
    {
        private readonly DispatcherQueue? _dispatcher;
        private readonly ConversionItem _item;
        private long _lastUpdateTimestamp;
        private int _lastPercent = -1;

        public DispatcherProgress(DispatcherQueue dispatcher, ConversionItem item)
        {
            _dispatcher = dispatcher;
            _item = item;
        }

        public void Report(DecodeProgress value)
        {
            var percent = (int)Math.Round(value.Percent);
            var now = Environment.TickCount64;

            // 大文件每 64 KB 回调一次，直接把几千次更新压到 UI 线程会让界面卡住。
            // 限制为最多约 10 次/秒，并降低优先级，保证输入事件优先处理。
            if (now - _lastUpdateTimestamp < 100 && percent < 100)
            {
                return;
            }

            if (percent == _lastPercent && now - _lastUpdateTimestamp < 300)
            {
                return;
            }

            _lastUpdateTimestamp = now;
            _lastPercent = percent;

            if (_dispatcher is null)
            {
                _item.Progress = value.Percent;
                return;
            }

            _dispatcher.TryEnqueue(
                DispatcherQueuePriority.Low,
                () => _item.Progress = value.Percent);
        }
    }
}
