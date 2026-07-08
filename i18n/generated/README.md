# Generated platform resources

This folder stores platform-specific files generated from `i18n/source/*.json`.

Do not edit generated files by hand. Update the source language files first, then regenerate platform resources.

Expected platform outputs:

- `android/values/strings.xml`
- `android/values-en/strings.xml`
- `dotnet/*.json`, `.resx`, or embedded C# resources
- `apple/Localizable.strings`, `.xcstrings`, or generated Swift resources in the future

Current direction:

- Android should read UI strings from Android resources whenever possible.
- Windows should read UI strings through a central text provider, not scattered literals.
- iOS and macOS should reuse the same source keys when those platforms are added.
- `i18n/tools/Update-I18nResources.ps1` owns these generated files.
- Android APK builds run the generator before compiling resources.
- Windows builds copy `generated/dotnet/*.json` and `source/*.json`; runtime reads generated resources first and falls back to source files.

Before releasing a language version, check the real UI instead of only reviewing text files. English strings are often longer than Chinese strings and may require layout adjustments.
