// GET /dashboard/resumen. `ultimasRecetas` viene como RecetaResponse completa (verificado contra el backend).
import type { RecetaResponse } from "./receta";

export interface DashboardResumen {
  recetasPendientes: number;
  recetasAplicadasMes: number;
  recetasVencidasMes: number;
  comisionMesActual: number;
  ventasGeneradasMesActual: number;
  ultimasRecetas: RecetaResponse[];
}

// GET /dashboard/cierre-mensual?year=&month= — shape del contrato (endpoint aún en 500, no verificado).
export interface CierreMensual {
  year: number;
  month: number;
  recetasEmitidas: number;
  recetasAplicadas: number;
  tasaConversion: number;
  ventasGeneradas: number;
  comisionTotal: number;
  detalle: {
    recetaCodigo: string;
    paciente: string;
    ordenTotal: number;
    comisionMonto: number;
    paidAt: string;
  }[];
}
