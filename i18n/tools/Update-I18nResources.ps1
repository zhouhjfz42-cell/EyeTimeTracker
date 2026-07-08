param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'

$toolDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$i18nDir = Resolve-Path (Join-Path $toolDir '..')
$sourceDir = Join-Path $i18nDir 'source'
$generatedDir = Join-Path $i18nDir 'generated'
$androidDir = Join-Path $generatedDir 'android'
$dotnetDir = Join-Path $generatedDir 'dotnet'

$locales = @(
    @{ Name = 'zh-CN'; AndroidFolder = 'values' },
    @{ Name = 'en-US'; AndroidFolder = 'values-en' }
)

function Read-LocaleMessages {
    param([string]$Locale)

    $path = Join-Path $sourceDir "$Locale.json"
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing locale file: $path"
    }

    $json = Get-Content -Raw -Encoding UTF8 -LiteralPath $path
    $object = $json | ConvertFrom-Json
    $messages = [ordered]@{}
    foreach ($property in $object.PSObject.Properties) {
        if ($property.Value -isnot [string]) {
            throw "Locale $Locale key '$($property.Name)' must be a string."
        }
        if ([string]::IsNullOrWhiteSpace($property.Value)) {
            throw "Locale $Locale key '$($property.Name)' is empty."
        }
        $messages[$property.Name] = $property.Value
    }
    return $messages
}

function Get-Placeholders {
    param([string]$Text)

    $values = New-Object System.Collections.Generic.HashSet[string]
    foreach ($match in [regex]::Matches($Text, '\{([A-Za-z][A-Za-z0-9_]*)(?::[^}]*)?\}')) {
        [void]$values.Add($match.Groups[1].Value)
    }
    return @($values | Sort-Object)
}

function Convert-ToAndroidName {
    param([string]$Key)
    $value = $Key.Replace('.', '_')
    $value = [regex]::Replace($value, '([a-z0-9])([A-Z])', '$1_$2')
    return $value.ToLowerInvariant()
}

function Escape-AndroidText {
    param([string]$Text)

    return $Text.
        Replace('&', '&amp;').
        Replace('<', '&lt;').
        Replace('>', '&gt;').
        Replace("'", "\'").
        Replace('"', '\"').
        Replace("`r`n", '\n').
        Replace("`n", '\n').
        Replace("`r", '\n')
}

function Escape-DotNetText {
    param([string]$Text)

    return $Text
}

function Write-Or-CheckFile {
    param(
        [string]$Path,
        [string]$Content
    )

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    if ($Check) {
        if (-not (Test-Path -LiteralPath $Path)) {
            throw "Generated file is missing: $Path"
        }
        $existing = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
        if ($existing -ne $Content) {
            throw "Generated file is stale: $Path"
        }
        return
    }

    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

$catalogs = [ordered]@{}
foreach ($locale in $locales) {
    $catalogs[$locale.Name] = Read-LocaleMessages $locale.Name
}

$baseLocale = $locales[0].Name
$baseKeys = @($catalogs[$baseLocale].Keys | Sort-Object)
foreach ($locale in $locales) {
    $keys = @($catalogs[$locale.Name].Keys | Sort-Object)
    $missing = @($baseKeys | Where-Object { $_ -notin $keys })
    $extra = @($keys | Where-Object { $_ -notin $baseKeys })
    if ($missing.Count -gt 0) {
        throw "Locale $($locale.Name) is missing keys: $($missing -join ', ')"
    }
    if ($extra.Count -gt 0) {
        throw "Locale $($locale.Name) has extra keys: $($extra -join ', ')"
    }
}

foreach ($key in $baseKeys) {
    $basePlaceholders = @(Get-Placeholders $catalogs[$baseLocale][$key])
    foreach ($locale in $locales | Select-Object -Skip 1) {
        $placeholders = @(Get-Placeholders $catalogs[$locale.Name][$key])
        if (($basePlaceholders -join '|') -ne ($placeholders -join '|')) {
            throw "Placeholder mismatch for key '$key' between $baseLocale and $($locale.Name)."
        }
    }
}

foreach ($locale in $locales) {
    $messages = $catalogs[$locale.Name]

    $xmlLines = New-Object System.Collections.Generic.List[string]
    $xmlLines.Add('<?xml version="1.0" encoding="utf-8"?>')
    $xmlLines.Add('<resources>')
    foreach ($key in $baseKeys) {
        $name = Convert-ToAndroidName $key
        $value = Escape-AndroidText $messages[$key]
        $xmlLines.Add("    <string name=""$name"" formatted=""false"">$value</string>")
    }
    $xmlLines.Add('</resources>')
    $androidPath = Join-Path (Join-Path $androidDir $locale.AndroidFolder) 'strings.xml'
    Write-Or-CheckFile $androidPath (($xmlLines -join "`r`n") + "`r`n")

    $jsonObject = [ordered]@{}
    foreach ($key in $baseKeys) {
        $jsonObject[$key] = Escape-DotNetText $messages[$key]
    }
    $json = ($jsonObject | ConvertTo-Json -Depth 4)
    Write-Or-CheckFile (Join-Path $dotnetDir "$($locale.Name).json") ($json + "`r`n")
}

if ($Check) {
    Write-Host 'i18n resources are up to date.'
} else {
    Write-Host 'i18n resources generated.'
}
