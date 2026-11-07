using System.Security.Cryptography;
using NcmConverter.Core.Audio;
using NcmConverter.Core.Conversion;
using NcmConverter.Core.Tagging;

namespace NcmConverter.Core.Ncm;

public sealed class NcmDecoder
{
    private const int BufferSize = 64 * 1024;
    private const int SniffLength = 32;

    public NcmFileInfo Inspect(string sourcePath, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var header = NcmContainerReader.Read(sourcePath);

        AudioFormat? actualFormat = null;
        var warnings = header.Warnings;
        try
        {
            actualFormat = DetectFormat(sourcePath, header);
        }
        catch (Exception ex) when (ex is not NcmException)
        {
            warnings.Add("无法识别音频格式，转换时可能失败");
        }

        return new NcmFileInfo
        {
            FilePath = sourcePath,
            FileSize = header.FileSize,
            KeyLength = header.KeyLength,
            KeyBytes = header.KeyBytes,
            MetadataLength = header.MetadataLength,
            Crc32 = header.Crc32,
            ImageVersion = header.ImageVersion,
            CoverFrameLength = header.CoverFrameLength,
            CoverImageLength = header.CoverImageLength,
            CoverMimeType = header.CoverMimeType,
            CoverBytes = header.CoverBytes,
            AudioOffset = header.AudioOffset,
            AudioLength = header.AudioLength,
            Metadata = header.Metadata,
            ActualFormat = actualFormat,
            Warnings = warnings
        };
    }

    public DecodeResult Decode(
        string sourcePath,
        string? outputDirectory = null,
        DecodeOptions? options = null,
        IProgress<DecodeProgress>? progress = null,
        CancellationToken cancellationToken = default)
    {
        options ??= new DecodeOptions();
        var header = NcmContainerReader.Read(sourcePath);
        var directory = outputDirectory ?? Path.GetDirectoryName(Path.GetFullPath(sourcePath)) ?? ".";
        var warnings = header.Warnings;
        var tempPath = Path.Combine(directory, $".ncm-{Guid.NewGuid():N}.tmp");

        try
        {
            Directory.CreateDirectory(directory);
            progress?.Report(new DecodeProgress(0, header.AudioLength));

            using var input = OpenAudioStream(sourcePath, header);
            AudioFormat format;
            string sha256;
            long processedBytes;

            using (var output = new FileStream(
                       tempPath, FileMode.CreateNew, FileAccess.Write, FileShare.None, BufferSize, FileOptions.SequentialScan))
            {
                (format, sha256, processedBytes) = DecryptStream(
                    input, header, output, progress, cancellationToken);
            }

            if (format == AudioFormat.Unknown)
            {
                throw new NcmException(NcmErrorCode.UnsupportedAudio, "内部音频格式暂不支持");
            }

            var extension = format.Extension();
            var fileName = BuildOutputFileName(options, header, sourcePath, extension);
            var finalPath = NamingPolicy.ResolveOutputPath(directory, fileName, options.ConflictPolicy);

            if (File.Exists(finalPath))
            {
                File.Delete(finalPath);
            }

            File.Move(tempPath, finalPath);

            if (options.WriteTags && format.SupportsTags())
            {
                warnings.AddRange(TagWriter.Write(
                    finalPath, format, header.Metadata, header.CoverBytes, header.CoverMimeType));
            }

            if (options.SaveCover && header.CoverBytes.Length > 0)
            {
                TrySaveCover(directory, header, warnings);
            }

            return new DecodeResult
            {
                OutputPath = finalPath,
                Format = format,
                AudioBytes = processedBytes,
                Sha256 = sha256,
                Metadata = header.Metadata,
                Warnings = warnings
            };
        }
        catch (OperationCanceledException)
        {
            throw new NcmException(NcmErrorCode.Cancelled, "转换已取消");
        }
        catch (NcmException)
        {
            throw;
        }
        catch (UnauthorizedAccessException ex)
        {
            throw new NcmException(NcmErrorCode.OutputPermissionDenied, "无法写入输出目录", ex);
        }
        catch (IOException ex)
        {
            if (ex.HResult == unchecked((int)0x80070070))
            {
                throw new NcmException(NcmErrorCode.DiskFull, "目标磁盘空间不足", ex);
            }

            throw new NcmException(NcmErrorCode.IoError, "读取或写入失败", ex);
        }
        catch (Exception ex)
        {
            throw new NcmException(NcmErrorCode.Unknown, "转换失败", ex);
        }
        finally
        {
            if (File.Exists(tempPath))
            {
                try
                {
                    File.Delete(tempPath);
                }
                catch
                {
                    // 清理失败不覆盖原始异常
                }
            }
        }
    }

