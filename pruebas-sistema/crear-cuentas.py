"""Crea las cuentas de las pruebas de sistema en la base del despliegue.

Lo ejecuta la CI despues de levantar los contenedores. Cada cuenta recibe una
contrasena aleatoria que cumple la politica (HU-38); el hash BCrypt va a la
base y la contrasena en claro solo vive en las variables de entorno de esta
ejecucion (enmascaradas en el registro). Nada queda en el repositorio.

Uso: python3 crear-cuentas.py <archivo.sql> <archivo de entorno>
"""
import secrets
import sys

import bcrypt

ROLES = {"ADMIN": 1, "ENCARGADO": 2, "GERENTE": 3, "BLOQUEO": 2, "DESACTIVAR": 2}


def main() -> None:
    sql_destino, entorno_destino = sys.argv[1], sys.argv[2]
    sufijo = secrets.token_hex(3)
    filas, entorno = [], []
    for clave, rol in ROLES.items():
        usuario = f"e2e_{clave.lower()}_{sufijo}"
        password = "Prueba#" + secrets.token_hex(8) + "Zq9"
        documento = str(secrets.randbelow(89_999_999) + 10_000_000)
        hash_ = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=10, prefix=b"2a")).decode()
        filas.append(
            "INSERT INTO usuarios (nombre, apellidos, tipo_documento, numero_documento, correo, nombre_usuario, "
            "password, area, estado, fecha_creacion, rol_id) VALUES "
            f"('Prueba {clave.title()}', 'Sistema', 'DNI', '{documento}', '{usuario}@prueba.pe', '{usuario}', "
            f"'{hash_}', 'LOGISTICA', 'ACTIVO', NOW(), {rol});"
        )
        print(f"::add-mask::{password}")
        entorno.append(f"E2E_{clave}_USUARIO={usuario}")
        entorno.append(f"E2E_{clave}_PASSWORD={password}")
    with open(sql_destino, "w", encoding="utf-8") as f:
        f.write("\n".join(filas) + "\n")
    with open(entorno_destino, "a", encoding="utf-8") as f:
        f.write("\n".join(entorno) + "\n")
    print(f"Cuentas de prueba creadas: {', '.join(ROLES)}")


if __name__ == "__main__":
    main()
