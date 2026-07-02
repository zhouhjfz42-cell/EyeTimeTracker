# EyeTimeTracker 当前交接

更新时间：2026-07-02

## 当前分支

`codex-wifi-sync-foundation`

请在新窗口先确认：

```powershell
git status --short --branch
```

预期分支是 `codex-wifi-sync-foundation`。不要切回 `master` 继续做 WiFi 同步半成品。

## 当前 Git 状态

本轮 WiFi 同步基础工作尚未提交、尚未推送。

最近已推送到 GitHub 的提交：

- `2947586 完善统计图表交互与显示`
- `8817ef9 docs: add product roadmap`
- `a8b63bb 修正24H图按小时满格缩放`

当前未提交变更包含：

- WiFi 同步设计文档
- WiFi 同步实施计划
- PC 端 10 秒片段模型、合并去重、签名消息
- Android 端 10 秒片段模型、合并去重、签名消息
- PC/Android 本地计时写入片段

## 已完成内容

### 设计与计划

已新增：

- `docs/superpowers/specs/2026-07-02-wifi-lan-sync-design.md`
- `docs/superpowers/plans/2026-07-02-wifi-lan-sync.md`

设计结论：

- 第一版只支持一台 PC + 一台 Android 手机。
- PC 应用内置轻量同步监听，随 PC 程序运行；缩到托盘也运行；退出程序则停止。
- 不做独立 Windows Service。
- Android 主动连接 PC。
- 手机仍是主设备：手机生成配对码，手机端提醒设置覆盖 PC。
- 精确去重从新 10 秒片段数据开始；旧每日汇总继续显示，但无法精确反推跨设备重叠区间。

### PC Core

已新增：

- `src/EyeTimeTracker.Core/Models/UsageSegment.cs`
- `src/EyeTimeTracker.Core/Models/SyncSettings.cs`
- `src/EyeTimeTracker.Core/Sync/UsageSegmentId.cs`
- `src/EyeTimeTracker.Core/Sync/UsageSegmentFactory.cs`
- `src/EyeTimeTracker.Core/Sync/UsageSegmentMerger.cs`
- `src/EyeTimeTracker.Core/Sync/SyncMessages.cs`
- `src/EyeTimeTracker.Core/Sync/SyncMessageSigner.cs`

已修改：

- `src/EyeTimeTracker.Core/Models/AppState.cs`
- `src/EyeTimeTracker.Core/Storage/JsonStateStore.cs`

已实现：

- 10 秒片段模型。
- 稳定片段 ID。
- 10 秒桶合并去重。
- 按片段生成 `DailyRecord`。
- 连续使用按 3 分钟中断规则分段。
- 同步消息类型：`pairRequest`、`pairAccept`、`syncRequest`、`syncResponse`、`reminderClaim`。
- HMAC-SHA256 签名与 5 分钟时间戳过期校验。

### PC App

已修改：

- `src/EyeTimeTracker.App/Tracking/TrackingController.cs`

已实现：

- 本地真实计时路径在有效计时后写入 `UsageSegment`。
- `pc-input` 与 `pc-media` 来源区分。
- 保存状态时保留 `Segments` 和 `Sync` 字段。

尚未实现：

- PC 同步监听。
- PC 配对窗口。
- PC 同步状态 UI。
- 配对后 PC 提醒设置只读。

### Android

已新增：

- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegment.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegmentId.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegmentMerger.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncMessages.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/SyncMessageSigner.java`

已修改：

- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeService.java`
- `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/CoreLogicTest.java`

已实现：

- Android 本地计时写入 `segments`。
- `getDay()` 在当天存在片段时，优先用片段合并统计；没有片段时继续读取旧汇总。
- `sumRange()` 通过 `getDay()` 汇总，因此新片段会进入周/月统计。
- Android 端同步消息类型和 HMAC-SHA256 签名校验。

尚未实现：

- Android 连接 PC。
- Android 配对 UI。
- Android 同步状态 UI。
- Android 后台定时同步。

## 已验证

最近一次已通过：

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -o outputs\verify-pc-sync-contracts
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

Android 核心测试也通过：

```powershell
$out = 'outputs\android-core-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
javac -encoding UTF-8 -d $out android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ActivityDecision.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\DailySummary.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\DurationFormatter.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\TodayTone.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ReminderThreshold.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ReminderAlert.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\ReminderPolicy.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\UsageSegment.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\UsageSegmentId.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\UsageSegmentMerger.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\SyncMessages.java android\EyeTimeTrackerAndroid\src\com\eyetimetracker\android\SyncMessageSigner.java android\EyeTimeTrackerAndroid\tests\com\eyetimetracker\android\CoreLogicTest.java
java -cp $out com.eyetimetracker.android.CoreLogicTest
```

## 下一步

下一步按计划做 **PC 应用内同步监听**。

建议顺序：

1. 新增 `src/EyeTimeTracker.App/Sync/PcSyncCoordinator.cs`
   - 先不碰真实 Socket。
   - 直接测试：接收 Android 片段，合并进 PC `AppState.Segments`。
   - 返回 PC 本地片段。
   - Android 设置覆盖 PC 设置。

2. 新增 `src/EyeTimeTracker.App/Sync/PcSyncServer.cs`
   - 使用 `TcpListener`。
   - 默认端口 `17420`，被占用时尝试 `17421` 到 `17429`。
   - 一次 TCP 连接处理一条 JSON 请求并返回一条 JSON 响应。
   - 网络异常不能让 PC 应用崩溃，只记录 `LastError`。

3. 暂时不要急着做 UI。
   - 先用测试或最小本地请求验证 server/coordinator。
   - UI 放到 PC listener 能跑通之后再做。

## 注意事项

- 用户明确说：提交、推送要等他说；不要自动 commit/push。
- 用户明确说：除特殊提醒外，功能通常要 PC 和 Android 双端一起实施。
- 当前阶段还没有安装新 APK 到手机。
- 现在是基础同步分支，不要在 `master` 上继续做。
- 文档和代码都在未提交状态，新窗口不要误以为这些已经进 Git。

## 新窗口启动建议

新窗口开始时执行：

```powershell
git status --short --branch
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

然后阅读：

- `docs/superpowers/specs/2026-07-02-wifi-lan-sync-design.md`
- `docs/superpowers/plans/2026-07-02-wifi-lan-sync.md`
- `src/EyeTimeTracker.Core/Sync/SyncMessages.cs`
- `src/EyeTimeTracker.Core/Sync/SyncMessageSigner.cs`
- `src/EyeTimeTracker.App/Tracking/TrackingController.cs`
