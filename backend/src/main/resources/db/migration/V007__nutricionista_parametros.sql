-- C-01 (post-demo 2026-07-31): % de descuento y % de comisión configurables POR NUTRICIONISTA.
-- Leo: "está bueno tener una herramienta de que por nutricionista" (call 19:44).
-- Los setea SIEMPRE el admin, nunca la nutricionista (call 19:29).
--
-- Nullable a propósito: NULL = "usá el valor global de configuracion_sistema". Así no hay que
-- backfillear nada ni duplicar la configuración global en cada fila.

ALTER TABLE nutricionistas ADD COLUMN descuento_pct NUMERIC(5,2)
    CHECK (descuento_pct IS NULL OR (descuento_pct >= 0 AND descuento_pct <= 100));
ALTER TABLE nutricionistas ADD COLUMN comision_pct NUMERIC(5,2)
    CHECK (comision_pct IS NULL OR (comision_pct >= 0 AND comision_pct <= 100));

COMMENT ON COLUMN nutricionistas.descuento_pct IS
    'C-01: % de descuento propio de esta nutricionista. NULL = usa el global de configuracion_sistema.';
COMMENT ON COLUMN nutricionistas.comision_pct IS
    'C-01: % de comisión propio de esta nutricionista. NULL = usa el global de configuracion_sistema.';
