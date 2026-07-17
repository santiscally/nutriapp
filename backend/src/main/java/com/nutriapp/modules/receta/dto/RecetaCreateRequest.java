package com.nutriapp.modules.receta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RecetaCreateRequest(
        @NotNull UUID pacienteId,
        @NotEmpty @Valid List<Item> items,
        /** Opcional: si null, toma el default de config (nutriapp.recetas.descuento-default-pct). */
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal descuentoPct
) {
    public record Item(
            @NotNull UUID productoId,
            @Min(1) int cantidad,
            String indicaciones
    ) {}
}
