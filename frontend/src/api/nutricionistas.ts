import type { PageResponse } from "../types/common";
import type { EstadoValidacion, NutricionistaAdmin, ParametrosRequest } from "../types/nutricionista";
import { api } from "./client";
import { config } from "../config";
import { getValidAccessToken } from "../lib/auth";

/** Bandeja de validación (admin:manage). `estado` vacío = todas. */
export const listarNutricionistas = (
  params: { estado?: EstadoValidacion | ""; q?: string; page?: number; size?: number },
  signal?: AbortSignal,
) =>
  api.get<PageResponse<NutricionistaAdmin>>(
    "/admin/nutricionistas",
    {
      estado: params.estado || undefined,
      q: params.q || undefined,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
    signal,
  );

/** Aprueba la solicitud: habilita el usuario en Keycloak y recién ahí puede loguearse. */
export const aprobarNutricionista = (id: string) =>
  api.post<NutricionistaAdmin>(`/admin/nutricionistas/${id}/aprobar`);

export const rechazarNutricionista = (id: string, motivo?: string) =>
  api.post<NutricionistaAdmin>(`/admin/nutricionistas/${id}/rechazar`, { motivo });

/** C-01 — % propios de esta nutricionista. null = usa el global. */
export const actualizarParametros = (id: string, body: ParametrosRequest) =>
  api.put<NutricionistaAdmin>(`/admin/nutricionistas/${id}/parametros`, body);

/**
 * C-08 — abre la matrícula en una pestaña nueva. No se puede usar un <a href> pelado: el endpoint
 * pide Bearer y el browser no lo manda en una navegación. Se baja con fetch y se abre como blob.
 */
export async function abrirMatricula(id: string): Promise<void> {
  const token = await getValidAccessToken();
  const res = await fetch(`${config.apiBaseUrl}/admin/nutricionistas/${id}/matricula`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) throw new Error("No se pudo abrir la matrícula.");
  const url = URL.createObjectURL(await res.blob());
  window.open(url, "_blank", "noopener");
  // El object URL se libera cuando la pestaña ya lo cargó; un revoke inmediato la deja en blanco.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
