package com.nutriapp.modules.producto.mapper;

import com.nutriapp.modules.producto.dto.ProductoResponse;
import com.nutriapp.modules.producto.entity.Producto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    @Mapping(target = "origen", expression = "java(producto.getOrigen().name())")
    ProductoResponse toResponse(Producto producto);
}
