using CommunityToolkit.Mvvm.ComponentModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using NcmConverter.Core.Audio;
using NcmConverter.Core.Ncm;
using Windows.UI;

namespace NcmConverter.App.Models;

public sealed partial class ConversionItem : ObservableObject
{
    public ConversionItem(string sourcePath)
    {
        SourcePath = sourcePath;
    }

    public string SourcePath { get; }

    [ObservableProperty]
    private NcmFileInfo? _info;

    [ObservableProperty]
    private ConversionStatus _status = ConversionStatus.Pending;

    [ObservableProperty]
    private double _progress;

    [ObservableProperty]
    private string? _errorMessage;

    [ObservableProperty]
    private string? _outputPath;

    public string DisplayTitle =>
        Info?.Metadata.Title is { Length: > 0 } title
            ? title
            : Path.GetFileNameWithoutExtension(SourcePath);

    public string Subtitle
    {
        get
        {
            var artist = Info?.Metadata.ArtistText;
            var album = Info?.Metadata.Album;
            var size = FormatSize(Info?.FileSize ?? 0);
            var parts = new List<string>();
            if (!string.IsNullOrWhiteSpace(artist))
            {
                parts.Add(artist);
            }

            if (!string.IsNullOrWhiteSpace(album))
            {
                parts.Add(album);
            }

            parts.Add(size);
            return string.Join(" · ", parts);
        }
    }

    public string FormatBadge => Info?.ActualFormat?.DisplayName() ?? "NCM";

    public string StatusText => Status switch
    {
        ConversionStatus.Pending => "待处理",
        ConversionStatus.Analyzing => "解析中",
        ConversionStatus.Queued => "排队中",
        ConversionStatus.Converting => $"{Progress:F0}%",
        ConversionStatus.Succeeded => "完成",
        ConversionStatus.Failed => "失败",
        ConversionStatus.Cancelled => "已取消",
        ConversionStatus.Skipped => "已跳过",
        _ => string.Empty
    };

    public Brush StatusBrush => new SolidColorBrush(Status switch
    {
        ConversionStatus.Succeeded => Color.FromArgb(255, 22, 163, 74),
        ConversionStatus.Failed => Color.FromArgb(255, 220, 38, 38),
        ConversionStatus.Skipped => Color.FromArgb(255, 161, 161, 170),
        ConversionStatus.Cancelled => Color.FromArgb(255, 161, 161, 170),
        ConversionStatus.Queued => Color.FromArgb(255, 0, 161, 214),
        _ => Color.FromArgb(255, 251, 114, 153)
    });

    public Visibility ProgressVisibility =>
        Status is ConversionStatus.Converting ? Visibility.Visible : Visibility.Collapsed;

    public Visibility ErrorVisibility =>
        Status is ConversionStatus.Failed && !string.IsNullOrWhiteSpace(ErrorMessage)
            ? Visibility.Visible
            : Visibility.Collapsed;

    public Visibility RetryVisibility =>
        Status is ConversionStatus.Failed or ConversionStatus.Cancelled
            ? Visibility.Visible
            : Visibility.Collapsed;

    public string ErrorText => ErrorMessage ?? string.Empty;

    partial void OnInfoChanged(NcmFileInfo? value)
    {
        OnPropertyChanged(nameof(DisplayTitle));
        OnPropertyChanged(nameof(Subtitle));
        OnPropertyChanged(nameof(FormatBadge));
    }

    partial void OnStatusChanged(ConversionStatus value)
    {
        OnPropertyChanged(nameof(StatusText));
        OnPropertyChanged(nameof(StatusBrush));
        OnPropertyChanged(nameof(ProgressVisibility));
        OnPropertyChanged(nameof(ErrorVisibility));
        OnPropertyChanged(nameof(RetryVisibility));
    }

    partial void OnProgressChanged(double value)
    {
        OnPropertyChanged(nameof(StatusText));
    }

    partial void OnErrorMessageChanged(string? value)
    {
        OnPropertyChanged(nameof(ErrorVisibility));
        OnPropertyChanged(nameof(ErrorText));
    }

    private static string FormatSize(long bytes)
    {
        if (bytes <= 0)
        {
            return "0 B";
        }

        if (bytes < 1024)
        {
            return $"{bytes} B";
        }

        if (bytes < 1024 * 1024)
        {
            return $"{bytes / 1024.0:F1} KB";
        }

        if (bytes < 1024L * 1024 * 1024)
        {
            return $"{bytes / 1024.0 / 1024:F1} MB";
        }

        return $"{bytes / 1024.0 / 1024 / 1024:F2} GB";
    }
}
