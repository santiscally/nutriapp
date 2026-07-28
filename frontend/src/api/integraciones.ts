import type {
  IntegracionesEstado,
  ResyncCuponesResponse,
  SyncProductosResponse,
} from "../types/integraciones";
import { api } from "./client";

/** Estado de las 4 integraciones externas (2.7). Solo admin (admin:manage). */
export const getIntegracionesEstado = (signal?: AbortSignal) =>
  api.get<IntegracionesEstado>("/admin/integraciones/estado", undefined, signal);

/** Reintenta el registro de los cupones pendientes de sync (2.8). Solo admin. */
export const resyncCupones = () =>
  api.post<ResyncCuponesResponse>("/admin/tiendanube/resync-cupones");

/** Fuerza la sync del catálogo desde Contabilium (2.9). En stub → 503. Solo admin. */
export const syncProductos = () =>
  api.post<SyncProductosResponse>("/admin/contabilium/sync-productos");
