using NcmConverter.Core.Conversion;

namespace NcmConverter.Core.Ncm;

public sealed class DecodeOptions
{
    public string? OutputFileName { get; init; }

    public ConflictPolicy ConflictPolicy { get; init; } = ConflictPolicy.Rename;

    public bool SaveCover { get; init; }

    public bool WriteTags { get; init; } = true;

    public NamingMode NamingMode { get; init; } = NamingMode.Metadata;
}
