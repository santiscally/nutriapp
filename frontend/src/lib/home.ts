// Pantalla inicial de cada rol. Vive acá (y no en el guard) para que el archivo del componente
// exporte sólo componentes — si no, fast-refresh deja de andar.
import type { Me } from "../types/session";

/** C-07: el admin no tiene panel de recetas; su casa es la bandeja de nutricionistas (C-09). */
export function homeDe(me: Me | null): string {
  return me?.roles.includes("ADMIN") ? "/nutricionistas" : "/dashboard";
}
