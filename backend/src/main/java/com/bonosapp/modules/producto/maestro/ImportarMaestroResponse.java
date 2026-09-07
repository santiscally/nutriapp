package com.bonosapp.modules.producto.maestro;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de importar el maestro. Gon pidió "algún mensaje de 'archivo importado correctamente'";
 * esto es eso más los números que lo hacen accionable: si de 2225 filas matchearon 300, el mensaje de
 * éxito solo sería engañoso.
 *
 * @param skusSinMatch SKUs del Excel que no existen en el catálogo (recortado, ver {@code muestraMaxima}).
 * @param rechazos     filas descartadas y por qué (SKU repetido, ID CONTABILIUM que no coincide).
 * @param despublicados productos que dejaron de ser recetables por el ESTADO=BLOQUEADO del maestro.
 */
public record ImportarMaestroResponse(
        int filasLeidas,
        int filasMatcheadas,
        int filasActualizadas,
        int filasSinMatch,
        int filasRechazadas,
        List<String> skusSinMatch,
        List<String> rechazos,
        int publicados,
        int despublicados,
        Instant importadoAt,
        String mensaje
) {
    /** Tope de ejemplos que viajan en la respuesta: el total va en los contadores. */
    public static final int MUESTRA_MAXIMA = 50;
}
