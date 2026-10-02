using System.Media;
using System.Text;

namespace EyeTimeTracker.App.Platform;

/// <summary>
/// 程序内合成的提醒音：不依赖系统声音主题。
/// 两类提醒共用同一个基础音（880Hz 正弦 150ms，淡入淡出）：
/// 远望到期播一次；活动到期播两次（同一波形 + 220ms 静音间隔 + 重复，生成在同一个 WAV 里一次播放，
/// 不靠两次 Play 的时序）；合并组按活动规则播两次。
/// 波形为纯逻辑（可测试）；播放经 SoundPlayer 加轻量缓存，无外部资源文件。
/// </summary>
public static class ReminderTones
{
    public const int SampleRate = 22050;

    public const int BaseFrequencyHz = 660;
    public const int BaseDurationMs = 300;
    public const int MovementGapMs = 120;

    private const double Amplitude = 0.8;
    private const int FadeInMs = 5;
    private const int FadeOutFraction = 3; // 末 1/3 线性淡出

    private static readonly Lazy<SoundPlayer> EyePlayer = new(() => CreatePlayer(GenerateEyeTonePcm()));
    private static readonly Lazy<SoundPlayer> MovementPlayer = new(() => CreatePlayer(GenerateMovementTonePcm()));

    /// <summary>远望：基础音单次。</summary>
    public static short[] GenerateEyeTonePcm()
    {
        return GenerateBaseNotePcm();
    }

    /// <summary>活动：基础音 + 静音间隔 + 基础音（同一 WAV 内重复，两次响声节奏明确）。</summary>
    public static short[] GenerateMovementTonePcm()
    {
        var note = GenerateBaseNotePcm();
        var gapSamples = SampleRate * MovementGapMs / 1000;
        var pcm = new short[note.Length * 2 + gapSamples];
        Array.Copy(note, pcm, note.Length);
        Array.Copy(note, 0, pcm, note.Length + gapSamples, note.Length);
        return pcm;
    }

    /// <summary>16-bit 单声道 PCM → WAV 字节。</summary>
    public static byte[] BuildWav(short[] pcm, int sampleRate)
    {
        ArgumentNullException.ThrowIfNull(pcm);
        var dataBytes = pcm.Length * 2;
        using var stream = new MemoryStream(44 + dataBytes);
        using (var writer = new BinaryWriter(stream, Encoding.ASCII, leaveOpen: true))
        {
            writer.Write(Encoding.ASCII.GetBytes("RIFF"));
            writer.Write(36 + dataBytes);
            writer.Write(Encoding.ASCII.GetBytes("WAVE"));
            writer.Write(Encoding.ASCII.GetBytes("fmt "));
            writer.Write(16);
            writer.Write((short)1); // PCM
            writer.Write((short)1); // 单声道
            writer.Write(sampleRate);
            writer.Write(sampleRate * 2); // 字节率
            writer.Write((short)2); // 块对齐
            writer.Write((short)16); // 位深
            writer.Write(Encoding.ASCII.GetBytes("data"));
            writer.Write(dataBytes);
            foreach (var sample in pcm)
            {
                writer.Write(sample);
            }
        }

        return stream.ToArray();
    }

    /// <summary>播远望音（单次）。声音设备不可用时抛异常，由调用方记入投递证据。</summary>
    public static void PlayEye()
    {
        EyePlayer.Value.Play();
    }

    /// <summary>播活动音（同一提示音连续两次）。</summary>
    public static void PlayMovement()
    {
        MovementPlayer.Value.Play();
    }

    private static SoundPlayer CreatePlayer(short[] pcm)
    {
        var wav = BuildWav(pcm, SampleRate);
        var player = new SoundPlayer(new MemoryStream(wav, writable: false));
        player.Load();
        return player;
    }

    private static short[] GenerateBaseNotePcm()
    {
        var count = SampleRate * BaseDurationMs / 1000;
        var pcm = new short[count];
        var fadeInSamples = SampleRate * FadeInMs / 1000;
        var fadeOutSamples = count / FadeOutFraction;
        for (var i = 0; i < count; i++)
        {
            var envelope = 1.0;
            if (i < fadeInSamples)
            {
                envelope = (double)i / fadeInSamples;
            }
            else if (i >= count - fadeOutSamples)
            {
                envelope = (double)(count - 1 - i) / fadeOutSamples;
            }

            var value = Math.Sin(2.0 * Math.PI * BaseFrequencyHz * i / SampleRate) * envelope * Amplitude;
            pcm[i] = (short)(value * short.MaxValue);
        }

        return pcm;
    }
}
