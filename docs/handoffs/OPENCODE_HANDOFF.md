# EyeTimeTracker OpenCode 交接文件

更新时间：2026-08-18

这份文件用于帮助 OpenCode 接手 EyeTimeTracker 的后续开发。接手前请先阅读本文件、`docs/handoffs/CURRENT.md`、`README.md` 和 `docs/DEVELOPMENT_GUIDE.md`，然后再检查 Git 状态和实际代码。

## 1. 项目身份

### 真实项目位置

```
C:/Users/zhouh/Documents/Codex/2026-06-26/new-chat
```

这是当前项目的真实 Git 工作区。后续修改应在这个目录中进行。不要把 `C:/Users/zhouh/Documents/Codex/2026-07-09/eyetimetracker-c-users-zhouh-documents-codex` 当成新的独立项目，也不要复制出另一份后继续开发。

### GitHub

- 仓库：<https://github.com/zhouhjfz42-cell/EyeTimeTracker>
- 远程名：`origin`
- 当前分支：`codex-wifi-sync-foundation`
- 当前 HEAD：`68c1c41 优化应用排行与家庭同步状态`

当前工作区存在大量未提交改动。接手时必须先运行：

```powershell
Set-Location 'C:/Users/zhouh/Documents/Codex/2026-06-26/new-chat'
git status --short --branch
git diff --stat
```

不要切回 `master`，不要覆盖或回滚已有未提交改动。提交和推送必须等用户明确要求。

### 设备约定

- 小米 15：Android 调试序列号 `660005a0\)，主要作为家长端或个人端测试机。
- 红米 K40：Android 调试序列号 `efe1f62\)，主要作为儿童端测试机。

## 2. 产品定位

EyeTimeTracker 是一个跨设备用眼统计和提醒工具，核心价值是把 Windows PC 与 Android 手机的屏幕使用时间放在一起统计，并减少两端同时使用时的重复计算。

产品不是单纯的儿童控制工具，也不是医学诊断软件。当前产品分为同一个 App 内的两个模式：

1. **个人护眼模式**
   - 面向成人自己使用。
   - 统计 PC 和手机合计用眼时间。
   - 显示每日、每周、每月趋势、24 小时分布、App 使用排行和护眼摘要。
   - 提供累计用眼提醒、连续用眼提醒等轻提醒。

2. **家庭护眼模式**
   - 面向家长查看和管理孩子设备。
   - 家长端和儿童端仍使用同一个 App，只是设备角色不同。
   - 已有本地家庭档案、家长/儿童角色、绑定流程、保护密码、家庭首页、规则入口、孩子 App 使用排行和局域网同步基础。
   - 当前一对一绑定可用，一对多绑定暂未开放；家长端保留入口，但会提示当前服务尚未开放。
   - 正式商业版不能只停留在本地规则，最终必须包含账号、家庭空间、云同步、家长查看和规则下发。

## 3. 已完成的主要能力

### Windows PC 端

- WinForms 桌面应用。
- 根据键盘、鼠标和媒体播放活动统计屏幕使用时间，不把单纯开机时间当成用眼时间。
- 今日、昨日、本周、本月数据。
- 统计页：24 小时分布、本周用眼、月度趋势、护眼摘要、App 使用排行。
- PC 与 Android 配对、断开、自动重连和同步状态显示。
- 系统托盘菜单和开机启动入口。
- PC 提醒弹窗和提示音。
- 护眼提醒设置页：累计用眼提醒、连续用眼提醒、豁免时间。

### Android 端

- 原生 Java Android 应用，使用前台服务统计亮屏使用时间。
- 屏幕亮起时计时，息屏后停止计时；不使用摄像头判断用户是否真正注视屏幕。
- 今日、昨日、本周、本月数据和统计页。
- Android App 使用时长采集、已安装 App 过滤、App 使用排行。
- Android 横幅通知、文字提醒、声音和振动能力。
- 连续用眼提醒默认开启，默认 20 分钟。
- 走路使用手机提醒只做 Android 端，需要身体活动权限；应用只判断是否在走路，不记录路线。
- 自定义护眼提醒设置页和可复用的跨午夜时间选择器。
- 家庭护眼首页、家庭设置、孩子档案、保护密码、规则基础、儿童/家长角色。
- 儿童端修改设置需要保护密码；家长端可直接修改保护密码，不要求旧密码。

