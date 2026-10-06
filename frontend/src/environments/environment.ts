// Configuración de producción: el cliente se sirve desde el mismo dominio que la API (Nginx en el VPS).
export const environment = {
  production: true,
  apiUrl: '/api'
};
