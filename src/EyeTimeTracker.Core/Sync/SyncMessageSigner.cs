using System.Security.Cryptography;
using System.Text;

namespace EyeTimeTracker.Core.Sync;

public static class SyncMessageSigner
{
    public const long MaxClockSkewSeconds = 300;

    public static string Sign(string type, long timestampUnixSeconds, string bodyJson, string sharedSecret)
    {
        if (string.IsNullOrEmpty(sharedSecret))
        {
            throw new ArgumentException("Shared secret is required.", nameof(sharedSecret));
        }

        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(sharedSecret));
        var hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(CanonicalPayload(type, timestampUnixSeconds, bodyJson)));
        return Convert.ToHexString(hash).ToLowerInvariant();
    }

    public static bool Verify(
        string type,
        long timestampUnixSeconds,
        string bodyJson,
        string sharedSecret,
        string signature,
        long nowUnixSeconds)
    {
        if (string.IsNullOrEmpty(sharedSecret) || string.IsNullOrEmpty(signature))
        {
            return false;
        }

        if (Math.Abs(nowUnixSeconds - timestampUnixSeconds) > MaxClockSkewSeconds)
        {
            return false;
        }

        var expected = Sign(type, timestampUnixSeconds, bodyJson, sharedSecret);
        var expectedBytes = Encoding.UTF8.GetBytes(expected);
        var actualBytes = Encoding.UTF8.GetBytes(signature);
        return expectedBytes.Length == actualBytes.Length
            && CryptographicOperations.FixedTimeEquals(expectedBytes, actualBytes);
    }

    private static string CanonicalPayload(string type, long timestampUnixSeconds, string bodyJson)
    {
        return $"{type}\n{timestampUnixSeconds}\n{bodyJson ?? string.Empty}";
    }
}
