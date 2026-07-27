-- Parámetros de negocio configurables por el ADMIN en runtime (antes fijos en application.yml):
--   descuento_pct: % de descuento de las recetas (global y fijo — el nutricionista no lo edita).
--   comision_pct : % de comisión del nutricionista sobre la orden convertida.
-- Tabla singleton (una sola fila). Los valores iniciales replican los defaults de nutriapp.recetas.*.

CREATE TABLE configuracion_sistema (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    descuento_pct   NUMERIC(5,2) NOT NULL DEFAULT 15.00
                    CHECK (descuento_pct >= 0 AND descuento_pct <= 100),
    comision_pct    NUMERIC(5,2) NOT NULL DEFAULT 10.00
                    CHECK (comision_pct >= 0 AND comision_pct <= 100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      UUID,
    deleted_at      TIMESTAMPTZ
);
CREATE TRIGGER trg_configuracion_sistema_updated BEFORE UPDATE ON configuracion_sistema
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- Fila única inicial.
INSERT INTO configuracion_sistema (descuento_pct, comision_pct) VALUES (15.00, 10.00);
