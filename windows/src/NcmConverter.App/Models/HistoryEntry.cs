using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace NcmConverter.App.Models;

public sealed class HistoryEntry
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");

    public string? Title { get; set; }

    public string? Artists { get; set; }

    public string? Album { get; set; }

    public string? SourceFormat { get; set; }

    public string OutputFormat { get; set; } = string.Empty;

    public long SourceSize { get; set; }

    public long OutputSize { get; set; }

    public string? OutputPath { get; set; }

    public DateTimeOffset FinishedAt { get; set; } = DateTimeOffset.Now;

    public long ElapsedMs { get; set; }

    public string Status { get; set; } = "success";

    public string? ErrorMessage { get; set; }

    public string DisplayTitle =>
        Title is { Length: > 0 } title ? title : Path.GetFileNameWithoutExtension(OutputPath ?? "未命名");

    public string Subtitle
    {
        get
        {
            var parts = new List<string>();
            if (!string.IsNullOrWhiteSpace(Artists))
            {
                parts.Add(Artists);
            }

            parts.Add(OutputFormat.ToUpperInvariant());
            parts.Add(FormatSize(OutputSize));
            return string.Join(" · ", parts);
        }
    }

    public string TimeText => FinishedAt.ToLocalTime().ToString("MM-dd HH:mm");

    public string StatusText => Status switch
    {
        "success" => "完成",
        "skipped" => "已跳过",
        _ => "失败"
    };

    public Brush StatusBrush => new SolidColorBrush(Status switch
    {
        "success" => Color.FromArgb(255, 22, 163, 74),
        "skipped" => Color.FromArgb(255, 161, 161, 170),
        _ => Color.FromArgb(255, 220, 38, 38)
    });

    public string DetailText => Status == "success"
        ? OutputPath ?? string.Empty
        : ErrorMessage ?? "未知错误";

    public bool MatchesSearch(string keyword)
    {
        if (string.IsNullOrWhiteSpace(keyword))
        {
            return true;
        }

        var text = $"{Title} {Artists} {Album} {OutputPath}";
        return text.Contains(keyword, StringComparison.OrdinalIgnoreCase);
    }

    public bool MatchesStatus(string filter) => filter switch
    {
        "success" => Status == "success",
        "failed" => Status is "failed" or "skipped",
        _ => true
    };

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
