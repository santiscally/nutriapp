package com.nutriapp.modules.receta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Emisión de receta. El % de descuento NO viaja en el request: es fijo global y lo define el admin
 * (ConfiguracionService). El backend lo aplica siempre; el front lo muestra como dato de solo lectura.
 */
public record RecetaCreateRequest(
        @NotNull UUID pacienteId,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull UUID productoId,
            @Min(1) int cantidad,
            String indicaciones
    ) {}
}
