package com.nutriapp.modules.producto.service;

import com.nutriapp.common.error.NotFoundException;
import com.nutriapp.modules.producto.dto.ProductoFiltrosResponse;
import com.nutriapp.modules.producto.dto.ProductoResponse;
import com.nutriapp.modules.producto.entity.Producto;
import com.nutriapp.modules.producto.mapper.ProductoMapper;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository repository;
    private final ProductoMapper mapper;

    @Transactional(readOnly = true)
    public Page<ProductoResponse> search(ProductoFiltro filtro, Pageable pageable) {
        return repository.search(filtro.q(), filtro.marca(), filtro.departamento(), filtro.categoria(),
                        filtro.subcategoria(), filtro.laboratorio(), filtro.tag(), filtro.conStock(),
                        filtro.precioMin(), filtro.precioMax(), pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductoResponse get(UUID id) {
        Producto p = repository.findById(id)
                .filter(prod -> !prod.isDeleted())
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public ProductoFiltrosResponse filtros() {
        List<Object[]> filas = repository.taxonomia();
        return new ProductoFiltrosResponse(
                repository.distinctMarcas(),
                valoresDe(filas, 1),
                valoresDe(filas, 0),
                valoresDe(filas, 2),
                repository.distinctLaboratorios(),
                arbol(filas));
    }

    /** Valores distintos y ordenados de una de las tres columnas de la taxonomía, sin nulos. */
    private static List<String> valoresDe(List<Object[]> filas, int col) {
        Set<String> out = new java.util.TreeSet<>();
        for (Object[] f : filas) {
            if (f[col] != null) {
                out.add((String) f[col]);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Arma departamento → categorías → subcategorías a partir de las combinaciones que existen de
     * verdad en el catálogo. Un producto sin departamento (o sin categoría) no inventa uno: queda
     * fuera del árbol y se lo encuentra por búsqueda o por los otros filtros.
     */
    private static List<ProductoFiltrosResponse.Departamento> arbol(List<Object[]> filas) {
        Map<String, Map<String, Set<String>>> mapa = new LinkedHashMap<>();
        for (Object[] f : filas) {
            String dep = (String) f[0];
            String cat = (String) f[1];
            String sub = (String) f[2];
            if (dep == null) {
                continue;
            }
            Map<String, Set<String>> cats = mapa.computeIfAbsent(dep, k -> new LinkedHashMap<>());
            if (cat == null) {
                continue;
            }
            Set<String> subs = cats.computeIfAbsent(cat, k -> new LinkedHashSet<>());
            if (sub != null) {
                subs.add(sub);
            }
        }
        List<ProductoFiltrosResponse.Departamento> out = new ArrayList<>();
        mapa.forEach((dep, cats) -> {
            List<ProductoFiltrosResponse.Categoria> categorias = new ArrayList<>();
            cats.forEach((cat, subs) ->
                    categorias.add(new ProductoFiltrosResponse.Categoria(cat, List.copyOf(subs))));
            out.add(new ProductoFiltrosResponse.Departamento(dep, List.copyOf(categorias)));
        });
        return List.copyOf(out);
    }

    /**
     * Los filtros del buscador en un solo objeto: son 10 y crecieron con el maestro (C-11), pasarlos
     * sueltos era una fila de parámetros del mismo tipo esperando que alguien invierta dos.
     */
    public record ProductoFiltro(
            String q,
            String marca,
            String departamento,
            String categoria,
            String subcategoria,
            String laboratorio,
            String tag,
            boolean conStock,
            BigDecimal precioMin,
            BigDecimal precioMax
    ) {}
}
