import type { Configuracion } from "../types/configuracion";
import { api } from "./client";

/** Parámetros vigentes (descuento fijo global + comisión). Cualquier autenticado. */
export const getConfiguracion = (signal?: AbortSignal) =>
  api.get<Configuracion>("/configuracion", undefined, signal);

/** Actualiza los parámetros. Solo admin (admin:manage). */
export const updateConfiguracion = (body: Configuracion) =>
  api.put<Configuracion>("/admin/configuracion", body);
