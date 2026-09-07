package com.bonosapp.modules.producto.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Producto tal como lo ve el emisor de recetas.
 *
 * <p>Campos que aporta el maestro de artículos de TBC (C-11/C-12): {@code departamento},
 * {@code categoria}, {@code subcategoria}, {@code laboratorio}, {@code descripcionWeb},
 * {@code imagenUrl} y {@code tags}. Pueden venir en null: el catálogo puede tener productos que
 * todavía no están en el maestro.
 *
 * <p>{@code principioActivo} y {@code presentacion} siguen existiendo en el contrato pero hoy son
 * siempre null: no están en Contabilium ni en el maestro (ver 07-...md §2.4). La búsqueda por
 * principio activo se resuelve por tags.
 */
public record ProductoResponse(
        UUID id,
        String sku,
        String codigoBarras,
        String nombre,
        String descripcion,
        String descripcionWeb,
        BigDecimal precio,
        Integer stock,
        String imagenUrl,
        String marca,
        String departamento,
        String categoria,
        String subcategoria,
        String laboratorio,
        List<String> tags,
        String principioActivo,
        String presentacion,
        boolean publicado,
        String origen
) {}
