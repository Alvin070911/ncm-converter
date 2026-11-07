namespace NcmConverter.Core.Audio;

public enum AudioFormat
{
    Unknown,
    Flac,
    Mp3,
    M4a,
    Ogg,
    Wav
}

public static class AudioFormatExtensions
{
    public static string Extension(this AudioFormat format) => format switch
    {
        AudioFormat.Flac => ".flac",
        AudioFormat.Mp3 => ".mp3",
        AudioFormat.M4a => ".m4a",
        AudioFormat.Ogg => ".ogg",
        AudioFormat.Wav => ".wav",
        _ => string.Empty
    };

    public static string DisplayName(this AudioFormat format) => format switch
    {
        AudioFormat.Flac => "FLAC",
        AudioFormat.Mp3 => "MP3",
        AudioFormat.M4a => "M4A",
        AudioFormat.Ogg => "OGG",
        AudioFormat.Wav => "WAV",
        _ => "未知"
    };

    public static bool SupportsTags(this AudioFormat format) =>
        format is AudioFormat.Flac or AudioFormat.Mp3;
}
