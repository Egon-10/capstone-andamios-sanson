#!/bin/sh
# Respaldo de la base de datos del sistema de inventario (docs/despliegue.md).
# Uso: ./respaldo.sh   (desde la carpeta del proyecto)
set -eu

carpeta="respaldos"
archivo="$carpeta/inventario-$(date +%F-%H%M).sql.gz"
mkdir -p "$carpeta"

docker compose exec -T db sh -c \
  'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines inventario_andamios' \
  | gzip > "$archivo"

# Un respaldo vacío es peor que ninguno: da una falsa sensación de seguridad.
if [ ! -s "$archivo" ] || [ "$(gunzip -c "$archivo" | grep -c 'CREATE TABLE')" -lt 10 ]; then
  echo "El respaldo $archivo está incompleto. Revise el contenedor de la base." >&2
  exit 1
fi

find "$carpeta" -name '*.sql.gz' -mtime +30 -delete
echo "Respaldo guardado en $archivo"
