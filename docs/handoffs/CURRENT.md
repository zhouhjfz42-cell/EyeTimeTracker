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

产品主线已经调整为：先完善中文版商业版，再考虑海外英文版、官网和更多平台。

商业版不是另做一个独立儿童 App，也不是从零开始。现有 PC + Android 用眼统计、提醒、WiFi 同步、多语言底座继续保留，并作为同一个 App 的基础能力。

后续产品应按“用眼健康 App”理解，包含两个模式：

- 个人护眼模式：面向成人自己使用，继续提供跨设备用眼统计、提醒、趋势和护眼表现。
- 家庭护眼模式：面向家长管理孩子设备，在个人护眼能力基础上增加孩子档案、App 使用时长、App 限制、总时长规则和家长报告。

当前个人护眼模式建议作为基线版本保存：

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

新增儿童控制商业版第一阶段合规底稿：

- `docs/compliance/2026-07-08-child-control-compliance-draft.md`

新增儿童控制商业版竞品分析底稿：

- `docs/market/2026-07-08-child-control-competitor-analysis.md`

新增儿童控制商业版路线图与第一阶段范围：

- `docs/superpowers/specs/2026-07-08-child-commercial-edition-roadmap-design.md`

更新主路线图：

- `docs/ROADMAP.md`

主路线图已经改为“中文版商业版优先，个人护眼模式 + 家庭护眼模式共用同一个 App”，英文实装后置。第一阶段合规底稿已经列出数据清单、权限说明、家长同意流程、平台能力边界和审核风险。

已确认的儿童控制商业版关键决策：

- 上架主体使用公司主体。
- 首发渠道为国内应用市场。
- 中国版使用境内服务器。
- 首版不接广告。
- 成人用眼健康也有商业价值，家庭护眼模式付费意愿更强，但不应把成人版和家庭版拆成两个独立 App。
- 儿童范围按低于 18 岁处理。
- 孩子档案首版只强制昵称。
- 中国版短信登录和微信登录为必选，邮箱登录可选。
- Apple 登录跟 iOS 版一起考虑。
- 中国版支付首选内置微信支付和支付宝支付。
- 暂不需要家长网页后台，也暂不预留学校或机构入口。
- 后续可以考虑与学习产品、护眼产品、儿童硬件等同用户群产品做联名推广，例如合作方购买或赠送本应用会员。
- 联名推广需要预留兑换码、渠道来源、批量授权和数据边界，默认不把儿童使用数据回传给合作方。
- 竞品判断中补充：手机厂商可能有系统级能力，但入口弱、用户感知弱，不能等同于已经占住家长控制市场。
- 小米“未成年模式”真机观察：第一步要求使用未注册小米账号的手机进行注册或初始化，说明系统能力存在，但对存量设备快速接入不友好。
- 家庭护眼模式的正式 v1 产品闭环必须从一开始包含账号、家庭空间、孩子设备绑定、云同步和家长手机查看。
- 第一轮代码可以先做本地家庭模式数据模型、孩子档案、年龄段、家长密码和入口，但这只是开发步骤，不能把“只在孩子设备本地设置规则”当成首发目标。
- 家长手机应能查看孩子使用情况并修改规则；规则同步到孩子设备后，由孩子设备本地执行。孩子设备离线时按最后一次规则继续执行，恢复网络后再补传记录和补收规则。

## 最近一次个人版基线提交

```text
25eb81c 记录儿童版路线与个人版基线
```

本轮文档提交状态以下次接手时的 `git status --short --branch` 和 `git log -1 --oneline` 为准。

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
- 商业版涉及账号、支付、后台管理时，优先复用已购买模板和成熟开源库，不从零开始写。

## 当前重要文档

- `docs/ROADMAP.md`
- `docs/DEVELOPMENT_GUIDE.md`
- `docs/VERSIONING.md`
- `docs/compliance/2026-07-08-child-control-compliance-draft.md`
- `docs/market/2026-07-08-child-control-competitor-analysis.md`
- `docs/superpowers/specs/2026-07-08-child-commercial-edition-roadmap-design.md`
- `docs/superpowers/specs/2026-07-08-android-family-eye-mode-v1-scope.md`
- `docs/superpowers/specs/2026-07-08-android-family-eye-mode-v1-code-audit.md`
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
- 家庭护眼模式的数据标记字段尚未落代码；后续需要考虑 `productMode`、`dataSchemaVersion`、`syncProtocolVersion`、`clientVersion`、`deviceRole`。

## 下一步建议

1. 继续统一“同一 App 的个人护眼模式 + 家庭护眼模式”口径。
2. 用真机应用商店核验家庭护眼和儿童控制类竞品，尤其是华为、小米、OPPO、vivo。
3. 先更新 Android 家庭护眼 v1 范围和代码反查清单，明确“第一轮本地模型”和“v1 产品闭环”不是同一件事。
4. 根据更新后的代码反查清单，写第一轮实施计划。
5. 第一轮只覆盖家庭模式基础数据模型、孩子档案、年龄段、家长密码和家庭模式入口。
6. 紧接着规划账号、家庭空间、孩子设备绑定、云同步、家长手机报告和规则下发。
7. 进入账号、支付、后台管理前，先评估 ShipAny 模板、Sa-Token、Shiro 等可复用资源。
8. 不急着写最终隐私政策、用户协议或应用商店数据安全表，等核心功能和第三方服务稳定后再定稿。
9. 继续维护现有 PC + Android 个人护眼模式稳定性，但不要把 Windows 做成孩子设备限制主平台。

## 2026-07-09 最新交接补充

### 本轮新增代码改动

- Android 统计页卡顿排查后，已在 `EyeTimeStore` 中新增过去日期的每日统计结果缓存。
- 过去日期会保存总时长、每小时分布、连续使用、电脑/手机来源数据等结果；今天仍实时计算。
- `EyeTimeService` 启动、跨过零点、同步完成后会后台整理过去日期。
- `MainActivity` 主界面刷新从约每秒一次改为约 10 秒一次，连接检查从约 5 秒一次改为约 60 秒一次。
- `StatsActivity` 本周电脑/手机来源数据改为一次读取一整周，减少重复计算。
- Android APK 已构建并安装成功到设备 `660005a0`。

### 本轮新增规划

- 新增 `docs/superpowers/plans/2026-07-09-android-family-eye-mode-round1.md`。
- 该文档明确家庭护眼模式分轮推进：
  1. 第 1 轮：家庭模式本地底座。
  2. 第 1.5 轮：Android 标准项目化。
  3. 第 2 轮：Android App 使用统计。
  4. 第 3 轮：本地规则与限制。
  5. 第 4 轮：账号、家庭空间、云同步。
  6. 第 5 轮：支付、会员、上架准备。
- 正式家庭护眼 v1 至少要完成到第 4 轮，不能只停留在孩子设备本地设置。

### 下一步

用户已确认要先保存当前状态，然后开新对话继续。

新对话建议从以下顺序开始：

1. 先确认分支和未提交状态。
2. 阅读本交接文件。
3. 如果当前提交已完成，开始执行 `docs/superpowers/plans/2026-07-09-android-family-eye-mode-round1.md` 的 Task 1。
4. Task 1 只做家庭模式数据模型：`ProductMode`、`DeviceRole`、`ChildProfile`，以及 `EyeTimeStore` 的本地读写。
5. 先不做 UI、不做账号、不做云同步、不做支付、不做 App 限制。
6. 每次 Android 代码改动后，构建 APK 并安装到已连接手机。
