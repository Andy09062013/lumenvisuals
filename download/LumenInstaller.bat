@echo off
title Lumen Installer
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=[IO.File]::ReadAllText('%~f0'); $i=$s.IndexOf('#PS'+'START'); Invoke-Expression $s.Substring($i)"
exit /b
#PSSTART
# Lumen installer: pick a launcher + profile, it puts Lumen (+ Fabric API, optional Distant Horizons) in that profile's mods folder.
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$MC = '1.21.11'
$LumenUrl = 'https://github.com/Andy09062013/lumenvisuals/raw/main/download/lumen.jar'
$UA = @{ 'User-Agent' = 'Andy09062013/LumenInstaller/1.0' }

function New-Profile($name, $mods, $fabric, $note) {
    [pscustomobject]@{ Name = $name; Mods = $mods; Fabric = $fabric; Note = $note }
}

function Get-OfficialProfiles {
    $root = Join-Path $env:APPDATA '.minecraft'
    $file = Join-Path $root 'launcher_profiles.json'
    $list = @()
    if (-not (Test-Path $file)) { return $list }
    try { $json = Get-Content $file -Raw | ConvertFrom-Json } catch { return $list }
    foreach ($prop in $json.profiles.PSObject.Properties) {
        $p = $prop.Value
        $name = $p.name
        if (-not $name) { $name = $p.type }
        $ver = [string]$p.lastVersionId
        $fabric = ($ver -like 'fabric-loader*') -and ($ver -like "*$MC")
        $dir = $root
        if ($p.gameDir) { $dir = $p.gameDir }
        $list += New-Profile $name (Join-Path $dir 'mods') $fabric $ver
    }
    return $list
}

function Get-PrismProfiles {
    $list = @()
    foreach ($base in @((Join-Path $env:APPDATA 'PrismLauncher\instances'), (Join-Path $env:APPDATA 'MultiMC\instances'))) {
        if (-not (Test-Path $base)) { continue }
        foreach ($d in Get-ChildItem $base -Directory) {
            $cfg = Join-Path $d.FullName 'instance.cfg'
            if (-not (Test-Path $cfg)) { continue }
            $name = $d.Name
            $line = Select-String -Path $cfg -Pattern '^name=' | Select-Object -First 1
            if ($line) { $name = $line.Line.Substring(5) }
            $game = Join-Path $d.FullName 'minecraft'
            if (-not (Test-Path $game) -and (Test-Path (Join-Path $d.FullName '.minecraft'))) { $game = Join-Path $d.FullName '.minecraft' }
            $pack = Join-Path $d.FullName 'mmc-pack.json'
            $fabric = $false
            if (Test-Path $pack) {
                $t = Get-Content $pack -Raw
                $fabric = ($t -match 'net\.fabricmc\.fabric-loader') -and ($t -match [regex]::Escape($MC))
            }
            $list += New-Profile $name (Join-Path $game 'mods') $fabric ''
        }
    }
    return $list
}

function Get-ModrinthProfiles {
    $list = @()
    foreach ($base in @((Join-Path $env:APPDATA 'ModrinthApp\profiles'), (Join-Path $env:APPDATA 'com.modrinth.theseus\profiles'))) {
        if (-not (Test-Path $base)) { continue }
        foreach ($d in Get-ChildItem $base -Directory) {
            $fabric = ($d.Name -match 'fabric') -or ($d.Name -match [regex]::Escape($MC))
            $list += New-Profile $d.Name (Join-Path $d.FullName 'mods') $fabric 'check it is Fabric 1.21.11'
        }
    }
    return $list
}

function Get-CurseForgeProfiles {
    $list = @()
    $base = Join-Path $env:USERPROFILE 'curseforge\minecraft\Instances'
    if (-not (Test-Path $base)) { return $list }
    foreach ($d in Get-ChildItem $base -Directory) {
        $fabric = $false
        $info = Join-Path $d.FullName 'minecraftinstance.json'
        if (Test-Path $info) {
            try {
                $j = Get-Content $info -Raw | ConvertFrom-Json
                $fabric = ([string]$j.baseModLoader.name -like 'fabric*') -and ([string]$j.gameVersion -eq $MC)
            } catch {}
        }
        $list += New-Profile $d.Name (Join-Path $d.FullName 'mods') $fabric ''
    }
    return $list
}

