-- Ola 3 — Maestro de artículos de TBC + ajustes de catálogo (mail de Gon 2026-08-03).
-- Análisis completo y números: instrucciones_claude/07-maestro-articulos-y-catalogo.md
--
-- Dos fuentes escriben la misma fila de `productos` y NINGÚN campo tiene dos dueños:
--   Contabilium (sync)  → nombre, descripcion, precio, stock, marca, rubro*, tipo_erp, activo_erp, codigo_barras
--   Excel maestro (imp) → departamento, categoria, subcategoria, laboratorio, descripcion_web,
--                         imagen_url, bloqueado_maestro, tags
-- Así el orden de ejecución (sincronizar / importar) deja de importar.

-- --- Campos que aporta el Excel maestro ---------------------------------------------------------
ALTER TABLE productos ADD COLUMN departamento     VARCHAR(120);
ALTER TABLE productos ADD COLUMN subcategoria     VARCHAR(160);
ALTER TABLE productos ADD COLUMN descripcion_web  TEXT;
-- ESTADO = BLOQUEADO en el maestro: "los bloqueados no deberían mostrarse en Nutriapp" (hoja de Gon).
ALTER TABLE productos ADD COLUMN bloqueado_maestro BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE productos ADD COLUMN maestro_synced_at TIMESTAMPTZ;

-- --- Campos que aporta Contabilium ---------------------------------------------------------------
-- Código de barras: lo pidió Gon explícitamente y viene en el payload de /conceptos/search.
ALTER TABLE productos ADD COLUMN codigo_barras VARCHAR(64);
-- Entradas de la regla de publicación, persistidas para que el importador pueda recalcularla
-- sin volver a pegarle a Contabilium (y viceversa).
ALTER TABLE productos ADD COLUMN tipo_erp   VARCHAR(40);
ALTER TABLE productos ADD COLUMN activo_erp BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE productos ADD COLUMN rubro      VARCHAR(120);
ALTER TABLE productos ADD COLUMN rubro_id   VARCHAR(32);

-- --- `categoria` cambia de dueño -----------------------------------------------------------------
-- Hasta acá `categoria` guardaba el Rubro de Contabilium, que para el 99,8% del catálogo recetable
-- vale "Producto terminado" → como filtro era decorativo. Además el mail lo convierte en filtro de
-- ingreso (solo entra el rubro 144331), con lo cual como faceta desaparece.
-- El rubro se muda a su columna y `categoria` pasa a ser la CATEGORIA del maestro (23 valores reales).
UPDATE productos SET rubro = categoria WHERE categoria IS NOT NULL;
UPDATE productos SET categoria = NULL;

-- --- Tags del maestro ----------------------------------------------------------------------------
-- `tag` conserva la forma original (para mostrar) y `tag_norm` la normalizada (para indexar):
-- en el Excel conviven 'salud' (436) y 'Salud' (51) como si fueran tags distintos.
CREATE TABLE producto_tags (
    producto_id UUID        NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    tag         VARCHAR(120) NOT NULL,
    tag_norm    VARCHAR(120) NOT NULL,
    PRIMARY KEY (producto_id, tag_norm)
);
CREATE INDEX ix_producto_tags_norm ON producto_tags(tag_norm);

-- --- Historial de importaciones -------------------------------------------------------------------
-- El archivo NO se guarda (trae costos y márgenes de TBC que nutriapp no necesita); sí queda el
-- reporte, que es lo que convierte el "archivo importado correctamente" en algo accionable.
CREATE TABLE maestro_importaciones (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre_archivo      VARCHAR(255),
    tamano_bytes        BIGINT,
    filas_leidas        INTEGER NOT NULL DEFAULT 0,
    filas_matcheadas    INTEGER NOT NULL DEFAULT 0,
    filas_actualizadas  INTEGER NOT NULL DEFAULT 0,
    filas_sin_match     INTEGER NOT NULL DEFAULT 0,
    filas_rechazadas    INTEGER NOT NULL DEFAULT 0,
    detalle             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID
);

-- --- Índices de los filtros nuevos (C-11) ---------------------------------------------------------
CREATE INDEX ix_productos_departamento ON productos(departamento) WHERE deleted_at IS NULL;
CREATE INDEX ix_productos_categoria    ON productos(categoria)    WHERE deleted_at IS NULL;
CREATE INDEX ix_productos_subcategoria ON productos(subcategoria) WHERE deleted_at IS NULL;
CREATE INDEX ix_productos_laboratorio  ON productos(laboratorio)  WHERE deleted_at IS NULL;
CREATE INDEX ix_productos_codigo_barras ON productos(codigo_barras) WHERE deleted_at IS NULL;
