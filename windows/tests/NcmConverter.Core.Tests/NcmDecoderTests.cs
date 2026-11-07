using NcmConverter.Core.Audio;
using NcmConverter.Core.Ncm;

namespace NcmConverter.Core.Tests;

public sealed class NcmDecoderTests
{
    private readonly NcmDecoder _decoder = new();

    [Theory]
    [InlineData("Lia - 鳥の詩.ncm", AudioFormat.Flac, 275316396L,
        "d76a57ea2e50830240f08ed306ce2b0565b60cc5b32ef5e4592d1d4c8c94153b", "鳥の詩", "Lia")]
    [InlineData("Top Barry,INDEcompany - 隐藏相册.ncm", AudioFormat.Mp3, 2507666L,
        "37d883ad558cfbdda09fde14b01e74862b442df2c002f45f3672cd4256d42296", "隐藏相册", "Top Barry / INDEcompany")]
    [InlineData("いきものがかり - SAKURA.ncm", AudioFormat.Flac, 255726511L,
        "fc2356f29f020cbc7b2c193fa0bcb8806d1444820bc66877139f792aa99e41f7", "SAKURA", "いきものがかり")]
    [InlineData("胡夏 - 那些年.ncm", AudioFormat.Flac, 245280801L,
        "40a37c882e2fb3f3b431ff2d7b5e13c14999badc23d53b962786423fd175a545", "那些年", "胡夏")]
    public void DecodesSamplesWithExpectedPayload(
        string fileName,
        AudioFormat expectedFormat,
        long expectedAudioBytes,
        string expectedSha256,
        string expectedTitle,
        string expectedArtist)
    {
        var path = FindSample(fileName);
        if (path is null)
        {
            return;
        }

        var result = _decoder.ComputePayloadHash(path);

        Assert.Equal(expectedFormat, result.Format);
        Assert.Equal(expectedAudioBytes, result.AudioBytes);
        Assert.Equal(expectedSha256, result.Sha256);
        Assert.Equal(expectedTitle, result.Metadata.Title);
        Assert.Equal(expectedArtist, result.Metadata.ArtistText);
        Assert.Empty(result.Warnings);
    }

    [Fact]
    public void InspectReadsMetadataCoverAndOffsets()
    {
        var path = FindSample("Lia - 鳥の詩.ncm");
        if (path is null)
        {
            return;
        }

        var info = _decoder.Inspect(path);

        Assert.Equal(AudioFormat.Flac, info.ActualFormat);
        Assert.Equal(128, info.KeyLength);
        Assert.Equal(726, info.MetadataLength);
        Assert.Equal(0xC475B012u, info.Crc32);
        Assert.Equal(180694, info.AudioOffset);
        Assert.Equal(179809, info.CoverImageLength);
        Assert.Equal("image/jpeg", info.CoverMimeType);
        Assert.Equal("鳥の詩", info.Metadata.Title);
        Assert.Equal("Lia", info.Metadata.ArtistText);
        Assert.Equal(4999000, info.Metadata.Bitrate);
        Assert.Equal(368000L, info.Metadata.DurationMs);
    }

    [Fact]
    public void DecodeWritesMp3AndCover()
    {
        var path = FindSample("Top Barry,INDEcompany - 隐藏相册.ncm");
        if (path is null)
        {
            return;
        }

        var outputDirectory = Path.Combine(Path.GetTempPath(), $"ncm-test-{Guid.NewGuid():N}");
        try
        {
            var result = _decoder.Decode(path, outputDirectory, new DecodeOptions { SaveCover = true });

            Assert.True(File.Exists(result.OutputPath));
            Assert.EndsWith(".mp3", result.OutputPath, StringComparison.OrdinalIgnoreCase);
            Assert.Equal(AudioFormat.Mp3, result.Format);
            Assert.Equal(2507666L, result.AudioBytes);
            Assert.True(File.Exists(Path.Combine(outputDirectory, "cover.jpg")));
        }
        finally
        {
            if (Directory.Exists(outputDirectory))
            {
                Directory.Delete(outputDirectory, recursive: true);
            }
        }
    }

    [Fact]
    public void RejectsNonNcmFile()
    {
        var path = Path.Combine(Path.GetTempPath(), $"{Guid.NewGuid():N}.ncm");
        File.WriteAllText(path, "not an ncm file");
        try
        {
            var exception = Assert.Throws<NcmException>(() => _decoder.Inspect(path));
            Assert.Equal(NcmErrorCode.NotNcm, exception.Code);
        }
        finally
        {
            File.Delete(path);
        }
    }

    private static string? FindSample(string fileName)
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null)
        {
            var candidate = Path.Combine(directory.FullName, "testdata", fileName);
            if (File.Exists(candidate))
            {
                return candidate;
            }

            directory = directory.Parent;
        }

        return null;
    }
}
