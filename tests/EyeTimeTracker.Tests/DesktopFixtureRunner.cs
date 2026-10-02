using System.Text.Json;
using System.Text.Json.Serialization;
using EyeTimeTracker.Core.DesktopActivity;

/// <summary>
/// 桌面领域 JSON 夹具重放器：signals 按时间偏移驱动 ActivityClassifier，
/// 对照预期状态、committed 区间、中断、缺口与提醒机会。夹具与 macOS 共用。
/// </summary>
static class DesktopFixtureRunner
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        ReadCommentHandling = JsonCommentHandling.Skip
    };

    public static void Run(string path)
    {
        var fixture = JsonSerializer.Deserialize<Fixture>(File.ReadAllText(path), JsonOptions)
            ?? throw new Exception($"fixture parse failed: {path}");
        var name = fixture.Name ?? Path.GetFileName(path);

        var settings = new DesktopSettings
        {
            Desk = ParseDesk(fixture.Desk),
            IdleGraceSeconds = fixture.Settings?.IdleGraceSeconds ?? 60,
            QualifyingAwaySeconds = fixture.Settings?.QualifyingAwaySeconds ?? 120,
            EyeIntervalSeconds = fixture.Settings?.EyeIntervalSeconds ?? 1200,
            MovementIntervalSeconds = fixture.Settings?.MovementIntervalSeconds ?? 2400,
            EyeEnabled = fixture.Settings?.EyeEnabled ?? true,
            MovementEnabled = fixture.Settings?.MovementEnabled ?? true
        };
        var startUtc = DateTimeOffset.Parse(fixture.StartUtc ?? "2026-09-29T01:00:00Z");
        var classifier = new ActivityClassifier(settings);
        var monotonic = 0L;
        foreach (var signal in fixture.Signals ?? new List<FixtureSignal>())
        {
            var at = startUtc + TimeSpan.FromSeconds(signal.T);
            monotonic = signal.T * 1000L;
            classifier.Apply(new ActivitySignal(
                ParseKind(signal.Kind),
                at,
                monotonic,
                signal.Desk is null ? null : ParseDesk(signal.Desk)));
        }

        if (fixture.AdvanceTo is { } advance)
        {
            classifier.AdvanceTo(startUtc + TimeSpan.FromSeconds(advance), advance * 1000L);
        }

        var expect = fixture.Expect ?? throw new Exception($"{name}: missing expect");
        var label = $"{name}";

        if (expect.State is not null)
        {
            Check(expect.State, classifier.State.ToString(), label + " state");
        }

        if (expect.CommittedSeconds is { } committedSeconds)
        {
            Check(
                committedSeconds,
                SessionAnalyzer.CommittedSeconds(classifier.Intervals),
                label + " committedSeconds");
        }

        if (expect.Intervals is not null)
        {
            var actual = classifier.Intervals
                .Select(interval => new[]
                {
                    (long)(interval.StartUtc - startUtc).TotalSeconds,
                    (long)(interval.EndUtc - startUtc).TotalSeconds
                })
                .ToList();
            Check(expect.Intervals.Count, actual.Count, label + " intervals count");
            for (var i = 0; i < actual.Count; i++)
            {
                Check(expect.Intervals[i][0], actual[i][0], label + $" interval[{i}] start");
                Check(expect.Intervals[i][1], actual[i][1], label + $" interval[{i}] end");
            }
        }

        if (expect.Sessions is not null)
        {
            Check(expect.Sessions.Count, classifier.Sessions.Count, label + " sessions count");
            for (var i = 0; i < expect.Sessions.Count; i++)
            {
                var expectedSession = expect.Sessions[i];
                var actualSession = classifier.Sessions[i];
                if (expectedSession.StartedT is { } startedT)
                {
                    Check(startedT, (long)(actualSession.StartedAtUtc - startUtc).TotalSeconds, label + $" session[{i}] start");
                }

                if (expectedSession.EndedT is { } endedT)
                {
                    Check(endedT, (long?)(actualSession.EndedAtUtc - startUtc)?.TotalSeconds, label + $" session[{i}] end");
                }

                if (expectedSession.EndReason is not null)
                {
                    Check(expectedSession.EndReason, actualSession.EndReason ?? "", label + $" session[{i}] endReason");
                }

                if (expectedSession.SameAsPrevious is true && i > 0)
                {
                    Check(
                        classifier.Sessions[i - 1].SessionId,
                        actualSession.SessionId,
                        label + $" session[{i}] same id");
                }
            }
        }

        if (expect.Breaks is not null)
        {
            Check(expect.Breaks.Count, classifier.Breaks.Count, label + " breaks count");
            for (var i = 0; i < expect.Breaks.Count; i++)
            {
                var expectedBreak = expect.Breaks[i];
                var actualBreak = classifier.Breaks[i];
                Check(expectedBreak.AbsenceT, (long)(actualBreak.AbsenceStartedAtUtc - startUtc).TotalSeconds, label + $" break[{i}] absence");
                Check(expectedBreak.ThresholdT, (long)(actualBreak.ThresholdReachedAtUtc - startUtc).TotalSeconds, label + $" break[{i}] threshold");
                Check(
                    expectedBreak.ReturnedT,
                    actualBreak.ReturnedAtUtc is { } returned ? (long?)(returned - startUtc).TotalSeconds : null,
                    label + $" break[{i}] returned");
                if (expectedBreak.Reason is not null)
                {
                    Check(expectedBreak.Reason, actualBreak.Reason, label + $" break[{i}] reason");
                }
            }
        }

        if (expect.Gaps is not null)
        {
            Check(expect.Gaps.Count, classifier.Gaps.Count, label + " gaps count");
            for (var i = 0; i < expect.Gaps.Count; i++)
            {
                var expectedGap = expect.Gaps[i];
                var actualGap = classifier.Gaps[i];
                Check(expectedGap.FromT, (long)(actualGap.FromUtc - startUtc).TotalSeconds, label + $" gap[{i}] from");
                Check(
                    expectedGap.ToT,
                    actualGap.ToUtc is { } to ? (long?)(to - startUtc).TotalSeconds : null,
                    label + $" gap[{i}] to");
                Check(expectedGap.Reason, actualGap.Reason, label + $" gap[{i}] reason");
            }
        }

        if (expect.Opportunities is not null)
        {
            var activeSeconds = SessionAnalyzer.CommittedSeconds(classifier.Intervals);
            var actual = SessionAnalyzer.Opportunities(activeSeconds, settings).ToList();
            Check(expect.Opportunities.Count, actual.Count, label + " opportunities count");
            for (var i = 0; i < actual.Count; i++)
            {
                Check(expect.Opportunities[i].Type, actual[i].ReminderType, label + $" opportunity[{i}] type");
                Check(expect.Opportunities[i].DueActiveSeconds, actual[i].DueActiveSeconds, label + $" opportunity[{i}] due");
                Check(expect.Opportunities[i].Merged, actual[i].MergedIntoGroup, label + $" opportunity[{i}] merged");
                if (expect.Opportunities[i].DeliveryGroupId is not null)
                {
                    Check(expect.Opportunities[i].DeliveryGroupId, actual[i].DeliveryGroupId ?? "", label + $" opportunity[{i}] group");
                }
            }
        }

        if (expect.Over40 is { } over40)
        {
            var activeSeconds = SessionAnalyzer.CommittedSeconds(classifier.Intervals);
            Check(over40, SessionAnalyzer.CrossedLongSessionThreshold(activeSeconds), label + " over40");
        }

        if (expect.Desk is not null)
        {
            Check(expect.Desk, classifier.Desk.ToString(), label + " desk");
        }

        Console.WriteLine($"  fixture passed: {label}");
    }

    private static void Check<T>(T expected, T actual, string name)
    {
        if (!EqualityComparer<T>.Default.Equals(expected, actual))
        {
            throw new Exception($"{name}: expected {expected}, actual {actual}");
        }
    }

    private static ActivitySignalKind ParseKind(string? kind) => kind switch
    {
        "Input" => ActivitySignalKind.Input,
        "LockScreen" => ActivitySignalKind.LockScreen,
        "UnlockScreen" => ActivitySignalKind.UnlockScreen,
        "Suspend" => ActivitySignalKind.Suspend,
        "Resume" => ActivitySignalKind.Resume,
        "ObservationLost" => ActivitySignalKind.ObservationLost,
        "DeskProfileChanged" => ActivitySignalKind.DeskProfileChanged,
        _ => throw new Exception($"unknown signal kind: {kind}")
    };

    private static DeskType ParseDesk(string? desk) => desk switch
    {
        null or "unknown" => DeskType.Unknown,
        "ordinary" => DeskType.Ordinary,
        "adjustable" => DeskType.Adjustable,
        _ => throw new Exception($"unknown desk: {desk}")
    };

    public static string FindFixtureDir()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            var candidate = Path.Combine(dir.FullName, "tests", "fixtures", "desktop");
            if (Directory.Exists(candidate))
            {
                return candidate;
            }

            dir = dir.Parent;
        }

        throw new Exception("tests/fixtures/desktop not found");
    }

    private sealed class Fixture
    {
        public string? Name { get; set; }
        public string? Desk { get; set; }
        public string? StartUtc { get; set; }
        public FixtureSettings? Settings { get; set; }
        public List<FixtureSignal>? Signals { get; set; }
        public long? AdvanceTo { get; set; }
        public FixtureExpect? Expect { get; set; }
    }

    private sealed class FixtureSettings
    {
        public int? IdleGraceSeconds { get; set; }
        public int? QualifyingAwaySeconds { get; set; }
        public int? EyeIntervalSeconds { get; set; }
        public int? MovementIntervalSeconds { get; set; }
        public bool? EyeEnabled { get; set; }
        public bool? MovementEnabled { get; set; }
    }

    private sealed class FixtureSignal
    {
        public long T { get; set; }
        public string? Kind { get; set; }
        public string? Desk { get; set; }
    }

    private sealed class FixtureExpect
    {
        public string? State { get; set; }
        public long? CommittedSeconds { get; set; }
        public List<long[]>? Intervals { get; set; }
        public List<ExpectSession>? Sessions { get; set; }
        public List<ExpectBreak>? Breaks { get; set; }
        public List<ExpectGap>? Gaps { get; set; }
        public List<ExpectOpportunity>? Opportunities { get; set; }
        public bool? Over40 { get; set; }
        public string? Desk { get; set; }
    }

    private sealed class ExpectSession
    {
        public long? StartedT { get; set; }
        public long? EndedT { get; set; }
        public string? EndReason { get; set; }
        public bool? SameAsPrevious { get; set; }
    }

    private sealed class ExpectBreak
    {
        public long AbsenceT { get; set; }
        public long ThresholdT { get; set; }
        public long? ReturnedT { get; set; }
        public string? Reason { get; set; }
    }

    private sealed class ExpectGap
    {
        public long FromT { get; set; }
        public long? ToT { get; set; }
        public string Reason { get; set; } = string.Empty;
    }

    private sealed class ExpectOpportunity
    {
        public string Type { get; set; } = string.Empty;
        public long DueActiveSeconds { get; set; }
        public bool Merged { get; set; }
        public string? DeliveryGroupId { get; set; }
    }
}
