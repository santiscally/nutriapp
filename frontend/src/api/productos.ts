import type { PageResponse } from "../types/common";
import type { Producto, ProductoFiltros, ProductoQuery } from "../types/producto";
import { api } from "./client";

export const buscarProductos = (query: ProductoQuery, signal?: AbortSignal) =>
  api.get<PageResponse<Producto>>(
    "/productos",
    { ...query, size: query.size ?? 10 },
    signal,
  );

export const getFiltros = (signal?: AbortSignal) =>
  api.get<ProductoFiltros>("/productos/filtros", undefined, signal);
