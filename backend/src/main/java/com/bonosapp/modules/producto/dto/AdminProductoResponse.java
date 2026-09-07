package com.bonosapp.modules.producto.dto;

import java.time.Instant;

/**
 * Producto como lo ve el <b>admin</b>: el del emisor más el estado de las dos fuentes que lo
 * escriben (Contabilium y el maestro de TBC) y el motivo por el que está o no publicado.
 *
 * <p>El buscador de recetas sólo muestra publicados y no dice nada del origen del dato — no le
 * sirve a la nutricionista. Al admin sí: es quien corre el sync y el import, y necesita ver qué
 * quedó afuera, sobre todo los que no matchearon contra el maestro (62 en el import real).
 */
public record AdminProductoResponse(
        ProductoResponse producto,
        /** ¿El maestro llegó a tocarlo? false = no matcheó por SKU o nunca se importó. */
        boolean enMaestro,
        /** ESTADO = BLOQUEADO en el maestro: despublicado a pedido de TBC. */
        boolean bloqueadoMaestro,
        Instant maestroSyncedAt,
        Instant lastSyncedAt,
        // Campos del ERP que no viajan al emisor —a la nutricionista no le dicen nada— pero acá son
        // el contexto de por qué un producto entró o quedó afuera del catálogo recetable.
        String tipoErp,
        boolean activoErp,
        String rubro,
        /**
         * Por qué no aparece en el buscador, en castellano y ya resuelto. null si está publicado.
         * Es la diferencia entre "hay 26 despublicados" y saber cuál arreglar.
         */
        String motivoNoPublicado
) {}
