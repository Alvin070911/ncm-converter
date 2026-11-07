namespace NcmConverter.App.Models;

public enum ConversionStatus
{
    Pending,
    Analyzing,
    Queued,
    Converting,
    Succeeded,
    Failed,
    Cancelled,
    Skipped
}
