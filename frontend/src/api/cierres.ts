import type { CierreConsolidado, LiquidacionResultado } from "../types/cierre";
import { api } from "./client";

/** C-06 — consolidado de todas las nutricionistas. Fechas YYYY-MM-DD, inclusive ambas. */
export const getCierreConsolidado = (desde: string, hasta: string, signal?: AbortSignal) =>
  api.get<CierreConsolidado>("/admin/liquidaciones/consolidado", { desde, hasta }, signal);

/** C-05 — marca como liquidadas (pagadas) las recetas indicadas. Idempotente. */
export const liquidarRecetas = (recetaIds: string[]) =>
  api.post<LiquidacionResultado>("/admin/liquidaciones", { recetaIds });
