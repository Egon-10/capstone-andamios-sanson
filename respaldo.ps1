# Respaldo de la base de datos en Windows (equivalente a respaldo.sh).
# Uso, desde la carpeta del proyecto:   powershell -ExecutionPolicy Bypass -File .\respaldo.ps1
# Programarlo a diario: ver docs/despliegue-red-local.md, paso 8.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$carpeta = 'respaldos'
New-Item -ItemType Directory -Force $carpeta | Out-Null
$archivo = Join-Path $carpeta ("inventario-{0}.sql.gz" -f (Get-Date -Format 'yyyy-MM-dd-HHmm'))

# El volcado se comprime dentro del contenedor y se copia tal cual, sin pasar
# por la consola de PowerShell (que alteraría la codificación).
docker compose exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines inventario_andamios | gzip > /tmp/respaldo.sql.gz'
docker compose cp db:/tmp/respaldo.sql.gz $archivo
docker compose exec -T db rm -f /tmp/respaldo.sql.gz

# Un respaldo vacío es peor que ninguno: da una falsa sensación de seguridad.
if ((Get-Item $archivo).Length -lt 2048) {
    throw "El respaldo $archivo está incompleto. Revise el contenedor de la base."
}

# Conservar 30 días.
Get-ChildItem $carpeta -Filter '*.sql.gz' |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } |
    Remove-Item

Write-Output "Respaldo guardado en $archivo"
