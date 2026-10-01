// GET /me — cualquier autenticado
export type EstadoValidacion = "PENDIENTE" | "APROBADA" | "RECHAZADA";

export interface Me {
  id: string;
  nombre: string;
  apellido: string;
  email: string;
  roles: string[]; // ["NUTRICIONISTA"] | ["ADMIN"]
  authorities: string[]; // ["recetas:write", ...] namespace resource_access.bonosapp-backend
  estadoValidacion: EstadoValidacion;
  /** C-17: avatar como data URI. Ausente si no cargó foto. */
  foto?: string | null;
  /** S-12 — % de comisión propio. null para el admin. Lo muestra el perfil (F-10). */
  comisionPct?: number | null;
  /** S-11 — null en las altas anteriores a V014 y para el admin. */
  profesion?: string | null;
  jurisdiccion?: string | null;
}
