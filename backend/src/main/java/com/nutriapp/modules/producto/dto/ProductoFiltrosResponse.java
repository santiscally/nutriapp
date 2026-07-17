package com.nutriapp.modules.producto.dto;

import java.util.List;

/** Valores distintos para poblar los dropdowns de búsqueda del emisor de recetas. */
public record ProductoFiltrosResponse(
        List<String> marcas,
        List<String> laboratorios,
        List<String> presentaciones
) {}
