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
  // C-01 — overrides propios. El backend NO serializa nulls: si no hay override, el campo viene
  // ausente. Los `*Efectivo` siempre vienen, ya resueltos contra el global.
  descuentoPct?: number;
  comisionPct?: number;
  descuentoPctEfectivo: number;
  comisionPctEfectiva: number;
}

/** PUT /admin/nutricionistas/{id}/parametros — null en un campo lo devuelve al valor global. */
export interface ParametrosRequest {
  descuentoPct: number | null;
  comisionPct: number | null;
}
