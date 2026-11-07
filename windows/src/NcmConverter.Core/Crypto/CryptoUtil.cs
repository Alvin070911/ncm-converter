using System.Security.Cryptography;

namespace NcmConverter.Core.Crypto;

internal static class CryptoUtil
{
    public static byte[] Xor(ReadOnlySpan<byte> data, byte mask)
    {
        var result = new byte[data.Length];
        for (var i = 0; i < data.Length; i++)
        {
            result[i] = (byte)(data[i] ^ mask);
        }

        return result;
    }

    public static byte[] AesEcbDecrypt(ReadOnlySpan<byte> data, byte[] key)
    {
        if (data.Length == 0 || data.Length % 16 != 0)
        {
            throw new ArgumentException("AES 密文长度必须是 16 的倍数", nameof(data));
        }

        using var aes = Aes.Create();
        aes.Mode = CipherMode.ECB;
        aes.Padding = PaddingMode.None;
        aes.Key = key;

        using var decryptor = aes.CreateDecryptor();
        var input = data.ToArray();
        return decryptor.TransformFinalBlock(input, 0, input.Length);
    }

    public static byte[] UnpadPkcs7(ReadOnlySpan<byte> data)
    {
        if (data.Length == 0)
        {
            return Array.Empty<byte>();
        }

        var pad = data[^1];
        if (pad is < 1 or > 16 || pad > data.Length)
        {
            return data.ToArray();
        }

        return data[..^pad].ToArray();
    }
}
