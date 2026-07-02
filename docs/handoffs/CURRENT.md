# EyeTimeTracker 当前交接

更新时间：2026-07-02

## 当前分支

`codex-wifi-sync-foundation`

新窗口开始时先确认：

```powershell
git status --short --branch
```

不要切回 `master` 继续做功能。提交、推送只在用户明确要求时执行。

## 今日收尾状态

本轮 WiFi 局域网同步和双端统计显示相关工作已经完成验证，并按用户要求准备提交和推送到 GitHub。

本轮主要内容：

- PC 和 Android 均已支持配对、同步、断开、离线/重连状态显示。
- PC 主界面和 Android 主界面在配对成功后显示已连接状态。
- 双端断开时，会按同步响应清理配对状态。
- Android 离开 WiFi 后显示暂时离线，再回到 WiFi 后可重连。
- PC 和 Android 统计页都区分电脑/手机来源。
- 今日用眼、最长连续、来源比例、提醒次数等显示改为基于同步后的可见统计。
- 有同步明细的日期，两端都用同步明细生成的统计；没有同步明细的日期继续使用本机旧汇总。
- 24 小时圆形柱状图增加 0、6、12、18 点标记。
- 24 小时图来源叠加时，每小时最多显示 60 分钟；电脑/手机重合时按比例压缩到 60 分钟内。
- PC 统计页右侧改为“今日摘要”，筛选和颜色示例移到“单日情况”标题后。
- Android 底部说明文字已更新为用户指定文案。
- PC 托盘菜单样式已从旧系统菜单改为应用内统一风格。

## 已新增或重点修改文件

PC：

- `src/EyeTimeTracker.Core/Sync/UsageDeviceBreakdown.cs`
- `src/EyeTimeTracker.Core/Sync/DailyRecordReconciler.cs`
- `src/EyeTimeTracker.Core/Reminders/ReminderDisplayCount.cs`
- `src/EyeTimeTracker.App/Tracking/TrackingController.cs`
- `src/EyeTimeTracker.App/UI/StatsForm.cs`
- `src/EyeTimeTracker.App/UI/TrayApplicationContext.cs`

Android：

- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/DeviceUsageBreakdown.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/DailySummaryReconciler.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/MainActivity.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ReminderPolicy.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/StatsActivity.java`
- `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/CoreLogicTest.java`

Tests：

- `tests/EyeTimeTracker.Tests/Program.cs`

## 已验证

已通过：

```powershell
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

Android 核心测试已通过，测试内容包括：

- 同步片段合并去重。
- 双端来源比例。
- 每小时来源叠加最多 60 分钟。
- 同步日期优先使用同步明细统计。
- 提醒次数按可见总时长计算。

APK 已生成并安装到手机：

```text
C:\Users\zhouh\Documents\Codex\2026-06-26\new-chat\outputs\android\EyeTimeTrackerAndroid-debug.apk
```

PC 最近可用程序位置：

```text
C:\Users\zhouh\Documents\Codex\2026-06-26\new-chat\outputs\verify-sync-summary-fix\EyeTimeTracker.App.exe
```

如果旧输出目录被正在运行的 PC 程序占用，后续构建应生成到新的输出目录，并给用户可点击链接。

## 用户明确偏好

- 后续不要使用用户看不懂的术语；如果必须说，要用普通话解释。
- 每次有代码改动后：
  - 自动构建 PC 版。
  - 自动显示 PC 程序的可点击链接。
  - 自动构建 Android APK。
  - 自动安装安卓版到已连接手机。
- 不要自动提交或推送，除非用户明确说“保存 git / 提交 / 推送”。
- 用户不喜欢需要复制路径，尽量给可点击链接。

## 明天优先事项

用户提出底层统计问题：希望确认或调整为“每天固定 10 秒格子”，双端都使用同一套时间格子，重合时同一格只算一次。

当前实现已经使用 Unix 时间的统一 10 秒格子进行同步合并：

- PC：`UsageSegmentMerger` 使用 `unixSeconds / 10`。
- Android：`UsageSegmentMerger` 使用同样的 10 秒格子。

明天建议先做一次专项复核：

1. 确认 PC 和 Android 所有新增片段都使用同一套 Unix 10 秒格子。
2. 检查是否存在 UI 或本机旧汇总仍按“本机动作开始秒数”造成统计分叉。
3. 如果需要，补测试明确：
   - 任意秒数开始的 PC/Android 重合片段能落到同一批 10 秒格子。
   - 同一小时最多 3600 秒。
   - 跨小时片段能正确分配到两个小时。
4. 如果测试证明现有实现已满足，则只补测试和说明，不需要重写逻辑。

## 新窗口启动建议

```powershell
git status --short --branch
dotnet run --project tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj
```

然后查看：

- `src/EyeTimeTracker.Core/Sync/UsageSegmentMerger.cs`
- `src/EyeTimeTracker.Core/Sync/UsageDeviceBreakdown.cs`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegmentMerger.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/DeviceUsageBreakdown.java`
- `src/EyeTimeTracker.App/UI/StatsForm.cs`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/StatsActivity.java`
