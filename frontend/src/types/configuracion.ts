// GET /configuracion (cualquier autenticado) · PUT /admin/configuracion (solo admin).
// Parámetros de negocio que el admin edita en runtime.
export interface Configuracion {
  descuentoPct: number;
  comisionPct: number;
}