### 双端统计和同步

- 当前局域网同步主要支持一台 Windows PC 和一台 Android 手机。
- 配对使用设备身份、配对码和共享密钥。
- 发现和连接依赖同一可互通局域网，不需要用户输入 PC IP。
- 原有用户可见统计主要基于统一 10 秒时间格，重叠时间只算一次。
- PC/手机来源比例中，双方重叠部分按 50/50 分摊，这是当前产品口径。
- 已新增精确区间合并旁路实现，但尚未完全切换所有用户可见统计；见 `docs/superpowers/plans/2026-07-28-stats-engine-commercial-phase0.md`。
- 新产生的连续使用片段已经开始采用可延长的稳定 ID，减少每 10 秒新增一条记录造成的数据膨胀。
- 历史旧片段不能随意重写，避免同步后重复或丢失。

## 4. 关键目录和文件

```
src/EyeTimeTracker.Core/       # PC/共享统计、提醒、同步、数据模型
src/EyeTimeTracker.App/        # Windows WinForms 桌面端
tests/EyeTimeTracker.Tests/    # PC/Core 逻辑测试
android/EyeTimeTrackerAndroid/ # Android 原生 Java 应用和构建脚本
i18n/source/                   # 中文、英文源文案
i18n/generated/                # Android 和 .NET 生成文案
docs/                          # 设计、路线图、协议和交接文档
tools/StatsEngineComparison/   # 旧统计和精确区间统计对照工具
```

重点代码：

