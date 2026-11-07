using System.Text.RegularExpressions;
using NcmConverter.Core.Ncm;

namespace NcmConverter.Core.Conversion;

public static class NamingPolicy
{
    private const int MaxNameLength = 180;

    public static string BuildFileName(NcmMetadata metadata, string sourcePath, string extension)
    {
        var title = metadata.Title?.Trim();
        var artist = metadata.ArtistText.Trim();

        string stem;
        if (!string.IsNullOrWhiteSpace(title))
        {
            stem = string.IsNullOrWhiteSpace(artist) ? title : $"{title} - {artist}";
        }
        else
        {
            stem = Path.GetFileNameWithoutExtension(sourcePath);
        }

        stem = Sanitize(stem);
        if (stem.Length > MaxNameLength)
        {
            stem = stem[..MaxNameLength].TrimEnd();
        }

        if (string.IsNullOrWhiteSpace(stem))
        {
            stem = "未命名";
        }

        return stem + extension;
    }

    public static string ResolveOutputPath(string directory, string fileName, ConflictPolicy policy)
    {
        var path = Path.Combine(directory, fileName);
        if (!File.Exists(path))
        {
            return path;
        }

        switch (policy)
        {
            case ConflictPolicy.Skip:
                throw new NcmException(NcmErrorCode.ConflictSkipped, "目标文件已存在，已跳过");
            case ConflictPolicy.Overwrite:
                return path;
            default:
                var stem = Path.GetFileNameWithoutExtension(fileName);
                var extension = Path.GetExtension(fileName);
                for (var index = 1; index <= 999; index++)
                {
                    var candidate = Path.Combine(directory, $"{stem} ({index}){extension}");
                    if (!File.Exists(candidate))
                    {
                        return candidate;
                    }
                }

                throw new NcmException(NcmErrorCode.ConflictSkipped, "同名文件过多，无法生成唯一文件名");
        }
    }

    public static string Sanitize(string name)
    {
        var invalidChars = Path.GetInvalidFileNameChars();
        var builder = new char[name.Length];
        for (var i = 0; i < name.Length; i++)
        {
            builder[i] = invalidChars.Contains(name[i]) ? '_' : name[i];
        }

        var sanitized = new string(builder);
        sanitized = Regex.Replace(sanitized, @"\s+", " ");
        sanitized = sanitized.Trim().TrimEnd('.', ' ');
        return sanitized;
    }
}
