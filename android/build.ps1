# 手动构建 FrontiersImageConverter.apk（无 Gradle、无第三方依赖）
$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "E:\FrontiersImageTool\jdk17\jdk-17.0.21.1+2"
$java = Join-Path $env:JAVA_HOME "bin\java.exe"
$javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
$jar = Join-Path $env:JAVA_HOME "bin\jar.exe"
$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"

$sdk = "E:\FrontiersImageTool\android-sdk"
$bt = Join-Path $sdk "build-tools\34.0.0"
$aapt2 = Join-Path $bt "aapt2.exe"
$d8 = Join-Path $bt "d8.bat"
$zipalign = Join-Path $bt "zipalign.exe"
$apksigner = Join-Path $bt "apksigner.bat"
$androidJar = Join-Path $sdk "platforms\android-34\android.jar"

$app = "E:\FrontiersImageTool\android\app"
$build = "E:\FrontiersImageTool\android\build"
if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $build | Out-Null

Write-Host "1/7 aapt2 compile resources"
& $aapt2 compile --dir (Join-Path $app "res") -o (Join-Path $build "res.zip")

Write-Host "2/7 aapt2 link"
$gen = Join-Path $build "gen"
New-Item -ItemType Directory -Force -Path $gen | Out-Null
& $aapt2 link `
    -o (Join-Path $build "app-base.apk") `
    -I $androidJar `
    --manifest (Join-Path $app "AndroidManifest.xml") `
    -R (Join-Path $build "res.zip") `
    --auto-add-overlay `
    --java $gen `
    --min-sdk-version 26 --target-sdk-version 34 `
    --version-code 1 --version-name 1.0

Write-Host "3/7 javac"
$classes = Join-Path $build "classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$srcs = Get-ChildItem -Path (Join-Path $app "src"), $gen -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac -source 8 -target 8 -encoding UTF-8 -classpath $androidJar -d $classes $srcs

Write-Host "4/7 d8"
$dexDir = Join-Path $build "dex"
New-Item -ItemType Directory -Force -Path $dexDir | Out-Null
$classFiles = Get-ChildItem -Path $classes -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& $d8 --min-api 26 --lib $androidJar --output $dexDir $classFiles

Write-Host "5/7 package dex into apk"
$apkDex = Join-Path $build "app-dex.apk"
Copy-Item (Join-Path $build "app-base.apk") $apkDex
Push-Location $dexDir
& $jar uf $apkDex "classes.dex"
Pop-Location

Write-Host "6/7 zipalign"
$aligned = Join-Path $build "app-aligned.apk"
& $zipalign -f -p 4 $apkDex $aligned

Write-Host "7/7 sign"
# 固定 keystore，避免每次构建签名不同导致无法覆盖安装
$ks = "E:\FrontiersImageTool\android\debug.keystore"
if (-not (Test-Path $ks)) {
    & $keytool -genkeypair -keystore $ks -alias frontiers -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass android -keypass android `
        -dname "CN=Frontiers Converter, OU=Dev, O=Personal, L=Changsha, ST=Hunan, C=CN"
}
$out = "E:\FrontiersImageTool\android\FrontiersImageConverter.apk"
& $apksigner sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out $out $aligned
& $apksigner verify --verbose $out

Write-Host "BUILD OK: $out"
Get-Item $out | Select-Object FullName, Length
