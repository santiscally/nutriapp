-- S-07: el cupón dejaba de existir como "no combinable" — TiendaNube lo crea combinable por defecto.

ALTER TABLE recetas ADD COLUMN combinable BOOLEAN NOT NULL DEFAULT false;

-- Los bonos ya emitidos SÍ se crearon combinables en la tienda; la columna refleja lo que pasó.
UPDATE recetas SET combinable = true WHERE cupon_tiendanube_id IS NOT NULL;

COMMENT ON COLUMN recetas.combinable IS
    'Si el cupón se puede sumar a otras promos de la tienda. Lo elige quien emite; default no.';
