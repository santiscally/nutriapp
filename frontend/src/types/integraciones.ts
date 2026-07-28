// Estado y acciones de resiliencia de las integraciones externas (2.7–2.9). Solo admin.
// Espejo de los DTOs del backend: GET /admin/integraciones/estado + los dos POST de recuperación.

export type ModoIntegracion = "stub" | "live";

export interface IntegracionEstado {
  proveedor: string; // contabilium | tiendanube | mail | whatsapp
  modo: ModoIntegracion;
  /** false en stub (sin conexión por diseño), null en live sin interacción aún, true/false según último resultado. */
  disponible: boolean | null;
  /** Trabajo acumulado por caída: cupones sin sync (tiendanube) / notifs QUEUED (mail·whatsapp) / 0 (contabilium). */
  pendientes: number;
  ultimoError?: string;
  ultimoErrorAt?: string;
  ultimaSync?: string;
}

export interface IntegracionesEstado {
  integraciones: IntegracionEstado[];
}

/** POST /admin/tiendanube/resync-cupones */
export interface ResyncCuponesResponse {
  intentados: number;
  sincronizados: number;
  pendientes: number;
}

/** POST /admin/contabilium/sync-productos (en stub responde 503) */
export interface SyncProductosResponse {
  revisados: number;
  creados: number;
  actualizados: number;
  sinCambios: number;
  syncedAt: string;
}
