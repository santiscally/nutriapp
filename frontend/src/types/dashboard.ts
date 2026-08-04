// GET /dashboard/resumen. `ultimasRecetas` viene como RecetaResponse completa (verificado contra el backend).
import type { RecetaResponse } from "./receta";

export interface DashboardResumen {
  recetasPendientes: number;
  recetasAplicadasMes: number;
  recetasVencidasMes: number;
  comisionMesActual: number;
  ultimasRecetas: RecetaResponse[];
}

// GET /dashboard/estadisticas?meses=6 — serie mensual real (cronológica; el último es el mes en curso).
export interface EstadisticasMes {
  year: number;
  month: number;
  recetasEmitidas: number;
  recetasAplicadas: number;
  comisionTotal: number;
}

export interface Estadisticas {
  meses: EstadisticasMes[];
}

// GET /dashboard/cierre-mensual?year=&month= — shape del contrato (endpoint aún en 500, no verificado).
export interface CierreMensual {
  year: number;
  month: number;
  recetasEmitidas: number;
  recetasAplicadas: number;
  tasaConversion: number;
  comisionTotal: number;
  detalle: {
    recetaCodigo: string;
    paciente: string;
    comisionMonto: number;
    paidAt: string;
    liquidadaAt?: string | null;
  }[];
}
