import type { PageResponse } from "../types/common";
import type {
  CatalogoResumen,
  Producto,
  ProductoAdmin,
  ProductoFiltros,
  ProductoQuery,
} from "../types/producto";
import { api } from "./client";

export const buscarProductos = (query: ProductoQuery, signal?: AbortSignal) =>
  api.get<PageResponse<Producto>>(
    "/productos",
    { ...query, size: query.size ?? 10 },
    signal,
  );

export const getFiltros = (signal?: AbortSignal) =>
  api.get<ProductoFiltros>("/productos/filtros", undefined, signal);

// --- Admin ---

/** Catálogo completo (incluye despublicados). `sinMaestro` filtra los que no matchearon el Excel. */
export const listarProductosAdmin = (
  params: {
    q?: string;
    departamento?: string;
    categoria?: string;
    sinMaestro?: boolean;
    publicado?: boolean;
    page?: number;
    size?: number;
  },
  signal?: AbortSignal,
) =>
  api.get<PageResponse<ProductoAdmin>>(
    "/admin/productos",
    {
      q: params.q || undefined,
      departamento: params.departamento || undefined,
      categoria: params.categoria || undefined,
      sinMaestro: params.sinMaestro ? true : undefined,
      publicado: params.publicado,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
    signal,
  );

export const getCatalogoResumen = (signal?: AbortSignal) =>
  api.get<CatalogoResumen>("/admin/productos/resumen", undefined, signal);
