import type { PageResponse } from "../types/common";
import type {
  RecetaCreateRequest,
  RecetaQuery,
  RecetaResponse,
} from "../types/receta";
import { api } from "./client";

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
