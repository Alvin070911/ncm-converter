namespace NcmConverter.App.Services;

public static class AppServices
{
    public static SettingsService Settings { get; private set; } = null!;

    public static HistoryService History { get; private set; } = null!;

    public static ConversionQueueService Queue { get; private set; } = null!;

    public static void Initialize()
    {
        Settings = new SettingsService();
        History = new HistoryService();
        Queue = new ConversionQueueService(Settings, History);
    }
}
