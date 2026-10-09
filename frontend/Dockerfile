# =====================================================================
# Imagen del cliente web (HU-42: despliegue reproducible)
#
# Compila Angular en modo producción y lo sirve con Nginx, que además hace
# de proxy inverso hacia el backend: el navegador ve un solo origen, así que
# no hace falta abrir CORS ni exponer el puerto del backend.
# =====================================================================

# --- Etapa 1: compilación -------------------------------------------------
FROM node:22-alpine AS construccion
WORKDIR /fuente

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npx ng build --configuration production

# --- Etapa 2: servidor web ------------------------------------------------
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=construccion /fuente/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
