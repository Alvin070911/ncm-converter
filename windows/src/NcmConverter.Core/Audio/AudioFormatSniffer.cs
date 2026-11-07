using System.Text;

namespace NcmConverter.Core.Audio;

public static class AudioFormatSniffer
{
    public static AudioFormat Detect(ReadOnlySpan<byte> head)
    {
        if (head.Length >= 4 && head[..4].SequenceEqual("fLaC"u8))
        {
            return AudioFormat.Flac;
        }

        if (head.Length >= 3 && head[..3].SequenceEqual("ID3"u8))
        {
            return AudioFormat.Mp3;
        }

        if (head.Length >= 2 && head[0] == 0xFF && (head[1] & 0xE0) == 0xE0)
        {
            return AudioFormat.Mp3;
        }

        if (head.Length >= 8 && head[4..8].SequenceEqual("ftyp"u8))
        {
            return AudioFormat.M4a;
        }

        if (head.Length >= 4 && head[..4].SequenceEqual("OggS"u8))
        {
            return AudioFormat.Ogg;
        }

        if (head.Length >= 12 && head[..4].SequenceEqual("RIFF"u8) && head[8..12].SequenceEqual("WAVE"u8))
        {
            return AudioFormat.Wav;
        }

        return AudioFormat.Unknown;
    }

    public static string? DetectCoverMimeType(ReadOnlySpan<byte> cover)
    {
        if (cover.Length >= 3 && cover[0] == 0xFF && cover[1] == 0xD8 && cover[2] == 0xFF)
        {
            return "image/jpeg";
        }

        if (cover.Length >= 8 &&
            cover[..8].SequenceEqual(new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }))
        {
            return "image/png";
        }

        return null;
    }
}
