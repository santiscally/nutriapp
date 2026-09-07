package com.bonosapp.modules.producto.maestro;

import com.bonosapp.modules.producto.entity.ProductoTag;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Una fila del maestro, ya limpia: solo los 10 campos que bonosapp lee, sin nada de la estructura de
 * costos de TBC.
 *
 * @param fila           número de fila en la planilla (1-based, incluyendo el encabezado) — es lo que
 *                       le sirve a Gon para ir a buscarla si algo no matcheó.
 * @param sku            clave de cruce con {@code productos.sku} (= {@code Codigo} de Contabilium).
 * @param idContabilium  verificación del cruce; null si la planilla no trae la columna.
 * @param bloqueado      ESTADO = BLOQUEADO → no se muestra en bonosapp.
 */
public record MaestroFila(
        int fila,
        String sku,
        Long idContabilium,
        String departamento,
        String categoria,
        String subcategoria,
        String laboratorio,
        boolean bloqueado,
        String imagenUrl,
        String descripcionWeb,
        List<String> tags
) {
    /** Los tags vienen separados por salto de línea dentro de una celda, no por coma. */
    public static List<String> parsearTags(String celda) {
        if (celda == null || celda.isBlank()) {
            return List.of();
        }
        Set<ProductoTag> vistos = new LinkedHashSet<>();
        List<String> out = new java.util.ArrayList<>();
        for (String parte : celda.split("\\r?\\n")) {
            ProductoTag t = ProductoTag.of(parte);
            if (t != null && vistos.add(t)) {
                out.add(t.getTag());
            }
        }
        return List.copyOf(out);
    }
}