- PC 统计与页面：`src/EyeTimeTracker.App/Tracking/TrackingController.cs`、`src/EyeTimeTracker.App/UI/StatsForm.cs`
- PC 同步：`src/EyeTimeTracker.App/Sync/PcSyncCoordinator.cs`
- PC 提醒：`src/EyeTimeTracker.Core/Reminders/`、`src/EyeTimeTracker.App/UI/EyeCareRemindersForm.cs`
- Android 服务：`android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeService.java`
- Android 本地存储：`android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- Android 统计页：`android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/StatsActivity.java`
- Android 家庭页：`android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyHomeActivity.java`
- Android 同步：`AndroidSyncRunner.java`、`FamilyStatsLanClient.java`、`FamilyStatsLanServer.java`
- Android 提醒设置：`EyeCareRemindersActivity.java`、`ReminderPolicy.java`
- Android 连续提醒去重：`ContinuousReminderGuard.java`、`ContinuousReminderBaseline.java`
- 当前 PC 提醒诊断：`src/EyeTimeTracker.App/Diagnostics/ReminderDiagnosticLog.cs`

## 5. 百岁计划的关系

“百岁计划”是另一个独立的长期健康管理产品，正式项目目录是：

```
C:/Users/zhouh/Documents/Codex/2026-07-18/c-users-zhouh-documents-codex-2026/outputs/百岁计划-正式项目
```

两者的边界：

- 百岁计划负责更大的健康寿命管理方向，包括身体底账、长期风险、健康证据、任务和反馈闭环。
- EyeTimeTracker 是一个先独立解决“屏幕用眼时间和护眼提醒”这个单一问题的卫星应用。
- EyeTimeTracker 不是百岁计划的子目录，不是百岁计划的运行依赖，也不应合并到同一个 Git 仓库。
- 当前没有自动向百岁计划上传数据，也没有共享数据库、共享账号或共享 API。
- 未来如果需要整合，必须经过用户明确授权，并设计独立的数据授权、字段映射、撤销授权和版本兼容机制。
- 百岁计划资料已经明确：EyeTimeTracker 等卫星应用先独立解决单一健康问题，后续再经用户授权向百岁计划提供数据，正式同步系统不提前混入百岁计划 MVP。

当前优先级是把 EyeTimeTracker 做成稳定、可上架、可商业化的独立产品。不要为了关联百岁计划提前加入健康评分、医学结论或跨项目账号依赖。

## 6. 当前未完成和已知风险

### 数据和统计

- App 使用排行仍需要继续验证包名、系统进程、同名 App、重复记录和图标获取问题。
- App 排行是说明性数据，不应直接等同于总用眼时间；排行总和超过今日用眼时需要继续修正口径或展示方式。
- 旧 10 秒格与精确区间统计仍处于并行/渐进切换阶段，不能一次性悄悄改变所有历史结果。
- 统计页必须继续使用历史缓存，避免每次切换日期重新扫描全部原始片段。

### 提醒

- PC 和 Android 的连续用眼定义不同，跨端提醒可能存在秒级到分钟级差异。
- 用户曾观察过重复提醒、提醒过早、两端提醒时间差异较大、后台状态下提醒不稳定等现象。
- 任何提醒修复都要先看诊断日志，再修改触发条件；不要只通过“再加一个定时器”解决。
- Android 后台弹窗受系统后台启动限制影响；必要时应优先使用通知作为可靠兜底。

### Android 稳定性和同步体积

- 家庭同步和个人同步都曾出现后台响应不稳定、家长端请求不到儿童端、应用从家庭页退回首页或闪退的现场反馈。
- 当前同步仍可能携带较多历史片段和 App 使用记录。云端化前必须把“首页快速快照”和“统计详情”分开，避免打开首页就发送或解析大量历史数据。
- 建议优先解决有限大小的响应、增量/分页同步、首页总计快照和统计详情后台补齐，避免大 JSON、内存峰值和页面卡顿。

### 局域网

- 2.4G/5G 是否互通取决于路由器，不能只看 WiFi 名称相同。
- Windows 防火墙、访客网络、AP 隔离、Android 省电策略都可能影响发现和同步。
- 当前版本还不是云同步，不能把局域网在线状态当成账号在线状态。

### 商业化基础

- 没有账号系统、家庭空间云端模型、云同步、订单/支付、会员权益服务端校验。
- 没有正式网站、Google Play 上架流程、隐私政策定稿、用户协议、账号删除和 Data safety 材料。
- 英文文案底座已存在，但上架前需要逐屏检查并完成英文界面。
- 目前没有把 AI 分析接入产品；AI 摘要应放在统计和权限边界清楚之后，并限制成本和敏感数据传输。

## 7. 商业化方向和已确认决策

- 商业化以个人护眼为主，家庭护眼作为附加价值。
- 免费版保留基础个人手机合并能力。
- 首次登录后提供 3 天全功能试用。
- 试用结束后，统计页可以进入，但数据虚焦/遮罩，并显示付费引导。
- Pro 建议价格：`5 美元/月` 或 `50 美元/年`。
- Pro 赠送 1 个儿童设备名额；更多儿童设备以后再增加 `3 美元/月` 或 `30 美元/年` 的扩展价格。
- 走路用手机提醒只规划 Android；连续用眼提醒和累计用眼提醒按平台能力提供。
- 上架前必须完成英文界面、权限引导、崩溃和运行状态说明。
- Google Play 版本优先考虑 Google 登录和邮箱登录；手机号登录不是当前优先项，因为会带来短信服务、费用和区域合规成本。
- 邮箱登录不能把免费 Supabase 默认邮件能力直接当成生产方案，需要后续配置正式 SMTP/邮件服务或由 Cloudflare 侧承接邮件入口，开始前再核对服务限制。

## 8. 推荐后续路线

### P0：本地版稳定化

1. 读取本交接文件和 `CURRENT.md`，确认所有未提交改动的来源。
2. 追踪 Android 闪退、后台服务存活、同步响应大小和统计页卡顿。
3. 完善提醒诊断日志，记录触发判断、连续会话起点、上次提醒点、豁免时间、设备角色、通知/弹窗结果和失败原因；不要记录密码、完整 Token 或不必要的个人数据。
4. 用真实测试数据对比旧 10 秒统计和精确区间统计，决定按日期逐步切换。
5. 修正 App 排行过滤、去重、总时长口径和图标/文字降级展示。

### P1：统计和同步底座

1. 首页只请求小型总计快照，先显示上次缓存并标记更新时间。
2. 详细统计在进入统计页后按日期/范围增量获取，不阻塞首页绘制。
3. 同步优先使用上次成功地址，失败后再串行扫描；未来云端用设备心跳、增量游标或推送唤醒替代局域网扫描。
4. 家长端“强制获取最新数据”要有请求状态、超时、重试和儿童端未响应提示。
5. 设计与未来云服务兼容的数据边界，不继续依赖“家长端直接呼叫儿童端进程”作为唯一机制。

### P2：商业化后台和账号

1. 评估 `C:/Users/zhouh/Documents/trae/shipany-template-two` 的许可证、登录、权限、订阅、订单和官网能力。
2. 评估 Supabase 数据库/存储、Cloudflare Workers/Pages、邮件服务和 Google 登录的组合。
3. 先定义用户、设备、每日统计汇总、家庭、孩子档案、规则、会员权益和同步游标，再写迁移。
4. 服务端确认用户身份、价格、订单和权益，客户端只负责展示，不能信任客户端传入的会员状态。
5. Android 和 PC 都支持登录、拉取权益和云端恢复；局域网同步继续保留为离线/近场能力。

### P3：支付、上架和网站

1. Google Play Billing 订阅和服务端购买校验。
2. 3 天试用、Pro 权益、家庭赠送 1 个儿童设备、退款和恢复购买。
3. 英文界面逐屏检查。
4. 网站、隐私政策、用户协议、账号删除、客服入口和 Google Play Data safety。
5. 内测、崩溃收集、不同 Android 厂商权限引导和 Google Play 封闭测试。

### P4：百岁计划接口

只有在 EyeTimeTracker 商业版稳定后再做：

1. 定义可共享的最小字段，例如每日屏幕时间、连续使用最长时长、提醒响应情况。
2. 只通过用户主动授权共享，不上传原始 App 明细或家庭儿童数据，除非另有明确同意。
3. 用独立 API、授权记录、撤销机制和版本号隔离两个产品。
4. 先把 EyeTimeTracker 当作独立产品完成验证，不把百岁计划当成当前开发阻塞项。

## 9. 构建和验证

### PC

```powershell
dotnet build src\\EyeTimeTracker.App\\EyeTimeTracker.App.csproj -c Release
dotnet test tests\\EyeTimeTracker.Tests\\EyeTimeTracker.Tests.csproj -c Release
```

### Android

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android\\EyeTimeTrackerAndroid\\build-android.ps1
```