function Get-ModrinthFile($project) {
    $gv = [uri]::EscapeDataString('["' + $MC + '"]')
    $ld = [uri]::EscapeDataString('["fabric"]')
    $url = "https://api.modrinth.com/v2/project/$project/version?game_versions=$gv&loaders=$ld"
    $versions = Invoke-RestMethod -Uri $url -Headers $UA
    if (-not $versions -or $versions.Count -eq 0) { throw "No $project version found for $MC" }
    $file = $versions[0].files | Where-Object { $_.primary } | Select-Object -First 1
    if (-not $file) { $file = $versions[0].files[0] }
    return $file
}

# ---------------- window ----------------
$form = New-Object System.Windows.Forms.Form
$form.Text = 'Lumen Installer'
$form.Size = New-Object System.Drawing.Size(520, 470)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.Font = New-Object System.Drawing.Font('Segoe UI', 9)

$title = New-Object System.Windows.Forms.Label
$title.Text = 'Install Lumen for Minecraft ' + $MC
$title.Font = New-Object System.Drawing.Font('Segoe UI', 13, [System.Drawing.FontStyle]::Bold)
$title.Location = New-Object System.Drawing.Point(16, 12)
$title.AutoSize = $true
$form.Controls.Add($title)

$l1 = New-Object System.Windows.Forms.Label
$l1.Text = '1. Which launcher do you use?'
$l1.Location = New-Object System.Drawing.Point(16, 52)
$l1.AutoSize = $true
$form.Controls.Add($l1)

$launcher = New-Object System.Windows.Forms.ComboBox
$launcher.DropDownStyle = 'DropDownList'
$launcher.Location = New-Object System.Drawing.Point(16, 74)
$launcher.Width = 470
[void]$launcher.Items.AddRange(@('Minecraft Launcher (official)', 'Prism Launcher / MultiMC', 'Modrinth App', 'CurseForge'))
$form.Controls.Add($launcher)

$l2 = New-Object System.Windows.Forms.Label
$l2.Text = '2. Which profile? (Fabric ' + $MC + ' profiles are marked)'
$l2.Location = New-Object System.Drawing.Point(16, 108)
$l2.AutoSize = $true
$form.Controls.Add($l2)

$profiles = New-Object System.Windows.Forms.ListBox
$profiles.Location = New-Object System.Drawing.Point(16, 130)
$profiles.Size = New-Object System.Drawing.Size(470, 150)
$form.Controls.Add($profiles)

$cbApi = New-Object System.Windows.Forms.CheckBox
$cbApi.Text = 'Fabric API (required, skipped if you already have it)'
$cbApi.Checked = $true
$cbApi.Location = New-Object System.Drawing.Point(16, 290)
$cbApi.AutoSize = $true
$form.Controls.Add($cbApi)

$cbDh = New-Object System.Windows.Forms.CheckBox
$cbDh.Text = 'Distant Horizons (recommended, see much further)'
$cbDh.Checked = $true
$cbDh.Location = New-Object System.Drawing.Point(16, 314)
$cbDh.AutoSize = $true
$form.Controls.Add($cbDh)

$install = New-Object System.Windows.Forms.Button
$install.Text = 'Install'
$install.Location = New-Object System.Drawing.Point(16, 350)
$install.Size = New-Object System.Drawing.Size(150, 34)
$form.Controls.Add($install)

$open = New-Object System.Windows.Forms.Button
$open.Text = 'Open mods folder'
$open.Location = New-Object System.Drawing.Point(176, 350)
$open.Size = New-Object System.Drawing.Size(150, 34)
$open.Enabled = $false
$form.Controls.Add($open)

$status = New-Object System.Windows.Forms.Label
$status.Location = New-Object System.Drawing.Point(16, 394)
$status.Size = New-Object System.Drawing.Size(470, 36)
$status.Text = 'Pick your launcher to begin.'
$form.Controls.Add($status)