    public PayloadHashResult ComputePayloadHash(
        string sourcePath,
        IProgress<DecodeProgress>? progress = null,
        CancellationToken cancellationToken = default)
    {
        var header = NcmContainerReader.Read(sourcePath);
        progress?.Report(new DecodeProgress(0, header.AudioLength));

        try
        {
            using var input = OpenAudioStream(sourcePath, header);
            var (format, sha256, processedBytes) = DecryptStream(
                input, header, null, progress, cancellationToken);

            if (format == AudioFormat.Unknown)
            {
                throw new NcmException(NcmErrorCode.UnsupportedAudio, "内部音频格式暂不支持");
            }

            return new PayloadHashResult
            {
                Format = format,
                AudioBytes = processedBytes,
                Sha256 = sha256,
                Metadata = header.Metadata,
                Warnings = header.Warnings
            };
        }
        catch (OperationCanceledException)
        {
            throw new NcmException(NcmErrorCode.Cancelled, "转换已取消");
        }
        catch (NcmException)
        {
            throw;
        }
        catch (IOException ex)
        {
            throw new NcmException(NcmErrorCode.IoError, "读取文件失败", ex);
        }
        catch (Exception ex)
        {
            throw new NcmException(NcmErrorCode.Unknown, "计算音频哈希失败", ex);
        }
    }

    private static FileStream OpenAudioStream(string sourcePath, NcmHeader header)
    {
        var stream = new FileStream(
            sourcePath, FileMode.Open, FileAccess.Read, FileShare.Read, BufferSize, FileOptions.SequentialScan);
        stream.Seek(header.AudioOffset, SeekOrigin.Begin);
        return stream;
    }

    private static AudioFormat DetectFormat(string sourcePath, NcmHeader header)
    {
        using var stream = new FileStream(
            sourcePath, FileMode.Open, FileAccess.Read, FileShare.Read, 64, FileOptions.RandomAccess);
        stream.Seek(header.AudioOffset, SeekOrigin.Begin);
        var length = (int)Math.Min(SniffLength, header.AudioLength);
        var buffer = new byte[length];
        var read = stream.Read(buffer, 0, length);
        if (read <= 0)
        {
            return AudioFormat.Unknown;
        }

        var plain = new byte[read];
        NcmKeyBox.Create(header.Key).Decrypt(buffer.AsSpan(0, read), plain, 0);
        return AudioFormatSniffer.Detect(plain);
    }

    private static (AudioFormat Format, string Sha256, long ProcessedBytes) DecryptStream(
        FileStream input,
        NcmHeader header,
        FileStream? output,
        IProgress<DecodeProgress>? progress,
        CancellationToken cancellationToken)
    {
        var keyBox = NcmKeyBox.Create(header.Key);
        var cipherBuffer = new byte[BufferSize];
        var plainBuffer = new byte[BufferSize];
        var format = AudioFormat.Unknown;
        var processedBytes = 0L;

        using var hasher = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);

        while (true)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var read = input.Read(cipherBuffer, 0, cipherBuffer.Length);
            if (read <= 0)
            {
                break;
            }

            keyBox.Decrypt(cipherBuffer.AsSpan(0, read), plainBuffer.AsSpan(0, read), processedBytes);

            if (format == AudioFormat.Unknown)
            {
                format = AudioFormatSniffer.Detect(plainBuffer.AsSpan(0, Math.Min(read, SniffLength)));
                if (format == AudioFormat.Unknown)
                {
                    return (format, string.Empty, processedBytes);
                }
            }

            output?.Write(plainBuffer, 0, read);
            hasher.AppendData(plainBuffer, 0, read);
            processedBytes += read;
            progress?.Report(new DecodeProgress(processedBytes, header.AudioLength));
        }

        cancellationToken.ThrowIfCancellationRequested();
        var sha256 = Convert.ToHexString(hasher.GetHashAndReset()).ToLowerInvariant();
        return (format, sha256, processedBytes);
    }

    private static string BuildOutputFileName(
        DecodeOptions options,
        NcmHeader header,
        string sourcePath,
        string extension)
    {
        if (string.IsNullOrWhiteSpace(options.OutputFileName))
        {
            return NamingPolicy.BuildFileName(header.Metadata, sourcePath, extension, options.NamingMode);
        }

        var requested = Path.GetFileName(options.OutputFileName.Trim());
        var requestedStem = Path.GetFileNameWithoutExtension(requested);
        var requestedExtension = Path.GetExtension(requested);
        var stem = NamingPolicy.Sanitize(requestedStem);
        if (string.IsNullOrWhiteSpace(stem))
        {
            stem = "未命名";
        }

        return stem + (string.IsNullOrWhiteSpace(requestedExtension) ? extension : requestedExtension);
    }

    private static void TrySaveCover(string directory, NcmHeader header, List<string> warnings)
    {
        try
        {
            var extension = header.CoverMimeType == "image/png" ? ".png" : ".jpg";
            var path = NamingPolicy.ResolveOutputPath(
                directory, "cover" + extension, ConflictPolicy.Rename);
            File.WriteAllBytes(path, header.CoverBytes);
        }
        catch (Exception)
        {
            warnings.Add("封面单独导出失败");
        }
    }
}
