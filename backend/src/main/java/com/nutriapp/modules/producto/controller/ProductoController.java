package com.nutriapp.modules.producto.controller;

import com.nutriapp.common.dto.PageResponse;
import com.nutriapp.modules.producto.dto.ProductoFiltrosResponse;
import com.nutriapp.modules.producto.dto.ProductoResponse;
import com.nutriapp.modules.producto.service.ProductoService;
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

    @GetMapping
    @PreAuthorize("hasAuthority('productos:read')")
    public PageResponse<ProductoResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String laboratorio,
            @RequestParam(required = false) String principioActivo,
            @RequestParam(required = false) String presentacion,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.search(q, marca, laboratorio, principioActivo, presentacion, pageable));
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
