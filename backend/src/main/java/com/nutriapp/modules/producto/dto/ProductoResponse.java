package com.nutriapp.modules.producto.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductoResponse(
        UUID id,
        String sku,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        String imagenUrl,
        String marca,
        String laboratorio,
        String principioActivo,
        String presentacion,
        boolean publicado,
        String origen
) {}
