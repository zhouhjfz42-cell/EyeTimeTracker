# Android 家庭护眼模式第一轮实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Android 个人护眼能力基础上，加入家庭护眼模式的本地底座，为后续 App 使用统计、规则限制、账号、云同步和商业化做准备。

**Architecture:** 第一轮只建立产品模式、设备角色、孩子档案、本地家长密码和家庭模式入口，不接账号、不接云同步、不接支付、不做真正 App 限制。数据结构先预留 `productMode`、`deviceRole`、`familyId`、`childId`、`dataSchemaVersion` 等字段，避免后续接云同步时推翻重做。

**Tech Stack:** Android Java 原生界面、现有 `EyeTimeStore` 本地 JSON 存储、现有多语言资源结构、现有手工 APK 构建脚本。第 1.5 轮再做标准 Android Studio / Gradle 项目化。

---

## 一、轮次定位

家庭护眼模式不作为独立 App 开发，而是在同一个 EyeTimeTracker App 中增加一个模式：

- 个人护眼模式：成年人自己使用，继续保留现在的手机/电脑用眼统计、提醒、趋势、WiFi 同步。
- 家庭护眼模式：家长管理孩子手机和平板，在个人护眼能力基础上增加孩子档案、设备角色、App 使用统计、规则限制和家长报告。

第一轮是“本地底座”，不是正式家庭版。正式家庭护眼 v1 至少需要完成到第 4 轮，也就是账号、家庭空间、孩子设备绑定、云同步、家长手机查看和规则下发。

## 二、总轮次安排

1. 第 1 轮：家庭模式本地底座
2. 第 1.5 轮：Android 标准项目化
3. 第 2 轮：Android App 使用统计
4. 第 3 轮：本地规则与限制
5. 第 4 轮：账号、家庭空间、云同步
6. 第 5 轮：支付、会员、上架准备

第 1 轮完成后再做第 1.5 轮项目化，是为了先确定 App 会长期保留“个人护眼 + 家庭护眼”双模式，再把 Android 工程改成适合上架的标准结构。

## 三、第 1 轮范围

第 1 轮必须完成：

- App 内增加产品模式：个人护眼、家庭护眼。
- App 内增加设备角色：个人设备、家长设备、孩子设备、PC 伴随设备。
- 增加家庭模式入口。
- 增加本地孩子档案。
- 孩子档案只要求昵称必填，年龄段可选。
- 增加本地家长密码，用于保护家庭模式设置入口。
- 预留家庭空间、孩子 ID、设备角色、数据版本字段。
- 不影响现有个人护眼模式。
- 不影响现有 WiFi 同步、提醒、统计页和通知。

第 1 轮不做：

- 不做账号登录。
- 不做短信登录、微信登录。
- 不做云同步。
- 不做支付。
- 不做会员。
- 不做 App 使用时长读取。
- 不做 App 限制。
- 不做家长远程查看。
- 不做儿童设备强控制。
- 不做标准 Android 项目化。

## 四、数据结构设计

### 1. 产品模式

字段：`productMode`

可选值：

- `personal`：个人护眼模式。
- `family`：家庭护眼模式。

默认值：`personal`

旧用户升级后应继续进入个人护眼模式，不弹出家庭模式流程，避免打断现有用户。

### 2. 设备角色

字段：`deviceRole`

可选值：

- `personal_device`：个人设备。
- `parent_device`：家长设备。
- `child_device`：孩子设备。
- `pc_companion`：PC 伴随设备。

第一轮只在 Android 本地保存角色，不要求真的和服务器绑定。

### 3. 家庭空间

字段：`familyId`

第一轮可生成本地临时 ID，例如 `local-family-<uuid>`。后续接账号后，云端家庭空间 ID 替换或映射到这个本地 ID。

### 4. 孩子档案

建议结构：

```json
{
  "childProfiles": [
    {
      "childId": "local-child-uuid",
      "nickname": "小明",
      "ageBand": "7-9",
      "createdAtUnixSeconds": 1783526400,
      "updatedAtUnixSeconds": 1783526400
    }
  ],
  "activeChildId": "local-child-uuid"
}
```

规则：

- `nickname` 必填。
- `ageBand` 可选。
- 不收集生日。
- 不要求头像。
- 第一轮界面可以只支持一个孩子，但数据结构必须允许多个孩子。

年龄段建议值：

- `3-6`
- `7-9`
- `10-12`
- `13-15`
- `16-17`
- `unknown`

如果用户不填年龄段，保存为 `unknown`。

### 5. 本地家长密码

