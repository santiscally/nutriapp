package com.nutriapp.modules.producto.dto;

import java.util.List;

/**
 * Valores distintos para poblar los dropdowns de búsqueda del emisor de recetas.
 *
 * <p>Las listas planas sirven para el estado "sin filtrar"; {@code taxonomia} es el árbol
 * departamento → categoría → subcategoría para encadenarlos (142 subcategorías sueltas en un
 * dropdown no las usa nadie).
 *
 * <p><b>Ojo con {@code categorias}</b>: hasta V009 traía el Rubro de Contabilium (valía "Producto
 * terminado" para casi todo el catálogo). Ahora trae la CATEGORIA del maestro de TBC — 23 valores
 * reales. Es el mismo campo con datos distintos.
 */
public record ProductoFiltrosResponse(
        List<String> marcas,
        List<String> categorias,
        List<String> departamentos,
        List<String> subcategorias,
        List<String> laboratorios,
        List<Departamento> taxonomia
) {
    public record Departamento(String nombre, List<Categoria> categorias) {}

    public record Categoria(String nombre, List<String> subcategorias) {}
}
