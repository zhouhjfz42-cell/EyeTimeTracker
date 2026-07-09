# Android 家庭护眼 v1 代码反查清单

更新时间：2026-07-08

## 结论

现有 Android 端已经有个人护眼模式的核心基础：本地用眼统计、前台服务、提醒、横幅通知、统计页、护眼摘要、多语言生成资源、PC 同步和合并去重。

家庭护眼 v1 不是从零开始，但以下能力目前还没有：家庭模式入口、孩子档案、年龄段、App 级使用时长、单 App 限制、每日总时长限制、家长密码、限制页、使用情况访问权限引导。

产品层面要特别注意：家庭护眼 v1 的正式闭环不能只是“家长拿孩子手机本地设置”。首发目标应包含账号、家庭空间、孩子设备绑定、云同步、家长手机查看孩子数据、家长手机修改规则、孩子设备本地执行规则。第一轮代码可以先做本地模型和入口，但这只是开发步骤，不是最终 v1 范围。

建议第一轮代码不要直接做完整限制系统，而是先做：

1. 家庭模式数据字段和本地存储。
2. 家庭模式入口和孩子档案。
3. 使用情况访问权限检测和引导。
4. App 使用时长读取的独立封装。

这四步完成后，再做总时长规则、单 App 限制和限制页。
随后必须进入账号、家庭空间、孩子设备绑定、云同步、家长设备报告和规则下发，否则家庭护眼模式无法形成正式产品闭环。

## 当前代码基础

### 1. Android 清单和权限

文件：

- `android/EyeTimeTrackerAndroid/AndroidManifest.xml`

已有：

- 前台服务权限。
- 网络权限。
- 通知权限。
- 全屏提醒权限。
- `MainActivity`、`StatsActivity`、`ReminderActivity`。
- `EyeTimeService` 前台服务。

缺口：

- 没有使用情况访问相关声明或引导。
- 没有悬浮窗相关能力。
- 没有设备管理器能力。
- 没有无障碍服务。
- 没有家庭模式、家长端或孩子端专用 Activity。

判断：

- v1 如果要做 App 使用时长和当前 App 判断，必须新增使用情况访问的检测和设置页引导。
- v1 暂不建议默认引入无障碍服务，审核和用户信任风险更高。

### 2. 主界面

文件：

- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/MainActivity.java`

已有：

- 手写原生 UI。
- 今日、昨日、本周、本月、提醒卡片。
- 统计页入口。
- PC 配对和断开入口。
- 提醒设置弹窗。
- 通知权限请求。
- 前台服务启动。
- 连接状态刷新。

缺口：

- 没有模式选择入口。
- 没有家庭护眼入口。
- 没有孩子档案 UI。
- 没有家长密码设置或验证。
- 没有 App 限制规则 UI。
- 没有权限总览页。

可复用：

- 标题、卡片、按钮、弹窗、颜色、字体、布局风格。
- 家庭模式的孩子档案弹窗、家长密码弹窗、规则设置弹窗，可以沿用当前 `Dialog` 风格。

建议：

- 不要把家庭模式直接塞进当前主界面所有卡片中。
- 建议先新增一个“模式/家庭护眼入口”，进入单独的家庭模式首页或设置页。

### 3. 前台统计服务

文件：

- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeService.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ActivityDecision.java`

已有：

- 每 10 秒检查一次。
- 亮屏 + 近期动作计入统计。
- 亮屏 + 媒体播放计入统计。
- 熄屏不计入统计。
- 亮屏但长时间静止且无媒体播放暂停统计。
- 保存当天使用时长。
- 保存本机提醒状态。
- 达到提醒阈值后触发提醒。
- 配对后定期同步。

缺口：

- 不知道当前正在使用哪个 App。
- 不记录 App 包名、App 名称或 App 图标。
- 不判断某个 App 是否已达到限制。
- 不显示限制页。
- 不阻止或劝退某个 App。

可复用：

- 总用眼时间统计可以继续作为家庭模式的总时长基础。
- 提醒策略可以作为“接近限制先提醒”的基础。
- 前台服务保活和通知机制可以继续复用。

建议：

- App 级统计不要直接混进 `EyeTimeService` 主逻辑里，先新增独立封装，例如 `AppUsageReader` 或 `AppUsageRepository`。
- 限制规则判断可以后续新增独立类，例如 `FamilyLimitPolicy`，不要直接写死在服务里。

### 4. 本地存储

文件：

- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`

已有：

- 使用 `SharedPreferences` 保存状态。
- `state_json` 保存日记录和时间片。
- 设备 ID。
- 提醒设置。
- PC 同步设置。
- 本机和对端提醒状态。
- 今日、昨日、本周、本月显示重置基线。
- 时间片合并和读取。

缺口：

- 没有 `productMode`。
- 没有 `familyId`。
- 没有 `childId`。
- 没有 `ageBand`。
- 没有设备角色。
- 没有孩子档案。
- 没有 App 使用记录。
- 没有总时长限制规则。
- 没有单 App 限制规则。
- 没有夜间规则。
- 没有家长密码。
- 没有会员方案字段。

可复用：

- `SharedPreferences` 和 JSON 状态适合 v1 原型继续使用。
- 时间片结构适合继续做总用眼统计。
- `dataSchemaVersion` 可以先落在 `state_json` 根对象上。

建议：

- v1 可以继续使用本地 JSON，不急着引入数据库。
- 但家庭模式数据应单独分区，不要直接塞进现有 `records`。
- 本地字段要同时考虑未来云同步，避免后面再推翻数据结构。
- 建议新增根字段：
  - `productMode`
  - `dataSchemaVersion`
  - `families`
  - `children`
  - `familyRules`
  - `appUsageRecords`
  - `familyId`
  - `childId`
  - `deviceRole`
  - `lastCloudSyncAt`

### 5. 统计页和报告

文件：

- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/StatsActivity.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeCareSummaryFormatter.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/DeviceUsageBreakdown.java`

已有：

- 单日统计。
- 24 小时分布。
- 本周用眼。
- 月度趋势。
- 连续使用分布。
- 昨日或当日摘要。
- 护眼表现文本。
- 设备来源比例。

缺口：

- 没有 App 使用排行。
- 没有孩子报告视角。
- 没有年龄段相关建议。
- 没有规则完成情况。
- 没有剩余可用时间。
- 没有家长端报告首页。

可复用：

- 统计页图表风格、日期选择、卡片样式可以复用。
- 护眼摘要规则可以扩展成家庭模式的规则建议。

建议：

- 家庭模式 v1 不要直接改现有统计页结构。
- 先新增家庭报告区域或家庭统计页，避免破坏个人护眼模式。
- App 使用排行应作为家庭模式新增图表，不影响现有 PC/手机来源图表。

### 6. 提醒和限制提示

文件：

- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ReminderPolicy.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ReminderActivity.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ReminderAlert.java`
- `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ReminderNotificationProfile.java`

已有：

- 到达用眼时长提醒。
- 一天一次或反复提醒。
- 横幅通知、声音和振动。
- 全屏提醒 Activity。
- 应用内弹窗提醒。

缺口：

- 没有“接近限制”的提前提醒。
- 没有“达到总时长限制”的休息页。
- 没有“达到单 App 限制”的限制页。
- 没有孩子可理解的规则解释文案。
- 没有家长批准延长时间。

可复用：

- 通知渠道和全屏提醒机制可以复用。
- `ReminderActivity` 的视觉风格可以作为休息页基础。

建议：

- 新增 `LimitActivity` 或 `BreakActivity`，不要直接把 `ReminderActivity` 改成多用途巨型页面。
- 限制页第一版先做“解释 + 知道了/休息一下”，后续再做申请延长。

### 7. PC 同步

文件：

- `AndroidSyncRunner.java`
- `AndroidSyncClient.java`
- `AndroidPairingClient.java`
- `AndroidPcDiscoveryClient.java`
- `SyncSettings.java`

已有：

- PC 配对。
- 自动发现和重连。
- 时间片同步。
- 提醒设置同步。
- 断开连接。

缺口：

- 没有家庭模式云同步。
- 没有家长设备和孩子设备配对。
- 没有家庭规则同步。
- 没有 App 使用记录同步。

判断：

- PC 同步机制可以作为后续同步设计参考，但不应直接承担家庭模式账号同步。
- 家庭模式应为未来云同步预留字段，不要把家长/孩子同步绑定到现有 PC 局域网协议里。

### 8. 账号、家庭空间和云同步

当前 Android 端没有：

- 用户登录。
- 家庭空间。
- 家长设备与孩子设备绑定。
- 云端上传孩子设备使用记录。
- 家长设备读取孩子报告。
- 家长设备下发规则到孩子设备。
- 离线后补传记录和补收规则。

判断：

- 这些能力属于家庭护眼 v1 的正式产品闭环，不应只作为远期能力。
- 但第一轮代码仍可先做本地模型、入口和权限读取，避免一开始被账号和服务器拖住。
- 开始账号和云同步前，应先评估 `C:\Users\zhouh\Documents\trae\shipany-template-two`、Sa-Token、Apache Shiro 等可复用资源。

### 9. 多语言

文件：

- `i18n/source`
- `i18n/generated/android/values/strings.xml`
- `i18n/generated/android/values-en/strings.xml`
- `android/EyeTimeTrackerAndroid/res/values/strings.xml`

已有：

- Android 主要文案已通过生成资源接入。
- 中文和英文资源已有基础结构。

缺口：

- 家庭模式、孩子档案、年龄段、权限说明、限制页、规则建议等文案还没有。

建议：

- 所有家庭模式新增文字必须先进入 i18n 源文案，再生成 Android 资源。
- 不要在 Java 代码中散落新中文文案。

### 10. 测试基础

文件：

- `android/EyeTimeTrackerAndroid/tests/com/eyetimetracker/android/CoreLogicTest.java`

已有：

- 计时判断测试。
- 提醒规则测试。
- 时间片合并和去重测试。
- 设备来源统计测试。
- 同步协议测试。
- 连接状态测试。

缺口：

- 没有家庭模式数据字段测试。
- 没有年龄段测试。
- 没有 App 使用记录聚合测试。
- 没有总时长限制规则测试。
- 没有单 App 限制规则测试。
- 没有家长密码校验测试。

建议：

- 家庭护眼 v1 的第一批代码应先补纯逻辑测试。
- 优先测试这些类：
  - `FamilyProfile`
  - `FamilyModeSettings`
  - `AppUsageRecord`
  - `DailyTotalLimitRule`
  - `AppLimitRule`
  - `FamilyLimitPolicy`

## 功能反查表

| v1 能力 | 当前状态 | 可复用位置 | 建议 |
| --- | --- | --- | --- |
| 家庭模式入口 | 无 | `MainActivity` UI 风格 | 新增入口，不直接改乱个人主界面 |
| 孩子档案 | 无 | `EyeTimeStore` 存储方式 | 新增本地模型和 JSON 字段 |
| 年龄段 | 无 | 无 | 新增 `ageBand`，可选但建议填写 |
| 设备角色 | 仅有 PC/Android 同步概念 | `SyncSettings` 可参考 | 新增独立 `deviceRole`，不要绑死 PC 同步 |
| App 使用时长 | 无 | 无 | 新增使用情况访问读取封装 |
| 总时长规则 | 提醒可复用 | `ReminderPolicy` 思路 | 新增限制规则，不直接复用提醒字段 |
| 单 App 限制 | 无 | 无 | 新增规则和当前 App 判断 |
| 夜间规则 | 统计页有夜间计算 | `StatsActivity` 夜间逻辑 | 抽出公共规则，避免只在 UI 内计算 |
| 休息提醒 | 已有 | `ReminderPolicy`、`ReminderActivity` | 可复用通知和弹窗机制 |
| 家长密码 | 无 | `EyeTimeStore` 可存本地设置 | 新增本地密码，后续再接账号 |
| 权限提示 | 只有通知权限 | `MainActivity.requestNotificationPermission` | 新增使用情况访问、后台、电池优化提示 |
| 限制页 | 无 | `ReminderActivity` 风格 | 新增独立 `LimitActivity` |
| 护眼建议 | 基础规则已有 | `EyeCareSummaryFormatter` | 先扩展规则建议，AI 后置 |
| 多语言 | 有基础 | `i18n/source` | 家庭文案全部集中接入 |

## 建议第一轮开发顺序

1. 新增家庭模式基础数据模型。

   目标：先让 `productMode`、孩子昵称、`ageBand`、本机家长密码能保存和读取。

2. 新增家庭模式入口和孩子档案设置。

   目标：用户能从个人模式进入家庭模式，创建孩子档案。

3. 新增使用情况访问权限检测和引导。

   目标：家长知道为什么要开权限，以及如何打开。

4. 新增 App 使用时长读取封装。

   目标：先能读出今日 App 使用排行，不急着限制。

5. 新增 App 使用排行 UI。

   目标：让家长先看到孩子在用什么 App。

6. 再做总时长限制和单 App 限制。

   目标：先提醒和解释，再逐步做限制页。

## v1 产品闭环后续顺序

第一轮本地代码完成后，建议继续按这个顺序补齐正式 v1：

1. 评估并选定账号、家庭空间和服务端复用方案。
2. 新增家长账号登录和家庭空间。
3. 新增孩子设备绑定流程。
4. 孩子设备上传使用记录和设备状态。
5. 家长设备显示孩子总用屏、App 排行和护眼报告。
6. 家长设备修改规则后同步到孩子设备。
7. 孩子设备离线时继续按最后一次规则执行，恢复网络后补传和补收。
8. 再进入支付、会员和更复杂的家庭协作能力。

## 暂不建议第一轮做

- 不先做远程家长端，但它必须进入 v1 产品闭环后续顺序。
- 不先做支付。
- 不先做云同步，但它必须进入 v1 产品闭环后续顺序。
- 不先做复杂 AI。
- 不先做无障碍服务。
- 不先做多孩子复杂管理。
- 不先动 PC 端。

## 下一步

下一步应写一份实施计划，范围只覆盖第一轮：

- 家庭模式基础数据模型。
- 孩子档案。
- 年龄段。
- 家长密码。
- 家庭模式入口。

这一步完成后，再进入 App 使用时长读取和权限引导。