APK 输出：`outputs/android/EyeTimeTrackerAndroid-debug.apk`。

Android 代码修改后，按用户约定构建 APK，并在已连接设备上安装。文档改动不需要构建和安装 Android。

### 统计对照

```powershell
dotnet run --project tools\\StatsEngineComparison\\StatsEngineComparison.csproj -c Release -- `
  "$env:LOCALAPPDATA\\EyeTimeTracker\\state.json" `
  outputs\\stats-engine-comparison.md
```

重要原则：

- 没有实际运行的命令不能写成“已通过”。
- 真机未连接、账号/支付服务未配置、网络或权限导致的失败要单独说明。
- Android 独立 Java 测试受本机环境影响时，不能只凭 APK 编译通过就宣称全部逻辑已验证。
- 大改动前先建立 Git 检查点；不要在用户未要求时自动提交或推送。

## 10. 接手后的第一步

1. 打开 `C:/Users/zhouh/Documents/Codex/2026-06-26/new-chat`。
2. 阅读本文件、`docs/handoffs/CURRENT.md`、`README.md`、`docs/ROADMAP.md`。
3. 查看 `git status --short --branch`，确认未提交改动，不要覆盖它们。
4. 阅读最近的商业化和统计计划：
   - `docs/superpowers/specs/2026-07-28-commercial-v1-product-technical-plan.md`
   - `docs/superpowers/plans/2026-07-28-stats-engine-commercial-phase0.md`
   - `docs/superpowers/plans/2026-07-28-eye-care-reminders-prelaunch.md`
5. 如果修 Bug，先用日志或测试复现，再提出简短方案，然后修改最少的文件。
6. 涉及账号、权限、支付、数据库、云同步或数据迁移时，必须先做方案、风险和回滚说明。
7. 完成后用中文报告：完成内容、修改文件、实现方式、实际检查结果、未验证项目、影响风险、手动验收和回滚方式。

## 11. 回滚和安全边界

- 不使用 `git reset --hard`、`git checkout --` 或批量删除来“清理”工作区。
- 不删除旧统计数据，不静默覆盖个人历史数据，不把个人数据直接转换成孩子数据。
- 不把密码、API 密钥、数据库连接串、支付凭证或完整 Token 写入代码和日志。
- 数据库迁移必须可重复检查、兼容旧版本，并有明确回滚办法。
- 如果需要提交或推送，先向用户说明提交范围和验证结果，等用户明确授权。



