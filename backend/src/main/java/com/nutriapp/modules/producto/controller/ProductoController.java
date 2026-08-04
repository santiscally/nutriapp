package com.nutriapp.modules.producto.controller;

import com.nutriapp.common.dto.PageResponse;
import com.nutriapp.modules.producto.dto.ProductoFiltrosResponse;
import com.nutriapp.modules.producto.dto.ProductoResponse;
import com.nutriapp.modules.producto.service.ProductoService;
import com.nutriapp.modules.producto.service.ProductoService.ProductoFiltro;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService service;

    /**
     * Buscador del emisor. {@code q} matchea nombre, descripción, SKU, código de barras y tags del
     * maestro, con ranking (nombre &gt; descripción &gt; tag). Los filtros de departamento,
     * categoría, subcategoría y laboratorio salen del maestro de artículos (C-11).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('productos:read')")
    public PageResponse<ProductoResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String subcategoria,
            @RequestParam(required = false) String laboratorio,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") boolean conStock,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax,
            @PageableDefault(size = 20) Pageable pageable) {
        ProductoFiltro filtro = new ProductoFiltro(q, marca, departamento, categoria, subcategoria,
                laboratorio, tag, conStock, precioMin, precioMax);
        return PageResponse.of(service.search(filtro, pageable));
    }

    @GetMapping("/filtros")
    @PreAuthorize("hasAuthority('productos:read')")
    public ProductoFiltrosResponse filtros() {
        return service.filtros();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('productos:read')")
    public ProductoResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
