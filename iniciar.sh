#!/usr/bin/env bash
# =====================================================================
# Inicia el sistema de inventario en Linux o en GitHub Codespaces.
# (En Windows use iniciar.ps1.)
#
#   ./iniciar.sh            # iniciar
#   ./iniciar.sh detener    # detener (los datos se conservan)
#
# La primera vez crea .env con contrasenas aleatorias (no se sube a GitHub).
# En Codespaces agrega a CORS la direccion https://...-8080.app.github.dev
# que GitHub asigna al puerto 8080.
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"
PUERTO=8080

if [ "${1:-}" = "detener" ]; then docker compose stop; exit 0; fi

command -v docker >/dev/null || { echo "Docker no esta instalado."; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker no esta en ejecucion."; exit 1; }

origenes="http://localhost:${PUERTO},http://127.0.0.1:${PUERTO}"
if [ -n "${CODESPACE_NAME:-}" ]; then
  url="https://${CODESPACE_NAME}-${PUERTO}.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
  origenes="${origenes},${url}"
fi

if [ ! -f .env ]; then
  {
    echo "MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)"
    echo "MYSQL_USER=inventario"
    echo "MYSQL_PASSWORD=$(openssl rand -hex 24)"
    echo "JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')"
  } > .env
  chmod 600 .env
  echo ".env creado con contrasenas aleatorias."
fi
grep -v -E '^(CORS_ORIGENES|PUERTO_WEB)=' .env > .env.tmp || true
{ cat .env.tmp; echo "PUERTO_WEB=${PUERTO}"; echo "CORS_ORIGENES=${origenes}"; } > .env
rm -f .env.tmp

# En Codespaces, Docker convive con dos firewalls (iptables e iptables-legacy).
# El legacy puede traer la politica FORWARD en DROP y bloquear el trafico entre
# contenedores: el backend no alcanza a MySQL ("Connect timed out").
if [ -n "${CODESPACE_NAME:-}" ] && command -v iptables-legacy >/dev/null; then
  sudo iptables-legacy -P FORWARD ACCEPT 2>/dev/null || true
fi

echo "Levantando el sistema (la primera vez tarda entre 5 y 15 minutos)..."
docker compose up -d --build

echo "Esperando a que la pagina responda..."
for _ in $(seq 1 60); do
  if curl -fs "http://localhost:${PUERTO}/" >/dev/null; then break; fi
  sleep 5
done
docker compose ps
echo
if [ -n "${CODESPACE_NAME:-}" ]; then
  echo "Sistema iniciado: ${url}"
  echo "(o en la pestana PORTS de VS Code, puerto ${PUERTO} -> icono del globo)"
else
  echo "Sistema iniciado: http://localhost:${PUERTO}"
fi
