param(
    [string]$SourceDir = "i18n/source"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$files = Get-ChildItem -Path $SourceDir -Filter "*.json" | Sort-Object Name
if ($files.Count -lt 2) {
    throw "At least two locale files are expected in $SourceDir."
}

$referenceFile = $files[0]
$referenceMessages = Get-Content $referenceFile.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
$referenceKeys = $referenceMessages.PSObject.Properties.Name | Sort-Object

foreach ($file in $files) {
    $messages = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $keys = $messages.PSObject.Properties.Name | Sort-Object
    $diff = Compare-Object $referenceKeys $keys
    if ($diff) {
        Write-Host "Locale key mismatch: $($file.Name)"
        $diff
        exit 1
    }
}

Write-Host "i18n source OK. Locale count: $($files.Count). Key count: $($referenceKeys.Count)."
