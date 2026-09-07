package com.bonosapp.modules.producto.dto;

import java.time.Instant;

/**
 * Resultado de una sync manual del catálogo desde Contabilium (2.9). Sólo se devuelve cuando la
 * integración está en {@code live}; en {@code stub} el endpoint corta antes con 503 explícito.
 *
 * @param revisados     conceptos (productos) traídos del ERP y conciliados por SKU
 * @param creados       productos nuevos insertados en el catálogo local
 * @param actualizados  productos existentes cuyos datos cambiaron
 * @param sinCambios    productos existentes que ya estaban al día
 * @param syncedAt      momento de la conciliación (se sella en cada producto tocado)
 */
public record SyncProductosResponse(
        int revisados,
        int creados,
        int actualizados,
        int sinCambios,
        Instant syncedAt
) {}
