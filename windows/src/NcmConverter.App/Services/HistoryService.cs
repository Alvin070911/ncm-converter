using System.Text;
using System.Text.Json;
using NcmConverter.App.Models;

namespace NcmConverter.App.Services;

public sealed class HistoryService
{
    private const int MaxEntries = 300;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true
    };

    private readonly string _path;
    private readonly List<HistoryEntry> _entries;

    public HistoryService()
    {
        var directory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "NcmConverter");
        Directory.CreateDirectory(directory);
        _path = Path.Combine(directory, "history.json");
        _entries = Load();
    }

    public event EventHandler? Changed;

    public IReadOnlyList<HistoryEntry> Entries => _entries;

    public void Add(HistoryEntry entry)
    {
        _entries.Insert(0, entry);
        while (_entries.Count > MaxEntries)
        {
            _entries.RemoveAt(_entries.Count - 1);
        }

        Save();
        Changed?.Invoke(this, EventArgs.Empty);
    }

    public void Remove(string id)
    {
        _entries.RemoveAll(entry => entry.Id == id);
        Save();
        Changed?.Invoke(this, EventArgs.Empty);
    }

    public void Clear()
    {
        _entries.Clear();
        Save();
        Changed?.Invoke(this, EventArgs.Empty);
    }

    private List<HistoryEntry> Load()
    {
        if (!File.Exists(_path))
        {
            return new List<HistoryEntry>();
        }

        try
        {
            var json = File.ReadAllText(_path, Encoding.UTF8);
            return JsonSerializer.Deserialize<List<HistoryEntry>>(json, JsonOptions) ?? new List<HistoryEntry>();
        }
        catch
        {
            return new List<HistoryEntry>();
        }
    }

    private void Save()
    {
        var tempPath = _path + ".tmp";
        try
        {
            File.WriteAllText(tempPath, JsonSerializer.Serialize(_entries, JsonOptions), Encoding.UTF8);
            File.Move(tempPath, _path, overwrite: true);
        }
        catch
        {
            // 历史记录属于辅助数据，写入失败不能影响转换结果。
            try
            {
                if (File.Exists(tempPath))
                {
                    File.Delete(tempPath);
                }
            }
            catch
            {
                // 忽略清理失败
            }
        }
    }
}
