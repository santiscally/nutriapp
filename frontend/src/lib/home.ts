// Pantalla inicial de cada rol. Vive acá (y no en el guard) para que el archivo del componente
// exporte sólo componentes — si no, fast-refresh deja de andar.
import type { Me } from "../types/session";

/**
 * C-07: el admin no tiene panel de recetas propios. Desde F-24 sí tiene un panel consolidado, y
 * esa pasó a ser su casa: antes entraba directo a la bandeja de solicitudes (C-09).
 */
export function homeDe(me: Me | null): string {
  return me?.roles.includes("ADMIN") ? "/panel" : "/dashboard";
}
