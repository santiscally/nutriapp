package com.bonosapp.modules.producto.mapper;

import com.bonosapp.modules.producto.dto.ProductoResponse;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.entity.ProductoTag;
import java.util.Comparator;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    @Mapping(target = "origen", expression = "java(producto.getOrigen().name())")
    @Mapping(target = "tags", expression = "java(tags(producto))")
    ProductoResponse toResponse(Producto producto);

    /**
     * Tags en su forma original (la normalizada es interna del buscador), ordenados alfabéticamente
     * para que la lista no cambie de orden entre requests.
     */
    default List<String> tags(Producto producto) {
        if (producto.getTags() == null || producto.getTags().isEmpty()) {
            return List.of();
        }
        return producto.getTags().stream()
                .map(ProductoTag::getTag)
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
