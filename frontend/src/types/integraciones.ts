// Estado y acciones de resiliencia de las integraciones externas (2.7–2.9). Solo admin.
// Espejo de los DTOs del backend: GET /admin/integraciones/estado + los dos POST de recuperación.

export type ModoIntegracion = "stub" | "live";

export interface IntegracionEstado {
  proveedor: string; // contabilium | tiendanube | mail
  modo: ModoIntegracion;
  /** false en stub (sin conexión por diseño), null en live sin interacción aún, true/false según último resultado. */
  disponible: boolean | null;
  /** Trabajo acumulado por caída: cupones sin sync (tiendanube) / notifs QUEUED (mail) / 0 (contabilium). */
  pendientes: number;
  ultimoError?: string;
  ultimoErrorAt?: string;
  ultimaSync?: string;
  /** Solo contabilium: sync del catálogo en curso ahora mismo. */
  sincronizando?: boolean | null;
  /** Solo contabilium: resumen del último sync ("revisados=.. creados=.." o "error: ..."). */
  ultimoResultado?: string | null;
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

/** POST /admin/contabilium/sync-productos → 202: la sync corre en background. El resultado
 *  se sigue por el estado (sincronizando + ultimoResultado). */
export interface SyncIniciadaResponse {
  estado: "iniciada" | "en_curso";
  mensaje: string;
}
