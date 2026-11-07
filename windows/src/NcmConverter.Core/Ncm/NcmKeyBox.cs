namespace NcmConverter.Core.Ncm;

public sealed class NcmKeyBox
{
    private readonly byte[] _box;

    private NcmKeyBox(byte[] box)
    {
        _box = box;
    }

    public static NcmKeyBox Create(byte[] key)
    {
        if (key.Length == 0)
        {
            throw new ArgumentException("key 不能为空", nameof(key));
        }

        var box = new int[256];
        for (var i = 0; i < box.Length; i++)
        {
            box[i] = i;
        }

        var last = 0;
        var keyOffset = 0;
        for (var i = 0; i < 256; i++)
        {
            var swap = box[i];
            last = (swap + last + key[keyOffset]) & 0xFF;
            keyOffset = (keyOffset + 1) % key.Length;
            box[i] = box[last];
            box[last] = swap;
        }

        var bytes = new byte[256];
        for (var i = 0; i < bytes.Length; i++)
        {
            bytes[i] = (byte)box[i];
        }

        return new NcmKeyBox(bytes);
    }

    public void Decrypt(ReadOnlySpan<byte> input, Span<byte> output, long streamOffset)
    {
        if (output.Length < input.Length)
        {
            throw new ArgumentException("输出缓冲区过小", nameof(output));
        }

        for (var i = 0; i < input.Length; i++)
        {
            var position = streamOffset + i;
            var j = (int)((position + 1) & 0xFF);
            var t = (_box[j] + j) & 0xFF;
            output[i] = (byte)(input[i] ^ _box[(_box[j] + _box[t]) & 0xFF]);
        }
    }
}
