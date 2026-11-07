using System.Buffers.Binary;
using System.Text;
using NcmConverter.Core.Ncm;

namespace NcmConverter.Core.Tagging;

internal static class FlacTagWriter
{
    private const int VorbisCommentBlock = 4;
    private const int PictureBlock = 6;
    private const int MaxBlockLength = 0xFFFFFF;

    public static IReadOnlyList<string> Write(
        string audioPath,
        NcmMetadata metadata,
        byte[] coverBytes,
        string? coverMimeType)
    {
        var warnings = new List<string>();
        if (string.IsNullOrWhiteSpace(metadata.Title) &&
            metadata.Artists.Count == 0 &&
            string.IsNullOrWhiteSpace(metadata.Album) &&
            coverBytes.Length == 0)
        {
            return warnings;
        }

        var tempPath = audioPath + ".tagtmp";
        try
        {
            long audioOffset;
            var blocks = new List<(int Type, byte[] Data)>();

            using (var input = new FileStream(
                       audioPath, FileMode.Open, FileAccess.Read, FileShare.Read, 8192, FileOptions.SequentialScan))
            {
                var magic = ReadExactly(input, 4);
                if (magic[0] != 0x66 || magic[1] != 0x4C || magic[2] != 0x61 || magic[3] != 0x43)
                {
                    throw new InvalidDataException("不是有效的 FLAC 文件");
                }

                var last = false;
                while (!last)
                {
                    var header = ReadExactly(input, 4);
                    last = (header[0] & 0x80) != 0;
                    var type = header[0] & 0x7F;
                    var length = (header[1] << 16) | (header[2] << 8) | header[3];
                    if (length <= 0 || length > MaxBlockLength)
                    {
                        throw new InvalidDataException("FLAC 元数据块长度异常");
                    }

                    var data = ReadExactly(input, length);
                    if (type != VorbisCommentBlock && type != PictureBlock)
                    {
                        blocks.Add((type, data));
                    }
                }

                audioOffset = input.Position;

                var comment = BuildVorbisComment(metadata);
                blocks.Add((VorbisCommentBlock, comment));

                if (coverBytes.Length > 0)
                {
                    blocks.Add((PictureBlock, BuildPicture(coverBytes, coverMimeType)));
                }

                if (blocks.Count == 0 || blocks[0].Type != 0)
                {
                    throw new InvalidDataException("FLAC 缺少 STREAMINFO");
                }

                using (var output = new FileStream(
                           tempPath, FileMode.CreateNew, FileAccess.Write, FileShare.None, 8192, FileOptions.SequentialScan))
                {
                    output.Write("fLaC"u8);

                    for (var i = 0; i < blocks.Count; i++)
                    {
                        var (type, data) = blocks[i];
                        if (data.Length > MaxBlockLength)
                        {
                            throw new InvalidDataException("FLAC 元数据块过大");
                        }

                        var header = new byte[4];
                        header[0] = (byte)(type | (i == blocks.Count - 1 ? 0x80 : 0));
                        header[1] = (byte)(data.Length >> 16);
                        header[2] = (byte)(data.Length >> 8);
                        header[3] = (byte)data.Length;
                        output.Write(header, 0, header.Length);
                        output.Write(data, 0, data.Length);
                    }

                    input.Position = audioOffset;
                    input.CopyTo(output, 64 * 1024);
                }
            }

            try
            {
                File.Replace(tempPath, audioPath, null);
            }
            catch (PlatformNotSupportedException)
            {
                File.Move(tempPath, audioPath, overwrite: true);
            }
            catch (IOException)
            {
                File.Move(tempPath, audioPath, overwrite: true);
            }
        }
        catch (Exception ex)
        {
            warnings.Add($"FLAC 标签写入失败：{ex.Message}");
            if (File.Exists(tempPath))
            {
                try
                {
                    File.Delete(tempPath);
                }
                catch
                {
                    // 清理失败忽略
                }
            }
        }

        return warnings;
    }

    private static byte[] BuildVorbisComment(NcmMetadata metadata)
    {
        var comments = new List<string>();
        if (!string.IsNullOrWhiteSpace(metadata.Title))
        {
            comments.Add($"TITLE={metadata.Title}");
        }

        foreach (var artist in metadata.Artists.Where(artist => !string.IsNullOrWhiteSpace(artist)))
        {
            comments.Add($"ARTIST={artist}");
        }

        if (!string.IsNullOrWhiteSpace(metadata.Album))
        {
            comments.Add($"ALBUM={metadata.Album}");
        }

        using var stream = new MemoryStream();
        WriteLittleEndianLength(stream, Encoding.UTF8.GetByteCount("NcmConverter"));
        stream.Write(Encoding.UTF8.GetBytes("NcmConverter"));

        WriteLittleEndianLength(stream, comments.Count);
        foreach (var comment in comments)
        {
            var bytes = Encoding.UTF8.GetBytes(comment);
            WriteLittleEndianLength(stream, bytes.Length);
            stream.Write(bytes, 0, bytes.Length);
        }

        return stream.ToArray();
    }

    private static byte[] BuildPicture(byte[] coverBytes, string? coverMimeType)
    {
        using var stream = new MemoryStream();
        WriteBigEndianInt32(stream, 3); // front cover

        var mime = Encoding.ASCII.GetBytes(coverMimeType ?? "image/jpeg");
        WriteBigEndianInt32(stream, mime.Length);
        stream.Write(mime, 0, mime.Length);

        WriteBigEndianInt32(stream, 0); // description 长度
        WriteBigEndianInt32(stream, 0); // width
        WriteBigEndianInt32(stream, 0); // height
        WriteBigEndianInt32(stream, 0); // color depth
        WriteBigEndianInt32(stream, 0); // indexed colors
        WriteBigEndianInt32(stream, coverBytes.Length);
        stream.Write(coverBytes, 0, coverBytes.Length);

        return stream.ToArray();
    }

    private static void WriteLittleEndianLength(Stream stream, int value)
    {
        Span<byte> buffer = stackalloc byte[4];
        BinaryPrimitives.WriteInt32LittleEndian(buffer, value);
        stream.Write(buffer);
    }

    private static void WriteBigEndianInt32(Stream stream, int value)
    {
        Span<byte> buffer = stackalloc byte[4];
        BinaryPrimitives.WriteInt32BigEndian(buffer, value);
        stream.Write(buffer);
    }

    private static byte[] ReadExactly(Stream stream, int count)
    {
        var buffer = new byte[count];
        stream.ReadExactly(buffer, 0, count);
        return buffer;
    }
}