建议字段：

```json
{
  "parentPasscodeHash": "hash-value",
  "parentPasscodeSalt": "salt-value",
  "parentPasscodeUpdatedAtUnixSeconds": 1783526400
}
```

第一轮只做本地密码保护，后续接账号后再升级为账号验证。

原则：

- 不保存明文密码。
- 密码只保护家庭模式设置入口。
- 不用于账号登录。
- 如果用户忘记密码，第一轮可以允许清除本地家庭模式数据后重设，具体交互后续再定。

### 6. 数据版本

新增字段：

- `dataSchemaVersion`
- `syncProtocolVersion`
- `clientVersion`

第一轮只保存，不做复杂迁移。这样后续云同步、标准项目化和数据升级时有识别基础。

## 五、界面入口设计

第一轮建议只加最少入口，避免打乱当前主界面。

### 1. 主界面入口

在 Android 主界面增加一个轻量入口，例如：

- “家庭护眼”
- 或右上角/底部入口进入“模式与家庭设置”

点击后进入家庭护眼设置流程。

### 2. 首次进入家庭护眼

流程：

1. 展示简短说明：家庭护眼用于帮助家长了解和管理孩子设备用眼。
2. 选择当前设备角色：
   - 这是家长手机。
   - 这是孩子手机/平板。
3. 创建孩子档案：
   - 昵称必填。
   - 年龄段可跳过。
4. 设置本地家长密码。
5. 完成后进入家庭护眼本地首页。

### 3. 家庭护眼本地首页

第一轮只显示基础状态：

- 当前模式：家庭护眼。
- 当前设备角色。
- 当前孩子昵称。
- 年龄段。
- 本地家长密码已设置。
- 后续功能提示：App 使用统计、规则限制、云同步会在后续版本加入。

这页不是最终产品页，只是为了验证数据和入口。

## 六、与现有个人模式的关系

第一轮必须保证：

- 默认仍是个人护眼模式。
- 老用户打开 App 不被强制进入家庭模式。
- 个人模式下不申请家庭模式相关权限。
- 个人模式下主界面、统计页、提醒、WiFi 同步不变。
- 家庭模式入口可以进入，但未配置家庭模式时不影响个人统计。

如果用户进入家庭模式后返回个人模式，个人模式数据不能丢失。

## 七、第 1.5 轮 Android 标准项目化

第 1 轮完成后，单独进行第 1.5 轮。

目标：

- 改成标准 Android Studio / Gradle 项目。
- 保留现有 Java 代码和资源。
- 保留现有包名。
- 建立 debug / release 构建。
- 支持 APK。
- 为后续 AAB 做准备。
- 配置版本号。
- 准备正式签名结构。
- 保留当前手工构建脚本一段时间作为备用。

验收标准：

- Android Studio 可以打开项目。
- debug APK 可以安装。
- 现有个人护眼功能不变。
- 第 1 轮家庭模式入口和本地数据仍可用。

第 1.5 轮不引入新产品功能，只换工程结构。

## 八、实施任务

### Task 1: 增加家庭模式数据模型

**Files:**

- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ProductMode.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/DeviceRole.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ChildProfile.java`

- [ ] 新增 `ProductMode`，包含 `PERSONAL` 和 `FAMILY`。
- [ ] 新增 `DeviceRole`，包含 `PERSONAL_DEVICE`、`PARENT_DEVICE`、`CHILD_DEVICE`、`PC_COMPANION`。
- [ ] 新增 `ChildProfile`，包含 `childId`、`nickname`、`ageBand`、`createdAtUnixSeconds`、`updatedAtUnixSeconds`。
- [ ] 在 `EyeTimeStore` 中增加读取和保存产品模式的方法。
- [ ] 在 `EyeTimeStore` 中增加读取和保存设备角色的方法。
- [ ] 在 `EyeTimeStore` 中增加读取和保存孩子档案的方法。
- [ ] 确保默认 `productMode` 为 `personal`。
- [ ] 确保默认 `deviceRole` 为 `personal_device`。

验证：

- 新安装时默认仍是个人模式。
- 旧数据升级后默认仍是个人模式。
- 保存孩子档案后重新打开 App 仍能读到。

### Task 2: 增加本地家长密码

**Files:**

- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/EyeTimeStore.java`
- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/ParentPasscode.java`

- [ ] 新增本地家长密码保存结构。
- [ ] 使用随机 salt 保存密码摘要。
- [ ] 不保存明文密码。
- [ ] 增加 `hasParentPasscode()`。
- [ ] 增加 `saveParentPasscode(String passcode)`。
- [ ] 增加 `verifyParentPasscode(String passcode)`。

验证：

- 设置密码后可以验证成功。
- 错误密码验证失败。
- 重新打开 App 后验证仍然有效。

### Task 3: 增加家庭护眼入口

**Files:**

- Modify: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/MainActivity.java`
- Modify: `i18n/source/zh-CN.json`
- Modify: `i18n/source/en-US.json`

