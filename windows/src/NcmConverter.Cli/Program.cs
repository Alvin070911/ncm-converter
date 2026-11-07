using System.Globalization;
using System.Text;
using NcmConverter.Core.Audio;
using NcmConverter.Core.Ncm;

Console.OutputEncoding = Encoding.UTF8;

if (args.Length == 0)
{
    PrintUsage();
    return 0;
}

var decoder = new NcmDecoder();

try
{
    switch (args[0].ToLowerInvariant())
    {
        case "inspect":
            return Inspect(args);
        case "decode":
            return Decode(args);
        case "verify":
            return Verify(args);
        case "verify-all":
            return VerifyAll(args);
        default:
            PrintUsage();
            return 1;
    }
}
catch (NcmException ex)
{
    Console.Error.WriteLine($"错误 [{ex.Code}]：{ex.Message}");
    if (ex.InnerException is not null)
    {
        Console.Error.WriteLine($"详情：{ex.InnerException.Message}");
    }

    return 1;
}
catch (Exception ex)
{
    Console.Error.WriteLine($"未处理异常：{ex.Message}");
    return 1;
}

int Inspect(string[] arguments)
{
    if (arguments.Length < 2)
    {
        PrintUsage();
        return 1;
    }

    var info = decoder.Inspect(arguments[1]);
    var metadata = info.Metadata;

    Console.WriteLine($"文件        : {Path.GetFileName(info.FilePath)}");
    Console.WriteLine($"大小        : {FormatSize(info.FileSize)}");
    Console.WriteLine($"真实格式    : {info.ActualFormat?.DisplayName() ?? "未知"}");
    Console.WriteLine($"声明格式    : {metadata.DeclaredFormat ?? "-"}");
    Console.WriteLine($"歌名        : {metadata.Title ?? "-"}");
    Console.WriteLine($"歌手        : {metadata.ArtistText}");
    Console.WriteLine($"专辑        : {metadata.Album ?? "-"}");
    Console.WriteLine($"码率        : {metadata.Bitrate?.ToString(CultureInfo.InvariantCulture) ?? "-"}");
    Console.WriteLine($"时长        : {FormatDuration(metadata.DurationMs)}");
    Console.WriteLine($"封面        : {info.CoverMimeType ?? "无"} / {FormatSize(info.CoverImageLength)}");
    Console.WriteLine($"音频偏移    : {info.AudioOffset}");
    Console.WriteLine($"音频大小    : {FormatSize(info.AudioLength)}");
    Console.WriteLine($"Key         : {info.KeyLength} B / {info.KeyBytes} B");
    Console.WriteLine($"元数据      : {info.MetadataLength} B");
    Console.WriteLine($"CRC32       : 0x{info.Crc32:X8}");
    Console.WriteLine($"ImageVer    : {info.ImageVersion}");

    foreach (var warning in info.Warnings)
    {
        Console.WriteLine($"警告        : {warning}");
    }

    return 0;
}

int Decode(string[] arguments)
{
    if (arguments.Length < 2)
    {
        PrintUsage();
        return 1;
    }

    var input = arguments[1];
    var outputDirectory = arguments.Length >= 3 && !arguments[2].StartsWith("--", StringComparison.Ordinal)
        ? arguments[2]
        : null;
    var saveCover = arguments.Any(argument => argument == "--cover");

    var result = decoder.Decode(
        input,
        outputDirectory,
        new DecodeOptions { SaveCover = saveCover },
        new ConsoleProgress());

    Console.WriteLine($"输出        : {result.OutputPath}");
    Console.WriteLine($"格式        : {result.Format.DisplayName()}");
    Console.WriteLine($"音频大小    : {FormatSize(result.AudioBytes)}");
    Console.WriteLine($"SHA-256     : {result.Sha256}");

    foreach (var warning in result.Warnings)
    {
        Console.WriteLine($"警告        : {warning}");
    }

    return 0;
}

int Verify(string[] arguments)
{
    if (arguments.Length < 2)
    {
        PrintUsage();
        return 1;
    }

    var result = decoder.ComputePayloadHash(arguments[1], new ConsoleProgress());
    Console.WriteLine($"{result.Format.DisplayName()}\t{result.AudioBytes}\t{result.Sha256}\t{Path.GetFileName(arguments[1])}");
    return 0;
}

int VerifyAll(string[] arguments)
{
    if (arguments.Length < 2)
    {
        PrintUsage();
        return 1;
    }

    var directory = arguments[1];
    var files = Directory
        .EnumerateFiles(directory, "*.ncm", SearchOption.TopDirectoryOnly)
        .OrderBy(file => file, StringComparer.OrdinalIgnoreCase)
        .ToList();

    if (files.Count == 0)
    {
        Console.Error.WriteLine("目录中没有 .ncm 文件");
        return 1;
    }

    var failures = 0;
    foreach (var file in files)
    {
        try
        {
            var result = decoder.ComputePayloadHash(file);
            Console.WriteLine($"{result.Format.DisplayName()}\t{result.AudioBytes}\t{result.Sha256}\t{Path.GetFileName(file)}");
        }
        catch (NcmException ex)
        {
            failures++;
            Console.Error.WriteLine($"失败 [{ex.Code}]\t{Path.GetFileName(file)}\t{ex.Message}");
        }
    }

    return failures == 0 ? 0 : 1;
}

void PrintUsage()
{
    Console.WriteLine("用法：");
    Console.WriteLine("  ncm-converter inspect <文件.ncm>");
    Console.WriteLine("  ncm-converter decode <文件.ncm> [输出目录] [--cover]");
    Console.WriteLine("  ncm-converter verify <文件.ncm>");
    Console.WriteLine("  ncm-converter verify-all <目录>");
}

static string FormatSize(long bytes)
{
    if (bytes < 1024)
    {
        return $"{bytes} B";
    }

    if (bytes < 1024 * 1024)
    {
        return $"{bytes / 1024.0:F1} KB";
    }

    if (bytes < 1024L * 1024 * 1024)
    {
        return $"{bytes / 1024.0 / 1024:F2} MB";
    }

    return $"{bytes / 1024.0 / 1024 / 1024:F2} GB";
}

static string FormatDuration(long? milliseconds)
{
    if (milliseconds is null or <= 0)
    {
        return "-";
    }

    var time = TimeSpan.FromMilliseconds(milliseconds.Value);
    return time.TotalHours >= 1
        ? $"{(int)time.TotalHours}:{time.Minutes:D2}:{time.Seconds:D2}"
        : $"{time.Minutes:D2}:{time.Seconds:D2}";
}

sealed class ConsoleProgress : IProgress<DecodeProgress>
{
    private long _lastPercent = -1;

    public void Report(DecodeProgress value)
    {
        var percent = (long)Math.Round(value.Percent);
        if (percent == _lastPercent)
        {
            return;
        }

        _lastPercent = percent;
        Console.WriteLine($"进度        : {percent}%");
    }
}
