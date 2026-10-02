using EyeTimeTracker.App.Platform;

/// <summary>合成提醒音纯逻辑测试：远望/活动共用同一基础音；活动 = 两次基础音 + 中间静音段。</summary>
internal static class ReminderToneTests
{
    private static void AssertEqual<T>(T expected, T actual, string name)
    {
        if (!EqualityComparer<T>.Default.Equals(expected, actual))
        {
            throw new Exception($"{name}: expected {expected}, actual {actual}");
        }
    }

    private static void AssertTrue(bool condition, string name)
    {
        if (!condition)
        {
            throw new Exception($"{name}: expected true");
        }
    }

    private static int MaxAbs(short[] pcm, int offset, int count)
    {
        var max = 0;
        for (var i = offset; i < offset + count && i < pcm.Length; i++)
        {
            max = Math.Max(max, Math.Abs((int)pcm[i]));
        }

        return max;
    }

    private static void TestEyeToneDurationAndPeak()
    {
        var pcm = ReminderTones.GenerateEyeTonePcm();
        AssertEqual(ReminderTones.SampleRate * ReminderTones.BaseDurationMs / 1000, pcm.Length, nameof(TestEyeToneDurationAndPeak) + " length");
        var peak = MaxAbs(pcm, 0, pcm.Length);
        AssertTrue(peak > short.MaxValue / 4, nameof(TestEyeToneDurationAndPeak) + " peak non-trivial");
        AssertTrue(peak <= short.MaxValue, nameof(TestEyeToneDurationAndPeak) + " peak within range");
    }

    private static void TestMovementToneIsBaseNoteTwiceWithSilenceGap()
    {
        var eye = ReminderTones.GenerateEyeTonePcm();
        var movement = ReminderTones.GenerateMovementTonePcm();
        var gapSamples = ReminderTones.SampleRate * ReminderTones.MovementGapMs / 1000;
        AssertEqual(eye.Length * 2 + gapSamples, movement.Length, nameof(TestMovementToneIsBaseNoteTwiceWithSilenceGap) + " length");

        // 两个波峰段与远望音完全同源
        AssertTrue(eye.SequenceEqual(movement.Take(eye.Length).ToArray()), nameof(TestMovementToneIsBaseNoteTwiceWithSilenceGap) + " first note same as eye");
        AssertTrue(eye.SequenceEqual(movement.Skip(eye.Length + gapSamples).Take(eye.Length).ToArray()), nameof(TestMovementToneIsBaseNoteTwiceWithSilenceGap) + " second note same as eye");

        // 中间静音段接近零
        AssertEqual(0, MaxAbs(movement, eye.Length, gapSamples), nameof(TestMovementToneIsBaseNoteTwiceWithSilenceGap) + " gap is silent");

        // 两段都有声音
        AssertTrue(MaxAbs(movement, 0, eye.Length) > short.MaxValue / 4, nameof(TestMovementToneIsBaseNoteTwiceWithSilenceGap) + " first note audible");
        AssertTrue(MaxAbs(movement, eye.Length + gapSamples, eye.Length) > short.MaxValue / 4, nameof(TestMovementToneIsBaseNoteTwiceWithSilenceGap) + " second note audible");
    }

    private static void TestTwoTonesDiffer()
    {
        var eye = ReminderTones.GenerateEyeTonePcm();
        var movement = ReminderTones.GenerateMovementTonePcm();
        AssertTrue(eye.Length != movement.Length, nameof(TestTwoTonesDiffer) + " different length");
        AssertTrue(!eye.SequenceEqual(movement.Take(eye.Length).ToArray().Concat(movement.Skip(eye.Length)).ToArray()), nameof(TestTwoTonesDiffer) + " movement has extra content");
    }

    private static void TestWavHeader()
    {
        var pcm = ReminderTones.GenerateEyeTonePcm();
        var wav = ReminderTones.BuildWav(pcm, ReminderTones.SampleRate);
        AssertEqual(44 + pcm.Length * 2, wav.Length, nameof(TestWavHeader) + " size");
        AssertEqual((byte)'R', wav[0], nameof(TestWavHeader) + " RIFF R");
        AssertEqual((byte)'I', wav[1], nameof(TestWavHeader) + " RIFF I");
        AssertEqual((byte)'W', wav[8], nameof(TestWavHeader) + " WAVE");
        AssertEqual((byte)1, wav[20], nameof(TestWavHeader) + " PCM format");
        AssertEqual((byte)1, wav[22], nameof(TestWavHeader) + " mono");
        AssertEqual((byte)(ReminderTones.SampleRate & 0xFF), wav[24], nameof(TestWavHeader) + " sample rate low byte");
        AssertEqual((byte)(ReminderTones.SampleRate >> 8 & 0xFF), wav[25], nameof(TestWavHeader) + " sample rate high byte");
        AssertEqual((byte)16, wav[34], nameof(TestWavHeader) + " 16-bit depth");
    }

    public static void RunAll()
    {
        TestEyeToneDurationAndPeak();
        TestMovementToneIsBaseNoteTwiceWithSilenceGap();
        TestTwoTonesDiffer();
        TestWavHeader();
    }
}
