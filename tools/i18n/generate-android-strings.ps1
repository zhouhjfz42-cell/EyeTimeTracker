param(
    [string]$SourceDir = "i18n/source",
    [string]$OutputDir = "i18n/generated/android"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Convert-ToAndroidName([string]$key) {
    $snake = $key -creplace "([a-z0-9])([A-Z])", '$1_$2'
    return ($snake -replace "[^A-Za-z0-9_]", "_").ToLowerInvariant()
}

function Escape-AndroidString([string]$value) {
    return $value.
        Replace("&", "&amp;").
        Replace("<", "&lt;").
        Replace(">", "&gt;").
        Replace("'", "\'").
        Replace('"', '\"').
        Replace("`r`n", "\n").
        Replace("`n", "\n")
}

function Get-AndroidFolder([string]$locale) {
    if ($locale -eq "zh-CN") {
        return "values"
    }

    $language = $locale.Split("-")[0]
    return "values-$language"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Get-ChildItem -Path $SourceDir -Filter "*.json" | ForEach-Object {
    $locale = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
    $messages = Get-Content $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $folder = Join-Path $OutputDir (Get-AndroidFolder $locale)
    New-Item -ItemType Directory -Force -Path $folder | Out-Null

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('<?xml version="1.0" encoding="utf-8"?>')
    $lines.Add('<resources>')

    foreach ($property in $messages.PSObject.Properties | Sort-Object Name) {
        $name = Convert-ToAndroidName $property.Name
        $value = Escape-AndroidString ([string]$property.Value)
        $lines.Add("    <string name=`"$name`" formatted=`"false`">$value</string>")
    }

    $lines.Add('</resources>')
    Set-Content -Path (Join-Path $folder "strings.xml") -Value $lines -Encoding UTF8
}
