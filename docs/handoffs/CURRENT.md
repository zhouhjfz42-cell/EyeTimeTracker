# EyeTimeTracker 当前交接

更新时间：2026-07-08

## 当前分支

`codex-wifi-sync-foundation`

新窗口开始时先确认：

```powershell
git status --short --branch
```

不要切回 `master` 继续做功能。提交、推送只在用户明确要求时执行。

## 当前方向

产品主线已经调整为：先完善中文版儿童控制商业版，再考虑海外英文版、官网和更多平台。

现有 PC + Android 用眼统计、提醒、WiFi 同步、多语言底座继续保留，并作为儿童控制商业版的基础能力。但接下来商业化重点放在 Android 手机和平板的儿童控制能力。

当前个人成人版建议作为基线版本保存：

- 产品阶段：`personal`
- 版本代号：`personal-sync-baseline`
- Git 标签：`v0.9-personal-sync`

## 当前已完成基础能力

- PC 和 Android 均可本地统计用眼时间。
- PC 和 Android 支持 WiFi 局域网配对、同步、断开和自动重连。
- 双端使用统一 10 秒时间格合并统计，重叠时间只计算一次。
- 主界面今日、昨日、本周、本月等数据尽量使用合并后的可见统计。
- 统计页包含 24 小时分布、本周用眼、月度趋势、连续使用分布和护眼表现摘要。
- Android 横幅通知、声音、振动提醒已可用。
- PC 提醒弹窗带声音提示。
- 配对后提醒设置以手机为主，PC 跟随同步。
- 如果两端都正在计时，达到提醒条件时两端都可以弹出提醒。
- 修改提醒时间后，不补弹旧提醒点，从当前累计继续等待下一个新提醒点。
- PC 系统托盘菜单改为应用内统一风格。
- PC 主界面增加开机启动开关。
- 多语言基础结构已建立，PC 和 Android 主要界面文案已开始集中接入。

## 本轮文档更新

新增儿童控制商业版路线图与第一阶段范围：

- `docs/superpowers/specs/2026-07-08-child-commercial-edition-roadmap-design.md`

更新主路线图：

- `docs/ROADMAP.md`

主路线图已经改为“中文版儿童控制商业版优先”，英文实装后置。

## 最近一次已知提交

```text
a179ea8 完善双端多语言文案接入
```

当前分支本地领先远端 1 个提交。本轮文档更新尚未提交、尚未推送。

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
- 儿童控制商业版涉及账号、支付、后台管理时，优先复用已购买模板和成熟开源库，不从零开始写。

## 当前重要文档

- `docs/ROADMAP.md`
- `docs/DEVELOPMENT_GUIDE.md`
- `docs/VERSIONING.md`
- `docs/superpowers/specs/2026-07-08-child-commercial-edition-roadmap-design.md`
- `README.md`
- `docs/WIFI_SYNC.md`
- `i18n/README.md`

## 当前重要代码文件

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

可复用模板和候选库：

- `C:\Users\zhouh\Documents\trae\shipany-template-two`
- Sa-Token
- Apache Shiro

## 已知限制

- 当前 WiFi 同步主要支持一台 PC 和一台 Android 手机。
- 部分路由器会隔离 2.4G 和 5G，可能导致两端不能自动发现或同步。
- Windows 防火墙可能影响局域网连接。
- 旧版本只保存汇总数据的日期，无法完美还原成可去重片段。
- Android 不使用摄像头判断是否真实注视屏幕，也不能确认用户是否完成远眺休息。
- 云同步、账号系统、iOS、macOS 和鸿蒙还未实现。
- 英文界面暂时后置，保留多语言结构即可。
- 儿童控制版的数据标记字段尚未落代码；后续需要考虑 `productMode`、`dataSchemaVersion`、`syncProtocolVersion`、`clientVersion`、`deviceRole`。

## 下一步建议

1. 提交当前文档，并本地标记 `v0.9-personal-sync`。
2. 根据儿童控制商业版路线图，创建第一阶段合规底稿。
3. 第一阶段先写数据清单、权限说明、家长同意流程、平台能力边界和审核风险清单。
4. 不急着写最终隐私政策、用户协议或应用商店数据安全表，等核心功能和第三方服务稳定后再定稿。
5. 第一阶段完成后，再依据底稿确定 Android 儿童控制核心版的功能范围。
6. 进入账号、支付、后台管理前，先评估 ShipAny 模板、Sa-Token、Shiro 等可复用资源。
7. 继续维护现有 PC + Android 统计版稳定性，但不要把 Windows 做成儿童控制主平台。
