# EyeTimeTracker 当前交接

更新时间：2026-07-07

## 当前分支

`codex-wifi-sync-foundation`

新窗口开始时先确认：

```powershell
git status --short --branch
```

不要切回 `master` 继续做功能。提交、推送只在用户明确要求时执行。

## 当前状态

最新一轮主要目标是把应用推进到“PC + Android 可配对同步、可日常试用”的状态，并开始为英文商业化版本做准备。

当前已经完成：

- PC 和 Android 均可本地统计用眼时间。
- PC 和 Android 支持 WiFi 局域网配对、同步、断开和自动重连。
- 双端使用统一 10 秒时间格合并统计，重叠时间只计算一次。
- 主界面今日、昨日、本周、本月等数据尽量使用合并后的可见统计。
- 统计页包含 24 小时分布、本周用眼、月度趋势、连续使用分布和护眼表现摘要。
- 统计页按电脑/手机来源分色显示，24 小时图标出 0、6、12、18 点。
- Android 横幅通知、声音、振动提醒已可用。
- PC 提醒弹窗带声音提示。
- 配对后提醒设置以手机为主，PC 跟随同步。
- 如果两端都正在计时，达到提醒条件时两端都可以弹出提醒。
- 修改提醒时间后，不补弹旧提醒点，从当前累计继续等待下一个新提醒点。
- PC 系统托盘菜单改为应用内统一风格。
- PC 主界面增加开机启动开关。
- 多语言基础结构已建立，后续要继续把界面文字集中到文案文件。
- README 和路线图已按当前状态更新。

## 最近一次已知提交

代码功能提交：

```text
f81a17e 完善提醒逻辑与多语言基础
```

本次文档更新提交后，应继续在同一分支推送到 GitHub。

## 用户明确偏好

- 回答用中文。
- 不要使用用户看不懂的术语；如果必须说，要用普通话解释。
- 不要切回 `master`。
- 不要自动提交或推送，除非用户明确说“保存 git / 提交 / 推送”。
- 每次有代码改动后：
  - 自动构建 PC 版。
  - 自动显示 PC 程序的可点击链接。
  - 自动构建 Android APK。
  - 自动安装 Android 版到已连接手机。
- 文档改动不需要自动安装 Android。
- 给用户路径时尽量给可点击链接，不要让用户复制路径。
- 改动要聚焦，不要顺手重构项目。

## 最近验证情况

最近一次代码验证已通过：

- PC 测试通过。
- Android APK 构建和签名通过。
- Android 安装通过。
- 用户确认 Android 横幅通知已经可以弹出。

本次文档更新属于说明文档改动，建议只做：

```powershell
git diff --check
```

## 当前重要文件

PC：

- `src/EyeTimeTracker.Core/Sync/UsageSegmentMerger.cs`
- `src/EyeTimeTracker.Core/Sync/DailyRecordReconciler.cs`
- `src/EyeTimeTracker.Core/Sync/PcSyncCoordinator.cs`
- `src/EyeTimeTracker.Core/Reminders/ReminderPolicy.cs`
- `src/EyeTimeTracker.App/Tracking/TrackingController.cs`
- `src/EyeTimeTracker.App/UI/MainForm.cs`
- `src/EyeTimeTracker.App/UI/StatsForm.cs`
- `src/EyeTimeTracker.App/UI/TrayApplicationContext.cs`

Android：

- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/MainActivity.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/StatsActivity.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/UsageSegmentMerger.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/DailySummaryReconciler.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ReminderPolicy.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ReminderNotifier.java`

多语言：

- `i18n/source/zh-CN.json`
- `i18n/source/en-US.json`
- `i18n/generated/`

## 已知限制

- 当前 WiFi 同步主要支持一台 PC 和一台 Android 手机。
- 部分路由器会隔离 2.4G 和 5G，可能导致两端不能自动发现或同步。
- Windows 防火墙可能影响局域网连接。
- 旧版本只保存汇总数据的日期，无法完美还原成可去重片段。
- Android 不使用摄像头判断是否真实注视屏幕，也不能确认用户是否完成远眺休息。
- 云同步、账号系统、iOS 和 macOS 还未实现。
- 英文界面仍处于基础架构阶段，还需要逐屏检查布局。

## 下一步建议

1. 继续推进多语言架构，把 PC 和 Android 所有界面文字集中引用文案文件。
2. 做英文界面实际截图检查，重点看按钮、卡片、弹窗和图表标签是否遮挡。
3. 增加语言选择入口。
4. 继续观察 WiFi 同步在不同路由器、断开重连、换网络场景下的表现。
5. 补充公开发布需要的安装说明、权限说明和故障排查说明。
6. 后续商业化版本考虑 AI 摘要分析，但不要在当前最小试验阶段过早引入复杂模型依赖。
