# EyeTimeTracker i18n

This folder is the shared copy source for all product text.

## Structure

```text
i18n/
  source/
    zh-CN.json
    en-US.json
  generated/
    android/
    windows/
    apple/
```

## Rules

- `source/*.json` is the only source of truth for UI copy.
- Windows, Android, iOS, and macOS should read or generate platform resources from the same keys.
- New UI text should be added here before it is used in platform code.
- Keys stay stable across languages. Values can be adapted for each market instead of translated word for word.
- Dynamic text uses braces, for example `{device}`, `{duration}`, `{percent}`, `{count}`.

## Platform Targets

- Windows: generate or embed a C# text resource from `source/*.json`.
- Android: generate `res/values/strings.xml`, `res/values-en/strings.xml`, and future locale folders.
- iOS/macOS: generate `Localizable.strings` or String Catalog files from the same source.

The app currently runs in Chinese by default. English copy is prepared for the commercial English-market version.

## Commands

Check locale files:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\i18n\validate-i18n.ps1
```

Generate Android resource samples:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\i18n\generate-android-strings.ps1
```
