-- S-01/S-02: el descuento del bono deja de ser de la profesional y pasa a ser del producto.

ALTER TABLE productos ADD COLUMN descuento_pct NUMERIC(5,2)
    CHECK (descuento_pct >= 0 AND descuento_pct <= 100);
ALTER TABLE productos ADD COLUMN estado_bonosapp BOOLEAN;
ALTER TABLE productos ADD COLUMN tiendanube_handle VARCHAR(200);

-- NULL no es 0: significa "el maestro todavía no dijo nada", y ahí manda el % de la profesional.
COMMENT ON COLUMN productos.descuento_pct IS
    'Columna DESCUENTO % del maestro, en escala 0-100. NULL = sin dato: cae al % de la profesional.';
COMMENT ON COLUMN productos.estado_bonosapp IS
    'Columna ESTADO BONOSAPP del maestro. true = recetable. NULL = el maestro no trae la columna.';
COMMENT ON COLUMN productos.tiendanube_handle IS
    'Slug del producto en la tienda. La URL se arma con TIENDANUBE_STORE_URL, que cambia de dominio.';
