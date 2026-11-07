namespace NcmConverter.Core.Ncm;

public enum NcmErrorCode
{
    NotNcm,
    Truncated,
    KeyDecryptFailed,
    MetadataDecryptFailed,
    CoverInvalid,
    UnsupportedAudio,
    OutputPermissionDenied,
    DiskFull,
    TagWriteFailed,
    IoError,
    Cancelled,
    ConflictSkipped,
    Unknown
}
