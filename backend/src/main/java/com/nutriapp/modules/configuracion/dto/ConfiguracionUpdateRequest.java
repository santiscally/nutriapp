package com.nutriapp.modules.configuracion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Actualización de los parámetros por el admin. Ambos porcentajes en [0, 100]. */
public record ConfiguracionUpdateRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal descuentoPct,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal comisionPct
) {}
