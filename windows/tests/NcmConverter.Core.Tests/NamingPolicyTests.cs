using NcmConverter.Core.Conversion;
using NcmConverter.Core.Ncm;

namespace NcmConverter.Core.Tests;

public sealed class NamingPolicyTests
{
    [Fact]
    public void BuildsNameFromMetadata()
    {
        var metadata = new NcmMetadata
        {
            Title = "那些年",
            Artists = new[] { "胡夏" }
        };

        var name = NamingPolicy.BuildFileName(metadata, @"C:\music\source.ncm", ".flac");

        Assert.Equal("那些年 - 胡夏.flac", name);
    }

    [Fact]
    public void FallsBackToSourceFileName()
    {
        var name = NamingPolicy.BuildFileName(NcmMetadata.Empty, @"C:\music\source.ncm", ".mp3");

        Assert.Equal("source.mp3", name);
    }

    [Fact]
    public void SanitizesInvalidCharacters()
    {
        var metadata = new NcmMetadata
        {
            Title = "A/B: C?",
            Artists = new[] { "D*E" }
        };

        var name = NamingPolicy.BuildFileName(metadata, "source.ncm", ".flac");

        Assert.DoesNotContain('/', name);
        Assert.DoesNotContain(':', name);
        Assert.DoesNotContain('?', name);
        Assert.DoesNotContain('*', name);
    }

    [Fact]
    public void RenamesWhenTargetExists()
    {
        var directory = Path.Combine(Path.GetTempPath(), $"ncm-name-{Guid.NewGuid():N}");
        Directory.CreateDirectory(directory);
        try
        {
            var first = NamingPolicy.ResolveOutputPath(directory, "song.flac", ConflictPolicy.Rename);
            File.WriteAllText(first, "x");

            var second = NamingPolicy.ResolveOutputPath(directory, "song.flac", ConflictPolicy.Rename);

            Assert.Equal(Path.Combine(directory, "song (1).flac"), second);
        }
        finally
        {
            Directory.Delete(directory, recursive: true);
        }
    }
}
