package com.bonosapp.modules.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** % de comisión de una nutricionista. Lo setea sólo el admin; el descuento es del producto. */
public record ParametrosNutricionistaRequest(
        @NotNull(message = "La comisión es obligatoria")
        @DecimalMin(value = "0", message = "La comisión no puede ser negativa")
        @DecimalMax(value = "100", message = "La comisión no puede superar 100%")
        BigDecimal comisionPct
) {}
