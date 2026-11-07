using Microsoft.UI.Xaml;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace NcmConverter.App.Services;

public static class FilePickerService
{
    public static async Task<IReadOnlyList<string>> PickFilesAsync(Window window)
    {
        var picker = new FileOpenPicker
        {
            ViewMode = PickerViewMode.List,
            SuggestedStartLocation = PickerLocationId.MusicLibrary
        };
        picker.FileTypeFilter.Add(".ncm");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(window));

        var files = await picker.PickMultipleFilesAsync();
        if (files is null)
        {
            return Array.Empty<string>();
        }

        return files
            .Select(file => file.Path)
            .Where(path => !string.IsNullOrWhiteSpace(path))
            .ToList();
    }

    public static async Task<string?> PickFolderAsync(Window window)
    {
        var picker = new FolderPicker
        {
            SuggestedStartLocation = PickerLocationId.MusicLibrary
        };
        picker.FileTypeFilter.Add("*");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(window));

        var folder = await picker.PickSingleFolderAsync();
        return folder?.Path;
    }

    public static IReadOnlyList<string> ExpandNcmFiles(IEnumerable<string> paths)
    {
        var result = new List<string>();
        foreach (var path in paths)
        {
            if (Directory.Exists(path))
            {
                try
                {
                    result.AddRange(Directory.EnumerateFiles(path, "*.ncm", SearchOption.AllDirectories));
                }
                catch
                {
                    // 目录不可访问时跳过
                }

                continue;
            }

            if (File.Exists(path) && string.Equals(Path.GetExtension(path), ".ncm", StringComparison.OrdinalIgnoreCase))
            {
                result.Add(path);
            }
        }

        return result
            .Select(Path.GetFullPath)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
    }
}
