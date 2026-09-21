// S-13 / S-14 — las dos solapas nuevas del admin (F-24 PANEL y F-25 BONOS).
// Shapes verificados contra el backend real (no sólo contra 05-api-endpoints.md).
import type { EstadisticasMes } from "./dashboard";
import type { Paciente } from "./paciente";
import type { EstadoReceta, RecetaItem } from "./receta";

/** GET /admin/dashboard/resumen — el panel de la profesional, consolidado sobre todas. */
export interface AdminResumen {
  recetasPendientes: number;
  recetasAplicadasMes: number;
  recetasVencidasMes: number;
  comisionMesActual: number;
  /** El admin sí ve facturación: es con lo que liquida. En los endpoints de ella no existe. */
  facturadoMesActual: number;
  profesionalesActivos: number;
  profesionalesPendientes: number;
  ultimasRecetas: AdminReceta[];
}

/** GET /admin/dashboard/estadisticas?meses=6 — igual que la de ella más el facturado. */
export interface AdminEstadisticasMes extends EstadisticasMes {
  facturadoTotal: number;
}

export interface AdminEstadisticas {
  meses: AdminEstadisticasMes[];
}

/**
 * GET /admin/recetas — el bono de siempre + de quién es. Los nombres de campo son los mismos que
 * en `RecetaResponse` a propósito; lo único que cambia es que no vienen `waMeUrl` ni
 * `notificaciones` (el WhatsApp lo manda la profesional, no el admin).
 */
export interface AdminReceta {
  id: string;
  codigo: string;
  estado: EstadoReceta;
  paciente: Paciente;
  items: RecetaItem[];
  descuentoPct: number;
  emitidaAt: string;
  venceAt: string;
  cuponSyncEstado: string;
  nutricionista: {
    id: string;
    nombre: string;
    apellido: string;
    email: string;
  };
  conversion?: {
    ordenNumero: number;
    paidAt: string;
    comisionPct: number;
    comisionMonto: number;
    /** Sólo acá: lo que facturó la orden en la tienda. */
    ordenTotal: number;
    liquidadaAt?: string | null;
  } | null;
}

/** Query de GET /admin/recetas: los filtros de la profesional + el de profesional (F-25). */
export interface AdminRecetaQuery {
  estado?: EstadoReceta | "";
  nutricionistaId?: string;
  q?: string;
  desde?: string; // YYYY-MM-DD
  hasta?: string;
  page?: number;
  size?: number;
}
