$ErrorActionPreference = 'Stop'
function Invoke-Checked {
  param([scriptblock]$Command)
  & $Command
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code $LASTEXITCODE"
  }
}

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Resolve-Path (Join-Path $project '..\..')
$sdkRoot = [Environment]::GetEnvironmentVariable('ANDROID_SDK_ROOT', 'User')
$javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
if (-not $sdkRoot) { throw 'ANDROID_SDK_ROOT is not set' }
if (-not $javaHome) { throw 'JAVA_HOME is not set' }
$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:Path = (Join-Path $javaHome 'bin') + ';' + (Join-Path $sdkRoot 'cmdline-tools\latest\bin') + ';' + $env:Path

$androidJar = Join-Path $sdkRoot 'platforms\android-36\android.jar'
$buildTools = Join-Path $sdkRoot 'build-tools\36.0.0'
$aapt2 = Join-Path $buildTools 'aapt2.exe'
$d8 = Join-Path $buildTools 'd8.bat'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$javac = Join-Path $javaHome 'bin\javac.exe'
$keytool = Join-Path $javaHome 'bin\keytool.exe'
$build = Join-Path $project 'build'
$gen = Join-Path $build 'gen'
$resCompiled = Join-Path $build 'compiled'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
$outDir = Join-Path $root 'outputs\android'
Remove-Item -LiteralPath $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $gen, $resCompiled, $classes, $dex, $outDir | Out-Null

Invoke-Checked { & $aapt2 compile --dir (Join-Path $project 'res') -o $resCompiled }
$unsigned = Join-Path $build 'unsigned.apk'
$manifest = Join-Path $project 'AndroidManifest.xml'
$compiledResources = Get-ChildItem -Path $resCompiled -Filter *.flat -Recurse | ForEach-Object { $_.FullName }
Invoke-Checked { & $aapt2 link -o $unsigned -I $androidJar --manifest $manifest --java $gen --min-sdk-version 26 --target-sdk-version 36 --auto-add-overlay $compiledResources }
$sources = @(Get-ChildItem -Path (Join-Path $project 'src') -Filter *.java -Recurse | ForEach-Object { $_.FullName }) + @(Get-ChildItem -Path $gen -Filter *.java -Recurse | ForEach-Object { $_.FullName })
Invoke-Checked { & $javac -encoding UTF-8 -source 11 -target 11 -classpath $androidJar -d $classes $sources }
$classFiles = Get-ChildItem -Path $classes -Filter *.class -Recurse | ForEach-Object { $_.FullName }
Invoke-Checked { & $d8 --lib $androidJar --output $dex $classFiles }

$apkWithDex = Join-Path $build 'with-dex.apk'
Copy-Item -LiteralPath $unsigned -Destination $apkWithDex -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($apkWithDex, 'Update')
try {
  $existing = $zip.GetEntry('classes.dex')
  if ($existing) { $existing.Delete() }
  [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $dex 'classes.dex'), 'classes.dex') | Out-Null
} finally {
  $zip.Dispose()
}

$aligned = Join-Path $build 'EyeTimeTrackerAndroid-aligned.apk'
Invoke-Checked { & $zipalign -f 4 $apkWithDex $aligned }
$keystore = Join-Path $project 'debug.keystore'
if (-not (Test-Path $keystore)) {
  Invoke-Checked { & $keytool -genkeypair -v -keystore $keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' }
}
$finalApk = Join-Path $outDir 'EyeTimeTrackerAndroid-debug.apk'
Invoke-Checked { & $apksigner sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $finalApk $aligned }
Invoke-Checked { & $apksigner verify --verbose $finalApk }
Get-Item $finalApk | Select-Object FullName,Length,LastWriteTime