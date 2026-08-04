package com.nutriapp.modules.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * C-01 — % propios de una nutricionista. Cualquiera de los dos en {@code null} significa
 * "usá el valor global", no "no lo cambies": el PUT reemplaza ambos campos.
 */
public record ParametrosNutricionistaRequest(
        @DecimalMin(value = "0", message = "El descuento no puede ser negativo")
        @DecimalMax(value = "100", message = "El descuento no puede superar 100%")
        BigDecimal descuentoPct,

        @DecimalMin(value = "0", message = "La comisión no puede ser negativa")
        @DecimalMax(value = "100", message = "La comisión no puede superar 100%")
        BigDecimal comisionPct
) {}