- [ ] 在 Android 主界面增加“家庭护眼”入口。
- [ ] 入口文案接入多语言资源。
- [ ] 入口不影响原有“统计页”按钮。
- [ ] 入口不遮挡现有主界面文字。

验证：

- 主界面布局不出现遮挡。
- 点击入口能进入家庭护眼设置流程。
- 个人护眼统计、提醒、统计页仍可正常使用。

### Task 4: 增加家庭护眼首次设置页

**Files:**

- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilySetupActivity.java`
- Modify: `android/EyeTimeTrackerAndroid/AndroidManifest.xml`
- Modify: `i18n/source/zh-CN.json`
- Modify: `i18n/source/en-US.json`

- [ ] 新增家庭护眼说明页。
- [ ] 增加设备角色选择。
- [ ] 增加孩子昵称输入。
- [ ] 增加年龄段选择，可跳过。
- [ ] 增加本地家长密码设置。
- [ ] 完成后保存 `productMode`、`deviceRole`、`childProfile`、`parentPasscode`。

验证：

- 昵称为空时不能继续。
- 年龄段跳过时保存为 `unknown`。
- 设置完成后返回家庭护眼本地首页。

### Task 5: 增加家庭护眼本地首页

**Files:**

- Create: `android/EyeTimeTrackerAndroid/src/com/eyetimetracker/android/FamilyHomeActivity.java`
- Modify: `android/EyeTimeTrackerAndroid/AndroidManifest.xml`
- Modify: `i18n/source/zh-CN.json`
- Modify: `i18n/source/en-US.json`

- [ ] 显示当前模式。
- [ ] 显示当前设备角色。
- [ ] 显示孩子昵称。
- [ ] 显示年龄段。
- [ ] 显示本地家长密码已设置。
- [ ] 显示后续功能提示。

验证：

- 设置完成后能看到保存的信息。
- 关闭重开 App 后信息仍然存在。
- 个人护眼入口仍然可用。

### Task 6: 更新文档与交接

**Files:**

- Modify: `docs/ROADMAP.md`
- Modify: `docs/handoffs/CURRENT.md`
- Modify: `docs/superpowers/specs/2026-07-08-android-family-eye-mode-v1-scope.md`

- [ ] 记录第 1 轮已开始。
- [ ] 明确第 1 轮只做本地底座。
- [ ] 明确第 1.5 轮做标准 Android 项目化。
- [ ] 明确正式家庭护眼 v1 至少需要完成到第 4 轮。

验证：

- 文档没有把第 1 轮误写成正式家庭版。
- 文档没有承诺账号、云同步或支付已经完成。

## 九、检查方式

第 1 轮是 Android 代码改动，完成每个代码任务后需要：

- 构建 Android APK。
- 安装到已连接手机。
- 检查个人护眼主界面。
- 检查统计页。
- 检查提醒设置入口。
- 检查家庭护眼入口。
- 检查家庭护眼设置流程。
- 检查退出并重新打开后数据是否保存。

如果修改到 PC 代码，才需要构建 PC 版。本计划第 1 轮原则上不改 PC 代码。

## 十、风险

- 当前 Android 项目还不是标准 Android Studio / Gradle 项目，新增 Activity 和资源时要谨慎维护手工构建脚本兼容性。
- 当前 Android UI 是 Java 手写布局，新增页面容易出现文字遮挡，需要真机检查。
- 本地家长密码只是第一轮保护方式，不能当作正式账号安全体系。
- 第一轮不读取 App 使用时长，所以不能向用户展示“已可管理孩子 App 使用”。
- 第一轮不做云同步，所以家长手机还不能远程查看孩子设备。

## 十一、完成定义

第 1 轮完成时，应满足：

- 个人护眼模式仍可正常使用。
- Android App 内可以进入家庭护眼入口。
- 可以选择设备角色。
- 可以创建孩子档案。
- 可以设置本地家长密码。
- 可以看到家庭护眼本地首页。
- 所有新增文案集中进入多语言资源。
- APK 能构建并安装到测试手机。

完成后再进入第 1.5 轮 Android 标准项目化。
