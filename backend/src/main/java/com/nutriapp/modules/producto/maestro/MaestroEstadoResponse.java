package com.nutriapp.modules.producto.maestro;

import java.time.Instant;

/**
 * Foto de la última importación del maestro, para mostrarla en el panel de integraciones al lado de
 * la última sync de Contabilium. Todos los campos en null = nunca se importó.
 *
 * @param catalogoActualizadoAt sello en los productos. Puede ser anterior a {@code importadoAt} si la
 *                              última importación no matcheó nada.
 */
public record MaestroEstadoResponse(
        Instant importadoAt,
        String nombreArchivo,
        Integer filasLeidas,
        Integer filasMatcheadas,
        Integer filasSinMatch,
        Integer filasRechazadas,
        Instant catalogoActualizadoAt
) {
    public static MaestroEstadoResponse vacio(Instant catalogoActualizadoAt) {
        return new MaestroEstadoResponse(null, null, null, null, null, null, catalogoActualizadoAt);
    }
}
