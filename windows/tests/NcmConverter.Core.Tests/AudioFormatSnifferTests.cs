using NcmConverter.Core.Audio;

namespace NcmConverter.Core.Tests;

public sealed class AudioFormatSnifferTests
{
    [Fact]
    public void DetectsFlac()
    {
        Assert.Equal(AudioFormat.Flac, AudioFormatSniffer.Detect("fLaC"u8.ToArray()));
    }

    [Fact]
    public void DetectsMp3ById3()
    {
        Assert.Equal(AudioFormat.Mp3, AudioFormatSniffer.Detect("ID3"u8.ToArray()));
    }

    [Fact]
    public void DetectsMp3ByFrameSync()
    {
        Assert.Equal(AudioFormat.Mp3, AudioFormatSniffer.Detect(new byte[] { 0xFF, 0xFB, 0x90, 0x00 }));
    }

    [Fact]
    public void DetectsM4a()
    {
        var bytes = new byte[12];
        "ftyp"u8.CopyTo(bytes.AsSpan(4));
        Assert.Equal(AudioFormat.M4a, AudioFormatSniffer.Detect(bytes));
    }

    [Fact]
    public void DetectsUnknown()
    {
        Assert.Equal(AudioFormat.Unknown, AudioFormatSniffer.Detect(new byte[] { 0x01, 0x02, 0x03, 0x04 }));
    }
}
