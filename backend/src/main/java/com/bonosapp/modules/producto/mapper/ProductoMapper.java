package com.bonosapp.modules.producto.mapper;

import com.bonosapp.integrations.IntegrationsProperties;
import com.bonosapp.modules.producto.dto.ProductoResponse;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.entity.ProductoTag;
import java.util.Comparator;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ProductoMapper {

    /** El link al producto depende del dominio de la tienda, que es config y cambió una vez ya. */
    @Autowired
    protected IntegrationsProperties integrations;

    @Mapping(target = "origen", expression = "java(producto.getOrigen().name())")
    @Mapping(target = "tags", expression = "java(tags(producto))")
    @Mapping(target = "urlProducto", expression = "java(urlProducto(producto))")
    public abstract ProductoResponse toResponse(Producto producto);

    /**
     * Tags en su forma original (la normalizada es interna del buscador), ordenados alfabéticamente
     * para que la lista no cambie de orden entre requests.
     */
    protected List<String> tags(Producto producto) {
        if (producto.getTags() == null || producto.getTags().isEmpty()) {
            return List.of();
        }
        return producto.getTags().stream()
                .map(ProductoTag::getTag)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    protected String urlProducto(Producto producto) {
        return integrations.tiendanube().urlDeProducto(producto.getTiendanubeHandle());
    }
}
