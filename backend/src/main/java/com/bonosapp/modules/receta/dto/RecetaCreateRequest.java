package com.bonosapp.modules.receta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Emisión de receta. El % de descuento NO viaja en el request: es el de la nutricionista y lo define
 * el admin. El backend lo aplica siempre; el front lo muestra como dato de solo lectura.
 */
public record RecetaCreateRequest(
        @NotNull UUID pacienteId,
        @NotEmpty @Valid List<Item> items
) {
    /**
     * El bono aplica a productos, no a cantidades: el cupón de TiendaNube restringe por producto y
     * no tiene forma de limitar unidades, así que una cantidad mayor a 1 no sería respetada en la
     * compra (con 2 recetadas y 3 en el carrito, las 3 salen con descuento). Se recetan N productos
     * distintos, uno de cada uno. El campo se conserva por las recetas viejas que ya tienen cantidad.
     */
    public record Item(
            @NotNull UUID productoId,
            @Min(1) @Max(value = 1, message = "El bono aplica a un producto por vez, no a cantidades")
            int cantidad,
            String indicaciones
    ) {}
}
