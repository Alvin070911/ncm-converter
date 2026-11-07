namespace NcmConverter.Core.Ncm;

public sealed class NcmException : Exception
{
    public NcmException(NcmErrorCode code, string message, Exception? innerException = null)
        : base(message, innerException)
    {
        Code = code;
    }

    public NcmErrorCode Code { get; }
}
