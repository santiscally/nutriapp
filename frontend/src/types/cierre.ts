// C-06 — cierre consolidado del admin. Espejo de CierreConsolidadoResponse.

export interface CierreFila {
  nutricionistaId: string;
  nombre: string;
  apellido: string;
  email: string;
  /** C-08: puede faltar en altas previas al registro ampliado. */
  cuit?: string;
  /** Convertidas en el rango (incluye las ya liquidadas). */
  recetas: number;
  facturado: number;
  comision: number;
  /** Subconjunto todavía impago: es lo que se liquida. */
  recetasPendientes: number;
  comisionPendiente: number;
  recetaIdsPendientes: string[];
}

export interface CierreTotales {
  recetas: number;
  facturado: number;
  comision: number;
  comisionPendiente: number;
}

export interface CierreConsolidado {
  desde: string;
  hasta: string;
  filas: CierreFila[];
  totales: CierreTotales;
}

export interface LiquidacionOmitida {
  recetaId: string;
  codigo?: string;
  motivo: string;
}

export interface LiquidacionResultado {
  liquidadas: number;
  comisionTotal: number;
  liquidadaAt: string;
  omitidas: LiquidacionOmitida[];
}
