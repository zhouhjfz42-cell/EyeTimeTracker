namespace EyeTimeTracker.App.Sync;

public sealed class PcPairingCodeProvider
{
    private readonly object _gate = new();
    private readonly Func<DateTimeOffset> _clock;
    private readonly TimeSpan _codeLifetime;
    private string _code = string.Empty;
    private DateTimeOffset _expiresAt = DateTimeOffset.MinValue;

    public PcPairingCodeProvider(Func<DateTimeOffset>? clock = null, TimeSpan? codeLifetime = null)
    {
        _clock = clock ?? (() => DateTimeOffset.UtcNow);
        _codeLifetime = codeLifetime ?? TimeSpan.FromMinutes(5);
        if (_codeLifetime <= TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(nameof(codeLifetime), "Code lifetime must be positive.");
        }
    }

    public void AllowCode(string code)
    {
        if (!TryAllowCode(code, out var error))
        {
            throw new ArgumentException(error, nameof(code));
        }
    }

    public bool TryAllowCode(string code, out string error)
    {
        var normalizedCode = (code ?? string.Empty).Trim();
        if (normalizedCode.Length != 6 || normalizedCode.Any(ch => ch < '0' || ch > '9'))
        {
            error = "请输入手机上显示的 6 位数字配对码。";
            return false;
        }

        lock (_gate)
        {
            _code = normalizedCode;
            _expiresAt = _clock().Add(_codeLifetime);
        }

        error = string.Empty;
        return true;
    }

    public string GetExpectedCode()
    {
        lock (_gate)
        {
            if (string.IsNullOrWhiteSpace(_code) || _clock() > _expiresAt)
            {
                return string.Empty;
            }

            return _code;
        }
    }

    public void Clear()
    {
        lock (_gate)
        {
            _code = string.Empty;
            _expiresAt = DateTimeOffset.MinValue;
        }
    }
}
