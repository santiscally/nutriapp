// S-13 / S-14 — panel consolidado y listado de bonos de todas las profesionales (solo ADMIN).
import type {
  AdminEstadisticas,
  AdminReceta,
  AdminRecetaQuery,
  AdminResumen,
} from "../types/admin";
import type { PageResponse } from "../types/common";
import { api } from "./client";

export const getAdminResumen = (signal?: AbortSignal) =>
  api.get<AdminResumen>("/admin/dashboard/resumen", undefined, signal);

export const getAdminEstadisticas = (meses = 6, signal?: AbortSignal) =>
  api.get<AdminEstadisticas>("/admin/dashboard/estadisticas", { meses }, signal);

export const listarAdminRecetas = (query: AdminRecetaQuery, signal?: AbortSignal) =>
  api.get<PageResponse<AdminReceta>>(
    "/admin/recetas",
    { ...query, size: query.size ?? 10 },
    signal,
  );
