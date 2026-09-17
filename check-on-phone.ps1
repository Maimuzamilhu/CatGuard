<#
    CatGuard - install on a connected phone and watch what it does.

    Usage (from the project folder):

        powershell -ExecutionPolicy Bypass -File .\check-on-phone.ps1

    Options:
        -SkipBuild     use the existing APK instead of rebuilding
        -NoLogs        install and launch, but do not tail logcat
        -Verbose Detections   also log every single detection the model makes
#>

[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$NoLogs,
    [switch]$AllDetections
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $projectDir

$adb = 'D:\android-toolchain\sdk\platform-tools\adb.exe'
$apk = Join-Path $projectDir 'app\build\outputs\apk\debug\app-debug.apk'
$pkg = 'com.catguard'

function Step($text) { Write-Host "`n>> $text" -ForegroundColor Cyan }
function Ok($text)   { Write-Host "   $text" -ForegroundColor Green }
function Warn($text) { Write-Host "   $text" -ForegroundColor Yellow }

if (-not (Test-Path $adb)) {
    throw "adb not found at $adb - check the Android SDK location."
}

# ---------------------------------------------------------------- 1. build
if (-not $SkipBuild) {
    Step 'Building the debug APK'
    $env:JAVA_HOME = 'D:\android-toolchain\jdk21'
    & (Join-Path $projectDir 'gradlew.bat') assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Build failed - fix the errors above first.' }
    Ok 'Build succeeded'
} else {
    if (-not (Test-Path $apk)) { throw "No APK at $apk - run without -SkipBuild." }
    Warn 'Skipping build, using the existing APK'
}

# ------------------------------------------------------------- 2. find phone
Step 'Looking for a connected phone'
& $adb start-server | Out-Null
$devices = (& $adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }

$ready = $devices | Where-Object { $_ -match '\sdevice$' }
$unauthorized = $devices | Where-Object { $_ -match 'unauthorized' }

if ($unauthorized) {
    Write-Host ''
    Warn 'Phone found but NOT AUTHORISED.'
    Warn 'Look at the phone screen: there is an "Allow USB debugging?" prompt.'
    Warn 'Tick "Always allow from this computer" and press Allow, then re-run this script.'
    exit 1
}

if (-not $ready) {
    Write-Host ''
    Warn 'No phone detected. On the phone:'
    Warn '  1. Settings > About phone > tap "Build number" 7 times'
    Warn '  2. Settings > System > Developer options > enable "USB debugging"'
    Warn '  3. Plug it in with a data cable (not a charge-only cable)'
    Warn '  4. On the USB notification, choose "File transfer" rather than "Charging"'
    Warn '  5. Accept the "Allow USB debugging?" prompt'
    Write-Host ''
    Warn 'Then re-run this script. Waiting 60s for a device now...'
    & $adb wait-for-device
    $ready = (& $adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
    if (-not $ready) { throw 'Still no device.' }
}

$model   = (& $adb shell getprop ro.product.model).Trim()
$release = (& $adb shell getprop ro.build.version.release).Trim()
$sdk     = (& $adb shell getprop ro.build.version.sdk).Trim()
Ok "Connected: $model - Android $release (API $sdk)"

if ([int]$sdk -lt 24) {
    throw "This phone is API $sdk; CatGuard needs API 24 (Android 7.0) or newer."
}

# --------------------------------------------------------------- 3. install
Step 'Installing CatGuard'
$installOutput = & $adb install -r $apk 2>&1
$installText = $installOutput -join "`n"
if ($installText -match 'Success') {
    Ok 'Installed'
} elseif ($installText -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
    Warn 'A different build is already installed. Uninstalling and retrying...'
    & $adb uninstall $pkg | Out-Null
    & $adb install -r $apk | Out-Null
    Ok 'Installed after a clean uninstall'
} else {
    Write-Host $installText
    throw 'Install failed - see the output above.'
}

# ----------------------------------------------------- 4. logging + launch
Step 'Enabling CatGuard debug logging'
& $adb shell setprop log.tag.CatGuard DEBUG        | Out-Null
& $adb shell setprop log.tag.CatGuardService DEBUG | Out-Null
& $adb shell setprop log.tag.CatGuardAudio DEBUG   | Out-Null
& $adb shell setprop log.tag.CatGuardCamera DEBUG  | Out-Null
if ($AllDetections) {
    & $adb shell setprop log.tag.CatGuardDetect DEBUG | Out-Null
    Ok 'Per-frame detection logging ON (very chatty)'
}

Step 'Launching CatGuard'
& $adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
Ok 'Launched - look at the phone'

Write-Host ''
Write-Host '--------------------------------------------------------------' -ForegroundColor DarkGray
Write-Host ' ON THE PHONE, IN THIS ORDER:' -ForegroundColor White
Write-Host '   1. Allow camera access. The preview should appear.'
Write-Host '   2. Settings -> check "What we detect" names the model.'
Write-Host '   3. Settings -> Deterrent sound -> TEST SOUND. You should hear a bark.'
Write-Host '   4. Back, then START GUARD. Status should read MONITORING.'
Write-Host '   5. Hold up a cat photo on another screen, or tick "Person" in'
Write-Host '      Settings and walk in front of the camera to test the full chain.'
Write-Host '   6. Watch for: CAT DETECTED -> CAT CONFIRMED -> BARKING -> COOLDOWN'
Write-Host '--------------------------------------------------------------' -ForegroundColor DarkGray
Write-Host ''

if ($NoLogs) { exit 0 }

Step 'Streaming logs (Ctrl+C to stop)'
& $adb logcat -c
& $adb logcat -s CatGuard:V CatGuardService:V CatGuardAudio:V CatGuardCamera:V `
    CatGuardDetect:V CatGuardThermal:V CatGuardEvents:V CatGuardSounds:V `
    AndroidRuntime:E
