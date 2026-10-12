# =====================================================================
# Inicia el sistema de inventario en esta PC (Windows + Docker Desktop) y lo
# deja disponible para las demas PCs y celulares de la misma red.
#
# Uso, desde la carpeta del proyecto:
#   powershell -ExecutionPolicy Bypass -File .\iniciar.ps1           # iniciar
#   powershell -ExecutionPolicy Bypass -File .\iniciar.ps1 -Detener  # detener
#
# La primera vez crea el archivo .env con contrasenas aleatorias (no se sube
# a GitHub). Cada vez actualiza la direccion IP permitida, por si la PC
# cambio de red. Ver docs/despliegue-red-local.md.
# =====================================================================
param([switch]$Detener)

Set-Location $PSScriptRoot
$Puerto = 8080
$NombreRegla = 'Inventario Sanson (red local)'

function Paso($texto)  { Write-Host "`n== $texto" -ForegroundColor Cyan }
function Falla($texto) { Write-Host "`n$texto" -ForegroundColor Red; exit 1 }
function DockerListo   { cmd /c "docker info >nul 2>&1"; return ($LASTEXITCODE -eq 0) }

# --- 1. Docker ----------------------------------------------------------
Paso 'Comprobando Docker'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Falla 'Docker no esta instalado. Instale Docker Desktop (https://www.docker.com/products/docker-desktop/), reinicie la PC y vuelva a ejecutar este script.'
}
if (-not (DockerListo)) {
    # Docker Desktop puede estar instalado para todos los usuarios o solo para el actual.
    $escritorio = @(
        (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'),
        (Join-Path $env:LOCALAPPDATA 'Docker\Docker Desktop.exe')
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($escritorio) { Write-Host 'Abriendo Docker Desktop...'; Start-Process $escritorio }
    else { Write-Host 'Abra Docker Desktop desde el menu Inicio.' }
    Write-Host 'Esperando a que Docker este listo (hasta 3 minutos)...'
    $limite = (Get-Date).AddMinutes(3)
    while (-not (DockerListo)) {
        if ((Get-Date) -gt $limite) { Falla 'Docker no respondio. Abra Docker Desktop, espere a que diga "Engine running" y vuelva a ejecutar.' }
        Start-Sleep -Seconds 5
    }
}
Write-Host 'Docker listo.'

if ($Detener) {
    Paso 'Deteniendo el sistema (los datos se conservan)'
    docker compose stop
    exit 0
}

# --- 2. Direccion IP de esta PC en la red ------------------------------
Paso 'Buscando la direccion IP de esta PC'
$config = Get-NetIPConfiguration |
    Where-Object { $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } |
    Select-Object -First 1
$ip = $null
if ($config) { $ip = @($config.IPv4Address)[0].IPAddress }
if ($ip) { Write-Host "IP: $ip ($($config.InterfaceAlias))" }
else     { Write-Host 'No se encontro una red con salida al router: solo funcionara en esta PC.' -ForegroundColor Yellow }

# --- 3. Configuracion (.env) -------------------------------------------
Paso 'Preparando la configuracion (.env)'
function Azar([int]$n) {
    $letras = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $n
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    -join ($bytes | ForEach-Object { $letras[$_ % $letras.Length] })
}
$origenes = "http://localhost:$Puerto,http://127.0.0.1:$Puerto"
if ($ip) { $origenes += ",http://${ip}:$Puerto" }

if (Test-Path .env) {
    $lineas = @(Get-Content .env | Where-Object { $_ -notmatch '^(CORS_ORIGENES|PUERTO_WEB)=' })
    Write-Host '.env existente: se conservan las contrasenas.'
} else {
    $lineas = @(
        "MYSQL_ROOT_PASSWORD=$(Azar 32)",
        'MYSQL_USER=inventario',
        "MYSQL_PASSWORD=$(Azar 32)",
        "JWT_SECRET=$(Azar 64)"
    )
    Write-Host '.env creado con contrasenas aleatorias. No lo comparta ni lo suba a GitHub.'
}
$lineas += "PUERTO_WEB=$Puerto"
$lineas += "CORS_ORIGENES=$origenes"
Set-Content -Path .env -Value $lineas -Encoding ascii

# --- 4. Levantar los contenedores --------------------------------------
Paso 'Levantando el sistema (la primera vez tarda entre 5 y 15 minutos)'
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Falla 'docker compose fallo. Revise el mensaje de arriba.' }

Write-Host 'Esperando a que la pagina responda...'
$limite = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 5
    try { $ok = (Invoke-WebRequest "http://localhost:$Puerto/" -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200 } catch { $ok = $false }
} until ($ok -or (Get-Date) -gt $limite)
docker compose ps
if (-not $ok) { Falla 'El sistema no respondio a tiempo. Revise: docker compose logs backend' }

# --- 5. Permitir el acceso desde otras PCs de la red -------------------
if ($ip) {
    Paso 'Permitiendo el acceso desde la red local'
    if (Get-NetFirewallRule -DisplayName $NombreRegla -ErrorAction SilentlyContinue) {
        Write-Host 'La regla del firewall ya existe.'
    } else {
        Write-Host 'Windows pedira permiso de administrador para abrir el puerto solo a la red local.'
        $comando = "New-NetFirewallRule -DisplayName '$NombreRegla' -Direction Inbound -Protocol TCP -LocalPort $Puerto -Action Allow -Profile Private,Domain -RemoteAddress LocalSubnet"
        try { Start-Process powershell -Verb RunAs -Wait -ArgumentList '-NoProfile', '-Command', $comando }
        catch { Write-Host 'No se concedio el permiso: las otras PCs no podran entrar hasta crear la regla.' -ForegroundColor Yellow }
    }
    $perfil = Get-NetConnectionProfile -InterfaceIndex $config.InterfaceIndex -ErrorAction SilentlyContinue
    if ($perfil -and $perfil.NetworkCategory -eq 'Public') {
        Write-Host "AVISO: la red '$($perfil.Name)' esta marcada como Publica y Windows bloquea las conexiones entrantes." -ForegroundColor Yellow
        Write-Host '       Si es una red de confianza (casa u oficina), marquela como Privada en:' -ForegroundColor Yellow
        Write-Host '       Configuracion > Red e Internet > (su red) > Perfil de red: Privada' -ForegroundColor Yellow
    }
}

# --- Listo -------------------------------------------------------------
Paso 'Sistema iniciado'
Write-Host "En esta PC:               http://localhost:$Puerto" -ForegroundColor Green
if ($ip) { Write-Host "Otras PCs y celulares:    http://${ip}:$Puerto  (conectados a la misma red)" -ForegroundColor Green }
Write-Host 'Para detenerlo:           powershell -ExecutionPolicy Bypass -File .\iniciar.ps1 -Detener'
