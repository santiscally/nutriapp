// Espejo de ProductoResponse (GET /productos) + filtros. Shape verificado contra el backend.
export interface Producto {
  id: string;
  sku: string;
  /** Código de barras del ERP. Es buscable: se puede escanear o pegar en el buscador. */
  codigoBarras?: string | null;
  nombre: string;
  descripcion: string;
  /** Descripción larga del maestro de TBC — la que se muestra en "más info". */
  descripcionWeb?: string | null;
  precio: number;
  stock: number;
  /** Link a la imagen en el CDN de TiendaNube. Solo ~1 de cada 4 productos la tiene. */
  imagenUrl?: string | null;
  /** Marca = Subrubro de Contabilium. */
  marca?: string | null;
  // --- Los cuatro de abajo vienen del maestro de artículos de TBC: null si el producto no está ahí.
  departamento?: string | null;
  /** OJO: antes traía el Rubro de Contabilium ("Producto terminado"); ahora, la categoría del maestro. */
  categoria?: string | null;
  subcategoria?: string | null;
  laboratorio?: string | null;
  /** Tags del maestro. Son la vía de búsqueda por propiedad/principio activo ("magnesio"). */
  tags?: string[];
  publicado: boolean;
  origen: string; // SEED | CONTABILIUM | TIENDANUBE | ...
}

/** Nodo del árbol de la taxonomía del maestro (para encadenar los dropdowns). */
export interface TaxonomiaCategoria {
  nombre: string;
  subcategorias: string[];
}

export interface TaxonomiaDepartamento {
  nombre: string;
  categorias: TaxonomiaCategoria[];
}

// GET /productos/filtros — valores distintos para poblar los dropdowns de búsqueda.
export interface ProductoFiltros {
  marcas: string[];
  categorias: string[];
  departamentos: string[];
  subcategorias: string[];
  laboratorios: string[];
  /** Solo las combinaciones que existen de verdad: sirve para filtrar en cascada. */
  taxonomia: TaxonomiaDepartamento[];
  /** Rango real de precios de lo recetable: los extremos del slider. null si el catálogo está vacío. */
  precioMin?: number | null;
  precioMax?: number | null;
}

// Query params de GET /productos.
export interface ProductoQuery {
  q?: string;
  marca?: string;
  departamento?: string;
  categoria?: string;
  subcategoria?: string;
  laboratorio?: string;
  /** Tag exacto (click en un tag), a diferencia de `q` que busca parcial. */
  tag?: string;
  conStock?: boolean;
  precioMin?: number;
  precioMax?: number;
  page?: number;
  size?: number;
}

// --- Catálogo del admin (GET /admin/productos) ---
// Es el mismo producto más el estado de las dos fuentes que lo escriben. La nutricionista sólo ve
// publicados y nada de esto; el admin necesita justamente lo contrario: qué quedó afuera y por qué.
export interface ProductoAdmin {
  producto: Producto;
  /** ¿El maestro de TBC llegó a tocarlo? false = no matcheó por SKU o nunca se importó. */
  enMaestro: boolean;
  bloqueadoMaestro: boolean;
  maestroSyncedAt?: string | null;
  lastSyncedAt?: string | null;
  /** Contexto del ERP: no viaja al emisor, pero explica por qué entró o no al catálogo. */
  tipoErp?: string | null;
  activoErp: boolean;
  rubro?: string | null;
  /** Por qué no aparece en el buscador, ya resuelto en castellano. null si está publicado. */
  motivoNoPublicado?: string | null;
}

export interface CatalogoResumen {
  total: number;
  publicados: number;
  noPublicados: number;
  sinMaestro: number;
  bloqueados: number;
}
