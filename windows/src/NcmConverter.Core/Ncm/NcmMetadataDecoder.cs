using System.Text;
using System.Text.Json;
using NcmConverter.Core.Crypto;

namespace NcmConverter.Core.Ncm;

internal static class NcmMetadataDecoder
{
    private const string MetadataPrefix = "163 key(Don't modify):";

    private static readonly byte[] ModifyKey =
    {
        0x23, 0x31, 0x34, 0x6C, 0x6A, 0x6B, 0x5F, 0x21,
        0x5C, 0x5D, 0x26, 0x30, 0x55, 0x3C, 0x27, 0x28
    };

    public static NcmMetadata Decode(byte[] metaBlob)
    {
        try
        {
            var raw = CryptoUtil.Xor(metaBlob, 0x63);
            if (raw.Length < 22)
            {
                throw new NcmException(NcmErrorCode.MetadataDecryptFailed, "元数据长度异常");
            }

            var prefix = Encoding.ASCII.GetString(raw, 0, 22);
            if (!string.Equals(prefix, MetadataPrefix, StringComparison.Ordinal))
            {
                throw new NcmException(NcmErrorCode.MetadataDecryptFailed, "元数据前缀不匹配");
            }

            var base64 = Encoding.ASCII.GetString(raw, 22, raw.Length - 22).Trim();
            var cipher = Convert.FromBase64String(base64);
            var plain = CryptoUtil.UnpadPkcs7(CryptoUtil.AesEcbDecrypt(cipher, ModifyKey));
            var text = Encoding.UTF8.GetString(plain);

            if (!text.StartsWith("music:", StringComparison.Ordinal))
            {
                throw new NcmException(NcmErrorCode.MetadataDecryptFailed, "元数据明文前缀不匹配");
            }

            return Parse(text[6..]);
        }
        catch (NcmException)
        {
            throw;
        }
        catch (Exception ex)
        {
            throw new NcmException(NcmErrorCode.MetadataDecryptFailed, "无法解析 NCM 元数据", ex);
        }
    }

    private static NcmMetadata Parse(string json)
    {
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;

        var artists = new List<string>();
        if (root.TryGetProperty("artist", out var artistElement) && artistElement.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in artistElement.EnumerateArray())
            {
                if (item.ValueKind == JsonValueKind.Array)
                {
                    var values = item.EnumerateArray()
                        .Select(value => value.ValueKind == JsonValueKind.String ? value.GetString() : value.ToString())
                        .Where(value => !string.IsNullOrWhiteSpace(value))
                        .ToList();

                    if (values.Count >= 2)
                    {
                        // 当前格式为 [name, id]；兼容 [id, name] 的异常形态
                        if (IsAllDigits(values[0]!) && !IsAllDigits(values[1]!))
                        {
                            artists.Add(values[1]!);
                        }
                        else
                        {
                            artists.Add(values[0]!);
                        }
                    }
                    else if (values.Count == 1)
                    {
                        artists.Add(values[0]!);
                    }
                }
                else if (item.ValueKind == JsonValueKind.String)
                {
                    var value = item.GetString();
                    if (!string.IsNullOrWhiteSpace(value))
                    {
                        artists.Add(value);
                    }
                }
            }
        }

        return new NcmMetadata
        {
            MusicId = GetString(root, "musicId"),
            Title = GetString(root, "musicName"),
            Artists = artists,
            Album = GetString(root, "album"),
            Bitrate = GetInt(root, "bitrate"),
            DurationMs = GetLong(root, "duration"),
            DeclaredFormat = GetString(root, "format"),
            CoverUrl = GetString(root, "albumPic") ?? GetString(root, "cover")
        };
    }

    private static string? GetString(JsonElement root, string propertyName)
    {
        if (!root.TryGetProperty(propertyName, out var value))
        {
            return null;
        }

        return value.ValueKind switch
        {
            JsonValueKind.String => value.GetString(),
            JsonValueKind.Number => value.ToString(),
            _ => null
        };
    }

    private static int? GetInt(JsonElement root, string propertyName)
    {
        if (!root.TryGetProperty(propertyName, out var value))
        {
            return null;
        }

        if (value.ValueKind == JsonValueKind.Number && value.TryGetInt32(out var number))
        {
            return number;
        }

        if (value.ValueKind == JsonValueKind.String && int.TryParse(value.GetString(), out number))
        {
            return number;
        }

        return null;
    }

    private static long? GetLong(JsonElement root, string propertyName)
    {
        if (!root.TryGetProperty(propertyName, out var value))
        {
            return null;
        }

        if (value.ValueKind == JsonValueKind.Number && value.TryGetInt64(out var number))
        {
            return number;
        }

        if (value.ValueKind == JsonValueKind.String && long.TryParse(value.GetString(), out number))
        {
            return number;
        }

        return null;
    }

    private static bool IsAllDigits(string value) => value.Length > 0 && value.All(char.IsDigit);
}
