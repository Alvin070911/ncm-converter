using System.Buffers.Binary;
using System.Text;
using NcmConverter.Core.Audio;

namespace NcmConverter.Core.Ncm;

internal sealed class NcmHeader
{
    public long FileSize { get; init; }

    public int KeyLength { get; init; }

    public int KeyBytes { get; init; }

    public int MetadataLength { get; init; }

    public uint Crc32 { get; init; }

    public byte ImageVersion { get; init; }

    public uint CoverFrameLength { get; init; }

    public int CoverImageLength { get; init; }

    public long AudioOffset { get; init; }

    public long AudioLength { get; init; }

    public byte[] Key { get; init; } = Array.Empty<byte>();

    public NcmMetadata Metadata { get; init; } = NcmMetadata.Empty;

    public byte[] CoverBytes { get; init; } = Array.Empty<byte>();

    public string? CoverMimeType { get; init; }

    public List<string> Warnings { get; init; } = new();
}

internal static class NcmContainerReader
{
    private const int MaxMetadataLength = 32 * 1024 * 1024;
    private const int MaxCoverLength = 20 * 1024 * 1024;
    private const string Magic = "CTENFDAM";

    public static NcmHeader Read(string path)
    {
        try
        {
            using var stream = new FileStream(
                path, FileMode.Open, FileAccess.Read, FileShare.Read, 8192, FileOptions.RandomAccess);

            var fileSize = stream.Length;
            if (fileSize < 8)
            {
                throw new NcmException(NcmErrorCode.Truncated, "文件过小，不是完整的 NCM 文件");
            }

            var magic = ReadExactly(stream, 8);
            if (Encoding.ASCII.GetString(magic) != Magic)
            {
                throw new NcmException(NcmErrorCode.NotNcm, "不是有效的 NCM 文件（文件头不匹配）");
            }

            ReadExactly(stream, 2); // version

            var keyLength = checked((int)ReadUInt32(stream));
            if (keyLength <= 0 || keyLength > 4096 || keyLength > fileSize - stream.Position)
            {
                throw new NcmException(NcmErrorCode.Truncated, "密钥段长度异常");
            }

            var keyBlob = ReadExactly(stream, keyLength);

            var metadataLength = checked((int)ReadUInt32(stream));
            if (metadataLength < 0 || metadataLength > MaxMetadataLength ||
                metadataLength > fileSize - stream.Position)
            {
                throw new NcmException(NcmErrorCode.Truncated, "元数据段长度异常");
            }

            var metadataBlob = ReadExactly(stream, metadataLength);

            var crc32 = ReadUInt32(stream);
            var imageVersion = ReadExactly(stream, 1)[0];
            var coverFrameLength = ReadUInt32(stream);
            var coverImageLength = ReadUInt32(stream);

            if (coverFrameLength < coverImageLength || coverFrameLength > MaxCoverLength)
            {
                throw new NcmException(NcmErrorCode.Truncated, "封面长度字段异常");
            }

            var coverLength = checked((int)coverImageLength);
            if (coverLength > 0 && coverLength > fileSize - stream.Position)
            {
                throw new NcmException(NcmErrorCode.Truncated, "封面数据超出文件范围");
            }

            var coverBytes = coverLength > 0 ? ReadExactly(stream, coverLength) : Array.Empty<byte>();

            var padding = coverFrameLength - coverImageLength;
            if (padding > 0)
            {
                if (stream.Position + padding > fileSize)
                {
                    throw new NcmException(NcmErrorCode.Truncated, "封面填充长度异常");
                }

                stream.Seek(padding, SeekOrigin.Current);
            }

            var audioOffset = stream.Position;
            var audioLength = fileSize - audioOffset;
            if (audioLength <= 0)
            {
                throw new NcmException(NcmErrorCode.Truncated, "缺少音频数据");
            }

            var warnings = new List<string>();
            var key = NcmKeyDecoder.Decode(keyBlob);

            NcmMetadata metadata;
            try
            {
                metadata = NcmMetadataDecoder.Decode(metadataBlob);
            }
            catch (NcmException)
            {
                metadata = NcmMetadata.Empty;
                warnings.Add("元数据解析失败，将仅输出音频");
            }

            string? coverMimeType = null;
            if (coverBytes.Length > 0)
            {
                coverMimeType = AudioFormatSniffer.DetectCoverMimeType(coverBytes);
                if (coverMimeType is null)
                {
                    warnings.Add("封面格式无法识别，将按原始数据嵌入");
                }
            }

            return new NcmHeader
            {
                FileSize = fileSize,
                KeyLength = keyLength,
                KeyBytes = key.Length,
                MetadataLength = metadataLength,
                Crc32 = crc32,
                ImageVersion = imageVersion,
                CoverFrameLength = coverFrameLength,
                CoverImageLength = coverLength,
                AudioOffset = audioOffset,
                AudioLength = audioLength,
                Key = key,
                Metadata = metadata,
                CoverBytes = coverBytes,
                CoverMimeType = coverMimeType,
                Warnings = warnings
            };
        }
        catch (NcmException)
        {
            throw;
        }
        catch (EndOfStreamException ex)
        {
            throw new NcmException(NcmErrorCode.Truncated, "文件不完整或已损坏", ex);
        }
        catch (UnauthorizedAccessException ex)
        {
            throw new NcmException(NcmErrorCode.OutputPermissionDenied, "没有读取该文件的权限", ex);
        }
        catch (IOException ex)
        {
            throw new NcmException(NcmErrorCode.IoError, "读取 NCM 文件失败", ex);
        }
    }

    private static byte[] ReadExactly(Stream stream, int count)
    {
        var buffer = new byte[count];
        stream.ReadExactly(buffer, 0, count);
        return buffer;
    }

    private static uint ReadUInt32(Stream stream) =>
        BinaryPrimitives.ReadUInt32LittleEndian(ReadExactly(stream, 4));
}
