# EyeTimeTracker 多语言文案

这个目录用于集中管理 EyeTimeTracker 的界面文案，目标是避免按钮、弹窗、图表标签、系统托盘菜单等文字散落在代码里，方便后续增加英文、日文或其他语言版本。

## 目录结构

```text
i18n/
  source/
    zh-CN.json        # 中文源文案
    en-US.json        # 英文源文案
  generated/
    android/          # 生成给 Android 使用的资源
    dotnet/           # 生成给 PC/.NET 使用的资源
  tools/
    Update-I18nResources.ps1   # 文案检查和生成脚本
```

## 使用原则

- 所有新增界面文字优先写入 `source` 下的语言文件。
- PC 和 Android 代码尽量引用生成后的资源，不直接硬编码中文或英文。
- 文案键名应表达含义，不绑定某一个平台控件。
- 如果同一句话在 PC 和 Android 上确实需要不同长度，可以拆成两个键。
- 健康建议类文案不能写成医学诊断或治疗承诺。

## 当前状态

目前已经建立多语言基础结构，并开始把部分 PC 和 Android 文案接入集中管理。

已经完成：

- `source/zh-CN.json` 和 `source/en-US.json` 使用同一套键名。
- `tools/Update-I18nResources.ps1` 会检查两套语言键名是否一致。
- 生成脚本会检查 `{minutes}`、`{duration}` 这类占位符是否一致。
- Android 资源生成到 `generated/android/values*/strings.xml`。
- PC 资源生成到 `generated/dotnet/*.json`。
- PC 端优先读取 `generated/dotnet`，找不到时再回退到 `source`。
- Android 构建 APK 时会先生成文案资源，并把生成后的 Android strings 合入临时构建资源。

仍需要继续推进：

- 主界面所有文字。
- 统计页所有标题、图表标签、下拉项和提示文字。
- 提醒设置和提醒弹窗。
- 配对、断开、离线、同步状态。
- Android 系统通知标题、内容和通知渠道名称。
- PC 系统托盘菜单。
- 错误提示和确认弹窗。
- 日历、日期范围等控件中可控的文案。

## 后续语言切换

后续建议加入应用内语言选择：

- 默认使用系统语言。
- 允许用户手动选择中文或英文。
- 手动选择后保存到本机设置。
- PC 和 Android 使用同一套语言键名，便于未来扩展 iOS 和 macOS。

## 生成与检查

修改 `source` 下文案后，应重新生成平台资源：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File i18n\tools\Update-I18nResources.ps1
```

如果只想检查生成文件是否已经是最新：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File i18n\tools\Update-I18nResources.ps1 -Check
```

脚本会检查：

- 是否有缺失键。
- 是否有中文和英文键名不一致。
- 是否有占位符不一致。
- 英文按钮是否超出按钮范围。
- 弹窗说明是否换行正常。
- 图表标签和卡片文字是否遮挡。

其中“是否遮挡”仍然需要真实界面检查；脚本只负责文案结构和生成结果。
