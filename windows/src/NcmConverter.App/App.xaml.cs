using System.Text;
using Microsoft.UI.Xaml;
using NcmConverter.App.Services;

namespace NcmConverter.App;

public partial class App : Application
{
    private static readonly string LogDirectory = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "NcmConverter");

    public App()
    {
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            WriteLog("AppDomain.UnhandledException", args.ExceptionObject?.ToString() ?? "unknown");
        UnhandledException += (_, args) =>
            WriteLog("Application.UnhandledException", args.Exception.ToString());

        try
        {
            InitializeComponent();
        }
        catch (Exception ex)
        {
            WriteLog("App.InitializeComponent", ex.ToString());
            throw;
        }

        try
        {
            AppServices.Initialize();
        }
        catch (Exception ex)
        {
            WriteLog("AppServices.Initialize", ex.ToString());
            throw;
        }
    }

    public static MainWindow? MainWindow { get; private set; }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        try
        {
            MainWindow = new MainWindow();
            MainWindow.Activate();
            WriteLog("OnLaunched", "window activated");
        }
        catch (Exception ex)
        {
            WriteLog("OnLaunched", ex.ToString());
            throw;
        }
    }

    private static void WriteLog(string stage, string message)
    {
        try
        {
            Directory.CreateDirectory(LogDirectory);
            File.AppendAllText(
                Path.Combine(LogDirectory, "startup.log"),
                $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] {stage}: {message}{Environment.NewLine}",
                Encoding.UTF8);
        }
        catch
        {
            // 日志写入失败不影响启动
        }
    }
}
