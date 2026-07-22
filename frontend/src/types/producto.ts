// Espejo de ProductoResponse (GET /productos) + filtros. Shape verificado contra el backend seeded.
export interface Producto {
  id: string;
  sku: string;
  nombre: string;
  descripcion: string;
  precio: number;
  stock: number;
  imagenUrl?: string; // puede no venir en el seed
  marca: string;
  laboratorio: string;
  principioActivo: string;
  presentacion: string;
  publicado: boolean;
  origen: string; // SEED | TIENDANUBE | ...
}

// GET /productos/filtros — valores distintos para poblar los dropdowns de búsqueda.
export interface ProductoFiltros {
  marcas: string[];
  laboratorios: string[];
  presentaciones: string[];
}

// Query params de GET /productos.
export interface ProductoQuery {
  q?: string;
  marca?: string;
  laboratorio?: string;
  principioActivo?: string;
  presentacion?: string;
  page?: number;
  size?: number;
}
