import type { ImportarMaestroResponse, MaestroEstado } from "../types/maestro";
import { api } from "./client";

/**
 * Sube el maestro de artículos de TBC (.xlsx) y lo aplica al catálogo cruzando por SKU (C-12).
 * Síncrono: la respuesta ya trae el reporte. Solo admin (`admin:manage`).
 */
export const importarMaestro = (archivo: File) => {
  const fd = new FormData();
  fd.append("archivo", archivo);
  return api.post<ImportarMaestroResponse>("/admin/productos/importar-maestro", fd);
};

/** Última importación, para mostrar "importado el …" junto al botón. Solo admin. */
export const getMaestroEstado = (signal?: AbortSignal) =>
  api.get<MaestroEstado>("/admin/productos/maestro/estado", undefined, signal);
