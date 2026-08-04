// GET /me — cualquier autenticado
export type EstadoValidacion = "PENDIENTE" | "APROBADA" | "RECHAZADA";

export interface Me {
  id: string;
  nombre: string;
  apellido: string;
  email: string;
  roles: string[]; // ["NUTRICIONISTA"] | ["ADMIN"]
  authorities: string[]; // ["recetas:write", ...] namespace resource_access.nutriapp-backend
  estadoValidacion: EstadoValidacion;
  /** C-17: avatar como data URI. Ausente si no cargó foto. */
  foto?: string | null;
}