$script:found = @()

$launcher.Add_SelectedIndexChanged({
    $profiles.Items.Clear()
    switch ($launcher.SelectedIndex) {
        0 { $script:found = @(Get-OfficialProfiles) }
        1 { $script:found = @(Get-PrismProfiles) }
        2 { $script:found = @(Get-ModrinthProfiles) }
        3 { $script:found = @(Get-CurseForgeProfiles) }
    }
    # Fabric 1.21.11 profiles first
    $script:found = @($script:found | Sort-Object @{ Expression = { -not $_.Fabric } }, Name)
    foreach ($p in $script:found) {
        $tag = ''
        if ($p.Fabric) { $tag = '   [Fabric ' + $MC + ']' }
        [void]$profiles.Items.Add($p.Name + $tag)
    }
    if ($script:found.Count -eq 0) {
        $status.Text = "No profiles found for that launcher. Make a Fabric $MC profile/instance first, then reopen this."
    } else {
        $status.Text = 'Pick a profile, then press Install.'
        $profiles.SelectedIndex = 0
    }
})

$install.Add_Click({
    if ($profiles.SelectedIndex -lt 0) { $status.Text = 'Pick a profile first.'; return }
    $p = $script:found[$profiles.SelectedIndex]
    if (-not $p.Fabric) {
        $ans = [System.Windows.Forms.MessageBox]::Show("'$($p.Name)' doesn't look like a Fabric $MC profile. Lumen only runs on Fabric $MC.`n`nInstall into it anyway?", 'Lumen Installer', 'YesNo', 'Warning')
        if ($ans -ne 'Yes') { return }
    }
    $install.Enabled = $false
    try {
        New-Item -ItemType Directory -Force -Path $p.Mods | Out-Null
        $status.Text = 'Removing old Lumen versions...'; $form.Refresh()
        Get-ChildItem $p.Mods -Filter '*.jar' | Where-Object { $_.Name -match '^lumen.*\.jar$' } | Remove-Item -Force

        $status.Text = 'Downloading Lumen...'; $form.Refresh()
        Invoke-WebRequest -Uri $LumenUrl -OutFile (Join-Path $p.Mods 'lumen.jar') -Headers $UA -UseBasicParsing

        if ($cbApi.Checked) {
            $have = Get-ChildItem $p.Mods -Filter 'fabric-api*.jar' -ErrorAction SilentlyContinue
            if (-not $have) {
                $status.Text = 'Downloading Fabric API...'; $form.Refresh()
                $f = Get-ModrinthFile 'fabric-api'
                Invoke-WebRequest -Uri $f.url -OutFile (Join-Path $p.Mods $f.filename) -Headers $UA -UseBasicParsing
            }
        }
        if ($cbDh.Checked) {
            $have = Get-ChildItem $p.Mods -Filter '*istant*orizons*.jar' -ErrorAction SilentlyContinue
            if (-not $have) {
                $status.Text = 'Downloading Distant Horizons...'; $form.Refresh()
                $f = Get-ModrinthFile 'distanthorizons'
                Invoke-WebRequest -Uri $f.url -OutFile (Join-Path $p.Mods $f.filename) -Headers $UA -UseBasicParsing
            }
        }
        $script:lastMods = $p.Mods
        $open.Enabled = $true
        $status.Text = "Done! Lumen is installed in '$($p.Name)'. Start that profile and you're good. Press F6 in game for settings."
        [System.Windows.Forms.MessageBox]::Show("Lumen is installed in '$($p.Name)'.`n`nStart Minecraft with that profile and rejoin the server.", 'Lumen Installer', 'OK', 'Information') | Out-Null
    } catch {
        $status.Text = 'Something went wrong: ' + $_.Exception.Message
    } finally {
        $install.Enabled = $true
    }
})

$open.Add_Click({ if ($script:lastMods) { Start-Process explorer.exe $script:lastMods } })

[void]$form.ShowDialog()
