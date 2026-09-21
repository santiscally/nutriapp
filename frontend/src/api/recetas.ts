import type { PageResponse } from "../types/common";
import type {
  RecetaCreateRequest,
  RecetaQuery,
  RecetaResponse,
} from "../types/receta";
import { api } from "./client";
import { config } from "../config";
import { getValidAccessToken } from "../lib/auth";

/** Emite una receta: crea receta+items+código único y encola notificaciones. */
export const emitirReceta = (body: RecetaCreateRequest) =>
  api.post<RecetaResponse>("/recetas", body);

export const listarRecetas = (query: RecetaQuery, signal?: AbortSignal) =>
  api.get<PageResponse<RecetaResponse>>(
    "/recetas",
    { ...query, size: query.size ?? 10 },
    signal,
  );

export const getReceta = (id: string, signal?: AbortSignal) =>
  api.get<RecetaResponse>(`/recetas/${id}`, undefined, signal);

/** Anula (solo PENDIENTE): intenta borrar el cupón en TiendaNube. */
export const anularReceta = (id: string) =>
  api.post<RecetaResponse>(`/recetas/${id}/anular`);

/** Reenvía notificaciones (solo PENDIENTE). */
export const reenviarReceta = (id: string) =>
  api.post<RecetaResponse>(`/recetas/${id}/reenviar`);

/**
 * F-15 — baja de nuevo el PDF del bono. Igual que la matrícula del admin: el endpoint pide Bearer
 * y el browser no lo manda en una navegación, así que se baja con fetch y se guarda como blob.
 */
export async function descargarBonoPdf(id: string, codigo: string): Promise<void> {
  const token = await getValidAccessToken();
  const res = await fetch(`${config.apiBaseUrl}/recetas/${id}/pdf`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) throw new Error("No se pudo descargar el bono.");
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = `bono-${codigo}.pdf`;
  a.click();
  URL.revokeObjectURL(url);
}
