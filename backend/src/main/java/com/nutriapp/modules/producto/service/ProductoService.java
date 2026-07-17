package com.nutriapp.modules.producto.service;

import com.nutriapp.common.error.NotFoundException;
import com.nutriapp.modules.producto.dto.ProductoFiltrosResponse;
import com.nutriapp.modules.producto.dto.ProductoResponse;
import com.nutriapp.modules.producto.entity.Producto;
import com.nutriapp.modules.producto.mapper.ProductoMapper;
import com.nutriapp.modules.producto.repository.ProductoRepository;
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
    public Page<ProductoResponse> search(String q, String marca, String laboratorio,
                                         String principioActivo, String presentacion, Pageable pageable) {
        return repository.search(q, marca, laboratorio, principioActivo, presentacion, pageable)
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
        return new ProductoFiltrosResponse(
                repository.distinctMarcas(),
                repository.distinctLaboratorios(),
                repository.distinctPresentaciones());
    }
}
