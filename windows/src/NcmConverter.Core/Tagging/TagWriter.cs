using TagLib;
using NcmConverter.Core.Audio;
using NcmConverter.Core.Ncm;

namespace NcmConverter.Core.Tagging;

public static class TagWriter
{
    public static IReadOnlyList<string> Write(
        string audioPath,
        AudioFormat format,
        NcmMetadata metadata,
        byte[] coverBytes,
        string? coverMimeType)
    {
        if (format == AudioFormat.Flac)
        {
            return FlacTagWriter.Write(audioPath, metadata, coverBytes, coverMimeType);
        }

        return WriteTagLib(audioPath, metadata, coverBytes, coverMimeType);
    }

    private static IReadOnlyList<string> WriteTagLib(
        string audioPath,
        NcmMetadata metadata,
        byte[] coverBytes,
        string? coverMimeType)
    {
        var warnings = new List<string>();
        try
        {
            using var file = TagLib.File.Create(audioPath);
            var tag = GetWritableTag(file);

            if (!string.IsNullOrWhiteSpace(metadata.Title))
            {
                tag.Title = metadata.Title;
            }

            if (metadata.Artists.Count > 0)
            {
                tag.Performers = metadata.Artists.ToArray();
            }

            if (!string.IsNullOrWhiteSpace(metadata.Album))
            {
                tag.Album = metadata.Album;
            }

            if (coverBytes.Length > 0)
            {
                tag.Pictures = new IPicture[]
                {
                    new Picture
                    {
                        Type = PictureType.FrontCover,
                        MimeType = coverMimeType ?? "image/jpeg",
                        Data = new ByteVector(coverBytes)
                    }
                };
            }

            file.Save();
        }
        catch (Exception ex)
        {
            warnings.Add($"标签写入失败：{ex.Message}");
        }

        return warnings;
    }

    private static TagLib.Tag GetWritableTag(TagLib.File file)
    {
        if (file is TagLib.Mpeg.File mpegFile)
        {
            return mpegFile.GetTag(TagTypes.Id3v2, true);
        }

        if (file is TagLib.Flac.File flacFile)
        {
            return flacFile.GetTag(TagTypes.Xiph, true);
        }

        return file.Tag;
    }
}
