package com.bonosapp.modules.producto.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resultado del mapeo del catálogo local contra TiendaNube por SKU. Sin este mapeo el cupón de una
 * receta se registra <b>sin restricción de productos</b> — un descuento sobre toda la tienda.
 *
 * @param revisados        variantes traídas de TiendaNube
 * @param mapeados         productos locales a los que se les escribió (o corrigió) el id
 * @param yaMapeados       productos locales que ya tenían el id correcto
 * @param sinSku           variantes de TiendaNube sin SKU: no hay por dónde conciliarlas
 * @param sinMatch         SKUs de TiendaNube que no existen en el catálogo local
 * @param pendientes       publicados que siguen sin id de TiendaNube tras esta corrida
 * @param skusSinMatch     muestra acotada de {@code sinMatch}, para revisar a ojo
 * @param mapeadoAt        momento de la corrida
 */
public record MapeoTiendaNubeResponse(
        int revisados,
        int mapeados,
        int yaMapeados,
        int sinSku,
        int sinMatch,
        long pendientes,
        List<String> skusSinMatch,
        Instant mapeadoAt
) {}
