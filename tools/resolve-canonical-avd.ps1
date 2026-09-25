[CmdletBinding()]
param()

$displayName = 'Pixel 10a V16 Service'
$adbCommand = Get-Command adb -ErrorAction Stop
$roots = @()
if ($env:ANDROID_AVD_HOME) { $roots += $env:ANDROID_AVD_HOME }
if ($env:ANDROID_SDK_HOME) { $roots += (Join-Path $env:ANDROID_SDK_HOME '.android\avd') }
if ($env:USERPROFILE) { $roots += (Join-Path $env:USERPROFILE '.android\avd') }
if ($env:HOME) { $roots += (Join-Path $env:HOME '.android\avd') }
$roots = @($roots | Where-Object { $_ } | ForEach-Object { [IO.Path]::GetFullPath($_) } | Sort-Object -Unique)

$avdMatches = @()
foreach ($root in $roots) {
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
    foreach ($ini in Get-ChildItem -LiteralPath $root -Filter '*.ini' -File -ErrorAction SilentlyContinue) {
        $iniValues = @{}
        foreach ($line in Get-Content -LiteralPath $ini.FullName -ErrorAction SilentlyContinue) {
            $entry = [regex]::Match($line, '^([^=]+)=(.*)$')
            if ($entry.Success) { $iniValues[$entry.Groups[1].Value.Trim()] = $entry.Groups[2].Value.Trim() }
        }
        $avdDir = $iniValues['path']
        if (-not $avdDir) { $avdDir = Join-Path $root ($ini.BaseName + '.avd') }
        $avdDir = [IO.Path]::GetFullPath($avdDir)
        $configPath = Join-Path $avdDir 'config.ini'
        $configValues = @{}
        if (Test-Path -LiteralPath $configPath -PathType Leaf) {
            foreach ($line in Get-Content -LiteralPath $configPath -ErrorAction SilentlyContinue) {
                $entry = [regex]::Match($line, '^([^=]+)=(.*)$')
                if ($entry.Success) { $configValues[$entry.Groups[1].Value.Trim()] = $entry.Groups[2].Value.Trim() }
            }
        }
        $name = $configValues['avd.ini.displayname']
        if (-not $name) { $name = $iniValues['avd.ini.displayname'] }
        if ($name -ceq $displayName) {
            $avdId = $configValues['AvdId']
            if (-not $avdId) { $avdId = $ini.BaseName }
            $avdMatches += [pscustomobject]@{ DisplayName=$name; AvdId=$avdId; Directory=$avdDir }
        }
    }
}
if ($avdMatches.Count -ne 1) { throw "Expected one AVD named '$displayName'; found $($avdMatches.Count) in metadata roots: $($roots -join ', ')" }

$avd = $avdMatches[0]
$deviceLines = & $adbCommand.Source devices
$serials = @($deviceLines | ForEach-Object {
    if ($_ -match '^(emulator-\d+)\s+device\b') { $Matches[1] }
})
$resolved = @()
foreach ($serial in $serials) {
    $console = @(& $adbCommand.Source -s $serial emu avd name 2>$null)
    $runningId = $console | Where-Object { $_ -and $_ -ne 'OK' } | Select-Object -First 1
    if ($runningId -and $runningId.Trim() -ceq $avd.AvdId) { $resolved += $serial }
}
if ($resolved.Count -gt 1) { throw "More than one running emulator matches AVD ID '$($avd.AvdId)': $($resolved -join ', ')" }

[pscustomobject]@{
    displayName = $avd.DisplayName
    avdId = $avd.AvdId
    avdDirectory = $avd.Directory
    adbSerial = $(if ($resolved.Count -eq 1) { $resolved[0] } else { $null })
} | ConvertTo-Json -Compress
