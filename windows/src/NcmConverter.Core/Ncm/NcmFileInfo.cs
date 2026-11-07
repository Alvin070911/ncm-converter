using NcmConverter.Core.Audio;

namespace NcmConverter.Core.Ncm;

public sealed class NcmFileInfo
{
    public required string FilePath { get; init; }

    public AudioFormat? ActualFormat { get; init; }

    public long FileSize { get; init; }

    public int KeyLength { get; init; }

    public int KeyBytes { get; init; }

    public int MetadataLength { get; init; }

    public uint Crc32 { get; init; }

    public byte ImageVersion { get; init; }

    public uint CoverFrameLength { get; init; }

    public int CoverImageLength { get; init; }

    public string? CoverMimeType { get; init; }

    public byte[] CoverBytes { get; init; } = Array.Empty<byte>();

    public long AudioOffset { get; init; }

    public long AudioLength { get; init; }

    public NcmMetadata Metadata { get; init; } = NcmMetadata.Empty;

    public IReadOnlyList<string> Warnings { get; init; } = Array.Empty<string>();
}
