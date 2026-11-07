using System.Text;
using NcmConverter.Core.Crypto;

namespace NcmConverter.Core.Ncm;

internal static class NcmKeyDecoder
{
    private const string KeyPrefix = "neteasecloudmusic";

    private static readonly byte[] CoreKey = Encoding.ASCII.GetBytes("hzHRAmso5kInbaxW");

    public static byte[] Decode(byte[] keyBlob)
    {
        try
        {
            var raw = CryptoUtil.Xor(keyBlob, 0x64);
            var plain = CryptoUtil.UnpadPkcs7(CryptoUtil.AesEcbDecrypt(raw, CoreKey));

            if (plain.Length < 17)
            {
                throw new NcmException(NcmErrorCode.KeyDecryptFailed, "密钥长度异常");
            }

            var prefix = Encoding.ASCII.GetString(plain, 0, 17);
            if (!string.Equals(prefix, KeyPrefix, StringComparison.Ordinal))
            {
                throw new NcmException(NcmErrorCode.KeyDecryptFailed, "密钥前缀不匹配，可能是不兼容的客户端版本");
            }

            var key = plain[17..];
            if (key.Length == 0)
            {
                throw new NcmException(NcmErrorCode.KeyDecryptFailed, "解密后的密钥为空");
            }

            return key;
        }
        catch (NcmException)
        {
            throw;
        }
        catch (Exception ex)
        {
            throw new NcmException(NcmErrorCode.KeyDecryptFailed, "无法解析 NCM 密钥", ex);
        }
    }
}
