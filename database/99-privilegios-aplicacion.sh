#!/bin/sh
# =====================================================================
# Privilegios mínimos de la cuenta de la aplicación (SEG-BD-02, SEG-BD-03)
#
# Solo lo ejecuta el contenedor de MySQL del despliegue (docker-compose.yml)
# en el primer arranque, después del volcado y las migraciones 01 a 05.
#
# La imagen oficial crea MYSQL_USER con todos los privilegios sobre la base.
# Aquí se reducen a lo que la aplicación necesita:
#   - consultar e insertar en todas las tablas;
#   - modificar y borrar en todas, salvo en las bitácoras (auditoria y
#     accesos), que quedan de solo inserción: ni siquiera la propia
#     aplicación, si se viera comprometida, puede alterar el historial;
#   - ningún privilegio para crear, alterar o borrar tablas (el esquema lo
#     cambian solo las migraciones, con la cuenta de administración).
# =====================================================================
set -eu

sql() {
  mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" -N "$@"
}

sql -e "REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${MYSQL_USER}'@'%';
        GRANT SELECT, INSERT ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_USER}'@'%';"

for tabla in $(sql -e "SELECT table_name FROM information_schema.tables
                       WHERE table_schema = '${MYSQL_DATABASE}'
                         AND table_type = 'BASE TABLE'
                         AND table_name NOT IN ('auditoria', 'accesos');"); do
  sql -e "GRANT UPDATE, DELETE ON \`${MYSQL_DATABASE}\`.\`${tabla}\` TO '${MYSQL_USER}'@'%';"
done

sql -e "FLUSH PRIVILEGES;"
echo "Privilegios mínimos aplicados a ${MYSQL_USER}: bitácoras de solo inserción, sin DDL."
