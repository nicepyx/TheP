[CmdletBinding()]
param(
    [string]$JdkPath,
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

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
try {
    $env:JAVA_HOME = $selectedJdk
    $env:Path = (Join-Path $selectedJdk 'bin') + [IO.Path]::PathSeparator + $previousPath
    Write-Host "The P: using JDK 21 at $selectedJdk"
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
}
