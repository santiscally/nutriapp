// Configuración leída de variables Vite (import.meta.env.VITE_*).
// Defaults pensados para una máquina limpia; en la de Santi el backend queda en :8088
// (el 8080 lo ocupa plataforma GIA) → ajustar VITE_API_BASE_URL en frontend/.env.local.

const env = import.meta.env;

/** Base del backend SIN el prefijo /api/v1 (ej. http://localhost:8080). */
const API_ORIGIN = env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const config = {
  /** Base completa de la API REST. El contrato vive bajo /api/v1. */
  apiBaseUrl: `${API_ORIGIN}/api/v1`,
  /**
   * Modo pre-lanzamiento: `/` muestra la landing "Próximamente" y lo único público es
   * `/registro`. El login sigue existiendo en `/ingresar`, sin link (equipo + demos).
   * Se hornea en el build (Vite): apagarlo requiere `npm run build` de nuevo.
   */
  comingSoon: env.VITE_COMING_SOON === "true",
  /** Casilla pública de contacto. Sigue en @nutriappok.com.ar hasta que el cliente mude el mail. */
  contactoEmail: env.VITE_CONTACTO_EMAIL ?? "info@nutriappok.com.ar",
  keycloak: {
    url: env.VITE_KEYCLOAK_URL ?? "http://localhost:8081",
    realm: env.VITE_KEYCLOAK_REALM ?? "bonosapp",
    clientId: env.VITE_KEYCLOAK_CLIENT_ID ?? "bonosapp-frontend",
  },
} as const;
