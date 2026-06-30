# EyeTimeTracker 用眼时间记录

EyeTimeTracker 是一个轻量的用眼时间统计工具，用来记录电脑和安卓手机上的屏幕使用时长。项目目前包含 Windows PC 端和 Android 端，目标是帮助用户了解每天、每周、每月的屏幕注视时间，并在接近设定时长时提醒休息。

## 功能概览

- Windows PC 端：根据键盘/鼠标动作和媒体播放状态统计用眼时间。
- Android 端：亮屏且有机身动作，或亮屏且有媒体播放时计入统计；息屏播放不计时。
- 展示今天、昨天、本周、本月的累计用眼时间。
- 支持提醒时间设置，默认 330 分钟，也就是 5 小时 30 分。
- 支持当天内反复提醒：开启后会在 330、660、990 分钟等倍数时间再次提醒。
- 支持重置显示：只把当前界面显示归零，不删除后台保存的历史统计数据。
- 无需登录；后续可扩展多端账号同步。

## 统计规则

### PC 端

PC 端不是简单统计开机时间，而是按活动状态计时：

- 最近一段时间内有键盘或鼠标动作时计时。
- 检测到媒体播放时计时。
- 长时间无输入且无媒体播放时暂停。
- 计时使用增量累加，不会因为之后某一次检测到活动，就把前面很长一段空闲时间补算进去。

### Android 端

Android 端统计的是更接近手机屏幕使用的时间：

- 亮屏 + 机身动作：计入统计。
- 亮屏 + 媒体播放：计入统计。
- 息屏播放：不计入统计。
- 亮屏但长时间静止且无媒体播放：暂停统计。

## 健康颜色

今天的用眼时间会根据使用时长改变颜色：

- 6 小时以内：绿色。
- 6 到 8 小时：黄色。
- 大于 8 小时：红色。

## 项目结构

```text
src/
  EyeTimeTracker.Core/        # 统计、提醒、存储等共享核心逻辑
  EyeTimeTracker.App/         # Windows WinForms 桌面端
tests/
  EyeTimeTracker.Tests/       # PC/Core 逻辑测试
android/
  EyeTimeTrackerAndroid/      # Android 原生 Java 应用
docs/
  mockups/                    # 界面设计预览
  superpowers/                # 设计和实施记录
```

## 构建与测试

### Windows PC 端

需要 .NET 8 SDK。

```powershell
dotnet build src\EyeTimeTracker.App\EyeTimeTracker.App.csproj -c Release
dotnet test tests\EyeTimeTracker.Tests\EyeTimeTracker.Tests.csproj -c Release
```

### Android 端

需要安装 Android SDK、JDK，并配置用户环境变量：

- `ANDROID_SDK_ROOT`
- `JAVA_HOME`

构建 APK：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android\EyeTimeTrackerAndroid\build-android.ps1
```

生成的调试 APK 位于：

```text
outputs/android/EyeTimeTrackerAndroid-debug.apk
```

安装到已连接并开启 USB 调试的手机：

```powershell
adb install -r outputs\android\EyeTimeTrackerAndroid-debug.apk
```

## 数据保存

PC 端会在本机用户目录下保存每日统计数据。重置显示不会删除后台历史记录，后续统计页可以继续基于这些每日记录生成每周、每月、年度趋势。

Android 端使用本机应用私有存储保存统计数据和提醒设置。

## 当前限制

- 当前没有账号系统，多端数据不会自动同步。
- Android 端不使用前置摄像头判断是否注视屏幕，避免隐私和耗电问题。
- Android 的“后台运行”按钮主要用于启动或恢复前台服务；后续界面会增加更明确的文字反馈。
- 目前 Android 工程使用轻量脚本直接构建，尚未迁移到 Gradle/Android Studio 标准项目结构。

## 许可证

当前尚未选择开源许可证。如需公开复用代码，请先补充 LICENSE 文件。
