using NcmConverter.Core.Audio;

namespace NcmConverter.Core.Ncm;

public sealed class DecodeProgress
{
    public DecodeProgress(long processedBytes, long totalBytes)
    {
        ProcessedBytes = processedBytes;
        TotalBytes = totalBytes;
    }

    public long ProcessedBytes { get; }

    public long TotalBytes { get; }

    public double Percent => TotalBytes <= 0 ? 0 : (double)ProcessedBytes / TotalBytes * 100;
}

public sealed class DecodeResult
{
    public required string OutputPath { get; init; }

    public required AudioFormat Format { get; init; }

    public required long AudioBytes { get; init; }

    public required string Sha256 { get; init; }

    public NcmMetadata Metadata { get; init; } = NcmMetadata.Empty;

    public IReadOnlyList<string> Warnings { get; init; } = Array.Empty<string>();
}

public sealed class PayloadHashResult
{
    public required AudioFormat Format { get; init; }

    public required long AudioBytes { get; init; }

    public required string Sha256 { get; init; }

    public NcmMetadata Metadata { get; init; } = NcmMetadata.Empty;

    public IReadOnlyList<string> Warnings { get; init; } = Array.Empty<string>();
}
