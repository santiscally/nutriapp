-- C-05 (post-demo 2026-07-31): estado terminal LIQUIDADA.
-- Una receta APLICADA (el paciente compró) pasa a LIQUIDADA cuando el admin le pagó la comisión
-- a la nutricionista. A partir de ahí deja de aparecer entre las pendientes de liquidar.
-- Se liquida POR RECETA, aunque la pantalla del cierre sea mensual (call 57:54).

ALTER TABLE recetas DROP CONSTRAINT IF EXISTS recetas_estado_check;
ALTER TABLE recetas ADD CONSTRAINT recetas_estado_check
    CHECK (estado IN ('PENDIENTE','APLICADA','VENCIDA','ANULADA','LIQUIDADA'));

ALTER TABLE recetas ADD COLUMN liquidada_at TIMESTAMPTZ;

-- C-04: los cierres agrupan por la fecha de pago real en TiendaNube (orden_paid_at),
-- no por la de emisión. Índice para las ventanas del cierre y del dashboard.
CREATE INDEX idx_recetas_nutri_orden_paid ON recetas(nutricionista_id, orden_paid_at);
