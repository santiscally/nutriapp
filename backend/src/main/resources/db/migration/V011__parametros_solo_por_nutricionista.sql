-- V011 — los % de descuento y comisión pasan a vivir SÓLO en la nutricionista.
--
-- V004 creó `configuracion_sistema` (un global editable por el admin) y V007 le sumó un override
-- por nutricionista, nullable, donde NULL significaba "usá el global". Convivían dos lugares para
-- el mismo dato y dos formas de leerlo: la fuente de verdad quedaba repartida y cualquier lectura
-- que se salteara el resolutor daba un número distinto al de la emisión.
--
-- Ahora cada nutricionista tiene sus propios porcentajes, siempre. Se acabó el NULL-como-señal.
--
-- Las recetas ya emitidas no se tocan: los % están snapshoteados en la fila de la receta desde
-- C-01, así que ni los cierres ni las liquidaciones históricas cambian de valor por esta migración.

-- 1. Backfill: quien no tenía override hereda el global vigente al momento de migrar (no un
--    literal 15/10 — si el admin lo cambió, el valor que estaba aplicando es el suyo).
UPDATE nutricionistas n
SET descuento_pct = COALESCE(n.descuento_pct, c.descuento_pct),
    comision_pct  = COALESCE(n.comision_pct,  c.comision_pct)
FROM (SELECT descuento_pct, comision_pct
      FROM configuracion_sistema
      WHERE deleted_at IS NULL
      ORDER BY created_at ASC
      LIMIT 1) c
WHERE n.descuento_pct IS NULL OR n.comision_pct IS NULL;

-- 2. El default de la columna sale del mismo global: una nutricionista que se registra arranca con
--    los valores que el admin venía usando, y él los ajusta al aprobarla. Sin default, el registro
--    público (que no conoce ningún porcentaje) no podría insertar.
DO $$
DECLARE
    v_descuento NUMERIC(5,2);
    v_comision  NUMERIC(5,2);
BEGIN
    SELECT descuento_pct, comision_pct INTO v_descuento, v_comision
    FROM configuracion_sistema WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1;

    -- Si la tabla quedó vacía (nadie debería haberla borrado, pero no vamos a fallar la migración
    -- por eso), caemos a los valores con los que se sembró en V004.
    v_descuento := COALESCE(v_descuento, 15.00);
    v_comision  := COALESCE(v_comision, 10.00);

    EXECUTE format('ALTER TABLE nutricionistas ALTER COLUMN descuento_pct SET DEFAULT %L', v_descuento);
    EXECUTE format('ALTER TABLE nutricionistas ALTER COLUMN comision_pct SET DEFAULT %L', v_comision);

    UPDATE nutricionistas SET descuento_pct = v_descuento WHERE descuento_pct IS NULL;
    UPDATE nutricionistas SET comision_pct  = v_comision  WHERE comision_pct  IS NULL;
END $$;

ALTER TABLE nutricionistas ALTER COLUMN descuento_pct SET NOT NULL;
ALTER TABLE nutricionistas ALTER COLUMN comision_pct  SET NOT NULL;

-- 3. Los CHECK de V007 aceptaban NULL: ya no puede haberlo.
ALTER TABLE nutricionistas DROP CONSTRAINT IF EXISTS nutricionistas_descuento_pct_check;
ALTER TABLE nutricionistas DROP CONSTRAINT IF EXISTS nutricionistas_comision_pct_check;
ALTER TABLE nutricionistas ADD CONSTRAINT nutricionistas_descuento_pct_check
    CHECK (descuento_pct >= 0 AND descuento_pct <= 100);
ALTER TABLE nutricionistas ADD CONSTRAINT nutricionistas_comision_pct_check
    CHECK (comision_pct >= 0 AND comision_pct <= 100);

COMMENT ON COLUMN nutricionistas.descuento_pct IS
    '% de descuento de las recetas de esta nutricionista. Lo setea el admin. Se snapshotea al emitir.';
COMMENT ON COLUMN nutricionistas.comision_pct IS
    '% de comisión de esta nutricionista. Lo setea el admin. Se snapshotea al convertir.';

-- 4. Fuera el global.
DROP TABLE configuracion_sistema;
