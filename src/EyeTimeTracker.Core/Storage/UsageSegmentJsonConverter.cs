using System.Text.Json;
using System.Text.Json.Serialization;
using EyeTimeTracker.Core.Models;

namespace EyeTimeTracker.Core.Storage;

/// <summary>
/// state.json 中 UsageSegment 的紧凑序列化：写成定长数组
/// [segmentId, deviceId, platform, source, start, end, localDate, createdAt, updatedAt]，
/// 比对象写法省一半以上体积。读取同时兼容旧的对象格式，保证旧状态文件可以正常载入。
/// 只用于本地状态文件，不影响同步协议（同步仍用对象格式）。
/// </summary>
public sealed class UsageSegmentJsonConverter : JsonConverter<UsageSegment>
{
    public override UsageSegment Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType == JsonTokenType.StartArray)
        {
            var values = new string?[9];
            var index = 0;
            while (reader.Read() && reader.TokenType != JsonTokenType.EndArray)
            {
                if (index < values.Length)
                {
                    values[index] = reader.TokenType == JsonTokenType.String
                        ? reader.GetString()
                        : reader.TokenType == JsonTokenType.Number
                            ? reader.GetInt64().ToString(System.Globalization.CultureInfo.InvariantCulture)
                            : null;
                }

                index++;
            }

            return new UsageSegment
            {
                SegmentId = values[0] ?? string.Empty,
                DeviceId = values[1] ?? string.Empty,
                Platform = values[2] ?? string.Empty,
                Source = values[3] ?? string.Empty,
                StartUnixSeconds = ParseLong(values[4]),
                EndUnixSeconds = ParseLong(values[5]),
                LocalDate = ParseDate(values[6]),
                CreatedAtUnixSeconds = ParseLong(values[7]),
                UpdatedAtUnixSeconds = ParseLong(values[8])
            };
        }

        using var document = JsonDocument.ParseValue(ref reader);
        var root = document.RootElement;
        return new UsageSegment
        {
            SegmentId = GetString(root, nameof(UsageSegment.SegmentId)),
            DeviceId = GetString(root, nameof(UsageSegment.DeviceId)),
            Platform = GetString(root, nameof(UsageSegment.Platform)),
            Source = GetString(root, nameof(UsageSegment.Source)),
            StartUnixSeconds = GetLong(root, nameof(UsageSegment.StartUnixSeconds)),
            EndUnixSeconds = GetLong(root, nameof(UsageSegment.EndUnixSeconds)),
            LocalDate = ParseDate(GetString(root, nameof(UsageSegment.LocalDate))),
            CreatedAtUnixSeconds = GetLong(root, nameof(UsageSegment.CreatedAtUnixSeconds)),
            UpdatedAtUnixSeconds = GetLong(root, nameof(UsageSegment.UpdatedAtUnixSeconds))
        };
    }

    public override void Write(Utf8JsonWriter writer, UsageSegment value, JsonSerializerOptions options)
    {
        writer.WriteStartArray();
        writer.WriteStringValue(value.SegmentId);
        writer.WriteStringValue(value.DeviceId);
        writer.WriteStringValue(value.Platform);
        writer.WriteStringValue(value.Source);
        writer.WriteNumberValue(value.StartUnixSeconds);
        writer.WriteNumberValue(value.EndUnixSeconds);
        writer.WriteStringValue(value.LocalDate.ToString("yyyy-MM-dd"));
        writer.WriteNumberValue(value.CreatedAtUnixSeconds);
        writer.WriteNumberValue(value.UpdatedAtUnixSeconds);
        writer.WriteEndArray();
    }

    private static string GetString(JsonElement element, string name)
    {
        return element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String
            ? value.GetString() ?? string.Empty
            : string.Empty;
    }

    private static long GetLong(JsonElement element, string name)
    {
        return element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.Number
            ? value.GetInt64()
            : 0L;
    }

    private static long ParseLong(string? value)
    {
        return long.TryParse(value, out var parsed) ? parsed : 0L;
    }

    private static DateOnly ParseDate(string? value)
    {
        return DateOnly.TryParse(value, out var parsed) ? parsed : default;
    }
}
