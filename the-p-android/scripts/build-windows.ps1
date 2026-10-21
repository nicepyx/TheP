[CmdletBinding()]
param(
    [string]$JdkPath,
    [string]$SdkPath,
    [switch]$CheckOnly,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments = @('assembleDebug')
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$candidates = New-Object 'System.Collections.Generic.List[string]'

function Add-JdkCandidate([string]$Path) {
    if (-not [string]::IsNullOrWhiteSpace($Path)) {
        $candidates.Add($Path)
    }
}

function Test-Jdk21([string]$Path) {
    if (-not (Test-Path -LiteralPath (Join-Path $Path 'bin\java.exe')) -or
        -not (Test-Path -LiteralPath (Join-Path $Path 'bin\javac.exe'))) {
        return $false
    }
    $releaseFile = Join-Path $Path 'release'
    if (-not (Test-Path -LiteralPath $releaseFile)) {
        return $false
    }
    return [bool](Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="21([.+"]|$)' -Quiet)
}

function Get-SdkFromProperties([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $sdkValue = $null
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        if ($line -match '^\s*sdk\.dir\s*[=:]\s*(.*)$') {
            # Android Studio writes Java properties escapes, e.g. C\:\\Android\\Sdk.
            $sdkValue = [regex]::Replace($Matches[1].Trim(), '\\(u[0-9a-fA-F]{4}|.)', {
                param($match)
                $value = $match.Groups[1].Value
                if ($value -match '^u[0-9a-fA-F]{4}$') {
                    return [string][char][Convert]::ToInt32($value.Substring(1), 16)
                }
                switch -CaseSensitive ($value) {
                    't' { return "`t" }
                    'r' { return "`r" }
                    'n' { return "`n" }
                    'f' { return "`f" }
                    default { return $value }
                }
            })
        }
    }
    return $sdkValue
}

function Test-AndroidSdk([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return $false }
    return ((Test-Path -LiteralPath (Join-Path $Path 'platforms') -PathType Container) -or
        (Test-Path -LiteralPath (Join-Path $Path 'cmdline-tools') -PathType Container))
}

if ($JdkPath) {
    Add-JdkCandidate $JdkPath
} else {
    Add-JdkCandidate $env:JAVA_HOME
    Add-JdkCandidate $env:JDK_HOME
    foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if ($base) {
            Add-JdkCandidate (Join-Path $base 'Android\Android Studio\jbr')
            Add-JdkCandidate (Join-Path $base 'Programs\Android Studio\jbr')
        }
    }
    # Android Studio installed at a custom location normally registers its path here.
    foreach ($registryPath in @(
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*'
    )) {
        Get-ItemProperty -Path $registryPath -ErrorAction SilentlyContinue |
            Where-Object { $_.DisplayName -like 'Android Studio*' -and $_.InstallLocation } |
            ForEach-Object { Add-JdkCandidate (Join-Path $_.InstallLocation 'jbr') }
    }
    $searchRoots = @()
    if ($env:ProgramFiles) {
        foreach ($vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto', 'BellSoft', 'Zulu')) {
            $searchRoots += Join-Path $env:ProgramFiles $vendor
        }
    }
    if ($env:USERPROFILE) {
        $searchRoots += Join-Path $env:USERPROFILE '.jdks'
        $searchRoots += Join-Path $env:USERPROFILE '.gradle\jdks'
    }
    foreach ($root in $searchRoots) {
        Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            ForEach-Object {
                Add-JdkCandidate $_.FullName
                # Gradle's downloaded toolchains may be nested one directory deeper.
                Get-ChildItem -LiteralPath $_.FullName -Directory -ErrorAction SilentlyContinue |
                    ForEach-Object { Add-JdkCandidate $_.FullName }
            }
    }
    $javaCommand = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($javaCommand) {
        Add-JdkCandidate (Split-Path -Parent (Split-Path -Parent $javaCommand.Source))
    }
}

$selectedJdk = $candidates | Select-Object -Unique | Where-Object { Test-Jdk21 $_ } | Select-Object -First 1
if (-not $selectedJdk) {
    throw 'No complete JDK 21 found. Install JDK 21 or run this script with -JdkPath "C:\path\to\jdk-21". Java 8 is not supported.'
}
$selectedJdk = (Resolve-Path -LiteralPath $selectedJdk).ProviderPath

$localProperties = Join-Path $projectDirectory 'local.properties'
$configuredSdk = Get-SdkFromProperties $localProperties
if ($configuredSdk) {
    # AGP gives sdk.dir precedence over environment variables. Never silently replace it.
    if (-not (Test-AndroidSdk $configuredSdk)) {
        throw "Invalid sdk.dir in $localProperties. Correct this path in Android Studio's SDK settings; this script will not overwrite the file."
    }
    $selectedSdk = $configuredSdk
    if ($SdkPath -and (Resolve-Path -LiteralPath $SdkPath).ProviderPath -ne
        (Resolve-Path -LiteralPath $configuredSdk).ProviderPath) {
        throw '-SdkPath conflicts with this project''s local.properties sdk.dir. Update that setting in Android Studio first.'
    }
} elseif ($SdkPath) {
    if (-not (Test-AndroidSdk $SdkPath)) { throw "No Android SDK found at $SdkPath." }
    $selectedSdk = $SdkPath
} else {
    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $legacyProperties = Join-Path (Split-Path -Parent $projectDirectory) 'android\local.properties'
    $sdkCandidates += Get-SdkFromProperties $legacyProperties
    if ($env:LOCALAPPDATA) { $sdkCandidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    if ($env:USERPROFILE) { $sdkCandidates += Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk' }
    $selectedSdk = $sdkCandidates | Where-Object { Test-AndroidSdk $_ } | Select-Object -First 1
    if (-not $selectedSdk) {
        throw 'No Android SDK found. Open Android Studio > SDK Manager to locate/install the SDK, then run this script with -SdkPath "D:\Android\Sdk".'
    }
}
$selectedSdk = (Resolve-Path -LiteralPath $selectedSdk).ProviderPath

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
$previousAndroidHome = $env:ANDROID_HOME
$previousAndroidSdkRoot = $env:ANDROID_SDK_ROOT
try {
    $env:JAVA_HOME = $selectedJdk
    $env:Path = (Join-Path $selectedJdk 'bin') + [IO.Path]::PathSeparator + $previousPath
    $env:ANDROID_HOME = $selectedSdk
    $env:ANDROID_SDK_ROOT = $selectedSdk
    Write-Host "The P: using JDK 21 at $selectedJdk"
    Write-Host "The P: using Android SDK at $selectedSdk"
    & (Join-Path $selectedJdk 'bin\java.exe') -version
    if ($LASTEXITCODE -ne 0) { throw 'JDK 21 could not start.' }
    if ($CheckOnly) { return }
    Push-Location $projectDirectory
    try {
        & .\gradlew.bat "-Dorg.gradle.java.home=$selectedJdk" @GradleArguments
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed (exit code $LASTEXITCODE)." }
        if ($GradleArguments -contains 'assembleDebug') {
            Write-Host ('APK: ' + (Join-Path $projectDirectory 'app\build\outputs\apk\debug\app-debug.apk'))
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:Path = $previousPath
    $env:ANDROID_HOME = $previousAndroidHome
    $env:ANDROID_SDK_ROOT = $previousAndroidSdkRoot
}
