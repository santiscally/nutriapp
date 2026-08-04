// Espejo de ImportarMaestroResponse / MaestroEstadoResponse — importación del maestro de artículos
// de TBC (C-12). Solo admin.

export interface ImportarMaestroResponse {
  filasLeidas: number;
  filasMatcheadas: number;
  filasActualizadas: number;
  filasSinMatch: number;
  filasRechazadas: number;
  /** Muestra recortada a 50; el total está en filasSinMatch. */
  skusSinMatch: string[];
  /** Filas descartadas y por qué (SKU repetido, ID de Contabilium que no coincide). */
  rechazos: string[];
  publicados: number;
  despublicados: number;
  importadoAt: string;
  /** Mensaje ya armado por el backend, con los números adentro: se muestra tal cual. */
  mensaje: string;
}

export interface MaestroEstado {
  importadoAt: string | null;
  nombreArchivo: string | null;
  filasLeidas: number | null;
  filasMatcheadas: number | null;
  filasSinMatch: number | null;
  filasRechazadas: number | null;
  catalogoActualizadoAt: string | null;
}
