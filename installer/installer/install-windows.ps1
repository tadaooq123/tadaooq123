# Установщик мода «Слепой, Глухой, Немой» для Windows.
# Запускается из "УСТАНОВИТЬ (Windows).bat".

try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

$Here      = Split-Path -Parent $PSScriptRoot
$McVersion = '1.21.1'
$Mc        = Join-Path $env:APPDATA '.minecraft'
$Mods      = Join-Path $Mc 'mods'
$Agent     = 'BlindDeafMute-installer/1.0'

Write-Host '=============================================='
Write-Host '   Слепой, Глухой, Немой — установка' -ForegroundColor Yellow
Write-Host '=============================================='
Write-Host "Папка игры: $Mc"
Write-Host ''

if (-not (Test-Path (Join-Path $Here 'mods'))) {
    Write-Host 'Не найдена папка mods рядом с установщиком.' -ForegroundColor Red
    Write-Host 'Сначала РАСПАКУЙ архив (правой кнопкой -> Извлечь всё), потом запускай.' -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path $Mods | Out-Null

# 1. Моды из архива
Write-Host '[1/3] Копирую моды...'
Get-ChildItem -Path $Mods -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -ne 'fabric-api-0.116.17+1.21.1.jar' } |
    ForEach-Object {
        Write-Host "   ! В папке mods уже есть другая версия Fabric API: $($_.Name)" -ForegroundColor Yellow
        Write-Host '     Если игра не запустится — удали её.' -ForegroundColor Yellow
    }
Copy-Item -Path (Join-Path $Here 'mods\*.jar') -Destination $Mods -Force
Write-Host '   Готово: мод и Fabric API в папке mods.' -ForegroundColor Green

# 2. Simple Voice Chat с Modrinth
Write-Host ''
Write-Host '[2/3] Голосовой чат (Simple Voice Chat)...'
if (Get-ChildItem -Path $Mods -Filter 'voicechat-fabric-*' -ErrorAction SilentlyContinue) {
    Write-Host '   Уже установлен — пропускаю.'
} else {
    try {
        $api = "https://api.modrinth.com/v2/project/simple-voice-chat/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22$McVersion%22%5D"
        $versions = @(Invoke-RestMethod -Uri $api -UserAgent $Agent -ErrorAction Stop)
        if ($versions.Count -eq 0) { throw 'нет версий' }
        $file = $versions[0].files | Where-Object { $_.primary } | Select-Object -First 1
        if (-not $file) { $file = $versions[0].files | Select-Object -First 1 }
        Invoke-WebRequest -Uri $file.url -OutFile (Join-Path $Mods $file.filename) -UserAgent $Agent -UseBasicParsing -ErrorAction Stop
        Write-Host "   Скачан: $($file.filename)" -ForegroundColor Green
    } catch {
        Write-Host '   ! Не получилось скачать. Скачай вручную (Fabric 1.21.1) и положи в папку mods:' -ForegroundColor Yellow
        Write-Host '     https://modrinth.com/plugin/simple-voice-chat' -ForegroundColor Yellow
    }
}

# 3. Fabric
Write-Host ''
Write-Host "[3/3] Fabric $McVersion..."
$versionsDir = Join-Path $Mc 'versions'
$hasFabric = (Test-Path $versionsDir) -and
    (Get-ChildItem -Path $versionsDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "fabric-loader-*-$McVersion" })
if ($hasFabric) {
    Write-Host '   Уже установлен — пропускаю.'
} else {
    $java = $null
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { $java = $cmd.Source }
    if (-not $java) {
        # Java, которую скачал сам лаунчер Minecraft или TLauncher
        $roots = @(
            (Join-Path $Mc 'runtime'),
            (Join-Path $env:APPDATA '.tlauncher'),
            (Join-Path ${env:ProgramFiles(x86)} 'Minecraft Launcher\runtime'),
            (Join-Path $env:LOCALAPPDATA 'Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime')
        )
        foreach ($root in $roots) {
            if ($root -and (Test-Path $root)) {
                $found = Get-ChildItem -Path $root -Recurse -Filter 'java.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($found) { $java = $found.FullName; break }
            }
        }
    }
    if ($java) {
        # Копируем установщик в путь без русских букв — так Java точно его откроет
        $tmpJar = Join-Path ([IO.Path]::GetTempPath()) 'bdm-fabric-installer.jar'
        try {
            Copy-Item -Path (Join-Path $PSScriptRoot 'fabric-installer-1.1.2.jar') -Destination $tmpJar -Force
        } catch {
            $tmpJar = Join-Path $PSScriptRoot 'fabric-installer-1.1.2.jar'
        }
        $jargs = @('-jar', $tmpJar, 'client', '-dir', $Mc, '-mcversion', $McVersion)
        if (-not (Test-Path (Join-Path $Mc 'launcher_profiles.json'))) { $jargs += '-noprofile' }
        & $java @jargs
        if ($LASTEXITCODE -eq 0) {
            Write-Host '   Fabric установлен.' -ForegroundColor Green
        } else {
            Write-Host '   ! Fabric не установился. Запусти installer\fabric-installer-1.1.2.exe вручную.' -ForegroundColor Yellow
        }
    } else {
        Write-Host '   Java не найдена, поэтому Fabric не ставлю.' -ForegroundColor Yellow
        Write-Host "   TLauncher: просто выбери в списке версию «Fabric $McVersion»."
        Write-Host '   Официальный лаунчер: запусти installer\fabric-installer-1.1.2.exe.'
    }
}

Write-Host ''
Write-Host '=============================================='
Write-Host " Всё! Запусти лаунчер и выбери Fabric $McVersion." -ForegroundColor Green
Write-Host ' В игре: один открывает мир для сети,'
Write-Host ' остальные заходят, хост пишет /bdm start'
Write-Host '=============================================='
