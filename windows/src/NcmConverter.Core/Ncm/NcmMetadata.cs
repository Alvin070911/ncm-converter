namespace NcmConverter.Core.Ncm;

public sealed class NcmMetadata
{
    public string? MusicId { get; init; }

    public string? Title { get; init; }

    public IReadOnlyList<string> Artists { get; init; } = Array.Empty<string>();

    public string? Album { get; init; }

    public int? Bitrate { get; init; }

    public long? DurationMs { get; init; }

    public string? DeclaredFormat { get; init; }

    public string? CoverUrl { get; init; }

    public string ArtistText => string.Join(" / ", Artists);

    public static NcmMetadata Empty { get; } = new();
}
