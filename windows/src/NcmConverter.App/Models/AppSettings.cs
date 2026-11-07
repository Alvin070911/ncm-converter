namespace NcmConverter.App.Models;

public sealed class AppSettings
{
    public string Theme { get; set; } = "System";

    public string? OutputDirectory { get; set; }

    public string NamingMode { get; set; } = "Metadata";

    public string ConflictPolicy { get; set; } = "Rename";

    public bool SaveCoverSeparately { get; set; }

    public int Concurrency { get; set; } = 2;

    public bool RecordHistory { get; set; } = true;
}
