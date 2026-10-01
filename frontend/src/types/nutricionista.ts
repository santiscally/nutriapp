// Espejo de la bandeja de validación de nutricionistas (admin). Fuente: NutricionistaResponse del back.

export type EstadoValidacion = "PENDIENTE" | "APROBADA" | "RECHAZADA";

export interface NutricionistaAdmin {
  id: string;
  nombre: string;
  apellido: string;
  email: string;
  telefono?: string;
  matricula?: string;
  // C-08 — datos fiscales del registro (ausentes en altas previas al cambio).
  dni?: string;
  cuit?: string;
  condicionFiscal?: string;
  /** ¿Subió matrícula? El listado no trae los bytes, sólo si hay algo para mirar. */
  tieneMatricula: boolean;
  estadoValidacion: EstadoValidacion;
  validadoAt?: string | null;
  notasValidacion?: string | null;
  createdAt: string;
  // F-14: el descuento es del producto; de la profesional queda sólo la comisión.
  comisionPct: number;
  /** ¿Puede loguearse hoy? Espejo del enabled de Keycloak. */
  activo: boolean;
}

/** PUT /admin/nutricionistas/{id}/parametros */
export interface ParametrosRequest {
  comisionPct: number;
}
